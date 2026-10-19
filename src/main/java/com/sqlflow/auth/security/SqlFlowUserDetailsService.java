package com.sqlflow.auth.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.mapper.UserAccountMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SqlFlowUserDetailsService implements UserDetailsService {

    private final UserAccountMapper userAccountMapper;

    @Override
    public UserDetails loadUserByUsername(String loginId)
            throws UsernameNotFoundException {
        UserAccount account = userAccountMapper.selectByLoginId(loginId);
        if (account == null) {
            throw new UsernameNotFoundException("계정을 찾을 수 없습니다.");
        }
        return new SqlFlowUserDetails(
                account,
                userAccountMapper.selectRoleCodes(account.getUserId()));
    }
}
