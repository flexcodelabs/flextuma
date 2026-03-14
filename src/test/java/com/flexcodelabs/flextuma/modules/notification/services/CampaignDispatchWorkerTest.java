package com.flexcodelabs.flextuma.modules.notification.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.sms.SmsCampaign;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.core.entities.sms.SmsConnector;
import com.flexcodelabs.flextuma.core.enums.SmsCampaignStatus;
import com.flexcodelabs.flextuma.core.helpers.SmsSegmentCalculator;
import com.flexcodelabs.flextuma.core.helpers.SmsSegmentResult;
import com.flexcodelabs.flextuma.core.repositories.SmsCampaignRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.modules.finance.services.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignDispatchWorkerTest {

    @Mock
    private SmsCampaignRepository campaignRepository;

    @Mock
    private SmsLogRepository logRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private SmsSegmentCalculator segmentCalculator;

    @InjectMocks
    private CampaignDispatchWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "pricePerSegment", new BigDecimal("1.5"));
    }

    @Test
    void processCampaigns_whenNoCampaignsDue_shouldDoNothing() {
        when(campaignRepository.findDueCampaigns(eq(SmsCampaignStatus.SCHEDULED), any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        worker.processCampaigns();

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void processCampaigns_withDueCampaigns_shouldProcessThem() {
        SmsCampaign campaign = new SmsCampaign();
        campaign.setName("Test Campaign");
        campaign.setRecipients("255700112233, 255700445566");
        SmsTemplate template = new SmsTemplate();
        template.setContent("Hello world");
        campaign.setTemplate(template);
        SmsConnector connector = new SmsConnector();
        campaign.setConnector(connector);
        User adminUser = new User();
        adminUser.setUsername("admin");
        campaign.setCreatedBy(adminUser);

        when(campaignRepository.findDueCampaigns(eq(SmsCampaignStatus.SCHEDULED), any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(List.of(campaign));

        when(segmentCalculator.calculate(any())).thenReturn(new SmsSegmentResult(1, true, 11, 149));

        worker.processCampaigns();

        verify(campaignRepository, atLeastOnce()).save(campaign);
        verify(walletService, times(2)).debit(eq(adminUser), any(BigDecimal.class), anyString(), any());
        verify(logRepository, times(2)).save(any(SmsLog.class));
        assert (campaign.getStatus() == SmsCampaignStatus.COMPLETED);
    }

    @Test
    void processCampaigns_withEmptyRecipients_shouldCompleteImmediately() {
        SmsCampaign campaign = new SmsCampaign();
        campaign.setRecipients("");

        when(campaignRepository.findDueCampaigns(eq(SmsCampaignStatus.SCHEDULED), any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(List.of(campaign));

        worker.processCampaigns();

        verify(campaignRepository, atLeastOnce()).save(campaign);
        assert (campaign.getStatus() == SmsCampaignStatus.COMPLETED);
        verify(logRepository, never()).save(any());
    }

    @Test
    void processCampaigns_whenDebitFails_shouldContinueProcessing() {
        SmsCampaign campaign = new SmsCampaign();
        campaign.setRecipients("255700112233");
        SmsTemplate template = new SmsTemplate();
        template.setContent("Hello");
        campaign.setTemplate(template);
        SmsConnector connector = new SmsConnector();
        campaign.setConnector(connector);
        User user1 = new User();
        user1.setUsername("user1");
        campaign.setCreatedBy(user1);

        when(campaignRepository.findDueCampaigns(eq(SmsCampaignStatus.SCHEDULED), any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(List.of(campaign));
        when(segmentCalculator.calculate(any())).thenReturn(new SmsSegmentResult(1, true, 5, 155));

        doThrow(new RuntimeException("InSufficient Funds")).when(walletService).debit(any(), any(), any(), any());

        worker.processCampaigns();

        verify(walletService).debit(any(), any(), any(), any());
        verify(logRepository, never()).save(any());
        assert (campaign.getStatus() == SmsCampaignStatus.COMPLETED);
    }
}
