package com.gamehub.monopoly.domain;

public record Railroad(
        int position,
        String name,
        int purchasePrice,
        int baseRent) implements Tile {

    @Override
    public TileType tileType() {
        return TileType.RAILROAD;
    }
}
