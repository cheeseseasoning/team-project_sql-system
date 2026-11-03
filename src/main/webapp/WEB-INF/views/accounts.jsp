<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

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
        <table>
            <caption class="sr-only">전체 사용자 계정 목록</caption>
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

    function renderAccounts(accounts) {
        if (accounts.length === 0) {
            showTableMessage("등록된 계정이 없습니다.");
            countElement.textContent = "총 0명";
            return;
        }

        const fragment = document.createDocumentFragment();

        accounts.forEach(account => {
            const row = document.createElement("tr");
            const loginCell = createCell("");

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
                createCell(account.roleNames)
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