package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.wallet.CoinTransactionDto;
import org.example.helptreeservice.dto.wallet.WalletDto;
import org.example.helptreeservice.entity.CoinTransaction;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.TransactionType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.repository.CoinTransactionRepository;
import org.example.helptreeservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class WalletService {

    private final UserRepository userRepository;
    private final CoinTransactionRepository transactionRepository;
    private final UserService userService;

    private static final long COINS_PER_HELP = 10L;
    private static final long COINS_PER_HELP_RECEIVED = 2L;
    private static final long COINS_PER_REVIEW = 2L;
    private static final long COINS_PER_DAILY_LOGIN = 1L;
    private static final long COINS_PER_FIRST_HELP = 3L;
    private static final int MAX_FIRST_HELP_BONUSES = 3;
    private static final long COST_NICKNAME_COLOR = 20L;

    @Transactional(readOnly = true)
    public WalletDto getWallet(Long userId) {
        User user = getUserOrThrow(userId);
        Long totalEarned = transactionRepository.getTotalEarned(userId);
        Long totalSpent = transactionRepository.getTotalSpent(userId);
        
        return WalletDto.builder()
                .userId(userId)
                .balance(user.getHelpCoins() != null ? user.getHelpCoins() : 0L)
                .totalEarned(totalEarned != null ? totalEarned : 0L)
                .totalSpent(totalSpent != null ? totalSpent : 0L)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<CoinTransactionDto> getTransactions(Long userId, int page, int size) {
        Page<CoinTransaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        return transactions.map(this::toDto);
    }

    public void addCoinsForHelp(Long helperId, Long receiverId, Long postId) {
        log.info("Начисление монет за помощь: helper={}, receiver={}", helperId, receiverId);
        
        User helper = getUserOrThrow(helperId);
        Long current = helper.getHelpCoins();
        long totalCoins = (current != null ? current : 0L) + COINS_PER_HELP;
        
        Long firstHelpCount = transactionRepository.countFirstHelpBonuses(helperId);
        if (firstHelpCount != null && firstHelpCount < MAX_FIRST_HELP_BONUSES) {
            totalCoins += COINS_PER_FIRST_HELP;
            CoinTransaction firstHelpTx = CoinTransaction.builder()
                    .userId(helperId)
                    .type(TransactionType.FIRST_HELP)
                    .amount(COINS_PER_FIRST_HELP)
                    .description("Бонус новичка")
                    .relatedUserId(receiverId)
                    .relatedPostId(postId)
                    .build();
            transactionRepository.save(firstHelpTx);
            log.info("Начислен бонус новичка {} монет пользователю {}", COINS_PER_FIRST_HELP, helperId);
        }
        
        helper.setHelpCoins(totalCoins);
        userRepository.save(helper);
        
        CoinTransaction transaction = CoinTransaction.builder()
                .userId(helperId)
                .type(TransactionType.HELP_GIVEN)
                .amount(COINS_PER_HELP)
                .description("Помощь пользователю")
                .relatedUserId(receiverId)
                .relatedPostId(postId)
                .build();
        transactionRepository.save(transaction);
        
        log.info("Начислено {} монет пользователю {}", COINS_PER_HELP, helperId);
    }

    public void addCoinsForReceivedHelp(Long receiverId, Long helperId, Long postId) {
        log.info("Начисление монет за полученную помощь: receiver={}", receiverId);
        
        User receiver = getUserOrThrow(receiverId);
        Long current = receiver.getHelpCoins();
        receiver.setHelpCoins((current != null ? current : 0L) + COINS_PER_HELP_RECEIVED);
        userRepository.save(receiver);
        
        CoinTransaction transaction = CoinTransaction.builder()
                .userId(receiverId)
                .type(TransactionType.HELP_RECEIVED)
                .amount(COINS_PER_HELP_RECEIVED)
                .description("Получена помощь")
                .relatedUserId(helperId)
                .relatedPostId(postId)
                .build();
        transactionRepository.save(transaction);
        
        log.info("Начислено {} монет пользователю {}", COINS_PER_HELP_RECEIVED, receiverId);
    }

    public void addCoinsForReview(Long userId) {
        log.info("Начисление монет за отзыв пользователю {}", userId);
        
        User user = getUserOrThrow(userId);
        Long current = user.getHelpCoins();
        user.setHelpCoins((current != null ? current : 0L) + COINS_PER_REVIEW);
        userRepository.save(user);
        
        CoinTransaction transaction = CoinTransaction.builder()
                .userId(userId)
                .type(TransactionType.REVIEW_BONUS)
                .amount(COINS_PER_REVIEW)
                .description("Бонус за отзыв")
                .build();
        transactionRepository.save(transaction);
        
        log.info("Начислено {} монет за отзыв пользователю {}", COINS_PER_REVIEW, userId);
    }

    public void addDailyLoginBonus(Long userId) {
        log.info("Проверка ежедневного бонуса для пользователя {}", userId);
        
        var recentTransactions = transactionRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId);
        boolean alreadyClaimed = recentTransactions.stream()
                .anyMatch(t -> t.getType() == TransactionType.DAILY_LOGIN 
                        && t.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24)));
        
        if (alreadyClaimed) {
            log.info("Ежедневный бонус уже получен пользователем {}", userId);
            return;
        }
        
        User user = getUserOrThrow(userId);
        Long currentCoins = user.getHelpCoins();
        if (currentCoins == null) {
            currentCoins = 0L;
        }
        user.setHelpCoins(currentCoins + COINS_PER_DAILY_LOGIN);
        userRepository.save(user);
        
        CoinTransaction transaction = CoinTransaction.builder()
                .userId(userId)
                .type(TransactionType.DAILY_LOGIN)
                .amount(COINS_PER_DAILY_LOGIN)
                .description("Ежедневный вход")
                .build();
        transactionRepository.save(transaction);
        
        log.info("Начислен ежедневный бонус {} монет пользователю {}", COINS_PER_DAILY_LOGIN, userId);
    }

    public void spendCoins(Long userId, long amount, TransactionType type, String description) {
        log.info("Списание {} монет у пользователя {} для {}", amount, userId, type);
        
        if (amount <= 0) {
            throw new BadRequestException("Сумма должна быть положительной");
        }
        
        User user = getUserOrThrow(userId);
        Long currentCoins = user.getHelpCoins();
        if (currentCoins == null) {
            currentCoins = 0L;
        }
        if (currentCoins < amount) {
            throw new BadRequestException("Недостаточно монет. Доступно: " + currentCoins);
        }
        
        user.setHelpCoins(currentCoins - amount);
        
        if (type == TransactionType.ACCOUNT_UNBLOCK) {
            userService.unblockUser(userId);
        }
        
        if (type == TransactionType.VIP_STATUS || type == TransactionType.VIP) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime currentVip = user.getVipUntil();
            if (currentVip != null && currentVip.isAfter(now)) {
                user.setVipUntil(currentVip.plusDays(30));
            } else {
                user.setVipUntil(now.plusDays(30));
            }
            log.info("VIP статус активирован до {} для пользователя {}", user.getVipUntil(), userId);
        }
        
        userRepository.save(user);
        
        CoinTransaction transaction = CoinTransaction.builder()
                .userId(userId)
                .type(type)
                .amount(-amount)
                .description(description)
                .build();
        transactionRepository.save(transaction);
        
        log.info("Списано {} монет у пользователя {}. Новый баланс: {}", amount, userId, user.getHelpCoins());
    }
    
    public void changeNicknameColor(Long userId, String color) {
        log.info("Смена цвета ника на {} для пользователя {}", color, userId);
        
        User user = getUserOrThrow(userId);
        Long currentCoins = user.getHelpCoins();
        if (currentCoins == null) {
            currentCoins = 0L;
        }
        if (currentCoins < COST_NICKNAME_COLOR) {
            throw new BadRequestException("Недостаточно монет. Нужно: " + COST_NICKNAME_COLOR + ", у вас: " + currentCoins);
        }
        
        user.setHelpCoins(currentCoins - COST_NICKNAME_COLOR);
        user.setNicknameColor(color);
        userRepository.save(user);
        
        CoinTransaction transaction = CoinTransaction.builder()
                .userId(userId)
                .type(TransactionType.NICKNAME_COLOR)
                .amount(-COST_NICKNAME_COLOR)
                .description("Смена цвета ника: " + color)
                .build();
        transactionRepository.save(transaction);
        
        log.info("Цвет ника изменён на {} для пользователя {}", color, userId);
    }
    
    public void adminAddCoins(Long userId, long amount, String description) {
        log.info("Админ: начисление {} монет пользователю {}", amount, userId);
        
        User user = getUserOrThrow(userId);
        Long current = user.getHelpCoins();
        user.setHelpCoins((current != null ? current : 0L) + amount);
        userRepository.save(user);
        
        CoinTransaction transaction = CoinTransaction.builder()
                .userId(userId)
                .type(TransactionType.ADMIN_BONUS)
                .amount(amount)
                .description(description != null ? description : "Бонус от администратора")
                .build();
        transactionRepository.save(transaction);
        
        log.info("Начислено {} монет пользователю {} админом", amount, userId);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Пользователь не найден"));
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
}
