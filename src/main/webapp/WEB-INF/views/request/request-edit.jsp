<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div id="edit-config"
     data-request-id="<c:out value='${requestId}'/>"
     data-redraft="${redraftMode eq true}"
     hidden>
</div>

<div class="page-heading">
    <div>
        <span id="edit-request-number" class="caption"></span>
        <h1><c:out value="${pageTitle}"/></h1>
    </div>
    <span class="badge">초안</span>
</div>

<p id="edit-loading">기존 내용을 불러오는 중입니다.</p>

<form id="edit-form"
	  class="panel form-panel request-editor">
    <fieldset id="edit-fields" disabled>
        <legend class="sr-only">조회 요청 수정</legend>

		<section class="request-basic-fields"
		         aria-label="요청 기본 정보">
		    <div class="request-heading-fields">
		        <label for="edit-title"
		               class="request-input-field request-title-field">
		            <span>
		                요청 제목 <span class="required">*</span>
		            </span>

		            <input id="edit-title"
		                   type="text"
		                   required
		                   maxlength="200"
		                   placeholder="요청 제목을 입력하세요.">
		        </label>

		        <label for="edit-desired-date"
		               class="request-input-field">
		            <span>처리 희망일 · 선택</span>

		            <input id="edit-desired-date"
		                   type="date"
		                   aria-describedby="edit-priority-help">
		        </label>

		        <label for="edit-is-urgent"
		               class="request-urgent-check">
		            <input id="edit-is-urgent"
		                   type="checkbox"
		                   aria-describedby="edit-priority-help">
		            <span>긴급</span>
		        </label>
		    </div>

		    <label for="edit-purpose" class="request-input-field">
		        <span>
		            조회 목적 <span class="required">*</span>
		        </span>

		        <textarea id="edit-purpose"
		                  rows="1"
		                  required
		                  placeholder="데이터가 필요한 목적을 입력하세요."></textarea>
		    </label>

		    <label for="edit-detail-content"
		           class="request-input-field">
		        <span>상세 내용 · 선택</span>

		        <textarea id="edit-detail-content"
		                  rows="2"
		                  placeholder="요청 배경, 조회 범위, 주의사항을 입력하세요."></textarea>
		    </label>

		    <p id="edit-priority-help" class="sr-only">
		        긴급 여부와 처리 희망일은 처리 담당자의 참고 정보입니다.
		    </p>
		</section>
		
        <section class="request-sql-section"
		         aria-labelledby="edit-sql-heading">
		    <div class="request-sql-heading">
		        <h2 id="edit-sql-heading">SQL 작성</h2>
		    </div>

		    <div class="request-sql-toolbar">
		        <div id="edit-sql-tabs"
		             class="request-sql-tabs"
		             role="tablist"
		             aria-label="수정할 SQL 선택">
		        </div>

		        <div class="request-sql-actions">
		            <span id="edit-sql-count"
		                  class="caption"
		                  aria-live="polite"></span>

		            <button id="edit-add-sql"
		                    type="button"
		                    class="button">
		                + SQL 추가
		            </button>
		        </div>
		    </div>

		    <div id="edit-sql-items"></div>
		</section>

		<div class="form-footer">
		    <span class="caption">
		        빈 SQL 입력 칸은 저장 목록에서 제외됩니다.
		    </span>

		    <div class="request-save-actions">
		        <a id="edit-cancel" class="button">
		            <c:choose>
		                <c:when test="${redraftMode eq true}">
		                    원본 상세로 돌아가기
		                </c:when>
		                <c:otherwise>
		                    상세로 돌아가기
		                </c:otherwise>
		            </c:choose>
		        </a>

		        <button id="edit-save"
		                type="submit"
		                class="button primary">
		            <c:choose>
		                <c:when test="${redraftMode eq true}">
		                    새 초안 저장
		                </c:when>
		                <c:otherwise>
		                    수정 저장
		                </c:otherwise>
		            </c:choose>
		        </button>
		    </div>
		</div>
    </fieldset>
</form>

<script>
	const editConfig = $('#edit-config');
	const editRequestId = editConfig.dataset.requestId;
	const isRedraft = editConfig.dataset.redraft === 'true';

	const detailUrl =
	    BASE + '/requests/' + encodeURIComponent(editRequestId);

	$('#edit-cancel').href = detailUrl;

	let editSqls = [];
	let activeEditSqlIndex = 0;
	let saveInProgress = false;

	$('#edit-cancel').addEventListener('click', function (event) {
	    if (saveInProgress) {
	        event.preventDefault();
	    }
	});
	
	function readEditSqls() {
	    // 숨겨진 탭의 입력값도 함께 읽는다.
	    editSqls = Array.from(
	        document.querySelectorAll('#edit-sql-items .sql-item')
	    ).map(section => ({
	        title: section.querySelector('.edit-sql-title-input').value,
	        sqlText: section.querySelector('.edit-sql-editor').value
	    }));
	}

	function activateEditSqlTab(index, focusTab = false) {
	    if (saveInProgress
	            || index < 0
	            || index >= editSqls.length) {
	        return;
	    }

	    activeEditSqlIndex = index;

	    const tabs = $('#edit-sql-tabs')
	        .querySelectorAll('[role="tab"]');

	    const panels = $('#edit-sql-items')
	        .querySelectorAll('[role="tabpanel"]');

	    tabs.forEach(function (tab, tabIndex) {
	        const selected = tabIndex === index;

	        tab.setAttribute('aria-selected', String(selected));
	        tab.tabIndex = selected ? 0 : -1;
	    });

	    panels.forEach(function (panel, panelIndex) {
	        panel.hidden = panelIndex !== index;
	    });

	    if (focusTab) {
	        tabs[index]?.focus();
	    }
	}

	function removeEditSql(index) {
	    if (saveInProgress || editSqls.length <= 1) return;

	    readEditSqls();

	    const item = editSqls[index];

	    if ((item.title.trim() || item.sqlText.trim())
	            && !window.confirm(
	                'SQL ' + (index + 1) + '을 삭제하시겠습니까?'
	            )) {
	        return;
	    }

	    editSqls.splice(index, 1);

	    if (index < activeEditSqlIndex) {
	        activeEditSqlIndex--;
	    }

	    activeEditSqlIndex = Math.min(
	        activeEditSqlIndex,
	        editSqls.length - 1
	    );

	    renderEditSqls();
	    activateEditSqlTab(activeEditSqlIndex, true);
	}

	function renderEditSqls() {
	    const tabs = $('#edit-sql-tabs');
	    const box = $('#edit-sql-items');

	    // 등록한 SQL이 없어도 빈 입력 칸 하나는 표시한다.
	    if (editSqls.length === 0) {
	        editSqls.push({ title: '', sqlText: '' });
	    }

	    activeEditSqlIndex = Math.max(
	        0,
	        Math.min(activeEditSqlIndex, editSqls.length - 1)
	    );

	    tabs.replaceChildren();
	    box.replaceChildren();

	    editSqls.forEach(function (item, index) {
	        const tabId = 'edit-sql-tab-' + index;
	        const panelId = 'edit-sql-panel-' + index;

	        const group = document.createElement('div');
	        group.className = 'request-sql-tab-group';
	        group.setAttribute('role', 'presentation');

	        const tab = document.createElement('button');
	        tab.id = tabId;
	        tab.type = 'button';
	        tab.className = 'request-sql-tab';
	        tab.textContent = 'SQL ' + (index + 1);
	        tab.setAttribute('role', 'tab');
	        tab.setAttribute('aria-controls', panelId);

	        tab.addEventListener('click', function () {
	            activateEditSqlTab(index);
	        });

	        const remove = document.createElement('button');
	        remove.type = 'button';
	        remove.className = 'request-sql-tab-remove';
	        remove.textContent = '×';
	        remove.disabled = editSqls.length <= 1;

	        remove.setAttribute(
	            'aria-label',
	            'SQL ' + (index + 1) + ' 삭제'
	        );

	        remove.title = editSqls.length <= 1
	            ? 'SQL 입력 칸은 최소 1개 유지됩니다.'
	            : 'SQL 삭제';

	        remove.addEventListener('click', function () {
	            removeEditSql(index);
	        });

	        group.append(tab, remove);
	        tabs.append(group);

	        const section = document.createElement('section');
	        section.id = panelId;
	        section.className = 'sql-item';
	        section.setAttribute('role', 'tabpanel');
	        section.setAttribute('aria-labelledby', tabId);

	        // 사용자 입력값은 아래에서 value로 넣는다.
	        section.innerHTML =
	            '<label class="request-sql-field">'
	            + '<span>SQL 제목 <span class="required">*</span></span>'
	            + '<input type="text"'
	            + ' class="sql-title-input edit-sql-title-input"'
	            + ' maxlength="200"'
	            + ' aria-required="true"'
	            + ' placeholder="SQL 제목을 입력하세요.">'
	            + '</label>'
	            + '<label class="request-sql-field">'
	            + '<span>SQL 원문</span>'
	            + '<textarea class="sql-editor edit-sql-editor"'
	            + ' rows="6"'
	            + ' maxlength="20000"'
	            + ' spellcheck="false"'
	            + ' placeholder="SELECT SQL을 입력하세요.">'
	            + '</textarea>'
	            + '</label>';

	        section.querySelector('.edit-sql-title-input').value =
	            item.title || '';

	        section.querySelector('.edit-sql-editor').value =
	            item.sqlText || '';

	        box.append(section);
	    });

	    $('#edit-sql-count').textContent =
	        editSqls.length + ' / 5';

	    $('#edit-add-sql').disabled =
	        editSqls.length >= 5 || saveInProgress;

	    activateEditSqlTab(activeEditSqlIndex);
	}

	$('#edit-add-sql').addEventListener('click', function () {
	    if (saveInProgress || editSqls.length >= 5) return;

	    readEditSqls();

	    editSqls.push({ title: '', sqlText: '' });
	    activeEditSqlIndex = editSqls.length - 1;

	    renderEditSqls();
	    activateEditSqlTab(activeEditSqlIndex, true);
	});

	$('#edit-sql-tabs').addEventListener('keydown', function (event) {
	    const tab = event.target.closest('[role="tab"]');

	    if (!tab || saveInProgress) return;

	    let nextIndex = activeEditSqlIndex;

	    if (event.key === 'ArrowRight') {
	        nextIndex = (nextIndex + 1) % editSqls.length;
	    } else if (event.key === 'ArrowLeft') {
	        nextIndex =
	            (nextIndex - 1 + editSqls.length) % editSqls.length;
	    } else if (event.key === 'Home') {
	        nextIndex = 0;
	    } else if (event.key === 'End') {
	        nextIndex = editSqls.length - 1;
	    } else {
	        return;
	    }

	    event.preventDefault();
	    activateEditSqlTab(nextIndex, true);
	});

    async function loadEditDraft() {
        try {
            const request = await api(
                '/requests/mine/' + encodeURIComponent(editRequestId)
            );

			const editable = isRedraft
			    ? ['REJECTED', 'WITHDRAWN'].includes(request.requestStatus)
			    : request.requestStatus === 'DRAFT';

			if (!editable) {
			    const message = isRedraft
			        ? '반려되거나 회수된 요청만 재기안할 수 있습니다.'
			        : '초안 상태의 요청만 수정할 수 있습니다.';

			    $('#edit-loading').textContent = message;
			    notify(message, true);
			    return;
			}

			$('#edit-request-number').textContent =
				(isRedraft ? '원본 요청: ' : '') + request.requestNo;
			
            $('#edit-title').value = request.title;
            $('#edit-purpose').value = request.purpose;
			$('#edit-detail-content').value = request.detailContent || '';
			$('#edit-is-urgent').checked = request.isUrgent === 'Y';
			$('#edit-desired-date').value = request.desiredDate || '';

			editSqls = request.items.map(item => ({
			    title: item.queryTitle || '',
			    sqlText: item.sqlText || ''
			}));
			
            renderEditSqls();

            $('#edit-loading').hidden = true;
            $('#edit-fields').disabled = false;
        } catch (error) {
            $('#edit-loading').textContent =
                '요청을 불러오지 못했습니다. 상세 화면으로 돌아가 확인하세요.';
            notify(error.message, true);
        }
    }

	$('#edit-form').addEventListener('submit', async function (event) {
	    event.preventDefault();

	    if (saveInProgress) {
	        return;
	    }

	    const title = $('#edit-title').value.trim();
	    const purpose = $('#edit-purpose').value.trim();

	    if (!title) {
	        notify('요청 제목을 입력하세요.', true);
	        $('#edit-title').focus();
	        return;
	    }

	    if (!purpose) {
	        notify('조회 목적을 입력하세요.', true);
	        $('#edit-purpose').focus();
	        return;
	    }

	    readEditSqls();
		
		const missingTitleIndex = editSqls.findIndex(item =>
		    item.sqlText.trim() && !item.title.trim()
		);

		if (missingTitleIndex !== -1) {
			activateEditSqlTab(missingTitleIndex);
			
		    document.querySelectorAll(
		        '#edit-sql-items .edit-sql-title-input'
		    )[missingTitleIndex].focus();

		    notify(
		        'SQL ' + (missingTitleIndex + 1)
		        + '의 제목을 입력하세요.',
		        true
		    );

		    return;
		}

	    const savedItems = editSqls.filter(
	        item => item.sqlText.trim().length > 0
	    );

	    const payload = {
	        title,
	        purpose,
	        detailContent: $('#edit-detail-content').value.trim() || null,
	        isUrgent: $('#edit-is-urgent').checked ? 'Y' : 'N',
	        desiredDate: $('#edit-desired-date').value || null,
	        sqls: savedItems.map(item => item.sqlText),
	        sqlTitles: savedItems.map(item => item.title.trim())
	    };

	    const saveButton = $('#edit-save');
	    const originalText = saveButton.textContent;

	    saveInProgress = true;
	    $('#edit-fields').disabled = true;
	    saveButton.textContent = '저장 중…';

	    let navigating = false;

	    try {
	        let destination = detailUrl;

	        if (isRedraft) {
	            // 저장 버튼을 누른 시점에 새 초안을 생성한다.
	            const result = await api(
	                '/requests/'
	                + encodeURIComponent(editRequestId)
	                + '/redraft',
	                {
	                    method: 'POST',
	                    body: JSON.stringify(payload)
	                }
	            );

	            if (!result?.requestId) {
	                throw Error(
	                    '저장 결과의 요청 ID를 확인할 수 없습니다. '
	                    + '내 요청 목록에서 생성 여부를 확인해 주세요.'
	                );
	            }

	            destination =
	                BASE + '/requests/'
	                + encodeURIComponent(result.requestId);
	        } else {
	            // 기존 초안 수정은 동일한 요청에 저장한다.
	            await api(
	                '/requests/drafts/'
	                + encodeURIComponent(editRequestId),
	                {
	                    method: 'PUT',
	                    body: JSON.stringify(payload)
	                }
	            );
	        }

	        location.assign(destination);
	        navigating = true;
	    } catch (error) {
	        notify(error.message, true);
	    } finally {
	        // 성공 후 이동 중에는 중복 저장을 막는다.
	        if (!navigating) {
	            saveInProgress = false;
	            $('#edit-fields').disabled = false;
	            saveButton.textContent = originalText;
	        }
	    }
	});

    loadEditDraft();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
