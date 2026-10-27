<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div class="view-detail-page">
    <a id="view-back-link"
       class="view-back-link"
       href="${pageContext.request.contextPath}/views">
        ← 뷰 목록
    </a>

    <div class="page-heading view-heading">
        <div>
            <p id="view-detail-schema" class="caption view-schema"></p>
            <h1 id="view-detail-title" class="view-title">뷰 상세</h1>
            <p id="view-detail-resource-name"
               class="caption view-resource-name"></p>
        </div>

        <span class="badge">읽기 전용</span>
    </div>

    <section id="view-detail-loading"
             class="panel view-loading"
             role="status">
        뷰 정보를 불러오는 중입니다.
    </section>

    <section id="view-detail-error" class="panel" hidden>
        <h2>뷰 정보를 불러오지 못했습니다.</h2>
        <p id="view-detail-error-message"
           class="view-error-message"
           role="alert"></p>

        <div class="view-error-actions">
            <button id="view-detail-retry" type="button" class="button primary">
                다시 시도
            </button>

            <a id="view-error-back-link"
               class="button"
               href="${pageContext.request.contextPath}/views">
                목록으로
            </a>
        </div>
    </section>

    <div id="view-detail-content" hidden>
        <section class="panel view-panel">
            <div class="panel-heading">
                <h2>뷰 정보</h2>
                <span id="view-detail-registration" class="badge"></span>
            </div>

            <dl class="view-info">
                <div>
                    <dt>스키마</dt>
                    <dd id="view-info-schema"></dd>
                </div>

                <div>
                    <dt>등록 이름</dt>
                    <dd id="view-info-resource-name"></dd>
                </div>

                <div class="view-info-description">
                    <dt>설명</dt>
                    <dd id="view-info-description"></dd>
                </div>
            </dl>
        </section>

        <section class="panel view-panel">
            <div class="panel-heading">
                <h2>부서별 접근 정책</h2>
                <span id="view-policy-count" class="caption"></span>
            </div>

            <p id="view-policy-notice" class="view-policy-notice" hidden></p>

            <div class="table-scroll view-table-scroll">
                <table class="view-policy-table">
                    <caption class="sr-only">
                        현재 사용자에게 공개된 부서별 접근 정책
                    </caption>

                    <thead>
                        <tr>
                            <th scope="col">부서</th>
                            <th scope="col">접근 등급</th>
                            <th scope="col">반출 정책</th>
                            <th scope="col">정책 상태</th>
                        </tr>
                    </thead>

                    <tbody id="view-policy-rows"></tbody>
                </table>
            </div>

            <p class="caption view-policy-description">
                실제 데이터 조회·다운로드 시 현재 권한을 다시 확인합니다.
            </p>
        </section>

        <section class="panel view-panel">
            <div class="panel-heading">
                <h2>컬럼 목록</h2>
                <span id="view-column-count" class="caption"></span>
            </div>

            <div class="table-scroll view-table-scroll">
                <table class="view-column-table">
                    <caption class="sr-only">현재 뷰의 컬럼 구조</caption>

                    <thead>
                        <tr>
                            <th scope="col">순서</th>
                            <th scope="col">컬럼명</th>
                            <th scope="col">자료형</th>
                            <th scope="col">NULL 허용 여부</th>
                            <th scope="col">설명</th>
                        </tr>
                    </thead>

                    <tbody id="view-column-rows"></tbody>
                </table>
            </div>
        </section>

        <section class="panel view-panel">
            <div class="panel-heading">
                <h2>뷰 정의 · CREATE문</h2>
                <span class="caption">현재 DB 정의</span>
            </div>

            <pre class="view-ddl"><code id="view-create-statement"></code></pre>

            <p class="caption view-ddl-description">
                MySQL에서 조회한 현재 정의입니다.
                최초 작성 원문과 표현이 다를 수 있으며, 실행·수정 기능은 제공하지 않습니다.
            </p>
        </section>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/vendor/sql-formatter.min.js"></script>

<script>
(() => {
    const params = new URLSearchParams(window.location.search);
    const schemaName = params.get('schemaName');
    const objectName = params.get('objectName');

    const loadingSection = $('#view-detail-loading');
    const errorSection = $('#view-detail-error');
    const contentSection = $('#view-detail-content');
    const retryButton = $('#view-detail-retry');

    let loading = false;

    /** 목록으로 돌아갈 때 부서·검색 조건을 복원한다. */
    function configureBackLinks() {
        const listParams = new URLSearchParams();

        if (params.get('deptId')) {
            listParams.set('deptId', params.get('deptId'));
        }

        if (params.get('keyword')) {
            listParams.set('keyword', params.get('keyword'));
        }

        const query = listParams.toString();
        const url = BASE + '/views' + (query ? '?' + query : '');

        $('#view-back-link').href = url;
        $('#view-error-back-link').href = url;
    }

    /** DB에서 받은 문자열은 HTML이 아닌 텍스트로 표시한다. */
    function appendCell(row, value, className = '') {
        const cell = document.createElement('td');

        cell.textContent = value == null || value === '' ? '—' : String(value);
        cell.className = className;

        row.appendChild(cell);

        return cell;
    }

    function appendBadgeCell(row, label, kind = 'neutral') {
        const cell = document.createElement('td');
        const badgeElement = document.createElement('span');

        badgeElement.className = 'badge ' + kind;
        badgeElement.textContent = label;

        cell.appendChild(badgeElement);
        row.appendChild(cell);
    }

    function showEmptyRow(body, columnCount, message) {
        const row = document.createElement('tr');
        const cell = appendCell(row, message, 'empty');

        cell.colSpan = columnCount;
        body.replaceChildren(row);
    }

    function renderRegistration(detail) {
        const badgeElement = $('#view-detail-registration');

        let label = '미등록';
        let kind = 'neutral';

        if (detail.registered && detail.enabled === 'Y') {
            label = '활성 등록';
            kind = 'success';
        } else if (detail.registered && detail.enabled === 'N') {
            label = '비활성 등록';
            kind = 'waiting';
        } else if (detail.registered) {
            label = '상태 확인 필요';
            kind = 'waiting';
        }

        badgeElement.className = 'badge ' + kind;
        badgeElement.textContent = label;
    }

    /** 미등록·비활성·활성 정책 중복을 구분해 표시한다. */
    function renderPolicyRow(policies) {
        const row = document.createElement('tr');

        const registered = policies.filter(policy => policy.policyId != null);
        const active = registered.filter(policy => policy.policyEnabled === 'Y');

        appendCell(row, policies[0].deptName);

        if (registered.length === 0) {
            appendCell(row, '—');
            appendCell(row, '—');
            appendBadgeCell(row, '미등록');

            return row;
        }

        if (active.length !== 1) {
            appendCell(row, '—');
            appendCell(row, '—');

            const allInactive = registered.every(policy => policy.policyEnabled === 'N');

            const label = active.length > 1
                    ? '활성 정책 중복'
                    : allInactive ? '비활성' : '상태 확인 필요';

            appendBadgeCell(row, label, 'waiting');

            return row;
        }

        const policy = active[0];
        const accessNames = {
            GENERAL: '일반',
            SENSITIVE: '민감'
        };

        const accessName = accessNames[policy.accessLevel];
        const validExport =
                policy.exportPolicy === 'ALLOW' || policy.exportPolicy === 'BLOCK';

        appendCell(row, accessName || '설정 확인 필요');

        if (policy.exportPolicy === 'ALLOW') {
            appendBadgeCell(row, '반출 허용', 'success');
        } else if (policy.exportPolicy === 'BLOCK') {
            appendBadgeCell(row, '반출 금지', 'waiting');
        } else {
            appendBadgeCell(row, '설정 확인 필요', 'waiting');
        }

        appendBadgeCell(
            row,
            accessName && validExport ? '활성' : '설정 확인 필요',
            accessName && validExport ? 'success' : 'waiting'
        );

        return row;
    }

    /**
     * API가 공개 범위를 제한한 정책만 표시한다.
     * 일반 사용자의 다른 부서 정책을 화면에서 추가 조회하지 않는다.
     */
    function renderPolicies(detail) {
        const body = $('#view-policy-rows');
        const notice = $('#view-policy-notice');
        const groups = new Map();

        detail.departmentPolicies.forEach(function (policy) {
            const key = String(policy.deptId);

            if (!groups.has(key)) {
                groups.set(key, []);
            }

            groups.get(key).push(policy);
        });

        $('#view-policy-count').textContent = '표시 부서 ' + groups.size + '개';

        notice.hidden = true;
        notice.textContent = '';

        if (!detail.registered) {
            notice.textContent = '미등록 뷰입니다. 데이터 조회에는 리소스와 부서 정책 등록이 필요합니다.';
            notice.hidden = false;
        } else if (detail.enabled !== 'Y') {
            notice.textContent = '리소스가 비활성이거나 상태 확인이 필요합니다. 아래 정책만으로 접근이 허용되지 않습니다.';
            notice.hidden = false;
        }

        if (groups.size === 0) {
            showEmptyRow(body, 4, '표시할 부서 정책이 없습니다.');
            return;
        }

        const fragment = document.createDocumentFragment();

        groups.forEach(function (policies) {
            fragment.appendChild(renderPolicyRow(policies));
        });

        body.replaceChildren(fragment);
    }

    function renderColumns(columns) {
        const body = $('#view-column-rows');

        $('#view-column-count').textContent = '총 ' + columns.length + '개';

        if (columns.length === 0) {
            showEmptyRow(body, 5, '표시할 컬럼이 없습니다.');
            return;
        }

        const fragment = document.createDocumentFragment();
        const nullableNames = {
            YES: '허용',
            NO: '불가'
        };

        const sortedColumns = columns.slice()
                .sort((first, second) => first.ordinalPosition - second.ordinalPosition);

        sortedColumns.forEach(function (column) {
            const row = document.createElement('tr');

            appendCell(row, column.ordinalPosition);
            appendCell(row, column.columnName, 'view-column-name');
            appendCell(row, column.columnType, 'view-column-type');
            appendCell(row, nullableNames[column.isNullable] || '확인 필요');
            appendCell(row, column.columnComment);

            fragment.appendChild(row);
        });

        body.replaceChildren(fragment);
    }
	
	/**
	 * 화면 표시용으로 줄바꿈·들여쓰기를 적용한다.
	 * 정렬할 수 없는 구문은 원문으로 표시한다.
	 */
	function formatCreateStatement(sql) {
	    if (!window.sqlFormatter) {
	        return sql;
	    }

	    try {
	        return window.sqlFormatter.format(sql, {
	            language: 'mysql',
	            tabWidth: 4,
	            keywordCase: 'upper'
	        });
	    } catch (error) {
	        console.warn('CREATE문 정렬에 실패해 원문을 표시합니다.');
	        return sql;
	    }
	}

    function renderDetail(detail) {
        $('#view-detail-schema').textContent = detail.schemaName;
        $('#view-detail-title').textContent = detail.objectName;
        $('#view-detail-resource-name').textContent = detail.resourceName || '등록 이름 없음';

        $('#view-info-schema').textContent = detail.schemaName;
        $('#view-info-resource-name').textContent = detail.resourceName || '등록 이름 없음';
        $('#view-info-description').textContent = detail.description || '등록된 설명이 없습니다.';

        renderRegistration(detail);
        renderPolicies(detail);
        renderColumns(detail.columns);

		// 화면 표시용으로 정렬하며, 실패하면 원문을 표시한다.
		$('#view-create-statement').textContent = formatCreateStatement(detail.createStatement);

        document.title = detail.objectName + ' | SQLFlow';
    }

    async function loadDetail() {
        if (loading) {
            return;
        }

        loading = true;
        retryButton.disabled = true;

        loadingSection.hidden = false;
        errorSection.hidden = true;
        contentSection.hidden = true;

        try {
            if (!schemaName || !schemaName.trim() || !objectName || !objectName.trim()) {
                throw new Error('스키마명과 뷰 이름이 없습니다. 목록에서 다시 선택해 주세요.');
            }

            const query = new URLSearchParams({
                schemaName: schemaName,
                objectName: objectName
            });

            const detail = await api('/views/detail?' + query.toString());

            if (!detail
                    || typeof detail.schemaName !== 'string'
                    || typeof detail.objectName !== 'string'
                    || !Array.isArray(detail.departmentPolicies)
                    || !Array.isArray(detail.columns)
                    || typeof detail.createStatement !== 'string') {

                throw new Error('뷰 상세 응답을 확인할 수 없습니다.');
            }

            renderDetail(detail);
            contentSection.hidden = false;
        } catch (error) {
            $('#view-detail-error-message').textContent =
                    error.message || '뷰 정보를 불러오지 못했습니다.';

            errorSection.hidden = false;
        } finally {
            loading = false;
            retryButton.disabled = false;
            loadingSection.hidden = true;
        }
    }

    retryButton.addEventListener('click', loadDetail);

    configureBackLinks();
    loadDetail();
})();
</script>

<%@ include file="fragments/bottom.jspf" %>