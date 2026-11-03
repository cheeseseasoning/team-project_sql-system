package com.sqlflow.auth.service;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.dto.MyPasswordChangeRequest;
import com.sqlflow.auth.mapper.MyPasswordMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyPasswordService {

    private final MyPasswordMapper myPasswordMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(transactionManager = "managementTransactionManager")
    public void changePassword(
            Long userId,
            MyPasswordChangeRequest input) {

        if (userId == null || userId <= 0) {
            throw unauthorized();
        }

        validateInput(input);

        String currentHash =
                myPasswordMapper.selectPasswordHashForUpdate(userId);

        if (currentHash == null) {
            throw unauthorized();
        }

        if (!passwordEncoder.matches(
                input.getCurrentPassword(), currentHash)) {
            throw badRequest("현재 비밀번호가 일치하지 않습니다.");
        }

        if (passwordEncoder.matches(
                input.getNewPassword(), currentHash)) {
            throw badRequest("현재 비밀번호와 다른 비밀번호를 입력하세요.");
        }

        String newHash =
                passwordEncoder.encode(input.getNewPassword());

        if (myPasswordMapper.updatePasswordHash(userId, newHash) != 1) {
            throw unauthorized();
        }
    }

    private void validateInput(MyPasswordChangeRequest input) {
        if (input == null
                || input.getCurrentPassword() == null
                || input.getCurrentPassword().isBlank()) {
            throw badRequest("현재 비밀번호를 입력하세요.");
        }

        if (byteLength(input.getCurrentPassword()) > 72) {
            throw badRequest("현재 비밀번호 입력값이 너무 깁니다.");
        }

        String newPassword = input.getNewPassword();

        if (newPassword == null
                || newPassword.isBlank()
                || newPassword.length() < 8
                || byteLength(newPassword) > 72) {
            throw badRequest(
                    "새 비밀번호는 8자 이상, 72바이트 이하여야 합니다.");
        }

        if (!newPassword.equals(input.getNewPasswordConfirm())) {
            throw badRequest("새 비밀번호 확인이 일치하지 않습니다.");
        }
    }

    private int byteLength(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "사용 가능한 로그인 계정을 확인할 수 없습니다.");
    }
}