(() => {
    'use strict';

    const requestId =
        $('#department-detail-config').dataset.requestId;

    $('#department-back').href =
        BASE + '/department-requests' + location.search;

	function addChangeSqlResultForms(detail) {
	    if (!detail.canManageChangeSqlResult) {
	        return;
	    }

	    const cards = $('#detail-items').querySelectorAll('.result-item');
	    let viewOptions;
	    let executorOptions;

	    (detail.items || []).forEach((item, index) => {
	        if (!cards[index]) {
	            return;
	        }

	        const hasResult = Boolean(item.changeResultStatus);
	        const canLinkView = detail.requestType === 'CREATE_VIEW' && !hasResult;
	        const form = document.createElement('form');
	        form.className = 'change-sql-result-form';
	        form.innerHTML = `
                <h4>${hasResult ? '외부 실행 결과 변경' : '외부 실행 결과 등록'}</h4>
                <div class="change-result-fields">
	            <label>처리 상태 <span class="required">*</span>
	                <select name="resultStatus" required>
	                    <option value="COMPLETED">성공</option>
	                    <option value="FAILED">실패</option>
	                    <option value="CANCELLED">취소</option>
	                </select>
	            </label>
	            ${canLinkView ? `
	                <label class="change-result-full" hidden>생성된 뷰 <span class="required">*</span>
	                    <select name="resourceId">
	                        <option value="">등록된 뷰를 선택하세요</option>
	                    </select>
	                    <small class="caption" data-view-options-status></small>
	                </label>
	            ` : ''}
	            <div class="change-executor-picker">
	                <label>외부 실행자 <span class="required" data-executor-required>*</span>
	                    <input name="executorSearch" type="search"
	                           placeholder="이름 또는 부서로 검색" autocomplete="off">
	                </label>
	                <input name="executedBy" type="hidden">
	                <div class="change-executor-options" aria-label="직원 검색 결과" role="group" hidden></div>
	            </div>
                <label class="change-result-full change-result-note-field">처리 메모 <span class="required" data-note-required hidden>*</span>
                    <textarea name="resultNote" maxlength="1000" rows="2"
                              placeholder="처리 내용이나 확인 사항을 입력하세요."></textarea>
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
	                <label class="change-result-full">변경 사유 <span class="required">*</span>
	                    <textarea name="changeReason" maxlength="1000" rows="2"
                                  placeholder="기존 결과를 변경하는 이유를 입력하세요." required></textarea>
	                </label>
	            ` : ''}
                </div>
                <div class="change-result-form-footer">
                <p class="caption" role="status"></p>
                <button class="button" type="button" data-result-close>닫기</button>
	            <button class="button primary" type="submit">
	                ${hasResult ? '결과 변경 저장' : '결과 등록'}
	            </button>
                </div>
	        `;

	        const fields = form.elements;
	        fields.resultStatus.value = item.changeResultStatus || 'COMPLETED';
	        fields.resultNote.value = item.changeResultNote || '';
	        fields.executedBy.value = item.changeExecutedBy ?? '';
	        const updateExecutorPicker = bindExecutorPicker(form, item.changeExecutedBy, () => {
	            executorOptions ??= loadExecutorOptions().catch(error => {
	                executorOptions = null;
	                throw error;
	            });
	            return executorOptions;
	        });

	        function updateRequiredFields() {
	            const status = fields.resultStatus.value;
	            fields.resultNote.required = status !== 'COMPLETED';
                form.querySelector('[data-note-required]').hidden = status === 'COMPLETED';
                form.querySelector('[data-executor-required]').hidden = status === 'CANCELLED';
	            updateExecutorPicker(status === 'CANCELLED');
	            if (canLinkView) {
	                const showViewPicker = status === 'COMPLETED';
	                fields.resourceId.closest('label').hidden = !showViewPicker;
	                fields.resourceId.disabled = !showViewPicker;
	                fields.resourceId.required = showViewPicker;
	            }
	        }

	        fields.resultStatus.addEventListener('change', updateRequiredFields);
	        updateRequiredFields();
	        form.addEventListener('submit', event => {
	            event.preventDefault();
	            saveChangeSqlResult(form, item.queryId, hasResult);
	        });

            // 요약은 항상 표시하고, 기존 입력·저장 로직은 필요할 때만 펼친다.
			const resultEditor = document.createElement('div');
			resultEditor.className = 'change-sql-result-editor';
            resultEditor.id = 'change-result-editor-' + item.queryId;
            resultEditor.hidden = true;
            resultEditor.open = false;
            resultEditor.append(form);

            let resultSummary = cards[index].querySelector('.change-result-summary');
            if (!resultSummary) {
                resultSummary = document.createElement('section');
                resultSummary.className = 'change-result-summary';
                resultSummary.setAttribute('aria-label', '외부 실행 결과');
                resultSummary.innerHTML = '<div class="change-result-heading"><h4>외부 실행 결과</h4>'
                    + '<span class="change-result-muted">미등록</span><span class="change-result-actions"></span></div>';
                (cards[index].querySelector('.request-sql-body') || cards[index]).append(resultSummary);
            }
            const toggle = document.createElement('button');
            toggle.type = 'button';
            toggle.className = 'button change-result-toggle';
            toggle.setAttribute('aria-controls', resultEditor.id);
            function setEditorOpen(open) {
                resultEditor.open = open;
                resultEditor.hidden = !open;
                toggle.textContent = open ? '입력 닫기' : hasResult ? '결과 변경' : '결과 등록';
                toggle.setAttribute('aria-expanded', String(open));
                resultEditor.dispatchEvent(new Event('toggle'));
            }
            toggle.addEventListener('click', () => setEditorOpen(!resultEditor.open));
            form.querySelector('[data-result-close]').addEventListener('click', () => {
                setEditorOpen(false);
                toggle.focus();
            });
            resultSummary.querySelector('.change-result-actions').append(toggle);
            setEditorOpen(false);
			if (item.changeExecutedBy != null) {
			    resultEditor.addEventListener('toggle', () => {
			        if (resultEditor.open && fields.executedBy.value) {
			            fields.executorSearch.dispatchEvent(new Event('focus'));
			        }
			    });
			}
			if (canLinkView) {
			    resultEditor.addEventListener('toggle', async () => {
			        if (!resultEditor.open || fields.resourceId.dataset.loaded) return;
			        const status = form.querySelector('[data-view-options-status]');
			        status.textContent = '등록된 뷰를 불러오는 중입니다.';
			        try {
			            viewOptions ??= loadRegisteredViews();
			            const views = await viewOptions;
			            fields.resourceId.replaceChildren(
			                new Option('등록된 뷰를 선택하세요', ''),
			                ...views.map(view => new Option(
			                    view.schemaName + '.' + view.objectName, String(view.resourceId)))
			            );
			            fields.resourceId.dataset.loaded = 'true';
			            status.textContent = views.length ? '' : '리소스 관리에서 생성된 뷰를 먼저 등록해 주세요.';
			        } catch (error) {
			            viewOptions = null;
			            status.textContent = '뷰 목록을 불러오지 못했습니다. 폼을 다시 열어 주세요.';
			        }
			    });
			}
            resultSummary.after(resultEditor);
	    });
	}

	function bindExecutorPicker(form, initialId, getOptions) {
	    const search = form.elements.executorSearch;
	    const selected = form.elements.executedBy;
	    const list = form.querySelector('.change-executor-options');
	    let searchVersion = 0;
	    let initialLabelLoaded = false;

	    function showMessage(message) {
	        const text = document.createElement('p');
	        text.textContent = message;
	        list.replaceChildren(text);
	    }

	    function closeList() {
	        searchVersion++;
	        list.hidden = true;
	    }

	    function validateSelection() {
	        search.required = !search.disabled && !selected.value;
	        search.setCustomValidity(search.disabled || selected.value || !search.value.trim()
	            ? '' : '검색 결과에서 외부 실행자를 선택해 주세요.');
	    }

	    search.addEventListener('focus', async () => {
	        if (!initialId || initialLabelLoaded || selected.value !== String(initialId)) return;
	        try {
	            const employee = (await getOptions()).find(item => String(item.userId) === String(initialId));
	            if (employee && selected.value === String(initialId)) {
	                search.value = employee.userName + ' · ' + employee.loginId;
	                initialLabelLoaded = true;
	            }
	        } catch {
	            search.placeholder = '직원 목록을 불러오지 못했습니다. 다시 검색해 주세요.';
	        }
	    });

	    search.addEventListener('input', async () => {
	        selected.value = '';
	        validateSelection();
	        const keyword = search.value.trim().toLowerCase();
	        closeList();
	        if (!keyword) return;

	        const version = searchVersion;
	        list.hidden = false;
	        showMessage('직원 목록을 불러오는 중입니다.');
	        try {
	            const employees = await getOptions();
	            if (version !== searchVersion) return;
	            const matches = employees.filter(employee =>
	                [employee.userName, employee.deptName].some(value => value?.toLowerCase().includes(keyword)));
	            if (!matches.length) {
	                showMessage('검색 결과가 없습니다.');
	                return;
	            }
	            list.replaceChildren(...matches.map(employee => {
	                const option = document.createElement('button');
	                option.type = 'button';
	                const detail = document.createElement('small');
	                detail.textContent = [employee.deptName, employee.positionName, employee.loginId].filter(Boolean).join(' · ');
	                option.append(employee.userName, detail);
	                option.addEventListener('click', () => {
	                    selected.value = employee.userId;
	                    search.value = employee.userName + ' · ' + employee.loginId;
	                    validateSelection();
	                    closeList();
	                    search.focus();
	                });
	                return option;
	            }));
	            list.scrollTop = 0;
	        } catch {
	            if (version === searchVersion) showMessage('직원 목록을 불러오지 못했습니다. 다시 검색해 주세요.');
	        }
	    });

	    form.querySelector('.change-executor-picker').addEventListener('keydown', event => {
	        if (event.key === 'Escape' && !list.hidden) {
	            event.preventDefault();
	            closeList();
	            search.focus();
	        }
	    });

	    return cancelled => {
	        search.disabled = cancelled;
	        validateSelection();
	        if (cancelled) closeList();
	    };
	}

	async function loadExecutorOptions() {
	    const employees = [];
	    for (let page = 1; ; page++) {
	        const response = await api('/accounts?page=' + page + '&size=100');
	        employees.push(...response.items.filter(employee => employee.deptName === 'DB팀'));
	        if (page >= response.totalPages) return employees;
	    }
	}

	async function loadRegisteredViews() {
	    const registered = [];
	    let page = 1;
	    let totalPages;
	    do {
	        const result = await api('/views?page=' + page + '&size=50');
	        registered.push(...result.items.filter(view => view.registered && view.resourceId != null));
	        totalPages = result.totalPages;
	        page++;
	    } while (page <= totalPages);
	    return registered;
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
	    if (fields.resourceId && !fields.resourceId.disabled) {
	        body.resourceId = fields.resourceId.value;
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

            $('#detail-items').innerHTML =
                RequestDetailRenderer.renderItems(
                    detail.items,
                    new Map(),
                    { readOnly: true }
                );

			addChangeSqlResultForms(detail);

            $('#request-info').hidden = false;
            $('#department-detail-content').hidden = false;

			await RequestDetailRenderer.loadRejectionReason(
			    detail.requestStatus,
			    '/requests/department/'
			        + encodeURIComponent(requestId)
			        + '/approval-history'
			);

        } catch (error) {
            $('#detail-title').textContent = '기안을 열 수 없습니다.';
            $('#department-detail-content').hidden = true;
            notify(error.message, true);
            return;
        }
    }

    loadDetail();
})();
