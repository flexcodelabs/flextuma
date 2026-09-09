package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.enums.WhatsAppRelayStatus;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppRelayDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppRelayDispatchWorkerTest {

    @Mock
    private WhatsAppRelayDeliveryRepository deliveryRepository;

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WhatsAppRelayDispatchWorker worker;

    @BeforeEach
    void setUp() {
        worker = new WhatsAppRelayDispatchWorker(deliveryRepository, restTemplate, objectMapper);
    }

    private WhatsAppRelayDelivery pendingDelivery() {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setCallbackUrl("https://example.com/hook");

        WhatsAppRelayDelivery delivery = new WhatsAppRelayDelivery();
        delivery.setStatus(WhatsAppRelayStatus.PENDING);
        delivery.setConfig(config);
        delivery.setPayload("{\"hello\":\"world\"}");
        delivery.setRetries(0);
        return delivery;
    }

    @Test
    void dispatch_shouldMarkSucceeded_whenPostSucceeds() {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        when(deliveryRepository.findTop50ByStatusOrderByCreatedAsc(WhatsAppRelayStatus.PENDING))
                .thenReturn(List.of(delivery));
        when(deliveryRepository.claimPendingDelivery(any(), eq(WhatsAppRelayStatus.PENDING), eq(WhatsAppRelayStatus.PROCESSING)))
                .thenReturn(1);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());
        when(deliveryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        worker.dispatch();

        assertEquals(WhatsAppRelayStatus.SUCCEEDED, delivery.getStatus());
        assertEquals(0, delivery.getRetries());
    }

    @Test
    void dispatch_shouldRetry_whenPostFailsAndRetriesBelowMax() {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        when(deliveryRepository.findTop50ByStatusOrderByCreatedAsc(WhatsAppRelayStatus.PENDING))
                .thenReturn(List.of(delivery));
        when(deliveryRepository.claimPendingDelivery(any(), eq(WhatsAppRelayStatus.PENDING), eq(WhatsAppRelayStatus.PROCESSING)))
                .thenReturn(1);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("connection refused"));
        when(deliveryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        worker.dispatch();

        assertEquals(WhatsAppRelayStatus.PENDING, delivery.getStatus());
        assertEquals(1, delivery.getRetries());
        assertEquals("connection refused", delivery.getLastError());
    }

    @Test
    void dispatch_shouldMarkFailed_whenMaxRetriesReached() {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        delivery.setRetries(2);
        when(deliveryRepository.findTop50ByStatusOrderByCreatedAsc(WhatsAppRelayStatus.PENDING))
                .thenReturn(List.of(delivery));
        when(deliveryRepository.claimPendingDelivery(any(), eq(WhatsAppRelayStatus.PENDING), eq(WhatsAppRelayStatus.PROCESSING)))
                .thenReturn(1);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("timeout"));
        when(deliveryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        worker.dispatch();

        assertEquals(WhatsAppRelayStatus.FAILED, delivery.getStatus());
        assertEquals(3, delivery.getRetries());
    }

    @Test
    void dispatch_shouldSkip_whenClaimLostToAnotherWorker() {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        when(deliveryRepository.findTop50ByStatusOrderByCreatedAsc(WhatsAppRelayStatus.PENDING))
                .thenReturn(List.of(delivery));
        when(deliveryRepository.claimPendingDelivery(any(), eq(WhatsAppRelayStatus.PENDING), eq(WhatsAppRelayStatus.PROCESSING)))
                .thenReturn(0);

        worker.dispatch();

        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(Void.class));
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void dispatch_shouldDoNothing_whenNoPendingDeliveries() {
        when(deliveryRepository.findTop50ByStatusOrderByCreatedAsc(WhatsAppRelayStatus.PENDING))
                .thenReturn(Collections.emptyList());

        worker.dispatch();

        verify(deliveryRepository, never()).save(any());
    }
}
