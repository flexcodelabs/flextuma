package com.flexcodelabs.flextuma.core.services;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PersistenceContext;

@Component
public class EntityAssociationReferenceResolver {

    @PersistenceContext
    private EntityManager entityManager;

    public <T> T resolve(T entity) {
        if (entity == null) {
            return null;
        }

        resolveEntity(entity, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        return entity;
    }

    private void resolveEntity(Object entity, Set<Object> visited) {
        if (entity == null || !visited.add(entity)) {
            return;
        }

        for (Field field : getAllFields(entity.getClass())) {
            if (!isAssociationField(field)) {
                continue;
            }

            field.setAccessible(true);

            try {
                Object value = field.get(entity);
                if (value == null) {
                    continue;
                }

                if (value instanceof Collection<?> collection) {
                    field.set(entity, resolveCollection(collection, visited));
                    continue;
                }

                field.set(entity, resolveAssociationValue(value, visited));
            } catch (IllegalAccessException ignored) {
                // Ignore inaccessible association fields and continue resolving the rest.
            }
        }
    }

    private Object resolveAssociationValue(Object value, Set<Object> visited) {
        if (value instanceof BaseEntity baseEntity && baseEntity.getId() != null) {
            @SuppressWarnings("unchecked")
            Class<BaseEntity> entityClass = (Class<BaseEntity>) Hibernate.getClass(value);
            return entityManager.getReference(entityClass, baseEntity.getId());
        }

        resolveEntity(value, visited);
        return value;
    }

    private Collection<?> resolveCollection(Collection<?> collection, Set<Object> visited) {
        Collection<Object> resolved = collection instanceof Set<?> ? new LinkedHashSet<>() : new ArrayList<>();

        for (Object item : collection) {
            resolved.add(resolveAssociationValue(item, visited));
        }

        return resolved;
    }

    private boolean isAssociationField(Field field) {
        return field.isAnnotationPresent(ManyToOne.class)
                || field.isAnnotationPresent(OneToOne.class)
                || field.isAnnotationPresent(OneToMany.class)
                || field.isAnnotationPresent(ManyToMany.class);
    }

    private java.util.List<Field> getAllFields(Class<?> type) {
        java.util.List<Field> fields = new ArrayList<>();
        Class<?> current = type;

        while (current != null && current != Object.class) {
            java.util.Collections.addAll(fields, current.getDeclaredFields());
            current = current.getSuperclass();
        }

        return fields;
    }
}
