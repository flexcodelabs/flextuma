package com.flexcodelabs.flextuma.modules.retention.services;

import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.settings.DataRetentionPolicy;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.DataRetentionPolicyRepository;
import com.flexcodelabs.flextuma.modules.retention.dtos.DataRetentionPolicyDTO;
import com.flexcodelabs.flextuma.modules.retention.dtos.DataRetentionPolicyUpdateDTO;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppEventRetentionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRetentionPolicyServiceTest {

    @Mock
    private DataRetentionPolicyRepository repository;

    @Mock
    private CurrentUserResolver currentUserResolver;

    private DataRetentionPolicyService service;

    @BeforeEach
    void setUp() {
        service = new DataRetentionPolicyService(repository, currentUserResolver, new WhatsAppEventRetentionPolicy(30, 90));
    }

    @Test
    void getForCurrentUser_shouldReturnPlatformDefaults_whenNoPolicyExists() {
        User user = user(null);
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(repository.findByOwner(user)).thenReturn(Optional.empty());

        DataRetentionPolicyDTO dto = service.getForCurrentUser();

        assertEquals("USER", dto.scope());
        assertNull(dto.whatsappEventRetentionDays());
        assertEquals(30, dto.whatsappEventDefaultDays());
        assertEquals(90, dto.whatsappEventMaxDays());
        assertEquals(30, dto.whatsappEventEffectiveDays());
    }

    @Test
    void updateForCurrentUser_shouldCreateOrganisationScopedPolicy_whenUserHasOrganisation() {
        Organisation organisation = new Organisation();
        organisation.setId(UUID.randomUUID());
        User user = user(organisation);
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(repository.findByOrganisation(organisation)).thenReturn(Optional.empty());

        DataRetentionPolicyDTO dto = service.updateForCurrentUser(new DataRetentionPolicyUpdateDTO(14));

        ArgumentCaptor<DataRetentionPolicy> captor = ArgumentCaptor.forClass(DataRetentionPolicy.class);
        verify(repository).save(captor.capture());
        assertSame(organisation, captor.getValue().getOrganisation());
        assertNull(captor.getValue().getOwner());
        assertEquals(14, captor.getValue().getWhatsappEventRetentionDays());
        assertEquals("ORGANISATION", dto.scope());
        assertEquals(14, dto.whatsappEventEffectiveDays());
    }

    @Test
    void updateForCurrentUser_shouldResetToDefault_whenZero() {
        User user = user(null);
        DataRetentionPolicy existing = new DataRetentionPolicy();
        existing.setOwner(user);
        existing.setWhatsappEventRetentionDays(14);
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(repository.findByOwner(user)).thenReturn(Optional.of(existing));

        DataRetentionPolicyDTO dto = service.updateForCurrentUser(new DataRetentionPolicyUpdateDTO(0));

        assertNull(existing.getWhatsappEventRetentionDays());
        assertEquals(30, dto.whatsappEventEffectiveDays());
    }

    @Test
    void updateForCurrentUser_shouldRejectAboveMaximum() {
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user(null)));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateForCurrentUser(new DataRetentionPolicyUpdateDTO(91)));
        verify(repository, never()).save(any());
    }

    private User user(Organisation organisation) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setOrganisation(organisation);
        return user;
    }
}
