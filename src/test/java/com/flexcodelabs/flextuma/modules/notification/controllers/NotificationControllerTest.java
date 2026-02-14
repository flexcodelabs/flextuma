package com.flexcodelabs.flextuma.modules.notification.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.modules.notification.services.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Principal principal;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(notificationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void sendSms_shouldQueueSms_whenParametersValid() throws Exception {
        Map<String, String> variables = new HashMap<>();
        variables.put("phone", "1234567890");
        variables.put("code", "1234");
        // Principal mock setup if needed, but we pass it directly

        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(variables))
                .principal(() -> "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("SMS request queued successfully"));

        verify(notificationService).sendTemplatedSms(any(), eq("testuser"));
    }
}
