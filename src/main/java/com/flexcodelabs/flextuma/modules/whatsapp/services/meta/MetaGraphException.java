package com.flexcodelabs.flextuma.modules.whatsapp.services.meta;

import lombok.Getter;

/** A Graph API call that failed, carrying a message fit to show the user. */
@Getter
public class MetaGraphException extends RuntimeException {
    private final String code;

    public MetaGraphException(String code, String message) {
        super(message);
        this.code = code;
    }
}
