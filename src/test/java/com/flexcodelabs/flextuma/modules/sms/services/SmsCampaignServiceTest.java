package com.flexcodelabs.flextuma.modules.sms.services;

import com.flexcodelabs.flextuma.core.entities.sms.SmsCampaign;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.core.enums.SmsTemplateStatus;
import com.flexcodelabs.flextuma.core.repositories.SmsCampaignRepository;
import com.flexcodelabs.flextuma.core.repositories.SmsTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsCampaignServiceTest {

    @Mock
    private SmsCampaignRepository repository;

    @Mock
    private SmsTemplateRepository templateRepository;

    @InjectMocks
    private SmsCampaignService smsCampaignService;

    @Test
    void onPreSave_shouldRejectInactiveTemplate() {
        UUID templateId = UUID.randomUUID();
        SmsTemplate templateStub = new SmsTemplate();
        templateStub.setId(templateId);

        SmsTemplate persistedTemplate = new SmsTemplate();
        persistedTemplate.setId(templateId);
        persistedTemplate.setStatus(SmsTemplateStatus.INACTIVE);

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(persistedTemplate));

        SmsCampaign campaign = new SmsCampaign();
        campaign.setTemplate(templateStub);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> smsCampaignService.onPreSave(campaign));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void onPreSave_shouldAllowActiveTemplate() {
        UUID templateId = UUID.randomUUID();
        SmsTemplate templateStub = new SmsTemplate();
        templateStub.setId(templateId);

        SmsTemplate persistedTemplate = new SmsTemplate();
        persistedTemplate.setId(templateId);
        persistedTemplate.setStatus(SmsTemplateStatus.ACTIVE);

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(persistedTemplate));

        SmsCampaign campaign = new SmsCampaign();
        campaign.setTemplate(templateStub);

        smsCampaignService.onPreSave(campaign);
    }

    @Test
    void onPreSave_shouldAllowMissingTemplate() {
        SmsCampaign campaign = new SmsCampaign();

        smsCampaignService.onPreSave(campaign);
    }
}
