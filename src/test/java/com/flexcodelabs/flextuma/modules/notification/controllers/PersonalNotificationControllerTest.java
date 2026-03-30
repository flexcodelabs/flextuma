package com.flexcodelabs.flextuma.modules.notification.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.notification.PersonalNotification;
import com.flexcodelabs.flextuma.modules.notification.dtos.NotificationSummaryDTO;
import com.flexcodelabs.flextuma.modules.notification.services.PersonalNotificationService;

@ExtendWith(MockitoExtension.class)
class PersonalNotificationControllerTest {

    @Mock
    private PersonalNotificationService service;

    private MockMvc mockMvc;
    private PersonalNotificationController controller;

    @BeforeEach
    void setUp() {
        org.springframework.data.web.PageableHandlerMethodArgumentResolver resolver = new org.springframework.data.web.PageableHandlerMethodArgumentResolver();
        resolver.setOneIndexedParameters(true);
        controller = new PersonalNotificationController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(resolver)
                .build();
    }

    @Test
    void summary_shouldReturnUnreadCountAndNotifications() throws Exception {
        PersonalNotification notification = new PersonalNotification();
        notification.setTitle("Low Balance Alert");

        NotificationSummaryDTO summary = NotificationSummaryDTO.builder()
                .unreadCount(3)
                .notifications(List.of(notification))
                .build();

        when(service.getSummary(5)).thenReturn(summary);

        mockMvc.perform(get("/api/personalNotifications/summary").param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.notifications[0].title").value("Low Balance Alert"));
    }

    @Test
    void markAsRead_shouldReturnNotification() throws Exception {
        UUID id = UUID.randomUUID();
        PersonalNotification notification = new PersonalNotification();
        notification.setId(id);
        notification.setTitle("Campaign Completed");

        when(service.markAsRead(id)).thenReturn(notification);

        mockMvc.perform(post("/api/personalNotifications/{id}/read", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Campaign Completed"));
    }

    @Test
    void markAllAsRead_shouldReturnUpdateCount() throws Exception {
        when(service.markAllAsRead()).thenReturn(Map.of("message", "Notifications marked as read", "updated", 4));
        org.springframework.http.ResponseEntity<Map<String, Object>> response = controller.markAllAsRead();
        assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4, response.getBody().get("updated"));
    }

    @Test
    void getAll_shouldReturnPaginatedNotifications() throws Exception {
        PersonalNotification notification = new PersonalNotification();
        notification.setId(UUID.randomUUID());
        notification.setTitle("System Update");

        Pagination<PersonalNotification> pagination = Pagination.<PersonalNotification>builder()
                .page(1)
                .total(1)
                .pageSize(10)
                .data(List.of(notification))
                .build();

        when(service.findAllPaginated(org.mockito.ArgumentMatchers.any(Pageable.class),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(pagination);
        when(service.getPropertyName()).thenReturn("personalNotifications");

        mockMvc.perform(get("/api/personalNotifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalNotifications[0].title").value("System Update"));
    }
}
