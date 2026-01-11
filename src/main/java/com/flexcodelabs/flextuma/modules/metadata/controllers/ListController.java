package com.flexcodelabs.flextuma.modules.metadata.controllers;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.metadata.ListEntity;
import com.flexcodelabs.flextuma.modules.metadata.services.ListService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/" + ListEntity.PLURAL)
public class ListController extends BaseController<ListEntity, ListService> {

	public ListController(ListService service) {
		super(service);
	}
}
