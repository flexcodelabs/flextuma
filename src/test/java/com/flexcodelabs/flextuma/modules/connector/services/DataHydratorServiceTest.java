package com.flexcodelabs.flextuma.modules.connector.services;

import com.flexcodelabs.flextuma.core.entities.connector.ConnectorConfig;
import com.flexcodelabs.flextuma.core.helpers.FieldMapping;
import com.flexcodelabs.flextuma.core.repositories.ConnectorConfigRepository;
import com.flexcodelabs.flextuma.core.enums.AuthType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataHydratorServiceTest {

    @Mock
    private ConnectorConfigRepository repository;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private DataHydratorService service;

    @BeforeEach
    void setUp() {
        // Mock the builder chain
        when(restClientBuilder.build()).thenReturn(restClient);
        service = new DataHydratorService(repository, restClientBuilder);
    }

    @Test
    void getMemberData_shouldReturnHydratedData_whenConfigExists() {
        // Setup config
        ConnectorConfig config = new ConnectorConfig();
        config.setTenantId("tenant1");
        config.setUrl("http://api.example.com");
        config.setEndpoint("/users/{id}");
        config.setAuthType(AuthType.NONE);

        List<FieldMapping> mappings = new ArrayList<>();
        FieldMapping mapping = new FieldMapping("fullName", "$.name");
        mappings.add(mapping);
        config.setMappings(mappings);

        when(repository.findByTenantId("tenant1")).thenReturn(Optional.of(config));

        // Mock RestClient chain
        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
        doReturn(requestHeadersSpec).when(requestHeadersSpec).headers(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"name\": \"John Doe\"}");

        Map<String, String> result = service.getMemberData("tenant1", "123");

        assertEquals("John Doe", result.get("fullName"));
    }

    @Test
    void getMemberData_shouldApplyBearerAuth() {
        ConnectorConfig config = new ConnectorConfig();
        config.setTenantId("tenant1");
        config.setUrl("http://api.example.com");
        config.setEndpoint("/users/{id}");
        config.setAuthType(AuthType.BEARER);
        config.setToken("token123");
        config.setMappings(new ArrayList<>());

        when(repository.findByTenantId("tenant1")).thenReturn(Optional.of(config));

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
        // Verify headers are applied
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.http.HttpHeaders> headersConsumer = invocation
                    .getArgument(0);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headersConsumer.accept(headers);
            assertNotNull(headers.get(org.springframework.http.HttpHeaders.AUTHORIZATION));
            assertTrue(headers.getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION).startsWith("Bearer "));
            return requestHeadersSpec;
        }).when(requestHeadersSpec).headers(any());

        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{}");

        service.getMemberData("tenant1", "123");
    }

    @Test
    void getMemberData_shouldApplyBasicAuth() {
        ConnectorConfig config = new ConnectorConfig();
        config.setTenantId("tenant1");
        config.setUrl("http://api.example.com");
        config.setEndpoint("/users/{id}");
        config.setAuthType(AuthType.BASIC);
        config.setUsername("user");
        config.setPassword("pass");
        config.setMappings(new ArrayList<>());

        when(repository.findByTenantId("tenant1")).thenReturn(Optional.of(config));

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.http.HttpHeaders> headersConsumer = invocation
                    .getArgument(0);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headersConsumer.accept(headers);
            assertNotNull(headers.get(org.springframework.http.HttpHeaders.AUTHORIZATION));
            assertTrue(headers.getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION).startsWith("Basic "));
            return requestHeadersSpec;
        }).when(requestHeadersSpec).headers(any());

        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{}");

        service.getMemberData("tenant1", "123");
    }

    @Test
    void getMemberData_shouldApplyApiKeyAuth() {
        ConnectorConfig config = new ConnectorConfig();
        config.setTenantId("tenant1");
        config.setUrl("http://api.example.com");
        config.setEndpoint("/users/{id}");
        config.setAuthType(AuthType.API_KEY);
        config.setToken("api-key-123");
        config.setMappings(new ArrayList<>());

        when(repository.findByTenantId("tenant1")).thenReturn(Optional.of(config));

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.http.HttpHeaders> headersConsumer = invocation
                    .getArgument(0);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headersConsumer.accept(headers);
            assertNotNull(headers.get("X-API-KEY"));
            assertEquals("api-key-123", headers.getFirst("X-API-KEY"));
            return requestHeadersSpec;
        }).when(requestHeadersSpec).headers(any());

        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{}");

        service.getMemberData("tenant1", "123");
    }

    @Test
    void getMemberData_shouldThrowException_whenApiCallFails() {
        ConnectorConfig config = new ConnectorConfig();
        config.setTenantId("tenant1");
        config.setUrl("http://api.example.com");
        config.setEndpoint("/users/{id}");
        config.setAuthType(AuthType.NONE);

        when(repository.findByTenantId("tenant1")).thenReturn(Optional.of(config));

        doReturn(requestHeadersUriSpec).when(restClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
        doReturn(requestHeadersSpec).when(requestHeadersSpec).headers(any());
        when(requestHeadersSpec.retrieve()).thenThrow(new RuntimeException("API Error"));

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.getMemberData("tenant1", "123"));
    }
}
