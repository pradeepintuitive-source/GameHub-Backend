package com.gamehub.monopoly.domain;

public record SimpleTile(
        int position,
        String name,
        TileType tileType,
        int value) implements Tile {
}
