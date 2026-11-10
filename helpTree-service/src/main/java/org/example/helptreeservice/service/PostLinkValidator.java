package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PostProperties;
import org.example.helptreeservice.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server-side link policy for post text.
 *
 * <p>Only {@code http} and {@code https} URLs are accepted. The URL host must
 * not be a loopback, private, link-local, site-local, multicast or
 * unique-local address, and {@code localhost} / single-label intranet hosts are
 * rejected. The check never performs DNS resolution or an outbound request:
 * only literal IP addresses are inspected with {@link InetAddress#getByName},
 * which is a pure parse for literals.</p>
 */
@Component
@RequiredArgsConstructor
public class PostLinkValidator {

    private static final Pattern LINK = Pattern.compile(
            "(?i)(?<![\\w+.-])(?:(https?)://|(www\\.))([^\\s<>\"']+)");
    private static final Pattern OTHER_SCHEME = Pattern.compile(
            "(?i)(?<![\\w+.-])([a-z][a-z0-9+.-]*)://");
    private static final Pattern DANGEROUS_SCHEME = Pattern.compile(
            "(?i)\\b(?:javascript|vbscript):|\\bfile://|\\bdata:[a-z0-9.+-]+/[a-z0-9.+-]+[,;]");
    private static final Pattern IPV4_LITERAL = Pattern.compile(
            "^([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})$");
    private static final String TRAILING_PUNCTUATION = ".,;!)]}";

    private final PostProperties postProperties;

    public void validate(String title, String description) {
        String text = (title == null ? "" : title) + "\n" + (description == null ? "" : description);

        if (DANGEROUS_SCHEME.matcher(text).find()) {
            throw new BadRequestException("Ссылки в заявке должны использовать только http или https");
        }

        Matcher otherScheme = OTHER_SCHEME.matcher(text);
        while (otherScheme.find()) {
            String scheme = otherScheme.group(1).toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new BadRequestException("Ссылки в заявке должны использовать только http или https");
            }
        }

        int links = 0;
        Matcher matcher = LINK.matcher(text);
        while (matcher.find()) {
            links++;
            if (links > postProperties.getMaxLinks()) {
                throw new BadRequestException(
                        "В заявке можно указать не более " + postProperties.getMaxLinks() + " ссылок");
            }
            String scheme = matcher.group(1);
            String prefix = scheme != null ? scheme.toLowerCase(Locale.ROOT) + "://" : "http://";
            String url = trimTrailing(prefix + matcher.group(3));
            checkHost(url);
        }
    }

    private void checkHost(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new BadRequestException("Некорректная ссылка в заявке");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BadRequestException("Некорректная ссылка в заявке");
        }

        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        // A trailing dot is the same DNS name; strip it before the checks so
        // "127.0.0.1." cannot slip past the literal branch below.
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            throw new BadRequestException("Некорректная ссылка в заявке");
        }

        if (normalized.equals("localhost") || normalized.endsWith(".localhost")) {
            throw new BadRequestException("Ссылки на локальные адреса запрещены");
        }

        if (normalized.contains(":")) {
            if (isForbiddenAddress(normalized)) {
                throw new BadRequestException("Ссылки на локальные и частные адреса запрещены");
            }
            return;
        }

        if (isNumericHost(normalized)) {
            // Browsers also accept abbreviated, octal and hexadecimal IPv4
            // forms (127.1, 0177.0.0.1, 0x7f.0.0.1), which resolve differently
            // in Java. Only canonical dotted-decimal is accepted here.
            if (!IPV4_LITERAL.matcher(normalized).matches()) {
                throw new BadRequestException("Некорректная ссылка в заявке");
            }
            for (String part : normalized.split("\\.")) {
                if (part.length() > 1 && part.charAt(0) == '0') {
                    throw new BadRequestException("Некорректная ссылка в заявке");
                }
            }
            if (isForbiddenAddress(normalized)) {
                throw new BadRequestException("Ссылки на локальные и частные адреса запрещены");
            }
            return;
        }

        // A single-label host such as "intranet" is not a public site. This is
        // also the shape used by internal service discovery names.
        if (!normalized.contains(".")) {
            throw new BadRequestException("Ссылки на локальные и частные адреса запрещены");
        }
    }

    /**
     * True when every dot-separated label looks numeric (decimal, octal or
     * hexadecimal). Such a host is an IPv4 spelling, never a public domain, so
     * it must be validated as an address rather than treated as a hostname.
     */
    private boolean isNumericHost(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length == 0 || parts.length > 4) {
            return false;
        }
        for (String part : parts) {
            if (!part.matches("0[xX][0-9a-fA-F]+|[0-9]+")) {
                return false;
            }
        }
        return true;
    }

    private boolean isForbiddenAddress(String literal) {
        try {
            InetAddress address = InetAddress.getByName(literal);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return true;
            }
            if (address instanceof Inet4Address) {
                byte[] bytes = address.getAddress();
                int first = bytes[0] & 0xff;
                int second = bytes[1] & 0xff;
                // 0.0.0.0/8 and 100.64.0.0/10 (carrier-grade NAT).
                if (first == 0 || (first == 100 && second >= 64 && second <= 127)) {
                    return true;
                }
            }
            if (address instanceof Inet6Address) {
                // fc00::/7 unique-local addresses are not covered by
                // isSiteLocalAddress in Java.
                int first = address.getAddress()[0] & 0xff;
                return (first & 0xfe) == 0xfc;
            }
            return false;
        } catch (Exception e) {
            // A literal that cannot be parsed is not a usable public address.
            return true;
        }
    }

    private String trimTrailing(String url) {
        int end = url.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
    }
}
