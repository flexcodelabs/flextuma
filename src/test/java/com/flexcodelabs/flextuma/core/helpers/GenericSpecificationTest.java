package com.flexcodelabs.flextuma.core.helpers;

import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenericSpecificationTest {

    @Mock
    private Root<TestEntity> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path<Object> path;

    @Mock
    private Path<String> stringPath;

    @Mock
    private Predicate predicate;

    @Mock
    private CriteriaBuilder.In<Object> inClause;

    static class TestEntity extends BaseEntity {
        // dummy entity
    }

    enum TestEnum {
        ONE, TWO
    }

    @BeforeEach
    void setUp() {
        // Set up the path mock for standard operations. Note that "javaType" mocking is
        // important!
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void mockPathForType(Class<?> type) {
        when(root.get(anyString())).thenReturn((Path) path);
        lenient().when(path.getJavaType()).thenReturn((Class) type);
    }

    @Test
    void testEqOperatorString() {
        mockPathForType(String.class);
        when(cb.equal(path, "value")).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("name:EQ:value");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, "value");
    }

    @Test
    void testEqOperatorUUID() {
        mockPathForType(UUID.class);
        UUID id = UUID.randomUUID();
        when(cb.equal(path, id)).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("id:EQ:" + id);
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, id);
    }

    @Test
    void testEqOperatorBoolean() {
        mockPathForType(Boolean.class);
        when(cb.equal(path, true)).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("active:EQ:true");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, true);
    }

    @Test
    void testEqOperatorEnum() {
        mockPathForType(TestEnum.class);
        when(cb.equal(path, TestEnum.ONE)).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("status:EQ:ONE");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, TestEnum.ONE);
    }

    @Test
    void testNeOperator() {
        mockPathForType(String.class);
        when(cb.notEqual(path, "value")).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("name:NE:value");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).notEqual(path, "value");
    }

    @Test
    void testLikeOperator() {
        mockPathForType(String.class);
        when(path.as(String.class)).thenReturn(stringPath);
        when(cb.lower(any())).thenReturn(stringPath);
        when(cb.like(any(), anyString())).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("name:LIKE:value");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).like(any(), eq("%value%"));
    }

    @Test
    void testILikeOperator() {
        mockPathForType(String.class);
        when(path.as(String.class)).thenReturn(stringPath);
        when(cb.lower(any())).thenReturn(stringPath);
        when(cb.like(any(), anyString())).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("name:ILIKE:VaLuE");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).like(any(), eq("%value%"));
    }

    @Test
    void testInOperator() {
        mockPathForType(String.class);
        when(path.in(anyList())).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("status:IN:A,B,C");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(path).in(anyList());
    }

    @Test
    void testGtOperator() {
        mockPathForType(Integer.class);
        when(path.as(String.class)).thenReturn(stringPath);
        when(cb.greaterThan(any(), eq("10"))).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("age:GT:10");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).greaterThan(any(), eq("10"));
    }

    @Test
    void testLtOperator() {
        mockPathForType(Integer.class);
        when(path.as(String.class)).thenReturn(stringPath);
        when(cb.lessThan(any(), eq("10"))).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("age:LT:10");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).lessThan(any(), eq("10"));
    }

    @Test
    void testNullOperator() {
        mockPathForType(String.class);
        lenient().when(cb.equal(any(Expression.class), eq((Object) null))).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("name:EQ:null");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, (Object) null);
    }

    @Test
    void testInvalidEnumShouldReturnNullForValue() {
        mockPathForType(TestEnum.class);
        lenient().when(cb.equal(any(Expression.class), eq((Object) null))).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("status:EQ:INVALID_ENUM");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        // an invalid enum returns null so it calls cb.equal(path, null)
        verify(cb).equal(path, (Object) null);
    }

    @Test
    void testMissingValue() {
        mockPathForType(String.class);
        when(cb.equal(path, "")).thenReturn(predicate);

        // String splitting "field:EQ" shouldn't throw but defaults value to ""
        GenericSpecification<TestEntity> spec = new GenericSpecification<>("name:EQ");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, "");
    }
}
