package org.example.helptreeservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        passwordService = new PasswordService(encoder);
    }

    @Test
    void encode_shouldReturnEncodedPassword() {
        String encoded = passwordService.encode("Test1234");

        assertNotNull(encoded);
        assertNotEquals("Test1234", encoded);
        assertTrue(encoded.startsWith("$2a$"));
    }

    @Test
    void matches_shouldReturnTrueForCorrectPassword() {
        String encoded = passwordService.encode("Test1234");

        assertTrue(passwordService.matches("Test1234", encoded));
    }

    @Test
    void matches_shouldReturnFalseForWrongPassword() {
        String encoded = passwordService.encode("Test1234");

        assertFalse(passwordService.matches("Wrong123", encoded));
    }

    @Test
    void validate_shouldPassForValidPassword() {
        assertDoesNotThrow(() -> passwordService.validate("Strong1Pass"));
    }

    @Test
    void validate_shouldThrowForShortPassword() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordService.validate("Ab1")
        );
        assertTrue(ex.getMessage().contains("минимум 8 символов"));
    }

    @Test
    void validate_shouldThrowForNoUppercase() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordService.validate("nouppercase1")
        );
        assertTrue(ex.getMessage().contains("заглавную букву"));
    }

    @Test
    void validate_shouldThrowForNoLowercase() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordService.validate("NOLOWERCASE1")
        );
        assertTrue(ex.getMessage().contains("строчную букву"));
    }

    @Test
    void validate_shouldThrowForNoDigit() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> passwordService.validate("NoDigitHere")
        );
        assertTrue(ex.getMessage().contains("цифру"));
    }

    @Test
    void validate_shouldThrowForNull() {
        assertThrows(IllegalArgumentException.class, () -> passwordService.validate(null));
    }
}
