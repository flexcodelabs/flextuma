package com.flexcodelabs.flextuma.modules.notification.services;

import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.senders.WhatsAppSender;
import com.flexcodelabs.flextuma.core.services.SmsSendResult;
import com.flexcodelabs.flextuma.core.services.SmsSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsDispatchWorker {

    private final SmsLogRepository logRepository;
    private final List<SmsSender> smsSenders;
    private final WhatsAppSender whatsAppSender;
    private final NotificationService notificationService;
    private final MessageRetryPolicy retryPolicy;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void dispatch() {
        List<SmsLog> pending = logRepository.findDueMessages(
                SmsLogStatus.PENDING,
                java.time.LocalDateTime.now(),
                org.springframework.data.domain.PageRequest.of(0, 50));

        if (pending.isEmpty()) {
            return;
        }

        log.debug("SmsDispatchWorker: picking up {} PENDING log(s)", pending.size());

		for (SmsLog smsLog : pending) {
			if (markProcessing(smsLog)) {
				send(smsLog);
			}
        }
    }

	private boolean markProcessing(SmsLog smsLog) {
		return logRepository.claimPendingMessage(smsLog.getId(), SmsLogStatus.PENDING,
				SmsLogStatus.PROCESSING) == 1;
    }

    private void send(SmsLog smsLog) {
        SmsConnector connector = smsLog.getConnector();
        smsLog.setDeliveryAttempts(smsLog.getDeliveryAttempts() + 1);
        smsLog.setNextRetryAt(null);

        if (connector == null) {
            log.error("SmsLog [{}] has no connector — marking FAILED", smsLog.getId());
            // No connector can ever be attached to an existing log, so retrying could not help.
            retryPolicy.deadLetter(smsLog, "No connector associated with this log");
            logRepository.save(smsLog);
            return;
        }

        SmsSendResult result;
        try {
            result = smsLog.getWhatsappTemplate() != null ? sendTemplate(smsLog, connector) : sendText(smsLog, connector);
        } catch (Exception e) {
            result = SmsSendResult.failure(e.getMessage(), "SEND_ERROR", null);
        }

        if (result.isSuccess()) {
            smsLog.setStatus(SmsLogStatus.SENT);
            smsLog.setError(null);
            smsLog.setProviderResponse(result.getProviderResponse());
            smsLog.setProviderMessageId(result.getProviderMessageId());
            log.debug("SmsLog [{}] sent successfully via [{}] on attempt {}", smsLog.getId(), connector.getProvider(),
                    smsLog.getDeliveryAttempts());
        } else {
            retryPolicy.onFailure(smsLog, result);
        }

        logRepository.save(smsLog);
    }

    private SmsSendResult sendText(SmsLog smsLog, SmsConnector connector) {
        SmsSender sender = smsSenders.stream()
                .filter(s -> s.getProvider().equalsIgnoreCase(connector.getProvider()))
                .findFirst()
                .orElse(null);

        if (sender == null) {
            log.error("No SmsSender implementation found for provider [{}]", connector.getProvider());
            return SmsSendResult.failure("No sender implementation for provider: " + connector.getProvider(),
                    "NO_SENDER", null);
        }
        return sender.sendSms(connector, smsLog.getRecipient(), smsLog.getContent());
    }

    @SuppressWarnings("unchecked")
    private SmsSendResult sendTemplate(SmsLog smsLog, SmsConnector connector) {
        Map<String, Object> template = smsLog.getWhatsappTemplate();
        Object components = template.get("components");
        SmsSendResult result = whatsAppSender.sendTemplate(connector, smsLog.getRecipient(),
                String.valueOf(template.get("name")), String.valueOf(template.get("language")),
                components instanceof List<?> list ? (List<Map<String, Object>>) list : List.of());
        if (result.isSuccess()) {
            try {
                notificationService.chargeTemplateSend(smsLog.getCreatedBy(), connector, smsLog.getContent(),
                        smsLog.getRecipient());
            } catch (Exception e) {
                // The message is already out; failing here would roll back SENT and resend it.
                log.error("SmsLog [{}] sent but could not be billed: {}", smsLog.getId(), e.getMessage());
            }
        }
        return result;
    }
}
