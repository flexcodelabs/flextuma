package com.flexcodelabs.flextuma.modules.app.controllers;

import com.flexcodelabs.flextuma.core.dtos.AppUploadRequest;
import com.flexcodelabs.flextuma.core.dtos.AppUploadResponse;
import com.flexcodelabs.flextuma.modules.app.services.AppUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class AppController {

    private final AppUploadService appUploadService;

    @PostMapping()
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN')")
    public ResponseEntity<AppUploadResponse> uploadApp(@ModelAttribute AppUploadRequest request) {
        try {
            AppUploadResponse response = appUploadService.uploadAndExtractApp(request);

            if (response.getMessage().startsWith("Failed")) {
                return ResponseEntity.badRequest().body(response);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            AppUploadResponse errorResponse = new AppUploadResponse(
                    "Upload failed: " + e.getMessage(),
                    request.getAppName(),
                    request.getVersion(),
                    null,
                    0L,
                    0);
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
