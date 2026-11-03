-- Representative V40 data used by the Block 6 migration rehearsal.
-- Load this file only after `flyway:migrate -Dflyway.target=40`.
--
-- It deliberately contains the two legacy values V41 normalizes:
--   * user 1 has rating = 0 (the old "unrated" default);
--   * user 2 has debt_count = NULL.
-- All other rows exercise the restored indexes and the V41/V42 constraints.

INSERT INTO users (
    id, name, email, password, phone, city,
    helped_count, debt_count, rating, status, created_at, updated_at,
    deleted, role, help_coins, token_version, email_verified
) VALUES
    (1, 'Legacy Receiver', 'legacy.receiver@example.test', '!', NULL, 'Moscow',
     0, 0, 0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     FALSE, 'USER', 0, 0, TRUE),
    (2, 'Legacy Helper', 'legacy.helper@example.test', '!', NULL, 'Kazan',
     1, NULL, 4.5, 'HELPER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     FALSE, 'USER', 10, 0, TRUE);

INSERT INTO posts (
    id, title, description, author_name, created_at, updated_at,
    user_id, helper_id, status, deleted, deleted_at, category
) VALUES
    (1, 'Legacy post', 'Post created before V41', 'Legacy Receiver',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 2, 'COMPLETED', FALSE, NULL, 'OTHER');

INSERT INTO helps (
    id, post_id, helper_id, receiver_id, status,
    accepted_at, completed_at, confirmed_at, created_at, updated_at,
    deleted, deleted_at
) VALUES
    (1, 1, 2, 1, 'CONFIRMED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE, NULL);

INSERT INTO chats (id, user1_id, user2_id, created_at, last_message_at)
VALUES (1, 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO messages (id, chat_id, sender_id, content, created_at, is_read)
VALUES
    (1, 1, 1, 'Hello', CURRENT_TIMESTAMP, TRUE),
    (2, 1, 2, 'Hi', CURRENT_TIMESTAMP, FALSE);

INSERT INTO chat_user_state (chat_id, user_id, deleted_at, last_read_message_id, created_at, updated_at)
VALUES
    (1, 1, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, 2, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO comments (id, post_id, user_id, content, created_at)
VALUES (1, 1, 2, 'Legacy comment', CURRENT_TIMESTAMP);

INSERT INTO achievements (id, user_id, type, earned_at)
VALUES (1, 1, 'FIRST_HELP', CURRENT_TIMESTAMP);

INSERT INTO reviews (
    id, help_id, from_user_id, to_user_id, rating, comment, created_at, deleted, deleted_at
) VALUES
    (1, 1, 1, 2, 5, 'Great help', CURRENT_TIMESTAMP, FALSE, NULL);

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('posts_id_seq', (SELECT MAX(id) FROM posts));
SELECT setval('helps_id_seq', (SELECT MAX(id) FROM helps));
SELECT setval('chats_id_seq', (SELECT MAX(id) FROM chats));
SELECT setval('messages_id_seq', (SELECT MAX(id) FROM messages));
SELECT setval('comments_id_seq', (SELECT MAX(id) FROM comments));
SELECT setval('achievements_id_seq', (SELECT MAX(id) FROM achievements));
SELECT setval('reviews_id_seq', (SELECT MAX(id) FROM reviews));
SELECT setval('chat_user_state_id_seq', (SELECT MAX(id) FROM chat_user_state));
