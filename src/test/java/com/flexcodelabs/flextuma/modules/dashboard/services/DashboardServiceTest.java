package com.flexcodelabs.flextuma.modules.dashboard.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.finance.Wallet;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsCampaignStatus;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.SmsCampaignRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.WalletRepository;
import com.flexcodelabs.flextuma.modules.dashboard.dtos.DashboardNotificationDTO;
import com.flexcodelabs.flextuma.modules.dashboard.dtos.DashboardSummaryDTO;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private SmsCampaignRepository smsCampaignRepository;

    @Mock
    private SmsLogRepository smsLogRepository;

    @Test
    void getSummary_shouldReturnUserScopedDashboardMetrics() {
        DashboardService service = new DashboardService(currentUserResolver, walletRepository, smsCampaignRepository,
                smsLogRepository);
        User user = createUser();
        Wallet wallet = new Wallet();
        wallet.setBalance(new BigDecimal("50000"));
        wallet.setCurrency("TZS");

        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(walletRepository.findTopByCreatedByOrderByCreatedDesc(user)).thenReturn(Optional.of(wallet));
        when(smsLogRepository.countByCreatedByAndStatusIn(eq(user), any())).thenReturn(20L);
        when(smsLogRepository.countByCreatedByAndStatus(user, SmsLogStatus.FAILED)).thenReturn(5L);
        when(smsLogRepository.countByCreatedByAndStatus(user, SmsLogStatus.PENDING)).thenReturn(3L);
        when(smsLogRepository.countByCreatedByAndStatus(user, SmsLogStatus.PROCESSING)).thenReturn(2L);
        when(smsLogRepository.countByCreatedByAndStatusInAndCreatedGreaterThanEqual(eq(user), any(), any()))
                .thenReturn(4L, 10L, 20L);
        when(smsCampaignRepository.countByCreatedByAndStatusIn(eq(user), any())).thenReturn(3L);

        DashboardSummaryDTO summary = service.getSummary();

        assertEquals("admin", summary.username());
        assertEquals(20L, summary.sent());
        assertEquals(5L, summary.failed());
        assertEquals("TZS 50000", summary.balance());
        assertEquals(3L, summary.activeCampaigns());
        assertEquals(4L, summary.today());
        assertEquals(10L, summary.thisWeek());
        assertEquals(20L, summary.thisMonth());
        assertEquals(80.0, summary.successRate());
        assertEquals(66.67, summary.statusBreakdown().get("sent"));
    }

    @Test
    void getRecentNotifications_shouldReturnMappedLogsForCurrentUser() {
        DashboardService service = new DashboardService(currentUserResolver, walletRepository, smsCampaignRepository,
                smsLogRepository);
        User user = createUser();
        SmsConnector connector = new SmsConnector();
        connector.setProvider("beem");

        SmsLog log = new SmsLog();
        log.setId(UUID.randomUUID());
        log.setRecipient("+255700000000");
        log.setContent("hello");
        log.setStatus(SmsLogStatus.DELIVERED);
        log.setConnector(connector);
        log.setCreated(LocalDateTime.of(2026, 3, 29, 10, 0));
        log.setUpdated(LocalDateTime.of(2026, 3, 29, 10, 5));

        Page<SmsLog> page = new PageImpl<>(List.of(log), PageRequest.of(1, 15), 31);

        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(smsLogRepository.findByCreatedByOrderByCreatedDesc(eq(user), any(Pageable.class))).thenReturn(page);

        Pagination<DashboardNotificationDTO> notifications = service.getRecentNotifications(PageRequest.of(1, 15));

        assertEquals(2, notifications.getPage());
        assertEquals(31, notifications.getTotal());
        assertEquals(15, notifications.getPageSize());
        assertEquals(1, notifications.getData().size());
        assertEquals("+255700000000", notifications.getData().get(0).phoneNumber());
        assertEquals("delivered", notifications.getData().get(0).status());
        assertEquals("BEEM", notifications.getData().get(0).provider());
    }

    @Test
    void getSummary_shouldThrowUnauthorized_whenNoCurrentUser() {
        DashboardService service = new DashboardService(currentUserResolver, walletRepository, smsCampaignRepository,
                smsLogRepository);
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, service::getSummary);
    }

    private User createUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("admin");
        return user;
    }
}
