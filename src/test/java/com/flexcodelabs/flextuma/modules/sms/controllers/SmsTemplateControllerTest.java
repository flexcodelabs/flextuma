package com.flexcodelabs.flextuma.modules.sms.controllers;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.controllers.BaseControllerTest;
import com.flexcodelabs.flextuma.core.entities.sms.SmsTemplate;
import com.flexcodelabs.flextuma.modules.sms.services.SmsTemplateService;
import com.flexcodelabs.flextuma.core.helpers.SmsSegmentCalculator;

import java.util.Map;

class SmsTemplateControllerTest extends BaseControllerTest<SmsTemplate, SmsTemplateService> {

    @Mock
    private SmsTemplateService service;

    private SmsTemplateController controller;

    @Override
    protected BaseController<SmsTemplate, SmsTemplateService> getController() {
        if (controller == null) {
            SmsSegmentCalculator calculator = new SmsSegmentCalculator();
            controller = new SmsTemplateController(service, calculator);
        }
        return controller;
    }

    @Override
    protected SmsTemplateService getService() {
        return service;
    }

    @Override
    protected SmsTemplate createEntity() {
        SmsTemplate template = new SmsTemplate();
        template.setName("Test Template");
        return template;
    }

    @Override
    protected String getBaseUrl() {
        return "/api/templates";
    }

    @Test
    void preview_shouldRenderContentAndCalculateSegments() throws Exception {
        PreviewRequest req = new PreviewRequest("Hello {{name}}, your code is {{code}}",
                Map.of("name", "Alice", "code", "1234"));

        mockMvc.perform(post(getBaseUrl() + "/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renderedContent").value("Hello Alice, your code is 1234"))
                .andExpect(jsonPath("$.segmentCount").value(1))
                .andExpect(jsonPath("$.encoding").value("GSM-7"))
                .andExpect(jsonPath("$.charactersRemaining").value(130));
    }
}
