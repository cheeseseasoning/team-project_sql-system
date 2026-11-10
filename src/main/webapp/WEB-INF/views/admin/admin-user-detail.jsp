<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="account-detail-page">
    <div class="page-heading account-detail-heading">
        <div>
            <h1>계정 상세</h1>
            <p class="caption">시스템 관리 / 계정 관리</p>
        </div>
    </div>

    <p id="account-message"
       class="account-notice"
       role="status"
       aria-live="polite"
       hidden></p>

    <form id="account-form" class="account-detail-form">
        <section class="panel account-info-panel"
                 aria-labelledby="account-info-title">
            <div class="account-section-heading">
                <h2 id="account-info-title">기본 정보</h2>

                <button id="reset-password"
                        type="button"
                        class="button">
                    초기 비밀번호 재설정
                </button>
            </div>

            <div class="account-info-list">
                <div class="account-info-row">
                    <label class="account-info-label" for="account-login">
                        로그인 ID
                    </label>
                    <input id="account-login"
                           class="account-info-value"
                           type="text"
                           readonly>
                </div>

                <div class="account-info-row">
                    <label class="account-info-label" for="account-name">
                        이름
                    </label>
                    <input id="account-name"
                           class="account-info-value"
                           name="userName"
                           type="text"
                           maxlength="100"
                           required>
                </div>

                <div class="account-info-row">
                    <label class="account-info-label" for="account-dept">
                        부서
                    </label>
                    <select id="account-dept"
                            class="account-info-value"
                            name="deptId"
                            required></select>
                </div>

                <div class="account-info-row">
                    <label class="account-info-label" for="account-position">
                        직급
                    </label>
                    <select id="account-position"
                            class="account-info-value"
                            name="positionId"
                            required></select>
                </div>

                <div class="account-info-row">
                    <label class="account-info-label" for="account-enabled">
                        계정 상태
                    </label>
                    <select id="account-enabled"
                            class="account-info-value"
                            name="enabled"
                            required>
                        <option value="Y">활성</option>
                        <option value="N">비활성</option>
                    </select>
                </div>
            </div>
        </section>

        <section class="panel account-role-panel"
                 aria-labelledby="account-role-title">
            <div class="account-section-heading">
                <h2 id="account-role-title">역할 관리</h2>
            </div>

            <div class="account-role-add">
                <label class="sr-only" for="account-role-select">
                    추가할 역할 선택
                </label>

                <select id="account-role-select">
                    <option value="">추가할 역할 선택</option>
                </select>

                <button id="add-account-role"
                        class="button primary"
                        type="button"
                        disabled>
                    역할 추가
                </button>
            </div>

            <div id="account-role-list"
                 class="account-role-list"
                 aria-live="polite">
                <p class="caption">역할 정보를 불러오는 중입니다.</p>
            </div>
        </section>

        <div class="account-detail-actions">
            <button id="save-account"
                    type="submit"
                    class="button primary">
                변경사항 저장
            </button>
        </div>
    </form>
</div>

<script>
(() => {
    const userId = Number("${userId}");
    const form = document.getElementById("account-form");
    const message = document.getElementById("account-message");
    const roleSelect = document.getElementById("account-role-select");
    const roleList = document.getElementById("account-role-list");
    const addRoleButton = document.getElementById("add-account-role");

    const roleLabels = {
        SYSTEM_ADMIN: "시스템 관리자",
        DB_ADMIN: "DB 관리자",
        DB_APPROVER: "DB 결재자",
        SENSITIVE_DATA_READER: "민감 데이터 조회자"
    };

    let availableRoles = [];
    let selectedRoles = [];

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

    function renderRoleSelect() {
        const currentValue = roleSelect.value;
        roleSelect.replaceChildren();

        const placeholder = document.createElement("option");
        placeholder.value = "";
        placeholder.textContent = "추가할 역할 선택";
        roleSelect.appendChild(placeholder);

        availableRoles
            .filter(roleCode => !selectedRoles.includes(roleCode))
            .forEach(roleCode => {
                const option = document.createElement("option");
                option.value = roleCode;
                option.textContent = roleLabels[roleCode] || roleCode;
                roleSelect.appendChild(option);
            });

        if ([...roleSelect.options].some(option => option.value === currentValue)) {
            roleSelect.value = currentValue;
        }

        addRoleButton.disabled = !roleSelect.value;
    }

    function renderRoleList() {
        roleList.replaceChildren();

        if (selectedRoles.length === 0) {
            const emptyMessage = document.createElement("p");
            emptyMessage.className = "caption account-role-empty";
            emptyMessage.textContent = "부여된 역할이 없습니다.";
            roleList.appendChild(emptyMessage);
        } else {
            selectedRoles.forEach(roleCode => {
                const row = document.createElement("div");
                row.className = "account-role-row";

                const name = document.createElement("strong");
                name.className = "account-role-name";
                name.textContent = roleLabels[roleCode] || roleCode;

                const removeButton = document.createElement("button");
                removeButton.className = "account-role-remove";
                removeButton.type = "button";
                removeButton.dataset.role = roleCode;
                removeButton.textContent = "제거";
                removeButton.setAttribute(
                    "aria-label",
                    (roleLabels[roleCode] || roleCode) + " 역할 제거"
                );

                row.append(name, removeButton);
                roleList.appendChild(row);
            });
        }

        renderRoleSelect();
    }

    function showFormError(error) {
        showMessage(
            error.message || "계정 정보를 불러오지 못했습니다.",
            true
        );

        form.querySelectorAll("input, select, button").forEach(element => {
            element.disabled = true;
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

            availableRoles = options.roles || [];
            selectedRoles = [...(account.roleCodes || [])];

            renderRoleList();
        } catch (error) {
            showFormError(error);
        }
    }

    roleSelect.addEventListener("change", () => {
        addRoleButton.disabled = !roleSelect.value;
    });

    addRoleButton.addEventListener("click", () => {
        const roleCode = roleSelect.value;

        if (!roleCode || selectedRoles.includes(roleCode)) {
            return;
        }

        selectedRoles = [...selectedRoles, roleCode];
        renderRoleList();
    });

    roleList.addEventListener("click", event => {
        const removeButton = event.target.closest(".account-role-remove");

        if (!removeButton) {
            return;
        }

        selectedRoles = selectedRoles.filter(
            roleCode => roleCode !== removeButton.dataset.role
        );

        renderRoleList();
    });

    form.addEventListener("submit", async event => {
        event.preventDefault();
        message.hidden = true;

        const body = {
            userName: document.getElementById("account-name").value.trim(),
            deptId: Number(document.getElementById("account-dept").value),
            positionId: Number(
                document.getElementById("account-position").value
            ),
            enabled: document.getElementById("account-enabled").value,
            roles: [...selectedRoles]
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

                showMessage(
                    result.message || "계정 정보가 수정되었습니다.",
                    false
                );
            } catch (error) {
                showMessage(
                    error.message || "계정 정보를 수정하지 못했습니다.",
                    true
                );
            }
        });
    });

    document.getElementById("reset-password")
        .addEventListener("click", async event => {
            if (!window.confirm(
                "초기 비밀번호를 kosa1004로 재설정할까요?"
            )) {
                return;
            }

            await busy(event.currentTarget, async () => {
                try {
                    const result = await api(
                        "/admin/users/"
                        + encodeURIComponent(userId)
                        + "/reset-password",
                        { method: "POST" }
                    );

                    showMessage(
                        result.message
                        || "초기 비밀번호가 재설정되었습니다.",
                        false
                    );
                } catch (error) {
                    showMessage(
                        error.message
                        || "초기 비밀번호를 재설정하지 못했습니다.",
                        true
                    );
                }
            });
        });

    loadAccount();
})();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>