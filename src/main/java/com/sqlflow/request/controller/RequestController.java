package com.sqlflow.request.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.approval.dto.ApprovalCandidatesResponse;
import com.sqlflow.approval.dto.ApprovalHistoryResponse;
import com.sqlflow.approval.service.ApprovalService;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.request.dto.DepartmentRequestPageResponse;
import com.sqlflow.request.dto.DmlDraftRequest;
import com.sqlflow.request.dto.RequestDashboardResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.dto.RequestDraftUpdateRequest;
import com.sqlflow.request.dto.RequestListResponse;
import com.sqlflow.request.dto.RequestPageResponse;
import com.sqlflow.request.dto.RequestSqlValidationResult;
import com.sqlflow.request.dto.RequestSubmitRequest;
import com.sqlflow.request.dto.SelectSqlValidationRequest;
import com.sqlflow.request.dto.SelectSqlValidationResult;
import com.sqlflow.request.dto.ViewCreateDraftRequest;
import com.sqlflow.request.dto.ViewCreateSqlValidationRequestDTO;
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO;
import com.sqlflow.request.service.DepartmentRequestService;
import com.sqlflow.request.service.DmlSqlValidationService;
import com.sqlflow.request.service.DmlSqlValidationService.SqlType;
import com.sqlflow.request.service.DmlSqlValidationService.ValidationResult;
import com.sqlflow.request.service.RequestService;
import com.sqlflow.request.service.ViewCreationLinkService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;
    private final ApprovalService approvalService;
    private final DepartmentRequestService departmentRequestService;
    private final ViewCreationLinkService viewCreationLinkService;
    private final DmlSqlValidationService dmlSqlValidationService;

    @GetMapping("/dashboard")
    public RequestDashboardResponse getMyDashboard(
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        return requestService.getMyDashboard(user.getUserId());
    }

    public record DraftCreateResponse(Long requestId) {
    }

    @GetMapping("/mine")
    public List<RequestListResponse> getMyRequests(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "status", defaultValue = "") String status,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        return requestService.getMyRequests(user.getUserId(), keyword, status);
    }

    @GetMapping("/mine/page")
    public RequestPageResponse getMyRequestsPage(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "requestType", defaultValue = "") String requestType,
            @RequestParam(name = "status", defaultValue = "") String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        return requestService.getMyRequestsPage(
                user.getUserId(),
                keyword,
                requestType,
                status,
                page,
                size);
    }

    @GetMapping("/mine/{requestId}")
    public RequestDetailResponse getMyRequestDetail(
            @PathVariable Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        return requestService.getMyRequestDetail(
                requestId,
                user.getUserId());
    }

    @GetMapping("/department/page")
    public DepartmentRequestPageResponse getDepartmentRequestsPage(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "type", defaultValue = "") String type,
            @RequestParam(name = "status", defaultValue = "") String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        return departmentRequestService.getRequestsPage(
                user == null ? null : user.getUserId(),
                keyword,
                type,
                status,
                page,
                size);
    }

    @GetMapping("/department/{requestId}")
    public RequestDetailResponse getDepartmentRequestDetail(
            @PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        return departmentRequestService.getRequestDetail(
                requestId,
                user == null ? null : user.getUserId());
    }

    @GetMapping("/department/{requestId}/approval-history")
    public List<ApprovalHistoryResponse> getDepartmentRequestApprovalHistory(
            @PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        return departmentRequestService.getRequestApprovalHistory(
                requestId,
                user == null ? null : user.getUserId());
    }

    @PostMapping("/drafts")
    public ResponseEntity<DraftCreateResponse> createDraft(
            @Valid @RequestBody RequestDraftCreateRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        Long requestId = requestService.createDraft(
                input,
                user.getUserId(),
                user.getDeptId());

        return ResponseEntity
                .created(URI.create("/api/requests/mine/" + requestId))
                .body(new DraftCreateResponse(requestId));
    }

    /** 로그인 사용자의 데이터 변경 초안을 생성한다. */
    @PostMapping("/dml-drafts")
    public ResponseEntity<DraftCreateResponse> createDmlDraft(
            @Valid @RequestBody DmlDraftRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        Long requestId = requestService.createDmlDraft(
                input,
                user.getUserId(),
                user.getDeptId());

        return ResponseEntity
                .created(URI.create("/api/requests/mine/" + requestId))
                .body(new DraftCreateResponse(requestId));
    }

    @PutMapping("/dml-drafts/{requestId}")
    public ResponseEntity<Void> updateDmlDraft(
            @PathVariable Long requestId,
            @Valid @RequestBody DmlDraftRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        requestService.updateDmlDraft(
                requestId,
                input,
                user.getUserId());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/view-create-drafts")
    public ResponseEntity<DraftCreateResponse> createViewDraft(
            @Valid @RequestBody ViewCreateDraftRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        Long requestId = requestService.createViewDraft(
                input,
                user.getUserId(),
                user.getDeptId());

        return ResponseEntity
                .created(URI.create("/api/requests/mine/" + requestId))
                .body(new DraftCreateResponse(requestId));
    }

    @PutMapping("/view-create-drafts/{requestId}")
    public ResponseEntity<Void> updateViewDraft(
            @PathVariable Long requestId,
            @Valid @RequestBody ViewCreateDraftRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        requestService.updateViewDraft(
                requestId,
                input,
                user.getUserId());

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/drafts/{requestId}")
    public ResponseEntity<Void> updateDraft(
            @PathVariable Long requestId,
            @Valid @RequestBody RequestDraftUpdateRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        requestService.updateDraft(
                requestId,
                input,
                user.getUserId());

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/drafts/{requestId}")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        requestService.deleteDraft(requestId, user.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{requestId}/approval-candidates")
    public ApprovalCandidatesResponse getApprovalCandidates(
            @PathVariable Long requestId,
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        return approvalService.getApprovalCandidates(
                requestId,
                user.getUserId(),
                keyword);
    }

    @PostMapping("/{requestId}/submit")
    public ResponseEntity<Void> submitRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody RequestSubmitRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        requestService.submitRequest(
                requestId,
                input,
                user.getUserId());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestId}/validate")
    public List<RequestSqlValidationResult> validateDraftSql(
            @PathVariable Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        return requestService.validateDraftSql(
                requestId,
                user.getUserId());
    }
    
    @PostMapping("/select-sql/validate")
    public List<SelectSqlValidationResult> validateSelectSqlTexts(
            @Valid @RequestBody SelectSqlValidationRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        return requestService.validateSelectSqlTexts(
                user.getUserId(), input.sqls());
    }

    /** 로그인한 사용자가 작성 중인 뷰 SQL을 저장 없이 검사한다. */
    @PostMapping("/view-sql/validate")
    public List<ViewCreateSqlValidationResultDTO> validateViewSqlTexts(
            @Valid @RequestBody ViewCreateSqlValidationRequestDTO input) {
        return requestService.validateViewSqlTexts(input.getSqls());
    }

    /** 화면에서 선택한 유형을 SQL 목록 전체에 적용한다. */
    @PostMapping("/dml-sql/validate")
    public List<ValidationResult> validateDmlSqlTexts(
            @Valid @RequestBody DmlSqlValidationRequest input) {

        return input.sqls().stream()
                .map(sql -> dmlSqlValidationService.validate(
                        input.sqlType(),
                        sql))
                .toList();
    }

    public record DmlSqlValidationRequest(
            @NotNull SqlType sqlType,
            @NotEmpty @Size(max = 5)
            List<@NotNull @Size(max = 20000) String> sqls
    ) {
    }

    /** 로그인한 작성자의 저장된 뷰 생성 SQL을 검사한다. */
    @PostMapping("/{requestId}/view-sql/validate")
    public Map<Long, ViewCreateSqlValidationResultDTO> validateViewDraftSql(
            @PathVariable Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        return requestService.validateViewDraftSql(
                requestId,
                user.getUserId());
    }

    @GetMapping("/{requestId}/view-sql/download")
    public ResponseEntity<byte[]> downloadApprovedViewSql(
            @PathVariable Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        byte[] content = requestService.downloadApprovedViewSql(
                requestId,
                user.getUserId());

        return ResponseEntity.ok()
                .header(
                        org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment()
                                .filename(
                                        "request-" + requestId + ".sql",
                                        java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(
                        org.springframework.http.MediaType.parseMediaType(
                                "application/sql; charset=UTF-8"))
                .body(content);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleRequestError(
            ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "message",
                Objects.requireNonNullElse(
                        exception.getReason(),
                        "요청을 처리할 수 없습니다.")));
    }

    @PostMapping("/{requestId}/redraft")
    public ResponseEntity<DraftCreateResponse> redraftRequest(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody RequestDraftCreateRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        Long newRequestId = requestService.redraftRequest(
                requestId,
                input,
                user.getUserId(),
                user.getDeptId());

        return ResponseEntity
                .created(URI.create("/api/requests/mine/" + newRequestId))
                .body(new DraftCreateResponse(newRequestId));
    }

    @PostMapping("/{requestId}/withdraw")
    public ResponseEntity<Void> withdrawRequest(
            @PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        requestService.withdrawRequest(
                requestId,
                user == null ? null : user.getUserId());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/department/{requestId}/change-sql/{queryId}/result")
    public ResponseEntity<Void> registerChangeSqlResult(
            @PathVariable Long requestId,
            @PathVariable Long queryId,
            @Valid @RequestBody ChangeSqlResultRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        viewCreationLinkService.register(
                requestId,
                queryId,
                user.getUserId(),
                input.resultStatus(),
                input.resultNote(),
                input.executedBy(),
                input.resourceId());

        return ResponseEntity.noContent().build();
    }

    public record ChangeSqlResultRequest(
            @NotNull String resultStatus,
            @Size(max = 1000) String resultNote,
            Long executedBy,
            Long resourceId) {
    }

    @PatchMapping("/department/{requestId}/change-sql/{queryId}/result")
    public ResponseEntity<Void> changeSqlResult(
            @PathVariable Long requestId,
            @PathVariable Long queryId,
            @Valid @RequestBody ChangeSqlResultUpdateRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        departmentRequestService.changeSqlResult(
                requestId,
                queryId,
                user.getUserId(),
                input.resultStatus(),
                input.resultNote(),
                input.executedBy(),
                input.changeType(),
                input.changeReason());

        return ResponseEntity.noContent().build();
    }

    public record ChangeSqlResultUpdateRequest(
            @NotNull String resultStatus,
            @Size(max = 1000) String resultNote,
            Long executedBy,
            @NotNull String changeType,
            @NotNull @Size(max = 1000) String changeReason) {
    }
}
