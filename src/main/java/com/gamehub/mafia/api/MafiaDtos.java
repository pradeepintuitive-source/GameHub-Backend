package com.gamehub.mafia.api;

import com.gamehub.mafia.domain.MafiaActionType;
import com.gamehub.mafia.domain.MafiaPhase;
import com.gamehub.mafia.domain.Role;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MafiaDtos {

    private MafiaDtos() {
    }

    public record MafiaActionRequest(
            @NotNull MafiaActionType type,
            @NotNull UUID targetPlayerId) {
    }

    public record MafiaStateResponse(
            UUID sessionId,
            MafiaPhase phase,
            Map<UUID, PlayerRoleResponse> roles,
            List<NightActionResponse> nightActions,
            DayCycleResponse currentCycle,
            List<String> announcements,
            Set<UUID> protectedPlayers,
            Set<UUID> investigatedPlayers) {
    }

    public record PlayerRoleResponse(
            UUID playerId,
            Role role,
            boolean alive,
            boolean revealed) {
    }

    public record NightActionResponse(
            UUID actorPlayerId,
            MafiaActionType type,
            UUID targetPlayerId) {
    }

    public record VoteResponse(
            UUID voterPlayerId,
            UUID targetPlayerId) {
    }

    public record DayCycleResponse(
            int cycleNumber,
            List<VoteResponse> votes) {
    }
}
