package com.gamehub.monopoly.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamehub.monopoly.domain.Board;
import com.gamehub.monopoly.domain.MonopolyAction;
import com.gamehub.monopoly.domain.MonopolyActionType;
import com.gamehub.monopoly.domain.MonopolyGameState;
import com.gamehub.monopoly.domain.MonopolyPhase;
import com.gamehub.monopoly.domain.PlayerAsset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonopolyEngineTest {

    private final MonopolyEngine monopolyEngine = new MonopolyEngine();

    @Test
    void startGameShouldInitializeState() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();

        MonopolyGameState state = monopolyEngine.startGame(UUID.randomUUID(), List.of(playerOne, playerTwo));

        assertThat(state.phase()).isEqualTo(MonopolyPhase.WAITING_FOR_ROLL);
        assertThat(state.currentPlayerId()).isEqualTo(playerOne);
        assertThat(state.assets()).hasSize(2);
        assertThat(state.assets().get(playerOne).cash()).isEqualTo(1500);
    }

    @Test
    void buyPropertyShouldAssignOwnershipAndDeductCash() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        Map<UUID, PlayerAsset> assets = new HashMap<>();
        assets.put(playerOne, new PlayerAsset(playerOne, 1500, 1, false, 0, new HashSet<>()));
        assets.put(playerTwo, new PlayerAsset(playerTwo, 1500, 0, false, 0, new HashSet<>()));

        MonopolyGameState state = new MonopolyGameState(
                UUID.randomUUID(),
                MonopolyPhase.WAITING_FOR_DECISION,
                playerOne,
                1,
                0,
                Board.standardBoard(),
                assets,
                new HashMap<>(),
                new HashMap<>(),
                new HashSet<>(),
                List.of("Landed on Mediterranean Avenue"));

        MonopolyGameState updated = monopolyEngine.processAction(
                state,
                new MonopolyAction(playerOne, MonopolyActionType.BUY_PROPERTY, null, null, null, Map.of()));

        assertThat(updated.owners()).containsEntry(1, playerOne);
        assertThat(updated.assets().get(playerOne).cash()).isEqualTo(1440);
        assertThat(updated.assets().get(playerOne).ownedTilePositions()).contains(1);
    }

    @Test
    void endTurnShouldRotateToNextPlayer() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        Map<UUID, PlayerAsset> assets = new HashMap<>();
        assets.put(playerOne, new PlayerAsset(playerOne, 1500, 0, false, 0, Set.of()));
        assets.put(playerTwo, new PlayerAsset(playerTwo, 1500, 0, false, 0, Set.of()));

        MonopolyGameState state = new MonopolyGameState(
                UUID.randomUUID(),
                MonopolyPhase.WAITING_FOR_DECISION,
                playerOne,
                1,
                0,
                Board.standardBoard(),
                assets,
                new HashMap<>(),
                new HashMap<>(),
                new HashSet<>(),
                List.of());

        MonopolyGameState updated = monopolyEngine.processAction(
                state,
                new MonopolyAction(playerOne, MonopolyActionType.END_TURN, null, null, null, Map.of()));

        assertThat(updated.currentPlayerId()).isEqualTo(playerTwo);
        assertThat(updated.phase()).isEqualTo(MonopolyPhase.WAITING_FOR_ROLL);
        assertThat(updated.currentTurn()).isEqualTo(2);
    }
}
