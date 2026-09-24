# Order Manager — Bootcamp 2026

API REST de gestion de commandes (utilisateurs, catalogue d'articles, commandes), construite au fil des modules
du bootcamp : Spring Boot 4, PostgreSQL + Flyway, MapStruct, e-mails de confirmation, sécurité OAuth2/JWT,
documentation Swagger, cache Valkey, tests unitaires / d'intégration / de charge.

## Sommaire

1. [Prérequis](#1-prérequis)
2. [Démarrer l'environnement et l'API](#2-démarrer-lenvironnement-et-lapi)
3. [Adresses utiles](#3-adresses-utiles)
4. [S'authentifier](#4-sauthentifier)
5. [Tests automatisés (unitaires et d'intégration)](#5-tests-automatisés)
6. [Tests manuels avec Bruno](#6-tests-manuels-avec-bruno)
7. [Tests de charge (k6)](#7-tests-de-charge-k6)
8. [Kubernetes](#8-kubernetes)
9. [Organisation du code](#9-organisation-du-code)
10. [Configuration](#10-configuration)
11. [Dépannage](#11-dépannage)

---

## 1. Prérequis

| Outil | Version | Pourquoi |
|---|---|---|
| **JDK** | **25** | Le projet est compilé en Java 25 (`java -version` doit afficher 25) |
| **Docker** | Docker Desktop ou Colima | Base de données, e-mails, cache, et conteneurs des tests d'intégration |
| Maven | *rien à installer* | Le wrapper `./mvnw` (ou `mvnw.cmd` sous Windows) télécharge Maven 3.9.16 |
| [Bruno](https://www.usebruno.com/) | récent (2.x) | Tests manuels de l'API (optionnel) |
| [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) | — | Tests de charge (optionnel, utilisable via Docker) |

> Si plusieurs JDK sont installés, pointe `JAVA_HOME` vers le JDK 25 avant de lancer `./mvnw`.

---

## 2. Démarrer l'environnement et l'API

### 2.1 Les services (base de données, e-mails, cache)

```bash
cd developer
docker compose up -d
```

| Service | Port | Rôle |
|---|---|---|
| PostgreSQL | 5432 | Base `order_manager` (utilisateur `franck` / `root`) |
| Mailpit | 1025 (SMTP), 8025 (web) | Attrape tous les e-mails envoyés par l'API |
| Valkey | 6379 | Cache des commandes (compatible Redis) |

Le schéma de la base est créé et mis à jour automatiquement par **Flyway** au démarrage de l'API
(`apis/order-manager-api/src/main/resources/db/migration`).

### 2.2 L'API

Depuis la racine du projet :

```bash
./mvnw package -DskipTests
java -jar apis/order-manager-api/target/order-manager-api-0.0.1-SNAPSHOT.jar
```

Ou depuis l'IDE : lancer `BootcampApplication` (module `order-manager-api`).

L'API écoute sur **http://localhost:8080/api/v1** (toutes les routes sont préfixées par `/api/v1`).

---

## 3. Adresses utiles

| Quoi | Adresse | Accès |
|---|---|---|
| API | http://localhost:8080/api/v1 | Token requis (sauf inscription) |
| Swagger UI | **http://127.0.0.1:8080/api/v1/swagger-ui.html** | Libre ; bouton **Authorize** pour se connecter (voir §4) |
| Description OpenAPI | http://localhost:8080/api/v1/api-docs | Libre |
| Page de login | http://localhost:8080/api/v1/login | Libre |
| Mailpit (e-mails reçus) | http://localhost:8025 | Libre |

### Les endpoints

| Méthode | Route | Rôle | Réponses |
|---|---|---|---|
| POST | `/users` | Inscription (**sans token**) | 201, 400 |
| GET | `/users`, `/users/{id}` | Lister / lire | 200, 404 |
| PUT | `/users/{id}` | Renommer (l'e-mail suit) | 200, 400, 404 |
| DELETE | `/users/{id}` | Supprimer | 204, 404, 409 (a des commandes) |
| POST | `/items` | Ajouter au catalogue | 201, 400 |
| GET | `/items`, `/items/{id}` | Lister / lire | 200, 404 |
| PUT | `/items/{id}` | Modifier nom et prix | 200, 400, 404 |
| DELETE | `/items/{id}` | Supprimer | 204, 404 |
| POST | `/orders` | Commander + e-mail de confirmation | 201, 400, 500 (e-mail impossible : commande annulée) |
| GET | `/orders`, `/orders/{id}` | Lister / lire (mis en cache) | 200, 404 |
| DELETE | `/orders/{id}` | Supprimer | 204, 404 |
| GET | `/ping` | Vérifier que l'API répond (rôle USER ou ADMIN) | 200 |

Exemple de création de commande :

```json
{ "customer": { "id": 1 }, "items": [ { "id": 10, "quantity": 2 }, { "id": 20 } ] }
```

La quantité vaut 1 si elle est omise. Les erreurs sont renvoyées au format *Problem Details* :
`{ "status": 400, "title": "Bad Request", "detail": "Customer not found", ... }`.

---

## 4. S'authentifier

L'API est protégée par **OAuth2 / JWT** (module 11). Elle contient son propre serveur d'autorisation.

1. **Créer un compte** (route publique) :
   `POST /api/v1/users` avec `{ "username": "blue_hiker_26", "password": "Str0ngPassw0rd!" }`
   (mot de passe de 8 à 72 caractères, stocké haché en BCrypt, jamais renvoyé).
2. **Obtenir un token** avec le flux « Authorization Code » + PKCE, par exemple dans Bruno :

| Champ | Valeur |
|---|---|
| Authorization URL | `http://localhost:8080/api/v1/oauth2/authorize` |
| Access Token URL | `http://localhost:8080/api/v1/oauth2/token` |
| Callback URL | `http://127.0.0.1:8080/api/v1/authorized` |
| Client ID / Secret | `localClientId` / `localClientSecret` (envoyés en Basic Auth) |
| Scope | `read` |
| PKCE | activé (S256) — **obligatoire** |

   Une page de login s'ouvre : connecte-toi avec le compte créé à l'étape 1.
3. **Appeler l'API** avec l'en-tête `Authorization: Bearer <token>`.

### Se connecter depuis Swagger UI

1. Ouvrir Swagger via **http://127.0.0.1:8080/api/v1/swagger-ui.html** (avec `127.0.0.1`, **pas** `localhost` :
   le serveur d'autorisation refuse les adresses de retour en `localhost`, règle de sécurité d'OAuth2).
2. **Authorize** → section **oauth2** → cocher `read` → **Authorize** : la page de login s'ouvre dans une fenêtre.
3. Se connecter : la fenêtre se ferme, Swagger a le token, **Try it out** fonctionne sur tous les endpoints.

Swagger est enregistré comme client OAuth2 **public** (`swagger-ui`, sans secret puisqu'il tourne dans le
navigateur, protégé par PKCE). Section **bearerAuth** du même bouton : pour coller un token obtenu ailleurs.

Bon à savoir :
- le token est valable **30 minutes** ; il devient invalide si l'API **redémarre** (la clé de signature est
  régénérée à chaque démarrage) : il suffit d'en redemander un ;
- tout utilisateur a le rôle `ADMIN` (comme dans le module 11 : pas encore de gestion des rôles) ;
- la Callback URL n'existe pas dans l'API : c'est normal, Bruno intercepte la redirection.

---

## 5. Tests automatisés

```bash
./mvnw verify
```

Lance tous les tests des deux modules, puis vérifie la **couverture de code** (build en échec sous **80 %**
de lignes couvertes). Rapport : `apis/order-manager-api/target/site/jacoco/index.html`.

| Commande | Effet |
|---|---|
| `./mvnw verify` | Tous les tests + couverture |
| `./mvnw test -pl common/order-manager-common` | Tests du module `common` seulement |
| `./mvnw test -pl apis/order-manager-api -am -Dtest=OrderUnitaryTest -Dsurefire.failIfNoSpecifiedTests=false` | Une seule classe de test |

**Docker doit être démarré** pour les tests d'intégration : ils lancent leurs propres conteneurs jetables
(PostgreSQL 17, Mailpit, Valkey) avec **Testcontainers**, sans toucher à ta base de dev.
Sans Docker, ils sont **ignorés** (pas en échec) — la couverture peut alors passer sous 80 %.

### Où sont les tests

```
apis/order-manager-api/src/test/java/.../api/tests/
├── unit/                    Tests isolés, sans Spring ni base (Mockito)
│   ├── controllers/         Contrôleurs + services avec un faux repository (MockMvc standalone)
│   ├── mappers/             Conversions MapStruct
│   ├── models/              Règles métier : validate(), validateReference()
│   └── services/            Services avec des mocks (@InjectMocks)
└── integration/             Application complète + vrais conteneurs (Testcontainers)
    ├── config/              Initializers PostgreSQL / Mailpit / Valkey, JWT de test
    └── controllers/         Items, commandes, sécurité (flux OAuth2 complet), cache, Swagger…

common/order-manager-common/src/test/java/.../common/tests/unit/   Tests du module common
```

Conventions : noms de tests sous forme de phrases (`should_return_404_when_order_does_not_exist`),
corps JSON construits avec `ObjectMapper` + les DTO (jamais écrits à la main), `@Transactional` pour annuler
les écritures en base à la fin de chaque test d'intégration.

---

## 6. Tests manuels avec Bruno

La collection est dans **`developer/bruno/`** : 28 requêtes et 52 vérifications automatiques.

1. Bruno → **Open Collection** → choisir le dossier `developer/bruno`.
2. En haut à droite, sélectionner l'environnement **local**.
3. Lancer **0 - Setup / Sign up (login account)** : crée le compte `blue_hiker_26` (400 s'il existe déjà : normal).
4. Paramètres de la collection → onglet **Auth** → **Get Access Token** → se connecter avec
   `blue_hiker_26` / `Str0ngPassw0rd!`. Toutes les requêtes héritent du token.
5. Lancer les dossiers dans l'ordre, ou toute la collection avec le **Runner** (clic droit → Run).

| Dossier | Contenu |
|---|---|
| 0 - Setup | Inscription, ping, description OpenAPI |
| 1 - Users | Création d'un client, lecture, renommage, erreurs (doublon, mot de passe trop court, 404) |
| 2 - Items | Création, lecture, changement de prix, erreurs (doublon, prix à 0) |
| 3 - Orders | Commande (quantité 2, total vérifié), lecture, erreurs (client inconnu, quantité 0, 404) |
| 4 - Security | Sans token et avec un faux token → 401 |
| 5 - Cleanup | Supprime ce qui a été créé |

Les identifiants créés (client, article, commande) passent d'une requête à l'autre via des variables.
Après **3 - Orders / Create order**, l'e-mail de confirmation est visible dans Mailpit (http://localhost:8025).
Les identifiants de connexion et l'adresse de l'API se changent dans `developer/bruno/environments/local.bru`.

---

## 7. Tests de charge (k6)

Dossier : `apis/order-manager-api/src/test/load-test/release-load-test-2026-09-23/` — voir son
[README](apis/order-manager-api/src/test/load-test/release-load-test-2026-09-23/README.md) pour le détail
et les résultats mesurés (avec / sans cache).

```bash
cd apis/order-manager-api/src/test/load-test/release-load-test-2026-09-23
# Avec k6 installé
k6 run seed-data.js                  # une fois : crée 20 clients, 50 articles, 500 commandes
k6 run -e K6_WEB_DASHBOARD=true -e K6_WEB_DASHBOARD_EXPORT=./report.html ./order-performance-plan.js
# Ou sans l'installer (Docker)
SCRIPT=seed-data.js docker compose up
docker compose up
```

Les scripts récupèrent eux-mêmes un token (compte `load-test-user`, créé automatiquement).

---

## 8. Kubernetes

Guide pas à pas (macOS) pour créer un cluster local k3d synchronisé avec GitHub par FluxCD (module 16) :
[`developer/INSTALL-K8S-MACOS.md`](developer/INSTALL-K8S-MACOS.md). Les fichiers YAML sont dans `developer/k8s/`.

---

## 9. Organisation du code

```
bootcamp/
├── pom.xml                              Parent Maven (Spring Boot 4.1, Java 25)
├── common/order-manager-common/         Briques techniques partagées, sans code métier
│   └── .../common/
│       ├── autoconfigure/               Auto-configurations Spring Boot (actives selon ce que le module utilise)
│       ├── exception/                   BusinessException et ses sous-types, NotFoundException
│       ├── mail/                        MailUseCase / MailService
│       ├── security/                    Erreurs 401/403, convention du claim « roles » des JWT
│       └── web/                         ApiExceptionHandler (format Problem Details)
├── apis/order-manager-api/              L'application
│   └── .../api/
│       ├── config/                      Sécurité (OAuth2, filter chains), cache
│       ├── controllers/  (+ dtos/)      Couche HTTP, annotations Swagger
│       ├── entities/     (+ repositories/)  JPA
│       ├── mappers/                     MapStruct : DTO ↔ modèle ↔ entité
│       ├── models/                      Modèle métier et ses règles (validate)
│       ├── services/                    Implémentations des cas d'usage
│       └── usecases/                    Interfaces des cas d'usage (ce que l'API sait faire)
├── developer/
│   ├── docker-compose.yml               PostgreSQL, Mailpit, Valkey
│   ├── bruno/                           Collection Bruno
│   ├── k8s/                             Fichiers Kubernetes (module 16)
│   └── INSTALL-K8S-MACOS.md
└── PDF/                                 Énoncés des modules
```

### Les erreurs métier

| Exception (`common`) | Quand | HTTP |
|---|---|---|
| `InvalidDataException` | Données invalides (`validate()`) | 400 |
| `AlreadyExistsException` | Nom d'utilisateur / de produit déjà pris | 400 |
| `ReferenceNotFoundException` | Commande qui cite un client / article inconnu | 400 |
| `NotFoundException` | Ressource de l'URL introuvable | 404 |

Toutes les autres erreurs donnent un message générique, sans détail technique (pas de trace ni de SQL).

---

## 10. Configuration

Valeurs par défaut dans `apis/order-manager-api/src/main/resources/application.properties`, prévues pour le
développement. En production, les surcharger par des **variables d'environnement** :

| Variable | Défaut | Rôle |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/order_manager` | Base de données |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `franck` / `root` | Identifiants de la base |
| `SECURITY_OAUTH2_CLIENT_ID` | `localClientId` | Client OAuth2 autorisé à demander des tokens |
| `SECURITY_OAUTH2_CLIENT_SECRET` | `localClientSecret` | Son secret |
| `SECURITY_OAUTH2_REDIRECT_URI` | `http://127.0.0.1:8080/api/v1/authorized` | Adresse de retour du flux OAuth2 |
| `SECURITY_OAUTH2_SWAGGER_CLIENT_ID` | `swagger-ui` | Client OAuth2 public de Swagger UI |
| `SECURITY_OAUTH2_SWAGGER_REDIRECT_URI` | `http://127.0.0.1:8080/api/v1/swagger-ui/oauth2-redirect.html` | Adresse de retour de Swagger UI |
| `OPENAPI_OAUTH2_BASE_URL` | `http://127.0.0.1:8080/api/v1` | Adresse du serveur d'autorisation annoncée dans Swagger |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Valkey (cache) |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | `localhost` / `1025` | Serveur SMTP |
| `SPRING_CACHE_TYPE` | `redis` | `none` pour désactiver le cache |

Autres réglages : durée des tokens (`spring.security.oauth2.access-token-validity-minutes`, 30 min),
durée de vie du cache (`bootcamp.cache.time-to-live`, 10 min), expéditeur des e-mails
(`bootcamp.mail.sender`, `no-reply@decathlon.com`).

---

## 11. Dépannage

| Problème | Cause / solution |
|---|---|
| Le build Maven échoue avec des erreurs incompréhensibles (« cannot access… », types introuvables) alors que le code est bon | Un IDE compile en même temps dans `target/`. VS Code avec un JDK autre que 25 produit des classes cassées : configure-le en JDK 25 (`java.jdt.ls.java.home`) ou mets `"java.autobuild.enabled": false`, puis relance `./mvnw clean verify` |
| Les tests d'intégration sont « skipped » | Testcontainers ne trouve pas Docker : le message `Docker is not reachable by Testcontainers` dans la sortie des tests donne la raison. Docker démarré mais tests quand même ignorés (fréquent sur macOS) : voir [Docker sur macOS](#docker-sur-macos-colima-docker-desktop) |
| `ClassNotFoundException` / `NoClassDefFoundError` sur une classe de `...ordermanager.common` en lançant seulement l'API (`-pl apis/order-manager-api`) | Maven prend une vieille version de `common` dans son cache local : ajoute `-am`, ou lance `./mvnw install -DskipTests` une fois |
| Impossible de se connecter avec un ancien utilisateur | Les comptes créés avant le module 11 ont le mot de passe `no_password` (non valide) : crée un nouveau compte |
| Swagger : « Authorize » échoue ou la fenêtre de login affiche une erreur 400 | Swagger est ouvert via `localhost` : utilise http://127.0.0.1:8080/api/v1/swagger-ui.html |
| 401 alors que j'avais un token | Token expiré (30 min) ou API redémarrée : redemande un token |
| 500 à la création d'une commande | L'e-mail de confirmation n'a pas pu partir (Mailpit arrêté ?) : la commande est annulée volontairement |
| L'API démarre mais les données semblent anciennes | Cache Valkey : il se vide tout seul à chaque modification ; sinon `docker exec order_manager_valkey valkey-cli FLUSHALL` |
| Port déjà utilisé (5432, 6379, 8080…) | Un autre service l'occupe : arrête-le, ou change le port dans `developer/docker-compose.yml` |
| `./mvnw` utilise le mauvais Java | Pointe `JAVA_HOME` vers le JDK 25 |

### Docker sur macOS (Colima, Docker Desktop)

`docker ps` peut marcher alors que Testcontainers ne trouve pas Docker : la commande `docker` utilise un
« contexte » (`docker context ls`), alors que Testcontainers cherche `/var/run/docker.sock` ou la variable
`DOCKER_HOST`.

**1. Diagnostiquer**

```bash
docker context ls                  # le contexte actif (*) indique où est vraiment Docker
ls -la /var/run/docker.sock        # existe-t-il ?
echo $DOCKER_HOST
cat ~/.testcontainers.properties   # Testcontainers y mémorise la dernière méthode qui a marché
```

**2. Corriger selon ton installation**

- **Colima** (Docker est dans `~/.colima/default/docker.sock`). Dans `~/.zshrc` :

  ```bash
  export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  ```

  La 2ᵉ ligne est indispensable : elle indique le chemin du socket *à l'intérieur* de la machine virtuelle
  Colima, où Testcontainers monte son conteneur de nettoyage (Ryuk).
  Puis ouvrir un nouveau terminal et relancer `./mvnw verify`.

- **Docker Desktop** : *Settings* → *Advanced* → cocher **« Allow the default Docker socket to be used »**
  (demande le mot de passe administrateur), puis redémarrer Docker Desktop.

**3. Depuis l'IDE (IntelliJ, VS Code)**, les variables de `~/.zshrc` ne sont souvent pas vues. Mettre la même
configuration dans `~/.testcontainers.properties` (remplacer `TON_NOM` par ton nom d'utilisateur macOS) :

```properties
docker.host=unix:///Users/TON_NOM/.colima/default/docker.sock
```

et ajouter `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` dans les variables d'environnement de
la configuration de lancement des tests de l'IDE.

Si `~/.testcontainers.properties` contient une ligne `docker.client.strategy=...` qui ne correspond plus à ton
installation (par exemple après être passé de Docker Desktop à Colima), supprime cette ligne.

