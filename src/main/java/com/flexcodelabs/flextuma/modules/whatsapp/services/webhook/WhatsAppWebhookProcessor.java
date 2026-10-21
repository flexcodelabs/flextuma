package com.flexcodelabs.flextuma.modules.whatsapp.services.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppMetaApp;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.helpers.HmacUtil;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppRelayDeliveryRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;

/** Applies one signature-valid Meta delivery to a single webhook configuration. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppWebhookProcessor {
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final WhatsAppWebhookConfigRepository configRepository;
    private final WhatsAppRelayDeliveryRepository relayDeliveryRepository;
    private final WhatsAppWebhookEventRecorder eventRecorder;
    private final WhatsAppStatusUpdater statusUpdater;
    private final WhatsAppInboundMessageIngestor inboundMessageIngestor;
    private final ObjectMapper objectMapper;

    public void process(WhatsAppWebhookConfig config, Map<String, Object> payload) {
        config.setLastEventAt(LocalDateTime.now());
        if (config.getDisplayPhoneNumber() == null) {
            WhatsAppPayloads.displayPhoneNumberOfPayload(payload).ifPresent(config::setDisplayPhoneNumber);
        }
        configRepository.save(config);
        eventRecorder.record(config, payload);
        statusUpdater.updateDeliveryStatus(payload);
        inboundMessageIngestor.ingest(config, payload);
        statusUpdater.updateTemplateStatus(config, payload);
        relay(config, payload);
        log.info("Processed WhatsApp webhook for config [{}]: {} change(s)", config.getId(), WhatsAppPayloads.changes(payload).size());
    }

    /** The config's own app secret wins; otherwise a linked Meta app's secret is used, since a
     * number subscribed to that app is signed with it on every callback URL. */
    public boolean validSignature(WhatsAppWebhookConfig config, String rawPayload, String signature) {
        String secret = isBlank(config.getAppSecret()) ? metaAppSecret(config.getMetaApp()) : config.getAppSecret();
        return validSignature(secret, rawPayload, signature);
    }

    public boolean validSignature(WhatsAppMetaApp app, String rawPayload, String signature) {
        return validSignature(app.getAppSecret(), rawPayload, signature);
    }

    private boolean validSignature(String secret, String rawPayload, String signature) {
        if (isBlank(secret)) {
            return true;
        }
        if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        try {
            String expected = SIGNATURE_PREFIX + HmacUtil.sha256Hex(rawPayload, secret);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            log.warn("Unable to compute WhatsApp webhook signature: {}", e.getMessage());
            return false;
        }
    }

    private void relay(WhatsAppWebhookConfig config, Map<String, Object> payload) {
        if (isBlank(config.getCallbackUrl())) {
            return;
        }
        try {
            WhatsAppRelayDelivery delivery = new WhatsAppRelayDelivery();
            delivery.setConfig(config);
            delivery.setCreatedBy(config.getCreatedBy());
            delivery.setPayload(objectMapper.writeValueAsString(payload));
            relayDeliveryRepository.save(delivery);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Unable to queue WhatsApp relay for config [{}]: {}", config.getId(), e.getMessage());
        }
    }

    private static String metaAppSecret(WhatsAppMetaApp app) {
        return app == null ? null : app.getAppSecret();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
