package com.flexcodelabs.flextuma.modules.dashboard.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.finance.Wallet;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsCampaignStatus;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.SmsCampaignRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.WalletRepository;
import com.flexcodelabs.flextuma.modules.dashboard.dtos.DashboardNotificationDTO;
import com.flexcodelabs.flextuma.modules.dashboard.dtos.DashboardSummaryDTO;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final EnumSet<SmsLogStatus> SUCCESS_STATUSES = EnumSet.of(SmsLogStatus.SENT, SmsLogStatus.DELIVERED);
    private static final EnumSet<SmsCampaignStatus> ACTIVE_CAMPAIGN_STATUSES = EnumSet.of(
            SmsCampaignStatus.SCHEDULED,
            SmsCampaignStatus.PROCESSING);

    private final CurrentUserResolver currentUserResolver;
    private final WalletRepository walletRepository;
    private final SmsCampaignRepository smsCampaignRepository;
    private final SmsLogRepository smsLogRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryDTO getSummary() {
        User user = getCurrentUser();
        Wallet wallet = walletRepository.findTopByCreatedByOrderByCreatedDesc(user).orElseGet(Wallet::new);

        long successfulMessages = smsLogRepository.countByCreatedByAndStatusIn(user, SUCCESS_STATUSES);
        long failedMessages = smsLogRepository.countByCreatedByAndStatus(user, SmsLogStatus.FAILED);
        long todayMessages = countMessagesSince(user, startOfToday());
        long weeklyMessages = countMessagesSince(user, LocalDateTime.now().minusDays(7));
        long monthlyMessages = countMessagesSince(user, startOfMonth());
        long activeCampaigns = smsCampaignRepository.countByCreatedByAndStatusIn(user, ACTIVE_CAMPAIGN_STATUSES);

        long pendingMessages = smsLogRepository.countByCreatedByAndStatus(user, SmsLogStatus.PENDING);
        long processingMessages = smsLogRepository.countByCreatedByAndStatus(user, SmsLogStatus.PROCESSING);
        long totalStatusCount = successfulMessages + failedMessages + pendingMessages + processingMessages;

        BigDecimal balanceAmount = wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO;
        String currency = wallet.getCurrency() != null ? wallet.getCurrency() : "TZS";

        return DashboardSummaryDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .sent(successfulMessages)
                .failed(failedMessages)
                .balanceAmount(balanceAmount)
                .balance(currency + " " + balanceAmount.stripTrailingZeros().toPlainString())
                .currency(currency)
                .activeCampaigns(activeCampaigns)
                .today(todayMessages)
                .thisWeek(weeklyMessages)
                .thisMonth(monthlyMessages)
                .successRate(calculateSuccessRate(successfulMessages, failedMessages))
                .statusBreakdown(buildStatusBreakdown(successfulMessages, failedMessages, pendingMessages, processingMessages,
                        totalStatusCount))
                .build();
    }

    @Transactional(readOnly = true)
    public Pagination<DashboardNotificationDTO> getRecentNotifications(Pageable pageable) {
        User user = getCurrentUser();
        int safePage = Math.max(pageable.getPageNumber(), 0);
        int safePageSize = Math.max(1, Math.min(pageable.getPageSize(), 100));
        Page<SmsLog> page = smsLogRepository.findByCreatedByOrderByCreatedDesc(user,
                PageRequest.of(safePage, safePageSize));

        List<DashboardNotificationDTO> notifications = page.getContent().stream()
                .map(this::toNotificationDto)
                .toList();

        return Pagination.<DashboardNotificationDTO>builder()
                .page(safePage + 1)
                .total(page.getTotalElements())
                .pageSize(safePageSize)
                .data(notifications)
                .build();
    }

    private long countMessagesSince(User user, LocalDateTime start) {
        return smsLogRepository.countByCreatedByAndStatusInAndCreatedGreaterThanEqual(user, SUCCESS_STATUSES, start);
    }

    private LocalDateTime startOfToday() {
        return LocalDate.now().atStartOfDay();
    }

    private LocalDateTime startOfMonth() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay();
    }

    private double calculateSuccessRate(long successfulMessages, long failedMessages) {
        long totalProcessed = successfulMessages + failedMessages;
        if (totalProcessed == 0) {
            return 0.0;
        }

        return BigDecimal.valueOf(successfulMessages * 100.0 / totalProcessed)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private LinkedHashMap<String, Double> buildStatusBreakdown(long successfulMessages, long failedMessages,
            long pendingMessages, long processingMessages, long totalStatusCount) {
        LinkedHashMap<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("sent", percentage(successfulMessages, totalStatusCount));
        breakdown.put("failed", percentage(failedMessages, totalStatusCount));
        breakdown.put("pending", percentage(pendingMessages, totalStatusCount));
        breakdown.put("other", percentage(processingMessages, totalStatusCount));
        return breakdown;
    }

    private double percentage(long value, long total) {
        if (total == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(value * 100.0 / total)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private DashboardNotificationDTO toNotificationDto(SmsLog smsLog) {
        return DashboardNotificationDTO.builder()
                .id(smsLog.getId())
                .phoneNumber(smsLog.getRecipient())
                .message(smsLog.getContent())
                .status(smsLog.getStatus() != null ? smsLog.getStatus().name().toLowerCase() : null)
                .provider(resolveProvider(smsLog))
                .createdAt(smsLog.getCreated())
                .updatedAt(smsLog.getUpdated())
                .build();
    }

    private String resolveProvider(SmsLog smsLog) {
        if (smsLog.getConnector() == null || smsLog.getConnector().getProvider() == null) {
            return null;
        }
        return smsLog.getConnector().getProvider().toUpperCase();
    }

    private User getCurrentUser() {
        return currentUserResolver.getCurrentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }
}
