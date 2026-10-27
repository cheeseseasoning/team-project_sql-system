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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.approval.dto.ApprovalCandidatesResponse;
import com.sqlflow.approval.service.ApprovalService;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.request.dto.DepartmentRequestPageResponse;
import com.sqlflow.request.dto.RequestDashboardResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.dto.RequestDraftUpdateRequest;
import com.sqlflow.request.dto.RequestListResponse;
import com.sqlflow.request.dto.RequestPageResponse;
import com.sqlflow.request.dto.RequestSqlValidationResult;
import com.sqlflow.request.dto.RequestSubmitRequest;
import com.sqlflow.request.service.DepartmentRequestService;
import com.sqlflow.request.service.RequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

	private final RequestService requestService;
	private final ApprovalService approvalService;
	private final DepartmentRequestService departmentRequestService;
	
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
			@RequestParam(name = "status", defaultValue = "") String status,
			@RequestParam(name = "page", defaultValue = "1") int page,
			@RequestParam(name = "size", defaultValue = "10") int size,
			@AuthenticationPrincipal SqlFlowUserDetails user) {
		return requestService.getMyRequestsPage(user.getUserId(), keyword, status, page, size);
	}

	@GetMapping("/mine/{requestId}")
	public RequestDetailResponse getMyRequestDetail(
			@PathVariable Long requestId,
			@AuthenticationPrincipal SqlFlowUserDetails user) {
		return requestService.getMyRequestDetail(requestId, user.getUserId());
	}
	
	@GetMapping("/department/page")
	public DepartmentRequestPageResponse getDepartmentRequestsPage(
	        @RequestParam(name = "keyword", defaultValue = "") String keyword,
	        @RequestParam(name = "status", defaultValue = "") String status,
	        @RequestParam(name = "page", defaultValue = "1") int page,
	        @RequestParam(name = "size", defaultValue = "10") int size,
	        @AuthenticationPrincipal SqlFlowUserDetails user) {

	    return departmentRequestService.getRequestsPage(
	            user == null ? null : user.getUserId(),
	            keyword,
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
	
	@PostMapping("/drafts")
	public ResponseEntity<DraftCreateResponse> createDraft(
			@Valid @RequestBody RequestDraftCreateRequest input,
			@AuthenticationPrincipal SqlFlowUserDetails user) {
		Long requestId = requestService.createDraft(input, user.getUserId(), user.getDeptId());
		return ResponseEntity.created(URI.create("/api/requests/mine/" + requestId))
				.body(new DraftCreateResponse(requestId));
	}
	
	@PutMapping("/drafts/{requestId}")
	public ResponseEntity<Void> updateDraft(
			@PathVariable Long requestId,
			@Valid @RequestBody RequestDraftUpdateRequest input,
			@AuthenticationPrincipal SqlFlowUserDetails user) {
		requestService.updateDraft(requestId, input, user.getUserId());
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
			@AuthenticationPrincipal SqlFlowUserDetails user) {
		return approvalService.getApprovalCandidates(requestId, user.getUserId());
	}

	@PostMapping("/{requestId}/submit")
	public ResponseEntity<Void> submitRequest(
			@PathVariable Long requestId,
			@Valid @RequestBody RequestSubmitRequest input,
			@AuthenticationPrincipal SqlFlowUserDetails user) {
		requestService.submitRequest(requestId, input, user.getUserId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{requestId}/validate")
	public List<RequestSqlValidationResult> validateDraftSql(
			@PathVariable Long requestId,
			@AuthenticationPrincipal SqlFlowUserDetails user) {
		return requestService.validateDraftSql(requestId, user.getUserId());
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, String>> handleRequestError(ResponseStatusException exception) {
		return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
				"message", Objects.requireNonNullElse(exception.getReason(), "요청을 처리할 수 없습니다.")));
	}
	
	@PostMapping("/{requestId}/redraft")
	public ResponseEntity<DraftCreateResponse> redraftRequest(
	        @PathVariable Long requestId,
	        @AuthenticationPrincipal SqlFlowUserDetails user) {

	    Long newRequestId = requestService.redraftRequest(
	            requestId,
	            user.getUserId(),
	            user.getDeptId());

	    return ResponseEntity
	            .created(URI.create("/api/requests/mine/" + newRequestId))
	            .body(new DraftCreateResponse(newRequestId));
	}
}
