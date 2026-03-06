package com.flexcodelabs.flextuma.modules.logging.controllers;

import com.flexcodelabs.flextuma.core.entities.logging.SystemLog;
import com.flexcodelabs.flextuma.core.enums.LogLevel;
import com.flexcodelabs.flextuma.modules.logging.services.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/systemLogs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'ALL')")
public class SystemLogController {

    private final SystemLogService systemLogService;

    @GetMapping
    public Map<String, Object> getAll(
            Pageable pageable,
            @RequestParam(required = false) LogLevel level,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        Page<SystemLog> page = systemLogService.findAll(pageable, level, source, traceId, from, to);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", page.getNumber());
        response.put("total", page.getTotalElements());
        response.put("pageSize", page.getSize());
        response.put("systemLog", page.getContent());

        return response;
    }

    @GetMapping(value = "/tail", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter tail(@RequestParam(required = false) LogLevel level) {
        return systemLogService.streamLogs(level);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(systemLogService.getSystemHealth());
    }

    @DeleteMapping("/purge")
    public ResponseEntity<Map<String, Object>> purge(
            @RequestParam(defaultValue = "30") int days) {
        int deleted = systemLogService.purgeOlderThan(days);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", deleted + " log entries purged");
        response.put("olderThanDays", days);

        return ResponseEntity.ok(response);
    }
}
