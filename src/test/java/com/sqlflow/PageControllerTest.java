package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.sqlflow.access.service.ResourcePolicyAdminService;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.auth.service.AccountService;

@ExtendWith(MockitoExtension.class)
class PageControllerTest {

    private static final String ADMIN_LOGIN_ID = "system-admin";

    @Mock
    private ResourcePolicyAdminService resourcePolicyAdminService;

    @Mock
    private AccountService accountService;

    private PageController pageController;

    @BeforeEach
    void setUp() {
        pageController = new PageController(
                resourcePolicyAdminService,
                accountService);
    }

    @Test
    void createUserPageRechecksCurrentAdminInDatabase() {
        Model model = new ExtendedModelMap();

        String page = pageController.createUser(
                model,
                adminAuthentication());

        verify(accountService)
                .assertActiveSystemAdmin(ADMIN_LOGIN_ID);

        assertEquals("admin-user-form", page);
    }

    @Test
    void accountDetailPageRechecksCurrentAdminInDatabase() {
        Model model = new ExtendedModelMap();

        String page = pageController.accountDetail(
                model,
                25L,
                adminAuthentication());

        verify(accountService)
                .assertActiveSystemAdmin(ADMIN_LOGIN_ID);

        assertEquals("admin-user-detail", page);
        assertEquals(25L, model.getAttribute("userId"));
    }

    private Authentication adminAuthentication() {
        UserAccount account = new UserAccount();
        account.setUserId(1L);
        account.setLoginId(ADMIN_LOGIN_ID);
        account.setPasswordHash("test-hash");
        account.setUserName("시스템 관리자");
        account.setEnabled("Y");

        SqlFlowUserDetails principal = new SqlFlowUserDetails(
                account,
                List.of("SYSTEM_ADMIN"));

        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                "test-password",
                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));
    }
}