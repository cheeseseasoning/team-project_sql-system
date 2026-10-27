<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div class="views-page">
    <div class="page-heading">
        <div>
            <h1>뷰 조회</h1>
            <p class="caption views-introduction">
                업무에 사용할 뷰와 데이터 구성을 확인하세요.
            </p>
        </div>
    </div>

    <section class="panel">
        <form id="view-filter-form" class="views-filter">
            <label for="view-department" class="views-department-field">
                부서
                <select id="view-department" name="deptId" disabled>
                    <option value="">전체 부서</option>
                </select>
            </label>

            <label for="view-keyword" class="views-keyword-field">
                뷰 검색
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
                        class="button"
                        disabled>
                    초기화
                </button>
            </div>
        </form>

        <p class="caption views-filter-description">
            전체 부서는 실제 존재하는 모든 뷰를 표시합니다.
            부서를 선택하면 활성 접근 정책이 등록된 뷰만 표시합니다.
        </p>

        <div class="panel-heading">
            <h2>뷰 목록</h2>
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
                    <col class="views-schema-column">
                    <col class="views-description-column">
                    <col class="views-status-column">
                </colgroup>

                <thead>
                    <tr>
                        <th scope="col">뷰 이름 / 등록 이름</th>
                        <th scope="col">스키마</th>
                        <th scope="col">설명</th>
                        <th scope="col">등록 상태</th>
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

    function updateUrl(filters, mode) {
        if (mode === 'none') {
            return;
        }

        const url = new URL(window.location.href);

        url.searchParams.delete('deptId');
        url.searchParams.delete('keyword');

        if (filters.deptId) {
            url.searchParams.set('deptId', filters.deptId);
        }

        if (filters.keyword) {
            url.searchParams.set('keyword', filters.keyword);
        }

        if (mode === 'replace') {
            history.replaceState(null, '', url);
        } else if (url.href !== window.location.href) {
            history.pushState(null, '', url);
        }
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
                label: '활성 등록',
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

        views.forEach(function (view) {
            const row = document.createElement('tr');

            const nameCell = document.createElement('td');
            const objectName = document.createElement('a');
            const resourceName = document.createElement('span');

			const detailParams = new URLSearchParams({schemaName: view.schemaName, objectName: view.objectName});

			// 상세에서 목록으로 돌아올 때 기존 검색 조건을 유지한다.
			const listParams = new URLSearchParams(window.location.search);

			if (listParams.get('deptId')) {
			    detailParams.set('deptId', listParams.get('deptId'));
			}

			if (listParams.get('keyword')) {
			    detailParams.set('keyword', listParams.get('keyword'));
			}

			objectName.className = 'views-object-name';
			objectName.textContent = view.objectName;
			objectName.href = BASE + '/views/detail?' + detailParams.toString();

            resourceName.className = 'secondary';
            resourceName.textContent = view.resourceName || '등록 이름 없음';

            nameCell.append(objectName, resourceName);

            const schemaCell = document.createElement('td');

            schemaCell.className = 'views-schema';
            schemaCell.textContent = view.schemaName;

            const descriptionCell = document.createElement('td');

            descriptionCell.className = 'views-description';
            descriptionCell.textContent =
                view.description || '등록된 설명이 없습니다.';

            const statusCell = document.createElement('td');
            const statusBadge = document.createElement('span');
            const status = getRegistrationStatus(view);

            statusBadge.className = 'badge ' + status.kind;
            statusBadge.textContent = status.label;
            statusCell.appendChild(statusBadge);

            row.append(
                nameCell,
                schemaCell,
                descriptionCell,
                statusCell
            );

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

    async function loadViews(historyMode = 'push') {
        const version = ++requestVersion;
        const filters = readFilters();
        const params = createSearchParams(filters);
        const query = params.toString();

        updateUrl(filters, historyMode);
        setLoading(true);

        retryButton.hidden = true;
        countElement.textContent = '';
        messageElement.textContent = '뷰 목록을 조회하고 있습니다.';
        messageElement.classList.remove('views-error');

        showTableMessage('뷰 목록을 불러오는 중입니다.');

        try {
            const views = await api(
                '/views' + (query ? '?' + query : '')
            );

            // 이전 검색 응답이 나중에 도착해도 최신 목록을 덮지 않는다.
            if (version !== requestVersion) {
                return;
            }

            if (!Array.isArray(views)) {
                throw new Error('뷰 목록 응답을 확인할 수 없습니다.');
            }

            if (views.length === 0) {
                showTableMessage(
                    '검색 조건에 맞는 뷰가 없습니다.'
                );
            } else {
                renderRows(views);
            }

            countElement.textContent = '총 ' + views.length + '건';
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

<%@ include file="fragments/bottom.jspf" %>