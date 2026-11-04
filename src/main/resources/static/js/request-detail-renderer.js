window.RequestDetailRenderer = (() => {
    'use strict';

    const statusNames = Object.freeze({
        DRAFT: '초안',
        PENDING_APPROVAL: '결재 대기',
        READY: '실행 대기',
        RUNNING: '실행 중',
        RETRY_REVIEW: '재실행 검토',
        COMPLETED: '실행 완료',
        PARTIAL_FAILED: '일부 실패',
        FAILED: '실패',
        REJECTED: '반려',
        WITHDRAWN: '회수'
    });

    const classNames = Object.freeze({
        GENERAL: '일반 조회',
        SENSITIVE: '민감 조회'
    });

	const approvalNames = Object.freeze({
	    NOT_REQUIRED: '결재 불필요',
	    PENDING: '결재 대기',
	    APPROVED: '승인',
	    REJECTED: '반려',
	    CANCELLED: '결재 취소'
	});

	const validationNames = {
	    PENDING: '검증 전',
	    VALID: '검증 완료',
	    INVALID: '검증 실패',
	    WARNING: '검사 불확실',
	    BLOCKED: '제출 차단'
	};

    const authoringNames = {
        MANUAL: '직접 작성',
        AI: 'AI 작성'
    };

    function formatDate(value) {
        return value
            ? String(value).replace('T', ' ').substring(0, 19)
            : '-';
    }
	
	function renderChangeResult(item) {
	    const statusNames = {
	        COMPLETED: '성공',
	        FAILED: '실패',
	        CANCELLED: '취소'
	    };
	    const changeLabels = {
	        CORRECTION: { name: '정정', reason: '정정 사유' },
	        REEXECUTION: { name: '재실행', reason: '재실행 사유' }
	    };

	    const status = statusNames[item.changeResultStatus];
	    if (!status) {
	        return '';
	    }

	    // 현재 메모는 결과에서, 현재 결과로 바뀐 사유는 최신 이력에서 읽는다.
	    const latestChange = item.changeResultHistory?.[0];
	    const change = changeLabels[latestChange?.changeType];
	    const resultLabel = change ? change.name + '·' + status : status;

	    const resultNote = item.changeResultNote
	        ? '<p class="caption">처리 메모 · ' + esc(item.changeResultNote) + '</p>'
	        : '';
	    const changeReason = change && latestChange.changeReason
	        ? '<p class="caption">' + esc(change.reason) + ' · ' + esc(latestChange.changeReason) + '</p>'
	        : '';

	    return '<p class="caption">외부 처리 결과 · ' + esc(resultLabel) + '</p>'
	        + resultNote
	        + changeReason;
	}

	function renderItems(items, validationMessages = new Map(), { readOnly = false } = {}) {
	    if (!Array.isArray(items) || items.length === 0) {
	        return '<p class="empty">등록된 SQL이 없습니다.</p>';
	    }

	    const changeResultNames = {
	        COMPLETED: '성공',
	        FAILED: '실패',
	        CANCELLED: '취소'
	    };

	    return items.map(item => {
	        const validationLabel = validationNames[item.validationStatus] || item.validationStatus || '-';
	        const authoringLabel = authoringNames[item.authoringType] || item.authoringType || '-';
	        const validationMessage = validationMessages.get(item.queryId);
	        const showValidationDetail = Boolean(validationMessage)
	            || ['INVALID', 'WARNING', 'BLOCKED'].includes(item.validationStatus);

	        let validationClass = '';
	        if (item.validationStatus === 'WARNING') {
	            validationClass = ' is-warning';
	        } else if (item.validationStatus === 'VALID') {
	            validationClass = ' is-valid';
	        }

	        const defaultValidationMessage = readOnly
	            ? '저장된 SQL의 검증 상태를 확인해 주세요.'
	            : '전체 SQL 검증을 다시 실행해 안내를 확인하세요.';
	        const validationDetail = showValidationDetail
	            ? '<p class="sql-validation-message' + validationClass + '" role="status">'
	                + esc(validationMessage || defaultValidationMessage) + '</p>'
	            : '';

	        // 상세 API가 반환한 외부 처리 결과를 해당 SQL 아래에 표시한다.
	        const resultLabel = changeResultNames[item.changeResultStatus];
	        const resultNote = item.changeResultNote
	            ? '<p class="caption">처리 메모 · ' + esc(item.changeResultNote) + '</p>'
	            : '';
	        const resultDetail = resultLabel
	            ? '<p class="caption">외부 처리 결과 · ' + esc(resultLabel) + '</p>' + resultNote
	            : '';

	        return '<article class="result-item">'
	            + '<div class="panel-heading">'
	            + '<h3>' + esc(item.queryTitle || ('SQL ' + item.queryOrder)) + '</h3>'
	            + badge(validationLabel)
	            + '</div>'
	            + '<p class="caption">순서 ' + esc(item.queryOrder) + ' · ' + esc(authoringLabel) + '</p>'
	            + '<pre><code>' + esc(item.sqlText) + '</code></pre>'
	            + validationDetail
	            + resultDetail
	            + '</article>';
	    }).join('');
	}

    function renderHistory(items) {
        if (!Array.isArray(items) || items.length === 0) {
            return '<li class="empty">등록된 결재 이력이 없습니다.</li>';
        }

        const typeNames = {
            INTERMEDIATE: '중간 결재',
            DIRECTOR: '부서장 결재',
            DB_EXECUTION: 'DB팀 실행 승인'
        };

        const historyStatusNames = {
            WAITING: '대기',
            PENDING: '결재 대기',
            APPROVED: '승인',
            REJECTED: '반려',
            CANCELLED: '취소'
        };

        return items.map(item => {
            const typeName =
                typeNames[item.approvalType]
                || item.approvalType
                || '-';

            const statusName =
                historyStatusNames[item.status]
                || item.status
                || '-';

            const comment = item.commentText
                ? '<p class="approval-history-comment">'
                    + esc(item.commentText)
                    + '</p>'
                : '';

            return '<li class="approval-history-item">'
                + '<div class="panel-heading">'
                + '<strong>' + esc(typeName) + '</strong>'
                + badge(statusName)
                + '</div>'
                + '<p>' + esc(item.approverName || '-')
                + ' · ' + esc(item.deptName || '-')
                + ' · ' + esc(item.positionName || '-') + '</p>'
                + comment
                + '<p class="caption">처리 시각: '
                + esc(
                    item.decidedAt
                        ? formatDate(item.decidedAt)
                        : '아직 처리되지 않았습니다.'
                )
                + '</p>'
                + '</li>';
        }).join('');
    }

    function renderApprovalRows(rows) {
		const lineStatusNames = {
		    PENDING: '결재 차례',
		    WAITING: '대기',
		    APPROVED: '승인',
		    REJECTED: '반려',
		    CANCELLED: '취소'
		};

        return rows.map(row => {
            const organization = [
                row.deptName,
                row.positionName
            ].filter(Boolean).join(' · ');

            const statusText = row.status
                ? lineStatusNames[row.status] || row.status
                : '';

            return '<li>'
                + '<span class="request-approval-number" aria-hidden="true">'
                + esc(row.order)
                + '</span>'
                + '<div>'
                + '<strong>' + esc(row.role) + '</strong>'
                + '<p>' + esc(row.name || '사용자 정보 없음') + '</p>'
                + '<span>' + esc(organization || '—') + '</span>'
                + (
                    statusText
                        ? '<span>상태: ' + esc(statusText) + '</span>'
                        : ''
                )
                + '</div>'
                + '</li>';
        }).join('');
    }
	
	function renderRequestInfo(request) {
	    $('#request-number').textContent = request.requestNo;
	    $('#detail-title').textContent = request.title;

	    $('#detail-state').innerHTML = badge(
	        statusNames[request.requestStatus]
	        || request.requestStatus
	        || '-'
	    );

	    $('#drafter-name').textContent = request.drafterName || '-';
	    $('#dept-name').textContent = request.deptName || '-';
	    $('#request-type').textContent = request.requestType === 'CREATE_VIEW'
	        ? '뷰 생성 요청'
	        : '조회 요청';
	    $('#request-purpose').textContent = request.purpose || '-';

	    const isUrgent = request.isUrgent === 'Y';

	    $('#request-urgency').textContent = isUrgent ? '긴급' : '일반';
	    $('#request-urgency').classList.toggle('urgent-badge', isUrgent);

	    $('#request-desired-date').textContent =
	        request.desiredDate || '미지정';

	    $('#request-detail-content').textContent =
	        request.detailContent || '입력된 상세 내용이 없습니다.';

	    $('#request-class').textContent =
	        classNames[request.requestClass]
	        || request.requestClass
	        || '미분류';

	    $('#approval-status').textContent =
	        request.requestStatus === 'DRAFT'
	            ? '제출 전'
	            : (
	                approvalNames[request.approvalStatus]
	                || request.approvalStatus
	                || '-'
	            );

	    $('#created-at').textContent = formatDate(request.createdAt);

	    $('#submitted-at').textContent = request.submittedAt
	        ? formatDate(request.submittedAt)
	        : '아직 제출하지 않았습니다.';

	    $('#sql-count').textContent =
	        '총 ' + (Array.isArray(request.items) ? request.items.length : 0) + '개';
	}

	function renderSavedApprovalLine(request) {
	    const isDraft = request.requestStatus === 'DRAFT';

	    const approvals = Array.isArray(request.approvals)
	        ? request.approvals
	        : [];

	    if (approvals.length === 0) {
	        $('#request-approval-list').innerHTML =
	            '<li><p class="muted">'
	            + (
	                isDraft
	                    ? '결재선을 등록해 주세요.'
	                    : '저장된 결재선이 없습니다.'
	            )
	            + '</p></li>';

	        $('#request-approval-badge').textContent =
	            isDraft ? '미등록' : '결재선 없음';

	        $('#approval-preview-help').textContent = isDraft
	            ? '결재선 등록 버튼에서 결재자를 구성할 수 있습니다.'
	            : '저장된 결재선 정보를 확인해 주세요.';

	        return;
	    }

	    const roleNames = {
	        INTERMEDIATE: '중간 결재자',
	        DIRECTOR: '소속 부서장',
	        DB_EXECUTION: 'DB팀 실행 승인'
	    };

	    const rows = [...approvals]
	        .sort((a, b) => a.approvalOrder - b.approvalOrder)
	        .map(approval => ({
	            order: approval.approvalOrder,
	            role: roleNames[approval.approvalType]
	                || approval.approvalType
	                || '결재자',
	            name: approval.approverName,
	            deptName: approval.deptName,
	            positionName: approval.positionName,
	            status: approval.status
	        }));

	    $('#request-approval-list').innerHTML = renderApprovalRows(rows);

		const summaryNames = {
		    PENDING: '결재 진행 중',
		    APPROVED: '결재 완료',
		    REJECTED: '반려',
		    CANCELLED: '결재 취소'
		};

	    $('#request-approval-badge').textContent =
	        summaryNames[request.approvalStatus]
	        || request.approvalStatus
	        || '결재선';

	    $('#approval-preview-help').textContent =
	        '저장된 결재선과 현재 처리 상태입니다.';
	}

    return Object.freeze({
        statusNames,
        classNames,
        approvalNames,
        formatDate,
        renderItems,
        renderHistory,
        renderApprovalRows,
		renderRequestInfo,
		renderSavedApprovalLine
    });
})();
