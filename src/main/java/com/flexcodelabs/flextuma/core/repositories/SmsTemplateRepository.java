package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.SmsTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SmsTemplateRepository extends JpaRepository<SmsTemplateEntity, UUID> {
    // This empty interface tells Spring: "I manage SmsTemplateEntity"
}