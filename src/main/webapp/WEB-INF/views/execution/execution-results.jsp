<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:set var="pageTitle" value="실행 결과" />
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<main class="page-content execution-results-page">
    <section class="page-heading">
        <div>
            <h1>실행 결과</h1>
            <p>SQL 항목별 최신 실행 이력과 결과를 확인하세요.</p>
        </div>

        <div class="heading-actions">
            <span class="scope-badge">조회 범위 · 열람 가능 요청</span>
            <button type="button" class="button button-secondary" id="refresh-button">
                새로고침
            </button>
        </div>
    </section>

    <section class="results-panel">
        <form id="search-form" class="results-filter">
            <div class="filter-field status-field">
                <label for="status-filter">SQL 실행 상태</label>
                <select id="status-filter" name="status">
                    <option value="">전체</option>
                    <option value="SUCCEEDED">성공</option>
                    <option value="FAILED">실패</option>
                    <option value="RUNNING">실행 중</option>
                    <option value="PENDING">실행 대기</option>
                </select>
            </div>

            <div class="filter-field keyword-field">
                <label for="keyword-filter">요청 검색</label>
                <input
                    type="search"
                    id="keyword-filter"
                    name="keyword"
                    placeholder="요청번호, 요청명, SQL 제목, 부서명, 작성자 검색"
                    maxlength="200">
            </div>

            <div class="filter-actions">
                <button type="submit" class="button button-primary">검색</button>
                <button type="button" class="button button-secondary" id="reset-button">
                    초기화
                </button>
            </div>
        </form>

        <div id="error-message" class="alert alert-error" role="alert" hidden></div>

        <div class="results-count-row">
            <p id="total-count" class="total-count">총 0건</p>
        </div>

        <div class="table-scroll">
            <table class="results-table">
                <thead>
                    <tr>
                        <th scope="col">요청번호</th>
                        <th scope="col">요청명</th>
                        <th scope="col">SQL 제목</th>
                        <th scope="col">부서</th>
                        <th scope="col">작성자</th>
                        <th scope="col">실행 상태</th>
                        <th scope="col">결과 등록일</th>
                    </tr>
                </thead>
                <tbody id="results-body">
                    <tr>
                        <td colspan="7" class="table-message">불러오는 중입니다.</td>
                    </tr>
                </tbody>
            </table>
        </div>

        <footer class="table-footer">
            <nav id="pagination" class="pagination" aria-label="실행 결과 페이지 이동"></nav>
        </footer>
    </section>
</main>

<style>
    .execution-results-page {
        display: flex;
        flex-direction: column;
        gap: 28px;
    }

    .page-heading {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 24px;
    }

    .page-heading h1 {
        margin: 0;
        color: #203746;
        font-size: 30px;
        font-weight: 700;
        line-height: 1.3;
    }

    .page-heading p {
        margin: 8px 0 0;
        color: #607789;
        font-size: 15px;
    }

    .heading-actions {
        display: flex;
        align-items: center;
        gap: 12px;
    }

    .scope-badge {
        display: inline-flex;
        align-items: center;
        min-height: 38px;
        padding: 0 18px;
        border-radius: 6px;
        background: #e2f0f4;
        color: #276b84;
        font-size: 14px;
        white-space: nowrap;
    }

    .results-panel {
        padding: 0 0 8px;
        background: #fff;
    }

    .results-filter {
        display: grid;
        grid-template-columns: minmax(180px, 245px) minmax(280px, 1fr) auto;
        align-items: end;
        gap: 16px;
        padding: 16px;
        border-top: 1px solid #d4e0e7;
        border-bottom: 1px solid #d4e0e7;
    }

    .filter-field {
        display: flex;
        flex-direction: column;
        gap: 8px;
        min-width: 0;
    }

    .filter-field label {
        color: #536c7e;
        font-size: 14px;
    }

    .filter-field input,
    .filter-field select {
        box-sizing: border-box;
        width: 100%;
        height: 42px;
        padding: 0 12px;
        border: 1px solid #cbd9e2;
        border-radius: 6px;
        background: #fff;
        color: #203746;
        font: inherit;
    }

    .filter-field input:focus,
    .filter-field select:focus {
        border-color: #276b84;
        outline: 3px solid rgb(39 107 132 / 12%);
    }

    .filter-actions {
        display: flex;
        gap: 10px;
    }

    .button {
        min-height: 42px;
        padding: 0 20px;
        border: 1px solid #cbd9e2;
        border-radius: 6px;
        background: #fff;
        color: #203746;
        font: inherit;
        cursor: pointer;
        white-space: nowrap;
    }

    .button-primary {
        border-color: #21627b;
        background: #21627b;
        color: #fff;
    }

    .button:hover:not(:disabled) {
        filter: brightness(.97);
    }

    .button:disabled {
        cursor: not-allowed;
        opacity: .5;
    }

    .alert {
        margin: 16px;
        padding: 12px 16px;
        border-radius: 6px;
    }

    .alert-error {
        border: 1px solid #f0c8c5;
        background: #fff2f1;
        color: #a8322b;
    }

    .results-count-row {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        padding: 14px 16px 0;
    }

    .total-count {
        margin: 0;
        color: #607789;
        font-size: 14px;
        text-align: right;
    }

    .table-scroll {
        overflow-x: auto;
        padding: 12px 0 0;
    }

    .results-table {
        width: 100%;
        border-collapse: collapse;
        table-layout: fixed;
    }

    .results-table th,
    .results-table td {
        padding: 16px;
        border-bottom: 1px solid #d8e1e7;
        text-align: left;
        vertical-align: middle;
    }

    .results-table th {
        background: #eef3f6;
        color: #536c7e;
        font-size: 14px;
        font-weight: 500;
    }

    .results-table td {
        color: #203746;
        font-size: 15px;
    }

    .results-table th:nth-child(1) { width: 13%; }
    .results-table th:nth-child(2) { width: 18%; }
    .results-table th:nth-child(3) { width: 18%; }
    .results-table th:nth-child(4) { width: 14%; }
    .results-table th:nth-child(5) { width: 12%; }
    .results-table th:nth-child(6) { width: 12%; }
    .results-table th:nth-child(7) { width: 13%; }

    .request-link,
    .query-link {
        color: #176b8c;
        text-decoration: none;
    }

    .request-link {
        font-weight: 700;
    }

    .request-link:hover,
    .query-link:hover {
        text-decoration: underline;
    }

    .status {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        white-space: nowrap;
    }

    .status::before {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        background: #8c9aa3;
        content: "";
    }

    .status-success {
        color: #216b50;
    }

    .status-success::before {
        background: #216b50;
    }

    .status-failed {
        color: #ae3434;
    }

    .status-failed::before {
        background: #ae3434;
    }

    .status-running {
        color: #9a6a12;
    }

    .status-running::before {
        background: #d39420;
    }

    .status-pending {
        color: #637587;
    }

    .table-message {
        height: 120px;
        color: #607789 !important;
        text-align: center !important;
    }

    .table-footer {
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: 52px;
        padding: 14px 16px 0;
    }

    /* 중앙 숫자 페이징 */
    .pagination {
        position: absolute;
        left: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 12px;
        transform: translateX(-50%);
    }

    .page-move,
    .page-number {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        height: 40px;
        padding: 0 8px;
        border: 0;
        border-radius: 5px;
        background: transparent;
        color: #536c7e;
        font: inherit;
        cursor: pointer;
    }

    .page-move {
        min-width: auto;
        gap: 4px;
    }

    .page-numbers {
        display: flex;
        align-items: center;
        gap: 4px;
    }

    .page-number {
        min-width: 40px;
    }

    .page-move:not(:disabled):hover,
    .page-number:hover:not(.is-current) {
        background: #f1f4f6;
    }

    .page-number.is-current {
        background: #e2f0f4;
        color: #21627b;
        font-weight: 700;
    }

    .page-move:disabled {
        color: #b9c2c9;
        cursor: not-allowed;
    }

    .page-move:focus-visible,
    .page-number:focus-visible {
        outline: 2px solid #276b84;
        outline-offset: 2px;
    }

    @media (max-width: 900px) {
        .results-filter {
            grid-template-columns: 1fr 1fr;
        }

        .keyword-field {
            grid-column: 1 / -1;
        }

        .filter-actions {
            justify-content: flex-end;
        }

        .results-table {
            min-width: 920px;
        }
    }

    @media (max-width: 600px) {
        .page-heading,
        .heading-actions {
            align-items: stretch;
            flex-direction: column;
        }

        .results-filter {
            grid-template-columns: 1fr;
        }

        .keyword-field {
            grid-column: auto;
        }

        .filter-actions {
            justify-content: flex-start;
        }

        .table-footer {
            min-height: auto;
            padding-bottom: 8px;
        }

        .pagination {
            position: static;
            max-width: 100%;
            flex-wrap: wrap;
            transform: none;
        }

        .page-numbers {
            flex-wrap: wrap;
        }

        .page-number {
            min-width: 36px;
        }
    }
</style>

<script>
    (function () {
        "use strict";

        const PAGE_SIZE = 10;
        const PAGE_BLOCK_SIZE = 10;
        const params = new URLSearchParams(window.location.search);

        let currentPage = Math.max(1, Number(params.get("page")) || 1);
        let totalPages = 0;
        let totalCountValue = 0;
        let loading = false;
        let requestVersion = 0;

        const statusFilter = document.getElementById("status-filter");
        const keywordFilter = document.getElementById("keyword-filter");
        const resultsBody = document.getElementById("results-body");
        const totalCount = document.getElementById("total-count");
        const pagination = document.getElementById("pagination");
        const errorMessage = document.getElementById("error-message");

        statusFilter.value = params.get("status") || "";
        keywordFilter.value = params.get("keyword") || "";

        function escapeHtml(value) {
            return String(value == null ? "" : value)
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll('"', "&quot;")
                .replaceAll("'", "&#39;");
        }

        function formatDate(value) {
            if (!value) {
                return "—";
            }

            const normalized = String(value).replace(" ", "T");
            const date = new Date(normalized);

            if (Number.isNaN(date.getTime())) {
                return escapeHtml(value);
            }

            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, "0");
            const day = String(date.getDate()).padStart(2, "0");

            return year + "." + month + "." + day;
        }

        function statusLabel(status) {
            const labels = {
                SUCCEEDED: ["성공", "status-success"],
                SUCCESS: ["성공", "status-success"],
                FAILED: ["실패", "status-failed"],
                RUNNING: ["실행 중", "status-running"],
                PENDING: ["실행 대기", "status-pending"],
                QUEUED: ["실행 대기", "status-pending"]
            };

            return labels[status] || [status || "상태 미상", "status-pending"];
        }

        function requestDetailUrl(row) {
            return "/requests/" + encodeURIComponent(row.requestId);
        }

        function resultDetailUrl(row) {
            return "/execution-results/" + encodeURIComponent(row.executionId);
        }

        function renderRows(items) {
            if (!Array.isArray(items) || items.length === 0) {
                resultsBody.innerHTML =
                    '<tr><td colspan="7" class="table-message">조회된 실행 결과가 없습니다.</td></tr>';
                return;
            }

            resultsBody.innerHTML = items.map(function (row) {
                const state = statusLabel(row.status);

                return `
                    <tr>
                        <td>
                            <a class="query-link" href="${requestDetailUrl(row)}">
                                ${escapeHtml(row.requestNo || "—")}
                            </a>
                        </td>
                        <td>
                            <a class="request-link" href="${requestDetailUrl(row)}">
                                ${escapeHtml(row.requestTitle || "제목 없음")}
                            </a>
                        </td>
                        <td>
                            <a class="query-link" href="${resultDetailUrl(row)}">
                                ${escapeHtml(row.queryTitle || "SQL")}
                            </a>
                        </td>
                        <td>${escapeHtml(row.departmentName || "—")}</td>
                        <td>${escapeHtml(row.drafterName || "—")}</td>
                        <td>
                            <span class="status ${state[1]}">
                                ${escapeHtml(state[0])}
                            </span>
                        </td>
                        <td>${formatDate(row.resultCreatedAt)}</td>
                    </tr>
                `;
            }).join("");
        }

        function renderPagination() {
            if (totalPages <= 0) {
                pagination.innerHTML = "";
                return;
            }

            const blockStart =
                Math.floor((currentPage - 1) / PAGE_BLOCK_SIZE) * PAGE_BLOCK_SIZE + 1;
            const blockEnd = Math.min(
                blockStart + PAGE_BLOCK_SIZE - 1,
                totalPages
            );

            let pageButtons = "";

            for (let page = blockStart; page <= blockEnd; page += 1) {
                const isCurrent = page === currentPage;

                pageButtons +=
                    '<button type="button"'
                    + ' class="page-number'
                    + (isCurrent ? ' is-current' : '')
                    + '" data-page="' + page + '"'
                    + (isCurrent ? ' aria-current="page"' : '')
                    + ' aria-label="' + page + '페이지">'
                    + page
                    + '</button>';
            }

            pagination.innerHTML =
                '<button type="button" class="page-move"'
                + ' data-action="previous"'
                + (loading || currentPage <= 1 ? ' disabled' : '')
                + ' aria-label="이전 페이지">‹ 이전</button>'
                + '<div class="page-numbers">' + pageButtons + '</div>'
                + '<button type="button" class="page-move"'
                + ' data-action="next"'
                + (loading || currentPage >= totalPages ? ' disabled' : '')
                + ' aria-label="다음 페이지">다음 ›</button>';
        }

        function updateUrl() {
            const nextParams = new URLSearchParams();
            nextParams.set("page", String(currentPage));

            if (statusFilter.value) {
                nextParams.set("status", statusFilter.value);
            }

            if (keywordFilter.value.trim()) {
                nextParams.set("keyword", keywordFilter.value.trim());
            }

            const queryString = nextParams.toString();
            window.history.replaceState(
                {},
                "",
                window.location.pathname + (queryString ? "?" + queryString : "")
            );
        }

        async function loadResults(page) {
            if (loading) {
                return;
            }

            if (Number.isInteger(page) && page >= 1) {
                currentPage = page;
            }

            const version = ++requestVersion;
            loading = true;
            errorMessage.hidden = true;
            errorMessage.textContent = "";
            resultsBody.setAttribute("aria-busy", "true");
            resultsBody.innerHTML =
                '<tr><td colspan="7" class="table-message">불러오는 중입니다.</td></tr>';
            renderPagination();

            const searchParams = new URLSearchParams({
                page: String(currentPage),
                size: String(PAGE_SIZE)
            });

            if (statusFilter.value) {
                searchParams.set("status", statusFilter.value);
            }

            if (keywordFilter.value.trim()) {
                searchParams.set("keyword", keywordFilter.value.trim());
            }

            try {
                const response = await fetch(
                    "/api/execution-results/mine?" + searchParams.toString(),
                    {
                        method: "GET",
                        headers: { "Accept": "application/json" },
                        credentials: "same-origin"
                    }
                );

                if (!response.ok) {
                    throw new Error("실행 결과를 불러오지 못했습니다.");
                }

                const data = await response.json();
                const items = data.items || data.content || [];
                totalPages = Number(data.totalPages || data.pages || 0);
                totalCountValue = Number(data.totalCount || data.totalElements || 0);

                if (totalPages > 0 && currentPage > totalPages) {
                    loading = false;
                    await loadResults(totalPages);
                    return;
                }

                if (totalPages === 0) {
                    currentPage = 1;
                } else if (data.page != null) {
                    currentPage = Number(data.page) || currentPage;
                }

                renderRows(items);
                totalCount.textContent = "총 " + totalCountValue + "건";
                updateUrl();
            } catch (error) {
                if (version !== requestVersion) {
                    return;
                }

                totalPages = 0;
                totalCountValue = 0;
                resultsBody.innerHTML =
                    '<tr><td colspan="7" class="table-message">목록을 불러오지 못했습니다.</td></tr>';
                totalCount.textContent = "총 0건";
                pagination.innerHTML = "";
                errorMessage.textContent =
                    error.message || "요청 처리 중 오류가 발생했습니다.";
                errorMessage.hidden = false;
            } finally {
                if (version === requestVersion) {
                    loading = false;
                    resultsBody.setAttribute("aria-busy", "false");
                    renderPagination();
                }
            }
        }

        document.getElementById("search-form").addEventListener("submit", function (event) {
            event.preventDefault();
            loadResults(1);
        });

        document.getElementById("reset-button").addEventListener("click", function () {
            statusFilter.value = "";
            keywordFilter.value = "";
            loadResults(1);
        });

        document.getElementById("refresh-button").addEventListener("click", function () {
            loadResults(currentPage);
        });

        pagination.addEventListener("click", function (event) {
            const button = event.target.closest("button");

            if (!button || button.disabled || loading) {
                return;
            }

            const action = button.dataset.action;
            const selectedPage = Number(button.dataset.page);

            if (action === "previous" && currentPage > 1) {
                loadResults(currentPage - 1);
            } else if (action === "next" && currentPage < totalPages) {
                loadResults(currentPage + 1);
            } else if (
                Number.isInteger(selectedPage)
                && selectedPage >= 1
                && selectedPage <= totalPages
                && selectedPage !== currentPage
            ) {
                loadResults(selectedPage);
            }
        });

        loadResults(currentPage);
    })();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>