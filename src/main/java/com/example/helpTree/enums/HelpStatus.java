package com.example.helpTree.enums;

public enum HelpStatus {
    ACCEPTED("Принято", "Помощник откликнулся"),
    COMPLETED("Выполнено", "Помощник отметил как выполненное"),
    CONFIRMED("Подтверждено", "Автор подтвердил выполнение"),
    CANCELLED("Отменено", "Помощь отменена");

    private final String displayName;
    private final String description;

    HelpStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
