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
        REJECTED: '반려'
    });

    const validationNames = {
        PENDING: '검증 전',
        VALID: '검증 완료',
        INVALID: '검증 실패'
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

    function renderItems(items, validationMessages = new Map()) {
        if (!Array.isArray(items) || items.length === 0) {
            return '<p class="empty">등록된 SQL이 없습니다.</p>';
        }

        return items.map(item => {
            const validationLabel =
                validationNames[item.validationStatus]
                || item.validationStatus
                || '-';

            const authoringLabel =
                authoringNames[item.authoringType]
                || item.authoringType
                || '-';

            const validationMessage =
                validationMessages.get(item.queryId);

            const validationDetail =
                item.validationStatus === 'INVALID'
                    ? '<p class="sql-validation-message" role="alert">'
                        + '<strong>검증 실패 원인:</strong> '
                        + esc(
                            validationMessage
                            || '전체 SQL 검증을 다시 실행해 원인을 확인하세요.'
                        )
                        + '</p>'
                    : '';

            return '<article class="result-item">'
                + '<div class="panel-heading">'
                + '<h3>'
                + esc(item.queryTitle || ('SQL ' + item.queryOrder))
                + '</h3>'
                + badge(validationLabel)
                + '</div>'
                + '<p class="caption">'
                + '순서 ' + esc(item.queryOrder)
                + ' · ' + esc(authoringLabel)
                + '</p>'
                + '<pre><code>'
                + esc(item.sqlText)
                + '</code></pre>'
                + validationDetail
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
            REJECTED: '반려'
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

    return Object.freeze({
        statusNames,
        classNames,
        approvalNames,
        formatDate,
        renderItems,
        renderHistory,
        renderApprovalRows
    });
})();