package com.flexcodelabs.flextuma.core.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.flexcodelabs.flextuma.core.entities.auth.User;
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

	Page<SmsLog> findByCreatedByOrderByCreatedDesc(User user, Pageable pageable);

	long countByCreatedByAndStatus(User user, SmsLogStatus status);

	long countByCreatedByAndStatusIn(User user, Collection<SmsLogStatus> statuses);

	long countByCreatedByAndStatusInAndCreatedGreaterThanEqual(User user, Collection<SmsLogStatus> statuses,
			LocalDateTime created);

	long countByStatus(SmsLogStatus status);

	long countByStatusIn(Collection<SmsLogStatus> statuses);

	long countByStatusInAndCreatedGreaterThanEqual(Collection<SmsLogStatus> statuses, LocalDateTime created);
}
