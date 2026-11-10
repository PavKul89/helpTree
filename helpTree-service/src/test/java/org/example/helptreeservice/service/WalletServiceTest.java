package org.example.helptreeservice.service;

import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.config.WalletProperties;
import org.example.helptreeservice.dto.wallet.WalletOperationResult;
import org.example.helptreeservice.entity.AdminCoinAudit;
import org.example.helptreeservice.entity.CoinTransaction;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.TransactionDirection;
import org.example.helptreeservice.enums.TransactionType;
import org.example.helptreeservice.enums.PaidFeatureType;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.repository.AdminCoinAuditRepository;
import org.example.helptreeservice.repository.CoinTransactionRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CoinTransactionRepository transactionRepository;
    @Mock private AdminCoinAuditRepository adminAuditRepository;
    @Mock private RatingService ratingService;
    @Mock private PublicProfileCache publicProfileCache;
    @Mock private NotificationService notificationService;
    @Mock private AdminAuditService adminAuditService;
    @Mock private PaidFeaturePeriodService paidFeaturePeriodService;

    private EconomyConfig economyConfig;
    private WalletService walletService;
    private User user;

    @BeforeEach
    void setUp() {
        economyConfig = new EconomyConfig();
        PaginationConfig paginationConfig = new PaginationConfig();
        walletService = new WalletService(
                userRepository, transactionRepository, adminAuditRepository,
                ratingService, economyConfig, paginationConfig, publicProfileCache,
                notificationService, adminAuditService, paidFeaturePeriodService,
                new WalletProperties());

        user = new User();
        user.setId(1L);
        user.setName("Owner");
        user.setDeleted(false);
        user.setHelpCoins(100L);
        user.setDebtCount(0);

        lenient().when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        lenient().when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        lenient().when(adminAuditRepository.save(any(AdminCoinAudit.class))).thenAnswer(invocation -> {
            AdminCoinAudit audit = invocation.getArgument(0);
            audit.setId(500L);
            return audit;
        });
        lenient().when(transactionRepository.save(any(CoinTransaction.class))).thenAnswer(invocation -> {
            CoinTransaction transaction = invocation.getArgument(0);
            if (transaction.getId() == null) {
                transaction.setId(900L);
            }
            return transaction;
        });
    }

    // ------------------------------------------------------------- accruals

    @Test
    void addDailyLoginBonus_shouldCreditExactlyOncePerConfiguredDay() {
        LocalDate today = LocalDate.now(economyConfig.getDailyBonusZone());
        CoinTransaction existing = CoinTransaction.builder()
                .userId(1L)
                .type(TransactionType.DAILY_LOGIN)
                .amount(economyConfig.getCoinsPerDailyLogin())
                .description("Ежедневный вход")
                .referenceId("daily:" + today)
                .build();
        when(transactionRepository.findByUserIdAndTypeAndReferenceId(
                1L, TransactionType.DAILY_LOGIN, "daily:" + today))
                .thenReturn(Optional.empty(), Optional.of(existing));

        assertTrue(walletService.addDailyLoginBonus(1L));
        assertEquals(101L, user.getHelpCoins());

        assertFalse(walletService.addDailyLoginBonus(1L));
        assertEquals(101L, user.getHelpCoins(),
                "A second claim on the same local day must not credit coins again");

        verify(userRepository, times(1)).save(user);
        ArgumentCaptor<CoinTransaction> captor = ArgumentCaptor.forClass(CoinTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals("daily:" + today, captor.getValue().getReferenceId());
        assertEquals(TransactionType.DAILY_LOGIN, captor.getValue().getType());
    }

    @Test
    void addCoinsForHelp_shouldCreditBaseAndFirstHelpBonus() {
        when(transactionRepository.countFirstHelpBonuses(1L)).thenReturn(0L);

        walletService.addCoinsForHelp(1L, 2L, 10L);

        assertEquals(100L + economyConfig.getCoinsPerHelp() + economyConfig.getFirstHelpBonus(),
                user.getHelpCoins());

        ArgumentCaptor<CoinTransaction> captor = ArgumentCaptor.forClass(CoinTransaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(tx -> tx.getType() == TransactionType.HELP_GIVEN
                        && "help-given:post:10".equals(tx.getReferenceId())));
        assertTrue(captor.getAllValues().stream()
                .anyMatch(tx -> tx.getType() == TransactionType.FIRST_HELP
                        && "first-help:post:10".equals(tx.getReferenceId())));
    }

    @Test
    void addCoinsForHelp_shouldNotCreditAgainForSamePost() {
        CoinTransaction existing = CoinTransaction.builder()
                .userId(1L)
                .type(TransactionType.HELP_GIVEN)
                .amount(economyConfig.getCoinsPerHelp())
                .description("Помощь пользователю")
                .relatedUserId(2L)
                .relatedPostId(10L)
                .referenceId("help-given:post:10")
                .build();
        when(transactionRepository.findByUserIdAndTypeAndReferenceId(
                1L, TransactionType.HELP_GIVEN, "help-given:post:10"))
                .thenReturn(Optional.of(existing));

        walletService.addCoinsForHelp(1L, 2L, 10L);

        assertEquals(100L, user.getHelpCoins());
        verify(userRepository, never()).save(any(User.class));
        verify(transactionRepository, never()).save(any(CoinTransaction.class));
        verify(transactionRepository, never()).countFirstHelpBonuses(anyLong());
    }

    // ------------------------------------------------------------ purchases

    @Test
    void purchaseVip_shouldRejectWhenBalanceIsInsufficient() {
        user.setHelpCoins(10L);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> walletService.purchaseVip(1L, "vip-key-1"));

        assertTrue(error.getMessage().contains("Недостаточно"));
        assertEquals(10L, user.getHelpCoins());
        verify(userRepository, never()).save(any(User.class));
        verify(transactionRepository, never()).save(any(CoinTransaction.class));
    }

    @Test
    void purchaseVip_shouldExtendActiveVipAndChargeServerPrice() {
        LocalDateTime currentExpiry = LocalDateTime.now().plusDays(10);
        user.setVipUntil(currentExpiry);

        WalletOperationResult result = walletService.purchaseVip(1L, "vip-key-2");

        assertTrue(result.getApplied());
        assertEquals(economyConfig.getVipPrice(), result.getPrice());
        assertEquals(100L - economyConfig.getVipPrice(), user.getHelpCoins());
        assertEquals(currentExpiry.plusDays(economyConfig.getVipDays()), user.getVipUntil());
        verify(transactionRepository).save(argThat(tx ->
                tx.getType() == TransactionType.VIP
                        && tx.getAmount().equals(-economyConfig.getVipPrice())
                        && "vip:vip-key-2".equals(tx.getReferenceId())));
    }

    @Test
    void purchaseVip_shouldBeIdempotentForSameKey() {
        CoinTransaction existing = CoinTransaction.builder()
                .id(500L)
                .userId(1L)
                .type(TransactionType.VIP)
                .amount(-economyConfig.getVipPrice())
                .description("VIP-статус на " + economyConfig.getVipDays() + " дн.")
                .referenceId("vip:vip-key-3")
                .build();
        when(transactionRepository.findByUserIdAndTypeAndReferenceId(
                1L, TransactionType.VIP, "vip:vip-key-3"))
                .thenReturn(Optional.of(existing));

        WalletOperationResult result = walletService.purchaseVip(1L, "vip-key-3");

        assertFalse(result.getApplied());
        assertEquals(100L, user.getHelpCoins());
        assertEquals(500L, result.getTransactionId());
        verify(userRepository, never()).save(any(User.class));
        verify(transactionRepository, never()).save(any(CoinTransaction.class));
    }

    @Test
    void purchaseVip_shouldRejectKeyReusedWithDifferentPayload() {
        CoinTransaction existing = CoinTransaction.builder()
                .id(501L)
                .userId(1L)
                .type(TransactionType.VIP)
                .amount(-1L)
                .description("Something else")
                .referenceId("vip:reused")
                .build();
        when(transactionRepository.findByUserIdAndTypeAndReferenceId(
                1L, TransactionType.VIP, "vip:reused"))
                .thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> walletService.purchaseVip(1L, "reused"));
        assertEquals(100L, user.getHelpCoins());
    }

    @Test
    void purchaseAccountUnblock_shouldReduceDebtAndClearBlock() {
        user.setHelpCoins(100L);
        user.setDebtCount(5);
        user.setBlockedAt(LocalDateTime.now());
        user.setBlockedUntil(LocalDateTime.now().plusDays(7));

        WalletOperationResult result = walletService.purchaseAccountUnblock(1L, "unblock-key-1");

        assertTrue(result.getApplied());
        assertEquals(0, result.getDebtCount());
        assertFalse(result.getBlocked());
        assertEquals(0, user.getDebtCount());
        assertNull(user.getBlockedAt());
        assertNull(user.getBlockedUntil());
        assertEquals(100L - economyConfig.getAccountUnblockPrice(), user.getHelpCoins());
        verify(ratingService).recalculateUsers(List.of(1L), "Покупка разблокировки");
    }

    @Test
    void purchaseAccountUnblock_shouldRejectWhenDebtWouldRemainAboveThreshold() {
        user.setDebtCount(12);
        user.setBlockedAt(LocalDateTime.now());

        assertThrows(BadRequestException.class,
                () -> walletService.purchaseAccountUnblock(1L, "unblock-key-2"));

        assertEquals(12, user.getDebtCount());
        assertEquals(100L, user.getHelpCoins());
        verify(transactionRepository, never()).save(any(CoinTransaction.class));
    }

    @Test
    void purchaseAccountUnblock_shouldRejectWhenAccountIsNotBlocked() {
        assertThrows(BadRequestException.class,
                () -> walletService.purchaseAccountUnblock(1L, "unblock-key-3"));
        verify(transactionRepository, never()).save(any(CoinTransaction.class));
    }

    @Test
    void changeNicknameColor_shouldChargeServerPriceAndStoreColor() {
        user.setNicknameColor("#000000");

        WalletOperationResult result = walletService.changeNicknameColor(1L, "#FF5733", "nick-key-1");

        assertTrue(result.getApplied());
        assertEquals("#FF5733", user.getNicknameColor());
        assertEquals(100L - economyConfig.getNicknameColorPrice(), user.getHelpCoins());
    }

    @Test
    void changeNicknameColor_shouldBeIdempotent() {
        CoinTransaction existing = CoinTransaction.builder()
                .id(600L)
                .userId(1L)
                .type(TransactionType.NICKNAME_COLOR)
                .amount(-economyConfig.getNicknameColorPrice())
                .description("Смена цвета ника: #FF5733")
                .referenceId("nickname:nick-key-2")
                .build();
        when(transactionRepository.findByUserIdAndTypeAndReferenceId(
                1L, TransactionType.NICKNAME_COLOR, "nickname:nick-key-2"))
                .thenReturn(Optional.of(existing));

        WalletOperationResult result = walletService.changeNicknameColor(1L, "#FF5733", "nick-key-2");

        assertFalse(result.getApplied());
        assertEquals(100L, user.getHelpCoins());
        verify(transactionRepository, never()).save(any(CoinTransaction.class));
    }

    @Test
    void changeNicknameColor_shouldRejectSameColorBeforeCharging() {
        user.setNicknameColor("#FF5733");

        assertThrows(BadRequestException.class,
                () -> walletService.changeNicknameColor(1L, "#FF5733", "nick-key-3"));
        assertEquals(100L, user.getHelpCoins());
        verify(transactionRepository, never()).save(any(CoinTransaction.class));
    }

    @Test
    void paidOperations_shouldRejectMissingOrMalformedIdempotencyKey() {
        assertThrows(BadRequestException.class, () -> walletService.purchaseVip(1L, null));
        assertThrows(BadRequestException.class, () -> walletService.purchaseVip(1L, " "));
        assertThrows(BadRequestException.class, () -> walletService.purchaseVip(1L, "bad key with spaces"));
        assertEquals(100L, user.getHelpCoins());
    }

    // ---------------------------------------------------------------- admin

    @Test
    void adminAddCoins_shouldApplySignedAmountAndWriteAudit() {
        WalletOperationResult result = walletService.adminAddCoins(
                1L, 99L, 250L, "  Компенсация сбоя  ", "admin-key-1");

        assertTrue(result.getApplied());
        assertEquals(350L, user.getHelpCoins());
        assertEquals(350L, result.getBalance());

        ArgumentCaptor<AdminCoinAudit> captor = ArgumentCaptor.forClass(AdminCoinAudit.class);
        verify(adminAuditRepository).save(captor.capture());
        AdminCoinAudit audit = captor.getValue();
        assertEquals(99L, audit.getAdminId());
        assertEquals(1L, audit.getUserId());
        assertEquals(250L, audit.getAmount());
        assertEquals(100L, audit.getBalanceBefore());
        assertEquals(350L, audit.getBalanceAfter());
        assertEquals("Компенсация сбоя", audit.getReason());
        verify(adminAuditService).record(eq(99L), eq(AdminAuditAction.COINS_ADJUSTED),
                eq(AdminAuditService.ENTITY_WALLET), eq(1L), anyMap(), anyMap(),
                eq("Компенсация сбоя"));
    }

    @Test
    void adminAddCoins_shouldAllowNegativeCorrection() {
        walletService.adminAddCoins(1L, 99L, -30L, "Списание ошибочного бонуса", "admin-key-2");

        assertEquals(70L, user.getHelpCoins());
        verify(adminAuditRepository).save(argThat(audit -> audit.getAmount() == -30L));
    }

    @Test
    void adminAddCoins_shouldRejectNegativeBalance() {
        assertThrows(BadRequestException.class,
                () -> walletService.adminAddCoins(1L, 99L, -101L, "Correction", "admin-key-3"));
        assertEquals(100L, user.getHelpCoins());
        verify(adminAuditRepository, never()).save(any());
    }

    @Test
    void adminAddCoins_shouldRejectZeroOverLimitAndBlankReason() {
        assertThrows(BadRequestException.class,
                () -> walletService.adminAddCoins(1L, 99L, 0L, "reason", "k1"));
        assertThrows(BadRequestException.class,
                () -> walletService.adminAddCoins(1L, 99L,
                        economyConfig.getAdminMaxCoinsPerOperation() + 1, "reason", "k2"));
        assertThrows(BadRequestException.class,
                () -> walletService.adminAddCoins(1L, 99L, 10L, "   ", "k3"));
        assertEquals(100L, user.getHelpCoins());
        verify(adminAuditRepository, never()).save(any());
    }

    @Test
    void adminAddCoins_shouldNotAuditOrCreditOnIdempotentReplay() {
        CoinTransaction existing = CoinTransaction.builder()
                .id(700L)
                .userId(1L)
                .type(TransactionType.ADMIN_BONUS)
                .amount(250L)
                .description("Администратор: Компенсация сбоя")
                .referenceId("admin:admin-key-4")
                .build();
        when(transactionRepository.findByUserIdAndTypeAndReferenceId(
                1L, TransactionType.ADMIN_BONUS, "admin:admin-key-4"))
                .thenReturn(Optional.of(existing));

        WalletOperationResult result = walletService.adminAddCoins(
                1L, 99L, 250L, "Компенсация сбоя", "admin-key-4");

        assertFalse(result.getApplied());
        assertEquals(100L, user.getHelpCoins());
        verify(adminAuditRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // -------------------------------------------------------------- history

    @Test
    void getTransactions_shouldRejectInvalidFiltersAndClampPageSize() {
        when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(Page.empty());

        assertThrows(BadRequestException.class,
                () -> walletService.getTransactions(1L, -1, 20, null, null, null, null));
        assertThrows(BadRequestException.class,
                () -> walletService.getTransactions(1L, 0, 0, null, null, null, null));
        assertThrows(BadRequestException.class,
                () -> walletService.getTransactions(1L, 0, 20, null,
                        LocalDateTime.now(), LocalDateTime.now().minusDays(1), null));

        walletService.getTransactions(1L, 0, 1000, TransactionType.VIP,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), TransactionDirection.OUT);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(
                any(org.springframework.data.jpa.domain.Specification.class), captor.capture());
        assertEquals(new PaginationConfig().getMaxPageSize(), captor.getValue().getPageSize());
    }

    @Test
    void getWallet_shouldReturnBalanceAndActiveVipFlag() {
        user.setVipUntil(LocalDateTime.now().plusDays(3));
        user.setNicknameColor("#123456");
        when(transactionRepository.getTotalEarned(1L)).thenReturn(150L);
        when(transactionRepository.getTotalSpent(1L)).thenReturn(50L);

        var wallet = walletService.getWallet(1L);

        assertEquals(100L, wallet.getBalance());
        assertEquals(150L, wallet.getTotalEarned());
        assertEquals(50L, wallet.getTotalSpent());
        assertTrue(wallet.getVipActive());
        assertEquals("#123456", wallet.getNicknameColor());
    }

    @Test
    void getPricing_shouldExposeConfiguredCatalogOnly() {
        var pricing = walletService.getPricing();

        assertEquals(economyConfig.getBoostPrice(), pricing.getBoostPrice());
        assertEquals(economyConfig.getBoostHours(), pricing.getBoostHours());
        assertEquals(economyConfig.getVipPrice(), pricing.getVipPrice());
        assertEquals(economyConfig.getVipDays(), pricing.getVipDays());
        assertEquals(economyConfig.getNicknameColorPrice(), pricing.getNicknameColorPrice());
        assertEquals(economyConfig.getAccountUnblockPrice(), pricing.getAccountUnblockPrice());
        assertEquals(economyConfig.getAccountUnblockDebtReduction(),
                pricing.getAccountUnblockDebtReduction());
        assertEquals(economyConfig.getDebtBlockThreshold(), pricing.getDebtBlockThreshold());
    }

    @Test
    void exportTransactions_shouldRenderCsvWithBomAndEscapedFields() {
        CoinTransaction income = CoinTransaction.builder()
                .id(1L)
                .userId(1L)
                .type(TransactionType.DAILY_LOGIN)
                .amount(10L)
                .description("Бонус")
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5))
                .build();
        CoinTransaction expense = CoinTransaction.builder()
                .id(2L)
                .userId(1L)
                .type(TransactionType.VIP)
                .amount(-50L)
                .description("Описание, с \"кавычками\"")
                .createdAt(LocalDateTime.of(2026, 1, 3, 3, 4, 5))
                .build();
        when(transactionRepository.count(any(Specification.class))).thenReturn(2L);
        when(transactionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(income, expense));

        var export = walletService.exportTransactions(
                1L, null, null, null, null, "csv");

        String csv = new String(export.content(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("id,type,amount,direction,description"));
        assertTrue(csv.contains(",IN,"));
        assertTrue(csv.contains(",OUT,"));
        assertTrue(csv.contains("\"Описание, с \"\"кавычками\"\"\""));
        assertEquals("text/csv;charset=UTF-8", export.contentType());
        assertTrue(export.filename().endsWith(".csv"));
    }

    @Test
    void exportTransactions_shouldRenderJson() throws Exception {
        CoinTransaction income = CoinTransaction.builder()
                .id(1L)
                .userId(1L)
                .type(TransactionType.DAILY_LOGIN)
                .amount(10L)
                .description("Бонус")
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5))
                .build();
        when(transactionRepository.count(any(Specification.class))).thenReturn(1L);
        when(transactionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(income));

        var export = walletService.exportTransactions(
                1L, null, null, null, null, "json");

        var json = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(export.content());
        assertEquals(1, json.size());
        assertEquals("IN", json.get(0).get("direction").asText());
        assertEquals(1L, json.get(0).get("id").asLong());
        assertEquals("application/json;charset=UTF-8", export.contentType());
        assertTrue(export.filename().endsWith(".json"));
    }

    @Test
    void exportTransactions_shouldRejectUnsupportedFormatAndInvertedPeriod() {
        assertThrows(BadRequestException.class, () -> walletService.exportTransactions(
                1L, null, null, null, null, "xml"));
        assertThrows(BadRequestException.class, () -> walletService.exportTransactions(
                1L, null, LocalDateTime.now(), LocalDateTime.now().minusDays(1), null, "csv"));
        verify(transactionRepository, never()).findAll(
                any(Specification.class), any(Sort.class));
    }

    @Test
    void exportTransactions_shouldRejectTooManyRows() {
        when(transactionRepository.count(any(Specification.class))).thenReturn(50_001L);

        assertThrows(BadRequestException.class, () -> walletService.exportTransactions(
                1L, null, null, null, null, "csv"));

        verify(transactionRepository, never()).findAll(
                any(Specification.class), any(Sort.class));
    }

    @Test
    void getPaidFeatureHistory_shouldDelegateToPeriodService() {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        Page<org.example.helptreeservice.dto.wallet.PaidFeaturePeriodDto> page =
                Page.empty(pageable);
        when(paidFeaturePeriodService.getHistory(1L, pageable)).thenReturn(page);

        var result = walletService.getPaidFeatureHistory(1L, pageable);

        assertEquals(page, result);
        verify(paidFeaturePeriodService).getHistory(1L, pageable);
    }

    @Test
    void purchaseVip_shouldRecordPaidFeaturePeriod() {
        LocalDateTime before = LocalDateTime.now();

        walletService.purchaseVip(1L, "vip-key-period");

        verify(paidFeaturePeriodService).record(eq(1L), eq(PaidFeatureType.VIP),
                eq(900L), any(LocalDateTime.class), argThat(end -> end.isAfter(before)));
    }
}
