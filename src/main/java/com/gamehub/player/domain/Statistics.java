package com.gamehub.player.domain;

public record Statistics(
        int gamesPlayed,
        int wins,
        int losses,
        long playTimeSeconds) {
}
