package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppTemplateRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Read-only CRUD browsing for Meta-synced WhatsApp templates. READ uses the
 * "ALL" sentinel (open
 * to any tenant user, scoped to their own rows by BaseService's tenant
 * filtering), but ADD/UPDATE/
 * DELETE require a dedicated permission no tenant role is ever granted -- so,
 * unlike "ALL", those
 * aren't satisfied by the generic tenant-user bypass in
 * BaseService#checkPermission, and only
 * SUPER_ADMIN (or a future explicit grant) can hit them via the inherited
 * BaseController routes.
 * Writes in practice happen only through WhatsAppTemplateSyncService and the
 * webhook's
 * message_template_status_update handler, both of which save via the repository
 * directly.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppTemplateService extends BaseService<WhatsAppTemplate> {
    private final WhatsAppTemplateRepository repository;

    @Override
    protected JpaRepository<WhatsAppTemplate, UUID> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<WhatsAppTemplate> getRepositoryAsExecutor() {
        return repository;
    }

    /** Closes BaseService#checkPermission's generic "ALL" bypass for ADD/UPDATE/DELETE -- without
     * this override (the same one User/Role/Organisation/Privilege/Wallet/TenantFeature already
     * use) any tenant user holding the common "ALL" authority could write template rows, e.g.
     * self-declare status: APPROVED for a template Meta never approved. READ is unaffected: its
     * permission constant is the literal "ALL" sentinel, satisfied by checkPermission's second
     * clause regardless of this override. */
    @Override
    protected boolean isAdminEntity() {
        return true;
    }

    @Override
    protected String getReadPermission() {
        return WhatsAppTemplate.READ;
    }

    @Override
    protected String getAddPermission() {
        return WhatsAppTemplate.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return WhatsAppTemplate.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return WhatsAppTemplate.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return WhatsAppTemplate.NAME_PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return WhatsAppTemplate.NAME_SINGULAR;
    }

    @Override
    public String getPropertyName() {
        return WhatsAppTemplate.PLURAL;
    }

    @Override
    protected String getTableName() {
        return "whatsapp_template";
    }
}
