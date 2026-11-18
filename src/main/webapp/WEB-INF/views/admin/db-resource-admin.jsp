<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="resource-admin-page">
    <div id="resource-list-heading" class="page-heading panel-aligned-heading resource-page-heading">
        <div>
            <h1>리소스·정책 관리</h1>
        </div>
    </div>

    <div id="resource-list-view" class="views-page">
        <section class="panel resource-list-panel">
            <div class="resource-search-block">
                <label for="resource-search" class="sr-only">리소스 검색</label>

                <form id="resource-search-form" class="resource-search-controls">
                    <label for="resource-search-type" class="sr-only">검색 유형</label>
                    <select id="resource-search-type" aria-label="검색 유형">
                        <option value="ALL">전체</option>
                        <option value="OBJECT">뷰 이름</option>
                        <option value="RESOURCE">등록 이름</option>
                        <option value="SCHEMA">스키마</option>
                        <option value="DESCRIPTION">설명</option>
                    </select>
                    <input
                        id="resource-search"
                        type="search"
                        placeholder="검색어 입력"
                        aria-label="검색어 입력">

                    <button id="search-resources"
                            class="button primary"
                            type="submit">
                        검색
                    </button>

                    <button id="reset-resource-search"
                            class="button resource-search-reset"
                            type="button" title="검색 조건 초기화 및 새로고침" aria-label="검색 조건 초기화 및 새로고침">
                        <img src="${pageContext.request.contextPath}/icons/RefreshCw.svg" alt="">
                    </button>
                </form>
            </div>

            <div class="resource-list-content">
                <div class="resource-list-heading">
                    <span id="resource-total" class="caption"></span>
                </div>

                <div class="table-scroll">
                    <table class="resource-list-table views-table">
                        <caption class="sr-only">리소스·정책 목록</caption>
                        <colgroup>
                            <col class="resource-object-column"><col class="resource-name-column">
                            <col class="resource-schema-column"><col class="resource-access-column">
                            <col class="resource-export-column"><col class="resource-state-column">
                        </colgroup>
                        <thead>
                            <tr>
                                <th scope="col">뷰 이름</th>
                                <th scope="col">등록 이름</th>
                                <th scope="col">스키마</th>
                                <th scope="col">조회 정책</th>
                                <th scope="col">반출 정책</th>
                                <th scope="col">활성 여부</th>
                            </tr>
                        </thead>
                        <tbody id="resource-rows">
                            <tr>
                                <td colspan="6" class="empty">
                                    뷰 목록을 불러오는 중입니다.
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>

                <nav
                    id="resource-pagination"
                    class="resource-pagination"
                    aria-label="리소스 목록 페이지 이동"
                    hidden>
                    <button
                        id="resource-previous-page"
                        class="resource-page-button"
                        type="button"
                        aria-label="이전 페이지">
                        ‹ 이전
                    </button>

                    <div
                        id="resource-page-numbers"
                        class="resource-page-numbers"
                        aria-label="페이지 번호"></div>

                    <button
                        id="resource-next-page"
                        class="resource-page-button"
                        type="button"
                        aria-label="다음 페이지">
                        다음 ›
                    </button>

                    <span
                        id="resource-page-status"
                        class="resource-page-status sr-only"
                        aria-live="polite"></span>
                </nav>
            </div>
        </section>
    </div>

    <div id="resource-detail-view" class="resource-detail-view view-detail-page" hidden>
        <div class="view-detail-toolbar">
            <a class="view-back-link" id="resource-back" href="${pageContext.request.contextPath}/db-admin/resources">← 리소스·정책 관리</a>
        </div>

        <section class="panel view-panel" aria-label="뷰 기본 정보">
            <dl class="view-info">
                <div><dt>뷰 이름</dt><dd id="detail-object-name" class="view-info-emphasis"></dd></div>
                <div><dt>등록 이름</dt><dd id="detail-resource-name" class="view-info-emphasis"></dd></div>
                <div><dt>스키마</dt><dd id="detail-schema-name"></dd></div>
                <div><dt>활성 여부</dt><dd><span id="detail-resource-state" class="badge"></span></dd></div>
                <div class="view-info-description"><dt>설명</dt><dd id="detail-object-description"></dd></div>
            </dl>
        </section>

        <section class="panel view-panel resource-detail-panel resource-policy-panel">
            <div class="panel-heading resource-section-heading">
                <h2>조회·반출 정책</h2>
                <div class="resource-policy-save">
                    <span id="policy-save-state" class="caption" aria-live="polite"></span>

                    <c:if test="${pageContext.request.isUserInRole('DB_ADMIN')}">
                        <button
                            type="button"
                            id="save-resource-policy"
                            class="button primary">
                            정책 저장
                        </button>
                    </c:if>
                </div>
            </div>

            <div class="resource-policy-fields">
                <div class="resource-policy-field">
                    <label class="sr-only" for="policy-access">조회 수준</label>
                    <c:choose>
                        <c:when test="${pageContext.request.isUserInRole('DB_ADMIN')}">
							<select id="policy-access">
							    <option value="GENERAL">일반 조회 (GENERAL)</option>
							    <option value="SENSITIVE">민감 조회 (SENSITIVE)</option>
							</select>
                        </c:when>
                        <c:otherwise>
                            <strong id="policy-access-read" class="resource-policy-value"></strong>
                        </c:otherwise>
                    </c:choose>
                </div>

                <div class="resource-policy-field">
                    <label class="sr-only" for="policy-export">반출 정책</label>
                    <c:choose>
                        <c:when test="${pageContext.request.isUserInRole('DB_ADMIN')}">
							<select id="policy-export">
							    <option value="ALLOW">반출 허용 (ALLOW)</option>
							    <option value="BLOCK">반출 차단 (BLOCK)</option>
							</select>
                        </c:when>
                        <c:otherwise>
                            <strong id="policy-export-read" class="resource-policy-value"></strong>
                        </c:otherwise>
                    </c:choose>
                </div>

                <div class="resource-policy-field">
                    <label class="sr-only" for="policy-enabled">활성 여부</label>
                    <c:choose>
                        <c:when test="${pageContext.request.isUserInRole('DB_ADMIN')}">
                            <select id="policy-enabled">
                                <option value="Y">활성</option>
                                <option value="N">비활성</option>
                            </select>
                        </c:when>
                        <c:otherwise>
                            <strong id="policy-enabled-read" class="resource-policy-value"></strong>
                        </c:otherwise>
                    </c:choose>
                </div>


            </div>

            <p class="resource-role-note" id="resource-readonly-note" hidden>
                조회 전용: 정책과 접근 부서를 변경하려면 DB 운영 관리 권한이 필요합니다.
            </p>
        </section>

        <section class="panel view-panel resource-detail-panel resource-department-panel">
            <div class="panel-heading resource-section-heading">
                <div class="resource-dept-heading">
                    <h2>접근 권한 보유 부서</h2>
                    <span id="detail-dept-count" class="caption"></span>
                </div>

            <div class="resource-heading-actions">
                <c:if test="${pageContext.request.isUserInRole('DB_ADMIN')}">
                    <button type="button" id="toggle-add-dept" class="button">
                        + 부서 추가
                    </button>
                </c:if>
                <span id="dept-save-state" class="caption" aria-live="polite"></span>

                <c:if test="${pageContext.request.isUserInRole('DB_ADMIN')}">
                    <button
                        type="button"
                        id="save-resource-depts"
                        class="button primary">
                        권한 저장
                    </button>
                </c:if>
            </div>
            </div>

            <div class="table-scroll">
                <table class="resource-dept-table">
                    <thead>
                        <tr>
                            <th scope="col">부서명</th>
                            <th scope="col" class="numeric">활성 Q-Pass 계정</th>
                            <th scope="col" aria-label="작업"></th>
                        </tr>
                    </thead>
                    <tbody id="detail-dept-rows"></tbody>
                </table>
            </div>


        </section>

    </div>
</div>


<c:if test="${pageContext.request.isUserInRole('DB_ADMIN')}">
    <dialog id="approval-line-dialog" aria-labelledby="resource-dept-dialog-title" aria-describedby="resource-dept-dialog-help">
        <header class="approval-line-dialog-heading">
            <h2 id="resource-dept-dialog-title">접근 부서 추가</h2>
            <p id="resource-dept-dialog-help">부서를 선택해 추가한 뒤, 상세 화면의 권한 저장을 눌러 반영하세요.</p>
        </header>
        <div class="approval-line-dialog-body">
            <form id="resource-dept-search-form" class="approval-line-search-fields">
                <div class="approval-line-search">
                    <label class="sr-only" for="resource-dept-keyword">부서 검색</label>
                    <input id="resource-dept-keyword" type="search" placeholder="부서명 검색" maxlength="120">
                    <button type="submit" class="button">검색</button>
                </div>
            </form>
            <section class="approval-line-candidate-area" aria-labelledby="resource-dept-candidate-title">
                <h3 id="resource-dept-candidate-title" class="approval-line-list-heading">부서 목록</h3>
                <div class="approval-line-table-scroll" id="resource-dept-candidates"></div>
            </section>
            <div class="approval-line-transfer-actions">
                <button id="resource-dept-add" type="button" class="button primary" disabled>추가 →</button>
                <button id="resource-dept-remove" type="button" class="button" disabled>← 제외</button>
            </div>
            <section class="approval-line-current-area" aria-labelledby="resource-dept-selected-title">
                <h3 id="resource-dept-selected-title" class="approval-line-list-heading">추가할 부서 <span id="resource-dept-selected-count" class="approval-line-selected-count" aria-live="polite">0개</span></h3>
                <div class="approval-line-current-scroll"><ul id="resource-dept-selected" class="approval-line-selected-list"></ul></div>
            </section>
        </div>
        <footer class="approval-line-dialog-footer">
            <button id="cancel-add-dept" type="button" class="button">취소</button>
            <button id="confirm-add-dept" type="button" class="button primary" disabled>선택 부서 추가</button>
        </footer>
    </dialog>
</c:if>

<script>
(() => {
    const isAdmin = ${pageContext.request.isUserInRole('DB_ADMIN')};
    const initialResourceId = ${empty resourceId ? 'null' : resourceId};
    const pageSize = 10;
    const pageBlockSize = 10;

    const rows = document.getElementById('resource-rows');
    const search = document.getElementById('resource-search');
    const departmentDialog = document.getElementById('approval-line-dialog');
    const stagedDepartmentIds = new Set();
    const candidateChecks = new Set();
    const stagedChecks = new Set();
    const listView = document.getElementById('resource-list-view');
    const detailView = document.getElementById('resource-detail-view');
    const listHeading = document.getElementById('resource-list-heading');
    const pagination = document.getElementById('resource-pagination');
    const previousPageButton = document.getElementById('resource-previous-page');
    const nextPageButton = document.getElementById('resource-next-page');
    const pageNumbers = document.getElementById('resource-page-numbers');
    const pageStatus = document.getElementById('resource-page-status');

    let objects = [];
    let departments = [];
    let selected = null;
    let selectedDepartmentIds = new Set();
    let savedDepartmentIds = new Set();
    let currentPage = 1;
    let totalPages = 0;

    function resourcePolicyLabel(object, type) {
        if (!object.resourceId) return '—';
        const labels = type === 'access'
            ? { GENERAL: '일반 조회', SENSITIVE: '민감 조회' }
            : { ALLOW: '반출 허용', BLOCK: '반출 차단' };
        const value = type === 'access' ? object.accessLevel : object.exportPolicy;
        return esc(labels[value] || '—');
    }

    function getFilteredObjects() {
        const keyword = search.value.trim().toLowerCase();
        const searchType = document.getElementById('resource-search-type').value;
        const fields = { OBJECT: 'objectName', RESOURCE: 'resourceName', SCHEMA: 'schemaName', DESCRIPTION: 'description' };
        return objects.filter(object => {
            const values = searchType === 'ALL'
                ? [object.objectName, object.resourceName, object.schemaName, object.description]
                : [object[fields[searchType]]];
            return values.some(value => String(value || '').toLowerCase().includes(keyword));
        });
    }

    function renderPageNumbers() {
        pageNumbers.replaceChildren();

        if (totalPages <= 0) {
            return;
        }

        const firstPageInBlock =
            Math.floor((currentPage - 1) / pageBlockSize)
            * pageBlockSize
            + 1;

        const lastPageInBlock = Math.min(
            firstPageInBlock + pageBlockSize - 1,
            totalPages
        );

        for (
            let page = firstPageInBlock;
            page <= lastPageInBlock;
            page += 1
        ) {
            const button = document.createElement('button');

            button.type = 'button';
            button.className = 'resource-page-button';
            button.textContent = String(page);
            button.setAttribute('aria-label', page + '페이지');

            if (page === currentPage) {
                button.classList.add('is-current');
                button.setAttribute('aria-current', 'page');
            }

            button.addEventListener('click', () => {
                if (page !== currentPage) {
                    currentPage = page;
                    renderRows();
                }
            });

            pageNumbers.appendChild(button);
        }
    }

    function renderPagination() {
        pagination.hidden = totalPages === 0;
        previousPageButton.disabled = currentPage <= 1;
        nextPageButton.disabled =
            totalPages === 0 || currentPage >= totalPages;

        pageStatus.textContent = totalPages === 0
            ? '페이지가 없습니다.'
            : currentPage + ' / ' + totalPages + ' 페이지';

        renderPageNumbers();
    }

    function renderRows() {
        const filteredObjects = getFilteredObjects();

        totalPages = Math.ceil(filteredObjects.length / pageSize);
        currentPage = totalPages === 0
            ? 1
            : Math.min(currentPage, totalPages);

        const startIndex = (currentPage - 1) * pageSize;
        const visibleObjects = filteredObjects.slice(
            startIndex,
            startIndex + pageSize
        );

        if (visibleObjects.length === 0) {
            rows.innerHTML =
                '<tr><td colspan="6" class="empty">'
                + (search.value.trim()
                    ? '검색 조건에 맞는 리소스가 없습니다.'
                    : '등록된 뷰가 없습니다.')
                + '</td></tr>';
        } else {
            rows.innerHTML = visibleObjects.map(object => {
                const resourceRegistered = Number(object.resourceId) > 0;
                const enabled =
                    resourceRegistered
                    && object.resourceEnabled === true;

                const resourceName =
                    object.resourceName?.trim() || '등록 이름 없음';

                const viewName = resourceRegistered
                    ? '<a class="resource-view-link views-object-name" href="'
                        + BASE
                        + '/db-admin/resources/'
                        + encodeURIComponent(object.resourceId)
                        + '">'
                        + esc(object.objectName)
                        + '</a>'
                    : '<strong class="resource-view-name views-object-name">'
                        + esc(object.objectName)
                        + '</strong>';

                const registerAction = resourceRegistered
                    ? ''
                    : '<button type="button" class="text-button register-resource"'
                        + ' data-schema="' + esc(object.schemaName) + '"'
                        + ' data-object="' + esc(object.objectName) + '"'
                        + ' data-description="' + esc(object.description || '') + '">'
                        + '리소스 등록 +</button>';

                return '<tr>'
                    + '<td><div class="resource-view-cell">'
                    + viewName

                    + (registerAction
                        ? '<span class="resource-register-action">'
                            + registerAction
                            + '</span>'
                        : '')
                    + '</div></td>'
                    + '<td><span class="views-resource-name">' + esc(resourceName) + '</span></td>'
                    + '<td>' + esc(object.schemaName || '—') + '</td>'
                    + '<td>' + resourcePolicyLabel(object, 'access') + '</td>'
                    + '<td>' + resourcePolicyLabel(object, 'export') + '</td>'
                    + '<td><span class="badge ' + (enabled ? 'success' : 'neutral') + '">'
                    + (enabled ? '활성' : resourceRegistered ? '비활성' : '미등록')
                    + '</span></td>'
                    + '</tr>';
            }).join('');
        }

        document.getElementById('resource-total').textContent =
            '총 ' + filteredObjects.length + '건';

        renderPagination();

        rows.querySelectorAll('.register-resource').forEach(button => {
            button.addEventListener('click', async () => {
                try {
                    await post('/db-admin/resources', {
                        schemaName: button.dataset.schema,
                        objectName: button.dataset.object,
                        resourceName: button.dataset.object,
                        description: button.dataset.description
                    });

                    notify('리소스를 등록했습니다.');
                    await load();
                } catch (error) {
                    notify(error.message, true);
                }
            });
        });
    }

    function updateDepartmentDialogButtons() {
        document.getElementById('resource-dept-add').disabled = candidateChecks.size === 0;
        document.getElementById('resource-dept-remove').disabled = stagedChecks.size === 0;
        document.getElementById('confirm-add-dept').disabled = stagedDepartmentIds.size === 0;
    }

    function renderDepartmentDialog() {
        const keyword = document.getElementById('resource-dept-keyword').value.trim().toLowerCase();
        const available = departments.filter(dept => !selectedDepartmentIds.has(Number(dept.deptId))
            && !stagedDepartmentIds.has(Number(dept.deptId))
            && String(dept.deptName || '').toLowerCase().includes(keyword));
        // 검색으로 숨겨진 후보는 선택을 해제해 보이는 부서만 추가한다.
        const visibleIds = new Set(available.map(dept => Number(dept.deptId)));
        candidateChecks.forEach(id => { if (!visibleIds.has(id)) candidateChecks.delete(id); });
        document.getElementById('resource-dept-candidates').innerHTML = available.length
            ? available.map(dept => '<label class="approval-line-candidate-item">'
                + '<input class="approval-line-candidate-check" type="checkbox" data-dept="' + Number(dept.deptId) + '"'
                + (candidateChecks.has(Number(dept.deptId)) ? ' checked' : '') + '>'
                + '<span>' + esc(dept.deptName) + '</span></label>').join('')
            : '<p class="approval-line-empty">' + (keyword ? '검색 결과가 없습니다.' : '추가할 부서가 없습니다.') + '</p>';
        const staged = departments.filter(dept => stagedDepartmentIds.has(Number(dept.deptId)));
        document.getElementById('resource-dept-selected').innerHTML = staged.length
            ? staged.map(dept => '<li class="approval-line-selected-item"><label class="approval-line-selected-label">'
                + '<input class="approval-line-selected-check" type="checkbox" data-dept="' + Number(dept.deptId) + '"'
                + (stagedChecks.has(Number(dept.deptId)) ? ' checked' : '') + '>'
                + '<span class="approval-line-selected-person"><strong>' + esc(dept.deptName) + '</strong></span></label></li>').join('')
            : '<li class="approval-line-empty">추가할 부서를 선택하세요.</li>';
        document.getElementById('resource-dept-selected-count').textContent = staged.length + '개';
        updateDepartmentDialogButtons();
    }

    function renderDepartments() {
        const activeDepartments = departments.filter(department =>
            selectedDepartmentIds.has(Number(department.deptId))
        );

        const tbody = document.getElementById('detail-dept-rows');

        tbody.innerHTML = activeDepartments.length
            ? activeDepartments.map(department =>
                '<tr><td>'
                + esc(department.deptName)
                + '</td>'
                + '<td class="numeric">'
                + Number(department.activeAccountCount || 0).toLocaleString('ko-KR')
                + '명</td>'
                + '<td>'
                + (isAdmin
                    ? '<button type="button" class="remove-dept" data-id="'
                        + department.deptId
                        + '" aria-label="'
                        + esc(department.deptName)
                        + ' 접근 권한 제외">제외</button>'
                    : '')
                + '</td></tr>'
            ).join('')
            : '<tr><td colspan="3" class="empty">'
                + '접근 권한이 있는 부서가 없습니다.'
                + '</td></tr>';

        document.getElementById('detail-dept-count').textContent =
            activeDepartments.length + '개 부서';

        const currentIds = activeDepartments
            .map(department => Number(department.deptId))
            .sort()
            .join(',');

        const savedIds = Array.from(savedDepartmentIds)
            .sort()
            .join(',');

        const saveButton = document.getElementById('save-resource-depts');

        if (saveButton) {
            saveButton.disabled = currentIds === savedIds;
        }

        document.getElementById('dept-save-state').textContent =
            currentIds === savedIds
                ? ''
                : '저장되지 않은 항목이 있습니다.';
    }

	function openDetail(resourceId) {
	    selected = objects.find(object =>
	        String(object.resourceId) === String(resourceId)
	    );

	    if (!selected) {
	        return;
	    }

	    document.getElementById('detail-object-name').textContent = selected.objectName;
	    document.getElementById('detail-resource-name').textContent = selected.resourceName?.trim() || '등록 이름 없음';
        document.getElementById('detail-schema-name').textContent = selected.schemaName || '—';
        document.getElementById('detail-object-description').textContent = selected.description?.trim() || '등록된 설명이 없습니다.';
        const resourceState = selected.resourceEnabled === true;
        const stateElement = document.getElementById('detail-resource-state');
        stateElement.textContent = resourceState ? '활성' : '비활성';
        stateElement.className = 'badge ' + (resourceState ? 'success' : 'neutral');
        const accessLevel = selected.accessLevel || 'GENERAL';
        const exportPolicy = selected.exportPolicy || 'BLOCK';

        if (isAdmin) {
	        // 저장에 사용하는 정책 코드는 기존 값을 유지한다.
	        document.getElementById('policy-access').value = accessLevel;
	        document.getElementById('policy-export').value = exportPolicy;
	        document.getElementById('policy-enabled').value = resourceState ? 'Y' : 'N';
	    } else {
	        // 읽기 전용 화면에도 관리자 선택 항목과 같은 설명을 표시한다.
	        const accessLabels = {
	            GENERAL: '일반 조회 (GENERAL)',
	            SENSITIVE: '민감 조회 (SENSITIVE)'
	        };
	        const exportLabels = {
	            ALLOW: '반출 허용 (ALLOW)',
	            BLOCK: '반출 차단 (BLOCK)'
	        };

	        document.getElementById('policy-access-read').textContent =
	            accessLabels[accessLevel] || accessLevel;
	        document.getElementById('policy-export-read').textContent =
	            exportLabels[exportPolicy] || exportPolicy;
	        document.getElementById('policy-enabled-read').textContent =
	            resourceState ? '활성' : '비활성';
	    }

	    document.getElementById('policy-save-state').textContent = '';

	    const savePolicyButton = document.getElementById('save-resource-policy');

	    if (savePolicyButton) {
	        savePolicyButton.disabled = true;
	    }

	    savedDepartmentIds = new Set(
	        (selected.policies || []).map(policy => Number(policy.deptId))
	    );
	    selectedDepartmentIds = new Set(savedDepartmentIds);

	    renderDepartments();

	    listView.hidden = true;
	    listHeading.hidden = true;
	    detailView.hidden = false;
	    document.getElementById('resource-readonly-note').hidden = isAdmin;
	}

    async function load() {
        try {
            const results = await Promise.all([
                api('/db-admin/resources'),
                api('/db-admin/resources/departments')
            ]);

            objects = results[0].filter(object => object.objectType === 'VIEW');
            departments = results[1];

            renderRows();

            if (selected && !detailView.hidden) {
                openDetail(selected.resourceId);
            } else if (initialResourceId !== null) {
                openDetail(initialResourceId);
            }
        } catch (error) {
            rows.innerHTML =
                '<tr><td colspan="6" class="empty">'
                + esc(error.message || '뷰 목록을 불러오지 못했습니다.')
                + '</td></tr>';

            document.getElementById('resource-total').textContent = '';
            pagination.hidden = true;
            pageNumbers.replaceChildren();
        }
    }

    function applySearch() {
        currentPage = 1;
        renderRows();
    }

    // 검색 버튼과 Enter가 동일한 제출 경로를 사용한다.
    document.getElementById('resource-search-form').addEventListener('submit', event => {
        event.preventDefault();
        applySearch();
    });

    document.getElementById('reset-resource-search')
        .addEventListener('click', () => {
            search.value = '';
            document.getElementById('resource-search-type').value = 'ALL';
            currentPage = 1;
            renderRows();
        });

    document.getElementById('resource-search-type').addEventListener('change', applySearch);

    previousPageButton.addEventListener('click', () => {
        if (currentPage > 1) {
            currentPage -= 1;
            renderRows();
        }
    });

    nextPageButton.addEventListener('click', () => {
        if (currentPage < totalPages) {
            currentPage += 1;
            renderRows();
        }
    });

    if (isAdmin) {
        ['policy-access', 'policy-export', 'policy-enabled']
            .forEach(id => {
                document.getElementById(id).addEventListener('change', () => {
                    document.getElementById('save-resource-policy').disabled =
                        false;
                    document.getElementById('policy-save-state').textContent =
                        '저장되지 않은 항목이 있습니다.';
                });
            });

        document.getElementById('save-resource-policy')
            .addEventListener('click', async () => {
                try {
                    await api(
                        '/db-admin/resources/'
                        + selected.resourceId
                        + '/policies',
                        {
                            method: 'PUT',
                            body: JSON.stringify({
                                accessLevel:
                                    document.getElementById('policy-access').value,
                                exportPolicy:
                                    document.getElementById('policy-export').value,
                                enabled:
                                    document.getElementById('policy-enabled').value
                            })
                        }
                    );

                    notify('리소스 정책을 저장했습니다.');
                    await load();
                } catch (error) {
                    notify(error.message, true);
                }
            });

        document.getElementById('toggle-add-dept').addEventListener('click', () => {
            stagedDepartmentIds.clear();
            candidateChecks.clear();
            stagedChecks.clear();
            document.getElementById('resource-dept-keyword').value = '';
            renderDepartmentDialog();
            departmentDialog.showModal();
            document.getElementById('resource-dept-keyword').focus();
        });
        departmentDialog.addEventListener('close', () => document.getElementById('toggle-add-dept').focus());
        document.getElementById('cancel-add-dept').addEventListener('click', () => departmentDialog.close());
        document.getElementById('resource-dept-search-form').addEventListener('submit', event => {
            event.preventDefault();
            renderDepartmentDialog();
        });
        document.getElementById('resource-dept-candidates').addEventListener('change', event => {
            const check = event.target.closest('input[data-dept]');
            if (!check) return;
            const id = Number(check.dataset.dept);
            if (check.checked) candidateChecks.add(id); else candidateChecks.delete(id);
            updateDepartmentDialogButtons();
        });
        document.getElementById('resource-dept-selected').addEventListener('change', event => {
            const check = event.target.closest('input[data-dept]');
            if (!check) return;
            const id = Number(check.dataset.dept);
            if (check.checked) stagedChecks.add(id); else stagedChecks.delete(id);
            updateDepartmentDialogButtons();
        });
        document.getElementById('resource-dept-add').addEventListener('click', () => {
            candidateChecks.forEach(id => stagedDepartmentIds.add(id));
            candidateChecks.clear();
            renderDepartmentDialog();
        });
        document.getElementById('resource-dept-remove').addEventListener('click', () => {
            stagedChecks.forEach(id => stagedDepartmentIds.delete(id));
            stagedChecks.clear();
            renderDepartmentDialog();
        });
        document.getElementById('confirm-add-dept').addEventListener('click', () => {
            // 모달에서는 선택만 확정한다. DB 반영은 기존 권한 저장 API로 수행한다.
            stagedDepartmentIds.forEach(id => selectedDepartmentIds.add(id));
            renderDepartments();
            departmentDialog.close();
        });

        document.getElementById('detail-dept-rows')
            .addEventListener('click', event => {
                const button = event.target.closest('.remove-dept');

                if (button) {
                    selectedDepartmentIds.delete(Number(button.dataset.id));
                    renderDepartments();
                }
            });

        document.getElementById('save-resource-depts')
            .addEventListener('click', async () => {
                try {
                    await api(
                        '/db-admin/resources/'
                        + selected.resourceId
                        + '/departments',
                        {
                            method: 'PUT',
                            body: JSON.stringify({
                                deptIds: Array.from(selectedDepartmentIds)
                            })
                        }
                    );

                    savedDepartmentIds = new Set(selectedDepartmentIds);
                    notify('접근 부서를 저장했습니다.');
                    await load();
                } catch (error) {
                    notify(error.message, true);
                }
            });
    }

    load();
})();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
