# SingPay Demo — Angular + Spring Boot + MySQL

Intégration complète de SingPay Mobile Money avec un frontend Angular et un backend Spring Boot. Supporte deux modes de paiement : **USSD Push** (débit direct sur le téléphone du client) et **lien externe SingPay** (redirection vers la page de paiement hébergée). La confirmation est reçue dans les deux cas via webhook.

---

## Table des matières

1. [Stack technique](#stack-technique)
2. [Architecture](#architecture)
3. [Modes de paiement](#modes-de-paiement)
4. [Comprendre les deux URLs à configurer](#comprendre-les-deux-urls-à-configurer)
5. [Prérequis](#prérequis)
6. [Installation et démarrage local](#installation-et-démarrage-local)
7. [Webhook SingPay — fonctionnement détaillé](#webhook-singpay--fonctionnement-détaillé)
8. [API endpoints](#api-endpoints)
9. [Statuts de transaction](#statuts-de-transaction)
10. [Déploiement en production](#déploiement-en-production)
11. [Structure du projet](#structure-du-projet)
12. [Sécurité](#sécurité)

---

## Stack technique

### Backend
| Technologie | Version | Rôle |
|---|---|---|
| Java | 21 | Langage |
| Spring Boot | 3.2.5 | Framework principal |
| Spring Web | (inclus) | REST API |
| Spring Data JPA | (inclus) | ORM / accès base de données |
| Spring Validation | (inclus) | Validation des requêtes entrantes |
| Hibernate | (inclus via JPA) | Implémentation JPA |
| MySQL Connector/J | (géré par Boot) | Driver JDBC MySQL |
| spring-dotenv | 4.0.0 | Chargement automatique du fichier `.env` |
| Maven | 3.x | Build et gestion des dépendances |

### Frontend
| Technologie | Version | Rôle |
|---|---|---|
| Node.js | 20+ | Runtime JavaScript |
| Angular CLI | 17.3 | Tooling Angular |
| Angular | 17.3 | Framework frontend (standalone components) |
| TypeScript | 5.4 | Langage |
| RxJS | 7.8 | Programmation réactive (HttpClient) |
| uuid | 9.0 | Génération de références de commande uniques |
| Zone.js | 0.14 | Change detection Angular |

### Infrastructure
| Technologie | Version | Rôle |
|---|---|---|
| MySQL | 8+ | Base de données relationnelle |
| SingPay Gateway | v1 | API Mobile Money (Gabon) |

---

## Architecture

### Mode USSD Push

```
Angular (port 4200)
  └── POST /api/payment/ussd  ──►  Spring Boot (port 8080)
                                       └── POST /ussd  ──►  SingPay Gateway
                                                                └── Prompt USSD → téléphone client

Polling toutes les 5s (max 60s) :
  Angular  ──►  GET /api/payment/order/{ref}  ──►  Spring Boot  ──►  MySQL

Après confirmation PIN par le client :
  SingPay  ──►  POST /webhook/singpay  ──►  Spring Boot  ──►  MySQL (maj statut → PAID / FAILED_*)
  Angular  détecte le changement de statut via polling  ──►  affiche l'écran de résultat
```

### Mode lien externe SingPay (/ext)

```
Angular (port 4200)
  └── POST /api/payment/create-link  ──►  Spring Boot (port 8080)
                                              └── POST /ext  ──►  SingPay Gateway
  ◄── redirect navigateur vers page SingPay ───────────────────────────────────┘

Après confirmation PIN par le client :
  SingPay  ──►  POST /webhook/singpay  ──►  Spring Boot  ──►  MySQL (maj statut)
  SingPay  ──►  redirect navigateur  ──►  Angular /paiement/succes ou /paiement/echec

Angular (page résultat)  ──►  GET /api/payment/order/{ref}  ──►  Spring Boot  ──►  MySQL
```

---

## Modes de paiement

L'application propose deux modes accessibles depuis la même page de catalogue via un toggle.

### USSD Push (mode par défaut)

Le client entre son numéro de téléphone. Le backend déclenche directement un prompt USSD sur son téléphone — il n'est pas redirigé vers une autre page. Il confirme le paiement en saisissant son PIN Mobile Money sur son téléphone.

**Flux :**
1. Le client sélectionne son opérateur (Airtel Money, Moov Money, Maviance)
2. Il saisit son nom, email et numéro de téléphone
3. Le backend appelle `POST /ussd` chez SingPay — un code USSD est envoyé sur le téléphone
4. Angular interroge `GET /api/payment/order/{ref}` toutes les 5 secondes pendant 60s
5. Le client compose son PIN sur son téléphone
6. SingPay notifie le backend via webhook — le statut passe de `PENDING` à `PAID` ou `FAILED_*`
7. Le polling Angular détecte le changement et affiche le résultat

**Avantage :** expérience fluide sans quitter l'application.

### Lien externe SingPay

Le client est redirigé vers la page de paiement hébergée par SingPay. Après confirmation, SingPay le redirige vers `/paiement/succes` ou `/paiement/echec`.

**Avantage :** zéro logique de paiement côté frontend, idéal pour une intégration rapide.

---

## Comprendre les deux URLs à configurer

C'est le point le plus important à comprendre avant de commencer. SingPay a besoin de deux URLs de votre application, et elles ont chacune un rôle différent.

### URL 1 — La callbackURL (le webhook)

C'est l'adresse que SingPay va appeler **en arrière-plan**, de serveur à serveur, pour vous dire si le paiement a réussi ou échoué. L'utilisateur ne voit pas cet appel.

```
https://votre-serveur.com/webhook/singpay
```

**Cette URL pointe vers votre backend (port 8080).** Elle doit être accessible depuis internet — c'est pourquoi en développement local vous avez besoin de ngrok (votre `localhost` n'est pas accessible depuis l'extérieur).

**Cette URL ne se change pas dans le code.** Elle est enregistrée directement dans le système SingPay via une commande `curl` que vous faites une fois. SingPay la mémorise de son côté.

### URL 2 — L'URL de redirection (APP_FRONTEND_URL)

C'est l'adresse vers laquelle SingPay renvoie **le navigateur de l'utilisateur** après le paiement (succès ou échec). L'utilisateur voit cette redirection.

```
http://localhost:4200        ← en développement local
https://votre-domaine.com   ← en production
```

**Cette URL se configure dans votre fichier `.env`**, via la variable `APP_FRONTEND_URL`. Le backend s'en sert pour construire les liens de retour envoyés à SingPay lors de la création du lien de paiement.

### Résumé — ce qui change selon l'environnement

| Ce qui change | En développement local | En production |
|---|---|---|
| **callbackURL** (webhook) | URL ngrok temporaire | Votre domaine HTTPS définitif |
| **Comment la changer** | Re-lancer la commande `curl` | Re-lancer la commande `curl` |
| **APP_FRONTEND_URL** (redirects) | `http://localhost:4200` (défaut) | `https://votre-domaine.com` |
| **Comment la changer** | Rien à faire (valeur par défaut) | Modifier le fichier `.env` |

---

## Prérequis

Avant de commencer, assurez-vous d'avoir installé :

- **Java 21+** — [adoptium.net](https://adoptium.net/)
- **Maven 3.x** — [maven.apache.org](https://maven.apache.org/download.cgi)
- **Node.js 20+** — [nodejs.org](https://nodejs.org/)
- **Angular CLI 17+** — `npm install -g @angular/cli`
- **MySQL 8+** — [dev.mysql.com](https://dev.mysql.com/downloads/mysql/)
- **Un compte SingPay Workspace** — [workspace.singpay.ga](https://workspace.singpay.ga)
- **Un compte ngrok (gratuit)** — [ngrok.com](https://ngrok.com) — nécessaire pour recevoir les webhooks en local

---

## Installation et démarrage local

### Étape 1 — Cloner le dépôt

```bash
git clone <url-du-repo>
cd singpay-demo
```

### Étape 2 — Base de données MySQL

Connectez-vous à MySQL et créez la base de données :

```sql
CREATE DATABASE singpay_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Spring Boot créera automatiquement la table `orders` au premier démarrage. Vous n'avez rien d'autre à faire.

### Étape 3 — Créer le fichier `.env`

Créez un fichier nommé `.env` dans le dossier `backend/`. Ce fichier contient vos informations confidentielles. **Il ne sera jamais envoyé sur Git** (il est dans `.gitignore`).

```env
# Credentials SingPay — obtenus sur workspace.singpay.ga
SINGPAY_CLIENT_ID=ton_client_id
SINGPAY_CLIENT_SECRET=ton_client_secret
SINGPAY_WALLET_ID=ton_wallet_id
SINGPAY_DISBURSEMENT_ID=ton_disbursement_id

# En local, cette valeur par défaut convient — ne pas changer sauf si votre Angular
# tourne sur un autre port
APP_FRONTEND_URL=http://localhost:4200

# Base de données
DB_USER=root
DB_PASSWORD=ton_mot_de_passe_mysql
```

> Ces variables sont chargées automatiquement par le backend au démarrage. Vous n'avez pas à les exporter dans un terminal.

### Étape 4 — Installer et configurer ngrok

ngrok est un outil qui crée un tunnel entre votre ordinateur et internet. Cela permet à SingPay d'envoyer des webhooks à votre backend qui tourne en local, comme si votre machine était un vrai serveur accessible depuis l'extérieur.

**4a. Créer un compte gratuit**

Rendez-vous sur [ngrok.com](https://ngrok.com) et créez un compte gratuit (inscription via GitHub ou email).

**4b. Télécharger ngrok**

Sur la page de téléchargement de ngrok ([ngrok.com/download](https://ngrok.com/download)), choisissez Windows. Vous téléchargez un fichier `.zip` contenant un seul exécutable `ngrok.exe`.

Décompressez ce fichier et placez `ngrok.exe` dans un dossier accessible, par exemple `C:\ngrok\`.

Alternativement, si vous avez `winget` (Windows 10/11) :
```bash
winget install ngrok.ngrok
```

**4c. Récupérer votre token d'authentification**

Une fois connecté sur [ngrok.com](https://ngrok.com), allez dans **"Your Authtoken"** dans le menu gauche (ou directement sur [dashboard.ngrok.com/get-started/your-authtoken](https://dashboard.ngrok.com/get-started/your-authtoken)).

Vous verrez un token de ce genre :
```
2abc123def456_XXXXXXXXXXXXXXXXXXXXXXXXXXX
```

Copiez ce token. Ensuite, dans un terminal, exécutez :

```bash
ngrok config add-authtoken VOTRE_TOKEN_ICI
```

Cette commande enregistre votre token dans un fichier de configuration ngrok sur votre machine. **À faire une seule fois.**

**4d. Lancer le tunnel**

Assurez-vous d'abord que votre backend Spring Boot tourne (étape 6). Puis dans un nouveau terminal :

```bash
ngrok http 8080
```

Vous verrez un affichage comme celui-ci :

```
Session Status     online
Account            votre@email.com (Plan: Free)
Forwarding         https://abc123.ngrok-free.app -> http://localhost:8080
```

L'URL qui vous intéresse est celle en `https://` sur la ligne `Forwarding`. Dans cet exemple : `https://abc123.ngrok-free.app`.

> **Important :** cette URL change à chaque fois que vous relancez ngrok. Si vous fermez et relancez ngrok, vous obtiendrez une URL différente, et vous devrez refaire l'étape 5 ci-dessous.

### Étape 5 — Enregistrer la callbackURL chez SingPay

C'est l'étape qui dit à SingPay : "quand un paiement se termine, envoie-moi la notification à cette adresse".

Remplacez `https://abc123.ngrok-free.app` par votre vraie URL ngrok, et exécutez :

```bash
curl -X PUT "https://gateway.singpay.ga/v1/portefeuille/api/VOTRE_WALLET_ID" \
  -H "x-client-id: VOTRE_CLIENT_ID" \
  -H "x-client-secret: VOTRE_CLIENT_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"callbackURL": "https://abc123.ngrok-free.app/webhook/singpay"}'
```

> **Cette commande ne modifie pas votre code.** Elle enregistre simplement votre URL dans le système SingPay. SingPay mémorise cette URL de son côté.
>
> **À refaire chaque fois que vous relancez ngrok** (car l'URL change).

### Étape 6 — Démarrer le backend

```bash
cd backend
mvn spring-boot:run
```

Démarre sur `http://localhost:8080`. Attendez de voir `Started SingPayDemoApplication` dans la console.

### Étape 7 — Démarrer le frontend

Dans un nouveau terminal :

```bash
cd frontend
npm install    # à faire uniquement la première fois
npm start
```

L'application est accessible sur `http://localhost:4200`.

Le fichier `proxy.conf.json` est déjà configuré — en développement, tous les appels vers `/api` et `/webhook` sont automatiquement redirigés vers le backend sur le port 8080. Vous n'avez rien à configurer.

---

## Webhook SingPay — fonctionnement détaillé

### Pourquoi un webhook ?

Le paiement Mobile Money est **asynchrone**. Voici ce qui se passe après que l'utilisateur clique "Payer" :

1. SingPay envoie un code USSD sur le téléphone du client
2. Le client ouvre l'interface de son opérateur (Airtel, Moov…)
3. Le client saisit son PIN pour confirmer
4. SingPay reçoit la confirmation de l'opérateur
5. **SingPay appelle votre webhook** pour vous informer du résultat

Ce processus peut prendre de quelques secondes à plusieurs minutes. Votre serveur doit donc être capable de recevoir cet appel entrant à tout moment, même après que l'utilisateur a quitté la page.

### Endpoint

```
POST /webhook/singpay
```

### Payload JSON reçu de SingPay

```json
{
  "transaction": {
    "reference": "CMD-550e8400-e29b",
    "status": "Terminate",
    "result": "Success",
    "amount": 5000,
    "airtel_money_id": "MP250101.1234.A00001",
    "client_msisdn": "24107XXXXXXX"
  }
}
```

| Champ | Type | Description |
|---|---|---|
| `reference` | String | Votre référence de commande (générée par Angular) |
| `status` | String | Étape du paiement : `Start` (initié), `Partenaire` (en cours), `Terminate` (terminé) |
| `result` | String | Résultat final : `Success`, `BalanceError`, `PasswordError`, `TimeOutError`, `Error` |
| `amount` | Integer | Montant en FCFA |
| `airtel_money_id` | String | ID de transaction Airtel Money (uniquement si succès) |
| `client_msisdn` | String | Numéro de téléphone du payeur |

### Logique de traitement

Le backend applique les règles suivantes dans l'ordre :

1. **Filtre sur le statut** — seuls les callbacks avec `status = "Terminate"` déclenchent une mise à jour. Les statuts intermédiaires (`Start`, `Partenaire`) sont ignorés silencieusement, avec une réponse HTTP 200.

2. **Vérification de la référence** — si la référence est inconnue en base, on logue un avertissement et on répond 200 (pour éviter les rejeux SingPay).

3. **Idempotence** — si la commande n'est plus `PENDING` (déjà traitée), le callback est ignoré. Cela protège contre les doubles appels en cas de rejeu.

4. **Vérification du montant** — si le montant reçu ne correspond pas au montant stocké en base, la commande passe en `FRAUD_SUSPECTED` et une alerte est loguée.

5. **Mise à jour du statut** :
   - `result = "Success"` → statut `PAID`, stockage de l'`airtel_money_id`
   - autre résultat → statut `FAILED_<result>` (ex: `FAILED_BalanceError`)

6. **Réponse HTTP 200 systématique** — même en cas d'erreur interne. Si le backend répond autre chose (404, 500…), SingPay considère que la notification n'est pas arrivée et la rejoue indéfiniment.

### Où ajouter votre logique métier post-paiement

Dans [backend/src/main/java/com/demo/singpay/controller/WebhookController.java](backend/src/main/java/com/demo/singpay/controller/WebhookController.java), repérez le commentaire `TODO` :

```java
// TODO: déclencher ici la logique métier post-paiement :
//   - envoyer un email de confirmation
//   - activer l'abonnement
//   - appeler un event bus / service métier
```

C'est le seul endroit à modifier pour ajouter des actions à la suite d'un paiement réussi.

### Tester le webhook manuellement

Pour simuler un appel SingPay sans passer par un vrai paiement :

```bash
curl -X POST http://localhost:8080/webhook/singpay \
  -H "Content-Type: application/json" \
  -d '{
    "transaction": {
      "reference": "VOTRE_REFERENCE_EN_BASE",
      "status": "Terminate",
      "result": "Success",
      "amount": 5000
    }
  }'
```

---

## API endpoints

| Méthode | Endpoint | Description |
|---|---|---|
| `POST` | `/api/payment/ussd` | Initie un paiement USSD Push (mode direct) |
| `POST` | `/api/payment/create-link` | Génère un lien de paiement SingPay (mode /ext) |
| `GET` | `/api/payment/order/{reference}` | Récupère la commande depuis la base locale (polling) |
| `GET` | `/api/payment/status/{reference}` | Vérifie le statut via l'API SingPay |
| `POST` | `/webhook/singpay` | Reçoit les notifications SingPay (usage interne) |

---

## Statuts de transaction

| Statut | Description |
|---|---|
| `PENDING` | Lien généré, paiement en attente de confirmation |
| `PAID` | Paiement confirmé avec succès |
| `FAILED_BalanceError` | Solde Mobile Money insuffisant |
| `FAILED_PasswordError` | PIN incorrect |
| `FAILED_TimeOutError` | Délai de confirmation dépassé |
| `FAILED_Error` | Erreur générique SingPay |
| `FRAUD_SUSPECTED` | Montant reçu dans le webhook différent du montant en base |

---

## Déploiement en production

Passer en production nécessite de mettre à jour deux choses (et uniquement deux) : votre fichier `.env` et la callbackURL enregistrée chez SingPay. **Le code source n'a pas à être modifié.**

### Ce qu'il faut absolument avoir avant de déployer

- Un **domaine avec HTTPS** — SingPay refuse les callbackURL en HTTP. Un certificat SSL est obligatoire.
- Un **serveur MySQL accessible** depuis votre backend
- Vos **credentials SingPay de production** (différents des credentials de test)
- Le **`SINGPAY_DISBURSEMENT_ID`** renseigné (obligatoire en production)

---

### Option A — VPS (Ubuntu/Debian + Nginx)

Vous louez un serveur Linux et gérez tout vous-même. C'est l'option la plus flexible.

**1. Construire le backend**

```bash
cd backend
mvn clean package -DskipTests
# Produit : backend/target/singpay-demo-1.0.0.jar
```

**2. Copier le JAR sur le serveur**

```bash
scp backend/target/singpay-demo-1.0.0.jar utilisateur@votre-serveur:/opt/singpay/
```

**3. Créer le fichier `.env` sur le serveur**

Connectez-vous en SSH sur votre serveur et créez `/opt/singpay/.env` :

```env
SINGPAY_CLIENT_ID=prod_client_id
SINGPAY_CLIENT_SECRET=prod_client_secret
SINGPAY_WALLET_ID=prod_wallet_id
SINGPAY_DISBURSEMENT_ID=prod_disbursement_id

# L'URL de votre domaine — c'est ici que vous changez l'URL de redirection post-paiement
APP_FRONTEND_URL=https://votre-domaine.com

DB_USER=singpay_user
DB_PASSWORD=mot_de_passe_fort
```

> **C'est ici que se fait le changement d'URL par rapport au développement local.** En remplaçant `http://localhost:4200` par `https://votre-domaine.com`, SingPay redirigera l'utilisateur vers votre vrai site après le paiement.

**4. Lancer le backend**

```bash
java -jar /opt/singpay/singpay-demo-1.0.0.jar --spring.profiles.active=prod
```

Le profil `prod` active `ddl-auto: validate` (plus sûr qu'`update` en production) et réduit le niveau de logs.

Pour un démarrage automatique, créez un service systemd :

```ini
# /etc/systemd/system/singpay.service
[Unit]
Description=SingPay Demo Backend
After=network.target mysql.service

[Service]
User=singpay
WorkingDirectory=/opt/singpay
ExecStart=java -jar singpay-demo-1.0.0.jar --spring.profiles.active=prod
EnvironmentFile=/opt/singpay/.env
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
systemctl enable singpay
systemctl start singpay
```

**5. Construire le frontend**

```bash
cd frontend
npm install
ng build --configuration production
# Produit : frontend/dist/frontend/browser/
```

**6. Configurer Nginx**

Nginx sert les fichiers Angular (frontend) et redirige les appels `/api` et `/webhook` vers le backend :

```nginx
server {
    listen 80;
    server_name votre-domaine.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name votre-domaine.com;

    ssl_certificate     /etc/letsencrypt/live/votre-domaine.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/votre-domaine.com/privkey.pem;

    # Frontend Angular
    root /var/www/singpay/;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # Proxy vers le backend Spring Boot (port 8080)
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /webhook/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

Certificat SSL gratuit via Let's Encrypt :

```bash
apt install certbot python3-certbot-nginx
certbot --nginx -d votre-domaine.com
```

**7. Mettre à jour la callbackURL chez SingPay**

Maintenant que votre serveur est en ligne, dites à SingPay d'envoyer les webhooks à votre vraie URL de production. Exécutez cette commande (depuis n'importe quel terminal avec `curl`) :

```bash
curl -X PUT "https://gateway.singpay.ga/v1/portefeuille/api/VOTRE_WALLET_ID" \
  -H "x-client-id: VOTRE_CLIENT_ID" \
  -H "x-client-secret: VOTRE_CLIENT_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"callbackURL": "https://votre-domaine.com/webhook/singpay"}'
```

> Cette commande remplace la callbackURL ngrok de développement par votre vraie URL de production. **C'est la seule chose à faire pour que les webhooks arrivent au bon endroit en production.** Le code, lui, ne change pas.

---

### Option B — Railway / Render / Fly.io (PaaS)

Ces plateformes hébergent votre application sans que vous ayez à gérer un serveur Linux. Idéal pour démarrer rapidement.

**Variables d'environnement à configurer dans l'interface de la plateforme :**

```
SINGPAY_CLIENT_ID         → votre client id de prod
SINGPAY_CLIENT_SECRET     → votre client secret de prod
SINGPAY_WALLET_ID         → votre wallet id de prod
SINGPAY_DISBURSEMENT_ID   → votre disbursement id de prod
APP_FRONTEND_URL          → https://votre-app.railway.app  (l'URL donnée par la plateforme)
DB_USER                   → utilisateur MySQL
DB_PASSWORD               → mot de passe MySQL
SPRING_PROFILES_ACTIVE    → prod
```

La commande de build backend : `mvn clean package -DskipTests`  
La commande de démarrage : `java -jar target/singpay-demo-1.0.0.jar`

Sur **Railway** : créez deux services (backend Java, frontend statique) + un addon MySQL.  
Sur **Render** : un Web Service pour le backend, un Static Site pour le frontend.

Une fois déployé, mettez à jour la callbackURL chez SingPay avec l'URL de production donnée par la plateforme (voir étape 7 de l'option A).

---

### Option C — Docker

```dockerfile
# backend/Dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/singpay-demo-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
docker build -t singpay-backend ./backend
docker run -p 8080:8080 \
  --env-file backend/.env \
  -e SPRING_PROFILES_ACTIVE=prod \
  singpay-backend
```

---

### Checklist avant de passer en production

- [ ] `APP_FRONTEND_URL` dans `.env` pointe vers le domaine HTTPS définitif
- [ ] `SINGPAY_DISBURSEMENT_ID` est renseigné
- [ ] Le profil Spring Boot `prod` est activé
- [ ] La callbackURL a été mise à jour chez SingPay avec l'URL de production (commande `curl`)
- [ ] Le certificat SSL est en place (HTTPS obligatoire pour le webhook)
- [ ] Les credentials SingPay utilisés sont bien ceux de **production**
- [ ] Le mot de passe MySQL en production est fort et différent du dev
- [ ] Tester le webhook manuellement avec `curl` après déploiement (voir section webhook)
- [ ] Vérifier les logs au premier démarrage pour confirmer que la base est accessible

---

## Structure du projet

```
singpay-demo/
├── backend/
│   ├── src/main/java/com/demo/singpay/
│   │   ├── controller/
│   │   │   ├── PaymentController.java      # POST ussd, POST create-link, GET order, GET status
│   │   │   └── WebhookController.java      # POST /webhook/singpay ← logique principale
│   │   ├── model/
│   │   │   ├── Order.java                  # Entité JPA (table orders)
│   │   │   ├── UssdPaymentRequest.java     # DTO requête USSD Push (opérateur, téléphone…)
│   │   │   ├── CreatePaymentRequest.java   # DTO requête lien externe /ext
│   │   │   ├── ExtLinkResponse.java        # DTO réponse SingPay /ext
│   │   │   ├── SingPayWebhookPayload.java  # DTO webhook (enveloppe)
│   │   │   ├── SingPayCallback.java        # DTO webhook (contenu transaction)
│   │   │   └── TransactionStatus.java      # DTO réponse GET /status
│   │   ├── repository/
│   │   │   └── OrderRepository.java        # Spring Data JPA
│   │   └── service/
│   │       └── SingPayService.java         # Appels HTTP vers SingPay
│   ├── src/main/resources/
│   │   └── application.yml                 # Config Spring Boot (dev + profil prod)
│   ├── .env                                # Variables locales (à créer, non commité)
│   └── pom.xml
│
├── frontend/
│   ├── src/app/
│   │   ├── catalogue/                      # Page de sélection de produits (route /)
│   │   ├── checkout/                       # Page de paiement (route /checkout?product=…&amount=…)
│   │   ├── ussd-checkout/                  # Composant USSD Push embarqué dans checkout
│   │   ├── services/
│   │   │   └── payment.service.ts          # Service Angular centralisé (initierUssd, getOrderStatus, createPaymentLink)
│   │   ├── payment-success/                # Page de succès post-redirection /ext
│   │   └── payment-error/                  # Page d'erreur post-redirection /ext
│   ├── src/assets/logos/                   # Logos opérateurs (Airtel Money, Moov Money, Maviance)
│   ├── proxy.conf.json                     # Proxy dev : /api et /webhook → :8080
│   └── package.json
│
├── .gitignore
└── README.md
```

---

## Sécurité

- Les credentials SingPay ne transitent **jamais** côté Angular — tout passe par le backend
- Chaque référence est **unique** (UUID) — le backend vérifie l'idempotence avant insertion
- Le montant du webhook est **vérifié** contre la base — toute incohérence déclenche `FRAUD_SUSPECTED`
- Le backend répond toujours **HTTP 200** au webhook pour éviter les rejeux SingPay
- Le fichier `.env` est dans `.gitignore` — ne jamais le commiter
