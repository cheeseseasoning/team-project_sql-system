<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<div class="account-detail-page">
    <div class="page-heading account-detail-heading">
        <div>
            <h1>계정 상세</h1>
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
            <div class="account-form-inner">
            <div class="account-section-heading">
                <h2 id="account-info-title">기본 정보</h2>

                <button id="reset-password"
                        type="button"
                        class="button">
                    초기 비밀번호 재설정
                </button>
            </div>

            <div class="account-info-list">
                <div class="account-info-row account-employee-row">
                    <label class="account-info-label" for="account-employee-no">사번</label>
                    <input id="account-employee-no" class="account-info-value" type="text" readonly>
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
                    <label class="account-info-label" for="account-login">
                        아이디
                    </label>
                    <input id="account-login"
                           class="account-info-value"
                           type="text"
                           readonly>
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
            </div>
        </section>

        <section class="panel account-role-panel"
                 aria-labelledby="account-role-title">
            <div class="account-form-inner">
            <div class="account-section-heading">
                <h2 id="account-role-title">역할 관리</h2>
                <button id="open-account-role" class="button" type="button" disabled>+ 역할 추가</button>
            </div>

            <div id="account-role-list"
                 class="account-role-list"
                 aria-live="polite">
                <p class="caption">역할 정보를 불러오는 중입니다.</p>
            </div>
            </div>
        </section>

        <div class="account-detail-actions">
            <a class="button" href="${pageContext.request.contextPath}/accounts">목록으로</a>
            <button id="save-account"
                    type="submit"
                    class="button primary">
                변경사항 저장
            </button>
        </div>
    </form>

    <dialog id="account-role-dialog" class="account-role-dialog" aria-labelledby="account-role-dialog-title">
        <div class="account-role-dialog-heading">
            <h2 id="account-role-dialog-title">역할 추가</h2>
            <button id="close-account-role" class="account-role-dialog-close" type="button" aria-label="역할 추가 닫기">×</button>
        </div>
        <div class="account-role-dialog-body">
            <label for="account-role-select">추가할 역할</label>
            <select id="account-role-select" autofocus>
                <option value="">추가할 역할 선택</option>
            </select>
            <p id="detail-role-description" class="caption" style="margin-top: 10px;" aria-live="polite"></p>
        </div>
        <div class="account-role-dialog-actions">
            <button id="cancel-account-role" class="button" type="button">취소</button>
            <button id="add-account-role" class="button primary" type="button" disabled>추가</button>
        </div>
    </dialog>
</div>

<script>
(() => {
    const userId = Number("${userId}");
    const form = document.getElementById("account-form");
    const message = document.getElementById("account-message");
    const roleSelect = document.getElementById("account-role-select");
    const roleList = document.getElementById("account-role-list");
    const addRoleButton = document.getElementById("add-account-role");
    const roleDialog = document.getElementById("account-role-dialog");
    const openRoleButton = document.getElementById("open-account-role");

    const roleLabels = window.AccountRoles.labels;

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
        openRoleButton.disabled = roleSelect.options.length <= 1;
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

            document.getElementById("account-employee-no").value = account.employeeNo || "미등록";

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

    // 모달의 추가는 화면 선택 목록만 바꾸고, 서버 반영은 기존 저장 버튼에서 처리한다.
    openRoleButton.addEventListener("click", () => {
        roleSelect.value = "";
        document.getElementById("detail-role-description").textContent = "";
        renderRoleSelect();
        roleDialog.showModal();
    });
    document.getElementById("close-account-role").addEventListener("click", () => roleDialog.close());
    document.getElementById("cancel-account-role").addEventListener("click", () => roleDialog.close());

    roleSelect.addEventListener("change", () => {
        document.getElementById("detail-role-description").textContent = window.AccountRoles.details[roleSelect.value]?.description || "";
        addRoleButton.disabled = !roleSelect.value;
    });

    addRoleButton.addEventListener("click", () => {
        const roleCode = roleSelect.value;

        if (!roleCode || selectedRoles.includes(roleCode)) {
            return;
        }

        selectedRoles = [...selectedRoles, roleCode];
        renderRoleList();
        roleDialog.close();
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