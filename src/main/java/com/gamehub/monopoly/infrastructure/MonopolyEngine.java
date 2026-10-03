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

    private final Random random = new Random();
    
    @Autowired(required = false)
    private BankerOllamaService bankerOllamaService;

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
        if (!bankAction && !state.currentPlayerId().equals(action.actorPlayerId())) {
            throw new BusinessRuleViolationException("It is not this player's turn");
        }
    }

    @Override
    public MonopolyGameState processAction(MonopolyGameState state, MonopolyAction action) {
        validateAction(state, action);
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
                MonopolyPhase.WAITING_FOR_DECISION,
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
                MonopolyPhase.WAITING_FOR_DECISION,
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
        if (!ownerId.equals(state.currentPlayerId())) {
            throw new BusinessRuleViolationException("Player does not own this property");
        }
        Map<Integer, PropertyDevelopment> developments = new HashMap<>(state.developments());
        PropertyDevelopment current = developments.getOrDefault(tilePosition, new PropertyDevelopment(0, false));
        if (current.houses() <= 0 && !current.hotel()) {
            throw new BusinessRuleViolationException("No houses to sell");
        }
        int refund = property.houseCost() / 2;
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset p = assets.get(state.currentPlayerId());
        assets.put(p.playerId(), new PlayerAsset(
                p.playerId(),
                p.cash() + refund,
                p.position(),
                p.inJail(),
                p.jailTurns(),
                p.ownedTilePositions()));
        PropertyDevelopment updatedDev;
        if (current.hotel()) {
            // demote hotel to 4 houses
            updatedDev = new PropertyDevelopment(4, false);
        } else {
            updatedDev = new PropertyDevelopment(current.houses() - 1, false);
        }
        developments.put(tilePosition, updatedDev);
        List<String> log = new java.util.ArrayList<>(state.log());
        log.add("House sold on " + tile.name());
        return new MonopolyGameState(
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
                state.activeEvent());
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
        if (currentPosition + total >= state.board().tiles().size()) {
            cash += goBonus(activeEvent);
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

        MonopolyPhase phase = MonopolyPhase.WAITING_FOR_DECISION;
        if (landedTile instanceof SimpleTile simpleTile) {
            if (simpleTile.tileType() == TileType.TAX) {
                int tax = taxAmount(simpleTile, activeEvent);
                assets.put(updatedAsset.playerId(), new PlayerAsset(
                        updatedAsset.playerId(),
                        updatedAsset.cash() - tax,
                        updatedAsset.position(),
                        false,
                        0,
                        updatedAsset.ownedTilePositions()));
                log.add("Tax collected: " + tax);
            } else if (simpleTile.tileType() == TileType.GO_TO_JAIL) {
                assets.put(updatedAsset.playerId(), new PlayerAsset(
                        updatedAsset.playerId(),
                        updatedAsset.cash(),
                        10,
                        true,
                        1,
                        updatedAsset.ownedTilePositions()));
                log.add("Player sent to jail");
            } else if (simpleTile.tileType() == TileType.CHANCE
                    || simpleTile.tileType() == TileType.COMMUNITY_CHEST) {
                BharatCards.Deck deck = simpleTile.tileType() == TileType.CHANCE
                        ? BharatCards.Deck.CHANCE
                        : BharatCards.Deck.CHEST;
                CardDrawResult drawn = drawAndApplyCard(state, assets, log, updatedAsset.playerId(), deck);
                assets = drawn.assets();
                log = drawn.log();
            } else if (simpleTile.tileType() == TileType.FREE_PARKING) {
                int expires = state.currentTurn() + Math.max(4, 2 * state.assets().size());
                String exclude = activeEvent != null ? activeEvent.id() : null;
                activeEvent = exclude != null
                        ? IndianEvents.drawExcluding(exclude, expires)
                        : IndianEvents.draw(expires);
                log.add("Indian Event: " + activeEvent.title() + " — " + activeEvent.description());
                if ("FLOODS".equals(activeEvent.id()) || "CYCLONE".equals(activeEvent.id())) {
                    assets = payFloodInsurance(state, assets, log);
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
                    assets,
                    state.owners(),
                    state.developments(),
                    state.mortgagedTiles(),
                    state.log(),
                    activeEvent);
            int rent = rentFor(landedTile, state.developments().get(landedTile.position()), rentState, assets);
            UUID ownerId = state.owners().get(landedTile.position());
            PlayerAsset owner = assets.get(ownerId);
            PlayerAsset payer = assets.get(updatedAsset.playerId());
            assets.put(updatedAsset.playerId(), new PlayerAsset(
                    payer.playerId(),
                    payer.cash() - rent,
                    payer.position(),
                    payer.inJail(),
                    payer.jailTurns(),
                    payer.ownedTilePositions()));
            assets.put(ownerId, new PlayerAsset(
                    owner.playerId(),
                    owner.cash() + rent,
                    owner.position(),
                    owner.inJail(),
                    owner.jailTurns(),
                    owner.ownedTilePositions()));
            log.add("Rent paid: " + rent + " to " + playerLabel(state, ownerId));
        } else if (!(landedTile instanceof Property || landedTile instanceof Railroad || landedTile instanceof Utility)) {
            phase = MonopolyPhase.WAITING_FOR_DECISION;
        }

        return new MonopolyGameState(
                state.sessionId(),
                phase,
                state.currentPlayerId(),
                state.currentTurn(),
                total,
                state.board(),
                assets,
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                log,
                activeEvent);
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
        return new MonopolyGameState(
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
                state.activeEvent());
    }

    private MonopolyGameState payRent(MonopolyGameState state, MonopolyAction action) {
        if (action.targetPlayerId() == null || action.amount() == null) {
            throw new BusinessRuleViolationException("PAY_RENT requires target player and amount");
        }
        return transfer(state, state.currentPlayerId(), action.targetPlayerId(), action.amount(), "Manual rent payment");
    }

    private MonopolyGameState mortgage(MonopolyGameState state, MonopolyAction action) {
        int tilePosition = requiredTilePosition(action);
        if (!ownsTile(state, state.currentPlayerId(), tilePosition)) {
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
        
        // Get banker advice on mortgage
        String advice = getBankerPropertyAdvice(state, tile.name(), amount, false);
        
        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset asset = assets.get(state.currentPlayerId());
        assets.put(asset.playerId(), new PlayerAsset(
                asset.playerId(),
                asset.cash() + amount,
                asset.position(),
                asset.inJail(),
                asset.jailTurns(),
                asset.ownedTilePositions()));
        Set<Integer> mortgagedTiles = new HashSet<>(state.mortgagedTiles());
        mortgagedTiles.add(tilePosition);
        List<String> log = new ArrayList<>(state.log());
        log.add("Mortgage placed on " + tile.name() + " (Banker: " + advice + ")");
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
                mortgagedTiles,
                log,
                state.activeEvent());
    }

    private MonopolyGameState unmortgage(MonopolyGameState state, MonopolyAction action) {
        int tilePosition = requiredTilePosition(action);
        if (!state.mortgagedTiles().contains(tilePosition)) {
            throw new BusinessRuleViolationException("Tile is not mortgaged");
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
        PlayerAsset asset = assets.get(state.currentPlayerId());
        if (asset.cash() < cost) {
            throw new BusinessRuleViolationException("Insufficient funds");
        }
        assets.put(asset.playerId(), new PlayerAsset(
                asset.playerId(),
                asset.cash() - cost,
                asset.position(),
                asset.inJail(),
                asset.jailTurns(),
                asset.ownedTilePositions()));
        Set<Integer> mortgagedTiles = new HashSet<>(state.mortgagedTiles());
        mortgagedTiles.remove(tilePosition);
        List<String> log = new ArrayList<>(state.log());
        String logMsg = "Mortgage cleared on " + tile.name();
        if (bankerDecision != null) {
            logMsg += " (Banker: " + bankerDecision.reasoning() + ")";
        }
        log.add(logMsg);
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
                mortgagedTiles,
                log,
                state.activeEvent());
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
        return new MonopolyGameState(
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
                state.activeEvent());
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
        return new MonopolyGameState(
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
                state.activeEvent());
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
        return new MonopolyGameState(
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
                state.activeEvent());
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
        return new MonopolyGameState(
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
                state.activeEvent());
    }

    private MonopolyGameState endTurn(MonopolyGameState state) {
        List<UUID> players = new ArrayList<>(state.assets().keySet());
        int currentIndex = players.indexOf(state.currentPlayerId());
        UUID nextPlayer = players.get((currentIndex + 1) % players.size());
        int nextTurn = state.currentTurn() + 1;
        IndianEvent event = state.activeEvent();
        if (event != null && nextTurn > event.expiresOnTurn()) {
            event = null;
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
                event);
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
        return new MonopolyGameState(
                state.sessionId(),
                state.phase(),
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
                assets,
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                append(state.log(), "Bank: " + signed + " to " + playerLabel(state, target.playerId())),
                state.activeEvent());
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
        return new MonopolyGameState(
                state.sessionId(),
                state.phase(),
                state.currentPlayerId(),
                state.currentTurn(),
                state.lastDiceTotal(),
                state.board(),
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
                                + playerLabel(state, action.targetPlayerId())),
                state.activeEvent());
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

    private record CardDrawResult(Map<UUID, PlayerAsset> assets, List<String> log, boolean sentToJail) {}

    private CardDrawResult drawAndApplyCard(
            MonopolyGameState state,
            Map<UUID, PlayerAsset> assets,
            List<String> log,
            UUID playerId,
            BharatCards.Deck deckType) {
        BharatCards.Card[] deck = deckType == BharatCards.Deck.CHANCE ? BharatCards.CHANCE : BharatCards.CHEST;
        BharatCards.Card card = deck[random.nextInt(deck.length)];
        List<String> nextLog = new ArrayList<>(log);
        nextLog.add("Card (" + deckType.name() + "): " + card.text());
        Map<UUID, PlayerAsset> nextAssets = new LinkedHashMap<>(assets);
        PlayerAsset player = nextAssets.get(playerId);
        boolean sentToJail = false;

        switch (card.kind()) {
            case MONEY -> {
                nextAssets.put(playerId, new PlayerAsset(
                        player.playerId(),
                        player.cash() + card.amount(),
                        player.position(),
                        player.inJail(),
                        player.jailTurns(),
                        player.ownedTilePositions()));
            }
            case MONEY_FROM_EACH -> {
                int amt = card.amount();
                int cash = player.cash();
                for (Map.Entry<UUID, PlayerAsset> entry : nextAssets.entrySet()) {
                    if (entry.getKey().equals(playerId)) continue;
                    PlayerAsset other = entry.getValue();
                    if (amt >= 0) {
                        cash += amt;
                        entry.setValue(new PlayerAsset(
                                other.playerId(),
                                other.cash() - amt,
                                other.position(),
                                other.inJail(),
                                other.jailTurns(),
                                other.ownedTilePositions()));
                    } else {
                        int pay = -amt;
                        cash -= pay;
                        entry.setValue(new PlayerAsset(
                                other.playerId(),
                                other.cash() + pay,
                                other.position(),
                                other.inJail(),
                                other.jailTurns(),
                                other.ownedTilePositions()));
                    }
                }
                nextAssets.put(playerId, new PlayerAsset(
                        player.playerId(),
                        cash,
                        player.position(),
                        player.inJail(),
                        player.jailTurns(),
                        player.ownedTilePositions()));
            }
            case MOVE -> {
                int from = player.position();
                int to = card.to();
                int boardSize = state.board().tiles().size();
                int cash = player.cash();
                if (card.collectGoIfPass() && to < from) {
                    cash += GO_BONUS;
                }
                nextAssets.put(playerId, new PlayerAsset(
                        player.playerId(),
                        cash,
                        to,
                        false,
                        0,
                        player.ownedTilePositions()));
                nextLog.add("Moved to " + state.board().tileAt(to).name());
                // Collect rent if landing on owned tile (simple pass)
                Tile dest = state.board().tileAt(to);
                if (state.owners().containsKey(to)
                        && !state.owners().get(to).equals(playerId)
                        && !state.mortgagedTiles().contains(to)) {
                    int rent = rentFor(dest, state.developments().get(to), state, nextAssets);
                    PlayerAsset mover = nextAssets.get(playerId);
                    UUID ownerId = state.owners().get(to);
                    PlayerAsset owner = nextAssets.get(ownerId);
                    nextAssets.put(playerId, new PlayerAsset(
                            mover.playerId(),
                            mover.cash() - rent,
                            mover.position(),
                            false,
                            0,
                            mover.ownedTilePositions()));
                    nextAssets.put(ownerId, new PlayerAsset(
                            owner.playerId(),
                            owner.cash() + rent,
                            owner.position(),
                            owner.inJail(),
                            owner.jailTurns(),
                            owner.ownedTilePositions()));
                    nextLog.add("Rent paid from card move: " + rent);
                }
            }
            case MOVE_REL -> {
                int boardSize = state.board().tiles().size();
                int nextPos = ((player.position() + card.amount()) % boardSize + boardSize) % boardSize;
                nextAssets.put(playerId, new PlayerAsset(
                        player.playerId(),
                        player.cash(),
                        nextPos,
                        false,
                        0,
                        player.ownedTilePositions()));
                nextLog.add("Moved to " + state.board().tileAt(nextPos).name());
            }
            case JAIL -> {
                nextAssets.put(playerId, new PlayerAsset(
                        player.playerId(),
                        player.cash(),
                        10,
                        true,
                        1,
                        player.ownedTilePositions()));
                nextLog.add("Player sent to jail by card");
                sentToJail = true;
            }
            case GET_OUT -> nextLog.add("Get Out of Jail Free held (use via jail card action)");
            case NEAREST_RAILROAD -> {
                int nextPos = nearestAhead(player.position(), RAILROAD_POSITIONS, state.board().tiles().size());
                int cash = player.cash();
                if (nextPos < player.position()) cash += GO_BONUS;
                nextAssets.put(playerId, new PlayerAsset(
                        player.playerId(), cash, nextPos, false, 0, player.ownedTilePositions()));
                nextLog.add("Advanced to nearest railway: " + state.board().tileAt(nextPos).name());
                Tile dest = state.board().tileAt(nextPos);
                if (state.owners().containsKey(nextPos)
                        && !playerId.equals(state.owners().get(nextPos))
                        && !state.mortgagedTiles().contains(nextPos)) {
                    int rent = rentFor(dest, null, state, nextAssets) * 2;
                    PlayerAsset mover = nextAssets.get(playerId);
                    UUID ownerId = state.owners().get(nextPos);
                    PlayerAsset owner = nextAssets.get(ownerId);
                    nextAssets.put(playerId, new PlayerAsset(
                            mover.playerId(), mover.cash() - rent, mover.position(), false, 0, mover.ownedTilePositions()));
                    nextAssets.put(ownerId, new PlayerAsset(
                            owner.playerId(), owner.cash() + rent, owner.position(), owner.inJail(), owner.jailTurns(), owner.ownedTilePositions()));
                    nextLog.add("Double railway rent paid: " + rent);
                }
            }
            case NEAREST_UTILITY -> {
                int nextPos = nearestAhead(player.position(), UTILITY_POSITIONS, state.board().tiles().size());
                int cash = player.cash();
                if (nextPos < player.position()) cash += GO_BONUS;
                nextAssets.put(playerId, new PlayerAsset(
                        player.playerId(), cash, nextPos, false, 0, player.ownedTilePositions()));
                nextLog.add("Advanced to nearest utility: " + state.board().tileAt(nextPos).name());
            }
            case REPAIRS -> {
                int cost = 0;
                for (int pos : player.ownedTilePositions()) {
                    PropertyDevelopment dev = state.developments().get(pos);
                    if (dev == null) continue;
                    if (dev.hotel()) cost += card.perHotel();
                    else cost += card.perHouse() * Math.max(0, dev.houses());
                }
                nextAssets.put(playerId, new PlayerAsset(
                        player.playerId(),
                        player.cash() - cost,
                        player.position(),
                        player.inJail(),
                        player.jailTurns(),
                        player.ownedTilePositions()));
                nextLog.add("Repairs paid: " + cost);
            }
        }

        return new CardDrawResult(nextAssets, nextLog, sentToJail);
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
                state.activeEvent());
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
