package com.flexcodelabs.flextuma.core.aspects;

import com.flexcodelabs.flextuma.core.annotations.FeatureGate;
import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.feature.TenantFeature;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.TenantFeatureRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class FeatureGateAspect {

    private final CurrentUserResolver currentUserResolver;
    private final TenantFeatureRepository featureRepository;

    @Before("@annotation(gate)")
    public void checkFeature(FeatureGate gate) {
        String featureKey = gate.value();

        Optional<User> userOpt = currentUserResolver.getCurrentUser();

        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();
        Organisation organisation = user.getOrganisation();

        if (organisation == null) {
            return;
        }

        Optional<TenantFeature> feature = featureRepository.findByOrganisationAndFeatureKey(organisation, featureKey);

        if (feature.isEmpty()) {
            return;
        }

        if (Boolean.FALSE.equals(feature.get().getEnabled())) {
            log.warn("Feature [{}] is disabled for organisation [{}]", featureKey, organisation.getId());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Feature [" + featureKey + "] is not enabled for your organisation");
        }
    }
}
