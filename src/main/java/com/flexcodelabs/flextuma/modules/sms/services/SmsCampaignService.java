package com.flexcodelabs.flextuma.modules.sms.services;

import com.flexcodelabs.flextuma.core.entities.sms.SmsCampaign;
import com.flexcodelabs.flextuma.core.enums.SmsCampaignStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsCampaignRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmsCampaignService extends BaseService<SmsCampaign> {

    private final SmsCampaignRepository repository;

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
    }

    @Override
    protected void validateDelete(SmsCampaign entity) {
        if (entity.getStatus() == SmsCampaignStatus.PROCESSING) {
            throw new IllegalStateException("Cannot delete a campaign that is currently processing");
        }
    }
}
