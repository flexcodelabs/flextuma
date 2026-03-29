package com.flexcodelabs.flextuma.modules.app.controllers;

import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Controller
public class FrontendController {

    @Value("${flextuma.app.frontend.directory:${APP_FRONTEND_DIRECTORY:/app/client}}")
    private String frontendDirectory;
    private String indexFile = "index.html";

    @GetMapping("/")
    public ResponseEntity<Resource> serveIndex() {
        return serveStatic(indexFile);
    }

    @GetMapping("/assets/{filename:.+}")
    public ResponseEntity<Resource> serveAsset(@PathVariable String filename) {
        return serveStatic("assets/" + filename);
    }

    @GetMapping("/**")
    public ResponseEntity<Resource> serveCatchAll(HttpServletRequest request) {
        String path = request.getRequestURI().substring(1);
        if (path == null || path.isEmpty()) {
            return serveStatic(indexFile);
        }

        if (path.startsWith("api/")) {
            return ResponseEntity.notFound().build();
        }

        if (path.startsWith("assets/")) {
            return serveStatic(path);
        }

        if (path.equals("favicon.ico")) {
            return serveStatic(path);
        }

        if (path.startsWith(".well-known/")) {
            return serveStatic(indexFile);
        }

        return serveStatic(indexFile);
    }

    private ResponseEntity<Resource> serveStatic(String path) {
        try {
            Path filePath = Paths.get(frontendDirectory, path);
            Resource resource = new FileSystemResource(filePath.toString());

            if (resource.exists() || resource.isReadable()) {
                String contentType = getContentType(path);

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header("Content-Type", contentType)
                        .header("X-Content-Type-Options", "nosniff")
                        .body(resource);
            }
        } catch (Exception e) {
            log.error("Error serving file {}: {}", path, e.getMessage(), e);
        }

        log.warn("File not found: {}", path);
        return ResponseEntity.notFound().build();
    }

    private String getContentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css";
        } else if (path.endsWith(".js")) {
            return "application/javascript";
        } else if (path.endsWith(".html")) {
            return "text/html";
        } else if (path.endsWith(".json")) {
            return "application/json";
        } else if (path.endsWith(".png")) {
            return "image/png";
        } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (path.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (path.endsWith(".ico")) {
            return "image/x-icon";
        }

        try {
            Path filePath = Paths.get(frontendDirectory, path);
            return Files.probeContentType(filePath);
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }
}
