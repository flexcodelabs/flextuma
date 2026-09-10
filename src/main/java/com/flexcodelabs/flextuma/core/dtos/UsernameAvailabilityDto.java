package com.flexcodelabs.flextuma.core.dtos;

import java.util.List;

/** Suggestions is empty when {@code available} is true -- there's nothing to suggest an
 * alternative to. */
public record UsernameAvailabilityDto(String username, boolean available, List<String> suggestions) {
}
