package com.gamehub.monopoly.domain;

public record Utility(
        int position,
        String name,
        int purchasePrice) implements Tile {

    @Override
    public TileType tileType() {
        return TileType.UTILITY;
    }
}
