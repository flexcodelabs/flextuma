package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.enums.WhatsAppRelayStatus;
import com.flexcodelabs.flextuma.core.helpers.HmacUtil;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppRelayDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppRelayDispatchWorker {

    private static final int MAX_RETRIES = 3;

    private final WhatsAppRelayDeliveryRepository deliveryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void dispatch() {
        List<WhatsAppRelayDelivery> pending = deliveryRepository.findTop50ByStatusOrderByCreatedAsc(WhatsAppRelayStatus.PENDING);

        if (pending.isEmpty()) {
            return;
        }

        log.debug("WhatsAppRelayDispatchWorker: picking up {} PENDING deliverie(s)", pending.size());

        for (WhatsAppRelayDelivery delivery : pending) {
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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Flextuma-Event", "whatsapp");
            if (config.getSigningSecret() != null && !config.getSigningSecret().isBlank()) {
                headers.set("X-Flextuma-Signature-256", "sha256=" + HmacUtil.sha256Hex(delivery.getPayload(), config.getSigningSecret()));
            }

            restTemplate.postForEntity(config.getCallbackUrl(), new HttpEntity<>(delivery.getPayload(), headers), Void.class);

            delivery.setStatus(WhatsAppRelayStatus.SUCCEEDED);
            delivery.setLatencyMs(System.currentTimeMillis() - startedAt);
            delivery.setLastAttemptAt(LocalDateTime.now());
            log.debug("WhatsApp relay [{}] delivered successfully for config [{}]", delivery.getId(), config.getId());
        } catch (Exception e) {
            int retries = delivery.getRetries() + 1;
            delivery.setRetries(retries);
            delivery.setLatencyMs(System.currentTimeMillis() - startedAt);
            delivery.setLastAttemptAt(LocalDateTime.now());
            delivery.setLastError(e.getMessage());

            if (retries >= MAX_RETRIES) {
                delivery.setStatus(WhatsAppRelayStatus.FAILED);
                log.warn("WhatsApp relay [{}] FAILED after {} retries: {}", delivery.getId(), retries, e.getMessage());
            } else {
                delivery.setStatus(WhatsAppRelayStatus.PENDING);
                log.warn("WhatsApp relay [{}] retry {}/{}: {}", delivery.getId(), retries, MAX_RETRIES, e.getMessage());
            }
        }

        deliveryRepository.save(delivery);
    }
}
