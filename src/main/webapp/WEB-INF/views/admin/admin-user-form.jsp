<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<main class="account-create-page">
    <header class="account-create-heading">
        <div>
            <h1>계정 생성</h1>
        </div>
    </header>

    <form id="account-form" class="account-create-form">
        <section
            class="panel account-create-panel account-create-info"
            aria-labelledby="account-info-title">
            <div class="account-form-inner">

            <div class="account-create-section-heading">
                <h2 id="account-info-title">기본 정보</h2>
                <span class="caption"><span class="required">*</span> 필수 입력</span>
            </div>

            <div class="account-create-grid">
                <label class="account-create-field" for="account-employee-no">
				    <span class="account-create-label">
						사번 <span class="required">*</span>
					</span>
				    <input id="account-employee-no"
				           name="employeeNo"
				           type="text"
				           autocomplete="off"
				           maxlength="10"
				           pattern="[0-9]{4}-[0-9]{5}"
						   required
				           title="입사연도 4자리와 일련번호 5자리를 하이픈으로 구분해 입력하세요."
				           placeholder="2026-00042">
				</label>
                <label class="account-create-field" for="account-name">
                    <span class="account-create-label">
                        이름 <span class="required">*</span>
                    </span>
                    <input
                        id="account-name"
                        name="userName"
                        autocomplete="off"
                        maxlength="100"
                        required
                        placeholder="사용자 이름">
                </label>
                <label class="account-create-field" for="account-dept">
                    <span class="account-create-label">
                        부서 <span class="required">*</span>
                    </span>
                    <select
                        id="account-dept"
                        name="deptId"
                        required
                        disabled
                        aria-describedby="organization-help">
                        <option value="">부서 목록을 불러오는 중…</option>
                    </select>
                </label>
                <label class="account-create-field" for="account-position">
                    <span class="account-create-label">
                        직급 <span class="required">*</span>
                    </span>
                    <select
                        id="account-position"
                        name="positionId"
                        required
                        disabled
                        aria-describedby="organization-help">
                        <option value="">직급 목록을 불러오는 중…</option>
                    </select>
                </label>
            </div>

            <p id="organization-help" class="caption account-create-help">
                등록된 부서와 직급 중에서 선택하세요.
            </p>

            <div class="account-create-login-group">
                <label class="account-create-field" for="account-login">
                    <span class="account-create-label">
                        아이디 <span class="required">*</span>
                    </span>
                    <input
                        id="account-login"
                        name="loginId"
                        autocomplete="off"
                        maxlength="50"
                        pattern="[A-Za-z0-9._-]{3,50}"
                        title="영문, 숫자, 마침표, 밑줄, 하이픈으로 3~50자 입력하세요."
                        required
                        placeholder="로그인에 사용할 아이디">
                    <small class="caption">
                        영문·숫자·마침표·밑줄·하이픈으로 3~50자 입력하세요.
                    </small>
                </label>
            </div>

            <div class="account-create-grid">
                <label class="account-create-field" for="account-password">
                    <span class="account-create-label">
                        초기 비밀번호 <span class="required">*</span>
                    </span>
                    <input
                        id="account-password"
                        name="initialPassword"
                        type="password"
                        autocomplete="new-password"
                        minlength="8"
                        required
                        placeholder="초기 비밀번호 입력">
                    <small class="caption">8자 이상 입력하세요.</small>
                </label>

                <label
                    class="account-create-field"
                    for="account-password-confirm">
                    <span class="account-create-label">
                        초기 비밀번호 확인 <span class="required">*</span>
                    </span>
                    <input
                        id="account-password-confirm"
                        type="password"
                        autocomplete="new-password"
                        minlength="8"
                        required
                        placeholder="초기 비밀번호 다시 입력">
                </label>
            </div>

            <label class="account-create-password-toggle">
                <input id="show-account-password" type="checkbox">
                <span>비밀번호 표시</span>
            </label>

            <p class="caption account-create-help">
                발급한 초기 비밀번호는 사용자에게 별도로 전달하세요.
            </p>
            </div>
        </section>

        <section
            class="panel account-create-panel account-create-roles"
            aria-labelledby="account-roles-title">
            <div class="account-form-inner">

            <div class="account-create-section-heading">
                <h2 id="account-roles-title">역할 관리</h2>
                <button id="account-role-open" class="button" type="button" disabled>+ 역할 추가</button>
            </div>

            <div
                id="account-role-list"
                class="account-create-role-list"
                aria-live="polite">
                <p class="account-create-role-empty">
                    부여된 역할이 없습니다.
                </p>
            </div>

            <p class="caption account-create-role-note">
                부서·직급과 업무 역할은 별개입니다. 결재자는 기안 작성자가 지정하며,
                SQL 실행에는 별도 실행 권한이 필요합니다.
            </p>
            </div>
        </section>

        <div class="account-create-actions">
            <button type="reset" class="button">입력 초기화</button>
            <button type="submit" class="button primary">계정 생성</button>
        </div>
    </form>
    <dialog id="account-create-role-dialog" class="account-create-role-dialog" aria-labelledby="create-role-dialog-title">
        <div class="create-role-dialog-heading">
            <h2 id="create-role-dialog-title">역할 추가</h2>
            <button id="account-role-close" class="create-role-dialog-close" type="button" aria-label="역할 추가 닫기">×</button>
        </div>
        <div class="create-role-dialog-body">
            <label for="account-role-select">추가할 역할</label>
            <select id="account-role-select" autofocus>
                <option value="">추가할 역할 선택</option>
            </select>
            <p id="create-role-description" class="caption" style="margin-top: 10px;" aria-live="polite"></p>
        </div>
        <div class="create-role-dialog-actions">
            <button id="account-role-cancel" class="button" type="button">취소</button>
            <button id="account-role-add-button" class="button primary" type="button" disabled>추가</button>
        </div>
    </dialog>
</main>

<script>
    (() => {
        const form = document.getElementById('account-form');
        const password = document.getElementById('account-password');
        const confirmation =
            document.getElementById('account-password-confirm');
        const departmentSelect = document.getElementById('account-dept');
        const positionSelect = document.getElementById('account-position');
        const roleSelect = document.getElementById('account-role-select');
        const roleAddButton =
            document.getElementById('account-role-add-button');
        const roleList = document.getElementById('account-role-list');
        const roleDialog = document.getElementById('account-create-role-dialog');
        const openRoleButton = document.getElementById('account-role-open');
        const showPassword =
            document.getElementById('show-account-password');

        const roleDetails = window.AccountRoles.details;

        let selectedRoles = [];

        function renderSelectedRoles() {
            roleList.replaceChildren();

            if (selectedRoles.length === 0) {
                const emptyMessage = document.createElement('p');
                emptyMessage.className = 'account-create-role-empty';
                emptyMessage.textContent = '부여된 역할이 없습니다.';
                roleList.appendChild(emptyMessage);
                return;
            }

            selectedRoles.forEach(roleCode => {
                const role = roleDetails[roleCode];

                if (!role) {
                    return;
                }

                const row = document.createElement('div');
                row.className = 'account-create-role-row';

                const name = document.createElement('strong');
                name.className = 'account-create-role-name';
                name.textContent = role.label;

                const removeButton = document.createElement('button');
                removeButton.type = 'button';
                removeButton.className = 'account-create-role-remove';
                removeButton.dataset.role = roleCode;
                removeButton.textContent = '제거';
                removeButton.setAttribute(
                    'aria-label',
                    role.label + ' 역할 제거'
                );

                row.append(name, removeButton);
                roleList.appendChild(row);
            });
        }

        function updateRoleOptions(availableRoleCodes) {
            const previousValue = roleSelect.value;
            roleSelect.replaceChildren();

            const placeholder = document.createElement('option');
            placeholder.value = '';
            placeholder.textContent = '추가할 역할 선택';
            roleSelect.appendChild(placeholder);

            availableRoleCodes
                .filter(roleCode => !selectedRoles.includes(roleCode))
                .forEach(roleCode => {
                    const role = roleDetails[roleCode];

                    if (!role) {
                        return;
                    }

                    const option = document.createElement('option');
                    option.value = roleCode;
                    option.textContent = role.label;
                    roleSelect.appendChild(option);
                });

            if ([...roleSelect.options].some(
                option => option.value === previousValue
            )) {
                roleSelect.value = previousValue;
            } else {
                roleSelect.value = '';
            }

            roleAddButton.disabled = !roleSelect.value;
            openRoleButton.disabled = roleSelect.options.length <= 1;
        }

        function validatePasswordConfirmation() {
            confirmation.setCustomValidity(
                confirmation.value !== password.value
                    ? '초기 비밀번호가 일치하지 않습니다.'
                    : ''
            );
        }

        async function loadOptions() {
            const options = await api('/admin/users/options');

            departmentSelect.innerHTML =
                '<option value="">부서를 선택하세요</option>'
                + options.departments.map(item =>
                    '<option value="' + esc(item.id) + '">'
                    + esc(item.name)
                    + '</option>'
                ).join('');

            positionSelect.innerHTML =
                '<option value="">직급을 선택하세요</option>'
                + options.positions.map(item =>
                    '<option value="' + esc(item.id) + '">'
                    + esc(item.name)
                    + '</option>'
                ).join('');

            departmentSelect.disabled = false;
            positionSelect.disabled = false;

            const availableRoleCodes = Array.isArray(options.roles)
                ? options.roles
                : Object.keys(roleDetails);

            updateRoleOptions(availableRoleCodes);
        }

        form.addEventListener('input', validatePasswordConfirmation);

        confirmation.addEventListener('input', validatePasswordConfirmation);
        password.addEventListener('input', validatePasswordConfirmation);

        showPassword.addEventListener('change', () => {
            const inputType = showPassword.checked ? 'text' : 'password';
            password.type = inputType;
            confirmation.type = inputType;
        });

        // 모달에서는 화면의 역할 선택만 바꾸며 실제 저장은 계정 생성 시 처리한다.
        openRoleButton.addEventListener('click', () => {
            roleSelect.value = '';
            document.getElementById('create-role-description').textContent = '';
            roleAddButton.disabled = true;
            roleDialog.showModal();
        });
        document.getElementById('account-role-close').addEventListener('click', () => roleDialog.close());
        document.getElementById('account-role-cancel').addEventListener('click', () => roleDialog.close());

        roleSelect.addEventListener('change', () => {
            document.getElementById('create-role-description').textContent = roleDetails[roleSelect.value]?.description || '';
            roleAddButton.disabled = !roleSelect.value;
        });

        roleAddButton.addEventListener('click', () => {
            const roleCode = roleSelect.value;

            if (!roleCode || selectedRoles.includes(roleCode)) {
                return;
            }

            selectedRoles = [...selectedRoles, roleCode];
            renderSelectedRoles();
            updateRoleOptions(Object.keys(roleDetails));
            roleDialog.close();
        });

        roleList.addEventListener('click', event => {
            const removeButton = event.target.closest(
                '.account-create-role-remove'
            );

            if (!removeButton) {
                return;
            }

            selectedRoles = selectedRoles.filter(
                roleCode => roleCode !== removeButton.dataset.role
            );

            renderSelectedRoles();
            updateRoleOptions(Object.keys(roleDetails));
        });

        form.addEventListener('reset', () => {
            confirmation.setCustomValidity('');
            password.type = 'password';
            confirmation.type = 'password';

            setTimeout(() => {
                selectedRoles = [];
                showPassword.checked = false;
                renderSelectedRoles();
                updateRoleOptions(Object.keys(roleDetails));
            }, 0);
        });

        form.addEventListener('submit', async event => {
            event.preventDefault();

            const loginId = document.getElementById('account-login');
            const userName = document.getElementById('account-name');
			const employeeNo = document.getElementById('account-employee-no');

            loginId.value = loginId.value.trim();
            userName.value = userName.value.trim();

            validatePasswordConfirmation();

            if (!form.reportValidity()) {
                return;
            }

            const submitButton =
                form.querySelector('button[type="submit"]');

            await busy(submitButton, async () => {
                const result = await post('/admin/users', {
                    loginId: loginId.value,
                    userName: userName.value,
					employeeNo: employeeNo.value.trim() || null,
                    deptId: Number(departmentSelect.value),
                    positionId: Number(positionSelect.value),
                    initialPassword: password.value,
                    roles: [...selectedRoles]
                });

                form.reset();
                notify(result.message + ' 아이디: ' + result.loginId);
            });
        });

        renderSelectedRoles();

        loadOptions().catch(error => {
            departmentSelect.innerHTML =
                '<option value="">부서 목록을 불러오지 못했습니다</option>';
            positionSelect.innerHTML =
                '<option value="">직급 목록을 불러오지 못했습니다</option>';
            updateRoleOptions([]);

            notify(
                '부서·직급 목록을 불러오지 못했습니다: ' + error.message,
                true
            );
        });
    })();
</script>

<%@ include file="/WEB-INF/views/fragments/bottom.jspf" %>