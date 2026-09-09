package com.flexcodelabs.flextuma.modules.whatsapp.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.dtos.Pagination;
import com.flexcodelabs.flextuma.core.entities.whatsapp.WhatsAppInboxMessage;
import com.flexcodelabs.flextuma.modules.whatsapp.dtos.WhatsAppConversationDTO;
import com.flexcodelabs.flextuma.modules.whatsapp.services.WhatsAppInboxMessageService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/" + WhatsAppInboxMessage.PLURAL)
public class WhatsAppInboxMessageController extends BaseController<WhatsAppInboxMessage, WhatsAppInboxMessageService> {

    public WhatsAppInboxMessageController(WhatsAppInboxMessageService service) {
        super(service);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<WhatsAppInboxMessage> markAsRead(@PathVariable UUID id) {
        return ResponseEntity.ok(service.markAsRead(id));
    }

    @GetMapping("/{id}/media")
    public ResponseEntity<byte[]> media(@PathVariable UUID id) {
        WhatsAppInboxMessageService.MediaContent media = service.getMedia(id);
        MediaType contentType = media.mimeType() != null
                ? MediaType.parseMediaType(media.mimeType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(contentType).body(media.bytes());
    }

    @GetMapping("/conversations")
    public Map<String, Object> conversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        Pagination<WhatsAppConversationDTO> result = service.listConversations(page, pageSize);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", result.getPage());
        response.put("total", result.getTotal());
        response.put("pageSize", result.getPageSize());
        response.put("conversations", result.getData());
        return response;
    }
}
