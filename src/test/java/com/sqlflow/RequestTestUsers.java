package com.sqlflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.List;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SqlFlowUserDetails;

final class RequestTestUsers {

    private RequestTestUsers() {
    }

    static RequestPostProcessor authenticatedUser() {
        UserAccount account = new UserAccount();
        account.setUserId(1L);
        account.setDeptId(1L);
        account.setLoginId("test-user");
        account.setPasswordHash("{bcrypt}test");
        account.setUserName("테스트 사용자");
        account.setDeptName("테스트팀");
        account.setPositionName("사원");
        account.setEnabled("Y");
        return user(new SqlFlowUserDetails(account, List.of()));
    }
}
