package com.flexcodelabs.flextuma.modules.sms.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.core.repositories.SmsTemplateRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SmsTemplateService extends BaseService<SmsTemplate> {

    private final SmsTemplateRepository repository;

    @Override
    protected JpaRepository<SmsTemplate, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return SmsTemplate.READ;
    }

    @Override
    protected String getAddPermission() {
        return SmsTemplate.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return SmsTemplate.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return SmsTemplate.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return SmsTemplate.NAME_PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return SmsTemplate.NAME_SINGULAR;
    }

    @Override
    public String getPropertyName() {
        return SmsTemplate.PLURAL;
    }

    @Override
    protected JpaSpecificationExecutor<SmsTemplate> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected void validateDelete(SmsTemplate entity) {
        if (Boolean.TRUE.equals(entity.getActive())) {
            throw new IllegalStateException("Cannot delete an active template");
        }
    }
}
