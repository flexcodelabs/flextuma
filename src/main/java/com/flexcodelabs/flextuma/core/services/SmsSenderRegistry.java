package com.flexcodelabs.flextuma.core.services;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.repositories.SmsConnectorRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SmsSenderRegistry {

    private final SmsConnectorRepository connectorRepository;
    private final List<SmsSender> senders;

    public String send(String to, String message) {
        SmsConnector activeConnector = connectorRepository.findByActive(true)
                .orElseThrow(() -> new RuntimeException("No active SMS connector found in database"));

        SmsSender sender = senders.stream()
                .filter(s -> s.getProvider().equalsIgnoreCase(activeConnector.getProvider()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No implementation found for provider: " + activeConnector.getProvider()));

        return sender.sendSms(activeConnector, to, message);
    }
}