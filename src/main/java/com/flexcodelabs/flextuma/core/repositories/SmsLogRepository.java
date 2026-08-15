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
import org.springframework.data.jpa.repository.Modifying;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;

@Repository
public interface SmsLogRepository extends BaseRepository<SmsLog, UUID>,
		JpaSpecificationExecutor<SmsLog> {

	List<SmsLog> findTop50ByStatusOrderByCreatedAsc(SmsLogStatus status);

	List<SmsLog> findTop50ByStatusAndProviderMessageIdIsNotNullOrderByCreatedAsc(SmsLogStatus status);

	@org.springframework.data.jpa.repository.Query("SELECT s FROM SmsLog s WHERE s.status = :status AND (s.scheduledAt IS NULL OR s.scheduledAt <= :now) ORDER BY s.created ASC")
	List<SmsLog> findDueMessages(
			@org.springframework.data.repository.query.Param("status") SmsLogStatus status,
			@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now,
			org.springframework.data.domain.Pageable pageable);

	Optional<SmsLog> findByProviderMessageId(String providerMessageId);

	@Modifying
	@org.springframework.data.jpa.repository.Query("UPDATE SmsLog s SET s.status = :processing WHERE s.id = :id AND s.status = :pending")
	int claimPendingMessage(@org.springframework.data.repository.query.Param("id") UUID id,
			@org.springframework.data.repository.query.Param("pending") SmsLogStatus pending,
			@org.springframework.data.repository.query.Param("processing") SmsLogStatus processing);

	Page<SmsLog> findByCreatedByOrderByCreatedDesc(User user, Pageable pageable);

	long countByCreatedByAndStatus(User user, SmsLogStatus status);

	long countByCreatedByAndStatusIn(User user, Collection<SmsLogStatus> statuses);

	long countByCreatedByAndStatusInAndCreatedGreaterThanEqual(User user, Collection<SmsLogStatus> statuses,
			LocalDateTime created);

	long countByStatus(SmsLogStatus status);

	long countByStatusIn(Collection<SmsLogStatus> statuses);

	long countByStatusInAndCreatedGreaterThanEqual(Collection<SmsLogStatus> statuses, LocalDateTime created);
}
