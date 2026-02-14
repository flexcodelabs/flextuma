package com.flexcodelabs.flextuma.modules.metadata.controllers;

import org.mockito.Mock;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.controllers.BaseControllerTest;
import com.flexcodelabs.flextuma.core.entities.metadata.Tag;
import com.flexcodelabs.flextuma.modules.metadata.services.TagService;

public class TagControllerTest extends BaseControllerTest<Tag, TagService> {

    @Mock
    private TagService service;

    private TagController controller;

    @Override
    protected BaseController<Tag, TagService> getController() {
        if (controller == null) {
            controller = new TagController(service);
        }
        return controller;
    }

    @Override
    protected TagService getService() {
        return service;
    }

    @Override
    protected Tag createEntity() {
        Tag tag = new Tag();
        tag.setName("Test Tag");
        return tag;
    }

    @Override
    protected String getBaseUrl() {
        return "/api/tags";
    }
}
