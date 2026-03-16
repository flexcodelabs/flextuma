package com.flexcodelabs.flextuma.core.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppUploadResponse {
    private String message;
    private String appName;
    private String version;
    private String extractedPath;
    private long fileSize;
    private int extractedFiles;
}
