package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppWebhookConfigService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/" + WhatsAppWebhookConfig.PLURAL)
public class WhatsAppWebhookConfigController extends BaseController<WhatsAppWebhookConfig, WhatsAppWebhookConfigService> {
    public WhatsAppWebhookConfigController(WhatsAppWebhookConfigService service) { super(service); }
}
