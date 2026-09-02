package com.flexcodelabs.flextuma.core.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ProfileUpdateDto(
        @NotBlank String name,
        @NotBlank String username,
        @Email String email,
        @NotBlank String phoneNumber) {
}
