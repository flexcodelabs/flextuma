package com.flexcodelabs.flextuma.modules.notification.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppTemplate;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.enums.SmsTemplateStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsTemplateRepository;
import com.flexcodelabs.flextuma.core.repositories.UserRepository;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppTemplateRepository;
import com.flexcodelabs.flextuma.core.senders.WhatsAppSender;
import com.flexcodelabs.flextuma.core.services.EntityAssociationReferenceResolver;
import com.flexcodelabs.flextuma.core.services.EntityResponseInitializer;
import com.flexcodelabs.flextuma.core.services.SmsSendResult;
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
import com.flexcodelabs.flextuma.core.services.RateLimiterService;
import com.flexcodelabs.flextuma.core.helpers.SmsSegmentCalculator;
import com.flexcodelabs.flextuma.core.helpers.SmsSegmentResult;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

        @Mock
        private RateLimiterService rateLimiterService;

        @Mock
        private SmsSegmentCalculator segmentCalculator;

        @Mock
        private EntityResponseInitializer entityResponseInitializer;

        @Mock
        private EntityAssociationReferenceResolver entityAssociationReferenceResolver;

        @Mock
        private WhatsAppTemplateRepository whatsAppTemplateRepository;

        @Mock
        private WhatsAppSender whatsAppSender;

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
                testUser.setId(UUID.randomUUID());

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
                        "provider, provider is missing",
                        "templateCode, templateCode is missing",
                        "phoneNumber, phoneNumber is missing"
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
        void queueTemplatedSms_shouldThrowWhenTemplateIsInactive() {
                SmsTemplate template = new SmsTemplate();
                template.setContent("Hello {{name}}");
                template.setStatus(SmsTemplateStatus.INACTIVE);

                when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
                when(templateRepository.findByCreatedByAndCode(testUser, "WELCOME")).thenReturn(Optional.of(template));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> notificationService.queueTemplatedSms(validPlaceholders, "testuser"));

                assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
                assertTrue(ex.getReason().contains("Template is not active"));
        }

        @Test
        void queueTemplatedSms_shouldThrowWhenConnectorNotFound() {
                SmsTemplate template = new SmsTemplate();
                template.setContent("Hello {{name}}");
                template.setStatus(SmsTemplateStatus.ACTIVE);

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
                template.setStatus(SmsTemplateStatus.ACTIVE);

                SmsConnector connector = new SmsConnector();
                connector.setProvider("Twilio");

                when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
                when(templateRepository.findByCreatedByAndCode(testUser, "WELCOME")).thenReturn(Optional.of(template));
                when(connectorRepository.findByCreatedByAndProviderAndActiveTrue(testUser, "Twilio"))
                                .thenReturn(Optional.of(connector));

                SmsSegmentResult mockSegmentResult = new SmsSegmentResult(1, true, 14, 146, BigDecimal.ONE,
                                BigDecimal.ONE);
                when(segmentCalculator.calculate(anyString())).thenReturn(mockSegmentResult);

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
                verify(entityAssociationReferenceResolver).resolve(capturedLog);

                assertNotNull(result);
        }

        @Test
        void queueRawSms_shouldDebitWalletAtPerSegmentPriceForSystemConnector() {
                Map<String, String> payload = new HashMap<>();
                payload.put("provider", "BEEM");
                payload.put("phoneNumber", "+255700000000");
                payload.put("message", "System connector message");
                SmsConnector systemConnector = new SmsConnector();
                systemConnector.setProvider("BEEM");
                systemConnector.setCode("BEEM_SYSTEM");

                when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
                when(connectorRepository.findByCreatedByAndProviderAndActiveTrue(testUser, "BEEM")).thenReturn(Optional.empty());
                when(connectorRepository.findByProviderAndCode("BEEM", "BEEM_SYSTEM")).thenReturn(Optional.of(systemConnector));
                when(segmentCalculator.calculate(anyString())).thenReturn(new SmsSegmentResult(2, true, 0, 0, BigDecimal.ONE, BigDecimal.ONE));
                when(logRepository.save(any(SmsLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

                notificationService.queueRawSms(payload, "testuser");

                verify(walletService).debit(eq(testUser), argThat(amount -> amount.compareTo(BigDecimal.valueOf(40)) == 0), contains("System connector BEEM"), isNull());
        }

        @Test
        void queueRawSms_shouldRejectAnotherUsersConnectorId() {
                Map<String, String> payload = new HashMap<>();
                payload.put("provider", "BEEM");
                payload.put("phoneNumber", "+255700000000");
                payload.put("message", "Attempted misuse");
                UUID connectorId = UUID.randomUUID();
                payload.put("connectorId", connectorId.toString());
                User otherUser = new User(); otherUser.setId(UUID.randomUUID());
                SmsConnector connector = new SmsConnector(); connector.setProvider("BEEM"); connector.setCreatedBy(otherUser);

                when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
                when(connectorRepository.findByIdAndActiveTrue(connectorId)).thenReturn(Optional.of(connector));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> notificationService.queueRawSms(payload, "testuser"));
                assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
                verifyNoInteractions(walletService);
        }

        private Map<String, Object> validTemplatePayload() {
                Map<String, Object> payload = new HashMap<>();
                payload.put("phoneNumber", "+255700000000");
                payload.put("templateName", "farm_alert");
                payload.put("templateLanguage", "en");
                payload.put("components", java.util.List.of(
                                Map.of("type", "body", "parameters", java.util.List.of(Map.of("type", "text", "text", "Feed is low")))));
                return payload;
        }

        @Test
        void queueWhatsAppTemplate_shouldRejectWhenNoSyncedTemplateMatches() {
                SmsConnector connector = new SmsConnector();
                connector.setProvider("WHATSAPP");
                connector.setCreatedBy(testUser);

                when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
                when(connectorRepository.findByCreatedByAndProviderAndActiveTrue(testUser, "WHATSAPP"))
                                .thenReturn(Optional.of(connector));
                when(whatsAppTemplateRepository.findByNameAndLanguageAndConnectorAndCreatedBy("farm_alert", "en",
                                connector, testUser)).thenReturn(Optional.empty());

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> notificationService.queueWhatsAppTemplate(validTemplatePayload(), "testuser"));

                assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
                verifyNoInteractions(whatsAppSender);
        }

        @Test
        void queueWhatsAppTemplate_shouldRejectWhenTemplateNotApproved() {
                SmsConnector connector = new SmsConnector();
                connector.setProvider("WHATSAPP");
                connector.setCreatedBy(testUser);

                WhatsAppTemplate template = new WhatsAppTemplate();
                template.setStatus("PENDING");

                when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
                when(connectorRepository.findByCreatedByAndProviderAndActiveTrue(testUser, "WHATSAPP"))
                                .thenReturn(Optional.of(connector));
                when(whatsAppTemplateRepository.findByNameAndLanguageAndConnectorAndCreatedBy("farm_alert", "en",
                                connector, testUser)).thenReturn(Optional.of(template));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> notificationService.queueWhatsAppTemplate(validTemplatePayload(), "testuser"));

                assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
                assertTrue(ex.getReason().contains("not approved"));
                verifyNoInteractions(whatsAppSender);
        }

        @Test
        void queueWhatsAppTemplate_shouldSendAndSaveSentLogOnSuccess() {
                SmsConnector connector = new SmsConnector();
                connector.setProvider("WHATSAPP");
                connector.setCreatedBy(testUser);

                WhatsAppTemplate template = new WhatsAppTemplate();
                template.setStatus("APPROVED");

                when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
                when(connectorRepository.findByCreatedByAndProviderAndActiveTrue(testUser, "WHATSAPP"))
                                .thenReturn(Optional.of(connector));
                when(whatsAppTemplateRepository.findByNameAndLanguageAndConnectorAndCreatedBy("farm_alert", "en",
                                connector, testUser)).thenReturn(Optional.of(template));
                when(whatsAppSender.sendTemplate(eq(connector), eq("+255700000000"), eq("farm_alert"), eq("en"), any()))
                                .thenReturn(SmsSendResult.success("accepted", "wamid.123", Map.of("messages", "ok")));
                when(logRepository.save(any(SmsLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

                SmsLog result = notificationService.queueWhatsAppTemplate(validTemplatePayload(), "testuser");

                verify(logRepository).save(smsLogCaptor.capture());
                SmsLog capturedLog = smsLogCaptor.getValue();
                assertEquals(SmsLogStatus.SENT, capturedLog.getStatus());
                assertEquals("+255700000000", capturedLog.getRecipient());
                assertEquals("wamid.123", capturedLog.getProviderMessageId());
                assertTrue(capturedLog.getContent().contains("farm_alert/en"));
                assertTrue(capturedLog.getContent().contains("Feed is low"));
                assertNotNull(result);
                // Not a system connector, so no wallet debit is expected either way.
                verifyNoInteractions(walletService);
        }

        @Test
        void queueWhatsAppTemplate_shouldSaveFailedLogWhenSenderFails() {
                SmsConnector connector = new SmsConnector();
                connector.setProvider("WHATSAPP");
                connector.setCreatedBy(testUser);

                WhatsAppTemplate template = new WhatsAppTemplate();
                template.setStatus("APPROVED");

                when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
                when(connectorRepository.findByCreatedByAndProviderAndActiveTrue(testUser, "WHATSAPP"))
                                .thenReturn(Optional.of(connector));
                when(whatsAppTemplateRepository.findByNameAndLanguageAndConnectorAndCreatedBy("farm_alert", "en",
                                connector, testUser)).thenReturn(Optional.of(template));
                when(whatsAppSender.sendTemplate(eq(connector), eq("+255700000000"), eq("farm_alert"), eq("en"), any()))
                                .thenReturn(SmsSendResult.failure("Meta rejected the template", "131047", Map.of()));
                when(logRepository.save(any(SmsLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

                SmsLog result = notificationService.queueWhatsAppTemplate(validTemplatePayload(), "testuser");

                verify(logRepository).save(smsLogCaptor.capture());
                SmsLog capturedLog = smsLogCaptor.getValue();
                assertEquals(SmsLogStatus.FAILED, capturedLog.getStatus());
                assertEquals("Meta rejected the template", capturedLog.getError());
                assertNotNull(result);
                verifyNoInteractions(walletService);
        }
}
