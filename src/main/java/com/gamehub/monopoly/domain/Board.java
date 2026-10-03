package com.gamehub.monopoly.domain;

import java.util.List;

public record Board(List<Tile> tiles) {

    public Tile tileAt(int position) {
        return tiles.get(position % tiles.size());
    }

    public static Board standardBoard() {
        return new Board(List.of(
                new SimpleTile(0, "GO", TileType.GO, 2000),
                new Property(1, "Manglore", "BROWN", 600, 20, 500),
                new SimpleTile(2, "Community Chest", TileType.COMMUNITY_CHEST, 0),
                new Property(3, "Ranchi", "BROWN", 600, 40, 500),
                new SimpleTile(4, "Income Tax", TileType.TAX, 2000),
                new Railroad(5, "Indian Railways", 2000, 250),
                new Property(6, "Varanasi", "LIGHT_BLUE", 1000, 60, 500),
                new SimpleTile(7, "Chance", TileType.CHANCE, 0),
                new Property(8, "Amritsar", "LIGHT_BLUE", 1000, 60, 500),
                new Property(9, "Udaipur", "LIGHT_BLUE", 1200, 80, 500),
                new SimpleTile(10, "Jail", TileType.JAIL, 0),
                new Property(11, "Jaipur", "PINK", 1400, 100, 1000),
                new Utility(12, "Electricity Board", 1500),
                new Property(13, "Lucknow", "PINK", 1400, 100, 1000),
                new Property(14, "Bhopal", "PINK", 1600, 120, 1000),
                new Railroad(15, "Southern Railway", 2000, 250),
                new Property(16, "Surat", "ORANGE", 1800, 140, 1000),
                new SimpleTile(17, "Community Chest", TileType.COMMUNITY_CHEST, 0),
                new Property(18, "Coimbatore", "ORANGE", 1800, 140, 1000),
                new Property(19, "Indore", "ORANGE", 2000, 160, 1000),
                new SimpleTile(20, "Free Parking", TileType.FREE_PARKING, 0),
                new Property(21, "Kolkata", "RED", 2200, 180, 1500),
                new SimpleTile(22, "Chance", TileType.CHANCE, 0),
                new Property(23, "Hyderabad", "RED", 2200, 180, 1500),
                new Property(24, "Bengaluru", "RED", 2400, 200, 1500),
                new Railroad(25, "Western Railway", 2000, 250),
                new Property(26, "Chennai", "YELLOW", 2600, 220, 1500),
                new Property(27, "Kochi", "YELLOW", 2600, 220, 1500),
                new Utility(28, "Water Board", 1500),
                new Property(29, "Panaji (Goa)", "YELLOW", 2800, 240, 1500),
                new SimpleTile(30, "Go To Jail", TileType.GO_TO_JAIL, 0),
                new Property(31, "Gurugram", "GREEN", 3000, 260, 2000),
                new Property(32, "Noida", "GREEN", 3000, 260, 2000),
                new SimpleTile(33, "Community Chest", TileType.COMMUNITY_CHEST, 0),
                new Property(34, "Whitefield", "GREEN", 3200, 280, 2000),
                new Railroad(35, "Northern Railway", 2000, 250),
                new SimpleTile(36, "Chance", TileType.CHANCE, 0),
                new Property(37, "Connaught Place", "DARK_BLUE", 3500, 350, 2000),
                new SimpleTile(38, "GST", TileType.TAX, 1000),
                new Property(39, "Nariman Point", "DARK_BLUE", 4000, 500, 2000)));
    }
}
