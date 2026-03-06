package com.flexcodelabs.flextuma.core.events;

import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EntityEvent<T extends BaseEntity> extends ApplicationEvent {

    private final transient T entity;
    private final EntityEventType type;

    public EntityEvent(Object source, T entity, EntityEventType type) {
        super(source);
        this.entity = entity;
        this.type = type;
    }

    public enum EntityEventType {
        CREATED, UPDATED, DELETED
    }
}
