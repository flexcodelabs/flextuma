package com.flexcodelabs.flextuma.modules.notification.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.modules.dashboard.dtos.DashboardNotificationDTO;
import com.flexcodelabs.flextuma.modules.dashboard.services.DashboardService;
import com.flexcodelabs.flextuma.modules.notification.services.NotificationService;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final DashboardService dashboardService;

    @GetMapping()
    public ResponseEntity<Pagination<DashboardNotificationDTO>> listRecentNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        return ResponseEntity.ok(dashboardService.getRecentNotifications(PageRequest.of(safePage - 1, safePageSize)));
    }

    @PostMapping()
    public ResponseEntity<SmsLog> send(
            @RequestBody Map<String, String> variables,
            java.security.Principal principal) {

        SmsLog log = notificationService.queueTemplatedSms(variables, principal.getName());

        return ResponseEntity.ok(log);
    }

    @PostMapping("/raw")
    public ResponseEntity<SmsLog> sendRaw(
            @RequestBody Map<String, String> payload,
            java.security.Principal principal) {

        SmsLog log = notificationService.queueRawSms(payload, principal.getName());

        return ResponseEntity.ok(log);
    }
}
