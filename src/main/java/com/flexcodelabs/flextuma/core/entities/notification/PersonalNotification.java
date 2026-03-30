package com.flexcodelabs.flextuma.core.entities.notification;

import java.time.LocalDateTime;

import com.flexcodelabs.flextuma.core.entities.base.Owner;
import com.flexcodelabs.flextuma.core.enums.PersonalNotificationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "personalnotification", uniqueConstraints = {
        @UniqueConstraint(name = "unique_personal_notification_code", columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PersonalNotification extends Owner {

    public static final String PLURAL = "personalNotifications";
    public static final String NAME_PLURAL = "Personal Notifications";
    public static final String NAME_SINGULAR = "Personal Notification";

    public static final String ALL = "ALL";
    public static final String READ = ALL;
    public static final String ADD = ALL;
    public static final String DELETE = ALL;
    public static final String UPDATE = ALL;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PersonalNotificationType type;

    @Column(name = "link_url")
    private String linkUrl;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    public boolean isUnread() {
        return readAt == null;
    }
}
