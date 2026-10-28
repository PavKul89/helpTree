package org.example.helptreeservice.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ImageService {

    private final MinioClient siloClient;
    private final Executor imageUploadExecutor;

    @Value("${upload.max-size}")
    private long maxSize;

    @Value("${upload.allowed-types}")
    private List<String> allowedTypes;

    @Value("${silo.public-endpoint}")
    private String publicEndpoint;

    @Value("${silo.bucket}")
    private String bucket;

    private volatile boolean bucketInitialized;

    public ImageService(MinioClient siloClient, Executor imageUploadExecutor) {
        this.siloClient = siloClient;
        this.imageUploadExecutor = imageUploadExecutor;
    }

    public String upload(MultipartFile file) {
        validateFile(file);
        String filename = generateFilename(file);

        try (InputStream input = file.getInputStream()) {
            ensureBucket();
            siloClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(filename)
                    .stream(input, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("Изображение загружено в Silo: {}", filename);
            return publicEndpoint.replaceAll("/+$", "") + "/" + bucket + "/" + filename;
        } catch (Exception e) {
            log.error("Ошибка загрузки изображения в Silo: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка загрузки изображения", e);
        }
    }

    private synchronized void ensureBucket() throws Exception {
        if (bucketInitialized) {
            return;
        }
        if (!siloClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            siloClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        String policy = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                """.formatted(bucket);
        siloClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(bucket)
                .config(policy)
                .build());
        bucketInitialized = true;
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
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            URI imageUri = URI.create(url);
            String imagePath = imageUri.getPath();
            String objectPrefix = "/" + bucket + "/";
            if (imagePath != null && imagePath.startsWith(objectPrefix)
                    && sameOrigin(imageUri, URI.create(publicEndpoint))) {
                String objectName = imagePath.substring(objectPrefix.length());
                if (!objectName.isBlank() && !objectName.contains("/")) {
                    ensureBucket();
                    siloClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
                    log.info("Изображение удалено из Silo: {}", objectName);
                }
                return;
            }

        } catch (Exception e) {
            log.error("Ошибка удаления изображения: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка удаления изображения", e);
        }
    }

    private boolean sameOrigin(URI first, URI second) {
        return first.getScheme() != null
                && first.getHost() != null
                && second.getScheme() != null
                && second.getHost() != null
                && first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && first.getPort() == second.getPort();
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
        String ext = getExtension(file.getContentType());
        return UUID.randomUUID() + ext;
    }

    private String getExtension(String contentType) {
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

}
