package org.example.helptreeservice.service;

import jakarta.persistence.criteria.Predicate;
import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.wallet.CoinTransactionDto;
import org.example.helptreeservice.dto.wallet.WalletDto;
import org.example.helptreeservice.dto.wallet.WalletOperationResult;
import org.example.helptreeservice.dto.wallet.WalletPricingDto;
import org.example.helptreeservice.entity.AdminCoinAudit;
import org.example.helptreeservice.entity.CoinTransaction;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.TransactionDirection;
import org.example.helptreeservice.enums.TransactionType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.AdminCoinAuditRepository;
import org.example.helptreeservice.repository.CoinTransactionRepository;
import org.example.helptreeservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Single entry point for every help_coins change.
 *
 * <p>All mutations lock the user row with {@code SELECT ... FOR UPDATE} and write
 * the ledger row in the same transaction. Money can therefore never be lost by
 * a read-modify-write race, a balance can never become negative, and a retry
 * with the same business reference/idempotency key is a no-op instead of a
 * second accrual or debit.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class WalletService {

    private static final int MAX_FIRST_HELP_BONUSES = 3;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_REFERENCE_LENGTH = 120;
    private static final java.util.regex.Pattern IDEMPOTENCY_KEY_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._:-]+$");
    private static final java.util.regex.Pattern HEX_COLOR_PATTERN =
            java.util.regex.Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final UserRepository userRepository;
    private final CoinTransactionRepository transactionRepository;
    private final AdminCoinAuditRepository adminAuditRepository;
    private final EconomyConfig economyConfig;
    private final PaginationConfig paginationConfig;

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public WalletDto getWallet(Long userId) {
        User user = getUserOrThrow(userId);
        Long totalEarned = transactionRepository.getTotalEarned(userId);
        Long totalSpent = transactionRepository.getTotalSpent(userId);

        return WalletDto.builder()
                .userId(userId)
                .balance(currentBalance(user))
                .totalEarned(totalEarned != null ? totalEarned : 0L)
                .totalSpent(totalSpent != null ? totalSpent : 0L)
                .vipUntil(user.getVipUntil())
                .vipActive(user.isVip())
                .nicknameColor(user.getNicknameColor())
                .blocked(isActivelyBlocked(user, LocalDateTime.now()))
                .blockedAt(user.getBlockedAt())
                .blockedUntil(user.getBlockedUntil())
                .debtCount(nvl(user.getDebtCount()))
                .build();
    }

    @Transactional(readOnly = true)
    public WalletPricingDto getPricing() {
        return WalletPricingDto.builder()
                .boostPrice(economyConfig.getBoostPrice())
                .boostHours(economyConfig.getBoostHours())
                .vipPrice(economyConfig.getVipPrice())
                .vipDays(economyConfig.getVipDays())
                .nicknameColorPrice(economyConfig.getNicknameColorPrice())
                .accountUnblockPrice(economyConfig.getAccountUnblockPrice())
                .accountUnblockDebtReduction(economyConfig.getAccountUnblockDebtReduction())
                .debtBlockThreshold(economyConfig.getDebtBlockThreshold())
                .dailyBonusCoins(economyConfig.getCoinsPerDailyLogin())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<CoinTransactionDto> getTransactions(
            Long userId,
            int page,
            int size,
            TransactionType type,
            LocalDateTime from,
            LocalDateTime to,
            TransactionDirection direction) {

        if (page < 0) {
            throw new BadRequestException("Параметр page не может быть отрицательным");
        }
        if (size < 1) {
            throw new BadRequestException("Параметр size должен быть положительным");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("Начало периода не может быть позже конца периода");
        }

        int resolvedSize = Math.min(size, paginationConfig.getMaxPageSize());
        Pageable pageable = PageRequest.of(page, resolvedSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Specification<CoinTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (direction == TransactionDirection.IN) {
                predicates.add(cb.greaterThan(root.get("amount"), 0L));
            } else if (direction == TransactionDirection.OUT) {
                predicates.add(cb.lessThan(root.get("amount"), 0L));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return transactionRepository.findAll(spec, pageable).map(this::toDto);
    }

    // -------------------------------------------------------------- accruals

    /**
     * HELP_GIVEN plus the one-time new-helper bonus. Both rows are written under
     * the same helper row lock; a retry for the same post returns the existing
     * rows and changes nothing.
     */
    public void addCoinsForHelp(Long helperId, Long receiverId, Long postId) {
        log.info("Начисление монет за помощь: helper={}, receiver={}, post={}", helperId, receiverId, postId);

        User helper = lockUser(helperId);
        AppliedTransaction base = applyDelta(
                helper,
                economyConfig.getCoinsPerHelp(),
                TransactionType.HELP_GIVEN,
                "Помощь пользователю",
                receiverId,
                postId,
                reference("help-given:post:" + postId));

        if (!base.applied()) {
            log.info("Награда за помощь уже начислена: helper={}, post={}", helperId, postId);
            return;
        }

        Long firstHelpCount = transactionRepository.countFirstHelpBonuses(helperId);
        if (firstHelpCount != null && firstHelpCount < MAX_FIRST_HELP_BONUSES) {
            applyDelta(
                    helper,
                    economyConfig.getFirstHelpBonus(),
                    TransactionType.FIRST_HELP,
                    "Бонус новичка",
                    receiverId,
                    postId,
                    reference("first-help:post:" + postId));
            log.info("Начислен бонус новичка {} монет пользователю {}",
                    economyConfig.getFirstHelpBonus(), helperId);
        }
    }

    public void addCoinsForReceivedHelp(Long receiverId, Long helperId, Long postId) {
        log.info("Начисление монет за полученную помощь: receiver={}, post={}", receiverId, postId);

        User receiver = lockUser(receiverId);
        applyDelta(
                receiver,
                economyConfig.getCoinsPerHelpReceived(),
                TransactionType.HELP_RECEIVED,
                "Получена помощь",
                helperId,
                postId,
                reference("help-received:post:" + postId));
    }

    /**
     * @param reviewId the persisted review id; it makes the +coins operation
     *                 idempotent even if the review endpoint is retried.
     */
    public void addCoinsForReview(Long userId, Long reviewId) {
        log.info("Начисление монет за отзыв пользователю {}, review={}", userId, reviewId);

        User user = lockUser(userId);
        applyDelta(
                user,
                economyConfig.getCoinsPerReview(),
                TransactionType.REVIEW_BONUS,
                "Бонус за отзыв",
                null,
                null,
                reference("review:" + reviewId));
    }

    /**
     * One bonus per configured local calendar day, not per rolling 24 hours.
     */
    public boolean addDailyLoginBonus(Long userId) {
        log.info("Проверка ежедневного бонуса для пользователя {}", userId);

        User user = lockUser(userId);
        LocalDate today = LocalDate.now(economyConfig.getDailyBonusZone());
        LocalDateTime now = LocalDateTime.now();

        AppliedTransaction applied = applyDelta(
                user,
                economyConfig.getCoinsPerDailyLogin(),
                TransactionType.DAILY_LOGIN,
                "Ежедневный вход",
                null,
                null,
                reference("daily:" + today));

        if (!applied.applied()) {
            log.info("Ежедневный бонус уже получен пользователем {} за {}", userId, today);
            return false;
        }

        log.info("Начислен ежедневный бонус {} монет пользователю {} за {}, время {}",
                economyConfig.getCoinsPerDailyLogin(), userId, today, now);
        return true;
    }

    // --------------------------------------------------------------- purchases

    /**
     * VIP is always priced by the server. Buying while VIP is active extends the
     * current expiry instead of resetting it.
     */
    public WalletOperationResult purchaseVip(Long userId, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        String ref = reference("vip:" + key);
        User user = lockUser(userId);
        long price = positivePrice(economyConfig.getVipPrice(), "VIP");
        String description = "VIP-статус на " + economyConfig.getVipDays() + " дн.";

        Optional<CoinTransaction> existing = findExisting(userId, TransactionType.VIP, ref);
        if (existing.isPresent()) {
            validateExisting(existing.get(), -price, description, null, null);
            return result(TransactionType.VIP, price, currentBalance(user), false, user.getVipUntil())
                    .transactionId(existing.get().getId())
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = user.getVipUntil() != null && user.getVipUntil().isAfter(now)
                ? user.getVipUntil()
                : now;
        LocalDateTime newVipUntil = base.plusDays(economyConfig.getVipDays());

        AppliedTransaction applied = applyDelta(
                user, -price, TransactionType.VIP, description, null, null, ref);

        if (applied.applied()) {
            user.setVipUntil(newVipUntil);
            userRepository.save(user);
        }

        log.info("VIP активирован/продлён до {} для пользователя {}", user.getVipUntil(), userId);
        return result(TransactionType.VIP, price, currentBalance(user), applied.applied(), user.getVipUntil())
                .transactionId(applied.transaction().getId())
                .build();
    }

    /**
     * Paying for an unblock also repays a configured part of the debt. The
     * purchase is rejected when the remaining debt would still be above the
     * block threshold, otherwise the hourly scheduler would block the user again.
     */
    public WalletOperationResult purchaseAccountUnblock(Long userId, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        String ref = reference("unblock:" + key);
        User user = lockUser(userId);
        long price = positivePrice(economyConfig.getAccountUnblockPrice(), "ACCOUNT_UNBLOCK");
        String description = "Разблокировка аккаунта";

        Optional<CoinTransaction> existing = findExisting(userId, TransactionType.ACCOUNT_UNBLOCK, ref);
        if (existing.isPresent()) {
            validateExisting(existing.get(), -price, description, null, null);
            return result(TransactionType.ACCOUNT_UNBLOCK, price, currentBalance(user), false, null)
                    .debtCount(nvl(user.getDebtCount()))
                    .blocked(isActivelyBlocked(user, LocalDateTime.now()))
                    .transactionId(existing.get().getId())
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        if (!isActivelyBlocked(user, now)) {
            throw new BadRequestException("Аккаунт не заблокирован");
        }

        int debt = nvl(user.getDebtCount());
        int reduction = Math.max(0, economyConfig.getAccountUnblockDebtReduction());
        int newDebt = Math.max(0, debt - reduction);
        if (newDebt > economyConfig.getDebtBlockThreshold()) {
            throw new BadRequestException(
                    "Долг слишком велик для разблокировки. Помогите другим пользователям, чтобы снизить долг.");
        }

        AppliedTransaction applied = applyDelta(
                user, -price, TransactionType.ACCOUNT_UNBLOCK, description, null, null, ref);

        if (applied.applied()) {
            user.setDebtCount(newDebt);
            user.setBlockedAt(null);
            user.setBlockedUntil(null);
            if (newDebt <= economyConfig.getDebtClearThreshold()) {
                user.setDebtStartedAt(null);
            }
            userRepository.save(user);
            log.info("Пользователь {} разблокирован за монеты, долг снижен с {} до {}", userId, debt, newDebt);
        }

        return result(TransactionType.ACCOUNT_UNBLOCK, price, currentBalance(user), applied.applied(), null)
                .debtCount(newDebt)
                .blocked(false)
                .transactionId(applied.transaction().getId())
                .build();
    }

    /**
     * The nickname colour is a one-off purchase; the server sets the price and
     * validates the colour format. A repeat of the same colour is rejected
     * before any coins are debited.
     */
    public WalletOperationResult changeNicknameColor(Long userId, String color, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        if (color == null || !HEX_COLOR_PATTERN.matcher(color).matches()) {
            throw new BadRequestException("Цвет должен быть в формате #RRGGBB (например, #FF5733)");
        }

        String ref = reference("nickname:" + key);
        User user = lockUser(userId);
        long price = positivePrice(economyConfig.getNicknameColorPrice(), "NICKNAME_COLOR");
        String description = "Смена цвета ника: " + color;

        Optional<CoinTransaction> existing = findExisting(userId, TransactionType.NICKNAME_COLOR, ref);
        if (existing.isPresent()) {
            validateExisting(existing.get(), -price, description, null, null);
            return result(TransactionType.NICKNAME_COLOR, price, currentBalance(user), false, null)
                    .nicknameColor(user.getNicknameColor())
                    .transactionId(existing.get().getId())
                    .build();
        }

        if (color.equalsIgnoreCase(user.getNicknameColor())) {
            throw new BadRequestException("Этот цвет ника уже установлен");
        }

        AppliedTransaction applied = applyDelta(
                user, -price, TransactionType.NICKNAME_COLOR, description, null, null, ref);

        if (applied.applied()) {
            user.setNicknameColor(color);
            userRepository.save(user);
        }

        log.info("Цвет ника изменён на {} для пользователя {}", color, userId);
        return result(TransactionType.NICKNAME_COLOR, price, currentBalance(user), applied.applied(), null)
                .nicknameColor(user.getNicknameColor())
                .transactionId(applied.transaction().getId())
                .build();
    }

    /**
     * Debits the boost price. PostService applies the time change in the same
     * transaction; the idempotency reference includes the post id and the
     * client key so a retry cannot charge twice.
     */
    public WalletOperationResult chargeForPostBoost(Long payerId, Long postId, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        String ref = reference("boost:post:" + postId + ":" + key);
        User payer = lockUser(payerId);
        long price = positivePrice(economyConfig.getBoostPrice(), "POST_BOOST");
        String description = "Поднятие поста #" + postId;

        Optional<CoinTransaction> existing = findExisting(payerId, TransactionType.POST_BOOST, ref);
        if (existing.isPresent()) {
            validateExisting(existing.get(), -price, description, null, postId);
            return result(TransactionType.POST_BOOST, price, currentBalance(payer), false, null)
                    .transactionId(existing.get().getId())
                    .build();
        }

        AppliedTransaction applied = applyDelta(
                payer, -price, TransactionType.POST_BOOST, description, null, postId, ref);

        log.info("Списано {} монет за boost поста {} у пользователя {}", price, postId, payerId);
        return result(TransactionType.POST_BOOST, price, currentBalance(payer), applied.applied(), null)
                .transactionId(applied.transaction().getId())
                .build();
    }

    // ------------------------------------------------------------------ admin

    /**
     * Administrative correction with sign, magnitude, mandatory reason and an
     * append-only audit row. The user row is locked, so concurrent corrections
     * cannot overwrite each other.
     */
    public WalletOperationResult adminAddCoins(
            Long userId,
            Long adminId,
            long amount,
            String reason,
            String idempotencyKey) {

        if (adminId == null) {
            throw new BadRequestException("Не удалось определить администратора");
        }
        if (amount == 0) {
            throw new BadRequestException("Сумма должна быть отлична от нуля");
        }
        if (Math.abs(amount) > economyConfig.getAdminMaxCoinsPerOperation()) {
            throw new BadRequestException(
                    "Сумма превышает допустимый лимит " + economyConfig.getAdminMaxCoinsPerOperation() + " HC");
        }

        String normalizedReason = normalizeReason(reason);
        String key = requireIdempotencyKey(idempotencyKey);
        String ref = reference("admin:" + key);
        String description = truncate("Администратор: " + normalizedReason, MAX_DESCRIPTION_LENGTH);

        User user = lockUser(userId);
        Optional<CoinTransaction> existing = findExisting(userId, TransactionType.ADMIN_BONUS, ref);
        if (existing.isPresent()) {
            validateExisting(existing.get(), amount, description, null, null);
            return result(TransactionType.ADMIN_BONUS, Math.abs(amount), currentBalance(user), false, null)
                    .transactionId(existing.get().getId())
                    .build();
        }

        long balanceBefore = currentBalance(user);
        AppliedTransaction applied = applyDelta(
                user, amount, TransactionType.ADMIN_BONUS, description, null, null, ref);

        if (applied.applied()) {
            adminAuditRepository.save(AdminCoinAudit.builder()
                    .adminId(adminId)
                    .userId(userId)
                    .amount(amount)
                    .balanceBefore(balanceBefore)
                    .balanceAfter(currentBalance(user))
                    .reason(normalizedReason)
                    .transactionId(applied.transaction().getId())
                    .build());
        }

        log.info("Админ {} начислил {} монет пользователю {} (причина: {})",
                adminId, amount, userId, normalizedReason);
        return result(TransactionType.ADMIN_BONUS, Math.abs(amount), currentBalance(user), applied.applied(), null)
                .transactionId(applied.transaction().getId())
                .build();
    }

    // --------------------------------------------------------------- internals

    private User getUserOrThrow(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    private User lockUser(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }
        return user;
    }

    /**
     * Applies a signed delta to a locked user and writes exactly one ledger row.
     * Repeating the same (type, reference) returns the existing row untouched.
     */
    private AppliedTransaction applyDelta(
            User user,
            long delta,
            TransactionType type,
            String description,
            Long relatedUserId,
            Long relatedPostId,
            String referenceId) {

        if (delta == 0) {
            throw new BadRequestException("Сумма операции должна быть отлична от нуля");
        }

        Optional<CoinTransaction> existing = findExisting(user.getId(), type, referenceId);
        if (existing.isPresent()) {
            validateExisting(existing.get(), delta, description, relatedUserId, relatedPostId);
            return new AppliedTransaction(existing.get(), false);
        }

        long current = currentBalance(user);
        long next;
        try {
            next = Math.addExact(current, delta);
        } catch (ArithmeticException ex) {
            throw new BadRequestException("Баланс вышел за допустимый диапазон");
        }
        if (next < 0) {
            throw new BadRequestException("Недостаточно монет. Доступно: " + current);
        }

        user.setHelpCoins(next);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        CoinTransaction transaction = CoinTransaction.builder()
                .userId(user.getId())
                .type(type)
                .amount(delta)
                .description(description)
                .relatedUserId(relatedUserId)
                .relatedPostId(relatedPostId)
                .referenceId(referenceId)
                .build();
        transactionRepository.save(transaction);

        return new AppliedTransaction(transaction, true);
    }

    private Optional<CoinTransaction> findExisting(Long userId, TransactionType type, String referenceId) {
        if (referenceId == null) {
            return Optional.empty();
        }
        return transactionRepository.findByUserIdAndTypeAndReferenceId(userId, type, referenceId);
    }

    private void validateExisting(
            CoinTransaction existing,
            long expectedDelta,
            String expectedDescription,
            Long expectedRelatedUserId,
            Long expectedRelatedPostId) {
        boolean sameAmount = Objects.equals(existing.getAmount(), expectedDelta);
        boolean sameDescription = Objects.equals(existing.getDescription(), expectedDescription);
        boolean sameUser = Objects.equals(existing.getRelatedUserId(), expectedRelatedUserId);
        boolean samePost = Objects.equals(existing.getRelatedPostId(), expectedRelatedPostId);
        if (!sameAmount || !sameDescription || !sameUser || !samePost) {
            throw new ConflictException(
                    "Idempotency-Key уже использован для другой операции");
        }
    }

    private WalletOperationResult.WalletOperationResultBuilder result(
            TransactionType operation,
            long price,
            long balance,
            boolean applied,
            LocalDateTime expiresAt) {
        return WalletOperationResult.builder()
                .operation(operation)
                .price(price)
                .balance(balance)
                .applied(applied)
                .expiresAt(expiresAt);
    }

    private long currentBalance(User user) {
        return user.getHelpCoins() != null ? user.getHelpCoins() : 0L;
    }

    private int nvl(Integer value) {
        return value != null ? value : 0;
    }

    private boolean isActivelyBlocked(User user, LocalDateTime now) {
        return (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(now))
                || user.getBlockedAt() != null;
    }

    private long positivePrice(long price, String operation) {
        if (price <= 0) {
            log.error("Некорректная цена операции {}: {}", operation, price);
            throw new IllegalStateException("Paid operation price must be positive");
        }
        return price;
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Требуется заголовок Idempotency-Key");
        }
        String key = idempotencyKey.trim();
        if (key.length() > economyConfig.getIdempotencyKeyMaxLength()) {
            throw new BadRequestException(
                    "Idempotency-Key длиннее " + economyConfig.getIdempotencyKeyMaxLength() + " символов");
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new BadRequestException(
                    "Idempotency-Key может содержать только латиницу, цифры, '.', '_', ':' и '-'");
        }
        return key;
    }

    private String reference(String value) {
        if (value == null || value.length() > MAX_REFERENCE_LENGTH) {
            throw new BadRequestException("Слишком длинная ссылка денежной операции");
        }
        return value;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Причина административного начисления обязательна");
        }
        String normalized = reason.trim();
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestException(
                    "Причина не должна превышать " + MAX_DESCRIPTION_LENGTH + " символов");
        }
        return normalized;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private CoinTransactionDto toDto(CoinTransaction transaction) {
        return CoinTransactionDto.builder()
                .id(transaction.getId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .relatedUserId(transaction.getRelatedUserId())
                .relatedPostId(transaction.getRelatedPostId())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private record AppliedTransaction(CoinTransaction transaction, boolean applied) {
    }
}
