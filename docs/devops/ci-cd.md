# CI/CD Pipeline

## Vue d'ensemble

```
push / pull_request → ci-cd.yml
                         ├── build.yml   (build, test, push images)
                         └── deploy.yml  (helm upgrade)
                               ├── deploy-production  (push on main)
                               └── deploy-preview     (pull_request)

pull_request closed  → cleanup.yml  (helm uninstall + delete namespace)
pull_request opened  → pr-title-check.yml (lint PR title)
```

## Workflows

### `ci-cd.yml` — Orchestrateur

Point d'entrée déclenché sur :
- `push` → `main`
- `pull_request` → `main`

Enchaîne `build` puis `deploy` (séquentiel, `needs: [build]`).

---

### `build.yml` — Build & Push des images

| Job | Runner | Ce qu'il fait |
|-----|--------|---------------|
| `build-shared-libs` | ubuntu-latest | Compile + teste les 10 libs partagées (`backend/shared/**`) ; upload les rapports Jacoco |
| `build-backend` (×5) | ubuntu-latest | Compile + teste chaque service Quarkus en parallèle (`fail-fast: false`) ; pousse l'image Docker sur GHCR avec les tags `<sha>`, `latest` (main seulement), `pr-N` (PR seulement) |
| `sonar-aggregate` | ubuntu-latest | Recompile le réacteur Maven (skip tests), fusionne tous les rapports Jacoco et envoie le scan SonarCloud agrégé |
| `build-frontend` | ubuntu-latest | `npm ci` → lint → tests + coverage → Sonar → `npm run build --mode preview\|production` → push image GHCR |

**Images publiées sur GHCR** (`ghcr.io/unige-pinfo6-2026/`) :
- `unige-events-{event,user,engagement,moderation,notification}-service` (×5)
- `unige-events-web`

---

### `deploy.yml` — Déploiement Helm

#### `deploy-production`

- **Condition** : `github.ref_name == 'main'`
- **Runner** : `self-hosted`
- **Environment GitHub** : `production`
- **Concurrence** : groupe `deploy-production`, pas d'annulation en cours (`cancel-in-progress: false`)

Étapes :
1. Checkout
2. Écriture du kubeconfig depuis le secret `KUBE_CONFIG`
3. Installation de Helm
4. `helm upgrade --install unige-events ./helm` avec :
   - `image.tag` et `image.web.tag` = `github.sha`
   - `secrets.dopplerToken`, `secrets.ghcrUsername`, `secrets.ghcrPassword` passés via `--set-string`
   - `--create-namespace --timeout 6m --wait`

#### `deploy-preview`

- **Condition** : `pull_request` et auteur non-bot (`github.event.pull_request.user.type != 'Bot'`)
- **Runner** : `ubuntu-latest`
- **Environment GitHub** : `preview`
- **Namespace** : `unige-events-pr-<N>`
- **URL** : `pr-<N>.pinfo6.p-info.net` (tunnel Cloudflare Quick mode)

Étapes supplémentaires par rapport à la production :
- Vérification que la PR est toujours ouverte (abort si closed/merged)
- Self-heal du release Helm bloqué en état `pending-*` (rollback ou uninstall)
- Overlay `helm/values-preview.yaml` pour ressources réduites
- Récupération de l'URL Cloudflare Tunnel depuis les logs du pod `cloudflared`
- Commentaire sticky sur la PR avec l'URL de prévisualisation

---

### `cleanup.yml` — Nettoyage preview

Déclenché sur `pull_request: closed` (merge ou fermeture).

```bash
helm uninstall unige-events-pr-<N> --namespace unige-events-pr-<N> --ignore-not-found
kubectl delete namespace unige-events-pr-<N> --ignore-not-found
```

Partage le groupe de concurrence `deploy-preview` pour éviter les races avec un deploy en cours.

---

### `pr-title-check.yml` — Validation du titre de PR

Vérifie que le titre de la PR respecte la convention Conventional Commits (`feat:`, `fix:`, `ci:`, etc.) avant toute revue.

---

## Secrets & Variables GitHub requis

### Environment `production`

| Nom | Type | Description |
|-----|------|-------------|
| `KUBE_CONFIG` | Secret | Kubeconfig base64 encodé du cluster de production |
| `DOPPLER_TOKEN` | Secret | Service token Doppler pour l'environnement `prd` |
| `GITHUB_TOKEN` | Auto | Fourni automatiquement par GitHub Actions |

### Environment `preview`

| Nom | Type | Description |
|-----|------|-------------|
| `KUBE_CONFIG` | Secret | Kubeconfig base64 encodé du cluster de preview |
| `DOPPLER_TOKEN` | Secret | Service token Doppler pour l'environnement `staging` |
| `GITHUB_TOKEN` | Auto | Fourni automatiquement par GitHub Actions |

### Repository (tous environments)

| Nom | Type | Description |
|-----|------|-------------|
| `SONAR_TOKEN` | Secret | Token SonarCloud pour l'analyse de code |

---

## Gestion des secrets Kubernetes

Les secrets Kubernetes sont entièrement gérés par le chart Helm (`helm/templates/secrets.yaml`). Aucune étape de création manuelle n'est nécessaire dans la pipeline.

| Secret K8s | Type | Créé par | Utilisé par |
|------------|------|----------|-------------|
| `doppler-token` | `Opaque` | Helm (`secrets.dopplerToken`) | Opérateur Doppler → synchronise `app-secrets` |
| `ghcr-secret` | `dockerconfigjson` | Helm (`secrets.ghcrUsername/Password`) | `imagePullSecrets` de tous les Deployments |
| `app-secrets` | `Opaque` | Opérateur Doppler | Tous les services (`envFrom`) |

L'opérateur Doppler doit être installé en amont sur le cluster (voir [`k8s.md`](k8s.md)).

---

## Diagramme de déploiement PR

```
push on PR branch
       │
       ▼
  build.yml ──► GHCR (image tag = <sha> + pr-N)
       │
       ▼
 deploy-preview
       │
       ├─ helm upgrade --install unige-events-pr-N
       │    namespace: unige-events-pr-N
       │    values: values.yaml + values-preview.yaml
       │
       └─ PR comment: 🚀 Preview URL (Cloudflare tunnel)

PR closed
       │
       ▼
  cleanup.yml
       │
       └─ helm uninstall + kubectl delete namespace
```
