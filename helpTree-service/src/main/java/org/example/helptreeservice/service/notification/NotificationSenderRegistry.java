package org.example.helptreeservice.service.notification;

import org.example.helptreeservice.enums.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Single lookup point for the configured external transports. */
@Component
public class NotificationSenderRegistry {

    private final Map<NotificationChannel, NotificationSender> senders =
            new EnumMap<>(NotificationChannel.class);

    public NotificationSenderRegistry(List<NotificationSender> configuredSenders) {
        for (NotificationSender sender : configuredSenders) {
            senders.put(sender.channel(), sender);
        }
    }

    public Optional<NotificationSender> find(NotificationChannel channel) {
        return Optional.ofNullable(senders.get(channel));
    }

    /**
     * IN_APP is always available because it is the canonical row plus the
     * authenticated WebSocket queue. External channels require a configured
     * transport.
     */
    public boolean isAvailable(NotificationChannel channel) {
        if (channel == NotificationChannel.IN_APP) {
            return true;
        }
        return find(channel).map(NotificationSender::isAvailable).orElse(false);
    }
}
