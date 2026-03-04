package com.flexcodelabs.flextuma.modules.sms.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.modules.sms.services.SmsTemplateService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.flexcodelabs.flextuma.core.helpers.SmsSegmentCalculator;
import com.flexcodelabs.flextuma.core.helpers.SmsSegmentResult;
import com.flexcodelabs.flextuma.core.helpers.TemplateUtils;

@RestController
@RequestMapping("/api/" + SmsTemplate.PLURAL)
public class SmsTemplateController extends BaseController<SmsTemplate, SmsTemplateService> {

	public SmsTemplateController(SmsTemplateService service) {
		super(service);
	}

	@PostMapping("/preview")
	public ResponseEntity<PreviewResponse> preview(@RequestBody PreviewRequest request) {
		String rendered = TemplateUtils.fillTemplate(request.template(), request.variables());
		SmsSegmentResult segments = SmsSegmentCalculator.calculate(rendered);
		String encoding = segments.isGsm7() ? "GSM-7" : "UCS-2";
		return ResponseEntity.ok(new PreviewResponse(rendered, segments.segments(), encoding));
	}
}
