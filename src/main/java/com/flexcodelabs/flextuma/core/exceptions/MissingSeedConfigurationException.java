package com.flexcodelabs.flextuma.core.exceptions;

/** Thrown when a required seed-time secret (e.g. an initial account password) isn't configured.
 * Unlike other seeding failures, this must abort startup rather than be logged and swallowed --
 * silently continuing would boot the app with no way to log in, or worse, quietly skip creating
 * the account at all. See DataInitializer, which re-throws this one specifically. */
public class MissingSeedConfigurationException extends RuntimeException {
    public MissingSeedConfigurationException(String message) {
        super(message);
    }
}
