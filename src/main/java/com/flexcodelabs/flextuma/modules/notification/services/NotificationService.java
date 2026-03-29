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
import com.flexcodelabs.flextuma.core.helpers.SmsSegmentResult;
import com.flexcodelabs.flextuma.core.helpers.SmsSegmentCalculator;
import com.flexcodelabs.flextuma.core.helpers.TemplateUtils;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsTemplateRepository;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;
import com.flexcodelabs.flextuma.core.services.EntityResponseInitializer;
import com.flexcodelabs.flextuma.modules.finance.services.WalletService;
import com.flexcodelabs.flextuma.core.services.RateLimiterService;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

        private final SmsTemplateRepository templateRepository;
        private final SmsLogRepository logRepository;
        private final UserRepository userRepository;
        private final SmsConnectorRepository connectorRepository;
        private final WalletService walletService;
        private final RateLimiterService rateLimiterService;
        private final SmsSegmentCalculator segmentCalculator;
        private final EntityResponseInitializer entityResponseInitializer;

        @Value("${flextuma.sms.price-per-segment:1.0}")
        private BigDecimal pricePerSegment;

        @Transactional
        public SmsLog queueTemplatedSms(Map<String, String> placeholders, String username) {
                User currentUser = getUser(username);
                checkRateLimit(currentUser);

                String providerValue = getRequiredField(placeholders, "provider");
                String templateCode = getRequiredField(placeholders, "templateCode");
                String phoneNumber = getRequiredField(placeholders, "phoneNumber");

                SmsTemplate template = templateRepository.findByCreatedByAndCode(currentUser, templateCode)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Template not found or you don't have access to it"));

                SmsConnector connector = getConnector(currentUser, providerValue);

                String finalMessage = TemplateUtils.fillTemplate(template.getContent(), placeholders);

                return processAndSaveSms(currentUser, connector, phoneNumber, finalMessage, template, placeholders);
        }

        @Transactional
        public SmsLog queueRawSms(Map<String, String> payload, String username) {
                User currentUser = getUser(username);
                checkRateLimit(currentUser);

                String providerValue = getRequiredField(payload, "provider");
                String content = getRequiredField(payload, "message");
                String phoneNumber = getRequiredField(payload, "phoneNumber");

                if (containsUnreplacedVariables(content)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Message contains unreplaced template variables. Please ensure all variables like {{variable}} are properly replaced.");
                }

                SmsConnector connector = getConnector(currentUser, providerValue);

                return processAndSaveSms(currentUser, connector, phoneNumber, content, null, payload);
        }

        private User getUser(String username) {
                if (username == null) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
                }
                return userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                                "User not found"));
        }

        private void checkRateLimit(User user) {
                UUID tenantId = user.getOrganisation() != null ? user.getOrganisation().getId() : user.getId();
                rateLimiterService.checkRateLimit(tenantId);
        }

        private String getRequiredField(Map<String, String> data, String key) {
                return Optional.ofNullable(data.get(key))
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                key + " is missing"));
        }

        private boolean containsUnreplacedVariables(String content) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{[^}]*\\}");
                java.util.regex.Matcher matcher = pattern.matcher(content);
                return matcher.find();
        }

        private SmsConnector getConnector(User user, String provider) {
                Optional<SmsConnector> connector = connectorRepository.findByCreatedByAndProviderAndActiveTrue(user,
                                provider);
                if (connector.isPresent()) {
                        return connector.get();
                }

                return connectorRepository.findByProviderAndCode(provider, provider + "_SYSTEM")
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "No active SMS connector found for provider [" + provider + "]"));
        }

        private SmsLog processAndSaveSms(User user, SmsConnector connector, String phoneNumber, String content,
                        SmsTemplate template, Map<String, String> metadata) {
                SmsSegmentResult segmentResult = segmentCalculator.calculate(content);
                if (connector.getCode() != null && connector.getCode().equals(connector.getProvider() + "_SYSTEM")) {
                        BigDecimal cost = BigDecimal.valueOf(Math.ceil(BigDecimal.valueOf(segmentResult.segments())
                                        .divide(pricePerSegment).doubleValue()));
                        walletService.debit(user, cost, "SMS send to " + phoneNumber, null);
                }

                SmsLog log = new SmsLog();
                log.setRecipient(phoneNumber);
                log.setContent(content);
                log.setTemplate(template);
                log.setConnector(connector);
                log.setStatus(SmsLogStatus.PENDING);
                log.setCreatedBy(user);

                if (metadata.containsKey("scheduledAt")) {
                        try {
                                log.setScheduledAt(java.time.LocalDateTime.parse(metadata.get("scheduledAt")));
                        } catch (Exception e) {
                                // Ignore invalid date format and fallback to no-scheduling
                        }
                }

                SmsLog savedLog = logRepository.save(log);
                entityResponseInitializer.initialize(savedLog);
                return savedLog;
        }
}
