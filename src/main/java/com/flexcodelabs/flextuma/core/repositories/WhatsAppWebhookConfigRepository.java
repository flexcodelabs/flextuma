package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppWebhookConfigRepository extends BaseRepository<WhatsAppWebhookConfig, UUID>, JpaSpecificationExecutor<WhatsAppWebhookConfig> {
    Optional<WhatsAppWebhookConfig> findByPhoneNumberIdAndActiveTrue(String phoneNumberId);
    Optional<WhatsAppWebhookConfig> findByVerifyTokenAndActiveTrue(String verifyToken);
    Optional<WhatsAppWebhookConfig> findByCallbackTokenAndActiveTrue(String callbackToken);
}
