package org.example.helptreeservice.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.WsUserPrincipal;
import org.example.helptreeservice.dto.chat.CreateMessageRequest;
import org.example.helptreeservice.dto.chat.CreateMessageWsRequest;
import org.example.helptreeservice.dto.chat.MessageResponse;
import org.example.helptreeservice.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
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
}
