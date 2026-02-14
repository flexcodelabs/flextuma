package com.flexcodelabs.flextuma.core.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.base.BaseEntity;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class BaseServiceTest {

    private TestService service;

    @Mock
    private JpaRepository<TestEntity, UUID> repository;

    @Mock
    private JpaSpecificationExecutor<TestEntity> specificationExecutor;

    @Mock
    private EntityManager entityManager;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private MockedStatic<SecurityContextHolder> securityContextHolderMock;

    @BeforeEach
    void setUp() {
        service = new TestService(repository, specificationExecutor);
        service.entityManager = entityManager;

        securityContextHolderMock = Mockito.mockStatic(SecurityContextHolder.class);
        securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @AfterEach
    void tearDown() {
        securityContextHolderMock.close();
    }

    @Test
    void findAllPaginated_shouldReturnPagination_whenAuthorized() {
        mockPermissions(Set.of("READ_TEST"));
        Pageable pageable = Pageable.ofSize(10).withPage(0);
        TestEntity entity = new TestEntity();
        Page<TestEntity> page = new PageImpl<>(List.of(entity));

        when(specificationExecutor.findAll(any(), eq(pageable))).thenReturn(page);

        Pagination<TestEntity> result = service.findAllPaginated(pageable, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getData().size());
    }

    @Test
    void findAllPaginated_shouldThrowAccessDenied_whenUnauthorized() {
        mockPermissions(Set.of("OTHER_PERMISSION"));
        Pageable pageable = Pageable.ofSize(10);

        assertThrows(AccessDeniedException.class, () -> service.findAllPaginated(pageable, null, null));
    }

    @Test
    void findById_shouldReturnEntity_whenAuthorized() {
        mockPermissions(Set.of("READ_TEST"));
        UUID id = UUID.randomUUID();
        TestEntity entity = new TestEntity();
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        Optional<TestEntity> result = service.findById(id);

        assertTrue(result.isPresent());
        assertEquals(entity, result.get());
    }

    @Test
    void save_shouldSaveEntity_whenAuthorized() {
        mockPermissions(Set.of("ADD_TEST"));
        TestEntity entity = new TestEntity();
        when(repository.save(entity)).thenReturn(entity);

        TestEntity result = service.save(entity);

        assertNotNull(result);
        verify(repository).save(entity);
    }

    @Test
    void update_shouldUpdateEntity_whenAuthorized() {
        mockPermissions(Set.of("UPDATE_TEST"));
        UUID id = UUID.randomUUID();
        TestEntity existing = new TestEntity();
        existing.setId(id);
        TestEntity update = new TestEntity();
        update.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(existing));

        TestEntity result = service.update(id, update);

        assertNotNull(result);
    }

    @Test
    void delete_shouldDeleteEntity_whenAuthorized() {
        mockPermissions(Set.of("DELETE_TEST"));
        UUID id = UUID.randomUUID();
        TestEntity entity = new TestEntity();
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        service.delete(id);

        verify(repository).deleteById(id);
    }

    private void mockPermissions(Set<String> permissions) {
        when(authentication.isAuthenticated()).thenReturn(true);

        List<org.springframework.security.core.GrantedAuthority> authorities = permissions.stream()
                .map(p -> (org.springframework.security.core.GrantedAuthority) () -> p)
                .toList();

        doReturn(authorities).when(authentication).getAuthorities();
    }

    // Concrete implementation for testing
    static class TestEntity extends BaseEntity {
    }

    static class TestService extends BaseService<TestEntity> {
        private final JpaRepository<TestEntity, UUID> repository;
        private final JpaSpecificationExecutor<TestEntity> executor;

        public TestService(JpaRepository<TestEntity, UUID> repository, JpaSpecificationExecutor<TestEntity> executor) {
            this.repository = repository;
            this.executor = executor;
        }

        @Override
        protected JpaRepository<TestEntity, UUID> getRepository() {
            return repository;
        }

        @Override
        protected String getReadPermission() {
            return "READ_TEST";
        }

        @Override
        protected String getAddPermission() {
            return "ADD_TEST";
        }

        @Override
        protected String getUpdatePermission() {
            return "UPDATE_TEST";
        }

        @Override
        protected String getDeletePermission() {
            return "DELETE_TEST";
        }

        @Override
        public String getEntityPlural() {
            return "testEntities";
        }

        @Override
        public String getPropertyName() {
            return "testEntities";
        }

        @Override
        protected String getEntitySingular() {
            return "testEntity";
        }

        @Override
        protected JpaSpecificationExecutor<TestEntity> getRepositoryAsExecutor() {
            return executor;
        }
    }

    @Test
    void onPreUpdate_shouldReturnNewEntity_whenExceptionOccurs() {
        // We need to inject a mock ObjectMapper to force an exception
        com.fasterxml.jackson.databind.ObjectMapper mockMapper = mock(
                com.fasterxml.jackson.databind.ObjectMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", mockMapper);

        TestEntity oldEntity = new TestEntity();
        TestEntity newEntity = new TestEntity();

        try {
            lenient().when(mockMapper.updateValue(any(), any())).thenThrow(new RuntimeException("Update error"));
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        // onPreUpdate is protected, but we are in the same package (by package name
        // matching), so we can access it.
        // We call onPreUpdate directly to test the return value logic in the catch
        // block.

        TestEntity result = service.onPreUpdate(newEntity, oldEntity);

        assertEquals(newEntity, result);
    }
}