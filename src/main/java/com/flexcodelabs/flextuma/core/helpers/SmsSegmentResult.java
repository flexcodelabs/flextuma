package com.flexcodelabs.flextuma.core.helpers;

import java.math.BigDecimal;

public record SmsSegmentResult(int segments, boolean isGsm7, int length, int charactersRemaining, BigDecimal cost,
        BigDecimal pricePerSegment) {
}
