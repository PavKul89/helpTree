package org.example.helptreeservice.metrics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Records duration, response size, authorization errors and 5xx responses.
 *
 * <p>The URI tag uses the matched handler pattern (for example
 * {@code /api/helps/{id}}), never the raw path: a raw path would create one
 * series per id. When no pattern is available the path is normalized by
 * replacing numeric segments.</p>
 *
 * <p>Actuator requests are skipped so scraping {@code /actuator/prometheus}
 * does not feed its own latency back into the dashboard.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class ApiMetricsFilter extends OncePerRequestFilter {

    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("/\\d+");
    private static final int MAX_URI_LENGTH = 120;

    private final HelpTreeMetrics metrics;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        long start = System.nanoTime();
        CountingResponseWrapper wrapped = new CountingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            long duration = System.nanoTime() - start;
            metrics.apiRequest(request.getMethod(), uriTag(request, response),
                    wrapped.getStatus(), duration, wrapped.getBytesWritten());
        }
    }

    private String uriTag(HttpServletRequest request, HttpServletResponse response) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String uri = pattern != null ? pattern.toString() : request.getRequestURI();
        if (pattern == null) {
            uri = NUMERIC_SEGMENT.matcher(uri).replaceAll("/{id}");
        }
        if (uri.length() > MAX_URI_LENGTH) {
            uri = uri.substring(0, MAX_URI_LENGTH);
        }
        return uri;
    }

    /**
     * Counts bytes written without buffering the body. The container flushes
     * the writer at the end of the request, so the counter is complete by the
     * time the filter reads it.
     */
    static final class CountingResponseWrapper extends HttpServletResponseWrapper {

        private CountingOutputStream counter;
        private ServletOutputStream outputStream;
        private PrintWriter writer;

        CountingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (outputStream == null) {
                counter = new CountingOutputStream(super.getOutputStream());
                outputStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        // Blocking servlet output is always ready.
                    }

                    @Override
                    public void write(int b) throws IOException {
                        counter.write(b);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        counter.write(b, off, len);
                    }

                    @Override
                    public void flush() throws IOException {
                        counter.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        counter.close();
                    }
                };
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                counter = new CountingOutputStream(super.getOutputStream());
                String encoding = getCharacterEncoding();
                Charset charset = encoding == null
                        ? StandardCharsets.UTF_8
                        : Charset.forName(encoding);
                writer = new PrintWriter(new OutputStreamWriter(counter, charset));
            }
            return writer;
        }

        long getBytesWritten() {
            return counter == null ? 0 : counter.getCount();
        }
    }

    private static final class CountingOutputStream extends FilterOutputStream {

        private long count;

        private CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }

        long getCount() {
            return count;
        }
    }
}
