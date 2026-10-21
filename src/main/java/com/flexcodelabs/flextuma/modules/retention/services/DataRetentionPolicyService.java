package com.flexcodelabs.flextuma.modules.retention.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.settings.DataRetentionPolicy;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.DataRetentionPolicyRepository;
import com.flexcodelabs.flextuma.modules.retention.dtos.DataRetentionPolicyDTO;
import com.flexcodelabs.flextuma.modules.retention.dtos.DataRetentionPolicyUpdateDTO;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppEventRetentionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Reads and writes the signed-in user's account retention policy (their organisation's when they
 * have one). Any signed-in member can change it -- the same trust level as managing the account's
 * WhatsApp webhooks.
 */
@Service
@RequiredArgsConstructor
public class DataRetentionPolicyService {
    private final DataRetentionPolicyRepository repository;
    private final CurrentUserResolver currentUserResolver;
    private final WhatsAppEventRetentionPolicy whatsAppEventPolicy;

    @Transactional(readOnly = true)
    public DataRetentionPolicyDTO getForCurrentUser() {
        User user = currentUser();
        return toDto(user, find(user).map(DataRetentionPolicy::getWhatsappEventRetentionDays).orElse(null));
    }

    @Transactional
    public DataRetentionPolicyDTO updateForCurrentUser(DataRetentionPolicyUpdateDTO update) {
        User user = currentUser();
        Integer days = update == null ? null : update.whatsappEventRetentionDays();
        whatsAppEventPolicy.validateOverride(days);
        Integer stored = days == null || days <= 0 ? null : days;

        DataRetentionPolicy policy = find(user).orElseGet(() -> {
            DataRetentionPolicy created = new DataRetentionPolicy();
            if (user.getOrganisation() != null) created.setOrganisation(user.getOrganisation());
            else created.setOwner(user);
            return created;
        });
        policy.setWhatsappEventRetentionDays(stored);
        repository.save(policy);
        return toDto(user, stored);
    }

    /** The account-level WhatsApp event retention for the tenant that owns a resource, or null. */
    @Transactional(readOnly = true)
    public Integer whatsAppEventDaysFor(User owner) {
        if (owner == null) return null;
        return find(owner).map(DataRetentionPolicy::getWhatsappEventRetentionDays).orElse(null);
    }

    private Optional<DataRetentionPolicy> find(User user) {
        return user.getOrganisation() != null
                ? repository.findByOrganisation(user.getOrganisation())
                : repository.findByOwner(user);
    }

    private DataRetentionPolicyDTO toDto(User user, Integer days) {
        return DataRetentionPolicyDTO.builder()
                .scope(user.getOrganisation() != null ? "ORGANISATION" : "USER")
                .whatsappEventRetentionDays(days)
                .whatsappEventDefaultDays(whatsAppEventPolicy.defaultDays())
                .whatsappEventMaxDays(whatsAppEventPolicy.maxDays())
                .whatsappEventEffectiveDays(whatsAppEventPolicy.effectiveDays(null, days))
                .build();
    }

    private User currentUser() {
        return currentUserResolver.getCurrentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }
}
