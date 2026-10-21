package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.enums.WhatsAppRelayStatus;
import com.flexcodelabs.flextuma.core.helpers.HmacUtil;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppRelayDeliveryRepository;
import com.flexcodelabs.flextuma.modules.notification.services.MessageRetryPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Relays inbound WhatsApp webhooks to the customer's callback URL. Failed deliveries follow the
 * same Kafka retry-topic style policy as outbound messages ({@link MessageRetryPolicy}): every
 * failure, 4xx included (the customer may fix their endpoint), is re-queued with an exponentially
 * growing delay until the retry window measured from the delivery's creation runs out.
 * Relay-specific {@code flextuma.whatsapp.relay.retry.*} properties override the shared
 * {@code flextuma.messaging.retry.*} ones.
 */
@Slf4j
@Service
public class WhatsAppRelayDispatchWorker {

    private static final int BATCH_SIZE = 50;

    private final WhatsAppRelayDeliveryRepository deliveryRepository;
    private final RestTemplate restTemplate;
    private final MessageRetryPolicy retryPolicy;

    @Autowired
    public WhatsAppRelayDispatchWorker(
            WhatsAppRelayDeliveryRepository deliveryRepository,
            RestTemplate restTemplate,
            @Value("${flextuma.whatsapp.relay.retry.initial-interval-ms:${flextuma.messaging.retry.initial-interval-ms:10000}}") long initialIntervalMs,
            @Value("${flextuma.whatsapp.relay.retry.multiplier:${flextuma.messaging.retry.multiplier:2.0}}") double multiplier,
            @Value("${flextuma.whatsapp.relay.retry.max-interval-ms:${flextuma.messaging.retry.max-interval-ms:3600000}}") long maxIntervalMs,
            @Value("${flextuma.whatsapp.relay.retry.window-ms:${flextuma.messaging.retry.window-ms:43200000}}") long retryWindowMs) {
        this(deliveryRepository, restTemplate,
                new MessageRetryPolicy(initialIntervalMs, multiplier, maxIntervalMs, retryWindowMs));
    }

    public WhatsAppRelayDispatchWorker(WhatsAppRelayDeliveryRepository deliveryRepository, RestTemplate restTemplate,
            MessageRetryPolicy retryPolicy) {
        this.deliveryRepository = deliveryRepository;
        this.restTemplate = restTemplate;
        this.retryPolicy = retryPolicy;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void dispatch() {
        List<WhatsAppRelayDelivery> due = deliveryRepository.findDueDeliveries(WhatsAppRelayStatus.PENDING,
                LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));

        if (due.isEmpty()) {
            return;
        }

        log.debug("WhatsAppRelayDispatchWorker: picking up {} due deliverie(s)", due.size());

        for (WhatsAppRelayDelivery delivery : due) {
            if (markProcessing(delivery)) {
                send(delivery);
            }
        }
    }

    private boolean markProcessing(WhatsAppRelayDelivery delivery) {
        return deliveryRepository.claimPendingDelivery(delivery.getId(), WhatsAppRelayStatus.PENDING,
                WhatsAppRelayStatus.PROCESSING) == 1;
    }

    private void send(WhatsAppRelayDelivery delivery) {
        WhatsAppWebhookConfig config = delivery.getConfig();
        long startedAt = System.currentTimeMillis();

        try {
            restTemplate.postForEntity(config.getCallbackUrl(),
                    new HttpEntity<>(delivery.getPayload(), buildHeaders(delivery, config)), Void.class);
            recordAttempt(delivery, startedAt);
            delivery.setStatus(WhatsAppRelayStatus.SUCCEEDED);
            delivery.setNextRetryAt(null);
            log.debug("WhatsApp relay [{}] delivered successfully for config [{}]", delivery.getId(), config.getId());
        } catch (Exception e) {
            recordAttempt(delivery, startedAt);
            onFailure(delivery, e);
        }

        deliveryRepository.save(delivery);
    }

    private HttpHeaders buildHeaders(WhatsAppRelayDelivery delivery, WhatsAppWebhookConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Flextuma-Event", "whatsapp");
        String secret = config.getSigningSecret();
        if (secret != null && !secret.isBlank()) {
            headers.set("X-Flextuma-Signature-256", "sha256=" + sign(delivery.getPayload(), secret));
        }
        return headers;
    }

    private static String sign(String payload, String secret) {
        try {
            return HmacUtil.sha256Hex(payload, secret);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign relay payload: " + e.getMessage(), e);
        }
    }

    private void recordAttempt(WhatsAppRelayDelivery delivery, long startedAt) {
        delivery.setLatencyMs(System.currentTimeMillis() - startedAt);
        delivery.setLastAttemptAt(LocalDateTime.now());
    }

    /** Schedules the next retry with backoff, or dead-letters the delivery as FAILED once the window closes. */
    private void onFailure(WhatsAppRelayDelivery delivery, Exception e) {
        int attempt = delivery.getRetries() + 1;
        delivery.setRetries(attempt);
        delivery.setLastError(e.getMessage());

        Optional<Duration> delay = retryPolicy.nextRetryDelay(attempt, delivery.getCreated());
        if (delay.isPresent()) {
            delivery.setStatus(WhatsAppRelayStatus.PENDING);
            delivery.setNextRetryAt(LocalDateTime.now().plus(delay.get()));
            log.warn("WhatsApp relay [{}] attempt {} failed, retrying in {}s: {}", delivery.getId(), attempt,
                    delay.get().toSeconds(), e.getMessage());
            return;
        }

        delivery.setStatus(WhatsAppRelayStatus.FAILED);
        delivery.setNextRetryAt(null);
        log.warn("WhatsApp relay [{}] FAILED after {} attempt(s), retry window exhausted: {}", delivery.getId(),
                attempt, e.getMessage());
    }
}
