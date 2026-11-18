<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div id="approval-config"
     data-request-id="<c:out value='${requestId}'/>"
     hidden></div>

<main class="approval-page request-detail-page request-document-page approval-document-page">
    <a class="back-link approval-detail-breadcrumb"
       href="${pageContext.request.contextPath}/approvals/pending">
        ← 받은 결재
    </a>

    <article class="request-document approval-detail-card">
        <header class="request-document-heading">
            <p class="request-document-caption">Q-Pass · 받은 결재</p>
            <h1 id="approval-title">기안서를 불러오는 중입니다.</h1>
        </header>

        <section class="panel request-info-panel approval-detail-top">
            <div class="request-detail-layout">
                <div class="request-information-summary">
                    <h2 class="request-information-heading">요청 정보</h2>
                    <table class="request-info-table">
                        <caption class="sr-only">요청 정보</caption>
                        <colgroup>
                            <col class="request-info-label-column">
                            <col class="request-info-value-column">
                            <col class="request-info-label-column">
                            <col class="request-info-value-column">
                        </colgroup>
                        <tbody>
                            <tr>
                                <th>문서번호</th>
                                <td id="approval-request-no" colspan="3">불러오는 중</td>
                            </tr>
                            <tr>
                                <th>기안 부서</th>
                                <td id="approval-department"></td>
                                <th>기안자</th>
                                <td id="approval-drafter"></td>
                            </tr>
                            <tr>
                                <th>작성일</th>
                                <td id="approval-created-at"></td>
                                <th>처리 희망일</th>
                                <td id="approval-desired-date"></td>
                            </tr>
                        </tbody>
                    </table>
                </div>

                <aside class="panel request-approval-panel" aria-labelledby="approval-line-title">
                    <div class="panel-heading">
                        <h2 id="approval-line-title">결재란</h2>
                        <div class="approval-line-controls">
                            <button id="approval-line-prev"
                                    type="button"
                                    aria-label="이전 결재자"
                                    disabled>◀</button>
                            <span id="approval-line-range">0 / 0</span>
                            <button id="approval-line-next"
                                    type="button"
                                    aria-label="다음 결재자"
                                    disabled>▶</button>
                        </div>
                    </div>
                    <div id="approval-line" class="approval-line">
                        <p class="caption">결재선을 불러오는 중입니다.</p>
                    </div>
                </aside>
            </div>
        </section>

        <section class="request-content-section approval-section">
            <h2>요청 목적</h2>
            <p id="approval-purpose" class="notice-body"></p>
        </section>

        <section class="request-content-section approval-section">
            <h2>상세 내용</h2>
            <p id="approval-description" class="notice-body"></p>
        </section>

        <section class="panel section-gap request-sql-panel approval-section">
            <div class="panel-heading request-sql-heading">
                <h2 id="approval-sql-heading">첨부 SQL</h2>
            </div>
            <div id="approval-sql-list" class="approval-sql-list request-sql-list">
                <p class="caption">SQL을 불러오는 중입니다.</p>
            </div>
        </section>

        <footer class="request-document-footer approval-reject">
            <label for="approval-reject-comment">반려 의견</label>
            <textarea id="approval-reject-comment"
                      rows="2"
                      maxlength="2000"
                      placeholder="반려 사유를 입력하세요."></textarea>

            <div class="request-document-actions approval-detail-actions">
                <button id="reject-request"
                        type="button"
                        class="button"
                        disabled>반려</button>
                <button id="approve-request"
                        type="button"
                        class="button primary"
                        disabled>승인</button>
            </div>
        </footer>
    </article>
</main>

<script>
    (function () {
        'use strict';

        const requestId = $('#approval-config').dataset.requestId;
        const PAGE_SIZE = 3;
        let approvalHistory = [];
        let approvalPage = 0;

        const approvalTypeNames = {
            APPROVER: '중간 결재',
            INTERMEDIATE: '중간 결재',
            DIRECTOR: '부서장 결재',
            DB_EXECUTION: 'DB팀 결재'
        };

        const approvalStatusNames = {
            PENDING: '결재 대기',
            WAITING: '결재 대기',
            APPROVED: '승인',
            REJECTED: '반려',
            CANCELED: '취소'
        };

        function formatDateTime(value) {
            if (!value) {
                return '-';
            }

            const parsed = new Date(value);
            if (Number.isNaN(parsed.getTime())) {
                return '-';
            }

            return new Intl.DateTimeFormat('ko-KR', {
                timeZone: 'Asia/Seoul',
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                hour12: false
            }).format(parsed);
        }

        function renderApprovalLine() {
            const totalPages = Math.max(
                1,
                Math.ceil(approvalHistory.length / PAGE_SIZE)
            );

            if (approvalPage >= totalPages) {
                approvalPage = totalPages - 1;
            }

            const startIndex = approvalPage * PAGE_SIZE;
            const visibleApprovals = approvalHistory.slice(
                startIndex,
                startIndex + PAGE_SIZE
            );

            $('#approval-line-range').textContent =
                approvalHistory.length === 0
                    ? '0 / 0'
                    : (startIndex + 1) + '–'
                        + Math.min(startIndex + PAGE_SIZE, approvalHistory.length)
                        + ' / ' + approvalHistory.length;

            $('#approval-line-prev').disabled = approvalPage === 0;
            $('#approval-line-next').disabled = approvalPage >= totalPages - 1;

            if (!visibleApprovals.length) {
                $('#approval-line').innerHTML =
                    '<p class="caption">등록된 결재선이 없습니다.</p>';
                return;
            }

            $('#approval-line').innerHTML = visibleApprovals
                .map(function (approval) {
                    const status = String(approval.status || '').toUpperCase();
                    const isCurrent = status === 'PENDING';
                    const isDepartment = approval.targetType === 'DEPARTMENT';
                    const typeName = isDepartment ? '부서 결재'
                        : approvalTypeNames[approval.approvalType] || '결재';
                    const personName = isDepartment
                        ? [approval.approverDeptName || approval.deptName || '부서 정보 없음', approval.approverName]
                            .filter(Boolean).join(' · ')
                        : approval.approverName || '-';
                    const statusName = approvalStatusNames[status]
                        || approval.status
                        || '결재 대기';

                    return '<article class="approval-step'
                        + (isCurrent ? ' is-current' : '') + '">'
                        + '<strong>' + esc(typeName) + '</strong>'
                        + '<span class="approval-step-name">'
                        + esc(personName)
                        + '</span>'
                        + '<span>' + esc(statusName) + '</span>'
                        + '</article>';
                }).join('');
        }

        function renderSqlItems(items) {
            if (!items || items.length === 0) {
                $('#approval-sql-heading').textContent = '첨부 SQL · 0건';
                $('#approval-sql-list').innerHTML =
                    '<p class="approval-empty">등록된 SQL이 없습니다.</p>';
                return;
            }

            $('#approval-sql-heading').textContent =
                '첨부 SQL · ' + items.length + '건';

            $('#approval-sql-list').innerHTML = items
                .map(function (item, index) {
                    const order = item.queryOrder || index + 1;
                    const title = item.queryTitle || '제목 없음';

                    return '<article class="request-sql-item approval-sql-item">'
                        + '<details class="request-sql-accordion"'
                        + (index === 0 ? ' open' : '') + '>'
                        + '<summary><span class="request-sql-summary-title">'
                        + 'SQL ' + esc(String(order).padStart(2, '0'))
                        + ' · ' + esc(title)
                        + '</span><span class="request-sql-validation-state is-valid">'
                        + '검증 완료</span></summary>'
                        + '<div class="request-sql-body"><pre><code>'
                        + esc(item.sqlText || '')
                        + '</code></pre></div></details></article>';
                }).join('');
        }

        async function loadApprovalDetail() {
            try {
                const [request, history] = await Promise.all([
                    api('/approvals/' + encodeURIComponent(requestId)),
                    api('/approvals/' + encodeURIComponent(requestId) + '/history')
                ]);

                approvalHistory = Array.isArray(history) ? history : [];

                $('#approval-request-no').textContent =
                    request.requestNo || '-';
                $('#approval-department').textContent =
                    request.deptName || '-';
                $('#approval-drafter').textContent =
                    request.drafterName || '-';
                $('#approval-created-at').textContent =
                    formatDateTime(request.createdAt);
                $('#approval-desired-date').textContent =
                    request.desiredDate || '-';
                $('#approval-title').textContent =
                    request.title || '제목 없음';
                $('#approval-purpose').textContent =
                    request.purpose || '요청 목적 없음';
                $('#approval-description').textContent =
                    request.detailContent || '상세 내용 없음';

                renderSqlItems(request.items || []);
                renderApprovalLine();

                $('#approve-request').disabled = false;
                $('#reject-request').disabled = false;
            } catch (error) {
                $('#approval-title').textContent = '요청을 열 수 없습니다.';
                $('#approval-sql-list').textContent = error.message;
                $('#approval-line').textContent = '결재선을 불러오지 못했습니다.';
                notify(error.message, true);
            }
        }

        $('#approval-line-prev').addEventListener('click', function () {
            if (approvalPage > 0) {
                approvalPage -= 1;
                renderApprovalLine();
            }
        });

        $('#approval-line-next').addEventListener('click', function () {
            const totalPages = Math.ceil(approvalHistory.length / PAGE_SIZE);
            if (approvalPage < totalPages - 1) {
                approvalPage += 1;
                renderApprovalLine();
            }
        });

        $('#approve-request').addEventListener('click', function () {
            busy(this, async function () {
                await api(
                    '/approvals/' + encodeURIComponent(requestId) + '/approve',
                    { method: 'POST' }
                );
                window.location.href = BASE + '/approvals/pending';
            });
        });

        $('#reject-request').addEventListener('click', function () {
            const commentText =
                $('#approval-reject-comment').value.trim();

            if (!commentText) {
                notify('반려 의견을 입력하세요.', true);
                $('#approval-reject-comment').focus();
                return;
            }

            busy(this, async function () {
                await post(
                    '/approvals/' + encodeURIComponent(requestId) + '/reject',
                    { commentText: commentText }
                );
                window.location.href = BASE + '/approvals/pending';
            });
        });

        loadApprovalDetail();
    })();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>
