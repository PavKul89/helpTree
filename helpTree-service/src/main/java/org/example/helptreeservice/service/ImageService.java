package org.example.helptreeservice.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${upload.max-size}")
    private long maxSize;

    @Value("${upload.allowed-types}")
    private List<String> allowedTypes;

    public String upload(MultipartFile file) {
        validateFile(file);
        String filename = generateFilename(file);
        
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(filename)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(filename)
                            .expiry(7, TimeUnit.DAYS)
                            .build());

            log.info("✅ Изображение загружено: {}", filename);
            return url;

        } catch (Exception e) {
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

    public void delete(String url) {
        String filename = extractFilename(url);
        
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(filename)
                    .build());
            log.info("✅ Изображение удалено: {}", filename);
        } catch (Exception e) {
            log.error("❌ Ошибка удаления изображения: {}", e.getMessage());
        }
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
        switch (contentType) {
            case "image/jpeg": return ".jpg";
            case "image/png": return ".png";
            case "image/webp": return ".webp";
            case "image/gif": return ".gif";
            default: return ".jpg";
        }
    }

    private String extractFilename(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }
}
