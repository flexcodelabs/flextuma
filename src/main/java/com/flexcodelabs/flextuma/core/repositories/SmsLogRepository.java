package com.flexcodelabs.flextuma.core.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;

@Repository
public interface SmsLogRepository extends JpaRepository<SmsLog, UUID>,
		JpaSpecificationExecutor<SmsLog> {
}
