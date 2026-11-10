<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="execution-detail-page">
    <nav class="execution-detail-breadcrumb" aria-label="현재 위치">
        <a href="${pageContext.request.contextPath}/execution-queue">실행 목록</a>
        <span aria-hidden="true">/</span>
        <span>실행 요청 상세</span>
    </nav>

    <section class="execution-document" aria-labelledby="document-title">
        <div class="execution-document-heading">
            <h1 id="document-title">기안서</h1>
            <button id="detail-refresh" class="button" type="button">
                상태 새로고침
            </button>
        </div>

        <div class="execution-document-overview">
            <dl id="request-info" class="execution-document-meta"></dl>

            <section class="execution-approval-section"
                     aria-labelledby="approval-history-title">
                <div class="execution-approval-heading">
                    <h2 id="approval-history-title">결재란</h2>
                    <span id="approval-overall-status" class="badge neutral">조회 중</span>
                </div>
                <ol id="approval-history" class="execution-approval-list">
                    <li class="execution-approval-empty">결재 이력을 불러오는 중입니다.</li>
                </ol>
            </section>
        </div>

        <div class="execution-document-title-row">
            <span>제목</span>
            <strong id="request-title">불러오는 중</strong>
        </div>

        <section class="execution-document-section">
            <h2>1. 요청 목적</h2>
            <p id="request-purpose">불러오는 중</p>
        </section>

        <section class="execution-document-section">
            <h2>2. 상세 내용</h2>
            <p id="request-description">불러오는 중</p>
        </section>

        <section class="execution-sql-section" aria-labelledby="execution-sql-title">
            <div class="execution-sql-heading">
                <div>
                    <h2 id="execution-sql-title">3. 실행 대상 SQL</h2>
                    <p id="sql-summary" class="caption"></p>
                </div>
                <div id="batch-actions" class="execution-queue-actions"></div>
            </div>

            <div id="sql-items" class="queue-sql-list">
                <p class="execution-sql-empty">SQL 목록을 불러오는 중입니다.</p>
            </div>
        </section>

        <p id="detail-message"
           class="execution-queue-message"
           role="status"
           aria-live="polite"></p>
    </section>
</div>

<script>
(function () {
    'use strict';

    const requestId = '${requestId}';
    const $id = id => document.getElementById(id);

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
        SUCCESS: '성공',
        FAILED: '실패'
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
        const list = $id('approval-history');
        const overallStatus = $id('approval-overall-status');

        if (!approvalHistory.length) {
            list.innerHTML =
                '<li class="execution-approval-empty">'
                + '등록된 결재 이력이 없습니다.'
                + '</li>';
            return;
        }

        overallStatus.textContent =
            detail && detail.approvalStatus === 'APPROVED'
                ? '최종 승인'
                : approvalStatusLabel(
                    approvalHistory[approvalHistory.length - 1].status
                );

        overallStatus.className =
            'badge '
            + statusKind(
                detail && detail.approvalStatus === 'APPROVED'
                    ? 'APPROVED'
                    : approvalHistory[approvalHistory.length - 1].status
            );

        list.innerHTML = approvalHistory.map(function (item, index) {
            const person = [
                item.approverName,
                item.deptName,
                item.positionName
            ].filter(Boolean).join(' · ');

            const decidedAt = item.decidedAt
                ? formatShortDateTime(item.decidedAt)
                : '';

            return '<li class="execution-approval-card'
                + (item.status === 'APPROVED'
                    && index === approvalHistory.length - 1
                    ? ' is-current'
                    : '')
                + '">'
                + '<span class="execution-approval-type">'
                + esc(approvalLabel(item.approvalType))
                + '</span>'
                + '<strong class="execution-approval-person">'
                + esc(person || '결재자 미지정')
                + '</strong>'
                + '<span class="execution-approval-status '
                + 'status-' + esc(statusKind(item.status)) + '">'
                + esc(approvalStatusLabel(item.status))
                + '</span>'
                + (decidedAt
                    ? '<time>' + esc(decidedAt) + '</time>'
                    : '')
                + '</li>';
        }).join('');
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

        $id('request-title').textContent = data.title || '제목 없음';
        $id('request-purpose').textContent =
            data.purpose || '요청 목적이 입력되지 않았습니다.';
        $id('request-description').textContent =
            data.detailContent || '상세 내용이 입력되지 않았습니다.';

        $id('request-info').innerHTML =
            '<div><dt>문서번호</dt><dd>'
            + esc(data.requestNo || '—')
            + '</dd></div>'
            + '<div><dt>기안 부서</dt><dd>'
            + esc(data.deptName || '—')
            + '</dd></div>'
            + '<div><dt>기안자</dt><dd>'
            + esc(data.drafterName || '—')
            + '</dd></div>'
            + '<div><dt>작성일</dt><dd>'
            + esc(formatDateTime(data.submittedAt))
            + '</dd></div>'
            + '<div><dt>처리 상태</dt><dd>'
            + esc(requestStatusLabel(data))
            + '</dd></div>';

        $id('sql-summary').textContent =
            '총 ' + items.length + '건 · 대기 '
            + items.filter(item =>
                item.executionId == null
                && item.executionStatus == null
            ).length
            + '건';

        const hasExecutedSql = items.some(item =>
            item.executionStatus === 'SUCCESS'
            || item.executionStatus === 'FAILED'
        );

        const wholeLabel =
            data.batchId == null && hasExecutedSql
                ? '남은 SQL 전체 실행'
                : '전체 실행';

        $id('batch-actions').innerHTML =
            '<button id="run-all" class="button primary" type="button"'
            + (canRunWhole(data) ? '' : ' disabled')
            + '>' + esc(wholeLabel) + '</button>';

        $id('sql-items').innerHTML = items.map(function (item, index) {
            const rawStatus = item.executionStatus;
            const label = rawStatus == null
                ? '실행 대기'
                : sqlStateLabel[rawStatus] || rawStatus;

            const error = item.errorMessage
                ? '<p class="queue-error"><strong>실패 원인:</strong> '
                    + esc(item.errorMessage)
                    + '</p>'
                : '';

            return '<details class="execution-sql-card"'
                + (index === 0 ? ' open' : '')
                + '>'
                + '<summary class="execution-sql-summary">'
                + '<span class="execution-sql-name">'
                + esc(String(item.queryOrder || index + 1).padStart(2, '0'))
                + '　'
                + esc(item.queryTitle || 'SQL')
                + '</span>'
                + '<span class="execution-sql-status '
                + 'status-' + esc(statusKind(rawStatus))
                + '">' + esc(label) + '</span>'
                + '</summary>'
                + '<div class="execution-sql-body">'
                + '<pre><code>' + esc(item.sqlText || '') + '</code></pre>'
                + '<div class="queue-sql-footer">'
                + error
                + runButton(data, item)
                + '</div>'
                + '</div>'
                + '</details>';
        }).join('') || '<p class="execution-sql-empty">등록된 SQL이 없습니다.</p>';

        renderApprovalHistory();

        $id('detail-refresh').disabled = posting;
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
            $id('approval-history').innerHTML =
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
                error.message + ' 상태 새로고침으로 다시 확인하세요.',
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
                        : ' 상태 새로고침으로 처리 상태를 확인하세요.'),
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

    $id('detail-refresh').addEventListener('click', async function () {
        if (posting) {
            return;
        }

        const refreshed = await load();

        if (refreshed) {
            showMessage('실행 상태를 갱신했습니다.', false);
        }
    });

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