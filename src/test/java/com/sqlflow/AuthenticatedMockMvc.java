package com.sqlflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SqlFlowUserDetails;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

final class AuthenticatedMockMvc {

    private AuthenticatedMockMvc() {
    }

    static MockMvc forRequester(WebApplicationContext context) {
        UserAccount account = new UserAccount();
        account.setUserId(1L);
        account.setDeptId(1L);
        account.setLoginId("test-requester");
        account.setPasswordHash("{bcrypt}test");
        account.setUserName("테스트 요청자");
        account.setDeptName("테스트팀");
        account.setPositionName("사원");
        account.setEnabled("Y");

        return MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/")
                        .with(user(new SqlFlowUserDetails(account, List.of())))
                        .with(csrf()))
                .apply(springSecurity())
                .build();
    }
}
