package com.sqlflow.request.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.dto.RequestDraftUpdateRequest;
import com.sqlflow.request.dto.RequestListResponse;
import com.sqlflow.request.dto.RequestSubmitRequest;
import com.sqlflow.request.service.RequestService;
import com.sqlflow.approval.dto.ApprovalCandidatesResponse;
import com.sqlflow.approval.service.ApprovalService;
import com.sqlflow.auth.security.SqlFlowUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;
    private final ApprovalService approvalService;

    @PostMapping("/drafts")
    public ResponseEntity<DraftCreateResponse> createDraft(
            @Valid @RequestBody RequestDraftCreateRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        Long requestId = requestService.createDraft(input, user.getUserId(), user.getDeptId());

        return ResponseEntity
        		.created(URI.create("/api/requests/mine/" + requestId))
                .body(new DraftCreateResponse(requestId));
    }

    public record DraftCreateResponse(Long requestId) {
    }
    
    @GetMapping("/mine")
    public List<RequestListResponse> getMyRequests(
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        return requestService.getMyRequests(user.getUserId());
    }
    
    @GetMapping("/mine/{requestId}")
    public RequestDetailResponse getMyRequestDetail(
            @PathVariable Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        return requestService.getMyRequestDetail(requestId, user.getUserId());
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
            @PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        requestService.deleteDraft(requestId, user.getUserId());

        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{requestId}/submit")
    public ResponseEntity<Void> submitRequest(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody RequestSubmitRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        requestService.submitRequest(requestId, input, user.getUserId());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{requestId}/approval-candidates")
    public ApprovalCandidatesResponse getApprovalCandidates(
            @PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        return approvalService.getApprovalCandidates(requestId, user.getUserId());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleRequestError(
            ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "message",
                Objects.requireNonNullElse(
                        exception.getReason(), "요청을 처리할 수 없습니다.")
        ));
    }
}
