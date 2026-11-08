package org.example.helptreeservice.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defence-in-depth masking for log output and operational messages.
 *
 * <p>Code must already avoid logging secrets and personal data. This class is
 * the second line: the logback layouts run every formatted line through
 * {@link #maskSensitive(String)}, so a missed debug statement or a third-party
 * exception message cannot leak an email address, phone number, bearer token,
 * refresh token or complaint text.</p>
 */
public final class SensitiveDataMasker {

    public static final String REDACTED = "[REDACTED]";

    private static final Pattern BEARER = Pattern.compile(
            "(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{8,}");

    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");

    /**
     * key=value / key: value pairs whose value is a credential. The key is kept
     * so operators still see which field was present.
     */
    private static final Pattern SECRET_PAIR = Pattern.compile(
            "(?i)\\b((?:refresh|access|id|ws)?[_-]?token|authorization|password|passcode|secret|api[_-]?key)"
                    + "(\\s*[:=]\\s*)"
                    // Leave an auth scheme to BEARER: it keeps the "Bearer "
                    // label so the value is recognisable in a log line.
                    + "(?!(?:Bearer|Basic|Digest)\\s)(\"[^\"]*\"|'[^']*'|[^\\s,;]+)");

    /**
     * Complaint and moderation free text. A quoted value is masked from 8
     * characters; an unquoted value must look like a phrase (at least three
     * words) so short enum values such as {@code reason=CREATED} remain
     * readable. The lookahead stops the match before the next key so a phrase
     * cannot swallow the following field.
     */
    private static final Pattern SENSITIVE_PAIR = Pattern.compile(
            "(?i)\\b(description|complaint|internal[_-]?note|reason|body)"
                    + "(\\s*[:=]\\s*)"
                    + "(\"[^\"]{8,}\"|'[^']{8,}'|[^\\s=]{2,}(?:\\s+[^\\s=]{2,}){2,})"
                    + "(?=\\s+[A-Za-z_][\\w-]*\\s*[:=]|[,;]|$)");

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /*
     * Ten to fifteen digits with optional +7/8 country prefix and common
     * separators. The lookarounds prevent matching fragments of longer numbers
     * (ids, timestamps, hashes).
     */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\d+])(?:\\+?[78][\\s-]?)?\\(?\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}(?!\\d)");

    private SensitiveDataMasker() {
    }

    /** Applies every known rule to a free-form message. */
    public static String maskSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = replaceBearer(text);
        masked = JWT.matcher(masked).replaceAll("[REDACTED_JWT]");
        masked = replaceValue(SECRET_PAIR, masked);
        masked = replaceValue(SENSITIVE_PAIR, masked);
        masked = maskEmails(masked);
        return maskPhones(masked);
    }

    /** {@code alice@example.com} -> {@code a***@example.com}. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at);
        return local.charAt(0) + "***" + domain;
    }

    /** Keeps only the last two digits of a phone number. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 2) {
            return "***";
        }
        return "***" + digits.substring(digits.length() - 2);
    }

    /** Never log the value of a token or password, even partially. */
    public static String maskToken(String token) {
        return token == null || token.isBlank() ? token : REDACTED;
    }

    /** Complaint text, internal notes and message bodies are never logged. */
    public static String maskComplaintData(String value) {
        return value == null || value.isEmpty() ? value : REDACTED;
    }

    private static String replaceBearer(String text) {
        Matcher matcher = BEARER.matcher(text);
        return matcher.replaceAll("$1" + REDACTED);
    }

    private static String replaceValue(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll(match -> match.group(1) + match.group(2) + REDACTED);
    }

    private static String maskEmails(String text) {
        Matcher matcher = EMAIL.matcher(text);
        return matcher.replaceAll(match -> maskEmail(match.group()));
    }

    private static String maskPhones(String text) {
        Matcher matcher = PHONE.matcher(text);
        return matcher.replaceAll(match -> maskPhone(match.group()));
    }
}
