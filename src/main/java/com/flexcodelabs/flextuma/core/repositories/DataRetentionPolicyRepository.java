package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.settings.DataRetentionPolicy;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataRetentionPolicyRepository extends BaseRepository<DataRetentionPolicy, UUID> {

    Optional<DataRetentionPolicy> findByOrganisation(Organisation organisation);

    Optional<DataRetentionPolicy> findByOwner(User owner);
}
