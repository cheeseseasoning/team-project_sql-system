<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div id="approval-config"
     data-request-id="<c:out value='${requestId}'/>"
     hidden></div>

<a class="back-link"
   href="${pageContext.request.contextPath}/approvals/pending">
    ← 결재 대기 목록
</a>

<div class="page-heading">
    <div>
        <span id="approval-request-no" class="caption"></span>
        <h1 id="approval-title">결재 요청을 불러오는 중입니다.</h1>
    </div>

    <div class="approval-actions">
        <button id="approve-request"
                type="button"
                class="button primary"
                disabled>
            승인
        </button>
        <button id="reject-request"
                type="button"
                class="button"
                disabled>
            반려
        </button>
    </div>
</div>

<section class="panel">
    <h2>요청 정보</h2>
    <dl class="key-values">
        <div>
            <dt>작성자</dt>
            <dd id="approval-drafter"></dd>
        </div>
        <div>
            <dt>요청 부서</dt>
            <dd id="approval-department"></dd>
        </div>
        <div>
            <dt>조회 목적</dt>
            <dd id="approval-purpose"></dd>
        </div>
        <div>
            <dt>상세 설명</dt>
            <dd id="approval-description"></dd>
        </div>
        <div>
            <dt>제출 시각</dt>
            <dd id="approval-submitted-at"></dd>
        </div>
        <div>
            <dt>긴급 요청</dt>
            <dd id="approval-urgent"></dd>
        </div>
    </dl>
</section>

<section class="panel section-gap">
    <h2>제출된 SQL</h2>
    <div id="approval-sql-list" class="section-gap">
        불러오는 중입니다.
    </div>
</section>

<section class="panel section-gap">
    <h2>반려 의견</h2>
    <label for="approval-reject-comment">
        반려 사유를 입력해 주세요.
    </label>
    <textarea id="approval-reject-comment"
              rows="4"
              maxlength="2000"
              placeholder="요청자에게 전달할 반려 의견을 입력하세요."></textarea>
</section>

<script>
    const approvalRequestId =
        $('#approval-config').dataset.requestId;

    async function loadApprovalDetail() {
        try {
            const request = await api(
                '/approvals/' + encodeURIComponent(approvalRequestId)
            );

            $('#approval-request-no').textContent = request.requestNo;
            $('#approval-title').textContent = request.title;
            $('#approval-drafter').textContent = request.drafterName;
            $('#approval-department').textContent = request.deptName;
            $('#approval-purpose').textContent = request.purpose;
            $('#approval-description').textContent =
                request.detailContent || '상세 설명 없음';
            $('#approval-submitted-at').textContent =
                date(request.submittedAt);
            $('#approval-urgent').textContent =
                request.isUrgent === 'Y' ? '긴급' : '일반';

            $('#approval-sql-list').innerHTML =
                request.items.map(function (item) {
                    return '<article class="result-item">'
                        + '<h3>SQL ' + esc(item.queryOrder)
                        + ' · ' + esc(item.queryTitle || '제목 없음')
                        + '</h3>'
                        + '<pre><code>' + esc(item.sqlText)
                        + '</code></pre>'
                        + '</article>';
                }).join('')
                || '<p class="empty">등록된 SQL이 없습니다.</p>';

            $('#approve-request').disabled = false;
            $('#reject-request').disabled = false;
        } catch (error) {
            $('#approval-title').textContent = '요청을 열 수 없습니다.';
            $('#approval-sql-list').textContent = error.message;
            notify(error.message, true);
        }
    }

    $('#approve-request').addEventListener('click', function () {
        busy(this, async function () {
            await api(
                '/approvals/' + encodeURIComponent(approvalRequestId) + '/approve',
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
                '/approvals/' + encodeURIComponent(approvalRequestId) + '/reject',
                { commentText: commentText }
            );

            window.location.href = BASE + '/approvals/pending';
        });
    });

    loadApprovalDetail();
</script>

<%@ include file="fragments/bottom.jspf" %>