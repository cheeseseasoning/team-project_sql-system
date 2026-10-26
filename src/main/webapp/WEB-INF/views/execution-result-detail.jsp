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

			<button id="execution-result-download"
			        type="button"
			        class="button"
			        hidden>
			    CSV 다운로드
			</button>
		</div>

        <p id="execution-result-message" role="status"></p>
		
		<p id="execution-download-message"
		   role="status"
		   aria-live="polite"></p>

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

<section id="execution-preview-section"
     	 class="panel section-gap"
    	 hidden>
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
	let downloading = false;
	
	// 결과 화면의 GET 요청에만 사용한다.
	// 공통 api()를 수정하지 않고 HTTP 상태를 전달한다.
	async function executionResultApi(path) {
	    const controller = new AbortController();

	    const timer = setTimeout(function () {
	        controller.abort();
	    }, 30000);

	    try {
	        const response = await fetch(
	            BASE + '/api' + path,
	            {
	                method: 'GET',
	                credentials: 'same-origin',
	                cache: 'no-store',
	                headers: {
	                    Accept: 'application/json'
	                },
	                signal: controller.signal
	            }
	        );

	        const data = await response.json().catch(function (error) {
	            if (error.name === 'AbortError') {
	                throw error;
	            }

	            return null;
	        });

	        if (!response.ok) {
	            const error = new Error(
	                data?.message
	                || data?.detail
	                || '결과 요청을 처리하지 못했습니다.'
	            );

	            error.status = response.status;

	            throw error;
	        }

	        if (data == null) {
	            throw new Error('서버 응답을 확인할 수 없습니다.');
	        }

	        return data;

	    } catch (error) {
	        if (error.name === 'AbortError') {
	            throw new Error(
	                '응답 시간이 초과되었습니다. 다시 시도해 주세요.'
	            );
	        }

	        throw error;

	    } finally {
	        clearTimeout(timer);
	    }
	}

	function executionResultErrorMessage(error) {
	    if (error.status === 401) {
	        return '로그인이 만료되었습니다. 다시 로그인해 주세요.';
	    }

	    if (error.status >= 500) {
	        return '서버에서 결과를 제공하지 못했습니다. 잠시 후 다시 시도해 주세요.';
	    }

	    return error.message || '결과를 불러오지 못했습니다.';
	}

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
		
		// 현재 API의 expiresAt은 한국 시간 기준 LocalDateTime이다.
		const expiresAt = detail.expiresAt
		    ? Date.parse(detail.expiresAt + '+09:00')
		    : null;

		const expired =
		    expiresAt !== null
		    && (!Number.isFinite(expiresAt) || expiresAt <= Date.now());

		const downloadButton = $('#execution-result-download');

		const canRequestDownload =
		    detail.status === 'SUCCESS'
		    && hasResult
		    && !detail.deletedAt
		    && !expired;

		downloadButton.hidden = !canRequestDownload;
		downloadButton.disabled = downloading;

		$('#execution-download-message').textContent = '';

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
				    : expired
				        ? '결과 파일의 보관 기간이 만료되었습니다.'
				        : hasResult
				            ? '실행이 완료되어 결과 파일이 저장됐습니다.'
				            : '실행은 성공했지만 저장된 결과 정보가 없습니다.';
				break;
            default:
                resultMessage = '실행 상태를 확인해 주세요.';
        }

        $('#execution-result-message').textContent = resultMessage;
		
		return canRequestDownload;
    }
	
	async function downloadExecutionCsv() {
	    const button = $('#execution-result-download');
	    const message = $('#execution-download-message');

	    if (downloading || button.hidden) {
	        return;
	    }

	    const version = executionDetailVersion;

	    downloading = true;
	    button.disabled = true;
	    button.textContent = '다운로드 준비 중…';
	    message.textContent = 'CSV 파일을 요청하고 있습니다.';

	    try {
	        const response = await fetch(
	            BASE
	            + '/api/execution-results/mine/'
	            + encodeURIComponent(executionId)
	            + '/csv',
	            {
	                method: 'GET',
	                credentials: 'same-origin',
	                cache: 'no-store',
	                headers: {
	                    Accept: 'text/csv, application/json'
	                }
	            }
	        );

	        if (!response.ok) {
	            const data = await response.json().catch(() => null);

	            const error = new Error(
	                data?.message
	                || data?.detail
	                || 'CSV 다운로드에 실패했습니다.'
	            );

	            error.status = response.status;

	            throw error;
	        }

	        const contentType =
	            response.headers.get('Content-Type') || '';

	        if (!contentType.toLowerCase().startsWith('text/csv')) {
	            throw new Error(
	                'CSV 파일 응답을 받지 못했습니다. 로그인 상태를 확인해 주세요.'
	            );
	        }

	        const blob = await response.blob();

	        // 새로고침 이후 도착한 이전 요청의 결과는 사용하지 않는다.
	        if (version !== executionDetailVersion) {
	            return;
	        }

	        const objectUrl = URL.createObjectURL(blob);
	        const link = document.createElement('a');

	        link.href = objectUrl;
	        link.download = 'sqlflow-execution-' + executionId + '.csv';

	        document.body.appendChild(link);
	        link.click();
	        link.remove();

	        setTimeout(function () {
	            URL.revokeObjectURL(objectUrl);
	        }, 60000);

	        message.textContent =
	            '브라우저의 다운로드 목록을 확인해 주세요.';

	    } catch (error) {
	        if (version !== executionDetailVersion) {
	            return;
	        }

	        switch (error.status) {
	            case 401:
	                message.textContent =
	                    '로그인이 만료되었습니다. 다시 로그인해 주세요.';
	                break;

	            case 403:
	                // 조회 거부와 반출 거부는 서버 메시지로 구분한다.
	                // 다운로드 실패로 미리보기 표를 지우지 않는다.
	                message.textContent =
	                    error.message || 'CSV 다운로드 권한이 없습니다.';
	                break;

	            case 404:
	                message.textContent =
	                    error.message || 'CSV 결과 파일을 찾을 수 없습니다.';
	                break;

	            case 409:
	                message.textContent =
	                    error.message || '현재 상태에서는 다운로드할 수 없습니다.';
	                break;

	            case 410:
	                message.textContent =
	                    error.message || '결과가 만료되었거나 삭제되었습니다.';
	                break;

	            default:
	                message.textContent = error.status >= 500
	                    ? '서버에서 파일을 제공하지 못했습니다. 잠시 후 다시 시도해 주세요.'
	                    : error.message || '서버에 연결하지 못했습니다.';
	        }

	    } finally {
	        downloading = false;
	        button.disabled = false;
	        button.textContent = 'CSV 다운로드';
	    }
	}

	$('#execution-result-download').addEventListener(
	    'click',
	    downloadExecutionCsv
	);

    async function loadExecutionDetail() {
        const version = ++executionDetailVersion;
		
		$('#execution-result-download').hidden = true;

		document.dispatchEvent(
		    new Event('execution-preview-reset')
		);

        $('#execution-detail-refresh').disabled = true;
        $('#execution-detail-loading').hidden = false;
        $('#execution-detail-loading').textContent =
            '실행 정보를 불러오는 중입니다.';

        $('#execution-detail-content').hidden = true;
        $('#page-message').hidden = true;

        try {
            const detail = await executionResultApi(
                '/execution-results/mine/'
                + encodeURIComponent(executionId)
            );

            if (version !== executionDetailVersion) {
                return;
            }

			const canRequestPreview = renderExecutionDetail(detail);

			$('#execution-detail-loading').hidden = true;
			$('#execution-detail-content').hidden = false;

			document.dispatchEvent(
			    new CustomEvent(
			        'execution-result-ready',
			        {
			            detail: {
			                available: canRequestPreview
			            }
			        }
			    )
			);

        } catch (error) {
            if (version !== executionDetailVersion) {
                return;
            }

			const message = executionResultErrorMessage(error);

			$('#execution-detail-loading').textContent = message;
			notify(message, true);

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
</script>

<script>
(() => {
    const section = $('#execution-preview-section');
    const scrollBox = $('#csv-preview-scroll');
    const headerRow = $('#csv-preview-columns');
    const tableBody = $('#csv-preview-rows');
    const countLabel = $('#csv-preview-count');
    const message = $('#csv-preview-message');
    const retryButton = $('#csv-preview-retry');

    const pageSize = 25;

    let columns = null;
    let nextOffset = 0;
    let loadedRows = 0;
    let hasMore = false;
    let loading = false;
    let failed = false;
    let previewVersion = 0;

    function clearRows() {
        headerRow.replaceChildren();
        tableBody.replaceChildren();

        columns = null;
        nextOffset = 0;
        loadedRows = 0;
        countLabel.textContent = '';

        scrollBox.scrollTop = 0;
    }

    function resetPreview() {
        // 이전 요청이 늦게 도착해도 새 표에 반영되지 않게 한다.
        previewVersion++;

        clearRows();

        hasMore = false;
        loading = false;
        failed = false;

        section.hidden = true;
        scrollBox.hidden = false;
        retryButton.hidden = true;
        message.textContent = '';

        scrollBox.setAttribute('aria-busy', 'false');
    }

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

                td.textContent = value == null ? '' : String(value);
                td.style.whiteSpace = 'pre-wrap';
                td.style.minWidth = '100px';
                td.style.verticalAlign = 'top';

                tr.appendChild(td);
            });

            fragment.appendChild(tr);
        });

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

        const version = previewVersion;

        loading = true;
        retryButton.hidden = true;
        scrollBox.setAttribute('aria-busy', 'true');
        message.textContent = '데이터를 불러오는 중입니다.';

        try {
            const params = new URLSearchParams({
                offset: String(nextOffset),
                limit: String(pageSize)
            });

            const data = await executionResultApi(
                '/execution-results/mine/'
                + encodeURIComponent(executionId)
                + '/preview?'
                + params.toString()
            );

            if (version !== previewVersion) {
                return;
            }

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
                    '미리보기 끝입니다. 최대 100행까지 표시합니다. '
                    + '전체 CSV 다운로드는 반출 정책에 따라 제한될 수 있습니다.';
            }

        } catch (error) {
            if (version !== previewVersion) {
                return;
            }

            failed = true;

            const blocked =
                [400, 401, 403, 404, 409, 410].includes(error.status);

            if (blocked) {
                // 미리보기 자체가 거부된 경우 기존 데이터를 지운다.
                // 다운로드 API의 반출 거부는 이 코드로 들어오지 않는다.
                clearRows();

                hasMore = false;
                scrollBox.hidden = true;
                retryButton.hidden = true;

                $('#execution-result-download').hidden = true;

            } else {
                // 서버·통신 오류는 이미 표시한 행과 offset을 유지한다.
                retryButton.hidden = false;
            }

            message.textContent =
                executionResultErrorMessage(error);

        } finally {
            if (version === previewVersion) {
                loading = false;
                scrollBox.setAttribute('aria-busy', 'false');
            }
        }
    }

    document.addEventListener(
        'execution-preview-reset',
        resetPreview
    );

    document.addEventListener(
        'execution-result-ready',
        function (event) {
            if (!event.detail.available) {
                return;
            }

            section.hidden = false;
            hasMore = true;

            loadNextRows();
        }
    );

    scrollBox.addEventListener('scroll', function () {
        const remaining =
            scrollBox.scrollHeight
            - scrollBox.scrollTop
            - scrollBox.clientHeight;

        if (remaining <= 60) {
            loadNextRows();
        }
    }, { passive: true });

    retryButton.addEventListener('click', function () {
        failed = false;
        loadNextRows();
    });

    // 미리보기 이벤트를 등록한 뒤 상세 정보를 먼저 조회한다.
    loadExecutionDetail();
})();
</script>

<%@ include file="fragments/bottom.jspf" %>