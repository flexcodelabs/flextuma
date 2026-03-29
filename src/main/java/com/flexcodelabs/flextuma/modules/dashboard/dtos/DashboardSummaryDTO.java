package com.flexcodelabs.flextuma.modules.dashboard.dtos;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;

@Builder
public record DashboardSummaryDTO(
        UUID userId,
        String username,
        long sent,
        long failed,
        BigDecimal balanceAmount,
        String balance,
        String currency,
        long activeCampaigns,
        long today,
        long thisWeek,
        long thisMonth,
        double successRate,
        Map<String, Double> statusBreakdown) {
}
