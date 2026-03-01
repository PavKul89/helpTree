package com.example.helpTree.enums;

public enum UserStatus {
    NEWBIE("Новичок", "Может только создавать посты"),
    HELPER("Помощник", "Должен помочь 2 людям"),
    ACTIVE("Активный", "Может всё, помог двоим"),
    DEBTOR("Должник", "Не помогает другим");

    private final String displayName;
    private final String description;

    UserStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
