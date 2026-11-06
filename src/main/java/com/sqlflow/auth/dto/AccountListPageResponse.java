package com.sqlflow.auth.dto;

import java.util.List;

import lombok.Getter;

@Getter
public class AccountListPageResponse {
	
	private final List<AccountListItemResponse> items;
	private final int page;
	private final int size;
	private final long totalCount;
	private final long totalPages;
	
	public AccountListPageResponse(
			List<AccountListItemResponse> items,
			int page,
			int size,
			long totalCount,
			long totalPages) {
		this.items = items;
		this.page = page;
		this.size = size;
		this.totalCount = totalCount;
		this.totalPages = totalPages;
	}
}












