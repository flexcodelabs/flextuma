package com.flexcodelabs.flextuma.modules.sms.controllers;

import org.mockito.Mock;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.controllers.BaseControllerTest;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.modules.sms.services.SmsConnectorService;

public class SmsConnectorControllerTest extends BaseControllerTest<SmsConnector, SmsConnectorService> {

    @Mock
    private SmsConnectorService service;

    private SmsConnectorController controller;

    @Override
    protected BaseController<SmsConnector, SmsConnectorService> getController() {
        if (controller == null) {
            controller = new SmsConnectorController(service);
        }
        return controller;
    }

    @Override
    protected SmsConnectorService getService() {
        return service;
    }

    @Override
    protected SmsConnector createEntity() {
        SmsConnector connector = new SmsConnector();
        connector.setProvider("Test Connector");
        return connector;
    }

    @Override
    protected String getBaseUrl() {
        return "/api/smsConnectors";
    }
}
