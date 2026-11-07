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

    /**
     * Generic transactional notification email. Unlike the security flows it
     * carries no token and is delivered from the durable outbox, where a false
     * result schedules a retry.
     */
    boolean sendNotification(String to, String subject, String body);
}
