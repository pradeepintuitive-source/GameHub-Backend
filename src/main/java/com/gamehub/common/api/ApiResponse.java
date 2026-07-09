package com.gamehub.common.api;

import java.time.Instant;
import java.util.List;

public record ApiResponse<T>(
        Instant timestamp,
        int status,
        String message,
        T data,
        List<String> details,
        String path) {
}
