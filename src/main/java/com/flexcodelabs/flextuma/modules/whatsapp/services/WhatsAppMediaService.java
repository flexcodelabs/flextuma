package com.flexcodelabs.flextuma.modules.whatsapp.services;

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
 * Graph API and stores it on local disk. Meta's webhook payload only carries a media id, not
 * the bytes: the id must first be exchanged for a short-lived CDN url (GET /{media-id}), which
 * is then downloaded with the same bearer token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppMediaService {

    private static final String WHATSAPP_PROVIDER = "WHATSAPP";

    private final SmsConnectorRepository smsConnectorRepository;
    private final RestTemplate restTemplate;

    @Value("${flextuma.whatsapp.media.directory:/tmp/whatsapp-media}")
    private String mediaDirectory;

    /**
     * Best-effort download: returns the stored filename, or empty if no usable WhatsApp
     * connector (needed for the access token) can be found or the download otherwise fails.
     * Never throws -- a missing/failed media download must not block ingesting the message.
     */
    public Optional<String> download(WhatsAppWebhookConfig config, String mediaId) {
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

            String filename = sanitize(mediaId);
            Path directory = Paths.get(mediaDirectory);
            Files.createDirectories(directory);
            Files.write(directory.resolve(filename), bytes);
            return Optional.of(filename);
        } catch (Exception e) {
            log.warn("Failed to download WhatsApp media [{}]: {}", mediaId, e.getMessage());
            return Optional.empty();
        }
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

    /** Reads back previously downloaded media bytes. Empty if the file is missing. */
    public Optional<byte[]> read(String filename) {
        try {
            Path path = Paths.get(mediaDirectory).resolve(sanitize(filename));
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            log.warn("Failed to read stored WhatsApp media [{}]: {}", filename, e.getMessage());
            return Optional.empty();
        }
    }

    private String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
