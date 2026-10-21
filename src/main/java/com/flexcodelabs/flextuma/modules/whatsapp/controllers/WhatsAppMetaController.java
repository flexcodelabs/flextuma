package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppMetaCheckRequest;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppMetaCheckResult;
import com.flexcodelabs.flextuma.modules.whatsapp.services.meta.WhatsAppMetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Live checks against Meta's Graph API for the WhatsApp setup screens (authenticated). */
@RestController
@RequestMapping("/api/whatsappMeta")
@RequiredArgsConstructor
public class WhatsAppMetaController {
    private final WhatsAppMetaService metaService;

    /** Validates credentials and returns the account's phone numbers and app subscriptions. */
    @PostMapping("/check")
    public ResponseEntity<WhatsAppMetaCheckResult> check(@RequestBody WhatsAppMetaCheckRequest request) {
        return ResponseEntity.ok(metaService.check(request));
    }

    /** Subscribes the token's Meta app to the WhatsApp Business Account's webhooks. */
    @PostMapping("/subscribe")
    public ResponseEntity<WhatsAppMetaCheckResult> subscribe(@RequestBody WhatsAppMetaCheckRequest request) {
        return ResponseEntity.ok(metaService.subscribe(request));
    }
}
