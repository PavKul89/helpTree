package org.example.helptreeservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ImageService {

    private final Executor imageUploadExecutor;

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    @Value("${upload.max-size}")
    private long maxSize;

    @Value("${upload.allowed-types}")
    private List<String> allowedTypes;

    @Value("${server.port:8080}")
    private String serverPort;

    public ImageService(Executor imageUploadExecutor) {
        this.imageUploadExecutor = imageUploadExecutor;
    }

    public String upload(MultipartFile file) {
        validateFile(file);
        String filename = generateFilename(file);
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(uploadPath);
            Path target = uploadPath.resolve(filename).normalize();
            if (!target.startsWith(uploadPath)) {
                throw new BadRequestException("Недопустимое имя файла");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            log.info("✅ Изображение загружено: {}", filename);
            return "http://localhost:" + serverPort + "/uploads/" + filename;
        } catch (IOException e) {
            log.error("❌ Ошибка загрузки изображения: {}", e.getMessage());
            throw new RuntimeException("Ошибка загрузки изображения", e);
        }
    }

    public List<String> uploadMultiple(List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                urls.add(upload(file));
            }
        }
        return urls;
    }

    @Async("imageUploadExecutor")
    public CompletableFuture<String> uploadAsync(MultipartFile file) {
        return CompletableFuture.completedFuture(upload(file));
    }

    public CompletableFuture<List<String>> uploadMultipleAsync(List<MultipartFile> files) {
        List<CompletableFuture<String>> futures = files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .map(this::uploadAsync)
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    public void delete(String url) {
        String filename = extractFilename(url);
        if (filename == null || filename.isBlank()) {
            return;
        }
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = uploadPath.resolve(filename).normalize();
        if (!target.startsWith(uploadPath)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
            log.info("✅ Изображение удалено: {}", filename);
        } catch (IOException e) {
            log.error("❌ Ошибка удаления изображения: {}", e.getMessage());
        }
    }

    public String refreshUrl(String storedUrl) {
        return storedUrl;
    }

    public List<String> refreshUrls(List<String> urls) {
        if (urls == null) return urls;
        return urls;
    }

    public List<String> refreshUrlsBatch(List<String> urls) {
        if (urls == null || urls.isEmpty()) return urls;
        return urls;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Файл не выбран");
        }

        if (file.getSize() > maxSize) {
            throw new BadRequestException("Размер файла превышает 10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new BadRequestException("Недопустимый тип файла. Разрешены: jpg, png, webp, gif");
        }
    }

    private String generateFilename(MultipartFile file) {
        String ext = getExtension(file.getOriginalFilename(), file.getContentType());
        return UUID.randomUUID() + ext;
    }

    private String getExtension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf("."));
        }
        if (contentType == null) {
            return ".jpg";
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }

    private String extractFilename(String url) {
        if (url == null) return null;
        int lastSlash = url.lastIndexOf("/");
        if (lastSlash == -1) return url;
        String filename = url.substring(lastSlash + 1);
        if (filename.contains("?")) {
            filename = filename.substring(0, filename.indexOf("?"));
        }
        return filename;
    }
}
