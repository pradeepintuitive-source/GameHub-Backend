package com.gamehub.monopoly.domain;

/**
 * Active Indian Event drawn on Free Parking. Nullable on MonopolyGameState for forward compatibility.
 */
public record IndianEvent(
        String id,
        String title,
        String description,
        int expiresOnTurn) {}
