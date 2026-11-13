package com.sqlflow.auth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sqlflow.auth.dto.AccountListPageResponse;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.auth.service.AccountService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/accounts")
    public AccountListPageResponse getAccountList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword,
            Authentication authentication) {
    	
    	String actorLoginId = null;
    	
    	if (authentication != null
    			&& authentication.getPrincipal() instanceof SqlFlowUserDetails user) {
    		actorLoginId = user.getLoginId();
    	}
    	
        return accountService.getAccountListForActor(
        		page, 
        		size,
                actorLoginId, keyword);
    }
}