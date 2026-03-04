package com.flexcodelabs.flextuma.modules.sms.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.sms.SmsCampaign;
import com.flexcodelabs.flextuma.modules.sms.services.SmsCampaignService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/" + SmsCampaign.PLURAL)
public class SmsCampaignController extends BaseController<SmsCampaign, SmsCampaignService> {

    public SmsCampaignController(SmsCampaignService service) {
        super(service);
    }
}
