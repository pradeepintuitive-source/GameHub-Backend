package com.gamehub.common.domain;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ApiException extends DomainException {

    private final HttpStatus status;
    private final List<String> details;

    public ApiException(HttpStatus status, String message, List<String> details) {
        super(message);
        this.status = status;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<String> getDetails() {
        return details;
    }
}
