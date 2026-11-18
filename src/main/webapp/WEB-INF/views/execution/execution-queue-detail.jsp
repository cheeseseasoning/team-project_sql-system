<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="execution-detail-page request-detail-page request-document-page">
    <a class="back-link" href="${pageContext.request.contextPath}/execution-queue">← 쿼리 실행 대기 목록</a>
    <article class="request-document" aria-labelledby="detail-title">
        <header class="request-document-heading">
            <p id="request-document-heading" class="request-document-caption">Q-Pass · 데이터 조회 기안서</p>
            <h1 id="detail-title">요청을 불러오는 중입니다.</h1>
        </header>
        <c:set var="requestDetailReadOnly" value="${true}" />
        <c:set var="requestDetailDocument" value="${true}" />
        <%@ include file="/WEB-INF/views/fragments/request-detail-body.jspf" %>
        <footer class="request-document-footer">
            <p id="detail-message" class="execution-queue-message" role="status" aria-live="polite"></p>
        </footer>
    </article>
</div>
<script src="${pageContext.request.contextPath}/js/request-detail-renderer.js?v=20261116-department-labels"></script>

<script>
(function () {
    'use strict';

    const requestId = '${requestId}';
    const $id = id => document.getElementById(id);

    const batchActions = document.createElement('div');
    batchActions.id = 'batch-actions';
    batchActions.className = 'execution-batch-actions';
    document.querySelector('.request-sql-heading').appendChild(batchActions);

    // 전체 건수는 공통 제목 안에, 대기 건수는 제목 옆 보조 문구로 표시한다.
    const pendingCount = document.createElement('span');
    pendingCount.id = 'execution-pending-count';
    pendingCount.className = 'caption';
    pendingCount.setAttribute('aria-live', 'polite');
    document.querySelector('.request-sql-heading-main h2').after(pendingCount);

    let detail = null;
    let approvalHistory = [];
    let posting = false;
    let dataFresh = false;
    let timer = null;
    let loadVersion = 0;
    let disposed = false;

    const requestStateLabel = {
        READY: '실행 대기',
        RUNNING: '처리 중',
        COMPLETED: '실행 완료',
        PARTIAL_FAILED: '일부 실패',
        FAILED: '실패'
    };

    const sqlStateLabel = {
        PENDING: '실행 대기',
        RUNNING: '실행 중',
        SUCCESS: '실행 성공',
        FAILED: '실행 실패'
    };

    const approvalTypeLabel = {
        INTERMEDIATE: '중간 결재',
        DIRECTOR: '부서장 결재',
        DB_EXECUTION: 'DB팀 결재',
        DB_APPROVAL: 'DB팀 결재'
    };

    const approvalStateLabel = {
        APPROVED: '승인',
        PENDING: '결재 대기',
        REJECTED: '반려',
        CANCELED: '취소',
        WITHDRAWN: '회수'
    };

    const terminalStatuses = new Set([
        'COMPLETED',
        'PARTIAL_FAILED',
        'FAILED'
    ]);

    function formatDateTime(value) {
        if (!value) {
            return '—';
        }

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return String(value).replace('T', ' ');
        }

        return date.toLocaleString('ko-KR', {
            timeZone: 'Asia/Seoul',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function formatShortDateTime(value) {
        if (!value) {
            return '';
        }

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return String(value).replace('T', ' ');
        }

        return date.toLocaleString('ko-KR', {
            timeZone: 'Asia/Seoul',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function showMessage(message, error) {
        const element = $id('detail-message');
        element.textContent = message || '';
        element.classList.toggle('error-text', Boolean(error));
    }

    function sqlItems(data) {
        return Array.isArray(data && data.sqlItems)
            ? data.sqlItems
            : [];
    }

    function isRequestRunnable(data) {
        return data.approvalStatus === 'APPROVED'
            && ['READY', 'RUNNING'].includes(data.requestStatus);
    }

    function hasActiveSql(data) {
        return sqlItems(data).some(item =>
            item.executionStatus === 'PENDING'
            || item.executionStatus === 'RUNNING'
        );
    }

    function canRunIndividual(data, item) {
        return dataFresh
            && !posting
            && isRequestRunnable(data)
            && data.batchStatus !== 'RUNNING'
            && !hasActiveSql(data)
            && item.executionId == null
            && item.executionStatus == null;
    }

    function canRunWhole(data) {
        const items = sqlItems(data);

        if (!dataFresh
            || posting
            || !isRequestRunnable(data)
            || items.length === 0) {
            return false;
        }

        if (data.batchId != null) {
            return data.batchStatus === 'PENDING'
                && data.requestStatus === 'READY'
                && items.every(item =>
                    item.executionId != null
                    && item.executionStatus === 'PENDING'
                );
        }

        return data.batchStatus == null
            && !hasActiveSql(data)
            && items.some(item =>
                item.executionId == null
                && item.executionStatus == null
            );
    }

    function needsPolling(data) {
        if (!data || terminalStatuses.has(data.requestStatus)) {
            return false;
        }

        const items = sqlItems(data);
        const running = data.batchStatus === 'RUNNING'
            || items.some(item => item.executionStatus === 'RUNNING');

        const aggregating = data.requestStatus === 'RUNNING'
            && items.length > 0
            && items.every(item =>
                item.executionStatus === 'SUCCESS'
                || item.executionStatus === 'FAILED'
            );

        return running || aggregating;
    }

    function stopPolling() {
        if (timer !== null) {
            clearTimeout(timer);
            timer = null;
        }
    }

    function schedulePolling() {
        if (disposed || posting || !dataFresh || !needsPolling(detail)) {
            stopPolling();
            return;
        }

        if (timer !== null) {
            return;
        }

        timer = setTimeout(function () {
            timer = null;
            load();
        }, 1500);
    }

    function requestStatusLabel(data) {
        if (terminalStatuses.has(data.requestStatus)) {
            return requestStateLabel[data.requestStatus]
                || data.requestStatus;
        }

        const items = sqlItems(data);

        if (data.batchStatus === 'RUNNING') {
            return '전체 SQL 실행 중';
        }

        if (items.some(item => item.executionStatus === 'RUNNING')) {
            return '개별 SQL 실행 중';
        }

        if (data.requestStatus === 'RUNNING') {
            const allFinished = items.length > 0
                && items.every(item =>
                    item.executionStatus === 'SUCCESS'
                    || item.executionStatus === 'FAILED'
                );

            return allFinished ? '결과 집계 중' : '일부 SQL 처리 완료';
        }

        return requestStateLabel[data.requestStatus]
            || data.requestStatus
            || '상태 확인 중';
    }

    function statusKind(status) {
        if (status === 'SUCCESS'
            || status === 'COMPLETED'
            || status === 'APPROVED') {
            return 'success';
        }

        if (status === 'FAILED'
            || status === 'PARTIAL_FAILED'
            || status === 'REJECTED') {
            return 'danger';
        }

        if (status === 'RUNNING' || status === 'PENDING') {
            return 'waiting';
        }

        return 'neutral';
    }

    function approvalLabel(type) {
        return approvalTypeLabel[type] || type || '결재';
    }

    function approvalStatusLabel(status) {
        return approvalStateLabel[status] || status || '상태 미확인';
    }

    function renderApprovalHistory() {
        window.RequestDetailRenderer.renderApprovalPreview(approvalHistory.map(function (item, index) {
            return {
                order: item.approvalOrder || index + 1,
                name: item.approverName,
                deptName: item.deptName,
                positionName: item.positionName,
                status: item.status,
                decidedAt: item.decidedAt
            };
        }));
    }

    function runButton(data, item) {
        const enabled = canRunIndividual(data, item);

        return '<button type="button" class="button sql-run"'
            + ' data-query="' + esc(item.queryId) + '"'
            + (enabled ? '' : ' disabled')
            + '>이 SQL 실행</button>';
    }

    function render(data) {
        const items = sqlItems(data);
        const renderer = window.RequestDetailRenderer;

        // 요청 상세와 같은 정보표·본문·결재란·SQL 표시를 재사용한다.
        renderer.renderRequestInfo({ ...data, items });
        pendingCount.textContent = '대기 ' + items.filter(item =>
            item.executionId == null && item.executionStatus == null).length + '건';
        $id('request-info').hidden = false;
        const hasExecutedSql = items.some(item =>
            item.executionStatus === 'SUCCESS' || item.executionStatus === 'FAILED');
        const wholeLabel = data.batchId == null && hasExecutedSql ? '남은 SQL 전체 실행' : '전체 실행';
        $id('batch-actions').innerHTML =
            '<button id="run-all" class="button primary" type="button"'
            + (canRunWhole(data) ? '' : ' disabled') + '>' + esc(wholeLabel) + '</button>';

        $id('detail-items').innerHTML = renderer.renderItems(items, new Map(), { readOnly: true });
        $id('detail-items').querySelectorAll('.request-sql-item').forEach(function (card, index) {
            const item = items[index];
            const label = item.executionStatus == null ? '실행 대기'
                : sqlStateLabel[item.executionStatus] || item.executionStatus;
            // 검증 결과와 실행 상태를 제목 오른쪽 한 그룹으로 표시한다.
            const summary = card.querySelector('summary');
            const states = document.createElement('span');
            states.className = 'execution-item-states';
            states.appendChild(summary.querySelector('.request-sql-validation-state'));
            states.insertAdjacentHTML('beforeend',
                '<span class="execution-state-separator" aria-hidden="true">·</span>'
                + '<span class="execution-item-status status-' + esc(statusKind(item.executionStatus)) + '">'
                + esc(label) + '</span>');
            // 접힌 상태에서는 상태만 오른쪽에, 펼치면 그 옆에 실행 버튼을 표시한다.
            const toolbar = document.createElement('span');
            toolbar.className = 'execution-item-toolbar';
            toolbar.appendChild(states);
            toolbar.insertAdjacentHTML('beforeend',
                '<span class="execution-item-actions">' + runButton(data, item) + '</span>');
            summary.appendChild(toolbar);
            toolbar.querySelector('.sql-run').addEventListener('click', function (event) {
                // 실행 버튼 클릭으로 SQL 접기가 함께 동작하지 않도록 한다.
                event.preventDefault();
                event.stopPropagation();
            });
            if (item.errorMessage) {
                card.querySelector('.request-sql-body').insertAdjacentHTML('beforeend',
                    '<p class="queue-error" role="status"><strong>실패 원인:</strong> '
                    + esc(item.errorMessage) + '</p>');
            }
        });
        renderApprovalHistory();
        bindActions();
    }

    async function loadApprovalHistory() {
        try {
            const history = await api(
                '/approvals/' + encodeURIComponent(requestId) + '/history'
            );
            approvalHistory = Array.isArray(history) ? history : [];
        } catch (error) {
            approvalHistory = [];
            $id('request-approval-list').innerHTML =
                '<li class="execution-approval-empty">'
                + '결재 이력을 불러오지 못했습니다.'
                + '</li>';
        }
    }

    async function load() {
        const version = ++loadVersion;

        try {
            const results = await Promise.all([
                api('/executions/requests/' + encodeURIComponent(requestId)),
                loadApprovalHistory()
            ]);

            if (disposed || version !== loadVersion) {
                return false;
            }

            const data = results[0];

            if (!data || !Array.isArray(data.sqlItems)) {
                throw new Error('실행 상태 응답을 확인할 수 없습니다.');
            }

            detail = data;
            dataFresh = true;
            render(detail);
            schedulePolling();

            return true;
        } catch (error) {
            if (disposed || version !== loadVersion) {
                return false;
            }

            dataFresh = false;
            stopPolling();

            if (detail) {
                render(detail);
            }

            showMessage(
                error.message + ' 잠시 후 화면을 다시 열어 확인하세요.',
                true
            );

            return false;
        }
    }

    async function submitAction(action, successMessage) {
        if (posting || !dataFresh || !detail) {
            return;
        }

        posting = true;
        stopPolling();
        render(detail);

        let requestError = null;

        try {
            await action();
            showMessage(successMessage, false);
        } catch (error) {
            requestError = error;
            showMessage(error.message, true);
        } finally {
            const refreshed = await load();

            posting = false;

            if (!disposed && detail) {
                render(detail);
            }

            if (!disposed && requestError) {
                showMessage(
                    requestError.message
                    + (refreshed
                        ? ''
                        : ' 잠시 후 화면을 다시 열어 처리 상태를 확인하세요.'),
                    true
                );
            }

            schedulePolling();
        }
    }

    function bindActions() {
        const runAllButton = $id('run-all');

        if (runAllButton) {
            runAllButton.onclick = async function () {
                if (!canRunWhole(detail)) {
                    return;
                }

                const legacyBatch = detail.batchId != null;
                const targets = sqlItems(detail).filter(item =>
                    legacyBatch
                        ? item.executionStatus === 'PENDING'
                        : item.executionId == null
                            && item.executionStatus == null
                );

                if (!confirm(
                    'SQL ' + targets.length
                    + '건을 요청 순서대로 전체 실행할까요?'
                )) {
                    return;
                }

                const path = legacyBatch
                    ? '/executions/batches/'
                        + encodeURIComponent(detail.batchId)
                        + '/run'
                    : '/executions/requests/'
                        + encodeURIComponent(requestId)
                        + '/run';

                await submitAction(
                    () => api(path, { method: 'POST' }),
                    '전체 실행을 접수했습니다.'
                );
            };
        }

        document.querySelectorAll('.sql-run').forEach(function (button) {
            button.onclick = async function () {
                const queryId = button.dataset.query;
                const item = sqlItems(detail).find(row =>
                    String(row.queryId) === queryId
                );

                if (!item || !canRunIndividual(detail, item)) {
                    return;
                }

                if (!confirm('선택한 SQL을 실행할까요?')) {
                    return;
                }

                await submitAction(
                    () => api(
                        '/executions/requests/'
                            + encodeURIComponent(requestId)
                            + '/queries/'
                            + encodeURIComponent(queryId)
                            + '/run',
                        { method: 'POST' }
                    ),
                    'SQL 실행을 접수했습니다.'
                );
            };
        });
    }

    window.addEventListener('pagehide', function () {
        disposed = true;
        loadVersion++;
        stopPolling();
    });

    window.addEventListener('pageshow', function (event) {
        if (!event.persisted) {
            return;
        }

        disposed = false;
        dataFresh = false;

        if (detail) {
            render(detail);
        }

        load();
    });

    load();
})();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
