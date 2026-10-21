package com.sqlflow.request.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

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
import com.sqlflow.request.mapper.RequestMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequestService {

	private final RequestMapper requestMapper;

	private final ObjectProvider<ApprovalLineCreator> approvalLineCreatorProvider;

	private static final Set<String> SEARCHABLE_STATUSES = Set.of("DRAFT", "PENDING_APPROVAL", "READY", "RUNNING",
			"RETRY_REVIEW", "COMPLETED", "PARTIAL_FAILED", "FAILED", "REJECTED", "WITHDRAWN");
	
	private final RequestSqlValidationService requestSqlValidationService;

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

		detail.setItems(
		        requestMapper.selectMyRequestItems(requestId, drafterId)
		);

		detail.setApprovals(
		        requestMapper.selectMyRequestApprovals(requestId, drafterId)
		);

		return detail;
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

		List<RequestItemResponse> items = requestMapper.selectMyRequestItems(requestId, drafterId);

		if (items.isEmpty() || items.size() > 5) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제출하려면 SQL을 1개 이상, 5개 이하로 등록하세요.");
		}

		for (RequestItemResponse item : items) {
			if (item.getSqlText() == null || item.getSqlText().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 SQL 항목은 제출할 수 없습니다.");
			}
		}

		request.setItems(items);

		return request;
	}

	@Transactional
	public void submitRequest(Long requestId, RequestSubmitRequest input) {
		submitRequest(requestId, input, 1L);
	}

	@Transactional
	public void submitRequest(Long requestId, RequestSubmitRequest input, Long actorId) {

		// 1. 본인 초안 잠금 및 저장된 내용 검사
		RequestDetailResponse draft = loadDraftForSubmission(requestId, actorId);

		// 2. 결재 기능 연결 확인
		ApprovalLineCreator approvalLineCreator = approvalLineCreatorProvider.getIfAvailable();

		if (approvalLineCreator == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "결재 기능이 아직 연결되지 않았습니다.");
		}

		// 3. 결재자 재검증 및 결재선 생성
		// 준오 구현에서 APPROVAL_STATUS도 PENDING으로 설정
		//저장된 SQL과 접근 권한을 검사한다.
		//실패하면 결재선 생성과 제출 처리를 중단한다.
		requestSqlValidationService.validate(
		     actorId,
		     draft.getItems().stream()
		             .map(RequestItemResponse::getSqlText)
		             .toList());
		
		//중간 결재자 목록을 포함해 결재선을 생성한다.
		approvalLineCreator.createApprovalLine(
		     requestId,
		     actorId,
		     input.getIntermediateApproverIds(),
		     input.getDbApproverId());

		// 4. 요청 상태 및 제출 시각 변경
		int updatedCount = requestMapper.submitDraft(requestId, actorId);

		if (updatedCount != 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "요청 상태가 변경되어 제출할 수 없습니다.");
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

		if (page < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 번호는 1 이상이어야 합니다.");
		}

		if (size < 1 || size > 50) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 크기는 1 이상, 50 이하여야 합니다.");
		}

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

		if (keyword.length() > 120) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어는 120자 이하여야 합니다.");
		}

		if (!status.isEmpty() && !SEARCHABLE_STATUSES.contains(status)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바르지 않은 요청 상태입니다.");
		}
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

			requestMapper.insertItem(item);
		}
	}
	
	private String normalizeOptionalText(String value) {
	    return value == null || value.isBlank()
	            ? null
	            : value.strip();
	}
	
	@Transactional
	public Long redraftRequest(
	        Long sourceRequestId,
	        Long actorId,
	        Long deptId) {

	    // 1. 본인 요청 확인 및 동시 처리 방지를 위한 잠금
	    String status = requestMapper.selectMyRequestStatusForUpdate(
	            sourceRequestId, actorId);

	    if (status == null) {
	        throw new ResponseStatusException(
	                HttpStatus.NOT_FOUND,
	                "조회 요청을 찾을 수 없습니다.");
	    }

	    // 2. 반려된 요청만 재기안 가능
	    if (!"REJECTED".equals(status)) {
	        throw new ResponseStatusException(
	                HttpStatus.CONFLICT,
	                "반려된 요청만 재기안할 수 있습니다.");
	    }

	    // 3. 원본 요청과 SQL 조회
	    RequestDetailResponse source =
	            getMyRequestDetail(sourceRequestId, actorId);

	    // 4. 새 요청 ID와 요청번호 발급
	    Long newRequestId = requestMapper.selectNextRequestId();

	    QueryRequest draft = new QueryRequest();
	    draft.setRequestId(newRequestId);
	    draft.setRequestNo(createRequestNo(newRequestId));
	    draft.setSourceRequestId(sourceRequestId);

	    // 새 초안의 작성자·부서는 현재 로그인 사용자 기준
	    draft.setDrafterId(actorId);
	    draft.setDeptId(deptId);

	    draft.setTitle(source.getTitle());
	    draft.setPurpose(source.getPurpose());
	    draft.setDetailContent(source.getDetailContent());
	    draft.setIsUrgent(source.getIsUrgent());
	    draft.setDesiredDate(source.getDesiredDate());

	    // 요청·결재 상태는 DB 기본값인 DRAFT / NOT_REQUIRED 사용
	    if (requestMapper.insertRequest(draft) != 1) {
	        throw new IllegalStateException("재기안 초안 생성에 실패했습니다.");
	    }

	    // 5. SQL 내용과 제목만 복사하여 새로운 SQL 항목 생성
	    List<String> sqls = source.getItems().stream()
	            .map(RequestItemResponse::getSqlText)
	            .toList();

	    List<String> sqlTitles = source.getItems().stream()
	            .map(RequestItemResponse::getQueryTitle)
	            .toList();

	    saveDraftItems(newRequestId, sqls, sqlTitles);

	    return newRequestId;
	}
}
