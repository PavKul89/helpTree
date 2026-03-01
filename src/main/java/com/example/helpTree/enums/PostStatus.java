package com.example.helpTree.enums;

public enum PostStatus {
    OPEN("Открыт", "Можно откликнуться"),
    IN_PROGRESS("В работе", "Кто-то уже помогает"),
    COMPLETED("Выполнен", "Помощь оказана"),
    CANCELLED("Отменен", "Автор отменил просьбу");

    private final String displayName;
    private final String description;

    PostStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
