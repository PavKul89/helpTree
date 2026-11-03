package org.example.helptreeservice.service;

import org.example.helptreeservice.config.ChatProperties;
import org.springframework.stereotype.Component;

/**
 * Normalizes user-generated chat text. Messages are rendered as text by the
 * clients, so the server keeps them as plain text and removes only characters
 * that can corrupt protocols or spoof direction (NUL, control and bidi chars).
 * It deliberately does not HTML-escape: escaping belongs to the renderer and
 * would otherwise show "&lt;" to users.
 */
@Component
public class ChatContentSanitizer {

    private final ChatProperties properties;

    public ChatContentSanitizer(ChatProperties properties) {
        this.properties = properties;
    }

    public String sanitize(String rawContent) {
        if (rawContent == null) {
            return null;
        }

        String normalized = rawContent.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\t') {
                result.appendCodePoint(codePoint);
                return;
            }
            int type = Character.getType(codePoint);
            if (type == Character.CONTROL
                    || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                return;
            }
            result.appendCodePoint(codePoint);
        });

        return result.toString().strip();
    }

    public boolean isBlank(String sanitizedContent) {
        return sanitizedContent == null || sanitizedContent.isBlank();
    }

    public int length(String content) {
        return content == null ? 0 : content.codePointCount(0, content.length());
    }

    public int maxLength() {
        return properties.getMaxMessageLength();
    }

    public boolean exceedsMaxLength(String content) {
        return length(content) > maxLength();
    }
}
