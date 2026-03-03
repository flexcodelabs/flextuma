package com.flexcodelabs.flextuma.modules.feature.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.feature.TenantFeature;
import com.flexcodelabs.flextuma.modules.feature.services.TenantFeatureService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/" + TenantFeature.PLURAL)
public class TenantFeatureController extends BaseController<TenantFeature, TenantFeatureService> {

    public TenantFeatureController(TenantFeatureService service) {
        super(service);
    }
}
