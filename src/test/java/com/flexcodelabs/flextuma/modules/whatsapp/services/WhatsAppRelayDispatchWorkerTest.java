package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.enums.WhatsAppRelayStatus;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppRelayDeliveryRepository;
import com.flexcodelabs.flextuma.modules.notification.services.MessageRetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppRelayDispatchWorkerTest {

    // 10s initial delay doubling each time, capped at 15s, retried for 12h.
    private static final MessageRetryPolicy POLICY = new MessageRetryPolicy(10_000, 2.0, 15_000, 43_200_000);

    @Mock
    private WhatsAppRelayDeliveryRepository deliveryRepository;

    @Mock
    private RestTemplate restTemplate;

    private WhatsAppRelayDispatchWorker worker;

    @BeforeEach
    void setUp() {
        worker = new WhatsAppRelayDispatchWorker(deliveryRepository, restTemplate, POLICY);
    }

    private WhatsAppRelayDelivery pendingDelivery() {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setCallbackUrl("https://example.com/hook");

        WhatsAppRelayDelivery delivery = new WhatsAppRelayDelivery();
        delivery.setStatus(WhatsAppRelayStatus.PENDING);
        delivery.setConfig(config);
        delivery.setPayload("{\"hello\":\"world\"}");
        delivery.setRetries(0);
        delivery.setCreated(LocalDateTime.now());
        return delivery;
    }

    private void stubDue(WhatsAppRelayDelivery delivery) {
        when(deliveryRepository.findDueDeliveries(eq(WhatsAppRelayStatus.PENDING), any(LocalDateTime.class),
                any(Pageable.class))).thenReturn(List.of(delivery));
        when(deliveryRepository.claimPendingDelivery(any(), eq(WhatsAppRelayStatus.PENDING), eq(WhatsAppRelayStatus.PROCESSING)))
                .thenReturn(1);
        when(deliveryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void stubPostThrows(RuntimeException e) {
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class))).thenThrow(e);
    }

    private static void assertRetryInSeconds(WhatsAppRelayDelivery delivery, long seconds, LocalDateTime before) {
        LocalDateTime nextRetryAt = delivery.getNextRetryAt();
        assertNotNull(nextRetryAt);
        assertFalse(nextRetryAt.isBefore(before.plusSeconds(seconds)));
        assertTrue(nextRetryAt.isBefore(LocalDateTime.now().plusSeconds(seconds + 1)));
    }

    @Test
    void dispatch_shouldMarkSucceeded_whenPostSucceeds() {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        delivery.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        stubDue(delivery);
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        worker.dispatch();

        assertEquals(WhatsAppRelayStatus.SUCCEEDED, delivery.getStatus());
        assertEquals(0, delivery.getRetries());
        assertNull(delivery.getNextRetryAt());
        assertNotNull(delivery.getLastAttemptAt());
    }

    @Test
    void dispatch_shouldScheduleRetryWithInitialBackoff_whenTransportErrorOnFirstAttempt() {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        stubDue(delivery);
        stubPostThrows(new ResourceAccessException("connection refused"));
        LocalDateTime before = LocalDateTime.now();

        worker.dispatch();

        assertEquals(WhatsAppRelayStatus.PENDING, delivery.getStatus());
        assertEquals(1, delivery.getRetries());
        assertEquals("connection refused", delivery.getLastError());
        assertRetryInSeconds(delivery, 10, before);
    }

    @Test
    void dispatch_shouldCapBackoff_whenServerErrorOnLaterAttempt() {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        delivery.setRetries(1);
        stubDue(delivery);
        stubPostThrows(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        LocalDateTime before = LocalDateTime.now();

        worker.dispatch();

        // attempt 2 would wait 20s, but max-interval caps it at 15s
        assertEquals(WhatsAppRelayStatus.PENDING, delivery.getStatus());
        assertEquals(2, delivery.getRetries());
        assertRetryInSeconds(delivery, 15, before);
    }

    @Test
    void dispatch_shouldKeepRetrying_afterManyAttemptsWithinWindow() {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        delivery.setRetries(20);
        delivery.setCreated(LocalDateTime.now().minusHours(11));
        stubDue(delivery);
        stubPostThrows(new ResourceAccessException("timeout"));

        worker.dispatch();

        assertEquals(WhatsAppRelayStatus.PENDING, delivery.getStatus());
        assertEquals(21, delivery.getRetries());
        assertNotNull(delivery.getNextRetryAt());
    }

    @Test
    void dispatch_shouldMarkFailed_whenRetryWindowExhausted() {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        delivery.setRetries(20);
        delivery.setCreated(LocalDateTime.now().minusHours(12).minusMinutes(1));
        delivery.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        stubDue(delivery);
        stubPostThrows(new ResourceAccessException("timeout"));

        worker.dispatch();

        assertEquals(WhatsAppRelayStatus.FAILED, delivery.getStatus());
        assertEquals(21, delivery.getRetries());
        assertNull(delivery.getNextRetryAt());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 408, 410, 422, 429})
    void dispatch_shouldRetry_whenCallbackReturns4xx(int status) {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        stubDue(delivery);
        stubPostThrows(HttpClientErrorException.create(HttpStatus.valueOf(status), "rejected", null, null, null));

        worker.dispatch();

        assertEquals(WhatsAppRelayStatus.PENDING, delivery.getStatus());
        assertEquals(1, delivery.getRetries());
        assertNotNull(delivery.getNextRetryAt());
    }

    @Test
    void dispatch_shouldRetry_whenErrorIsUnexpected() {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        stubDue(delivery);
        stubPostThrows(new IllegalStateException("bad payload"));

        worker.dispatch();

        assertEquals(WhatsAppRelayStatus.PENDING, delivery.getStatus());
        assertEquals("bad payload", delivery.getLastError());
    }

    @Test
    void dispatch_shouldSkip_whenClaimLostToAnotherWorker() {
        WhatsAppRelayDelivery delivery = pendingDelivery();
        when(deliveryRepository.findDueDeliveries(eq(WhatsAppRelayStatus.PENDING), any(LocalDateTime.class),
                any(Pageable.class))).thenReturn(List.of(delivery));
        when(deliveryRepository.claimPendingDelivery(any(), eq(WhatsAppRelayStatus.PENDING), eq(WhatsAppRelayStatus.PROCESSING)))
                .thenReturn(0);

        worker.dispatch();

        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(Void.class));
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void dispatch_shouldDoNothing_whenNoDueDeliveries() {
        when(deliveryRepository.findDueDeliveries(eq(WhatsAppRelayStatus.PENDING), any(LocalDateTime.class),
                any(Pageable.class))).thenReturn(Collections.emptyList());

        worker.dispatch();

        verify(deliveryRepository, never()).save(any());
    }
}
