<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<style>
    .account-list-page {
        width: 100%;
    }

    .account-list-heading {
        margin-bottom: 20px;
    }

    .account-list-heading h1 {
        color: #263d4c;
        font-size: 24px;
        font-weight: 700;
    }

    .account-list-heading p {
        margin-top: 2px;
        color: #607487;
        font-size: 13px;
    }

    .account-list-heading .button {
        min-width: 112px;
    }

    .account-list-panel {
        padding: 24px 26px 18px;
        border: 1px solid #cbd9e3;
        border-radius: 7px;
        background: #fff;
    }

    .account-list-toolbar {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 16px;
        margin-bottom: 16px;
    }

    .account-count {
        color: #607487;
        font-size: 13px;
        text-align: right;
    }

    .account-list-table {
        width: 100%;
        min-width: 900px;
        table-layout: fixed;
        border-collapse: collapse;
        text-align: left;
    }

    .account-list-table col.account-login-column {
        width: 12%;
    }

    .account-list-table col.account-name-column {
        width: 12%;
    }

    .account-list-table col.account-department-column {
        width: 16%;
    }

    .account-list-table col.account-position-column {
        width: 10%;
    }

    .account-list-table col.account-role-column {
        width: 34%;
    }

    .account-list-table col.account-status-column {
        width: 16%;
    }

    .account-list-table th,
    .account-list-table td {
        overflow-wrap: anywhere;
    }

    .account-list-table th {
        padding: 13px 10px;
        background: #edf3f6;
        color: #425d70;
        font-size: 13px;
        font-weight: 600;
        white-space: nowrap;
    }

    .account-list-table td {
        height: 57px;
        padding: 11px 10px;
        border-bottom: 1px solid #cbd9e3;
        color: #263d4c;
        font-size: 14px;
        vertical-align: middle;
    }

    .account-login-link {
        color: #263d4c;
        font-size: 14px;
    }

    .account-login-link:hover {
        color: #1e627f;
    }

    .account-role-list {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 4px 8px;
    }

    .account-role-item {
        display: inline;
        color: #263d4c;
        font-size: 13px;
    }

    .account-role-item + .account-role-item::before {
        content: "·";
        margin-right: 8px;
        color: #607487;
    }

    .account-status-badge {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 64px;
        padding: 6px 10px;
        border-radius: 4px;
        background: #e7f2ec;
        color: #326446;
        font-size: 12px;
        font-weight: 500;
        white-space: nowrap;
    }

    .account-status-badge.is-disabled {
        background: #f0f3f5;
        color: #607487;
    }

    .account-pagination {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 12px;
        margin-top: 18px;
    }

    .account-pagination[hidden] {
        display: none;
    }

    .account-page-numbers {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 4px;
    }

    .account-page-button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 40px;
        height: 40px;
        padding: 0 8px;
        border: 0;
        border-radius: 5px;
        background: transparent;
        color: #536c7e;
        font-size: 14px;
        cursor: pointer;
    }

    .account-page-button:hover:not(:disabled):not(.is-current) {
        background: #f1f4f6;
    }

    .account-page-button.is-current {
        background: #e2f0f4;
        color: #21627b;
        font-weight: 700;
    }

    .account-page-button:disabled {
        cursor: not-allowed;
        color: #b9c2c9;
    }

    .account-page-button:focus-visible {
        outline: 2px solid #276b84;
        outline-offset: 2px;
    }

    .account-list-message {
        margin-top: 10px;
    }

    @media (max-width: 700px) {
        .account-list-heading {
            align-items: flex-start;
            gap: 14px;
        }

        .account-list-heading h1 {
            font-size: 21px;
        }

        .account-list-heading .button {
            min-width: 94px;
            padding-right: 10px;
            padding-left: 10px;
        }

        .account-list-panel {
            padding: 17px 14px 14px;
        }

        .account-list-table {
            min-width: 850px;
        }

        .account-pagination {
            gap: 6px;
        }

        .account-page-numbers {
            gap: 2px;
        }

        .account-page-button {
            min-width: 36px;
            height: 38px;
            padding: 0 6px;
        }
    }
</style>

<div class="account-list-page">
    <div class="page-heading account-list-heading">
        <div>
            <h1>계정 목록</h1>
            <p>사용자 계정과 부여된 역할을 확인합니다.</p>
        </div>

        <c:if test="${pageContext.request.isUserInRole('SYSTEM_ADMIN')}">
            <a
                class="button primary"
                href="${pageContext.request.contextPath}/admin/users/new">
                계정 생성
            </a>
        </c:if>
    </div>

    <section class="panel account-list-panel" aria-labelledby="account-list-title">
        <div class="account-list-toolbar">
            <h2 id="account-list-title" class="sr-only">계정</h2>
            <span id="account-count" class="account-count" aria-live="polite"></span>
        </div>

        <div class="table-scroll">
            <table class="account-list-table">
                <caption class="sr-only">사용자 계정 목록</caption>

                <colgroup>
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
                            colspan="${pageContext.request.isUserInRole('SYSTEM_ADMIN') ? 6 : 5}"
                            class="empty">
                            계정 목록을 불러오는 중입니다.
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>

        <nav
            id="account-pagination"
            class="account-pagination"
            aria-label="계정 목록 페이지 이동"
            hidden>
            <button
                id="account-previous-page"
                class="account-page-button"
                type="button"
                aria-label="이전 페이지">
                ‹ 이전
            </button>

            <div
                id="account-page-numbers"
                class="account-page-numbers"
                aria-label="페이지 번호"></div>

            <button
                id="account-next-page"
                class="account-page-button"
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

    const columnCount = isSystemAdmin ? 6 : 5;
    const pageSize = 10;
    const pageBlockSize = 10;

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
            .filter(Boolean);

        if (roles.length === 0) {
            cell.textContent = "-";
            return cell;
        }

        const roleList = document.createElement("div");
        roleList.className = "account-role-list";

        roles.forEach(role => {
            const roleItem = document.createElement("span");
            roleItem.className = "account-role-item";
            roleItem.textContent = role;
            roleList.appendChild(roleItem);
        });

        cell.appendChild(roleList);
        return cell;
    }

    function renderAccounts(accounts) {
        if (accounts.length === 0) {
            showTableMessage("등록된 계정이 없습니다.");
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
            button.className = "account-page-button";
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
        rowsElement.setAttribute("aria-busy", "true");
        messageElement.textContent = "";

        try {
            const response = await api(
                "/accounts?page=" + encodeURIComponent(page)
                + "&size=" + encodeURIComponent(pageSize)
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

    loadAccounts(currentPage);
})();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>