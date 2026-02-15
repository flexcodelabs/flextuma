package com.flexcodelabs.flextuma.modules.connector.services;

import com.flexcodelabs.flextuma.core.entities.connector.ConnectorConfig;
import com.flexcodelabs.flextuma.core.helpers.FieldMapping;
import com.flexcodelabs.flextuma.core.repositories.ConnectorConfigRepository;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DataHydratorService {

    private final ConnectorConfigRepository repository;
    private final RestClient restClient;

    public DataHydratorService(ConnectorConfigRepository repository, RestClient.Builder restClientBuilder) {
        this.repository = repository;
        this.restClient = restClientBuilder.build();
    }

    public Map<String, String> getMemberData(String tenantId, String memberId) {
        ConnectorConfig config = repository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Connector not configured for tenant: " + tenantId));

        String url = config.getUrl() + config.getEndpoint().replace("{id}", memberId);

        try {
            String rawJsonResponse = restClient.get()
                    .uri(url)
                    .headers(h -> applyAuthentication(h, config))
                    .retrieve()
                    .body(String.class);

            return applyMappings(rawJsonResponse, config.getMappings());
        } catch (Exception e) {
            log.error("Failed to hydrate data for member {}: {}", memberId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "External API call failed");
        }
    }

    private Map<String, String> applyMappings(String json, List<FieldMapping> mappings) {
        Map<String, String> hydratedData = new HashMap<>();

        DocumentContext context = JsonPath.parse(json);

        for (FieldMapping m : mappings) {
            try {
                Object value = context.read(m.jsonPath());
                hydratedData.put(m.systemKey(), value != null ? String.valueOf(value) : null);
            } catch (Exception e) {
                log.warn("Path {} not found in response", m.jsonPath());
                hydratedData.put(m.systemKey(), "[N/A]");
            }
        }
        return hydratedData;
    }

    private void applyAuthentication(HttpHeaders headers, ConnectorConfig config) {
        if (config.getAuthType() == null)
            return;

        switch (config.getAuthType()) {
            case BEARER -> headers.setBearerAuth(config.getToken());
            case API_KEY -> headers.set("X-API-KEY", config.getToken());
            case BASIC -> headers.setBasicAuth(config.getUsername(), config.getPassword());
            case NONE -> {
                // No auth needed
            }
            default -> throw new IllegalArgumentException("Unsupported auth type: " + config.getAuthType());
        }
    }
}