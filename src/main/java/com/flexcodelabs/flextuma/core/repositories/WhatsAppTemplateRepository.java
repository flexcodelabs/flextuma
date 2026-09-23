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

    /** Used by the webhook's message_template_status_update handler, scoped to the webhook
     * config's owner -- metaTemplateId is only unique per (id, creator), so an unscoped lookup
     * could match a different tenant's row of the same Meta template id. */
    Optional<WhatsAppTemplate> findFirstByMetaTemplateIdAndCreatedBy(String metaTemplateId, User createdBy);

    /** Fallback lookup for a status update payload that omits message_template_id, scoped to the
     * webhook config's owner so two tenants sharing a common template name/language (e.g.
     * "otp_verification") can't have their status cross-contaminated. */
    Optional<WhatsAppTemplate> findFirstByNameAndLanguageAndCreatedBy(String name, String language, User createdBy);
}
