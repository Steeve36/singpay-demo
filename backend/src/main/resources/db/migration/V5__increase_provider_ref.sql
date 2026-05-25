-- Stripe Checkout Session IDs (cs_test_...) peuvent dépasser 100 caractères
ALTER TABLE payment_transactions
    MODIFY COLUMN provider_ref VARCHAR(255);
