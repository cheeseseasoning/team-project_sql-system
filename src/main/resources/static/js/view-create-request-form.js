(() => {
    'use strict';

    const MAX_SQL_COUNT = 5;
    const TYPE = {
        CREATE_VIEW: {
            path: '/requests/view-sql/validate',
            guide: '신규 뷰를 만드는 CREATE VIEW 문을 요청합니다.',
            sqlGuide: '항목마다 CREATE VIEW 문 한 개를 작성하세요. 위에서 아래 순서로 처리됩니다.',
            placeholder: 'CREATE VIEW v_product AS\nSELECT product_id FROM demo_product;',
            saveGuide: '초안 저장 후 내 기안함에서 결재선을 지정하고 제출할 수 있습니다.'
        },
        DATA_CHANGE: {
            path: '/requests/dml-sql/validate',
            guide: 'INSERT, UPDATE, DELETE 문을 검사할 수 있습니다.',
            sqlGuide: '항목마다 변경문 한 개를 작성하세요. 구문 검사 결과는 각 입력란 아래에 표시됩니다.',
            placeholder: "UPDATE demo_product\nSET product_name = '변경할 이름'\nWHERE product_id = 1;",
            saveGuide: '데이터 변경은 현재 구문 검사까지 사용할 수 있습니다.'
        }
    };

    const RESULT_LABEL = {
        VALID: '기본 검사 통과',
        WARNING: '검토 필요',
        BLOCKED: '검사 차단'
    };

    const config = document.getElementById('view-create-edit-config');
    const editMode = config.dataset.editMode === 'true';
    const requestId = config.dataset.requestId;

    const form = document.getElementById('view-create-request-form');
    const changeType = document.getElementById('db-change-type');
    const typeGuide = document.getElementById('db-change-guide');
    const sqlGuide = document.getElementById('view-sql-guide');
    const saveGuide = document.getElementById('view-save-guide');
    const items = document.getElementById('view-sql-items');
    const template = document.getElementById('view-sql-item-template');
    const count = document.getElementById('view-sql-count');
    const message = document.getElementById('view-form-message');

    const title = document.getElementById('view-request-title');
    const purpose = document.getElementById('view-request-purpose');
    const detail = document.getElementById('view-request-detail');
    const urgent = document.getElementById('view-request-urgent');
    const desiredDate = document.getElementById('view-request-date');

    const addButton = document.getElementById('view-add-sql');
    const validateButton = document.getElementById('view-validate-sqls');
    const saveButton = document.getElementById('view-save-draft');
    const saveLabel = editMode ? '수정 저장' : '초안 저장';

    let nextItemId = 0;
    let validationRevision = 0;
    let validationInProgress = false;
    let saving = false;

    function getSections() {
        return Array.from(items.querySelectorAll('.sql-item'));
    }

    function updateTypeDisplay() {
        const type = TYPE[changeType.value];

        typeGuide.textContent = type.guide;
        sqlGuide.textContent = type.sqlGuide;
        saveGuide.textContent = type.saveGuide;

        items.querySelectorAll('.sql-editor').forEach(editor => {
            editor.placeholder = type.placeholder;
        });

        saveButton.disabled = saving || changeType.value === 'DATA_CHANGE';
    }

    // 입력, 유형, 순서가 바뀌면 이전 응답이 새 입력에 표시되지 않도록 한다.
    function invalidateValidation() {
        validationRevision++;
        items.querySelectorAll('.view-sql-validation').forEach(element => element.remove());
        message.textContent = '';
    }

    function refreshOrder() {
        invalidateValidation();

        const sections = getSections();

        sections.forEach((section, index) => {
            const label = 'SQL ' + (index + 1);
            const up = section.querySelector('[data-action="up"]');
            const down = section.querySelector('[data-action="down"]');
            const remove = section.querySelector('[data-action="remove"]');

            section.querySelector('.view-sql-heading').textContent = label;

            up.disabled = index === 0;
            down.disabled = index === sections.length - 1;
            remove.disabled = sections.length === 1;

            up.setAttribute('aria-label', label + ' 위로 이동');
            down.setAttribute('aria-label', label + ' 아래로 이동');
            remove.setAttribute('aria-label', label + ' 삭제');
        });

        count.textContent = sections.length + ' / ' + MAX_SQL_COUNT;
        addButton.disabled = sections.length >= MAX_SQL_COUNT;
    }

    function addSql(focusEditor, values = {}) {
        if (getSections().length >= MAX_SQL_COUNT) return;

        const fragment = template.content.cloneNode(true);
        const sqlTitle = fragment.querySelector('.view-sql-title');
        const editor = fragment.querySelector('.sql-editor');
        const itemId = ++nextItemId;

        sqlTitle.name = 'sqlTitle-' + itemId;
        sqlTitle.value = values.queryTitle || '';
        editor.name = 'sqlText-' + itemId;
        editor.value = values.sqlText || '';
        editor.placeholder = TYPE[changeType.value].placeholder;

        items.append(fragment);
        refreshOrder();

        if (focusEditor) editor.focus();
    }

    function validateFields(fields) {
        for (const field of fields) {
            field.setCustomValidity(field.value.trim() ? '' : '내용을 입력하세요.');
            if (!field.reportValidity()) return false;
        }

        return true;
    }

    function showValidationResults(sections, results) {
        const validResponse = Array.isArray(results)
            && results.length === sections.length
            && results.every(result => result && Object.hasOwn(RESULT_LABEL, result.status));

        if (!validResponse) {
            throw new Error('전체 SQL의 검사 결과를 받지 못했습니다. 다시 검사해 주세요.');
        }

        results.forEach((result, index) => {
            const element = document.createElement('p');
            element.className = 'sql-validation-message view-sql-validation';

            if (result.status === 'VALID') element.classList.add('is-valid');
            if (result.status === 'WARNING') element.classList.add('is-warning');

            element.textContent = RESULT_LABEL[result.status] + ' · ' + result.message;
            sections[index].append(element);
        });

        const blocked = results.filter(result => result.status === 'BLOCKED').length;
        const warnings = results.filter(result => result.status === 'WARNING').length;
        const passed = results.length - blocked - warnings;

        message.textContent = `통과 ${passed}건 · 검토 필요 ${warnings}건 · 차단 ${blocked}건`;
    }

    async function validateSqls() {
        if (validationInProgress || saving) return;

        const sections = getSections();
        const editors = sections.map(section => section.querySelector('.sql-editor'));

        if (!validateFields(editors)) return;

        invalidateValidation();
        const revision = validationRevision;
        const sqls = editors.map(editor => editor.value);

        validationInProgress = true;
        validateButton.disabled = true;
        validateButton.textContent = '검사 중…';

        try {
            const results = await post(TYPE[changeType.value].path, { sqls });

            if (revision === validationRevision) {
                showValidationResults(sections, results);
            }
        } catch (error) {
            if (revision === validationRevision) {
                message.textContent = error.message || 'SQL 검사 요청에 실패했습니다.';
            }
        } finally {
            validationInProgress = false;
            validateButton.disabled = false;
            validateButton.textContent = '구문 검사';
        }
    }

    function readRequest() {
        return {
            title: title.value.trim(),
            purpose: purpose.value.trim(),
            detailContent: detail.value.trim(),
            isUrgent: urgent.checked ? 'Y' : 'N',
            desiredDate: desiredDate.value || null,
            items: getSections().map((section, index) => ({
                queryOrder: index + 1,
                queryTitle: section.querySelector('.view-sql-title').value.trim(),
                sqlText: section.querySelector('.sql-editor').value
            }))
        };
    }

    async function saveDraft() {
        if (saving || changeType.value !== 'CREATE_VIEW') return;

        message.textContent = '';
        if (!validateFields([title, purpose, ...items.querySelectorAll('.sql-editor')])) return;

        const request = readRequest();
        saving = true;
        changeType.disabled = true;
        saveButton.disabled = true;
        saveButton.textContent = '저장 중…';

        try {
            let savedId = requestId;

            if (editMode) {
                await api('/requests/view-create-drafts/' + encodeURIComponent(requestId), {
                    method: 'PUT',
                    body: JSON.stringify(request)
                });
            } else {
                const result = await post('/requests/view-create-drafts', request);
                savedId = result.requestId;
            }

            location.href = BASE + '/requests/' + encodeURIComponent(savedId);
        } catch (error) {
            message.textContent = error.message || '초안을 저장하지 못했습니다.';
            saving = false;
            changeType.disabled = editMode;
            saveButton.textContent = saveLabel;
            updateTypeDisplay();
        }
    }

    async function loadDraft() {
        const controls = form.querySelectorAll('input, textarea, button');
        controls.forEach(control => { control.disabled = true; });
        saveButton.textContent = '초안 불러오는 중…';

        try {
            const request = await api('/requests/mine/' + encodeURIComponent(requestId));

            if (request.requestStatus !== 'DRAFT' || request.requestType !== 'CREATE_VIEW') {
                throw new Error('수정할 수 있는 뷰 생성 초안이 아닙니다.');
            }

            title.value = request.title || '';
            purpose.value = request.purpose || '';
            detail.value = request.detailContent || '';
            urgent.checked = request.isUrgent === 'Y';
            desiredDate.value = request.desiredDate || '';

            controls.forEach(control => { control.disabled = false; });
            items.replaceChildren();

            (request.items || []).forEach(item => addSql(false, item));
            if (getSections().length === 0) addSql(false);

            updateTypeDisplay();
        } catch (error) {
            message.textContent = error.message || '뷰 생성 초안을 불러오지 못했습니다.';
        } finally {
            saveButton.textContent = saveLabel;
        }
    }

    items.addEventListener('click', event => {
        const button = event.target.closest('button[data-action]');
        if (!button || !items.contains(button) || button.disabled) return;

        const section = button.closest('.sql-item');
        const previous = section.previousElementSibling;
        const next = section.nextElementSibling;
        let focusSection = section;

        switch (button.dataset.action) {
            case 'up':
                if (!previous) return;
                items.insertBefore(section, previous);
                break;
            case 'down':
                if (!next) return;
                items.insertBefore(next, section);
                break;
            case 'remove':
                focusSection = next || previous;
                if (!focusSection) return;
                section.remove();
                break;
            default:
                return;
        }

        refreshOrder();
        focusSection.querySelector('.sql-editor').focus();
    });

    form.addEventListener('input', event => {
        if (typeof event.target.setCustomValidity === 'function') {
            event.target.setCustomValidity('');
        }

        if (event.target.matches('.sql-editor')) invalidateValidation();
    });

    form.addEventListener('submit', event => event.preventDefault());

    changeType.addEventListener('change', () => {
        invalidateValidation();
        updateTypeDisplay();
    });

    addButton.addEventListener('click', () => addSql(true));
    validateButton.addEventListener('click', validateSqls);
    saveButton.addEventListener('click', saveDraft);

    addSql(false);

    if (editMode) {
        loadDraft();
    } else {
        updateTypeDisplay();
    }
})();