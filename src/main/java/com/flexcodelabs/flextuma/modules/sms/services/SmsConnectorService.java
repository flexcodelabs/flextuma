package com.flexcodelabs.flextuma.modules.sms.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SmsConnectorService extends BaseService<SmsConnector> {

    private final SmsConnectorRepository repository;

    @Override
    protected JpaRepository<SmsConnector, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return SmsConnector.READ;
    }

    @Override
    protected String getAddPermission() {
        return SmsConnector.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return SmsConnector.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return SmsConnector.DELETE;
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
        validateProviderConfig(entity);
    }

    @Override
    protected SmsConnector onPreUpdate(SmsConnector newEntity, SmsConnector oldEntity) {
        SmsConnector merged = super.onPreUpdate(newEntity, oldEntity);
        validateProviderConfig(merged);
        return merged;
    }

    private void validateProviderConfig(SmsConnector entity) {
        String provider = entity.getProvider();
        if ("BEEM".equalsIgnoreCase(provider) || "NEXT".equalsIgnoreCase(provider)) {
            if (entity.getUrl() == null || entity.getUrl().isBlank()) {
                throw new IllegalArgumentException("URL is required for " + provider);
            }
            if (entity.getKey() == null || entity.getKey().isBlank()) {
                throw new IllegalArgumentException("API Key is required for " + provider);
            }
            if (entity.getSecret() == null || entity.getSecret().isBlank()) {
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
        SmsConnector existing = getRepository().findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, getEntitySingular() + " not found"));

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
