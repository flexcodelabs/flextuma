package com.flexcodelabs.flextuma.modules.auth.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.flexcodelabs.flextuma.core.dtos.UsernameAvailabilityDto;
import com.flexcodelabs.flextuma.core.exceptions.RateLimitExceededException;
import com.flexcodelabs.flextuma.core.services.PublicEndpointRateLimitService;
import com.flexcodelabs.flextuma.modules.auth.services.UserService;

@ExtendWith(MockitoExtension.class)
class PublicUserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private PublicEndpointRateLimitService rateLimitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PublicUserController controller = new PublicUserController(userService, rateLimitService);
        ReflectionTestUtils.setField(controller, "maxRequestsPerWindow", 20);
        ReflectionTestUtils.setField(controller, "windowSeconds", 60);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void checkUsernameAvailability_shouldRecordAgainstRateLimiter_beforeReturningResult() throws Exception {
        when(userService.checkUsernameAvailability("jane"))
                .thenReturn(new UsernameAvailabilityDto("jane", true, List.of()));

        mockMvc.perform(get("/api/public/users/username-availability").param("username", "jane"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        verify(rateLimitService).checkAndRecord(eq("username-availability"), any(), eq(20), eq(60));
    }

    @Test
    void checkUsernameAvailability_shouldRejectWithTooManyRequests_whenRateLimited() throws Exception {
        doThrow(new RateLimitExceededException("Too many requests.", 30))
                .when(rateLimitService).checkAndRecord(eq("username-availability"), any(), eq(20), eq(60));

        mockMvc.perform(get("/api/public/users/username-availability").param("username", "jane"))
                .andExpect(status().isTooManyRequests());

        verify(userService, never()).checkUsernameAvailability(any());
    }
}
