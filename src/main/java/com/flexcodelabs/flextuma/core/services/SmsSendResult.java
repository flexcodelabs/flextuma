package com.flexcodelabs.flextuma.core.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmsSendResult {
    private boolean success;
    private String message;
    private String providerMessageId;
    private Map<String, Object> providerResponse;
    private String errorCode;

    public static SmsSendResult success(String message, String providerMessageId,
            Map<String, Object> providerResponse) {
        return SmsSendResult.builder()
                .success(true)
                .message(message)
                .providerMessageId(providerMessageId)
                .providerResponse(providerResponse)
                .build();
    }

    public static SmsSendResult failure(String message, String errorCode, Map<String, Object> providerResponse) {
        return SmsSendResult.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .providerResponse(providerResponse)
                .build();
    }
}
