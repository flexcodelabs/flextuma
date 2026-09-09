package com.flexcodelabs.flextuma.modules.whatsapp.dtos;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;

@Builder
public record WhatsAppWebhookOverviewDTO(
        long eventsLast24h,
        long eventsPrevious24h,
        List<Long> eventsHourly,
        Double deliverySuccessRate,
        long retriesLastHour,
        Long p95LatencyMs,
        Map<UUID, String> configHealth,
        List<WhatsAppActivityItemDTO> activity) {
}
