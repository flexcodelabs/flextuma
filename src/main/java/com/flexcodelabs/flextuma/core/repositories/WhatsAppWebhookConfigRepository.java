package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppWebhookConfigRepository extends BaseRepository<WhatsAppWebhookConfig, UUID>, JpaSpecificationExecutor<WhatsAppWebhookConfig> {

    // createdBy and connector are fetched eagerly here: the webhook controller reads
    // config.getCreatedBy() (to stamp ownership on inbound messages/relay rows) and
    // config.getConnector() (to pick the right Meta access token for media downloads) after
    // this lookup's own transaction has already closed (spring.jpa.open-in-view=false, no
    // @Transactional on the controller), so a lazy load at that point throws
    // LazyInitializationException. connector is nullable, so LEFT JOIN FETCH.
    @Query("SELECT c FROM WhatsAppWebhookConfig c JOIN FETCH c.createdBy LEFT JOIN FETCH c.connector WHERE c.phoneNumberId = :phoneNumberId AND c.active = true")
    Optional<WhatsAppWebhookConfig> findByPhoneNumberIdAndActiveTrue(@Param("phoneNumberId") String phoneNumberId);

    Optional<WhatsAppWebhookConfig> findByVerifyTokenAndActiveTrue(String verifyToken);

    @Query("SELECT c FROM WhatsAppWebhookConfig c JOIN FETCH c.createdBy LEFT JOIN FETCH c.connector WHERE c.callbackToken = :callbackToken AND c.active = true")
    Optional<WhatsAppWebhookConfig> findByCallbackTokenAndActiveTrue(@Param("callbackToken") String callbackToken);
}
