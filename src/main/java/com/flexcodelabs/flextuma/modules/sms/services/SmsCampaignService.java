package com.flexcodelabs.flextuma.modules.sms.services;

import com.flexcodelabs.flextuma.core.entities.sms.SmsCampaign;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.core.enums.SmsCampaignStatus;
import com.flexcodelabs.flextuma.core.enums.SmsTemplateStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsCampaignRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsTemplateRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmsCampaignService extends BaseService<SmsCampaign> {

    private final SmsCampaignRepository repository;
    private final SmsTemplateRepository templateRepository;

    @Override
    protected JpaRepository<SmsCampaign, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return SmsCampaign.READ;
    }

    @Override
    protected String getAddPermission() {
        return SmsCampaign.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return SmsCampaign.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return SmsCampaign.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return SmsCampaign.NAME_PLURAL;
    }

    @Override
    public String getPropertyName() {
        return SmsCampaign.PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return SmsCampaign.NAME_SINGULAR;
    }

    @Override
    protected JpaSpecificationExecutor<SmsCampaign> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected String getTableName() {
        return "smscampaign";
    }

    @Override
    protected void onPreSave(SmsCampaign entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(SmsCampaignStatus.SCHEDULED);
        }
        validateTemplateIsActive(entity);
    }

    @Override
    protected SmsCampaign onPreUpdate(SmsCampaign newEntity, SmsCampaign oldEntity) {
        SmsCampaign merged = super.onPreUpdate(newEntity, oldEntity);
        validateTemplateIsActive(newEntity);
        return merged;
    }

    private void validateTemplateIsActive(SmsCampaign entity) {
        if (entity.getTemplate() == null || entity.getTemplate().getId() == null) {
            return;
        }
        SmsTemplate template = templateRepository.findById(entity.getTemplate().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
        if (template.getStatus() != SmsTemplateStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot use an inactive template for a campaign");
        }
    }

    @Override
    protected void validateDelete(SmsCampaign entity) {
        if (entity.getStatus() == SmsCampaignStatus.PROCESSING) {
            throw new IllegalStateException("Cannot delete a campaign that is currently processing");
        }
    }
}
