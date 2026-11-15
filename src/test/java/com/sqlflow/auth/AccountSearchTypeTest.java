package com.sqlflow.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.mapper.UserAccountMapper;
import com.sqlflow.auth.service.AccountService;

@ExtendWith(MockitoExtension.class)
class AccountSearchTypeTest {

    @Mock private UserAccountMapper userAccountMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AccountService accountService;

    @ParameterizedTest
    @ValueSource(strings = {"ALL", "NAME", "DEPARTMENT", "POSITION", "ROLE"})
    void appliesSameSearchTypeToCountAndPage(String type) {
        when(userAccountMapper.countMatchingAccountsByType("검색어", type)).thenReturn(25L);
        when(userAccountMapper.searchAccountListItemsByType(10L, 10, "검색어", type))
                .thenReturn(List.of());

        accountService.getAccountListForActor(2, 10, null, " 검색어 ", type);

        verify(userAccountMapper).countMatchingAccountsByType("검색어", type);
        verify(userAccountMapper).searchAccountListItemsByType(10L, 10, "검색어", type);
    }

    @Test
    void rejectsUnknownSearchTypeEvenWithEmptyKeyword() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> accountService.getAccountListForActor(1, 10, null, "", "UNKNOWN"));
        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void reusesUnfilteredPageWhenKeywordIsBlank() {
        when(userAccountMapper.countAllAccounts()).thenReturn(0L);
        when(userAccountMapper.selectAccountListItems(0L, 10)).thenReturn(List.of());

        accountService.getAccountListForActor(1, 10, null, "  ", "ROLE");

        verify(userAccountMapper).countAllAccounts();
        verify(userAccountMapper).selectAccountListItems(0L, 10);
    }
}
