-- V40: allow the canonical VIP ledger type.
--
-- V39 introduced TransactionType.VIP for VIP purchases (VIP_STATUS remains a
-- legacy input alias). The old CHECK constraint from V31 only listed VIP_STATUS,
-- so every VIP purchase was rejected by the database. Recreate the constraint
-- with the full current enum so application and database agree.

ALTER TABLE coin_transactions DROP CONSTRAINT IF EXISTS coin_transactions_type_check;

ALTER TABLE coin_transactions ADD CONSTRAINT coin_transactions_type_check
    CHECK (type IN (
        'HELP_RECEIVED',
        'HELP_GIVEN',
        'REVIEW_BONUS',
        'DAILY_LOGIN',
        'POST_BOOST',
        'BOOST',
        'VIP',
        'VIP_STATUS',
        'ACCOUNT_UNBLOCK',
        'GIFT_SENT',
        'GIFT_RECEIVED',
        'ADMIN_BONUS',
        'FIRST_HELP',
        'NICKNAME_COLOR'
    ));
