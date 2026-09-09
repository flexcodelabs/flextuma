package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppMediaServiceTest {

    @Mock
    private SmsConnectorRepository smsConnectorRepository;

    @Mock
    private RestTemplate restTemplate;

    private WhatsAppMediaService service;

    @TempDir
    Path tempDir;

    private User owner;
    private SmsConnector connector;
    private WhatsAppWebhookConfig config;

    @BeforeEach
    void setUp() {
        service = new WhatsAppMediaService(smsConnectorRepository, restTemplate);
        ReflectionTestUtils.setField(service, "mediaDirectory", tempDir.toString());

        owner = new User();
        owner.setId(UUID.randomUUID());

        connector = new SmsConnector();
        connector.setProvider("WHATSAPP");
        connector.setUrl("https://graph.facebook.com/v21.0");
        connector.setKey("permanent-token");

        config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());
        config.setCreatedBy(owner);
    }

    @Test
    void download_shouldFetchMetadataThenBytesAndWriteToDisk_viaOwnerFallback() {
        when(smsConnectorRepository.findByCreatedByAndProviderAndActiveTrue(owner, "WHATSAPP"))
                .thenReturn(Optional.of(connector));
        when(restTemplate.exchange(eq("https://graph.facebook.com/v21.0/media-123"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("url", "https://cdn.example.com/blob")));
        byte[] fileBytes = { 1, 2, 3, 4 };
        when(restTemplate.exchange(eq("https://cdn.example.com/blob"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(fileBytes));

        Optional<String> stored = service.download(config, "media-123");

        assertTrue(stored.isPresent());
        Path storedFile = tempDir.resolve(stored.get());
        assertTrue(Files.exists(storedFile));
        assertArrayEquals(fileBytes, readAllBytes(storedFile));
    }

    @Test
    void download_shouldUseConfigsLinkedConnector_withoutConsultingOwnerLookup() {
        config.setConnector(connector);
        when(restTemplate.exchange(eq("https://graph.facebook.com/v21.0/media-123"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("url", "https://cdn.example.com/blob")));
        byte[] fileBytes = { 5, 6, 7 };
        when(restTemplate.exchange(eq("https://cdn.example.com/blob"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(fileBytes));

        Optional<String> stored = service.download(config, "media-123");

        assertTrue(stored.isPresent());
        verify(smsConnectorRepository, never()).findByCreatedByAndProviderAndActiveTrue(any(), any());
    }

    @Test
    void download_shouldReturnEmpty_whenOwnerHasNoActiveWhatsAppConnector() {
        when(smsConnectorRepository.findByCreatedByAndProviderAndActiveTrue(owner, "WHATSAPP"))
                .thenReturn(Optional.empty());

        Optional<String> stored = service.download(config, "media-123");

        assertEquals(Optional.empty(), stored);
    }

    @Test
    void download_shouldReturnEmpty_whenMetadataHasNoUrl() {
        when(smsConnectorRepository.findByCreatedByAndProviderAndActiveTrue(owner, "WHATSAPP"))
                .thenReturn(Optional.of(connector));
        when(restTemplate.exchange(eq("https://graph.facebook.com/v21.0/media-123"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of()));

        Optional<String> stored = service.download(config, "media-123");

        assertEquals(Optional.empty(), stored);
    }

    @Test
    void download_shouldReturnEmpty_whenHttpCallThrows() {
        when(smsConnectorRepository.findByCreatedByAndProviderAndActiveTrue(owner, "WHATSAPP"))
                .thenReturn(Optional.of(connector));
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("network error"));

        Optional<String> stored = service.download(config, "media-123");

        assertEquals(Optional.empty(), stored);
    }

    @Test
    void read_shouldReturnStoredBytes() {
        ReflectionTestUtils.setField(service, "mediaDirectory", tempDir.toString());
        writeFile(tempDir.resolve("some-file"), new byte[] { 9, 8, 7 });

        Optional<byte[]> bytes = service.read("some-file");

        assertTrue(bytes.isPresent());
        assertArrayEquals(new byte[] { 9, 8, 7 }, bytes.get());
    }

    @Test
    void read_shouldReturnEmpty_whenFileDoesNotExist() {
        Optional<byte[]> bytes = service.read("missing-file");

        assertEquals(Optional.empty(), bytes);
    }

    private byte[] readAllBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeFile(Path path, byte[] content) {
        try {
            Files.write(path, content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
