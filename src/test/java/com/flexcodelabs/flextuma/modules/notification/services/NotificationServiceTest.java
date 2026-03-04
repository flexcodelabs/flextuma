package com.flexcodelabs.flextuma.modules.notification.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsTemplateRepository;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;
import com.flexcodelabs.flextuma.modules.finance.services.WalletService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private SmsTemplateRepository templateRepository;

    @Mock
    private SmsLogRepository logRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SmsConnectorRepository connectorRepository;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private NotificationService notificationService;

    @Captor
    private ArgumentCaptor<SmsLog> smsLogCaptor;

    private User testUser;
    private Map<String, String> validPlaceholders;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUsername("testuser");

        validPlaceholders = new HashMap<>();
        validPlaceholders.put("provider", "Twilio");
        validPlaceholders.put("templateCode", "WELCOME");
        validPlaceholders.put("phoneNumber", "+1234567890");
        validPlaceholders.put("name", "John Doe"); // Custom placeholder

        ReflectionTestUtils.setField(notificationService, "pricePerSegment", BigDecimal.valueOf(20.0));
    }

    @Test
    void queueTemplatedSms_shouldThrowWhenUsernameIsNull() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.queueTemplatedSms(validPlaceholders, null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("User not authenticated"));
    }

    @Test
    void queueTemplatedSms_shouldThrowWhenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.queueTemplatedSms(validPlaceholders, "unknown"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("User not found"));
    }

    @ParameterizedTest
    @CsvSource({
            "provider, SMS provider is missing",
            "templateCode, Template is missing",
            "phoneNumber, Phone number is missing"
    })
    void queueTemplatedSms_shouldThrowWhenRequiredPlaceholderMissing(String missingKey, String expectedMessage) {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        validPlaceholders.remove(missingKey);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.queueTemplatedSms(validPlaceholders, "testuser"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains(expectedMessage));
    }

    @Test
    void queueTemplatedSms_shouldThrowWhenTemplateNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(templateRepository.findByCreatedByAndCode(testUser, "WELCOME")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.queueTemplatedSms(validPlaceholders, "testuser"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Template not found or you don't have access to it"));
    }

    @Test
    void queueTemplatedSms_shouldThrowWhenConnectorNotFound() {
        SmsTemplate template = new SmsTemplate();
        template.setContent("Hello {{name}}");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(templateRepository.findByCreatedByAndCode(testUser, "WELCOME")).thenReturn(Optional.of(template));
        when(connectorRepository.findByCreatedByAndProviderAndActiveTrue(testUser, "Twilio"))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.queueTemplatedSms(validPlaceholders, "testuser"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("No active SMS connector found"));
    }

    @Test
    void queueTemplatedSms_shouldQueueSmsSuccessfully() {
        SmsTemplate template = new SmsTemplate();
        template.setContent("Hello {{name}}");

        SmsConnector connector = new SmsConnector();
        connector.setProvider("Twilio");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(templateRepository.findByCreatedByAndCode(testUser, "WELCOME")).thenReturn(Optional.of(template));
        when(connectorRepository.findByCreatedByAndProviderAndActiveTrue(testUser, "Twilio"))
                .thenReturn(Optional.of(connector));

        SmsLog expectedSavedLog = new SmsLog();
        expectedSavedLog.setStatus(SmsLogStatus.PENDING);
        when(logRepository.save(any(SmsLog.class))).thenReturn(expectedSavedLog);

        SmsLog result = notificationService.queueTemplatedSms(validPlaceholders, "testuser");

        verify(logRepository).save(smsLogCaptor.capture());
        SmsLog capturedLog = smsLogCaptor.getValue();

        assertEquals("+1234567890", capturedLog.getRecipient());
        assertEquals("Hello John Doe", capturedLog.getContent());
        assertEquals(template, capturedLog.getTemplate());
        assertEquals(connector, capturedLog.getConnector());
        assertEquals(SmsLogStatus.PENDING, capturedLog.getStatus());

        assertNotNull(result);
    }
}
