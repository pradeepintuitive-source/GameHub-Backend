package com.gamehub.mafia.domain;

import java.util.List;

public record DayCycle(
        int cycleNumber,
        List<Vote> votes) {
}
