package com.flexcodelabs.flextuma.core.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.notification.PersonalNotification;
import com.flexcodelabs.flextuma.core.enums.PersonalNotificationType;

@Repository
public interface PersonalNotificationRepository extends BaseRepository<PersonalNotification, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<PersonalNotification> {

    long countByCreatedByAndReadAtIsNull(User user);

    List<PersonalNotification> findByCreatedByOrderByCreatedDesc(User user, Pageable pageable);

    Optional<PersonalNotification> findByIdAndCreatedBy(UUID id, User user);

    Optional<PersonalNotification> findByCreatedByAndTypeAndReadAtIsNull(User user, PersonalNotificationType type);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
            UPDATE PersonalNotification p
            SET p.readAt = :readAt
            WHERE p.createdBy = :user AND p.readAt IS NULL
            """)
    int markAllAsRead(
            @org.springframework.data.repository.query.Param("user") User user,
            @org.springframework.data.repository.query.Param("readAt") java.time.LocalDateTime readAt);
}
