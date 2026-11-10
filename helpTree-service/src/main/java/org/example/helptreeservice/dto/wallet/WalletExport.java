package org.example.helptreeservice.dto.wallet;

/**
 * Rendered wallet export. The controller only adds HTTP headers, so the service
 * stays independent from the web layer.
 */
public record WalletExport(byte[] content, String contentType, String filename) {
}
