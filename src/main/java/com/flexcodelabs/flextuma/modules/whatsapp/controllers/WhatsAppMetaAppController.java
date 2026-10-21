package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppMetaApp;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppMetaAppService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/" + WhatsAppMetaApp.PLURAL)
public class WhatsAppMetaAppController extends BaseController<WhatsAppMetaApp, WhatsAppMetaAppService> {

    public WhatsAppMetaAppController(WhatsAppMetaAppService service) {
        super(service);
    }
}
