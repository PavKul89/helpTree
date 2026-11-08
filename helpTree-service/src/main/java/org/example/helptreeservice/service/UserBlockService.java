package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.FeatureFlags;
import org.example.helptreeservice.dto.users.BlockUserRequest;
import org.example.helptreeservice.dto.users.BlockedUserResponse;
import org.example.helptreeservice.dto.users.UserBlockResponse;
import org.example.helptreeservice.dto.users.UserBlockStatusResponse;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserBlockRelation;
import org.example.helptreeservice.enums.FeatureFlag;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.UserBlockRelationRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Single source of truth for user-initiated blocks (Block 16).
 *
 * <p>Blocking is <strong>bidirectional inside a scope</strong>: after A blocks B
 * with {@code MESSAGES}, neither A nor B can create a chat or send a message to
 * the other. The row is stored once, against the pair, and every check looks at
 * both directions.</p>
 *
 * <p>This service never touches {@code users.blockedAt}/{@code blockedUntil} or
 * the moderation {@code user_blocks} journal. Those are account sanctions; this
 * is a user preference.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserBlockService {

    private final UserBlockRelationRepository blockRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final FeatureFlags featureFlags;

    /**
     * Creates a block or updates the scope/expiry of the existing one. The
     * repository performs a single {@code INSERT ... ON CONFLICT DO UPDATE}, so
     * two concurrent requests cannot create duplicate pairs or lose an update.
     */
    @Transactional
    public UserBlockResponse blockUser(Long blockerId, Long blockedId, BlockUserRequest request) {
        featureFlags.requireEnabled(FeatureFlag.USER_BLOCKS);
        if (blockerId == null) {
            throw new ForbiddenException("Вы не авторизованы");
        }
        if (blockerId.equals(blockedId)) {
            throw new BadRequestException("Нельзя заблокировать самого себя");
        }

        requireActiveUser(blockerId);
        requireActiveUser(blockedId);

        UserBlockScope scope = request != null && request.getScope() != null
                ? request.getScope()
                : UserBlockScope.ALL;
        LocalDateTime expiresAt = request != null ? request.getExpiresAt() : null;
        LocalDateTime now = LocalDateTime.now();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new BadRequestException("Срок блокировки должен быть в будущем");
        }

        blockRepository.upsert(blockerId, blockedId, scope.name(), now, expiresAt);

        UserBlockRelation saved = blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseThrow(() -> new NotFoundException("Блокировка не найдена после сохранения"));
        log.info("Пользователь {} заблокировал {} в области {} до {}",
                blockerId, blockedId, scope, expiresAt);
        return toResponse(saved);
    }

    /**
     * Removes the block. Repeated calls are a no-op: the endpoint is idempotent.
     * The target user must exist so a typo is reported as 404 rather than
     * silently succeeding.
     */
    @Transactional
    public void unblockUser(Long blockerId, Long blockedId) {
        featureFlags.requireEnabled(FeatureFlag.USER_BLOCKS);
        if (blockerId == null) {
            throw new ForbiddenException("Вы не авторизованы");
        }
        if (blockerId.equals(blockedId)) {
            throw new BadRequestException("Нельзя разблокировать самого себя");
        }
        requireActiveUser(blockedId);

        long removed = blockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
        if (removed > 0) {
            log.info("Пользователь {} разблокировал {}", blockerId, blockedId);
        } else {
            log.debug("Повторная разблокировка {} -> {}: связи нет", blockerId, blockedId);
        }
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> getBlockedUsers(Long blockerId) {
        featureFlags.requireEnabled(FeatureFlag.USER_BLOCKS);
        requireActiveUser(blockerId);
        return blockRepository.findActiveByBlockerId(blockerId, LocalDateTime.now()).stream()
                .map(relation -> BlockedUserResponse.builder()
                        .id(relation.getId())
                        .userId(relation.getBlocked().getId())
                        .name(relation.getBlocked().getName())
                        .avatarUrl(imageService.refreshUrl(relation.getBlocked().getAvatarUrl()))
                        .scope(relation.getScope())
                        .createdAt(relation.getCreatedAt())
                        .expiresAt(relation.getExpiresAt())
                        .build())
                .toList();
    }

    /**
     * Viewer-specific state. A user may be blocked by the target, may block the
     * target, or both. Expired rows are ignored but not deleted: the unique pair
     * keeps its identity until the owner removes it or creates it again.
     */
    @Transactional(readOnly = true)
    public UserBlockStatusResponse getBlockStatus(Long viewerId, Long targetId) {
        featureFlags.requireEnabled(FeatureFlag.USER_BLOCKS);
        if (viewerId == null) {
            throw new ForbiddenException("Вы не авторизованы");
        }
        requireActiveUser(targetId);

        if (viewerId.equals(targetId)) {
            return UserBlockStatusResponse.builder()
                    .userId(targetId)
                    .blockedByMe(false)
                    .blockedMe(false)
                    .active(false)
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<UserBlockRelation> mine = activeRelation(viewerId, targetId, now);
        Optional<UserBlockRelation> theirs = activeRelation(targetId, viewerId, now);
        Optional<UserBlockRelation> effective = mine.isPresent() ? mine : theirs;

        return UserBlockStatusResponse.builder()
                .userId(targetId)
                .blockedByMe(mine.isPresent())
                .blockedMe(theirs.isPresent())
                .active(effective.isPresent())
                .scope(effective.map(UserBlockRelation::getScope).orElse(null))
                .expiresAt(effective.map(UserBlockRelation::getExpiresAt).orElse(null))
                .build();
    }

    /**
     * Enforcement check used by every approved scenario. The row is matched in
     * both directions and narrowed to {@code scope} (or {@code ALL}).
     */
    @Transactional(readOnly = true)
    public boolean isInteractionBlocked(Long firstId, Long secondId, UserBlockScope scope) {
        if (firstId == null || secondId == null || firstId.equals(secondId)) {
            return false;
        }
        return blockRepository.existsEffectiveBetween(
                firstId, secondId, List.of(UserBlockScope.matching(scope)), LocalDateTime.now());
    }

    /** Fails the current operation with 403 when the block covers the scenario. */
    @Transactional(readOnly = true)
    public void assertInteractionAllowed(Long firstId, Long secondId, UserBlockScope scope, String message) {
        if (isInteractionBlocked(firstId, secondId, scope)) {
            throw new ForbiddenException(message);
        }
    }

    private Optional<UserBlockRelation> activeRelation(Long blockerId, Long blockedId, LocalDateTime now) {
        return blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .filter(relation -> relation.isActiveAt(now));
    }

    private User requireActiveUser(Long userId) {
        if (userId == null) {
            throw new NotFoundException("Пользователь не найден");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + userId));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден с id: " + userId);
        }
        return user;
    }

    private UserBlockResponse toResponse(UserBlockRelation relation) {
        return UserBlockResponse.builder()
                .id(relation.getId())
                .blockerId(relation.getBlocker().getId())
                .blockedId(relation.getBlocked().getId())
                .scope(relation.getScope())
                .createdAt(relation.getCreatedAt())
                .expiresAt(relation.getExpiresAt())
                .active(relation.isActiveAt(LocalDateTime.now()))
                .build();
    }
}
