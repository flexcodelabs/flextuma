package com.flexcodelabs.flextuma.modules.notification.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.modules.notification.services.NotificationService;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("")
    public ResponseEntity<SmsLog> send(
            @RequestBody Map<String, String> variables,
            java.security.Principal principal) {

        SmsLog log = notificationService.queueTemplatedSms(variables, principal.getName());

        return ResponseEntity.ok(log);
    }
}