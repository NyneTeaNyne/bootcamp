# Module 16 — Déployer sur Kubernetes en local (macOS)

Ce guide reprend le PDF « 16 - Deploy an API to K8S » étape par étape, avec les précisions qui manquent.
À la fin, tu auras un cluster Kubernetes local (k3d) qui se synchronise tout seul avec ton dépôt GitHub
grâce à FluxCD, et qui fait tourner un **nginx de test** (pas encore notre API).

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
> relance cette commande avant toute commande `kubectl` ou `flux`.

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

```
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
- `github-k3d-key` : la clé **privée** (reste sur ta machine et dans le cluster, ne la partage jamais) ;
- `github-k3d-key.pub` : la clé **publique** (à donner à GitHub).

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
- **comme dans le PDF** : GitHub → *Settings* → *SSH and GPG keys* → *New SSH key*, et colle-la ;
- **plus sûr (recommandé)** : dans ton dépôt → *Settings* → *Deploy keys* → *Add deploy key*, colle-la
  et **ne coche pas** *Allow write access*. La clé ne donne alors accès qu'à ce dépôt, en lecture seule.

---

## 6. Dire à Flux quoi surveiller et quoi déployer

Les fichiers sont déjà prêts dans le projet :

| Fichier | Contenu |
|---|---|
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

```
NAME                 URL                                   AGE   READY   STATUS
kustomization-repo   ssh://git@github.com/OWNER/REPO.git   6m    True    stored artifact for revision 'main@sha1:...'

NAME            AGE   READY   STATUS
my-app-deploy   6m    True    Applied revision: main@sha1:...
```

Puis les pods :

```bash
kubectl get pods -n my-app-dev
```

```
NAME                      READY   STATUS    RESTARTS   AGE
my-nginx-96b9d695-df7l6   1/1     Running   0          117s
my-nginx-96b9d695-rgbgk   1/1     Running   0          117s
```

### 6.4 (Bonus) Voir nginx dans ton navigateur

```bash
kubectl port-forward -n my-app-dev svc/nginx-service 8081:80
```

Ouvre <http://localhost:8081> : la page « Welcome to nginx! » doit s'afficher. `Ctrl+C` pour arrêter.

### 6.5 Voir le GitOps en action

Change `replicas: 2` en `replicas: 3` dans `developer/k8s/nginx-test/nginx.yaml`, commit, push,
attends quelques minutes (ou force avec `flux reconcile kustomization my-app-deploy -n my-app-dev --with-source`) :
un 3ᵉ pod apparaît sans que tu aies lancé de `kubectl apply`.

---

## 7. En cas de problème

| Symptôme | Piste |
|---|---|
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

## Et ensuite ?

Ce module ne déploie qu'un **nginx de test**. Pour déployer **notre API**, il faudra en plus
(sans doute l'objet des modules 12 à 14 qui manquent) :

- une **image Docker** de l'API, publiée dans un registre (par exemple `ghcr.io`) ;
- des fichiers Kubernetes pour l'API, PostgreSQL et Mailpit (Deployments, Services) ;
- des **Secrets** Kubernetes pour la base de données et OAuth2
  (`SECURITY_OAUTH2_CLIENT_ID`, `SECURITY_OAUTH2_CLIENT_SECRET`, …) au lieu des valeurs par défaut.
