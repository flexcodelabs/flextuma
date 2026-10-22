package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppTemplateSyncResultDTO;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppTemplateService;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppTemplateSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/" + WhatsAppTemplate.PLURAL)
public class WhatsAppTemplateController extends BaseController<WhatsAppTemplate, WhatsAppTemplateService> {
    private final WhatsAppTemplateSyncService syncService;

    public WhatsAppTemplateController(WhatsAppTemplateService service, WhatsAppTemplateSyncService syncService) {
        super(service);
        this.syncService = syncService;
    }

    /** Pulls templates from Meta for every WhatsApp Business Account the caller has a number in
     * (or just {@code businessAccountId}), marking any Meta no longer returns as REMOVED. */
    @PostMapping("/sync")
    public ResponseEntity<WhatsAppTemplateSyncResultDTO> sync(@RequestParam(required = false) String businessAccountId) {
        return ResponseEntity.ok(syncService.syncForCurrentUser(businessAccountId));
    }
}
