const requestId = $('#detail-config').dataset.requestId;
let detailItems = [];
let validationMessages = new Map();
let sqlValidationInProgress = false;
let currentRequestType = 'SELECT';

function validationMessageStorageKey(queryId) {
	return 'sqlflow-validation-message:' + requestId + ':' + queryId;
}

function restoreValidationMessages(items) {
	const messages = new Map();
	items.forEach(function (item) {
		if (item.validationStatus !== 'INVALID') {
			return;
		}
		try {
			const message = sessionStorage.getItem(
				validationMessageStorageKey(item.queryId));
			if (message) {
				messages.set(item.queryId, message);
			}
		} catch (error) {
			// The persisted status still renders when browser storage is unavailable.
		}
	});
	return messages;
}

function formatDetailDate(value) {
    return RequestDetailRenderer.formatDate(value);
}

function renderDetailItems(items) {
    return RequestDetailRenderer.renderItems(
        items,
        validationMessages
    );
}

function renderApprovalHistory(items) {
    return RequestDetailRenderer.renderHistory(items);
}

async function loadApprovalHistory() {
	const panel = $('#approval-history-panel');
	const list = $('#approval-history-list');

	try {
		const items = await api(
			'/approvals/' + encodeURIComponent(requestId) + '/history'
		);
		list.innerHTML = renderApprovalHistory(items);
	} catch (error) {
		list.innerHTML =
			'<li class="empty">결재 이력을 불러오지 못했습니다.</li>';
	}

	panel.hidden = false;
}

async function loadDetail() {
	try {
		const request = await api(
			'/requests/mine/' + encodeURIComponent(requestId)
		);

		RequestDetailRenderer.renderRequestInfo(request);
	RequestDetailRenderer.renderViewCreateResult(request);
		currentRequestType = request.requestType || 'SELECT';

		const hasSource =
			request.sourceRequestId != null
			&& Boolean(request.sourceRequestNo);

		$('#source-request-row').hidden = !hasSource;

		const sourceLink = $('#source-request-link');

		if (hasSource) {
			sourceLink.textContent = request.sourceRequestNo;
			sourceLink.href =
				BASE + '/requests/'
				+ encodeURIComponent(request.sourceRequestId);
		} else {
			sourceLink.textContent = '';
			sourceLink.removeAttribute('href');
		}

		// 초안 수정 화면으로 이동할 주소
		$('#draft-edit-link').href = request.requestType === 'CREATE_VIEW'
			? BASE + '/view-create-requests/'
				+ encodeURIComponent(request.requestId) + '/edit'
			: BASE + '/requests/'
				+ encodeURIComponent(request.requestId) + '/edit';

		// 초안 상태에서만 수정 버튼 표시
		$('#draft-edit-action').hidden =
			request.requestStatus !== 'DRAFT';
		$('#draft-edit-link').hidden = false;

		$('#approval-line-open').disabled =
			request.requestStatus !== 'DRAFT';

		// 회수 가능 여부는 화면 표시용이며 서버에서 다시 검사한다.
		const approvals = Array.isArray(request.approvals)
		    ? request.approvals
		    : [];

		const canWithdraw =
		    request.requestStatus === 'PENDING_APPROVAL'
		    && request.approvalStatus === 'PENDING'
		    && approvals.some(approval => approval.status === 'PENDING')
		    && approvals.every(approval =>
		        approval.status === 'PENDING'
		        || approval.status === 'WAITING'
		    );

		$('#request-withdraw').hidden = !canWithdraw;

		// 반려·회수된 요청은 새로운 초안으로 재작성할 수 있다.
		$('#request-redraft').hidden =
		    !['REJECTED', 'WITHDRAWN'].includes(request.requestStatus);

		
		detailItems = (request.items || []).map(function (item) {
			return {
				...item,
				validationStatus: item.validationStatus || 'PENDING'
			};
		});
		
		validationMessages = restoreValidationMessages(detailItems);
		
		$('#sql-validation-controls').hidden =
			request.requestStatus !== 'DRAFT';

		$('#detail-items').innerHTML =
			renderDetailItems(detailItems);

		renderRequestApprovalLine(request);
		
		$('#request-info').hidden = false;
		
		await loadApprovalHistory();

	} catch (error) {
		$('#detail-title').textContent = '조회 요청 상세';
		$('#detail-items').textContent =
			'요청을 불러오지 못했습니다. 내 요청 목록으로 돌아가 확인하세요.';

		$('#draft-edit-action').hidden = true;
		$('#request-redraft').hidden = true;
		$('#source-request-row').hidden = true;
		$('#request-withdraw').hidden = true;

		notify(error.message, true);
	}
}

$('#draft-delete').addEventListener('click', function () {
	const confirmed = window.confirm(
		'이 초안을 삭제하시겠습니까?\n'
		+ '등록한 SQL도 함께 삭제되며 복구할 수 없습니다.'
	);

	if (!confirmed) {
		return;
	}

	busy(this, async function () {
		await api(
			'/requests/drafts/' + encodeURIComponent(requestId),
			{
				method: 'DELETE'
			}
		);

		location.href = BASE + '/requests';
	});
});

/**
 * 저장된 뷰 생성 SQL을 검사하고 항목별 결과를 표시한다.
 * 결과는 화면에만 유지하며, 제출 시 서버에서 다시 검사한다.
 */
async function validateSavedViewSqls() {
    sqlValidationInProgress = true;

    // 이전 검사 결과가 새 검사 결과로 오인되지 않도록 초기화한다.
    validationMessages = new Map();
    detailItems = detailItems.map(item => ({
        ...item,
        validationStatus: 'PENDING'
    }));

    $('#detail-items').innerHTML = renderDetailItems(detailItems);

    try {
        const results = await api(
            '/requests/' + encodeURIComponent(requestId)
                + '/view-sql/validate',
            { method: 'POST', timeoutMs: 120000 }
        );

        const allowedStatuses = ['VALID', 'WARNING', 'BLOCKED'];

        // 일부 SQL의 결과가 누락돼도 전체 검사 완료로 표시하지 않는다.
        const complete = detailItems.length > 0
            && detailItems.every(item =>
                allowedStatuses.includes(
                    results?.[String(item.queryId)]?.status
                )
            );

        if (!complete) {
            throw new Error(
                '일부 SQL의 검사 결과를 확인하지 못했습니다. 다시 검사해 주세요.'
            );
        }

        detailItems = detailItems.map(item => {
            const result = results[String(item.queryId)];

            validationMessages.set(
                item.queryId,
                result.message || '검사 결과 안내가 없습니다.'
            );

            return {
                ...item,
                validationStatus: result.status
            };
        });

        $('#detail-items').innerHTML = renderDetailItems(detailItems);

        const blockedCount = detailItems.filter(
            item => item.validationStatus === 'BLOCKED'
        ).length;

        const warningCount = detailItems.filter(
            item => item.validationStatus === 'WARNING'
        ).length;

        if (blockedCount > 0) {
            notify(
                blockedCount + '개 SQL이 제출 차단 대상입니다. '
                    + '각 SQL 아래 안내를 확인하세요.',
                true
            );
        } else if (warningCount > 0) {
            notify(
                warningCount + '개 SQL은 검사 불확실 상태입니다. '
                    + '제출은 가능하지만 DB팀 검토가 필요합니다.'
            );
        } else {
            notify(
                '전체 SQL이 기본 파싱을 통과했습니다. '
                    + '실제 실행 성공을 보장하지는 않습니다.'
            );
        }
    } finally {
        sqlValidationInProgress = false;
    }
}

async function validateSavedSqls() {
	if (sqlValidationInProgress) {
	    return;
	}
	
	if (currentRequestType === 'CREATE_VIEW') {
	    await validateSavedViewSqls();
	    return;
	}
	
	sqlValidationInProgress = true;
	try {
		const results = await api(
			'/requests/' + encodeURIComponent(requestId) + '/validate',
			{ method: 'POST', timeoutMs: 120000 }
		);

		const byQueryId = new Map(
			(results || []).map(function (result) {
				return [String(result.queryId), result];
			})
		);
		validationMessages = new Map();
		detailItems = detailItems.map(function (item) {
			const result = byQueryId.get(String(item.queryId));
			if (!result) {
				return item;
			}
			if (result.validationStatus === 'INVALID') {
				validationMessages.set(item.queryId, result.message);
				try {
					sessionStorage.setItem(
						validationMessageStorageKey(item.queryId),
						result.message || '검증에 실패했습니다.');
				} catch (error) {
					// The failure reason remains visible until the page is closed.
				}
			} else {
				try {
					sessionStorage.removeItem(
						validationMessageStorageKey(item.queryId));
				} catch (error) {
					// Ignore unavailable browser storage.
				}
			}
			return { ...item, validationStatus: result.validationStatus };
		});

		$('#detail-items').innerHTML = renderDetailItems(detailItems);
		const failedCount = detailItems.filter(
			item => item.validationStatus !== 'VALID'
		).length;
		if (failedCount === 0 && detailItems.length > 0) {
			notify('등록한 SQL ' + detailItems.length + '개가 모두 검증 완료되었습니다.');
		} else {
			notify(failedCount + '개 SQL의 검증이 실패했습니다. 각 SQL 아래 원인을 확인해 수정한 뒤 다시 검증하세요.', true);
		}
	} finally {
		sqlValidationInProgress = false;
	}
}

$('#validate-sqls').addEventListener('click', function () {
	busy(this, validateSavedSqls);
});

let requestSubmitting = false;

$('#request-redraft').addEventListener('click', function () {
    location.href =
        BASE + '/requests/'
        + encodeURIComponent(requestId)
        + '/redraft';
});

// 결재선 등록 모달
const approvalLineDialog = $('#approval-line-dialog');
const approvalLineOpenButton = $('#approval-line-open');
const approvalLineSearchFields = $('#approval-line-search-fields');
const approvalLineCandidateBody = $('#approval-line-candidates');
const approvalLineSelectedBody = $('#approval-line-selected');

let approvalLineCandidateVersion = 0;
let approvalLineLoading = false;
let approvalLineReady = false;

let approvalLineVisibleCandidates = [];
let approvalLineApprovers = [];

// 모달에서 편집 중인 목록과 적용된 목록은 별도로 보관한다.
let appliedApprovalLine = null;

const approvalLineCheckedCandidateIds = new Set();

function approvalLineUserId(user) {
    return String(user.userId);
}

function copyApprovalLine(users) {
    return users.map(user => ({ ...user }));
}

function showApprovalLineMessage(message) {
    $('#approval-line-message').textContent = message;
}

function showApprovalLineCandidateMessage(message) {
    approvalLineCandidateBody.innerHTML =
        '<tr><td colspan="5" class="empty" role="status">'
        + esc(message)
        + '</td></tr>';
}

function getApprovalLineValidationMessage(users) {
    if (!Array.isArray(users) || users.length === 0) {
        return '결재자를 1명 이상 지정하세요.';
    }

    const ids = users.map(user => Number(user.userId));

    if (ids.some(id => !Number.isSafeInteger(id) || id <= 0)) {
        return '결재자 정보를 다시 확인해 주세요.';
    }

    if (new Set(ids).size !== ids.length) {
        return '동일한 결재자를 중복 지정할 수 없습니다.';
    }

    return '';
}

function isApprovalLineUserAssigned(user) {
    return approvalLineApprovers.some(
        item => approvalLineUserId(item) === approvalLineUserId(user)
    );
}

function updateApprovalLineControls() {
    approvalLineSearchFields.disabled = approvalLineLoading;

    $('#approval-line-add').disabled =
        approvalLineLoading
        || approvalLineCheckedCandidateIds.size === 0;

    $('#approval-line-reset').disabled =
        approvalLineLoading || approvalLineApprovers.length === 0;

    $('#approval-line-apply').disabled =
        approvalLineLoading
        || !approvalLineReady
        || Boolean(getApprovalLineValidationMessage(approvalLineApprovers));
}

function renderApprovalLineCandidates() {
    if (approvalLineVisibleCandidates.length === 0) {
        showApprovalLineCandidateMessage('조회된 사용자가 없습니다.');
        return;
    }

    approvalLineCandidateBody.innerHTML =
        approvalLineVisibleCandidates.map(user => {
            const userId = approvalLineUserId(user);
            const assigned = isApprovalLineUserAssigned(user);
            const checked =
                assigned || approvalLineCheckedCandidateIds.has(userId);

            return '<tr class="' + (checked ? 'is-selected' : '') + '">'
                + '<td><input type="checkbox"'
                + ' class="approval-line-candidate-check"'
                + ' value="' + esc(userId) + '"'
                + ' aria-label="' + esc(user.userName) + ' 선택"'
                + (checked ? ' checked' : '')
                + (assigned ? ' disabled' : '')
                + '></td>'
                + '<td>' + esc(user.userName) + '</td>'
                + '<td>' + esc(user.userId) + '</td>'
                + '<td>' + esc(user.deptName) + '</td>'
                + '<td>' + esc(user.positionName || '—') + '</td>'
                + '</tr>';
        }).join('');
}

function renderApprovalLineSelected() {
    if (approvalLineApprovers.length === 0) {
        approvalLineSelectedBody.innerHTML =
            '<tr><td colspan="6" class="empty">'
            + '결재자를 1명 이상 추가하세요.'
            + '</td></tr>';
        return;
    }

    approvalLineSelectedBody.innerHTML =
        approvalLineApprovers.map((user, index) => {
            const actions = [
                {
                    action: 'up',
                    label: '↑',
                    description: '위로 이동',
                    disabled: index === 0
                },
                {
                    action: 'down',
                    label: '↓',
                    description: '아래로 이동',
                    disabled: index === approvalLineApprovers.length - 1
                },
                {
                    action: 'delete',
                    label: '삭제',
                    description: '결재선에서 삭제',
                    disabled: false
                }
            ].map(item =>
                '<button type="button" class="button"'
                + ' data-approver-action="' + item.action + '"'
                + ' data-index="' + index + '"'
                + ' aria-label="' + esc(user.userName)
                + ' ' + item.description + '"'
                + (approvalLineLoading || item.disabled ? ' disabled' : '')
                + '>' + item.label + '</button>'
            ).join('');

            return '<tr>'
                + '<td>' + (index + 1) + '</td>'
                + '<td>'
                + (index === approvalLineApprovers.length - 1
                    ? '최종 결재자'
                    : '결재자')
                + '</td>'
                + '<td>' + esc(user.userName) + '</td>'
                + '<td>' + esc(user.deptName) + '</td>'
                + '<td>' + esc(user.positionName || '—') + '</td>'
                + '<td><div class="approval-line-row-actions">'
                + actions
                + '</div></td>'
                + '</tr>';
        }).join('');
}

function refreshApprovalLineEditor() {
    renderApprovalLineSelected();
    renderApprovalLineCandidates();
    updateApprovalLineControls();
}

function renderAppliedApprovalLine() {
    const rows = appliedApprovalLine.map((user, index) => ({
        order: index + 1,
        role: index === appliedApprovalLine.length - 1
            ? '최종 결재자'
            : '결재자',
        name: user.userName,
        deptName: user.deptName,
        positionName: user.positionName,
        status: null
    }));

    $('#request-approval-list').innerHTML =
        RequestDetailRenderer.renderApprovalRows(rows);

    $('#request-approval-badge').textContent = '제출 전';
    $('#approval-preview-help').textContent =
        '결재선이 적용됐습니다. 제출 시 서버가 결재자 자격을 다시 확인합니다.';
}

function renderRequestApprovalLine(request) {
    if (request.requestStatus === 'DRAFT' && appliedApprovalLine) {
        renderAppliedApprovalLine();
        return;
    }

    RequestDetailRenderer.renderSavedApprovalLine(request);
}

async function loadApprovalLineCandidates() {
    const version = ++approvalLineCandidateVersion;
    const keyword = $('#approval-line-keyword').value.trim();

    approvalLineLoading = true;
    approvalLineReady = false;
    approvalLineVisibleCandidates = [];
    approvalLineCheckedCandidateIds.clear();

    renderApprovalLineSelected();
    updateApprovalLineControls();
    showApprovalLineMessage('');
    showApprovalLineCandidateMessage('결재자를 불러오는 중입니다.');
    approvalLineCandidateBody.setAttribute('aria-busy', 'true');

    try {
        const data = await api(
            '/requests/' + encodeURIComponent(requestId)
            + '/approval-candidates?keyword='
            + encodeURIComponent(keyword)
        );

        if (!approvalLineDialog.open
                || version !== approvalLineCandidateVersion) {
            return;
        }

        if (!Array.isArray(data.candidates)) {
            throw Error('결재자 후보 응답을 확인할 수 없습니다.');
        }

        approvalLineVisibleCandidates = data.candidates;
        approvalLineReady = true;
    } catch (error) {
        if (!approvalLineDialog.open
                || version !== approvalLineCandidateVersion) {
            return;
        }

        showApprovalLineCandidateMessage('결재자를 불러오지 못했습니다.');
        showApprovalLineMessage(error.message);
    } finally {
        if (approvalLineDialog.open
                && version === approvalLineCandidateVersion) {
            approvalLineLoading = false;
            approvalLineCandidateBody.setAttribute('aria-busy', 'false');

            renderApprovalLineSelected();

            if (approvalLineReady) {
                renderApprovalLineCandidates();
            }

            updateApprovalLineControls();
        }
    }
}

approvalLineOpenButton.addEventListener('click', function () {
    if (this.disabled || requestSubmitting || approvalLineDialog.open) {
        return;
    }

    approvalLineApprovers = appliedApprovalLine
        ? copyApprovalLine(appliedApprovalLine)
        : [];

    approvalLineVisibleCandidates = [];
    approvalLineCheckedCandidateIds.clear();
    approvalLineReady = false;

    $('#approval-line-keyword').value = '';
    $('#approval-line-name').value = '';

    showApprovalLineMessage('');
    approvalLineDialog.showModal();

    loadApprovalLineCandidates();
});

$('#approval-line-search').addEventListener('click', function () {
    if (!approvalLineLoading) {
        loadApprovalLineCandidates();
    }
});

$('#approval-line-keyword').addEventListener('keydown', function (event) {
    if (event.key !== 'Enter' || event.isComposing) return;

    event.preventDefault();

    if (!approvalLineLoading) {
        loadApprovalLineCandidates();
    }
});

approvalLineCandidateBody.addEventListener('change', function (event) {
    const checkbox = event.target;

    if (approvalLineLoading
            || !checkbox.matches('.approval-line-candidate-check')
            || checkbox.disabled) {
        return;
    }

    if (checkbox.checked) {
        approvalLineCheckedCandidateIds.add(checkbox.value);
    } else {
        approvalLineCheckedCandidateIds.delete(checkbox.value);
    }

    checkbox.closest('tr').classList.toggle(
        'is-selected', checkbox.checked
    );

    updateApprovalLineControls();
});

$('#approval-line-add').addEventListener('click', function () {
    if (approvalLineLoading) return;

    let added = 0;

    // 여러 명을 선택하면 검색 결과의 표시 순서대로 추가한다.
    approvalLineVisibleCandidates.forEach(user => {
        if (approvalLineCheckedCandidateIds.has(approvalLineUserId(user))
                && !isApprovalLineUserAssigned(user)) {
            approvalLineApprovers.push({ ...user });
            added++;
        }
    });

    approvalLineCheckedCandidateIds.clear();
    refreshApprovalLineEditor();
    showApprovalLineMessage(added + '명을 추가했습니다.');
});

approvalLineSelectedBody.addEventListener('click', function (event) {
    const button = event.target.closest('[data-approver-action]');

    if (!button || button.disabled || approvalLineLoading) return;

    const index = Number(button.dataset.index);
    const action = button.dataset.approverAction;

    if (!Number.isInteger(index)
            || index < 0
            || index >= approvalLineApprovers.length) {
        return;
    }

    if (action === 'delete') {
        approvalLineApprovers.splice(index, 1);
    } else if (action === 'up' || action === 'down') {
        const next = index + (action === 'up' ? -1 : 1);

        if (next < 0 || next >= approvalLineApprovers.length) return;

        [approvalLineApprovers[index], approvalLineApprovers[next]] =
            [approvalLineApprovers[next], approvalLineApprovers[index]];
    } else {
        return;
    }

    refreshApprovalLineEditor();
    showApprovalLineMessage('');
});

$('#approval-line-reset').addEventListener('click', function () {
    if (approvalLineLoading || approvalLineApprovers.length === 0) return;
    if (!window.confirm('구성한 결재선을 초기화하시겠습니까?')) return;

    approvalLineApprovers = [];
    approvalLineCheckedCandidateIds.clear();

    refreshApprovalLineEditor();
    showApprovalLineMessage('결재선을 초기화했습니다.');
});

$('#approval-line-apply').addEventListener('click', function () {
    if (this.disabled || approvalLineLoading) return;

    const error = getApprovalLineValidationMessage(approvalLineApprovers);

    if (error) {
        showApprovalLineMessage(error);
        return;
    }

    appliedApprovalLine = copyApprovalLine(approvalLineApprovers);
    renderAppliedApprovalLine();
    approvalLineDialog.close();
});

$('#approval-line-cancel').addEventListener('click', function () {
    approvalLineDialog.close();
});

approvalLineDialog.addEventListener('close', function () {
    approvalLineCandidateVersion++;
    approvalLineLoading = false;
    approvalLineReady = false;
    approvalLineVisibleCandidates = [];
    approvalLineCheckedCandidateIds.clear();

    approvalLineCandidateBody.setAttribute('aria-busy', 'false');
    approvalLineOpenButton.focus();
});

// 적용한 결재선을 요청과 함께 제출한다.
$('#request-submit-open').addEventListener('click', function () {
    if (requestSubmitting) return;

    if (sqlValidationInProgress) {
        window.alert('SQL 검증이 끝난 뒤 제출해 주세요.');
        return;
    }

    if (!detailItems.length) {
        window.alert('SQL을 먼저 등록해 주세요.');
        return;
    }

    if (currentRequestType !== 'CREATE_VIEW'
            && detailItems.some(item => item.validationStatus !== 'VALID')) {
        window.alert('제출 전에 전체 SQL 검증을 완료해 주세요.');
        $('#validate-sqls').focus();
        return;
    }

    const error = getApprovalLineValidationMessage(appliedApprovalLine);

    if (error) {
        window.alert(error);
        approvalLineOpenButton.click();
        return;
    }

    if (!window.confirm('선택한 결재선으로 요청을 제출하시겠습니까?')) {
        return;
    }

    const approverIds =
        appliedApprovalLine.map(user => Number(user.userId));

    requestSubmitting = true;

    busy(this, async function () {
        try {
            await post(
                '/requests/' + encodeURIComponent(requestId) + '/submit',
                { approverIds }
            );

            location.reload();
        } catch (error) {
            if (currentRequestType !== 'CREATE_VIEW') {
                try {
                    await validateSavedSqls();
                } catch (validationError) {
                    // 최초 제출 오류를 아래에서 표시한다.
                }
            }

            notify(error.message, true);
        } finally {
            requestSubmitting = false;
        }
    });
});

let requestWithdrawing = false;

$('#request-withdraw').addEventListener('click', function () {
    if (requestWithdrawing) {
        return;
    }

    const confirmed = window.confirm(
        '이 기안서를 회수하시겠습니까?\n'
        + '현재 결재선은 종료되고 원본과 이력은 유지됩니다.\n'
        + '수정이 필요하면 회수 후 재기안해 주세요.'
    );

    if (!confirmed) {
        return;
    }

    requestWithdrawing = true;

    busy(this, async function () {
        try {
            await api(
                '/requests/'
                + encodeURIComponent(requestId)
                + '/withdraw',
                { method: 'POST' }
            );
        } catch (error) {
            requestWithdrawing = false;

            // 화면을 연 뒤 승인이 진행됐을 수 있으므로 최신 상태를 조회한다.
            await loadDetail();
            throw error;
        }

        location.reload();
    });
});

loadDetail();
