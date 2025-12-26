package com.flexcodelabs.flextuma.modules.sms.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.modules.sms.services.SmsTemplateService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/" + SmsTemplate.PLURAL)
public class SmsTemplateController extends BaseController<SmsTemplate, SmsTemplateService> {

	public SmsTemplateController(SmsTemplateService service) {
		super(service);
	}
}
