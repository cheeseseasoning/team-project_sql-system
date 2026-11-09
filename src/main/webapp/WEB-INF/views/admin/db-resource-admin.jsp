<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<style>
    .resource-pagination {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 16px;
        margin-top: 24px;
    }

    .resource-pagination[hidden] {
        display: none;
    }

    .resource-pagination button:disabled {
        cursor: not-allowed;
        opacity: 0.55;
    }

    .resource-page-status {
        min-width: 150px;
        text-align: center;
    }
</style>

<div class="page-heading resource-page-heading">
    <div>
        <h1>리소스·접근 정책 관리</h1>
        <p class="caption">뷰의 조회 정책 및 접근 부서를 관리합니다.</p>
    </div>
    <span class="badge">DB팀 관리</span>
</div>

<div id="resource-list-view">
    <section class="panel resource-list-panel">
        <div class="resource-list-toolbar">
            <label class="resource-search">
                <span aria-hidden="true">⌕</span>
                <input
                    id="resource-search"
                    type="search"
                    placeholder="뷰 이름, 스키마 또는 설명 검색"
                    aria-label="뷰 이름, 스키마 또는 설명 검색">
            </label>

            <button id="reload-resources" class="button" type="button">
                새로고침
            </button>
        </div>

        <div class="table-scroll">
            <table class="resource-list-table">
                <thead>
                    <tr>
                        <th>리소스</th>
                        <th>종류</th>
                        <th>조회 / 반출 정책</th>
                        <th>접근 부서</th>
                        <th>등록 상태</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody id="resource-rows">
                    <tr>
                        <td colspan="6" class="empty">뷰 목록을 불러오는 중입니다.</td>
                    </tr>
                </tbody>
            </table>
        </div>

        <div class="resource-table-foot">
            <span id="resource-total"></span>
            <span>뷰만 표시</span>
        </div>

        <nav
            id="resource-pagination"
            class="resource-pagination"
            aria-label="뷰 목록 페이지 이동"
            hidden>
            <button
                id="resource-previous-page"
                class="button"
                type="button">
                이전
            </button>

            <span
                id="resource-page-status"
                class="resource-page-status caption"
                aria-live="polite"></span>

            <button
                id="resource-next-page"
                class="button"
                type="button">
                다음
            </button>
        </nav>
    </section>
</div>

<div id="resource-detail-view" hidden>
    <a
        class="text-button resource-back"
        id="resource-back"
        href="${pageContext.request.contextPath}/db-admin/resources">
        ←　리소스 목록
    </a>

    <div class="resource-detail-title">
        <div>
            <span class="eyebrow">RESOURCE POLICY</span>
            <h1>리소스 정책 상세</h1>
        </div>
        <span class="badge">DB팀 관리</span>
    </div>

    <section class="panel resource-object-summary">
        <span class="resource-object-icon" aria-hidden="true">▤</span>
        <div class="resource-object-main">
            <strong id="detail-object-name"></strong>
            <span id="detail-object-meta" class="caption"></span>
        </div>
        <span id="detail-resource-state" class="badge success"></span>
    </section>

    <section class="panel resource-detail-panel">
        <div class="resource-section-heading">
            <div>
                <h2>조회·반출 정책</h2>
                <span class="caption">객체 공통 설정</span>
            </div>
        </div>

        <div class="resource-policy-fields">
            <div class="resource-policy-field">
                <span>조회 수준<code>ACCESS_LEVEL</code></span>
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
                <span>반출 정책<code>EXPORT_POLICY</code></span>
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
                <span>리소스 상태<code>ENABLED</code></span>
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

        <div class="resource-section-footer">
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
    </section>

    <section class="panel resource-detail-panel">
        <div class="resource-section-heading">
            <div class="resource-dept-heading">
                <h2>접근 권한 보유 부서</h2>
                <span id="detail-dept-count" class="caption"></span>
            </div>

            <c:if test="${pageContext.request.isUserInRole('DB_ADMIN')}">
                <button type="button" id="toggle-add-dept" class="button">
                    ＋ 부서 추가
                </button>
            </c:if>
        </div>

        <div id="add-dept-row" class="resource-add-dept" hidden>
            <select id="add-dept-select" aria-label="추가할 부서"></select>
            <button type="button" id="confirm-add-dept" class="button primary">추가</button>
            <button type="button" id="cancel-add-dept" class="button">취소</button>
        </div>

        <div class="table-scroll">
            <table class="resource-dept-table">
                <thead>
                    <tr>
                        <th>부서명</th>
                        <th class="numeric">활성 SQLFlow 계정</th>
                        <th></th>
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

<script>
(() => {
    const isAdmin = ${pageContext.request.isUserInRole('DB_ADMIN')};
    const initialResourceId = ${empty resourceId ? 'null' : resourceId};
    const pageSize = 10;

    const rows = document.getElementById('resource-rows');
    const search = document.getElementById('resource-search');
    const departmentSelect = document.getElementById('add-dept-select');
    const listView = document.getElementById('resource-list-view');
    const detailView = document.getElementById('resource-detail-view');
    const listHeading = document.querySelector('.resource-page-heading');
    const pagination = document.getElementById('resource-pagination');
    const previousPageButton = document.getElementById('resource-previous-page');
    const nextPageButton = document.getElementById('resource-next-page');
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

        return '<span class="resource-code">'
            + esc(object.accessLevel || 'GENERAL')
            + '</span><span class="resource-separator">/</span><span class="resource-code">'
            + esc(object.exportPolicy || 'BLOCK')
            + '</span>';
    }

    function getFilteredObjects() {
        const keyword = search.value.trim().toLowerCase();

        return objects.filter(object => {
            const searchableText = [
                object.schemaName,
                object.objectName,
                object.description
            ].join(' ').toLowerCase();

            return searchableText.includes(keyword);
        });
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
                    ? '검색 조건에 맞는 뷰가 없습니다.'
                    : '등록된 뷰가 없습니다.')
                + '</td></tr>';
        } else {
            rows.innerHTML = visibleObjects.map(object => {
                const resourceRegistered = Number(object.resourceId) > 0;
                const enabled = resourceRegistered && object.resourceEnabled;
                const departmentCount = (object.policies || []).length;

                const manage = resourceRegistered
                    ? '<a class="text-button open-resource" href="'
                        + BASE
                        + '/db-admin/resources/'
                        + encodeURIComponent(object.resourceId)
                        + '">정책 상세　→</a>'
                    : '<button type="button" class="text-button register-resource"'
                        + ' data-schema="' + esc(object.schemaName) + '"'
                        + ' data-object="' + esc(object.objectName) + '"'
                        + ' data-description="' + esc(object.description || '') + '">'
                        + '리소스 등록　＋</button>';

                return '<tr>'
                    + '<td><strong class="resource-name">'
                    + esc(object.objectName)
                    + '</strong><small>'
                    + esc(object.schemaName)
                    + '</small></td>'
                    + '<td><span class="resource-type">VIEW</span></td>'
                    + '<td>' + resourcePolicyLabel(object) + '</td>'
                    + '<td>' + (resourceRegistered
                        ? departmentCount + '개 부서'
                        : '—')
                    + '</td>'
                    + '<td><span class="resource-state '
                    + (enabled ? 'is-active' : '')
                    + '">'
                    + (enabled ? '활성' : resourceRegistered ? '비활성' : '미등록')
                    + '</span></td>'
                    + '<td>' + manage + '</td>'
                    + '</tr>';
            }).join('');
        }

        document.getElementById('resource-total').textContent =
            '검색 결과 ' + filteredObjects.length + '개 뷰';

        pagination.hidden = totalPages <= 1;
        previousPageButton.disabled = currentPage <= 1;
        nextPageButton.disabled =
            totalPages === 0 || currentPage >= totalPages;

        pageStatus.textContent = totalPages === 0
            ? '0 페이지'
            : currentPage + ' / ' + totalPages + ' 페이지';

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
                '<tr><td><span class="resource-dept-name">'
                + '<span class="dept-initial">'
                + esc((department.deptName || '').slice(0, 1))
                + '</span>'
                + esc(department.deptName)
                + '</span></td>'
                + '<td class="numeric">'
                + Number(department.activeAccountCount || 0).toLocaleString('ko-KR')
                + '명</td>'
                + '<td>'
                + (isAdmin
                    ? '<button type="button" class="remove-dept" data-id="'
                        + department.deptId
                        + '" aria-label="'
                        + esc(department.deptName)
                        + ' 접근 권한 제거">−</button>'
                    : '')
                + '</td></tr>'
            ).join('')
            : '<tr><td colspan="3" class="empty">'
                + '접근 권한이 있는 부서가 없습니다.'
                + '</td></tr>';

        document.getElementById('detail-dept-count').textContent =
            activeDepartments.length + '개';

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
            selected.schemaName
            + '　·　VIEW'
            + (selected.description ? '　·　' + selected.description : '');

        document.getElementById('detail-resource-state').textContent =
            selected.resourceEnabled ? '활성 리소스' : '비활성 리소스';

        if (isAdmin) {
            document.getElementById('policy-access').value =
                selected.accessLevel || 'GENERAL';
            document.getElementById('policy-export').value =
                selected.exportPolicy || 'BLOCK';
            document.getElementById('policy-enabled').value =
                selected.resourceEnabled ? 'Y' : 'N';
        } else {
            document.getElementById('policy-access-read').textContent =
                selected.accessLevel || 'GENERAL';

            const exportPolicy = selected.exportPolicy || 'BLOCK';
            document.getElementById('policy-export-read').textContent =
                exportPolicy
                + (exportPolicy === 'BLOCK' ? ' · 심사 필요' : '');

            document.getElementById('policy-enabled-read').textContent =
                selected.resourceEnabled ? '활성' : '비활성';
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

            // 화면 목록은 VIEW만 표시한다. 테이블은 기존 목록에서 제외한다.
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
        }
    }

    search.addEventListener('input', () => {
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

    document.getElementById('reload-resources')
        .addEventListener('click', load);

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