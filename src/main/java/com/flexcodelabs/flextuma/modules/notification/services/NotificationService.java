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

        @Value("${flextuma.sms.price-per-segment:1.0}")
        private BigDecimal pricePerSegment;

        @Transactional
        public SmsLog queueTemplatedSms(Map<String, String> placeholders, String username) {

                if (username == null) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
                }

                User currentUser = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                                "User not found"));

                UUID tenantId = currentUser.getOrganisation() != null ? currentUser.getOrganisation().getId()
                                : currentUser.getId();
                rateLimiterService.checkRateLimit(tenantId);

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

                SmsSegmentResult segmentResult = segmentCalculator.calculate(finalMessage);
                BigDecimal cost = pricePerSegment.multiply(BigDecimal.valueOf(segmentResult.segments()));

                walletService.debit(currentUser, cost, "SMS send to " + phoneNumber, null);

                SmsLog log = new SmsLog();
                log.setRecipient(phoneNumber);
                log.setContent(finalMessage);
                log.setTemplate(template);
                log.setConnector(connector);
                log.setStatus(SmsLogStatus.PENDING);

                if (placeholders.containsKey("scheduledAt")) {
                        try {
                                log.setScheduledAt(java.time.LocalDateTime.parse(placeholders.get("scheduledAt")));
                        } catch (Exception e) {
                                // Fallback or ignore invalid date format for now
                        }
                }

                return logRepository.save(log);
        }
}