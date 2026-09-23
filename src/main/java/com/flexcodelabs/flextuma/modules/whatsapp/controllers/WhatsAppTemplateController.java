package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppTemplateService;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppTemplateSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/" + WhatsAppTemplate.PLURAL)
public class WhatsAppTemplateController extends BaseController<WhatsAppTemplate, WhatsAppTemplateService> {
    private final WhatsAppTemplateSyncService syncService;

    public WhatsAppTemplateController(WhatsAppTemplateService service, WhatsAppTemplateSyncService syncService) {
        super(service);
        this.syncService = syncService;
    }

    /** Pulls the caller's own WhatsApp connector(s) templates from Meta's Graph API and upserts
     * them, marking any template Meta no longer returns as REMOVED. */
    @PostMapping("/sync")
    public ResponseEntity<List<WhatsAppTemplate>> sync() {
        return ResponseEntity.ok(syncService.syncForCurrentUser());
    }
}
