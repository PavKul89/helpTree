package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.AntivirusProperties;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ServiceUnavailableException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * ClamAV INSTREAM client.
 *
 * <p>The scanner is disabled by default. When enabled the upload path is
 * fail-closed: an unreachable daemon, timeout or malformed answer rejects the
 * file rather than storing unverified bytes. No filename or other user data is
 * sent, only the raw content.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AntivirusScanner {

    private static final byte[] INSTREAM = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PING = "zPING\0".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_RESPONSE_BYTES = 4 * 1024;

    private final AntivirusProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Scans the bytes and returns normally when the content is clean.
     *
     * @throws BadRequestException        when the scanner reports malware;
     * @throws ServiceUnavailableException when the scanner cannot be reached or
     *                                    returns an unusable answer (fail-closed).
     */
    public void scan(byte[] content) {
        if (!properties.isEnabled()) {
            return;
        }
        if (content == null || content.length == 0) {
            throw new BadRequestException("Файл пустой");
        }

        String response;
        try (Socket socket = open()) {
            OutputStream output = socket.getOutputStream();
            output.write(INSTREAM);
            int chunkSize = Math.max(1024, properties.getChunkSize());
            for (int offset = 0; offset < content.length; offset += chunkSize) {
                int length = Math.min(chunkSize, content.length - offset);
                output.write(ByteBuffer.allocate(4).putInt(length).array());
                output.write(content, offset, length);
            }
            // Zero-length chunk terminates the stream.
            output.write(new byte[]{0, 0, 0, 0});
            output.flush();
            response = readResponse(socket.getInputStream());
        } catch (IOException e) {
            log.error("ClamAV недоступен ({}:{}): {}", properties.getHost(), properties.getPort(),
                    e.getMessage());
            throw new ServiceUnavailableException(
                    "Проверка файла временно недоступна. Попробуйте позже.", e);
        }

        String normalized = response.trim();
        if (normalized.endsWith("OK")) {
            return;
        }
        if (normalized.contains("FOUND")) {
            log.warn("Антивирус отклонил загрузку: {}", normalized);
            throw new BadRequestException("Файл отклонён антивирусной проверкой");
        }
        log.error("Неожиданный ответ ClamAV: {}", normalized);
        throw new ServiceUnavailableException("Проверка файла временно недоступна. Попробуйте позже.");
    }

    /** Lightweight health probe used by the actuator indicator. */
    public boolean ping() {
        if (!properties.isEnabled()) {
            return true;
        }
        try (Socket socket = open()) {
            socket.getOutputStream().write(PING);
            socket.getOutputStream().flush();
            return readResponse(socket.getInputStream()).trim().endsWith("PONG");
        } catch (IOException e) {
            log.warn("ClamAV ping failed: {}", e.getMessage());
            return false;
        }
    }

    private Socket open() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(properties.getHost(), properties.getPort()),
                Math.max(1, properties.getConnectTimeoutMillis()));
        socket.setSoTimeout(Math.max(1, properties.getReadTimeoutMillis()));
        return socket;
    }

    private String readResponse(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == 0) {
                break;
            }
            if (buffer.size() >= MAX_RESPONSE_BYTES) {
                throw new IOException("Слишком длинный ответ ClamAV");
            }
            buffer.write(value);
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }
}
