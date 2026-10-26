<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div class="page-heading">
    <h1>계정 생성·역할 부여</h1>
    <span class="badge">시스템 관리</span>
</div>
<p class="account-intro">계정 신청 내용을 확인하고 사용자 정보와 업무 역할을 설정하세요.</p>
<div class="account-notice" role="note">
    계정을 생성하면 선택한 역할이 즉시 적용됩니다.
</div>

<form id="account-form" class="panel form-panel">
    <section aria-labelledby="account-info-title">
        <div class="panel-heading">
            <h2 id="account-info-title">계정 정보</h2>
            <span class="caption">* 필수 입력</span>
        </div>

        <div class="form-grid account-fields">
            <label for="account-login">
                <span class="account-label-text">아이디 <span class="required">*</span></span>
                <input id="account-login"
                       name="loginId"
                       autocomplete="off"
                       maxlength="50"
                       pattern="[A-Za-z0-9._-]{3,50}"
                       title="영문, 숫자, 마침표, 밑줄, 하이픈으로 3~50자 입력하세요."
                       required
                       placeholder="로그인에 사용할 아이디">
                <small class="caption">영문·숫자·마침표·밑줄·하이픈으로 3~50자 입력하세요.</small>
            </label>

            <label for="account-name">
                <span class="account-label-text">이름 <span class="required">*</span></span>
                <input id="account-name"
                       name="userName"
                       autocomplete="off"
                       maxlength="100"
                       required
                       placeholder="사용자 이름">
            </label>

            <label for="account-dept">
                <span class="account-label-text">부서 <span class="required">*</span></span>
                <select id="account-dept"
                        name="deptId"
                        required
                        disabled
                        aria-describedby="organization-help">
                    <option value="">부서 목록을 불러오는 중…</option>
                </select>
            </label>

            <label for="account-position">
                <span class="account-label-text">직급 <span class="required">*</span></span>
                <select id="account-position"
                        name="positionId"
                        required
                        disabled
                        aria-describedby="organization-help">
                    <option value="">직급 목록을 불러오는 중…</option>
                </select>
            </label>
        </div>

        <p id="organization-help" class="caption">
            등록된 부서와 직급 중에서 선택하세요.
        </p>

        <div class="form-grid account-fields">
            <label for="account-password">
                <span class="account-label-text">초기 비밀번호 <span class="required">*</span></span>
                <input id="account-password"
                       name="initialPassword"
                       type="password"
                       autocomplete="new-password"
                       minlength="8"
                       required
                       placeholder="초기 비밀번호 입력">
                <small class="caption">8자 이상 입력하세요.</small>
            </label>

            <label for="account-password-confirm">
                <span class="account-label-text">초기 비밀번호 확인 <span class="required">*</span></span>
                <input id="account-password-confirm"
                       type="password"
                       autocomplete="new-password"
                       minlength="8"
                       required
                       placeholder="초기 비밀번호 다시 입력">
            </label>
        </div>

        <label class="account-password-toggle">
            <input id="show-account-password" type="checkbox">
            비밀번호 표시
        </label>
        <p class="caption">발급한 초기 비밀번호는 사용자에게 별도로 전달하세요.</p>
    </section>

    <fieldset class="account-roles">
        <legend>업무 역할</legend>
        <p class="caption">담당 업무에 필요한 역할을 선택하세요. 여러 역할을 함께 부여할 수 있습니다.</p>

        <div class="account-role-grid">
            <label class="account-role">
                <input type="checkbox" name="roles" value="SYSTEM_ADMIN">
                <span>
                    <strong>시스템 관리자</strong>
                    <code>SYSTEM_ADMIN</code>
                    <small>계정 생성 및 사용자 역할 부여</small>
                </span>
            </label>

            <label class="account-role">
                <input type="checkbox" name="roles" value="DB_ADMIN">
                <span>
                    <strong>DB 관리자</strong>
                    <code>DB_ADMIN</code>
                    <small>변경 실행 결과 등록·정정 및 리소스·접근·반출 정책 관리</small>
                </span>
            </label>

            <label class="account-role">
                <input type="checkbox" name="roles" value="DB_APPROVER">
                <span>
                    <strong>DB팀 결재자</strong>
                    <code>DB_APPROVER</code>
                    <small>DB팀 최종 결재 및 SELECT 조회 실행</small>
                </span>
            </label>

            <label class="account-role">
                <input type="checkbox" name="roles" value="SENSITIVE_DATA_READER">
                <span>
                    <strong>민감 데이터 조회자</strong>
                    <code>SENSITIVE_DATA_READER</code>
                    <small>부서 접근 정책에서 허용한 민감 데이터 조회</small>
                </span>
            </label>
        </div>

        <p id="account-role-summary" class="caption" aria-live="polite">
            선택한 역할이 없습니다.
        </p>
        <p class="caption">
            부서·직급과 업무 역할은 별개입니다. 부서장 결재는 소속 부서와 직급을 기준으로 판단합니다.
        </p>
    </fieldset>

    <div class="form-footer">
        <span class="caption">계정 정보와 역할을 확인한 뒤 생성하세요.</span>
        <div>
            <button type="reset" class="button">입력 초기화</button>
            <button type="submit" class="button primary">계정 생성</button>
        </div>
    </div>
</form>

<script>
    (() => {
        const form = document.getElementById('account-form');
        const password = document.getElementById('account-password');
        const confirmation = document.getElementById('account-password-confirm');

        const roles = () =>
            Array.from(form.querySelectorAll('input[name="roles"]:checked'));

        function updateRoleSummary() {
            const selected = roles();

            document.getElementById('account-role-summary').textContent =
                selected.length
                    ? '선택한 역할 ' + selected.length + '개: '
                        + selected.map(input =>
                            input.nextElementSibling
                                .querySelector('strong')
                                .textContent
                        ).join(', ')
                    : '선택한 역할이 없습니다.';
        }

        async function loadOptions() {
            const options = await api('/admin/users/options');
            const department = document.getElementById('account-dept');
            const position = document.getElementById('account-position');

            department.innerHTML =
                '<option value="">부서를 선택하세요</option>'
                + options.departments.map(item =>
                    '<option value="' + esc(item.id) + '">'
                    + esc(item.name) + '</option>'
                ).join('');

            position.innerHTML =
                '<option value="">직급을 선택하세요</option>'
                + options.positions.map(item =>
                    '<option value="' + esc(item.id) + '">'
                    + esc(item.name) + '</option>'
                ).join('');

            department.disabled = false;
            position.disabled = false;
        }

        function validatePasswordConfirmation() {
            confirmation.setCustomValidity(
                confirmation.value !== password.value
                    ? '초기 비밀번호가 일치하지 않습니다.'
                    : ''
            );
        }

        form.addEventListener('input', validatePasswordConfirmation);

        form.addEventListener('change', () => {
            const showPassword =
                document.getElementById('show-account-password').checked;

            password.type = showPassword ? 'text' : 'password';
            confirmation.type = showPassword ? 'text' : 'password';

            updateRoleSummary();
        });

        form.addEventListener('reset', () => {
            confirmation.setCustomValidity('');
            password.type = 'password';
            confirmation.type = 'password';
            setTimeout(updateRoleSummary, 0);
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

            const selectedRoles = roles().map(input => input.value);
            if (selectedRoles.length === 0) {
                notify('하나 이상의 업무 역할을 선택하세요.', true);
                return;
            }

            const submitButton =
                form.querySelector('button[type="submit"]');

            await busy(submitButton, async () => {
                const result = await post('/admin/users', {
                    loginId: loginId.value,
                    userName: userName.value,
                    deptId: Number(
                        document.getElementById('account-dept').value
                    ),
                    positionId: Number(
                        document.getElementById('account-position').value
                    ),
                    initialPassword: password.value,
                    roles: selectedRoles
                });

                form.reset();
                updateRoleSummary();
                notify(result.message + ' 아이디: ' + result.loginId);
            });
        });

        updateRoleSummary();

        loadOptions().catch(error => {
            const department = document.getElementById('account-dept');
            const position = document.getElementById('account-position');

            department.innerHTML =
                '<option value="">부서 목록을 불러오지 못했습니다</option>';
            position.innerHTML =
                '<option value="">직급 목록을 불러오지 못했습니다</option>';

            notify(
                '부서·직급 목록을 불러오지 못했습니다: ' + error.message,
                true
            );
        });
    })();
</script>

<%@ include file="fragments/bottom.jspf" %>