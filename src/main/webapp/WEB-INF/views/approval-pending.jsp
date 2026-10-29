<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div class="page-heading">
    <div>
        <h1>결재 대기 목록</h1>
        <p class="caption">현재 로그인한 사용자에게 배정된 결재 요청입니다.</p>
    </div>
    <button id="approval-refresh" type="button" class="button">새로고침</button>
</div>

<section class="panel">
    <div class="table-scroll">
        <table>
            <thead>
                <tr>
                    <th>요청명 / 요청번호</th>
                    <th>요청 부서 / 작성자</th>
                    <th>결재 단계</th>
                    <th>제출 시각</th>
                    <th>검토</th>
                </tr>
            </thead>
            <tbody id="approval-rows">
                <tr><td colspan="5" class="empty">결재 목록을 불러오는 중입니다.</td></tr>
            </tbody>
        </table>
    </div>
</section>

<script>
    function approvalOrderName(order) {
        return order === 1 ? '부서장 결재' : order === 2 ? 'DB팀 최종 결재' : '결재';
    }

    function renderPendingApprovals(items) {
        if (!items.length) {
            return '<tr><td colspan="5" class="empty">현재 결재 대기 중인 요청이 없습니다.</td></tr>';
        }

        return items.map(function (item) {
            const detailUrl = BASE + '/approvals/' + encodeURIComponent(item.requestId);
            return '<tr>'
                + '<td><a class="table-title" href="' + detailUrl + '">' + esc(item.title) + '</a>'
                + '<span class="secondary">' + esc(item.requestNo) + '</span>'
                + (item.requestType === 'CREATE_VIEW'
                    ? '<span class="secondary">뷰 생성 요청</span>' : '')
                + '</td>'
                + '<td>' + esc(item.deptName) + '<span class="secondary">' + esc(item.drafterName) + '</span></td>'
                + '<td>' + esc(approvalOrderName(item.approvalOrder)) + '</td>'
                + '<td>' + esc(date(item.submittedAt)) + '</td>'
                + '<td><a class="button" href="' + detailUrl + '">SQL 검토</a></td>'
                + '</tr>';
        }).join('');
    }

    async function loadPendingApprovals() {
        try {
            const items = await api('/approvals/pending');
            $('#approval-rows').innerHTML = renderPendingApprovals(items);
        } catch (error) {
            $('#approval-rows').innerHTML = '<tr><td colspan="5" class="empty">결재 목록을 불러오지 못했습니다.</td></tr>';
            notify(error.message, true);
        }
    }

    $('#approval-refresh').addEventListener('click', function () {
        busy(this, loadPendingApprovals);
    });
    loadPendingApprovals();
</script>

<%@ include file="fragments/bottom.jspf" %>
