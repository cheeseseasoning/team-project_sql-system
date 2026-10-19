<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div id="edit-config"
     data-request-id="<c:out value='${requestId}'/>"
     hidden>
</div>

<div class="page-heading">
    <div>
        <span id="edit-request-number" class="caption"></span>
        <h1>조회 요청 초안 수정</h1>
    </div>
    <span class="badge">초안</span>
</div>

<p id="edit-loading">기존 내용을 불러오는 중입니다.</p>

<form id="edit-form" class="panel form-panel">
    <fieldset id="edit-fields" disabled>
        <legend>초안 수정</legend>

        <div class="form-grid">
            <label>
                요청 제목 <span class="required">*</span>
                <input id="edit-title" required maxlength="200">
            </label>

            <label>
                조회 목적 <span class="required">*</span>
                <textarea id="edit-purpose" rows="3" required></textarea>
            </label>
        </div>

        <div class="panel-heading section-gap">
            <h2>
                SQL 항목
                <span id="edit-sql-count" class="muted"></span>
            </h2>

            <button id="edit-add-sql" type="button" class="button">
                + SQL 추가
            </button>
        </div>

        <div id="edit-sql-items"></div>

        <div class="form-footer">
            <span class="caption">
                빈 SQL 입력 칸은 저장 목록에서 제외됩니다.
            </span>

            <button id="edit-save" type="submit" class="button primary">
                수정 저장
            </button>
        </div>
    </fieldset>
</form>

<a id="edit-cancel" class="back-link">← 상세로 돌아가기</a>

<script>
    const editRequestId = $('#edit-config').dataset.requestId;

    const detailUrl =
        BASE + '/requests/' + encodeURIComponent(editRequestId);

    $('#edit-cancel').href = detailUrl;

    let editSqls = [];

    function readEditSqls() {
        editSqls = Array.from(
            document.querySelectorAll('.edit-sql-editor')
        ).map(editor => editor.value);
    }

    function renderEditSqls() {
        const box = $('#edit-sql-items');
        box.replaceChildren();

        editSqls.forEach(function (sql, index) {
            const section = document.createElement('section');
            section.className = 'sql-item';

            const head = document.createElement('div');
            head.className = 'panel-heading';

            const label = document.createElement('label');
            label.htmlFor = 'edit-sql-' + index;
            label.textContent = 'SQL ' + (index + 1);

            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'text-button';
            remove.textContent = '삭제';

            remove.addEventListener('click', function () {
                readEditSqls();
                editSqls.splice(index, 1);
                renderEditSqls();
            });

            head.append(label, remove);

            const editor = document.createElement('textarea');
            editor.id = 'edit-sql-' + index;
            editor.className = 'edit-sql-editor';
            editor.rows = 8;
            editor.spellcheck = false;
            editor.value = sql;

            section.append(head, editor);
            box.append(section);
        });

        $('#edit-sql-count').textContent = editSqls.length + ' / 5';
        $('#edit-add-sql').disabled = editSqls.length >= 5;
    }

    $('#edit-add-sql').addEventListener('click', function () {
        readEditSqls();

        if (editSqls.length < 5) {
            editSqls.push('');
            renderEditSqls();
        }
    });

    async function loadEditDraft() {
        try {
            const request = await api(
                '/requests/mine/' + encodeURIComponent(editRequestId)
            );

            if (request.requestStatus !== 'DRAFT') {
                $('#edit-loading').textContent =
                    '초안 상태의 요청만 수정할 수 있습니다.';
                notify('초안 상태의 요청만 수정할 수 있습니다.', true);
                return;
            }

            $('#edit-request-number').textContent = request.requestNo;
            $('#edit-title').value = request.title;
            $('#edit-purpose').value = request.purpose;

            editSqls = request.items.map(item => item.sqlText);
            renderEditSqls();

            $('#edit-loading').hidden = true;
            $('#edit-fields').disabled = false;
        } catch (error) {
            $('#edit-loading').textContent =
                '요청을 불러오지 못했습니다. 상세 화면으로 돌아가 확인하세요.';
            notify(error.message, true);
        }
    }

    $('#edit-form').addEventListener('submit', function (event) {
        event.preventDefault();

        busy($('#edit-save'), async function () {
            const title = $('#edit-title').value.trim();
            const purpose = $('#edit-purpose').value.trim();

            if (!title) {
                throw Error('요청 제목을 입력하세요.');
            }

            if (!purpose) {
                throw Error('조회 목적을 입력하세요.');
            }

            readEditSqls();

            const payload = {
                title,
                purpose,
                sqls: editSqls.filter(sql => sql.trim().length > 0)
            };

            // 저장 중에는 입력과 SQL 추가·삭제를 막음
            $('#edit-fields').disabled = true;

            try {
                await api(
                    '/requests/drafts/'
                        + encodeURIComponent(editRequestId),
                    {
                        method: 'PUT',
                        body: JSON.stringify(payload)
                    }
                );

                location.href = detailUrl;
            } finally {
                $('#edit-fields').disabled = false;
            }
        });
    });

    loadEditDraft();
</script>

<%@ include file="fragments/bottom.jspf" %>