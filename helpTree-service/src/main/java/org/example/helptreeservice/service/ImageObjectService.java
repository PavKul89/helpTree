package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.ImageUploadProperties;
import org.example.helptreeservice.entity.ImageObject;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.ImagePurpose;
import org.example.helptreeservice.enums.ImageStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.ImageObjectRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ownership, quota and lifecycle operations for {@link ImageObject}.
 *
 * <p>All mutating methods are separate Spring beans from the upload worker so
 * the transaction proxy actually applies; an async upload thread never holds a
 * user row lock while the bytes are streamed to Silo.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageObjectService {

    private final ImageObjectRepository imageObjectRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ImageUploadProperties properties;

    /**
     * Registers all objects of one upload under a single owner lock. The lock
     * serialises concurrent uploads from the same user, so the quota check can
     * never be bypassed by two requests that both read the old total.
     */
    @Transactional
    public List<ImageObject> register(Long ownerId, List<NewImage> images, ImagePurpose purpose) {
        if (ownerId == null) {
            throw new BadRequestException("Не удалось определить владельца изображения");
        }
        if (images == null || images.isEmpty()) {
            throw new BadRequestException("Файл не выбран");
        }

        User owner = userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (Boolean.TRUE.equals(owner.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }

        long incomingBytes = 0;
        for (NewImage image : images) {
            incomingBytes = Math.addExact(incomingBytes, Math.max(0, image.sizeBytes()));
        }
        long currentBytes = imageObjectRepository.sumSizeByOwnerAndStatus(ownerId, ImageStatus.ACTIVE);
        long currentCount = imageObjectRepository.countByOwnerIdAndStatus(ownerId, ImageStatus.ACTIVE);

        if (currentBytes + incomingBytes > properties.getQuotaBytes()) {
            throw new BadRequestException("Превышена квота изображений: "
                    + (properties.getQuotaBytes() / (1024 * 1024)) + " МБ");
        }
        if (currentCount + images.size() > properties.getQuotaCount()) {
            throw new BadRequestException("Превышено допустимое количество изображений: "
                    + properties.getQuotaCount());
        }

        List<ImageObject> saved = images.stream()
                .map(image -> ImageObject.builder()
                        .ownerId(ownerId)
                        .objectKey(image.objectKey())
                        .url(image.url())
                        .contentType(image.contentType())
                        .sizeBytes(image.sizeBytes())
                        .width(image.width())
                        .height(image.height())
                        .sha256(image.sha256())
                        .purpose(purpose)
                        .status(ImageStatus.ACTIVE)
                        .createdAt(LocalDateTime.now())
                        .build())
                .toList();

        log.info("Регистрация {} изображений для пользователя {} ({} байт)",
                saved.size(), ownerId, incomingBytes);
        return imageObjectRepository.saveAll(saved);
    }

    /**
     * Checks that every URL belongs to the author and is still usable, then
     * updates its purpose. Post create/update calls this before persisting, so
     * a client cannot attach an image uploaded by somebody else.
     */
    @Transactional
    public void claimForOwner(Long ownerId, Collection<String> urls, ImagePurpose purpose) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        List<String> distinct = urls.stream()
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return;
        }
        if (distinct.size() > properties.getMaxFiles()) {
            throw new BadRequestException("Слишком много изображений: максимум "
                    + properties.getMaxFiles());
        }

        List<ImageObject> objects = imageObjectRepository.findAllByUrlInAndStatus(
                distinct, ImageStatus.ACTIVE);
        Map<String, ImageObject> byUrl = new HashMap<>();
        for (ImageObject object : objects) {
            byUrl.put(object.getUrl(), object);
        }

        for (String url : distinct) {
            ImageObject object = byUrl.get(url);
            if (object == null) {
                throw new BadRequestException("Изображение не найдено или уже удалено");
            }
            if (!ownerId.equals(object.getOwnerId())) {
                throw new ForbiddenException("Нельзя использовать чужое изображение");
            }
            if (object.getPurpose() == ImagePurpose.AVATAR && purpose != ImagePurpose.AVATAR) {
                throw new BadRequestException("Аватар нельзя использовать как изображение поста");
            }
            // Chat attachments are deleted together with their message and must
            // never become a post/avatar image (and vice versa).
            if (object.getPurpose() == ImagePurpose.CHAT && purpose != ImagePurpose.CHAT) {
                throw new BadRequestException("Изображение из чата нельзя использовать в другом месте");
            }
            if (purpose == ImagePurpose.CHAT
                    && object.getPurpose() != ImagePurpose.CHAT
                    && object.getPurpose() != ImagePurpose.GENERAL) {
                throw new BadRequestException("Это изображение уже используется в другом месте");
            }
            if (object.getPurpose() != purpose) {
                object.setPurpose(purpose);
                imageObjectRepository.save(object);
            }
        }
    }

    @Transactional(readOnly = true)
    public Optional<ImageObject> findActiveByUrl(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        return imageObjectRepository.findByUrlAndStatus(url, ImageStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Optional<ImageObject> findActiveByObjectKey(String objectKey) {
        return imageObjectRepository.findByObjectKeyAndStatus(objectKey, ImageStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public boolean isOwnedBy(Long ownerId, String url) {
        return findActiveByUrl(url)
                .map(object -> ownerId.equals(object.getOwnerId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasOwnershipRow(String objectKey) {
        return imageObjectRepository.existsByObjectKey(objectKey);
    }

    /**
     * Explicit user/admin deletion. Every reference is detached first, then the
     * row becomes a tombstone. The object is removed from Silo after commit by
     * the caller; if that call fails the cleanup job retries it.
     */
    @Transactional
    public Optional<ImageObject> markDeletedByUser(Long requesterId, String role, String url) {
        Optional<ImageObject> found = imageObjectRepository.findByUrl(url);
        if (found.isEmpty()) {
            throw new NotFoundException("Изображение не найдено");
        }

        ImageObject object = found.get();
        if (!object.getOwnerId().equals(requesterId) && !"ADMIN".equals(role)) {
            throw new ForbiddenException("Вы можете удалять только свои изображения");
        }
        if (object.getStatus() == ImageStatus.DELETED) {
            // Repeated DELETE is idempotent. The cleanup job still owns the
            // eventual Silo removal if a previous attempt failed.
            return Optional.empty();
        }

        userRepository.clearAvatarUrl(url);
        postRepository.deletePostImageUrl(url);

        object.setStatus(ImageStatus.DELETED);
        object.setDeletedAt(LocalDateTime.now());
        imageObjectRepository.save(object);
        log.info("Изображение {} помечено на удаление пользователем {}",
                object.getObjectKey(), requesterId);
        return Optional.of(object);
    }

    /**
     * An avatar was replaced by a new upload. The old object is released only
     * if no post still uses it; a shared image stays alive.
     */
    @Transactional
    public Optional<ImageObject> releaseReplacedAvatar(Long ownerId, String oldUrl) {
        if (oldUrl == null || oldUrl.isBlank()) {
            return Optional.empty();
        }
        Optional<ImageObject> found = imageObjectRepository.findByUrlAndStatus(
                oldUrl, ImageStatus.ACTIVE);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        ImageObject object = found.get();
        if (!ownerId.equals(object.getOwnerId())) {
            return Optional.empty();
        }
        if (postRepository.existsPostImageUrl(oldUrl)) {
            // Another feature still uses the bytes; just stop treating it as
            // the current avatar (the caller already replaced users.avatar_url).
            return Optional.empty();
        }

        object.setStatus(ImageStatus.DELETED);
        object.setDeletedAt(LocalDateTime.now());
        imageObjectRepository.save(object);
        return Optional.of(object);
    }

    @Transactional
    public int markUnreferencedDeleted(Long id, LocalDateTime cutoff) {
        return imageObjectRepository.markUnreferencedDeleted(id, cutoff, LocalDateTime.now());
    }

    /**
     * Deletes the ownership rows of chat attachments whose message was removed
     * by the retention sweep. The Silo cleanup job removes the bytes later.
     */
    @Transactional
    public int markChatAttachmentsDeleted(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return 0;
        }
        List<ImageObject> objects = imageObjectRepository.findAllByUrlInAndStatus(
                urls, ImageStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        int marked = 0;
        for (ImageObject object : objects) {
            if (object.getPurpose() != ImagePurpose.CHAT) {
                continue;
            }
            object.setStatus(ImageStatus.DELETED);
            object.setDeletedAt(now);
            imageObjectRepository.save(object);
            marked++;
        }
        return marked;
    }

    @Transactional
    public int deleteDeletedRow(Long id) {
        return imageObjectRepository.deleteDeletedById(id, ImageStatus.DELETED);
    }

    @Transactional(readOnly = true)
    public List<ImageObject> findUnreferencedActive(LocalDateTime cutoff, int limit) {
        return imageObjectRepository.findUnreferencedActive(cutoff, limit);
    }

    @Transactional(readOnly = true)
    public List<ImageObject> findDeletedBefore(LocalDateTime cutoff, int limit) {
        return imageObjectRepository.findDeletedBefore(cutoff, limit);
    }

    public record NewImage(
            String objectKey,
            String url,
            String contentType,
            long sizeBytes,
            int width,
            int height,
            String sha256) {
    }
}
