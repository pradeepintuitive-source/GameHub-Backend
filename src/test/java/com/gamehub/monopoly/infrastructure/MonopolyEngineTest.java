package com.gamehub.monopoly.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.monopoly.domain.Board;
import com.gamehub.monopoly.domain.MonopolyAction;
import com.gamehub.monopoly.domain.MonopolyActionType;
import com.gamehub.monopoly.domain.MonopolyGameState;
import com.gamehub.monopoly.domain.MonopolyPhase;
import com.gamehub.monopoly.domain.PendingDebt;
import com.gamehub.monopoly.domain.PlayerAsset;
import com.gamehub.monopoly.domain.PropertyDevelopment;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
        assertThat(state.assets().get(playerOne).cash()).isEqualTo(15000);
    }

    @Test
    void startGameShouldUseIndiaEditionEconomyForFourPlayers() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        UUID playerThree = UUID.randomUUID();
        UUID playerFour = UUID.randomUUID();

        MonopolyGameState state = monopolyEngine.startGame(UUID.randomUUID(), List.of(playerOne, playerTwo, playerThree, playerFour));

        assertThat(state.board().tileAt(1).name()).isEqualTo("Manglore");
        assertThat(state.assets().get(playerOne).cash()).isEqualTo(15000);
    }

    @Test
    void buyPropertyShouldAssignOwnershipAndDeductCash() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>();
        assets.put(playerOne, new PlayerAsset(playerOne, 15000, 1, false, 0, new HashSet<>()));
        assets.put(playerTwo, new PlayerAsset(playerTwo, 15000, 0, false, 0, new HashSet<>()));

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
                List.of("Landed on Manglore"));

        MonopolyGameState updated = monopolyEngine.processAction(
                state,
                new MonopolyAction(playerOne, MonopolyActionType.BUY_PROPERTY, null, null, null, Map.of()));

        assertThat(updated.owners()).containsEntry(1, playerOne);
        assertThat(updated.assets().get(playerOne).cash()).isEqualTo(14400);
        assertThat(updated.assets().get(playerOne).ownedTilePositions()).contains(1);
        assertThat(updated.phase()).isEqualTo(MonopolyPhase.WAITING_FOR_ROLL);
        assertThat(updated.currentPlayerId()).isEqualTo(playerTwo);
    }

    @Test
    void endTurnShouldRotateToNextPlayer() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        Map<UUID, PlayerAsset> assets = new HashMap<>();
        assets.put(playerOne, new PlayerAsset(playerOne, 15000, 0, false, 0, Set.of()));
        assets.put(playerTwo, new PlayerAsset(playerTwo, 15000, 0, false, 0, Set.of()));

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

    @Test
    void taxAboveCashOpensRaisingFundsAndLeavesCashUnchanged() {
        MonopolyEngine engine = new MonopolyEngine();
        engine.setRandom(new FixedDie());
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>();
        assets.put(playerOne, new PlayerAsset(playerOne, 100, 36, false, 0, Set.of()));
        assets.put(playerTwo, new PlayerAsset(playerTwo, 15000, 0, false, 0, Set.of()));

        MonopolyGameState updated = engine.processAction(
                base(playerOne, MonopolyPhase.WAITING_FOR_ROLL, assets, new HashMap<>(), null, Set.of()),
                action(playerOne, MonopolyActionType.ROLL_DICE, null, null, null));

        assertThat(updated.phase()).isEqualTo(MonopolyPhase.RAISING_FUNDS);
        assertThat(updated.assets().get(playerOne).cash()).isEqualTo(100);
        assertThat(updated.assets().get(playerOne).position()).isEqualTo(38);
        assertThat(updated.pendingDebt().amount()).isEqualTo(1000);
        assertThat(updated.pendingDebt().creditorId()).isNull();
    }

    @Test
    void sellingAHouseThenMortgagingPaysTheFullDebt() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>();
        assets.put(playerOne, new PlayerAsset(playerOne, 100, 0, false, 0, new HashSet<>(Set.of(24))));
        assets.put(playerTwo, new PlayerAsset(playerTwo, 1000, 0, false, 0, Set.of()));
        Map<Integer, UUID> owners = new HashMap<>();
        owners.put(24, playerOne);
        Map<Integer, PropertyDevelopment> developments = new HashMap<>();
        developments.put(24, new PropertyDevelopment(1, false));
        MonopolyGameState state = raising(
                playerOne, assets, owners, developments, new PendingDebt(playerOne, playerTwo, 2000, "Rent for Bengaluru"));

        MonopolyGameState sold = monopolyEngine.processAction(state, action(playerOne, MonopolyActionType.SELL_HOUSE, 24, null, null));
        assertThat(sold.assets().get(playerOne).cash()).isEqualTo(850);
        assertThat(sold.phase()).isEqualTo(MonopolyPhase.RAISING_FUNDS);

        MonopolyGameState mortgaged = monopolyEngine.processAction(sold, action(playerOne, MonopolyActionType.MORTGAGE, 24, null, null));
        assertThat(mortgaged.assets().get(playerOne).cash()).isEqualTo(2050);
        assertThat(mortgaged.mortgagedTiles()).contains(24);

        MonopolyGameState paid = monopolyEngine.processAction(mortgaged, action(playerOne, MonopolyActionType.PAY_DEBT, null, null, null));
        assertThat(paid.phase()).isEqualTo(MonopolyPhase.WAITING_FOR_ROLL);
        assertThat(paid.currentPlayerId()).isEqualTo(playerTwo);
        assertThat(paid.pendingDebt()).isNull();
        assertThat(paid.assets().get(playerOne).cash()).isEqualTo(50);
        assertThat(paid.assets().get(playerTwo).cash()).isEqualTo(3000);
    }

    @Test
    void acceptedSaleIncreasesDebtorCash() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>();
        assets.put(playerOne, new PlayerAsset(playerOne, 100, 0, false, 0, new HashSet<>(Set.of(1))));
        assets.put(playerTwo, new PlayerAsset(playerTwo, 5000, 0, false, 0, Set.of()));
        Map<Integer, UUID> owners = new HashMap<>();
        owners.put(1, playerOne);
        MonopolyGameState state = raising(playerOne, assets, owners, new HashMap<>(), new PendingDebt(playerOne, playerTwo, 500, "Rent"));

        MonopolyGameState offered = monopolyEngine.processAction(
                state, action(playerOne, MonopolyActionType.PROPOSE_SALE, 1, playerTwo, 600));
        MonopolyGameState sold = monopolyEngine.processAction(
                offered, action(playerTwo, MonopolyActionType.ACCEPT_SALE, null, null, null));

        assertThat(sold.assets().get(playerOne).cash()).isEqualTo(700);
        assertThat(sold.owners()).containsEntry(1, playerTwo);
        assertThat(sold.phase()).isEqualTo(MonopolyPhase.RAISING_FUNDS);
    }

    @Test
    void bankruptcyTransfersPropertiesAndEndsATwoPlayerGame() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>();
        assets.put(playerOne, new PlayerAsset(playerOne, 50, 0, false, 0, new HashSet<>(Set.of(1))));
        assets.put(playerTwo, new PlayerAsset(playerTwo, 5000, 0, false, 0, Set.of()));
        Map<Integer, UUID> owners = new HashMap<>();
        owners.put(1, playerOne);
        MonopolyGameState state = raising(playerOne, assets, owners, new HashMap<>(), new PendingDebt(playerOne, playerTwo, 500, "Rent"));

        MonopolyGameState updated = monopolyEngine.processAction(
                state, action(playerOne, MonopolyActionType.DECLARE_BANKRUPTCY, null, null, null));

        assertThat(updated.phase()).isEqualTo(MonopolyPhase.ENDED);
        assertThat(updated.currentPlayerId()).isEqualTo(playerTwo);
        assertThat(updated.owners()).containsEntry(1, playerTwo);
        assertThat(updated.assets().get(playerOne).cash()).isZero();
        assertThat(updated.assets().get(playerTwo).cash()).isEqualTo(5050);
        assertThat(updated.bankruptPlayerIds()).contains(playerOne);
    }

    @Test
    void endTurnSkipsBankruptPlayer() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        UUID playerThree = UUID.randomUUID();
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>();
        assets.put(playerOne, new PlayerAsset(playerOne, 15000, 0, false, 0, Set.of()));
        assets.put(playerTwo, new PlayerAsset(playerTwo, 0, 0, false, 0, Set.of()));
        assets.put(playerThree, new PlayerAsset(playerThree, 15000, 0, false, 0, Set.of()));

        MonopolyGameState updated = monopolyEngine.processAction(
                base(playerOne, MonopolyPhase.WAITING_FOR_DECISION, assets, new HashMap<>(), null, Set.of(playerTwo)),
                action(playerOne, MonopolyActionType.END_TURN, null, null, null));

        assertThat(updated.currentPlayerId()).isEqualTo(playerThree);
        assertThat(updated.phase()).isEqualTo(MonopolyPhase.WAITING_FOR_ROLL);
    }

    @Test
    void buildHouseRequiresLandingOnThatCityAgain() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        MonopolyGameState away = ownedCity(playerOne, playerTwo, 0, List.of("Upgrade available on Manglore"));

        BusinessRuleViolationException error = assertThrows(
                BusinessRuleViolationException.class,
                () -> monopolyEngine.processAction(away, buildHouse(playerOne)));

        assertThat(error.getMessage()).contains("Land on this property again");
    }

    @Test
    void buildHouseSucceedsOnceWhenPlayerLandsOnOwnedCity() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        MonopolyGameState landed = ownedCity(
                playerOne,
                playerTwo,
                1,
                List.of("Upgrade available on Manglore"));

        MonopolyGameState updated = monopolyEngine.processAction(landed, buildHouse(playerOne));

        assertThat(updated.developments().get(1).houses()).isEqualTo(1);
        assertThat(updated.assets().get(playerOne).cash()).isEqualTo(14500);

        assertThrows(
                BusinessRuleViolationException.class,
                () -> monopolyEngine.processAction(updated, buildHouse(playerOne)));
    }

    @Test
    void buyingACityDoesNotAllowAnUpgradeOnThatVisit() {
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        Map<UUID, PlayerAsset> assets = new HashMap<>();
        assets.put(playerOne, new PlayerAsset(playerOne, 15000, 1, false, 0, new HashSet<>()));
        assets.put(playerTwo, new PlayerAsset(playerTwo, 15000, 0, false, 0, new HashSet<>()));
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
                List.of("Player rolled 1 and landed on Manglore"));

        MonopolyGameState bought = monopolyEngine.processAction(
                state,
                new MonopolyAction(playerOne, MonopolyActionType.BUY_PROPERTY, null, null, null, Map.of()));

        assertThrows(
                BusinessRuleViolationException.class,
                () -> monopolyEngine.processAction(bought, buildHouse(playerOne)));
    }

    private static MonopolyAction buildHouse(UUID playerId) {
        return new MonopolyAction(playerId, MonopolyActionType.BUILD_HOUSE, 1, null, null, Map.of());
    }

    private static MonopolyGameState ownedCity(
            UUID owner,
            UUID other,
            int ownerPosition,
            List<String> log) {
        Map<UUID, PlayerAsset> assets = new HashMap<>();
        assets.put(owner, new PlayerAsset(owner, 15000, ownerPosition, false, 0, Set.of(1)));
        assets.put(other, new PlayerAsset(other, 15000, 0, false, 0, Set.of()));
        Map<Integer, UUID> owners = new HashMap<>();
        owners.put(1, owner);
        return new MonopolyGameState(
                UUID.randomUUID(),
                MonopolyPhase.WAITING_FOR_DECISION,
                owner,
                1,
                0,
                Board.standardBoard(),
                assets,
                owners,
                new HashMap<>(),
                new HashSet<>(),
                log);
    }

    private MonopolyGameState raising(
            UUID current,
            Map<UUID, PlayerAsset> assets,
            Map<Integer, UUID> owners,
            Map<Integer, PropertyDevelopment> developments,
            PendingDebt debt) {
        return new MonopolyGameState(
                UUID.randomUUID(),
                MonopolyPhase.RAISING_FUNDS,
                current,
                1,
                0,
                Board.standardBoard(),
                assets,
                owners,
                developments,
                new HashSet<>(),
                List.of(),
                null,
                debt,
                Set.of(),
                List.of(),
                null);
    }

    private MonopolyGameState base(
            UUID current,
            MonopolyPhase phase,
            Map<UUID, PlayerAsset> assets,
            Map<Integer, UUID> owners,
            PendingDebt debt,
            Set<UUID> bankrupt) {
        return new MonopolyGameState(
                UUID.randomUUID(),
                phase,
                current,
                1,
                0,
                Board.standardBoard(),
                assets,
                owners,
                new HashMap<>(),
                new HashSet<>(),
                List.of(),
                null,
                debt,
                bankrupt,
                List.of(),
                null);
    }

    private MonopolyAction action(UUID actor, MonopolyActionType type, Integer tile, UUID target, Integer amount) {
        return new MonopolyAction(actor, type, tile, target, amount, Map.of());
    }

    private static final class FixedDie extends Random {
        @Override
        public int nextInt(int origin, int bound) {
            return 1;
        }
    }
}
