package com.flexcodelabs.flextuma.core.services;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;

public interface SmsSender {
    String getProvider();

    String sendSms(SmsConnector config, String to, String message);
}