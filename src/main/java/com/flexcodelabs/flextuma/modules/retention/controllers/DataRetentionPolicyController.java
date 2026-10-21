package com.flexcodelabs.flextuma.modules.retention.controllers;

import com.flexcodelabs.flextuma.modules.retention.dtos.DataRetentionPolicyDTO;
import com.flexcodelabs.flextuma.modules.retention.dtos.DataRetentionPolicyUpdateDTO;
import com.flexcodelabs.flextuma.modules.retention.services.DataRetentionPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dataRetentionPolicy")
@RequiredArgsConstructor
public class DataRetentionPolicyController {

    private final DataRetentionPolicyService service;

    @GetMapping
    public ResponseEntity<DataRetentionPolicyDTO> get() {
        return ResponseEntity.ok(service.getForCurrentUser());
    }

    @PutMapping
    public ResponseEntity<DataRetentionPolicyDTO> update(@RequestBody DataRetentionPolicyUpdateDTO update) {
        return ResponseEntity.ok(service.updateForCurrentUser(update));
    }
}
