-- V1 : Table des transactions multi-passerelles
-- La table `orders` préexistante est traitée comme baseline (V0) par Flyway.

CREATE TABLE IF NOT EXISTS payment_transactions (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    idempotency_key   VARCHAR(64)     NOT NULL,
    order_reference   VARCHAR(64)     NOT NULL,
    method            VARCHAR(20)     NOT NULL COMMENT 'CB | MOBILE_MONEY | BANK_TRANSFER',
    provider          VARCHAR(20)     NOT NULL COMMENT 'SINGPAY | STRIPE | BANK_TRANSFER',
    status            VARCHAR(15)     NOT NULL COMMENT 'PENDING | PROCESSING | SUCCESS | FAILED | REFUNDED',
    amount            INT             NOT NULL COMMENT 'Montant en unité entière (jamais float)',
    currency          VARCHAR(3)      NOT NULL COMMENT 'XAF, EUR, ...',
    provider_ref      VARCHAR(100)    NULL     COMMENT 'ID de transaction côté provider',
    failure_reason    VARCHAR(200)    NULL,
    provider_metadata TEXT            NULL     COMMENT 'JSON non sensible',
    audit_log         TEXT            NULL     COMMENT 'JSON array — append-only',
    customer_name     VARCHAR(100)    NULL,
    customer_email    VARCHAR(150)    NULL,
    created_at        DATETIME        NOT NULL,
    updated_at        DATETIME        NULL,

    PRIMARY KEY (id),
    UNIQUE  KEY idx_pt_idempotency_key (idempotency_key),
    INDEX         idx_pt_order_reference (order_reference),
    INDEX         idx_pt_provider_ref    (provider_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
