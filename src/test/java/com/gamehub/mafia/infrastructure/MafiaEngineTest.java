package com.gamehub.mafia.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamehub.mafia.domain.MafiaAction;
import com.gamehub.mafia.domain.MafiaActionType;
import com.gamehub.mafia.domain.MafiaGameState;
import com.gamehub.mafia.domain.MafiaPhase;
import com.gamehub.mafia.domain.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MafiaEngineTest {

    private final MafiaEngine mafiaEngine = new MafiaEngine();

    @Test
    void startGameShouldAssignRequiredRoles() {
        List<UUID> players = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());

        MafiaGameState state = mafiaEngine.startGame(UUID.randomUUID(), players);

        assertThat(state.phase()).isEqualTo(MafiaPhase.ROLE_ASSIGNMENT);
        assertThat(state.roles().values().stream().filter(role -> role.role() == Role.MAFIA).count()).isEqualTo(1);
        assertThat(state.roles().values().stream().filter(role -> role.role() == Role.DOCTOR).count()).isEqualTo(1);
        assertThat(state.roles().values().stream().filter(role -> role.role() == Role.DETECTIVE).count()).isEqualTo(1);
    }

    @Test
    void nightActionsShouldResolveIntoDay() {
        List<UUID> players = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());

        MafiaGameState state = mafiaEngine.advancePhase(mafiaEngine.startGame(UUID.randomUUID(), players));
        UUID mafia = state.roles().values().stream().filter(role -> role.role() == Role.MAFIA).findFirst().orElseThrow().playerId();
        UUID doctor = state.roles().values().stream().filter(role -> role.role() == Role.DOCTOR).findFirst().orElseThrow().playerId();
        UUID detective = state.roles().values().stream().filter(role -> role.role() == Role.DETECTIVE).findFirst().orElseThrow().playerId();
        UUID target = state.roles().keySet().stream().filter(playerId -> !playerId.equals(mafia)).findFirst().orElseThrow();

        state = mafiaEngine.processAction(state, new MafiaAction(mafia, MafiaActionType.KILL, target));
        state = mafiaEngine.processAction(state, new MafiaAction(doctor, MafiaActionType.PROTECT, doctor));
        state = mafiaEngine.processAction(state, new MafiaAction(detective, MafiaActionType.INVESTIGATE, mafia));

        assertThat(state.phase()).isEqualTo(MafiaPhase.DAY);
        assertThat(state.nightActions()).isEmpty();
        assertThat(state.announcements()).isNotEmpty();
    }

    @Test
    void discussionShouldAdvanceToVoting() {
        MafiaGameState state = new MafiaGameState(
                UUID.randomUUID(),
                MafiaPhase.DISCUSSION,
                java.util.Map.of(),
                List.of(),
                new com.gamehub.mafia.domain.DayCycle(1, List.of()),
                List.of(),
                java.util.Set.of(),
                java.util.Set.of());

        MafiaGameState updated = mafiaEngine.advancePhase(state);

        assertThat(updated.phase()).isEqualTo(MafiaPhase.VOTING);
    }
}
