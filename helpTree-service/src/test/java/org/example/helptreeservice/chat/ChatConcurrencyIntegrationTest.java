package org.example.helptreeservice.chat;

import org.example.helptreeservice.dto.chat.CreateChatRequest;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.repository.ChatRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that concurrent create-chat requests for the same unordered pair (including
 * reversed arguments) never create duplicate rows. The test commits real data and
 * removes it afterwards; run with {@code INTEGRATION_TEST=true}.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class ChatConcurrencyIntegrationTest {

    private static final int ATTEMPTS = 6;

    @Autowired
    private ChatService chatService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;

    @Test
    void concurrentCreationShouldProduceExactlyOneChatPerPair() throws Exception {
        User alice = createUser("Concurrent Alice");
        User bob = createUser("Concurrent Bob");

        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < ATTEMPTS; i++) {
                boolean aliceInitiates = i % 2 == 0;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    CreateChatRequest request = new CreateChatRequest();
                    request.setParticipantId(aliceInitiates ? bob.getId() : alice.getId());
                    return chatService.createChat(
                            request, aliceInitiates ? alice.getId() : bob.getId()).getId();
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<Long> ids = new ArrayList<>();
            for (Future<Long> future : futures) {
                ids.add(future.get(30, TimeUnit.SECONDS));
            }

            assertEquals(1L, ids.stream().distinct().count(),
                    "All concurrent requests must resolve to the same chat");
            assertTrue(chatRepository.findByUsers(alice.getId(), bob.getId()).isPresent());
        } finally {
            pool.shutdownNow();
            chatRepository.findByUsers(alice.getId(), bob.getId())
                    .ifPresent(chat -> chatRepository.deleteAll(List.of(chat)));
            chatRepository.flush();
            userRepository.deleteAll(List.of(alice, bob));
            userRepository.flush();
        }
    }

    private User createUser(String name) {
        User user = new User();
        user.setName(name);
        user.setEmail("chat-concurrency-" + UUID.randomUUID() + "@test.local");
        user.setPassword("$2a$10$test-only-not-a-real-hash");
        return userRepository.saveAndFlush(user);
    }
}
