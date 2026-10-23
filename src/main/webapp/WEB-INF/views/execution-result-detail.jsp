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

		    <a id="execution-result-download"
		       class="button"
		       hidden>
		        CSV 다운로드
		    </a>
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

<section class="panel section-gap">
    <div class="panel-heading">
        <h2>CSV 결과 미리보기</h2>
        <span id="csv-preview-count" class="caption"></span>
    </div>

    <p class="caption">
        아래로 스크롤하면 25행씩 추가됩니다. 최대 100행까지 표시합니다.
    </p>

    <div id="csv-preview-scroll"
         class="table-scroll"
         style="height: 360px; overflow: auto;"
         tabindex="0"
         aria-label="CSV 미리보기 스크롤 영역">

        <table>
            <caption class="sr-only">SQL 실행 결과 CSV 미리보기</caption>
            <thead>
                <tr id="csv-preview-columns"></tr>
            </thead>
            <tbody id="csv-preview-rows"></tbody>
        </table>
    </div>

    <p id="csv-preview-message" role="status"></p>

    <button id="csv-preview-retry"
            type="button"
            class="button"
            hidden>
        다시 불러오기
    </button>
</section>

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
		
		const downloadLink = $('#execution-result-download');

		const canDownload =
		    detail.status === 'SUCCESS'
		    && hasResult
		    && !detail.deletedAt;

		downloadLink.hidden = !canDownload;
		downloadLink.removeAttribute('href');

		if (canDownload) {
		    downloadLink.href =
		        BASE
		        + '/api/execution-results/mine/'
		        + encodeURIComponent(detail.executionId)
		        + '/csv';
		}

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

<script>
(() => {
    const scrollBox = document.getElementById('csv-preview-scroll');
    const headerRow = document.getElementById('csv-preview-columns');
    const tableBody = document.getElementById('csv-preview-rows');
    const countLabel = document.getElementById('csv-preview-count');
    const message = document.getElementById('csv-preview-message');
    const retryButton = document.getElementById('csv-preview-retry');

    const pageSize = 25;

    let columns = null;
    let nextOffset = 0;
    let loadedRows = 0;
    let hasMore = true;
    let loading = false;
    let failed = false;

    function createHeader(names) {
        const fragment = document.createDocumentFragment();

        names.forEach(function (name) {
            const th = document.createElement('th');

            th.scope = 'col';
            th.textContent = name;
            th.style.whiteSpace = 'nowrap';

            fragment.appendChild(th);
        });

        headerRow.appendChild(fragment);
    }

    function appendRows(rows) {
        const fragment = document.createDocumentFragment();

        rows.forEach(function (values) {
            const tr = document.createElement('tr');

            values.forEach(function (value) {
                const td = document.createElement('td');

                // CSV 값을 HTML로 해석하지 않고 문자 그대로 표시한다.
                td.textContent = value == null ? '' : String(value);

                // 값 내부의 줄바꿈을 유지한다.
                td.style.whiteSpace = 'pre-wrap';
                td.style.minWidth = '100px';
                td.style.verticalAlign = 'top';

                tr.appendChild(td);
            });

            fragment.appendChild(tr);
        });

        // 기존 행을 유지하고 새 행만 뒤에 붙인다.
        tableBody.appendChild(fragment);
    }

    function validateResponse(data) {
        if (!Array.isArray(data.columns)
                || !Array.isArray(data.rows)
                || typeof data.hasMore !== 'boolean'
                || !Number.isSafeInteger(data.nextOffset)) {
            throw new Error('미리보기 응답 형식이 올바르지 않습니다.');
        }

        if (data.rows.some(function (row) {
            return !Array.isArray(row)
                || row.length !== data.columns.length;
        })) {
            throw new Error('미리보기 컬럼 수가 일치하지 않습니다.');
        }

        if (data.nextOffset !== nextOffset + data.rows.length
                || (data.hasMore && data.rows.length === 0)) {
            throw new Error('다음 데이터 위치가 올바르지 않습니다.');
        }

        if (columns !== null
                && JSON.stringify(columns) !== JSON.stringify(data.columns)) {
            throw new Error('조회 중 CSV 컬럼이 변경되었습니다.');
        }
    }

    async function loadNextRows() {
        if (loading || !hasMore || failed) {
            return;
        }

        loading = true;
        retryButton.hidden = true;
        scrollBox.setAttribute('aria-busy', 'true');
        message.textContent = '데이터를 불러오는 중입니다.';

        try {
            const params = new URLSearchParams({
                offset: String(nextOffset),
                limit: String(pageSize)
            });

            // 기존 JSP에서 사용하는 공통 api() 함수를 이용한다.
			const data = await api(
			    '/execution-results/mine/'
			    + encodeURIComponent(executionId)
			    + '/preview?'
			    + params.toString()
			);

            validateResponse(data);

            if (columns === null) {
                columns = data.columns.slice();
                createHeader(columns);
            }

            appendRows(data.rows);

            nextOffset = data.nextOffset;
            loadedRows += data.rows.length;
            hasMore = data.hasMore;

            countLabel.textContent = loadedRows + '행 표시';

            if (loadedRows === 0) {
                message.textContent = '조회 결과가 없습니다.';
            } else if (hasMore) {
                message.textContent =
                    '아래로 스크롤하면 다음 데이터를 불러옵니다.';
            } else {
                message.textContent =
                    '미리보기 끝입니다. 전체 결과는 CSV 다운로드로 확인할 수 있습니다.';
            }

        } catch (error) {
            failed = true;

            message.textContent =
                error.message || '미리보기를 불러오지 못했습니다.';

            retryButton.hidden = false;

        } finally {
            loading = false;
            scrollBox.setAttribute('aria-busy', 'false');
        }
    }

    scrollBox.addEventListener('scroll', function () {
        const remaining =
            scrollBox.scrollHeight
            - scrollBox.scrollTop
            - scrollBox.clientHeight;

        // 표의 맨 아래에서 60px 이내로 접근하면 추가 조회한다.
        if (remaining <= 60) {
            loadNextRows();
        }
    }, { passive: true });

    retryButton.addEventListener('click', function () {
        failed = false;
        loadNextRows();
    });

    loadNextRows();
})();
</script>

<%@ include file="fragments/bottom.jspf" %>