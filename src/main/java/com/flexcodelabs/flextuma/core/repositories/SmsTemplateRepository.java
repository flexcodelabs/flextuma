package com.flexcodelabs.flextuma.core.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;

@Repository
public interface SmsTemplateRepository extends BaseRepository<SmsTemplate, UUID>,
		JpaSpecificationExecutor<SmsTemplate> {
	Optional<SmsTemplate> findByCreatedByAndCode(User createdBy, String code);

}
