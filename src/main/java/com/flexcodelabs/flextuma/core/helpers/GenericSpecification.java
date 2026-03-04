package com.flexcodelabs.flextuma.core.helpers;

import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.UUID;

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
        try {
            Path<?> path = resolvePath(root, field);
            Class<?> type = path.getJavaType();

            return switch (operator) {
                case EQ -> cb.equal(path, castValue(type, value));
                case NE -> cb.notEqual(path, castValue(type, value));
                case LIKE -> cb.like(cb.lower(path.as(String.class)), "%" + value.toLowerCase() + "%");
                case ILIKE -> cb.like(cb.lower(path.as(String.class)), "%" + value.toLowerCase() + "%");
                case STARTS_WITH -> cb.like(cb.lower(path.as(String.class)), value.toLowerCase() + "%");
                case ENDS_WITH -> cb.like(cb.lower(path.as(String.class)), "%" + value.toLowerCase());
                case IN -> path.in(Arrays.stream(value.split(",")).map(v -> castValue(type, v)).toList());
                case GT -> cb.greaterThan(path.as(String.class), value);
                case LT -> cb.lessThan(path.as(String.class), value);
                case GTE -> cb.greaterThanOrEqualTo(path.as(String.class), value);
                case LTE -> cb.lessThanOrEqualTo(path.as(String.class), value);
                case IS_TRUE -> cb.isTrue(path.as(Boolean.class));
                case IS_FALSE -> cb.isFalse(path.as(Boolean.class));
                case BTN -> {
                    String[] range = value.split(",");
                    if (range.length == 2) {
                        yield cb.between(path.as(String.class), range[0], range[1]);
                    }
                    yield cb.conjunction();
                }
                default -> cb.conjunction();
            };
        } catch (Exception e) {
            return cb.conjunction();
        }
    }

    private Path<?> resolvePath(Root<T> root, String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        Path<?> path = root;
        for (String part : parts) {
            path = path.get(part);
        }
        return path;
    }

    private Object castValue(Class<?> type, String value) {
        if (value == null || "null".equalsIgnoreCase(value))
            return null;
        if (type.equals(UUID.class))
            return UUID.fromString(value);
        if (type.isEnum()) {
            return stringToEnum(type, value);
        }
        if (type.equals(Boolean.class) || type.equals(boolean.class))
            return Boolean.valueOf(value);
        if (type.equals(Integer.class) || type.equals(int.class))
            return Integer.valueOf(value);
        return value;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object stringToEnum(Class<?> type, String value) {
        try {
            return Enum.valueOf((Class<Enum>) type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}