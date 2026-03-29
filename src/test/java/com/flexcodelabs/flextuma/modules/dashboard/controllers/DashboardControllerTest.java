package com.flexcodelabs.flextuma.modules.dashboard.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.flexcodelabs.flextuma.modules.dashboard.dtos.DashboardSummaryDTO;
import com.flexcodelabs.flextuma.modules.dashboard.services.DashboardService;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService)).build();
    }

    @Test
    void getSummary_shouldReturnDashboardData() throws Exception {
        DashboardSummaryDTO summary = DashboardSummaryDTO.builder()
                .userId(UUID.randomUUID())
                .username("admin")
                .sent(10)
                .failed(2)
                .balanceAmount(new BigDecimal("50000"))
                .balance("TZS 50000")
                .currency("TZS")
                .activeCampaigns(3)
                .today(2)
                .thisWeek(6)
                .thisMonth(10)
                .successRate(83.33)
                .statusBreakdown(Map.of("sent", 80.0, "failed", 20.0, "pending", 0.0, "other", 0.0))
                .build();

        when(dashboardService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.balance").value("TZS 50000"))
                .andExpect(jsonPath("$.activeCampaigns").value(3));
    }
}
