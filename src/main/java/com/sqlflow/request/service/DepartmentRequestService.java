package com.sqlflow.request.service;

import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.approval.dto.ApprovalHistoryResponse;
import com.sqlflow.request.dto.DepartmentRequestPageResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.mapper.DepartmentRequestMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepartmentRequestService {

    private static final Set<String> SEARCHABLE_REQUEST_TYPES = Set.of(
            "SELECT",
            "CREATE_VIEW",
            "INSERT",
            "UPDATE",
            "DELETE");

    private final DepartmentRequestMapper departmentRequestMapper;

    @Transactional(
            transactionManager = "managementTransactionManager",
            readOnly = true)
    public DepartmentRequestPageResponse getRequestsPage(
            Long actorId,
            String keyword,
            String type,
            String status,
            int page,
            int size) {

        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        String searchKeyword = keyword == null ? "" : keyword.strip();
        String searchType = type == null ? "" : type.strip();
        String searchStatus = status == null ? "" : status.strip();

        RequestSearchValidator.validateSearch(
                searchKeyword,
                searchStatus);
        validateRequestType(searchType);
        RequestSearchValidator.validatePage(page, size);

        long totalCount = departmentRequestMapper.countVisibleRequests(
                actorId,
                searchKeyword,
                searchType,
                searchStatus);

        long totalPages =
                totalCount == 0 ? 0 : (totalCount - 1) / size + 1;

        // 요청한 페이지가 마지막 페이지를 넘으면 마지막 페이지로 보정한다.
        int actualPage =
                totalPages == 0 ? 1 : (int) Math.min(page, totalPages);

        long offset = (long) (actualPage - 1) * size;

        var items = totalCount == 0
                ? List.<com.sqlflow.request.dto.DepartmentRequestListResponse>of()
                : departmentRequestMapper.selectVisibleRequestsPage(
                        actorId,
                        searchKeyword,
                        searchType,
                        searchStatus,
                        offset,
                        size);

        return new DepartmentRequestPageResponse(
                items,
                actualPage,
                size,
                totalCount,
                totalPages);
    }

    private void validateRequestType(String type) {
        if (!type.isEmpty() && !SEARCHABLE_REQUEST_TYPES.contains(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 요청 유형입니다.");
        }
    }

    @Transactional(
            transactionManager = "managementTransactionManager",
            readOnly = true)
    public RequestDetailResponse getRequestDetail(
            Long requestId,
            Long actorId) {
        validateDetailIds(requestId, actorId);

        RequestDetailResponse detail =
                departmentRequestMapper.selectVisibleRequestDetail(
                        requestId,
                        actorId);

        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다.");
        }

        String requestType = detail.getRequestType() == null
                ? "SELECT"
                : detail.getRequestType();

        boolean changeRequest = Set.of(
                "CREATE_VIEW",
                "INSERT",
                "UPDATE",
                "DELETE")
                .contains(requestType);

        detail.setItems(changeRequest
                ? departmentRequestMapper.selectVisibleViewSqlItems(
                        requestId,
                        actorId)
                : departmentRequestMapper.selectVisibleRequestItems(
                        requestId,
                        actorId));

        detail.setApprovals(
                departmentRequestMapper.selectVisibleRequestApprovals(
                        requestId,
                        actorId));

        // 결과 관리 버튼의 표시 여부는 로그인 계정과 기안 상태로 판단한다.
        detail.setCanManageChangeSqlResult(
                changeRequest
                        && departmentRequestMapper
                                .canManageChangeSqlResult(requestId, actorId) == 1);

        return detail;
    }

    private void validateDetailIds(Long requestId, Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        if (requestId == null || requestId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 요청 ID가 필요합니다.");
        }
    }

    @Transactional(
            transactionManager = "managementTransactionManager",
            readOnly = true)
    public List<ApprovalHistoryResponse> getRequestApprovalHistory(
            Long requestId,
            Long actorId) {

        validateDetailIds(requestId, actorId);

        if (departmentRequestMapper.countVisibleRequest(
                requestId,
                actorId) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다.");
        }

        return departmentRequestMapper.selectVisibleRequestApprovalHistory(
                requestId,
                actorId);
    }

    @Transactional(
            transactionManager = "managementTransactionManager")
    public void registerChangeSqlResult(
            Long requestId,
            Long queryId,
            Long actorId,
            String resultStatus,
            String resultNote,
            Long executedBy) {

        boolean completed = "COMPLETED".equals(resultStatus);
        boolean failed = "FAILED".equals(resultStatus);
        boolean cancelled = "CANCELLED".equals(resultStatus);

        if (!completed && !failed && !cancelled) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "처리 결과 상태를 확인해 주세요.");
        }

        String note = resultNote == null || resultNote.isBlank()
                ? null
                : resultNote.strip();

        if (!completed && note == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "실패 또는 취소 사유를 입력해 주세요.");
        }

        if (cancelled && executedBy != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "취소한 SQL에는 실행자를 지정할 수 없습니다.");
        }

        if (!cancelled && (executedBy == null || executedBy <= 0)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "외부 실행자를 지정해 주세요.");
        }

        try {
            int inserted = departmentRequestMapper.insertChangeSqlResult(
                    requestId,
                    queryId,
                    actorId,
                    resultStatus,
                    note,
                    executedBy);

            if (inserted != 1) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "등록할 수 있는 승인된 변경 SQL인지, 실행자가 DB팀 계정인지 확인해 주세요.");
            }
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 처리 결과가 등록된 SQL입니다.");
        }
    }

    @Transactional(
            transactionManager = "managementTransactionManager")
    public void changeSqlResult(
            Long requestId,
            Long queryId,
            Long actorId,
            String resultStatus,
            String resultNote,
            Long executedBy,
            String changeType,
            String changeReason) {

        boolean reexecution = "REEXECUTION".equals(changeType);

        if (!reexecution && !"CORRECTION".equals(changeType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "결과 변경 유형을 확인해 주세요.");
        }

        boolean completed = "COMPLETED".equals(resultStatus);
        boolean failed = "FAILED".equals(resultStatus);
        boolean cancelled = "CANCELLED".equals(resultStatus);

        if (!completed && !failed && !cancelled) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "처리 결과 상태를 확인해 주세요.");
        }

        String note = resultNote == null || resultNote.isBlank()
                ? null
                : resultNote.strip();

        if (!completed && note == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "실패 또는 취소 사유를 입력해 주세요.");
        }

        if (cancelled && executedBy != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "취소한 SQL에는 실행자를 지정할 수 없습니다.");
        }

        if (!cancelled && (executedBy == null || executedBy <= 0)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "외부 실행자를 지정해 주세요.");
        }

        if (changeReason == null || changeReason.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "결과 변경 사유를 입력해 주세요.");
        }

        String previousStatus =
                departmentRequestMapper.lockChangeSqlResult(
                        requestId,
                        queryId,
                        actorId);

        if (previousStatus == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "변경할 수 있는 SQL 처리 결과가 없습니다.");
        }

        if (reexecution && (!"FAILED".equals(previousStatus) || cancelled)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "실패한 SQL의 외부 재실행 결과는 성공 또는 실패로 등록해 주세요.");
        }

        // 현재 값이 바뀌기 전에 스냅샷을 남긴다.
        // 갱신 실패 시 이력도 함께 롤백된다.
        if (departmentRequestMapper.insertChangeSqlResultHistory(
                queryId,
                actorId,
                changeType,
                changeReason.strip()) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이전 결과를 이력에 저장하지 못했습니다.");
        }

        if (departmentRequestMapper.updateChangeSqlResult(
                queryId,
                actorId,
                resultStatus,
                note,
                executedBy) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "외부 실행자가 DB팀 계정인지 확인해 주세요.");
        }
    }
}