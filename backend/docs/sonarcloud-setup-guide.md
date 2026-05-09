# SonarCloud setup guide — création des 15 projets post-consolidation

> **Pour qui** : Elie (admin/owner SonarCloud de l'organisation `unige-pinfo6-2026`).
>
> **Objectif** : créer manuellement les **15 SonarCloud projects** attendus par
> le workflow CI matrix (`.github/workflows/build.yml`, jobs `build-shared-libs`
> + `build-backend`). Sans ces projets, le step `SonarQube Scan` de chaque
> cellule échoue avec **« project not found »** — actuellement neutralisé par
> `continue-on-error: true`, mais le quality gate ne s'applique pas tant que
> les projets n'existent pas.
>
> **Durée estimée** : 5-10 minutes via UI (méthode 1) ou 1 minute via API
> (méthode 2 si tu as un token avec scope admin sur l'orga).
>
> **Cf. aussi** : [`devops-handoff.md`](devops-handoff.md) item 1.

---

## Pré-requis (à valider avant de commencer)

- [ ] Tu es connecté à https://sonarcloud.io avec ton compte GitHub.
- [ ] Tu es **owner** ou **admin** de l'organisation `unige-pinfo6-2026`
      → vérifie sur https://sonarcloud.io/organizations/unige-pinfo6-2026/members.
- [ ] Le secret GitHub `SONAR_TOKEN` existe déjà (sinon le frontend Sonar ne
      passerait pas en CI — il passe actuellement, donc le secret est bon).
- [ ] La branche `refactor(backend)--migrate-to-microservices` est pushée
      (PR #158) et tu as déjà mergé la finalisation OU tu es ok que les
      premiers scans Sonar visent la PR avant merge.

---

## Méthode 1 — UI manuelle (recommandée si tu veux vérifier visuellement chaque création)

### Étapes communes

1. Va sur https://sonarcloud.io/organizations/unige-pinfo6-2026/projects
2. Clique sur le bouton **« + »** en haut à droite → **« Analyze new project »**
3. Choisis **« Manually »** dans la sélection du mode (PAS « Bind to GitHub » —
   on n'a pas besoin du binding, le scan vient de notre CI Maven via SONAR_TOKEN
   et le projectKey override dans chaque POM enfant).
4. Pour chaque projet de la liste ci-dessous, remplis le formulaire :
   - **Project key** : la valeur exacte de la colonne « projectKey »
   - **Display name** : la valeur de la colonne « displayName »
   - **Visibility** : **Public** (cohérent avec ton projet `unige-events` existant)
5. Clique **« Set up »** → tu arrives sur la page « How do you want to analyze your
   repository? ». **Ignore** cette page (le CI fera le scan, le projet est juste
   provisionné côté SonarCloud à ce stade).
6. Reviens sur https://sonarcloud.io/organizations/unige-pinfo6-2026/projects
   et passe au projet suivant.

### Liste des 15 projets à créer

#### Services métiers (5)

| # | Project key | Display name |
|---|---|---|
| 1 | `unige-pinfo6-2026_unige-events-event-service` | `unige-events-event-service` |
| 2 | `unige-pinfo6-2026_unige-events-user-service` | `unige-events-user-service` |
| 3 | `unige-pinfo6-2026_unige-events-engagement-service` | `unige-events-engagement-service` |
| 4 | `unige-pinfo6-2026_unige-events-moderation-service` | `unige-events-moderation-service` |
| 5 | `unige-pinfo6-2026_unige-events-notification-service` | `unige-events-notification-service` |

#### Shared libs (10)

| # | Project key | Display name |
|---|---|---|
| 6 | `unige-pinfo6-2026_unige-events-shared-rate-limit` | `unige-events-shared-rate-limit` |
| 7 | `unige-pinfo6-2026_unige-events-shared-storage` | `unige-events-shared-storage` |
| 8 | `unige-pinfo6-2026_unige-events-shared-api-error` | `unige-events-shared-api-error` |
| 9 | `unige-pinfo6-2026_unige-events-shared-domain-enums` | `unige-events-shared-domain-enums` |
| 10 | `unige-pinfo6-2026_unige-events-shared-domain-dtos` | `unige-events-shared-domain-dtos` |
| 11 | `unige-pinfo6-2026_unige-events-shared-domain-projections` | `unige-events-shared-domain-projections` |
| 12 | `unige-pinfo6-2026_unige-events-shared-jaxrs` | `unige-events-shared-jaxrs` |
| 13 | `unige-pinfo6-2026_unige-events-shared-tracing` | `unige-events-shared-tracing` |
| 14 | `unige-pinfo6-2026_unige-events-shared-kafka-events` | `unige-events-shared-kafka-events` |
| 15 | `unige-pinfo6-2026_unige-events-shared-platform` | `unige-events-shared-platform` |

---

## Méthode 2 — Script bash via API SonarCloud (rapide, ~1 min)

**Pré-requis supplémentaire** : un token API SonarCloud avec scope « Execute
Analysis » + « Administer Quality Gates » sur l'organisation. Crée-le depuis
https://sonarcloud.io/account/security → « Generate Tokens », nomme-le
`unige-events-bulk-create`, copie la valeur (elle ne sera plus visible après).

### Script

```bash
# Remplace par ton token, NE PAS commit
SONAR_API_TOKEN="<colle-ton-token-ici>"
ORG="unige-pinfo6-2026"

PROJECTS=(
  # 5 services
  "unige-events-event-service"
  "unige-events-user-service"
  "unige-events-engagement-service"
  "unige-events-moderation-service"
  "unige-events-notification-service"
  # 10 shared libs
  "unige-events-shared-rate-limit"
  "unige-events-shared-storage"
  "unige-events-shared-api-error"
  "unige-events-shared-domain-enums"
  "unige-events-shared-domain-dtos"
  "unige-events-shared-domain-projections"
  "unige-events-shared-jaxrs"
  "unige-events-shared-tracing"
  "unige-events-shared-kafka-events"
  "unige-events-shared-platform"
)

for name in "${PROJECTS[@]}"; do
  echo "Creating ${name}..."
  curl -s -u "${SONAR_API_TOKEN}:" \
    -X POST "https://sonarcloud.io/api/projects/create" \
    -d "organization=${ORG}" \
    -d "project=${ORG}_${name}" \
    -d "name=${name}" \
    -d "visibility=public" \
    | jq -r '.project.key // .errors[0].msg // "??"'
done
```

**Output attendu** : 15 lignes affichant la `project.key` créée. Si tu vois
`Could not create Project, key already exists`, c'est que le projet existe
déjà — ignore et continue.

### Vérification post-script

```bash
curl -s -u "${SONAR_API_TOKEN}:" \
  "https://sonarcloud.io/api/projects/search?organization=${ORG}&ps=50" \
  | jq -r '.components[] | .key' \
  | grep "_unige-events-" \
  | sort
```

Doit lister les 15 projets + l'ancien `unige-pinfo6-2026_unige-events-backend`
(parent reactor) + `unige-pinfo6-2026_unige-events-frontend`. Plus
éventuellement les 11 anciens orphelins (cf. ci-dessous).

---

## Après création — vérifier que ça marche

1. Va sur https://github.com/unige-pinfo6-2026/unige-events/pull/158/checks
2. Re-run la dernière exécution du workflow `Build` :
   - Clique « Re-run all jobs » sur la dernière PR check.
   - Attends ~10-15 min.
3. Tu devrais voir les **15 cellules** de Sonar Scan **passer** au lieu de
   « project not found ». Le step affichera maintenant le score (initialement
   faible — ~22 % L sur les services métiers, normal tant que l'Étape 5 de la
   spec finalization n'est pas livrée).
4. Sur https://sonarcloud.io/organizations/unige-pinfo6-2026/projects, les 15
   projets apparaissent avec leur première analyse, leur quality gate (souvent
   ❌ rouge sur les services tant que la couverture n'est pas remontée — c'est
   attendu).

---

## (Optionnel) Archiver les 11 anciens projets orphelins

Post-consolidation 14→5, ces 11 projets ne reçoivent plus jamais de scan :

```
unige-pinfo6-2026_unige-events-share-service
unige-pinfo6-2026_unige-events-view-service
unige-pinfo6-2026_unige-events-favorite-service
unige-pinfo6-2026_unige-events-calendar-service
unige-pinfo6-2026_unige-events-follow-service
unige-pinfo6-2026_unige-events-comment-service
unige-pinfo6-2026_unige-events-co-organizer-service
unige-pinfo6-2026_unige-events-attendance-service
unige-pinfo6-2026_unige-events-report-service
unige-pinfo6-2026_unige-events-stats-service
unige-pinfo6-2026_unige-events-me-aggregator-service
```

Tu peux les laisser (aucun coût, juste visuellement encombré) ou les supprimer :

### Via UI

Pour chaque projet :
1. Aller sur sa page (`https://sonarcloud.io/project/overview?id=<key>`)
2. **Administration** → **Deletion**
3. Taper le nom du projet pour confirmer → **Delete**

### Via API

```bash
ORPHANS=(
  share-service view-service favorite-service calendar-service follow-service
  comment-service co-organizer-service attendance-service report-service
  stats-service me-aggregator-service
)
for name in "${ORPHANS[@]}"; do
  curl -s -u "${SONAR_API_TOKEN}:" \
    -X POST "https://sonarcloud.io/api/projects/delete" \
    -d "project=unige-pinfo6-2026_unige-events-${name}" \
    && echo "deleted: unige-events-${name}"
done
```

**Aucune urgence** sur cette suppression — c'est purement cosmétique. Tu peux
laisser les orphelins pour archive.

---

## (Optionnel) Retirer le `continue-on-error` post-création

Une fois les 15 projets créés et opérationnels, tu peux retirer
`continue-on-error: true` du step `SonarQube Scan` dans
[`.github/workflows/build.yml`](../../.github/workflows/build.yml) — le quality
gate appliquera ses règles strictement.

**Mais attention** : la coverage des services métiers est ~22 % tant que
l'Étape 5 de la spec finalization (port des 1818 tests legacy) n'est pas
livrée. Si tu enlèves le marker maintenant, le quality gate `Coverage on New
Code` ou similaire risque de bloquer chaque PR. Mieux vaut attendre que
l'Étape 5 soit faite.

---

## FAQ

**Q : Pourquoi 15 projets et pas un seul `unige-events-backend` global ?**
R : La spec originale (décision 25) + la complétion-spec (Étape 12.2) fixent un
projet Sonar par module Maven pour avoir des dashboards de couverture
indépendants par bounded context. C'est une exigence de la migration
microservices, pas un nice-to-have.

**Q : Si je crée les 15 et que je merge la PR, est-ce que main va aussi avoir
les 15 projets actifs ?**
R : Oui — chaque scan CI sur `main` (post-merge) écrira aussi dans ces 15
projets. La branche est juste l'origine du premier scan.

**Q : Les 15 projets vont consommer mon quota SonarCloud Free ?**
R : SonarCloud Free pour les projets publics est illimité en lignes scannées.
Pas de souci sur le quota.

**Q : Est-ce que je peux créer les projets via une seule action GitHub plutôt
que manuellement ?**
R : Oui techniquement — il existe une `SonarSource/sonarcloud-github-action`
qui peut créer un projet à la volée si l'API token a le bon scope. Mais on a
déjà le scan via Maven (`./mvnw sonar:sonar`) configuré, donc l'action GitHub
serait redondante. Le script bash de la méthode 2 suffit.

---

## Liens utiles

- Liste des projets de l'orga : https://sonarcloud.io/organizations/unige-pinfo6-2026/projects
- Membres / permissions : https://sonarcloud.io/organizations/unige-pinfo6-2026/members
- API tokens personnels : https://sonarcloud.io/account/security
- Documentation SonarCloud API projects : https://sonarcloud.io/web_api/api/projects
- DevOps handoff item 1 (référence) : [`devops-handoff.md`](devops-handoff.md#1-création-de-5-sonarcloud-projects-per-service--10-shared-libs)
