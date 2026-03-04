package com.flexcodelabs.flextuma.modules.webhook.controllers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.flexcodelabs.flextuma.core.entities.connector.ConnectorConfig;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.modules.connector.services.ConnectorConfigService;
import com.flexcodelabs.flextuma.modules.connector.services.DataHydratorService;
import com.flexcodelabs.flextuma.modules.notification.services.NotificationService;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.webhooks.DlrParser;
import com.flexcodelabs.flextuma.core.webhooks.DlrResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Receives Delivery Report (DLR) callbacks from SMS providers.
 *
 * <p>
 * Endpoint: {@code POST /api/webhooks/sms/{provider}/dlr}
 *
 * <p>
 * The {@code provider} path variable must match the
 * {@code SmsConnector.provider}
 * value (e.g. {@code beem}, {@code next}). Matching is case-insensitive.
 *
 * <p>
 * The endpoint is unauthenticated — providers POST here without a session.
 * CSRF is disabled globally so no token is required.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
public class SmsWebhookController {

    private final SmsLogRepository logRepository;
    private final List<DlrParser> dlrParsers;
    private final ConnectorConfigService configService;
    private final DataHydratorService hydratorService;
    private final NotificationService notificationService;

    public SmsWebhookController(SmsLogRepository logRepository, List<DlrParser> dlrParsers,
            ConnectorConfigService configService, DataHydratorService hydratorService,
            NotificationService notificationService) {
        this.logRepository = logRepository;
        this.dlrParsers = dlrParsers;
        this.configService = configService;
        this.hydratorService = hydratorService;
        this.notificationService = notificationService;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Void> deliveryReport(
            @PathVariable String provider,
            @RequestBody Map<String, Object> payload) {

        log.debug("DLR received from provider [{}]: {}", provider, payload);

        DlrParser parser = dlrParsers.stream()
                .filter(p -> p.getProvider() != null && p.getProvider().equalsIgnoreCase(provider))
                .findFirst()
                .orElse(null);

        if (parser == null) {
            log.warn("No DLR parser registered for provider [{}] — ignoring (parsers={})", provider, dlrParsers.size());
            return ResponseEntity.ok().build();
        }

        DlrResult result = parser.parse(payload);

        if (result.messageId() == null || result.messageId().isBlank()) {
            log.warn("DLR from [{}] missing message ID — payload: {}", provider, payload);
            return ResponseEntity.ok().build();
        }

        if (result.status() == null) {
            log.debug("DLR from [{}] has intermediate status [{}] — no update needed", provider, result.rawStatus());
            return ResponseEntity.ok().build();
        }

        Optional<SmsLog> logOpt = logRepository.findByProviderResponse(result.messageId());

        if (logOpt.isEmpty()) {
            log.warn("DLR from [{}]: no SmsLog found for messageId [{}]", provider, result.messageId());
            return ResponseEntity.ok().build();
        }

        SmsLog smsLog = logOpt.get();

        if (SmsLogStatus.SENT.equals(smsLog.getStatus()) && SmsLogStatus.FAILED.equals(result.status())) {
            log.warn("DLR: ignoring FAILED update for already-SENT log [{}]", smsLog.getId());
            return ResponseEntity.ok().build();
        }

        smsLog.setStatus(result.status());
        logRepository.save(smsLog);

        log.info("DLR: SmsLog [{}] updated to [{}] via provider [{}]", smsLog.getId(), result.status(), provider);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/sms")
    public ResponseEntity<Map<String, Object>> triggerDispatch(
            @PathVariable java.util.UUID id,
            @RequestBody DispatchRequest request) {

        ConnectorConfig config = configService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector config not found"));

        List<Map<String, String>> recipients = hydratorService.getRecipients(config.getTenantId(),
                request.getFilterQuery());

        String username = config.getCreatedBy() != null ? config.getCreatedBy().getUsername() : "system";

        int queuedCount = 0;
        for (Map<String, String> recipient : recipients) {
            recipient.put("templateCode", request.getTemplateCode());
            recipient.put("provider", request.getProvider());
            try {
                notificationService.queueTemplatedSms(recipient, username);
                queuedCount++;
            } catch (Exception e) {
                log.error("Failed to queue templated SMS for recipient via webhook trigger", e);
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Successfully queued messages",
                "queued", queuedCount,
                "totalFetched", recipients.size()));
    }
}
