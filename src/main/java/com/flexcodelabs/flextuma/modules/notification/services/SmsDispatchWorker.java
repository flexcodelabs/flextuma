package com.flexcodelabs.flextuma.modules.notification.services;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.services.SmsSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsDispatchWorker {

    private static final int MAX_RETRIES = 3;

    private final SmsLogRepository logRepository;
    private final List<SmsSender> smsSenders;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void dispatch() {
        List<SmsLog> pending = logRepository.findTop50ByStatusOrderByCreatedAsc(SmsLogStatus.PENDING);

        if (pending.isEmpty()) {
            return;
        }

        log.debug("SmsDispatchWorker: picking up {} PENDING log(s)", pending.size());

        for (SmsLog smsLog : pending) {
            markProcessing(smsLog);
            send(smsLog);
        }
    }

    private void markProcessing(SmsLog smsLog) {
        smsLog.setStatus(SmsLogStatus.PROCESSING);
        logRepository.save(smsLog);
    }

    private void send(SmsLog smsLog) {
        SmsConnector connector = smsLog.getConnector();

        if (connector == null) {
            log.error("SmsLog [{}] has no connector — marking FAILED", smsLog.getId());
            smsLog.setStatus(SmsLogStatus.FAILED);
            smsLog.setError("No connector associated with this log");
            logRepository.save(smsLog);
            return;
        }

        SmsSender sender = smsSenders.stream()
                .filter(s -> s.getProvider().equalsIgnoreCase(connector.getProvider()))
                .findFirst()
                .orElse(null);

        if (sender == null) {
            log.error("No SmsSender implementation found for provider [{}]", connector.getProvider());
            smsLog.setStatus(SmsLogStatus.FAILED);
            smsLog.setError("No sender implementation for provider: " + connector.getProvider());
            logRepository.save(smsLog);
            return;
        }

        try {
            String providerResponse = sender.sendSms(connector, smsLog.getRecipient(), smsLog.getContent());
            smsLog.setStatus(SmsLogStatus.SENT);
            smsLog.setProviderResponse(providerResponse);
            log.debug("SmsLog [{}] sent successfully via [{}]", smsLog.getId(), connector.getProvider());
        } catch (Exception e) {
            int retries = smsLog.getRetries() + 1;
            smsLog.setRetries(retries);
            smsLog.setError(e.getMessage());

            if (retries >= MAX_RETRIES) {
                smsLog.setStatus(SmsLogStatus.FAILED);
                log.warn("SmsLog [{}] FAILED after {} retries: {}", smsLog.getId(), retries, e.getMessage());
            } else {
                smsLog.setStatus(SmsLogStatus.PENDING);
                log.warn("SmsLog [{}] retry {}/{}: {}", smsLog.getId(), retries, MAX_RETRIES, e.getMessage());
            }
        }

        logRepository.save(smsLog);
    }
}
