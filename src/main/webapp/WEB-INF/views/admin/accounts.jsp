<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="account-list-page">
    <div class="page-heading account-list-heading">
        <div>
            <h1>계정 목록</h1>
        </div>

        <c:if test="${pageContext.request.isUserInRole('SYSTEM_ADMIN')}">
            <a
                class="button primary"
                href="${pageContext.request.contextPath}/admin/users/new">
                계정 생성
            </a>
        </c:if>
    </div>

    <section id="account-list-panel" class="panel account-list-panel" aria-labelledby="account-list-title">
        <form id="account-filter-form" class="account-filter list-search-bar">
            <label for="account-keyword">
                <span class="account-search-label filter-label-text">계정 검색</span>
                <input id="account-keyword" type="search" maxlength="120"
                       placeholder="ID, 이름, 부서, 직급 또는 역할" autocomplete="off">
            </label>
            <button id="account-search" class="button primary" type="submit">검색</button>
            <button id="account-reset" class="button list-search-reset" type="button"
                    title="검색 조건 초기화 및 새로고침" aria-label="검색 조건 초기화 및 새로고침">
                <img src="${pageContext.request.contextPath}/icons/RefreshCw.svg" alt="">
            </button>
        </form>
        <div class="panel-heading account-list-toolbar request-list-heading">
            <h2 id="account-list-title">계정 목록</h2>
            <span id="account-count" class="account-count caption request-list-count" aria-live="polite"></span>
        </div>

        <div class="table-scroll">
            <table class="account-list-table request-list-table">
                <caption class="sr-only">사용자 계정 목록</caption>

                <colgroup>
                    <col class="account-employee-column">
                    <col class="account-login-column">
                    <col class="account-name-column">
                    <col class="account-department-column">
                    <col class="account-position-column">
                    <col class="account-role-column">
                    <c:if test="${pageContext.request.isUserInRole('SYSTEM_ADMIN')}">
                        <col class="account-status-column">
                    </c:if>
                </colgroup>

                <thead>
                    <tr>
                        <th scope="col">사번</th>
                        <th scope="col">ID</th>
                        <th scope="col">이름</th>
                        <th scope="col">부서</th>
                        <th scope="col">직급</th>
                        <th scope="col">역할</th>
                        <c:if test="${pageContext.request.isUserInRole('SYSTEM_ADMIN')}">
                            <th scope="col">계정 상태</th>
                        </c:if>
                    </tr>
                </thead>

                <tbody id="account-rows" aria-busy="true">
                    <tr>
                        <td
                            colspan="${pageContext.request.isUserInRole('SYSTEM_ADMIN') ? 7 : 6}"
                            class="empty">
                            계정 목록을 불러오는 중입니다.
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>

        <nav
            id="account-pagination"
            class="account-pagination request-pagination"
            aria-label="계정 목록 페이지 이동"
            hidden>
            <button
                id="account-previous-page"
                class="account-page-button request-page-direction"
                type="button"
                aria-label="이전 페이지">
                ‹ 이전
            </button>

            <div
                id="account-page-numbers"
                class="account-page-numbers request-page-numbers"
                aria-label="페이지 번호"></div>

            <button
                id="account-next-page"
                class="account-page-button request-page-direction"
                type="button"
                aria-label="다음 페이지">
                다음 ›
            </button>
        </nav>

        <p
            id="account-list-message"
            class="account-list-message caption"
            role="status"
            aria-live="polite"></p>
    </section>
</div>

<script>
(() => {
    const rowsElement = $("#account-rows");
    const countElement = $("#account-count");
    const messageElement = $("#account-list-message");
    const paginationElement = $("#account-pagination");
    const previousButton = $("#account-previous-page");
    const nextButton = $("#account-next-page");
    const pageNumbersElement = $("#account-page-numbers");

    const isSystemAdmin =
        ${pageContext.request.isUserInRole('SYSTEM_ADMIN')};

    const columnCount = isSystemAdmin ? 7 : 6;
    const pageSize = 10;
    const pageBlockSize = 10;

    let activeKeyword = "";
    let currentPage = 1;
    let totalPages = 0;
    let isLoading = false;

    function showTableMessage(text) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = columnCount;
        cell.className = "empty";
        cell.textContent = text;

        row.appendChild(cell);
        rowsElement.replaceChildren(row);
    }

    function createCell(value) {
        const cell = document.createElement("td");
        cell.textContent = value || "-";
        return cell;
    }

    function createRoleCell(roleNames) {
        const cell = document.createElement("td");
        const roles = (roleNames || "")
            .split(",")
            .map(role => role.trim())
            .filter(Boolean)
            .map(role => window.AccountRoles.label(role));

        if (roles.length === 0) {
            cell.textContent = "-";
            return cell;
        }

        const roleList = document.createElement("div");
        roleList.className = "account-role-list";

        // 목록에서는 대표 역할만 표시하고, 전체 역할은 마우스를 올려 확인한다.
        const roleItem = document.createElement("span");
        roleItem.className = "account-role-item";
        roleItem.textContent = roles[0] + (roles.length > 1 ? " 외 " + (roles.length - 1) + "개" : "");
        roleList.title = roles.join(", ");
        roleList.setAttribute("aria-label", roles.join(", "));
        roleList.appendChild(roleItem);

        cell.appendChild(roleList);
        return cell;
    }

    function renderAccounts(accounts) {
        if (accounts.length === 0) {
            showTableMessage(activeKeyword ? "검색 조건에 맞는 계정이 없습니다." : "등록된 계정이 없습니다.");
            return;
        }

        const fragment = document.createDocumentFragment();

        accounts.forEach(account => {
            const row = document.createElement("tr");
            const loginCell = document.createElement("td");

            if (isSystemAdmin) {
                const link = document.createElement("a");
                link.className = "account-login-link";
                link.href = BASE + "/admin/users/"
                    + encodeURIComponent(account.userId);
                link.textContent = account.loginId || "-";
                loginCell.appendChild(link);
            } else {
                loginCell.textContent = account.loginId || "-";
            }

            row.append(
                createCell(account.employeeNo),
                loginCell,
                createCell(account.userName),
                createCell(account.deptName),
                createCell(account.positionName),
                createRoleCell(account.roleNames)
            );

            if (isSystemAdmin) {
                const statusCell = document.createElement("td");
                const badge = document.createElement("span");
                const enabled = account.enabled === "Y";

                badge.className = enabled
                    ? "account-status-badge"
                    : "account-status-badge is-disabled";
                badge.textContent = enabled ? "활성" : "비활성";

                statusCell.appendChild(badge);
                row.appendChild(statusCell);
            }

            fragment.appendChild(row);
        });

        rowsElement.replaceChildren(fragment);
    }

    function renderPageNumbers() {
        pageNumbersElement.replaceChildren();

        if (totalPages <= 0) {
            return;
        }

        const firstPageInBlock =
            Math.floor((currentPage - 1) / pageBlockSize)
            * pageBlockSize
            + 1;

        const lastPageInBlock = Math.min(
            firstPageInBlock + pageBlockSize - 1,
            totalPages
        );

        for (
            let page = firstPageInBlock;
            page <= lastPageInBlock;
            page += 1
        ) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "account-page-button request-page-number";
            button.textContent = String(page);
            button.setAttribute("aria-label", page + "페이지");

            if (page === currentPage) {
                button.classList.add("is-current");
                button.setAttribute("aria-current", "page");
            }

            button.addEventListener("click", () => {
                if (page !== currentPage) {
                    loadAccounts(page);
                }
            });

            pageNumbersElement.appendChild(button);
        }
    }

    function renderPagination(response) {
        totalPages = Number(response.totalPages) || 0;
        currentPage = Number(response.page) || 1;

        countElement.textContent =
            "총 " + (Number(response.totalCount) || 0) + "건";

        paginationElement.hidden = totalPages === 0;

        if (totalPages === 0) {
            pageNumbersElement.replaceChildren();
            return;
        }

        previousButton.disabled = currentPage <= 1;
        nextButton.disabled = currentPage >= totalPages;

        renderPageNumbers();
    }

    async function loadAccounts(page) {
        if (isLoading) {
            return;
        }

        isLoading = true;
        $("#account-search").disabled = true;
        $("#account-reset").disabled = true;
        rowsElement.setAttribute("aria-busy", "true");
        messageElement.textContent = "";

        try {
            const response = await api(
                "/accounts?page=" + encodeURIComponent(page)
                + "&size=" + encodeURIComponent(pageSize)
                + "&keyword=" + encodeURIComponent(activeKeyword)
            );

            if (!response || !Array.isArray(response.items)) {
                throw new Error("계정 목록 응답 형식이 올바르지 않습니다.");
            }

            renderAccounts(response.items);
            renderPagination(response);
        } catch (error) {
            countElement.textContent = "";
            paginationElement.hidden = true;
            pageNumbersElement.replaceChildren();
            showTableMessage("계정 목록을 불러오지 못했습니다.");
            messageElement.textContent =
                error.message || "잠시 후 다시 시도해 주세요.";
        } finally {
            rowsElement.setAttribute("aria-busy", "false");
            isLoading = false;
            $("#account-search").disabled = false;
            $("#account-reset").disabled = false;
        }
    }

    previousButton.addEventListener("click", () => {
        if (currentPage > 1) {
            loadAccounts(currentPage - 1);
        }
    });

    nextButton.addEventListener("click", () => {
        if (currentPage < totalPages) {
            loadAccounts(currentPage + 1);
        }
    });

    // 검색 조건을 확정한 뒤 첫 페이지를 조회하고, 이후 페이지에도 유지한다.
    $("#account-filter-form").addEventListener("submit", event => {
        event.preventDefault();
        if (isLoading) return;
        activeKeyword = $("#account-keyword").value.trim();
        loadAccounts(1);
    });

    $("#account-reset").addEventListener("click", () => {
        if (isLoading) return;
        $("#account-keyword").value = "";
        activeKeyword = "";
        loadAccounts(1);
    });

    loadAccounts(currentPage);
})();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>