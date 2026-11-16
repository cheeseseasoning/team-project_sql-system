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

async function loadDetail() {
	try {
		const request = await api(
			'/requests/mine/' + encodeURIComponent(requestId)
		);

		RequestDetailRenderer.renderRequestInfo(request);
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
		$('#draft-edit-link').href = ['CREATE', 'INSERT', 'UPDATE', 'DELETE'].includes(request.requestType)
			? BASE + '/view-create-requests/'
				+ encodeURIComponent(request.requestId) + '/edit'
			: BASE + '/requests/'
				+ encodeURIComponent(request.requestId) + '/edit';

		// 초안 상태에서만 수정 버튼 표시
		$('#draft-edit-action').hidden =
			request.requestStatus !== 'DRAFT';
		$('#draft-edit-link').hidden = false;

		$('#approval-line-open').disabled = request.requestStatus !== 'DRAFT';

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

		// 반려·회수와 실행 실패·취소 재기안은 서버의 공통 판단을 사용한다.
		$('#request-redraft').hidden = request.canRedraft !== true;

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

		await RequestDetailRenderer.loadRejectionReason(
		    request.requestStatus,
		    '/approvals/'
		        + encodeURIComponent(requestId)
		        + '/history'
		);

	} catch (error) {
		$('#detail-title').textContent = '기안서 상세';
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
 * DB 변경 요청의 SQL을 검사하고 항목별 결과를 표시한다.
 * 화면 검사 결과는 안내에 사용하며, 제출 시 서버가 저장된 원문을 다시 검사한다.
 */
async function validateSavedViewSqls() {
    sqlValidationInProgress = true;

    try {
        const requestType = currentRequestType;
        validationMessages = new Map();
        detailItems = detailItems.map(item => ({
            ...item,
            validationStatus: 'PENDING'
        }));
        $('#detail-items').innerHTML = renderDetailItems(detailItems);

        let results;
        if (requestType === 'CREATE') {
            results = await api(
                '/requests/' + encodeURIComponent(requestId) + '/view-sql/validate',
                { method: 'POST', timeoutMs: 120000 }
            );
        } else {
            const dmlResults = await post('/requests/dml-sql/validate', {
                sqlType: requestType,
                sqls: detailItems.map(item => item.sqlText)
            });

            // DML 검사 API는 SQL을 받은 순서대로 결과를 돌려준다.
            if (!Array.isArray(dmlResults) || dmlResults.length !== detailItems.length) {
                throw new Error('일부 SQL의 검사 결과를 확인하지 못했습니다. 다시 검사해 주세요.');
            }

            results = Object.fromEntries(
                detailItems.map((item, index) => [item.queryId, dmlResults[index]])
            );
        }

        const allowedStatuses = ['VALID', 'WARNING', 'BLOCKED'];
        const complete = detailItems.length > 0 && detailItems.every(item =>
            allowedStatuses.includes(results?.[String(item.queryId)]?.status)
        );

        if (!complete) {
            throw new Error('일부 SQL의 검사 결과를 확인하지 못했습니다. 다시 검사해 주세요.');
        }

        detailItems = detailItems.map(item => {
            const result = results[String(item.queryId)];
            validationMessages.set(item.queryId, result.message || '검사 결과 안내가 없습니다.');
            return { ...item, validationStatus: result.status };
        });
        $('#detail-items').innerHTML = renderDetailItems(detailItems);

        const blockedCount = detailItems.filter(item => item.validationStatus === 'BLOCKED').length;
        const warningCount = detailItems.filter(item => item.validationStatus === 'WARNING').length;

        if (blockedCount > 0) {
            notify(blockedCount + '개 SQL이 제출 차단 대상입니다. 각 SQL 아래 안내를 확인하세요.', true);
        } else if (warningCount > 0) {
            notify(warningCount + '개 SQL은 제출할 수 있으며 DB팀 검토가 필요합니다.');
        } else {
            notify('전체 SQL이 기본 파싱을 통과했습니다.');
        }
    } finally {
        sqlValidationInProgress = false;
    }
}

async function validateSavedSqls() {
	if (sqlValidationInProgress) {
	    return;
	}

	if (['CREATE', 'INSERT', 'UPDATE', 'DELETE'].includes(currentRequestType)) {
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
    const isDbChange =
        ['CREATE', 'INSERT', 'UPDATE', 'DELETE'].includes(currentRequestType);
    const path = isDbChange ? '/view-create-requests/' : '/requests/';

    location.href = BASE + path
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
let approvalLineVisibleDepartments = [];
let approvalLineApprovers = [];

// 모달에서 편집 중인 목록과 적용된 목록은 별도로 보관한다.
let appliedApprovalLine = null;

const approvalLineCheckedCandidateIds = new Set();
const approvalLineCheckedSelectedIds = new Set();

// 개인 ID와 부서 ID가 같아도 서로 다른 결재 대상으로 구분한다.
function approvalLineTargetId(target) {
    return target.targetType === 'DEPARTMENT'
        ? 'DEPARTMENT:' + target.deptId
        : 'USER:' + target.userId;
}

function getApprovalLineGroups() {
    const groups = new Map();

    approvalLineVisibleDepartments.forEach(department => {
        groups.set(String(department.deptId), {
            key: String(department.deptId),
            name: department.deptName,
            department,
            users: []
        });
    });

    approvalLineVisibleCandidates.forEach(user => {
        const key = user.deptId == null ? 'name:' + user.deptName : String(user.deptId);
        if (!groups.has(key)) {
            groups.set(key, { key, name: user.deptName || '부서 정보 없음', department: null, users: [] });
        }
        groups.get(key).users.push(user);
    });

    return Array.from(groups.values());
}

function getApprovalLinePayload(targets) {
    return targets.map(target => ({
        targetType: target.targetType === 'DEPARTMENT' ? 'DEPARTMENT' : 'USER',
        approverId: target.targetType === 'DEPARTMENT' ? null : Number(target.userId),
        approverDeptId: target.targetType === 'DEPARTMENT' ? Number(target.deptId) : null
    }));
}

function copyApprovalLine(users) {
    return users.map(user => ({ ...user }));
}

function showApprovalLineMessage(message) {
    $('#approval-line-message').textContent = message;
}

function showApprovalLineCandidateMessage(message) {
    approvalLineCandidateBody.innerHTML =
        '<p class="approval-line-empty" role="status">'
        + esc(message)
        + '</p>';
}

function getApprovalLineValidationMessage(targets) {
    if (!Array.isArray(targets) || targets.length === 0) {
        return '개인 또는 부서 결재 대상을 1개 이상 지정하세요.';
    }

    const ids = new Set();
    for (const target of targets) {
        if (!target || !['USER', 'DEPARTMENT'].includes(target.targetType || 'USER')) {
            return '결재 대상 정보를 다시 확인해 주세요.';
        }
        const isDepartment = target.targetType === 'DEPARTMENT';
        const id = Number(isDepartment ? target.deptId : target.userId);
        if (!Number.isSafeInteger(id) || id <= 0) {
            return '결재 대상 정보를 다시 확인해 주세요.';
        }
        if (isDepartment && !(Number(target.eligibleUserCount) > 0)) {
            return '기안자 외 결재 가능한 활성 사용자가 없는 부서는 지정할 수 없습니다.';
        }
        const key = approvalLineTargetId(target);
        if (ids.has(key)) return '동일한 개인 또는 부서를 중복 지정할 수 없습니다.';
        ids.add(key);
    }

    return '';
}

function isApprovalLineTargetAssigned(user) {
    return approvalLineApprovers.some(
        item => approvalLineTargetId(item) === approvalLineTargetId(user)
    );
}

function updateApprovalLineControls() {
    approvalLineSearchFields.disabled = approvalLineLoading;

    $('#approval-line-add').disabled =
        approvalLineLoading
        || approvalLineCheckedCandidateIds.size === 0;

    $('#approval-line-remove').disabled =
        approvalLineLoading
        || approvalLineCheckedSelectedIds.size === 0;

    $('#approval-line-apply').disabled =
        approvalLineLoading
        || !approvalLineReady
        || Boolean(
            getApprovalLineValidationMessage(approvalLineApprovers)
        );
}

function renderApprovalLineCandidates() {
    const groups = getApprovalLineGroups();
    if (groups.length === 0) {
        showApprovalLineCandidateMessage('검색 조건에 맞는 직원 또는 부서가 없습니다.');
        return;
    }

    const openStates = new Map(Array.from(
        approvalLineCandidateBody.querySelectorAll('.approval-line-department')
    ).map(element => [element.dataset.department, element.open]));
    const hasKeyword = Boolean($('#approval-line-keyword').value.trim());

    approvalLineCandidateBody.innerHTML = groups.map(group => {
        const isOpen = openStates.has(group.key) ? openStates.get(group.key) : hasKeyword;
        const department = group.department;
        let heading = '<span>' + esc(group.name) + '</span>';
        let countLabel = '검색 결과 ' + group.users.length + '명';

        if (department) {
            const key = approvalLineTargetId(department);
            const assigned = isApprovalLineTargetAssigned(department);
            const available = Number(department.eligibleUserCount) > 0;
            const checked = assigned || approvalLineCheckedCandidateIds.has(key);
            const disabled = assigned || !available;
            const hint = available ? '부서 공동 결재로 선택' : '기안자 외 결재 가능한 활성 사용자가 없습니다.';
            heading = '<label class="approval-line-department-select'
                + (checked ? ' is-selected' : '') + (disabled ? ' is-assigned' : '')
                + '" title="' + esc(hint) + '">'
                + '<input type="checkbox" class="approval-line-candidate-check approval-line-department-check"'
                + ' value="' + esc(key) + '"'
                + ' aria-label="' + esc(group.name + ' 부서 공동 결재 선택') + '"'
                + (checked ? ' checked' : '') + (disabled ? ' disabled' : '') + '>'
                + '<span>' + esc(group.name) + '</span></label>';
            countLabel = Number(department.eligibleUserCount || 0) + '명';
        }

        const employees = group.users.map(user => {
            const key = approvalLineTargetId(user);
            const assigned = isApprovalLineTargetAssigned(user);
            const checked = assigned || approvalLineCheckedCandidateIds.has(key);
            return '<li><label class="approval-line-candidate-item'
                + (checked ? ' is-selected' : '') + (assigned ? ' is-assigned' : '') + '">'
                + '<input type="checkbox" class="approval-line-candidate-check" value="' + esc(key) + '"'
                + (checked ? ' checked' : '') + (assigned ? ' disabled' : '') + '>'
                + '<span class="approval-line-employee-name">' + esc(user.userName) + '</span>'
                + '<span class="approval-line-employee-position">' + esc(user.positionName || '') + '</span>'
                + '</label></li>';
        }).join('');

        return '<details class="approval-line-department" data-department="' + esc(group.key) + '"'
            + (isOpen ? ' open' : '') + '><summary>' + heading
            + '<span class="approval-line-department-count">' + esc(countLabel) + '</span></summary>'
            + '<ul class="approval-line-employees">' + employees + '</ul></details>';
    }).join('');
}
function renderApprovalLineSelected() {
    $('#approval-line-selected-count').textContent =
        approvalLineApprovers.length + '개';

    if (approvalLineApprovers.length === 0) {
        approvalLineSelectedBody.innerHTML =
            '<li class="approval-line-empty">'
            + '개인 또는 부서를 추가해 주세요.'
            + '</li>';
        return;
    }

    approvalLineSelectedBody.innerHTML =
        approvalLineApprovers.map(function (user, index) {
            const userId = approvalLineTargetId(user);
            const checked =
                approvalLineCheckedSelectedIds.has(userId);

            const isDepartment = user.targetType === 'DEPARTMENT';
            const person = isDepartment ? user.deptName
                : [user.userName, user.positionName].filter(Boolean).join(' ');

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
                }
            ].map(function (item) {
                return '<button type="button" class="button"'
                    + ' data-approver-action="' + item.action + '"'
                    + ' data-index="' + index + '"'
                    + ' aria-label="'
                    + esc(person + ' ' + item.description) + '"'
                    + (approvalLineLoading || item.disabled
                        ? ' disabled'
                        : '')
                    + '>' + item.label
                    + '</button>';
            }).join('');

            return '<li class="approval-line-selected-item'
                + (checked ? ' is-selected' : '') + '">'
                + '<label class="approval-line-selected-label">'
                + '<input type="checkbox"'
                + ' class="approval-line-selected-check"'
                + ' value="' + esc(userId) + '"'
                + (checked ? ' checked' : '')
                + (approvalLineLoading ? ' disabled' : '')
                + '>'
                + '<span class="approval-line-selected-person">'
                + '<strong>'
                + (index + 1) + '. ' + esc(person)
                + '</strong>'
                + '<span class="approval-line-selected-department">'
                + esc(isDepartment ? '부서 공동 결재' : (user.deptName || '—') + ' · 개인')
                + '</span>'
                + '</span>'
                + '</label>'
                + '<div class="approval-line-row-actions">'
                + actions
                + '</div>'
                + '</li>';
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
        targetType: user.targetType || 'USER',
        name: user.targetType === 'DEPARTMENT' ? '-' : user.userName,
        approverDeptId: user.targetType === 'DEPARTMENT' ? user.deptId : null,
        approverDeptName: user.targetType === 'DEPARTMENT' ? user.deptName : null,
        deptName: user.deptName,
        positionName: user.positionName,
        status: null
    }));

    RequestDetailRenderer.renderApprovalPreview(rows);

    $('#request-approval-badge').textContent = '제출 전';
    $('#approval-preview-help').textContent =
        '결재선이 적용됐습니다. 제출 시 서버가 개인·부서 결재 자격을 다시 확인합니다.';
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
    approvalLineVisibleDepartments = [];
    approvalLineCheckedCandidateIds.clear();
	approvalLineCheckedSelectedIds.clear();

    renderApprovalLineSelected();
    updateApprovalLineControls();
    showApprovalLineMessage('');
    showApprovalLineCandidateMessage('직원·부서를 불러오는 중입니다.');
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

        if (!Array.isArray(data.candidates) || !Array.isArray(data.departments)) {
            throw Error('직원·부서 후보 응답을 확인할 수 없습니다.');
        }

        approvalLineVisibleCandidates = data.candidates.map(user => ({ ...user, targetType: 'USER' }));
        approvalLineVisibleDepartments = data.departments.map(department => ({ ...department, targetType: 'DEPARTMENT' }));
        approvalLineReady = true;
    } catch (error) {
        if (!approvalLineDialog.open
                || version !== approvalLineCandidateVersion) {
            return;
        }

        showApprovalLineCandidateMessage('직원·부서를 불러오지 못했습니다.');
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
    approvalLineVisibleDepartments = [];
    approvalLineCheckedCandidateIds.clear();
    approvalLineReady = false;

    $('#approval-line-keyword').value = '';

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

	checkbox.closest('.approval-line-candidate-item, .approval-line-department-select')
		.classList.toggle(
		    'is-selected',
		    checkbox.checked
	);

    updateApprovalLineControls();
});

$('#approval-line-add').addEventListener('click', function () {
    if (approvalLineLoading) return;

    let added = 0;

    // 화면의 부서 → 직원 순서대로 추가한다. 부서 선택은 직원 전체 선택이 아니다.
    const visibleTargets = getApprovalLineGroups().flatMap(group =>
        group.department ? [group.department, ...group.users] : group.users
    );
    visibleTargets.forEach(target => {
        if (approvalLineCheckedCandidateIds.has(approvalLineTargetId(target))
                && !isApprovalLineTargetAssigned(target)
                && !getApprovalLineValidationMessage([target])) {
            approvalLineApprovers.push({ ...target });
            added++;
        }
    });

    approvalLineCheckedCandidateIds.clear();
    refreshApprovalLineEditor();
    showApprovalLineMessage(added + '개 결재 대상을 추가했습니다.');
});

approvalLineSelectedBody.addEventListener('change', function (event) {
    const checkbox = event.target;

    if (approvalLineLoading
            || !checkbox.matches('.approval-line-selected-check')
            || checkbox.disabled) {
        return;
    }

    if (checkbox.checked) {
        approvalLineCheckedSelectedIds.add(checkbox.value);
    } else {
        approvalLineCheckedSelectedIds.delete(checkbox.value);
    }

    checkbox.closest('.approval-line-selected-item')
        .classList.toggle('is-selected', checkbox.checked);

    updateApprovalLineControls();
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

$('#approval-line-remove').addEventListener('click', function () {
    if (approvalLineLoading
            || approvalLineCheckedSelectedIds.size === 0) {
        return;
    }

    const previousCount = approvalLineApprovers.length;

    approvalLineApprovers = approvalLineApprovers.filter(
        user => !approvalLineCheckedSelectedIds.has(
            approvalLineTargetId(user)
        )
    );

    const removedCount =
        previousCount - approvalLineApprovers.length;

    approvalLineCheckedSelectedIds.clear();

    refreshApprovalLineEditor();
    showApprovalLineMessage(removedCount + '개 결재 대상을 제외했습니다.');
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
    approvalLineVisibleDepartments = [];
    approvalLineCheckedCandidateIds.clear();
	approvalLineCheckedSelectedIds.clear();

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

    const isDmlRequest =
        ['INSERT', 'UPDATE', 'DELETE'].includes(currentRequestType);

    const hasUnsubmittableSql = detailItems.some(item =>
        item.validationStatus !== 'VALID'
        && !(isDmlRequest && item.validationStatus === 'WARNING')
    );

    // DML 경고는 제출할 수 있지만, 미검사·차단 항목은 제출하지 못한다.
    if (hasUnsubmittableSql) {
        if (detailItems.some(item =>
            ['INVALID', 'BLOCKED'].includes(item.validationStatus))) {
            window.alert(
                '제출할 수 없는 SQL이 있습니다. 원인을 수정한 뒤 전체 SQL을 다시 검증해 주세요.'
            );
        } else {
            window.alert('제출 전에 전체 SQL 검증을 먼저 진행해 주세요.');
        }

        $('#validate-sqls').focus();
        return;
    }

    const error =
        getApprovalLineValidationMessage(appliedApprovalLine);

    if (error) {
        window.alert(error);
        approvalLineOpenButton.click();
        return;
    }

    if (!window.confirm('선택한 결재선으로 요청을 제출하시겠습니까?')) {
        return;
    }

    const approvalTargets = getApprovalLinePayload(appliedApprovalLine);

    requestSubmitting = true;

    busy(this, async function () {
        try {
            await post(
                '/requests/' + encodeURIComponent(requestId) + '/submit',
                { approvalTargets }
            );

            location.reload();
        } catch (error) {
            try {
                await validateSavedSqls();
            } catch (validationError) {
                // 최초 제출 오류를 아래에서 표시한다.
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
