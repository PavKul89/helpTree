package org.example.helptreeservice.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.ImageUploadProperties;
import org.example.helptreeservice.dto.images.ProcessedImage;
import org.example.helptreeservice.entity.ImageObject;
import org.example.helptreeservice.enums.ImagePurpose;
import org.example.helptreeservice.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final MinioClient siloClient;
    private final Executor imageUploadExecutor;
    private final ImageProcessor imageProcessor;
    private final ImageObjectService imageObjectService;
    private final ImageUploadProperties properties;

    @Value("${silo.public-endpoint}")
    private String publicEndpoint;

    @Value("${silo.bucket}")
    private String bucket;

    private volatile boolean bucketInitialized;

    public String upload(MultipartFile file, Long ownerId, ImagePurpose purpose) {
        if (file == null) {
            throw new BadRequestException("Файл не выбран");
        }
        List<String> urls = uploadMultiple(List.of(file), ownerId, purpose);
        return urls.get(0);
    }

    /**
     * Validates and stores one or more files atomically from the caller's point
     * of view: if any step fails, every object already written to Silo is
     * removed again and no ownership row is committed.
     */
    public List<String> uploadMultiple(List<MultipartFile> files, Long ownerId, ImagePurpose purpose) {
        if (files == null) {
            throw new BadRequestException("Файлы не выбраны");
        }
        List<MultipartFile> present = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (present.isEmpty()) {
            throw new BadRequestException("Файлы не выбраны");
        }
        if (present.size() > properties.getMaxFiles()) {
            throw new BadRequestException("Слишком много файлов: максимум "
                    + properties.getMaxFiles());
        }

        long totalSize = 0;
        for (MultipartFile file : present) {
            validateFileSize(file);
            totalSize = Math.addExact(totalSize, file.getSize());
        }
        if (totalSize > properties.getMaxRequestSize()) {
            throw new BadRequestException("Суммарный размер файлов превышает "
                    + humanMegabytes(properties.getMaxRequestSize()));
        }

        List<ProcessedImage> processed = new ArrayList<>(present.size());
        for (MultipartFile file : present) {
            processed.add(imageProcessor.process(readBytes(file)));
        }

        return store(processed, ownerId, purpose);
    }

    private List<String> store(List<ProcessedImage> images, Long ownerId, ImagePurpose purpose) {
        List<String> uploadedKeys = new ArrayList<>(images.size());
        List<String> urls = new ArrayList<>(images.size());
        try {
            ensureBucket();
            for (ProcessedImage image : images) {
                String objectKey = UUID.randomUUID() + image.extension();
                putObject(objectKey, image);
                uploadedKeys.add(objectKey);
                urls.add(publicUrl(objectKey));
            }

            List<ImageObjectService.NewImage> registrations = new ArrayList<>(images.size());
            for (int i = 0; i < images.size(); i++) {
                ProcessedImage image = images.get(i);
                registrations.add(new ImageObjectService.NewImage(
                        uploadedKeys.get(i),
                        urls.get(i),
                        image.contentType(),
                        image.data().length,
                        image.width(),
                        image.height(),
                        image.sha256()));
            }
            imageObjectService.register(ownerId, registrations, purpose);
            return urls;
        } catch (Exception e) {
            for (String key : uploadedKeys) {
                tryRemoveObject(key);
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            log.error("Ошибка загрузки изображения в Silo: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка загрузки изображения", e);
        }
    }

    private void putObject(String objectKey, ProcessedImage image) throws Exception {
        try (InputStream input = new ByteArrayInputStream(image.data())) {
            siloClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(input, image.data().length, -1)
                    .contentType(image.contentType())
                    .build());
        }
        log.info("Изображение загружено в Silo: {} ({} байт, {})",
                objectKey, image.data().length, image.contentType());
    }

    public CompletableFuture<String> uploadAsync(MultipartFile file, Long ownerId, ImagePurpose purpose) {
        return CompletableFuture.supplyAsync(
                () -> upload(file, ownerId, purpose), imageUploadExecutor);
    }

    public CompletableFuture<List<String>> uploadMultipleAsync(
            List<MultipartFile> files, Long ownerId, ImagePurpose purpose) {
        // One task for the whole batch keeps compensation simple: either every
        // object is registered or every object is removed.
        return CompletableFuture.supplyAsync(
                () -> uploadMultiple(files, ownerId, purpose), imageUploadExecutor);
    }

    /**
     * Deletes an owned image (owner or admin). The object key always comes from
     * the ownership row, never from the client-supplied URL, so a crafted URL
     * cannot address another user's object.
     */
    public void deleteImage(Long requesterId, String role, String url) {
        Optional<ImageObject> marked = imageObjectService.markDeletedByUser(requesterId, role, url);
        marked.ifPresent(object -> scheduleRemovalAfterCommit(object.getObjectKey()));
    }

    /**
     * Releases the previous avatar after a successful replacement. If a post
     * still references the same bytes, the object is kept and only stops being
     * the current avatar.
     */
    public void releaseReplacedAvatar(Long ownerId, String oldUrl) {
        imageObjectService.releaseReplacedAvatar(ownerId, oldUrl)
                .ifPresent(object -> scheduleRemovalAfterCommit(object.getObjectKey()));
    }

    public void claimOwnedImages(Long ownerId, List<String> urls, ImagePurpose purpose) {
        imageObjectService.claimForOwner(ownerId, urls, purpose);
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

    public Optional<String> extractObjectKey(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(url);
            String imagePath = uri.getPath();
            String objectPrefix = "/" + bucket + "/";
            if (imagePath == null || !imagePath.startsWith(objectPrefix)) {
                return Optional.empty();
            }
            String objectName = imagePath.substring(objectPrefix.length());
            if (objectName.isBlank()
                    || objectName.contains("/")
                    || objectName.contains("..")
                    || objectName.contains("\\")) {
                return Optional.empty();
            }
            return Optional.of(objectName);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Best-effort removal used by the cleanup job. Returning false leaves the
     * tombstone in place so the next run retries instead of silently losing the
     * object reference.
     */
    public boolean tryRemoveObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return true;
        }
        try {
            deleteObject(objectKey);
            return true;
        } catch (Exception e) {
            log.warn("Не удалось удалить объект {} из Silo: {}", objectKey, e.getMessage());
            return false;
        }
    }

    private void scheduleRemovalAfterCommit(String objectKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tryRemoveObject(objectKey);
                }
            });
            return;
        }
        // markDeletedByUser owns its transaction when there is no outer one, so
        // the tombstone is already durable here.
        tryRemoveObject(objectKey);
    }

    private void deleteObject(String objectKey) throws Exception {
        siloClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
        log.info("Изображение удалено из Silo: {}", objectKey);
    }

    private synchronized void ensureBucket() throws Exception {
        if (bucketInitialized) {
            return;
        }
        if (!siloClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            siloClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        // Public read-only access. There is deliberately no ListBucket, no
        // anonymous PutObject and no DeleteObject: only the server credentials
        // can write or remove objects.
        String policy = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                """.formatted(bucket);
        siloClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(bucket)
                .config(policy)
                .build());
        bucketInitialized = true;
    }

    private String publicUrl(String objectKey) {
        return publicEndpoint.replaceAll("/+$", "") + "/" + bucket + "/" + objectKey;
    }

    private void validateFileSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Файл не выбран");
        }
        if (file.getSize() <= 0) {
            throw new BadRequestException("Файл пустой");
        }
        if (file.getSize() > properties.getMaxSize()) {
            throw new BadRequestException("Размер файла превышает "
                    + humanMegabytes(properties.getMaxSize()));
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new BadRequestException("Файл пустой");
            }
            if (bytes.length > properties.getMaxSize()) {
                throw new BadRequestException("Размер файла превышает "
                        + humanMegabytes(properties.getMaxSize()));
            }
            return bytes;
        } catch (IOException e) {
            throw new BadRequestException("Не удалось прочитать файл");
        }
    }

    private String humanMegabytes(long bytes) {
        return (bytes / (1024 * 1024)) + " МБ";
    }
}
