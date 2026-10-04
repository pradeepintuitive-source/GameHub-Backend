package com.gamehub.monopoly.domain;

import java.util.UUID;

/** A bill that has not been collected yet. A null creditor means the bank. */
public record PendingDebt(UUID debtorId, UUID creditorId, int amount, String reason) {
}
