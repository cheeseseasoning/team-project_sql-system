package com.sqlflow.request.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.dto.RequestDraftUpdateRequest;
import com.sqlflow.request.dto.RequestListResponse;
import com.sqlflow.request.dto.RequestSubmitRequest;
import com.sqlflow.request.service.RequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    @PostMapping("/drafts")
    public ResponseEntity<DraftCreateResponse> createDraft(
            @Valid @RequestBody RequestDraftCreateRequest input) {

        Long requestId = requestService.createDraft(input);

        return ResponseEntity
        		.created(URI.create("/api/requests/mine/" + requestId))
                .body(new DraftCreateResponse(requestId));
    }

    public record DraftCreateResponse(Long requestId) {
    }
    
    @GetMapping("/mine")
    public List<RequestListResponse> getMyRequests(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "status", defaultValue = "") String status) {

        return requestService.getMyRequests(keyword, status);
    }
    
    @GetMapping("/mine/{requestId}")
    public RequestDetailResponse getMyRequestDetail(
            @PathVariable Long requestId) {

        return requestService.getMyRequestDetail(requestId);
    }
    
    @PutMapping("/drafts/{requestId}")
    public ResponseEntity<Void> updateDraft(
            @PathVariable Long requestId,
            @Valid @RequestBody RequestDraftUpdateRequest input) {

        requestService.updateDraft(requestId, input);

        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping("/drafts/{requestId}")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable("requestId") Long requestId) {

        requestService.deleteDraft(requestId);

        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{requestId}/submit")
    public ResponseEntity<Void> submitRequest(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody RequestSubmitRequest input) {

        requestService.submitRequest(requestId, input);

        return ResponseEntity.noContent().build();
    }
}