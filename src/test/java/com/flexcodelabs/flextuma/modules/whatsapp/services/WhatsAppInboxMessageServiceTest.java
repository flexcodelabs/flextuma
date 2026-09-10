package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.auth.Organisation;
import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppWebhookConfig;
import com.flexcodelabs.flextuma.core.helpers.CurrentUserResolver;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppInboxMessageRepository;
import com.flexcodelabs.flextuma.core.senders.WhatsAppSender;
import com.flexcodelabs.flextuma.modules.sms.services.SmsLogService;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppConversationDTO;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppTenantStorageUsageDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppInboxMessageServiceTest {

    @Mock
    private WhatsAppInboxMessageRepository repository;

    @Mock
    private WhatsAppMediaService mediaService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private SmsLogService smsLogService;

    @Mock
    private WhatsAppSender whatsAppSender;

    @InjectMocks
    private WhatsAppInboxMessageService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String... authorities) {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        List<GrantedAuthority> granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        doReturn(granted).when(auth).getAuthorities();
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    private WhatsAppInboxMessage message(WhatsAppWebhookConfig config, String from, String content, LocalDateTime receivedAt, boolean read) {
        WhatsAppInboxMessage message = new WhatsAppInboxMessage();
        message.setId(UUID.randomUUID());
        message.setConfig(config);
        message.setFromNumber(from);
        message.setContent(content);
        message.setReceivedAt(receivedAt);
        message.setMessageType("text");
        message.setProviderMessageId(UUID.randomUUID().toString());
        if (read) message.setReadAt(LocalDateTime.now());
        return message;
    }

    @Test
    void listConversations_shouldGroupByConfigAndFromNumber_andCountUnread() {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());
        config.setPhoneNumberId("104725069208652");

        LocalDateTime now = LocalDateTime.now();
        List<WhatsAppInboxMessage> messages = List.of(
                message(config, "255700000001", "Latest from Ada", now, false),
                message(config, "255700000001", "Older from Ada", now.minusMinutes(5), false),
                message(config, "255700000002", "Hi from Ben", now.minusMinutes(1), true));

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(messages));

        Pagination<WhatsAppConversationDTO> result = service.listConversations(0, 25);

        assertEquals(2, result.getTotal());
        WhatsAppConversationDTO adaConversation = result.getData().stream()
                .filter(c -> c.fromNumber().equals("255700000001")).findFirst().orElseThrow();
        assertEquals("Latest from Ada", adaConversation.lastMessageContent());
        assertEquals(2, adaConversation.unreadCount());
        assertEquals("104725069208652", adaConversation.phoneNumberId());

        WhatsAppConversationDTO benConversation = result.getData().stream()
                .filter(c -> c.fromNumber().equals("255700000002")).findFirst().orElseThrow();
        assertEquals(0, benConversation.unreadCount());
    }

    @Test
    void listConversations_shouldReflectOutboundReply_sentAfterLastInboundMessage() {
        SmsConnector connector = new SmsConnector();
        connector.setId(UUID.randomUUID());

        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());
        config.setPhoneNumberId("104725069208652");
        config.setConnector(connector);

        LocalDateTime inboundAt = LocalDateTime.now().minusHours(2);
        WhatsAppInboxMessage inbound = message(config, "255655392445", "whatup", inboundAt, false);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inbound)));
        when(mediaService.resolveConnector(config)).thenReturn(connector);

        SmsLog outboundReply = new SmsLog();
        outboundReply.setRecipient("255655392445");
        outboundReply.setContent("Testing.....");
        outboundReply.setConnector(connector);
        outboundReply.setCreated(inboundAt.plusHours(2));

        when(smsLogService.findAllPaginated(any(Pageable.class), any(), any(), any()))
                .thenReturn(Pagination.<SmsLog>builder().data(List.of(outboundReply)).build());

        Pagination<WhatsAppConversationDTO> result = service.listConversations(0, 25);

        WhatsAppConversationDTO conversation = result.getData().get(0);
        assertEquals("Testing.....", conversation.lastMessageContent());
        assertEquals(inboundAt.plusHours(2), conversation.lastMessageAt());
        // The unread count still reflects the unread inbound message; the outbound reply itself
        // isn't something the agent can leave "unread".
        assertEquals(1, conversation.unreadCount());
    }

    @Test
    void listConversations_shouldReflectOutboundReply_whenConfigHasNoConnectorLinkedDirectly() {
        // WhatsAppWebhookConfig.connector is optional; resolveConnector() falls back to the
        // owner's first active WhatsApp connector. Outbound-activity matching must go through
        // that same fallback, not require an explicit link, or replies from tenants who never
        // set it never update the list.
        SmsConnector connector = new SmsConnector();
        connector.setId(UUID.randomUUID());

        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());
        config.setPhoneNumberId("104725069208652");
        // Deliberately left null: config.setConnector(...) is not called.

        LocalDateTime inboundAt = LocalDateTime.now().minusHours(2);
        WhatsAppInboxMessage inbound = message(config, "255655392445", "whatup", inboundAt, false);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inbound)));
        when(mediaService.resolveConnector(config)).thenReturn(connector);

        SmsLog outboundReply = new SmsLog();
        outboundReply.setRecipient("255655392445");
        outboundReply.setContent("I see");
        outboundReply.setConnector(connector);
        outboundReply.setCreated(inboundAt.plusHours(2));

        when(smsLogService.findAllPaginated(any(Pageable.class), any(), any(), any()))
                .thenReturn(Pagination.<SmsLog>builder().data(List.of(outboundReply)).build());

        Pagination<WhatsAppConversationDTO> result = service.listConversations(0, 25);

        WhatsAppConversationDTO conversation = result.getData().get(0);
        assertEquals("I see", conversation.lastMessageContent());
        assertEquals(inboundAt.plusHours(2), conversation.lastMessageAt());
    }

    @Test
    void listConversations_shouldExposeTypeAndNullContent_whenMediaMessageHasNoCaption() {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());

        WhatsAppInboxMessage imageMessage = message(config, "255700000001", null, LocalDateTime.now(), false);
        imageMessage.setMessageType("image");

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(imageMessage)));

        Pagination<WhatsAppConversationDTO> result = service.listConversations(0, 25);

        assertEquals(null, result.getData().get(0).lastMessageContent());
        assertEquals("image", result.getData().get(0).lastMessageType());
    }

    @Test
    void listConversations_shouldPaginate() {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());

        LocalDateTime now = LocalDateTime.now();
        List<WhatsAppInboxMessage> messages = List.of(
                message(config, "255700000001", "A", now, true),
                message(config, "255700000002", "B", now.minusMinutes(1), true),
                message(config, "255700000003", "C", now.minusMinutes(2), true));

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(messages));

        Pagination<WhatsAppConversationDTO> firstPage = service.listConversations(0, 2);
        assertEquals(3, firstPage.getTotal());
        assertEquals(2, firstPage.getData().size());

        Pagination<WhatsAppConversationDTO> secondPage = service.listConversations(1, 2);
        assertEquals(1, secondPage.getData().size());
    }

    @Test
    void getMedia_shouldReturnBytesAndMimeType_whenMediaStored() {
        WhatsAppInboxMessage message = message(new WhatsAppWebhookConfig(), "255700000001", null, LocalDateTime.now(), false);
        message.setMediaId("wamid.stored");
        message.setMediaPath("stored-filename");
        message.setMimeType("image/jpeg");
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(message));
        when(mediaService.read("stored-filename")).thenReturn(Optional.of(new byte[] { 1, 2, 3 }));

        WhatsAppInboxMessageService.MediaContent media = service.getMedia(message.getId());

        assertArrayEquals(new byte[] { 1, 2, 3 }, media.bytes());
        assertEquals("image/jpeg", media.mimeType());
    }

    @Test
    void getMedia_shouldThrowNotFound_whenMessageHasNoMedia() {
        WhatsAppInboxMessage message = message(new WhatsAppWebhookConfig(), "255700000001", "Hello", LocalDateTime.now(), false);
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(message));

        assertThrows(ResponseStatusException.class, () -> service.getMedia(message.getId()));
    }

    @Test
    void getMedia_shouldThrowNotFound_whenStoredFileIsMissing() {
        WhatsAppInboxMessage message = message(new WhatsAppWebhookConfig(), "255700000001", null, LocalDateTime.now(), false);
        message.setMediaId("wamid.stored");
        message.setMediaPath("stored-filename");
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(message));
        when(mediaService.read("stored-filename")).thenReturn(Optional.empty());
        // getMedia() retries the download when the cached file is missing; stub it to also fail
        // so this stays a true "media unavailable" case rather than exercising the retry's happy path.
        when(mediaService.download(message.getConfig(), "wamid.stored")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.getMedia(message.getId()));
    }

    @Test
    void markAsRead_shouldSendReadReceiptToMeta_whenConnectorResolves() {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());
        SmsConnector connector = new SmsConnector();
        connector.setKey("meta-token");
        connector.setUrl("https://graph.facebook.com/v22.0");

        WhatsAppInboxMessage message = message(config, "255700000001", "Hello", LocalDateTime.now(), false);
        message.setProviderMessageId("wamid.abc123");
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(message));
        when(repository.save(any(WhatsAppInboxMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mediaService.resolveConnector(config)).thenReturn(connector);

        service.markAsRead(message.getId());

        verify(whatsAppSender).markAsRead(connector, "wamid.abc123");
    }

    @Test
    void markAsRead_shouldNotSendReadReceiptAgain_whenAlreadyRead() {
        WhatsAppInboxMessage message = message(new WhatsAppWebhookConfig(), "255700000001", "Hello", LocalDateTime.now(), true);
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(message));

        service.markAsRead(message.getId());

        verify(whatsAppSender, never()).markAsRead(any(), any());
    }

    @Test
    void markAsRead_shouldSkipReadReceipt_whenNoConnectorResolves() {
        WhatsAppWebhookConfig config = new WhatsAppWebhookConfig();
        config.setId(UUID.randomUUID());
        WhatsAppInboxMessage message = message(config, "255700000001", "Hello", LocalDateTime.now(), false);
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(message));
        when(repository.save(any(WhatsAppInboxMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mediaService.resolveConnector(config)).thenReturn(null);

        WhatsAppInboxMessage result = service.markAsRead(message.getId());

        assertNotNull(result.getReadAt());
        verify(whatsAppSender, never()).markAsRead(any(), any());
    }

    @Test
    void listConversations_shouldReturnEmptyPage_whenNoMessages() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Pagination<WhatsAppConversationDTO> result = service.listConversations(0, 25);

        assertEquals(0, result.getTotal());
        assertEquals(0, result.getData().size());
    }

    @Test
    void storageUsage_shouldReturnFullTenantBreakdown_forSuperAdmin() {
        authenticateAs("SUPER_ADMIN");
        List<WhatsAppTenantStorageUsageDTO> breakdown = List.of(
                WhatsAppTenantStorageUsageDTO.builder().tenantId(UUID.randomUUID()).tenantLabel("Acme")
                        .totalBytes(2048L).mediaCount(3L).build());
        when(repository.findStorageUsageByTenant()).thenReturn(breakdown);

        List<WhatsAppTenantStorageUsageDTO> result = service.storageUsage();

        assertEquals(breakdown, result);
    }

    @Test
    void storageUsage_shouldScopeByOrganisation_forNonAdminOrgMember() {
        authenticateAs("USER");
        Organisation organisation = new Organisation();
        organisation.setId(UUID.randomUUID());
        organisation.setName("Acme");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setOrganisation(organisation);
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(repository.sumMediaStorageBytes(organisation, user)).thenReturn(4096L);
        when(repository.countMediaForTenant(organisation, user)).thenReturn(5L);

        List<WhatsAppTenantStorageUsageDTO> result = service.storageUsage();

        assertEquals(1, result.size());
        assertEquals(organisation.getId(), result.get(0).tenantId());
        assertEquals("Acme", result.get(0).tenantLabel());
        assertEquals(4096L, result.get(0).totalBytes());
        assertEquals(5L, result.get(0).mediaCount());
    }

    @Test
    void storageUsage_shouldFallBackToUser_forNonAdminWithoutOrganisation() {
        authenticateAs("USER");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("solo");
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.of(user));
        when(repository.sumMediaStorageBytes(null, user)).thenReturn(512L);
        when(repository.countMediaForTenant(null, user)).thenReturn(1L);

        List<WhatsAppTenantStorageUsageDTO> result = service.storageUsage();

        assertEquals(1, result.size());
        assertEquals(user.getId(), result.get(0).tenantId());
        assertEquals("solo", result.get(0).tenantLabel());
        assertEquals(512L, result.get(0).totalBytes());
    }

    @Test
    void storageUsage_shouldThrowUnauthorized_whenNoCurrentUser() {
        authenticateAs("USER");
        when(currentUserResolver.getCurrentUser()).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.storageUsage());
    }
}
