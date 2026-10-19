package com.sqlflow.request.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.request.domain.QueryItem;
import com.sqlflow.request.domain.QueryRequest;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.dto.RequestDraftUpdateRequest;
import com.sqlflow.request.dto.RequestListResponse;
import com.sqlflow.request.mapper.RequestMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestMapper requestMapper;
    
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
            "WITHDRAWN"
    );

    @Transactional
    public Long createDraft(RequestDraftCreateRequest input) {

        // 로그인 연동 전까지 사용하는 개발용 사용자·부서
        Long drafterId = 1L;
        Long deptId = 1L;

        // 1. 요청 ID 발급
        Long requestId = requestMapper.selectNextRequestId();

        // 2. 요청서 구성
        QueryRequest request = new QueryRequest();
        request.setRequestId(requestId);
        request.setRequestNo(createRequestNo(requestId));
        request.setDrafterId(drafterId);
        request.setDeptId(deptId);
        request.setTitle(input.getTitle().strip());
        request.setPurpose(input.getPurpose().strip());

        // 3. 요청서 저장
        requestMapper.insertRequest(request);

        // 4. SQL 항목 저장
        for (int index = 0; index < input.getSqls().size(); index++) {
            QueryItem item = new QueryItem();

            item.setRequestId(requestId);
            item.setQueryOrder(index + 1);
            item.setQueryTitle("SQL " + (index + 1));
            item.setSqlText(input.getSqls().get(index));
            item.setAuthoringType("MANUAL");

            requestMapper.insertItem(item);
        }

        return requestId;
    }

    private String createRequestNo(Long requestId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        return "Q-"
                + today.toString().replace("-", "")
                + "-"
                + requestId;
    }
    
    @Transactional(readOnly = true)
    public List<RequestListResponse> getMyRequests(
            String keyword,
            String status) {

        // 로그인 연동 전 개발용 사용자
        Long drafterId = 1L;

        String searchKeyword = keyword == null ? "" : keyword.strip();
        String searchStatus = status == null ? "" : status.strip();

        if (searchKeyword.length() > 120) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "검색어는 120자 이하여야 합니다."
            );
        }

        if (!searchStatus.isEmpty()
                && !SEARCHABLE_STATUSES.contains(searchStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 요청 상태입니다."
            );
        }

        return requestMapper.selectMyRequests(
                drafterId,
                searchKeyword,
                searchStatus
        );
    }
    
    @Transactional(readOnly = true)
    public RequestDetailResponse getMyRequestDetail(Long requestId) {

        // 로그인 연동 전 개발용 사용자
        Long drafterId = 1L;

        RequestDetailResponse detail =
                requestMapper.selectMyRequestDetail(requestId, drafterId);

        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다."
            );
        }

        detail.setItems(
                requestMapper.selectMyRequestItems(requestId, drafterId)
        );

        return detail;
    }
    
    @Transactional
    public void updateDraft(
            Long requestId,
            RequestDraftUpdateRequest input) {

        // 로그인 연동 전 개발용 사용자
        Long drafterId = 1L;

        // 1. 본인 요청 확인 및 수정 중 잠금
        String status = requestMapper.selectMyRequestStatusForUpdate(
                requestId, drafterId);

        if (status == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다."
            );
        }

        // 2. 초안 상태인지 확인
        if (!"DRAFT".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "초안 상태의 요청만 수정할 수 있습니다."
            );
        }

        // 3. 제목·목적 수정
        QueryRequest request = new QueryRequest();
        request.setRequestId(requestId);
        request.setDrafterId(drafterId);
        request.setTitle(input.getTitle().strip());
        request.setPurpose(input.getPurpose().strip());

        int updatedCount = requestMapper.updateDraft(request);

        if (updatedCount != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "요청 상태가 변경되어 수정할 수 없습니다."
            );
        }

        // 4. 기존 SQL 항목 삭제
        requestMapper.deleteDraftItems(requestId, drafterId);

        // 5. 수정된 SQL 목록 저장
        for (int index = 0; index < input.getSqls().size(); index++) {
            QueryItem item = new QueryItem();

            item.setRequestId(requestId);
            item.setQueryOrder(index + 1);
            item.setQueryTitle("SQL " + (index + 1));
            item.setSqlText(input.getSqls().get(index));
            item.setAuthoringType("MANUAL");

            requestMapper.insertItem(item);
        }
    }
    
    @Transactional
    public void deleteDraft(Long requestId) {

        // 로그인 연동 전 개발용 사용자
        Long drafterId = 1L;

        // 1. 본인 요청 확인 및 행 잠금
        String status = requestMapper.selectMyRequestStatusForUpdate(
                requestId, drafterId);

        if (status == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다."
            );
        }

        // 2. 초안 상태인지 확인
        if (!"DRAFT".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "초안 상태의 요청만 삭제할 수 있습니다."
            );
        }

        // 3. 연결된 SQL 항목 먼저 삭제
        requestMapper.deleteDraftItems(requestId, drafterId);

        // 4. 요청서 삭제
        int deletedCount = requestMapper.deleteDraftRequest(
                requestId, drafterId);

        if (deletedCount != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "요청 상태가 변경되어 삭제할 수 없습니다."
            );
        }
    }
}