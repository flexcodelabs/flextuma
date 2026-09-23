package com.flexcodelabs.flextuma.core.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;

@Repository
public interface SmsConnectorRepository extends BaseRepository<SmsConnector, UUID>,
		JpaSpecificationExecutor<SmsConnector> {

	Optional<SmsConnector> findByCreatedByAndProviderAndActiveTrue(User createdBy, String provider);

	/** Used by WhatsAppTemplateSyncService: a tenant may own more than one active WHATSAPP
	 * connector (e.g. multiple WABAs), unlike the single-connector assumption the send path's
	 * findByCreatedByAndProviderAndActiveTrue above makes. */
	List<SmsConnector> findAllByCreatedByAndProviderAndActiveTrue(User createdBy, String provider);

	Optional<SmsConnector> findFirstByCreatedByAndActiveTrue(User createdBy);

	Optional<SmsConnector> findByProviderAndCode(String provider, String code);

	Optional<SmsConnector> findByIdAndActiveTrue(UUID id);

}
