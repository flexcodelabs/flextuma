package com.flexcodelabs.flextuma.modules.sms.controllers;

public record PreviewResponse(String renderedContent, int segmentCount, String encoding, int charactersRemaining) {
}
