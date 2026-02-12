package com.flexcodelabs.flextuma.core.repositories;

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

	Optional<SmsConnector> findFirstByCreatedByAndActiveTrue(User createdBy);

}
