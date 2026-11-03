<%@ page pageEncoding="UTF-8" %>
<%@ include file="fragments/top.jspf" %>

<div class="page-heading">
    <h1>마이페이지</h1>
    <span class="badge">내 계정</span>
</div>

<p class="my-page-intro">
    내 계정 정보를 확인하고 연락처를 수정할 수 있습니다.
</p>

<div id="profile-loading" class="panel" role="status">
    내 정보를 불러오는 중입니다.
</div>

<div id="profile-load-error" class="panel" hidden>
    <p>내 정보를 불러오지 못했습니다.</p>
    <button id="profile-retry" class="button" type="button">
        다시 불러오기
    </button>
</div>

<div id="profile-content" class="my-page-content" hidden>
    <section class="panel" aria-labelledby="profile-info-title">
        <div class="panel-heading">
            <h2 id="profile-info-title">기본 정보</h2>
            <span class="caption">읽기 전용</span>
        </div>

        <dl class="profile-info-grid">
            <div>
                <dt>이름</dt>
                <dd id="profile-user-name">-</dd>
            </div>
            <div>
                <dt>사번</dt>
                <dd id="profile-employee-no">-</dd>
            </div>
            <div>
                <dt>아이디</dt>
                <dd id="profile-login-id">-</dd>
            </div>
            <div>
                <dt>부서</dt>
                <dd id="profile-dept-name">-</dd>
            </div>
            <div>
                <dt>직급</dt>
                <dd id="profile-position-name">-</dd>
            </div>
        </dl>
    </section>

    <form id="profile-form" class="panel section-gap" novalidate>
        <div class="panel-heading">
            <h2 id="profile-contact-title">연락처</h2>
            <span class="caption">직접 수정할 수 있습니다.</span>
        </div>

        <fieldset id="profile-fields"
                  aria-labelledby="profile-contact-title"
                  disabled>
            <div class="form-grid profile-contact-grid">
                <div>
                    <label for="profile-phone">전화번호</label>
                    <input id="profile-phone"
                           name="phoneNumber"
                           type="tel"
                           inputmode="tel"
                           autocomplete="tel"
                           maxlength="20"
                           placeholder="010-1234-5678"
                           aria-describedby="profile-phone-help profile-phone-error">

                    <p id="profile-phone-help" class="caption profile-field-help">
                        국내 휴대전화, 지역번호 전화, 070 번호를 입력하세요.
                    </p>
                    <p id="profile-phone-error"
                       class="profile-field-error"
                       hidden></p>
                </div>

                <div>
                    <label for="profile-email">이메일</label>
                    <input id="profile-email"
                           name="email"
                           type="email"
                           autocomplete="email"
                           maxlength="254"
                           placeholder="name@example.com"
                           aria-describedby="profile-email-error">

                    <p id="profile-email-error"
                       class="profile-field-error"
                       hidden></p>
                </div>
            </div>
        </fieldset>

        <div class="form-footer profile-footer">
            <p class="caption">
                전화번호와 이메일은 선택 입력입니다.<br>
                빈칸으로 저장하면 기존 정보가 삭제됩니다.
            </p>

            <button id="profile-save"
                    class="button primary"
                    type="submit"
                    disabled>
                변경사항 저장
            </button>
        </div>
    </form>
</div>

<script>
(() => {
    const form = $('#profile-form');
    const fields = $('#profile-fields');
    const phoneInput = $('#profile-phone');
    const emailInput = $('#profile-email');
    const saveButton = $('#profile-save');

    const phonePattern =
        /^(?:010[0-9]{8}|02[0-9]{7,8}|0(?:31|32|33|41|42|43|44|51|52|53|54|55|61|62|63|64)[0-9]{7,8}|070[0-9]{8})$/;

    let original = null;
    let loading = false;
    let saving = false;

    function normalizeText(value) {
        return value.trim() || null;
    }

    function normalizePhone(value) {
        const text = normalizeText(value);
        return text === null ? null : text.replace(/[ -]/g, '');
    }

    function currentContact() {
        return {
            phoneNumber: normalizePhone(phoneInput.value),
            email: normalizeText(emailInput.value)
        };
    }

    function hasChanges() {
        if (original === null) return false;

        const current = currentContact();

        return current.phoneNumber !== original.phoneNumber
            || current.email !== original.email;
    }

    function updateSaveButton() {
        saveButton.disabled =
            loading || saving || original === null || !hasChanges();

        saveButton.textContent = saving
            ? '저장 중…'
            : '변경사항 저장';
    }

    function setFieldError(input, errorElement, message) {
        errorElement.textContent = message;
        errorElement.hidden = !message;

        if (message) {
            input.setAttribute('aria-invalid', 'true');
        } else {
            input.removeAttribute('aria-invalid');
        }
    }

    function clearErrors() {
        setFieldError(phoneInput, $('#profile-phone-error'), '');
        setFieldError(emailInput, $('#profile-email-error'), '');
    }

    function validateContact() {
        clearErrors();

        const contact = currentContact();
        let firstInvalid = null;

        if (contact.phoneNumber !== null
                && !phonePattern.test(contact.phoneNumber)) {
            setFieldError(
                phoneInput,
                $('#profile-phone-error'),
                '국내 전화번호 형식을 확인하세요.'
            );
            firstInvalid = phoneInput;
        }

        // 앞뒤 공백은 서버와 동일하게 제거한 뒤 검사한다.
        emailInput.value = contact.email || '';

        if (!emailInput.validity.valid) {
            setFieldError(
                emailInput,
                $('#profile-email-error'),
                '이메일 형식을 확인하세요.'
            );
            firstInvalid = firstInvalid || emailInput;
        }

        if (firstInvalid) {
            firstInvalid.focus();
            return false;
        }

        return true;
    }

    function formatPhone(value) {
        const phone = value || '';

        if (/^02[0-9]{7,8}$/.test(phone)) {
            return phone.replace(/^(02)([0-9]{3,4})([0-9]{4})$/, '$1-$2-$3');
        }

        if (/^0[0-9]{9,10}$/.test(phone)) {
            return phone.replace(
                /^([0-9]{3})([0-9]{3,4})([0-9]{4})$/,
                '$1-$2-$3'
            );
        }

        return phone;
    }

    function renderProfile(profile) {
        const info = {
            '#profile-user-name': profile.userName,
            '#profile-employee-no': profile.employeeNo,
            '#profile-login-id': profile.loginId,
            '#profile-dept-name': profile.deptName,
            '#profile-position-name': profile.positionName
        };

        Object.entries(info).forEach(([selector, value]) => {
            $(selector).textContent = value || '-';
        });

        phoneInput.value = formatPhone(profile.phoneNumber);
        emailInput.value = profile.email || '';

        original = currentContact();
        clearErrors();
        updateSaveButton();
    }

    async function loadProfile() {
        if (loading || saving) return;

        loading = true;
        fields.disabled = true;
        $('#profile-loading').hidden = false;
        $('#profile-load-error').hidden = true;
        $('#profile-content').hidden = true;
        updateSaveButton();

        try {
            const profile = await api('/me');

            renderProfile(profile);
            $('#profile-content').hidden = false;
            fields.disabled = false;
        } catch (error) {
            original = null;
            $('#profile-load-error').hidden = false;
            notify(error.message, true);
        } finally {
            loading = false;
            $('#profile-loading').hidden = true;
            updateSaveButton();
        }
    }

    phoneInput.addEventListener('input', () => {
        setFieldError(phoneInput, $('#profile-phone-error'), '');
        updateSaveButton();
    });

    emailInput.addEventListener('input', () => {
        setFieldError(emailInput, $('#profile-email-error'), '');
        updateSaveButton();
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();

        if (loading || saving || !hasChanges()) return;
        if (!validateContact()) return;

        const contact = currentContact();
        const payload = {};

        // 수정한 항목만 전달한다. 빈칸은 null로 전달한다.
        if (contact.phoneNumber !== original.phoneNumber) {
            payload.phoneNumber = contact.phoneNumber;
        }
        if (contact.email !== original.email) {
            payload.email = contact.email;
        }

        saving = true;
        fields.disabled = true;
        updateSaveButton();

        try {
            const profile = await api('/me', {
                method: 'PATCH',
                body: JSON.stringify(payload)
            });

            renderProfile(profile);
            notify('연락처를 저장했습니다.');
        } catch (error) {
            notify(error.message, true);
        } finally {
            saving = false;
            fields.disabled = false;
            updateSaveButton();
        }
    });

    $('#profile-retry').addEventListener('click', loadProfile);

    loadProfile();
})();
</script>

<%@ include file="fragments/bottom.jspf" %>