package com.sqlflow.auth.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.sqlflow.auth.domain.UserAccount;

import lombok.Getter;

@Getter
public class SqlFlowUserDetails implements UserDetails {

    private final Long userId;
    private final Long deptId;
    private final String loginId;
    private final String passwordHash;
    private final String userName;
    private final String deptName;
    private final String positionName;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public SqlFlowUserDetails(UserAccount account, List<String> roleCodes) {
        this.userId = account.getUserId();
        this.deptId = account.getDeptId();
        this.loginId = account.getLoginId();
        this.passwordHash = account.getPasswordHash();
        this.userName = account.getUserName();
        this.deptName = account.getDeptName();
        this.positionName = account.getPositionName();
        this.enabled = "Y".equalsIgnoreCase(account.getEnabled());
        this.authorities = roleCodes.stream()
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    public String getDisplayName() {
        return userName;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
