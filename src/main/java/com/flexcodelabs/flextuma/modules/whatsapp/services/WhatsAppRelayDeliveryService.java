package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppRelayDeliveryRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * No REST controller — this exists purely so relay deliveries get the same
 * tenant-scoped {@link BaseService#findAllPaginated} that the webhook overview
 * aggregation reuses, instead of a hand-rolled tenant filter.
 */
@Service @RequiredArgsConstructor
public class WhatsAppRelayDeliveryService extends BaseService<WhatsAppRelayDelivery> {
    private final WhatsAppRelayDeliveryRepository repository;

    protected JpaRepository<WhatsAppRelayDelivery, UUID> getRepository() { return repository; }
    protected JpaSpecificationExecutor<WhatsAppRelayDelivery> getRepositoryAsExecutor() { return repository; }
    protected String getReadPermission() { return "ALL"; }
    protected String getAddPermission() { return "ALL"; }
    protected String getUpdatePermission() { return "ALL"; }
    protected String getDeletePermission() { return "ALL"; }
    public String getEntityPlural() { return WhatsAppRelayDelivery.NAME_PLURAL; }
    protected String getEntitySingular() { return WhatsAppRelayDelivery.NAME_SINGULAR; }
    public String getPropertyName() { return WhatsAppRelayDelivery.PLURAL; }
    protected String getTableName() { return "whatsapp_relay_delivery"; }
}
