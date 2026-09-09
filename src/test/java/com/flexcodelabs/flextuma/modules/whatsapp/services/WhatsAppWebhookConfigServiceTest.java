package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.enums.WhatsAppRelayStatus;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppWebhookOverviewDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookConfigServiceTest {

    @Mock
    private WhatsAppWebhookConfigRepository repository;

    @Mock
    private WhatsAppInboxMessageService inboxMessageService;

    @Mock
    private WhatsAppRelayDeliveryService relayDeliveryService;

    @InjectMocks
    private WhatsAppWebhookConfigService service;

    private void stubConfigs(List<WhatsAppWebhookConfig> configs) {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(configs));
    }

    private void stubMessages(List<WhatsAppInboxMessage> messages) {
        when(inboxMessageService.findAllPaginated(any(), any(), any(), any()))
                .thenReturn(Pagination.<WhatsAppInboxMessage>builder().page(1).pageSize(5000)
                        .total(messages.size()).data(messages).build());
    }

    private void stubDeliveries(List<WhatsAppRelayDelivery> deliveries) {
        when(relayDeliveryService.findAllPaginated(any(), any(), any(), any()))
                .thenReturn(Pagination.<WhatsAppRelayDelivery>builder().page(1).pageSize(5000)
                        .total(deliveries.size()).data(deliveries).build());
    }

    private WhatsAppInboxMessage message(LocalDateTime receivedAt) {
        WhatsAppInboxMessage message = new WhatsAppInboxMessage();
        message.setFromNumber("255700000000");
        message.setReceivedAt(receivedAt);
        return message;
    }

    private WhatsAppRelayDelivery delivery(WhatsAppWebhookConfig config, WhatsAppRelayStatus status, Long latencyMs) {
        WhatsAppRelayDelivery delivery = new WhatsAppRelayDelivery();
        delivery.setConfig(config);
        delivery.setStatus(status);
        delivery.setLatencyMs(latencyMs);
        return delivery;
    }

    @Test
    void getOverview_shouldCountEventsInLastAndPrevious24Hours() {
        LocalDateTime now = LocalDateTime.now();
        stubConfigs(List.of());
        stubMessages(List.of(
                message(now.minusHours(1)),
                message(now.minusHours(2)),
                message(now.minusHours(30))));
        stubDeliveries(List.of());

        WhatsAppWebhookOverviewDTO overview = service.getOverview();

        assertEquals(2, overview.eventsLast24h());
        assertEquals(1, overview.eventsPrevious24h());
        assertEquals(24, overview.eventsHourly().size());
    }

    @Test
    void getOverview_shouldComputeDeliverySuccessRateAndP95Latency() {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());
        config.setActive(true);

        stubConfigs(List.of());
        stubMessages(List.of());
        stubDeliveries(List.of(
                delivery(config, WhatsAppRelayStatus.SUCCEEDED, 100L),
                delivery(config, WhatsAppRelayStatus.SUCCEEDED, 200L),
                delivery(config, WhatsAppRelayStatus.SUCCEEDED, 300L),
                delivery(config, WhatsAppRelayStatus.FAILED, null)));

        WhatsAppWebhookOverviewDTO overview = service.getOverview();

        assertEquals(75.0, overview.deliverySuccessRate());
        assertEquals(300L, overview.p95LatencyMs());
    }

    @Test
    void getOverview_shouldReturnNullRateAndLatency_whenNoDeliveries() {
        stubConfigs(List.of());
        stubMessages(List.of());
        stubDeliveries(List.of());

        WhatsAppWebhookOverviewDTO overview = service.getOverview();

        assertNull(overview.deliverySuccessRate());
        assertNull(overview.p95LatencyMs());
    }

    @Test
    void getOverview_shouldClassifyConfigHealth() {
        WhatsAppWebhookConfig live = new WhatsAppWebhookConfig();
        live.setId(UUID.randomUUID());
        live.setActive(true);

        WhatsAppWebhookConfig retrying = new WhatsAppWebhookConfig();
        retrying.setId(UUID.randomUUID());
        retrying.setActive(true);

        WhatsAppWebhookConfig paused = new WhatsAppWebhookConfig();
        paused.setId(UUID.randomUUID());
        paused.setActive(false);

        stubConfigs(List.of(live, retrying, paused));
        stubMessages(List.of());
        stubDeliveries(List.of(delivery(retrying, WhatsAppRelayStatus.PENDING, null)));

        WhatsAppWebhookOverviewDTO overview = service.getOverview();

        assertEquals("LIVE", overview.configHealth().get(live.getId()));
        assertEquals("RETRYING", overview.configHealth().get(retrying.getId()));
        assertEquals("PAUSED", overview.configHealth().get(paused.getId()));
    }
}
