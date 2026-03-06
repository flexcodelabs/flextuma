package com.flexcodelabs.flextuma.core.helpers;

import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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

    // --- Parameterized EQ tests for different types ---

    static Stream<Arguments> eqOperatorProvider() {
        UUID testUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return Stream.of(
                Arguments.of("name:EQ:value", String.class, "value"),
                Arguments.of("id:EQ:" + testUuid, UUID.class, testUuid),
                Arguments.of("active:EQ:true", Boolean.class, true),
                Arguments.of("status:EQ:ONE", TestEnum.class, TestEnum.ONE));
    }

    @ParameterizedTest
    @MethodSource("eqOperatorProvider")
    void testEqOperator(String filterString, Class<?> type, Object expectedValue) {
        mockPathForType(type);
        when(cb.equal(path, expectedValue)).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>(filterString);
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, expectedValue);
    }

    // --- Parameterized LIKE-family tests ---

    static Stream<Arguments> likeOperatorProvider() {
        return Stream.of(
                Arguments.of("name:LIKE:value", "%value%"),
                Arguments.of("name:ILIKE:VaLuE", "%value%"),
                Arguments.of("name:startsWith:value", "value%"),
                Arguments.of("name:endsWith:value", "%value"));
    }

    @ParameterizedTest
    @MethodSource("likeOperatorProvider")
    void testLikeOperators(String filterString, String expectedPattern) {
        mockPathForType(String.class);
        when(path.as(String.class)).thenReturn(stringPath);
        when(cb.lower(any())).thenReturn(stringPath);
        when(cb.like(any(), anyString())).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>(filterString);
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).like(any(), eq(expectedPattern));
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

    @Test
    void testGteOperator() {
        mockPathForType(Integer.class);
        when(path.as(String.class)).thenReturn(stringPath);
        when(cb.greaterThanOrEqualTo(any(), eq("10"))).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("age:GTE:10");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).greaterThanOrEqualTo(any(), eq("10"));
    }

    @Test
    void testLteOperator() {
        mockPathForType(Integer.class);
        when(path.as(String.class)).thenReturn(stringPath);
        when(cb.lessThanOrEqualTo(any(), eq("10"))).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("age:LTE:10");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).lessThanOrEqualTo(any(), eq("10"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testIsTrueOperator() {
        mockPathForType(Boolean.class);
        Path<Boolean> booleanPath = mock(Path.class);
        when(path.as(Boolean.class)).thenReturn(booleanPath);
        when(cb.isTrue(booleanPath)).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("active:isTrue:ignored");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).isTrue(booleanPath);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testIsFalseOperator() {
        mockPathForType(Boolean.class);
        Path<Boolean> booleanPath = mock(Path.class);
        when(path.as(Boolean.class)).thenReturn(booleanPath);
        when(cb.isFalse(booleanPath)).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("active:isFalse:ignored");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).isFalse(booleanPath);
    }

    @Test
    void testBtnOperator() {
        mockPathForType(Integer.class);
        when(path.as(String.class)).thenReturn(stringPath);
        when(cb.between(stringPath, "10", "20")).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("age:BTN:10,20");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).between(stringPath, "10", "20");
    }

    @Test
    void testBtnOperatorInvalidRange() {
        mockPathForType(Integer.class);
        when(cb.conjunction()).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("age:BTN:10");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).conjunction();
    }

    @Test
    void testCastValueInteger() {
        mockPathForType(Integer.class);
        when(cb.equal(path, 10)).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("age:EQ:10");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, 10);
    }

    @Test
    void testToPredicateExceptionReturnsConjunction() {
        when(root.get(anyString())).thenThrow(new RuntimeException("Test exception"));
        when(cb.conjunction()).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("field:EQ:value");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).conjunction();
    }

    @Test
    void testCastValueBooleanPrimitive() {
        mockPathForType(boolean.class);
        when(cb.equal(path, true)).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("active:eq:true");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, true);
    }

    @Test
    void testCastValueIntPrimitive() {
        mockPathForType(int.class);
        when(cb.equal(path, 10)).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("age:eq:10");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, 10);
    }

    @Test
    void testCastValueNullString() {
        mockPathForType(String.class);
        when(cb.equal(path, (Object) null)).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("name:eq:null");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, (Object) null);
    }

    @Test
    void testCastValueEmptyValue() {
        mockPathForType(String.class);
        when(cb.equal(path, "")).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("name:eq:");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, "");
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void testNestedPathResolution() {
        Path<Object> nestedPath = mock(Path.class);
        when(root.get("parent")).thenReturn((Path) nestedPath);
        when(nestedPath.get("child")).thenReturn((Path) path);
        lenient().when(path.getJavaType()).thenReturn((Class) String.class);
        when(cb.equal(path, "value")).thenReturn(predicate);

        GenericSpecification<TestEntity> spec = new GenericSpecification<>("parent.child:EQ:value");
        Predicate p = spec.toPredicate(root, query, cb);

        assertNotNull(p);
        verify(cb).equal(path, "value");
    }
}
