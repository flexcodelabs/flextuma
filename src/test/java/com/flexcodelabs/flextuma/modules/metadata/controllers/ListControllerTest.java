package com.flexcodelabs.flextuma.modules.metadata.controllers;

import org.mockito.Mock;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.controllers.BaseControllerTest;
import com.flexcodelabs.flextuma.core.entities.metadata.ListEntity;
import com.flexcodelabs.flextuma.modules.metadata.services.ListService;

public class ListControllerTest extends BaseControllerTest<ListEntity, ListService> {

    @Mock
    private ListService service;

    private ListController controller;

    @Override
    protected BaseController<ListEntity, ListService> getController() {
        if (controller == null) {
            controller = new ListController(service);
        }
        return controller;
    }

    @Override
    protected ListService getService() {
        return service;
    }

    @Override
    protected ListEntity createEntity() {
        ListEntity list = new ListEntity();
        list.setName("Test List");
        return list;
    }

    @Override
    protected String getBaseUrl() {
        return "/api/lists";
    }
}
