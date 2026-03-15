package com.flexcodelabs.flextuma.core.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }

    public RateLimitExceededException(String message, long remainingTimeSeconds) {
        super(message + " Try again in " + remainingTimeSeconds + " seconds");
    }
}
