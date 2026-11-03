package com.sqlflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** 본인이 수정할 수 있는 연락처만 받는다. */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MyProfileUpdateRequest {

    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
    @Pattern(
            regexp = "^(?:010[0-9]{8}"
                    + "|02[0-9]{7,8}"
                    + "|0(?:31|32|33|41|42|43|44|51|52|53|54|55|61|62|63|64)"
                    + "[0-9]{7,8}"
                    + "|070[0-9]{8})$",
            message = "국내 전화번호 형식을 확인하세요.")
    private String phoneNumber;

    @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
    @Email(message = "이메일 형식을 확인하세요.")
    private String email;

    @JsonIgnore
    private boolean phoneNumberProvided;

    @JsonIgnore
    private boolean emailProvided;

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumberProvided = true;

        String value = normalize(phoneNumber);

        // 빈 입력은 삭제 의도로 처리한다.
        // 그 외에는 하이픈과 공백만 제거하고 형식 검사를 받는다.
        this.phoneNumber = value == null
                ? null
                : value.replaceAll("[ -]", "");
    }

    public void setEmail(String email) {
        this.emailProvided = true;
        this.email = normalize(email);
    }

    public boolean hasChanges() {
        return phoneNumberProvided || emailProvided;
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}