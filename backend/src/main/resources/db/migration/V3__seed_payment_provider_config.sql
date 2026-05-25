-- V3 : Seed initial des providers
-- SingPay (Mobile Money) : actif, priority 1
-- Stripe (CB) : inactif — stub prêt à brancher
-- Bank Transfer : inactif — placeholder

INSERT INTO payment_provider_config (provider, method, active, priority, config_json) VALUES
(
    'SINGPAY',
    'MOBILE_MONEY',
    1,
    1,
    '{"operators": ["AIRTEL", "MOOV", "MAVIANCE"], "subtypes": ["USSD", "EXT_LINK"]}'
),
(
    'STRIPE',
    'CB',
    0,
    1,
    '{}'
),
(
    'BANK_TRANSFER',
    'BANK_TRANSFER',
    0,
    1,
    '{"label": "Virement bancaire", "delay_days": "2-3 jours ouvrés"}'
);
