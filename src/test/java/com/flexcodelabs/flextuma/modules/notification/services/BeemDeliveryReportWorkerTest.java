package com.flexcodelabs.flextuma.modules.notification.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.senders.BeemDeliveryReportClient;

@ExtendWith(MockitoExtension.class)
class BeemDeliveryReportWorkerTest {

    @Mock
    private SmsLogRepository logRepository;

    @Mock
    private BeemDeliveryReportClient deliveryReportClient;

    @InjectMocks
    private BeemDeliveryReportWorker worker;

    @Test
    void pollDeliveryReports_shouldMarkDeliveredBeemMessage() {
        SmsConnector connector = new SmsConnector();
        connector.setProvider("BEEM");
        SmsLog smsLog = new SmsLog();
        smsLog.setConnector(connector);
        smsLog.setRecipient("255700000001");
        smsLog.setProviderMessageId("31951");
        smsLog.setStatus(SmsLogStatus.SENT);
        smsLog.setCreated(LocalDateTime.now().minusMinutes(6));
        BeemDeliveryReportClient.BeemDeliveryStatus report = new BeemDeliveryReportClient.BeemDeliveryStatus();
        report.setStatus("DELIVERED");

        when(logRepository.findTop50ByStatusAndProviderMessageIdIsNotNullOrderByCreatedAsc(SmsLogStatus.SENT))
                .thenReturn(List.of(smsLog));
        when(deliveryReportClient.lookup(connector, "255700000001", "31951")).thenReturn(report);

        worker.pollDeliveryReports();

        assertEquals(SmsLogStatus.DELIVERED, smsLog.getStatus());
        verify(logRepository).save(smsLog);
    }
}
