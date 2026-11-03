package com.sqlflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MyPasswordChangeRequest {

    @NotBlank(message = "현재 비밀번호를 입력하세요.")
    @Size(max = 72, message = "현재 비밀번호 입력값이 너무 깁니다.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력하세요.")
    @Size(
            min = 8,
            max = 72,
            message = "새 비밀번호는 8자 이상, 72바이트 이하여야 합니다.")
    private String newPassword;

    @NotBlank(message = "새 비밀번호 확인을 입력하세요.")
    @Size(max = 72, message = "새 비밀번호 확인 입력값이 너무 깁니다.")
    private String newPasswordConfirm;
}