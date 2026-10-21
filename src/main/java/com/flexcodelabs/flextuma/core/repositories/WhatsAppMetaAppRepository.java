package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppMetaApp;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppMetaAppRepository extends BaseRepository<WhatsAppMetaApp, UUID>, JpaSpecificationExecutor<WhatsAppMetaApp> {

    @Query("SELECT a FROM WhatsAppMetaApp a JOIN FETCH a.createdBy WHERE a.callbackToken = :callbackToken AND a.active = true")
    Optional<WhatsAppMetaApp> findByCallbackTokenAndActiveTrue(@Param("callbackToken") String callbackToken);

    Optional<WhatsAppMetaApp> findByIdAndActiveTrue(UUID id);
}
