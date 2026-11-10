package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.entity.AccountNotificationPreference;
import org.example.helptreeservice.entity.CoinTransaction;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Message;
import org.example.helptreeservice.entity.Notification;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserPrivacySettings;
import org.example.helptreeservice.enums.DigestFrequency;
import org.example.helptreeservice.enums.ProfileVisibility;
import org.example.helptreeservice.repository.AccountNotificationPreferenceRepository;
import org.example.helptreeservice.repository.CoinTransactionRepository;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.MessageRepository;
import org.example.helptreeservice.repository.NotificationRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.ReviewRepository;
import org.example.helptreeservice.repository.UserPrivacySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the portable JSON snapshot used by both the self-service export and the
 * administrative export endpoint. Passwords, tokens and session secrets are
 * deliberately absent.
 */
@Service
@RequiredArgsConstructor
public class UserDataExportService {

    private final UserPrivacySettingsRepository privacySettingsRepository;
    private final AccountNotificationPreferenceRepository notificationPreferenceRepository;
    private final PostRepository postRepository;
    private final ReviewRepository reviewRepository;
    private final HelpRepository helpRepository;
    private final CoinTransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> export(User user) {
        Long userId = user.getId();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("exportedAt", LocalDateTime.now());
        document.put("user", userFields(user));
        document.put("privacy", privacyFields(userId));
        document.put("notifications", notificationFields(userId));
        document.put("posts", postFields(userId));
        document.put("reviews", reviewFields(userId));
        document.put("helps", helpFields(userId));
        document.put("transactions", transactionFields(userId));
        document.put("notificationHistory", notificationHistoryFields(userId));
        document.put("messages", messageFields(userId));
        return document;
    }

    public Map<String, Object> userFields(User user) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", user.getId());
        fields.put("name", user.getName());
        fields.put("email", user.getEmail());
        fields.put("phone", user.getPhone());
        fields.put("city", user.getCity());
        fields.put("latitude", user.getLatitude());
        fields.put("longitude", user.getLongitude());
        fields.put("role", user.getRole());
        fields.put("status", user.getStatus());
        fields.put("rating", user.getRating());
        fields.put("helpedCount", user.getHelpedCount());
        fields.put("debtCount", user.getDebtCount());
        fields.put("helpCoins", user.getHelpCoins());
        fields.put("birthDate", user.getBirthDate());
        fields.put("createdAt", user.getCreatedAt());
        fields.put("updatedAt", user.getUpdatedAt());
        fields.put("lastLogin", user.getLastLogin());
        fields.put("lastSeenAt", user.getLastSeenAt());
        fields.put("vipUntil", user.getVipUntil());
        fields.put("emailVerified", user.getEmailVerified());
        fields.put("deleted", user.getDeleted());
        fields.put("deletedAt", user.getDeletedAt());
        fields.put("anonymizedAt", user.getAnonymizedAt());
        fields.put("favoritePostIds", user.getFavoritePostIds());
        return fields;
    }

    private Map<String, Object> privacyFields(Long userId) {
        UserPrivacySettings settings = privacySettingsRepository.findById(userId).orElse(null);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("profileVisibility", settings != null
                ? settings.getProfileVisibility() : ProfileVisibility.REGISTERED);
        fields.put("showCity", settings != null ? settings.getShowCity() : true);
        fields.put("showEmail", settings != null ? settings.getShowEmail() : false);
        fields.put("showPhone", settings != null ? settings.getShowPhone() : false);
        fields.put("showOnlineStatus", settings != null
                ? (settings.getShowOnlineStatus() == null || settings.getShowOnlineStatus()) : true);
        fields.put("allowDirectMessages", settings != null ? settings.getAllowDirectMessages() : true);
        fields.put("searchable", settings != null ? settings.getSearchable() : true);
        return fields;
    }

    private Map<String, Object> notificationFields(Long userId) {
        AccountNotificationPreference preferences = notificationPreferenceRepository.findById(userId).orElse(null);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("emailEnabled", preferences != null ? preferences.getEmailEnabled() : true);
        fields.put("pushEnabled", preferences != null ? preferences.getPushEnabled() : true);
        fields.put("inAppEnabled", preferences != null ? preferences.getInAppEnabled() : true);
        fields.put("digestFrequency", preferences != null
                ? preferences.getDigestFrequency() : DigestFrequency.INSTANT);
        fields.put("quietHoursStart", preferences != null ? preferences.getQuietHoursStart() : null);
        fields.put("quietHoursEnd", preferences != null ? preferences.getQuietHoursEnd() : null);
        return fields;
    }

    private List<Map<String, Object>> postFields(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Post post : postRepository.findAllForExport(userId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", post.getId());
            item.put("title", post.getTitle());
            item.put("description", post.getDescription());
            item.put("category", post.getCategory());
            item.put("status", post.getStatus());
            item.put("createdAt", post.getCreatedAt());
            item.put("updatedAt", post.getUpdatedAt());
            item.put("imageUrls", post.getImageUrls());
            item.put("helperId", post.getHelper() != null ? post.getHelper().getId() : null);
            item.put("helperName", post.getHelper() != null ? post.getHelper().getName() : null);
            item.put("viewsCount", post.getViewsCount());
            item.put("responsesCount", post.getResponsesCount());
            item.put("commentsCount", post.getCommentsCount());
            item.put("boostedUntil", post.getBoostedUntil());
            item.put("deleted", post.getDeleted());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> reviewFields(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Review review : reviewRepository.findAllForExport(userId)) {
            boolean authored = review.getFromUser() != null
                    && userId.equals(review.getFromUser().getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", review.getId());
            item.put("direction", authored ? "AUTHORED" : "RECEIVED");
            item.put("helpId", review.getHelp() != null ? review.getHelp().getId() : null);
            item.put("postId", review.getHelp() != null && review.getHelp().getPost() != null
                    ? review.getHelp().getPost().getId() : null);
            item.put("postTitle", review.getHelp() != null && review.getHelp().getPost() != null
                    ? review.getHelp().getPost().getTitle() : null);
            item.put("fromUserId", review.getFromUser() != null
                    ? review.getFromUser().getId() : null);
            item.put("toUserId", review.getToUser() != null
                    ? review.getToUser().getId() : null);
            item.put("rating", review.getRating());
            item.put("comment", review.getComment());
            item.put("replyText", review.getReplyText());
            item.put("replyAt", review.getReplyAt());
            item.put("createdAt", review.getCreatedAt());
            item.put("editedAt", review.getEditedAt());
            item.put("editCount", review.getEditCount());
            item.put("deleted", review.getDeleted());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> helpFields(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Help help : helpRepository.findAllForExport(userId)) {
            boolean helper = help.getHelper() != null && userId.equals(help.getHelper().getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", help.getId());
            item.put("role", helper ? "HELPER" : "RECEIVER");
            item.put("postId", help.getPost() != null ? help.getPost().getId() : null);
            item.put("postTitle", help.getPost() != null ? help.getPost().getTitle() : null);
            item.put("helperId", help.getHelper() != null ? help.getHelper().getId() : null);
            item.put("receiverId", help.getReceiver() != null ? help.getReceiver().getId() : null);
            item.put("status", help.getStatus());
            item.put("acceptedAt", help.getAcceptedAt());
            item.put("completedAt", help.getCompletedAt());
            item.put("confirmedAt", help.getConfirmedAt());
            item.put("cancelledAt", help.getCancelledAt());
            item.put("cancelReason", help.getCancelReason());
            item.put("cancelInitiator", help.getCancelInitiator());
            item.put("createdAt", help.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> transactionFields(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CoinTransaction transaction : transactionRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", transaction.getId());
            item.put("type", transaction.getType());
            item.put("amount", transaction.getAmount());
            item.put("description", transaction.getDescription());
            item.put("relatedUserId", transaction.getRelatedUserId());
            item.put("relatedPostId", transaction.getRelatedPostId());
            item.put("createdAt", transaction.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> notificationHistoryFields(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification notification : notificationRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", notification.getId());
            item.put("type", notification.getType());
            item.put("title", notification.getTitle());
            item.put("body", notification.getBody());
            item.put("payload", notification.getPayload());
            item.put("readAt", notification.getReadAt());
            item.put("createdAt", notification.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> messageFields(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message message : messageRepository.findAllForExportBySenderId(userId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", message.getId());
            item.put("chatId", message.getChat() != null ? message.getChat().getId() : null);
            item.put("content", message.getContent());
            item.put("attachmentUrls", message.getAttachmentUrls());
            item.put("createdAt", message.getCreatedAt());
            item.put("editedAt", message.getEditedAt());
            item.put("editCount", message.getEditCount());
            item.put("deletedAt", message.getDeletedAt());
            result.add(item);
        }
        return result;
    }
}
