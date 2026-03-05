package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrganisationRepository
        extends JpaRepository<Organisation, UUID>, JpaSpecificationExecutor<Organisation> {
    java.util.Optional<Organisation> findByCode(String code);

    java.util.Optional<Organisation> findByActive(Boolean active);
}
