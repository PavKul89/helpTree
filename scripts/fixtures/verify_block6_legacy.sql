-- Verify that the representative V40 data survived V41/V42 unchanged
-- (apart from the two documented normalizations).
\set ON_ERROR_STOP on

DO $$
DECLARE
    actual NUMERIC;
BEGIN
    SELECT COUNT(*) INTO actual FROM users;
    IF actual <> 2 THEN
        RAISE EXCEPTION 'users count changed: expected 2, got %', actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM posts;
    IF actual <> 1 THEN
        RAISE EXCEPTION 'posts count changed: expected 1, got %', actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM helps;
    IF actual <> 1 THEN
        RAISE EXCEPTION 'helps count changed: expected 1, got %', actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM chats;
    IF actual <> 1 THEN
        RAISE EXCEPTION 'chats count changed: expected 1, got %', actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM messages;
    IF actual <> 2 THEN
        RAISE EXCEPTION 'messages count changed: expected 2, got %', actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM comments;
    IF actual <> 1 THEN
        RAISE EXCEPTION 'comments count changed: expected 1, got %', actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM achievements;
    IF actual <> 1 THEN
        RAISE EXCEPTION 'achievements count changed: expected 1, got %', actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM reviews;
    IF actual <> 1 THEN
        RAISE EXCEPTION 'reviews count changed: expected 1, got %', actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM chat_user_state;
    IF actual <> 2 THEN
        RAISE EXCEPTION 'chat_user_state count changed: expected 2, got %', actual;
    END IF;

    SELECT rating INTO actual FROM users WHERE id = 1;
    IF actual <> 3 THEN
        RAISE EXCEPTION 'legacy rating 0 must normalize to 3.0, got %', actual;
    END IF;

    SELECT rating INTO actual FROM users WHERE id = 2;
    IF actual <> 4.5 THEN
        RAISE EXCEPTION 'valid rating must be preserved, got %', actual;
    END IF;

    SELECT debt_count INTO actual FROM users WHERE id = 2;
    IF actual <> 0 THEN
        RAISE EXCEPTION 'legacy NULL debt_count must normalize to 0, got %', actual;
    END IF;
END $$;
