package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppWebhookOverviewDTO;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppWebhookConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/" + WhatsAppWebhookConfig.PLURAL)
public class WhatsAppWebhookConfigController extends BaseController<WhatsAppWebhookConfig, WhatsAppWebhookConfigService> {
    public WhatsAppWebhookConfigController(WhatsAppWebhookConfigService service) { super(service); }

    @GetMapping("/overview")
    public ResponseEntity<WhatsAppWebhookOverviewDTO> overview() {
        return ResponseEntity.ok(service.getOverview());
    }
}
