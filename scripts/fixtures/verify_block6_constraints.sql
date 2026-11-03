-- Assert that the Block 6 constraints reject invalid writes.
-- Every insert runs in a PL/pgSQL subtransaction and is rolled back when the
-- expected constraint fires. The script fails if a write is unexpectedly
-- accepted.
\set ON_ERROR_STOP on

DO $$
DECLARE
    rejected BOOLEAN;
BEGIN
    -- rating outside 1..5
    rejected := FALSE;
    BEGIN
        INSERT INTO users (name, email, password, rating)
        VALUES ('Bad rating', 'bad-rating@example.test', '!', 6);
    EXCEPTION WHEN check_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN
        RAISE EXCEPTION 'rating > 5 was accepted';
    END IF;

    -- negative debt
    rejected := FALSE;
    BEGIN
        INSERT INTO users (name, email, password, debt_count)
        VALUES ('Bad debt', 'bad-debt@example.test', '!', -1);
    EXCEPTION WHEN check_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN
        RAISE EXCEPTION 'negative debt_count was accepted';
    END IF;

    -- negative wallet balance
    rejected := FALSE;
    BEGIN
        INSERT INTO users (name, email, password, help_coins)
        VALUES ('Bad wallet', 'bad-wallet@example.test', '!', -5);
    EXCEPTION WHEN check_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN
        RAISE EXCEPTION 'negative help_coins was accepted';
    END IF;

    -- case-insensitive normalized email uniqueness
    rejected := FALSE;
    BEGIN
        INSERT INTO users (name, email, password)
        VALUES ('Duplicate email', 'LEGACY.RECEIVER@EXAMPLE.TEST', '!');
    EXCEPTION WHEN unique_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN
        RAISE EXCEPTION 'normalized duplicate email was accepted';
    END IF;

    -- one achievement per user and type
    rejected := FALSE;
    BEGIN
        INSERT INTO achievements (user_id, type) VALUES (1, 'FIRST_HELP');
    EXCEPTION WHEN unique_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN
        RAISE EXCEPTION 'duplicate achievement was accepted';
    END IF;

    -- non-canonical / duplicate chat pair
    rejected := FALSE;
    BEGIN
        INSERT INTO chats (user1_id, user2_id) VALUES (2, 1);
    EXCEPTION WHEN integrity_constraint_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN
        RAISE EXCEPTION 'non-canonical chat pair was accepted';
    END IF;

    -- second active help for the same post, including CONFIRMED
    rejected := FALSE;
    BEGIN
        INSERT INTO helps (post_id, helper_id, receiver_id, status, created_at, updated_at, deleted)
        VALUES (1, 1, 2, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);
    EXCEPTION WHEN unique_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN
        RAISE EXCEPTION 'second active help for a post was accepted';
    END IF;

    -- a post must have an explicit owner
    rejected := FALSE;
    BEGIN
        INSERT INTO posts (title, author_name, user_id, status, deleted)
        VALUES ('No owner', 'Nobody', NULL, 'OPEN', FALSE);
    EXCEPTION WHEN not_null_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN
        RAISE EXCEPTION 'post with NULL user_id was accepted';
    END IF;
END $$;
