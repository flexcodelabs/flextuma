package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookEvent;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppWebhookEventService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/" + WhatsAppWebhookEvent.PLURAL)
public class WhatsAppWebhookEventController extends BaseController<WhatsAppWebhookEvent, WhatsAppWebhookEventService> {

    public WhatsAppWebhookEventController(WhatsAppWebhookEventService service) {
        super(service);
    }
}
