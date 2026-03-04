package com.flexcodelabs.flextuma.modules.auth.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.flexcodelabs.flextuma.core.entities.auth.PersonalAccessToken;
import com.flexcodelabs.flextuma.core.repositories.PersonalAccessTokenRepository;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

@Service
public class PersonalAccessTokenService extends BaseService<PersonalAccessToken> {

    private final PersonalAccessTokenRepository repository;
    private final UserRepository userRepository;

    public PersonalAccessTokenService(PersonalAccessTokenRepository repository, UserRepository userRepository) {
        super();
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Override
    protected JpaRepository<PersonalAccessToken, UUID> getRepository() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return "ALL";
    }

    @Override
    protected String getAddPermission() {
        return "ALL";
    }

    @Override
    protected String getUpdatePermission() {
        return "ALL";
    }

    @Override
    protected String getDeletePermission() {
        return "ALL";
    }

    @Override
    public String getEntityPlural() {
        return PersonalAccessToken.NAME_PLURAL;
    }

    @Override
    public String getPropertyName() {
        return PersonalAccessToken.PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return PersonalAccessToken.NAME_SINGULAR;
    }

    @Override
    protected JpaSpecificationExecutor<PersonalAccessToken> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected void onPreSave(PersonalAccessToken entity) {
        String rawToken = "ft_" + UUID.randomUUID().toString().replace("-", "");
        entity.setRawToken(rawToken);
        entity.setToken(hashToken(rawToken));

        if (entity.getUser() == null) {
            String currentUsername = com.flexcodelabs.flextuma.core.security.SecurityUtils.getCurrentUsername();
            if (currentUsername != null) {
                userRepository.findByUsername(currentUsername).ifPresent(entity::setUser);
            }
        }

        if (entity.getActive() == null) {
            entity.setActive(true);
        }
    }

    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SHA-256 algorithm not found", e);
        }
    }
}
