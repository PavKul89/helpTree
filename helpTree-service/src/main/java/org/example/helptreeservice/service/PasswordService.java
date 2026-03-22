package org.example.helptreeservice.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public void validate(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Пароль должен содержать минимум 8 символов");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Пароль должен содержать хотя бы одну заглавную букву");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Пароль должен содержать хотя бы одну строчную букву");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Пароль должен содержать хотя бы одну цифру");
        }
    }
}
