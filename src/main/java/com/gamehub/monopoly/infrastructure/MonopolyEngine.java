package com.gamehub.monopoly.infrastructure;

import com.gamehub.ai.infrastructure.ollama.BankerOllamaService;
import com.gamehub.ai.infrastructure.ollama.OllamaDtos;
import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.monopoly.domain.Board;
import com.gamehub.monopoly.domain.BharatCards;
import com.gamehub.monopoly.domain.IndianEvent;
import com.gamehub.monopoly.domain.IndianEvents;
import com.gamehub.monopoly.domain.MonopolyAction;
import com.gamehub.monopoly.domain.MonopolyActionType;
import com.gamehub.monopoly.domain.MonopolyGameState;
import com.gamehub.monopoly.domain.MonopolyPhase;
import com.gamehub.monopoly.domain.PendingDebt;
import com.gamehub.monopoly.domain.PendingSale;
import com.gamehub.monopoly.domain.PlayerAsset;
import com.gamehub.monopoly.domain.Property;
import com.gamehub.monopoly.domain.PropertyDevelopment;
import com.gamehub.monopoly.domain.Railroad;
import com.gamehub.monopoly.domain.SimpleTile;
import com.gamehub.monopoly.domain.Tile;
import com.gamehub.monopoly.domain.TileType;
import com.gamehub.monopoly.domain.Utility;
import com.gamehub.shared.domain.GameEngine;
import com.gamehub.shared.domain.GameType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonopolyEngine implements GameEngine<MonopolyGameState, MonopolyAction> {

    private static final int STARTING_CASH = 15000;
    private static final int GO_BONUS = 2000;
    /** Scaled with Bharat economy (classic 50 × 10). */
    private static final int JAIL_FEE = 500;
    private static final int[] RAILROAD_RENT = {250, 500, 1000, 2000};
    private static final int[] RAILROAD_POSITIONS = {5, 15, 25, 35};
    private static final int[] UTILITY_POSITIONS = {12, 28};

    private Random random = new Random();
    
    @Autowired(required = false)
    private BankerOllamaService bankerOllamaService;

    /** Test hook so a roll can land on a chosen tile. */
    void setRandom(Random random) {
        this.random = random;
    }

    @Override
    public GameType supportedGameType() {
        return GameType.MONOPOLY;
    }

    @Override
    public Class<MonopolyGameState> stateType() {
        return MonopolyGameState.class;
    }

    @Override
    public MonopolyGameState startGame(UUID sessionId, List<UUID> playerIds) {
        Board board = Board.standardBoard();
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>();
        for (UUID playerId : playerIds) {
            assets.put(playerId, new PlayerAsset(playerId, STARTING_CASH, 0, false, 0, new HashSet<>()));
        }
        return new MonopolyGameState(
                sessionId,
                MonopolyPhase.WAITING_FOR_ROLL,
                playerIds.getFirst(),
                1,
                0,
                board,
                assets,
                new HashMap<>(),
                new HashMap<>(),
                new HashSet<>(),
                new ArrayList<>(List.of("Game started")),
                null);
    }

    @Override
    public MonopolyGameState endGame(MonopolyGameState state) {
        return copy(state, MonopolyPhase.ENDED, state.currentPlayerId(), state.currentTurn(), state.lastDiceTotal(), "Game ended");
    }

    @Override
    public MonopolyGameState pauseGame(MonopolyGameState state) {
        return copy(state, MonopolyPhase.PAUSED, state.currentPlayerId(), state.currentTurn(), state.lastDiceTotal(), "Game paused");
    }

    @Override
    public MonopolyGameState resumeGame(MonopolyGameState state) {
        return copy(state, MonopolyPhase.WAITING_FOR_ROLL, state.currentPlayerId(), state.currentTurn(), state.lastDiceTotal(), "Game resumed");
    }

    @Override
    public void validateAction(MonopolyGameState state, MonopolyAction action) {
        if (state.phase() == MonopolyPhase.PAUSED || state.phase() == MonopolyPhase.ENDED) {
            throw new BusinessRuleViolationException("Game is not accepting actions");
        }
        boolean bankAction = action.type() == MonopolyActionType.BANK_ADJUST
                || action.type() == MonopolyActionType.BANK_TRANSFER;
        if (bankAction) {
            return;
        }
        if (state.phase() == MonopolyPhase.RAISING_FUNDS) {
            if (action.type() == MonopolyActionType.ACCEPT_SALE || action.type() == MonopolyActionType.DECLINE_SALE) {
                PendingSale sale = state.pendingSale();
                if (sale == null || !sale.buyerId().equals(action.actorPlayerId())) {
                    throw new BusinessRuleViolationException("Only the buyer can respond to this offer");
                }
                return;
            }
            UUID debtorId = state.pendingDebt() == null ? state.currentPlayerId() : state.pendingDebt().debtorId();
            if (!debtorId.equals(action.actorPlayerId())) {
                throw new BusinessRuleViolationException("It is not this player's turn");
            }
            return;
        }
        if (!state.currentPlayerId().equals(action.actorPlayerId())) {
            throw new BusinessRuleViolationException("It is not this player's turn");
        }
    }

    @Override
    public MonopolyGameState processAction(MonopolyGameState state, MonopolyAction action) {
        validateAction(state, action);
        if (state.phase() == MonopolyPhase.RAISING_FUNDS) {
            return switch (action.type()) {
                case MORTGAGE -> mortgage(state, action);
                case UNMORTGAGE -> unmortgage(state, action);
                case SELL_HOUSE -> sellHouse(state, action);
                case PAY_DEBT -> payDebt(state, action);
                case DECLARE_BANKRUPTCY -> declareBankruptcy(state, action);
                case PROPOSE_SALE -> proposeSale(state, action);
                case ACCEPT_SALE -> acceptSale(state, action);
                case DECLINE_SALE -> declineSale(state, action);
                case BANK_ADJUST -> bankAdjust(state, action);
                case BANK_TRANSFER -> bankTransfer(state, action);
                default -> throw new BusinessRuleViolationException("Resolve the outstanding debt first");
            };
        }
        return switch (action.type()) {
            case ROLL_DICE -> rollDice(state);
            case BUY_PROPERTY -> buyProperty(state);
            case PAY_RENT -> payRent(state, action);
            case MORTGAGE -> mortgage(state, action);
            case UNMORTGAGE -> unmortgage(state, action);
            case BUILD_HOUSE -> buildHouse(state, action);
            case BUILD_HOTEL -> buildHotel(state, action);
            case SELL_HOUSE -> sellHouse(state, action);
            case PAY_JAIL -> payJail(state, action);
            case USE_JAIL_CARD -> useJailCard(state, action);
            case TRADE -> trade(state, action);
            case AUCTION -> auction(state, action);
            case BANK_ADJUST -> bankAdjust(state, action);
            case BANK_TRANSFER -> bankTransfer(state, action);
            case END_TURN -> endTurn(state);
            case PAY_DEBT, DECLARE_BANKRUPTCY, PROPOSE_SALE, ACCEPT_SALE, DECLINE_SALE ->
                    throw new BusinessRuleViolationException("That action is only available while raising funds");
        };
    }

    private MonopolyGameState payJail(MonopolyGameState state, MonopolyAction action) {
        PlayerAsset current = state.assets().get(state.currentPlayerId());
        if (current == null || !current.inJail()) {
            throw new BusinessRuleViolationException("Player not in jail");
        }
        int fee = jailFee(state);
        if (current.cash() < fee) {
            throw new BusinessRuleViolationException("Insufficient funds to pay jail fee");
        }
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        assets.put(current.playerId(), new PlayerAsset(
                current.playerId(),
                current.cash() - fee,
                10,
                false,
                0,
                current.ownedTilePositions()));
        List<String> log = new java.util.ArrayList<>(state.log());
        log.add("Player paid jail fee: " + fee);
        return new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.WAITING_FOR_ROLL,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                log,
                state.activeEvent());
    }

    private MonopolyGameState useJailCard(MonopolyGameState state, MonopolyAction action) {
        PlayerAsset current = state.assets().get(state.currentPlayerId());
        if (current == null || !current.inJail()) {
            throw new BusinessRuleViolationException("Player not in jail");
        }
        // The PlayerAsset currently doesn't track jail cards in persisted model; if present in metadata handle it.
        // For now, allow use if recorded in metadata indicating player has a card.
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        // Since backend PlayerAsset doesn't have jailCards in current model, simply release from jail.
        assets.put(current.playerId(), new PlayerAsset(
                current.playerId(),
                current.cash(),
                current.position(),
                false,
                0,
                current.ownedTilePositions()));
        List<String> log = new java.util.ArrayList<>(state.log());
        log.add("Player used a Get Out of Jail Free card");
        return new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.WAITING_FOR_ROLL,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                log,
                state.activeEvent());
    }

    private MonopolyGameState sellHouse(MonopolyGameState state, MonopolyAction action) {
        int tilePosition = requiredTilePosition(action);
        Tile tile = state.board().tileAt(tilePosition);
        if (!(tile instanceof Property property)) {
            throw new BusinessRuleViolationException("Only properties can sell houses");
        }
        UUID ownerId = state.owners().get(tilePosition);
        UUID actorId = action.actorPlayerId();
        if (ownerId == null || !ownerId.equals(actorId)) {
            throw new BusinessRuleViolationException("Player does not own this property");
        }
        Map<Integer, PropertyDevelopment> developments = new HashMap<>(state.developments());
        PropertyDevelopment current = developments.getOrDefault(tilePosition, new PropertyDevelopment(0, false));
        if (current.houses() <= 0 && !current.hotel()) {
            throw new BusinessRuleViolationException("No houses to sell");
        }
        int refund = property.houseCost() / 2;
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset p = assets.get(actorId);
        assets.put(p.playerId(), withCash(p, p.cash() + refund));
        PropertyDevelopment updatedDev;
        if (current.hotel()) {
            // demote hotel to 4 houses
            updatedDev = new PropertyDevelopment(4, false);
        } else {
            updatedDev = new PropertyDevelopment(current.houses() - 1, false);
        }
        developments.put(tilePosition, updatedDev);
        List<String> log = new java.util.ArrayList<>(state.log());
        log.add((current.hotel() ? "Hotel sold on " : "House sold on ") + tile.name() + " for " + refund);
        return carry(state, decisionPhase(state), assets, new HashMap<>(state.owners()), developments, new HashSet<>(state.mortgagedTiles()), log);
    }

    private MonopolyGameState rollDice(MonopolyGameState state) {
        if (state.phase() != MonopolyPhase.WAITING_FOR_ROLL) {
            throw new BusinessRuleViolationException("The game is not waiting for a roll");
        }
        int dieOne = random.nextInt(1, 7);
        int dieTwo = random.nextInt(1, 7);
        int total = dieOne + dieTwo;
        PlayerAsset currentAsset = state.assets().get(state.currentPlayerId());
        int currentPosition = currentAsset.position();
        int nextPosition = (currentPosition + total) % state.board().tiles().size();
        int cash = currentAsset.cash();
        IndianEvent activeEvent = expireEventIfNeeded(state, state.activeEvent());
        boolean passedGo = currentPosition + total >= state.board().tiles().size();
        int salary = passedGo ? goBonus(activeEvent) : 0;
        if (passedGo) {
            cash += salary;
        }
        Tile landedTile = state.board().tileAt(nextPosition);
        PlayerAsset updatedAsset = new PlayerAsset(
                currentAsset.playerId(),
                cash,
                nextPosition,
                false,
                0,
                new HashSet<>(currentAsset.ownedTilePositions()));

        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        assets.put(updatedAsset.playerId(), updatedAsset);

        List<String> log = new ArrayList<>(state.log());
        log.add("%s rolled %d and landed on %s".formatted(playerLabel(state, state.currentPlayerId()), total, landedTile.name()));
        if (passedGo) {
            log.add("%s passed GO. +%d".formatted(playerLabel(state, state.currentPlayerId()), salary));
        }

        MonopolyPhase phase = MonopolyPhase.WAITING_FOR_DECISION;
        Settlement settlement = new Settlement(state, assets, log, phase);
        if (landedTile instanceof SimpleTile simpleTile) {
            if (simpleTile.tileType() == TileType.TAX) {
                int tax = taxAmount(simpleTile, activeEvent);
                settlement.charge(state.currentPlayerId(), null, tax, "Tax " + simpleTile.name());
            } else if (simpleTile.tileType() == TileType.GO_TO_JAIL) {
                PlayerAsset mover = settlement.asset(updatedAsset.playerId());
                settlement.replace(new PlayerAsset(
                        mover.playerId(),
                        mover.cash(),
                        10,
                        true,
                        1,
                        mover.ownedTilePositions()));
                settlement.log().add("Player sent to jail");
            } else if (simpleTile.tileType() == TileType.CHANCE
                    || simpleTile.tileType() == TileType.COMMUNITY_CHEST) {
                BharatCards.Deck deck = simpleTile.tileType() == TileType.CHANCE
                        ? BharatCards.Deck.CHANCE
                        : BharatCards.Deck.CHEST;
                drawAndApplyCard(state, settlement, updatedAsset.playerId(), deck);
            } else if (simpleTile.tileType() == TileType.FREE_PARKING) {
                int expires = state.currentTurn() + Math.max(4, 2 * state.assets().size());
                String exclude = activeEvent != null ? activeEvent.id() : null;
                activeEvent = exclude != null
                        ? IndianEvents.drawExcluding(exclude, expires)
                        : IndianEvents.draw(expires);
                settlement.log().add("Indian Event: " + activeEvent.title() + " — " + activeEvent.description());
                if ("FLOODS".equals(activeEvent.id()) || "CYCLONE".equals(activeEvent.id())) {
                    settlement.setAssets(payFloodInsurance(state, settlement.assets(), settlement.log()));
                }
            }
        } else if (state.owners().containsKey(landedTile.position())
                && !state.owners().get(landedTile.position()).equals(state.currentPlayerId())
                && !state.mortgagedTiles().contains(landedTile.position())) {
            MonopolyGameState rentState = new MonopolyGameState(
                    state.sessionId(),
                    state.phase(),
                    state.currentPlayerId(),
                    state.currentTurn(),
                    total,
                    state.board(),
                    settlement.assets(),
                    state.owners(),
                    state.developments(),
                    state.mortgagedTiles(),
                    state.log(),
                    activeEvent);
            int rent = rentFor(landedTile, state.developments().get(landedTile.position()), rentState, settlement.assets());
            UUID ownerId = state.owners().get(landedTile.position());
            settlement.charge(state.currentPlayerId(), ownerId, rent, "Rent for " + landedTile.name());
        } else if (landedTile instanceof Property
                && state.currentPlayerId().equals(state.owners().get(landedTile.position()))
                && !state.mortgagedTiles().contains(landedTile.position())) {
            settlement.log().add("Upgrade available on " + landedTile.name());
        }

        return handoverIfIdle(publish(state, settlement, state.currentPlayerId(), state.currentTurn(), total, activeEvent));
    }

    private MonopolyGameState buyProperty(MonopolyGameState state) {
        PlayerAsset asset = state.assets().get(state.currentPlayerId());
        Tile tile = state.board().tileAt(asset.position());
        int price = purchasePrice(tile);
        if (price <= 0) {
            throw new BusinessRuleViolationException("Current tile cannot be purchased");
        }
        if (state.owners().containsKey(tile.position())) {
            throw new BusinessRuleViolationException("Tile already owned");
        }
        if (asset.cash() < price) {
            throw new BusinessRuleViolationException("Insufficient funds");
        }
        
        // Get banker approval for purchase
        OllamaDtos.BankerDecisionResponse bankerDecision = getBankerApproval(
                state,
                "BUY_PROPERTY",
                price,
                "PURCHASE",
                tile.name()
        );
        
        if (bankerDecision != null && !bankerDecision.approved()) {
            log.info("Banker advised against purchase (non-blocking): {}", bankerDecision.reasoning());
        }
        
        Set<Integer> ownedTiles = new HashSet<>(asset.ownedTilePositions());
        ownedTiles.add(tile.position());
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        assets.put(asset.playerId(), new PlayerAsset(
                asset.playerId(),
                asset.cash() - price,
                asset.position(),
                asset.inJail(),
                asset.jailTurns(),
                ownedTiles));
        Map<Integer, UUID> owners = new HashMap<>(state.owners());
        owners.put(tile.position(), asset.playerId());
        List<String> log = new ArrayList<>(state.log());
        String logMsg = "Property purchased: " + tile.name();
        if (bankerDecision != null) {
            logMsg += " (Banker: " + bankerDecision.reasoning() + ")";
        }
        log.add(logMsg);
        return handoverIfIdle(new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.WAITING_FOR_DECISION,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                owners,
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                log,
                state.activeEvent()));
    }

    private MonopolyGameState payRent(MonopolyGameState state, MonopolyAction action) {
        if (action.targetPlayerId() == null || action.amount() == null) {
            throw new BusinessRuleViolationException("PAY_RENT requires target player and amount");
        }
        Settlement settlement = new Settlement(
                state,
                new LinkedHashMap<>(state.assets()),
                new ArrayList<>(state.log()),
                MonopolyPhase.WAITING_FOR_DECISION);
        settlement.charge(state.currentPlayerId(), action.targetPlayerId(), action.amount(), "Rent");
        return publish(state, settlement, state.currentPlayerId(), state.currentTurn(), state.lastDiceTotal(), state.activeEvent());
    }

    private MonopolyGameState mortgage(MonopolyGameState state, MonopolyAction action) {
        int tilePosition = requiredTilePosition(action);
        UUID actorId = action.actorPlayerId();
        if (!ownsTile(state, actorId, tilePosition)) {
            throw new BusinessRuleViolationException("Player does not own this tile");
        }
        if (state.mortgagedTiles().contains(tilePosition)) {
            throw new BusinessRuleViolationException("Tile already mortgaged");
        }
        PropertyDevelopment development = state.developments().getOrDefault(tilePosition, new PropertyDevelopment(0, false));
        if (development.hotel() || development.houses() > 0) {
            throw new BusinessRuleViolationException("Sell developments before mortgaging");
        }
        Tile tile = state.board().tileAt(tilePosition);
        int amount = purchasePrice(tile) / 2;

        String advice = getBankerPropertyAdvice(state, tile.name(), amount, false);

        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset asset = assets.get(actorId);
        assets.put(asset.playerId(), withCash(asset, asset.cash() + amount));
        Set<Integer> mortgagedTiles = new HashSet<>(state.mortgagedTiles());
        mortgagedTiles.add(tilePosition);
        List<String> log = new ArrayList<>(state.log());
        log.add("Mortgage placed on " + tile.name() + " for " + amount + " (Banker: " + advice + ")");
        return carry(state, decisionPhase(state), assets, new HashMap<>(state.owners()), new HashMap<>(state.developments()), mortgagedTiles, log);
    }

    private MonopolyGameState unmortgage(MonopolyGameState state, MonopolyAction action) {
        int tilePosition = requiredTilePosition(action);
        if (!state.mortgagedTiles().contains(tilePosition)) {
            throw new BusinessRuleViolationException("Tile is not mortgaged");
        }
        if (!ownsTile(state, action.actorPlayerId(), tilePosition)) {
            throw new BusinessRuleViolationException("Player does not own this tile");
        }
        Tile tile = state.board().tileAt(tilePosition);
        int cost = unmortgageCost(state, tile);
        
        // Get banker approval for unmortgage
        OllamaDtos.BankerDecisionResponse bankerDecision = getBankerApproval(
                state,
                "UNMORTGAGE",
                cost,
                "UNMORTGAGE",
                tile.name()
        );
        
        if (bankerDecision != null && !bankerDecision.approved()) {
            log.info("Banker advised against unmortgage: {}", bankerDecision.reasoning());
        }
        
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset asset = assets.get(action.actorPlayerId());
        if (asset.cash() < cost) {
            throw new BusinessRuleViolationException("Insufficient funds");
        }
        assets.put(asset.playerId(), withCash(asset, asset.cash() - cost));
        Set<Integer> mortgagedTiles = new HashSet<>(state.mortgagedTiles());
        mortgagedTiles.remove(tilePosition);
        List<String> log = new ArrayList<>(state.log());
        String logMsg = "Mortgage cleared on " + tile.name();
        if (bankerDecision != null) {
            logMsg += " (Banker: " + bankerDecision.reasoning() + ")";
        }
        log.add(logMsg);
        return carry(state, decisionPhase(state), assets, new HashMap<>(state.owners()), new HashMap<>(state.developments()), mortgagedTiles, log);
    }

    private MonopolyGameState buildHouse(MonopolyGameState state, MonopolyAction action) {
        int tilePosition = requiredTilePosition(action);
        Tile tile = state.board().tileAt(tilePosition);
        if (!(tile instanceof Property property)) {
            throw new BusinessRuleViolationException("Only properties can have houses");
        }
        if (!ownsTile(state, state.currentPlayerId(), tilePosition)) {
            throw new BusinessRuleViolationException("Player does not own this property");
        }
        if (state.mortgagedTiles().contains(tilePosition)) {
            throw new BusinessRuleViolationException("Cannot upgrade a mortgaged property");
        }
        PlayerAsset standing = state.assets().get(state.currentPlayerId());
        if (standing == null
                || standing.position() != tilePosition
                || !upgradeOfferedThisVisit(state, property.name())) {
            throw new BusinessRuleViolationException("Land on this property again to upgrade it");
        }
        Map<Integer, PropertyDevelopment> developments = new HashMap<>(state.developments());
        PropertyDevelopment current = developments.getOrDefault(tilePosition, new PropertyDevelopment(0, false));
        if (current.hotel() || current.houses() >= 4) {
            throw new BusinessRuleViolationException("Property cannot build more houses");
        }
        
        // Get banker advice on building
        int buildCost = upgradeCost(state, property);
        String advice = getBankerPropertyAdvice(state, property.name(), buildCost, true);
        
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset asset = assets.get(state.currentPlayerId());
        if (asset.cash() < buildCost) {
            throw new BusinessRuleViolationException("Insufficient funds");
        }
        assets.put(asset.playerId(), new PlayerAsset(
                asset.playerId(),
                asset.cash() - buildCost,
                asset.position(),
                asset.inJail(),
                asset.jailTurns(),
                asset.ownedTilePositions()));
        developments.put(tilePosition, new PropertyDevelopment(current.houses() + 1, false));
        List<String> log = new ArrayList<>(state.log());
        log.add("House built on " + property.name() + " for " + buildCost + " (Banker: " + advice + ")");
        return handoverIfIdle(new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.WAITING_FOR_DECISION,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                new HashMap<>(state.owners()),
                developments,
                new HashSet<>(state.mortgagedTiles()),
                log,
                state.activeEvent()));
    }

    private MonopolyGameState buildHotel(MonopolyGameState state, MonopolyAction action) {
        int tilePosition = requiredTilePosition(action);
        Tile tile = state.board().tileAt(tilePosition);
        if (!(tile instanceof Property property)) {
            throw new BusinessRuleViolationException("Only properties can have hotels");
        }
        if (!ownsTile(state, state.currentPlayerId(), tilePosition)) {
            throw new BusinessRuleViolationException("Player does not own this property");
        }
        if (state.mortgagedTiles().contains(tilePosition)) {
            throw new BusinessRuleViolationException("Cannot upgrade a mortgaged property");
        }
        PlayerAsset standing = state.assets().get(state.currentPlayerId());
        if (standing == null
                || standing.position() != tilePosition
                || !upgradeOfferedThisVisit(state, property.name())) {
            throw new BusinessRuleViolationException("Land on this property again to upgrade it");
        }
        PropertyDevelopment development = state.developments().getOrDefault(tilePosition, new PropertyDevelopment(0, false));
        if (development.houses() < 4 || development.hotel()) {
            throw new BusinessRuleViolationException("Property is not ready for a hotel");
        }
        
        // Get banker advice on building hotel
        String advice = getBankerPropertyAdvice(state, property.name(), property.houseCost(), true);
        
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset asset = assets.get(state.currentPlayerId());
        if (asset.cash() < property.houseCost()) {
            throw new BusinessRuleViolationException("Insufficient funds");
        }
        assets.put(asset.playerId(), new PlayerAsset(
                asset.playerId(),
                asset.cash() - property.houseCost(),
                asset.position(),
                asset.inJail(),
                asset.jailTurns(),
                asset.ownedTilePositions()));
        Map<Integer, PropertyDevelopment> developments = new HashMap<>(state.developments());
        developments.put(tilePosition, new PropertyDevelopment(4, true));
        List<String> log = new ArrayList<>(state.log());
        log.add("Hotel built on " + tile.name() + " (Banker: " + advice + ")");
        return handoverIfIdle(new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.WAITING_FOR_DECISION,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                new HashMap<>(state.owners()),
                developments,
                new HashSet<>(state.mortgagedTiles()),
                log,
                state.activeEvent()));
    }

    private MonopolyGameState trade(MonopolyGameState state, MonopolyAction action) {
        if (action.targetPlayerId() == null) {
            throw new BusinessRuleViolationException("TRADE requires target player");
        }
        int offeredTile = Integer.parseInt(action.metadata().getOrDefault("offeredTile", "-1"));
        int requestedTile = Integer.parseInt(action.metadata().getOrDefault("requestedTile", "-1"));
        int cash = action.amount() == null ? 0 : action.amount();
        if (!ownsTile(state, state.currentPlayerId(), offeredTile) || !ownsTile(state, action.targetPlayerId(), requestedTile)) {
            throw new BusinessRuleViolationException("Trade ownership validation failed");
        }
        if (cash < 0) {
            throw new BusinessRuleViolationException("Trade cash amount cannot be negative");
        }

        Map<Integer, UUID> owners = new HashMap<>(state.owners());
        owners.put(offeredTile, action.targetPlayerId());
        owners.put(requestedTile, state.currentPlayerId());

        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset source = assets.get(state.currentPlayerId());
        PlayerAsset target = assets.get(action.targetPlayerId());
        if (source == null || target == null) {
            throw new BusinessRuleViolationException("Trade players not found");
        }
        if (source.cash() < cash) {
            throw new BusinessRuleViolationException("Insufficient funds for trade");
        }

        Set<Integer> sourceTiles = new HashSet<>(source.ownedTilePositions());
        Set<Integer> targetTiles = new HashSet<>(target.ownedTilePositions());
        sourceTiles.remove(offeredTile);
        sourceTiles.add(requestedTile);
        targetTiles.remove(requestedTile);
        targetTiles.add(offeredTile);

        assets.put(source.playerId(), new PlayerAsset(
                source.playerId(),
                source.cash() - cash,
                source.position(),
                source.inJail(),
                source.jailTurns(),
                sourceTiles));
        assets.put(target.playerId(), new PlayerAsset(
                target.playerId(),
                target.cash() + cash,
                target.position(),
                target.inJail(),
                target.jailTurns(),
                targetTiles));
        return handoverIfIdle(new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.WAITING_FOR_DECISION,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                owners,
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                append(state.log(), "Trade completed"),
                state.activeEvent()));
    }

    private MonopolyGameState auction(MonopolyGameState state, MonopolyAction action) {
        int tilePosition = requiredTilePosition(action);
        if (action.targetPlayerId() == null || action.amount() == null) {
            throw new BusinessRuleViolationException("AUCTION requires a winner and bid amount");
        }
        Tile tile = state.board().tileAt(tilePosition);
        if (state.owners().containsKey(tilePosition)) {
            throw new BusinessRuleViolationException("Tile already owned");
        }
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset winner = assets.get(action.targetPlayerId());
        if (winner == null) {
            winner = assets.values().stream()
                    .filter(asset -> action.targetPlayerId().equals(asset.playerId()))
                    .findFirst()
                    .orElse(null);
        }
        if (winner == null) {
            throw new BusinessRuleViolationException("Winning bidder not found");
        }
        if (winner.cash() < action.amount()) {
            throw new BusinessRuleViolationException("Winning bidder lacks cash");
        }
        Set<Integer> owned = new HashSet<>(winner.ownedTilePositions());
        owned.add(tilePosition);
        assets.put(winner.playerId(), new PlayerAsset(
                winner.playerId(),
                winner.cash() - action.amount(),
                winner.position(),
                winner.inJail(),
                winner.jailTurns(),
                owned));
        Map<Integer, UUID> owners = new HashMap<>(state.owners());
        owners.put(tilePosition, winner.playerId());
        return handoverIfIdle(new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.WAITING_FOR_DECISION,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                owners,
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                append(state.log(), "Auction won for " + tile.name()),
                state.activeEvent()));
    }

    /** Passes the turn after a buy, auction, upgrade, or landing that needs no further choice. */
    public MonopolyGameState handOver(MonopolyGameState state) {
        return endTurn(state);
    }

    /**
     * Ends the turn when the current player has nothing left to choose.
     * A purchase or a city upgrade keeps the turn open.
     */
    private MonopolyGameState handoverIfIdle(MonopolyGameState state) {
        if (state.phase() != MonopolyPhase.WAITING_FOR_DECISION) {
            return state;
        }
        if (awaitsPurchase(state) || awaitsUpgrade(state)) {
            return state;
        }
        return endTurn(state);
    }

    private boolean awaitsPurchase(MonopolyGameState state) {
        PlayerAsset asset = state.assets().get(state.currentPlayerId());
        if (asset == null) {
            return false;
        }
        Tile tile = state.board().tileAt(asset.position());
        return purchasePrice(tile) > 0 && !state.owners().containsKey(tile.position());
    }

    private boolean awaitsUpgrade(MonopolyGameState state) {
        PlayerAsset asset = state.assets().get(state.currentPlayerId());
        if (asset == null) {
            return false;
        }
        Tile tile = state.board().tileAt(asset.position());
        if (!(tile instanceof Property property)) {
            return false;
        }
        if (!state.currentPlayerId().equals(state.owners().get(tile.position()))) {
            return false;
        }
        if (state.mortgagedTiles().contains(tile.position())) {
            return false;
        }
        PropertyDevelopment development = state.developments().get(tile.position());
        if (development != null && (development.hotel() || development.houses() >= 5)) {
            return false;
        }
        return upgradeOfferedThisVisit(state, property.name());
    }

    private MonopolyGameState endTurn(MonopolyGameState state) {
        if (state.phase() == MonopolyPhase.RAISING_FUNDS) {
            throw new BusinessRuleViolationException("Resolve the outstanding debt first");
        }
        if (state.phase() != MonopolyPhase.WAITING_FOR_DECISION) {
            return state;
        }
        List<UUID> players = new ArrayList<>(state.assets().keySet());
        UUID nextPlayer = nextSolventPlayer(state, state.currentPlayerId());
        if (nextPlayer == null) {
            return finishGame(state, state.currentPlayerId(), append(state.log(), "Turn ended"));
        }
        int nextTurn = state.currentTurn() + 1;
        IndianEvent event = state.activeEvent();
        if (event != null && nextTurn > event.expiresOnTurn()) {
            event = null;
        }
        long solvent = players.stream().filter(id -> !state.bankruptPlayerIds().contains(id)).count();
        if (solvent <= 1) {
            return finishGame(state, nextPlayer, append(state.log(), "Turn ended"));
        }
        return new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.WAITING_FOR_ROLL,
                nextPlayer,
                nextTurn,
                state.lastDiceTotal(),
                state.board(),
                new LinkedHashMap<>(state.assets()),
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                append(state.log(), "Turn ended"),
                event,
                null,
                state.bankruptPlayerIds(),
                List.of(),
                null);
    }

    private MonopolyGameState bankAdjust(MonopolyGameState state, MonopolyAction action) {
        if (action.targetPlayerId() == null || action.amount() == null || action.amount() == 0) {
            throw new BusinessRuleViolationException("BANK_ADJUST requires target player and non-zero amount");
        }
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset target = assets.get(action.targetPlayerId());
        if (target == null) {
            throw new BusinessRuleViolationException("Player not found");
        }
        int nextCash = target.cash() + action.amount();
        if (nextCash < 0) {
            throw new BusinessRuleViolationException("Insufficient funds");
        }
        assets.put(target.playerId(), new PlayerAsset(
                target.playerId(),
                nextCash,
                target.position(),
                target.inJail(),
                target.jailTurns(),
                target.ownedTilePositions()));
        String signed = action.amount() > 0 ? "+" + action.amount() : String.valueOf(action.amount());
        return carry(
                state,
                state.phase(),
                assets,
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                append(state.log(), "Bank: " + signed + " to " + playerLabel(state, target.playerId())));
    }

    private MonopolyGameState bankTransfer(MonopolyGameState state, MonopolyAction action) {
        if (action.targetPlayerId() == null || action.amount() == null || action.amount() <= 0) {
            throw new BusinessRuleViolationException("BANK_TRANSFER requires target player and positive amount");
        }
        String fromRaw = action.metadata() == null ? null : action.metadata().get("fromPlayerId");
        if (fromRaw == null || fromRaw.isBlank()) {
            throw new BusinessRuleViolationException("BANK_TRANSFER requires fromPlayerId");
        }
        UUID fromId;
        try {
            fromId = UUID.fromString(fromRaw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("Invalid fromPlayerId");
        }
        if (fromId.equals(action.targetPlayerId())) {
            throw new BusinessRuleViolationException("Cannot transfer cash to the same player");
        }
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset from = assets.get(fromId);
        PlayerAsset to = assets.get(action.targetPlayerId());
        if (from == null || to == null) {
            throw new BusinessRuleViolationException("Player not found");
        }
        if (from.cash() < action.amount()) {
            throw new BusinessRuleViolationException("Insufficient funds");
        }
        assets.put(from.playerId(), new PlayerAsset(
                from.playerId(),
                from.cash() - action.amount(),
                from.position(),
                from.inJail(),
                from.jailTurns(),
                from.ownedTilePositions()));
        assets.put(to.playerId(), new PlayerAsset(
                to.playerId(),
                to.cash() + action.amount(),
                to.position(),
                to.inJail(),
                to.jailTurns(),
                to.ownedTilePositions()));
        return carry(
                state,
                state.phase(),
                assets,
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                append(
                        state.log(),
                        "Bank transfer: "
                                + action.amount()
                                + " from "
                                + playerLabel(state, fromId)
                                + " to "
                                + playerLabel(state, action.targetPlayerId())));
    }

    private String playerLabel(MonopolyGameState state, UUID playerId) {
        if (playerId == null) {
            return "Unknown";
        }
        // UUID is rewritten to display name in MonopolyGameService before persist/response.
        return playerId.toString();
    }

    private MonopolyGameState transfer(MonopolyGameState state, UUID fromPlayerId, UUID toPlayerId, int amount, String message) {
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset from = assets.get(fromPlayerId);
        PlayerAsset to = assets.get(toPlayerId);
        if (from.cash() < amount) {
            throw new BusinessRuleViolationException("Insufficient funds");
        }
        assets.put(from.playerId(), new PlayerAsset(
                from.playerId(),
                from.cash() - amount,
                from.position(),
                from.inJail(),
                from.jailTurns(),
                from.ownedTilePositions()));
        assets.put(to.playerId(), new PlayerAsset(
                to.playerId(),
                to.cash() + amount,
                to.position(),
                to.inJail(),
                to.jailTurns(),
                to.ownedTilePositions()));
        return new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.WAITING_FOR_DECISION,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                append(state.log(), message),
                state.activeEvent());
    }

    private boolean ownsTile(MonopolyGameState state, UUID playerId, int tilePosition) {
        return playerId.equals(state.owners().get(tilePosition));
    }

    /** One upgrade per visit, only after landing again on a city the player already owns. */
    private boolean upgradeOfferedThisVisit(MonopolyGameState state, String tileName) {
        String marker = "Upgrade available on " + tileName;
        String built = "House built on " + tileName;
        String hotel = "Hotel built on " + tileName;
        List<String> log = state.log();
        for (int i = log.size() - 1; i >= 0; i--) {
            String line = log.get(i);
            if (line.startsWith("Turn ended")) return false;
            if (line.startsWith(built) || line.startsWith(hotel)) return false;
            if (line.equals(marker)) return true;
        }
        return false;
    }

    private void markUpgradeIfRevisited(MonopolyGameState state, UUID playerId, int position, List<String> log) {
        Tile tile = state.board().tileAt(position);
        if (!(tile instanceof Property property)) return;
        if (!ownsTile(state, playerId, position)) return;
        if (state.mortgagedTiles().contains(position)) return;
        log.add("Upgrade available on " + property.name());
    }

    private int purchasePrice(Tile tile) {
        return switch (tile) {
            case Property property -> property.purchasePrice();
            case Railroad railroad -> railroad.purchasePrice();
            case Utility utility -> utility.purchasePrice();
            default -> 0;
        };
    }

    private int rentFor(
            Tile tile,
            PropertyDevelopment development,
            MonopolyGameState state,
            Map<UUID, PlayerAsset> assets) {
        int base = switch (tile) {
            case Property property -> {
                if (development == null) {
                    yield property.baseRent();
                }
                if (development.hotel()) {
                    yield property.baseRent() * 10;
                }
                yield property.baseRent() * Math.max(1, development.houses() + 1);
            }
            case Railroad railroad -> {
                UUID ownerId = state.owners().get(railroad.position());
                int owned = countOwnedRailroads(state, ownerId);
                yield RAILROAD_RENT[Math.max(0, Math.min(owned, 4) - 1)];
            }
            case Utility utility -> {
                UUID ownerId = state.owners().get(utility.position());
                int owned = countOwnedUtilities(state, ownerId);
                int dice = Math.max(1, state.lastDiceTotal() > 0 ? state.lastDiceTotal() : random.nextInt(1, 7) + random.nextInt(1, 7));
                IndianEvent event = currentEvent(state);
                if (event != null && "CYCLONE".equals(event.id())) {
                    yield dice * 2;
                }
                yield owned >= 2 ? dice * 10 : dice * 4;
            }
            default -> 0;
        };
        return applyEventRentModifier(state, tile, base);
    }

    private int applyEventRentModifier(MonopolyGameState state, Tile tile, int rent) {
        IndianEvent event = currentEvent(state);
        if (event == null || rent <= 0) return rent;
        return switch (event.id()) {
            case "ECONOMIC_BOOM" -> tile instanceof Property ? (int) Math.round(rent * 1.5) : rent;
            case "ECONOMIC_RECESSION" -> tile instanceof Property ? Math.max(0, rent / 2) : rent;
            case "FESTIVAL_SEASON" -> tile instanceof Railroad ? rent * 2 : rent;
            case "TOURISM_SEASON" -> {
                if (tile instanceof Property p) {
                    String g = p.colorGroup();
                    yield ("LIGHT_BLUE".equals(g) || "YELLOW".equals(g)) ? rent * 2 : rent;
                }
                yield rent;
            }
            case "FLOODS", "CYCLONE" -> {
                if (tile instanceof Property p && "YELLOW".equals(p.colorGroup())) yield 0;
                yield rent;
            }
            case "STARTUP_WAVE" -> {
                int pos = tile.position();
                yield (pos == 24 || pos == 34) ? rent * 2 : rent;
            }
            default -> rent;
        };
    }

    private IndianEvent currentEvent(MonopolyGameState state) {
        return expireEventIfNeeded(state, state.activeEvent());
    }

    private IndianEvent expireEventIfNeeded(MonopolyGameState state, IndianEvent event) {
        if (event == null) return null;
        // Expire after the scheduled turn (checked on endTurn / land)
        if (state.currentTurn() > event.expiresOnTurn()) return null;
        return event;
    }

    private int goBonus(IndianEvent event) {
        int bonus = GO_BONUS;
        if (event != null && "BUDGET_ANNOUNCEMENT".equals(event.id())) bonus = 3000;
        if (event != null && "FESTIVAL_SEASON".equals(event.id())) bonus += 500;
        return bonus;
    }

    private int jailFee(MonopolyGameState state) {
        IndianEvent event = currentEvent(state);
        if (event != null && "IPL_SEASON".equals(event.id())) return 250;
        return JAIL_FEE;
    }

    private int taxAmount(SimpleTile tile, IndianEvent event) {
        if (event != null && "BUDGET_ANNOUNCEMENT".equals(event.id()) && tile.position() == 4) {
            return 3000;
        }
        return tile.value();
    }

    private int upgradeCost(MonopolyGameState state, Property property) {
        int cost = property.houseCost();
        IndianEvent event = currentEvent(state);
        if (event == null) return cost;
        if ("ECONOMIC_RECESSION".equals(event.id())) {
            return (int) Math.ceil(cost * 1.25);
        }
        if ("METRO_EXPANSION".equals(event.id())) {
            String g = property.colorGroup();
            if ("RED".equals(g) || "GREEN".equals(g)) {
                return Math.max(1, cost / 2);
            }
        }
        return cost;
    }

    private int unmortgageCost(MonopolyGameState state, Tile tile) {
        int mortgage = purchasePrice(tile) / 2;
        IndianEvent event = currentEvent(state);
        if (event != null && "REAL_ESTATE_BOOM".equals(event.id())) {
            return mortgage;
        }
        return (int) Math.ceil(mortgage * 1.1);
    }

    private Map<UUID, PlayerAsset> payFloodInsurance(
            MonopolyGameState state,
            Map<UUID, PlayerAsset> assets,
            List<String> log) {
        int[] yellow = {26, 27, 29};
        Map<UUID, Integer> payouts = new HashMap<>();
        for (int pos : yellow) {
            UUID ownerId = state.owners().get(pos);
            if (ownerId != null) {
                payouts.merge(ownerId, 200, Integer::sum);
            }
        }
        Map<UUID, PlayerAsset> next = new LinkedHashMap<>(assets);
        payouts.forEach((ownerId, amount) -> {
            PlayerAsset a = next.get(ownerId);
            if (a == null) return;
            next.put(ownerId, new PlayerAsset(
                    a.playerId(),
                    a.cash() + amount,
                    a.position(),
                    a.inJail(),
                    a.jailTurns(),
                    a.ownedTilePositions()));
            log.add("Flood insurance ₹" + amount + " to " + ownerId.toString());
        });
        return next;
    }

    private int countOwnedRailroads(MonopolyGameState state, UUID ownerId) {
        if (ownerId == null) return 0;
        int count = 0;
        for (int pos : RAILROAD_POSITIONS) {
            if (ownerId.equals(state.owners().get(pos))) count++;
        }
        return count;
    }

    private int countOwnedUtilities(MonopolyGameState state, UUID ownerId) {
        if (ownerId == null) return 0;
        int count = 0;
        for (int pos : UTILITY_POSITIONS) {
            if (ownerId.equals(state.owners().get(pos))) count++;
        }
        return count;
    }

    private void drawAndApplyCard(
            MonopolyGameState state,
            Settlement settlement,
            UUID playerId,
            BharatCards.Deck deckType) {
        BharatCards.Card[] deck = deckType == BharatCards.Deck.CHANCE ? BharatCards.CHANCE : BharatCards.CHEST;
        BharatCards.Card card = deck[random.nextInt(deck.length)];
        settlement.log().add("Card (" + deckType.name() + "): " + card.text());
        PlayerAsset player = settlement.asset(playerId);

        switch (card.kind()) {
            case MONEY -> {
                if (card.amount() >= 0) {
                    settlement.replace(withCash(player, player.cash() + card.amount()));
                } else {
                    settlement.charge(playerId, null, -card.amount(), "Card");
                }
            }
            case MONEY_FROM_EACH -> {
                int amt = card.amount();
                for (UUID otherId : new ArrayList<>(settlement.assets().keySet())) {
                    if (otherId.equals(playerId) || state.bankruptPlayerIds().contains(otherId)) {
                        continue;
                    }
                    if (amt >= 0) {
                        settlement.charge(otherId, playerId, amt, "Card");
                    } else {
                        settlement.charge(playerId, otherId, -amt, "Card");
                    }
                }
            }
            case MOVE -> {
                int from = player.position();
                int to = card.to();
                int cash = player.cash();
                if (card.collectGoIfPass() && to < from) {
                    cash += GO_BONUS;
                }
                settlement.replace(new PlayerAsset(player.playerId(), cash, to, false, 0, player.ownedTilePositions()));
                settlement.log().add("Moved to " + state.board().tileAt(to).name());
                Tile dest = state.board().tileAt(to);
                if (state.owners().containsKey(to)
                        && !state.owners().get(to).equals(playerId)
                        && !state.mortgagedTiles().contains(to)) {
                    int rent = rentFor(dest, state.developments().get(to), state, settlement.assets());
                    settlement.charge(playerId, state.owners().get(to), rent, "Rent for " + dest.name());
                } else {
                    markUpgradeIfRevisited(state, playerId, to, settlement.log());
                }
            }
            case MOVE_REL -> {
                int boardSize = state.board().tiles().size();
                int nextPos = ((player.position() + card.amount()) % boardSize + boardSize) % boardSize;
                settlement.replace(new PlayerAsset(
                        player.playerId(), player.cash(), nextPos, false, 0, player.ownedTilePositions()));
                settlement.log().add("Moved to " + state.board().tileAt(nextPos).name());
                markUpgradeIfRevisited(state, playerId, nextPos, settlement.log());
            }
            case JAIL -> {
                settlement.replace(new PlayerAsset(player.playerId(), player.cash(), 10, true, 1, player.ownedTilePositions()));
                settlement.log().add("Player sent to jail by card");
            }
            case GET_OUT -> settlement.log().add("Get Out of Jail Free held (use via jail card action)");
            case NEAREST_RAILROAD -> {
                int nextPos = nearestAhead(player.position(), RAILROAD_POSITIONS, state.board().tiles().size());
                int cash = player.cash();
                if (nextPos < player.position()) {
                    cash += GO_BONUS;
                }
                settlement.replace(new PlayerAsset(player.playerId(), cash, nextPos, false, 0, player.ownedTilePositions()));
                settlement.log().add("Advanced to nearest railway: " + state.board().tileAt(nextPos).name());
                Tile dest = state.board().tileAt(nextPos);
                if (state.owners().containsKey(nextPos)
                        && !playerId.equals(state.owners().get(nextPos))
                        && !state.mortgagedTiles().contains(nextPos)) {
                    int rent = rentFor(dest, null, state, settlement.assets()) * 2;
                    settlement.charge(playerId, state.owners().get(nextPos), rent, "Double railway rent for " + dest.name());
                }
            }
            case NEAREST_UTILITY -> {
                int nextPos = nearestAhead(player.position(), UTILITY_POSITIONS, state.board().tiles().size());
                int cash = player.cash();
                if (nextPos < player.position()) {
                    cash += GO_BONUS;
                }
                settlement.replace(new PlayerAsset(player.playerId(), cash, nextPos, false, 0, player.ownedTilePositions()));
                settlement.log().add("Advanced to nearest utility: " + state.board().tileAt(nextPos).name());
            }
            case REPAIRS -> {
                int cost = 0;
                for (int pos : player.ownedTilePositions()) {
                    PropertyDevelopment dev = state.developments().get(pos);
                    if (dev == null) {
                        continue;
                    }
                    if (dev.hotel()) {
                        cost += card.perHotel();
                    } else {
                        cost += card.perHouse() * Math.max(0, dev.houses());
                    }
                }
                settlement.charge(playerId, null, cost, "Repairs");
            }
        }
    }

    private int nearestAhead(int from, int[] targets, int boardSize) {
        int best = targets[0];
        int bestSteps = Integer.MAX_VALUE;
        for (int t : targets) {
            int steps = (t - from + boardSize) % boardSize;
            if (steps == 0) steps = boardSize;
            if (steps < bestSteps) {
                bestSteps = steps;
                best = t;
            }
        }
        return best;
    }

    private int requiredTilePosition(MonopolyAction action) {
        if (action.tilePosition() == null) {
            throw new BusinessRuleViolationException("Action requires tilePosition");
        }
        return action.tilePosition();
    }

    private MonopolyGameState payDebt(MonopolyGameState state, MonopolyAction action) {
        PendingDebt debt = state.pendingDebt();
        if (state.phase() != MonopolyPhase.RAISING_FUNDS || debt == null) {
            throw new BusinessRuleViolationException("There is no debt to pay");
        }
        if (!debt.debtorId().equals(action.actorPlayerId())) {
            throw new BusinessRuleViolationException("Only the debtor can pay this bill");
        }
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset debtor = assets.get(debt.debtorId());
        if (debtor == null || debtor.cash() < debt.amount()) {
            throw new BusinessRuleViolationException("Insufficient funds");
        }
        assets.put(debtor.playerId(), withCash(debtor, debtor.cash() - debt.amount()));
        credit(assets, debt.creditorId(), debt.amount(), state.bankruptPlayerIds());
        List<String> log = new ArrayList<>(state.log());
        log.add(playerLabel(state, debt.debtorId()) + " paid " + debt.amount() + " for " + debt.reason());
        return continueAfterDebt(
                state,
                assets,
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                log,
                state.bankruptPlayerIds(),
                new ArrayList<>(state.debtQueue()),
                state.pendingSale());
    }

    private MonopolyGameState declareBankruptcy(MonopolyGameState state, MonopolyAction action) {
        PendingDebt debt = state.pendingDebt();
        if (state.phase() != MonopolyPhase.RAISING_FUNDS || debt == null) {
            throw new BusinessRuleViolationException("There is no debt to settle");
        }
        if (!debt.debtorId().equals(action.actorPlayerId())) {
            throw new BusinessRuleViolationException("Only the debtor can declare bankruptcy");
        }
        UUID debtorId = debt.debtorId();
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        Map<Integer, UUID> owners = new HashMap<>(state.owners());
        Map<Integer, PropertyDevelopment> developments = new HashMap<>(state.developments());
        Set<Integer> mortgaged = new HashSet<>(state.mortgagedTiles());
        PlayerAsset debtor = assets.get(debtorId);
        Set<Integer> tiles = debtor == null ? Set.of() : new HashSet<>(debtor.ownedTilePositions());
        boolean creditorTakes = debt.creditorId() != null
                && assets.containsKey(debt.creditorId())
                && !state.bankruptPlayerIds().contains(debt.creditorId());
        if (creditorTakes) {
            PlayerAsset creditor = assets.get(debt.creditorId());
            Set<Integer> owned = new HashSet<>(creditor.ownedTilePositions());
            owned.addAll(tiles);
            assets.put(creditor.playerId(), new PlayerAsset(
                    creditor.playerId(),
                    creditor.cash() + (debtor == null ? 0 : debtor.cash()),
                    creditor.position(),
                    creditor.inJail(),
                    creditor.jailTurns(),
                    owned));
            for (int pos : tiles) {
                owners.put(pos, debt.creditorId());
                developments.remove(pos);
            }
        } else {
            for (int pos : tiles) {
                owners.remove(pos);
                developments.remove(pos);
                mortgaged.remove(pos);
            }
        }
        if (debtor != null) {
            assets.put(debtorId, new PlayerAsset(debtorId, 0, debtor.position(), false, 0, new HashSet<>()));
        }
        Set<UUID> bankrupt = new HashSet<>(state.bankruptPlayerIds());
        bankrupt.add(debtorId);
        List<String> log = new ArrayList<>(state.log());
        log.add(playerLabel(state, debtorId) + " is bankrupt");
        PendingSale sale = state.pendingSale();
        if (sale != null && (debtorId.equals(sale.sellerId()) || debtorId.equals(sale.buyerId()))) {
            sale = null;
        }
        List<PendingDebt> queue = new ArrayList<>();
        for (PendingDebt queued : state.debtQueue()) {
            if (!debtorId.equals(queued.debtorId())) {
                queue.add(queued);
            }
        }
        return continueAfterDebt(state, assets, owners, developments, mortgaged, log, bankrupt, queue, sale);
    }

    private MonopolyGameState proposeSale(MonopolyGameState state, MonopolyAction action) {
        PendingDebt debt = state.pendingDebt();
        if (debt == null || !debt.debtorId().equals(action.actorPlayerId())) {
            throw new BusinessRuleViolationException("Only the debtor can offer a property");
        }
        if (state.pendingSale() != null) {
            throw new BusinessRuleViolationException("A sale offer is already open");
        }
        int tilePosition = requiredTilePosition(action);
        if (!ownsTile(state, action.actorPlayerId(), tilePosition)) {
            throw new BusinessRuleViolationException("Player does not own this tile");
        }
        if (action.targetPlayerId() == null || action.amount() == null || action.amount() <= 0) {
            throw new BusinessRuleViolationException("Sale requires a buyer and a price");
        }
        if (action.targetPlayerId().equals(action.actorPlayerId())
                || !state.assets().containsKey(action.targetPlayerId())
                || state.bankruptPlayerIds().contains(action.targetPlayerId())) {
            throw new BusinessRuleViolationException("Choose another player as the buyer");
        }
        PendingSale sale = new PendingSale(action.actorPlayerId(), action.targetPlayerId(), tilePosition, action.amount());
        List<String> log = append(
                state.log(),
                "Offered " + state.board().tileAt(tilePosition).name() + " to "
                        + playerLabel(state, action.targetPlayerId()) + " for " + action.amount());
        return new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.RAISING_FUNDS,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                new LinkedHashMap<>(state.assets()),
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                log,
                state.activeEvent(),
                state.pendingDebt(),
                state.bankruptPlayerIds(),
                state.debtQueue(),
                sale);
    }

    private MonopolyGameState acceptSale(MonopolyGameState state, MonopolyAction action) {
        PendingSale sale = state.pendingSale();
        if (sale == null || !sale.buyerId().equals(action.actorPlayerId())) {
            throw new BusinessRuleViolationException("There is no offer to accept");
        }
        if (!ownsTile(state, sale.sellerId(), sale.tilePosition())) {
            throw new BusinessRuleViolationException("The seller no longer owns this tile");
        }
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset buyer = assets.get(sale.buyerId());
        PlayerAsset seller = assets.get(sale.sellerId());
        if (buyer == null || seller == null || buyer.cash() < sale.price()) {
            throw new BusinessRuleViolationException("Buyer cannot afford this price");
        }
        Set<Integer> sellerTiles = new HashSet<>(seller.ownedTilePositions());
        Set<Integer> buyerTiles = new HashSet<>(buyer.ownedTilePositions());
        sellerTiles.remove(sale.tilePosition());
        buyerTiles.add(sale.tilePosition());
        assets.put(seller.playerId(), new PlayerAsset(
                seller.playerId(), seller.cash() + sale.price(), seller.position(), seller.inJail(), seller.jailTurns(), sellerTiles));
        assets.put(buyer.playerId(), new PlayerAsset(
                buyer.playerId(), buyer.cash() - sale.price(), buyer.position(), buyer.inJail(), buyer.jailTurns(), buyerTiles));
        Map<Integer, UUID> owners = new HashMap<>(state.owners());
        owners.put(sale.tilePosition(), sale.buyerId());
        List<String> log = append(
                state.log(),
                state.board().tileAt(sale.tilePosition()).name() + " sold to "
                        + playerLabel(state, sale.buyerId()) + " for " + sale.price());
        return new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.RAISING_FUNDS,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                owners,
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                log,
                state.activeEvent(),
                state.pendingDebt(),
                state.bankruptPlayerIds(),
                state.debtQueue(),
                null);
    }

    private MonopolyGameState declineSale(MonopolyGameState state, MonopolyAction action) {
        PendingSale sale = state.pendingSale();
        if (sale == null || !sale.buyerId().equals(action.actorPlayerId())) {
            throw new BusinessRuleViolationException("There is no offer to decline");
        }
        List<String> log = append(state.log(), playerLabel(state, sale.buyerId()) + " declined the property offer");
        return new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.RAISING_FUNDS,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                new LinkedHashMap<>(state.assets()),
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                log,
                state.activeEvent(),
                state.pendingDebt(),
                state.bankruptPlayerIds(),
                state.debtQueue(),
                null);
    }

    private MonopolyGameState continueAfterDebt(
            MonopolyGameState state,
            Map<UUID, PlayerAsset> assets,
            Map<Integer, UUID> owners,
            Map<Integer, PropertyDevelopment> developments,
            Set<Integer> mortgagedTiles,
            List<String> log,
            Set<UUID> bankrupt,
            List<PendingDebt> queue,
            PendingSale sale) {
        PendingDebt next = null;
        List<PendingDebt> rest = new ArrayList<>();
        boolean found = false;
        for (PendingDebt candidate : queue) {
            if (found) {
                rest.add(candidate);
                continue;
            }
            if (bankrupt.contains(candidate.debtorId())) {
                continue;
            }
            PlayerAsset payer = assets.get(candidate.debtorId());
            if (payer != null && payer.cash() >= candidate.amount()) {
                assets.put(payer.playerId(), withCash(payer, payer.cash() - candidate.amount()));
                credit(assets, candidate.creditorId(), candidate.amount(), bankrupt);
                log.add(playerLabel(state, candidate.debtorId()) + " paid " + candidate.amount() + " for " + candidate.reason());
                continue;
            }
            next = candidate;
            found = true;
        }
        if (next != null) {
            log.add(playerLabel(state, next.debtorId()) + " cannot pay " + next.amount() + " for " + next.reason()
                    + ". Sell buildings or mortgage a property.");
            return new MonopolyGameState(
                    state.sessionId(),
                    MonopolyPhase.RAISING_FUNDS,
                    state.currentPlayerId(),
                    state.currentTurn(),
                    state.lastDiceTotal(),
                    state.board(),
                    assets,
                    owners,
                    developments,
                    mortgagedTiles,
                    log,
                    state.activeEvent(),
                    next,
                    bankrupt,
                    rest,
                    sale);
        }
        long solvent = assets.keySet().stream().filter(id -> !bankrupt.contains(id)).count();
        if (solvent <= 1) {
            UUID winner = assets.keySet().stream().filter(id -> !bankrupt.contains(id)).findFirst().orElse(state.currentPlayerId());
            log.add(playerLabel(state, winner) + " wins the game");
            return new MonopolyGameState(
                    state.sessionId(),
                    MonopolyPhase.ENDED,
                    winner,
                    state.currentTurn(),
                    state.lastDiceTotal(),
                    state.board(),
                    assets,
                    owners,
                    developments,
                    mortgagedTiles,
                    log,
                    state.activeEvent(),
                    null,
                    bankrupt,
                    List.of(),
                    null);
        }
        if (bankrupt.contains(state.currentPlayerId())) {
            UUID nextPlayer = nextSolventPlayer(assets, bankrupt, state.currentPlayerId());
            return new MonopolyGameState(
                    state.sessionId(),
                    MonopolyPhase.WAITING_FOR_ROLL,
                    nextPlayer,
                    state.currentTurn() + 1,
                    state.lastDiceTotal(),
                    state.board(),
                    assets,
                    owners,
                    developments,
                    mortgagedTiles,
                    log,
                    state.activeEvent(),
                    null,
                    bankrupt,
                    List.of(),
                    null);
        }
        return handoverIfIdle(new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.WAITING_FOR_DECISION,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                owners,
                developments,
                mortgagedTiles,
                log,
                state.activeEvent(),
                null,
                bankrupt,
                List.of(),
                null));
    }

    private void credit(Map<UUID, PlayerAsset> assets, UUID creditorId, int amount, Set<UUID> bankrupt) {
        if (creditorId == null || amount <= 0 || bankrupt.contains(creditorId)) {
            return;
        }
        PlayerAsset creditor = assets.get(creditorId);
        if (creditor != null) {
            assets.put(creditor.playerId(), withCash(creditor, creditor.cash() + amount));
        }
    }

    private UUID nextSolventPlayer(MonopolyGameState state, UUID from) {
        return nextSolventPlayer(state.assets(), state.bankruptPlayerIds(), from);
    }

    private UUID nextSolventPlayer(Map<UUID, PlayerAsset> assets, Set<UUID> bankrupt, UUID from) {
        List<UUID> players = new ArrayList<>(assets.keySet());
        if (players.isEmpty()) {
            return from;
        }
        int start = players.indexOf(from);
        if (start < 0) {
            start = 0;
        }
        for (int step = 1; step <= players.size(); step++) {
            UUID candidate = players.get((start + step) % players.size());
            if (!bankrupt.contains(candidate)) {
                return candidate;
            }
        }
        return from;
    }

    private MonopolyGameState finishGame(MonopolyGameState state, UUID winner, List<String> log) {
        log.add(playerLabel(state, winner) + " wins the game");
        return new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.ENDED,
                winner,
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                new LinkedHashMap<>(state.assets()),
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                log,
                state.activeEvent(),
                null,
                state.bankruptPlayerIds(),
                List.of(),
                null);
    }

    private MonopolyPhase decisionPhase(MonopolyGameState state) {
        return state.phase() == MonopolyPhase.RAISING_FUNDS
                ? MonopolyPhase.RAISING_FUNDS
                : MonopolyPhase.WAITING_FOR_DECISION;
    }

    private PlayerAsset withCash(PlayerAsset asset, int cash) {
        return new PlayerAsset(
                asset.playerId(),
                cash,
                asset.position(),
                asset.inJail(),
                asset.jailTurns(),
                asset.ownedTilePositions());
    }

    private MonopolyGameState carry(
            MonopolyGameState state,
            MonopolyPhase phase,
            Map<UUID, PlayerAsset> assets,
            Map<Integer, UUID> owners,
            Map<Integer, PropertyDevelopment> developments,
            Set<Integer> mortgagedTiles,
            List<String> log) {
        return new MonopolyGameState(
                state.sessionId(),
                phase,
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                owners,
                developments,
                mortgagedTiles,
                log,
                state.activeEvent(),
                state.pendingDebt(),
                state.bankruptPlayerIds(),
                state.debtQueue(),
                state.pendingSale());
    }

    private MonopolyGameState publish(
            MonopolyGameState state,
            Settlement settlement,
            UUID currentPlayerId,
            int turn,
            int dice,
            IndianEvent event) {
        return new MonopolyGameState(
                state.sessionId(),
                settlement.phase,
                currentPlayerId,
                turn,
                dice,
                state.board(),
                settlement.assets(),
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                settlement.log(),
                event,
                settlement.pendingDebt,
                state.bankruptPlayerIds(),
                settlement.debtQueue,
                null);
    }

    private final class Settlement {
        private Map<UUID, PlayerAsset> assets;
        private final List<String> log;
        private MonopolyPhase phase;
        private PendingDebt pendingDebt;
        private final List<PendingDebt> debtQueue = new ArrayList<>();
        private final Set<UUID> bankrupt;

        private Settlement(
                MonopolyGameState state,
                Map<UUID, PlayerAsset> assets,
                List<String> log,
                MonopolyPhase phase) {
            this.assets = assets;
            this.log = log;
            this.phase = phase;
            this.bankrupt = state.bankruptPlayerIds();
        }

        private Map<UUID, PlayerAsset> assets() {
            return assets;
        }

        private List<String> log() {
            return log;
        }

        private void setAssets(Map<UUID, PlayerAsset> assets) {
            this.assets = assets;
        }

        private PlayerAsset asset(UUID playerId) {
            return assets.get(playerId);
        }

        private void replace(PlayerAsset asset) {
            assets = new LinkedHashMap<>(assets);
            assets.put(asset.playerId(), asset);
        }

        private void charge(UUID payerId, UUID creditorId, int amount, String reason) {
            if (amount <= 0 || payerId == null || bankrupt.contains(payerId)) {
                return;
            }
            if (pendingDebt != null) {
                debtQueue.add(new PendingDebt(payerId, creditorId, amount, reason));
                phase = MonopolyPhase.RAISING_FUNDS;
                return;
            }
            PlayerAsset payer = assets.get(payerId);
            if (payer == null) {
                return;
            }
            if (payer.cash() >= amount) {
                replace(withCash(payer, payer.cash() - amount));
                if (creditorId != null && !bankrupt.contains(creditorId)) {
                    PlayerAsset creditor = assets.get(creditorId);
                    if (creditor != null) {
                        replace(withCash(creditor, creditor.cash() + amount));
                    }
                }
                log.add(reason + " paid: " + amount);
                return;
            }
            pendingDebt = new PendingDebt(payerId, creditorId, amount, reason);
            phase = MonopolyPhase.RAISING_FUNDS;
            log.add(playerLabel(null, payerId) + " cannot pay " + amount + " for " + reason
                    + ". Sell buildings or mortgage a property.");
        }
    }

    private MonopolyGameState copy(
            MonopolyGameState state,
            MonopolyPhase phase,
            UUID currentPlayerId,
            int turn,
            int lastDiceTotal,
            String logMessage) {
        return new MonopolyGameState(
                state.sessionId(),
                phase,
                currentPlayerId,
                turn,
                lastDiceTotal,
                state.board(),
                new LinkedHashMap<>(state.assets()),
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                append(state.log(), logMessage),
                state.activeEvent(),
                state.pendingDebt(),
                state.bankruptPlayerIds(),
                state.debtQueue(),
                state.pendingSale());
    }

    private List<String> append(List<String> log, String message) {
        List<String> updated = new ArrayList<>(log);
        updated.add(message);
        return updated;
    }

    // ============ Banker Ollama Integration Methods ============

    /**
     * Get banker approval for a transaction
     */
    private OllamaDtos.BankerDecisionResponse getBankerApproval(
            MonopolyGameState state,
            String actionType,
            int transactionAmount,
            String transactionType,
            String propertyName) {

        if (bankerOllamaService == null) {
            log.debug("Banker Ollama service not available, skipping approval");
            return null;
        }

        try {
            int difficulty = estimateGameDifficulty(state);
            OllamaDtos.BankerDecisionResponse response = bankerOllamaService.getBankerRecommendation(
                    state,
                    actionType,
                    transactionAmount,
                    transactionType,
                    difficulty
            );
            log.debug("Banker decision: {}", response);
            return response;
        } catch (Exception e) {
            log.warn("Error getting banker approval, proceeding without approval", e);
            return null;
        }
    }

    /**
     * Get banker advice for property transactions
     */
    private String getBankerPropertyAdvice(
            MonopolyGameState state,
            String propertyName,
            int amount,
            boolean isPurchase) {

        if (bankerOllamaService == null) {
            return "Evaluate your position carefully";
        }

        try {
            String advice = bankerOllamaService.getBankerPropertyAdvice(
                    state,
                    propertyName,
                    amount,
                    isPurchase
            );
            return advice != null ? advice : "Consider your finances";
        } catch (Exception e) {
            log.warn("Error getting banker advice", e);
            return "Consult your strategy";
        }
    }

    /**
     * Estimate game difficulty based on game state
     */
    private int estimateGameDifficulty(MonopolyGameState state) {
        // Base difficulty on turn number and property distribution
        int baseDifficulty = Math.min(5, (state.currentTurn() / 5) + 1);
        
        // Adjust based on property distribution
        long ownerCount = state.owners().values().stream().distinct().count();
        if (ownerCount > 2) {
            baseDifficulty = Math.min(5, baseDifficulty + 1);
        }
        
        return baseDifficulty;
    }
}
