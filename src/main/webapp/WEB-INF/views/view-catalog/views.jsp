<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="views-page">
	<div class="page-heading panel-aligned-heading">
	    <h1>뷰 조회</h1>
	</div>

    <section class="panel">
        <form id="view-filter-form" class="views-filter list-search-bar">
            <label for="view-department" class="views-department-field">
                <span class="sr-only">부서</span>
                <select id="view-department" name="deptId" disabled>
                    <option value="">전체 부서</option>
                </select>
            </label>

            <label for="view-keyword" class="views-keyword-field">
                <span class="sr-only">뷰 검색</span>
                <input id="view-keyword"
                       name="keyword"
                       type="search"
                       placeholder="뷰 이름 또는 설명 검색">
            </label>

            <div class="views-filter-actions">
                <button id="view-search"
                        type="submit"
                        class="button primary"
                        disabled>
                    검색
                </button>

                <button id="view-reset"
                        type="button"
                        class="button list-search-reset"
                        title="검색 조건 초기화 및 새로고침"
                        aria-label="검색 조건 초기화 및 새로고침"
                        disabled>
                    <img src="${pageContext.request.contextPath}/icons/RefreshCw.svg" alt="">
                </button>
            </div>
        </form>

        <p class="caption views-filter-description">
            전체 부서는 실제 존재하는 모든 뷰를 표시합니다.
            부서를 선택하면 해당 부서에서 접근 가능한 뷰만 표시합니다.
        </p>

        <div class="views-list-summary">
            <span id="view-count"
                  class="caption"
                  aria-live="polite"></span>
        </div>

        <div class="table-scroll views-table-scroll">
            <table class="views-table">
                <caption class="sr-only">
                    실제 업무 DB에 존재하는 뷰 목록
                </caption>

				<colgroup>
				    <col class="views-name-column">
				    <col class="views-resource-column">
				    <col class="views-schema-column">
				    <col class="views-status-column">
				</colgroup>

				<thead>
				    <tr>
				        <th scope="col">뷰 이름</th>
				        <th scope="col">등록 이름</th>
				        <th scope="col">스키마</th>
				        <th scope="col">활성 상태</th>
				    </tr>
				</thead>

                <tbody id="view-rows" aria-busy="true">
                    <tr>
                        <td colspan="4" class="empty">
                            뷰 목록을 불러오는 중입니다.
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>

		<nav class="views-pagination" aria-label="뷰 목록 페이지 이동">
		    <button id="view-prev-page" type="button" class="button" aria-label="이전 페이지" disabled>‹ 이전</button>
		    <div id="view-page-numbers" class="views-page-numbers"></div>
		    <button id="view-next-page" type="button" class="button" aria-label="다음 페이지" disabled>다음 ›</button>
		</nav>
		<span id="view-page-info" class="sr-only" aria-live="polite"></span>

        <div class="views-list-footer">
            <p id="view-list-message"
               class="caption"
               role="status"
               aria-live="polite"></p>

            <button id="view-retry"
                    type="button"
                    class="button"
                    hidden>
                다시 시도
            </button>
        </div>
    </section>
</div>

<script>
(() => {
    const form = $('#view-filter-form');
    const departmentSelect = $('#view-department');
    const keywordInput = $('#view-keyword');
    const searchButton = $('#view-search');
    const resetButton = $('#view-reset');
    const retryButton = $('#view-retry');
    const rowsElement = $('#view-rows');
    const countElement = $('#view-count');
    const messageElement = $('#view-list-message');

    let departmentsReady = false;
    let loading = false;
    let requestVersion = 0;

    function updateControls() {
        departmentSelect.disabled = loading || !departmentsReady;
        searchButton.disabled = loading || !departmentsReady;
        resetButton.disabled = loading || !departmentsReady;
        retryButton.disabled = loading;
    }

    function setLoading(value) {
        loading = value;
        rowsElement.setAttribute('aria-busy', String(value));
        updateControls();
    }

    function showTableMessage(message) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');

        cell.colSpan = 4;
        cell.className = 'empty';
        cell.textContent = message;

        row.appendChild(cell);
        rowsElement.replaceChildren(row);
    }

    function readFilters() {
        return {
            deptId: departmentSelect.value,
            keyword: keywordInput.value.trim()
        };
    }

    function createSearchParams(filters) {
        const params = new URLSearchParams();

        if (filters.deptId) {
            params.set('deptId', filters.deptId);
        }

        if (filters.keyword) {
            params.set('keyword', filters.keyword);
        }

        return params;
    }

    /**
     * URL에 저장된 검색 조건을 복원한다.
     * 존재하지 않는 부서 ID를 전체 부서로 조용히 바꾸지 않는다.
     */
    function restoreFiltersFromUrl() {
        const params = new URLSearchParams(window.location.search);
        const deptId = params.get('deptId') || '';

        keywordInput.value = params.get('keyword') || '';

        const exists = Array.from(departmentSelect.options)
            .some(option => option.value === deptId);

        if (!exists) {
            departmentSelect.value = '';

            throw new Error(
                'URL의 부서 조건이 올바르지 않습니다. 초기화 후 다시 검색하세요.'
            );
        }

        departmentSelect.value = deptId;
    }

    async function loadDepartments() {
        const departments = await api('/views/departments');

        if (!Array.isArray(departments)) {
            throw new Error('부서 목록 응답을 확인할 수 없습니다.');
        }

        const fragment = document.createDocumentFragment();
        const allOption = document.createElement('option');

        allOption.value = '';
        allOption.textContent = '전체 부서';
        fragment.appendChild(allOption);

        departments.forEach(function (department) {
            const option = document.createElement('option');

            option.value = String(department.deptId);
            option.textContent = department.deptName;
            fragment.appendChild(option);
        });

        departmentSelect.replaceChildren(fragment);
        departmentsReady = true;
    }

    function getRegistrationStatus(view) {
        if (!view.registered) {
            return {
                label: '미등록',
                kind: 'neutral'
            };
        }

        if (view.enabled === 'Y') {
            return {
                label: '활성',
                kind: 'success'
            };
        }

        if (view.enabled === 'N') {
            return {
                label: '비활성 등록',
                kind: 'waiting'
            };
        }

        return {
            label: '상태 확인 필요',
            kind: 'neutral'
        };
    }

    /**
     * 서버 문자열은 HTML로 해석하지 않고 텍스트로 출력한다.
     */
	 function renderRows(views) {
	     const fragment = document.createDocumentFragment();
	     const listParams = new URLSearchParams(window.location.search);

	     views.forEach(function (view) {
	         const row = document.createElement('tr');
	         const detailParams = new URLSearchParams({
	             schemaName: view.schemaName,
	             objectName: view.objectName
	         });

	         // 상세에서 목록으로 돌아올 때 부서와 키워드 검색 조건을 복원한다.
	         for (const key of ['deptId', 'keyword']) {
	             const value = listParams.get(key);
	             if (value) detailParams.set(key, value);
	         }

	         const nameCell = document.createElement('td');
	         const objectName = document.createElement('a');
	         objectName.className = 'views-object-name';
	         objectName.textContent = view.objectName;
	         objectName.href = BASE + '/views/detail?' + detailParams.toString();
	         nameCell.appendChild(objectName);

	         const resourceCell = document.createElement('td');
	         resourceCell.className = 'views-resource-name';
	         resourceCell.textContent = view.resourceName || '등록 이름 없음';

	         const schemaCell = document.createElement('td');
	         schemaCell.className = 'views-schema';
	         schemaCell.textContent = view.schemaName;

	         const statusCell = document.createElement('td');
	         const statusBadge = document.createElement('span');
	         const status = getRegistrationStatus(view);
	         statusBadge.className = 'badge ' + status.kind;
	         statusBadge.textContent = status.label;
	         statusCell.appendChild(statusBadge);

	         row.append(nameCell, resourceCell, schemaCell, statusCell);
	         fragment.appendChild(row);
	     });

	     rowsElement.replaceChildren(fragment);
	 }

    function showFailure(error) {
        countElement.textContent = '';
        showTableMessage('뷰 목록을 표시하지 못했습니다.');
        messageElement.textContent =
            error.message || '조회 중 오류가 발생했습니다.';
        messageElement.classList.add('views-error');
        retryButton.hidden = false;
    }

	function renderViewPageNumbers(page, totalPages) {
	    const container = $('#view-page-numbers');
	    const pageGroupSize = 10;
	    const fragment = document.createDocumentFragment();

	    // 현재 페이지가 속한 구간만 표시한다. 마지막 구간은 실제 페이지 수까지만 만든다.
	    const firstPage = Math.floor((Math.max(1, page) - 1) / pageGroupSize) * pageGroupSize + 1;
	    const lastPage = Math.min(firstPage + pageGroupSize - 1, totalPages);

	    for (let number = firstPage; number <= lastPage; number++) {
	        const button = document.createElement('button');
	        button.type = 'button';
	        button.className = 'button';
	        button.textContent = String(number);
	        button.setAttribute('aria-label', number + '페이지');

	        if (number === page) {
	            button.setAttribute('aria-current', 'page');
	        } else {
	            button.addEventListener('click', () => loadViews('push', number));
	        }

	        fragment.appendChild(button);
	    }

	    container.replaceChildren(fragment);
	}

	async function loadViews(historyMode = 'push', requestedPage) {
	    const version = ++requestVersion;
	    const previousButton = $('#view-prev-page');
	    const nextButton = $('#view-next-page');
	    const pageInfo = $('#view-page-info');
	    const currentUrl = new URL(window.location.href);

	    const filters = requestedPage === undefined
	        ? readFilters()
	        : {
	            deptId: currentUrl.searchParams.get('deptId') || '',
	            keyword: currentUrl.searchParams.get('keyword') || ''
	        };

	    const savedPage = Number(currentUrl.searchParams.get('page'));
	    const pageFromUrl = Number.isInteger(savedPage) && savedPage >= 1 && savedPage <= 2147483647
	        ? savedPage
	        : 1;
	    const page = requestedPage ?? (historyMode === 'push' ? 1 : pageFromUrl);
	    const params = createSearchParams(filters);

	    params.set('page', String(page));
	    params.set('size', '10');

	    function saveListUrl(actualPage, mode) {
	        const url = new URL(window.location.href);

	        for (const key of ['deptId', 'keyword', 'page']) {
	            url.searchParams.delete(key);
	        }

	        createSearchParams(filters).forEach((value, key) => url.searchParams.set(key, value));

	        if (actualPage > 1) {
	            url.searchParams.set('page', String(actualPage));
	        }

	        if (url.href === window.location.href) {
	            return;
	        }

	        if (mode === 'push') {
	            history.pushState(null, '', url);
	        } else {
	            history.replaceState(null, '', url);
	        }
	    }

	    if (historyMode !== 'none') {
	        saveListUrl(page, historyMode);
	    }

	    setLoading(true);
	    previousButton.disabled = true;
	    nextButton.disabled = true;
	    pageInfo.textContent = '';
	    renderViewPageNumbers(1, 0);
	    retryButton.hidden = true;
	    countElement.textContent = '';
	    messageElement.textContent = '뷰 목록을 조회하고 있습니다.';
	    messageElement.classList.remove('views-error');
	    showTableMessage('뷰 목록을 불러오는 중입니다.');

	    try {
	        const data = await api('/views?' + params.toString());

	        // 이전 요청이 늦게 끝나도 현재 페이지를 덮어쓰지 않는다.
	        if (version !== requestVersion) {
	            return;
	        }

	        if (!Array.isArray(data?.items)) {
	            throw new Error('뷰 목록 응답을 확인할 수 없습니다.');
	        }

	        // 요청한 페이지가 범위를 넘으면 서버가 실제 반환한 페이지를 URL에 남긴다.
	        saveListUrl(data.page, 'replace');

	        if (data.items.length === 0) {
	            showTableMessage('검색 조건에 맞는 뷰가 없습니다.');
	        } else {
	            renderRows(data.items);

	            // 기존 상세 링크에 목록 페이지를 넘겨 복귀 위치를 유지한다.
	            if (data.page > 1) {
	                rowsElement.querySelectorAll('a.views-object-name').forEach(function (link) {
	                    const detailUrl = new URL(link.href);
	                    detailUrl.searchParams.set('page', String(data.page));
	                    link.href = detailUrl.href;
	                });
	            }
	        }

	        countElement.textContent = '총 ' + data.totalCount + '건';
	        pageInfo.textContent = data.totalPages === 0
	            ? '0 / 0 페이지'
	            : data.page + ' / ' + data.totalPages + ' 페이지';
	        renderViewPageNumbers(data.page, data.totalPages);

	        previousButton.disabled = data.page <= 1;
	        nextButton.disabled = data.page >= data.totalPages;
	        previousButton.onclick = () => loadViews('push', data.page - 1);
	        nextButton.onclick = () => loadViews('push', data.page + 1);
	        messageElement.textContent = '';
	    } catch (error) {
	        if (version === requestVersion) {
	            showFailure(error);
	        }
	    } finally {
	        if (version === requestVersion) {
	            setLoading(false);
	        }
	    }
	}

    async function initialize() {
        setLoading(true);
        retryButton.hidden = true;

        try {
            await loadDepartments();
            restoreFiltersFromUrl();
        } catch (error) {
            showFailure(error);
            setLoading(false);
            return;
        }

        await loadViews('replace');
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();

        if (!loading && departmentsReady) {
            loadViews();
        }
    });

    resetButton.addEventListener('click', function () {
        departmentSelect.value = '';
        keywordInput.value = '';

        loadViews();
    });

    retryButton.addEventListener('click', function () {
        if (!departmentsReady) {
            initialize();
            return;
        }

        try {
            restoreFiltersFromUrl();
            loadViews('none');
        } catch (error) {
            showFailure(error);
        }
    });

    window.addEventListener('popstate', function () {
        if (!departmentsReady) {
            initialize();
            return;
        }

        try {
            restoreFiltersFromUrl();
            loadViews('none');
        } catch (error) {
            // 진행 중인 이전 검색 결과도 화면에 반영하지 않는다.
            requestVersion++;
            setLoading(false);
            showFailure(error);
        }
    });

    initialize();
})();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>