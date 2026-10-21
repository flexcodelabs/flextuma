package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppMetaApp;
import com.flexcodelabs.flextuma.core.helpers.TokenGenerator;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppMetaAppRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookConfigRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Shared Meta app callback URLs; tenant-scoped by BaseService like webhook configurations. */
@Service
@RequiredArgsConstructor
public class WhatsAppMetaAppService extends BaseService<WhatsAppMetaApp> {
    private static final String ALL = "ALL";
    private static final String MASK = "****";
    private static final int VERIFY_TOKEN_LENGTH = 32;

    private final WhatsAppMetaAppRepository repository;
    private final WhatsAppWebhookConfigRepository configRepository;
    @Value("${flextuma.public-base-url:}") private String publicBaseUrl;

    @Override protected JpaRepository<WhatsAppMetaApp, UUID> getRepository() { return repository; }
    @Override protected JpaSpecificationExecutor<WhatsAppMetaApp> getRepositoryAsExecutor() { return repository; }
    @Override protected String getReadPermission() { return ALL; }
    @Override protected String getAddPermission() { return ALL; }
    @Override protected String getUpdatePermission() { return ALL; }
    @Override protected String getDeletePermission() { return ALL; }
    @Override public String getEntityPlural() { return WhatsAppMetaApp.NAME_PLURAL; }
    @Override protected String getEntitySingular() { return WhatsAppMetaApp.NAME_SINGULAR; }
    @Override public String getPropertyName() { return WhatsAppMetaApp.PLURAL; }
    @Override protected String getTableName() { return "whatsapp_meta_app"; }

    @Override
    protected void onPreSave(WhatsAppMetaApp entity) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalStateException("FLEXTUMA_PUBLIC_BASE_URL must be configured before WhatsApp Meta apps can be created");
        }
        if (entity.getVerifyToken() == null || entity.getVerifyToken().isBlank()) {
            entity.setVerifyToken(TokenGenerator.generateSecureToken(VERIFY_TOKEN_LENGTH));
        }
        entity.setCallbackToken(UUID.randomUUID().toString().replace("-", ""));
        entity.setMetaCallbackUrl(publicBaseUrl.replaceAll("/+$", "") + "/api/webhooks/whatsapp/apps/" + entity.getCallbackToken());
    }

    /** The callback URL and verification timestamps are owned by Flextuma; the verify token and app
     * secret can be rotated, and submitting back the masked placeholder keeps the stored value. */
    @Override
    protected WhatsAppMetaApp onPreUpdate(WhatsAppMetaApp entity, WhatsAppMetaApp old) {
        entity.setCallbackToken(old.getCallbackToken());
        entity.setMetaCallbackUrl(old.getMetaCallbackUrl());
        entity.setLastVerifiedAt(old.getLastVerifiedAt());
        entity.setLastEventAt(old.getLastEventAt());
        if (isMasked(entity.getVerifyToken())) {
            entity.setVerifyToken(old.getVerifyToken());
        }
        if (isMasked(entity.getAppSecret())) {
            entity.setAppSecret(old.getAppSecret());
        }
        return super.onPreUpdate(entity, old);
    }

    /** Linked webhook configurations are detached rather than deleted: each keeps working through
     * its own per-number callback URL (and its own app secret, if set). */
    @Override
    @Transactional
    public Map<String, String> delete(UUID id) {
        checkPermission(getDeletePermission());
        WhatsAppMetaApp app = findAccessibleById(id);
        configRepository.detachMetaApp(app.getId());
        return super.delete(id);
    }

    private static boolean isMasked(String value) {
        return value != null && value.contains(MASK);
    }
}
