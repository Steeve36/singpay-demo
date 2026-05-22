# SingPay Demo — Angular + Spring Boot + MySQL

Exemple fonctionnel d'intégration SingPay Mobile Money (approche `/ext`).

---

## Architecture

```
Angular (port 4200)
  └── POST /api/payment/create-link  ──►  Spring Boot (port 8080)
                                              └── POST /ext  ──►  SingPay Gateway
                                                                      └── USSD Push client
  ◄── redirect vers link SingPay  ─────────────────────────────────────────────┘

Après paiement :
  SingPay  ──►  POST /webhook/singpay  ──►  Spring Boot  ──►  MySQL

Angular  ──►  GET /api/payment/status/{ref}  ──►  Spring Boot  ──►  SingPay
```

---

## Prérequis

- Java 21+
- Node.js 20+ / Angular CLI 17+
- MySQL 8+
- Compte SingPay Workspace (https://workspace.singpay.ga)

---

## Configuration

### 1. Variables d'environnement Spring Boot

```bash
export SINGPAY_CLIENT_ID=ton_client_id
export SINGPAY_CLIENT_SECRET=ton_client_secret
export SINGPAY_WALLET_ID=ton_wallet_id
export SINGPAY_DISBURSEMENT_ID=ton_disbursement_id   # obligatoire en prod
export APP_BASE_URL=https://monsite.com              # URL publique de ton app
export DB_USER=root
export DB_PASSWORD=motdepasse
```

### 2. Base de données MySQL

```sql
CREATE DATABASE singpay_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Spring Boot créera automatiquement la table `orders` au démarrage (`ddl-auto: update`).

### 3. Configurer la callbackURL SingPay (une seule fois)

```bash
curl -X PUT "https://gateway.singpay.ga/v1/portefeuille/api/$SINGPAY_WALLET_ID" \
  -H "x-client-id: $SINGPAY_CLIENT_ID" \
  -H "x-client-secret: $SINGPAY_CLIENT_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"callbackURL": "https://monsite.com/webhook/singpay"}'
```

---

## Démarrage

### Backend Spring Boot

```bash
cd backend
mvn spring-boot:run
```

### Frontend Angular

```bash
cd frontend
npm install
ng serve --proxy-config proxy.conf.json
```

Créer `frontend/proxy.conf.json` pour le dev :

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  },
  "/webhook": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```

---

## Flux de paiement complet (/ext)

1. **L'utilisateur** remplit le formulaire checkout Angular
2. **Angular** POST `/api/payment/create-link` → Spring Boot
3. **Spring Boot** POST `/ext` → SingPay → reçoit `{ link, exp }`
4. **Spring Boot** sauvegarde la commande en PENDING, retourne le lien
5. **Angular** redirige `window.location.href = link`
6. **L'utilisateur** choisit son opérateur et confirme sur la page SingPay
7. **SingPay** envoie un USSD Push sur le téléphone du client
8. **Le client** confirme avec son PIN
9. **SingPay** appelle `/webhook/singpay` → Spring Boot met à jour MySQL (PAID / FAILED_*)
10. **SingPay** redirige le navigateur vers `redirect_success?reference=...` ou `redirect_error?reference=...`
11. **Angular** (page success/error) GET `/api/payment/status/{ref}` → affiche le récapitulatif

---

## Statuts de transaction

| Statut order    | Description                          |
|-----------------|--------------------------------------|
| `PENDING`       | Lien généré, paiement en attente     |
| `PAID`          | Paiement confirmé avec succès        |
| `FAILED_BalanceError`   | Solde insuffisant            |
| `FAILED_PasswordError`  | PIN incorrect                |
| `FAILED_TimeOutError`   | Délai de confirmation dépassé|
| `FAILED_Error`          | Erreur générique             |
| `FRAUD_SUSPECTED`       | Montant incohérent détecté   |

---

## Sécurité

- Les credentials SingPay ne transitent **jamais** côté Angular
- Chaque référence est **unique** — vérification d'idempotence côté backend
- Le montant du webhook est **vérifié** contre la base de données
- Répondre toujours **HTTP 200** au webhook pour éviter les rejeux infinis
