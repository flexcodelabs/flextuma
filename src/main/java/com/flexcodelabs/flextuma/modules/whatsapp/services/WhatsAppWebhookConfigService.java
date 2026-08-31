package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import com.flexcodelabs.flextuma.core.helpers.TokenGenerator;

@Service @RequiredArgsConstructor
public class WhatsAppWebhookConfigService extends BaseService<WhatsAppWebhookConfig> {
    private final WhatsAppWebhookConfigRepository repository;
    @Value("${flextuma.public-base-url:}") private String publicBaseUrl;
    protected JpaRepository<WhatsAppWebhookConfig, UUID> getRepository() { return repository; }
    protected JpaSpecificationExecutor<WhatsAppWebhookConfig> getRepositoryAsExecutor() { return repository; }
    protected String getReadPermission() { return WhatsAppWebhookConfig.READ; }
    protected String getAddPermission() { return WhatsAppWebhookConfig.ADD; }
    protected String getUpdatePermission() { return WhatsAppWebhookConfig.UPDATE; }
    protected String getDeletePermission() { return WhatsAppWebhookConfig.DELETE; }
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
        try {
            URI uri = URI.create(entity.getCallbackUrl());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (Exception e) { throw new IllegalArgumentException("callbackUrl must be an absolute HTTPS URL"); }
    }
}
