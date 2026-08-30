package com.flexcodelabs.flextuma.modules.pricing.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicPricingControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PublicPricingController controller = new PublicPricingController();
        ReflectionTestUtils.setField(controller, "pricePerSegment", new BigDecimal("20.0"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getPricing_shouldReturnConfiguredPricePerSegment() throws Exception {
        mockMvc.perform(get("/api/public/pricing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricePerSegment").value(20.0))
                .andExpect(jsonPath("$.currency").value("TZS"));
    }
}
