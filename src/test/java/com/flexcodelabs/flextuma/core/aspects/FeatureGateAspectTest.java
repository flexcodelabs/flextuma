package com.flexcodelabs.flextuma.core.aspects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.flexcodelabs.flextuma.core.annotations.FeatureGate;
import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.feature.TenantFeature;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.TenantFeatureRepository;

@ExtendWith(MockitoExtension.class)
class FeatureGateAspectTest {

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private TenantFeatureRepository featureRepository;

    @InjectMocks
    private FeatureGateAspect aspect;

    @Mock
    private FeatureGate gate;

    private Organisation organisation;
    private User user;

    @BeforeEach
    void setUp() {
        organisation = new Organisation();
        user = new User();
        user.setOrganisation(organisation);
        when(gate.value()).thenReturn("BULK_CAMPAIGN");
    }

    @Test
    void checkFeature_shouldThrowForbidden_whenFeatureIsDisabled() {
        TenantFeature feature = new TenantFeature();
        feature.setEnabled(false);

        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(featureRepository.findByOrganisationAndFeatureKey(organisation, "BULK_CAMPAIGN"))
                .thenReturn(Optional.of(feature));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> aspect.checkFeature(gate));

        assertEquals(403, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("BULK_CAMPAIGN"));
    }

    @Test
    void checkFeature_shouldAllow_whenFeatureIsEnabled() {
        TenantFeature feature = new TenantFeature();
        feature.setEnabled(true);

        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(featureRepository.findByOrganisationAndFeatureKey(organisation, "BULK_CAMPAIGN"))
                .thenReturn(Optional.of(feature));

        assertDoesNotThrow(() -> aspect.checkFeature(gate));
    }

    @Test
    void checkFeature_shouldAllow_whenNoRecordExists() {
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(featureRepository.findByOrganisationAndFeatureKey(organisation, "BULK_CAMPAIGN"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> aspect.checkFeature(gate));
    }

    @Test
    void checkFeature_shouldBypass_whenUserHasNoOrganisation() {
        User systemUser = new User();
        systemUser.setOrganisation(null);

        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(systemUser));

        assertDoesNotThrow(() -> aspect.checkFeature(gate));
        verifyNoInteractions(featureRepository);
    }

    @Test
    void checkFeature_shouldBypass_whenNoAuthenticatedUser() {
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> aspect.checkFeature(gate));
        verifyNoInteractions(featureRepository);
    }
}
