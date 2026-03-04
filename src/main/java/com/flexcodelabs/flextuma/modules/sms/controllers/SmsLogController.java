package com.flexcodelabs.flextuma.modules.sms.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.modules.sms.services.SmsLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/smsLogs")
public class SmsLogController extends BaseController<SmsLog, SmsLogService> {

    public SmsLogController(SmsLogService service) {
        super(service);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<SmsLog> retryFailedMessage(@PathVariable UUID id) {
        SmsLog updatedLog = service.retryFailedMessage(id);
        return ResponseEntity.ok(updatedLog);
    }
}
