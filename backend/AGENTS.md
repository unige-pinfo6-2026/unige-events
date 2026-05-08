# AGENTS.md — unige-events-api

## Rôle
Backend REST API de UNIGE Events. Java 21 · Quarkus 3.32.3 · Hibernate Panache · PostgreSQL 16 · Auth0/OIDC.

## Layout Maven (post étape 1 migration microservices)

`backend/` est un projet **multi-module** depuis 2026-05-08. Le parent POM
agrégateur vit à `backend/pom.xml` (`packaging=pom`) et déclare 15 modules
sous `backend/services/` :

| Module | Packaging | Statut |
|---|---|---|
| `services/legacy-monolith/` | `quarkus` | Quarkus monolith (= ex-`backend/{src,pom.xml}`). C'est ici que vit aujourd'hui 100 % du code applicatif. Steps 2..14 de la migration (cf. [`specs_microservices_migration.md`](../specs_archives/specs_claude/specs_microservices_migration.md)) extrairont resources / services / entités vers les modules sœurs. |
| `services/<X>-service/` (×14) | `pom` | Placeholders shells pour user, event, attendance, favorite, view, co-organizer, comment, follow, report, stats, share, calendar, notification, me-aggregator. **Aucun ne porte de code aujourd'hui.** |

Tant que les extractions ne sont pas livrées, **toutes les conventions ci-dessous
s'appliquent à `services/legacy-monolith/`**. Quand un service sera extrait, ses
conventions migreront avec lui (camelCase, pas de préfixe `is`, Resource → Service
→ Entity, etc. — voir [`AGENTS.md` racine](../AGENTS.md)).

## Commandes
Toutes les commandes Maven s'exécutent depuis `backend/` (la racine du
multi-module). `./mvnw verify` traverse tous les modules, dont les 14
placeholders en no-op et `legacy-monolith` avec son build Quarkus complet.

```bash
./mvnw quarkus:dev          # dev local — depuis backend/services/legacy-monolith/ uniquement
./mvnw verify               # build + tests complets (CI) — depuis backend/
./mvnw test                 # tests uniquement (nécessite Docker-in-Docker pour DevServices)
./mvnw quarkus:dev -Ddebug  # debug port 5005 — depuis backend/services/legacy-monolith/
```

**Quarkus dev mode (`quarkus:dev`)** ne fonctionne pas depuis le parent multi-module
(c'est attendu : il s'exécute dans le contexte d'UN module Quarkus). Pour lancer
le monolithe en hot-reload local : `cd backend/services/legacy-monolith && ../../mvnw quarkus:dev`.
Une fois les services extraits, chaque module aura sa propre commande
`quarkus:dev` à lancer dans son dossier.

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

- Nommage : `V<N>__<snake_case_description>.sql` — description courte, 2-4 mots max, snake_case. Exemples corrects : `V3__create_attendances.sql`, `V7__add_event_archived.sql`. Exemples à éviter : `V3__create_attendances_table_with_status_and_constraints.sql`, `V7__reconcile_check_constraints_with_current_enum_values.sql`.
- Une migration committée est **immutable** : tout nouveau changement va dans un nouveau fichier `V<N+1>__…`.
- Stratégie d'adoption : `baseline-on-migrate=true` + `baseline-version=0` — les bases existantes (gérées historiquement par Hibernate `update`) adoptent Flyway à partir de V1 sans dump rétroactif. Pas de `quarkus.flyway.clean-*` (destructif).
- Si une base locale dérive (artefacts Hibernate `update` qui ne matchent plus le schéma post-migration), la solution est de la dropper et laisser Quarkus rebuilder.

### Enums
`EventCategory`, `AttendanceStatus`, `ReportStatus` — définis dans les entités, sérialisés en String dans le JSON.

Les champs `faculty` et `studyLevel` de `User` sont actuellement des `String` libres. Les valeurs attendues sont documentées dans `docs/data-model.md` et doivent correspondre aux enums définis dans le frontend (`Faculty`, `StudyLevel`). Ne pas introduire de valeurs hors de ce référentiel.

### Rôle ADMIN — claim Auth0, pas de champ DB
Le rôle ADMIN est porté **exclusivement** par la claim Auth0
(`https://quarkus-security.com/roles`, configurée via `quarkus.oidc.roles.role-claim-path`)
et consommé côté backend via :
- `@RolesAllowed("ADMIN")` (Quarkus Security) sur les classes/endpoints sensibles ;
- `identity.hasRole("ADMIN")` quand un check programmatique est nécessaire (ex. élévation
  conditionnelle d'un endpoint mixte créateur/admin).

**Pas de champ `admin: boolean` sur l'entité `User`** — décision SCRUM-94. Une seule
source de vérité (Auth0). Le frontend qui souhaite afficher un badge « Admin » lit la
claim depuis le token Auth0 (`auth.user['https://quarkus-security.com/roles']`), pas
depuis le payload profil.

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