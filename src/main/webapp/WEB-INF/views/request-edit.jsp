<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

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

<form id="edit-form" class="panel form-panel">
    <fieldset id="edit-fields" disabled>
        <legend>요청 내용</legend>

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
		
		<label for="edit-detail-content">
		    상세 내용
		</label>
		<textarea id="edit-detail-content"
		          rows="5"
		          placeholder="요청 배경·조회 범위·주의사항 등을 입력하세요."></textarea>

		<div class="form-grid">
		    <label class="urgent-option">
		        <input id="edit-is-urgent" type="checkbox">
                <span><strong>긴급 요청</strong><small>빠른 처리가 필요한 경우 체크하세요.</small></span>
		    </label>

		    <label>
		        희망일
		        <input id="edit-desired-date" type="date">
		    </label>
		</div>

		<p class="caption">
		    긴급 여부와 희망일은 처리 담당자의 참고 정보입니다.
		</p>
		
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

		    <div>
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
		                    초안 저장
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
	let saveInProgress = false;

	$('#edit-cancel').addEventListener('click', function (event) {
	    if (saveInProgress) {
	        event.preventDefault();
	    }
	});

	function readEditSqls() {
	    editSqls = Array.from(
	        document.querySelectorAll('#edit-sql-items .sql-item')
	    ).map(section => ({
	        title: section.querySelector('.edit-sql-title-input').value,
	        sqlText: section.querySelector('.edit-sql-editor').value
	    }));
	}
	
	function renderEditSqls() {
	    const box = $('#edit-sql-items');
	    box.replaceChildren();

	    editSqls.forEach(function (item, index) {
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

	        const titleLabel = document.createElement('label');
	        titleLabel.textContent = 'SQL 제목';

	        const titleInput = document.createElement('input');
	        titleInput.type = 'text';
	        titleInput.className = 'edit-sql-title-input';
	        titleInput.maxLength = 200;
	        titleInput.placeholder = 'SQL 제목 · 생략 가능';
	        titleInput.value = item.title;

	        titleLabel.append(titleInput);

	        const editor = document.createElement('textarea');
	        editor.id = 'edit-sql-' + index;
	        editor.className = 'edit-sql-editor';
	        editor.rows = 8;
	        editor.spellcheck = false;
	        editor.value = item.sqlText;

	        section.append(head, titleLabel, editor);
	        box.append(section);
	    });

	    $('#edit-sql-count').textContent = editSqls.length + ' / 5';
	    $('#edit-add-sql').disabled = editSqls.length >= 5;
	}

    $('#edit-add-sql').addEventListener('click', function () {
        readEditSqls();

        if (editSqls.length < 5) {
            editSqls.push({ title: '', sqlText: '' });
            renderEditSqls();
        }
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

<%@ include file="fragments/bottom.jspf" %>
