<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div class="page-heading">
    <div>
        <a href="${pageContext.request.contextPath}/accounts">← 계정 목록</a>
        <h1>계정 상세</h1>
        <p class="caption">계정 정보를 확인하고 수정할 수 있습니다.</p>
    </div>
</div>

<p id="account-message" class="account-notice" role="status" aria-live="polite" hidden></p>

<form id="account-form" class="panel form-panel">
    <section aria-labelledby="account-info-title">
        <div class="panel-heading">
            <h2 id="account-info-title">계정 정보</h2>
            <span class="caption">로그인 ID는 수정할 수 없습니다.</span>
        </div>

        <div class="form-grid account-fields">
            <label for="account-login">
                로그인 ID
                <input id="account-login" type="text" readonly>
            </label>

            <label for="account-name">
                이름
                <input id="account-name" name="userName"
                       type="text" maxlength="100" required>
            </label>

            <label for="account-dept">
                부서
                <select id="account-dept" name="deptId" required></select>
            </label>

            <label for="account-position">
                직급
                <select id="account-position" name="positionId" required></select>
            </label>

            <label for="account-enabled">
                계정 상태
                <select id="account-enabled" name="enabled" required>
                    <option value="Y">사용</option>
                    <option value="N">중지</option>
                </select>
            </label>
        </div>
    </section>

    <fieldset class="account-roles">
        <legend>업무 역할</legend>
        <p class="caption">계정에 부여할 역할을 선택하세요.</p>
        <div id="account-role-options" class="account-role-grid"></div>
    </fieldset>

    <div class="form-footer">
        <button id="reset-password" type="button" class="button">
            초기 비밀번호 재설정
        </button>
        <button id="save-account" type="submit" class="button primary">
            변경 사항 저장
        </button>
    </div>
</form>

<script>
(() => {
    const userId = Number("${userId}");
    const form = document.getElementById("account-form");
    const message = document.getElementById("account-message");

    const roleLabels = {
        SYSTEM_ADMIN: "시스템 관리자",
        DB_ADMIN: "DB 관리자",
        DB_APPROVER: "SQL 실행 담당자",
        SENSITIVE_DATA_READER: "민감 데이터 조회자"
    };

    function showMessage(text, isError) {
        message.textContent = text;
        message.hidden = false;
        message.classList.toggle("error", Boolean(isError));
    }

    function fillSelect(select, items, selectedId) {
        select.replaceChildren();

        items.forEach(item => {
            const option = document.createElement("option");
            option.value = item.id;
            option.textContent = item.name;
            option.selected = String(item.id) === String(selectedId);
            select.appendChild(option);
        });
    }

    function renderRoles(availableRoles, selectedRoles) {
        const container = document.getElementById("account-role-options");
        container.replaceChildren();

        availableRoles.forEach(roleCode => {
            const label = document.createElement("label");
            label.className = "account-role";

            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.name = "roles";
            checkbox.value = roleCode;
            checkbox.checked = selectedRoles.includes(roleCode);

            const text = document.createElement("span");
            text.textContent = roleLabels[roleCode] || roleCode;

            label.append(checkbox, text);
            container.appendChild(label);
        });
    }

    async function loadAccount() {
        try {
            const [account, options] = await Promise.all([
                api("/admin/users/" + encodeURIComponent(userId)),
                api("/admin/users/options")
            ]);

            document.getElementById("account-login").value =
                account.loginId || "";
            document.getElementById("account-name").value =
                account.userName || "";
            document.getElementById("account-enabled").value =
                account.enabled || "Y";

            fillSelect(
                document.getElementById("account-dept"),
                options.departments || [],
                account.deptId
            );
            fillSelect(
                document.getElementById("account-position"),
                options.positions || [],
                account.positionId
            );
            renderRoles(options.roles || [], account.roleCodes || []);
        } catch (error) {
            showMessage(error.message || "계정 정보를 불러오지 못했습니다.", true);
            form.querySelectorAll("input, select, button").forEach(element => {
                element.disabled = true;
            });
        }
    }

    form.addEventListener("submit", async event => {
        event.preventDefault();
        message.hidden = true;

        const body = {
            userName: document.getElementById("account-name").value.trim(),
            deptId: Number(document.getElementById("account-dept").value),
            positionId: Number(document.getElementById("account-position").value),
            enabled: document.getElementById("account-enabled").value,
            roles: Array.from(
                form.querySelectorAll('input[name="roles"]:checked')
            ).map(input => input.value)
        };

        const button = document.getElementById("save-account");
        await busy(button, async () => {
            try {
                const result = await api(
                    "/admin/users/" + encodeURIComponent(userId),
                    {
                        method: "PUT",
                        body: JSON.stringify(body)
                    }
                );
                showMessage(result.message || "계정 정보가 수정되었습니다.", false);
            } catch (error) {
                showMessage(error.message || "계정 정보를 수정하지 못했습니다.", true);
            }
        });
    });

    document.getElementById("reset-password").addEventListener("click", async event => {
        if (!window.confirm("초기 비밀번호를 kosa1004로 재설정할까요?")) {
            return;
        }

        await busy(event.currentTarget, async () => {
            try {
                const result = await api(
                    "/admin/users/" + encodeURIComponent(userId) + "/reset-password",
                    { method: "POST" }
                );
                showMessage(
                    result.message || "초기 비밀번호가 재설정되었습니다.",
                    false
                );
            } catch (error) {
                showMessage(error.message || "초기 비밀번호를 재설정하지 못했습니다.", true);
            }
        });
    });

    loadAccount();
})();
</script>

<%@ include file="fragments/bottom.jspf" %>