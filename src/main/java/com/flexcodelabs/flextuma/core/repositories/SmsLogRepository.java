package com.flexcodelabs.flextuma.core.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;

@Repository
public interface SmsLogRepository extends BaseRepository<SmsLog, UUID>,
		JpaSpecificationExecutor<SmsLog> {

	List<SmsLog> findTop50ByStatusOrderByCreatedAsc(SmsLogStatus status);

	@org.springframework.data.jpa.repository.Query("SELECT s FROM SmsLog s WHERE s.status = :status AND (s.scheduledAt IS NULL OR s.scheduledAt <= :now) ORDER BY s.created ASC")
	List<SmsLog> findDueMessages(
			@org.springframework.data.repository.query.Param("status") SmsLogStatus status,
			@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now,
			org.springframework.data.domain.Pageable pageable);

	Optional<SmsLog> findByProviderResponse(String providerResponse);
}
