package com.flexcodelabs.flextuma.core.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;

@Repository
public interface SmsConnectorRepository extends BaseRepository<SmsConnector, UUID>,
		JpaSpecificationExecutor<SmsConnector> {

	/** A tenant may own several active connectors per provider (e.g. one per WhatsApp phone
	 * number), so this lists them all: connectors flagged isDefault first, then oldest first. */
	@Query("SELECT c FROM SmsConnector c WHERE c.createdBy = :createdBy AND c.provider = :provider AND c.active = true"
			+ " ORDER BY CASE WHEN c.isDefault = true THEN 0 ELSE 1 END, c.created ASC")
	List<SmsConnector> findActiveByCreatedByAndProviderPreferringDefault(@Param("createdBy") User createdBy,
			@Param("provider") String provider);

	/** The connector used when a caller doesn't pick one explicitly (see the ordering above). */
	default Optional<SmsConnector> findPreferredActiveConnector(User createdBy, String provider) {
		return findActiveByCreatedByAndProviderPreferringDefault(createdBy, provider).stream().findFirst();
	}

	/** Used by WhatsAppTemplateSyncService to sync every WABA a tenant has connected. */
	List<SmsConnector> findAllByCreatedByAndProviderAndActiveTrue(User createdBy, String provider);

	Optional<SmsConnector> findFirstByCreatedByAndActiveTrue(User createdBy);

	Optional<SmsConnector> findByProviderAndCode(String provider, String code);

	Optional<SmsConnector> findByIdAndActiveTrue(UUID id);

}
