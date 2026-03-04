package com.flexcodelabs.flextuma.modules.sms.controllers;

import java.util.Map;

public record PreviewRequest(String template, Map<String, String> variables) {
}
