package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SmsTemplateRepository extends JpaRepository<SmsTemplate, UUID>,
	JpaSpecificationExecutor<SmsTemplate> {
}
