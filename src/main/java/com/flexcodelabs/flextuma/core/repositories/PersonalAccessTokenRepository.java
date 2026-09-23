package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.auth.PersonalAccessToken;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalAccessTokenRepository extends BaseRepository<PersonalAccessToken, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<PersonalAccessToken> {

    // Mirrors UserRepository#findByUsername's eager fetch: PatAuthenticationFilter walks
    // pat.getUser().getRoles()...getPrivileges() outside any transaction, so roles/privileges
    // must be loaded here, before this call's own short-lived session closes
    // (spring.jpa.open-in-view=false) -- otherwise that walk throws
    // "could not initialize proxy - no session".
    @Query("SELECT p FROM PersonalAccessToken p LEFT JOIN FETCH p.user u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.roles.privileges WHERE p.token = :token")
    Optional<PersonalAccessToken> findByToken(@Param("token") String token);
}
