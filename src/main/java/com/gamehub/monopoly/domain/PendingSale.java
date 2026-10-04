package com.gamehub.monopoly.domain;

import java.util.UUID;

/** A property offered for cash while the seller is raising funds. */
public record PendingSale(UUID sellerId, UUID buyerId, int tilePosition, int price) {
}
