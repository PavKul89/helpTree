package org.example.helptreeservice.service.email;

/**
 * Delivery abstraction for account security emails. Implementations never expose
 * tokens in HTTP responses; the token only travels to the mailbox.
 */
public interface EmailService {

    boolean isAvailable();

    boolean sendEmailVerification(String to, String token);

    boolean sendPasswordReset(String to, String token);

    boolean sendEmailChangeConfirmation(String to, String newEmail, String token);

    boolean sendAccountInvitation(String to, String token);

    void sendEmailChangedNotice(String oldEmail, String newEmail);
}
