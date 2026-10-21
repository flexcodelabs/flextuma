package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookEvent;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface WhatsAppWebhookEventRepository extends BaseRepository<WhatsAppWebhookEvent, UUID>,
        JpaSpecificationExecutor<WhatsAppWebhookEvent> {

    @Modifying
    @Query("DELETE FROM WhatsAppWebhookEvent e WHERE e.config.id = :configId AND e.receivedAt < :cutoff")
    int deleteByConfigIdAndReceivedAtBefore(@Param("configId") UUID configId, @Param("cutoff") LocalDateTime cutoff);
}
