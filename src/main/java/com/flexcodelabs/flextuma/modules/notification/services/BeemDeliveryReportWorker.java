package com.flexcodelabs.flextuma.modules.notification.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.senders.BeemDeliveryReportClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Polls Beem after its recommended five-minute delivery-report delay. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BeemDeliveryReportWorker {

    private final SmsLogRepository logRepository;
    private final BeemDeliveryReportClient deliveryReportClient;

    @Value("${flextuma.sms.beem.delivery-minimum-delay-minutes}")
    private int minimumReportDelayMinutes = 5;

    @Scheduled(fixedDelayString = "${flextuma.sms.beem.delivery-poll-interval-ms}")
    @Transactional
    public void pollDeliveryReports() {
        List<SmsLog> sentMessages = logRepository
                .findTop50ByStatusAndProviderMessageIdIsNotNullOrderByCreatedAsc(SmsLogStatus.SENT);
        LocalDateTime eligibleBefore = LocalDateTime.now().minusMinutes(minimumReportDelayMinutes);

        sentMessages.stream()
                .filter(this::isBeemMessage)
                .filter(log -> log.getCreated() != null && !log.getCreated().isAfter(eligibleBefore))
                .forEach(this::updateDeliveryStatus);
    }

    private boolean isBeemMessage(SmsLog log) {
        return log.getConnector() != null && "BEEM".equalsIgnoreCase(log.getConnector().getProvider());
    }

    private void updateDeliveryStatus(SmsLog smsLog) {
        try {
            BeemDeliveryReportClient.BeemDeliveryStatus report = deliveryReportClient.lookup(smsLog.getConnector(),
                    smsLog.getRecipient(), smsLog.getProviderMessageId());
            if (report == null || report.getStatus() == null) {
                return;
            }

            switch (report.getStatus().trim().toUpperCase()) {
                case "DELIVERED" -> smsLog.setStatus(SmsLogStatus.DELIVERED);
                case "UNDELIVERED" -> smsLog.setStatus(SmsLogStatus.FAILED);
                default -> {
                    return;
                }
            }
            logRepository.save(smsLog);
        } catch (Exception e) {
            log.warn("Unable to retrieve Beem delivery report for SmsLog [{}]: {}", smsLog.getId(), e.getMessage());
        }
    }
}
