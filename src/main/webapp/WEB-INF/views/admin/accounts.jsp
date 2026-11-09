<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<style>
    .account-list-table {
        width: 100%;
        min-width: 900px;
        table-layout: fixed;
    }

    .account-list-table col.account-login-column {
        width: 18%;
    }

    .account-list-table col.account-name-column {
        width: 18%;
    }

    .account-list-table col.account-department-column {
        width: 14%;
    }

    .account-list-table col.account-position-column {
        width: 10%;
    }

    .account-list-table col.account-role-column {
        width: 28%;
    }

    .account-list-table col.account-status-column {
        width: 12%;
    }

    .account-list-table th,
    .account-list-table td {
        overflow-wrap: anywhere;
    }

    .account-role-list {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 4px;
    }

    .account-role-item {
        display: block;
        max-width: 100%;
        overflow-wrap: anywhere;
    }

    .account-pagination {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 16px;
        margin-top: 24px;
    }

    .account-pagination[hidden] {
        display: none;
    }

    .account-pagination button:disabled {
        cursor: not-allowed;
        opacity: 0.55;
    }

    .account-page-status {
        min-width: 160px;
        text-align: center;
    }

    @media (max-width: 700px) {
        .account-list-table {
            min-width: 850px;
        }
    }
</style>

<div class="page-heading">
    <div>
        <h1>계정 목록</h1>
        <p class="caption">등록된 사용자 계정을 확인합니다.</p>
    </div>

    <c:if test="${pageContext.request.isUserInRole('SYSTEM_ADMIN')}">
        <a
            class="button primary"
            href="${pageContext.request.contextPath}/admin/users/new">
            계정 생성
        </a>
    </c:if>
</div>

<section class="panel" aria-labelledby="account-list-title">
    <div class="panel-heading">
        <h2 id="account-list-title">계정</h2>
        <span id="account-count" class="caption" aria-live="polite"></span>
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
                    <th scope="col">로그인 ID</th>
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
            class="button"
            type="button">
            이전
        </button>

        <span
            id="account-page-status"
            class="account-page-status caption"
            aria-live="polite"></span>

        <button
            id="account-next-page"
            class="button"
            type="button">
            다음
        </button>
    </nav>

    <p
        id="account-list-message"
        class="caption"
        role="status"
        aria-live="polite"></p>
</section>

<script>
(() => {
    const rowsElement = $("#account-rows");
    const countElement = $("#account-count");
    const messageElement = $("#account-list-message");
    const paginationElement = $("#account-pagination");
    const previousButton = $("#account-previous-page");
    const nextButton = $("#account-next-page");
    const pageStatusElement = $("#account-page-status");

    const isSystemAdmin =
        ${pageContext.request.isUserInRole('SYSTEM_ADMIN')};

    const columnCount = isSystemAdmin ? 6 : 5;
    const pageSize = 10;

    const roleLabels = {
        SYSTEM_ADMIN: "시스템 관리자",
        DB_ADMIN: "DB 관리자",
        DB_APPROVER: "DB 결재자",
        SENSITIVE_DATA_READER: "민감 데이터 조회자"
    };

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
            roleItem.textContent = roleLabels[role] || role;
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
                    ? "badge success"
                    : "badge neutral";
                badge.textContent = enabled ? "활성" : "비활성";

                statusCell.appendChild(badge);
                row.appendChild(statusCell);
            }

            fragment.appendChild(row);
        });

        rowsElement.replaceChildren(fragment);
    }

    function renderPagination(response) {
        totalPages = response.totalPages || 0;
        currentPage = response.page || 1;

        countElement.textContent =
            "총 " + response.totalCount + "명";

        if (totalPages <= 1) {
            paginationElement.hidden = true;
            pageStatusElement.textContent =
                totalPages === 0 ? "0 페이지" : "1 / 1 페이지";
            return;
        }

        paginationElement.hidden = false;
        previousButton.disabled = currentPage <= 1;
        nextButton.disabled = currentPage >= totalPages;

        pageStatusElement.textContent =
            currentPage + " / " + totalPages + " 페이지";
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