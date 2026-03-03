package com.flexcodelabs.flextuma.modules.feature.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseControllerTest;
import com.flexcodelabs.flextuma.core.entities.feature.TenantFeature;
import com.flexcodelabs.flextuma.modules.feature.services.TenantFeatureService;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantFeatureControllerTest extends BaseControllerTest<TenantFeature, TenantFeatureService> {

    @Mock
    private TenantFeatureService service;

    @Override
    protected TenantFeatureController getController() {
        return new TenantFeatureController(service);
    }

    @Override
    protected TenantFeatureService getService() {
        return service;
    }

    @Override
    protected TenantFeature createEntity() {
        TenantFeature feature = new TenantFeature();
        feature.setFeatureKey("BULK_CAMPAIGN");
        feature.setEnabled(true);
        return feature;
    }

    @Override
    protected String getBaseUrl() {
        return "/api/" + TenantFeature.PLURAL;
    }
}
