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

    private static final long COINS_PER_HELP = 10L;
    private static final long COINS_PER_REVIEW = 2L;
    private static final long COINS_PER_DAILY_LOGIN = 1L;

    @Transactional(readOnly = true)
    public WalletDto getWallet(Long userId) {
        User user = getUserOrThrow(userId);
        Long totalEarned = transactionRepository.getTotalEarned(userId);
        Long totalSpent = transactionRepository.getTotalSpent(userId);
        
        return WalletDto.builder()
                .userId(userId)
                .balance(user.getHelpCoins())
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
        helper.setHelpCoins(helper.getHelpCoins() + COINS_PER_HELP);
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
        receiver.setHelpCoins(receiver.getHelpCoins() + COINS_PER_HELP);
        userRepository.save(receiver);
        
        CoinTransaction transaction = CoinTransaction.builder()
                .userId(receiverId)
                .type(TransactionType.HELP_RECEIVED)
                .amount(COINS_PER_HELP)
                .description("Получена помощь")
                .relatedUserId(helperId)
                .relatedPostId(postId)
                .build();
        transactionRepository.save(transaction);
        
        log.info("Начислено {} монет пользователю {}", COINS_PER_HELP, receiverId);
    }

    public void addCoinsForReview(Long userId) {
        log.info("Начисление монет за отзыв пользователю {}", userId);
        
        User user = getUserOrThrow(userId);
        user.setHelpCoins(user.getHelpCoins() + COINS_PER_REVIEW);
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
                        && t.getCreatedAt().isAfter(LocalDateTime.now().minusHours(20)));
        
        if (alreadyClaimed) {
            log.info("Ежедневный бонус уже получен пользователем {}", userId);
            return;
        }
        
        User user = getUserOrThrow(userId);
        user.setHelpCoins(user.getHelpCoins() + COINS_PER_DAILY_LOGIN);
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
        if (user.getHelpCoins() < amount) {
            throw new BadRequestException("Недостаточно монет. Доступно: " + user.getHelpCoins());
        }
        
        user.setHelpCoins(user.getHelpCoins() - amount);
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

    public void adminAddCoins(Long userId, long amount, String description) {
        log.info("Админ: начисление {} монет пользователю {}", amount, userId);
        
        User user = getUserOrThrow(userId);
        user.setHelpCoins(user.getHelpCoins() + amount);
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
