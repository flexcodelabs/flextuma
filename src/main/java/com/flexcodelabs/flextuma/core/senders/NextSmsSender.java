package com.flexcodelabs.flextuma.core.senders;

import org.springframework.stereotype.Service;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.services.SmsSender;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NextSmsSender implements SmsSender {
    @Override
    public String getProvider() {
        return "NEXT";
    }

    @Override
    public String sendSms(SmsConnector config, String to, String message) {

        log.info("NEXT SMS  SENDER: Sending SMS to {} with message: {}", to, message);

        return "Message sent to " + to + " with content: " + message;
    }
}