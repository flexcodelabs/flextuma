package com.flexcodelabs.flextuma.modules.notification.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.notification.PersonalNotification;
import com.flexcodelabs.flextuma.core.enums.PersonalNotificationType;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.PersonalNotificationRepository;
import com.flexcodelabs.flextuma.core.security.SecurityUtils;
import com.flexcodelabs.flextuma.core.services.EntityResponseInitializer;
import com.flexcodelabs.flextuma.modules.notification.dtos.NotificationSummaryDTO;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class PersonalNotificationServiceTest {

    @Mock
    private PersonalNotificationRepository repository;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private EntityResponseInitializer entityResponseInitializer;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PersonalNotificationService service;
    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        service = new PersonalNotificationService(repository, currentUserResolver);
        service.setEntityResponseInitializer(entityResponseInitializer);
        service.setEventPublisher(eventPublisher);
        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserAuthorities).thenReturn(java.util.Set.of("ALL"));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void getSummary_shouldReturnUnreadCountAndRecentNotifications() {
        User user = user("admin");
        PersonalNotification notification = new PersonalNotification();
        notification.setTitle("Low Balance Alert");

        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(repository.findByCreatedByOrderByCreatedDesc(eq(user), any())).thenReturn(List.of(notification));
        when(repository.countByCreatedByAndReadAtIsNull(user)).thenReturn(3L);

        NotificationSummaryDTO summary = service.getSummary(5);

        assertEquals(3L, summary.unreadCount());
        assertEquals(1, summary.notifications().size());
        verify(entityResponseInitializer).initialize(notification);
    }

    @Test
    void notifyLowBalance_shouldCreateUnreadLowBalanceNotification() {
        User user = user("admin");
        ArgumentCaptor<PersonalNotification> captor = ArgumentCaptor.forClass(PersonalNotification.class);

        when(repository.findByCreatedByAndTypeAndReadAtIsNull(user, PersonalNotificationType.LOW_BALANCE_ALERT))
                .thenReturn(Optional.empty());

        service.notifyLowBalance(user, new BigDecimal("9500"));

        verify(repository).save(captor.capture());
        PersonalNotification saved = captor.getValue();
        assertEquals("Low Balance Alert", saved.getTitle());
        assertEquals(PersonalNotificationType.LOW_BALANCE_ALERT, saved.getType());
        assertEquals("/finance/wallet", saved.getLinkUrl());
        assertEquals("LOW_BALANCE_" + user.getId(), saved.getCode());
    }

    @Test
    void markAsRead_shouldSetReadTimestamp() {
        User user = user("admin");
        UUID notificationId = UUID.randomUUID();
        PersonalNotification notification = new PersonalNotification();
        notification.setId(notificationId);

        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(repository.findByIdAndCreatedBy(notificationId, user)).thenReturn(Optional.of(notification));
        when(repository.save(notification)).thenReturn(notification);

        PersonalNotification result = service.markAsRead(notificationId);

        assertNotNull(result.getReadAt());
        verify(entityResponseInitializer).initialize(notification);
    }

    @Test
    void markAllAsRead_shouldReturnUpdatedCount() {
        User user = user("admin");
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(repository.markAllAsRead(eq(user), any(LocalDateTime.class))).thenReturn(4);

        Map<String, Object> response = service.markAllAsRead();

        assertEquals(4, response.get("updated"));
    }

    private User user(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        return user;
    }
}
