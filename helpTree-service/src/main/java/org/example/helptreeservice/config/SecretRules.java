package org.example.helptreeservice.config;

import java.util.List;
import java.util.Locale;

/**
 * Shared rules for secrets and the bootstrap administrator password.
 *
 * <p>They are intentionally conservative: a value that looks like a sample,
 * documentation placeholder or development default is rejected even when its
 * length is sufficient. Production startup and the one-time administrator
 * bootstrap both use the same checks, so the two paths cannot drift apart.</p>
 */
public final class SecretRules {

    /**
     * Substrings that identify a documented example or a development default.
     * A random secret practically never contains one of them.
     */
    private static final List<String> PLACEHOLDER_FRAGMENTS = List.of(
            "change-me",
            "change_me",
            "changeme",
            "change-in-prod",
            "dev-only",
            "dev-admin",
            "development",
            "example",
            "not-for-production",
            "password",
            "postgres",
            "minioadmin",
            "default",
            "secret-key",
            "test-secret"
    );

    private SecretRules() {
    }

    /** A non-blank value of at least {@code minLength} characters. */
    public static boolean hasLength(String value, int minLength) {
        return value != null && value.trim().length() >= minLength;
    }

    /** True when the value is blank, too short, or a known placeholder. */
    public static boolean isWeak(String value, int minLength) {
        if (!hasLength(value, minLength)) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return PLACEHOLDER_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    /**
     * Bootstrap administrator password: at least 12 characters and at least
     * three of upper-case, lower-case, digit and symbol. Rejected fragments are
     * checked first so a password like {@code dev-admin-password-123} fails
     * even though it meets the character-class rule.
     */
    public static boolean isStrongPassword(String value) {
        if (isWeak(value, 12)) {
            return false;
        }
        int classes = 0;
        if (value.chars().anyMatch(Character::isUpperCase)) {
            classes++;
        }
        if (value.chars().anyMatch(Character::isLowerCase)) {
            classes++;
        }
        if (value.chars().anyMatch(Character::isDigit)) {
            classes++;
        }
        if (value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) {
            classes++;
        }
        return classes >= 3;
    }

    /** Simple shape check for the bootstrap administrator email. */
    public static boolean isEmail(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        int at = trimmed.indexOf('@');
        int dot = trimmed.lastIndexOf('.');
        return at > 0 && dot > at + 1 && dot < trimmed.length() - 1 && !trimmed.contains(" ");
    }
}
