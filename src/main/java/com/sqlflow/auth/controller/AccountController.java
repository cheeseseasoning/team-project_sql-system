package com.sqlflow.auth.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sqlflow.auth.dto.AccountListItemResponse;
import com.sqlflow.auth.service.AccountService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountController {
	
	private final AccountService accountService;
	
	@GetMapping("/accounts")
	public List<AccountListItemResponse> getAccountList(){
		return accountService.getAccountList();
	}
}
