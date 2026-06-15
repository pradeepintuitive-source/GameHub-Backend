package com.gamehub.monopoly.infrastructure;

import com.gamehub.common.domain.BusinessRuleViolationException;
import com.gamehub.monopoly.domain.Board;
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
import org.springframework.stereotype.Component;

@Component
public class MonopolyEngine implements GameEngine<MonopolyGameState, MonopolyAction> {

    private final Random random = new Random();

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
            assets.put(playerId, new PlayerAsset(playerId, 1500, 0, false, 0, new HashSet<>()));
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
                new ArrayList<>(List.of("Game started")));
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
        if (!state.currentPlayerId().equals(action.actorPlayerId())) {
            throw new BusinessRuleViolationException("It is not this player's turn");
        }
        if (state.phase() == MonopolyPhase.PAUSED || state.phase() == MonopolyPhase.ENDED) {
            throw new BusinessRuleViolationException("Game is not accepting actions");
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
            case TRADE -> trade(state, action);
            case AUCTION -> auction(state, action);
            case END_TURN -> endTurn(state);
        };
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
        if (currentPosition + total >= state.board().tiles().size()) {
            cash += 200;
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
        log.add("Player %s rolled %d and landed on %s".formatted(state.currentPlayerId(), total, landedTile.name()));

        MonopolyPhase phase = MonopolyPhase.WAITING_FOR_DECISION;
        if (landedTile instanceof SimpleTile simpleTile) {
            if (simpleTile.tileType() == TileType.TAX) {
                assets.put(updatedAsset.playerId(), new PlayerAsset(
                        updatedAsset.playerId(),
                        updatedAsset.cash() - simpleTile.value(),
                        updatedAsset.position(),
                        false,
                        0,
                        updatedAsset.ownedTilePositions()));
                log.add("Tax collected: " + simpleTile.value());
            } else if (simpleTile.tileType() == TileType.GO_TO_JAIL) {
                assets.put(updatedAsset.playerId(), new PlayerAsset(
                        updatedAsset.playerId(),
                        updatedAsset.cash(),
                        10,
                        true,
                        1,
                        updatedAsset.ownedTilePositions()));
                log.add("Player sent to jail");
            }
        } else if (state.owners().containsKey(landedTile.position())
                && !state.owners().get(landedTile.position()).equals(state.currentPlayerId())
                && !state.mortgagedTiles().contains(landedTile.position())) {
            int rent = rentFor(landedTile, state.developments().get(landedTile.position()));
            UUID ownerId = state.owners().get(landedTile.position());
            PlayerAsset owner = assets.get(ownerId);
            assets.put(updatedAsset.playerId(), new PlayerAsset(
                    updatedAsset.playerId(),
                    updatedAsset.cash() - rent,
                    updatedAsset.position(),
                    false,
                    0,
                    updatedAsset.ownedTilePositions()));
            assets.put(ownerId, new PlayerAsset(
                    owner.playerId(),
                    owner.cash() + rent,
                    owner.position(),
                    owner.inJail(),
                    owner.jailTurns(),
                    owner.ownedTilePositions()));
            log.add("Rent paid: " + rent + " to " + ownerId);
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
                log);
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
        log.add("Property purchased: " + tile.name());
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
                log);
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
        Tile tile = state.board().tileAt(tilePosition);
        int amount = purchasePrice(tile) / 2;
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
                append(state.log(), "Mortgage placed on " + tile.name()));
    }

    private MonopolyGameState unmortgage(MonopolyGameState state, MonopolyAction action) {
        int tilePosition = requiredTilePosition(action);
        if (!state.mortgagedTiles().contains(tilePosition)) {
            throw new BusinessRuleViolationException("Tile is not mortgaged");
        }
        Tile tile = state.board().tileAt(tilePosition);
        int cost = (int) Math.ceil((purchasePrice(tile) / 2.0) * 1.1);
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
                append(state.log(), "Mortgage cleared on " + tile.name()));
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
        Map<Integer, PropertyDevelopment> developments = new HashMap<>(state.developments());
        PropertyDevelopment current = developments.getOrDefault(tilePosition, new PropertyDevelopment(0, false));
        if (current.hotel() || current.houses() >= 4) {
            throw new BusinessRuleViolationException("Property cannot build more houses");
        }
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
        developments.put(tilePosition, new PropertyDevelopment(current.houses() + 1, false));
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
                append(state.log(), "House built on " + tile.name()));
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
        PropertyDevelopment development = state.developments().getOrDefault(tilePosition, new PropertyDevelopment(0, false));
        if (development.houses() < 4 || development.hotel()) {
            throw new BusinessRuleViolationException("Property is not ready for a hotel");
        }
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
                append(state.log(), "Hotel built on " + tile.name()));
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

        Map<Integer, UUID> owners = new HashMap<>(state.owners());
        owners.put(offeredTile, action.targetPlayerId());
        owners.put(requestedTile, state.currentPlayerId());

        Map<UUID, PlayerAsset> assets = new LinkedHashMap<>(state.assets());
        PlayerAsset source = assets.get(state.currentPlayerId());
        PlayerAsset target = assets.get(action.targetPlayerId());

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
                append(state.log(), "Trade completed"));
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
                append(state.log(), "Auction won for " + tile.name()));
    }

    private MonopolyGameState endTurn(MonopolyGameState state) {
        List<UUID> players = new ArrayList<>(state.assets().keySet());
        int currentIndex = players.indexOf(state.currentPlayerId());
        UUID nextPlayer = players.get((currentIndex + 1) % players.size());
        return new MonopolyGameState(
                state.sessionId(),
                MonopolyPhase.WAITING_FOR_ROLL,
                nextPlayer,
                state.currentTurn() + 1,
                state.lastDiceTotal(),
                state.board(),
                new LinkedHashMap<>(state.assets()),
                new HashMap<>(state.owners()),
                new HashMap<>(state.developments()),
                new HashSet<>(state.mortgagedTiles()),
                append(state.log(), "Turn ended"));
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
                append(state.log(), message));
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

    private int rentFor(Tile tile, PropertyDevelopment development) {
        return switch (tile) {
            case Property property -> {
                if (development == null) {
                    yield property.baseRent();
                }
                if (development.hotel()) {
                    yield property.baseRent() * 10;
                }
                yield property.baseRent() * Math.max(1, development.houses() + 1);
            }
            case Railroad railroad -> railroad.baseRent();
            case Utility ignored -> Math.max(20, 10 * Math.max(1, random.nextInt(1, 7) + random.nextInt(1, 7)));
            default -> 0;
        };
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
                append(state.log(), logMessage));
    }

    private List<String> append(List<String> log, String message) {
        List<String> updated = new ArrayList<>(log);
        updated.add(message);
        return updated;
    }
}
