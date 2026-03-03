package com.flexcodelabs.flextuma.modules.notification.services;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.helpers.TemplateUtils;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsTemplateRepository;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Handles message queuing only. Actual dispatch is done by
 * {@link com.flexcodelabs.flextuma.modules.notification.services.SmsDispatchWorker}.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

        private final SmsTemplateRepository templateRepository;
        private final SmsLogRepository logRepository;
        private final UserRepository userRepository;
        private final SmsConnectorRepository connectorRepository;

        /**
         * Validates the request, resolves template + connector, renders the message,
         * and persists a {@code PENDING} {@link SmsLog}. The dispatch worker picks it
         * up asynchronously — the caller returns immediately after the log is saved.
         */
        @Transactional
        public SmsLog queueTemplatedSms(Map<String, String> placeholders, String username) {

                if (username == null) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
                }

                User currentUser = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                                "User not found"));

                String providerValue = Optional.ofNullable(placeholders.get("provider"))
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "SMS provider is missing"));

                String templateCode = Optional.ofNullable(placeholders.get("templateCode"))
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "Template is missing"));

                String phoneNumber = Optional.ofNullable(placeholders.get("phoneNumber"))
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "Phone number is missing"));

                SmsTemplate template = templateRepository.findByCreatedByAndCode(currentUser, templateCode)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Template not found or you don't have access to it"));

                SmsConnector connector = connectorRepository
                                .findByCreatedByAndProviderAndActiveTrue(currentUser, providerValue)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "No active SMS connector found for provider [" + providerValue + "]"));

                String finalMessage = TemplateUtils.fillTemplate(template.getContent(), placeholders);

                SmsLog log = new SmsLog();
                log.setRecipient(phoneNumber);
                log.setContent(finalMessage);
                log.setTemplate(template);
                log.setConnector(connector);
                log.setStatus(SmsLogStatus.PENDING);

                return logRepository.save(log);
        }
}