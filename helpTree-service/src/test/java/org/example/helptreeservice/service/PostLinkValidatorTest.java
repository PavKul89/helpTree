package org.example.helptreeservice.service;

import org.example.helptreeservice.config.PostProperties;
import org.example.helptreeservice.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostLinkValidatorTest {

    private PostLinkValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PostLinkValidator(new PostProperties());
    }

    @Test
    void shouldAcceptPublicHttpAndHttpsLinks() {
        assertDoesNotThrow(() -> validator.validate(
                "Нужна помощь",
                "Подробности: https://example.com/help и http://example.org/page."));
    }

    @Test
    void shouldAcceptWwwPrefix() {
        assertDoesNotThrow(() -> validator.validate(
                "Помогите", "Смотрите www.example.com/page"));
    }

    @Test
    void shouldAcceptTextWithoutLinks() {
        assertDoesNotThrow(() -> validator.validate("Заголовок", "Описание без ссылок"));
    }

    @Test
    void shouldRejectDangerousSchemes() {
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "javascript:alert(1)"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "file:///etc/passwd"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "data:text/html;base64,AAAA"));
    }

    @Test
    void shouldRejectNonHttpSchemes() {
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "ftp://example.com/file"));
    }

    @Test
    void shouldRejectLocalhostAndSingleLabelHosts() {
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://localhost/admin"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://intranet/wiki"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://service:8080/health"));
    }

    @Test
    void shouldRejectPrivateAndLoopbackIpLiterals() {
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://127.0.0.1/"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://10.0.0.1/"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://192.168.1.1/"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://172.16.0.1/"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://169.254.169.254/latest/meta-data/"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://[::1]/"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://[fc00::1]/"));
    }

    @Test
    void shouldRejectObfuscatedLoopbackAndPrivateAddresses() {
        // Browsers expand these to 127.0.0.1 / private space even though a
        // strict dotted-quad check would treat them as a hostname.
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://127.1/"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://10.1/"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://0177.0.0.1/"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://0x7f.0.0.1/"));
        assertThrows(BadRequestException.class,
                () -> validator.validate("t", "http://127.0.0.1./"));
    }

    @Test
    void shouldAcceptPublicIpLiterals() {
        assertDoesNotThrow(() -> validator.validate("t", "http://8.8.8.8/"));
    }

    @Test
    void shouldRejectMoreThanConfiguredNumberOfLinks() {
        PostProperties properties = new PostProperties();
        properties.setMaxLinks(2);
        PostLinkValidator limited = new PostLinkValidator(properties);

        assertDoesNotThrow(() -> limited.validate("t",
                "https://a.example.com https://b.example.com"));
        assertThrows(BadRequestException.class, () -> limited.validate("t",
                "https://a.example.com https://b.example.com https://c.example.com"));
    }
}
