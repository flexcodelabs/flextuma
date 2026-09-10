package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppTenantStorageUsageDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface WhatsAppInboxMessageRepository extends BaseRepository<WhatsAppInboxMessage, UUID>,
        JpaSpecificationExecutor<WhatsAppInboxMessage> {
    boolean existsByProviderMessageId(String providerMessageId);

    /** Candidates for {@code WhatsAppMediaBackfillWorker}: messages that carry a Meta media id
     * but never got a cached copy on disk, bounded to recent messages since Meta's CDN only
     * keeps media retrievable for a limited window. */
    List<WhatsAppInboxMessage> findByMediaIdIsNotNullAndMediaPathIsNullAndReceivedAtAfter(
            LocalDateTime receivedAfter, Pageable pageable);

    /** Bytes of WhatsApp media stored for one tenant: everyone in {@code organisation} when it
     * is non-null, otherwise just {@code user} (the org-less-account fallback). */
    @Query("SELECT COALESCE(SUM(m.mediaSize), 0) FROM WhatsAppInboxMessage m WHERE m.mediaSize IS NOT NULL AND "
            + "((:organisation IS NOT NULL AND m.createdBy.organisation = :organisation) "
            + "OR (:organisation IS NULL AND m.createdBy = :user))")
    long sumMediaStorageBytes(@Param("organisation") Organisation organisation, @Param("user") User user);

    /** Count of stored media files for the same tenant scope as {@link #sumMediaStorageBytes}. */
    @Query("SELECT COUNT(m) FROM WhatsAppInboxMessage m WHERE m.mediaSize IS NOT NULL AND "
            + "((:organisation IS NOT NULL AND m.createdBy.organisation = :organisation) "
            + "OR (:organisation IS NULL AND m.createdBy = :user))")
    long countMediaForTenant(@Param("organisation") Organisation organisation, @Param("user") User user);

    /** Storage breakdown across every tenant (organisation, or org-less user), for admin views. */
    @Query("SELECT new com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppTenantStorageUsageDTO("
            + "COALESCE(o.id, u.id), COALESCE(o.name, u.username), SUM(m.mediaSize), COUNT(m)) "
            + "FROM WhatsAppInboxMessage m JOIN m.createdBy u LEFT JOIN u.organisation o "
            + "WHERE m.mediaSize IS NOT NULL "
            + "GROUP BY COALESCE(o.id, u.id), COALESCE(o.name, u.username) "
            + "ORDER BY SUM(m.mediaSize) DESC")
    List<WhatsAppTenantStorageUsageDTO> findStorageUsageByTenant();
}
