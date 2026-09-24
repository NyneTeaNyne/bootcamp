# Module 16 — Déployer sur Kubernetes en local (macOS)

Ce guide reprend le PDF « 16 - Deploy an API to K8S » étape par étape, avec les précisions qui manquent.
À la fin, tu auras un cluster Kubernetes local (k3d) qui se synchronise tout seul avec ton dépôt GitHub
grâce à FluxCD, et qui fait tourner ton application complète (API Spring Boot, PostgreSQL, Valkey, Mailpit).

> ⏱️ Compte environ 30 à 45 minutes la première fois.

## Ce que tu vas installer

| Outil | Rôle |
|---|---|
| **Colima** | Fait tourner Docker sur macOS (remplace Docker Desktop) |
| **k3d** | Crée un petit cluster Kubernetes (K3s) à l'intérieur de Docker |
| **kubectl** | La ligne de commande pour parler au cluster |
| **FluxCD** | Surveille ton dépôt GitHub et déploie automatiquement ce qu'il y trouve (GitOps) |

---

## 0. Prérequis

### 0.1 Homebrew

Si `brew --version` ne répond pas, installe-le depuis <https://brew.sh>.

### 0.2 Les outils

```bash
brew install colima docker kubectl k3d fluxcd/tap/flux

```

> Si tu as déjà Docker Desktop, tu peux l'utiliser à la place de Colima et sauter l'étape 1.
> Ne lance pas les deux en même temps.

### 0.3 Le projet doit être sur GitHub

FluxCD lit les fichiers à déployer **dans un dépôt GitHub**. Or le projet n'est pas encore un dépôt git.
Depuis la racine du projet :

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
# Crée d'abord un dépôt VIDE sur github.com, puis :
git remote add origin git@github.com:OWNER/REPO.git
git push -u origin main

```

Dans tout ce guide, remplace **`OWNER`** par ton nom d'utilisateur GitHub et **`REPO`** par le nom du dépôt.

---

## 1. Démarrer Colima (avec des DNS explicites)

```bash
colima start --dns 8.8.8.8 --dns 1.1.1.1 --cpu 2 --memory 4

```

Les options `--dns` évitent que le cluster ne sache pas résoudre les noms de domaine
(par exemple pour télécharger des images depuis `ghcr.io`).

Vérification : `docker ps` doit répondre sans erreur (une liste vide est normale).

---

## 2. Créer le cluster k3d

```bash
# Dossier où le cluster gardera ses données (volumes persistants)
mkdir -p "$HOME/k3d-data"

k3d cluster create my-local-dev \
  --kubeconfig-switch-context=false \
  --kubeconfig-update-default=false \
  -v "$HOME/k3d-data:/var/lib/rancher/k3s/storage@all"

# Configuration d'accès au cluster, dans un fichier SÉPARÉ
k3d kubeconfig get my-local-dev > ~/.kube/k3d-local.yaml

```

> Les deux options `--kubeconfig-...=false` évitent d'écraser ta configuration Kubernetes habituelle
> (celle de préprod / prod si tu en as une). Le cluster local a son propre fichier `~/.kube/k3d-local.yaml`.

### Utiliser ce cluster

```bash
export KUBECONFIG=~/.kube/k3d-local.yaml

kubectl config current-context   # doit afficher : k3d-my-local-dev
kubectl get nodes                # un nœud en statut Ready

```

> ⚠️ `export KUBECONFIG=...` ne vaut **que pour le terminal courant**. Dans un nouveau terminal,
> relance cette commande avant toute commande `kubectl` ou `flux`. *(Tu peux aussi fusionner la configuration avec `k3d kubeconfig merge my-local-dev --kubeconfig-switch-context` pour rendre cela permanent).*

---

## 3. Préparer l'environnement de dev

### 3.1 Créer le namespace

```bash
kubectl create namespace my-app-dev

```

### 3.2 Tester la résolution DNS depuis le cluster

```bash
docker exec -it k3d-my-local-dev-server-0 nslookup ghcr.io

```

Réponse attendue (les adresses peuvent varier) :

```text
Server:   172.21.0.1
Address:  172.21.0.1:53

Non-authoritative answer:
Name:     ghcr.io
Address:  140.82.121.34

```

Si ça échoue : `colima stop`, puis relance l'étape 1 avec les options `--dns`.

---

## 4. Installer FluxCD dans le cluster

Toujours avec `KUBECONFIG` exporté (étape 2) :

```bash
flux check --pre   # vérifie que le cluster est compatible
flux install       # installe les contrôleurs Flux dans le namespace flux-system

```

---

## 5. Donner à Flux l'accès à ton dépôt GitHub

### 5.1 Générer une clé SSH dédiée

> ⚠️ Génère-la **en dehors du projet**, pour ne jamais la commiter par erreur.

```bash
mkdir -p ~/.ssh/k3d
ssh-keygen -t ed25519 -f ~/.ssh/k3d/github-k3d-key -N ""

```

Cela crée deux fichiers :

* `github-k3d-key` : la clé **privée** (reste sur ta machine et dans le cluster, ne la partage jamais) ;
* `github-k3d-key.pub` : la clé **publique** (à donner à GitHub).

### 5.2 Créer le secret Flux à partir de cette clé

```bash
flux create secret git github-auth \
  --url=ssh://git@github.com/OWNER/REPO.git \
  --ssh-key-algorithm=ed25519 \
  --private-key-file="$HOME/.ssh/k3d/github-k3d-key" \
  --namespace=my-app-dev

```

### 5.3 Ajouter la clé publique sur GitHub

Copie la clé publique :

```bash
pbcopy < ~/.ssh/k3d/github-k3d-key.pub

```

Puis, au choix :

* **comme dans le PDF** : GitHub → *Settings* → *SSH and GPG keys* → *New SSH key*, et colle-la ;
* **plus sûr (recommandé)** : dans ton dépôt → *Settings* → *Deploy keys* → *Add deploy key*, colle-la
  et **ne coche pas** *Allow write access*. La clé ne donne alors accès qu'à ce dépôt, en lecture seule.

---

## 6. Dire à Flux quoi surveiller et quoi déployer (Test Nginx)

Les fichiers sont déjà prêts dans le projet :

| Fichier | Contenu |
| --- | --- |
| `developer/k8s/source.yaml` | Le dépôt GitHub à surveiller (`GitRepository`) et le dossier à déployer (`Kustomization`) |
| `developer/k8s/nginx-test/nginx.yaml` | Le déploiement de test : 2 pods nginx + un Service |

### 6.1 Adapter `source.yaml`

Ouvre `developer/k8s/source.yaml` et remplace `OWNER/REPO` par ton dépôt.
Le dossier déployé est déjà réglé sur `./developer/k8s/nginx-test`.

### 6.2 Pousser le déploiement de test sur GitHub

Flux déploie ce qui est **sur GitHub**, pas ce qui est sur ta machine :

```bash
git add developer/k8s
git commit -m "Add k8s test deployment for FluxCD"
git push

```

### 6.3 Appliquer la source et vérifier

```bash
kubectl apply -f developer/k8s/source.yaml

kubectl get gitrepository -n my-app-dev
kubectl get kustomization -n my-app-dev

```

Statut attendu (après une minute au plus) :

```text
NAME                 URL                                   AGE   READY   STATUS
kustomization-repo   ssh://git@github.com/OWNER/REPO.git   6m    True    stored artifact for revision 'main@sha1:...'

NAME            AGE   READY   STATUS
my-app-deploy   6m    True    Applied revision: main@sha1:...

```

Puis les pods :

```bash
kubectl get pods -n my-app-dev

```

```text
NAME                      READY   STATUS    RESTARTS   AGE
my-nginx-96b9d695-df7l6   1/1     Running   0          117s
my-nginx-96b9d695-rgbgk   1/1     Running   0          117s

```

---

## 7. En cas de problème

| Symptôme | Piste |
| --- | --- |
| `GitRepository` en `READY False` avec une erreur d'authentification | La clé publique n'est pas (ou pas encore) sur GitHub, ou `OWNER/REPO` est faux dans `source.yaml` et dans la commande de l'étape 5.2 |
| `Kustomization` en erreur `path not found` | Les fichiers n'ont pas été poussés (étape 6.2), ou le `path` ne correspond pas au dossier |
| `kubectl` parle au mauvais cluster | `KUBECONFIG` n'est pas exporté dans ce terminal (étape 2) |
| Pods en `ImagePullBackOff` | Problème DNS : refais le test de l'étape 3.2 |

Commande utile pour tout voir d'un coup : `flux get all -n my-app-dev`.

---

## 8. (Optionnel) Tout supprimer

```bash
k3d cluster delete my-local-dev

rm ~/.kube/k3d-local.yaml
rm -rf "$HOME/k3d-data"     # (le PDF écrit /User/USERNAME : c'est bien /Users, soit $HOME)

# Revenir à ta configuration Kubernetes habituelle
unset KUBECONFIG

```

Pense aussi à retirer la clé de GitHub (Deploy keys ou SSH keys) si tu n'utilises plus le cluster.

---

## 9. Remplacer Nginx par l'application complète

Maintenant que le pipeline GitOps fonctionne avec Nginx, nous allons déployer la véritable architecture : une API Spring Boot (Java 25 multi-modules), PostgreSQL, Valkey (Redis) et Mailpit.

### 9.1 Créer et publier l'image Docker de l'API

L'image doit être stockée sur le registre GitHub (`ghcr.io`) pour que le cluster puisse la télécharger.

1. **Générer un Token GitHub :** Va dans *Settings → Developer settings → Personal access tokens (classic)*. Crée un token avec les droits `write:packages` et copie-le.
2. **Connecter Docker :**
```bash
echo "TON_TOKEN" | docker login ghcr.io -u OWNER --password-stdin

```


3. **Construire et pousser l'image** (assure-toi que ton `Dockerfile` est bien adapté au projet Maven multi-modules) :
```bash
docker build -t ghcr.io/OWNER/REPO/api:v1 .
docker push ghcr.io/OWNER/REPO/api:v1

```



> ⚠️ **Important :** Sur GitHub, va dans la section **Packages** de ton profil ou de ton dépôt, clique sur l'image `api`, puis dans *Package settings*, change sa visibilité en **Public**. Sinon, Kubernetes affichera une erreur `ErrImagePull`.

### 9.2 Créer les manifestes Kubernetes

1. Crée un nouveau dossier pour l'application :
```bash
mkdir -p developer/k8s/app

```


2. Dans ce dossier, ajoute tes manifestes (ex: `services.yaml` pour Postgres/Valkey/Mailpit et `api.yaml` pour l'API Spring Boot).
   *L'API doit utiliser les variables d'environnement (`SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_HOST`, etc.) pour pointer vers les Services des bases de données.*

### 9.3 Dire à Flux de déployer la nouvelle architecture

1. Modifie le fichier `developer/k8s/source.yaml` pour pointer vers le nouveau dossier :
```yaml
  path: "./developer/k8s/app"  # <-- Remplace nginx-test par app

```


2. Supprime l'ancien dossier Nginx :
```bash
rm -rf developer/k8s/nginx-test

```


3. Pousse tes modifications sur GitHub :
```bash
git add developer/
git commit -m "Déploiement de l'API Spring Boot et suppression de Nginx"
git push

```



### 9.4 Vérifier le déploiement et tester en local

Force Flux à récupérer la dernière version sur GitHub :

```bash
flux reconcile kustomization my-app-deploy -n my-app-dev --with-source

```

Vérifie que tes 4 pods (API, Postgres, Valkey, Mailpit) démarrent correctement. Flux nettoiera automatiquement les anciens pods Nginx grâce au `prune: true` :

```bash
kubectl get pods -n my-app-dev

```

**Accéder à tes services en local depuis ton navigateur :**

Ouvre deux terminaux (n'oublie pas `export KUBECONFIG=~/.kube/k3d-local.yaml` dans chaque onglet si tu ne l'as pas fusionné) et lance :

```bash
# Terminal 1 : Accès à l'API Spring Boot
kubectl port-forward -n my-app-dev deployment/spring-api 8080:8080

# Terminal 2 : Accès à l'interface Mailpit
kubectl port-forward -n my-app-dev deployment/mailpit 8025:8025

```

🎉 Ton API est accessible sur **http://localhost:8080** et tes emails de test sur **http://localhost:8025** !