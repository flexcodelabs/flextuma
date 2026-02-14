package com.flexcodelabs.flextuma.modules.sms.controllers;

import org.mockito.Mock;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.controllers.BaseControllerTest;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.modules.sms.services.SmsTemplateService;

public class SmsTemplateControllerTest extends BaseControllerTest<SmsTemplate, SmsTemplateService> {

    @Mock
    private SmsTemplateService service;

    private SmsTemplateController controller;

    @Override
    protected BaseController<SmsTemplate, SmsTemplateService> getController() {
        if (controller == null) {
            controller = new SmsTemplateController(service);
        }
        return controller;
    }

    @Override
    protected SmsTemplateService getService() {
        return service;
    }

    @Override
    protected SmsTemplate createEntity() {
        SmsTemplate template = new SmsTemplate();
        template.setName("Test Template");
        return template;
    }

    @Override
    protected String getBaseUrl() {
        return "/api/templates";
    }
}
