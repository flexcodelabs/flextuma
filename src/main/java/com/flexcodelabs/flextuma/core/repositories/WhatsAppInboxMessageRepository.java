package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface WhatsAppInboxMessageRepository extends BaseRepository<WhatsAppInboxMessage, UUID>,
        JpaSpecificationExecutor<WhatsAppInboxMessage> {
    boolean existsByProviderMessageId(String providerMessageId);
}
