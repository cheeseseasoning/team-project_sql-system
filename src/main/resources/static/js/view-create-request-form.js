(() => {
    'use strict';

    const MAX_SQL_COUNT = 5;

    const form = document.getElementById('view-create-request-form');
    const items = document.getElementById('view-sql-items');
    const template = document.getElementById('view-sql-item-template');
    const addButton = document.getElementById('view-add-sql');
    const count = document.getElementById('view-sql-count');
    const message = document.getElementById('view-form-message');
    const saveButton = document.getElementById('view-save-draft');
	
	const validateButton = document.getElementById('view-validate-sqls');

	let validationInProgress = false;
	let validationRevision = 0;

    const title = document.getElementById('view-request-title');
    const purpose = document.getElementById('view-request-purpose');
    const editConfig = document.getElementById('view-create-edit-config');
    const editMode = editConfig.dataset.editMode === 'true';
    const requestId = editConfig.dataset.requestId;

    let nextItemId = 0;
	
	/**
	 * SQL 수정·추가·삭제·순서 변경 시 이전 검사 결과를 지운다.
	 */
	function invalidateSqlValidation() {
	    validationRevision++;

	    items.querySelectorAll('.view-sql-validation')
	        .forEach(element => element.remove());

	    message.textContent = '';
	}

	async function validateInputSqls() {
	    if (validationInProgress) return;

	    const sections = getSections();
	    const editors = sections.map(
	        section => section.querySelector('.sql-editor')
	    );

	    // 제목·목적이 아니라 SQL 입력란만 검사한다.
	    for (const editor of editors) {
	        if (!editor.reportValidity()) return;
	    }

	    invalidateSqlValidation();

	    const revision = validationRevision;
	    const sqls = editors.map(editor => editor.value);

	    validationInProgress = true;
	    validateButton.disabled = true;
	    validateButton.textContent = '검사 중…';

	    try {
	        const results = await post(
	            '/requests/view-sql/validate',
	            { sqls: sqls }
	        );

	        // 응답을 기다리는 동안 SQL이 변경됐다면 결과를 적용하지 않는다.
	        if (revision !== validationRevision) {
	            message.textContent =
	                'SQL 구성이 변경됐습니다. 다시 검사해 주세요.';
	            return;
	        }

	        const names = {
	            VALID: '기본 검사 통과',
	            WARNING: '검사 불확실',
	            BLOCKED: '제출 차단'
	        };

	        if (!Array.isArray(results)
	                || results.length !== sections.length
	                || !results.every(result =>
	                    result
	                    && Object.prototype.hasOwnProperty.call(
	                        names, result.status
	                    )
	                )) {
	            throw new Error(
	                '전체 SQL의 검사 결과를 받지 못했습니다. 다시 검사해 주세요.'
	            );
	        }

	        results.forEach((result, index) => {
	            const element = document.createElement('p');

	            element.className =
	                'sql-validation-message view-sql-validation';

	            if (result.status === 'WARNING') {
	                element.classList.add('is-warning');
	            } else if (result.status === 'VALID') {
	                element.classList.add('is-valid');
	            }

	            element.setAttribute('role', 'status');

	            // 서버 메시지를 HTML로 해석하지 않고 텍스트로 표시한다.
	            element.textContent =
	                names[result.status] + ': ' + result.message;

	            sections[index].append(element);
	        });

	        const blockedCount = results.filter(
	            result => result.status === 'BLOCKED'
	        ).length;

	        const warningCount = results.filter(
	            result => result.status === 'WARNING'
	        ).length;

	        if (blockedCount > 0) {
	            message.textContent =
	                blockedCount + '개 SQL이 제출 차단 대상입니다. '
	                + '항목별 안내를 확인해 수정하세요.';
	        } else if (warningCount > 0) {
	            message.textContent =
	                warningCount + '개 SQL은 검사 불확실 상태입니다. '
	                + '제출은 가능하지만 DB팀 검토가 필요합니다.';
	        } else {
	            message.textContent =
	                '전체 SQL이 기본 파싱을 통과했습니다. '
	                + '실제 DB 실행 성공을 보장하지는 않습니다.';
	        }
	    } catch (error) {
	        if (revision === validationRevision) {
	            message.textContent =
	                error.message || 'SQL 검사 요청에 실패했습니다.';
	        }
	    } finally {
	        validationInProgress = false;
	        validateButton.disabled = false;
	        validateButton.textContent = 'SQL 기본 검사';
	    }
	}

	validateButton.addEventListener('click', validateInputSqls);

    function getSections() {
        return Array.from(items.querySelectorAll('.sql-item'));
    }

    function refreshOrder() {
		invalidateSqlValidation();
        const sections = getSections();

        sections.forEach((section, index) => {
            const order = index + 1;

            section.dataset.queryOrder = String(order);
            section.querySelector('.view-sql-heading').textContent =
                'SQL ' + order;

            const up = section.querySelector('[data-action="up"]');
            const down = section.querySelector('[data-action="down"]');
            const remove = section.querySelector('[data-action="remove"]');

            up.disabled = index === 0;
            down.disabled = index === sections.length - 1;
            remove.disabled = sections.length === 1;

            up.setAttribute('aria-label', 'SQL ' + order + ' 위로 이동');
            down.setAttribute('aria-label', 'SQL ' + order + ' 아래로 이동');
            remove.setAttribute('aria-label', 'SQL ' + order + ' 삭제');
        });

        count.textContent = sections.length + ' / ' + MAX_SQL_COUNT;
        addButton.disabled = sections.length >= MAX_SQL_COUNT;
    }

    function addSql(focusEditor, values = {}) {
        if (getSections().length >= MAX_SQL_COUNT) {
            return;
        }

        const fragment = template.content.cloneNode(true);
        const section = fragment.querySelector('.sql-item');
        const itemId = ++nextItemId;

        const sqlTitle = section.querySelector('.view-sql-title');
        const editor = section.querySelector('.sql-editor');
        sqlTitle.value = values.queryTitle || '';
        editor.value = values.sqlText || '';

        sqlTitle.name = 'sqlTitle-' + itemId;
        editor.name = 'sqlText-' + itemId;

        items.append(fragment);
        refreshOrder();

        if (focusEditor) {
            editor.focus();
        }
    }

    function readRequest() {
        return {
            title: title.value.trim(),
            purpose: purpose.value.trim(),
            detailContent:
                document.getElementById('view-request-detail').value.trim(),
            isUrgent:
                document.getElementById('view-request-urgent').checked
                    ? 'Y'
                    : 'N',
            desiredDate:
                document.getElementById('view-request-date').value || null,
            items: getSections().map((section, index) => ({
                queryOrder: index + 1,
                queryTitle:
                    section.querySelector('.view-sql-title').value.trim(),
                sqlText: section.querySelector('.sql-editor').value
            }))
        };
    }

    function validateRequiredFields() {
        const requiredFields = [
            title,
            purpose,
            ...items.querySelectorAll('.sql-editor')
        ];

        requiredFields.forEach(field => {
            field.setCustomValidity(
                field.value.trim() ? '' : '공백을 제외한 내용을 입력하세요.'
            );
        });

        return form.reportValidity();
    }

    addButton.addEventListener('click', () => {
        message.textContent = '';
        addSql(true);
    });

    items.addEventListener('click', event => {
        const button = event.target.closest('button[data-action]');

        if (!button || !items.contains(button) || button.disabled) {
            return;
        }

        const section = button.closest('.sql-item');
        const action = button.dataset.action;
        let focusTarget;

        if (action === 'up') {
            const previous = section.previousElementSibling;
            if (!previous) return;

            items.insertBefore(section, previous);
            focusTarget = section;
        } else if (action === 'down') {
            const next = section.nextElementSibling;
            if (!next) return;

            items.insertBefore(next, section);
            focusTarget = section;
        } else if (action === 'remove') {
            if (getSections().length <= 1) return;

            focusTarget =
                section.nextElementSibling || section.previousElementSibling;
            section.remove();
        } else {
            return;
        }

        refreshOrder();
        focusTarget.querySelector('.sql-editor').focus();

        message.textContent = action === 'remove'
            ? 'SQL 항목을 삭제했습니다.'
            : 'SQL 처리 순서를 변경했습니다.';
    });

	form.addEventListener('input', event => {
	    if (typeof event.target.setCustomValidity === 'function') {
	        event.target.setCustomValidity('');
	    }

	    if (event.target.matches('.sql-editor')) {
	        invalidateSqlValidation();
	    } else {
	        message.textContent = '';
	    }
	});

	form.addEventListener('submit', event => {
	    event.preventDefault();
	});

    saveButton.addEventListener('click', async () => {
        message.textContent = '';
        if (!validateRequiredFields()) return;
        const request = readRequest();
        saveButton.disabled = true;
        saveButton.textContent = '저장 중…';
        try {
            if (editMode) {
                await api('/requests/view-create-drafts/'
                    + encodeURIComponent(requestId), {
                    method: 'PUT',
                    body: JSON.stringify(request)
                });
                location.href = BASE + '/requests/' + encodeURIComponent(requestId);
            } else {
                const result = await post('/requests/view-create-drafts', request);
                location.href = BASE + '/requests/' + encodeURIComponent(result.requestId);
            }
        } catch (error) {
            message.textContent = error.message || '초안을 저장하지 못했습니다.';
            saveButton.disabled = false;
            saveButton.textContent = editMode ? '수정 저장' : '초안 저장';
        }
    });

    addSql(false);

    async function loadViewDraft() {
        if (!editMode) return;
        form.querySelectorAll('input, textarea, button').forEach(element => {
            element.disabled = true;
        });
        saveButton.textContent = '초안 불러오는 중…';
        try {
            const request = await api(
                '/requests/mine/' + encodeURIComponent(requestId)
            );
            if (request.requestStatus !== 'DRAFT'
                    || request.requestType !== 'CREATE_VIEW') {
                throw new Error('수정할 수 있는 뷰 생성 초안이 아닙니다.');
            }
            title.value = request.title || '';
            purpose.value = request.purpose || '';
            document.getElementById('view-request-detail').value =
                request.detailContent || '';
            document.getElementById('view-request-urgent').checked =
                request.isUrgent === 'Y';
            document.getElementById('view-request-date').value =
                request.desiredDate || '';
            items.replaceChildren();
            nextItemId = 0;
            (request.items || []).forEach(item => addSql(false, {
                queryTitle: item.queryTitle,
                sqlText: item.sqlText
            }));
            if (getSections().length === 0) addSql(false);
            form.querySelectorAll('input, textarea, button').forEach(element => {
                element.disabled = false;
            });
            refreshOrder();
            saveButton.textContent = '수정 저장';
        } catch (error) {
            message.textContent = error.message || '뷰 생성 초안을 불러오지 못했습니다.';
            saveButton.textContent = '수정 저장';
            saveButton.disabled = true;
        }
    }

    loadViewDraft();
})();
