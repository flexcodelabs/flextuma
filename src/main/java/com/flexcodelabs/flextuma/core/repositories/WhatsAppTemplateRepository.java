package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppTemplateRepository extends BaseRepository<WhatsAppTemplate, UUID>,
        JpaSpecificationExecutor<WhatsAppTemplate> {

    List<WhatsAppTemplate> findByConnectorAndCreatedBy(SmsConnector connector, User createdBy);

    Optional<WhatsAppTemplate> findByNameAndLanguageAndConnectorAndCreatedBy(String name, String language,
            SmsConnector connector, User createdBy);

    /** Used by the webhook's message_template_status_update handler, which has no tenant context
     * of its own -- metaTemplateId is unique per (id, creator), so findFirst is safe here. */
    Optional<WhatsAppTemplate> findFirstByMetaTemplateId(String metaTemplateId);

    /** Fallback lookup for a status update payload that omits message_template_id. */
    Optional<WhatsAppTemplate> findFirstByNameAndLanguage(String name, String language);
}
