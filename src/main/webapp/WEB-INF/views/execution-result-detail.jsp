<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div id="execution-detail-config"
     data-execution-id="<c:out value='${executionId}'/>"
     hidden>
</div>

<a class="back-link"
   href="${pageContext.request.contextPath}/execution-results">
    ← 실행 결과 목록
</a>

<div class="page-heading">
    <h1 id="execution-detail-title">SQL 실행 결과 상세</h1>

    <button id="execution-detail-refresh"
            type="button"
            class="button">
        새로고침
    </button>
</div>

<p id="execution-detail-loading" role="status">
    실행 정보를 불러오는 중입니다.
</p>

<div id="execution-detail-content" hidden>
    <section class="panel">
        <div class="panel-heading">
            <h2>실행 정보</h2>
            <span id="execution-detail-status"></span>
        </div>

        <div class="detail-meta">
            <span>
                기안서 번호
                <a id="execution-request-link" class="table-title"></a>
            </span>
            <span>요청 제목 <strong id="execution-request-title"></strong></span>
            <span>배치 번호 <strong id="execution-batch-id"></strong></span>
            <span>실행 번호 <strong id="execution-id"></strong></span>
            <span>실행 차수 <strong id="execution-attempt"></strong></span>
        </div>

        <div class="status-grid">
            <div>
                <span>실행 시작</span>
                <strong id="execution-started-at"></strong>
            </div>
            <div>
                <span>실행 종료</span>
                <strong id="execution-finished-at"></strong>
            </div>
            <div>
                <span>소요 시간</span>
                <strong id="execution-duration"></strong>
            </div>
        </div>

        <p class="caption">
            실행 등록 시각:
            <span id="execution-requested-at"></span>
        </p>
    </section>

    <section class="panel section-gap">
        <div class="panel-heading">
            <h2>SQL 원문</h2>
        </div>

        <pre><code id="execution-sql"></code></pre>
    </section>

    <section id="execution-error-section"
             class="panel section-gap"
             hidden>
        <div class="panel-heading">
            <h2>실행 오류</h2>
        </div>

        <p class="error-text">
            오류 코드:
            <span id="execution-error-code"></span>
        </p>

        <pre id="execution-error-message"></pre>
    </section>

    <section class="panel section-gap">
        <div class="panel-heading">
            <h2>조회 결과</h2>
        </div>

        <p id="execution-result-message" role="status"></p>

        <div id="execution-result-metadata" class="status-grid" hidden>
            <div>
                <span>조회 건수</span>
                <strong id="execution-result-count"></strong>
            </div>
            <div>
                <span>결과 파일 크기</span>
                <strong id="execution-result-size"></strong>
            </div>
            <div>
                <span>결과 보관 만료 시각</span>
                <strong id="execution-result-expires"></strong>
            </div>
        </div>
    </section>
</div>

<script>
    const executionId =
        $('#execution-detail-config').dataset.executionId;

    let executionDetailVersion = 0;

    function formatExecutionDetailDate(value) {
        return value
            ? value.replace('T', ' ').substring(0, 19)
            : '—';
    }

    function renderExecutionDetail(detail) {
        const statusNames = {
            PENDING: '실행 대기',
            RUNNING: '실행 중',
            SUCCESS: '성공',
            FAILED: '실패'
        };

        const statusKind =
            detail.status === 'SUCCESS'
                ? 'success'
                : detail.status === 'PENDING' || detail.status === 'RUNNING'
                    ? 'waiting'
                    : 'neutral';

        $('#execution-detail-title').textContent =
            detail.queryTitle || '제목 없는 SQL';

        $('#execution-detail-status').innerHTML = badge(
            statusNames[detail.status] || detail.status || '—',
            statusKind
        );

        const requestLink = $('#execution-request-link');

        requestLink.textContent = detail.requestNo || '요청 보기';
        requestLink.href =
            BASE + '/requests/' + encodeURIComponent(detail.requestId);

        $('#execution-request-title').textContent =
            detail.requestTitle || '—';

        $('#execution-batch-id').textContent = detail.batchId ?? '—';
        $('#execution-id').textContent = detail.executionId ?? '—';
        $('#execution-attempt').textContent = detail.attemptNo ?? '—';

        $('#execution-requested-at').textContent =
            formatExecutionDetailDate(detail.requestedAt);

        $('#execution-started-at').textContent =
            formatExecutionDetailDate(detail.startedAt);

        $('#execution-finished-at').textContent =
            formatExecutionDetailDate(detail.finishedAt);

        $('#execution-duration').textContent =
            detail.durationMs == null ? '—' : detail.durationMs + ' ms';

        $('#execution-sql').textContent =
            detail.sqlText || '저장된 SQL이 없습니다.';

        $('#execution-error-section').hidden =
            detail.status !== 'FAILED';

        $('#execution-error-code').textContent =
            detail.errorCode || '—';

        $('#execution-error-message').textContent =
            detail.errorMessage || '저장된 오류 내용이 없습니다.';

        const hasResult = detail.resultId != null;

        $('#execution-result-metadata').hidden = !hasResult;

        $('#execution-result-count').textContent =
            detail.rowCount == null
                ? '—'
                : String(detail.rowCount) + '건';

        $('#execution-result-size').textContent =
            detail.fileSize == null
                ? '—'
                : String(detail.fileSize) + ' bytes';

        $('#execution-result-expires').textContent =
            formatExecutionDetailDate(detail.expiresAt);

        let resultMessage;

        switch (detail.status) {
            case 'PENDING':
                resultMessage = '아직 실행되지 않았습니다.';
                break;
            case 'RUNNING':
                resultMessage = 'SQL을 실행하고 있습니다. 잠시 후 새로고침해 주세요.';
                break;
            case 'FAILED':
                resultMessage = 'SQL 실행에 실패했습니다. 오류 내용을 확인해 주세요.';
                break;
            case 'SUCCESS':
                resultMessage = detail.deletedAt
                    ? '보관된 결과 파일이 삭제되었습니다.'
                    : hasResult
                        ? '실행이 완료되어 결과 파일이 저장됐습니다.'
                        : '실행은 성공했지만 저장된 결과 정보가 없습니다.';
                break;
            default:
                resultMessage = '실행 상태를 확인해 주세요.';
        }

        $('#execution-result-message').textContent = resultMessage;
    }

    async function loadExecutionDetail() {
        const version = ++executionDetailVersion;

        $('#execution-detail-refresh').disabled = true;
        $('#execution-detail-loading').hidden = false;
        $('#execution-detail-loading').textContent =
            '실행 정보를 불러오는 중입니다.';

        $('#execution-detail-content').hidden = true;
        $('#page-message').hidden = true;

        try {
            const detail = await api(
                '/execution-results/mine/'
                + encodeURIComponent(executionId)
            );

            if (version !== executionDetailVersion) {
                return;
            }

            renderExecutionDetail(detail);

            $('#execution-detail-loading').hidden = true;
            $('#execution-detail-content').hidden = false;

        } catch (error) {
            if (version !== executionDetailVersion) {
                return;
            }

            $('#execution-detail-loading').textContent =
                '실행 정보를 불러오지 못했습니다. 목록으로 돌아가 확인해 주세요.';

            notify(error.message, true);

        } finally {
            if (version === executionDetailVersion) {
                $('#execution-detail-refresh').disabled = false;
            }
        }
    }

    $('#execution-detail-refresh').addEventListener(
        'click',
        loadExecutionDetail
    );

    loadExecutionDetail();
</script>

<%@ include file="fragments/bottom.jspf" %>