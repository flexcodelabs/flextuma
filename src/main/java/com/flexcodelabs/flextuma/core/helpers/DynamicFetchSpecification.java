package com.flexcodelabs.flextuma.core.helpers;

import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;

/**
 * A Specification that dynamically adds fetch joins based on requested field
 * paths.
 * This helps prevent N+1 issues when specific relations are requested via the
 * "fields" parameter.
 */
public class DynamicFetchSpecification<T> implements Specification<T> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Set<String> fieldPaths;

    public DynamicFetchSpecification(Set<String> fieldPaths) {
        this.fieldPaths = fieldPaths != null ? fieldPaths : new HashSet<>();
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        // Fetch joins are not allowed in count queries
        Class<?> resultType = query.getResultType();
        if (resultType == Long.class || resultType == long.class || resultType == Integer.class
                || resultType == int.class) {
            return cb.conjunction();
        }

        // Apply fetch joins for requested paths
        for (String path : fieldPaths) {
            applyFetch(root, path);
        }

        // Use distinct to avoid duplicates when fetching collections
        query.distinct(true);

        return cb.conjunction();
    }

    private void applyFetch(Root<T> root, String path) {
        String[] parts = path.split("\\.");
        FetchParent<?, ?> current = root;
        ManagedType<?> currentType = root.getModel();

        for (String part : parts) {
            Attribute<?, ?> attribute = getAttribute(currentType, part);
            if (attribute != null && attribute.isAssociation()) {
                current = SafeFetch.fetch(current, part);
                currentType = getTargetType(attribute);
            } else {
                currentType = null;
            }

            if (currentType == null) {
                break;
            }
        }
    }

    private Attribute<?, ?> getAttribute(ManagedType<?> type, String attributeName) {
        try {
            return type.getAttribute(attributeName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ManagedType<?> getTargetType(Attribute<?, ?> attribute) {
        if (attribute instanceof jakarta.persistence.metamodel.PluralAttribute) {
            jakarta.persistence.metamodel.PluralAttribute<?, ?, ?> plural = (jakarta.persistence.metamodel.PluralAttribute<?, ?, ?>) attribute;
            if (plural.getElementType() instanceof ManagedType) {
                return (ManagedType<?>) plural.getElementType();
            }
        } else if (attribute instanceof jakarta.persistence.metamodel.SingularAttribute) {
            jakarta.persistence.metamodel.SingularAttribute<?, ?> singular = (jakarta.persistence.metamodel.SingularAttribute<?, ?>) attribute;
            if (singular.getType() instanceof ManagedType) {
                return (ManagedType<?>) singular.getType();
            }
        }
        return null;
    }

    /**
     * Utility to prevent multiple fetches of the same attribute on the same parent.
     */
    private static class SafeFetch {
        @SuppressWarnings("unchecked")
        static <X, Y> Fetch<X, Y> fetch(FetchParent<X, Y> parent, String attributeName) {
            for (Fetch<Y, ?> existingFetch : parent.getFetches()) {
                if (existingFetch.getAttribute().getName().equals(attributeName)) {
                    return (Fetch<X, Y>) existingFetch;
                }
            }
            return parent.fetch(attributeName, JoinType.LEFT);
        }
    }
}
