package com.sqlflow.request.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.service.RequestSqlValidationService;
import com.sqlflow.execution.service.SqlBasicValidator;
import com.sqlflow.request.domain.QueryItem;
import com.sqlflow.request.domain.QueryRequest;
import com.sqlflow.request.dto.DmlDraftRequest;
import com.sqlflow.request.dto.RequestDashboardResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.dto.RequestDraftUpdateRequest;
import com.sqlflow.request.dto.RequestItemResponse;
import com.sqlflow.request.dto.RequestListResponse;
import com.sqlflow.request.dto.RequestPageResponse;
import com.sqlflow.request.dto.RequestSqlValidationResult;
import com.sqlflow.request.dto.RequestSubmitRequest;
import com.sqlflow.request.dto.RequestSummaryResponse;
import com.sqlflow.request.dto.SelectSqlValidationResult;
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

    private final DmlSqlValidationService dmlSqlValidationService;
    private final SqlBasicValidator basicValidator;

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
		validateViewSqlItems(input, drafterId);
		Long requestId = requestMapper.selectNextRequestId();
		QueryRequest request = new QueryRequest();
		request.setRequestId(requestId);
		request.setRequestNo(createRequestNo(requestId));
		request.setRequestType("CREATE");
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

    /** 선택한 변경 유형과 SQL을 저장해 초안을 다시 열 때 복원할 수 있게 한다. */
    @Transactional(transactionManager = "managementTransactionManager")
    public Long createDmlDraft(DmlDraftRequest input, Long drafterId, Long deptId) {

        validateDmlDraftItems(input);

        Long requestId = requestMapper.selectNextRequestId();
        QueryRequest request = new QueryRequest();

        request.setRequestId(requestId);
        request.setRequestNo(createRequestNo(requestId));
        request.setRequestType(input.sqlType().name());
        request.setDrafterId(drafterId);
        request.setDeptId(deptId);
        request.setTitle(input.title().strip());
        request.setPurpose(input.purpose().strip());
        request.setDetailContent(normalizeOptionalText(input.detailContent()));
        request.setIsUrgent(input.isUrgent());
        request.setDesiredDate(input.desiredDate());

        if (requestMapper.insertRequest(request) != 1) {
            throw new IllegalStateException("데이터 변경 초안을 저장하지 못했습니다.");
        }

        saveDmlSqlItems(requestId, input);

        return requestId;
    }

    /** 작성자 본인의 DML 초안만 수정하며 기존 SQL 유형을 유지한다. */
    @Transactional(transactionManager = "managementTransactionManager")
    public void updateDmlDraft(Long requestId, DmlDraftRequest input, Long drafterId) {
        String status = requestMapper.selectMyRequestStatusForUpdate(requestId, drafterId);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다.");
        }
        if (!"DRAFT".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "초안 상태의 요청만 수정할 수 있습니다.");
        }

        RequestDetailResponse existing = requestMapper.selectMyRequestDetail(requestId, drafterId);
        if (existing == null || !input.sqlType().name().equals(existing.getRequestType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "초안의 변경 유형을 확인하세요.");
        }

        validateDmlDraftItems(input);
        QueryRequest request = new QueryRequest();
        request.setRequestId(requestId);
        request.setDrafterId(drafterId);
        request.setTitle(input.title().strip());
        request.setPurpose(input.purpose().strip());
        request.setDetailContent(normalizeOptionalText(input.detailContent()));
        request.setIsUrgent(input.isUrgent());
        request.setDesiredDate(input.desiredDate());

        if (requestMapper.updateDraft(request) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "요청 상태가 변경되어 수정할 수 없습니다.");
        }

        requestMapper.deleteDraftChangeSql(requestId, drafterId);
        saveDmlSqlItems(requestId, input);

    }

    /** DML과 CREATE의 SQL 원문은 DB_CHANGE_SQL에 저장한다. */
    private void saveDmlSqlItems(Long requestId, DmlDraftRequest input) {
        for (int index = 0; index < input.items().size(); index++) {
            var item = input.items().get(index);
            int order = index + 1;
            String title = item.queryTitle() == null || item.queryTitle().isBlank()
                    ? "SQL " + order : item.queryTitle().strip();

            if (requestMapper.insertViewSql(requestId, order, title, item.sqlText()) != 1) {
                throw new IllegalStateException("데이터 변경 SQL 항목을 저장하지 못했습니다.");
            }
        }
    }

    private void validateDmlDraftItems(DmlDraftRequest input) {
        // 화면 검사 결과 대신 서버가 저장할 SQL을 직접 검사한다.
        for (int index = 0; index < input.items().size(); index++) {
            var item = input.items().get(index);
            var result = dmlSqlValidationService.validate(input.sqlType(), item.sqlText());

            if (result.status() == DmlSqlValidationService.Status.BLOCKED) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "SQL " + (index + 1) + " · " + result.message());
            }
        }
    }

    private boolean isDmlRequest(String type) {
        return "INSERT".equals(type) || "UPDATE".equals(type) || "DELETE".equals(type);
    }

    private boolean isChangeSqlRequest(String type) {
        return "CREATE".equals(type) || isDmlRequest(type);
    }

	@Transactional(transactionManager = "managementTransactionManager", rollbackFor = Exception.class)
	public void updateViewDraft(Long requestId, ViewCreateDraftRequest input, Long drafterId) {
		validateViewSqlItems(input, drafterId);
		String status = requestMapper.selectMyRequestStatusForUpdate(requestId, drafterId);
		if (status == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다.");
		}
		if (!"DRAFT".equals(status)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "초안 상태의 요청만 수정할 수 있습니다.");
		}
		RequestDetailResponse existing = requestMapper.selectMyRequestDetail(requestId, drafterId);
		if (existing == null || !"CREATE".equals(existing.getRequestType())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CREATE 요청을 찾을 수 없습니다.");
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
		requestMapper.deleteDraftChangeSql(requestId, drafterId);
		saveViewSqlItems(requestId, input);
	}

    private void validateViewSqlItems(ViewCreateDraftRequest input, Long requesterId) {
        if (input.getItems() == null || input.getItems().isEmpty() || input.getItems().size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CREATE SQL은 1개 이상, 10개 이하로 입력하세요.");
        }

        for (ViewCreateDraftRequest.SqlEntry item : input.getItems()) {
            var result = viewCreateSqlValidationService.validate(item.getSqlText(), requesterId);

            if (result.getStatus() == Status.BLOCKED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getMessage());
            }
        }
    }

	private void saveViewSqlItems(Long requestId, ViewCreateDraftRequest input) {
		for (int i = 0; i < input.getItems().size(); i++) {
			ViewCreateDraftRequest.SqlEntry item = input.getItems().get(i);
			String title = item.getQueryTitle() == null || item.getQueryTitle().isBlank()
					? "CREATE SQL " + (i + 1) : item.getQueryTitle().strip();
			requestMapper.insertViewSql(requestId, i + 1, title, item.getSqlText().strip());
		}
	}

	private String createRequestNo(Long requestId) {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

		return "Q-" + today.toString().replace("-", "") + "-"
				+ String.format(Locale.ROOT, "%06d", requestId);
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

	    String requestType = detail.getRequestType();
	    boolean changeRequest = isChangeSqlRequest(requestType);

	    detail.setItems(changeRequest
	            ? requestMapper.selectMyViewSqlItems(requestId, drafterId)
	            : requestMapper.selectMyRequestItems(requestId, drafterId));
	    detail.setApprovals(requestMapper.selectMyRequestApprovals(requestId, drafterId));

	    // SQL별 최신 결과를 조회한 뒤 재기안 가능 여부와 복사 대상을 판단한다.
	    detail.setCanRedraft(canRedraftRequest(detail));
	    detail.setRedraftQueryIds(getRedraftItems(detail).stream()
	            .map(RequestItemResponse::getQueryId)
	            .toList());

	    return detail;
	}

	@Transactional(readOnly = true, transactionManager = "managementTransactionManager")
    public byte[] downloadApprovedViewSql(Long requestId, Long actorId) {
        List<RequestItemResponse> items =
                requestMapper.selectApprovedChangeSqlItems(requestId, actorId);

        // 두 SQL 저장 테이블은 분리돼 있지만 파일 생성 방식과 열람 조건은 같다.
        if (items.isEmpty()) {
            items = requestMapper.selectApprovedQuerySqlItems(requestId, actorId);
        }

        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다운로드 권한이 없거나 최종 승인된 SQL이 없습니다.");
        }

        StringBuilder content = new StringBuilder(
                "-- SQLFlow approved change request ")
                .append(requestId)
                .append("\n\n");

        for (RequestItemResponse item : items) {
            // 제목을 한 줄 주석으로 유지해 다음 줄이 SQL로 해석되지 않게 한다.
            String title = java.util.Objects.toString(item.getQueryTitle(), "")
                    .replace('\r', ' ')
                    .replace('\n', ' ');
            String sql = item.getSqlText().stripTrailing();

            content.append("-- ").append(title).append('\n')
                    .append(sql).append('\n');

            if (!sql.endsWith(";")) {
                // SQL 끝에 한 줄 주석이 있어도 세미콜론이 주석에 묻히지 않게 한다.
                content.append(';');
            }
            content.append("\n\n");
        }

        return content.toString()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 작성 중인 SQL을 입력 순서대로 검사한다.
     * 요청을 저장하거나 실제 SQL을 실행하지 않는다.
     */
    public List<ViewCreateSqlValidationResultDTO> validateViewSqlTexts(
            Long requesterId, List<String> sqls) {

        if (sqls == null || sqls.isEmpty() || sqls.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검사할 SQL을 1개 이상, 10개 이하로 입력하세요.");
        }

        return sqls.stream()
                .map(sql -> viewCreateSqlValidationService.validate(sql, requesterId))
                .toList();
    }

    /**
     * 작성자 본인의 CREATE 초안에 저장된 SQL을 검사한다.
     * 검사 결과는 반환만 하며 DB에는 저장하지 않는다.
     */
    @Transactional(transactionManager = "managementTransactionManager", readOnly = true)
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

        if (!"CREATE".equals(draft.getRequestType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CREATE 요청만 검사할 수 있습니다.");
        }

        if (!"DRAFT".equals(draft.getRequestStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "초안 상태에서만 검사할 수 있습니다.");
        }

        List<RequestItemResponse> items = requestMapper.selectMyViewSqlItems(requestId, actorId);

        if (items.isEmpty() || items.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CREATE SQL은 1개 이상, 10개 이하로 등록하세요.");
        }

        Map<Long, ViewCreateSqlValidationResultDTO> results = new LinkedHashMap<>();

        for (RequestItemResponse item : items) {
            results.put(item.getQueryId(), viewCreateSqlValidationService.validate(item.getSqlText(), actorId));
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
		if (request != null && isChangeSqlRequest(request.getRequestType())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "DB 변경 요청은 일반 조회 SQL 검증 대상이 아닙니다.");
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

	public List<SelectSqlValidationResult> validateSelectSqlTexts(
	        Long requesterId, List<String> sqls) {
	    return requestSqlValidationService.validateTexts(requesterId, sqls);
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
		if (draftType != null && isChangeSqlRequest(draftType.getRequestType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "DB 변경 요청은 DB 변경 화면에서 수정해야 합니다.");
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
	    String status = requestMapper.selectMyRequestStatusForUpdate(requestId, drafterId);
	    if (status == null) {
	        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조회 요청을 찾을 수 없습니다.");
	    }
	    if (!"DRAFT".equals(status)) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT, "초안 상태의 요청만 삭제할 수 있습니다.");
	    }

	    // 기안과 연결된 SQL을 먼저 삭제해야 기안의 외래 키 제약을 충족한다.
	    requestMapper.deleteDraftChangeSql(requestId, drafterId);
	    requestMapper.deleteDraftItems(requestId, drafterId);

	    if (requestMapper.deleteDraftRequest(requestId, drafterId) != 1) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT, "요청 상태가 변경되어 삭제할 수 없습니다.");
	    }
	}

    // 제출할 본인 초안을 조회한다.
    // 반드시 제출 처리의 @Transactional 메소드 안에서 호출한다.
    private RequestDetailResponse loadDraftForSubmission(Long requestId, Long drafterId) {
        // 수정·삭제·중복 제출과 동시에 처리되지 않도록 행을 잠근다.
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

        List<RequestItemResponse> items = isChangeSqlRequest(request.getRequestType())
                ? requestMapper.selectMyViewSqlItems(requestId, drafterId)
                : requestMapper.selectMyRequestItems(requestId, drafterId);

        if (items.isEmpty() || items.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제출하려면 SQL을 1개 이상, 10개 이하로 등록하세요.");
        }

        for (RequestItemResponse item : items) {
            if (item.getSqlText() == null || item.getSqlText().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 SQL 항목은 제출할 수 없습니다.");
            }
        }

        request.setItems(items);

        // 일반 CREATE 요청은 CREATE 유형으로 저장한다.
        // 이 경로의 CREATE 기본 검사는 submitRequest()에서 다시 수행한다.
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

	    if (input == null || !input.isApprovalLineValid()) {
	        throw new ResponseStatusException(
	                HttpStatus.BAD_REQUEST,
	                "결재선을 올바르게 지정하세요.");
	    }

	    // 기존 개인 ID 목록도 개인·부서 공통 형식으로 변환한다.
	    var targets = input.resolveApprovalTargets();

	    RequestDetailResponse draft =
	            loadDraftForSubmission(requestId, actorId);
	    String requestType = draft.getRequestType();

	    ApprovalLineCreator approvalLineCreator =
	            approvalLineCreatorProvider.getIfAvailable();

	    if (approvalLineCreator == null) {
	        throw new ResponseStatusException(
	                HttpStatus.SERVICE_UNAVAILABLE,
	                "결재 기능이 아직 연결되지 않았습니다.");
	    }

	    // 화면 검증 이후 수정됐을 수 있으므로 저장된 SQL을 다시 검사한다.
	    if ("CREATE".equals(requestType)) {
	        for (RequestItemResponse item : draft.getItems()) {
	            var result =
	                    viewCreateSqlValidationService.validate(item.getSqlText(), actorId);

	            if (result.getStatus() == Status.BLOCKED) {
	                throw new ResponseStatusException(
	                        HttpStatus.BAD_REQUEST,
	                        "SQL " + item.getQueryOrder()
	                                + " · " + result.getMessage());
	            }
	        }
	    } else if (isDmlRequest(requestType)) {
	        var sqlType =
	                DmlSqlValidationService.SqlType.valueOf(requestType);

	        for (RequestItemResponse item : draft.getItems()) {
	            var result =
	                    dmlSqlValidationService.validateForReview(actorId, sqlType, item.getSqlText());

	            // WARNING은 결재 검토 대상으로 제출할 수 있다.
	            if (result.status() == DmlSqlValidationService.Status.BLOCKED) {
	                throw new ResponseStatusException(
	                        HttpStatus.BAD_REQUEST,
	                        "SQL " + item.getQueryOrder()
	                                + " · " + result.message());
	            }
	        }
	    } else {
	        List<RequestSqlValidationResult> results =
	                requestSqlValidationService.validateForSubmission(
	                        actorId, draft.getItems());

	        boolean requestSensitive = false;

	        for (RequestSqlValidationResult result : results) {
	            String itemClass = result.accessLevel();

	            if (!"GENERAL".equals(itemClass)
	                    && !"SENSITIVE".equals(itemClass)) {
	                throw new IllegalStateException(
	                        "검증 결과의 SQL 분류가 올바르지 않습니다.");
	            }

	            if (requestMapper.updateSubmittedItemValidation(
	                    requestId, result.queryId(), itemClass) != 1) {
	                throw new IllegalStateException(
	                        "SQL 검증 결과 저장에 실패했습니다.");
	            }

	            requestMapper.deleteQueryResourceUsage(result.queryId());

	            for (Long resourceId : result.resourceIds()) {
	                if (requestMapper.insertQueryResourceUsage(
	                        result.queryId(), resourceId) != 1) {
	                    throw new IllegalStateException(
	                            "SQL 조회 대상 저장에 실패했습니다.");
	                }
	            }

	            requestSensitive |= "SENSITIVE".equals(itemClass);
	        }

	        String requestClass =
	                requestSensitive ? "SENSITIVE" : "GENERAL";

	        if (requestMapper.updateRequestClass(
	                requestId, actorId, requestClass) != 1) {
	            throw new IllegalStateException(
	                    "요청 분류 저장에 실패했습니다.");
	        }
	    }

	    // 개인·부서를 검증하고 사용자가 지정한 순서대로 결재선을 생성한다.
	    approvalLineCreator.createApprovalLineWithTargets(requestId, actorId, targets);

	    // 결재선 생성과 제출 상태 변경은 같은 트랜잭션에서 처리한다.
	    if (requestMapper.submitDraft(requestId, actorId) != 1) {
	        throw new ResponseStatusException(
	                HttpStatus.CONFLICT,
	                "요청 상태가 변경되어 제출할 수 없습니다.");
	    }
	}

	@Transactional(readOnly = true)
	public RequestPageResponse getMyRequestsPage(
	        String keyword,
	        String status,
	        int page,
	        int size) {

	    return getMyRequestsPage(
	            1L, keyword, "", status, page, size);
	}

	@Transactional(readOnly = true)
	public RequestPageResponse getMyRequestsPage(
	        Long drafterId,
	        String keyword,
	        String status,
	        int page,
	        int size) {

	    return getMyRequestsPage(
	            drafterId, keyword, "", status, page, size);
	}

	@Transactional(readOnly = true)
	public RequestPageResponse getMyRequestsPage(
	        Long drafterId,
	        String keyword,
	        String requestType,
	        String status,
	        int page,
	        int size) {

	    String searchKeyword = keyword == null ? "" : keyword.strip();
	    String searchRequestType = requestType == null ? "" : requestType.strip();
	    String searchStatus = status == null ? "" : status.strip();

	    validateSearchConditions(searchKeyword, searchStatus);
	    RequestSearchValidator.validateRequestType(searchRequestType);
	    RequestSearchValidator.validatePage(page, size);

	    long totalCount = requestMapper.countMyRequests(
	            drafterId,
	            searchKeyword,
	            searchRequestType,
	            searchStatus);

	    long totalPages = totalCount == 0
	            ? 0
	            : (totalCount - 1) / size + 1;

	    // 범위를 넘는 페이지를 요청하면 마지막 페이지로 보정
	    int actualPage = totalPages == 0
	            ? 1
	            : (int) Math.min(page, totalPages);

	    long offset = (long) (actualPage - 1) * size;

	    List<RequestListResponse> items = totalCount == 0
	            ? List.of()
	            : requestMapper.selectMyRequestsPage(
	                    drafterId,
	                    searchKeyword,
	                    searchRequestType,
	                    searchStatus,
	                    offset,
	                    size);

	    return new RequestPageResponse(
	            items,
	            actualPage,
	            size,
	            totalCount,
	            totalPages);
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

		List<RequestListResponse> recentRequests =
		        requestMapper.selectMyRequestsPage(
		                drafterId, "", "", "", 0L, 5);

		return new RequestDashboardResponse(summary, recentRequests);
	}

	private void saveDraftItems(
	        Long requestId,
	        List<String> sqls,
	        List<String> sqlTitles) {

	    if (sqls == null
	            || sqlTitles == null
	            || sqlTitles.size() != sqls.size()) {
	        throw new ResponseStatusException(
	                HttpStatus.BAD_REQUEST,
	                "SQL 목록과 SQL 제목 목록의 개수가 일치해야 합니다.");
	    }

	    for (int index = 0; index < sqls.size(); index++) {
            var basic = basicValidator.validate("SELECT", sqls.get(index));
            if (basic.blocked()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, basic.warning());
	        String title = sqlTitles.get(index);

	        if (title == null || title.isBlank()) {
	            throw new ResponseStatusException(
	                    HttpStatus.BAD_REQUEST,
	                    "SQL " + (index + 1) + "의 제목을 입력하세요.");
	        }

	        title = title.strip();

	        if (title.length() > 200) {
	            throw new ResponseStatusException(
	                    HttpStatus.BAD_REQUEST,
	                    "SQL 제목은 200자 이하여야 합니다.");
	        }

	        QueryItem item = new QueryItem();
	        item.setRequestId(requestId);
	        item.setQueryOrder(index + 1);
	        item.setQueryTitle(title);
	        item.setSqlText(sqls.get(index));
	        item.setAuthoringType("MANUAL");

	        if (requestMapper.insertItem(item) != 1) {
	            throw new IllegalStateException(
	                    "SQL 항목 저장에 실패했습니다.");
	        }
	    }
	}

	private String normalizeOptionalText(String value) {
	    return value == null || value.isBlank()
	            ? null
	            : value.strip();
	}
	
	/**
	 * 작성자 본인의 요청을 조회한 뒤 사용하는 재기안 허용 조건.
	 * 반려·회수는 기존 정책을 유지하고, 실행 결과에 따른 재기안은 처리가 끝난 요청만 허용한다.
	 */
	private boolean canRedraftRequest(RequestDetailResponse request) {
	    if (request == null) {
	        return false;
	    }

	    String status = request.getRequestStatus();
	    if ("REJECTED".equals(status) || "WITHDRAWN".equals(status)) {
	        return true;
	    }

	    List<RequestItemResponse> items = request.getItems();
	    if (!"APPROVED".equals(request.getApprovalStatus())
	            || items == null || items.isEmpty()) {
	        return false;
	    }

	    String requestType = request.getRequestType();

	    if ("SELECT".equals(requestType)) {
	        // 조회 요청은 전체 실행 집계가 실패 또는 일부 실패로 끝나야 한다.
	        if (!"FAILED".equals(status) && !"PARTIAL_FAILED".equals(status)) {
	            return false;
	        }

	        boolean allFinished = items.stream().allMatch(item ->
	                "SUCCESS".equals(item.getLatestExecutionStatus())
	                        || "FAILED".equals(item.getLatestExecutionStatus()));

	        return allFinished && items.stream().anyMatch(item -> isFailedRedraftItem(requestType, item));
	    }

	    if (!isChangeSqlRequest(requestType) || !"READY".equals(status)) {
	        return false;
	    }

	    // CREATE·DML은 모든 SQL의 외부 처리 결과가 등록돼야 한다.
	    boolean allFinished = items.stream().allMatch(item ->
	            "COMPLETED".equals(item.getChangeResultStatus())
	                    || "FAILED".equals(item.getChangeResultStatus())
	                    || "CANCELLED".equals(item.getChangeResultStatus()));

	    return allFinished && items.stream().anyMatch(item -> isFailedRedraftItem(requestType, item));
	}

	/** 요청 유형에 맞는 실행 결과 필드로 실패·취소 여부를 확인한다. */
	private boolean isFailedRedraftItem(String requestType, RequestItemResponse item) {
	    if ("SELECT".equals(requestType)) {
	        return "FAILED".equals(item.getLatestExecutionStatus());
	    }

	    return isChangeSqlRequest(requestType)
	            && ("FAILED".equals(item.getChangeResultStatus())
	                    || "CANCELLED".equals(item.getChangeResultStatus()));
	}

	/**
	 * 반려·회수는 전체 SQL을, 실행 실패 재기안은 실패·취소 SQL만 가져온다.
	 * 원본 SQL과 실행 이력은 변경하지 않는다.
	 */
	private List<RequestItemResponse> getRedraftItems(RequestDetailResponse request) {
	    if (!canRedraftRequest(request) || request.getItems() == null) {
	        return List.of();
	    }

	    String status = request.getRequestStatus();
	    if ("REJECTED".equals(status) || "WITHDRAWN".equals(status)) {
	        return request.getItems();
	    }

	    return request.getItems().stream()
	            .filter(item -> isFailedRedraftItem(request.getRequestType(), item))
	            .toList();
	}

	@Transactional(transactionManager = "managementTransactionManager")
	public Long redraftRequest(Long sourceRequestId, RequestDraftCreateRequest input,
	        Long actorId, Long deptId) {

	    // 본인 요청을 잠가 상태 변경과 재기안 처리가 겹치지 않도록 한다.
	    String status = requestMapper.selectMyRequestStatusForUpdate(sourceRequestId, actorId);
	    if (status == null) {
	        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다.");
	    }

	    // 화면을 연 뒤 실행 결과가 변경됐을 수 있으므로 저장 시 다시 판단한다.
	    RequestDetailResponse source = getMyRequestDetail(sourceRequestId, actorId);
	    if (!canRedraftRequest(source)) {
	        throw new ResponseStatusException(HttpStatus.CONFLICT,
	                "현재 재기안할 수 없는 요청입니다. 반려·회수되었거나, 모든 SQL 처리가 끝난 후 실패·취소 항목이 있는 요청만 재기안할 수 있습니다.");
	    }

	    String requestType = source.getRequestType();
	    Long newRequestId = requestMapper.selectNextRequestId();

	    QueryRequest draft = new QueryRequest();
	    draft.setRequestId(newRequestId);
	    draft.setRequestNo(createRequestNo(newRequestId));
	    draft.setRequestType(requestType);
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

	    // 재기안 화면에서 사용자가 확인·수정한 SQL을 기존 유형별 경로로 저장한다.
	    if ("CREATE".equals(requestType)) {
	        saveRedraftViewSqlItems(newRequestId, input.getSqls(), input.getSqlTitles());
	    } else if (isDmlRequest(requestType)) {
	        saveRedraftDmlSqlItems(newRequestId, requestType, input);
	    } else {
	        saveDraftItems(newRequestId, input.getSqls(), input.getSqlTitles());
	    }

	    // 원본과 실행 이력은 보존하고, 새 결재선은 새 초안을 제출할 때 생성한다.
	    return newRequestId;
	}

    private void saveRedraftDmlSqlItems(Long requestId, String requestType, RequestDraftCreateRequest input) {
        List<String> sqls = input.getSqls();
        List<String> titles = input.getSqlTitles();

        if (sqls == null || sqls.isEmpty() || sqls.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "변경 SQL은 1개 이상, 10개 이하로 입력하세요.");
        }

        if (titles != null && titles.size() != sqls.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 목록과 SQL 제목 목록의 개수가 일치해야 합니다.");
        }

        List<DmlDraftRequest.SqlEntry> items = new java.util.ArrayList<>();

        for (int i = 0; i < sqls.size(); i++) {
            String sql = sqls.get(i);
            String title = titles == null ? null : titles.get(i);

            if (sql == null || sql.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "변경 SQL을 입력하세요.");
            }

            if (title != null && title.strip().length() > 200) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 제목은 200자 이하여야 합니다.");
            }

            items.add(new DmlDraftRequest.SqlEntry(title, sql));
        }

        // 원본 기안의 유형으로 검사하고, 기존 DML 초안과 같은 경로에 저장한다.
        DmlDraftRequest dmlDraft = new DmlDraftRequest(
                DmlSqlValidationService.SqlType.valueOf(requestType),
                input.getTitle(), input.getPurpose(), input.getDetailContent(),
                input.getIsUrgent(), input.getDesiredDate(), items);

        validateDmlDraftItems(dmlDraft);
        saveDmlSqlItems(requestId, dmlDraft);
    }

    private void saveRedraftViewSqlItems(Long requestId, List<String> sqls, List<String> sqlTitles) {
        if (sqls == null || sqls.isEmpty() || sqls.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CREATE SQL은 1개 이상, 10개 이하로 입력하세요.");
        }

        if (sqlTitles != null && sqlTitles.size() != sqls.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 목록과 SQL 제목 목록의 개수가 일치해야 합니다.");
        }

        for (int i = 0; i < sqls.size(); i++) {
            String sql = sqls.get(i) == null ? "" : sqls.get(i).strip();
            var result = viewCreateSqlValidationService.validate(sql);

            if (result.getStatus() == Status.BLOCKED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getMessage());
            }

            String title = sqlTitles == null ? null : sqlTitles.get(i);
            title = title == null || title.isBlank() ? "CREATE SQL " + (i + 1) : title.strip();

            if (title.length() > 200) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 제목은 200자 이하여야 합니다.");
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
