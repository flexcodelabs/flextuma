package com.flexcodelabs.flextuma.modules.pricing.dtos;

import java.math.BigDecimal;

import lombok.Builder;

@Builder
public record PublicPricingDTO(
        BigDecimal pricePerSegment,
        String currency) {
}
