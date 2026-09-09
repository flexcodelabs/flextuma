package com.flexcodelabs.flextuma.core.entities.base;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseEntityTest {

    private static class TestEntity extends BaseEntity {
    }

    @Test
    void isNew_shouldBeTrue_whenIdIsNull() {
        TestEntity entity = new TestEntity();

        assertTrue(entity.isNew());
    }

    @Test
    void isNew_shouldBeFalse_onceIdIsAssigned() {
        TestEntity entity = new TestEntity();
        entity.setId(UUID.randomUUID());

        assertFalse(entity.isNew());
    }
}
