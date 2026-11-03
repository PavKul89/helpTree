package org.example.helptreeservice.config;

import org.example.helptreeservice.security.StompAuthChannelInterceptor;
import org.example.helptreeservice.security.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final WebSocketProperties webSocketProperties;
    private final TaskScheduler webSocketHeartbeatScheduler;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor,
                           WebSocketAuthInterceptor webSocketAuthInterceptor,
                           WebSocketProperties webSocketProperties,
                           @Qualifier("webSocketHeartbeatScheduler")
                           TaskScheduler webSocketHeartbeatScheduler) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.webSocketProperties = webSocketProperties;
        this.webSocketHeartbeatScheduler = webSocketHeartbeatScheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        long heartbeatMs = Math.max(1, webSocketProperties.getHeartbeatSeconds()) * 1000L;

        if (webSocketProperties.isRelayEnabled()) {
            // A real STOMP relay lets multiple application instances share
            // subscriptions without sticky sessions. Authorization still happens
            // in StompAuthChannelInterceptor before any frame reaches the relay.
            config.enableStompBrokerRelay("/topic", "/queue")
                    .setRelayHost(webSocketProperties.getRelayHost())
                    .setRelayPort(webSocketProperties.getRelayPort())
                    .setClientLogin(webSocketProperties.getClientLogin())
                    .setClientPasscode(webSocketProperties.getClientPasscode())
                    .setSystemLogin(webSocketProperties.getSystemLogin())
                    .setSystemPasscode(webSocketProperties.getSystemPasscode())
                    .setSystemHeartbeatSendInterval(heartbeatMs)
                    .setSystemHeartbeatReceiveInterval(heartbeatMs);
        } else {
            config.enableSimpleBroker("/topic", "/queue")
                    .setHeartbeatValue(new long[]{heartbeatMs, heartbeatMs})
                    .setTaskScheduler(webSocketHeartbeatScheduler);
        }

        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(webSocketProperties.getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        StompWebSocketEndpointRegistration endpoint = registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns(origins);

        // The HTTP upgrade must present a valid, short-lived handshake ticket.
        // The STOMP CONNECT frame is additionally authenticated with the access
        // token by StompAuthChannelInterceptor.
        endpoint.addInterceptors(webSocketAuthInterceptor);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
