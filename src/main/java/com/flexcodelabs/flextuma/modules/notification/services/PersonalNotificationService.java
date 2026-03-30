package com.flexcodelabs.flextuma.modules.notification.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.notification.PersonalNotification;
import com.flexcodelabs.flextuma.core.enums.PersonalNotificationType;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.PersonalNotificationRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import com.flexcodelabs.flextuma.modules.notification.dtos.NotificationSummaryDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PersonalNotificationService extends BaseService<PersonalNotification> {

    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("10000");

    private final PersonalNotificationRepository repository;
    private final CurrentUserResolver currentUserResolver;

    @Override
    protected JpaRepository<PersonalNotification, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return PersonalNotification.READ;
    }

    @Override
    protected String getAddPermission() {
        return PersonalNotification.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return PersonalNotification.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return PersonalNotification.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return PersonalNotification.NAME_PLURAL;
    }

    @Override
    public String getPropertyName() {
        return PersonalNotification.PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return PersonalNotification.NAME_SINGULAR;
    }

    @Override
    protected JpaSpecificationExecutor<PersonalNotification> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected String getTableName() {
        return "personalnotification";
    }

    @Override
    protected void onPreSave(PersonalNotification entity) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Personal notifications cannot be created manually");
    }

    @Override
    protected PersonalNotification onPreUpdate(PersonalNotification newEntity, PersonalNotification oldEntity) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Personal notifications cannot be updated manually");
    }

    @Override
    protected void validateDelete(PersonalNotification entity) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Personal notifications cannot be deleted manually");
    }

    @Transactional(readOnly = true)
    public NotificationSummaryDTO getSummary(int pageSize) {
        User user = getCurrentUser();
        int safeLimit = Math.max(1, Math.min(pageSize, 20));
        List<PersonalNotification> notifications = repository.findByCreatedByOrderByCreatedDesc(user,
                PageRequest.of(0, safeLimit));

        notifications.forEach(this::initializeAssociationsForResponse);

        return NotificationSummaryDTO.builder()
                .unreadCount(repository.countByCreatedByAndReadAtIsNull(user))
                .notifications(notifications)
                .build();
    }

    @Transactional
    public PersonalNotification markAsRead(UUID id) {
        User user = getCurrentUser();
        PersonalNotification notification = repository.findByIdAndCreatedBy(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
        }

        PersonalNotification saved = repository.save(notification);
        initializeAssociationsForResponse(saved);
        return saved;
    }

    @Transactional
    public Map<String, Object> markAllAsRead() {
        User user = getCurrentUser();
        int updated = repository.markAllAsRead(user, LocalDateTime.now());
        return Map.of("message", "Notifications marked as read", "updated", updated);
    }

    @Transactional
    public void notifyLowBalance(User user, BigDecimal balance) {
        if (user == null || balance == null || balance.compareTo(LOW_BALANCE_THRESHOLD) >= 0) {
            return;
        }

        PersonalNotification notification = repository.findByCreatedByAndTypeAndReadAtIsNull(user,
                PersonalNotificationType.LOW_BALANCE_ALERT)
                .orElseGet(PersonalNotification::new);

        notification.setCreatedBy(user);
        notification.setType(PersonalNotificationType.LOW_BALANCE_ALERT);
        notification.setTitle("Low Balance Alert");
        notification.setMessage("Your wallet balance is below TZS 10,000. Current balance: TZS "
                + balance.stripTrailingZeros().toPlainString() + ".");
        notification.setLinkUrl("/finance/wallet");
        notification.setCode("LOW_BALANCE_" + user.getId());
        notification.setReadAt(null);
        repository.save(notification);
    }

    @Transactional
    public void notifyCampaignCompleted(User user, String campaignName) {
        if (user == null) {
            return;
        }

        PersonalNotification notification = new PersonalNotification();
        notification.setCreatedBy(user);
        notification.setType(PersonalNotificationType.CAMPAIGN_COMPLETED);
        notification.setTitle("Campaign Completed");
        notification.setMessage((campaignName == null || campaignName.isBlank() ? "Your campaign"
                : campaignName) + " has finished sending.");
        notification.setLinkUrl("/campaigns");
        notification.setCode("CAMPAIGN_COMPLETED_" + UUID.randomUUID());
        repository.save(notification);
    }

    private User getCurrentUser() {
        return currentUserResolver.getCurrentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }
}
