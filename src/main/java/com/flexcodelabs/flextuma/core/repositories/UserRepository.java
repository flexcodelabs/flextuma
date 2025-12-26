package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @Query("SELECT u FROM User u WHERE u.email = :id OR u.phoneNumber = :id OR u.username = :id")
    Optional<User> findByIdentifier(@Param("id") String identifier);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);
}