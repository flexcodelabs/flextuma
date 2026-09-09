package com.flexcodelabs.flextuma.modules.connector.services;

import com.flexcodelabs.flextuma.core.entities.connector.ConnectorConfig;
import com.flexcodelabs.flextuma.core.enums.AuthType;
import com.flexcodelabs.flextuma.core.helpers.FieldMapping;
import com.flexcodelabs.flextuma.core.repositories.ConnectorConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectorConfigServiceTest {

    @Mock
    private ConnectorConfigRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private MockedStatic<SecurityContextHolder> securityContextHolderMock;

    @InjectMocks
    private ConnectorConfigService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);

        securityContextHolderMock = Mockito.mockStatic(SecurityContextHolder.class);
        securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @AfterEach
    void tearDown() {
        securityContextHolderMock.close();
    }

    private void mockPermissions(Set<String> permissions) {
        when(authentication.isAuthenticated()).thenReturn(true);
        List<org.springframework.security.core.GrantedAuthority> authorities = permissions.stream()
                .map(p -> (org.springframework.security.core.GrantedAuthority) () -> p)
                .toList();
        doReturn(authorities).when(authentication).getAuthorities();
    }

    @Test
    void update_shouldPreserveExistingMappings_whenRequestOmitsMappings() {
        mockPermissions(Set.of(ConnectorConfig.UPDATE));

        UUID id = UUID.randomUUID();

        ConnectorConfig existing = new ConnectorConfig();
        existing.setId(id);
        existing.setUrl("http://example.com");
        existing.setEndpoint("/api");
        existing.setAuthType(AuthType.NONE);
        existing.setMappings(List.of(new FieldMapping("name", "$.name")));

        ConnectorConfig incoming = new ConnectorConfig();
        incoming.setUrl("http://example.com/updated");
        incoming.setEndpoint("/api/v2");
        incoming.setAuthType(AuthType.NONE);

        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
        when(repository.save(any(ConnectorConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        ConnectorConfig result = service.update(id, incoming);

        assertEquals(1, result.getMappings().size());
        assertEquals("name", result.getMappings().get(0).systemKey());
        assertEquals("$.name", result.getMappings().get(0).jsonPath());
    }

    @Test
    void update_shouldReplaceMappings_whenRequestIncludesMappings() {
        mockPermissions(Set.of(ConnectorConfig.UPDATE));

        UUID id = UUID.randomUUID();

        ConnectorConfig existing = new ConnectorConfig();
        existing.setId(id);
        existing.setUrl("http://example.com");
        existing.setEndpoint("/api");
        existing.setAuthType(AuthType.NONE);
        existing.setMappings(List.of(new FieldMapping("name", "$.name")));

        ConnectorConfig incoming = new ConnectorConfig();
        incoming.setUrl("http://example.com/updated");
        incoming.setEndpoint("/api/v2");
        incoming.setAuthType(AuthType.NONE);
        incoming.setMappings(List.of(new FieldMapping("email", "$.email")));

        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
        when(repository.save(any(ConnectorConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        ConnectorConfig result = service.update(id, incoming);

        assertEquals(1, result.getMappings().size());
        assertEquals("email", result.getMappings().get(0).systemKey());
    }

    @Test
    void getRepository_shouldReturnRepository() {
        assertEquals(repository, service.getRepository());
    }

    @Test
    void getRepositoryAsExecutor_shouldReturnRepository() {
        assertEquals(repository, service.getRepositoryAsExecutor());
    }

    @Test
    void getPermissions_shouldReturnCorrectValues() {
        assertEquals(ConnectorConfig.READ, service.getReadPermission());
        assertEquals(ConnectorConfig.ADD, service.getAddPermission());
        assertEquals(ConnectorConfig.UPDATE, service.getUpdatePermission());
        assertEquals(ConnectorConfig.DELETE, service.getDeletePermission());
    }

    @Test
    void getEntityNames_shouldReturnCorrectValues() {
        assertEquals(ConnectorConfig.NAME_PLURAL, service.getEntityPlural());
        assertEquals(ConnectorConfig.NAME_SINGULAR, service.getEntitySingular());
        assertEquals(ConnectorConfig.PLURAL, service.getPropertyName());
    }

    @Test
    void validateDelete_whenActive_shouldThrowException() {
        ConnectorConfig config = new ConnectorConfig();
        config.setActive(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.validateDelete(config));
        assertEquals("Cannot delete an active config", exception.getMessage());
    }

    @Test
    void validateDelete_whenNotActive_shouldNotThrow() {
        ConnectorConfig config = new ConnectorConfig();
        config.setActive(false);

        assertDoesNotThrow(() -> service.validateDelete(config));
    }

    @Test
    void validateDelete_whenActiveIsNull_shouldNotThrow() {
        ConnectorConfig config = new ConnectorConfig();
        config.setActive(null);

        assertDoesNotThrow(() -> service.validateDelete(config));
    }
}
