package org.example.helptreeservice.service;

import org.example.helptreeservice.config.ImageUploadProperties;
import org.example.helptreeservice.dto.images.ProcessedImage;
import org.example.helptreeservice.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageProcessorTest {

    private ImageUploadProperties properties;
    private ImageProcessor processor;

    @BeforeEach
    void setUp() {
        properties = new ImageUploadProperties();
        processor = new ImageProcessor(properties);
    }

    @Test
    void jpegIsReEncodedAndMetadataIsDropped() throws IOException {
        byte[] injected = injectJpegApp1(
                jpeg(64, 48),
                "Exif\0\0SECRET_GPS_COORDS".getBytes(StandardCharsets.US_ASCII));
        assertTrue(new String(injected, StandardCharsets.ISO_8859_1)
                .contains("SECRET_GPS_COORDS"));

        ProcessedImage result = processor.process(injected);

        assertEquals("image/jpeg", result.contentType());
        assertEquals(".jpg", result.extension());
        assertEquals(64, result.width());
        assertEquals(48, result.height());
        assertEquals(64, result.sha256().length());
        assertFalse(new String(result.data(), StandardCharsets.ISO_8859_1)
                .contains("SECRET_GPS_COORDS"));
        assertNotNull(decode(result.data()));
    }

    @Test
    void pngIsReEncodedAndTextMetadataIsDropped() throws IOException {
        byte[] injected = injectPngTextChunk(png(32, 24), "Comment", "SECRET_GPS_COORDS");
        assertTrue(new String(injected, StandardCharsets.ISO_8859_1)
                .contains("SECRET_GPS_COORDS"));

        ProcessedImage result = processor.process(injected);

        assertEquals("image/png", result.contentType());
        assertEquals(".png", result.extension());
        assertEquals(32, result.width());
        assertEquals(24, result.height());
        assertFalse(new String(result.data(), StandardCharsets.ISO_8859_1)
                .contains("SECRET_GPS_COORDS"));
        assertNotNull(decode(result.data()));
    }

    @Test
    void pngTransparencySurvivesTheReEncode() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0, 0, 0, 0));
        graphics.fillRect(0, 0, 8, 8);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        ProcessedImage result = processor.process(out.toByteArray());

        BufferedImage decoded = decode(result.data());
        assertTrue(decoded.getColorModel().hasAlpha());
        assertEquals(0, decoded.getRGB(0, 0) >>> 24);
    }

    @Test
    void gifIsValidatedAndStoredVerbatimToKeepAnimation() throws IOException {
        byte[] raw = gif(16, 16);

        ProcessedImage result = processor.process(raw);

        assertEquals("image/gif", result.contentType());
        assertEquals(".gif", result.extension());
        assertEquals(16, result.width());
        assertEquals(16, result.height());
        assertArrayEquals(raw, result.data());
    }

    @Test
    void formatComesFromMagicBytesNotFromTheClaimedType() throws IOException {
        ProcessedImage pngAsJpeg = processor.process(png(4, 4));

        assertEquals("image/png", pngAsJpeg.contentType());
        assertEquals(".png", pngAsJpeg.extension());
    }

    @Test
    void webpIsDetectedAndRejectedWithAnExplicitMessage() {
        byte[] webp = new byte[16];
        webp[0] = 'R';
        webp[1] = 'I';
        webp[2] = 'F';
        webp[3] = 'F';
        webp[8] = 'W';
        webp[9] = 'E';
        webp[10] = 'B';
        webp[11] = 'P';

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> processor.process(webp));
        assertTrue(error.getMessage().contains("WebP"));
    }

    @Test
    void arbitraryBytesAreRejectedEvenWithAnImageExtension() {
        byte[] text = "#!/bin/sh\nrm -rf /\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(BadRequestException.class, () -> processor.process(text));
    }

    @Test
    void emptyFileIsRejected() {
        assertThrows(BadRequestException.class, () -> processor.process(new byte[0]));
        assertThrows(BadRequestException.class, () -> processor.process(null));
    }

    @Test
    void truncatedJpegHeaderIsRejected() {
        assertThrows(BadRequestException.class, () -> processor.process(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16}));
    }

    @Test
    void fileOverTheByteLimitIsRejectedBeforeDecoding() {
        properties.setMaxSize(10);

        assertThrows(BadRequestException.class, () -> processor.process(new byte[11]));
    }

    @Test
    void widthAndHeightAreBoundedBeforeAFullDecode() throws IOException {
        properties.setMaxWidth(10);
        properties.setMaxHeight(10);

        assertThrows(BadRequestException.class, () -> processor.process(jpeg(20, 20)));
    }

    @Test
    void pixelBudgetIsEnforced() throws IOException {
        properties.setMaxPixels(100);

        assertThrows(BadRequestException.class, () -> processor.process(jpeg(20, 20)));
    }

    @Test
    void gifFrameCountIsBounded() throws IOException {
        properties.setGifMaxFrames(0);

        assertThrows(BadRequestException.class, () -> processor.process(gif(8, 8)));
    }

    @Test
    void gifPixelBudgetIsBounded() throws IOException {
        properties.setGifMaxTotalPixels(1);

        assertThrows(BadRequestException.class, () -> processor.process(gif(8, 8)));
    }

    @Test
    void corruptedPayloadWithAValidSignatureIsRejected() {
        byte[] pngSignatureOnly = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 13, 'I', 'H', 'D', 'R'};

        assertThrows(BadRequestException.class, () -> processor.process(pngSignatureOnly));
    }

    static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpeg", out));
        return out.toByteArray();
    }

    static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(10, 20, 30, 255));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", out));
        return out.toByteArray();
    }

    static byte[] gif(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "gif", out));
        return out.toByteArray();
    }

    private BufferedImage decode(byte[] data) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        assertNotNull(image);
        return image;
    }

    /**
     * Inserts a synthetic APP1 segment directly after SOI. Real cameras put
     * EXIF here; the exact contents do not matter, only that the pipeline drops
     * the segment.
     */
    private byte[] injectJpegApp1(byte[] jpeg, byte[] payload) {
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((length >>> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** Inserts a tEXt chunk after IHDR, mirroring a metadata-carrying PNG. */
    private byte[] injectPngTextChunk(byte[] png, String keyword, String value) {
        byte[] type = "tEXt".getBytes(StandardCharsets.US_ASCII);
        byte[] data = (keyword + "\0" + value).getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc32 = new CRC32();
        crc32.update(type);
        crc32.update(data);
        long crc = crc32.getValue();

        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        writeInt(chunk, data.length);
        chunk.write(type, 0, type.length);
        chunk.write(data, 0, data.length);
        writeInt(chunk, (int) crc);

        // Signature (8) + IHDR length/type/data/crc (4 + 4 + 13 + 4).
        int insertAt = 8 + 4 + 4 + 13 + 4;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, insertAt);
        byte[] chunkBytes = chunk.toByteArray();
        out.write(chunkBytes, 0, chunkBytes.length);
        out.write(png, insertAt, png.length - insertAt);
        return out.toByteArray();
    }

    private void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
