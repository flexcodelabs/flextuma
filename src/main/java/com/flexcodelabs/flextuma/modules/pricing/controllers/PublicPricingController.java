package com.flexcodelabs.flextuma.modules.pricing.controllers;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flexcodelabs.flextuma.modules.pricing.dtos.PublicPricingDTO;

/** Unauthenticated, read-only config for the marketing site — see SecurityConfig's /api/public/** matcher. */
@RestController
@RequestMapping("/api/public/pricing")
public class PublicPricingController {

    @Value("${flextuma.sms.price-per-segment:1.0}")
    private BigDecimal pricePerSegment;

    @GetMapping
    public ResponseEntity<PublicPricingDTO> getPricing() {
        return ResponseEntity.ok(
                PublicPricingDTO.builder()
                        .pricePerSegment(pricePerSegment)
                        .currency("TZS")
                        .build());
    }
}
