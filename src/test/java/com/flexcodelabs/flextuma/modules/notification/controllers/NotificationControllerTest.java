package com.flexcodelabs.flextuma.modules.notification.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.modules.dashboard.dtos.DashboardNotificationDTO;
import com.flexcodelabs.flextuma.modules.dashboard.services.DashboardService;
import com.flexcodelabs.flextuma.modules.notification.services.NotificationService;

import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @Mock
    private DashboardService dashboardService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(notificationService, dashboardService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listRecentNotifications_shouldReturnDashboardNotifications() throws Exception {
        DashboardNotificationDTO notification = DashboardNotificationDTO.builder()
                .phoneNumber("+255700000000")
                .message("hello")
                .status("sent")
                .provider("BEEM")
                .createdAt(LocalDateTime.of(2026, 3, 29, 12, 0))
                .build();

        Pagination<DashboardNotificationDTO> pagination = Pagination.<DashboardNotificationDTO>builder()
                .page(2)
                .total(11)
                .pageSize(25)
                .data(List.of(notification))
                .build();

        when(dashboardService.getRecentNotifications(PageRequest.of(1, 25))).thenReturn(pagination);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/notifications")
                .param("page", "2")
                .param("pageSize", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.total").value(11))
                .andExpect(jsonPath("$.pageSize").value(25))
                .andExpect(jsonPath("$.data[0].phoneNumber").value("+255700000000"))
                .andExpect(jsonPath("$.data[0].status").value("sent"));
    }

    @Test
    void send_shouldReturnSmsLog_whenParametersValid() throws Exception {
        Map<String, String> variables = new HashMap<>();
        variables.put("phoneNumber", "255700000000");
        variables.put("templateCode", "OTP");
        variables.put("provider", "beem");

        SmsLog queued = new SmsLog();
        queued.setStatus(SmsLogStatus.PENDING);
        queued.setRecipient("255700000000");

        when(notificationService.queueTemplatedSms(any(), eq("testuser"))).thenReturn(queued);

        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(variables))
                .principal(() -> "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.recipient").value("255700000000"));
    }
}
