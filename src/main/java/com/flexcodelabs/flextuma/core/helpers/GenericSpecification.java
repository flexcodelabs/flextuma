package com.flexcodelabs.flextuma.core.helpers;

import com.flexcodelabs.flextuma.core.entities.BaseEntity;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;

public class GenericSpecification<T extends BaseEntity> implements Specification<T> {

    private final String field;
    private final FilterOperator operator;
    private final String value;

    public GenericSpecification(String filterStr) {
        String[] parts = filterStr.split(":", 3);
        this.field = parts[0];
        this.operator = FilterOperator.fromValue(parts[1]);
        this.value = (parts.length > 2) ? parts[2] : "";
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return switch (operator) {
            case EQ -> cb.equal(root.get(field), castValue(root, field, value));
            case NE -> cb.notEqual(root.get(field), castValue(root, field, value));
            case LIKE -> cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%");
            case GT -> cb.greaterThan(root.get(field), value);
            case LT -> cb.lessThan(root.get(field), value);
            case IN -> root.get(field).in(Arrays.asList(value.split(",")));
        };
    }

    private Object castValue(Root<T> root, String field, String value) {
        Class<?> type = root.get(field).getJavaType();
        if (type.equals(Boolean.class) || type.equals(boolean.class))
            return Boolean.valueOf(value);
        if (type.equals(Integer.class) || type.equals(int.class))
            return Integer.valueOf(value);
        return value;
    }
}