package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.enums.WhatsAppRelayStatus;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WhatsAppRelayDeliveryRepository extends BaseRepository<WhatsAppRelayDelivery, UUID>,
        JpaSpecificationExecutor<WhatsAppRelayDelivery> {

    List<WhatsAppRelayDelivery> findTop50ByStatusOrderByCreatedAsc(WhatsAppRelayStatus status);

    @Modifying
    @Query("UPDATE WhatsAppRelayDelivery d SET d.status = :processing WHERE d.id = :id AND d.status = :pending")
    int claimPendingDelivery(@Param("id") UUID id,
            @Param("pending") WhatsAppRelayStatus pending,
            @Param("processing") WhatsAppRelayStatus processing);
}
