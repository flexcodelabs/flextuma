package com.flexcodelabs.flextuma.core.helpers;

import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "rawtypes", "unchecked" })
class DynamicFetchSpecificationTest {

    @Mock
    private Root root;

    @Mock
    private CriteriaQuery query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private EntityType rootType;

    @Mock
    private SingularAttribute singularAttribute;

    @Mock
    private PluralAttribute pluralAttribute;

    @Mock
    private ManagedType targetType;

    @BeforeEach
    void setUp() {
        lenient().when(root.getModel()).thenReturn(rootType);
    }

    @Test
    void testConstructorWithNullFieldPaths() {
        DynamicFetchSpecification spec = new DynamicFetchSpecification(null);
        assertNotNull(spec);
    }

    @Test
    void testToPredicateForCountQuery() {
        Set<String> fieldPaths = Collections.singleton("relation");
        DynamicFetchSpecification spec = new DynamicFetchSpecification(fieldPaths);

        Class<?>[] countTypes = { Long.class, long.class, Integer.class, int.class };
        for (Class<?> type : countTypes) {
            doReturn(type).when(query).getResultType();
            lenient().when(cb.conjunction()).thenReturn(mock(Predicate.class));
            assertNotNull(spec.toPredicate(root, query, cb));
            verify(root, never()).fetch(anyString(), any(JoinType.class));
        }
    }

    @Test
    void testToPredicateWithValidFetch() {
        Set<String> fieldPaths = Collections.singleton("relation");
        DynamicFetchSpecification spec = new DynamicFetchSpecification(fieldPaths);

        doReturn(Object.class).when(query).getResultType();
        when(rootType.getAttribute("relation")).thenReturn(singularAttribute);
        when(singularAttribute.isAssociation()).thenReturn(true);
        when(singularAttribute.getType()).thenReturn(targetType);

        when(root.getFetches()).thenReturn(Collections.emptySet());
        when(root.fetch("relation", JoinType.LEFT)).thenReturn(mock(Fetch.class));
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        verify(query).distinct(true);
        verify(root).fetch("relation", JoinType.LEFT);
    }

    @Test
    void testApplyFetchNonExistentAttribute() {
        Set<String> fieldPaths = Collections.singleton("nonExistent");
        DynamicFetchSpecification spec = new DynamicFetchSpecification(fieldPaths);

        doReturn(Object.class).when(query).getResultType();
        when(rootType.getAttribute("nonExistent")).thenThrow(new IllegalArgumentException());
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        verify(root, never()).fetch(anyString(), any(JoinType.class));
    }

    @Test
    void testApplyFetchNonAssociationAttribute() {
        Set<String> fieldPaths = Collections.singleton("simpleField");
        DynamicFetchSpecification spec = new DynamicFetchSpecification(fieldPaths);

        doReturn(Object.class).when(query).getResultType();
        when(rootType.getAttribute("simpleField")).thenReturn(singularAttribute);
        when(singularAttribute.isAssociation()).thenReturn(false);
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        verify(root, never()).fetch(anyString(), any(JoinType.class));
    }

    @Test
    void testApplyFetchPluralAttribute() {
        Set<String> fieldPaths = Collections.singleton("collection");
        DynamicFetchSpecification spec = new DynamicFetchSpecification(fieldPaths);

        doReturn(Object.class).when(query).getResultType();
        when(rootType.getAttribute("collection")).thenReturn(pluralAttribute);
        when(pluralAttribute.isAssociation()).thenReturn(true);
        when(pluralAttribute.getElementType()).thenReturn(targetType);

        when(root.getFetches()).thenReturn(Collections.emptySet());
        when(root.fetch("collection", JoinType.LEFT)).thenReturn(mock(Fetch.class));
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        verify(root).fetch("collection", JoinType.LEFT);
    }

    @Test
    void testApplyFetchNestedPath() {
        Set<String> fieldPaths = Collections.singleton("relation.subRelation");
        DynamicFetchSpecification spec = new DynamicFetchSpecification(fieldPaths);

        doReturn(Object.class).when(query).getResultType();

        // relation
        when(rootType.getAttribute("relation")).thenReturn(singularAttribute);
        when(singularAttribute.isAssociation()).thenReturn(true);
        when(singularAttribute.getType()).thenReturn(targetType);

        Fetch firstFetch = mock(Fetch.class);
        when(root.getFetches()).thenReturn(Collections.emptySet());
        when(root.fetch("relation", JoinType.LEFT)).thenReturn(firstFetch);

        // subRelation
        ManagedType subTargetType = mock(ManagedType.class);
        SingularAttribute subAttr = mock(SingularAttribute.class);
        when(targetType.getAttribute("subRelation")).thenReturn(subAttr);
        when(subAttr.isAssociation()).thenReturn(true);
        when(subAttr.getType()).thenReturn(subTargetType);

        when(firstFetch.getFetches()).thenReturn(Collections.emptySet());
        when(firstFetch.fetch("subRelation", JoinType.LEFT)).thenReturn(mock(Fetch.class));

        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        verify(root).fetch("relation", JoinType.LEFT);
        verify(firstFetch).fetch("subRelation", JoinType.LEFT);
    }

    @Test
    void testSafeFetchReuseExisting() {
        Set<String> fieldPaths = Collections.singleton("relation");
        DynamicFetchSpecification spec = new DynamicFetchSpecification(fieldPaths);

        doReturn(Object.class).when(query).getResultType();
        when(rootType.getAttribute("relation")).thenReturn(singularAttribute);
        when(singularAttribute.isAssociation()).thenReturn(true);
        when(singularAttribute.getType()).thenReturn(targetType);

        Fetch existingFetch = mock(Fetch.class);
        Attribute existingAttr = mock(Attribute.class);
        lenient().when(existingAttr.getName()).thenReturn("relation");
        lenient().when(existingFetch.getAttribute()).thenReturn(existingAttr);

        when(root.getFetches()).thenReturn(Collections.singleton(existingFetch));
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        verify(root, never()).fetch(anyString(), any(JoinType.class));
    }

    /**
     * Test case to cover SafeFetch loop when multiple existing fetches exist.
     */
    @Test
    void testSafeFetchMultipleExisting() {
        Set<String> fieldPaths = Collections.singleton("relation");
        DynamicFetchSpecification spec = new DynamicFetchSpecification(fieldPaths);

        doReturn(Object.class).when(query).getResultType();
        when(rootType.getAttribute("relation")).thenReturn(singularAttribute);
        when(singularAttribute.isAssociation()).thenReturn(true);
        when(singularAttribute.getType()).thenReturn(targetType);

        // First existing fetch (mismatch)
        Fetch mismatchFetch = mock(Fetch.class);
        Attribute mismatchAttr = mock(Attribute.class);
        when(mismatchAttr.getName()).thenReturn("other");
        when(mismatchFetch.getAttribute()).thenReturn(mismatchAttr);

        // Second existing fetch (match)
        Fetch matchFetch = mock(Fetch.class);
        Attribute matchAttr = mock(Attribute.class);
        when(matchAttr.getName()).thenReturn("relation");
        when(matchFetch.getAttribute()).thenReturn(matchAttr);

        Set<Fetch> fetches = new LinkedHashSet<>();
        fetches.add(mismatchFetch);
        fetches.add(matchFetch);

        when(root.getFetches()).thenReturn(fetches);
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        verify(root, never()).fetch(anyString(), any(JoinType.class));
    }

    @Test
    void testGetTargetTypeReturnsNull() {
        Set<String> fieldPaths = Collections.singleton("relation");
        DynamicFetchSpecification spec = new DynamicFetchSpecification(fieldPaths);

        doReturn(Object.class).when(query).getResultType();
        when(rootType.getAttribute("relation")).thenReturn(singularAttribute);
        when(singularAttribute.isAssociation()).thenReturn(true);

        // SingularAttribute returns null Type (not instance of ManagedType)
        Type nonManagedType = mock(Type.class);
        when(singularAttribute.getType()).thenReturn(nonManagedType);

        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        // Should break after first part if targetType is null
        verify(root).fetch("relation", JoinType.LEFT);
    }

    @Test
    void testGetTargetTypePluralReturnsNull() {
        Set<String> fieldPaths = Collections.singleton("collection");
        DynamicFetchSpecification spec = new DynamicFetchSpecification(fieldPaths);

        doReturn(Object.class).when(query).getResultType();
        when(rootType.getAttribute("collection")).thenReturn(pluralAttribute);
        when(pluralAttribute.isAssociation()).thenReturn(true);

        // PluralAttribute returns null element Type
        Type nonManagedType = mock(Type.class);
        when(pluralAttribute.getElementType()).thenReturn(nonManagedType);

        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        verify(root).fetch("collection", JoinType.LEFT);
    }

    /**
     * Test case to cover getTargetType with an attribute that is neither Singular
     * nor Plural.
     */
    @Test
    void testGetTargetTypeNeitherSingularNorPlural() {
        Set<String> fieldPaths = Collections.singleton("other");
        DynamicFetchSpecification spec = new DynamicFetchSpecification(fieldPaths);

        doReturn(Object.class).when(query).getResultType();
        Attribute genericAttr = mock(Attribute.class);
        when(rootType.getAttribute("other")).thenReturn(genericAttr);
        when(genericAttr.isAssociation()).thenReturn(true);

        when(root.getFetches()).thenReturn(Collections.emptySet());
        when(root.fetch("other", JoinType.LEFT)).thenReturn(mock(Fetch.class));
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        verify(root).fetch("other", JoinType.LEFT);
    }
}
