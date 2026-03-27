package org.example.helptreeservice.enums;

public enum AchievementType {
    // По количеству помощи
    SEEDLING("🌱", "Новичок", "Зарегистрировался", "COMMON"),
    FIRST_HELP("🤝", "Первая помощь", "Помог 1 раз", "COMMON"),
    HELPER_5("⭐", "Волонтёр", "Помог 5 раз", "COMMON"),
    HELPER_10("💪", "Благотворитель", "Помог 10 раз", "UNCOMMON"),
    HELPER_25("👑", "Меценат", "Помог 25 раз", "RARE"),
    HELPER_50("🏆", "Легенда", "Помог 50 раз", "EPIC"),
    HELPER_100("💎", "Алмазная рука", "Помог 100 раз", "LEGENDARY"),

    // По скорости
    FAST_HELP("⚡", "Скороход", "Помог в течение часа", "COMMON"),
    NIGHT_OWL("🦉", "Ночной сова", "Помог ночью (22:00-06:00)", "COMMON"),
    WEEKEND_HERO("🎉", "Выходной герой", "Помог в выходные", "COMMON"),

    // По долголетию
    CONSISTENT("🔥", "Стойкий", "Помогал 3 месяца подряд", "RARE"),

    // По общению
    FIRST_CHAT("💬", "Общительный", "Написал 1 сообщение", "COMMON"),
    CHATTER_10("🗣️", "Болтун", "Написал 50 сообщений", "UNCOMMON"),
    FIRST_POST("📝", "Автор", "Создал 1 пост", "COMMON"),
    POSTER_10("✍️", "Публицист", "Создал 10 постов", "UNCOMMON"),

    // По долгам/рейтингу
    DEBT_FREE("⚖️", "Без долгов", "Долг = 0 при 5+ помощах", "UNCOMMON"),
    BALANCED("⚖️", "Мастер баланса", "Помог > чем получил", "RARE"),
    REPUTATION_5("⭐⭐⭐⭐⭐", "Идеальный", "Рейтинг 5.0", "LEGENDARY"),

    // Категории
    QUICK_FIX("🔧", "Мастер на все руки", "Помог в 5 категориях", "UNCOMMON"),
    VARIETY("🌈", "Разносторонний", "Помог 10+ людям", "RARE"),

    // Праздничные
    NEW_YEAR_WIZARD("🎄", "Новогодний волшебник", "Помощь в декабре", "COMMON"),
    EASTER_BUNNY("🕯️", "Пасхальный заяц", "7 помощей за Пасху", "RARE"),
    HALLOWEEN_HERO("🎃", "Хэллоуинский герой", "Помощь 31 октября", "COMMON"),
    BIRTHDAY_HERO("🎁", "День рождения", "Помощь в свой день рождения", "RARE"),
    WINTER_HELPER("❄️", "Зимний помощник", "Помогли в метель", "RARE");

    private final String emoji;
    private final String name;
    private final String description;
    private final String rarity;

    AchievementType(String emoji, String name, String description, String rarity) {
        this.emoji = emoji;
        this.name = name;
        this.description = description;
        this.rarity = rarity;
    }

    public String getEmoji() { return emoji; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getRarity() { return rarity; }
}
