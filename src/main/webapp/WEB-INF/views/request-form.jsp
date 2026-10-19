<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div id="form-config"
     data-mode="<c:out value='${mode}'/>"
     hidden>
</div>

<div class="page-heading">
    <h1>조회 요청 작성</h1>
    <span class="badge">초안 작성</span>
</div>

<div class="workflow">
    <strong>1 요청·SQL 작성</strong>
    <span>2 초안 저장</span>
    <span>3 결재선 확인·제출</span>
    <span>4 결재 및 실행</span>
</div>

<form id="request-form" class="panel form-panel">
    <fieldset id="request-fields"
              style="border: 0; padding: 0; margin: 0; min-width: 0;">
        <legend class="sr-only">조회 요청 작성</legend>

        <div class="form-grid">
            <label>
                요청 제목 <span class="required">*</span>
                <input id="title"
                       required
                       maxlength="200"
                       placeholder="요청 제목을 입력하세요.">
            </label>

            <label>
                조회 목적 <span class="required">*</span>
                <textarea id="purpose"
                          rows="3"
                          required
                          placeholder="데이터가 필요한 목적을 입력하세요."></textarea>
            </label>
        </div>

        <div class="tabs" role="group" aria-label="SQL 작성 방식">
            <button type="button" data-mode="direct">
                직접 SQL 작성
            </button>
            <button type="button" data-mode="ai">
                AI로 작성 · 시연
            </button>
        </div>

        <section id="ai-section" hidden>
            <label for="prompt">자연어 조회 요청</label>
            <textarea id="prompt"
                      rows="3"
                      maxlength="3000"
                      placeholder="조회하고 싶은 내용을 입력하세요."></textarea>

            <button id="generate"
                    type="button"
                    class="button">
                SQL 초안 생성
            </button>

            <p id="explanation"
               class="explanation"
               aria-live="polite">
                현재 AI 생성은 시연 기능입니다.
                생성된 SQL을 확인한 뒤 저장하세요.
            </p>
        </section>

        <div class="panel-heading section-gap">
            <h2>
                SQL 항목
                <span id="sql-count" class="muted"></span>
            </h2>

            <button id="add-sql"
                    type="button"
                    class="button">
                + SQL 추가
            </button>
        </div>

        <div id="sql-items"></div>

        <div class="form-footer">
            <span class="caption">
                빈 SQL 입력 칸은 제외됩니다.
                저장 후 상세 화면에서 제출할 수 있습니다.
            </span>

            <button id="save-draft"
                    type="submit"
                    class="button primary">
                초안 저장
            </button>
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
	
    let formBusy = false;
    let createdRequestId = null;

	function readSqls() {
	    sqls = Array.from(
	        document.querySelectorAll('#sql-items .sql-item')
	    ).map(section => ({
	        title: section.querySelector('.sql-title-input').value,
	        sqlText: section.querySelector('.sql-editor').value
	    }));
	}

    function setMode(next) {
        mode = next;
        $('#ai-section').hidden = mode !== 'ai';

        document.querySelectorAll('button[data-mode]')
            .forEach(function (button) {
                button.setAttribute(
                    'aria-pressed',
                    String(button.dataset.mode === mode)
                );
            });
    }

	function renderSqls() {
	    const box = $('#sql-items');
	    box.replaceChildren();

	    sqls.forEach(function (item, index) {
	        const section = document.createElement('section');
	        section.className = 'sql-item';

	        const head = document.createElement('div');
	        head.className = 'panel-heading';

	        const label = document.createElement('label');
	        label.htmlFor = 'sql-' + index;
	        label.textContent = 'SQL ' + (index + 1);

	        head.append(label);

	        if (sqls.length > 1) {
	            const remove = document.createElement('button');
	            remove.type = 'button';
	            remove.className = 'text-button';
	            remove.textContent = '삭제';

	            remove.addEventListener('click', function () {
	                readSqls();
	                sqls.splice(index, 1);
	                renderSqls();
	            });

	            head.append(remove);
	        }

	        const titleLabel = document.createElement('label');
	        titleLabel.textContent = 'SQL 제목';

	        const titleInput = document.createElement('input');
	        titleInput.type = 'text';
	        titleInput.className = 'sql-title-input';
	        titleInput.maxLength = 200;
	        titleInput.placeholder = '예: 부서별 매출 합계 · 생략 가능';
	        titleInput.value = item.title;

	        titleLabel.append(titleInput);

	        const editor = document.createElement('textarea');
	        editor.id = 'sql-' + index;
	        editor.className = 'sql-editor';
	        editor.rows = 8;
	        editor.spellcheck = false;
	        editor.maxLength = 20000;
	        editor.placeholder = '조회할 SQL을 입력하세요.';
	        editor.value = item.sqlText;

	        section.append(head, titleLabel, editor);
	        box.append(section);
	    });

	    $('#sql-count').textContent = sqls.length + ' / 5';
	    $('#add-sql').disabled = sqls.length >= 5;
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

        if (sqls.length < 5) {
            sqls.push({ title: '', sqlText: '' });
            renderSqls();
        }
    });

    $('#generate').addEventListener('click', function () {
        runFormTask(this, async function () {
            const prompt = $('#prompt').value.trim();

            if (!prompt) {
                throw Error('자연어 조회 요청을 입력하세요.');
            }

            const data = await post('/sql/generate', { prompt });

            if (!data || typeof data.sql !== 'string'
                    || !data.sql.trim()) {
                throw Error('생성된 SQL이 없습니다.');
            }

            readSqls();
            sqls[0].sqlText = data.sql;
            renderSqls();

            $('#explanation').textContent =
                data.explanation || '생성된 SQL을 확인하세요.';

            notify('SQL 초안이 생성되었습니다. 내용을 확인하세요.');
        });
    });

    $('#request-form').addEventListener('submit', function (event) {
        event.preventDefault();

        // 같은 화면에서 이미 생성한 요청은 다시 생성하지 않음
        if (createdRequestId !== null) {
            location.href = BASE + '/requests/'
                + encodeURIComponent(createdRequestId);
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
			    sqls: savedItems.map(item => item.sqlText),
			    sqlTitles: savedItems.map(item => item.title.trim())
			};

            const result = await post('/requests/drafts', payload);

            createdRequestId = result.requestId;

            location.href = BASE + '/requests/'
                + encodeURIComponent(createdRequestId);
        });
    });

    renderSqls();
    setMode(mode);
</script>

<%@ include file="fragments/bottom.jspf" %>