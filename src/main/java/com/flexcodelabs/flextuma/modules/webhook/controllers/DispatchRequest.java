package com.flexcodelabs.flextuma.modules.webhook.controllers;

import lombok.Data;
import java.util.Map;

@Data
public class DispatchRequest {
    private String templateCode;
    private String content;
    private String provider;
    private Map<String, String> filterQuery;
}
