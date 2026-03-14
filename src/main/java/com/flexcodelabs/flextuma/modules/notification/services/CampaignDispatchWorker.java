package com.flexcodelabs.flextuma.modules.notification.services;

import com.flexcodelabs.flextuma.core.entities.sms.SmsCampaign;
import com.flexcodelabs.flextuma.core.entities.sms.SmsLog;
import com.flexcodelabs.flextuma.core.enums.SmsCampaignStatus;
import com.flexcodelabs.flextuma.core.enums.SmsLogStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsCampaignRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsLogRepository;
import com.flexcodelabs.flextuma.core.helpers.SmsSegmentCalculator;
import com.flexcodelabs.flextuma.core.helpers.SmsSegmentResult;
import com.flexcodelabs.flextuma.modules.finance.services.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignDispatchWorker {

    private final SmsCampaignRepository campaignRepository;
    private final SmsLogRepository logRepository;
    private final WalletService walletService;
    private final SmsSegmentCalculator segmentCalculator;

    @Value("${flextuma.sms.price-per-segment:1.0}")
    private BigDecimal pricePerSegment;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processCampaigns() {
        List<SmsCampaign> dueCampaigns = campaignRepository.findDueCampaigns(
                SmsCampaignStatus.SCHEDULED,
                LocalDateTime.now(),
                PageRequest.of(0, 10));

        if (dueCampaigns.isEmpty()) {
            return;
        }

        log.info("CampaignDispatchWorker: Processing {} scheduled campaign(s)", dueCampaigns.size());

        for (SmsCampaign campaign : dueCampaigns) {
            processSingleCampaign(campaign);
        }
    }

    private void processSingleCampaign(SmsCampaign campaign) {
        try {
            campaign.setStatus(SmsCampaignStatus.PROCESSING);
            campaignRepository.save(campaign);

            String recipientsStr = campaign.getRecipients();
            if (recipientsStr == null || recipientsStr.isBlank()) {
                campaign.setStatus(SmsCampaignStatus.COMPLETED);
                campaignRepository.save(campaign);
                return;
            }

            String[] recipients = recipientsStr.split(",");
            log.info("Processing campaign [{}] for {} recipients", campaign.getName(), recipients.length);

            SmsSegmentResult segmentResult = segmentCalculator.calculate(campaign.getTemplate().getContent());
            BigDecimal costPerSms = pricePerSegment.multiply(BigDecimal.valueOf(segmentResult.segments()));

            for (String recipient : recipients) {
                dispatchToRecipient(campaign, recipient.trim(), costPerSms);
            }

            campaign.setStatus(SmsCampaignStatus.COMPLETED);
            campaignRepository.save(campaign);
            log.info("Campaign [{}] processing completed successfully", campaign.getName());

        } catch (Exception e) {
            log.error("Error processing campaign [{}]: {}", campaign.getName(), e.getMessage());
        }
    }

    private void dispatchToRecipient(SmsCampaign campaign, String recipient, BigDecimal cost) {
        if (recipient.isEmpty())
            return;

        SmsLog smsLog = new SmsLog();
        smsLog.setRecipient(recipient);
        smsLog.setContent(campaign.getTemplate().getContent());
        smsLog.setTemplate(campaign.getTemplate());
        smsLog.setConnector(campaign.getConnector());
        smsLog.setStatus(SmsLogStatus.PENDING);
        smsLog.setCreatedBy(campaign.getCreatedBy());

        try {
            walletService.debit(campaign.getCreatedBy(), cost, "Campaign SMS to " + recipient, null);
            logRepository.save(smsLog);
        } catch (Exception e) {
            log.error("Failed to debit wallet for campaign [{}] recipient [{}]: {}", campaign.getName(),
                    recipient, e.getMessage());
        }
    }
}
