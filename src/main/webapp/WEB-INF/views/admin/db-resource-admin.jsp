<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="resource-admin-page">
    <div id="resource-list-heading" class="page-heading resource-page-heading">
        <div>
            <h1>리소스·접근 정책 관리</h1>
        </div>
    </div>

    <div id="resource-list-view">
        <section class="panel resource-list-panel">
            <div class="resource-search-block">
                <label for="resource-search">리소스 검색</label>

                <div class="resource-search-controls">
                    <input
                        id="resource-search"
                        type="search"
                        placeholder="뷰 이름, 스키마 또는 설명 검색"
                        aria-label="뷰 이름, 스키마 또는 설명 검색">

                    <button id="search-resources"
                            class="button primary"
                            type="button">
                        검색
                    </button>

                    <button id="reset-resource-search"
                            class="button"
                            type="button">
                        초기화
                    </button>
                </div>

                <p class="caption">
                    뷰 이름, 스키마, 설명에 포함된 단어로 리소스를 찾을 수 있습니다.
                </p>
            </div>

            <div class="resource-list-content">
                <div class="resource-list-heading">
                    <h2>리소스 목록</h2>
                    <span id="resource-total" class="caption"></span>
                </div>

                <div class="table-scroll">
                    <table class="resource-list-table">
                        <thead>
                            <tr>
                                <th scope="col">뷰 이름</th>
                                <th scope="col">등록 이름</th>
                                <th scope="col">조회/반출정책</th>
                                <th scope="col">활성 상태</th>
                            </tr>
                        </thead>
                        <tbody id="resource-rows">
                            <tr>
                                <td colspan="4" class="empty">
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

    <div id="resource-detail-view" class="resource-detail-view" hidden>
        <a
            class="text-button resource-back"
            id="resource-back"
            href="${pageContext.request.contextPath}/db-admin/resources">
            ← 리소스·접근 정책 관리
        </a>

        <div class="resource-detail-title">
            <h1>리소스 정책 상세</h1>
            <span class="resource-admin-label">DB팀 관리</span>
        </div>

        <section class="panel resource-object-summary">
            <div class="resource-object-main">
                <strong id="detail-object-name"></strong>
                <span id="detail-object-meta" class="caption"></span>
            </div>
            <span id="detail-resource-state" class="resource-state-badge"></span>
        </section>

        <section class="panel resource-detail-panel resource-policy-panel">
            <div class="resource-section-heading">
                <h2>조회·반출 정책</h2>
            </div>

            <div class="resource-policy-fields">
                <div class="resource-policy-field">
                    <label for="policy-access">조회 수준</label>
                    <c:choose>
                        <c:when test="${pageContext.request.isUserInRole('DB_ADMIN')}">
                            <select id="policy-access">
                                <option value="GENERAL">GENERAL</option>
                                <option value="SENSITIVE">SENSITIVE</option>
                            </select>
                        </c:when>
                        <c:otherwise>
                            <strong id="policy-access-read" class="resource-policy-value"></strong>
                        </c:otherwise>
                    </c:choose>
                </div>

                <div class="resource-policy-field">
                    <label for="policy-export">반출 정책</label>
                    <c:choose>
                        <c:when test="${pageContext.request.isUserInRole('DB_ADMIN')}">
                            <select id="policy-export">
                                <option value="ALLOW">ALLOW</option>
                                <option value="BLOCK">BLOCK · 심사 필요</option>
                            </select>
                        </c:when>
                        <c:otherwise>
                            <strong id="policy-export-read" class="resource-policy-value"></strong>
                        </c:otherwise>
                    </c:choose>
                </div>

                <div class="resource-policy-field">
                    <label for="policy-enabled">리소스 상태</label>
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
        </section>

        <section class="panel resource-detail-panel resource-department-panel">
            <div class="resource-section-heading">
                <div class="resource-dept-heading">
                    <h2>접근 권한 보유 부서</h2>
                    <span id="detail-dept-count" class="caption"></span>
                </div>

                <c:if test="${pageContext.request.isUserInRole('DB_ADMIN')}">
                    <button type="button" id="toggle-add-dept" class="button">
                        + 부서 추가
                    </button>
                </c:if>
            </div>

            <div id="add-dept-row" class="resource-add-dept" hidden>
                <select id="add-dept-select" aria-label="추가할 부서"></select>
                <button type="button" id="confirm-add-dept" class="button primary">
                    추가
                </button>
                <button type="button" id="cancel-add-dept" class="button">
                    취소
                </button>
            </div>

            <div class="table-scroll">
                <table class="resource-dept-table">
                    <thead>
                        <tr>
                            <th scope="col">부서명</th>
                            <th scope="col" class="numeric">활성 SQLFlow 계정</th>
                            <th scope="col" aria-label="작업"></th>
                        </tr>
                    </thead>
                    <tbody id="detail-dept-rows"></tbody>
                </table>
            </div>

            <div class="resource-section-footer">
                <span id="dept-save-state" class="caption" aria-live="polite"></span>

                <c:if test="${pageContext.request.isUserInRole('DB_ADMIN')}">
                    <button
                        type="button"
                        id="save-resource-depts"
                        class="button primary">
                        부서 권한 저장
                    </button>
                </c:if>
            </div>
        </section>

        <p class="resource-role-note" id="resource-readonly-note" hidden>
            조회 전용: 정책과 접근 부서를 변경하려면 DB_ADMIN 권한이 필요합니다.
        </p>
    </div>
</div>

<script>
(() => {
    const isAdmin = ${pageContext.request.isUserInRole('DB_ADMIN')};
    const initialResourceId = ${empty resourceId ? 'null' : resourceId};
    const pageSize = 10;
    const pageBlockSize = 10;

    const rows = document.getElementById('resource-rows');
    const search = document.getElementById('resource-search');
    const departmentSelect = document.getElementById('add-dept-select');
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

    function resourcePolicyLabel(object) {
        if (!object.resourceId) {
            return '<span class="caption">—</span>';
        }

        const accessLevel = object.accessLevel || 'GENERAL';
        const exportPolicy = object.exportPolicy || 'BLOCK';
        const sensitive = accessLevel === 'SENSITIVE';

        return '<span class="resource-policy-summary'
            + (sensitive ? ' is-sensitive' : '')
            + '">'
            + (sensitive ? '민감 조회' : '일반 조회')
            + ' / '
            + (exportPolicy === 'ALLOW' ? '반출 허용' : '반출 차단')
            + '</span>';
    }

    function getFilteredObjects() {
        const keyword = search.value.trim().toLowerCase();

        return objects.filter(object => {
            const searchableText = [
                object.schemaName,
                object.objectName,
                object.resourceName,
                object.description
            ].join(' ').toLowerCase();

            return searchableText.includes(keyword);
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
                '<tr><td colspan="4" class="empty">'
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
                    object.resourceName || object.objectName || '—';

                const viewName = resourceRegistered
                    ? '<a class="resource-view-link" href="'
                        + BASE
                        + '/db-admin/resources/'
                        + encodeURIComponent(object.resourceId)
                        + '">'
                        + esc(object.objectName)
                        + '</a>'
                    : '<strong class="resource-view-name">'
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
                    + '<td>'
                    + viewName
                    + '<small class="resource-schema-name">'
                    + esc(object.schemaName)
                    + '</small>'
                    + (registerAction
                        ? '<span class="resource-register-action">'
                            + registerAction
                            + '</span>'
                        : '')
                    + '</td>'
                    + '<td>' + esc(resourceName) + '</td>'
                    + '<td>' + resourcePolicyLabel(object) + '</td>'
                    + '<td><span class="resource-state-pill'
                    + (enabled ? ' is-active' : '')
                    + '">'
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

        const availableDepartments = departments.filter(department =>
            !selectedDepartmentIds.has(Number(department.deptId))
        );

        if (departmentSelect) {
            departmentSelect.innerHTML = availableDepartments.map(department =>
                '<option value="' + department.deptId + '">'
                + esc(department.deptName)
                + '</option>'
            ).join('');
        }

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
                ? '저장된 부서 권한'
                : '저장하지 않은 변경사항';
    }

    function openDetail(resourceId) {
        selected = objects.find(object =>
            String(object.resourceId) === String(resourceId)
        );

        if (!selected) {
            return;
        }

        document.getElementById('detail-object-name').textContent =
            selected.objectName;

        document.getElementById('detail-object-meta').textContent =
            (selected.description || selected.resourceName || '')
            + (selected.schemaName ? ' · ' + selected.schemaName : '');

        const resourceState = selected.resourceEnabled === true;
        const stateElement =
            document.getElementById('detail-resource-state');

        stateElement.textContent = resourceState ? '활성' : '비활성';
        stateElement.classList.toggle('is-active', resourceState);

        if (isAdmin) {
            document.getElementById('policy-access').value =
                selected.accessLevel || 'GENERAL';
            document.getElementById('policy-export').value =
                selected.exportPolicy || 'BLOCK';
            document.getElementById('policy-enabled').value =
                resourceState ? 'Y' : 'N';
        } else {
            document.getElementById('policy-access-read').textContent =
                selected.accessLevel || 'GENERAL';

            const exportPolicy = selected.exportPolicy || 'BLOCK';
            document.getElementById('policy-export-read').textContent =
                exportPolicy
                + (exportPolicy === 'BLOCK' ? ' · 심사 필요' : '');

            document.getElementById('policy-enabled-read').textContent =
                resourceState ? '활성' : '비활성';
        }

        document.getElementById('policy-save-state').textContent =
            '저장된 정책';

        const savePolicyButton =
            document.getElementById('save-resource-policy');

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
                '<tr><td colspan="4" class="empty">'
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

    document.getElementById('search-resources')
        .addEventListener('click', applySearch);

    search.addEventListener('keydown', event => {
        if (event.key === 'Enter') {
            event.preventDefault();
            applySearch();
        }
    });

    document.getElementById('reset-resource-search')
        .addEventListener('click', () => {
            search.value = '';
            currentPage = 1;
            renderRows();
        });

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
                        '저장하지 않은 변경사항';
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

        document.getElementById('toggle-add-dept')
            .addEventListener('click', () => {
                const row = document.getElementById('add-dept-row');
                row.hidden = !row.hidden;
            });

        document.getElementById('cancel-add-dept')
            .addEventListener('click', () => {
                document.getElementById('add-dept-row').hidden = true;
            });

        document.getElementById('confirm-add-dept')
            .addEventListener('click', () => {
                if (departmentSelect.value) {
                    selectedDepartmentIds.add(Number(departmentSelect.value));
                    renderDepartments();
                }

                document.getElementById('add-dept-row').hidden = true;
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