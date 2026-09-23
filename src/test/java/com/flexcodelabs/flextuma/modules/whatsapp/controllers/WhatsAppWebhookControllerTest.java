package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppInboxMessageRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppRelayDeliveryRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppTemplateRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppMediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    @Mock
    private WhatsAppWebhookConfigRepository configRepository;

    @Mock
    private SmsLogRepository smsLogRepository;

    @Mock
    private WhatsAppInboxMessageRepository inboxMessageRepository;

    @Mock
    private WhatsAppRelayDeliveryRepository relayDeliveryRepository;

    @Mock
    private WhatsAppTemplateRepository templateRepository;

    @Mock
    private WhatsAppMediaService mediaService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpDefaults() {
        // Registered before any per-test stubbing, so a test that stubs a specific
        // mediaId/owner combination still wins over this catch-all default.
        lenient().when(mediaService.download(any(), any())).thenReturn(Optional.empty());
    }

    private WhatsAppWebhookController controller() {
        return new WhatsAppWebhookController(configRepository, smsLogRepository, inboxMessageRepository, relayDeliveryRepository, templateRepository, mediaService, objectMapper);
    }

    private WhatsAppWebhookConfig activeConfig() {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());
        config.setPhoneNumberId("104725069208652");
        config.setCallbackUrl("https://example.com/hook");
        config.setVerifyToken("secret-verify-token");
        config.setCallbackToken("callback-token");
        config.setActive(true);
        User owner = new User();
        owner.setId(UUID.randomUUID());
        config.setCreatedBy(owner);
        return config;
    }

    @Test
    void verify_shouldMarkVerifiedAndSaveConfig_whenModeAndTokenMatch() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByVerifyTokenAndActiveTrue("secret-verify-token")).thenReturn(Optional.of(config));

        ResponseEntity<String> response = controller().verify("subscribe", "secret-verify-token", "the-challenge");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("the-challenge", response.getBody());
        assertNotNull(config.getLastVerifiedAt());
        verify(configRepository).save(config);
    }

    @Test
    void verify_shouldReturnForbidden_whenTokenDoesNotMatch() {
        when(configRepository.findByVerifyTokenAndActiveTrue("wrong-token")).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller().verify("subscribe", "wrong-token", "the-challenge");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void receive_shouldMarkEventReceived_whenSignatureIsValid() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByPhoneNumberIdAndActiveTrue("104725069208652")).thenReturn(Optional.of(config));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\"104725069208652\"}}}]}]}";

        ResponseEntity<Void> response = controller().receive(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(config.getLastEventAt());
        verify(configRepository).save(config);

        ArgumentCaptor<WhatsAppRelayDelivery> captor = ArgumentCaptor.forClass(WhatsAppRelayDelivery.class);
        verify(relayDeliveryRepository).save(captor.capture());
        assertEquals(config, captor.getValue().getConfig());
        assertEquals(config.getCreatedBy(), captor.getValue().getCreatedBy());
    }

    @Test
    void receive_shouldNotMarkEventReceived_whenNoActiveConfigMatches() {
        when(configRepository.findByPhoneNumberIdAndActiveTrue("104725069208652")).thenReturn(Optional.empty());

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\"104725069208652\"}}}]}]}";

        ResponseEntity<Void> response = controller().receive(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(configRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void receiveGeneratedCallback_shouldAccept_whenPayloadHasNoPhoneNumberId() {
        // Meta delivers many event types (template status updates, account alerts, etc.) with no
        // metadata.phone_number_id at all. The callback token alone must be enough to accept these.
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByCallbackTokenAndActiveTrue("callback-token")).thenReturn(Optional.of(config));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"event\":\"template_status_update\"}}]}]}";

        ResponseEntity<Void> response = controller().receiveGeneratedCallback("callback-token", payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(config.getLastEventAt());
        verify(configRepository).save(config);
    }

    @Test
    void receiveGeneratedCallback_shouldUpdateTemplateStatusAndStillRelay_whenEventIsTemplateStatusUpdate() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByCallbackTokenAndActiveTrue("callback-token")).thenReturn(Optional.of(config));

        com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate template =
                new com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate();
        template.setMetaTemplateId("META_TEMPLATE_ID");
        template.setStatus("PENDING");
        when(templateRepository.findFirstByMetaTemplateIdAndCreatedBy("META_TEMPLATE_ID", config.getCreatedBy()))
                .thenReturn(Optional.of(template));

        String payload = "{\"entry\":[{\"changes\":[{\"field\":\"message_template_status_update\",\"value\":{"
                + "\"event\":\"APPROVED\",\"message_template_id\":\"META_TEMPLATE_ID\","
                + "\"message_template_name\":\"farm_alert\",\"message_template_language\":\"en\"}}]}]}";

        ResponseEntity<Void> response = controller().receiveGeneratedCallback("callback-token", payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("APPROVED", template.getStatus());
        verify(templateRepository).save(template);

        // Meta template-status events carry no phone_number_id, so relay must still fire via the
        // callback-token-scoped config rather than being skipped for lack of a phone match.
        ArgumentCaptor<WhatsAppRelayDelivery> relayCaptor = ArgumentCaptor.forClass(WhatsAppRelayDelivery.class);
        verify(relayDeliveryRepository).save(relayCaptor.capture());
        assertEquals(config, relayCaptor.getValue().getConfig());
    }

    @Test
    void receiveGeneratedCallback_shouldFallBackToNameAndLanguage_whenTemplateIdMissing() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByCallbackTokenAndActiveTrue("callback-token")).thenReturn(Optional.of(config));

        com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate template =
                new com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate();
        template.setName("farm_alert");
        template.setLanguage("en");
        template.setStatus("PENDING");
        when(templateRepository.findFirstByNameAndLanguageAndCreatedBy("farm_alert", "en", config.getCreatedBy()))
                .thenReturn(Optional.of(template));

        String payload = "{\"entry\":[{\"changes\":[{\"field\":\"message_template_status_update\",\"value\":{"
                + "\"event\":\"REJECTED\",\"message_template_name\":\"farm_alert\",\"message_template_language\":\"en\"}}]}]}";

        controller().receiveGeneratedCallback("callback-token", payload, null);

        assertEquals("REJECTED", template.getStatus());
        verify(templateRepository).save(template);
    }

    @Test
    void receiveGeneratedCallback_shouldNotUpdateAnotherTenantsTemplate_whenNameAndLanguageCollide() {
        // Two tenants can each sync a template with the same common name/language (e.g.
        // "otp_verification"/"en"). A status update for tenant A's webhook config must not be
        // able to touch tenant B's row of the same name+language -- the lookup is scoped to
        // config.getCreatedBy(), so stubbing only the caller's own owner leaves tenant B
        // untouched without needing a second mock to prove it.
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByCallbackTokenAndActiveTrue("callback-token")).thenReturn(Optional.of(config));
        when(templateRepository.findFirstByNameAndLanguageAndCreatedBy(any(), any(), any())).thenReturn(Optional.empty());

        // No message_template_id in this payload, matching real Meta events that omit it --
        // exercises the name+language fallback path this test is actually about.
        String payload = "{\"entry\":[{\"changes\":[{\"field\":\"message_template_status_update\",\"value\":{"
                + "\"event\":\"APPROVED\",\"message_template_name\":\"otp_verification\",\"message_template_language\":\"en\"}}]}]}";

        controller().receiveGeneratedCallback("callback-token", payload, null);

        verify(templateRepository).findFirstByNameAndLanguageAndCreatedBy("otp_verification", "en", config.getCreatedBy());
        verify(templateRepository, never()).save(any());
    }

    @Test
    void receiveGeneratedCallback_shouldAccept_whenPhoneNumberIdDiffersFromConfig() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByCallbackTokenAndActiveTrue("callback-token")).thenReturn(Optional.of(config));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\"some-other-number\"}}}]}]}";

        ResponseEntity<Void> response = controller().receiveGeneratedCallback("callback-token", payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(config.getLastEventAt());
    }

    @Test
    void receiveGeneratedCallback_shouldReturnOkWithoutSaving_whenTokenUnknown() {
        when(configRepository.findByCallbackTokenAndActiveTrue("missing-token")).thenReturn(Optional.empty());

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\"104725069208652\"}}}]}]}";

        ResponseEntity<Void> response = controller().receiveGeneratedCallback("missing-token", payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(configRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void receive_shouldSaveInboundMessage_whenPayloadHasMessages() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByPhoneNumberIdAndActiveTrue("104725069208652")).thenReturn(Optional.of(config));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
                + "\"metadata\":{\"phone_number_id\":\"104725069208652\"},"
                + "\"contacts\":[{\"profile\":{\"name\":\"Ada\"},\"wa_id\":\"255700000000\"}],"
                + "\"messages\":[{\"from\":\"255700000000\",\"id\":\"wamid.ABC\",\"timestamp\":\"1700000000\",\"type\":\"text\",\"text\":{\"body\":\"Hello\"}}]"
                + "}}]}]}";

        controller().receive(payload, null);

        ArgumentCaptor<WhatsAppInboxMessage> captor = ArgumentCaptor.forClass(WhatsAppInboxMessage.class);
        verify(inboxMessageRepository).save(captor.capture());
        WhatsAppInboxMessage saved = captor.getValue();
        assertEquals("255700000000", saved.getFromNumber());
        assertEquals("Ada", saved.getContactName());
        assertEquals("wamid.ABC", saved.getProviderMessageId());
        assertEquals("text", saved.getMessageType());
        assertEquals("Hello", saved.getContent());
        assertEquals(config.getCreatedBy(), saved.getCreatedBy());
    }

    @Test
    void receive_shouldCaptureMediaMetadataAndDownload_whenMessageIsImage() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByPhoneNumberIdAndActiveTrue("104725069208652")).thenReturn(Optional.of(config));
        when(mediaService.download(config, "media-123"))
                .thenReturn(Optional.of(new WhatsAppMediaService.DownloadedMedia("stored-media-123", 4L)));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
                + "\"metadata\":{\"phone_number_id\":\"104725069208652\"},"
                + "\"messages\":[{\"from\":\"255700000000\",\"id\":\"wamid.IMG\",\"timestamp\":\"1700000000\",\"type\":\"image\","
                + "\"image\":{\"id\":\"media-123\",\"mime_type\":\"image/jpeg\",\"caption\":\"Check this out\"}}]"
                + "}}]}]}";

        controller().receive(payload, null);

        ArgumentCaptor<WhatsAppInboxMessage> captor = ArgumentCaptor.forClass(WhatsAppInboxMessage.class);
        verify(inboxMessageRepository).save(captor.capture());
        WhatsAppInboxMessage saved = captor.getValue();
        assertEquals("image", saved.getMessageType());
        assertEquals("media-123", saved.getMediaId());
        assertEquals("image/jpeg", saved.getMimeType());
        assertEquals("Check this out", saved.getCaption());
        assertEquals("Check this out", saved.getContent());
        assertEquals("stored-media-123", saved.getMediaPath());
        assertEquals(4L, saved.getMediaSize());
    }

    @Test
    void receive_shouldLeaveContentNull_whenImageHasNoCaption() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByPhoneNumberIdAndActiveTrue("104725069208652")).thenReturn(Optional.of(config));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
                + "\"metadata\":{\"phone_number_id\":\"104725069208652\"},"
                + "\"messages\":[{\"from\":\"255700000000\",\"id\":\"wamid.IMG2\",\"timestamp\":\"1700000000\",\"type\":\"image\","
                + "\"image\":{\"id\":\"media-456\",\"mime_type\":\"image/png\"}}]"
                + "}}]}]}";

        controller().receive(payload, null);

        ArgumentCaptor<WhatsAppInboxMessage> captor = ArgumentCaptor.forClass(WhatsAppInboxMessage.class);
        verify(inboxMessageRepository).save(captor.capture());
        assertNull(captor.getValue().getContent());
        assertEquals("media-456", captor.getValue().getMediaId());
    }

    @Test
    void receive_shouldNotSaveInboundMessage_whenAlreadyIngested() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByPhoneNumberIdAndActiveTrue("104725069208652")).thenReturn(Optional.of(config));
        when(inboxMessageRepository.existsByProviderMessageId("wamid.ABC")).thenReturn(true);

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
                + "\"metadata\":{\"phone_number_id\":\"104725069208652\"},"
                + "\"messages\":[{\"from\":\"255700000000\",\"id\":\"wamid.ABC\",\"timestamp\":\"1700000000\",\"type\":\"text\",\"text\":{\"body\":\"Hello\"}}]"
                + "}}]}]}";

        controller().receive(payload, null);

        verify(inboxMessageRepository, never()).save(any());
    }

    @Test
    void receive_shouldNotQueueRelay_whenCallbackUrlIsBlank() {
        WhatsAppWebhookConfig config = activeConfig();
        config.setCallbackUrl(null);
        when(configRepository.findByPhoneNumberIdAndActiveTrue("104725069208652")).thenReturn(Optional.of(config));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{\"phone_number_id\":\"104725069208652\"}}}]}]}";

        ResponseEntity<Void> response = controller().receive(payload, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(relayDeliveryRepository, never()).save(any());
    }

    @Test
    void receive_shouldCaptureErrorMessage_whenStatusFailedWithErrors() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByPhoneNumberIdAndActiveTrue("104725069208652")).thenReturn(Optional.of(config));

        SmsLog log = new SmsLog();
        log.setProviderMessageId("wamid.FAILED");
        when(smsLogRepository.findByProviderMessageId("wamid.FAILED")).thenReturn(Optional.of(log));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
                + "\"metadata\":{\"phone_number_id\":\"104725069208652\"},"
                + "\"statuses\":[{\"id\":\"wamid.FAILED\",\"status\":\"failed\","
                + "\"errors\":[{\"code\":131047,\"title\":\"Re-engagement message\",\"message\":\"24 hour window closed\"}]}]"
                + "}}]}]}";

        controller().receive(payload, null);

        ArgumentCaptor<SmsLog> captor = ArgumentCaptor.forClass(SmsLog.class);
        verify(smsLogRepository).save(captor.capture());
        assertEquals("[131047] 24 hour window closed", captor.getValue().getError());
    }

    @Test
    void receive_shouldNotSetError_whenStatusIsNotFailed() {
        WhatsAppWebhookConfig config = activeConfig();
        when(configRepository.findByPhoneNumberIdAndActiveTrue("104725069208652")).thenReturn(Optional.of(config));

        SmsLog log = new SmsLog();
        log.setProviderMessageId("wamid.SENT");
        when(smsLogRepository.findByProviderMessageId("wamid.SENT")).thenReturn(Optional.of(log));

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
                + "\"metadata\":{\"phone_number_id\":\"104725069208652\"},"
                + "\"statuses\":[{\"id\":\"wamid.SENT\",\"status\":\"sent\"}]"
                + "}}]}]}";

        controller().receive(payload, null);

        ArgumentCaptor<SmsLog> captor = ArgumentCaptor.forClass(SmsLog.class);
        verify(smsLogRepository).save(captor.capture());
        assertNull(captor.getValue().getError());
    }
}
