package com.flexcodelabs.flextuma.modules.connector.controllers;

import org.mockito.Mock;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.controllers.BaseControllerTest;
import com.flexcodelabs.flextuma.core.entities.connector.ConnectorConfig;
import com.flexcodelabs.flextuma.modules.connector.services.ConnectorConfigService;

public class ConnectorConfigControllerTest extends BaseControllerTest<ConnectorConfig, ConnectorConfigService> {

    @Mock
    private ConnectorConfigService service;

    private ConnectorConfigController controller;

    @Override
    protected BaseController<ConnectorConfig, ConnectorConfigService> getController() {
        if (controller == null) {
            controller = new ConnectorConfigController(service);
        }
        return controller;
    }

    @Override
    protected ConnectorConfigService getService() {
        return service;
    }

    @Override
    protected ConnectorConfig createEntity() {
        ConnectorConfig config = new ConnectorConfig();
        config.setTenantId("test-tenant");
        config.setUrl("http://example.com");
        config.setEndpoint("/api");
        return config;
    }

    @Override
    protected String getBaseUrl() {
        return "/api/connectorConfigs";
    }
}
