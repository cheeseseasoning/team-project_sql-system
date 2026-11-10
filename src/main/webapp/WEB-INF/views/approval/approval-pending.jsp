<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<main class="approval-page">
    <div class="approval-heading">
        <div>
            <h1>결재 대기 목록</h1>
            <p class="caption">내게 배정된 요청을 열어 검토하세요.</p>
        </div>
        <span id="approval-count" class="approval-count">내 결재 대기 · 불러오는 중</span>
    </div>

    <form id="approval-filter" class="approval-filter">
        <div class="approval-filter-field">
            <label for="approval-keyword">요청 검색</label>
            <input
                id="approval-keyword"
                type="search"
                placeholder="제목 또는 요청번호">
        </div>

        <div class="approval-filter-field">
            <label for="approval-type">요청 유형</label>
            <select id="approval-type">
                <option value="">전체 유형</option>
                <option value="SELECT">조회 요청</option>
                <option value="CREATE_VIEW">뷰 생성 요청</option>
                <option value="INSERT">데이터 등록</option>
                <option value="UPDATE">데이터 수정</option>
                <option value="DELETE">데이터 삭제</option>
            </select>
        </div>

        <div class="approval-filter-field">
            <label for="approval-stage">결재 단계</label>
            <select id="approval-stage">
                <option value="">전체 단계</option>
                <option value="APPROVER">중간 결재</option>
                <option value="DIRECTOR">부서장 결재</option>
                <option value="DB_EXECUTION">DB팀 결재</option>
            </select>
        </div>

        <div class="approval-filter-actions">
            <button type="submit" class="button primary">검색</button>
            <button id="approval-reset" type="button" class="button">초기화</button>
        </div>
    </form>

    <section>
        <div class="approval-table-wrap">
            <table class="approval-table">
                <thead>
                    <tr>
                        <th scope="col">요청번호</th>
                        <th scope="col">요청명</th>
                        <th scope="col">유형</th>
                        <th scope="col">작성자</th>
                        <th scope="col">부서</th>
                        <th scope="col">처리 현황</th>
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

        <div class="approval-table-footer">
            <nav
                id="approval-pagination"
                class="approval-pagination"
                aria-label="결재 대기 목록 페이지">
            </nav>
        </div>
    </section>
</main>

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
        }).format(parsed).replace(/\s/g, '');
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
                    + '<td>' + esc(item.requestNo || '-') + '</td>'
                    + '<td>'
                    + '<a class="approval-request-link" href="'
                    + detailUrl + '">'
                    + esc(item.title || '제목 없음')
                    + '</a>'
                    + (item.isUrgent === 'Y'
                        ? '<span class="approval-urgent-badge">긴급</span>'
                        : '')
                    + '</td>'
                    + '<td><span class="approval-type-badge">'
                    + typeLabel + '</span></td>'
                    + '<td>' + esc(item.drafterName || '-') + '</td>'
                    + '<td>' + esc(item.deptName || '-') + '</td>'
                    + '<td>' + stageLabel + '</td>'
                    + '<td>' + esc(formatRequestDate(item.submittedAt))
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
            + ' class="approval-page-button approval-page-move"'
            + ' data-page="' + (currentPage - 1) + '"'
            + (currentPage <= 1 ? ' disabled' : '')
            + ' aria-label="이전 페이지">‹ 이전</button>'
        );

        for (
            let page = firstPageInBlock;
            page <= lastPageInBlock;
            page += 1
        ) {
            const isCurrent = page === currentPage;

            pageButtons.push(
                '<button type="button"'
                + ' class="approval-page-button'
                + (isCurrent ? ' is-current' : '')
                + '" data-page="' + page + '"'
                + (isCurrent ? ' aria-current="page"' : '')
                + ' aria-label="' + page + '페이지">'
                + page
                + '</button>'
            );
        }

        pageButtons.push(
            '<button type="button"'
            + ' class="approval-page-button approval-page-move"'
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