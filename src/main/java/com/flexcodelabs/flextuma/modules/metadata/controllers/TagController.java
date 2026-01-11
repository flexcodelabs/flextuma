package com.flexcodelabs.flextuma.modules.metadata.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.metadata.Tag;
import com.flexcodelabs.flextuma.modules.metadata.services.TagService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/" + Tag.PLURAL)
public class TagController extends BaseController<Tag, TagService> {

	public TagController(TagService service) {
		super(service);
	}
}
