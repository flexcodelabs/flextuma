package com.flexcodelabs.flextuma.core.services;

import java.util.Collection;

import org.hibernate.Hibernate;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;

@Component
public class EntityResponseInitializer {

    @PersistenceContext
    private EntityManager entityManager;

    public void initialize(Object entity) {
        initialize(entity, 1);
    }

    public void initialize(Object entity, int depth) {
        if (entity == null || depth < 0) {
            return;
        }

        Hibernate.initialize(entity);
        ManagedType<?> managedType = resolveManagedType(Hibernate.getClass(entity));
        if (managedType == null) {
            return;
        }

        BeanWrapper wrapper = new BeanWrapperImpl(entity);
        for (Attribute<?, ?> attribute : managedType.getAttributes()) {
            processAttribute(wrapper, attribute, depth);
        }
    }

    private void processAttribute(BeanWrapper wrapper, Attribute<?, ?> attribute, int depth) {
        if (!shouldProcessAttribute(wrapper, attribute)) {
            return;
        }

        Object value = wrapper.getPropertyValue(attribute.getName());
        if (value == null) {
            return;
        }

        Hibernate.initialize(value);
        if (depth > 0) {
            processAttributeValue(value, depth);
        }
    }

    private boolean shouldProcessAttribute(BeanWrapper wrapper, Attribute<?, ?> attribute) {
        return attribute.isAssociation() && wrapper.isReadableProperty(attribute.getName());
    }

    private void processAttributeValue(Object value, int depth) {
        if (value instanceof Collection<?> collection) {
            processCollection(collection, depth);
        } else {
            initializeSingularAssociations(value, depth - 1);
        }
    }

    private void processCollection(Collection<?> collection, int depth) {
        for (Object item : collection) {
            initializeSingularAssociations(item, depth - 1);
        }
    }

    private void initializeSingularAssociations(Object entity, int depth) {
        if (entity == null || depth < 0) {
            return;
        }

        Hibernate.initialize(entity);
        ManagedType<?> managedType = resolveManagedType(Hibernate.getClass(entity));
        if (managedType == null) {
            return;
        }

        BeanWrapper wrapper = new BeanWrapperImpl(entity);
        for (Attribute<?, ?> attribute : managedType.getAttributes()) {
            if (!attribute.isAssociation() || attribute.isCollection()
                    || !wrapper.isReadableProperty(attribute.getName())) {
                continue;
            }

            Object value = wrapper.getPropertyValue(attribute.getName());
            if (value != null) {
                Hibernate.initialize(value);
                if (depth > 0) {
                    initializeSingularAssociations(value, depth - 1);
                }
            }
        }
    }

    private ManagedType<?> resolveManagedType(Class<?> javaType) {
        try {
            if (entityManager == null || entityManager.getMetamodel() == null) {
                return null;
            }
            return entityManager.getMetamodel().managedType(javaType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
