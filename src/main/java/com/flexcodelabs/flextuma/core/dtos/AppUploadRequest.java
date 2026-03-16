package com.flexcodelabs.flextuma.core.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppUploadRequest {
    private MultipartFile zipFile;
    private String appName;
    private String version;
    private boolean overwrite = true;
}
