package com.flexcodelabs.flextuma.core.senders;

import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.services.SmsSender;

@Service
public class BeamSender implements SmsSender {
    @Override
    public String getProvider() {
        return "BEAM";
    }

    @Override
    public String sendSms(SmsConnector config, String to, String message) {
        return "Message sent to " + to + " with content: " + message;
    }
}