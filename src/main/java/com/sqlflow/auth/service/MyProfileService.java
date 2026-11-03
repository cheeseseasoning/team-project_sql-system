package com.sqlflow.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.dto.MyProfileResponse;
import com.sqlflow.auth.dto.MyProfileUpdateRequest;
import com.sqlflow.auth.mapper.MyProfileMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyProfileService {

    private final MyProfileMapper myProfileMapper;

    @Transactional(
            transactionManager = "managementTransactionManager",
            readOnly = true)
    public MyProfileResponse getProfile(Long userId) {
        if (userId == null || userId <= 0) {
            throw unauthorized();
        }

        MyProfileResponse profile =
                myProfileMapper.selectProfileByUserId(userId);

        // 존재하지 않거나 비활성화된 계정은 정보를 제공하지 않는다.
        if (profile == null) {
            throw unauthorized();
        }

        return profile;
    }
    
    @Transactional(transactionManager = "managementTransactionManager")
    public MyProfileResponse updateContact(
            Long userId,
            MyProfileUpdateRequest input) {

        if (userId == null || userId <= 0) {
            throw unauthorized();
        }

        if (input == null || !input.hasChanges()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "수정할 전화번호 또는 이메일을 입력하세요.");
        }

        if (myProfileMapper.updateContact(userId, input) != 1) {
            throw unauthorized();
        }

        // 수정된 정보를 같은 트랜잭션에서 다시 조회해서 반환한다.
        return getProfile(userId);
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "사용 가능한 로그인 계정을 확인할 수 없습니다.");
    }
}