package com.flexcodelabs.flextuma.modules.dashboard.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;

@Builder
public record DashboardNotificationDTO(
        UUID id,
        String phoneNumber,
        String message,
        String status,
        String provider,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
