package com.flexcodelabs.flextuma.modules.whatsapp.services.templates;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.modules.whatsapp.services.meta.MetaGraphClient;
import com.flexcodelabs.flextuma.modules.whatsapp.services.webhook.WhatsAppPayloads;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads every template of a WhatsApp Business Account from Meta, following cursor pagination. */
@Component
@RequiredArgsConstructor
public class WhatsAppTemplateFetcher {
    private static final int MAX_PAGES = 100;

    private final MetaGraphClient graph;

    /** Throws MetaGraphException with a user-facing message when Meta rejects the request. */
    public List<Map<String, Object>> fetchAll(SmsConnector connector, String businessAccountId) {
        String basePath = "/" + businessAccountId + "/message_templates?limit=100";
        List<Map<String, Object>> all = new ArrayList<>();
        String after = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, Object> body = graph.get(connector.getUrl(), connector.getKey(), after == null ? basePath : basePath + "&after=" + after);
            all.addAll(WhatsAppPayloads.mapList(body, "data"));
            after = nextCursor(body);
            if (after == null) {
                break;
            }
        }
        return all;
    }

    /** Meta only includes paging.next when another page exists; its cursor is reused against the
     * validated base URL rather than following the absolute URL Meta returns. */
    private static String nextCursor(Map<String, Object> body) {
        Map<String, Object> paging = WhatsAppPayloads.nestedMap(body, "paging");
        if (paging.get("next") == null) {
            return null;
        }
        return WhatsAppPayloads.text(WhatsAppPayloads.nestedMap(paging, "cursors").get("after"))
                .map(cursor -> URLEncoder.encode(cursor, StandardCharsets.UTF_8)).orElse(null);
    }
}
