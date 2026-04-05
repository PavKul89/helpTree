DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'coin_transactions_type_check'
    ) THEN
        ALTER TABLE coin_transactions DROP CONSTRAINT coin_transactions_type_check;
    END IF;
    
    ALTER TABLE coin_transactions ADD CONSTRAINT coin_transactions_type_check 
        CHECK (type IN (
            'HELP_RECEIVED',
            'HELP_GIVEN',
            'REVIEW_BONUS',
            'DAILY_LOGIN',
            'POST_BOOST',
            'VIP_STATUS',
            'ACCOUNT_UNBLOCK',
            'GIFT_SENT',
            'GIFT_RECEIVED',
            'ADMIN_BONUS',
            'FIRST_HELP',
            'NICKNAME_COLOR'
        ));
END $$;
