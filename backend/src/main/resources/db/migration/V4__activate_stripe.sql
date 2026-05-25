-- V4 : Active le provider Stripe pour les paiements CB
UPDATE payment_provider_config
SET active = 1
WHERE provider = 'STRIPE'
  AND method   = 'CB';
