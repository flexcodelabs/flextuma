package com.flexcodelabs.flextuma.modules.sms.controllers;

import java.math.BigDecimal;

public record PreviewResponse(String renderedContent, int segmentCount, String encoding, int charactersRemaining,
                BigDecimal cost, BigDecimal pricePerSegment) {
}
