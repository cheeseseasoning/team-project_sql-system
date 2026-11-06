<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<style>
    .account-list-table {
        width: 100%;
        min-width: 960px;
        table-layout: fixed;
    }

    .account-list-table col.account-login-column {
        width: 16%;
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
        width: 14%;
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

    @media (max-width: 700px) {
        .account-list-table {
            min-width: 900px;
        }
    }
</style>

<div class="page-heading">
    <div>
        <h1>계정 목록</h1>
        <p class="caption">등록된 사용자 계정을 확인합니다.</p>
    </div>

    <c:if test="${pageContext.request.isUserInRole('SYSTEM_ADMIN')}">
        <a class="button primary"
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
            <caption class="sr-only">전체 사용자 계정 목록</caption>

            <colgroup>
                <col class="account-login-column">
                <col class="account-name-column">
                <col class="account-department-column">
                <col class="account-position-column">
                <col class="account-role-column">
                <col class="account-status-column">
            </colgroup>

            <thead>
                <tr>
                    <th scope="col">로그인 ID</th>
                    <th scope="col">이름</th>
                    <th scope="col">부서</th>
                    <th scope="col">직급</th>
                    <th scope="col">역할</th>
                    <th scope="col">계정 상태</th>
                </tr>
            </thead>

            <tbody id="account-rows" aria-busy="true">
                <tr>
                    <td colspan="6" class="empty">계정 목록을 불러오는 중입니다.</td>
                </tr>
            </tbody>
        </table>
    </div>

    <p id="account-list-message"
       class="caption"
       role="status"
       aria-live="polite"></p>
</section>

<script>
(() => {
    const rowsElement = $("#account-rows");
    const countElement = $("#account-count");
    const messageElement = $("#account-list-message");
    const isSystemAdmin =
        ${pageContext.request.isUserInRole('SYSTEM_ADMIN')};

    function showTableMessage(text) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 6;
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
            countElement.textContent = "총 0명";
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

            const statusCell = document.createElement("td");
            const badge = document.createElement("span");
            const enabled = account.enabled === "Y";

            badge.className = enabled ? "badge success" : "badge neutral";
            badge.textContent = enabled ? "활성" : "비활성";
            statusCell.appendChild(badge);
            row.appendChild(statusCell);

            fragment.appendChild(row);
        });

        rowsElement.replaceChildren(fragment);
        countElement.textContent = "총 " + accounts.length + "명";
        messageElement.textContent = "";
    }

    async function loadAccounts() {
        rowsElement.setAttribute("aria-busy", "true");
        messageElement.textContent = "";

        try {
            const accounts = await api("/accounts");

            if (!Array.isArray(accounts)) {
                throw new Error("계정 목록 응답 형식이 올바르지 않습니다.");
            }

            renderAccounts(accounts);
        } catch (error) {
            countElement.textContent = "";
            showTableMessage("계정 목록을 불러오지 못했습니다.");
            messageElement.textContent =
                error.message || "잠시 후 다시 시도해 주세요.";
        } finally {
            rowsElement.setAttribute("aria-busy", "false");
        }
    }

    loadAccounts();
})();
</script>

<%@ include file="fragments/bottom.jspf" %>