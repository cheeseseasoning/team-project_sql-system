<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="view-detail-page">
    <div class="view-detail-toolbar">
        <a id="view-back-link" class="view-back-link" href="${pageContext.request.contextPath}/views">
            ← 뷰 목록
        </a>
        <span class="caption">읽기 전용</span>
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
		<section class="panel view-panel" aria-label="뷰 기본 정보">
		    <dl class="view-info">
		        <div>
		            <dt>뷰 이름</dt>
		            <dd id="view-detail-title" class="view-info-emphasis"></dd>
		        </div>
		        <div>
		            <dt>등록 이름</dt>
		            <dd id="view-info-resource-name" class="view-info-emphasis"></dd>
		        </div>

		        <div>
		            <dt>스키마</dt>
		            <dd id="view-info-schema"></dd>
		        </div>

		        <div>
		            <dt>등록 상태</dt>
		            <dd><span id="view-detail-registration" class="badge"></span></dd>
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
        </section>

        <section class="panel view-panel">
            <div class="panel-heading">
				<div class="view-column-heading">
				    <h2>컬럼 목록</h2>
				        <button id="view-column-edit" class="button" type="button" hidden
				                aria-haspopup="dialog" aria-controls="view-column-dialog">
				            컬럼 정보 편집
				        </button>
				</div>
                <span id="view-column-count" class="caption"></span>
            </div>

            <div class="table-scroll view-table-scroll">
                <table class="view-column-table">
                    <caption class="sr-only">현재 뷰의 컬럼 구조</caption>

                    <thead>
                        <tr>
                            <th scope="col">순서</th>
                            <th scope="col">논리명</th>
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

		    <dialog id="view-column-dialog" class="view-column-dialog" aria-labelledby="view-column-dialog-title">
		        <div class="view-column-editor">
		            <header class="view-column-editor-header">
		                <h2 id="view-column-dialog-title">컬럼 정보 편집</h2>
		                <p id="view-column-dialog-name" class="caption view-column-editor-name"></p>
		            </header>

		            <div class="view-column-editor-body">
		                <div class="view-column-editor-labels" aria-hidden="true">
		                    <span>컬럼명 · 자료형</span>
		                    <span>논리명</span>
		                    <span>설명</span>
		                </div>
		                <div id="view-column-editor-rows"></div>
		            </div>

		            <form id="view-column-editor-form" class="view-column-editor-footer">
		                <p id="view-column-editor-status" class="caption" role="status"></p>
		                <button class="button" type="submit" formmethod="dialog" formnovalidate>닫기</button>
		                <button id="view-column-save" class="button primary" type="submit">저장</button>
		            </form>
		        </div>
		    </dialog>

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

	/** 목록으로 돌아갈 때 부서, 검색어, 페이지를 복원한다. */
	function configureBackLinks() {
	    const listParams = new URLSearchParams();

	    for (const key of ['deptId', 'keyword', 'page']) {
	        const value = params.get(key);

	        if (value) {
	            listParams.set(key, value);
	        }
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

	function renderColumns(columns, metadata = new Map()) {
	    const body = $('#view-column-rows');
	    $('#view-column-count').textContent = '총 ' + columns.length + '개';

	    if (columns.length === 0) {
	        showEmptyRow(body, 6, '표시할 컬럼이 없습니다.');
	        return;
	    }

	    const fragment = document.createDocumentFragment();
	    const nullableNames = { YES: '허용', NO: '불가' };
	    const sortedColumns = columns.slice()
	        .sort((first, second) => first.ordinalPosition - second.ordinalPosition);

	    sortedColumns.forEach(function (column) {
	        const row = document.createElement('tr');

	        appendCell(row, column.ordinalPosition);
	        const entry = metadata.get(column.columnName);
	        appendCell(row, entry?.logicalName || '미등록');
	        appendCell(row, column.columnName, 'view-column-name');
	        appendCell(row, column.columnType, 'view-column-type');
	        appendCell(row, nullableNames[column.isNullable] || '확인 필요');
	        appendCell(row, entry?.description || column.columnComment);

	        fragment.appendChild(row);
	    });

	    body.replaceChildren(fragment);
	}

	/**
	 * 화면 표시용으로 줄바꿈·들여쓰기를 적용한다.
	 * 정렬할 수 없는 구문은 원문으로 표시한다.
	 */
	function formatCreateStatement(sql) {
        return window.SqlDisplay?.format(sql) ?? sql;
	}

	async function bindColumnEditor(detail) {
	    const button = $('#view-column-edit');
	    const url = '/views/column-metadata?' + new URLSearchParams({
	        schemaName: detail.schemaName, objectName: detail.objectName
	    });
	    button.hidden = true;
	    let response;
	    try {
	        response = await api(url);
	    } catch (error) {
	        notify('컬럼 메타데이터를 불러오지 못했습니다.', true);
	        return;
	    }
	    const metadata = new Map(response.columns.map(entry => [entry.columnName, entry]));
	    renderColumns(detail.columns, metadata);
	    if (!response.canEdit || detail.columns.length === 0) {
	        return;
	    }
	    button.hidden = false;

	    const dialog = $('#view-column-dialog');
	    const rows = $('#view-column-editor-rows');
	    const form = $('#view-column-editor-form');
	    const save = $('#view-column-save');
	    const status = $('#view-column-editor-status');
	    let inputs = [];

	    button.onclick = function () {
	        inputs = [];
	        status.textContent = '';
	        const fragment = document.createDocumentFragment();
	        $('#view-column-dialog-name').textContent = detail.objectName;

	        // 실제 뷰 컬럼을 기준으로 입력 행을 만들고, 컬럼명과 자료형은 텍스트로 표시한다.
	        detail.columns.forEach(function (column) {
	            const row = document.createElement('div');
	            row.className = 'view-column-editor-row';

	            const identity = document.createElement('div');
	            identity.className = 'view-column-editor-identity';
	            const columnName = document.createElement('strong');
	            columnName.textContent = column.columnName;

	            const columnType = document.createElement('span');
	            columnType.className = 'caption';
	            columnType.textContent = column.columnType;
	            identity.append(columnName, columnType);

	            const logicalName = document.createElement('input');
	            logicalName.type = 'text';
	            logicalName.maxLength = 255;
	            logicalName.placeholder = '논리명';
	            logicalName.setAttribute('aria-label', column.columnName + ' 논리명');

	            const description = document.createElement('textarea');
	            description.rows = 1;
	            description.maxLength = 1000;
	            description.placeholder = '설명';
	            description.setAttribute('aria-label', column.columnName + ' 설명');
	            const entry = metadata.get(column.columnName);
	            logicalName.value = entry?.logicalName || '';
	            description.value = entry?.description || '';
	            inputs.push({ columnName: column.columnName, logicalName, description });

	            row.append(identity, logicalName, description);
	            fragment.appendChild(row);
	        });

	        rows.replaceChildren(fragment);
	        dialog.showModal();
	    };

	    dialog.oncancel = function (event) {
	        if (save.disabled) event.preventDefault();
	    };
	    form.onsubmit = async function (event) {
	        if (save.disabled || event.submitter?.formMethod !== 'dialog') event.preventDefault();
	        if (save.disabled || event.submitter?.formMethod === 'dialog') return;

	        const changes = inputs.map(input => ({
	            columnName: input.columnName,
	            logicalName: input.logicalName.value.trim(),
	            description: input.description.value.trim()
	        })).filter(entry => {
	            const previous = metadata.get(entry.columnName);
	            return entry.logicalName !== (previous?.logicalName || '')
	                || entry.description !== (previous?.description || '');
	        });
	        if (changes.length === 0) {
	            dialog.close();
	            return;
	        }
	        save.disabled = true;
	        status.textContent = '';
	        try {
	            await api(url, { method: 'PUT', body: JSON.stringify(changes) });
	            changes.forEach(entry => metadata.set(entry.columnName, entry));
	            renderColumns(detail.columns, metadata);
	            dialog.close();
	        } catch (error) {
	            status.textContent = error.message;
	        } finally {
	            save.disabled = false;
	        }
	    };
	}

	function renderDetail(detail) {
	    $('#view-detail-title').textContent = detail.objectName;
	    $('#view-info-schema').textContent = detail.schemaName;
	    $('#view-info-resource-name').textContent = detail.resourceName || '등록 이름 없음';
	    $('#view-info-description').textContent = detail.description || '등록된 설명이 없습니다.';

	    renderRegistration(detail);
	    renderPolicies(detail);
	    renderColumns(detail.columns);
		bindColumnEditor(detail);

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

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
