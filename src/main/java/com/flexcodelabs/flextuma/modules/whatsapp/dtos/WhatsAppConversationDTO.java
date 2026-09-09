package com.flexcodelabs.flextuma.modules.whatsapp.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;

@Builder
public record WhatsAppConversationDTO(
        UUID configId,
        String phoneNumberId,
        String fromNumber,
        String contactName,
        String lastMessageContent,
        LocalDateTime lastMessageAt,
        long unreadCount) {
}
