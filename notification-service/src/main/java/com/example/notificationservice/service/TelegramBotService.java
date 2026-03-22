package com.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Service
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String botToken;

    public TelegramBotService() {
        super();
    }

    @Override
    public void onUpdateReceived(org.telegram.telegrambots.meta.api.objects.Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            log.info("Получено сообщение от {}: {}", chatId, messageText);

            if (messageText.startsWith("/start")) {
                sendTelegramMessage(chatId, 
                    "Добро пожаловать в helpTree!\n\n" +
                    "Ваш Chat ID: " + chatId + "\n\n" +
                    "Введите этот ID в приложении helpTree для получения уведомлений.");
            } else if (messageText.startsWith("/help")) {
                sendTelegramMessage(chatId, 
                    "Команды:\n" +
                    "/start - Начать\n" +
                    "/myid - Показать ваш Chat ID");
            } else if (messageText.startsWith("/myid")) {
                sendTelegramMessage(chatId, "Ваш Chat ID: " + chatId);
            } else {
                sendTelegramMessage(chatId, "Используйте /start или /help");
            }
        }
    }

    public void sendNotification(Long chatId, String message) {
        sendTelegramMessage(chatId, message);
    }

    private void sendTelegramMessage(Long chatId, String text) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build();
            execute(sendMessage);
            log.info("Telegram уведомление отправлено: chatId=" + chatId);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки Telegram сообщения: chatId=" + chatId + ", error=" + e.getMessage());
        }
    }

    public void sendHelpAccepted(Long chatId, String postTitle, String authorName) {
        String message = "✅ Ваш отклик принят!\n\n" +
            "Заявка: " + postTitle + "\n" +
            "Автор: " + authorName + "\n\n" +
            "Свяжитесь с автором для выполнения помощи.";
        sendNotification(chatId, message);
    }

    public void sendHelpAcceptedToAuthor(Long chatId, String postTitle, String helperName) {
        String message = "✅ Вам будут помогать!\n\n" +
            "Заявка: " + postTitle + "\n" +
            "Помощник: " + helperName + "\n\n" +
            "Свяжитесь с помощником.";
        sendNotification(chatId, message);
    }

    public void sendHelpCompleted(Long chatId, String postTitle, String helperName) {
        String message = "🏁 Помощь выполнена!\n\n" +
            "Заявка: " + postTitle + "\n" +
            "Помощник: " + helperName + "\n\n" +
            "Подтвердите выполнение в приложении helpTree.";
        sendNotification(chatId, message);
    }

    public void sendHelpConfirmed(Long chatId, String postTitle) {
        String message = "🎉 Помощь подтверждена!\n\n" +
            "Заявка: " + postTitle + "\n\n" +
            "Ваш рейтинг повышен! Спасибо за помощь!";
        sendNotification(chatId, message);
    }

    public void sendHelpCancelled(Long chatId, String postTitle) {
        String message = "❌ Помощь отменена!\n\n" +
            "Заявка: " + postTitle + "\n\n" +
            "Попробуйте найти другую помощь.";
        sendNotification(chatId, message);
    }

    @Override
    public String getBotUsername() {
        return "helpTreeNotifierBot";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}
