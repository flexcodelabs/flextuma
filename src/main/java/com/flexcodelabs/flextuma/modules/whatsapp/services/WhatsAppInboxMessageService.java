package com.flexcodelabs.flextuma.modules.whatsapp.services;

import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import com.flexcodelabs.flextuma.core.repositories.WhatsAppInboxMessageRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppConversationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class WhatsAppInboxMessageService extends BaseService<WhatsAppInboxMessage> {
    private final WhatsAppInboxMessageRepository repository;
    private final WhatsAppMediaService mediaService;

    public record MediaContent(byte[] bytes, String mimeType) {}

    protected JpaRepository<WhatsAppInboxMessage, UUID> getRepository() { return repository; }
    protected JpaSpecificationExecutor<WhatsAppInboxMessage> getRepositoryAsExecutor() { return repository; }
    // Each message is tenant-scoped by BaseService, so every signed-in user reads only their own inbox.
    protected String getReadPermission() { return "ALL"; }
    protected String getAddPermission() { return "ALL"; }
    protected String getUpdatePermission() { return "ALL"; }
    protected String getDeletePermission() { return "ALL"; }
    public String getEntityPlural() { return WhatsAppInboxMessage.NAME_PLURAL; }
    protected String getEntitySingular() { return WhatsAppInboxMessage.NAME_SINGULAR; }
    public String getPropertyName() { return WhatsAppInboxMessage.PLURAL; }
    protected String getTableName() { return "whatsapp_inbox_message"; }

    @Override protected void onPreSave(WhatsAppInboxMessage entity) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Inbox messages cannot be created manually");
    }

    @Override protected WhatsAppInboxMessage onPreUpdate(WhatsAppInboxMessage newEntity, WhatsAppInboxMessage oldEntity) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Inbox messages cannot be updated manually");
    }

    public MediaContent getMedia(UUID id) {
        WhatsAppInboxMessage message = findAccessibleById(id);
        if (message.getMediaPath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This message has no stored media");
        }
        byte[] bytes = mediaService.read(message.getMediaPath())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file is no longer available"));
        return new MediaContent(bytes, message.getMimeType());
    }

    @Transactional
    public WhatsAppInboxMessage markAsRead(UUID id) {
        WhatsAppInboxMessage message = findAccessibleById(id);
        if (message.getReadAt() == null) {
            message.setReadAt(LocalDateTime.now());
            message = repository.save(message);
        }
        initializeAssociationsForResponse(message);
        return message;
    }

    private static final int CONVERSATION_SCAN_LIMIT = 2000;

    /** A media message with no caption has null content; fall back to a type label for the preview. */
    private String displayContent(WhatsAppInboxMessage message) {
        String content = message.getContent();
        return content != null && !content.isBlank() ? content : "[" + message.getMessageType() + "]";
    }

    // findAllPaginated below is called on `this`, which bypasses the Spring proxy that backs
    // BaseService's own @Transactional -- without a transaction open here, EntityResponseInitializer
    // hits a LazyInitializationException initializing WhatsAppInboxMessage.config.
    @Transactional(readOnly = true)
    public Pagination<WhatsAppConversationDTO> listConversations(int page, int pageSize) {
        List<WhatsAppInboxMessage> recent = findAllPaginated(
                PageRequest.of(0, CONVERSATION_SCAN_LIMIT, Sort.by(Sort.Direction.DESC, "receivedAt")),
                List.of(), null, "AND").getData();

        Map<String, WhatsAppConversationDTO> conversations = new LinkedHashMap<>();
        Map<String, Long> unreadCounts = new LinkedHashMap<>();
        for (WhatsAppInboxMessage message : recent) {
            String key = message.getConfig().getId() + ":" + message.getFromNumber();
            unreadCounts.merge(key, message.getReadAt() == null ? 1L : 0L, Long::sum);
            conversations.putIfAbsent(key, WhatsAppConversationDTO.builder()
                    .configId(message.getConfig().getId())
                    .phoneNumberId(message.getConfig().getPhoneNumberId())
                    .fromNumber(message.getFromNumber())
                    .contactName(message.getContactName())
                    .lastMessageContent(displayContent(message))
                    .lastMessageAt(message.getReceivedAt())
                    .build());
        }

        List<WhatsAppConversationDTO> all = conversations.entrySet().stream()
                .map(entry -> {
                    WhatsAppConversationDTO summary = entry.getValue();
                    return WhatsAppConversationDTO.builder()
                            .configId(summary.configId())
                            .phoneNumberId(summary.phoneNumberId())
                            .fromNumber(summary.fromNumber())
                            .contactName(summary.contactName())
                            .lastMessageContent(summary.lastMessageContent())
                            .lastMessageAt(summary.lastMessageAt())
                            .unreadCount(unreadCounts.getOrDefault(entry.getKey(), 0L))
                            .build();
                })
                .toList();

        List<WhatsAppConversationDTO> pageData = all.stream()
                .skip((long) page * pageSize)
                .limit(pageSize)
                .toList();

        return Pagination.<WhatsAppConversationDTO>builder()
                .page(page)
                .total(all.size())
                .pageSize(pageSize)
                .data(pageData)
                .build();
    }
}
