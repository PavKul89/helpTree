package org.example.helptreeservice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataMaskerTest {

    @Test
    void maskEmailKeepsOnlyTheFirstLocalCharacterAndDomain() {
        assertEquals("a***@example.com", SensitiveDataMasker.maskEmail("alice@example.com"));
        assertEquals("***", SensitiveDataMasker.maskEmail("not-an-email"));
        assertNull(SensitiveDataMasker.maskEmail(null));
    }

    @Test
    void maskPhoneKeepsOnlyTheLastTwoDigits() {
        assertEquals("***67", SensitiveDataMasker.maskPhone("+7 (999) 123-45-67"));
        assertEquals("***", SensitiveDataMasker.maskPhone("12"));
        assertNull(SensitiveDataMasker.maskPhone(null));
    }

    @Test
    void bearerAndJwtAreNeverWrittenToLogs() {
        String bearer = SensitiveDataMasker.maskSensitive(
                "Authorization: Bearer abcdefghijklmnop");
        assertFalse(bearer.contains("abcdefghijklmnop"));
        assertTrue(bearer.contains("Bearer " + SensitiveDataMasker.REDACTED));

        String tokenPair = SensitiveDataMasker.maskSensitive(
                "token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
        assertFalse(tokenPair.contains("eyJhbGciOiJIUzI1NiJ9"));
        assertTrue(tokenPair.contains(SensitiveDataMasker.REDACTED));

        String standaloneJwt = SensitiveDataMasker.maskSensitive(
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
        assertTrue(standaloneJwt.contains("[REDACTED_JWT]"));
    }

    @Test
    void credentialsInKeyValuePairsAreRedacted() {
        String masked = SensitiveDataMasker.maskSensitive(
                "password=hunter2 refreshToken: abc123 api_key=secret-key-value");

        assertFalse(masked.contains("hunter2"));
        assertFalse(masked.contains("abc123"));
        assertFalse(masked.contains("secret-key-value"));
        assertTrue(masked.contains("password=" + SensitiveDataMasker.REDACTED));
    }

    @Test
    void complaintTextIsRedactedWhileEnumValuesSurvive() {
        String masked = SensitiveDataMasker.maskSensitive(
                "reason=CREATED description=Соседский кот снова сломал забор "
                        + "internal_note=\"клиент уже писал три раза\"");

        assertTrue(masked.contains("reason=CREATED"));
        assertFalse(masked.contains("Соседский кот"));
        assertFalse(masked.contains("клиент уже писал"));
    }

    @Test
    void emailsAndPhonesInsideFreeTextAreMasked() {
        String masked = SensitiveDataMasker.maskSensitive(
                "Не удалось отправить письмо alice@example.com, телефон +7 999 123-45-67");

        assertFalse(masked.contains("alice@example.com"));
        assertFalse(masked.contains("123-45-67"));
        assertTrue(masked.contains("a***@example.com"));
        assertTrue(masked.contains("***67"));
    }

    @Test
    void ordinaryTextIsNotChanged() {
        assertEquals("Запрос завершён за 42 ms, статус 200",
                SensitiveDataMasker.maskSensitive("Запрос завершён за 42 ms, статус 200"));
    }
}
