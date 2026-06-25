package com.gamehub.mafia.domain;

import com.gamehub.shared.domain.GameState;
import com.gamehub.shared.domain.GameType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record MafiaGameState(
        UUID sessionId,
        MafiaPhase phase,
        Map<UUID, PlayerRole> roles,
        List<NightAction> nightActions,
        DayCycle currentCycle,
        List<String> announcements,
        Set<UUID> protectedPlayers,
        Set<UUID> investigatedPlayers) implements GameState {

    @Override
    public GameType gameType() {
        return GameType.MAFIA;
    }
}
