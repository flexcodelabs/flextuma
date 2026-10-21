package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppMetaApp;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppMetaAppRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class WhatsAppMetaAppServiceTest {

    @Mock
    private WhatsAppMetaAppRepository repository;

    @Mock
    private WhatsAppWebhookConfigRepository configRepository;

    @InjectMocks
    private WhatsAppMetaAppService service;

    @Test
    void onPreSave_shouldProvisionSharedCallbackUrlAndVerifyToken() {
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://api.example.com/");
        WhatsAppMetaApp app = new WhatsAppMetaApp();

        service.onPreSave(app);

        assertNotNull(app.getVerifyToken());
        assertFalse(app.getVerifyToken().isBlank());
        assertEquals("https://api.example.com/api/webhooks/whatsapp/apps/" + app.getCallbackToken(), app.getMetaCallbackUrl());
    }

    @Test
    void onPreSave_shouldKeepCallerSuppliedVerifyToken() {
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://api.example.com");
        WhatsAppMetaApp app = new WhatsAppMetaApp();
        app.setVerifyToken("my-token");

        service.onPreSave(app);

        assertEquals("my-token", app.getVerifyToken());
    }

    @Test
    void onPreSave_shouldFail_whenPublicBaseUrlMissing() {
        ReflectionTestUtils.setField(service, "publicBaseUrl", "");
        WhatsAppMetaApp app = new WhatsAppMetaApp();

        assertThrows(IllegalStateException.class, () -> service.onPreSave(app));
    }

    @Test
    void onPreUpdate_shouldKeepFlextumaOwnedValuesAndMaskedSecrets() {
        WhatsAppMetaApp old = new WhatsAppMetaApp();
        old.setCallbackToken("token");
        old.setMetaCallbackUrl("https://api.example.com/api/webhooks/whatsapp/apps/token");
        old.setVerifyToken("old-verify");
        old.setAppSecret("old-secret");
        old.setLastVerifiedAt(LocalDateTime.now());
        WhatsAppMetaApp incoming = new WhatsAppMetaApp();
        incoming.setName("Renamed");
        incoming.setCallbackToken("forged");
        incoming.setMetaCallbackUrl("https://evil.example.com");
        incoming.setVerifyToken("ol****fy");
        incoming.setAppSecret("new-secret");

        WhatsAppMetaApp merged = service.onPreUpdate(incoming, old);

        assertEquals("token", merged.getCallbackToken());
        assertTrue(merged.getMetaCallbackUrl().endsWith("/apps/token"));
        assertEquals("old-verify", merged.getVerifyToken());
        assertEquals("new-secret", merged.getAppSecret());
        assertEquals("Renamed", merged.getName());
    }
}
