package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.chat.ChatResponse;
import org.example.helptreeservice.dto.chat.CreateChatRequest;
import org.example.helptreeservice.dto.chat.MessageCursorResponse;
import org.example.helptreeservice.dto.chat.MessageResponse;
import org.example.helptreeservice.dto.chat.UpdateMessageRequest;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.ChatService;
import org.example.helptreeservice.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat", description = "Личные чаты и сообщения")
public class ChatController {

    private final ChatService chatService;
    private final AuthorizationService authorizationService;
    private final PaginationConfig paginationConfig;

    @PostMapping
    @Operation(summary = "Создать чат",
            description = "Идемпотентно: повторный чат с тем же участником возвращается существующий.")
    public ResponseEntity<ChatResponse> createChat(@Valid @RequestBody CreateChatRequest request) {
        AuthorizationService.UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        ChatResponse response = chatService.createChat(request, user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Мои чаты",
            description = "Ограниченная коллекция, возвращается массивом, а не PageResponse.")
    public ResponseEntity<List<ChatResponse>> getChats() {
        AuthorizationService.UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        List<ChatResponse> chats = chatService.getChats(user.getUserId());
        return ResponseEntity.ok(chats);
    }

    @GetMapping("/{chatId}/messages")
    @Operation(summary = "Сообщения чата (постранично)",
            description = "PageResponse; новые сообщения первыми. Для бесконечной прокрутки есть cursor-вариант.")
    public ResponseEntity<PageResponse<MessageResponse>> getMessages(
            @PathVariable Long chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AuthorizationService.UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        Pageable pageable = PageRequests.of(page, size, paginationConfig, Sort.unsorted());
        Page<MessageResponse> messages = chatService.getMessages(chatId, user.getUserId(), pageable);
        return ResponseEntity.ok(PageResponse.from(messages));
    }

    @GetMapping("/{chatId}/messages/cursor")
    @Operation(summary = "Сообщения чата (cursor-пагинация)",
            description = "Возвращает items/nextCursor/hasMore; стабильно при появлении новых сообщений.")
    public ResponseEntity<MessageCursorResponse> getMessagesCursor(
            @PathVariable Long chatId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        AuthorizationService.UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        MessageCursorResponse messages = chatService.getMessagesCursor(
                chatId, beforeId, limit, user.getUserId());
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{chatId}/read")
    public ResponseEntity<Void> markMessagesAsRead(@PathVariable Long chatId) {
        AuthorizationService.UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        chatService.markMessagesAsRead(chatId, user.getUserId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteChat(@PathVariable Long chatId) {
        AuthorizationService.UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        chatService.deleteChat(chatId, user.getUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{chatId}/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long chatId,
            @PathVariable Long messageId) {
        
        AuthorizationService.UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        chatService.deleteMessage(chatId, messageId, user.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{chatId}/messages/{messageId}")
    @Operation(summary = "Отредактировать своё сообщение",
            description = "Доступно отправителю в течение настроенного окна (по умолчанию 15 минут).")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable Long chatId,
            @PathVariable Long messageId,
            @Valid @RequestBody UpdateMessageRequest request) {
        AuthorizationService.UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        MessageResponse response = chatService.editMessage(
                chatId, messageId, request, user.getUserId());
        return ResponseEntity.ok(response);
    }
}
