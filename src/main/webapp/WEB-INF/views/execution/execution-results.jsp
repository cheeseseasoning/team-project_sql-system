<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="page-heading">
    <h1>쿼리 실행 결과</h1>
    <button id="execution-refresh" type="button" class="button">
        새로고침
    </button>
</div>

<section class="panel">
	<form id="execution-filter-form" class="filter-bar">
	    <label>
	        결과 검색
	        <input id="execution-keyword"
	               name="keyword"
	               placeholder="기안서 번호 또는 제목"
	               maxlength="120">
	    </label>

	    <label>
	        처리 상태
	        <select id="execution-status" name="status">
	            <option value="">전체</option>
	            <option value="SUCCESS">성공</option>
	            <option value="FAILED">실패</option>
	        </select>
	    </label>

	    <button class="button primary" type="submit">검색</button>
	    <button id="execution-clear-filter"
	            class="button"
	            type="button">초기화</button>
	</form>

	<div class="panel-heading">
	    <h2>SQL 실행 목록</h2>
	    <span id="execution-count"
	          class="caption"
	          aria-live="polite"></span>
	</div>

    <div class="table-scroll">
        <table>
            <caption class="sr-only">SQL별 최신 실행 결과 목록</caption>
            <thead>
                <tr>
                    <th scope="col">기안서 번호</th>
                    <th scope="col">SQL 제목</th>
                    <th scope="col">배치 번호</th>
                    <th scope="col">실행 상태</th>
                    <th scope="col">조회 건수</th>
                    <th scope="col">실행 시작 시각</th>
                </tr>
            </thead>
            <tbody id="execution-rows"></tbody>
        </table>
    </div>

    <nav class="form-footer" aria-label="실행 결과 페이지 이동">
        <button id="execution-previous"
                type="button"
                class="button"
                disabled>
            이전
        </button>

        <span id="execution-page-info"
              class="caption"
              aria-live="polite"></span>

        <button id="execution-next"
                type="button"
                class="button"
                disabled>
            다음
        </button>
    </nav>
</section>

<script>
    let executionListVersion = 0;
    let executionCurrentPage = 1;
    const executionPageSize = 10;
	
	let executionActiveFilters = {
	    keyword: '',
	    status: ''
	};

    function formatExecutionDate(value) {
        return value
            ? value.replace('T', ' ').substring(0, 19)
            : '—';
    }

    function showExecutionListMessage(message) {
        $('#execution-rows').innerHTML =
            '<tr><td colspan="6" class="empty" role="status">'
            + esc(message)
            + '</td></tr>';
    }

    function renderExecutionRows(rows) {
        const statusNames = {
            PENDING: '실행 대기',
            RUNNING: '실행 중',
            SUCCESS: '성공',
            FAILED: '실패'
        };

        return rows.map(function (row) {
            const statusLabel =
                statusNames[row.status] || row.status || '—';

            const statusKind =
                row.status === 'SUCCESS'
                    ? 'success'
                    : row.status === 'PENDING' || row.status === 'RUNNING'
                        ? 'waiting'
                        : 'neutral';

			const requestUrl =
				BASE + '/department-requests/' + encodeURIComponent(row.requestId);
				
			const detailUrl =
				BASE + '/execution-results/' + encodeURIComponent(row.executionId);

            const rowCount = row.rowCount == null
                ? '—'
                : String(row.rowCount);

            return '<tr>'
                + '<td>'
                + '<a class="table-title" href="' + esc(requestUrl) + '">'
                + esc(row.requestNo || '요청 보기')
                + '</a>'
                + '<span class="secondary">'
                + esc(row.requestTitle || '')
                + '</span>'
                + '</td>'
				+ '<td>'
				+ '<a class="table-title" href="' + esc(detailUrl) + '">'
				+ esc(row.queryTitle || '제목 없는 SQL')
				+ '</a>'
				+ '</td>'
                + '<td>' + esc(row.batchId) + '</td>'
                + '<td>' + badge(statusLabel, statusKind) + '</td>'
                + '<td class="numeric">' + esc(rowCount) + '</td>'
                + '<td>' + esc(formatExecutionDate(row.startedAt)) + '</td>'
                + '</tr>';
        }).join('');
    }

    async function loadExecutionResults(page = 1) {
        const version = ++executionListVersion;

        $('#execution-previous').disabled = true;
        $('#execution-next').disabled = true;
        $('#execution-refresh').disabled = true;
        $('#execution-page-info').textContent = '조회 중입니다.';
        $('#execution-count').textContent = '';

        $('#execution-rows').setAttribute('aria-busy', 'true');
        showExecutionListMessage('실행 목록을 불러오는 중입니다.');

        try {
			const params = new URLSearchParams({
			    page: String(page),
			    size: String(executionPageSize),
			    keyword: executionActiveFilters.keyword,
			    status: executionActiveFilters.status
			});

            const data = await api(
                '/execution-results/mine?' + params.toString()
            );

            if (version !== executionListVersion) {
                return;
            }

            if (!Array.isArray(data.items)) {
                throw Error('실행 결과 목록 응답을 확인할 수 없습니다.');
            }

            // 조회 중 데이터가 줄어 현재 페이지가 사라진 경우
            if (data.totalPages > 0 && data.page > data.totalPages) {
                await loadExecutionResults(data.totalPages);
                return;
            }

            executionCurrentPage = data.totalPages === 0 ? 1 : data.page;

			if (data.items.length > 0) {
			    $('#execution-rows').innerHTML =
			        renderExecutionRows(data.items);
			} else {
			    const filtered =
			        executionActiveFilters.keyword !== ''
			        || executionActiveFilters.status !== '';

			    showExecutionListMessage(
			        filtered
			            ? '검색 조건에 맞는 실행 결과가 없습니다.'
			            : '실행 이력이 없습니다.'
			    );
			}

            $('#execution-count').textContent =
                '총 ' + data.totalCount + '건';

            $('#execution-page-info').textContent =
                data.totalPages === 0
                    ? '페이지 없음'
                    : data.page + ' / ' + data.totalPages + ' 페이지';

            $('#execution-previous').disabled =
                data.totalPages === 0 || data.page <= 1;

            $('#execution-next').disabled =
                data.totalPages === 0 || data.page >= data.totalPages;

        } catch (error) {
            if (version !== executionListVersion) {
                return;
            }

            showExecutionListMessage('실행 목록을 불러오지 못했습니다.');
            $('#execution-page-info').textContent = '';
            notify(error.message, true);

        } finally {
            if (version === executionListVersion) {
                $('#execution-refresh').disabled = false;
                $('#execution-rows').setAttribute('aria-busy', 'false');
            }
        }
    }
	
	$('#execution-filter-form').addEventListener('submit', function (event) {
	    event.preventDefault();

	    executionActiveFilters = {
	        keyword: $('#execution-keyword').value.trim(),
	        status: $('#execution-status').value
	    };

	    loadExecutionResults(1);
	});

	$('#execution-clear-filter').addEventListener('click', function () {
	    $('#execution-filter-form').reset();

	    executionActiveFilters = {
	        keyword: '',
	        status: ''
	    };

	    loadExecutionResults(1);
	});

    $('#execution-refresh').addEventListener('click', function () {
        loadExecutionResults(executionCurrentPage);
    });

    $('#execution-previous').addEventListener('click', function () {
        if (executionCurrentPage > 1) {
            loadExecutionResults(executionCurrentPage - 1);
        }
    });

    $('#execution-next').addEventListener('click', function () {
        loadExecutionResults(executionCurrentPage + 1);
    });

    loadExecutionResults();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
