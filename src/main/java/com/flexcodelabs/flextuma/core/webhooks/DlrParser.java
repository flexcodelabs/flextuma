package com.flexcodelabs.flextuma.core.webhooks;

import java.util.Map;

/**
 * Parses a raw DLR (Delivery Report) payload from an SMS provider into a
 * normalised {@link DlrResult}.
 *
 * <p>
 * Each provider sends a different JSON shape — implement this interface
 * once per provider, annotate with {@code @Component}, and the
 * {@link com.flexcodelabs.flextuma.modules.webhook.controllers.SmsWebhookController}
 * will automatically pick it up.
 */
public interface DlrParser {

    /**
     * Provider key, must match {@code SmsConnector.provider} (case-insensitive).
     */
    String getProvider();

    /**
     * Parses the raw payload map into a normalised result.
     *
     * @param payload the deserialized JSON body from the provider's DLR callback
     * @return a {@link DlrResult} with the message ID and normalised status
     */
    DlrResult parse(Map<String, Object> payload);
}
