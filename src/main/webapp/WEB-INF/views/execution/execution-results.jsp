<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="execution-results-page">
    <div class="page-heading request-inbox-heading">
        <h1><c:out value="${pageTitle}"/></h1>
        <button type="button"
                class="button refresh-button"
                id="refresh-button"
                title="현재 페이지 새로고침"
                aria-label="현재 페이지 새로고침">
            <img src="${pageContext.request.contextPath}/icons/RefreshCw.svg" alt="">
        </button>
    </div>

    <section id="execution-result-list" class="panel">
        <form id="search-form" class="filter-bar results-filter">
            <label for="status-filter">
                SQL 실행 상태
                <select id="status-filter" name="status">
                    <option value="">전체</option>
                    <option value="SUCCEEDED">성공</option>
                    <option value="FAILED">실패</option>
                    <option value="RUNNING">실행 중</option>
                    <option value="PENDING">실행 대기</option>
                </select>
            </label>

            <label for="keyword-filter">
                요청 검색
                <input type="search" id="keyword-filter" name="keyword"
                       placeholder="요청번호, 요청명, SQL 제목, 부서명, 작성자 검색" maxlength="200">
            </label>

            <button type="submit" class="button primary">검색</button>
            <button type="button"
                    class="button refresh-button"
                    id="reset-button"
                    title="검색 조건 초기화 및 새로고침"
                    aria-label="검색 조건 초기화 및 새로고침">
                <img src="${pageContext.request.contextPath}/icons/RefreshCw.svg" alt="">
            </button>
        </form>

        <div id="error-message" role="alert" hidden></div>

        <div class="panel-heading request-list-heading">
            <h2>실행 결과 목록</h2>
            <span id="total-count" class="caption request-list-count" aria-live="polite">총 0건</span>
        </div>

        <div class="table-scroll">
            <table class="request-list-table">
                <caption class="sr-only">쿼리 실행 결과 목록</caption>
                <colgroup>
                    <col class="request-number-column">
                    <col class="result-request-title-column">
                    <col class="result-sql-title-column">
                    <col class="result-department-column">
                    <col class="result-drafter-column">
                    <col class="result-status-column">
                    <col class="result-date-column">
                </colgroup>
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
                    <tr><td colspan="7" class="empty">불러오는 중입니다.</td></tr>
                </tbody>
            </table>
        </div>

        <nav id="pagination" class="request-pagination" aria-label="실행 결과 페이지 이동"></nav>
    </section>
</div>

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
							SUCCEEDED: ["성공", "is-completed"],
							SUCCESS: ["성공", "is-completed"],
							FAILED: ["실패", "is-failed"],
							RUNNING: ["실행 중", "is-running"],
							PENDING: ["실행 대기", "is-ready"],
							QUEUED: ["실행 대기", "is-ready"]
						};

						return labels[status] || [status || "상태 미상", "is-draft"];
					}

					function resultDetailUrl(row) {
						return "/execution-results/" + encodeURIComponent(row.executionId);
					}

					function renderRows(items) {
						if (!Array.isArray(items) || items.length === 0) {
							resultsBody.innerHTML =
								'<tr><td colspan="7" class="empty">조회된 실행 결과가 없습니다.</td></tr>';
							return;
						}

						resultsBody.innerHTML = items.map(function (row) {
							const state = statusLabel(row.status);

							return `
		            <tr>
		                <td class="request-number-cell" title="\${escapeHtml(row.requestNo || '')}">
		                    \${escapeHtml(row.requestNo || "—")}
		                </td>
		                <td>
		                    <span class="result-request-title" title="\${escapeHtml(row.requestTitle || '')}">
		                        \${escapeHtml(row.requestTitle || "제목 없음")}
		                    </span>
		                </td>
		                <td>
		                    <a class="request-title-link" href="\${resultDetailUrl(row)}"
		                       title="\${escapeHtml(row.queryTitle || 'SQL')} 실행 결과 보기">
		                        \${escapeHtml(row.queryTitle || "SQL")}
		                    </a>
		                </td>
		                <td class="request-department-cell" title="\${escapeHtml(row.departmentName || '')}">\${escapeHtml(row.departmentName || "—")}</td>
		                <td>\${escapeHtml(row.drafterName || "—")}</td>
		                <td>
		                    <span class="request-status-text \${state[1]}">
		                        \${escapeHtml(state[0])}
		                    </span>
		                </td>
		                <td class="request-date-cell">\${formatDate(row.resultCreatedAt)}</td>
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
								+ ' class="request-page-number'
								+ (isCurrent ? ' is-current' : '')
								+ '" data-page="' + page + '"'
								+ (isCurrent ? ' aria-current="page"' : '')
								+ ' aria-label="' + page + '페이지">'
								+ page
								+ '</button>';
						}

						pagination.innerHTML =
							'<button type="button" class="request-page-direction"'
							+ ' data-action="previous"'
							+ (loading || currentPage <= 1 ? ' disabled' : '')
							+ ' aria-label="이전 페이지">‹ 이전</button>'
							+ '<div class="request-page-numbers">' + pageButtons + '</div>'
							+ '<button type="button" class="request-page-direction"'
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
							'<tr><td colspan="7" class="empty">불러오는 중입니다.</td></tr>';
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
									headers: {"Accept": "application/json"},
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
								'<tr><td colspan="7" class="empty">목록을 불러오지 못했습니다.</td></tr>';
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