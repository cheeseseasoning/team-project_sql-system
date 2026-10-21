<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div id="detail-config"
     data-request-id="<c:out value='${requestId}'/>"
     hidden></div>

<a class="back-link" href="${pageContext.request.contextPath}/requests">
    ← 내 요청 목록
</a>

<div class="page-heading">
    <div>
        <span id="request-number" class="caption"></span>
        <h1 id="detail-title">조회 요청을 불러오는 중입니다.</h1>
    </div>

    <div>
        <span id="detail-state"></span>

        <span id="draft-edit-action" hidden>
            <a id="draft-edit-link" class="button">초안 수정</a>
            <button id="draft-delete" type="button" class="button">초안 삭제</button>
            <button id="request-submit-open" type="button" class="button primary">제출</button>
        </span>

        <button id="request-redraft"
                type="button"
                class="button primary"
                hidden>
            재기안
        </button>
    </div>
</div>

<section id="request-info" class="panel" hidden>
    <h2>요청 정보</h2>

    <dl class="key-values">
        <div id="source-request-row" hidden>
            <dt>원본 요청</dt>
            <dd><a id="source-request-link"></a></dd>
        </div>
        <div>
            <dt>작성자</dt>
            <dd id="drafter-name"></dd>
        </div>
        <div>
            <dt>요청 부서</dt>
            <dd id="dept-name"></dd>
        </div>
        <div>
            <dt>조회 유형</dt>
            <dd id="request-class"></dd>
        </div>
        <div>
            <dt>결재 상태</dt>
            <dd id="approval-status"></dd>
        </div>
        <div>
            <dt>생성 시각</dt>
            <dd id="created-at"></dd>
        </div>
        <div>
            <dt>제출 시각</dt>
            <dd id="submitted-at"></dd>
        </div>
        <div>
            <dt>긴급 여부</dt>
            <dd><span id="request-urgency" class="badge"></span></dd>
        </div>
        <div>
            <dt>희망일</dt>
            <dd id="request-desired-date"></dd>
        </div>
    </dl>

    <hr>

    <h2>조회 목적</h2>
    <p id="request-purpose" class="notice-body"></p>

    <hr>

    <h2>상세 내용</h2>
    <p id="request-detail-content" class="notice-body"></p>
</section>

<section class="panel section-gap">
    <div class="panel-heading">
        <h2>등록한 SQL</h2>
        <span id="sql-count" class="caption"></span>
    </div>
    <div id="detail-items">불러오는 중입니다.</div>
</section>

<style>
    #approval-dialog {
        width: min(620px, calc(100% - 32px));
        max-height: 85vh;
        overflow: auto;
        border: 1px solid var(--line);
        border-radius: 9px;
        padding: 24px;
    }

    #approval-dialog::backdrop {
        background: rgba(0, 0, 0, 0.35);
    }

    #approval-dialog label {
        margin-top: 18px;
    }

    #approval-fields {
        border: 0;
        padding: 0;
        margin: 0;
        min-width: 0;
    }

    #approval-dialog-message {
        margin: 16px 0;
    }

    .approval-search-row {
        display: flex;
        gap: 8px;
        align-items: center;
    }

    .approval-search-row input {
        flex: 1;
        min-width: 0;
    }

    .approval-candidate-results {
        display: grid;
        gap: 6px;
        margin-top: 8px;
        max-height: 180px;
        overflow-y: auto;
    }

    .approval-candidate-option {
        width: 100%;
        text-align: left;
    }

    .approval-selected-list {
        margin-top: 8px;
        padding-left: 28px;
    }

    .approval-selected-list li {
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 8px 0;
    }

    .approval-selected-name {
        flex: 1;
    }
</style>

<dialog id="approval-dialog" aria-labelledby="approval-dialog-title">
    <h2 id="approval-dialog-title">결재선 확인 및 제출</h2>

    <p id="approval-dialog-message" role="status"></p>

    <form id="approval-form">
        <fieldset id="approval-fields" disabled>
            <label for="department-approver">부서장 (자동 지정)</label>
            <input id="department-approver" readonly>

            <label for="intermediate-keyword">중간 결재자 검색</label>
            <div class="approval-search-row">
                <input id="intermediate-keyword"
                       type="search"
                       placeholder="사용자명으로 검색">
                <button id="intermediate-search-button"
                        type="button"
                        class="button">
                    검색
                </button>
            </div>

            <div id="intermediate-search-results"
                 class="approval-candidate-results"
                 aria-live="polite"></div>

            <label for="selected-intermediate-approvers">
                선택한 중간 결재자 순서
            </label>
            <ol id="selected-intermediate-approvers"
                class="approval-selected-list"></ol>

            <label for="db-approver">
                DB팀 실행 승인자 <span class="required">*</span>
            </label>
            <select id="db-approver" required>
                <option value="">DB팀 실행 승인자를 선택하세요.</option>
            </select>

            <p class="caption section-gap">
                중간 결재자 → 부서장 → DB팀 실행 승인 순으로 진행됩니다.
                중간 결재자는 선택하지 않아도 됩니다.
                제출 후에는 요청 내용과 SQL을 수정할 수 없습니다.
            </p>

            <div class="form-footer">
                <button id="request-submit-confirm"
                        type="submit"
                        class="button primary">
                    제출 확정
                </button>
            </div>
        </fieldset>
    </form>

    <button id="approval-cancel"
            type="button"
            class="button section-gap">
        취소
    </button>
</dialog>

<script>
    const requestId = $('#detail-config').dataset.requestId;

    const detailStatusNames = {
        DRAFT: '초안',
        PENDING_APPROVAL: '결재 대기',
        READY: '실행 대기',
        RUNNING: '실행 중',
        COMPLETED: '실행 완료',
        PARTIAL_FAILED: '일부 실패',
        FAILED: '실패',
        REJECTED: '반려',
        WITHDRAWN: '회수'
    };

    const detailClassNames = {
        GENERAL: '일반 조회',
        SENSITIVE: '민감 조회'
    };

    const detailApprovalNames = {
        NOT_REQUIRED: '결재 불필요',
        PENDING: '결재 대기',
        APPROVED: '승인',
        REJECTED: '반려'
    };

    const validationNames = {
        PENDING: '검증 전',
        VALID: '검증 통과',
        INVALID: '검증 실패'
    };

    const authoringNames = {
        MANUAL: '직접 작성',
        AI: 'AI 작성'
    };

    function formatDetailDate(value) {
        return value
            ? value.replace('T', ' ').substring(0, 19)
            : '-';
    }

    function renderDetailItems(items) {
        if (items.length === 0) {
            return '<p class="empty">등록된 SQL이 없습니다.</p>';
        }

        return items.map(function (item) {
            const validationLabel =
                validationNames[item.validationStatus]
                || item.validationStatus
                || '-';

            const authoringLabel =
                authoringNames[item.authoringType]
                || item.authoringType
                || '-';

            return '<article class="result-item">'
                + '<div class="panel-heading">'
                + '<h3>'
                + esc(item.queryTitle || ('SQL ' + item.queryOrder))
                + '</h3>'
                + badge(validationLabel)
                + '</div>'
                + '<p class="caption">'
                + '순서 ' + esc(item.queryOrder)
                + ' · ' + esc(authoringLabel)
                + '</p>'
                + '<pre><code>'
                + esc(item.sqlText)
                + '</code></pre>'
                + '</article>';
        }).join('');
    }

    async function loadDetail() {
        try {
            const request = await api(
                '/requests/mine/' + encodeURIComponent(requestId)
            );

            $('#request-number').textContent = request.requestNo;
            $('#detail-title').textContent = request.title;

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

            $('#draft-edit-link').href =
                BASE + '/requests/'
                + encodeURIComponent(request.requestId)
                + '/edit';

            $('#draft-edit-action').hidden =
                request.requestStatus !== 'DRAFT';

            $('#request-redraft').hidden =
                request.requestStatus !== 'REJECTED';

            $('#detail-state').innerHTML = badge(
                detailStatusNames[request.requestStatus]
                || request.requestStatus
            );

            $('#drafter-name').textContent = request.drafterName;
            $('#dept-name').textContent = request.deptName;
            $('#request-purpose').textContent = request.purpose;

            const isUrgent = request.isUrgent === 'Y';
            $('#request-urgency').textContent =
                isUrgent ? '긴급' : '일반';
            $('#request-urgency').classList.toggle(
                'urgent-badge',
                isUrgent
            );

            $('#request-desired-date').textContent =
                request.desiredDate || '미지정';

            $('#request-detail-content').textContent =
                request.detailContent || '입력된 상세 내용이 없습니다.';

            $('#request-class').textContent =
                detailClassNames[request.requestClass]
                || request.requestClass
                || '미분류';

            $('#approval-status').textContent =
                request.requestStatus === 'DRAFT'
                    ? '제출 전'
                    : (
                        detailApprovalNames[request.approvalStatus]
                        || request.approvalStatus
                        || '-'
                    );

            $('#created-at').textContent =
                formatDetailDate(request.createdAt);

            $('#submitted-at').textContent = request.submittedAt
                ? formatDetailDate(request.submittedAt)
                : '아직 제출하지 않았습니다.';

            $('#sql-count').textContent =
                '총 ' + request.items.length + '개';

            $('#detail-items').innerHTML =
                renderDetailItems(request.items);

            $('#request-info').hidden = false;
        } catch (error) {
            $('#detail-title').textContent = '조회 요청 상세';
            $('#detail-items').textContent =
                '요청을 불러오지 못했습니다. 내 요청 목록으로 돌아가 확인하세요.';

            $('#draft-edit-action').hidden = true;
            $('#request-redraft').hidden = true;
            $('#source-request-row').hidden = true;

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
                {method: 'DELETE'}
            );

            location.href = BASE + '/requests';
        });
    });

    const approvalDialog = $('#approval-dialog');

    let approvalLoadVersion = 0;
    let approvalReady = false;
    let requestSubmitting = false;
    let departmentApproverId = null;
    let selectedIntermediateApprovers = [];

    function showApprovalMessage(message, error = false) {
        const element = $('#approval-dialog-message');
        element.textContent = message;
        element.classList.toggle('error-text', error);
        element.setAttribute('role', error ? 'alert' : 'status');
    }

    function renderSelectedIntermediateApprovers() {
        const list = $('#selected-intermediate-approvers');
        list.replaceChildren();

        selectedIntermediateApprovers.forEach(function (user, index) {
            const item = document.createElement('li');

            const name = document.createElement('span');
            name.className = 'approval-selected-name';
            name.textContent = (index + 1) + '. '
                + user.userName + ' · '
                + user.deptName + ' · '
                + (user.positionName || '직급 정보 없음');

            const upButton = document.createElement('button');
            upButton.type = 'button';
            upButton.className = 'button';
            upButton.textContent = '위로';
            upButton.disabled = index === 0;
            upButton.addEventListener('click', function () {
                if (index > 0) {
                    [
                        selectedIntermediateApprovers[index - 1],
                        selectedIntermediateApprovers[index]
                    ] = [
                        selectedIntermediateApprovers[index],
                        selectedIntermediateApprovers[index - 1]
                    ];
                    renderSelectedIntermediateApprovers();
                }
            });

            const downButton = document.createElement('button');
            downButton.type = 'button';
            downButton.className = 'button';
            downButton.textContent = '아래로';
            downButton.disabled =
                index === selectedIntermediateApprovers.length - 1;
            downButton.addEventListener('click', function () {
                if (index < selectedIntermediateApprovers.length - 1) {
                    [
                        selectedIntermediateApprovers[index],
                        selectedIntermediateApprovers[index + 1]
                    ] = [
                        selectedIntermediateApprovers[index + 1],
                        selectedIntermediateApprovers[index]
                    ];
                    renderSelectedIntermediateApprovers();
                }
            });

            const removeButton = document.createElement('button');
            removeButton.type = 'button';
            removeButton.className = 'button';
            removeButton.textContent = '삭제';
            removeButton.addEventListener('click', function () {
                selectedIntermediateApprovers =
                    selectedIntermediateApprovers.filter(
                        function (_, selectedIndex) {
                            return selectedIndex !== index;
                        }
                    );
                renderSelectedIntermediateApprovers();
            });

            item.append(name, upButton, downButton, removeButton);
            list.append(item);
        });
    }

    async function searchIntermediateApprovers() {
        const keyword = $('#intermediate-keyword').value.trim();
        const results = $('#intermediate-search-results');
        results.replaceChildren();

        showApprovalMessage('사용자를 검색하는 중입니다.');

        try {
            const users = await api(
                '/approvals/candidates?keyword='
                + encodeURIComponent(keyword)
            );

            if (!Array.isArray(users) || users.length === 0) {
                showApprovalMessage('검색된 활성 사용자가 없습니다.');
                return;
            }

            users.forEach(function (user) {
                const button = document.createElement('button');
                button.type = 'button';
                button.className =
                    'button approval-candidate-option';
                button.textContent = user.userName + ' · '
                    + user.deptName + ' · '
                    + (user.positionName || '직급 정보 없음');

                button.addEventListener('click', function () {
                    const userId = Number(user.userId);
                    const dbApproverId =
                        Number($('#db-approver').value);

                    if (userId === departmentApproverId) {
                        showApprovalMessage(
                            '부서장은 중간 결재자로 추가할 수 없습니다.',
                            true
                        );
                        return;
                    }

                    if (userId === dbApproverId) {
                        showApprovalMessage(
                            'DB팀 실행 승인자는 중간 결재자로 추가할 수 없습니다.',
                            true
                        );
                        return;
                    }

                    if (selectedIntermediateApprovers.some(
                            function (selected) {
                                return Number(selected.userId) === userId;
                            })) {
                        showApprovalMessage(
                            '이미 선택한 사용자입니다.',
                            true
                        );
                        return;
                    }

                    selectedIntermediateApprovers.push(user);
                    renderSelectedIntermediateApprovers();
                    showApprovalMessage('중간 결재자를 추가했습니다.');
                });

                results.append(button);
            });

            showApprovalMessage(
                '검색 결과에서 중간 결재자를 선택하세요.'
            );
        } catch (error) {
            showApprovalMessage(
                '사용자 검색에 실패했습니다. ' + error.message,
                true
            );
        }
    }

    $('#intermediate-search-button').addEventListener(
        'click',
        searchIntermediateApprovers
    );

    $('#intermediate-keyword').addEventListener(
        'keydown',
        function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                searchIntermediateApprovers();
            }
        }
    );

    $('#request-submit-open').addEventListener(
        'click',
        async function () {
            if (approvalDialog.open) {
                return;
            }

            const loadVersion = ++approvalLoadVersion;

            approvalReady = false;
            departmentApproverId = null;
            selectedIntermediateApprovers = [];
            renderSelectedIntermediateApprovers();

            $('#approval-fields').disabled = true;
            $('#department-approver').value = '';
            $('#intermediate-keyword').value = '';
            $('#intermediate-search-results').replaceChildren();
            $('#db-approver').replaceChildren(
                new Option('DB팀 실행 승인자를 선택하세요.', '')
            );

            showApprovalMessage('결재자 정보를 불러오는 중입니다.');
            approvalDialog.showModal();

            try {
                const candidates = await api(
                    '/requests/'
                    + encodeURIComponent(requestId)
                    + '/approval-candidates'
                );

                if (!approvalDialog.open
                        || loadVersion !== approvalLoadVersion) {
                    return;
                }

                const departmentApprover =
                    candidates?.departmentApprover;
                const dbApprovers = candidates?.dbApprovers;

                if (!departmentApprover?.userId) {
                    throw Error('소속 부서장이 지정되어 있지 않습니다.');
                }

                if (!Array.isArray(dbApprovers)
                        || dbApprovers.length === 0) {
                    throw Error(
                        '선택 가능한 DB팀 실행 승인자가 없습니다.'
                    );
                }

                departmentApproverId =
                    Number(departmentApprover.userId);

                $('#department-approver').value =
                    departmentApprover.userName + ' · '
                    + departmentApprover.deptName + ' · '
                    + (departmentApprover.positionName
                        || '직급 정보 없음');

                dbApprovers.forEach(function (user) {
                    const option = new Option(
                        user.userName + ' · ' + user.deptName + ' · '
                            + (user.positionName || '직급 정보 없음'),
                        String(user.userId)
                    );
                    $('#db-approver').add(option);
                });

                if (dbApprovers.length === 1) {
                    $('#db-approver').value =
                        String(dbApprovers[0].userId);
                }

                approvalReady = true;
                $('#approval-fields').disabled = false;

                showApprovalMessage(
                    '중간 결재자 0명 → 부서장 → DB팀 실행 승인 순으로 진행됩니다.'
                );
            } catch (error) {
                if (approvalDialog.open
                        && loadVersion === approvalLoadVersion) {
                    showApprovalMessage(
                        '결재자 정보를 불러오지 못했습니다. '
                        + error.message,
                        true
                    );
                }
            }
        }
    );

    $('#db-approver').addEventListener('change', function () {
        const dbApproverId = Number(this.value);

        if (selectedIntermediateApprovers.some(
                function (user) {
                    return Number(user.userId) === dbApproverId;
                })) {
            showApprovalMessage(
                '선택한 DB팀 실행 승인자가 중간 결재자에도 있습니다. '
                + '중간 결재자 목록에서 삭제하세요.',
                true
            );
        }
    });

    $('#approval-cancel').addEventListener('click', function () {
        if (!requestSubmitting) {
            approvalDialog.close();
        }
    });

    approvalDialog.addEventListener('cancel', function (event) {
        if (requestSubmitting) {
            event.preventDefault();
        }
    });

    approvalDialog.addEventListener('close', function () {
        approvalLoadVersion++;
        approvalReady = false;
    });

    $('#approval-form').addEventListener('submit', function (event) {
        event.preventDefault();

        if (!approvalReady || requestSubmitting) {
            return;
        }

        const dbApproverId = Number($('#db-approver').value);

        if (!Number.isSafeInteger(dbApproverId) || dbApproverId <= 0) {
            showApprovalMessage(
                'DB팀 실행 승인자를 선택하세요.',
                true
            );
            return;
        }

        if (selectedIntermediateApprovers.some(
                function (user) {
                    return Number(user.userId) === dbApproverId;
                })) {
            showApprovalMessage(
                'DB팀 실행 승인자는 중간 결재자와 중복할 수 없습니다.',
                true
            );
            return;
        }

        if (!window.confirm(
                '선택한 결재선으로 요청을 제출하시겠습니까?')) {
            return;
        }

        const intermediateApproverIds =
            selectedIntermediateApprovers.map(
                function (user) {
                    return Number(user.userId);
                }
            );

        busy($('#request-submit-confirm'), async function () {
            requestSubmitting = true;
            $('#approval-fields').disabled = true;
            $('#approval-cancel').disabled = true;

            showApprovalMessage('요청을 제출하는 중입니다.');

            try {
                await post(
                    '/requests/'
                    + encodeURIComponent(requestId)
                    + '/submit',
                    {
                        intermediateApproverIds:
                            intermediateApproverIds,
                        dbApproverId: dbApproverId
                    }
                );

                location.reload();
            } catch (error) {
                showApprovalMessage(error.message, true);
            } finally {
                requestSubmitting = false;
                $('#approval-fields').disabled = false;
                $('#approval-cancel').disabled = false;
            }
        });
    });

    let redraftCreating = false;

    $('#request-redraft').addEventListener('click', function () {
        if (redraftCreating) {
            return;
        }

        const confirmed = window.confirm(
            '이 요청을 복사하여 새 초안을 작성하시겠습니까?\n'
            + '기존 요청과 결재 이력은 유지됩니다.'
        );

        if (!confirmed) {
            return;
        }

        redraftCreating = true;

        busy(this, async function () {
            try {
                const result = await api(
                    '/requests/'
                    + encodeURIComponent(requestId)
                    + '/redraft',
                    {method: 'POST'}
                );

                if (!result?.requestId) {
                    throw Error('생성된 초안 정보를 확인할 수 없습니다.');
                }

                location.href =
                    BASE + '/requests/'
                    + encodeURIComponent(result.requestId)
                    + '/edit';
            } catch (error) {
                redraftCreating = false;
                throw error;
            }
        });
    });

    loadDetail();
</script>

<%@ include file="fragments/bottom.jspf" %>