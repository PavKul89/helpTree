package org.example.helptreeservice.service;

import org.example.helptreeservice.config.ChatProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatContentSanitizerTest {

    private ChatContentSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        ChatProperties properties = new ChatProperties();
        properties.setMaxMessageLength(10);
        sanitizer = new ChatContentSanitizer(properties);
    }

    @Test
    void sanitize_shouldReturnNullForNull() {
        assertNull(sanitizer.sanitize(null));
    }

    @Test
    void sanitize_shouldNormalizeLineEndingsAndTrim() {
        assertEquals("first\nsecond\nthird",
                sanitizer.sanitize("  first\r\nsecond\rthird  "));
    }

    @Test
    void sanitize_shouldKeepTabsAndNewlines() {
        assertEquals("a\tb\nc", sanitizer.sanitize("a\tb\nc"));
    }

    @Test
    void sanitize_shouldRemoveControlAndBidiCharacters() {
        String input = "hel\u0000lo\u0007\u202Eworld";

        assertEquals("helloworld", sanitizer.sanitize(input));
    }

    @Test
    void sanitize_shouldNotHtmlEscape() {
        assertEquals("<b>bold</b>", sanitizer.sanitize("<b>bold</b>"));
    }

    @Test
    void isBlank_shouldDetectWhitespaceOnlyContent() {
        assertTrue(sanitizer.isBlank(null));
        assertTrue(sanitizer.isBlank(" \n\t "));
        assertFalse(sanitizer.isBlank("text"));
    }

    @Test
    void exceedsMaxLength_shouldCountCodePointsNotChars() {
        // Four emoji are eight UTF-16 chars but four code points.
        String emoji = "😀😀😀😀";

        assertEquals(4, sanitizer.length(emoji));
        assertFalse(sanitizer.exceedsMaxLength(emoji));
        assertTrue(sanitizer.exceedsMaxLength("a".repeat(11)));
    }
}
