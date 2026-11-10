<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<main class="execution-queue-page">
    <div class="page-heading">
        <div>
            <h1>실행 목록</h1>
        </div>
    </div>

    <section class="panel execution-queue-panel" aria-label="실행 요청 목록">
        <form id="queue-search" class="queue-search">
            <label class="queue-keyword-field" for="queue-keyword">
                <span class="sr-only">요청 검색</span>
                <input
                    id="queue-keyword"
                    name="keyword"
                    type="search"
                    placeholder="요청번호, 제목, 작성자"
                    maxlength="120"
                    autocomplete="off">
            </label>

            <button
                id="queue-search-button"
                class="button primary"
                type="submit">
                검색
            </button>

            <button
                id="queue-refresh"
                class="button"
                type="button">
                새로고침
            </button>
        </form>

        <div class="queue-table-heading">
            <span id="queue-total-count" class="queue-total-count" aria-live="polite">
                총 0건
            </span>
        </div>

        <div class="table-scroll">
            <table class="queue-table">
                <thead>
                    <tr>
                        <th scope="col">요청번호</th>
                        <th scope="col">제목</th>
                        <th scope="col">부서</th>
                        <th scope="col">작성자</th>
                        <th scope="col">SQL 수</th>
                        <th scope="col">상태</th>
                        <th scope="col">요청일</th>
                    </tr>
                </thead>
                <tbody id="queue-rows">
                    <tr>
                        <td colspan="7" class="empty">
                            실행 목록을 불러오는 중입니다.
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>

        <nav
            id="queue-pagination"
            class="queue-pagination"
            aria-label="실행 목록 페이지 이동">
        </nav>
    </section>
</main>

<script>
    (function () {
        const pageSize = 10;
        const pageBlockSize = 10;

        let currentPage = 1;
        let totalPages = 0;
        let totalCount = 0;
        let keyword = '';
        let loading = false;
        let requestVersion = 0;

        const $id = function (id) {
            return document.getElementById(id);
        };

        function escapeHtml(value) {
            return esc(value == null ? '' : String(value));
        }

        function formatRequestDate(value) {
            if (!value) {
                return '—';
            }

            const date = new Date(value);

            if (Number.isNaN(date.getTime())) {
                return '—';
            }

            return new Intl.DateTimeFormat('ko-KR', {
                timeZone: 'Asia/Seoul',
                year: 'numeric',
                month: '2-digit',
                day: '2-digit'
            }).format(date).replace(/\. /g, '.').replace(/\.$/, '');
        }

        function getStatus(row) {
            if (row.batchStatus === 'RUNNING'
                || row.requestStatus === 'RUNNING') {
                return { label: '실행 중', kind: 'waiting' };
            }

            if (row.batchStatus === 'SUCCESS') {
                return { label: '완료', kind: 'success' };
            }

            if (row.batchStatus === 'FAILED') {
                return { label: '실패', kind: 'danger' };
            }

            if (row.batchId) {
                return { label: '개별 실행 가능', kind: 'neutral' };
            }

            return { label: '실행 대기', kind: 'neutral' };
        }

        function showEmptyMessage(message) {
            $id('queue-rows').innerHTML =
                '<tr><td colspan="7" class="empty">'
                + escapeHtml(message)
                + '</td></tr>';
        }

        function getPageBlock() {
            const blockStart =
                Math.floor((currentPage - 1) / pageBlockSize) * pageBlockSize + 1;
            const blockEnd = Math.min(
                blockStart + pageBlockSize - 1,
                totalPages
            );

            return {
                start: blockStart,
                end: blockEnd
            };
        }

        function renderPagination() {
            const pagination = $id('queue-pagination');

            if (totalPages <= 0) {
                pagination.innerHTML = '';
                return;
            }

            const block = getPageBlock();
            let pageButtons = '';

            for (let page = block.start; page <= block.end; page += 1) {
                const isCurrent = page === currentPage;

                pageButtons +=
                    '<button type="button"'
                    + ' class="queue-page-button'
                    + (isCurrent ? ' is-current' : '')
                    + '" data-page="' + page + '"'
                    + (isCurrent ? ' aria-current="page"' : '')
                    + ' aria-label="' + page + '페이지">'
                    + page
                    + '</button>';
            }

            const previousDisabled = loading || currentPage <= 1;
            const nextDisabled = loading || currentPage >= totalPages;

            pagination.innerHTML =
                '<div class="queue-pagination-controls">'
                + '<button type="button"'
                + ' class="queue-page-move" data-action="previous"'
                + (previousDisabled ? ' disabled' : '')
                + ' aria-label="이전 페이지">'
                + '‹ 이전'
                + '</button>'
                + '<div class="queue-page-numbers">'
                + pageButtons
                + '</div>'
                + '<button type="button"'
                + ' class="queue-page-move" data-action="next"'
                + (nextDisabled ? ' disabled' : '')
                + ' aria-label="다음 페이지">'
                + '다음 ›'
                + '</button>'
                + '</div>';
        }

        function updateControls() {
            $id('queue-keyword').disabled = loading;
            $id('queue-search-button').disabled = loading;
            $id('queue-refresh').disabled = loading;
            renderPagination();
        }

        function renderRows(items) {
            if (!items.length) {
                showEmptyMessage(
                    keyword
                        ? '검색 조건에 맞는 실행 요청이 없습니다.'
                        : '실행 대기 중인 요청이 없습니다.'
                );
                return;
            }

            $id('queue-rows').innerHTML = items.map(function (row) {
                const detailUrl = BASE + '/execution-queue/'
                    + encodeURIComponent(row.requestId);
                const status = getStatus(row);

                return '<tr>'
                    + '<td>'
                    + '<a class="queue-request-link" href="'
                    + escapeHtml(detailUrl) + '">'
                    + escapeHtml(row.requestNo || '—')
                    + '</a>'
                    + '</td>'
                    + '<td>'
                    + '<a class="queue-title-link" href="'
                    + escapeHtml(detailUrl) + '">'
                    + escapeHtml(row.title || '제목 없음')
                    + '</a>'
                    + '</td>'
                    + '<td>' + escapeHtml(row.deptName || '—') + '</td>'
                    + '<td>' + escapeHtml(row.drafterName || '—') + '</td>'
                    + '<td class="numeric">'
                    + escapeHtml(row.queryCount == null ? 0 : row.queryCount)
                    + '</td>'
                    + '<td>' + badge(status.label, status.kind) + '</td>'
                    + '<td>' + escapeHtml(formatRequestDate(row.submittedAt)) + '</td>'
                    + '</tr>';
            }).join('');
        }

        async function load(page) {
            if (loading) {
                return;
            }

            const version = ++requestVersion;
            currentPage = page;
            loading = true;
            updateControls();

            $id('queue-rows').setAttribute('aria-busy', 'true');
            showEmptyMessage('실행 목록을 불러오는 중입니다.');

            try {
                const params = new URLSearchParams({
                    page: String(currentPage),
                    size: String(pageSize),
                    keyword: keyword
                });

                const data = await api(
                    '/executions/ready?' + params.toString()
                );

                if (version !== requestVersion) {
                    return;
                }

                if (!data || !Array.isArray(data.items)) {
                    throw new Error('실행 목록 응답을 확인할 수 없습니다.');
                }

                totalPages = Number(data.totalPages) || 0;
                totalCount = Number(data.totalCount) || 0;

                if (totalPages > 0 && currentPage > totalPages) {
                    loading = false;
                    await load(totalPages);
                    return;
                }

                if (totalPages === 0) {
                    currentPage = 1;
                }

                $id('queue-total-count').textContent =
                    '총 ' + totalCount + '건';

                renderRows(data.items);
            } catch (error) {
                if (version !== requestVersion) {
                    return;
                }

                totalPages = 0;
                totalCount = 0;
                $id('queue-total-count').textContent = '총 0건';
                showEmptyMessage('실행 목록을 불러오지 못했습니다.');
                notify(error.message || '실행 목록을 불러오지 못했습니다.', true);
            } finally {
                if (version === requestVersion) {
                    loading = false;
                    $id('queue-rows').setAttribute('aria-busy', 'false');
                    updateControls();
                }
            }
        }

        $id('queue-search').addEventListener('submit', function (event) {
            event.preventDefault();

            if (loading) {
                return;
            }

            keyword = $id('queue-keyword').value.trim();
            load(1);
        });

        $id('queue-refresh').addEventListener('click', function () {
            if (loading) {
                return;
            }

            $id('queue-search').reset();
            keyword = '';
            load(1);
        });

        $id('queue-pagination').addEventListener('click', function (event) {
            const button = event.target.closest('button');

            if (!button || button.disabled || loading) {
                return;
            }

            const action = button.dataset.action;
            const selectedPage = Number(button.dataset.page);

            if (action === 'previous' && currentPage > 1) {
                load(currentPage - 1);
            } else if (action === 'next' && currentPage < totalPages) {
                load(currentPage + 1);
            } else if (
                Number.isInteger(selectedPage)
                && selectedPage >= 1
                && selectedPage <= totalPages
                && selectedPage !== currentPage
            ) {
                load(selectedPage);
            }
        });

        updateControls();
        load(1);
    })();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>