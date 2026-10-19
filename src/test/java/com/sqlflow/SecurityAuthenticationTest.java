package com.sqlflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageIsPublicAndUnauthenticatedApiReturnsJson401() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/requests/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("{\"message\":\"로그인이 필요합니다.\"}"));
    }

    @Test
    void invalidLoginRedirectsBackToLoginPage() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "account-that-does-not-exist")
                        .param("password", "invalid"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
    }
}
