package com.gamehub.common.application;

import java.time.Instant;

public interface TimeProvider {

    Instant now();
}
