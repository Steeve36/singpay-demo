#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# setup-https.sh — Configuration HTTPS + Nginx sur un VPS Ubuntu/Debian
#
# Usage : sudo bash setup-https.sh ton-domaine.com
#
# Prérequis :
#   - Ubuntu 20.04+ ou Debian 11+
#   - Le domaine pointe déjà sur l'IP du serveur (DNS propagé)
#   - Le backend Spring Boot tourne sur le port 8080
#   - Le build Angular est dans /var/www/singpay/
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

DOMAIN="${1:-}"
if [ -z "$DOMAIN" ]; then
    echo "Usage: sudo bash setup-https.sh ton-domaine.com"
    exit 1
fi

echo "==> Domaine cible : $DOMAIN"

# ── 1. Installer Nginx et Certbot ─────────────────────────────────────────────
echo "==> Installation Nginx + Certbot..."
apt-get update -q
apt-get install -y nginx certbot python3-certbot-nginx

# ── 2. Créer le dossier racine Angular ────────────────────────────────────────
mkdir -p /var/www/singpay
mkdir -p /var/www/certbot

# ── 3. Copier et activer la config Nginx ──────────────────────────────────────
echo "==> Configuration Nginx..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NGINX_CONF="$SCRIPT_DIR/../nginx/singpay.conf"

if [ ! -f "$NGINX_CONF" ]; then
    echo "ERREUR : nginx/singpay.conf introuvable. Lance ce script depuis la racine du projet."
    exit 1
fi

# Remplacer le placeholder par le vrai domaine
sed "s/TON_DOMAINE.COM/$DOMAIN/g" "$NGINX_CONF" \
    > /etc/nginx/sites-available/singpay

ln -sf /etc/nginx/sites-available/singpay /etc/nginx/sites-enabled/singpay

# Désactiver le site default si actif
[ -f /etc/nginx/sites-enabled/default ] && rm /etc/nginx/sites-enabled/default

# Tester la config
nginx -t

# Recharger Nginx avec la config HTTP d'abord (pour le challenge ACME)
systemctl reload nginx

# ── 4. Obtenir le certificat Let's Encrypt ───────────────────────────────────
echo "==> Obtention du certificat Let's Encrypt pour $DOMAIN..."
certbot --nginx -d "$DOMAIN" -d "www.$DOMAIN" \
    --non-interactive \
    --agree-tos \
    --email "admin@$DOMAIN" \
    --redirect

# ── 5. Recharger Nginx avec la config HTTPS ───────────────────────────────────
systemctl reload nginx

# ── 6. Vérifier le renouvellement automatique ─────────────────────────────────
echo "==> Test du renouvellement automatique..."
certbot renew --dry-run

echo ""
echo "✓ HTTPS configuré avec succès pour https://$DOMAIN"
echo ""
echo "Prochaines étapes :"
echo "  1. Déployer le backend : java -jar backend/target/singpay-demo-1.0.0.jar"
echo "  2. Déployer le frontend : cp -r frontend/dist/frontend/* /var/www/singpay/"
echo "  3. Enregistrer le webhook Stripe : https://$DOMAIN/api/webhooks/stripe"
echo "  4. Mettre à jour APP_FRONTEND_URL=https://$DOMAIN dans backend/.env"
echo "  5. Vérifier CORS : app.frontend-url doit correspondre à https://$DOMAIN"
