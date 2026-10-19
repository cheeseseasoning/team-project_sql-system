package com.sqlflow;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashTest {

    public static void main(String[] args) {

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder();

        String password = "kosa1004";

        String hash = encoder.encode(password);

        System.out.println("BCrypt Hash: " + hash);
        System.out.println("검증 결과: "
                + encoder.matches(password, hash));
    }
}
