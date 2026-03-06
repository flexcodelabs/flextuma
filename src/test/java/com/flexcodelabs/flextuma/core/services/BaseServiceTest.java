package com.flexcodelabs.flextuma.core.services;

import com.flexcodelabs.flextuma.core.dtos.AggregateDTO;
import com.flexcodelabs.flextuma.core.dtos.EntityFieldDTO;
import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BaseServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private JpaRepository<TestEntity, UUID> repository;

    @Mock
    private JpaSpecificationExecutor<TestEntity> executor;

    @Mock
    private Metamodel metamodel;

    @Mock
    private EntityType<TestEntity> entityType;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private TestService service;

    static class TestEntity extends BaseEntity {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    class TestService extends BaseService<TestEntity> {
        @Override
        protected JpaRepository<TestEntity, UUID> getRepository() {
            return repository;
        }

        @Override
        protected String getReadPermission() {
            return "READ";
        }

        @Override
        protected String getAddPermission() {
            return "ADD";
        }

        @Override
        protected String getUpdatePermission() {
            return "UPDATE";
        }

        @Override
        protected String getDeletePermission() {
            return "DELETE";
        }

        @Override
        public String getEntityPlural() {
            return "tests";
        }

        @Override
        public String getPropertyName() {
            return "test";
        }

        @Override
        protected String getEntitySingular() {
            return "test";
        }

        @Override
        protected JpaSpecificationExecutor<TestEntity> getRepositoryAsExecutor() {
            return executor;
        }
    }

    @BeforeEach
    void setUp() {
        service = new TestService();
        service.entityManager = entityManager;
        service.setEventPublisher(eventPublisher);
        service.setCurrentUserResolver(currentUserResolver);

        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserAuthorities)
                .thenReturn(Set.of("READ", "ADD", "UPDATE", "DELETE"));
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    private void mockPermissions(Set<String> permissions) {
        securityUtilsMock.when(SecurityUtils::getCurrentUserAuthorities).thenReturn(permissions);
    }

    @Test
    void checkPermission_shouldThrowException_whenNoPermission() {
        securityUtilsMock.when(SecurityUtils::getCurrentUserAuthorities).thenReturn(Set.of("OTHER"));
        assertThrows(AccessDeniedException.class, () -> service.findAll());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getEntityFields_shouldReturnFieldDTOs() {
        when(entityManager.getMetamodel()).thenReturn(metamodel);
        when(metamodel.entity(TestEntity.class)).thenReturn(entityType);

        SingularAttribute<TestEntity, String> attr = mock(SingularAttribute.class);
        when(attr.getName()).thenReturn("name");
        when(attr.getJavaType()).thenReturn(String.class);
        when(attr.getPersistentAttributeType()).thenReturn(Attribute.PersistentAttributeType.BASIC);
        when(attr.isOptional()).thenReturn(true);

        when(entityType.getAttributes()).thenReturn(Set.of(attr));

        List<EntityFieldDTO> fields = service.getEntityFields();

        assertNotNull(fields);
        assertEquals(1, fields.size());
        assertEquals("name", fields.get(0).getName());
        assertFalse(fields.get(0).isMandatory());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_shouldReturnAllEntities() {
        TestEntity entity = new TestEntity();
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.empty());
        when(executor.findAll(any(Specification.class))).thenReturn(List.of(entity));

        List<TestEntity> result = service.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(executor).findAll(any(Specification.class));
    }

    @Test
    void findAllPaginated_shouldReturnPagination() {
        TestEntity entity = new TestEntity();
        Pageable pageable = PageRequest.of(0, 10);
        Page<TestEntity> page = new PageImpl<>(List.of(entity), pageable, 1);

        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.empty());
        when(executor.findAll(Mockito.<Specification<TestEntity>>any(), eq(pageable))).thenReturn(page);

        Pagination<TestEntity> result = service.findAllPaginated(pageable, List.of("name:eq:value"), "name");

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getData().size());
    }

    @Test
    void save_shouldSaveEntityAndPublishEvent() {
        TestEntity entity = new TestEntity();
        when(repository.save(entity)).thenReturn(entity);

        TestEntity saved = service.save(entity);

        assertNotNull(saved);
        verify(repository).save(entity);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void update_shouldUpdateExistingEntity() {
        UUID id = UUID.randomUUID();
        TestEntity existing = new TestEntity();
        TestEntity updatePayload = new TestEntity();
        updatePayload.setName("new name");

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        TestEntity result = service.update(id, updatePayload);

        assertNotNull(result);
        assertEquals("new name", existing.getName());
        verify(repository).save(existing);
    }

    @Test
    void findById_shouldReturnEmpty_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        Optional<TestEntity> result = service.findById(id);

        assertTrue(result.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void findById_shouldReturnWithFields_whenAuthorized() {
        mockPermissions(Set.of("READ"));
        UUID id = UUID.randomUUID();
        TestEntity entity = new TestEntity();
        when(executor.findOne(any(Specification.class))).thenReturn(Optional.of(entity));

        Optional<TestEntity> result = service.findById(id, "name");

        assertTrue(result.isPresent());
        verify(executor).findOne(any(Specification.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll_withFields_shouldReturnEntities() {
        TestEntity entity = new TestEntity();
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.empty());
        when(executor.findAll(any(Specification.class))).thenReturn(List.of(entity));

        List<TestEntity> result = service.findAll("name", List.of("name:eq:value"), "AND");

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getAggregatedData_shouldSupportAllFunctions() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        CriteriaQuery<Object[]> cq = mock(CriteriaQuery.class);
        @SuppressWarnings("unchecked")
        Root<TestEntity> root = mock(Root.class);
        @SuppressWarnings("unchecked")
        TypedQuery<Object[]> typedQuery = mock(TypedQuery.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(Object[].class)).thenReturn(cq);
        when(cq.from(TestEntity.class)).thenReturn(root);

        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        when(root.get("name")).thenReturn(path);
        @SuppressWarnings("unchecked")
        Expression<Number> numExpr = mock(Expression.class);
        lenient().when(path.as(Number.class)).thenReturn(numExpr);
        lenient().when(numExpr.alias(anyString())).thenReturn(numExpr);

        @SuppressWarnings("unchecked")
        Expression<Double> avgExpr = mock(Expression.class);
        when(cb.avg(any())).thenReturn(avgExpr);
        lenient().when(avgExpr.alias(anyString())).thenReturn(avgExpr);

        @SuppressWarnings("unchecked")
        Expression<Long> countExpr = mock(Expression.class);
        when(cb.count(any())).thenReturn(countExpr);
        lenient().when(countExpr.alias(anyString())).thenReturn(countExpr);

        @SuppressWarnings("unchecked")
        Expression<Number> minExpr = mock(Expression.class);
        when(cb.min(any())).thenReturn(minExpr);
        lenient().when(minExpr.alias(anyString())).thenReturn(minExpr);

        @SuppressWarnings("unchecked")
        Expression<Number> maxExpr = mock(Expression.class);
        when(cb.max(any())).thenReturn(maxExpr);
        lenient().when(maxExpr.alias(anyString())).thenReturn(maxExpr);

        when(cb.sum(any())).thenReturn(numExpr);

        when(cq.select(any())).thenReturn(cq);
        when(entityManager.createQuery(cq)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(new ArrayList<>());

        List<AggregateDTO> aggs = new ArrayList<>();
        for (String func : List.of("SUM", "AVG", "COUNT", "MIN", "MAX")) {
            AggregateDTO agg = new AggregateDTO();
            agg.setColumn("name");
            agg.setFunc(func);
            agg.setAlias(func.toLowerCase());
            aggs.add(agg);
        }

        service.getAggregatedData(aggs, null, null, "AND");

        verify(cb, atLeastOnce()).sum(any());
        verify(cb, atLeastOnce()).avg(any());
        verify(cb, atLeastOnce()).count(any());
        verify(cb, atLeastOnce()).min(any());
        verify(cb, atLeastOnce()).max(any());
    }

    @Test
    void save_shouldThrowAccessDenied_whenUnauthorized() {
        mockPermissions(Set.of("NONE"));
        TestEntity entity = new TestEntity();
        assertThrows(AccessDeniedException.class, () -> service.save(entity));
    }

    @Test
    void update_shouldThrowAccessDenied_whenUnauthorized() {
        mockPermissions(Set.of("NONE"));
        UUID id = UUID.randomUUID();
        TestEntity entity = new TestEntity();
        assertThrows(AccessDeniedException.class, () -> service.update(id, entity));
    }

    @Test
    void delete_shouldThrowAccessDenied_whenUnauthorized() {
        mockPermissions(Set.of("NONE"));
        UUID id = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () -> service.delete(id));
    }

    @Test
    void deleteMany_shouldThrowAccessDenied_whenUnauthorized() {
        mockPermissions(Set.of("NONE"));
        List<String> emptyList = List.of();
        assertThrows(AccessDeniedException.class, () -> service.deleteMany(emptyList));
    }

    @Test
    void delete_shouldDeleteById() {
        UUID id = UUID.randomUUID();
        TestEntity entity = new TestEntity();
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        Map<String, String> response = service.delete(id);

        assertEquals("test deleted successfully", response.get("message"));
        verify(repository).deleteById(id);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteMany_shouldDeleteMultiple() {
        TestEntity entity = new TestEntity();
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.empty());
        when(executor.findAll(any(Specification.class))).thenReturn(List.of(entity));

        Map<String, String> response = service.deleteMany(List.of("name:eq:value"));

        assertTrue(response.get("message").contains("1 tests deleted successfully"));
        verify(repository).deleteAll(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAggregatedData_shouldReturnComplexData() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<Object[]> cq = mock(CriteriaQuery.class);
        Root<TestEntity> root = mock(Root.class);
        TypedQuery<Object[]> typedQuery = mock(TypedQuery.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(Object[].class)).thenReturn(cq);
        when(cq.from(TestEntity.class)).thenReturn(root);

        Path<Object> path = mock(Path.class);
        when(root.get("name")).thenReturn(path);
        when(path.alias("name")).thenReturn(path);

        Expression<Number> numPath = mock(Expression.class);
        lenient().when(path.as(Number.class)).thenReturn(numPath);
        Expression<Number> sumExpr = mock(Expression.class);
        when(cb.sum(any(Expression.class))).thenReturn(sumExpr);
        when(sumExpr.alias("total")).thenReturn(sumExpr);

        when(cq.select(any())).thenReturn(cq);
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.empty());
        when(entityManager.createQuery(cq)).thenReturn(typedQuery);

        List<Object[]> results = new ArrayList<>();
        results.add(new Object[] { "test", 100L });
        when(typedQuery.getResultList()).thenReturn(results);

        AggregateDTO agg = new AggregateDTO();
        agg.setColumn("name");
        agg.setFunc("SUM");
        agg.setAlias("total");

        List<Map<String, Object>> result = service.getAggregatedData(List.of(agg), List.of("name"), null, "AND");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test", result.get(0).get("name"));
        assertEquals(100L, result.get(0).get("total"));
    }
}