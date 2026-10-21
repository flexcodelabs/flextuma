package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppMetaApp;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.helpers.HmacUtil;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppInboxMessageRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppMetaAppRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppRelayDeliveryRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppTemplateRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookEventRepository;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppMediaService;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppInboundMessageIngestor;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppSharedWebhookRouter;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppStatusUpdater;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppWebhookEventRecorder;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppWebhookProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppSharedWebhookControllerTest {
    private static final String APP_TOKEN = "shared-app-token";
    private static final String WABA_ID = "200000000000001";

    @Mock private WhatsAppWebhookConfigRepository configRepository;
    @Mock private WhatsAppMetaAppRepository metaAppRepository;
    @Mock private SmsLogRepository smsLogRepository;
    @Mock private WhatsAppInboxMessageRepository inboxMessageRepository;
    @Mock private WhatsAppRelayDeliveryRepository relayDeliveryRepository;
    @Mock private WhatsAppTemplateRepository templateRepository;
    @Mock private WhatsAppMediaService mediaService;
    @Mock private WhatsAppWebhookEventRepository eventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WhatsAppWebhookController controller() {
        WhatsAppWebhookProcessor processor = new WhatsAppWebhookProcessor(configRepository, relayDeliveryRepository,
                new WhatsAppWebhookEventRecorder(eventRepository, objectMapper),
                new WhatsAppStatusUpdater(smsLogRepository, templateRepository),
                new WhatsAppInboundMessageIngestor(inboxMessageRepository, mediaService), objectMapper);
        return new WhatsAppWebhookController(configRepository, metaAppRepository, processor,
                new WhatsAppSharedWebhookRouter(configRepository), objectMapper);
    }

    private WhatsAppMetaApp app(String appSecret) {
        WhatsAppMetaApp app = new WhatsAppMetaApp();
        app.setId(UUID.randomUUID());
        app.setName("Main business app");
        app.setVerifyToken("shared-verify-token");
        app.setAppSecret(appSecret);
        app.setCallbackToken(APP_TOKEN);
        app.setActive(true);
        when(metaAppRepository.findByCallbackTokenAndActiveTrue(APP_TOKEN)).thenReturn(Optional.of(app));
        return app;
    }

    private WhatsAppWebhookConfig config(WhatsAppMetaApp app, String phoneNumberId, String callbackUrl) {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());
        config.setPhoneNumberId(phoneNumberId);
        config.setBusinessAccountId(WABA_ID);
        config.setCallbackUrl(callbackUrl);
        config.setMetaApp(app);
        config.setActive(true);
        User owner = new User();
        owner.setId(UUID.randomUUID());
        config.setCreatedBy(owner);
        return config;
    }

    private static String messageChange(String phoneNumberId, String messageId) {
        return "{\"field\":\"messages\",\"value\":{\"metadata\":{\"phone_number_id\":\"" + phoneNumberId + "\"},"
                + "\"messages\":[{\"id\":\"" + messageId + "\",\"from\":\"255700000000\",\"type\":\"text\","
                + "\"timestamp\":\"1700000000\",\"text\":{\"body\":\"hi\"}}]}}";
    }

    private static String payload(String... changes) {
        return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"" + WABA_ID + "\",\"changes\":["
                + String.join(",", changes) + "]}]}";
    }

    @Test
    void verifySharedCallback_shouldEchoChallengeAndMarkVerified_whenTokenMatches() {
        WhatsAppMetaApp app = app(null);

        ResponseEntity<String> response = controller().verifySharedCallback(APP_TOKEN, "subscribe", "shared-verify-token", "42");

        assertEquals("42", response.getBody());
        assertNotNull(app.getLastVerifiedAt());
        verify(metaAppRepository).save(app);
    }

    @Test
    void verifySharedCallback_shouldReturnForbidden_whenTokenDiffers() {
        app(null);

        ResponseEntity<String> response = controller().verifySharedCallback(APP_TOKEN, "subscribe", "wrong", "42");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(metaAppRepository, never()).save(any());
    }

    @Test
    void receiveSharedCallback_shouldRouteEachChangeToItsPhoneNumberConfig() {
        WhatsAppMetaApp app = app(null);
        WhatsAppWebhookConfig first = config(app, "111", null);
        WhatsAppWebhookConfig second = config(app, "222", null);
        when(configRepository.findAllActiveByMetaAppId(app.getId())).thenReturn(List.of(first, second));

        ResponseEntity<Void> response = controller().receiveSharedCallback(APP_TOKEN,
                payload(messageChange("111", "wamid.A"), messageChange("222", "wamid.B")), null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<WhatsAppInboxMessage> saved = ArgumentCaptor.forClass(WhatsAppInboxMessage.class);
        verify(inboxMessageRepository, times(2)).save(saved.capture());
        assertEquals(first, saved.getAllValues().get(0).getConfig());
        assertEquals(second, saved.getAllValues().get(1).getConfig());
        assertNotNull(app.getLastEventAt());
    }

    @Test
    void receiveSharedCallback_shouldLearnDisplayPhoneNumber_fromMetadata() {
        WhatsAppMetaApp app = app(null);
        WhatsAppWebhookConfig first = config(app, "111", null);
        when(configRepository.findAllActiveByMetaAppId(app.getId())).thenReturn(List.of(first));
        String change = "{\"field\":\"messages\",\"value\":{\"metadata\":{\"phone_number_id\":\"111\","
                + "\"display_phone_number\":\"255700000001\"},\"statuses\":[]}}";

        controller().receiveSharedCallback(APP_TOKEN, payload(change), null);

        assertEquals("255700000001", first.getDisplayPhoneNumber());
    }

    @Test
    void receiveSharedCallback_shouldRelayOnlyTheConfigsOwnChanges() throws Exception {
        WhatsAppMetaApp app = app(null);
        WhatsAppWebhookConfig first = config(app, "111", "https://one.example.com/hook");
        WhatsAppWebhookConfig second = config(app, "222", null);
        when(configRepository.findAllActiveByMetaAppId(app.getId())).thenReturn(List.of(first, second));

        controller().receiveSharedCallback(APP_TOKEN, payload(messageChange("111", "wamid.A"), messageChange("222", "wamid.B")), null);

        ArgumentCaptor<WhatsAppRelayDelivery> relayed = ArgumentCaptor.forClass(WhatsAppRelayDelivery.class);
        verify(relayDeliveryRepository).save(relayed.capture());
        String relayedPayload = relayed.getValue().getPayload();
        assertTrue(relayedPayload.contains("wamid.A"));
        assertTrue(!relayedPayload.contains("wamid.B"));
        assertEquals(WABA_ID, objectMapper.readTree(relayedPayload).at("/entry/0/id").asText());
    }

    @Test
    void receiveSharedCallback_shouldIgnoreChanges_forPhoneNumbersNotLinkedToTheApp() {
        WhatsAppMetaApp app = app(null);
        when(configRepository.findAllActiveByMetaAppId(app.getId())).thenReturn(List.of(config(app, "111", null)));

        ResponseEntity<Void> response = controller().receiveSharedCallback(APP_TOKEN, payload(messageChange("999", "wamid.X")), null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(inboxMessageRepository, never()).save(any());
    }

    @Test
    void receiveSharedCallback_shouldRouteWabaLevelEvents_byBusinessAccountId() {
        WhatsAppMetaApp app = app(null);
        WhatsAppWebhookConfig first = config(app, "111", null);
        WhatsAppWebhookConfig other = config(app, "333", null);
        other.setBusinessAccountId("another-waba");
        when(configRepository.findAllActiveByMetaAppId(app.getId())).thenReturn(List.of(first, other));
        String templateChange = "{\"field\":\"message_template_status_update\",\"value\":{\"event\":\"APPROVED\","
                + "\"message_template_name\":\"welcome\",\"message_template_language\":\"en\"}}";

        controller().receiveSharedCallback(APP_TOKEN, payload(templateChange), null);

        verify(templateRepository).findFirstByNameAndLanguageAndCreatedBy("welcome", "en", first.getCreatedBy());
        verify(templateRepository, never()).findFirstByNameAndLanguageAndCreatedBy("welcome", "en", other.getCreatedBy());
    }

    @Test
    void receiveSharedCallback_shouldRejectInvalidSignature_usingTheAppSecret() {
        app("app-secret");

        ResponseEntity<Void> response = controller().receiveSharedCallback(APP_TOKEN, payload(messageChange("111", "wamid.A")), "sha256=bogus");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(configRepository, never()).findAllActiveByMetaAppId(any());
    }

    @Test
    void receiveSharedCallback_shouldAcceptValidSignature_usingTheAppSecret() throws Exception {
        WhatsAppMetaApp app = app("app-secret");
        when(configRepository.findAllActiveByMetaAppId(app.getId())).thenReturn(List.of(config(app, "111", null)));
        String raw = payload(messageChange("111", "wamid.A"));

        ResponseEntity<Void> response = controller().receiveSharedCallback(APP_TOKEN, raw, "sha256=" + HmacUtil.sha256Hex(raw, "app-secret"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(inboxMessageRepository).save(any());
    }

    @Test
    void receiveGeneratedCallback_shouldValidateWithLinkedAppSecret_whenConfigHasNoOwnSecret() {
        WhatsAppMetaApp app = new WhatsAppMetaApp();
        app.setAppSecret("app-secret");
        WhatsAppWebhookConfig config = config(app, "111", null);
        when(configRepository.findByCallbackTokenAndActiveTrue("number-token")).thenReturn(Optional.of(config));

        ResponseEntity<Void> response = controller().receiveGeneratedCallback("number-token", payload(messageChange("111", "wamid.A")), "sha256=bogus");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void receiveSharedCallback_shouldReturnOk_whenAppTokenUnknown() {
        ResponseEntity<Void> response = controller().receiveSharedCallback("unknown", payload(), null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(configRepository, never()).findAllActiveByMetaAppId(any());
    }
}
