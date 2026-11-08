package org.example.helptreeservice.enums;

/**
 * Cross-cutting product toggles that operators can flip without a deploy.
 *
 * <p>The code is part of the operational contract (metric tag and log field),
 * so it stays stable even if the enum constant is renamed.</p>
 */
public enum FeatureFlag {
    REPORTS("reports", "Жалобы"),
    NOTIFICATIONS("notifications", "Уведомления"),
    USER_BLOCKS("user-blocks", "Блокировки пользователей"),
    MODERATION("moderation", "Модерация");

    private final String code;
    private final String title;

    FeatureFlag(String code, String title) {
        this.code = code;
        this.title = title;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }
}
