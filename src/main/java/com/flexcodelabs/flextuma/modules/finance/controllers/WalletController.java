package com.flexcodelabs.flextuma.modules.finance.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flexcodelabs.flextuma.core.controllers.BaseController;
import com.flexcodelabs.flextuma.core.entities.finance.Wallet;
import com.flexcodelabs.flextuma.modules.finance.services.WalletService;

@RestController
@RequestMapping("/api/" + Wallet.PLURAL)
public class WalletController extends BaseController<Wallet, WalletService> {

	public WalletController(WalletService service) {
		super(service);
	}
}
