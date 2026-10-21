package com.flexcodelabs.flextuma.modules.sms.services;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SmsConnectorService extends BaseService<SmsConnector> {

    private final SmsConnectorRepository repository;
    private final CurrentUserResolver userResolver;

    @Override
    protected JpaRepository<SmsConnector, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return "ALL";
    }

    @Override
    protected String getAddPermission() {
        return "ALL";
    }

    @Override
    protected String getUpdatePermission() {
        return "ALL";
    }

    @Override
    protected String getDeletePermission() {
        return "ALL";
    }

    @Override
    public String getEntityPlural() {
        return SmsConnector.NAME_PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return SmsConnector.NAME_SINGULAR;
    }

    @Override
    public String getPropertyName() {
        return SmsConnector.PLURAL;
    }

    @Override
    protected JpaSpecificationExecutor<SmsConnector> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected String getTableName() {
        return "smsconnector";
    }

    @Override
    protected void onPreSave(SmsConnector entity) {
        reuseSavedKey(entity);
        validateProviderConfig(entity);
    }

    private void reuseSavedKey(SmsConnector entity) {
        if (entity.getReuseKeyFrom() == null) {
            return;
        }
        SmsConnector source = repository.findById(entity.getReuseKeyFrom())
                .filter(found -> found.getProvider() != null && found.getProvider().equalsIgnoreCase(entity.getProvider()))
                .filter(this::isOwnedByCurrentUser)
                .orElseThrow(() -> new IllegalArgumentException("The saved credentials to reuse were not found"));
        entity.setKey(source.getKey());
        if (isBlank(entity.getSecret())) {
            entity.setSecret(source.getSecret());
        }
        if (isBlank(entity.getUrl())) {
            entity.setUrl(source.getUrl());
        }
    }

    private boolean isOwnedByCurrentUser(SmsConnector connector) {
        Optional<User> user = userResolver.getCurrentUser();
        return user.isPresent() && connector.getCreatedBy() != null && connector.getCreatedBy().getId().equals(user.get().getId());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    protected SmsConnector onPreUpdate(SmsConnector newEntity, SmsConnector oldEntity) {
        SmsConnector merged = super.onPreUpdate(newEntity, oldEntity);
        validateProviderConfig(merged);
        return merged;
    }

    private void validateProviderConfig(SmsConnector entity) {
        String provider = entity.getProvider();
        if ("BEEM".equalsIgnoreCase(provider) || "NEXT".equalsIgnoreCase(provider)
                || "WHATSAPP".equalsIgnoreCase(provider)) {
            if (entity.getUrl() == null || entity.getUrl().isBlank()) {
                throw new IllegalArgumentException("URL is required for " + provider);
            }
            if (entity.getKey() == null || entity.getKey().isBlank()) {
                throw new IllegalArgumentException("API Key is required for " + provider);
            }
            // WhatsAppSender only sends `key` as a Bearer token; `secret` is only read by
            // BeemSender/NextSmsSender's basic-auth pair, so it's not required (and the
            // frontend hides the field) for WHATSAPP.
            if (!"WHATSAPP".equalsIgnoreCase(provider)
                    && (entity.getSecret() == null || entity.getSecret().isBlank())) {
                throw new IllegalArgumentException("Secret Key is required for " + provider);
            }
            if (entity.getSenderId() == null || entity.getSenderId().isBlank()) {
                throw new IllegalArgumentException("Sender ID is required for " + provider);
            }
        }
    }

    @Override
    protected void validateDelete(SmsConnector entity) {
        if (Boolean.TRUE.equals(entity.getActive())) {
            throw new IllegalStateException("You cannot delete an active connector");
        }
        if ((entity.getProvider() + "_SYSTEM").equalsIgnoreCase(entity.getProvider())) {
            throw new IllegalStateException("You cannot delete a system connector");
        }
    }

    @Override
    public SmsConnector update(UUID id, SmsConnector entity) {
        checkPermission(getUpdatePermission());
        SmsConnector existing = findAccessibleById(id);

        if (!Boolean.TRUE.equals(isAdminPermission())
                && (existing.getProvider() + "_SYSTEM").equalsIgnoreCase(existing.getProvider())) {
            throw new IllegalStateException("You cannot update a system connector");
        }

        if (entity.getKey() != null && entity.getKey().contains("****")) {
            entity.setKey(existing.getKey());
        }
        if (entity.getSecret() != null && entity.getSecret().contains("****")) {
            entity.setSecret(existing.getSecret());
        }

        return super.update(id, entity);
    }
}
