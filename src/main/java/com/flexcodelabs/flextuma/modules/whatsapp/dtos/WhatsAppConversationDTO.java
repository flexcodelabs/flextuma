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
        /** WhatsApp message type ("text", "image", "sticker", ...) of the last message. The
         * frontend uses this to render a type icon + label when {@code lastMessageContent} is
         * null (a media message with no caption). */
        String lastMessageType,
        LocalDateTime lastMessageAt,
        long unreadCount) {
}
