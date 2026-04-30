# AGENTS.md — unige-events-api

## Rôle
Backend REST API de UNIGE Events. Java 21 · Quarkus 3.32.3 · Hibernate Panache · PostgreSQL 16 · Auth0/OIDC.

## Commandes
```bash
./mvnw quarkus:dev          # dev local avec hot reload + DevServices PostgreSQL auto
./mvnw verify               # build + tests complets (CI)
./mvnw test                 # tests uniquement (nécessite Docker-in-Docker pour DevServices)
./mvnw quarkus:dev -Ddebug  # debug port 5005
```
Les tests nécessitent Docker-in-Docker (configuré en CI) car Quarkus DevServices lance un PostgreSQL éphémère automatiquement.

## Architecture en couches
```
Resource (JAX-RS, /api)
    ↓ @Inject
Service (@ApplicationScoped, @Transactional)
    ↓ Panache Active Record
Entity (PanacheEntity + JPA)
    ↓ JDBC
PostgreSQL
```
Jamais de saut de couche. La Resource ne touche pas aux entités directement. La logique métier est dans le Service.

## Conventions critiques

### Nommage — camelCase partout
- Les champs des entités JPA sont en **camelCase** : `displayName`, `startDate`, `creatorId`
- Jackson sérialise automatiquement en **camelCase** dans le JSON — c'est la convention Quarkus/Jackson par défaut
- **Ne jamais introduire de snake_case** dans les champs ou les réponses JSON
- Le frontend consomme `user.displayName`, `event.startDate`, etc. — toute déviation casse l'intégration

### Booléens — pas de préfixe `is`
- **Ne jamais nommer un champ booléen avec le préfixe `is`** dans les entités JPA
- Utiliser `active` (pas `isActive`), `featured` (pas `isFeatured`), `admin` (pas `isAdmin`), `read` (pas `isRead`)
- Raison : Lombok génère `isIsActive()` — conflit garanti. Jackson sérialise `isActive` — incohérence JSON.
- Le champ JSON retourné sera donc `active`, `featured`, etc.

### Entités et persistance
- Entités : étendent `PanacheEntity` — pas de repository séparé
- Services : `@ApplicationScoped` + `@Transactional` sur toutes les mutations
- Resources : JAX-RS, préfixe `/api` (configuré dans `application.properties`)
- **Hibernate est en mode `validate`** en dev/prod : Flyway pilote le schéma, Hibernate vérifie seulement que les entités correspondent. En `%test`, Hibernate est en `drop-and-create` pour bootstrapper la base éphémère DevServices ; le V1 Flyway s'y applique en no-op.
- Soft-delete : champ `active` (boolean) sur Event, **jamais de DELETE physique**
- Attendance : contrainte unique `(userId, eventId)` — un seul statut par user/event
- Auth : `quarkus-oidc` mode `service` en prod, `quarkus.oidc.enabled=false` en `%test`

### Schéma de base de données — Flyway

Les changements de schéma passent par des migrations Flyway dans `backend/src/main/resources/db/migration/`.

- Nommage : `V<N>__<snake_case_description>.sql` (ex. `V2__add_event_archived_flag.sql`).
- Une migration committée est **immutable** : tout nouveau changement va dans un nouveau fichier `V<N+1>__…`.
- Stratégie d'adoption : `baseline-on-migrate=true` + `baseline-version=0` — les bases existantes (gérées historiquement par Hibernate `update`) adoptent Flyway à partir de V1 sans dump rétroactif. Pas de `quarkus.flyway.clean-*` (destructif).
- Si une base locale dérive (artefacts Hibernate `update` qui ne matchent plus le schéma post-migration), la solution est de la dropper et laisser Quarkus rebuilder.

### Enums
`EventCategory`, `AttendanceStatus`, `ReportStatus` — définis dans les entités, sérialisés en String dans le JSON.

Les champs `faculty` et `studyLevel` de `User` sont actuellement des `String` libres. Les valeurs attendues sont documentées dans `docs/data-model.md` et doivent correspondre aux enums définis dans le frontend (`Faculty`, `StudyLevel`). Ne pas introduire de valeurs hors de ce référentiel.

### Champ `admin` sur User
Le champ `admin` (boolean) est **planifié Sprint 6** et n'existe pas encore dans l'entité. Le frontend l'attend déjà dans le contrat API — l'ajouter à l'entité et à `UserProfileResponse` au Sprint 6 (sans préfixe `is`).

## Contrat API
`openapi/openapi.yaml` est la **source de vérité** (monorepo — fichier unique partagé entre frontend et backend). Avant d'implémenter un endpoint :
1. L'ajouter dans `openapi/openapi.yaml` (schémas en camelCase, booléens sans préfixe `is`)
2. Ensuite seulement coder Resource → Service → Entity → Test

## Comportement attendu des endpoints
- `GET /users/me` : **401** si token absent ou invalide — jamais de body partiel ou null
- `PUT /users/me` : retourner l'objet **User complet mis à jour** dans la réponse (pas `204`) pour que le frontend puisse se mettre à jour sans refetch
- Toujours retourner un body JSON cohérent — jamais `null` ou un objet avec des champs manquants

## Ce qu'il ne faut jamais faire
- Utiliser du snake_case dans les noms de champs ou les réponses JSON
- Préfixer les booléens avec `is` dans les entités JPA
- Modifier une migration Flyway déjà committée — créer un nouveau `V<N+1>__…` à la place
- Mettre de la logique métier dans une Resource
- Retourner `null` ou un body vide là où le frontend attend un objet
- Créer un endpoint sans l'avoir d'abord spécifié dans `openapi.yaml`

## Documentation du projet
- `docs/README.md` — index de tous les fichiers docs
- `docs/architecture.md` — architecture système et backend
- `docs/data-model.md` — entités, champs, conventions de nommage, enums, gestion du schéma
- `openapi/openapi.yaml` — contrat API complet (source de vérité, fichier unique du monorepo)
- `docs/dev-guide.md` — guide de démarrage et workflows
- `docs/sprint-context.md` — état d'avancement et backlog

## Maintenance de la documentation
**En tant qu'agent, tu dois mettre à jour la documentation dans les cas suivants :**

| Fichier modifié | Documentation à mettre à jour |
|---|---|
| Nouvelle entité JPA ou modification de champ | `docs/data-model.md` + schémas dans `openapi.yaml` |
| Nouveau endpoint ou modification de signature | `docs/openapi/openapi.yaml` EN PREMIER, puis le code |
| Modification d'un enum | `docs/data-model.md` + schémas dans `openapi.yaml` |
| Changement d'architecture ou de convention | `docs/architecture.md` + section dans `AGENTS.md` |
| Fin de sprint / tâche JIRA terminée | `docs/sprint-context.md` |

**Règle d'or : si tu touches au code, tu touches à la doc correspondante dans le même commit.**

## Workflow Git
- Branche : `feature/SCRUM-XX-description`
- 1 PR par tâche, review obligatoire avant merge sur main
- Qualité : SonarCloud seuil 80% couverture (JaCoCo)

### Conventions de PR
- **Titre** : format `<type>(<scope>): <description>`, validé par le workflow CI `.github/workflows/pr-title-check.yml` (la check doit passer avant merge).
  - Types autorisés : `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `perf`.
  - Pour `feat` / `refactor` / `perf` le scope est **obligatoirement** l'identifiant Jira en minuscules (`scrum-XXX`), ex. `feat(scrum-133): add /users/me/events endpoint`.
  - Pour les autres types le scope est libre ou omis, ex. `fix(backend): handle null author`, `chore(ci): add workflow`, `docs: update data model`.
  - Astuce : commiter avec un message conforme dès le premier commit de la branche → GitHub pré-remplit le titre de PR avec.
- **Description** : le template `.github/pull_request_template.md` est pré-rempli automatiquement par GitHub à l'ouverture d'une nouvelle PR. Sections obligatoires : Résumé, Changements, Tests, Test plan, Documentation. Sections optionnelles balisées par un commentaire HTML, à supprimer si non pertinentes : Why / Motivation, Dépendances / ordre de merge, Décisions techniques tranchées, Notes pour le reviewer.

# Requis analyse Sonar :
- Minimum 80% de coverage sur le nouveau code
- Maximum 3% de duplication sur le nouveau code
- Security Rating : A
- Security Review Rating : A
- Reliability Rating : A
- Maintainability Rating : A