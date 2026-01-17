package com.flexcodelabs.flextuma.modules.connector.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.connector.ConnectorConfig;
import com.flexcodelabs.flextuma.modules.connector.services.ConnectorConfigService;

@RestController
@RequestMapping("/api/" + ConnectorConfig.PLURAL)
public class ConnectorConfigController extends BaseController<ConnectorConfig, ConnectorConfigService> {

	public ConnectorConfigController(ConnectorConfigService service) {
		super(service);
	}
}