package com.gamehub.common.infrastructure;

import com.gamehub.common.application.TimeProvider;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SystemTimeProvider implements TimeProvider {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
