package com.sqlflow.request.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.service.RequestSqlValidationService;

import com.sqlflow.request.domain.QueryItem;
import com.sqlflow.request.domain.QueryRequest;
import com.sqlflow.request.dto.RequestDashboardResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.dto.RequestDraftUpdateRequest;
import com.sqlflow.request.dto.RequestItemResponse;
import com.sqlflow.request.dto.RequestListResponse;
import com.sqlflow.request.dto.RequestPageResponse;
import com.sqlflow.request.dto.RequestSubmitRequest;
import com.sqlflow.request.dto.RequestSummaryResponse;
import com.sqlflow.request.dto.RequestSqlValidationResult;
import com.sqlflow.request.dto.ViewCreateDraftRequest;
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO;
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO.Status;
import com.sqlflow.request.mapper.RequestMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequestService {

	private final RequestMapper requestMapper;

	private final ObjectProvider<ApprovalLineCreator> approvalLineCreatorProvider;
	
	private final RequestSqlValidationService requestSqlValidationService;
	
    private final ViewCreateSqlValidationService viewCreateSqlValidationService;

	@Transactional
	public Long createDraft(RequestDraftCreateRequest input) {
		return createDraft(input, 1L, 1L);
	}

	@Transactional
	public Long createDraft(RequestDraftCreateRequest input, Long drafterId, Long deptId) {

		// 1. 요청 ID 발급
		Long requestId = requestMapper.selectNextRequestId();

		// 2. 요청서 구성
		QueryRequest request = new QueryRequest();
		request.setRequestType("SELECT");
		request.setRequestId(requestId);
		request.setRequestNo(createRequestNo(requestId));
		request.setDrafterId(drafterId);
		request.setDeptId(deptId);
		request.setTitle(input.getTitle().strip());
		request.setPurpose(input.getPurpose().strip());

		request.setDetailContent(
		        normalizeOptionalText(input.getDetailContent())
		);
		request.setIsUrgent(input.getIsUrgent());
		request.setDesiredDate(input.getDesiredDate());

		// 3. 요청서 저장
		requestMapper.insertRequest(request);

		// 4. SQL 항목 저장
		saveDraftItems(
		        requestId,
		        input.getSqls(),
		        input.getSqlTitles()
		);

		return requestId;
	}

	@Transactional(transactionManager = "managementTransactionManager")
	public Long createViewDraft(ViewCreateDraftRequest input, Long drafterId, Long deptId) {
		validateViewSqlItems(input);
		Long requestId = requestMapper.selectNextRequestId();
		QueryRequest request = new QueryRequest();
		request.setRequestId(requestId);
		request.setRequestNo(createRequestNo(requestId));
		request.setRequestType("CREATE_VIEW");
		request.setDrafterId(drafterId);
		request.setDeptId(deptId);
		request.setTitle(input.getTitle().strip());
		request.setPurpose(input.getPurpose().strip());
		request.setDetailContent(normalizeOptionalText(input.getDetailContent()));
		request.setIsUrgent(input.getIsUrgent());
		request.setDesiredDate(input.getDesiredDate());
		requestMapper.insertRequest(request);
		saveViewSqlItems(requestId, input);
		return requestId;
	}

	@Transactional(transactionManager = "managementTransactionManager", rollbackFor = Exception.class)
	public void updateViewDraft(Long requestId, ViewCreateDraftRequest input, Long drafterId) {
		validateViewSqlItems(input);
		String status = requestMapper.selectMyRequestStatusForUpdate(requestId, drafterId);
		if (status == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다.");
		}
		if (!"DRAFT".equals(status)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "초안 상태의 요청만 수정할 수 있습니다.");
		}
		RequestDetailResponse existing = requestMapper.selectMyRequestDetail(requestId, drafterId);
		if (existing == null || !"CREATE_VIEW".equals(existing.getRequestType())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "뷰 생성 요청을 찾을 수 없습니다.");
		}
		QueryRequest request = new QueryRequest();
		request.setRequestId(requestId);
		request.setDrafterId(drafterId);
		request.setTitle(input.getTitle().strip());
		request.setPurpose(input.getPurpose().strip());
		request.setDetailContent(normalizeOptionalText(input.getDetailContent()));
		request.setIsUrgent(input.getIsUrgent());
		request.setDesiredDate(input.getDesiredDate());
		if (requestMapper.updateDraft(request) != 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "요청 상태가 변경되어 수정할 수 없습니다.");
		}
		requestMapper.deleteDraftViewSql(requestId, drafterId);
		saveViewSqlItems(requestId, input);
	}

	private void validateViewSqlItems(ViewCreateDraftRequest input) {
		if (input.getItems() == null || input.getItems().isEmpty() || input.getItems().size() > 5) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "뷰 생성 SQL은 1개 이상, 5개 이하로 입력하세요.");
		}
		for (ViewCreateDraftRequest.SqlEntry item : input.getItems()) {
			String sql = item.getSqlText() == null ? "" : item.getSqlText().strip();
			if (!sql.matches("(?is)^CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\b.*")
					|| sql.substring(0, sql.length() - (sql.endsWith(";") ? 1 : 0)).contains(";")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "각 SQL은 CREATE VIEW 문 하나만 입력할 수 있습니다.");
			}
		}
	}

	private void saveViewSqlItems(Long requestId, ViewCreateDraftRequest input) {
		for (int i = 0; i < input.getItems().size(); i++) {
			ViewCreateDraftRequest.SqlEntry item = input.getItems().get(i);
			String title = item.getQueryTitle() == null || item.getQueryTitle().isBlank()
					? "뷰 생성 SQL " + (i + 1) : item.getQueryTitle().strip();
			requestMapper.insertViewSql(requestId, i + 1, title, item.getSqlText().strip());
		}
	}

	private String createRequestNo(Long requestId) {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

		return "Q-" + today.toString().replace("-", "") + "-" + requestId;
	}

	@Transactional(readOnly = true)
	public List<RequestListResponse> getMyRequests(String keyword, String status) {
		return getMyRequests(1L, keyword, status);
	}

	@Transactional(readOnly = true)
	public List<RequestListResponse> getMyRequests(Long drafterId, String keyword, String status) {

		String searchKeyword = keyword == null ? "" : keyword.strip();
		String searchStatus = status == null ? "" : status.strip();

		validateSearchConditions(searchKeyword, searchStatus);

		return requestMapper.selectMyRequests(drafterId, searchKeyword, searchStatus);
	}

	@Transactional(readOnly = true)
	public RequestDetailResponse getMyRequestDetail(Long requestId) {
		return getMyRequestDetail(requestId, 1L);
	}

	@Transactional(readOnly = true)
	public RequestDetailResponse getMyRequestDetail(Long requestId, Long drafterId) {

		RequestDetailResponse detail = requestMapper.selectMyRequestDetail(requestId, drafterId);

		if (detail == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조회 요청을 찾을 수 없습니다.");
		}

		detail.setItems("CREATE_VIEW".equals(detail.getRequestType())
		        ? requestMapper.selectMyViewSqlItems(requestId, drafterId)
		        : requestMapper.selectMyRequestItems(requestId, drafterId));

		detail.setApprovals(
		        requestMapper.selectMyRequestApprovals(requestId, drafterId)
		);
		if ("CREATE_VIEW".equals(detail.getRequestType())) {
			detail.setViewCreateResult(requestMapper.selectViewCreateResult(requestId));
			detail.setViewCreateResultHistory(requestMapper.selectViewCreateResultHistory(requestId));
		}

		return detail;
	}

	@Transactional(readOnly = true, transactionManager = "managementTransactionManager")
	public byte[] downloadApprovedViewSql(Long requestId, Long actorId) {
		if (requestMapper.canDownloadApprovedViewSql(requestId, actorId) != 1) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다운로드할 수 있는 승인된 뷰 SQL이 없습니다.");
		}
		List<RequestItemResponse> items = requestMapper.selectApprovedViewSqlItems(requestId);
		if (items.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다운로드할 뷰 SQL이 없습니다.");
		}
		StringBuilder content = new StringBuilder("-- SQLFlow approved CREATE VIEW request ")
				.append(requestId).append("\n\n");
		for (RequestItemResponse item : items) {
			content.append("-- ").append(item.getQueryTitle()).append("\n")
					.append(item.getSqlText().stripTrailing()).append("\n");
			String sql = item.getSqlText().stripTrailing();
			if (!sql.endsWith(";")) {
				content.append(';');
			}
			content.append("\n\n");
		}
		return content.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}
	
    /**
     * 작성 중인 SQL을 입력 순서대로 검사한다.
     * 요청을 저장하거나 실제 SQL을 실행하지 않는다.
     */
    public List<ViewCreateSqlValidationResultDTO> validateViewSqlTexts(
            List<String> sqls) {

        if (sqls == null || sqls.isEmpty() || sqls.size() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검사할 SQL을 1개 이상, 5개 이하로 입력하세요.");
        }

        return sqls.stream()
                .map(viewCreateSqlValidationService::validate)
                .toList();
    }
	
    /**
     * 작성자 본인의 뷰 생성 초안에 저장된 SQL을 검사한다.
     * 검사 결과는 반환만 하며 DB에는 저장하지 않는다.
     */
    @Transactional(
            transactionManager = "managementTransactionManager",
            readOnly = true)
    public Map<Long, ViewCreateSqlValidationResultDTO> validateViewDraftSql(Long requestId, Long actorId) {

        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        if (requestId == null || requestId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 요청 ID가 필요합니다.");
        }

        // 작성자 조건을 포함한 조회로 다른 사용자의 요청 접근을 막는다.
        RequestDetailResponse draft = requestMapper.selectMyRequestDetail(requestId, actorId);

        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다.");
        }

        if (!"CREATE_VIEW".equals(draft.getRequestType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "뷰 생성 요청만 검사할 수 있습니다.");
        }

        if (!"DRAFT".equals(draft.getRequestStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "초안 상태에서만 검사할 수 있습니다.");
        }

        List<RequestItemResponse> items = requestMapper.selectMyViewSqlItems(requestId, actorId);

        if (items.isEmpty() || items.size() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "뷰 생성 SQL은 1개 이상, 5개 이하로 등록하세요.");
        }

        Map<Long, ViewCreateSqlValidationResultDTO> results = new LinkedHashMap<>();

        for (RequestItemResponse item : items) {
            results.put(item.getQueryId(), viewCreateSqlValidationService.validate(item.getSqlText()));
        }

        return results;
    }

	@Transactional
	public List<RequestSqlValidationResult> validateDraftSql(Long requestId, Long drafterId) {
		String status = requestMapper.selectMyRequestStatusForUpdate(requestId, drafterId);
		if (status == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조회 요청을 찾을 수 없습니다.");
		}
		if (!"DRAFT".equals(status)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "초안 상태의 SQL만 검증할 수 있습니다.");
		}
		RequestDetailResponse request = requestMapper.selectMyRequestDetail(requestId, drafterId);
		if (request != null && "CREATE_VIEW".equals(request.getRequestType())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "뷰 생성 요청은 일반 조회 SQL 검증 대상이 아닙니다.");
		}

		List<RequestItemResponse> items = requestMapper.selectMyRequestItems(requestId, drafterId);
		List<RequestSqlValidationResult> results =
				requestSqlValidationService.validateItems(drafterId, items);
		for (RequestSqlValidationResult result : results) {
			int updated = requestMapper.updateDraftItemValidationStatus(
					requestId, result.queryId(), drafterId, result.validationStatus());
			if (updated != 1) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT, "SQL 검증 상태를 저장하지 못했습니다. 다시 불러와 주세요.");
			}
		}
		return results;
	}

	@Transactional
	public void updateDraft(Long requestId, RequestDraftUpdateRequest input) {
		updateDraft(requestId, input, 1L);
	}

	@Transactional
	public void updateDraft(Long requestId, RequestDraftUpdateRequest input, Long drafterId) {

		// 1. 본인 요청 확인 및 수정 중 잠금
		String status = requestMapper.selectMyRequestStatusForUpdate(requestId, drafterId);

		if (status == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조회 요청을 찾을 수 없습니다.");
		}

		// 2. 초안 상태인지 확인
		if (!"DRAFT".equals(status)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "초안 상태의 요청만 수정할 수 있습니다.");
		}
		RequestDetailResponse draftType = requestMapper.selectMyRequestDetail(requestId, drafterId);
		if (draftType != null && "CREATE_VIEW".equals(draftType.getRequestType())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "뷰 생성 요청은 뷰 생성 화면에서 수정해야 합니다.");
		}

		// 3. 제목·목적 수정
		QueryRequest request = new QueryRequest();
		request.setRequestId(requestId);
		request.setDrafterId(drafterId);
		request.setTitle(input.getTitle().strip());
		request.setPurpose(input.getPurpose().strip());

		request.setDetailContent(
		        normalizeOptionalText(input.getDetailContent())
		);
		request.setIsUrgent(input.getIsUrgent());
		request.setDesiredDate(input.getDesiredDate());

		int updatedCount = requestMapper.updateDraft(request);

		if (updatedCount != 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "요청 상태가 변경되어 수정할 수 없습니다.");
		}

		// 4. 기존 SQL 항목 삭제
		requestMapper.deleteDraftItems(requestId, drafterId);

		// 5. 수정된 SQL 목록 저장
		saveDraftItems(
		        requestId,
		        input.getSqls(),
		        input.getSqlTitles()
		);
	}

	@Transactional
	public void deleteDraft(Long requestId) {
		deleteDraft(requestId, 1L);
	}

	@Transactional
	public void deleteDraft(Long requestId, Long drafterId) {

		// 1. 본인 요청 확인 및 행 잠금
		String status = requestMapper.selectMyRequestStatusForUpdate(requestId, drafterId);

		if (status == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조회 요청을 찾을 수 없습니다.");
		}

		// 2. 초안 상태인지 확인
		if (!"DRAFT".equals(status)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "초안 상태의 요청만 삭제할 수 있습니다.");
		}

		// 3. 연결된 SQL 항목 먼저 삭제
		requestMapper.deleteDraftViewSql(requestId, drafterId);
		requestMapper.deleteDraftItems(requestId, drafterId);

		// 4. 요청서 삭제
		int deletedCount = requestMapper.deleteDraftRequest(requestId, drafterId);

		if (deletedCount != 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "요청 상태가 변경되어 삭제할 수 없습니다.");
		}
	}

	// 제출할 본인 초안을 조회하고 내용 검사
	// 반드시 제출 처리의 @Transactional 메서드 안에서 호출
	private RequestDetailResponse loadDraftForSubmission(Long requestId, Long drafterId) {

		// 수정·삭제·중복 제출과 동시에 처리되지 않도록 행 잠금
		String status = requestMapper.selectMyRequestStatusForUpdate(requestId, drafterId);

		if (status == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조회 요청을 찾을 수 없습니다.");
		}

		if (!"DRAFT".equals(status)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "초안 상태의 요청만 제출할 수 있습니다.");
		}

		RequestDetailResponse request = requestMapper.selectMyRequestDetail(requestId, drafterId);

		if (request == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조회 요청을 찾을 수 없습니다.");
		}

		String title = request.getTitle();

		if (title == null || title.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 제목을 입력하세요.");
		}

		if (title.length() > 200) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 제목은 200자 이하여야 합니다.");
		}

		String purpose = request.getPurpose();

		if (purpose == null || purpose.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 목적을 입력하세요.");
		}

		List<RequestItemResponse> items = "CREATE_VIEW".equals(request.getRequestType())
				? requestMapper.selectMyViewSqlItems(requestId, drafterId)
				: requestMapper.selectMyRequestItems(requestId, drafterId);

		if (items.isEmpty() || items.size() > 5) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제출하려면 SQL을 1개 이상, 5개 이하로 등록하세요.");
		}

		for (RequestItemResponse item : items) {
			if (item.getSqlText() == null || item.getSqlText().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 SQL 항목은 제출할 수 없습니다.");
			}
		}

		request.setItems(items);
		if ("CREATE_VIEW".equals(request.getRequestType())) {
			if (items.isEmpty() || items.size() > 5 || items.stream().anyMatch(item -> item.getSqlText() == null
					|| !item.getSqlText().matches("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\b.*"))) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CREATE VIEW SQL을 1개 이상, 5개 이하로 등록하세요.");
			}
			return request;
		}

		return request;
	}

	@Transactional(
	        transactionManager = "managementTransactionManager",
	        rollbackFor = Exception.class
	)
	public void submitRequest(Long requestId, RequestSubmitRequest input) {
	    submitRequest(requestId, input, 1L);
	}

	@Transactional(
	        transactionManager = "managementTransactionManager",
	        rollbackFor = Exception.class
	)
	public void submitRequest(
	        Long requestId,
	        RequestSubmitRequest input,
	        Long actorId) {

	    // 1. 본인 초안 잠금 및 저장된 내용 확인
	    RequestDetailResponse draft =
	            loadDraftForSubmission(requestId, actorId);

	    ApprovalLineCreator approvalLineCreator =
	            approvalLineCreatorProvider.getIfAvailable();

	    if (approvalLineCreator == null) {
	        throw new ResponseStatusException(
	                HttpStatus.SERVICE_UNAVAILABLE,
	                "결재 기능이 아직 연결되지 않았습니다."
	        );
	    }

        if ("CREATE_VIEW".equals(draft.getRequestType())) {

            // 화면에서 전달한 검사 결과 대신 저장된 SQL을 다시 검사한다.
            for (RequestItemResponse item : draft.getItems()) {
                var result = viewCreateSqlValidationService.validate(item.getSqlText());

                if (result.getStatus() == Status.BLOCKED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL " + item.getQueryOrder()
                                    + ": " + result.getMessage());
                }
            }

            // WARNING은 제출을 허용한다.
            // 경고 메시지는 다음 단계에서 화면에 연결한다.
            ApprovalLineCreator creator = approvalLineCreatorProvider.getIfAvailable();
	        if (creator == null) {
	            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "결재 기능이 아직 연결되지 않았습니다.");
	        }
	        creator.createApprovalLine(requestId, actorId,
	                input.getIntermediateApproverIds(), input.getDbApproverId());
	        if (requestMapper.submitDraft(requestId, actorId) != 1) {
	            throw new ResponseStatusException(HttpStatus.CONFLICT, "요청 상태가 변경되어 제출할 수 없습니다.");
	        }
	        return;
	    }

	    // 2. 저장된 모든 SQL 재검증
	    // 하나라도 실패하면 아래 저장 단계로 진행하지 않는다.
	    List<RequestSqlValidationResult> results =
	            requestSqlValidationService.validateForSubmission(
	                    actorId,
	                    draft.getItems()
	            );

	    boolean requestSensitive = false;

	    // 3. SQL별 검증 결과와 조회 대상 저장
	    for (RequestSqlValidationResult result : results) {
	        String itemClass = result.accessLevel();

	        if (!"GENERAL".equals(itemClass)
	                && !"SENSITIVE".equals(itemClass)) {
	            throw new IllegalStateException(
	                    "검증 결과의 SQL 분류가 올바르지 않습니다."
	            );
	        }

	        int updated = requestMapper.updateSubmittedItemValidation(
	                requestId,
	                result.queryId(),
	                itemClass
	        );

	        if (updated != 1) {
	            throw new IllegalStateException(
	                    "SQL 검증 결과 저장에 실패했습니다."
	            );
	        }

	        requestMapper.deleteQueryResourceUsage(result.queryId());

	        for (Long resourceId : result.resourceIds()) {
	            int inserted = requestMapper.insertQueryResourceUsage(
	                    result.queryId(),
	                    resourceId
	            );

	            if (inserted != 1) {
	                throw new IllegalStateException(
	                        "SQL 조회 대상 저장에 실패했습니다."
	                );
	            }
	        }

	        requestSensitive |= "SENSITIVE".equals(itemClass);
	    }

	    // 4. 하나라도 민감하면 전체 요청도 민감으로 분류
	    int classified = requestMapper.updateRequestClass(
	            requestId,
	            actorId,
	            requestSensitive ? "SENSITIVE" : "GENERAL"
	    );

	    if (classified != 1) {
	        throw new IllegalStateException(
	                "요청 분류 저장에 실패했습니다."
	        );
	    }

	    // 5. 기존 결재자 전달·결재선 생성 방식 유지
	    approvalLineCreator.createApprovalLine(
	            requestId,
	            actorId,
	            input.getIntermediateApproverIds(),
	            input.getDbApproverId()
	    );

	    // 6. 제출 상태와 제출 시각 변경
	    int submitted = requestMapper.submitDraft(requestId, actorId);

	    if (submitted != 1) {
	        throw new ResponseStatusException(
	                HttpStatus.CONFLICT,
	                "요청 상태가 변경되어 제출할 수 없습니다."
	        );
	    }
	}

	@Transactional(readOnly = true)
	public RequestPageResponse getMyRequestsPage(String keyword, String status, int page, int size) {
		return getMyRequestsPage(1L, keyword, status, page, size);
	}

	@Transactional(readOnly = true)
	public RequestPageResponse getMyRequestsPage(Long drafterId, String keyword, String status, int page, int size) {

		String searchKeyword = keyword == null ? "" : keyword.strip();
		String searchStatus = status == null ? "" : status.strip();

		validateSearchConditions(searchKeyword, searchStatus);

		RequestSearchValidator.validatePage(page, size);

		long totalCount = requestMapper.countMyRequests(drafterId, searchKeyword, searchStatus);

		long totalPages = totalCount == 0 ? 0 : (totalCount - 1) / size + 1;

		// 범위를 넘는 페이지를 요청하면 마지막 페이지로 보정
		int actualPage = totalPages == 0 ? 1 : (int) Math.min(page, totalPages);

		long offset = (long) (actualPage - 1) * size;

		List<RequestListResponse> items = totalCount == 0 ? List.of()
				: requestMapper.selectMyRequestsPage(drafterId, searchKeyword, searchStatus, offset, size);

		return new RequestPageResponse(items, actualPage, size, totalCount, totalPages);
	}

	private void validateSearchConditions(String keyword, String status) {
	    RequestSearchValidator.validateSearch(keyword, status);
	}

	@Transactional(readOnly = true)
	public RequestDashboardResponse getMyDashboard() {
		return getMyDashboard(1L);
	}

	@Transactional(readOnly = true)
	public RequestDashboardResponse getMyDashboard(Long drafterId) {

		RequestSummaryResponse summary = requestMapper.selectMyRequestSummary(drafterId);

		List<RequestListResponse> recentRequests = requestMapper.selectMyRequestsPage(drafterId, "", "", 0L, 5);

		return new RequestDashboardResponse(summary, recentRequests);
	}

	private void saveDraftItems(Long requestId, List<String> sqls, List<String> sqlTitles) {

		if (sqlTitles != null && sqlTitles.size() != sqls.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 목록과 SQL 제목 목록의 개수가 일치해야 합니다.");
		}

		for (int index = 0; index < sqls.size(); index++) {
			String title = sqlTitles == null ? null : sqlTitles.get(index);

			if (title == null || title.isBlank()) {
				title = "SQL " + (index + 1);
			} else {
				title = title.strip();
			}

			if (title.length() > 200) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 제목은 200자 이하여야 합니다.");
			}

			QueryItem item = new QueryItem();
			item.setRequestId(requestId);
			item.setQueryOrder(index + 1);
			item.setQueryTitle(title);
			item.setSqlText(sqls.get(index));
			item.setAuthoringType("MANUAL");

			if (requestMapper.insertItem(item) != 1) {
			    throw new IllegalStateException("SQL 항목 저장에 실패했습니다.");
			}
		}
	}
	
	private String normalizeOptionalText(String value) {
	    return value == null || value.isBlank()
	            ? null
	            : value.strip();
	}
    @Transactional(transactionManager = "managementTransactionManager")
    public Long redraftRequest(
            Long sourceRequestId,
            RequestDraftCreateRequest input,
            Long actorId,
            Long deptId) {

        // 본인 요청을 잠그고 반려되거나 회수된 요청인지 확인한다.
        String status = requestMapper.selectMyRequestStatusForUpdate(
                sourceRequestId, actorId);

        if (status == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "요청을 찾을 수 없습니다.");
        }
        if (!"REJECTED".equals(status) && !"WITHDRAWN".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "반려되거나 회수된 요청만 재기안할 수 있습니다.");
        }

        // 재기안 가능한 원본 요청과 SQL을 읽는다.
        RequestDetailResponse source =
                getMyRequestDetail(sourceRequestId, actorId);
        Long newRequestId = requestMapper.selectNextRequestId();

        QueryRequest draft = new QueryRequest();
        draft.setRequestId(newRequestId);
        draft.setRequestNo(createRequestNo(newRequestId));
        draft.setRequestType(source.getRequestType());
        draft.setSourceRequestId(sourceRequestId);
        draft.setDrafterId(actorId);
        draft.setDeptId(deptId);
        draft.setTitle(input.getTitle().strip());
        draft.setPurpose(input.getPurpose().strip());
        draft.setDetailContent(normalizeOptionalText(input.getDetailContent()));
        draft.setIsUrgent(input.getIsUrgent());
        draft.setDesiredDate(input.getDesiredDate());

        if (requestMapper.insertRequest(draft) != 1) {
            throw new IllegalStateException("재기안 초안 생성에 실패했습니다.");
        }

        if ("CREATE_VIEW".equals(source.getRequestType())) {
            saveRedraftViewSqlItems(newRequestId, input.getSqls(), input.getSqlTitles());
        } else {
            saveDraftItems(newRequestId, input.getSqls(), input.getSqlTitles());
        }

        // 결재선은 초안 제출 시 새로 등록한다.
        return newRequestId;
    }

    private void saveRedraftViewSqlItems(
            Long requestId,
            List<String> sqls,
            List<String> sqlTitles) {
        if (sqls == null || sqls.isEmpty() || sqls.size() > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "뷰 생성 SQL은 1개 이상, 5개 이하로 입력하세요.");
        }
        if (sqlTitles != null && sqlTitles.size() != sqls.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "SQL 목록과 SQL 제목 목록의 개수가 일치해야 합니다.");
        }

        for (int i = 0; i < sqls.size(); i++) {
            String sql = sqls.get(i) == null ? "" : sqls.get(i).strip();
            String withoutTrailingSemicolon = sql.endsWith(";")
                    ? sql.substring(0, sql.length() - 1)
                    : sql;
            if (!sql.matches("(?is)^CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\b.*")
                    || withoutTrailingSemicolon.contains(";")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "각 SQL은 CREATE VIEW 문 하나만 입력할 수 있습니다.");
            }

            String title = sqlTitles == null ? null : sqlTitles.get(i);
            title = title == null || title.isBlank()
                    ? "뷰 생성 SQL " + (i + 1)
                    : title.strip();
            if (title.length() > 200) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "SQL 제목은 200자 이하여야 합니다.");
            }
            requestMapper.insertViewSql(requestId, i + 1, title, sql);
        }
    }
    @Transactional(transactionManager = "managementTransactionManager")
    public void withdrawRequest(Long requestId, Long actorId) {

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

        // 첫 승인과 회수가 동시에 진행되지 않도록 요청 행을 잠근다.
        String status = requestMapper.selectMyRequestStatusForUpdate(
                requestId, actorId);
        if (status == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "요청을 찾을 수 없습니다.");
        }
        if (!"PENDING_APPROVAL".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결재 대기 중인 요청만 회수할 수 있습니다.");
        }

        // 첫 결재자가 승인하기 전에만 회수할 수 있다.
        if (requestMapper.withdrawRequest(requestId, actorId) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "첫 결재자가 승인하기 전까지만 회수할 수 있습니다. "
                            + "현재 계정과 결재 상태를 다시 확인하세요.");
        }

        // 실패하면 요청 상태 변경도 트랜잭션으로 롤백된다.
        int cancelled = requestMapper.cancelApprovalsForWithdrawal(requestId);
        if (cancelled < 1) {
            throw new IllegalStateException(
                    "회수할 결재선을 종료하지 못했습니다.");
        }
    }
}

