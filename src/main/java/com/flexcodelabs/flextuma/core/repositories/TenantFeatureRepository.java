package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.core.entities.feature.TenantFeature;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantFeatureRepository extends JpaRepository<TenantFeature, UUID>,
        JpaSpecificationExecutor<TenantFeature> {

    Optional<TenantFeature> findByOrganisationAndFeatureKey(Organisation organisation, String featureKey);

    List<TenantFeature> findAllByOrganisation(Organisation organisation);
}
