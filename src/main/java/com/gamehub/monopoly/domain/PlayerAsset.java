package com.gamehub.monopoly.domain;

import java.util.Set;
import java.util.UUID;

public record PlayerAsset(
        UUID playerId,
        int cash,
        int position,
        boolean inJail,
        int jailTurns,
        Set<Integer> ownedTilePositions) {
}
