package com.flexcodelabs.flextuma.core.dtos;

/** Request body for the username-availability check. {@code email} and {@code phoneNumber} are
 * optional. */
public record UsernameAvailabilityRequestDto(String username, String email, String phoneNumber) {
}
