package com.flexcodelabs.flextuma.core.dtos;

import java.util.List;

/** Suggestions is empty when {@code available} is true -- there's nothing to suggest an
 * alternative to. {@code emailAvailable} is null when no email was passed to the check. */
public record UsernameAvailabilityDto(String username, boolean available, List<String> suggestions,
        Boolean emailAvailable) {
}
