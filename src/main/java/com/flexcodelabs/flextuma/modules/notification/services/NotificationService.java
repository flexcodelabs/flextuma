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
import com.flexcodelabs.flextuma.core.services.EntityAssociationReferenceResolver;
import com.flexcodelabs.flextuma.core.services.EntityResponseInitializer;
import com.flexcodelabs.flextuma.modules.finance.services.WalletService;
import com.flexcodelabs.flextuma.core.services.RateLimiterService;
import com.flexcodelabs.flextuma.core.security.ApiTokenContext;
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
        private final EntityAssociationReferenceResolver entityAssociationReferenceResolver;

        @Value("${flextuma.sms.price-per-segment:1.0}")
        private BigDecimal pricePerSegment;

        @Value("${flextuma.system-connectors.daily-message-limit-per-user:1000}")
        private long systemConnectorDailyMessageLimit;

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

                SmsConnector connector = getConnector(currentUser, providerValue, placeholders.get("connectorId"));

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

                SmsConnector connector = getConnector(currentUser, providerValue, payload.get("connectorId"));

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

        private SmsConnector getConnector(User user, String provider, String connectorId) {
                if (connectorId != null && !connectorId.isBlank()) {
                        SmsConnector connector;
                        try {
                                connector = connectorRepository.findByIdAndActiveTrue(UUID.fromString(connectorId))
                                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active connector not found"));
                        } catch (IllegalArgumentException e) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "connectorId must be a UUID");
                        }
                        if (!provider.equalsIgnoreCase(connector.getProvider())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "connectorId does not match provider");
                        if (!isSystemConnector(connector) && (connector.getCreatedBy() == null || !connector.getCreatedBy().getId().equals(user.getId()))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this connector");
                        enforceTokenConnectorGrant(connector);
                        return connector;
                }
                Optional<SmsConnector> connector = connectorRepository.findByCreatedByAndProviderAndActiveTrue(user,
                                provider);
                if (connector.isPresent()) {
                        enforceTokenConnectorGrant(connector.get());
                        return connector.get();
                }

                SmsConnector systemConnector = connectorRepository.findByProviderAndCode(provider, provider + "_SYSTEM")
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "No active SMS connector found for provider [" + provider + "]"));
                enforceTokenConnectorGrant(systemConnector);
                return systemConnector;
        }

        private boolean isSystemConnector(SmsConnector connector) {
                return connector.getCode() != null && (connector.getProvider() + "_SYSTEM").equalsIgnoreCase(connector.getCode());
        }

        private void enforceTokenConnectorGrant(SmsConnector connector) {
                ApiTokenContext.TokenGrant grant = ApiTokenContext.get();
                if (grant == null) return;
                if (!grant.allows(ApiTokenContext.SEND_MESSAGES)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "API token is not allowed to send messages");
                if (isSystemConnector(connector)) {
                        if (!grant.allowSystemConnectors()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "API token is not allowed to use shared system connectors");
                } else if (!grant.allowsConnector(connector.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "API token is not allowed to use this connector");
        }

        private SmsLog processAndSaveSms(User user, SmsConnector connector, String phoneNumber, String content,
                        SmsTemplate template, Map<String, String> metadata) {
                SmsSegmentResult segmentResult = segmentCalculator.calculate(content);
                if (isSystemConnector(connector)) {
                        enforceSystemConnectorDailyLimit(user, connector);
                        BigDecimal cost = pricePerSegment.multiply(BigDecimal.valueOf(segmentResult.segments()));
                        walletService.debit(user, cost, "System connector " + connector.getProvider() + " send to " + phoneNumber, null);
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

                entityAssociationReferenceResolver.resolve(log);
                SmsLog savedLog = logRepository.save(log);
                entityResponseInitializer.initialize(savedLog);
                return savedLog;
        }

        private void enforceSystemConnectorDailyLimit(User user, SmsConnector connector) {
                if (systemConnectorDailyMessageLimit <= 0) return;
                long sendsToday = logRepository.countByCreatedByAndConnectorAndCreatedGreaterThanEqual(user, connector,
                                java.time.LocalDate.now().atStartOfDay());
                if (sendsToday >= systemConnectorDailyMessageLimit) {
                        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                                        "Daily system connector message limit reached");
                }
        }
}
