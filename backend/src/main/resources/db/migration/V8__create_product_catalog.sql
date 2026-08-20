-- V8 : Catalogue produits — référence de prix serveur
-- Empêche le price tampering : le montant dans la requête de paiement
-- est validé contre le prix stocké ici (source of truth côté serveur).

CREATE TABLE IF NOT EXISTS product_catalog (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    slug         VARCHAR(100) NOT NULL,          -- identifiant URL-safe (ex: "abonnement-mensuel")
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    price_xaf    INT          NOT NULL,           -- prix en FCFA (entier, jamais float)
    currency     VARCHAR(3)   NOT NULL DEFAULT 'XAF',
    active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME,
    CONSTRAINT pk_product_catalog PRIMARY KEY (id),
    CONSTRAINT uq_product_slug    UNIQUE (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Données de démo — à adapter selon les vrais produits
INSERT INTO product_catalog (slug, name, description, price_xaf, currency, active, created_at)
VALUES
  ('abonnement-mensuel',   'Abonnement Mensuel',   'Accès complet pendant 1 mois',  5000,  'XAF', 1, NOW()),
  ('abonnement-trimestr',  'Abonnement Trimestriel','Accès complet pendant 3 mois',  13000, 'XAF', 1, NOW()),
  ('abonnement-annuel',    'Abonnement Annuel',     'Accès complet pendant 12 mois', 45000, 'XAF', 1, NOW()),
  ('achat-unique',         'Achat Unique',          'Accès à vie',                   25000, 'XAF', 1, NOW());
