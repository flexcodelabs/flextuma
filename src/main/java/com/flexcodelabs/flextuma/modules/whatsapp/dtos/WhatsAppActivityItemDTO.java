package com.flexcodelabs.flextuma.modules.whatsapp.dtos;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record WhatsAppActivityItemDTO(
        String type,
        String summary,
        LocalDateTime at) {
}
