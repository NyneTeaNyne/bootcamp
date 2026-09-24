# Tests de performance — 23/09/2026 (module 15)

Scénario : « En tant qu'utilisateur, je liste toutes les commandes puis j'en ouvre une au hasard »,
de 1 à 20 itérations par seconde pendant 1 minute.

| Fichier | Rôle |
|---|---|
| `auth.js` | Récupère un token comme le fait Bruno (login → code d'autorisation + PKCE → token). Crée le compte `load-test-user` s'il n'existe pas. |
| `seed-data.js` | Crée des données : 20 clients, 50 articles, puis 500 commandes (`-e ORDERS=2000` pour plus). |
| `order-performance-plan.js` | Le plan de test du PDF, corrigé (voir plus bas). |
| `docker-compose.yaml` | Lance k6 dans Docker, sans l'installer. |
| `report-*.html` | Rapports de la mesure ci-dessous (500 commandes), à ouvrir dans un navigateur. |

## Lancer les tests

Prérequis : l'API tourne sur `http://localhost:8080` (avec `developer/docker-compose.yml` démarré :
PostgreSQL, Mailpit, Valkey).

**macOS** (k6 installé avec `brew install k6`) :

```bash
k6 run seed-data.js                     # une seule fois, pour avoir des données
k6 run -e K6_WEB_DASHBOARD=true -e K6_WEB_DASHBOARD_EXPORT=./report.html ./order-performance-plan.js
```

**Windows / sans installer k6** (Docker Desktop) :

```bash
SCRIPT=seed-data.js docker compose up   # une seule fois (PowerShell : $env:SCRIPT="seed-data.js"; docker compose up)
docker compose up                       # le plan de test ; rapport dans ./report.html
```

Pendant le test, le tableau de bord en direct est sur <http://localhost:5665>.

Variables utiles : `BASE_URL` (défaut `http://localhost:8080/api/v1`, ou `http://host.docker.internal:8080/api/v1`
via Docker), `CLIENT_ID`, `CLIENT_SECRET`, `LOAD_TEST_USERNAME`, `LOAD_TEST_PASSWORD`.

## Écarts avec le script du PDF

- **Authentification** : depuis le module 11 l'API exige un token ; `setup()` en récupère un une seule fois.
- **Bug corrigé** : le PDF tire un *index* au hasard (0, 1, 2…) et l'utilise comme *id* de commande, ce qui
  appelle `/orders/0` et des commandes inexistantes (404 comptés comme erreurs). Le script prend un vrai `id`.
- **1 → 5-20 VUs** : avec 1 seul utilisateur virtuel, dès qu'une requête est lente k6 saute des itérations en
  silence et le débit visé n'est jamais atteint.
- **Mesures par endpoint** : la liste et le détail sont mesurés séparément (`orders_list`, `order_detail`).
- **Docker** : `network_mode: host` ne marche pas avec Docker Desktop (Windows/macOS) → `host.docker.internal`.

## Résultats

Mesurés sur une copie isolée de l'API (PostgreSQL, Valkey et Mailpit dans des conteneurs temporaires),
JVM « chaude », temps moyens. Une seule exécution par cas : de l'ordre de ±10 % de bruit.

| Données | Endpoint | Sans cache | Avec cache Valkey | Gain |
|---|---|---|---|---|
| 500 commandes | `GET /orders` (liste) | 14,6 ms | 10,9 ms | −25 % |
| 500 commandes | `GET /orders/{id}` | 3,7 ms | 2,7 ms | −25 % |
| 2 500 commandes | `GET /orders` (liste) | 81,2 ms | 68,5 ms | −16 % |
| 2 500 commandes | `GET /orders/{id}` | 6,6 ms | 7,3 ms | ≈ (bruit) |

0 % d'erreurs dans tous les cas, et le seuil « moyenne < 100 ms » est respecté partout.

## Ce qu'on en retient

1. **Le cache aide, mais modérément.** PostgreSQL répond déjà vite à ces requêtes simples (index sur les clés
   primaires, commandes chargées en une requête grâce à l'`@EntityGraph`). Un cache ne gagne que le temps de
   la base de données.
2. **Le vrai goulot d'étranglement est `GET /orders` qui renvoie *toutes* les commandes.** Son coût grandit avec
   le nombre de commandes (×5 entre 500 et 2 500), cache ou pas : même servie par Valkey, la liste doit être relue,
   convertie et envoyée en entier (plusieurs centaines de Ko de JSON). À 10 000 commandes, on dépasserait le seuil.
   **La bonne correction est la pagination** (`GET /orders?page=0&size=20`), pas plus de cache.
3. Ce qu'apporte aussi le cache ici : il décharge la base de données quand beaucoup d'utilisateurs lisent les
   mêmes commandes, ce que ce scénario (1 seule instance, faible charge) ne met pas en évidence.
