package com.flexcodelabs.flextuma.core.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;

@Repository
public interface SmsLogRepository extends BaseRepository<SmsLog, UUID>,
		JpaSpecificationExecutor<SmsLog> {

	List<SmsLog> findTop50ByStatusOrderByCreatedAsc(SmsLogStatus status);
}
