package org.example.helptreeservice.controller;

import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.UserService;
import org.example.helptreeservice.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerOwnershipTest {

    @Mock
    private UserService userService;
    @Mock
    private AuthorizationService authService;
    @Mock
    private WalletService walletService;
    @Mock
    private PaginationConfig paginationConfig;
    @Mock
    private org.example.helptreeservice.config.EconomyConfig economyConfig;

    @InjectMocks
    private UserController userController;

    private static final Long OTHER_ID = 2L;
    private static final Long OWNER_ID = 1L;

    @BeforeEach
    void denyByDefault() {
        lenient().when(authService.canManageUser(anyLong())).thenReturn(false);
        lenient().when(authService.isAdmin()).thenReturn(false);
    }

    @Test
    void getUserById_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class, () -> userController.getUserById(OTHER_ID));
        verify(userService, never()).getUserById(anyLong());
    }

    @Test
    void updateUser_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class,
                () -> userController.updateUser(OTHER_ID, null));
        verify(userService, never()).updateUser(anyLong(), any());
    }

    @Test
    void deleteUser_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class, () -> userController.deleteUser(OTHER_ID));
        verify(userService, never()).deleteUser(anyLong());
    }

    @Test
    void getTelegramChatId_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class, () -> userController.getTelegramChatId(OTHER_ID));
        verify(userService, never()).getTelegramChatId(anyLong());
    }

    @Test
    void bindTelegram_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class,
                () -> userController.bindTelegram(OTHER_ID, java.util.Map.of("chatId", "123")));
        verify(userService, never()).bindTelegramChatId(anyLong(), anyString());
    }

    @Test
    void getFavorites_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class, () -> userController.getFavorites(OTHER_ID));
        verify(userService, never()).getFavorites(anyLong());
    }

    @Test
    void isFavorite_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class, () -> userController.isFavorite(OTHER_ID, 5L));
        verify(userService, never()).isFavorite(anyLong(), anyLong());
    }

    @Test
    void addFavorite_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class, () -> userController.addFavorite(OTHER_ID, 5L));
        verify(userService, never()).addFavorite(anyLong(), anyLong());
    }

    @Test
    void removeFavorite_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class, () -> userController.removeFavorite(OTHER_ID, 5L));
        verify(userService, never()).removeFavorite(anyLong(), anyLong());
    }

    @Test
    void getWallet_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class, () -> userController.getWallet(OTHER_ID));
        verify(walletService, never()).getWallet(anyLong());
    }

    @Test
    void getTransactions_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class,
                () -> userController.getTransactions(OTHER_ID, 0, 20, null, null, null, null));
        verify(walletService, never()).getTransactions(
                anyLong(), anyInt(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void claimDailyBonus_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class, () -> userController.claimDailyBonus(OTHER_ID));
        verify(walletService, never()).addDailyLoginBonus(anyLong());
    }

    @Test
    void spendCoins_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class,
                () -> userController.spendCoins(OTHER_ID,
                        org.example.helptreeservice.enums.TransactionType.VIP, null, "test-key"));
        verify(walletService, never()).purchaseVip(anyLong(), anyString());
        verify(walletService, never()).purchaseAccountUnblock(anyLong(), anyString());
        verify(walletService, never()).changeNicknameColor(anyLong(), anyString(), anyString());
    }

    @Test
    void changeNicknameColor_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class,
                () -> userController.changeNicknameColor(OTHER_ID, "#FFFFFF", "test-key"));
        verify(walletService, never()).changeNicknameColor(anyLong(), anyString(), anyString());
    }

    @Test
    void getPricing_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class, () -> userController.getPricing(OTHER_ID));
        verify(walletService, never()).getPricing();
    }

    @Test
    void adminAddCoins_shouldForbidNonAdmin() {
        assertThrows(ForbiddenException.class,
                () -> userController.adminAddCoins(OTHER_ID, 100L, "Gift", null, "admin-test-key"));
        verify(walletService, never()).adminAddCoins(anyLong(), anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void uploadAvatar_shouldForbidForeignProfile() {
        assertThrows(ForbiddenException.class,
                () -> userController.uploadAvatar(OTHER_ID, mock(MultipartFile.class)));
        verify(userService, never()).uploadAvatar(anyLong(), any());
    }

    @Test
    void getWallet_shouldAllowOwner() {
        when(authService.canManageUser(OWNER_ID)).thenReturn(true);
        when(walletService.getWallet(OWNER_ID)).thenReturn(new org.example.helptreeservice.dto.wallet.WalletDto());

        var response = userController.getWallet(OWNER_ID);

        assertEquals(200, response.getStatusCode().value());
        verify(walletService).getWallet(OWNER_ID);
    }

    @Test
    void getWallet_shouldAllowAdminForForeignProfile() {
        when(authService.canManageUser(OTHER_ID)).thenReturn(true);
        when(walletService.getWallet(OTHER_ID)).thenReturn(new org.example.helptreeservice.dto.wallet.WalletDto());

        var response = userController.getWallet(OTHER_ID);

        assertEquals(200, response.getStatusCode().value());
        verify(walletService).getWallet(OTHER_ID);
    }

    @Test
    void getAllUsers_shouldClampPageSizeToServerMaximum() {
        when(authService.isAdmin()).thenReturn(true);
        when(paginationConfig.getMaxPageSize()).thenReturn(100);
        when(userService.getAllUsers(any(Pageable.class), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Page.empty());

        userController.getAllUsers(0, 500, null, null, null, null, "createdAt", "desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).getAllUsers(captor.capture(), isNull(), isNull(), isNull(), isNull());
        assertEquals(100, captor.getValue().getPageSize());
        assertEquals(0, captor.getValue().getPageNumber());
    }

    @Test
    void getAllUsersPublic_shouldClampPageSizeToServerMaximum() {
        when(paginationConfig.getMaxPageSize()).thenReturn(100);
        when(userService.getAllUsersPublic(any(Pageable.class), isNull(), isNull()))
                .thenReturn(Page.empty());

        userController.getAllUsersPublic(1, 1000, null, null, "rating", "desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).getAllUsersPublic(captor.capture(), isNull(), isNull());
        assertEquals(100, captor.getValue().getPageSize());
        assertEquals(1, captor.getValue().getPageNumber());
    }

    @Test
    void getAllUsersPublic_shouldRejectNegativePage() {
        assertThrows(BadRequestException.class,
                () -> userController.getAllUsersPublic(-1, 20, null, null, "rating", "desc"));
        verify(userService, never()).getAllUsersPublic(any(Pageable.class), any(), any());
    }

    @Test
    void getAllUsersPublic_shouldRejectNonPositiveSize() {
        assertThrows(BadRequestException.class,
                () -> userController.getAllUsersPublic(0, 0, null, null, "rating", "desc"));
        verify(userService, never()).getAllUsersPublic(any(Pageable.class), any(), any());
    }

    @Test
    void getAllUsersPublic_shouldAlwaysReturnPagedEnvelope() {
        when(paginationConfig.getDefaultPageSize()).thenReturn(20);
        when(paginationConfig.getMaxPageSize()).thenReturn(100);
        when(userService.getAllUsersPublic(any(Pageable.class), isNull(), isNull()))
                .thenReturn(Page.empty());

        var response = userController.getAllUsersPublic(null, null, null, null, "rating", "desc");

        assertNotNull(response.getBody());
        assertTrue(response.getBody().getContent().isEmpty());
        verify(userService).getAllUsersPublic(any(Pageable.class), isNull(), isNull());
    }

    @Test
    void getAllUsers_shouldAlwaysReturnPagedEnvelope() {
        when(authService.isAdmin()).thenReturn(true);
        when(paginationConfig.getDefaultPageSize()).thenReturn(20);
        when(paginationConfig.getMaxPageSize()).thenReturn(100);
        when(userService.getAllUsers(any(Pageable.class), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Page.empty());

        var response = userController.getAllUsers(null, null, null, null, null, null, "createdAt", "desc");

        assertNotNull(response.getBody());
        assertTrue(response.getBody().getContent().isEmpty());
        verify(userService).getAllUsers(any(Pageable.class), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void resolvePageable_shouldUseDefaultSizeWhenOnlyPageProvided() {
        when(authService.isAdmin()).thenReturn(true);
        when(paginationConfig.getDefaultPageSize()).thenReturn(20);
        when(paginationConfig.getMaxPageSize()).thenReturn(100);
        when(userService.getAllUsers(any(Pageable.class), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Page.empty());

        userController.getAllUsers(2, null, null, null, null, null, "createdAt", "desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).getAllUsers(captor.capture(), isNull(), isNull(), isNull(), isNull());
        assertEquals(PageRequest.of(2, 20).getPageSize(), captor.getValue().getPageSize());
        assertEquals(2, captor.getValue().getPageNumber());
    }
}
