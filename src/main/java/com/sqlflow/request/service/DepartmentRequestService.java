package com.sqlflow.request.service;

import java.util.List;

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

    private final DepartmentRequestMapper departmentRequestMapper;

    @Transactional(
            transactionManager = "managementTransactionManager",
            readOnly = true)
    public DepartmentRequestPageResponse getRequestsPage(
            Long actorId,
            String keyword,
            String status,
            int page,
            int size) {

        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        String searchKeyword = keyword == null ? "" : keyword.strip();
        String searchStatus = status == null ? "" : status.strip();

        RequestSearchValidator.validateSearch(
                searchKeyword,
                searchStatus);

        RequestSearchValidator.validatePage(page, size);

        long totalCount = departmentRequestMapper.countVisibleRequests(
                actorId,
                searchKeyword,
                searchStatus);

        long totalPages =
                totalCount == 0 ? 0 : (totalCount - 1) / size + 1;

        // 기존 내 요청 목록과 동일하게 마지막 페이지로 보정한다.
        int actualPage =
                totalPages == 0 ? 1 : (int) Math.min(page, totalPages);

        long offset = (long) (actualPage - 1) * size;

        var items = totalCount == 0
                ? List.<com.sqlflow.request.dto.DepartmentRequestListResponse>of()
                : departmentRequestMapper.selectVisibleRequestsPage(
                        actorId,
                        searchKeyword,
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

        // 존재하지 않는 경우와 열람 권한이 없는 경우를 구분해 노출하지 않는다.
        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다.");
        }

        detail.setItems("CREATE_VIEW".equals(detail.getRequestType())
                ? departmentRequestMapper.selectVisibleViewSqlItems(requestId, actorId)
                : departmentRequestMapper.selectVisibleRequestItems(requestId, actorId));

        detail.setApprovals(
                departmentRequestMapper.selectVisibleRequestApprovals(
                        requestId,
                        actorId));
        if ("CREATE_VIEW".equals(detail.getRequestType())) {
            detail.setViewCreateResult(departmentRequestMapper.selectVisibleViewCreateResult(requestId, actorId));
            detail.setCanRegisterViewResult(departmentRequestMapper.canRegisterViewCreateResult(requestId, actorId) == 1);
        }

        return detail;
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public void registerViewCreateResult(Long requestId, Long actorId, String status, String content) {
        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (!"COMPLETED".equals(status) && !"FAILED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "처리 결과 상태가 올바르지 않습니다.");
        }
        if (content == null || content.isBlank() || content.strip().length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "처리 결과를 1~4000자로 입력해 주세요.");
        }
        if (departmentRequestMapper.canRegisterViewCreateResult(requestId, actorId) != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "최종 승인된 뷰 요청은 DB팀 결재자만 결과를 등록할 수 있습니다.");
        }
        if (departmentRequestMapper.upsertViewCreateResult(requestId, actorId, status, content.strip()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "처리 결과를 저장하지 못했습니다. 요청 상태를 확인해 주세요.");
        }
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
                requestId, actorId) != 1) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다.");
        }

        return departmentRequestMapper.selectVisibleRequestApprovalHistory(
                requestId,
                actorId);
    }
}
