(() => {
    'use strict';

    const MAX_SQL_COUNT = 5;

    const form = document.getElementById('view-create-request-form');
    const items = document.getElementById('view-sql-items');
    const template = document.getElementById('view-sql-item-template');
    const addButton = document.getElementById('view-add-sql');
    const count = document.getElementById('view-sql-count');
    const message = document.getElementById('view-form-message');

    const title = document.getElementById('view-request-title');
    const purpose = document.getElementById('view-request-purpose');

    let nextItemId = 0;

    function getSections() {
        return Array.from(items.querySelectorAll('.sql-item'));
    }

    function refreshOrder() {
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

    function addSql(focusEditor) {
        if (getSections().length >= MAX_SQL_COUNT) {
            return;
        }

        const fragment = template.content.cloneNode(true);
        const section = fragment.querySelector('.sql-item');
        const itemId = ++nextItemId;

        const sqlTitle = section.querySelector('.view-sql-title');
        const editor = section.querySelector('.sql-editor');

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

        message.textContent = '';
    });

    form.addEventListener('change', () => {
        message.textContent = '';
    });

    form.addEventListener('submit', event => {
        event.preventDefault();

        if (!validateRequiredFields()) {
            return;
        }

        const request = readRequest();

        message.textContent =
            '필수 입력값을 확인했습니다. SQL '
            + request.items.length
            + '개가 화면 순서대로 구성되어 있습니다. '
            + 'SQL 문법 검증과 서버 저장은 아직 수행하지 않았습니다.';
    });

    addSql(false);
})();