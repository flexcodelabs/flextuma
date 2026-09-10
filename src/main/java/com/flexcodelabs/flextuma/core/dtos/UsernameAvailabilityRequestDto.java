package com.flexcodelabs.flextuma.core.dtos;

/** Request body for the username-availability check. {@code email} is optional. */
public record UsernameAvailabilityRequestDto(String username, String email) {
}
