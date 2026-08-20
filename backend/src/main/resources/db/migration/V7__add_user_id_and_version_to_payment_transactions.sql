-- V7 : Lier les transactions au compte utilisateur + verrou optimiste
-- user_id    : FK vers users (nullable pour rétrocompatibilité avec transactions existantes)
-- version    : colonne de verrouillage optimiste JPA (@Version) — évite les race conditions webhook

ALTER TABLE payment_transactions
    ADD COLUMN user_id BIGINT       NULL     AFTER customer_email,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER user_id;

ALTER TABLE payment_transactions
    ADD CONSTRAINT fk_pt_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_pt_user_id ON payment_transactions (user_id);
