<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div id="form-config"
     data-mode="<c:out value='${mode}'/>"
     hidden>
</div>

<div class="page-heading">
    <h1><c:out value="${pageTitle}"/></h1>
    <span class="badge">초안 작성</span>
</div>

<form id="request-form"
	  class="panel form-panel request-editor">
    <fieldset id="request-fields"
              style="border: 0; padding: 0; margin: 0; min-width: 0;">
        <legend class="sr-only">조회 요청 작성</legend>

		<section class="request-basic-fields" aria-label="요청 기본 정보">
		    <div class="request-heading-fields">
		        <label for="title"
		               class="request-input-field request-title-field">
		            <span>
		                요청 제목 <span class="required">*</span>
		            </span>

		            <input id="title"
		                   type="text"
		                   required
		                   maxlength="200"
		                   placeholder="예: 활성 상품 목록 조회">
		        </label>

		        <label for="desired-date" class="request-input-field">
		            <span>처리 희망일 · 선택</span>

		            <input id="desired-date"
		                   type="date"
		                   aria-describedby="request-priority-help">
		        </label>

		        <label for="is-urgent" class="request-urgent-check">
		            <input id="is-urgent"
		                   type="checkbox"
		                   aria-describedby="request-priority-help">
		            <span>긴급</span>
		        </label>
		    </div>

		    <label for="purpose" class="request-input-field">
		        <span>
		            조회 목적 <span class="required">*</span>
		        </span>

		        <textarea id="purpose"
		                  rows="1"
		                  required
		                  placeholder="예: 월간 상품 운영 보고서 작성을 위해 판매 중인 상품 목록을 확인합니다."></textarea>
		    </label>

		    <label for="detail-content" class="request-input-field">
		        <span>상세 내용 · 선택</span>

		        <textarea id="detail-content"
		                  rows="2"
		                  placeholder="예: 상품 ID와 상품명만 조회하며, 판매 중인 상품 전체를 대상으로 합니다."></textarea>
		    </label>

		    <p id="request-priority-help" class="sr-only">
		        긴급 여부와 처리 희망일은 처리 담당자의 참고 정보입니다.
		    </p>
		</section>

		<section class="request-sql-section" aria-labelledby="sql-writing-title">
		    <div class="request-sql-heading">
		        <h2 id="sql-writing-title">SQL 작성</h2>

		        <div class="request-mode-switch"
		             role="group"
		             aria-label="SQL 작성 방식">
		            <button type="button" data-mode="direct">
		                직접 작성
		            </button>

		            <button type="button" data-mode="ai">
		                AI 작성 · 시연
		            </button>
		        </div>
		    </div>

		    <div class="request-sql-toolbar">
		        <div id="sql-tabs"
		             class="request-sql-tabs"
		             role="tablist"
		             aria-label="작성할 SQL 선택">
		        </div>

		        <div class="request-sql-actions">
		            <span id="sql-count"
		                  class="caption"
		                  aria-live="polite"></span>

		            <button id="add-sql"
		                    type="button"
		                    class="button">
		                + SQL 추가
		            </button>
		        </div>
		    </div>

		    <div id="sql-items"></div>
		</section>

		<div class="form-footer">
		    <span class="caption">
		        빈 SQL 입력 칸은 제외됩니다.
		        저장 후 상세 화면에서 제출할 수 있습니다.
		    </span>

		    <div class="request-save-actions">
		        <button id="validate-draft-sqls"
		                type="button"
		                class="button">
		            SQL 검증
		        </button>

		        <button id="save-draft"
		                type="submit"
		                class="button primary">
		            초안 저장
		        </button>
		    </div>
		</div>
    </fieldset>
</form>

<script>
    let mode = $('#form-config').dataset.mode === 'ai'
        ? 'ai'
        : 'direct';

	let sqls = [
	    { title: '', sqlText: '' }
	];

	let activeSqlIndex = 0;

    let formBusy = false;
    let createdRequestId = null;

	function activateSqlTab(index, moveFocus = false) {
	    activeSqlIndex = index;

	    const tabs = $('#sql-tabs').querySelectorAll('[role="tab"]');
	    const panels = $('#sql-items').querySelectorAll('.sql-item');

	    tabs.forEach(function (tab, tabIndex) {
	        const selected = tabIndex === index;

	        tab.setAttribute('aria-selected', String(selected));
	        tab.tabIndex = selected ? 0 : -1;
	    });

	    panels.forEach(function (panel, panelIndex) {
	        panel.hidden = panelIndex !== index;
	    });

	    if (moveFocus) {
	        tabs[index]?.focus();
	    }
	}

	function renderSqls() {
	    const tabs = $('#sql-tabs');
	    const box = $('#sql-items');

	    tabs.replaceChildren();
	    box.replaceChildren();

	    activeSqlIndex = Math.max(
	        0,
	        Math.min(activeSqlIndex, sqls.length - 1)
	    );

	    sqls.forEach(function (item, index) {
			const tabGroup = document.createElement('div');
			tabGroup.className = 'request-sql-tab-group';
			tabGroup.setAttribute('role', 'presentation');

			const tab = document.createElement('button');

			tab.type = 'button';
			tab.id = 'sql-tab-' + index;
			tab.className = 'request-sql-tab';
			tab.textContent = 'SQL ' + (index + 1);
			tab.setAttribute('role', 'tab');
			tab.setAttribute('aria-controls', 'sql-panel-' + index);

			tab.addEventListener('click', function () {
			    activateSqlTab(index);
			});

			const remove = document.createElement('button');

			remove.type = 'button';
			remove.className = 'request-sql-tab-remove';
			remove.textContent = '×';
			remove.disabled = sqls.length <= 1;

			remove.setAttribute(
			    'aria-label',
			    'SQL ' + (index + 1) + ' 삭제'
			);

			remove.title = sqls.length <= 1
			    ? 'SQL은 최소 1개를 유지해야 합니다.'
			    : 'SQL ' + (index + 1) + ' 삭제';

			remove.addEventListener('click', function (event) {
			    event.stopPropagation();
			    removeSql(index);
			});

			tabGroup.append(tab, remove);
			tabs.appendChild(tabGroup);

	        const section = document.createElement('section');

	        section.id = 'sql-panel-' + index;
	        section.className = 'sql-item';
	        section.setAttribute('role', 'tabpanel');
	        section.setAttribute('aria-labelledby', tab.id);

	        // 사용자 입력값은 HTML 문자열에 넣지 않고 아래에서 value로 지정한다.
	        section.innerHTML = `
	            <label class="request-sql-field">
	                <span>SQL 제목 <span class="required">*</span></span>
	                <input type="text"
	                       class="sql-title-input"
						   aria-required="true"
	                       maxlength="200"
	                       placeholder="예: 판매 중인 상품 ID·상품명 조회">
	            </label>

	            <div class="request-sql-editor-grid">
	                <div class="request-ai-panel" hidden>
	                    <label class="request-sql-field">
	                        <span>자연어 요청</span>
	                        <textarea class="sql-prompt"
	                                  rows="5"
	                                  maxlength="3000"
	                                  placeholder="예: 현재 판매 중인 상품의 상품 ID와 상품명을 조회해 주세요."></textarea>
	                    </label>

	                    <div class="request-ai-generate-actions">
	                        <button type="button"
	                                class="button primary sql-generate">
	                            SQL 초안 생성 →
	                        </button>
	                    </div>

	                    <p class="request-ai-explanation"
	                       role="status"
	                       aria-live="polite"></p>
	                </div>

	                <label class="request-sql-field request-sql-output">
	                    <span class="request-sql-output-heading">
	                        <span class="request-sql-editor-label">
	                            SQL 원문
	                        </span>
	                        <span class="request-ai-status"></span>
	                    </span>

	                    <textarea class="sql-editor"
	                              rows="5"
	                              maxlength="20000"
	                              spellcheck="false"></textarea>
	                </label>
	            </div>
				
				<p class="request-sql-validation caption" hidden></p>
	        `;

	        const titleInput =
	            section.querySelector('.sql-title-input');
	        const promptInput =
	            section.querySelector('.sql-prompt');
	        const editor =
	            section.querySelector('.sql-editor');
	        const status =
	            section.querySelector('.request-ai-status');

	        titleInput.value = item.title;
	        promptInput.value = item.prompt || '';
	        editor.id = 'sql-' + index;
	        editor.value = item.sqlText;
	        editor.placeholder =
	            "SELECT product_id, product_name\nFROM sqlflow_demo.v_active_product_catalog;";

	        function updateStatus() {
	            status.textContent = editor.value.trim()
	                ? '검토 필요'
	                : '생성 대기';
	        }

	        updateStatus();
			
			const validationMessage = section.querySelector('.request-sql-validation');
			const tabLabel = 'SQL ' + (index + 1);

			function renderValidation() {
			    const result = sqls[index].validation;
			    const passed = result?.validationStatus === 'VALID';

			    tab.textContent = tabLabel + (result ? (passed ? ' ✓' : ' !') : '');

			    if (result) {
			        tab.setAttribute(
			            'aria-label',
			            tabLabel + (passed ? ' 검증 완료' : ' 검증 실패')
			        );
			    } else {
			        tab.removeAttribute('aria-label');
			    }

			    validationMessage.hidden = !result;
			    validationMessage.classList.toggle('is-invalid', Boolean(result) && !passed);
			    validationMessage.textContent = result
			        ? (passed ? '검증 완료' : '검증 실패') + ' · ' + result.message
			        : '';
			}

			renderValidation();

			editor.addEventListener('input', function () {
			    delete sqls[index].validation;
			    updateStatus();
			    renderValidation();
			});

	        section.querySelector('.request-ai-explanation').textContent =
	            item.explanation || '';

	        section.querySelector('.sql-generate')
	            .addEventListener('click', function () {
	                generateSql(index, this);
	        });

	        box.appendChild(section);
	    });

	    $('#sql-count').textContent = sqls.length + ' / 5';
	    $('#add-sql').disabled = sqls.length >= 5;

	    activateSqlTab(activeSqlIndex);
	    setMode(mode);
	}

	function removeSql(index) {
	    if (formBusy || sqls.length <= 1) {
	        return;
	    }

	    readSqls();

	    const current = sqls[index];

	    if (!current) return;

	    const hasContent =
	        current.title.trim()
	        || current.sqlText.trim()
	        || (current.prompt || '').trim();

	    if (hasContent && !window.confirm(
	            'SQL ' + (index + 1)
	            + '의 작성 내용을 삭제하시겠습니까?')) {
	        return;
	    }

	    sqls.splice(index, 1);

	    // 선택된 탭보다 앞의 SQL을 지우면 같은 항목이 계속 선택되도록 보정한다.
	    if (index < activeSqlIndex) {
	        activeSqlIndex--;
	    }

	    activeSqlIndex = Math.min(
	        activeSqlIndex,
	        sqls.length - 1
	    );

	    renderSqls();
	    activateSqlTab(activeSqlIndex, true);
	}

	function setMode(next) {
	    mode = next;
	    $('#request-form').dataset.mode = mode;

	    document.querySelectorAll('button[data-mode]')
	        .forEach(function (button) {
	            button.setAttribute(
	                'aria-pressed',
	                String(button.dataset.mode === mode)
	            );
	        });

	    document.querySelectorAll('#sql-items .sql-item')
	        .forEach(function (section) {
	            section.querySelector('.request-ai-panel').hidden =
	                mode !== 'ai';

	            section.querySelector('.request-sql-editor-label').textContent =
	                mode === 'ai' ? '생성된 SQL' : 'SQL 원문';
	        });
	}

	function readSqls() {
	    const sections = document.querySelectorAll(
	        '#sql-items .sql-item'
	    );

	    sqls = Array.from(sections).map(function (section, index) {
	        return {
	            ...sqls[index],
	            title: section.querySelector('.sql-title-input').value,
	            sqlText: section.querySelector('.sql-editor').value,
	            prompt: section.querySelector('.sql-prompt').value
	        };
	    });
	}

    async function runFormTask(button, task) {
        if (formBusy) {
            return;
        }

        formBusy = true;

        try {
            await busy(button, async function () {
                $('#request-fields').disabled = true;

                try {
                    await task();
                } finally {
                    $('#request-fields').disabled = false;
                }
            });
        } finally {
            formBusy = false;
        }
    }

    document.querySelectorAll('button[data-mode]')
        .forEach(function (button) {
            button.addEventListener('click', function () {
                setMode(button.dataset.mode);
            });
        });

	$('#add-sql').addEventListener('click', function () {
	    readSqls();

	    if (sqls.length >= 5) {
	        return;
	    }

	    sqls.push({ title: '', sqlText: '' });
	    activeSqlIndex = sqls.length - 1;

	    renderSqls();
	    activateSqlTab(activeSqlIndex, true);
	});

	$('#sql-tabs').addEventListener('keydown', function (event) {
	    const tab = event.target.closest('[role="tab"]');

	    if (!tab) return;

	    const tabs = Array.from(
	        this.querySelectorAll('[role="tab"]')
	    );
	    const index = tabs.indexOf(tab);

	    let nextIndex;

	    switch (event.key) {
	        case 'ArrowRight':
	            nextIndex = (index + 1) % tabs.length;
	            break;
	        case 'ArrowLeft':
	            nextIndex = (index - 1 + tabs.length) % tabs.length;
	            break;
	        case 'Home':
	            nextIndex = 0;
	            break;
	        case 'End':
	            nextIndex = tabs.length - 1;
	            break;
	        default:
	            return;
	    }

	    event.preventDefault();
	    activateSqlTab(nextIndex, true);
	});

	async function generateSql(index, button) {
	    await runFormTask(button, async function () {
	        readSqls();

	        const item = sqls[index];
	        const prompt = item.prompt.trim();

	        if (!prompt) {
	            throw Error('자연어 요청을 입력하세요.');
	        }

	        if (item.sqlText.trim() && !window.confirm(
	                'SQL ' + (index + 1)
	                + '의 기존 원문을 새로 생성한 SQL로 바꾸시겠습니까?')) {
	            return;
	        }

	        button.textContent = '생성 중…';

	        const data = await post('/sql/generate', { prompt });

	        if (!data || typeof data.sql !== 'string'
	                || !data.sql.trim()) {
	            throw Error('생성된 SQL이 없습니다.');
	        }

	        if (data.sql.length > 20000) {
	            throw Error('생성된 SQL이 입력 가능한 길이를 초과했습니다.');
	        }

	        // 요청 성공 후에만 기존 SQL을 교체한다.
	        item.sqlText = data.sql;
			delete item.validation;
	        item.explanation =
	            data.explanation
	            || '생성된 SQL을 검토·수정한 뒤 저장하세요.';

	        activeSqlIndex = index;
	        renderSqls();

	        notify(
	            'SQL ' + (index + 1)
	            + ' 초안이 생성되었습니다. 내용을 확인하세요.'
	        );
	    });
	}

    $('#request-form').addEventListener('submit', function (event) {
        event.preventDefault();

        // 같은 화면에서 이미 생성한 요청은 다시 생성하지 않음
        if (createdRequestId !== null) {
            location.href = BASE + '/requests/'
                + encodeURIComponent(createdRequestId);
            return;
        }

		if (formBusy) return;

		readSqls();

		const missingTitleIndex = sqls.findIndex(item =>
		    item.sqlText.trim() && !item.title.trim()
		);

		if (missingTitleIndex !== -1) {
		    activateSqlTab(missingTitleIndex);

		    const titleInput = document
		        .querySelectorAll('#sql-items .sql-title-input')
		        [missingTitleIndex];

		    titleInput.focus();

		    notify(
		        'SQL ' + (missingTitleIndex + 1)
		        + '의 제목을 입력하세요.',
		        true
		    );

		    return;
		}

        runFormTask($('#save-draft'), async function () {
            const title = $('#title').value.trim();
            const purpose = $('#purpose').value.trim();

            if (!title) {
                throw Error('요청 제목을 입력하세요.');
            }

            if (title.length > 200) {
                throw Error('요청 제목은 200자 이하여야 합니다.');
            }

            if (!purpose) {
                throw Error('조회 목적을 입력하세요.');
            }

            readSqls();

			// 빈 SQL은 제목과 함께 제외
			const savedItems = sqls.filter(
			    item => item.sqlText.trim().length > 0
			);

			const payload = {
			    title,
			    purpose,
			    detailContent: $('#detail-content').value.trim() || null,
			    isUrgent: $('#is-urgent').checked ? 'Y' : 'N',
			    desiredDate: $('#desired-date').value || null,
			    sqls: savedItems.map(item => item.sqlText),
			    sqlTitles: savedItems.map(item => item.title.trim())
			};

            const result = await post('/requests/drafts', payload);

            createdRequestId = result.requestId;

            location.href = BASE + '/requests/'
                + encodeURIComponent(createdRequestId);
        });
		
	});
		
	$('#validate-draft-sqls').addEventListener('click', function () {
	    runFormTask(this, async function () {
	        readSqls();

	        const items = sqls.filter(item => item.sqlText.trim());
	        if (items.length === 0) {
	            throw Error('검증할 SQL을 입력하세요.');
	        }

	        const results = await post('/requests/select-sql/validate', {
	            sqls: items.map(item => item.sqlText)
	        });

	        if (!Array.isArray(results) || results.length !== items.length) {
	            throw Error('SQL 검증 결과를 확인할 수 없습니다.');
	        }

	        items.forEach(function (item, index) {
	            item.validation = results[index];
	        });

	        const firstInvalid = sqls.findIndex(
	            item => item.validation?.validationStatus === 'INVALID'
	        );

	        if (firstInvalid !== -1) {
	            activeSqlIndex = firstInvalid;
	        }

	        renderSqls();

	        if (firstInvalid === -1) {
	            notify('SQL ' + items.length + '개의 검증이 완료되었습니다.');
	        } else {
	            notify('검증에 실패한 SQL이 있습니다. 해당 탭에서 사유를 확인하세요.', true);
	        }
	    });
	});

    renderSqls();
    setMode(mode);
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
