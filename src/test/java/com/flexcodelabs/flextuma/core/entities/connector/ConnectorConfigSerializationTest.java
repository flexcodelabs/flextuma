package com.flexcodelabs.flextuma.core.entities.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcodelabs.flextuma.core.enums.AuthType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorConfigSerializationTest {

    @Test
    void testSensitiveFieldsAreMasked() throws Exception {
        ConnectorConfig config = new ConnectorConfig();
        config.setTenantId("secret-tenant");
        config.setUrl("http://example.com");
        config.setEndpoint("/api");
        config.setAuthType(AuthType.BEARER);
        config.setToken("secret-token");
        config.setApiKey("secret-key");
        config.setUsername("secret-user");
        config.setPassword("secret-pass");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(config);

        assertTrue(json.contains("\"tenantId\":\"******\""), "tenantId should be masked");
        assertTrue(json.contains("\"token\":\"******\""), "token should be masked");
        assertTrue(json.contains("\"apiKey\":\"******\""), "apiKey should be masked");
        assertTrue(json.contains("\"username\":\"******\""), "username should be masked");
        assertTrue(json.contains("\"password\":\"******\""), "password should be masked");
        assertTrue(json.contains("\"url\":\"******\""), "url should be masked");
    }
}
