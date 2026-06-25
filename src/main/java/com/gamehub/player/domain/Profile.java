package com.gamehub.player.domain;

public record Profile(
        String displayName,
        String avatarUrl,
        String locale) {
}
