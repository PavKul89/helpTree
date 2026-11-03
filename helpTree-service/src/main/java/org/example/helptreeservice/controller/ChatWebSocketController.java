package org.example.helptreeservice.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.chat.CreateMessageRequest;
import org.example.helptreeservice.dto.chat.CreateMessageWsRequest;
import org.example.helptreeservice.dto.chat.WsMessageEnvelope;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.example.helptreeservice.security.WsUserPrincipal;
import org.example.helptreeservice.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;

    public ChatWebSocketController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat/{chatId}/messages")
    public void sendMessage(
            @DestinationVariable Long chatId,
            @Payload @Valid CreateMessageWsRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        WsUserPrincipal principal = (WsUserPrincipal) headerAccessor.getUser();

        if (principal == null) {
            log.warn("WebSocket send attempt without authentication");
            return;
        }

        log.info("WebSocket message send to chat {} by user {}", chatId, principal.getUserId());

        CreateMessageRequest restRequest = new CreateMessageRequest(request.getContent());
        chatService.sendMessage(chatId, restRequest, principal.getUserId());
    }

    /**
     * A rejected message must not tear down the WebSocket session. The error is
     * delivered to the caller's private queue, which only that user can subscribe to.
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public WsMessageEnvelope handleMessageException(Exception exception) {
        String message = exception instanceof RateLimitExceededException
                ? exception.getMessage()
                : "Не удалось отправить сообщение";
        log.warn("Отклонена отправка WebSocket-сообщения: {}", exception.getMessage());
        return WsMessageEnvelope.error(message);
    }
}
