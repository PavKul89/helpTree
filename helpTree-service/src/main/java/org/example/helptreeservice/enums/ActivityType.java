package org.example.helptreeservice.enums;

public enum ActivityType {
    HELP_GIVEN("Помог", "🤝"),
    HELP_RECEIVED("Получил помощь", "🙏"),
    POST_CREATED("Создал запрос", "📝"),
    ACHIEVEMENT_EARNED("Получил достижение", "🏆"),
    USER_BLOCKED("Заблокирован за долг", "🚫");

    private final String label;
    private final String emoji;

    ActivityType(String label, String emoji) {
        this.label = label;
        this.emoji = emoji;
    }

    public String getLabel() {
        return label;
    }

    public String getEmoji() {
        return emoji;
    }
}
