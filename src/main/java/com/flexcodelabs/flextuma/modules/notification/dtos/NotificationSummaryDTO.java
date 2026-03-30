package com.flexcodelabs.flextuma.modules.notification.dtos;

import java.util.List;

import com.flexcodelabs.flextuma.core.entities.notification.PersonalNotification;

import lombok.Builder;

@Builder
public record NotificationSummaryDTO(
        long unreadCount,
        List<PersonalNotification> notifications) {
}
