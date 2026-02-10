package com.flexcodelabs.flextuma.modules.sms.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.modules.sms.services.SmsConnectorService;

@RestController
@RequestMapping("/api/" + SmsConnector.PLURAL)
public class SmsConnectorController extends BaseController<SmsConnector, SmsConnectorService> {

    public SmsConnectorController(SmsConnectorService service) {
        super(service);
    }
}