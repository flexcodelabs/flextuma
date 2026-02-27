package com.flexcodelabs.flextuma.core.helpers;

import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A JPA Specification that mirrors the Node.js getWhere pattern:
 *
 * - SUPER_ADMIN / ALL authority → no restriction (sees everything)
 * - User with an organisation → sees resources created by anyone in the same
 * org OR by themselves
 * - User without an organisation → sees only resources they created
 *
 * Applies only to entities that extend Owner (i.e. have a "createdBy" field).
 * Entities that do NOT have createdBy (e.g. Organisation itself) are
 * unaffected.
 */
public class TenantAwareSpecification<T extends BaseEntity> implements Specification<T> {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String CREATED_BY = "createdBy";
    private static final String ORGANISATION = "organisation";
    private static final Set<String> BYPASS_AUTHORITIES = Set.of("ALL", "SUPER_ADMIN");

    private final transient User currentUser;
    private final transient Set<String> userAuthorities;

    public TenantAwareSpecification(User currentUser, Set<String> userAuthorities) {
        this.currentUser = currentUser;
        this.userAuthorities = userAuthorities;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {

        if (userAuthorities.stream().anyMatch(BYPASS_AUTHORITIES::contains)) {
            return cb.conjunction();
        }

        try {
            root.get(CREATED_BY);
        } catch (IllegalArgumentException e) {
            return cb.conjunction();
        }

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.get(CREATED_BY), currentUser));

        Organisation organisation = currentUser.getOrganisation();
        if (organisation != null) {
            Join<T, User> creatorJoin = root.join(CREATED_BY, JoinType.LEFT);
            predicates.add(cb.equal(creatorJoin.get(ORGANISATION), organisation));
        }

        return cb.or(predicates.toArray(new Predicate[0]));
    }
}
