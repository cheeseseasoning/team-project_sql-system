(() => {
    'use strict';

    const requestId =
        $('#department-detail-config').dataset.requestId;

    $('#department-back').href =
        BASE + '/department-requests' + location.search;

    async function loadHistory() {
        const panel = $('#approval-history-panel');
        const list = $('#approval-history-list');

        try {
            const history = await api(
                '/requests/department/'
                + encodeURIComponent(requestId)
                + '/approval-history',
                { cache: 'no-store' }
            );

            list.innerHTML =
                RequestDetailRenderer.renderHistory(history);

        } catch (error) {
            list.innerHTML =
                '<li class="empty">'
                + esc(error.message || '결재 이력을 불러오지 못했습니다.')
                + '</li>';
        }

        panel.hidden = false;
    }
	
	function addChangeSqlResultForms(detail) {
	    if (!detail.canManageChangeSqlResult) {
	        return;
	    }

	    const cards = $('#detail-items').querySelectorAll('.result-item');

	    (detail.items || []).forEach((item, index) => {
	        if (!cards[index]) {
	            return;
	        }

	        const hasResult = Boolean(item.changeResultStatus);
	        const form = document.createElement('form');
	        form.className = 'change-sql-result-form';
	        form.innerHTML = `
	            <label>처리 상태
	                <select name="resultStatus" required>
	                    <option value="COMPLETED">성공</option>
	                    <option value="FAILED">실패</option>
	                    <option value="CANCELLED">취소</option>
	                </select>
	            </label>
	            <label>처리 메모
	                <textarea name="resultNote" maxlength="1000"></textarea>
	            </label>
	            <label>외부 실행자 사용자 ID
	                <input name="executedBy" type="number" min="1" step="1">
	            </label>
	            ${hasResult ? `
	                <label>변경 유형
	                    <select name="changeType">
	                        <option value="CORRECTION">결과 정정</option>
	                        ${item.changeResultStatus === 'FAILED'
	                            ? '<option value="REEXECUTION">외부 재실행 결과 등록</option>'
	                            : ''}
	                    </select>
	                </label>
	                <label>변경 사유
	                    <textarea name="changeReason" maxlength="1000" required></textarea>
	                </label>
	            ` : ''}
	            <button class="button primary" type="submit">
	                ${hasResult ? '결과 변경 저장' : '결과 등록'}
	            </button>
	            <p class="caption" role="status"></p>
	        `;

	        const fields = form.elements;
	        fields.resultStatus.value = item.changeResultStatus || 'COMPLETED';
	        fields.resultNote.value = item.changeResultNote || '';
	        fields.executedBy.value = item.changeExecutedBy ?? '';

	        function updateRequiredFields() {
	            const status = fields.resultStatus.value;
	            fields.resultNote.required = status !== 'COMPLETED';
	            fields.executedBy.disabled = status === 'CANCELLED';
	            fields.executedBy.required = status !== 'CANCELLED';
	        }

	        fields.resultStatus.addEventListener('change', updateRequiredFields);
	        updateRequiredFields();
	        form.addEventListener('submit', event => {
	            event.preventDefault();
	            saveChangeSqlResult(form, item.queryId, hasResult);
	        });

			// 현재 결과는 카드에 표시하고, 입력 폼은 필요할 때 펼친다.
			const resultEditor = document.createElement('details');
			resultEditor.className = 'change-sql-result-editor';

			const summary = document.createElement('summary');
			summary.textContent = hasResult ? '결과 변경' : '결과 등록';

			resultEditor.append(summary, form);
			cards[index].append(resultEditor);
	    });
	}

	async function saveChangeSqlResult(form, queryId, hasResult) {
	    const fields = form.elements;
	    const message = form.querySelector('[role="status"]');
	    const button = form.querySelector('button[type="submit"]');
	    const resultStatus = fields.resultStatus.value;
	    const body = {
	        resultStatus,
	        resultNote: fields.resultNote.value.trim(),
	        executedBy: resultStatus === 'CANCELLED' ? null : Number(fields.executedBy.value)
	    };

	    if (hasResult) {
	        body.changeType = fields.changeType.value;
	        body.changeReason = fields.changeReason.value.trim();
	    }

	    if (body.changeType === 'REEXECUTION' && resultStatus === 'CANCELLED') {
	        message.textContent = '재실행 결과는 성공 또는 실패로 등록해 주세요.';
	        return;
	    }

	    button.disabled = true;
	    message.textContent = '저장 중…';

	    try {
	        // 저장이 끝나면 상세를 다시 조회해 현재 결과와 이력을 함께 갱신한다.
	        await api('/requests/department/' + encodeURIComponent(requestId)
	            + '/change-sql/' + encodeURIComponent(queryId) + '/result', {
	            method: hasResult ? 'PATCH' : 'POST',
	            body: JSON.stringify(body)
	        });
	        notify('외부 처리 결과를 저장했습니다.');
	        await loadDetail();
	    } catch (error) {
	        message.textContent = error.message || '처리 결과를 저장하지 못했습니다.';
	    } finally {
	        button.disabled = false;
	    }
	}

    async function loadDetail() {
        try {
            const detail = await api(
                '/requests/department/' + encodeURIComponent(requestId),
                { cache: 'no-store' }
            );

            RequestDetailRenderer.renderRequestInfo(detail);
            RequestDetailRenderer.renderSavedApprovalLine(detail);

			const downloadButton = $('#view-sql-download');
			const isChangeRequest = ['CREATE_VIEW', 'INSERT', 'UPDATE', 'DELETE']
			    .includes(detail.requestType);

			// 최종 승인되어 외부 처리 대기 중인 변경 요청에 다운로드를 표시한다.
			if (downloadButton) {
			    downloadButton.hidden = !(
			        isChangeRequest
			        && detail.requestStatus === 'READY'
			        && detail.approvalStatus === 'APPROVED'
			    );
			}

            $('#detail-items').innerHTML =
                RequestDetailRenderer.renderItems(
                    detail.items,
                    new Map(),
                    { readOnly: true }
                );
				
			addChangeSqlResultForms(detail);

            $('#request-info').hidden = false;
            $('#department-detail-content').hidden = false;

        } catch (error) {
            $('#detail-title').textContent = '기안을 열 수 없습니다.';
            $('#department-detail-content').hidden = true;
            notify(error.message, true);
            return;
        }

        await loadHistory();
    }

    const downloadButton = $('#view-sql-download');
    if (downloadButton) {
        downloadButton.addEventListener('click', async () => {
            const message = $('#view-sql-download-message');
            downloadButton.disabled = true;
            message.textContent = '다운로드 준비 중…';
            try {
                const response = await fetch(BASE + '/api/requests/'
                        + encodeURIComponent(requestId) + '/view-sql/download', {
                    headers: {
                        'Accept': 'application/sql',
                        [document.querySelector('meta[name="csrf-header"]')?.content || 'X-CSRF-TOKEN']:
                            document.querySelector('meta[name="csrf-token"]')?.content || ''
                    }
                });
                if (!response.ok) {
                    let error = {};
                    try { error = await response.json(); } catch (ignored) { }
                    throw new Error(error.message || 'SQL 다운로드에 실패했습니다.');
                }
                const url = URL.createObjectURL(await response.blob());
                const link = document.createElement('a');
                link.href = url;
                link.download = 'view-create-request-' + requestId + '.sql';
                document.body.append(link);
                link.click();
                link.remove();
                URL.revokeObjectURL(url);
                message.textContent = 'SQL 파일을 다운로드했습니다.';
            } catch (error) {
                message.textContent = error.message || 'SQL 다운로드에 실패했습니다.';
            } finally {
                downloadButton.disabled = false;
            }
        });
    }
	
    loadDetail();
})();
