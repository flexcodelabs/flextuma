package com.flexcodelabs.flextuma.modules.notification.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.flexcodelabs.flextuma.modules.notification.services.NotificationService;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("")
    public ResponseEntity<Map<String, String>> sendSms(
            @RequestParam String templateCode,
            @RequestParam String phone,
            @RequestBody Map<String, String> variables) {
        notificationService.sendTemplatedSms(templateCode, phone, variables);
        return ResponseEntity.ok(Map.of("message", "SMS request queued successfully"));
    }
}