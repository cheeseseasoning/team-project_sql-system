package com.sqlflow.request.service;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class RequestSearchValidator {

    private static final Set<String> SEARCHABLE_STATUSES = Set.of(
            "DRAFT",
            "PENDING_APPROVAL",
            "READY",
            "RUNNING",
            "RETRY_REVIEW",
            "COMPLETED",
            "PARTIAL_FAILED",
            "FAILED",
            "REJECTED",
            "WITHDRAWN");

    private RequestSearchValidator() {
    }

    public static void validateSearch(String keyword, String status) {
        if (keyword.length() > 120) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "검색어는 120자 이하여야 합니다.");
        }

        if (!status.isEmpty() && !SEARCHABLE_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 요청 상태입니다.");
        }
    }

    public static void validatePage(int page, int size) {
        if (page < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지 번호는 1 이상이어야 합니다.");
        }

        if (size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지 크기는 1 이상, 50 이하여야 합니다.");
        }
    }
}