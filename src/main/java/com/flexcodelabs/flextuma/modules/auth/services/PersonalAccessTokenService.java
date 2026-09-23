package com.flexcodelabs.flextuma.modules.auth.services;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.auth.PersonalAccessToken;
import com.flexcodelabs.flextuma.core.repositories.PersonalAccessTokenRepository;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;
import com.flexcodelabs.flextuma.core.security.SecurityUtils;
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
    protected String getTableName() {
        return "personalaccesstoken";
    }

    @Override
    protected void onPreSave(PersonalAccessToken entity) {
        // Tokens are strictly personal: always force ownership to the caller and
        // ignore any client-supplied user/token/active, regardless of what the
        // request body contains -- otherwise a caller could mint a token hashed
        // from a secret they already know, pointed at someone else's account.
        entity.setUser(null);
        entity.setToken(null);
        entity.setActive(null);
        entity.setRawToken(null);

        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername != null) {
            userRepository.findByUsername(currentUsername).ifPresent(entity::setUser);
        }
    }

    @Override
    protected PersonalAccessToken onPreUpdate(PersonalAccessToken newEntity, PersonalAccessToken oldEntity) {
        // Only name/expiresAt are editable via PUT -- clearing the rest here makes
        // BaseService#update's getNullPropertyNames() skip them, so a client can't
        // reassign ownership, reactivate a revoked token, or rewrite its hash.
        newEntity.setUser(null);
        newEntity.setToken(null);
        newEntity.setActive(null);
        newEntity.setScopes(null);
        newEntity.setAllowedConnectorIds(null);
        newEntity.setAllowSystemConnectors(null);
        newEntity.setRawToken(null);
        return super.onPreUpdate(newEntity, oldEntity);
    }

    @Override
    protected Specification<PersonalAccessToken> buildTenantSpec() {
        // Unlike org-shared resources (contacts, tags, ...), a personal access
        // token must never be visible to anyone but its owner -- so this can't
        // reuse TenantAwareSpecification's org-wide sharing.
        if (SecurityUtils.getCurrentUserAuthorities().contains("SUPER_ADMIN")) {
            return (root, query, cb) -> cb.conjunction();
        }
        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("user").get("username"), currentUsername);
    }

}
