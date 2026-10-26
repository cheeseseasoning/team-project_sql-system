
package com.sqlflow.auth.security;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        AuthenticationEntryPoint entryPoint = (request, response, exception) -> {
            if (request.getRequestURI().startsWith(
                    request.getContextPath() + "/api/")) {

                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                    "{\"message\":\"로그인이 필요합니다.\"}"
                );

            } else {
                response.sendRedirect(
                    request.getContextPath() + "/login"
                );
            }
        };

        http
            .authorizeHttpRequests(authorize -> authorize

                // JSP 내부 이동 및 오류 처리 허용
                .dispatcherTypeMatchers(
                    DispatcherType.FORWARD,
                    DispatcherType.ERROR
                ).permitAll()

                // 로그인 페이지 및 정적 리소스 허용
                .requestMatchers(
                    "/login",
                    "/css/**",
                    "/icons/**",
                    "/fonts/**",
                    "/error"
                ).permitAll()

                // 나머지 요청은 로그인 필요
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("SYSTEM_ADMIN")
                .anyRequest().authenticated()
            )

            // 로그인 설정
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", false)
                .failureUrl("/login?error")
                .permitAll()
            )

            // 로그아웃 설정
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )

            // 인증되지 않은 사용자 처리
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(entryPoint)
            )

            // 로그인 시 세션 고정 공격 방지
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.migrateSession())
            );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
