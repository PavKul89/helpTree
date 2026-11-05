package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.ImageUploadProperties;
import org.example.helptreeservice.dto.images.ProcessedImage;
import org.example.helptreeservice.enums.ImageFormat;
import org.example.helptreeservice.exception.BadRequestException;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

/**
 * Decodes an upload and returns the exact bytes that may be stored.
 *
 * <p>Security properties:</p>
 * <ul>
 *   <li>the format comes from magic bytes, never from a client header;</li>
 *   <li>dimensions are read before a full decode, so a small compressed file
 *       cannot allocate an unbounded image buffer;</li>
 *   <li>JPEG and PNG are decoded and re-encoded, which validates the payload
 *       and drops EXIF/ICC/XMP metadata (including GPS coordinates);</li>
 *   <li>GIF is decoded and validated but kept verbatim, because re-encoding
 *       would destroy animation. GIF has no EXIF block.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageProcessor {

    static {
        // Keep decode buffers in memory: a temp-file cache makes the limits
        // depend on disk space and leaves attacker-controlled bytes on disk.
        ImageIO.setUseCache(false);
    }

    private final ImageUploadProperties properties;

    public ProcessedImage process(byte[] raw) {
        if (raw == null || raw.length == 0) {
            throw new BadRequestException("Файл не выбран");
        }
        if (raw.length > properties.getMaxSize()) {
            throw new BadRequestException("Размер файла превышает "
                    + humanMegabytes(properties.getMaxSize()));
        }

        ImageFormat format = ImageFormat.detect(raw)
                .orElseThrow(() -> new BadRequestException(
                        "Файл не является изображением JPEG, PNG или GIF"));
        if (!format.isSupported()) {
            throw new BadRequestException(
                    "Формат WebP не поддерживается. Загрузите JPEG, PNG или GIF");
        }

        if (format == ImageFormat.GIF) {
            return validateGif(raw, format);
        }
        return transcode(raw, format);
    }

    private ProcessedImage transcode(byte[] raw, ImageFormat format) {
        ImageReader reader = readerFor(format);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            if (input == null) {
                throw new BadRequestException("Не удалось прочитать изображение");
            }
            reader.setInput(input, true, true);

            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            validateDimensions(width, height, format);

            BufferedImage decoded = reader.read(0);
            if (decoded == null) {
                throw new BadRequestException("Не удалось декодировать изображение");
            }
            validateDimensions(decoded.getWidth(), decoded.getHeight(), format);

            BufferedImage prepared = format == ImageFormat.JPEG
                    ? flattenForJpeg(decoded)
                    : decoded;
            byte[] encoded = encode(prepared, format);

            if (encoded.length == 0) {
                throw new BadRequestException("Не удалось обработать изображение");
            }
            if (encoded.length > properties.getMaxSize()) {
                throw new BadRequestException("Обработанное изображение превышает допустимый размер");
            }

            return new ProcessedImage(
                    encoded,
                    format.getContentType(),
                    format.getExtension(),
                    decoded.getWidth(),
                    decoded.getHeight(),
                    sha256(encoded));
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.info("Отклонено изображение с сигнатурой {}: {}", format, e.getMessage());
            throw new BadRequestException("Файл повреждён или не является корректным изображением");
        } finally {
            reader.dispose();
        }
    }

    private ProcessedImage validateGif(byte[] raw, ImageFormat format) {
        ImageReader reader = readerFor(format);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            if (input == null) {
                throw new BadRequestException("Не удалось прочитать изображение");
            }
            // GIF's reader needs search access (getNumImages) and rejects
            // seekForwardOnly together with allowSearch.
            reader.setInput(input, false, true);

            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            validateDimensions(width, height, format);

            int frames = reader.getNumImages(true);
            if (frames < 1) {
                throw new BadRequestException("Файл не содержит изображений");
            }
            if (frames > properties.getGifMaxFrames()) {
                throw new BadRequestException("Слишком много кадров в GIF");
            }
            long totalPixels = (long) width * height * frames;
            if (totalPixels > properties.getGifMaxTotalPixels()) {
                throw new BadRequestException("GIF превышает допустимый бюджет пикселей");
            }

            // Decode the first frame to prove the stream is valid. Animated
            // frames stay untouched; the total-pixel budget bounds the damage
            // a pathological animation can cause.
            BufferedImage firstFrame = reader.read(0);
            if (firstFrame == null) {
                throw new BadRequestException("Не удалось декодировать изображение");
            }
            validateDimensions(firstFrame.getWidth(), firstFrame.getHeight(), format);

            return new ProcessedImage(
                    raw,
                    format.getContentType(),
                    format.getExtension(),
                    width,
                    height,
                    sha256(raw));
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.info("Отклонён GIF: {}", e.getMessage());
            throw new BadRequestException("Файл повреждён или не является корректным GIF");
        } finally {
            reader.dispose();
        }
    }

    private ImageReader readerFor(ImageFormat format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.getReaderFormat());
        if (!readers.hasNext()) {
            throw new BadRequestException("В этой среде нет декодера для " + format.getContentType());
        }
        return readers.next();
    }

    private void validateDimensions(int width, int height, ImageFormat format) {
        if (width <= 0 || height <= 0) {
            throw new BadRequestException("Некорректные размеры изображения");
        }
        if (width > properties.getMaxWidth() || height > properties.getMaxHeight()) {
            throw new BadRequestException("Разрешение изображения превышает "
                    + properties.getMaxWidth() + "×" + properties.getMaxHeight());
        }
        long pixels = (long) width * height;
        if (pixels > properties.getMaxPixels()) {
            throw new BadRequestException("Слишком много пикселей в изображении");
        }
        if (format == ImageFormat.GIF) {
            // GIF validation adds the frame budget; the per-frame check stays
            // here so every decoded frame is bounded too.
            if (pixels > properties.getGifMaxTotalPixels()) {
                throw new BadRequestException("GIF превышает допустимый бюджет пикселей");
            }
        }
    }

    /**
     * JPEG has no alpha channel. Draw the decoded image on white so transparent
     * PNG pixels do not turn black during the format conversion.
     */
    private BufferedImage flattenForJpeg(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private byte[] encode(BufferedImage image, ImageFormat format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (format == ImageFormat.JPEG) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG");
            if (!writers.hasNext()) {
                throw new IOException("No JPEG writer available");
            }
            ImageWriter writer = writers.next();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(stream);
                ImageWriteParam parameter = writer.getDefaultWriteParam();
                if (parameter.canWriteCompressed()) {
                    parameter.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    parameter.setCompressionQuality(clampQuality(properties.getJpegQuality()));
                }
                writer.write(null, new IIOImage(image, null, null), parameter);
            } finally {
                writer.dispose();
            }
        } else if (!ImageIO.write(image, "PNG", output)) {
            throw new IOException("No PNG writer available");
        }
        return output.toByteArray();
    }

    private float clampQuality(float quality) {
        if (quality <= 0f) {
            return 0.1f;
        }
        return Math.min(quality, 1f);
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(Character.forDigit((value >> 4) & 0x0F, 16));
                builder.append(Character.forDigit(value & 0x0F, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String humanMegabytes(long bytes) {
        return (bytes / (1024 * 1024)) + " МБ";
    }
}
