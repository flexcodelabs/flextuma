package com.flexcodelabs.flextuma.modules.app.services;

import com.flexcodelabs.flextuma.core.dtos.AppUploadRequest;
import com.flexcodelabs.flextuma.core.dtos.AppUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppUploadService {

    @Value("${flextuma.app.upload.directory:${APP_UPLOAD_DIRECTORY:/tmp/apps}}")
    private String uploadDirectory;

    @Value("${flextuma.app.frontend.directory:${APP_FRONTEND_DIRECTORY:/app/client}}")
    private String frontendDirectory;

    public AppUploadResponse uploadAndExtractApp(AppUploadRequest request) {
        try {
            MultipartFile zipFile = request.getZipFile();
            String appName = request.getAppName();
            String version = request.getVersion();
            boolean overwrite = request.isOverwrite();

            Path appDir = Paths.get(uploadDirectory, appName);
            Path versionDir = appDir.resolve(version);

            if (Files.exists(versionDir) && !overwrite) {
                return new AppUploadResponse(
                        "App version already exists. Use overwrite=true to replace.",
                        appName,
                        version,
                        versionDir.toString(),
                        zipFile.getSize(),
                        0);
            }

            Files.createDirectories(appDir);
            if (overwrite && Files.exists(versionDir)) {
                deleteDirectory(versionDir);
            }
            Files.createDirectories(versionDir);

            int extractedFiles = extractZipFile(zipFile, versionDir);

            Path frontendAppDir = Paths.get(frontendDirectory);
            if (overwrite && Files.exists(frontendAppDir)) {
                deleteDirectory(frontendAppDir);
            }
            Files.createDirectories(frontendAppDir);
            copyDirectory(versionDir, frontendAppDir);

            return new AppUploadResponse(
                    "App uploaded and extracted successfully",
                    appName,
                    version,
                    versionDir.toString(),
                    zipFile.getSize(),
                    extractedFiles);

        } catch (Exception e) {
            log.error("Error uploading app: {}", e.getMessage(), e);
            return new AppUploadResponse(
                    "Failed to upload app: " + e.getMessage(),
                    request.getAppName(),
                    request.getVersion(),
                    null,
                    request.getZipFile().getSize(),
                    0);
        }
    }

    private int extractZipFile(MultipartFile zipFile, Path extractDir) throws IOException {
        int extractedFiles = 0;

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Path dir = extractDir.resolve(entry.getName());
                    Files.createDirectories(dir);
                    continue;
                }

                Path filePath = extractDir.resolve(entry.getName());
                Files.createDirectories(filePath.getParent());

                try (OutputStream fos = Files.newOutputStream(filePath, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }

                extractedFiles++;
                zis.closeEntry();
            }
        }

        return extractedFiles;
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try {
                try (var stream = Files.walk(path)) {
                    stream.sorted((a, b) -> b.compareTo(a))
                            .forEach(filePath -> {
                                try {
                                    Files.delete(filePath);
                                } catch (IOException e) {
                                    // continue with other files
                                }
                            });
                }
            } catch (IOException e) {
                // directory might not exist
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try {
            try (var stream = Files.walk(source)) {
                stream.filter(path -> !Files.isDirectory(path))
                        .forEach(sourceFile -> {
                            Path targetFile = null;
                            try {
                                Path relative = source.relativize(sourceFile);
                                targetFile = target.resolve(relative);
                                Files.createDirectories(targetFile.getParent());
                                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                // continue with other files
                            }
                        });
            }
        } catch (IOException e) {
            // source might not exist
        }
    }
}
