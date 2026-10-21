package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppRelayDelivery;
import com.flexcodelabs.flextuma.core.enums.WhatsAppRelayStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface WhatsAppRelayDeliveryRepository extends BaseRepository<WhatsAppRelayDelivery, UUID>,
        JpaSpecificationExecutor<WhatsAppRelayDelivery> {
    boolean existsByConfigId(UUID configId);


    @Query("SELECT d FROM WhatsAppRelayDelivery d WHERE d.status = :status"
            + " AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now) ORDER BY d.created ASC")
    List<WhatsAppRelayDelivery> findDueDeliveries(@Param("status") WhatsAppRelayStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Modifying
    @Query("UPDATE WhatsAppRelayDelivery d SET d.status = :processing WHERE d.id = :id AND d.status = :pending")
    int claimPendingDelivery(@Param("id") UUID id,
            @Param("pending") WhatsAppRelayStatus pending,
            @Param("processing") WhatsAppRelayStatus processing);
}
