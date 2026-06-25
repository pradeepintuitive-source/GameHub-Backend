package com.gamehub.mafia.infrastructure;

import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.mafia.domain.DayCycle;
import com.gamehub.mafia.domain.MafiaAction;
import com.gamehub.mafia.domain.MafiaActionType;
import com.gamehub.mafia.domain.MafiaGameState;
import com.gamehub.mafia.domain.MafiaPhase;
import com.gamehub.mafia.domain.NightAction;
import com.gamehub.mafia.domain.PlayerRole;
import com.gamehub.mafia.domain.Role;
import com.gamehub.mafia.domain.Vote;
import com.gamehub.shared.domain.GameEngine;
import com.gamehub.shared.domain.GameType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MafiaEngine implements GameEngine<MafiaGameState, MafiaAction> {

    private final Random random = new Random();

    @Override
    public GameType supportedGameType() {
        return GameType.MAFIA;
    }

    @Override
    public Class<MafiaGameState> stateType() {
        return MafiaGameState.class;
    }

    @Override
    public MafiaGameState startGame(UUID sessionId, List<UUID> playerIds) {
        if (playerIds.size() < 4) {
            throw new BusinessRuleViolationException("Mafia requires at least 4 players");
        }
        List<UUID> shuffled = new ArrayList<>(playerIds);
        Collections.shuffle(shuffled, random);
        Map<UUID, PlayerRole> roles = new LinkedHashMap<>();
        int mafiaCount = playerIds.size() >= 7 ? 2 : 1;
        int index = 0;
        for (; index < mafiaCount; index++) {
            roles.put(shuffled.get(index), new PlayerRole(shuffled.get(index), Role.MAFIA, true, false));
        }
        if (playerIds.size() >= 4) {
            roles.put(shuffled.get(index), new PlayerRole(shuffled.get(index), Role.DOCTOR, true, false));
            index++;
        }
        if (playerIds.size() >= 5) {
            roles.put(shuffled.get(index), new PlayerRole(shuffled.get(index), Role.DETECTIVE, true, false));
            index++;
        }
        while (index < shuffled.size()) {
            roles.put(shuffled.get(index), new PlayerRole(shuffled.get(index), Role.VILLAGER, true, false));
            index++;
        }

        return new MafiaGameState(
                sessionId,
                MafiaPhase.ROLE_ASSIGNMENT,
                roles,
                new ArrayList<>(),
                new DayCycle(1, new ArrayList<>()),
                new ArrayList<>(List.of("Roles assigned")),
                new HashSet<>(),
                new HashSet<>());
    }

    @Override
    public MafiaGameState endGame(MafiaGameState state) {
        return copy(state, MafiaPhase.GAME_OVER, "Game ended");
    }

    @Override
    public MafiaGameState pauseGame(MafiaGameState state) {
        return copy(state, MafiaPhase.PAUSED, "Game paused");
    }

    @Override
    public MafiaGameState resumeGame(MafiaGameState state) {
        MafiaPhase phase = state.phase() == MafiaPhase.PAUSED ? MafiaPhase.DISCUSSION : state.phase();
        return copy(state, phase, "Game resumed");
    }

    @Override
    public void validateAction(MafiaGameState state, MafiaAction action) {
        PlayerRole playerRole = state.roles().get(action.actorPlayerId());
        if (playerRole == null || !playerRole.alive()) {
            throw new BusinessRuleViolationException("Player is not active in this game");
        }
        if (state.phase() == MafiaPhase.PAUSED || state.phase() == MafiaPhase.GAME_OVER || state.phase() == MafiaPhase.ROLE_ASSIGNMENT) {
            throw new BusinessRuleViolationException("Game is not accepting actions");
        }
    }

    @Override
    public MafiaGameState processAction(MafiaGameState state, MafiaAction action) {
        validateAction(state, action);
        return switch (action.type()) {
            case KILL, PROTECT, INVESTIGATE -> handleNightAction(state, action);
            case VOTE -> handleVote(state, action);
        };
    }

    public MafiaGameState advancePhase(MafiaGameState state) {
        return switch (state.phase()) {
            case ROLE_ASSIGNMENT -> copy(state, MafiaPhase.NIGHT, "Night begins");
            case DAY -> copy(state, MafiaPhase.DISCUSSION, "Discussion begins");
            case DISCUSSION -> copy(state, MafiaPhase.VOTING, "Voting begins");
            case PAUSED, GAME_OVER, VOTING, NIGHT -> state;
        };
    }

    private MafiaGameState handleNightAction(MafiaGameState state, MafiaAction action) {
        if (state.phase() != MafiaPhase.NIGHT) {
            throw new BusinessRuleViolationException("Night action is not allowed in this phase");
        }
        PlayerRole actor = state.roles().get(action.actorPlayerId());
        validateNightRole(actor, action.type());
        List<NightAction> nightActions = new ArrayList<>(state.nightActions());
        nightActions.removeIf(existing -> existing.actorPlayerId().equals(action.actorPlayerId()) && existing.type() == action.type());
        nightActions.add(new NightAction(action.actorPlayerId(), action.type(), action.targetPlayerId()));

        MafiaGameState updated = new MafiaGameState(
                state.sessionId(),
                state.phase(),
                new LinkedHashMap<>(state.roles()),
                nightActions,
                state.currentCycle(),
                append(state.announcements(), action.type() + " submitted"),
                new HashSet<>(state.protectedPlayers()),
                new HashSet<>(state.investigatedPlayers()));

        if (hasAllRequiredNightActions(updated)) {
            return resolveNight(updated);
        }
        return updated;
    }

    private MafiaGameState handleVote(MafiaGameState state, MafiaAction action) {
        if (state.phase() != MafiaPhase.VOTING) {
            throw new BusinessRuleViolationException("Voting is not open");
        }
        if (!state.roles().containsKey(action.targetPlayerId()) || !state.roles().get(action.targetPlayerId()).alive()) {
            throw new BusinessRuleViolationException("Vote target is not alive");
        }
        List<Vote> votes = new ArrayList<>(state.currentCycle().votes());
        votes.removeIf(vote -> vote.voterPlayerId().equals(action.actorPlayerId()));
        votes.add(new Vote(action.actorPlayerId(), action.targetPlayerId()));
        DayCycle updatedCycle = new DayCycle(state.currentCycle().cycleNumber(), votes);
        MafiaGameState updated = new MafiaGameState(
                state.sessionId(),
                state.phase(),
                new LinkedHashMap<>(state.roles()),
                new ArrayList<>(state.nightActions()),
                updatedCycle,
                append(state.announcements(), "Vote submitted"),
                new HashSet<>(state.protectedPlayers()),
                new HashSet<>(state.investigatedPlayers()));

        long aliveCount = state.roles().values().stream().filter(PlayerRole::alive).count();
        if (votes.size() >= aliveCount) {
            return resolveVotes(updated);
        }
        return updated;
    }

    private MafiaGameState resolveNight(MafiaGameState state) {
        Map<UUID, PlayerRole> roles = new LinkedHashMap<>(state.roles());
        UUID protectedTarget = state.nightActions().stream()
                .filter(action -> action.type() == MafiaActionType.PROTECT)
                .map(NightAction::targetPlayerId)
                .findFirst()
                .orElse(null);
        UUID killTarget = state.nightActions().stream()
                .filter(action -> action.type() == MafiaActionType.KILL)
                .map(NightAction::targetPlayerId)
                .findFirst()
                .orElse(null);
        UUID investigateTarget = state.nightActions().stream()
                .filter(action -> action.type() == MafiaActionType.INVESTIGATE)
                .map(NightAction::targetPlayerId)
                .findFirst()
                .orElse(null);

        List<String> announcements = new ArrayList<>(state.announcements());
        if (killTarget != null && !killTarget.equals(protectedTarget)) {
            PlayerRole targetRole = roles.get(killTarget);
            roles.put(killTarget, new PlayerRole(targetRole.playerId(), targetRole.role(), false, targetRole.revealed()));
            announcements.add("A player was eliminated overnight");
        } else {
            announcements.add("No one died during the night");
        }
        if (investigateTarget != null) {
            announcements.add("Investigation completed");
        }

        MafiaGameState resolved = new MafiaGameState(
                state.sessionId(),
                determineWinner(roles).map(ignored -> MafiaPhase.GAME_OVER).orElse(MafiaPhase.DAY),
                roles,
                new ArrayList<>(),
                new DayCycle(state.currentCycle().cycleNumber(), new ArrayList<>()),
                announcements,
                protectedTarget == null ? Set.of() : Set.of(protectedTarget),
                investigateTarget == null ? Set.of() : Set.of(investigateTarget));

        return determineWinner(roles)
                .map(winner -> new MafiaGameState(
                        resolved.sessionId(),
                        MafiaPhase.GAME_OVER,
                        resolved.roles(),
                        resolved.nightActions(),
                        resolved.currentCycle(),
                        append(resolved.announcements(), winner + " win"),
                        resolved.protectedPlayers(),
                        resolved.investigatedPlayers()))
                .orElse(resolved);
    }

    private MafiaGameState resolveVotes(MafiaGameState state) {
        Map<UUID, Long> tally = new HashMap<>();
        for (Vote vote : state.currentCycle().votes()) {
            tally.merge(vote.targetPlayerId(), 1L, Long::sum);
        }
        UUID eliminated = tally.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new BusinessRuleViolationException("No votes to resolve"));
        Map<UUID, PlayerRole> roles = new LinkedHashMap<>(state.roles());
        PlayerRole eliminatedRole = roles.get(eliminated);
        roles.put(eliminated, new PlayerRole(eliminatedRole.playerId(), eliminatedRole.role(), false, true));

        List<String> announcements = append(state.announcements(), "Voting resolved");
        MafiaGameState resolved = new MafiaGameState(
                state.sessionId(),
                determineWinner(roles).map(ignored -> MafiaPhase.GAME_OVER).orElse(MafiaPhase.NIGHT),
                roles,
                new ArrayList<>(),
                new DayCycle(state.currentCycle().cycleNumber() + 1, new ArrayList<>()),
                announcements,
                new HashSet<>(),
                new HashSet<>());
        return determineWinner(roles)
                .map(winner -> new MafiaGameState(
                        resolved.sessionId(),
                        MafiaPhase.GAME_OVER,
                        resolved.roles(),
                        resolved.nightActions(),
                        resolved.currentCycle(),
                        append(resolved.announcements(), winner + " win"),
                        resolved.protectedPlayers(),
                        resolved.investigatedPlayers()))
                .orElse(resolved);
    }

    private boolean hasAllRequiredNightActions(MafiaGameState state) {
        boolean mafiaActed = state.nightActions().stream().anyMatch(action -> action.type() == MafiaActionType.KILL);
        boolean doctorNeeded = state.roles().values().stream().anyMatch(role -> role.alive() && role.role() == Role.DOCTOR);
        boolean detectiveNeeded = state.roles().values().stream().anyMatch(role -> role.alive() && role.role() == Role.DETECTIVE);
        boolean doctorActed = state.nightActions().stream().anyMatch(action -> action.type() == MafiaActionType.PROTECT);
        boolean detectiveActed = state.nightActions().stream().anyMatch(action -> action.type() == MafiaActionType.INVESTIGATE);
        return mafiaActed && (!doctorNeeded || doctorActed) && (!detectiveNeeded || detectiveActed);
    }

    private void validateNightRole(PlayerRole actor, MafiaActionType type) {
        switch (type) {
            case KILL -> {
                if (actor.role() != Role.MAFIA) {
                    throw new BusinessRuleViolationException("Only Mafia can kill");
                }
            }
            case PROTECT -> {
                if (actor.role() != Role.DOCTOR) {
                    throw new BusinessRuleViolationException("Only Doctor can protect");
                }
            }
            case INVESTIGATE -> {
                if (actor.role() != Role.DETECTIVE) {
                    throw new BusinessRuleViolationException("Only Detective can investigate");
                }
            }
            case VOTE -> throw new BusinessRuleViolationException("Vote is not a night action");
        }
    }

    private java.util.Optional<String> determineWinner(Map<UUID, PlayerRole> roles) {
        long aliveMafia = roles.values().stream().filter(role -> role.alive() && role.role() == Role.MAFIA).count();
        long aliveTown = roles.values().stream().filter(role -> role.alive() && role.role() != Role.MAFIA).count();
        if (aliveMafia == 0) {
            return java.util.Optional.of("TOWN");
        }
        if (aliveMafia >= aliveTown) {
            return java.util.Optional.of("MAFIA");
        }
        return java.util.Optional.empty();
    }

    private MafiaGameState copy(MafiaGameState state, MafiaPhase phase, String announcement) {
        return new MafiaGameState(
                state.sessionId(),
                phase,
                new LinkedHashMap<>(state.roles()),
                new ArrayList<>(state.nightActions()),
                state.currentCycle(),
                append(state.announcements(), announcement),
                new HashSet<>(state.protectedPlayers()),
                new HashSet<>(state.investigatedPlayers()));
    }

    private List<String> append(List<String> announcements, String message) {
        List<String> updated = new ArrayList<>(announcements);
        updated.add(message);
        return updated;
    }
}
