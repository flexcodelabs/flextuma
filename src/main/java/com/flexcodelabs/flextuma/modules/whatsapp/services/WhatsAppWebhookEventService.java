package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookEvent;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppWebhookEventRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import com.flexcodelabs.flextuma.core.dtos.Pagination;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only browsing of the WhatsApp webhook event log. Same permission shape as
 * WhatsAppTemplateService: READ is the "ALL" sentinel (any tenant user, scoped to their own rows by
 * BaseService's tenant filtering); writes need a permission no tenant role holds, since events are
 * only ever recorded by WhatsAppWebhookController via the repository.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppWebhookEventService extends BaseService<WhatsAppWebhookEvent> {
    private final WhatsAppWebhookEventRepository repository;

    @Override
    protected JpaRepository<WhatsAppWebhookEvent, UUID> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<WhatsAppWebhookEvent> getRepositoryAsExecutor() {
        return repository;
    }

    /** Closes BaseService#checkPermission's generic "ALL" bypass so tenants can't fabricate events. */
    @Override
    protected boolean isAdminEntity() {
        return true;
    }

    /** isAdminEntity() also disables the "ALL" sentinel for READ; let it through explicitly. */
    @Override
    protected void checkPermission(String requiredPermission) {
        if (WhatsAppWebhookEvent.READ.equals(requiredPermission)) {
            return;
        }
        super.checkPermission(requiredPermission);
    }

    /** A log reads newest-first; the shared list endpoint otherwise returns rows unordered. */
    @Override
    @Transactional(readOnly = true)
    public Pagination<WhatsAppWebhookEvent> findAllPaginated(Pageable pageable, List<String> filter, String fields) {
        return super.findAllPaginated(newestFirst(pageable), filter, fields);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<WhatsAppWebhookEvent> findAllPaginated(Pageable pageable, List<String> filter, String fields,
            String rootJoin) {
        return super.findAllPaginated(newestFirst(pageable), filter, fields, rootJoin);
    }

    private Pageable newestFirst(Pageable pageable) {
        return pageable.isPaged() && pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "receivedAt"))
                : pageable;
    }

    @Override
    protected String getReadPermission() {
        return WhatsAppWebhookEvent.READ;
    }

    @Override
    protected String getAddPermission() {
        return WhatsAppWebhookEvent.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return WhatsAppWebhookEvent.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return WhatsAppWebhookEvent.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return WhatsAppWebhookEvent.NAME_PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return WhatsAppWebhookEvent.NAME_SINGULAR;
    }

    @Override
    public String getPropertyName() {
        return WhatsAppWebhookEvent.PLURAL;
    }

    @Override
    protected String getTableName() {
        return "whatsapp_webhook_event";
    }
}
