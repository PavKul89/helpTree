package org.example.helptreeservice.service;

import org.example.helptreeservice.config.CommentProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentContentSanitizerTest {

    private final CommentContentSanitizer sanitizer =
            new CommentContentSanitizer(new CommentProperties());

    @Test
    void shouldRemoveControlAndDirectionCharactersButKeepLineBreaks() {
        assertEquals("hello\nworld\t!", sanitizer.sanitize("hello\r\nworld\t\u0000\u202E!"));
    }

    @Test
    void shouldTrimAndDetectBlankContent() {
        assertEquals("text", sanitizer.sanitize("  text  "));
        assertTrue(sanitizer.isBlank(sanitizer.sanitize("\u0000 \n\t")));
        assertFalse(sanitizer.isBlank(sanitizer.sanitize("x")));
    }

    @Test
    void shouldCountCodePointsForLengthLimit() {
        CommentProperties properties = new CommentProperties();
        properties.setMaxLength(2);
        CommentContentSanitizer limited = new CommentContentSanitizer(properties);

        assertFalse(limited.exceedsMaxLength("😀😀"));
        assertTrue(limited.exceedsMaxLength("😀😀😀"));
        assertEquals(2, limited.length("😀😀"));
    }
}
