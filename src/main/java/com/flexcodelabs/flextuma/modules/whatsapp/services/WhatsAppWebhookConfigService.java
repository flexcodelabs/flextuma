package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.enums.WhatsAppRelayStatus;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppActivityItemDTO;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppWebhookOverviewDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import com.flexcodelabs.flextuma.core.helpers.TokenGenerator;

@Service @RequiredArgsConstructor
public class WhatsAppWebhookConfigService extends BaseService<WhatsAppWebhookConfig> {
    private final WhatsAppWebhookConfigRepository repository;
    private final WhatsAppInboxMessageService inboxMessageService;
    private final WhatsAppRelayDeliveryService relayDeliveryService;
    @Value("${flextuma.public-base-url:}") private String publicBaseUrl;
    protected JpaRepository<WhatsAppWebhookConfig, UUID> getRepository() { return repository; }
    protected JpaSpecificationExecutor<WhatsAppWebhookConfig> getRepositoryAsExecutor() { return repository; }
    // Each configuration is tenant-scoped by BaseService, so every signed-in
    // user can manage only their own WhatsApp webhook configurations.
    protected String getReadPermission() { return "ALL"; }
    protected String getAddPermission() { return "ALL"; }
    protected String getUpdatePermission() { return "ALL"; }
    protected String getDeletePermission() { return "ALL"; }
    public String getEntityPlural() { return WhatsAppWebhookConfig.NAME_PLURAL; }
    protected String getEntitySingular() { return WhatsAppWebhookConfig.NAME_SINGULAR; }
    public String getPropertyName() { return WhatsAppWebhookConfig.PLURAL; }
    protected String getTableName() { return "whatsapp_webhook_config"; }

    @Override protected void onPreSave(WhatsAppWebhookConfig entity) { provisionMetaCallback(entity); validate(entity); }
    @Override protected WhatsAppWebhookConfig onPreUpdate(WhatsAppWebhookConfig entity, WhatsAppWebhookConfig old) {
        // Meta-facing values are owned by Flextuma, rather than being supplied or overwritten by a client update.
        entity.setVerifyToken(old.getVerifyToken());
        entity.setCallbackToken(old.getCallbackToken());
        entity.setMetaCallbackUrl(old.getMetaCallbackUrl());
        entity.setLastVerifiedAt(old.getLastVerifiedAt());
        entity.setLastEventAt(old.getLastEventAt());
        if (entity.getSigningSecret() != null && entity.getSigningSecret().contains("****")) entity.setSigningSecret(old.getSigningSecret());
        if (entity.getAppSecret() != null && entity.getAppSecret().contains("****")) entity.setAppSecret(old.getAppSecret());
        WhatsAppWebhookConfig merged = super.onPreUpdate(entity, old); validate(merged); return merged;
    }
    private void provisionMetaCallback(WhatsAppWebhookConfig entity) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) throw new IllegalStateException("FLEXTUMA_PUBLIC_BASE_URL must be configured before WhatsApp webhooks can be created");
        entity.setVerifyToken(TokenGenerator.generateSecureToken(32));
        entity.setCallbackToken(UUID.randomUUID().toString().replace("-", ""));
        entity.setMetaCallbackUrl(publicBaseUrl.replaceAll("/+$", "") + "/api/webhooks/whatsapp/" + entity.getCallbackToken());
    }
    private void validate(WhatsAppWebhookConfig entity) {
        String callbackUrl = entity.getCallbackUrl();
        if (callbackUrl == null || callbackUrl.isBlank()) return;
        try {
            URI uri = URI.create(callbackUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (Exception e) { throw new IllegalArgumentException("callbackUrl must be an absolute HTTPS URL"); }
    }

    private static final int OVERVIEW_SCAN_LIMIT = 5000;

    public WhatsAppWebhookOverviewDTO getOverview() {
        LocalDateTime now = LocalDateTime.now();

        List<WhatsAppInboxMessage> recentMessages = inboxMessageService.findAllPaginated(
                PageRequest.of(0, OVERVIEW_SCAN_LIMIT, Sort.by(Sort.Direction.DESC, "receivedAt")),
                List.of(), null, "AND").getData();

        List<WhatsAppRelayDelivery> recentDeliveries = relayDeliveryService.findAllPaginated(
                PageRequest.of(0, OVERVIEW_SCAN_LIMIT, Sort.by(Sort.Direction.DESC, "created")),
                List.of(), null, "AND").getData();

        List<WhatsAppWebhookConfig> configs = findAllPaginated(
                PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "created")), List.of(), null, "AND").getData();

        return WhatsAppWebhookOverviewDTO.builder()
                .eventsLast24h(countSince(recentMessages, now.minusHours(24)))
                .eventsPrevious24h(countBetween(recentMessages, now.minusHours(48), now.minusHours(24)))
                .eventsHourly(hourlyBuckets(recentMessages, now))
                .deliverySuccessRate(deliverySuccessRate(recentDeliveries))
                .retriesLastHour(retriesSince(recentDeliveries, now.minusHours(1)))
                .p95LatencyMs(p95Latency(recentDeliveries))
                .configHealth(configHealth(configs, recentDeliveries, now))
                .activity(activity(recentMessages, recentDeliveries))
                .build();
    }

    private long countSince(List<WhatsAppInboxMessage> messages, LocalDateTime since) {
        return messages.stream().filter(m -> m.getReceivedAt().isAfter(since)).count();
    }

    private long countBetween(List<WhatsAppInboxMessage> messages, LocalDateTime start, LocalDateTime end) {
        return messages.stream()
                .filter(m -> m.getReceivedAt().isAfter(start) && !m.getReceivedAt().isAfter(end))
                .count();
    }

    private List<Long> hourlyBuckets(List<WhatsAppInboxMessage> messages, LocalDateTime now) {
        List<Long> buckets = new ArrayList<>();
        for (int hoursAgo = 23; hoursAgo >= 0; hoursAgo--) {
            LocalDateTime bucketStart = now.minusHours(hoursAgo + 1);
            LocalDateTime bucketEnd = now.minusHours(hoursAgo);
            long count = messages.stream()
                    .filter(m -> !m.getReceivedAt().isBefore(bucketStart) && m.getReceivedAt().isBefore(bucketEnd))
                    .count();
            buckets.add(count);
        }
        return buckets;
    }

    private Double deliverySuccessRate(List<WhatsAppRelayDelivery> deliveries) {
        long succeeded = deliveries.stream().filter(d -> d.getStatus() == WhatsAppRelayStatus.SUCCEEDED).count();
        long failed = deliveries.stream().filter(d -> d.getStatus() == WhatsAppRelayStatus.FAILED).count();
        long total = succeeded + failed;
        return total == 0 ? null : (double) succeeded / total * 100;
    }

    private long retriesSince(List<WhatsAppRelayDelivery> deliveries, LocalDateTime since) {
        return deliveries.stream()
                .filter(d -> d.getRetries() > 0 && d.getLastAttemptAt() != null && d.getLastAttemptAt().isAfter(since))
                .count();
    }

    private Long p95Latency(List<WhatsAppRelayDelivery> deliveries) {
        List<Long> latencies = deliveries.stream()
                .filter(d -> d.getStatus() == WhatsAppRelayStatus.SUCCEEDED && d.getLatencyMs() != null)
                .map(WhatsAppRelayDelivery::getLatencyMs)
                .sorted()
                .toList();
        if (latencies.isEmpty()) return null;
        int index = (int) Math.ceil(latencies.size() * 0.95) - 1;
        return latencies.get(Math.max(0, index));
    }

    private Map<UUID, String> configHealth(List<WhatsAppWebhookConfig> configs, List<WhatsAppRelayDelivery> deliveries, LocalDateTime now) {
        Map<UUID, List<WhatsAppRelayDelivery>> deliveriesByConfig = deliveries.stream()
                .collect(Collectors.groupingBy(d -> d.getConfig().getId()));

        Map<UUID, String> health = new LinkedHashMap<>();
        for (WhatsAppWebhookConfig config : configs) {
            if (!Boolean.TRUE.equals(config.getActive())) {
                health.put(config.getId(), "PAUSED");
                continue;
            }
            List<WhatsAppRelayDelivery> configDeliveries = deliveriesByConfig.getOrDefault(config.getId(), List.of());
            boolean retrying = configDeliveries.stream().anyMatch(d ->
                    d.getStatus() == WhatsAppRelayStatus.PENDING || d.getStatus() == WhatsAppRelayStatus.PROCESSING
                            || (d.getStatus() == WhatsAppRelayStatus.FAILED && d.getRetries() > 0
                                    && d.getLastAttemptAt() != null && d.getLastAttemptAt().isAfter(now.minusHours(1))));
            health.put(config.getId(), retrying ? "RETRYING" : "LIVE");
        }
        return health;
    }

    private List<WhatsAppActivityItemDTO> activity(List<WhatsAppInboxMessage> messages, List<WhatsAppRelayDelivery> deliveries) {
        List<WhatsAppActivityItemDTO> items = new ArrayList<>();
        messages.stream().limit(50).forEach(m -> items.add(WhatsAppActivityItemDTO.builder()
                .type("messages.received")
                .summary("From " + maskPhone(m.getFromNumber()))
                .at(m.getReceivedAt())
                .build()));
        deliveries.stream()
                .filter(d -> d.getRetries() > 0 && d.getLastAttemptAt() != null)
                .forEach(d -> items.add(WhatsAppActivityItemDTO.builder()
                        .type(d.getStatus() == WhatsAppRelayStatus.FAILED ? "delivery.failed" : "delivery.retry")
                        .summary((d.getStatus() == WhatsAppRelayStatus.FAILED ? "Failed: " : "Retrying: ")
                                + (d.getLastError() != null ? d.getLastError() : "delivery error"))
                        .at(d.getLastAttemptAt())
                        .build()));
        return items.stream()
                .sorted(Comparator.comparing(WhatsAppActivityItemDTO::at).reversed())
                .limit(20)
                .toList();
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() <= 4) return phoneNumber;
        return "•••• " + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
