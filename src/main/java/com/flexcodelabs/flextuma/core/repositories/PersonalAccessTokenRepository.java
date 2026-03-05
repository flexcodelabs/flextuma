package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.auth.PersonalAccessToken;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalAccessTokenRepository extends BaseRepository<PersonalAccessToken, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<PersonalAccessToken> {
    Optional<PersonalAccessToken> findByToken(String token);
}
