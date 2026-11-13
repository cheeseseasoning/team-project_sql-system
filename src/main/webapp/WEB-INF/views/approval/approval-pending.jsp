<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="approval-inbox-page">
    <div class="page-heading approval-inbox-heading">
        <h1><c:out value="${pageTitle}"/></h1>
    </div>

    <section id="approval-request-list" class="panel">
        <form id="approval-filter" class="filter-bar">
            <label for="approval-keyword">
                요청 검색
                <input
                    id="approval-keyword"
                    type="search"
                    placeholder="요청번호 또는 제목">
            </label>

            <label class="request-type-filter" for="approval-type">
                요청 유형
                <select id="approval-type">
                    <option value="">전체 유형</option>
                    <option value="SELECT">조회 요청</option>
                    <option value="CREATE_VIEW">뷰 생성 요청</option>
                    <option value="INSERT">데이터 등록</option>
                    <option value="UPDATE">데이터 수정</option>
                    <option value="DELETE">데이터 삭제</option>
                </select>
            </label>

            <label for="approval-stage">
                결재 단계
                <select id="approval-stage">
                    <option value="">전체 단계</option>
                    <option value="APPROVER">중간 결재</option>
                    <option value="DIRECTOR">부서장 결재</option>
                    <option value="DB_EXECUTION">DB팀 결재</option>
                </select>
            </label>

            <button type="submit" class="button primary">검색</button>
            <button id="approval-reset"
                    type="button"
                    class="button refresh-button"
                    title="검색 조건 초기화"
                    aria-label="검색 조건 초기화">
                <img src="${pageContext.request.contextPath}/icons/RefreshCw.svg" alt="">
            </button>
        </form>

        <div class="panel-heading request-list-heading">
            <h2>결재 요청 목록</h2>
            <span id="approval-count" class="caption request-list-count" aria-live="polite">내 결재 대기 · 불러오는 중</span>
        </div>

        <div class="table-scroll">
            <table class="request-list-table">
                <caption class="sr-only">받은 결재 목록</caption>
                <colgroup>
                    <col class="request-number-column">
                    <col class="request-title-column">
                    <col class="request-type-column">
                    <col class="request-drafter-column">
                    <col class="request-department-column">
                    <col class="request-status-column">
                    <col class="request-date-column">
                </colgroup>
                <thead>
                    <tr>
                        <th scope="col">요청번호</th>
                        <th scope="col">요청명</th>
                        <th scope="col">유형</th>
                        <th scope="col">작성자</th>
                        <th scope="col">부서</th>
                        <th scope="col">처리현황</th>
                        <th scope="col">요청일</th>
                    </tr>
                </thead>
                <tbody id="approval-rows">
                    <tr>
                        <td colspan="7" class="empty">결재 목록을 불러오는 중입니다.</td>
                    </tr>
                </tbody>
            </table>
        </div>

        <nav id="approval-pagination"
             class="request-pagination"
             aria-label="받은 결재 페이지 이동">
        </nav>
    </section>
</div>

<script>
(function () {
    'use strict';

    const PAGE_SIZE = 3;
    const PAGE_BLOCK_SIZE = 10;

    let allItems = [];
    let currentPage = 1;

    const typeNames = {
        SELECT: '조회',
        CREATE_VIEW: '뷰 생성',
        INSERT: '데이터 등록',
        UPDATE: '데이터 수정',
        DELETE: '데이터 삭제'
    };

    const stageNames = {
        APPROVER: '중간 결재',
        INTERMEDIATE: '중간 결재',
        DIRECTOR: '부서장 결재',
        DB_EXECUTION: 'DB팀 결재'
    };

    function formatRequestDate(value) {
        if (!value) {
            return '-';
        }

        const parsed = new Date(value);

        if (Number.isNaN(parsed.getTime())) {
            return '-';
        }

        return new Intl.DateTimeFormat('ko-KR', {
            timeZone: 'Asia/Seoul',
            month: '2-digit',
            day: '2-digit'
        }).format(parsed).replace(/\s/g, '').replace(/\.$/, '');
    }

    function getFilteredItems() {
        const keyword =
            $('#approval-keyword').value.trim().toLocaleLowerCase();
        const type = $('#approval-type').value;
        const stage = $('#approval-stage').value;

        return allItems.filter(function (item) {
            const searchableText =
                (item.title || '') + ' ' + (item.requestNo || '');

            return (!keyword
                    || searchableText.toLocaleLowerCase().includes(keyword))
                && (!type || item.requestType === type)
                && (!stage || item.approvalType === stage);
        });
    }

    function renderRows(items) {
        if (!items.length) {
            return '<tr><td colspan="7" class="empty">'
                + '조건에 맞는 결재 요청이 없습니다.'
                + '</td></tr>';
        }

        const startIndex = (currentPage - 1) * PAGE_SIZE;

        return items.slice(startIndex, startIndex + PAGE_SIZE)
            .map(function (item) {
                const detailUrl = BASE + '/approvals/'
                    + encodeURIComponent(item.requestId);
                const typeLabel = esc(
                    typeNames[item.requestType] || '기타 요청'
                );
                const stageLabel = esc(
                    stageNames[item.approvalType] || '결재 대기'
                );

                return '<tr>'
                    + '<td class="request-number-cell" title="'
                    + esc(item.requestNo || '') + '">'
                    + esc(item.requestNo || '-') + '</td>'
                    + '<td>'
                    + '<div class="request-title-content">'
                    + (item.isUrgent === 'Y'
                        ? '<span class="badge urgent-badge">긴급</span>'
                        : '')
                    + '<a class="request-title-link" href="'
                    + detailUrl + '" title="' + esc(item.title || '') + '">'
                    + esc(item.title || '제목 없음')
                    + '</a></div>'
                    + '</td>'
                    + '<td>' + typeLabel + '</td>'
                    + '<td>' + esc(item.drafterName || '-') + '</td>'
                    + '<td class="request-department-cell" title="'
                    + esc(item.deptName || '') + '">'
                    + esc(item.deptName || '-') + '</td>'
                    + '<td><span class="request-status-text is-pending">'
                    + stageLabel + '</span></td>'
                    + '<td class="request-date-cell">'
                    + esc(formatRequestDate(item.submittedAt))
                    + '</td>'
                    + '</tr>';
            }).join('');
    }

    function renderPagination(filteredCount) {
        const totalPages = Math.max(
            1,
            Math.ceil(filteredCount / PAGE_SIZE)
        );

        if (currentPage > totalPages) {
            currentPage = totalPages;
        }

        const firstPageInBlock =
            Math.floor((currentPage - 1) / PAGE_BLOCK_SIZE)
            * PAGE_BLOCK_SIZE
            + 1;
        const lastPageInBlock = Math.min(
            firstPageInBlock + PAGE_BLOCK_SIZE - 1,
            totalPages
        );

        const pageButtons = [];

        pageButtons.push(
            '<button type="button"'
            + ' class="approval-page-button request-page-direction"'
            + ' data-page="' + (currentPage - 1) + '"'
            + (currentPage <= 1 ? ' disabled' : '')
            + ' aria-label="이전 페이지">‹ 이전</button>'
        );

        // 기안함과 같은 번호 묶음·버튼 스타일을 사용한다.
        pageButtons.push('<div class="request-page-numbers">');

        for (
            let page = firstPageInBlock;
            page <= lastPageInBlock;
            page += 1
        ) {
            const isCurrent = page === currentPage;

            pageButtons.push(
                '<button type="button"'
                + ' class="approval-page-button request-page-number'
                + (isCurrent ? ' is-current' : '')
                + '" data-page="' + page + '"'
                + (isCurrent ? ' aria-current="page"' : '')
                + ' aria-label="' + page + '페이지">'
                + page
                + '</button>'
            );
        }

        pageButtons.push('</div>');

        pageButtons.push(
            '<button type="button"'
            + ' class="approval-page-button request-page-direction"'
            + ' data-page="' + (currentPage + 1) + '"'
            + (currentPage >= totalPages ? ' disabled' : '')
            + ' aria-label="다음 페이지">다음 ›</button>'
        );

        $('#approval-pagination').innerHTML = pageButtons.join('');
    }

    function render() {
        const filtered = getFilteredItems();
        const totalPages = Math.max(
            1,
            Math.ceil(filtered.length / PAGE_SIZE)
        );

        if (currentPage > totalPages) {
            currentPage = totalPages;
        }

        $('#approval-count').textContent =
            '내 결재 대기 · ' + allItems.length + '건';
        $('#approval-rows').innerHTML = renderRows(filtered);
        renderPagination(filtered.length);
    }

    async function loadPendingApprovals() {
        $('#approval-rows').innerHTML =
            '<tr><td colspan="7" class="empty">'
            + '결재 목록을 불러오는 중입니다.'
            + '</td></tr>';

        try {
            allItems = await api('/approvals/pending');
            currentPage = 1;
            render();
        } catch (error) {
            $('#approval-count').textContent = '내 결재 대기 · -건';
            $('#approval-rows').innerHTML =
                '<tr><td colspan="7" class="empty">'
                + '결재 목록을 불러오지 못했습니다.'
                + '</td></tr>';
            $('#approval-pagination').innerHTML = '';
            notify(error.message, true);
        }
    }

    $('#approval-filter').addEventListener('submit', function (event) {
        event.preventDefault();
        currentPage = 1;
        render();
    });

    $('#approval-reset').addEventListener('click', function () {
        $('#approval-keyword').value = '';
        $('#approval-type').value = '';
        $('#approval-stage').value = '';
        currentPage = 1;
        render();
    });

    $('#approval-pagination').addEventListener('click', function (event) {
        const button = event.target.closest('button[data-page]');

        if (!button || button.disabled) {
            return;
        }

        const selectedPage = Number(button.dataset.page);

        if (!Number.isInteger(selectedPage) || selectedPage < 1) {
            return;
        }

        currentPage = selectedPage;
        render();
    });

    loadPendingApprovals();
})();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>