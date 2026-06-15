package com.gamehub.monopoly.domain;

import java.util.List;

public record Board(List<Tile> tiles) {

    public Tile tileAt(int position) {
        return tiles.get(position % tiles.size());
    }

    public static Board standardBoard() {
        return new Board(List.of(
                new SimpleTile(0, "GO", TileType.GO, 200),
                new Property(1, "Mediterranean Avenue", "BROWN", 60, 2, 50),
                new SimpleTile(2, "Community Chest", TileType.COMMUNITY_CHEST, 0),
                new Property(3, "Baltic Avenue", "BROWN", 60, 4, 50),
                new SimpleTile(4, "Income Tax", TileType.TAX, 200),
                new Railroad(5, "Reading Railroad", 200, 25),
                new Property(6, "Oriental Avenue", "LIGHT_BLUE", 100, 6, 50),
                new SimpleTile(7, "Chance", TileType.CHANCE, 0),
                new Property(8, "Vermont Avenue", "LIGHT_BLUE", 100, 6, 50),
                new Property(9, "Connecticut Avenue", "LIGHT_BLUE", 120, 8, 50),
                new SimpleTile(10, "Jail", TileType.JAIL, 0),
                new Property(11, "St. Charles Place", "PINK", 140, 10, 100),
                new Utility(12, "Electric Company", 150),
                new Property(13, "States Avenue", "PINK", 140, 10, 100),
                new Property(14, "Virginia Avenue", "PINK", 160, 12, 100),
                new Railroad(15, "Pennsylvania Railroad", 200, 25),
                new Property(16, "St. James Place", "ORANGE", 180, 14, 100),
                new SimpleTile(17, "Community Chest", TileType.COMMUNITY_CHEST, 0),
                new Property(18, "Tennessee Avenue", "ORANGE", 180, 14, 100),
                new Property(19, "New York Avenue", "ORANGE", 200, 16, 100),
                new SimpleTile(20, "Free Parking", TileType.FREE_PARKING, 0),
                new Property(21, "Kentucky Avenue", "RED", 220, 18, 150),
                new SimpleTile(22, "Chance", TileType.CHANCE, 0),
                new Property(23, "Indiana Avenue", "RED", 220, 18, 150),
                new Property(24, "Illinois Avenue", "RED", 240, 20, 150),
                new Railroad(25, "B. & O. Railroad", 200, 25),
                new Property(26, "Atlantic Avenue", "YELLOW", 260, 22, 150),
                new Property(27, "Ventnor Avenue", "YELLOW", 260, 22, 150),
                new Utility(28, "Water Works", 150),
                new Property(29, "Marvin Gardens", "YELLOW", 280, 24, 150),
                new SimpleTile(30, "Go To Jail", TileType.GO_TO_JAIL, 0),
                new Property(31, "Pacific Avenue", "GREEN", 300, 26, 200),
                new Property(32, "North Carolina Avenue", "GREEN", 300, 26, 200),
                new SimpleTile(33, "Community Chest", TileType.COMMUNITY_CHEST, 0),
                new Property(34, "Pennsylvania Avenue", "GREEN", 320, 28, 200),
                new Railroad(35, "Short Line", 200, 25),
                new SimpleTile(36, "Chance", TileType.CHANCE, 0),
                new Property(37, "Park Place", "DARK_BLUE", 350, 35, 200),
                new SimpleTile(38, "Luxury Tax", TileType.TAX, 100),
                new Property(39, "Boardwalk", "DARK_BLUE", 400, 50, 200)));
    }
}
