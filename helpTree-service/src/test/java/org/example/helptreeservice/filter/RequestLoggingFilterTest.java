package org.example.helptreeservice.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.example.helptreeservice.security.ClientInfoResolver;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLoggingFilterTest {

    @Test
    void logsResolvedClientAddress() throws Exception {
        ClientInfoResolver resolver = mock(ClientInfoResolver.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setRemoteAddr("192.0.2.1");
        when(resolver.resolveIp(request)).thenReturn("203.0.113.10");
        RequestLoggingFilter filter = new RequestLoggingFilter(resolver);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> { };

        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            filter.doFilter(request, response, chain);
        } finally {
            logger.detachAppender(appender);
        }

        verify(resolver).resolveIp(request);
        assertTrue(appender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("clientIp=203.0.113.10")));
    }
}
