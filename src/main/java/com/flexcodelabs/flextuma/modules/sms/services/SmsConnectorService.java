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
    protected void validateDelete(SmsConnector entity) {
        if (Boolean.TRUE.equals(entity.getActive())) {
            throw new IllegalStateException("You cannot delete an active connector");
        }
    }
}
