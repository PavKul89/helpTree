package org.example.helptreeservice.service;

import org.example.helptreeservice.config.AntivirusProperties;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntivirusScannerTest {

    @Test
    void disabledScannerShouldDoNothing() {
        AntivirusProperties properties = new AntivirusProperties();
        AntivirusScanner scanner = new AntivirusScanner(properties);

        assertDoesNotThrow(() -> scanner.scan("content".getBytes(StandardCharsets.UTF_8)));
        assertTrue(scanner.ping());
    }

    @Test
    void enabledScannerShouldRejectEmptyContentBeforeConnecting() {
        AntivirusProperties properties = new AntivirusProperties();
        properties.setEnabled(true);
        AntivirusScanner scanner = new AntivirusScanner(properties);

        assertThrows(BadRequestException.class, () -> scanner.scan(new byte[0]));
    }

    @Test
    void scannerShouldAcceptCleanClamAvResponse() throws Exception {
        assertScanResponse("stream: OK\u0000", null);
    }

    @Test
    void scannerShouldRejectInfectedResponse() throws Exception {
        assertScanResponse("stream: Win.Test.EICAR_HDB-1 FOUND\u0000", BadRequestException.class);
    }

    @Test
    void scannerShouldFailClosedOnUnexpectedResponse() throws Exception {
        assertScanResponse("unexpected\u0000", ServiceUnavailableException.class);
    }

    private void assertScanResponse(String serverResponse,
                                    Class<? extends RuntimeException> expected) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            AtomicReference<Throwable> serverError = new AtomicReference<>();
            Thread responder = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    consumeInstream(socket.getInputStream());
                    socket.getOutputStream().write(
                            serverResponse.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                } catch (Throwable e) {
                    serverError.set(e);
                }
            });
            responder.start();

            AntivirusProperties properties = new AntivirusProperties();
            properties.setEnabled(true);
            properties.setHost("127.0.0.1");
            properties.setPort(server.getLocalPort());
            properties.setConnectTimeoutMillis(2_000);
            properties.setReadTimeoutMillis(2_000);
            AntivirusScanner scanner = new AntivirusScanner(properties);

            if (expected == null) {
                assertDoesNotThrow(() -> scanner.scan("content".getBytes(StandardCharsets.UTF_8)));
            } else {
                assertThrows(expected,
                        () -> scanner.scan("content".getBytes(StandardCharsets.UTF_8)));
            }

            responder.join(5_000);
            if (serverError.get() != null) {
                throw new AssertionError("Fake ClamAV server failed", serverError.get());
            }
        }
    }

    private void consumeInstream(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(input);
        byte[] command = new byte["zINSTREAM\u0000".length()];
        data.readFully(command);
        while (true) {
            int length = data.readInt();
            if (length == 0) {
                return;
            }
            data.skipNBytes(length);
        }
    }
}
