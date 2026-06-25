package com.gamehub.monopoly.domain;

public record Property(
        int position,
        String name,
        String colorGroup,
        int purchasePrice,
        int baseRent,
        int houseCost) implements Tile {

    @Override
    public TileType tileType() {
        return TileType.PROPERTY;
    }
}
