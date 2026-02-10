package com.flexcodelabs.flextuma.modules.sms.services;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.core.helpers.TemplateUtils;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsTemplateRepository;
import com.flexcodelabs.flextuma.core.services.SmsSender;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SmsNotificationService {

    private final SmsTemplateRepository templateRepository;
    private final SmsLogRepository logRepository;
    private final SmsSender smsSender;

    @Async
    public void sendTemplatedSms(String templateCode, String phoneNumber, Map<String, String> placeholders) {
        SmsTemplate template = templateRepository.findByCode(templateCode)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateCode));

        String finalMessage = TemplateUtils.fillTemplate(template.getContent(), placeholders);

        SmsLog log = new SmsLog();
        log.setRecipient(phoneNumber);
        log.setSentContent(finalMessage);
        log.setTemplate(template);
        log.setStatus("PENDING");
        log = logRepository.save(log);

        try {
            String providerId = smsSender.sendSms(null, phoneNumber, finalMessage);

            log.setStatus("SENT");
            log.setProviderResponse(providerId);
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setProviderResponse(e.getMessage());
        }

        logRepository.save(log);
    }
}