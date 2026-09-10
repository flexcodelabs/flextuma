package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches inbound WhatsApp media (images, documents, audio, video, stickers) from Meta's
 * Graph API and stores it on local disk, under a per-tenant subfolder so storage usage can be
 * attributed to the organisation (or, for org-less accounts, the individual user) that owns the
 * WhatsApp connector the message came through. Meta's webhook payload only carries a media id,
 * not the bytes: the id must first be exchanged for a short-lived CDN url (GET /{media-id}),
 * which is then downloaded with the same bearer token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppMediaService {

    private static final String WHATSAPP_PROVIDER = "WHATSAPP";

    private final SmsConnectorRepository smsConnectorRepository;
    private final RestTemplate restTemplate;

    @Value("${flextuma.whatsapp.media.directory:client/media/whatsapp}")
    private String mediaDirectory;

    /** Result of a successful download: the path (tenant folder + filename) to persist as
     * {@code WhatsAppInboxMessage.mediaPath}, and the byte size to persist as {@code mediaSize}
     * for per-tenant storage accounting. */
    public record DownloadedMedia(String path, long size) {}

    /**
     * Best-effort download: returns the stored path, or empty if no usable WhatsApp
     * connector (needed for the access token) can be found or the download otherwise fails.
     * Never throws -- a missing/failed media download must not block ingesting the message.
     */
    public Optional<DownloadedMedia> download(WhatsAppWebhookConfig config, String mediaId) {
        try {
            SmsConnector connector = resolveConnector(config);
            if (connector == null || connector.getKey() == null || connector.getUrl() == null) {
                log.warn("No usable WhatsApp connector with a token found for config [{}]; skipping media download for [{}]",
                        config.getId(), mediaId);
                return Optional.empty();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(connector.getKey());

            String metadataUrl = connector.getUrl().replaceAll("/$", "") + "/" + mediaId;
            Map<?, ?> metadata = restTemplate
                    .exchange(metadataUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class)
                    .getBody();
            Object cdnUrl = metadata != null ? metadata.get("url") : null;
            if (cdnUrl == null) {
                log.warn("WhatsApp media metadata for [{}] had no download url", mediaId);
                return Optional.empty();
            }

            byte[] bytes = restTemplate
                    .exchange(cdnUrl.toString(), HttpMethod.GET, new HttpEntity<>(headers), byte[].class)
                    .getBody();
            if (bytes == null || bytes.length == 0) {
                log.warn("WhatsApp media download for [{}] returned no bytes", mediaId);
                return Optional.empty();
            }

            String tenantFolder = tenantFolder(config.getCreatedBy());
            String filename = sanitize(mediaId);
            Path directory = Paths.get(mediaDirectory, tenantFolder);
            Files.createDirectories(directory);
            Files.write(directory.resolve(filename), bytes);
            return Optional.of(new DownloadedMedia(tenantFolder + "/" + filename, bytes.length));
        } catch (Exception e) {
            log.warn("Failed to download WhatsApp media [{}]: {}", mediaId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Mirrors {@code TenantAwareSpecification}'s notion of a tenant: the owning organisation,
     * falling back to the individual user for org-less accounts. */
    private String tenantFolder(User owner) {
        if (owner == null) {
            return "unassigned";
        }
        return owner.getOrganisation() != null
                ? "org-" + owner.getOrganisation().getId()
                : "user-" + owner.getId();
    }

    /** Prefers the webhook config's explicitly linked connector; falls back to the owner's
     * first active WhatsApp connector for configs that predate that link (ambiguous if the
     * owner has more than one). */
    private SmsConnector resolveConnector(WhatsAppWebhookConfig config) {
        if (config.getConnector() != null) {
            return config.getConnector();
        }
        return smsConnectorRepository
                .findByCreatedByAndProviderAndActiveTrue(config.getCreatedBy(), WHATSAPP_PROVIDER)
                .orElse(null);
    }

    /** Reads back previously downloaded media bytes. {@code storedPath} is either a bare
     * filename (pre-tenant-scoping messages) or a "tenantFolder/filename" path. Empty if the
     * file is missing. */
    public Optional<byte[]> read(String storedPath) {
        try {
            Path path = resolveStoredPath(storedPath);
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            log.warn("Failed to read stored WhatsApp media [{}]: {}", storedPath, e.getMessage());
            return Optional.empty();
        }
    }

    /** Sanitizes each path segment independently so a "tenantFolder/filename" path resolves to
     * the matching nested file rather than allowing traversal outside the media directory. */
    private Path resolveStoredPath(String storedPath) {
        Path resolved = Paths.get(mediaDirectory);
        for (String segment : storedPath.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            resolved = resolved.resolve(sanitize(segment));
        }
        return resolved;
    }

    private String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
