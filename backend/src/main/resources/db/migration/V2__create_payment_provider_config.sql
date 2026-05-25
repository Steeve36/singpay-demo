-- V2 : Table de configuration des providers de paiement

CREATE TABLE IF NOT EXISTS payment_provider_config (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    provider    VARCHAR(20) NOT NULL COMMENT 'SINGPAY | STRIPE | BANK_TRANSFER',
    method      VARCHAR(20) NOT NULL COMMENT 'CB | MOBILE_MONEY | BANK_TRANSFER',
    active      TINYINT(1)  NOT NULL DEFAULT 0,
    priority    INT         NOT NULL DEFAULT 1 COMMENT 'Plus bas = prioritaire',
    config_json TEXT        NULL     COMMENT 'Config non sensible au format JSON',

    PRIMARY KEY (id),
    UNIQUE KEY idx_ppc_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
