package com.flexcodelabs.flextuma.modules.connector.services;

import com.flexcodelabs.flextuma.core.entities.connector.ConnectorConfig;
import com.flexcodelabs.flextuma.core.repositories.ConnectorConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ConnectorConfigServiceTest {

    @Mock
    private ConnectorConfigRepository repository;

    @InjectMocks
    private ConnectorConfigService service;

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
