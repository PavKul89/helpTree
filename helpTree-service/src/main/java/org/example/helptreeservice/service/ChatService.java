package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.ChatProperties;
import org.example.helptreeservice.dto.chat.ChatResponse;
import org.example.helptreeservice.dto.chat.CreateChatRequest;
import org.example.helptreeservice.dto.chat.CreateMessageRequest;
import org.example.helptreeservice.dto.chat.MessageCursorResponse;
import org.example.helptreeservice.dto.chat.MessageResponse;
import org.example.helptreeservice.dto.chat.UpdateMessageRequest;
import org.example.helptreeservice.dto.chat.WsMessageEnvelope;
import org.example.helptreeservice.entity.Chat;
import org.example.helptreeservice.entity.ChatUserState;
import org.example.helptreeservice.entity.Message;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.ImagePurpose;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.ChatMapper;
import org.example.helptreeservice.repository.ChatRepository;
import org.example.helptreeservice.repository.ChatUserStateRepository;
import org.example.helptreeservice.repository.MessageRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatUserStateRepository chatUserStateRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatMapper chatMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatContentSanitizer contentSanitizer;
    private final ChatRateLimiter chatRateLimiter;
    private final ChatProperties chatProperties;
    private final UserBlockService userBlockService;
    private final ImageObjectService imageObjectService;

    /**
     * Creates or returns the single chat for an unordered participant pair.
     * Both user rows are locked in deterministic id order, so concurrent requests
     * for the same or reverse pair cannot create duplicates; the normalized unique
     * index in the database is the final backstop.
     */
    @Transactional
    public ChatResponse createChat(CreateChatRequest request, Long currentUserId) {
        Long participantId = request != null ? request.getParticipantId() : null;
        if (participantId == null) {
            throw new BadRequestException("ID пользователя обязательно");
        }
        if (participantId.equals(currentUserId)) {
            throw new BadRequestException("Нельзя создать чат с самим собой");
        }

        List<Long> orderedIds = List.of(currentUserId, participantId).stream()
                .sorted()
                .toList();
        List<User> lockedUsers = userRepository.findAllByIdInForUpdate(orderedIds);
        if (lockedUsers.size() != 2) {
            throw new NotFoundException("Пользователь не найден");
        }

        User currentUser = findUser(lockedUsers, currentUserId);
        User participant = findUser(lockedUsers, participantId);
        assertCanUseChat(currentUser);
        assertCanUseChat(participant);
        userBlockService.assertInteractionAllowed(currentUserId, participantId, UserBlockScope.MESSAGES,
                "Один из пользователей ограничил переписку");

        Chat chat = chatRepository.findByUsers(currentUserId, participantId)
                .orElseGet(() -> chatRepository.save(createCanonicalChat(currentUser, participant)));

        ensureState(chat, currentUser);
        ensureState(chat, participant);
        chatUserStateRepository.restore(chat.getId(), currentUserId);

        Chat freshChat = chatRepository.findById(chat.getId())
                .orElseThrow(() -> new NotFoundException("Чат не найден"));
        ChatUserState state = chatUserStateRepository
                .findByChatIdAndUserId(chat.getId(), currentUserId)
                .orElse(null);

        log.info("Создан/найден чат ID={} между пользователями {} и {}",
                chat.getId(), currentUserId, participantId);
        return chatMapper.toResponse(freshChat, currentUserId, state);
    }

    @Transactional(readOnly = true)
    public List<ChatResponse> getChats(Long currentUserId) {
        getActiveUser(currentUserId);
        List<Chat> chats = chatRepository.findVisibleByUserId(currentUserId);
        Map<Long, ChatUserState> states = chatUserStateRepository
                .findByUserIdAndDeletedAtIsNull(currentUserId)
                .stream()
                .collect(Collectors.toMap(
                        state -> state.getChat().getId(),
                        state -> state,
                        (first, ignored) -> first));

        return chats.stream()
                .map(chat -> chatMapper.toResponse(
                        chat, currentUserId, states.get(chat.getId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public MessageResponse sendMessage(Long chatId, CreateMessageRequest request, Long currentUserId) {
        Chat chat = getChatForParticipant(chatId, currentUserId);
        User sender = getActiveUser(currentUserId);
        User recipient = getActiveUser(chat.otherParticipantId(currentUserId));
        assertCanUseChat(sender);
        assertCanUseChat(recipient);
        userBlockService.assertInteractionAllowed(sender.getId(), recipient.getId(), UserBlockScope.MESSAGES,
                "Один из пользователей ограничил переписку");

        String content = sanitizeContent(request != null ? request.getContent() : null,
                request != null && request.getAttachmentUrls() != null
                        && !request.getAttachmentUrls().isEmpty());
        chatRateLimiter.checkAllowed(currentUserId);
        List<String> attachments = claimAttachments(sender.getId(),
                request != null ? request.getAttachmentUrls() : null);

        Message saved = messageRepository.save(Message.builder()
                .chat(chat)
                .sender(sender)
                .content(content)
                .isRead(false)
                .attachmentUrls(attachments)
                .build());

        chat.setLastMessageAt(LocalDateTime.now());
        chatRepository.save(chat);

        ensureState(chat, sender);
        ensureState(chat, recipient);
        // A new message makes the chat visible again for both participants even if
        // one of them hid it earlier.
        chatUserStateRepository.restore(chatId, sender.getId());
        chatUserStateRepository.restore(chatId, recipient.getId());

        MessageResponse response = chatMapper.toMessageResponse(saved);
        List<Delivery> deliveries = new ArrayList<>();
        publishNewMessage(chat, response, deliveries);

        // Keep the sender's read cursor at their own newest message. This is done
        // after the payloads are built because the bulk update clears the
        // persistence context, and after the message row exists but before commit.
        chatUserStateRepository.markReadUpTo(chatId, sender.getId(), saved.getId());
        dispatchAfterCommit(deliveries);

        log.info("Пользователь {} отправил сообщение в чат {}", currentUserId, chatId);
        return response;
    }

    /**
     * Edits the text of the sender's own message inside the configured window.
     * Attachments are immutable; a message with attachments keeps them.
     */
    @Transactional
    public MessageResponse editMessage(Long chatId,
                                       Long messageId,
                                       UpdateMessageRequest request,
                                       Long currentUserId) {
        Chat chat = getChatForParticipant(chatId, currentUserId);
        Message message = messageRepository
                .findByIdAndChatIdAndDeletedAtIsNull(messageId, chatId)
                .orElseThrow(() -> new NotFoundException("Сообщение не найдено"));

        if (!message.getSender().getId().equals(currentUserId)) {
            throw new ForbiddenException("Вы можете редактировать только свои сообщения");
        }

        LocalDateTime now = LocalDateTime.now();
        if (message.getCreatedAt() == null
                || now.isAfter(message.getCreatedAt()
                        .plusMinutes(chatProperties.getEditWindowMinutes()))) {
            throw new BadRequestException("Редактировать сообщение можно только в течение "
                    + chatProperties.getEditWindowMinutes() + " минут");
        }

        boolean hasAttachments = message.getAttachmentUrls() != null
                && !message.getAttachmentUrls().isEmpty();
        String content = sanitizeContent(request != null ? request.getContent() : null, hasAttachments);
        if (content.isEmpty() && !hasAttachments) {
            throw new BadRequestException("Сообщение не может быть пустым");
        }

        message.setContent(content);
        message.setEditedAt(now);
        message.setEditCount(message.getEditCount() == null ? 1 : message.getEditCount() + 1);
        Message saved = messageRepository.save(message);

        MessageResponse response = chatMapper.toMessageResponse(saved);
        WsMessageEnvelope envelope = WsMessageEnvelope.messageUpdated(response);
        List<Delivery> deliveries = new ArrayList<>();
        addChatParticipants(deliveries, chat, envelope);
        addChatUpdated(deliveries, chat, chat.getUser1().getId());
        addChatUpdated(deliveries, chat, chat.getUser2().getId());
        dispatchAfterCommit(deliveries);

        log.info("Пользователь {} отредактировал сообщение {} в чате {}",
                currentUserId, messageId, chatId);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(Long chatId, Long currentUserId, Pageable pageable) {
        getChatForParticipant(chatId, currentUserId);

        return messageRepository
                .findByChatIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(chatId, pageable)
                .map(chatMapper::toMessageResponse);
    }

    /**
     * Cursor pagination for message history. The cursor is the id of the last item
     * already loaded; the response is ordered newest first, matching the legacy
     * pageable endpoint.
     */
    @Transactional(readOnly = true)
    public MessageCursorResponse getMessagesCursor(Long chatId,
                                                   Long beforeId,
                                                   Integer limit,
                                                   Long currentUserId) {
        getChatForParticipant(chatId, currentUserId);

        int pageSize = limit != null ? limit : chatProperties.getCursorDefaultLimit();
        pageSize = Math.max(1, Math.min(pageSize, chatProperties.getCursorMaxLimit()));

        List<Message> messages = messageRepository.findPageByCursor(
                chatId, beforeId, PageRequest.of(0, pageSize + 1));

        boolean hasMore = messages.size() > pageSize;
        List<Message> page = hasMore ? messages.subList(0, pageSize) : messages;
        Long nextCursor = hasMore && !page.isEmpty()
                ? page.get(page.size() - 1).getId()
                : null;

        return MessageCursorResponse.builder()
                .items(page.stream().map(chatMapper::toMessageResponse).toList())
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Transactional
    public void markMessagesAsRead(Long chatId, Long currentUserId) {
        Chat chat = getChatForParticipant(chatId, currentUserId);
        User currentUser = getActiveUser(currentUserId);
        ensureState(chat, currentUser);

        long maxMessageId = messageRepository.findMaxActiveMessageId(chatId);
        messageRepository.markMessagesAsRead(chatId, currentUserId);
        if (maxMessageId > 0) {
            chatUserStateRepository.markReadUpTo(chatId, currentUserId, maxMessageId);
        }

        // The bulk updates clear the persistence context; reload the chat before
        // building per-user responses.
        Chat freshChat = chatRepository.findById(chatId)
                .orElseThrow(() -> new NotFoundException("Чат не найден"));

        WsMessageEnvelope envelope = WsMessageEnvelope.messagesRead(
                chatId, maxMessageId > 0 ? maxMessageId : null);
        List<Delivery> deliveries = new ArrayList<>();
        addChatParticipants(deliveries, freshChat, envelope);
        addChatUpdated(deliveries, freshChat, freshChat.getUser1().getId());
        addChatUpdated(deliveries, freshChat, freshChat.getUser2().getId());
        dispatchAfterCommit(deliveries);

        log.info("Пользователь {} прочитал сообщения в чате {}", currentUserId, chatId);
    }

    /**
     * Soft-deletes the chat only for the calling user. The other participant keeps
     * the full history, and the deletion event is delivered only to the caller's
     * own sessions.
     */
    @Transactional
    public void deleteChat(Long chatId, Long currentUserId) {
        Chat chat = getChatForParticipant(chatId, currentUserId);
        User currentUser = getActiveUser(currentUserId);
        ensureState(chat, currentUser);

        chatUserStateRepository.markDeleted(chatId, currentUserId, LocalDateTime.now());
        log.info("Пользователь {} скрыл чат {}", currentUserId, chatId);

        WsMessageEnvelope envelope = WsMessageEnvelope.chatDeleted(chatId);
        List<Delivery> deliveries = new ArrayList<>();
        addUserDelivery(deliveries, currentUserId, "/queue/chat/" + chatId, envelope);
        addUserDelivery(deliveries, currentUserId, "/queue/chat-updates", envelope);
        addLegacyDelivery(deliveries, "/topic/user/" + currentUserId, envelope);
        dispatchAfterCommit(deliveries);
    }

    @Transactional
    public void deleteMessage(Long chatId, Long messageId, Long currentUserId) {
        Chat chat = getChatForParticipant(chatId, currentUserId);

        Message message = messageRepository
                .findByIdAndChatIdAndDeletedAtIsNull(messageId, chatId)
                .orElseThrow(() -> new NotFoundException("Сообщение не найдено"));

        if (!message.getSender().getId().equals(currentUserId)) {
            throw new ForbiddenException("Вы можете удалить только свое сообщение");
        }

        message.setDeletedAt(LocalDateTime.now());
        message.setDeletedBy(getActiveUser(currentUserId));
        messageRepository.save(message);

        // Keep the chat preview consistent with the surviving history.
        messageRepository.findTopByChatIdAndDeletedAtIsNullOrderByIdDesc(chatId)
                .ifPresentOrElse(
                        latest -> chat.setLastMessageAt(latest.getCreatedAt()),
                        () -> chat.setLastMessageAt(null));
        chatRepository.save(chat);

        WsMessageEnvelope envelope = WsMessageEnvelope.messageDeleted(chatId, messageId);
        List<Delivery> deliveries = new ArrayList<>();
        addChatParticipants(deliveries, chat, envelope);
        addChatUpdated(deliveries, chat, chat.getUser1().getId());
        addChatUpdated(deliveries, chat, chat.getUser2().getId());
        dispatchAfterCommit(deliveries);

        log.info("Пользователь {} удалил сообщение {} из чата {}", currentUserId, messageId, chatId);
    }

    /**
     * Physically removes one batch of messages older than {@code cutoff}. Chat
     * participants' read cursors are cleared where they pointed at a removed
     * message, and chat previews are recomputed. Attachments that are no longer
     * referenced by any live message become DELETED image objects, so the
     * existing Silo cleanup job can remove the bytes.
     */
    @Transactional
    public int purgeExpiredMessages(LocalDateTime cutoff, int limit) {
        int batchSize = Math.max(1, Math.min(limit, chatProperties.getRetentionBatchSize()));
        List<Message> expired = messageRepository.findExpiredMessages(
                cutoff, PageRequest.of(0, batchSize));
        if (expired.isEmpty()) {
            return 0;
        }

        List<Long> ids = expired.stream().map(Message::getId).toList();
        List<String> candidateUrls = expired.stream()
                .map(Message::getAttachmentUrls)
                .filter(urls -> urls != null && !urls.isEmpty())
                .flatMap(List::stream)
                .distinct()
                .toList();
        List<Long> chatIds = expired.stream()
                .map(message -> message.getChat().getId())
                .distinct()
                .toList();

        chatUserStateRepository.clearLastReadMessageIds(ids);
        int deleted = messageRepository.deleteByIdIn(ids);

        // Recompute the preview timestamp for every touched chat.
        for (Long chatId : chatIds) {
            chatRepository.findById(chatId).ifPresent(chat -> {
                messageRepository.findTopByChatIdAndDeletedAtIsNullOrderByIdDesc(chatId)
                        .ifPresentOrElse(
                                latest -> chat.setLastMessageAt(latest.getCreatedAt()),
                                () -> chat.setLastMessageAt(null));
                chatRepository.save(chat);
            });
        }

        if (!candidateUrls.isEmpty()) {
            List<String> unreferenced = candidateUrls.stream()
                    .filter(url -> !messageRepository.existsActiveByAttachmentUrl(url))
                    .toList();
            imageObjectService.markChatAttachmentsDeleted(unreferenced);
        }

        log.info("Retention: удалено {} сообщений старше {}", deleted, cutoff);
        return deleted;
    }

    private Chat createCanonicalChat(User first, User second) {
        User user1 = first.getId() < second.getId() ? first : second;
        User user2 = first.getId() < second.getId() ? second : first;
        return Chat.builder()
                .user1(user1)
                .user2(user2)
                .build();
    }

    private User findUser(List<User> users, Long userId) {
        return users.stream()
                .filter(user -> user.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    private ChatUserState ensureState(Chat chat, User user) {
        return chatUserStateRepository.findByChatIdAndUserId(chat.getId(), user.getId())
                .orElseGet(() -> chatUserStateRepository.save(ChatUserState.builder()
                        .chat(chat)
                        .user(user)
                        .build()));
    }

    private String sanitizeContent(String rawContent, boolean hasAttachments) {
        String content = contentSanitizer.sanitize(rawContent);
        if (contentSanitizer.isBlank(content)) {
            if (hasAttachments) {
                return "";
            }
            throw new BadRequestException("Сообщение не может быть пустым");
        }
        if (contentSanitizer.exceedsMaxLength(content)) {
            throw new BadRequestException("Сообщение слишком длинное");
        }
        return content;
    }

    private List<String> claimAttachments(Long senderId, List<String> rawUrls) {
        if (rawUrls == null || rawUrls.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> urls = rawUrls.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (urls.isEmpty()) {
            return new ArrayList<>();
        }
        if (urls.size() > chatProperties.getMaxAttachments()) {
            throw new BadRequestException("Можно прикрепить не более "
                    + chatProperties.getMaxAttachments() + " изображений");
        }
        imageObjectService.claimForOwner(senderId, urls, ImagePurpose.CHAT);
        return new ArrayList<>(new LinkedHashSet<>(urls));
    }

    private void publishNewMessage(Chat chat, MessageResponse response,
                                   List<Delivery> deliveries) {
        WsMessageEnvelope envelope = WsMessageEnvelope.newMessage(response);
        addChatParticipants(deliveries, chat, envelope);
        addChatUpdated(deliveries, chat, chat.getUser1().getId());
        addChatUpdated(deliveries, chat, chat.getUser2().getId());
    }

    private void addChatParticipants(List<Delivery> deliveries, Chat chat,
                                     WsMessageEnvelope envelope) {
        addUserDelivery(deliveries, chat.getUser1().getId(),
                "/queue/chat/" + chat.getId(), envelope);
        addUserDelivery(deliveries, chat.getUser2().getId(),
                "/queue/chat/" + chat.getId(), envelope);
        addLegacyDelivery(deliveries, "/topic/chat/" + chat.getId(), envelope);
    }

    private void addChatUpdated(List<Delivery> deliveries, Chat chat, Long userId) {
        if (userId == null) {
            return;
        }
        ChatUserState state = chatUserStateRepository
                .findByChatIdAndUserId(chat.getId(), userId)
                .orElse(null);
        ChatResponse response = chatMapper.toResponse(chat, userId, state);
        WsMessageEnvelope envelope = WsMessageEnvelope.chatUpdated(response);
        addUserDelivery(deliveries, userId, "/queue/chat-updates", envelope);
        addLegacyDelivery(deliveries, "/topic/user/" + userId, envelope);
    }

    private void addUserDelivery(List<Delivery> deliveries, Long userId,
                                 String destination, WsMessageEnvelope envelope) {
        deliveries.add(new Delivery(userId, destination, envelope));
    }

    private void addLegacyDelivery(List<Delivery> deliveries,
                                   String destination, WsMessageEnvelope envelope) {
        if (chatProperties.isLegacyTopicsEnabled()) {
            deliveries.add(new Delivery(null, destination, envelope));
        }
    }

    /**
     * WebSocket notifications must never be emitted before the database commit:
     * a client that reacts immediately (for example by deleting the message it
     * just received) would otherwise look at a row that does not exist yet, and a
     * rolled-back transaction would still produce an event. Payloads are built
     * inside the transaction, delivery happens after a successful commit.
     */
    private void dispatchAfterCommit(List<Delivery> deliveries) {
        if (deliveries.isEmpty()) {
            return;
        }
        Runnable dispatch = () -> {
            for (Delivery delivery : deliveries) {
                try {
                    if (delivery.userId() == null) {
                        messagingTemplate.convertAndSend(
                                delivery.destination(), delivery.envelope());
                    } else {
                        messagingTemplate.convertAndSendToUser(
                                delivery.userId().toString(),
                                delivery.destination(),
                                delivery.envelope());
                    }
                } catch (RuntimeException e) {
                    log.warn("Не удалось доставить WebSocket-уведомление {}: {}",
                            delivery.destination(), e.getMessage());
                }
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatch.run();
                        }
                    });
        } else {
            dispatch.run();
        }
    }

    private record Delivery(Long userId, String destination, WsMessageEnvelope envelope) {
    }

    private Chat getChatForParticipant(Long chatId, Long currentUserId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new NotFoundException("Чат не найден"));

        if (!chat.hasParticipant(currentUserId)) {
            throw new ForbiddenException("Вы не участник этого чата");
        }

        return chat;
    }

    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }

        return user;
    }

    private void assertCanUseChat(User user) {
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }
        if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
            throw new ForbiddenException("Один из участников чата заблокирован");
        }
    }
}
