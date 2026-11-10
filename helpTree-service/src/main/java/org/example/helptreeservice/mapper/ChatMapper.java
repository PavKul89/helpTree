package org.example.helptreeservice.mapper;

import org.example.helptreeservice.config.ChatProperties;
import org.example.helptreeservice.dto.chat.ChatResponse;
import org.example.helptreeservice.dto.chat.MessageResponse;
import org.example.helptreeservice.entity.Chat;
import org.example.helptreeservice.entity.ChatUserState;
import org.example.helptreeservice.entity.Message;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.repository.MessageRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class ChatMapper {

    private static final int LAST_MESSAGE_PREVIEW_LENGTH = 50;

    private final MessageRepository messageRepository;
    private final ChatProperties chatProperties;

    public ChatMapper(MessageRepository messageRepository, ChatProperties chatProperties) {
        this.messageRepository = messageRepository;
        this.chatProperties = chatProperties;
    }

    public ChatResponse toResponse(Chat chat, Long currentUserId) {
        return toResponse(chat, currentUserId, null);
    }

    /**
     * Builds a chat list item for one participant. Unread state is per user and is
     * derived from {@code last_read_message_id}; soft-deleted messages are ignored.
     */
    public ChatResponse toResponse(Chat chat, Long currentUserId, ChatUserState state) {
        User participant = chat.getUser1().getId().equals(currentUserId)
                ? chat.getUser2()
                : chat.getUser1();

        String lastMessage = messageRepository
                .findTopByChatIdAndDeletedAtIsNullOrderByIdDesc(chat.getId())
                .map(this::preview)
                .orElse(null);

        Long lastReadMessageId = state != null ? state.getLastReadMessageId() : null;
        long unreadCount = messageRepository.countUnread(
                chat.getId(), currentUserId, lastReadMessageId);

        return ChatResponse.builder()
                .id(chat.getId())
                .participantId(participant.getId())
                .participantName(participant.getName())
                .participantAvatarUrl(participant.getAvatarUrl())
                .lastMessage(lastMessage)
                .lastMessageAt(chat.getLastMessageAt())
                .unreadCount(unreadCount)
                .build();
    }

    public MessageResponse toMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .chatId(message.getChat().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getName())
                .senderAvatarUrl(message.getSender().getAvatarUrl())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .isRead(message.getIsRead())
                .attachmentUrls(message.getAttachmentUrls() == null
                        ? new ArrayList<>() : new ArrayList<>(message.getAttachmentUrls()))
                .editedAt(message.getEditedAt())
                .editCount(message.getEditCount() == null ? 0 : message.getEditCount())
                .editableUntil(message.getCreatedAt() == null
                        ? null
                        : message.getCreatedAt().plusMinutes(chatProperties.getEditWindowMinutes()))
                .build();
    }

    private String preview(Message message) {
        String content = message.getContent();
        if (content != null && !content.isBlank()) {
            return preview(content);
        }
        int attachments = message.getAttachmentUrls() == null ? 0 : message.getAttachmentUrls().size();
        if (attachments == 0) {
            return null;
        }
        return attachments == 1 ? "[изображение]" : "[изображений: " + attachments + "]";
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() <= LAST_MESSAGE_PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, LAST_MESSAGE_PREVIEW_LENGTH) + "...";
    }
}
