package com.flexcodelabs.flextuma.modules.notification.controllers;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.notification.PersonalNotification;
import com.flexcodelabs.flextuma.modules.notification.dtos.NotificationSummaryDTO;
import com.flexcodelabs.flextuma.modules.notification.services.PersonalNotificationService;

@RestController
@RequestMapping("/api/" + PersonalNotification.PLURAL)
public class PersonalNotificationController
        extends BaseController<PersonalNotification, PersonalNotificationService> {

    public PersonalNotificationController(PersonalNotificationService service) {
        super(service);
    }

    @GetMapping("/summary")
    public ResponseEntity<NotificationSummaryDTO> summary(@RequestParam(defaultValue = "5") int pageSize) {
        return ResponseEntity.ok(service.getSummary(pageSize));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<PersonalNotification> markAsRead(@PathVariable UUID id) {
        return ResponseEntity.ok(service.markAsRead(id));
    }

    @PostMapping("/readAll")
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        return ResponseEntity.ok(service.markAllAsRead());
    }
}
