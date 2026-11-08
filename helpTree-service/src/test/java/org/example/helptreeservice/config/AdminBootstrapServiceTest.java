package org.example.helptreeservice.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordService passwordService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    private AdminProperties properties;
    private AdminBootstrapService service;

    @BeforeEach
    void setUp() {
        properties = new AdminProperties();
        properties.setBootstrapEnabled(true);
        properties.setEmail("  Admin@HelpTree.dev ");
        properties.setPassword("Str0ng!BootstrapPass");
        service = new AdminBootstrapService(properties, userRepository, passwordService,
                entityManager);
    }

    private void stubAdvisoryLock() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(null);
    }

    @Test
    void disabledBootstrapIsANoOp() {
        properties.setBootstrapEnabled(false);

        assertFalse(service.bootstrapIfEnabled());

        verifyNoInteractions(userRepository, passwordService, entityManager);
    }

    @Test
    void createsExactlyOneActiveAdminWithNormalizedEmailAndHashedPassword() {
        stubAdvisoryLock();
        when(userRepository.countActiveByRole(Role.ADMIN)).thenReturn(0L);
        when(userRepository.findByEmailIgnoreCase("admin@helptree.dev"))
                .thenReturn(Optional.empty());
        when(passwordService.encode("Str0ng!BootstrapPass")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.bootstrapIfEnabled());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User admin = saved.getValue();
        assertEquals("admin@helptree.dev", admin.getEmail());
        assertEquals("bcrypt-hash", admin.getPassword());
        assertEquals(Role.ADMIN, admin.getRole());
        assertEquals(UserStatus.ACTIVE, admin.getStatus());
        assertTrue(admin.getEmailVerified());
        assertEquals(0L, admin.getTokenVersion());
    }

    @Test
    void anExistingAdministratorStopsTheBootstrap() {
        stubAdvisoryLock();
        when(userRepository.countActiveByRole(Role.ADMIN)).thenReturn(1L);

        assertFalse(service.bootstrapIfEnabled());

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void anAlreadyUsedEmailIsRejectedInsteadOfOverwritten() {
        stubAdvisoryLock();
        when(userRepository.countActiveByRole(Role.ADMIN)).thenReturn(0L);
        when(userRepository.findByEmailIgnoreCase("admin@helptree.dev"))
                .thenReturn(Optional.of(new User()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.bootstrapIfEnabled());

        assertTrue(error.getMessage().contains("a***@helptree.dev"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void weakPasswordIsRejectedBeforeAnyDatabaseWrite() {
        properties.setPassword("dev-admin-password");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.bootstrapIfEnabled());

        assertTrue(error.getMessage().contains("ADMIN_PASSWORD"));
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).countActiveByRole(any(Role.class));
    }

    @Test
    void invalidEmailIsRejectedBeforeAnyDatabaseWrite() {
        properties.setEmail("not-an-email");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.bootstrapIfEnabled());

        assertTrue(error.getMessage().contains("ADMIN_EMAIL"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void theAdvisoryLockIsTakenAroundTheCheckAndCreate() {
        stubAdvisoryLock();
        when(userRepository.countActiveByRole(Role.ADMIN)).thenReturn(0L);
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(passwordService.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.bootstrapIfEnabled();

        verify(entityManager).createNativeQuery("SELECT pg_advisory_xact_lock(:key)");
        verify(query).setParameter(anyString(), anyLong());
        verify(query).getSingleResult();
    }
}
