<%@ page pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/views/fragments/top.jspf" %>

<main class="account-create-page">
    <header class="account-create-heading">
        <div>
            <h1>계정 생성·역할 부여</h1>
            <p>시스템 관리 / 계정 관리</p>
        </div>
    </header>

    <form id="account-form" class="account-create-form">
        <section
            class="panel account-create-panel account-create-info"
            aria-labelledby="account-info-title">

            <div class="account-create-section-heading">
                <h2 id="account-info-title">기본 정보</h2>
                <span class="caption"><span class="required">*</span> 필수 입력</span>
            </div>

            <div class="account-create-grid">
                <label class="account-create-field" for="account-login">
                    <span class="account-create-label">
                        로그인 ID <span class="required">*</span>
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
        </section>

        <section
            class="panel account-create-panel account-create-roles"
            aria-labelledby="account-roles-title">

            <div class="account-create-section-heading">
                <h2 id="account-roles-title">역할 관리</h2>
            </div>

            <div class="account-create-role-add">
                <label class="sr-only" for="account-role-select">
                    추가할 역할 선택
                </label>
                <select id="account-role-select" aria-label="추가할 역할 선택">
                    <option value="">추가할 역할 선택</option>
                </select>
                <button
                    id="account-role-add-button"
                    type="button"
                    class="button primary"
                    disabled>
                    역할 추가
                </button>
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
        </section>

        <div class="account-create-actions">
            <button type="reset" class="button">입력 초기화</button>
            <button type="submit" class="button primary">계정 생성</button>
        </div>
    </form>
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
        const showPassword =
            document.getElementById('show-account-password');

        const roleDetails = {
            SYSTEM_ADMIN: {
                label: '시스템 관리자',
                description: '계정 생성 및 사용자 역할 부여'
            },
            DB_ADMIN: {
                label: 'DB 관리자',
                description: '변경 실행 결과 등록·정정 및 리소스·접근·반출 정책 관리'
            },
            DB_APPROVER: {
                label: 'SQL 실행 담당자',
                description: 'SELECT 조회 실행'
            },
            SENSITIVE_DATA_READER: {
                label: '민감 데이터 조회자',
                description: '부서 접근 정책에서 허용한 민감 데이터 조회'
            }
        };

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

        roleSelect.addEventListener('change', () => {
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