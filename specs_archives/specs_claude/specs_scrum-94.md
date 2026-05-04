# Specs SCRUM-94 — Entité `Report` enrichie + endpoints signalement + routes admin modération

> **Branche :** `feature/s6-report-moderation` (nom historique du backlog ; conservé pour la traçabilité — cf. décision 1)
> **Base :** `origin/main` (à jour avec le merge de SCRUM-136 — `event_co_organizers` + V8 inclus)
> **Sprint :** S7 (24 avril – 8 mai 2026) — assigné Elie, statut Jira « En cours »
> **Ticket Jira :** [SCRUM-94](https://pinfo-groupe6.atlassian.net/browse/SCRUM-94) (5 SP)
> **Story Points :** 5
> **Épic :** SCRUM-15 (Modération & sécurité de la plateforme) · **Story :** [SCRUM-72](https://pinfo-groupe6.atlassian.net/browse/SCRUM-72) (US-T4) + [SCRUM-65](https://pinfo-groupe6.atlassian.net/browse/SCRUM-65) (US-15)
> **Frontend lié :** [SCRUM-96](https://pinfo-groupe6.atlassian.net/browse/SCRUM-96) (ReportModal) + [SCRUM-97](https://pinfo-groupe6.atlassian.net/browse/SCRUM-97) (Dashboard admin) — dépendent de cette PR
> **Règle d'or `openapi-first` :** **APPLICABLE — 3 endpoints à finaliser + 4 schémas à reprendre + 1 enum à ajouter.** Modifier [`openapi/openapi.yaml`](openapi/openapi.yaml) AVANT toute ligne de code Java. Voir [`backend/AGENTS.md`](backend/AGENTS.md#L62-L65).

---

## Contexte

### Le besoin produit (US-15 + US-T4)

> *« En tant qu'utilisateur, je veux signaler un événement contenant des informations inappropriées ou fausses afin que la plateforme reste sûre et digne de confiance. »* (US-15)
> *« En tant qu'admin, je veux un flux de modération simple (liste des signalements, actions dismisser/supprimer) afin d'assurer la qualité des événements publiés. »* (US-T4)

L'épic SCRUM-15 vise à fermer la boucle « contenu douteux détecté → admin agit ». Aujourd'hui, le job [`ModerationCleanupService`](backend/src/main/java/ch/unige/events/service/ModerationCleanupService.java) (livré en SCRUM-103) consomme déjà la table `reports` pour auto-masquer les events qui dépassent le seuil de 3 signalements PENDING — mais **rien dans la plateforme ne permet à un utilisateur de produire un signalement**, et **aucun admin n'a de surface API pour les traiter individuellement**. La table existe, le job tourne, le pipeline d'entrée et le pipeline de sortie sont vides.

### Ce qui manque aujourd'hui

| Pièce manquante | Conséquence |
|---|---|
| `Report.reason` est un `TEXT` libre | Pas de catégorisation côté UI ; pas d'agrégation possible (ex. « 80 % des signalements concernent du SPAM ») |
| Pas de champ `description` distinct du motif catégoriel | Aucun moyen de capturer le contexte libre saisi par l'utilisateur en plus de la catégorie |
| Pas de traçabilité de la modération (`reviewedAt`, `reviewedBy`) | Impossible d'auditer qui a traité quel signalement et quand |
| Pas de `ReportService` ni de `ReportResource` | L'utilisateur ne peut pas appeler `POST /events/{id}/report` |
| Pas d'`AdminReportResource` ni de `@RolesAllowed("ADMIN")` sur quoi que ce soit | L'admin ne peut ni lister les PENDING ni les transitionner |
| Pas de `ReportDTO` ni de `CreateReportRequest`/`HandleReportRequest` | Aucune projection sortante stable ni validation entrante |
| OpenAPI partiel (schémas `Report`/`ReportRequest` ébauchés, paths marqués `TODO Sprint 6`) | Le frontend (SCRUM-96 / SCRUM-97) n'a pas de contrat exploitable |

SCRUM-94 livre **le socle backend complet** du flux modération : enrichissement de l'entité existante, migration Flyway V9, DTOs, Service, deux Resources (utilisateur + admin), OpenAPI, doc, tests. SCRUM-96 et SCRUM-97 livreront l'UI (modale de signalement, dashboard admin).

### Ce qui existe déjà à RÉUTILISER tel quel (livré par SCRUM-103, ne pas recréer)

| Élément | Fichier / ligne | Rôle |
|---|---|---|
| Entité `Report` (PanacheEntity) | [`backend/src/main/java/ch/unige/events/entity/Report.java`](backend/src/main/java/ch/unige/events/entity/Report.java) | Champs actuels : `event`, `reporter`, `status`, `reason` (TEXT libre), `createdAt`. À **étendre**, pas à recréer. |
| Enum `ReportStatus` | [`backend/src/main/java/ch/unige/events/entity/ReportStatus.java`](backend/src/main/java/ch/unige/events/entity/ReportStatus.java) | `PENDING / REVIEWED / DISMISSED` — déjà défini, à utiliser tel quel. |
| Migration `V6__create_reports.sql` | [`backend/src/main/resources/db/migration/V6__create_reports.sql`](backend/src/main/resources/db/migration/V6__create_reports.sql) | **Immutable** (cf. [`AGENTS.md`](backend/AGENTS.md#L54-L57)). Toute évolution passe par V9. |
| `reports_status_check` posée par V7 | [`V7__reconcile_check_constraints.sql:18-20`](backend/src/main/resources/db/migration/V7__reconcile_check_constraints.sql#L18-L20) | CHECK pour les valeurs `ReportStatus`. **À NE PAS toucher** dans V9 — la modif d'enum future passera par V10. |
| Contrainte unique `uk_report_reporter_event` | [`V6__create_reports.sql:11`](backend/src/main/resources/db/migration/V6__create_reports.sql#L11) | Bloque déjà le double signalement par couple `(reporter_id, event_id)` → renvoie `409 already_reported` directement. **Aucune nouvelle contrainte à ajouter pour ce cas.** |
| `ModerationCleanupService` consommateur de la table | [`ModerationCleanupService.java:46-51`](backend/src/main/java/ch/unige/events/service/ModerationCleanupService.java#L46-L51) | Lit uniquement `r.event` et `r.status`. **Insensible** au renommage `reason → description` et à l'ajout de la nouvelle colonne `reason` typée. À ne pas toucher. |
| `ModerationCleanupJob` (cron 0 0 3 * * ?) | [`ModerationCleanupJob.java`](backend/src/main/java/ch/unige/events/scheduler/ModerationCleanupJob.java) | Idem — non concerné par la modif. |
| Tests existants `ReportTest`, `ModerationCleanupServiceTest`, `ModerationCleanupCoverageTest` | sous `backend/src/test/java/ch/unige/events/` | À **mettre à jour** (cf. étape 7.1 — `report.reason = "Inappropriate content"` devient `report.description = "..."`). |

### Pourquoi maintenant

- Sprint 7 — sprint courant, tâche déjà ouverte « En cours » sur le board Jira (assignée à Elie).
- **Aucune dépendance bloquante** : ni SCRUM-95 (featured), ni SCRUM-98 (expiration), ni SCRUM-99 (notifications) ne sont prérequis. SCRUM-136 (co-organisateurs) vient d'être mergé sur main — la base est propre et inclut V8.
- Débloque immédiatement SCRUM-96 (front modale signalement) et SCRUM-97 (dashboard admin), qui sont marqués « À faire » sur le board.
- La migration vers Flyway est terminée (cf. commits `01da2e6`, `83bc086`) — Hibernate est désormais en `validate`. **Le ticket Jira SCRUM-94 indique encore « Schéma géré par Hibernate (mode update) — aucune migration nécessaire » : cette mention est OBSOLÈTE et est explicitement remplacée par la décision 3 de cette spec.**

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### 1. Branche `feature/s6-report-moderation` — pas `feature/s7-...`

**Décision.** La branche s'appelle `feature/s6-report-moderation`, conformément au nom suggéré dans [`backlog_s5_s10.md` ligne 671](backend/docs/backlog_s5_s10.md#L671). Le préfixe `s6` est un artefact historique (ticket créé en S6, reporté en S7) — l'équipe a choisi de ne pas renommer pour respecter la traçabilité du backlog, **comme pour SCRUM-136** (`feature/s6-co-organizers`). Voir [`specs_scrum-136.md` décision 1](specs_archives/specs_claude/specs_scrum-136.md).

**Justification.** Cohérence intra-Sprint avec SCRUM-136 mergé. Les autres tickets « historiquement S6 » du backlog (SCRUM-96 `feature/s6-report-modal`, SCRUM-97 `feature/s6-admin-dashboard`) suivront le même préfixe.

### 2. Base = `origin/main` à jour, **pas** une branche en vol

**Décision.** La PR ouvre une branche fraîche depuis `origin/main` (clean, dernier commit `3a27135` au moment de cette spec, V8 + cascade SCRUM-136 inclus).

**Justification.** Aucune autre branche S7 actuellement ouverte ne fournit du substrat utile : SCRUM-98 (`feature/s7-expiration-job`) modifie `EventService` mais sur un axe orthogonal (status `EXPIRED`), aucun fichier touché en commun. SCRUM-95 et le reste sont à faire. Repartir de main est la base la plus saine.

```bash
git fetch origin
git checkout -b feature/s6-report-moderation origin/main --no-track
```

⚠️ **`--no-track` est OBLIGATOIRE** (cf. [`specs_scrum-136.md` étape Branche](specs_archives/specs_claude/specs_scrum-136.md#L1879-L1890)). Sans ce flag, la branche traque `origin/main` et `git push` envoie les commits sur main (incident historique repris par toutes les specs).

### 3. Schéma — Flyway V9 obligatoire (Hibernate est en `validate`)

**Décision.** Toute évolution du schéma de `reports` passe par un nouveau fichier `V9__add_report_reason_and_review_fields.sql`. **JAMAIS** de mutation des V6 ou V7 déjà committées (cf. [`AGENTS.md`](backend/AGENTS.md#L54-L57) : *« Une migration committée est immutable »*).

**Justification.** Hibernate est désormais en `quarkus.hibernate-orm.database.generation=validate` en dev/prod (cf. [`backend/docs/data-model.md` section « Gestion du schéma — Flyway »](backend/docs/data-model.md#L436-L455)). Si on modifie l'entité `Report.java` sans la migration correspondante, le démarrage Quarkus **échoue** avec un `SchemaManagementException` au boot. Le mode `update` mentionné dans le libellé du ticket Jira SCRUM-94 est obsolète depuis SCRUM-164.

**Description fonctionnelle de V9** (détail SQL en étape 1 de l'implémentation) :

1. Renommer la colonne actuelle `reports.reason` (TEXT libre) en `reports.description` (TEXT libre, nullable).
2. Ajouter une nouvelle colonne `reports.reason` typée `VARCHAR(32)` avec CHECK constraint sur `ReportReason` (`SPAM`, `INAPPROPRIATE`, `FAKE`, `OTHER`).
3. Backfill : pour les rows existantes (devraient être vides en prod, mais sécurise les envs locaux), poser `reason = 'OTHER'`.
4. Passer la colonne `reason` à `NOT NULL` après backfill.
5. Ajouter `reviewed_at TIMESTAMP NULL`.
6. Ajouter `reviewed_by UUID NULL` avec FK `fk_reports_reviewed_by` vers `users(id)`.
7. Ajouter `moderation_note TEXT NULL` (cf. décision 11 sur `HandleReportRequest`).

### 4. Pas de champ `admin: boolean` sur `User` — rôle Auth0 uniquement

**Décision.** **Aucun champ `admin: boolean`** ajouté à l'entité `User`. Le rôle ADMIN est exclusivement géré par Auth0 via la claim `https://quarkus-security.com/roles` (cf. [`application.properties:31`](backend/src/main/resources/application.properties#L31) — `quarkus.oidc.roles.role-claim-path=...`). La détection se fait via `identity.hasRole("ADMIN")`, pattern déjà utilisé dans :
- [`EventResource.java:85`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L85)
- [`EventCoOrganizerResource.java:44`](backend/src/main/java/ch/unige/events/resource/EventCoOrganizerResource.java#L44)
- [`EventService.java:143`](backend/src/main/java/ch/unige/events/service/EventService.java#L143)

`AdminReportResource` utilisera `@RolesAllowed("ADMIN")` (Quarkus Security) — **plus déclaratif** que `if (!isAdmin) throw new ForbiddenException()`. L'annotation est intercepteée par Quarkus avant l'exécution de la méthode et renvoie automatiquement `403 forbidden` si la claim manque.

**Justification — pourquoi pas le champ DB.**

| Option | Verdict |
|---|---|
| (a) Ajouter `User.admin: boolean` + colonne SQL + setter dédié | ❌ Double source de vérité (Auth0 vs DB) → risque de divergence (un user élevé en ADMIN dans Auth0 mais pas dans la DB serait bloqué) |
| (b) Synchroniser la claim Auth0 avec un champ `User.admin` à chaque login | ❌ Logique de sync à maintenir, race condition possible, complexité gratuite |
| (c) **Rôle Auth0 uniquement (statu quo)** | ✅ Une seule source de vérité, pattern déjà adopté par le reste du code, zéro changement DB |

**Action corollaire — retrait du TODO `admin` dans openapi.yaml.** L'OpenAPI documente actuellement [`admin: boolean # TODO: Sprint 6 — champ non encore implémenté`](openapi/openapi.yaml#L72-L74) sur le schéma `UserProfileResponse`. Cette décision retire la propriété et son TODO, et ajoute une note explicative dans la description du schéma : *« Le rôle ADMIN n'est pas exposé sur le profil — il est porté par la claim Auth0 et consommé via `@RolesAllowed`. »* Le frontend qui voudrait afficher un badge « Admin » doit lire `auth.user.['https://quarkus-security.com/roles']` côté Auth0 SDK, pas un champ profil.

> **Note pour AGENTS.md.** Le bloc *« Champ `admin` sur User »* ([`backend/AGENTS.md`](backend/AGENTS.md#L64-L65)) qui annonce le champ « planifié Sprint 6 » devient caduc — à remplacer par une note explicite *« Le rôle ADMIN est géré exclusivement via la claim Auth0 — pas de champ `admin` sur l'entité ; les endpoints sensibles utilisent `@RolesAllowed("ADMIN")`. »*

### 5. `ReportReason` — enum côté Java + CHECK constraint côté DB

**Décision.** Nouvel enum :

```java
public enum ReportReason {
    SPAM,
    INAPPROPRIATE,
    FAKE,
    OTHER
}
```

Côté DB (V9) : `reason VARCHAR(32) NOT NULL CHECK (reason IN ('SPAM', 'INAPPROPRIATE', 'FAKE', 'OTHER'))`.

**Justification.** Aligné sur le pattern existant pour `EventStatus`, `EventCategory`, `Faculty`, `AttendanceStatus`, `ReportStatus`, `CoOrganizerStatus` — tous des `@Enumerated(STRING)` côté Hibernate avec une CHECK explicite côté DB. Le CHECK fournit une garde *defense in depth* : même si un INSERT direct shorte Hibernate (ex. via une migration manuelle ou un script externe), la valeur invalide est rejetée par PostgreSQL.

> **Note future.** Toute modification de `ReportReason` (ajout d'une valeur, rename) **devra** passer par une migration `V<N+1>__update_report_reason_check.sql` qui drop+recrée la contrainte avec les valeurs courantes. Le pattern est documenté dans [`backend/docs/data-model.md` section Flyway](backend/docs/data-model.md#L449-L455).

### 6. Stratégie de renommage `reason → description` — pas une nouvelle colonne `description` séparée + delete `reason`

**Décision.** La colonne actuelle `reports.reason` (TEXT libre) est **renommée** `reports.description`. La sémantique métier de cette colonne (texte libre saisi par l'utilisateur en complément) reste inchangée — seul le nom change. Une **nouvelle** colonne `reports.reason` est ensuite ajoutée, typée `VARCHAR(32)` avec CHECK enum.

**Justification.** Trois options ont été pesées :

| Option | Conséquence | Verdict |
|---|---|---|
| (a) Drop l'ancienne `reason` + recréer une `reason` typée + ajouter `description` séparée | Perte des données existantes (vide en prod, mais hypothèque les envs locaux) | ❌ destructif gratuit |
| (b) Garder `reason` (TEXT) telle quelle + ajouter `reason_category` (VARCHAR enum) + ajouter `description` ? | 3 colonnes pour 2 concepts → noms incohérents (`reason` reste libre, `reason_category` est l'enum) — confusion garantie | ❌ pollue le contrat |
| (c) **Renommer `reason` (TEXT libre) en `description`, puis créer `reason` (VARCHAR enum)** | Un seul concept par nom — `reason` est désormais l'enum, `description` est le texte libre. Sémantique stable côté API (le frontend voit `reason` typé + `description` libre, conforme au libellé du ticket Jira) | ✅ retenu |

L'option (c) implique un mini-jeu de chaises musicales sur la même colonne, mais c'est PostgreSQL — `ALTER TABLE … RENAME COLUMN` est instantané et sans verrou table lourd. Le risque opérationnel est nul (la table est minuscule, et de toute façon vide en prod actuellement).

### 7. `Report.reporter` reste `nullable` — soft account-deletion compatible

**Décision.** La FK `reports.reporter_id` reste `NULL` autorisée, comme posée par [`V6__create_reports.sql:13`](backend/src/main/resources/db/migration/V6__create_reports.sql#L13). Aucune modification dans V9 sur cette colonne.

**Justification.** Pattern défensif : si un utilisateur supprime son compte (feature non livrée mais planifiée), ses signalements doivent rester traçables sans bloquer la suppression. La nullité est sémantiquement *« reporter inconnu / supprimé »*. **Service-side, on rejette le cas `reporter == null` à la création** (l'auth est obligatoire via `@Authenticated`).

### 8. `Report.reviewedBy` — `@ManyToOne LAZY` vers `User`, nullable

**Décision.** Sur l'entité Java :

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "reviewed_by")
public User reviewedBy;
```

Côté DB (V9) : `reviewed_by UUID NULL` + FK `fk_reports_reviewed_by` vers `users(id)`.

**Justification.** Pattern symétrique à `Report.reporter`. Le DTO sortant exposera uniquement l'`UUID` (`reviewedBy: User.id`), pas l'objet `User` — privacy + minimalisme (aligné sur la projection `creatorId` dans `EventDTO`). Voir décision 12.

### 9. `Report.reviewedAt` — `LocalDateTime`, posé côté service au moment du PATCH

**Décision.** `reviewed_at TIMESTAMP NULL` côté DB ; `LocalDateTime reviewedAt` côté Java. **Pas** de `@PreUpdate` automatique — la valeur est posée explicitement dans `ReportService.handle(...)` au moment de la transition `PENDING → REVIEWED|DISMISSED`. Si jamais une row est mutée hors de ce flow (hypothèse non envisageable en prod), `reviewedAt` reste `null`.

**Justification.** Cohérent avec le pattern `Event.updatedAt` qui utilise `@PreUpdate` — mais ici on veut une sémantique plus stricte : *« reviewedAt = quand l'admin a tranché »*, pas *« reviewedAt = la dernière fois que la row a été touchée »*. Le service est l'autorité.

### 10. `ReportService.create` — règles métier (4xx + 422)

**Décision.** Le service `create(eventId, reporterAuth0Id, CreateReportRequest req)` lève les exceptions suivantes :

| Cas | Code HTTP | Envelope `error` | Détail |
|---|---|---|---|
| `eventId` inexistant | `404` | (générique) | `NotFoundException("Event not found")` |
| Event en statut `DRAFT` | `400` | `cannot_report_draft` | Sémantique : un DRAFT n'est pas public, donc pas signalable. `WebApplicationException` 400 |
| Event en statut `CANCELLED` | `400` | `cannot_report_cancelled` | Idem — un event annulé n'a plus à être modéré (cf. décision 13) |
| Reporter = créateur de l'event (self-report) | **`422`** | `cannot_report_own_event` | `WebApplicationException` 422 avec envelope dédiée |
| Reporter déjà signalé cet event (unique constraint) | `409` | `already_reported` | Catch `PersistenceException` ou check préalable via `Report.find(...)` |
| `reason` absent ou hors enum | `400` | (validation) | Bean Validation `@NotNull`/`@Pattern` |
| `description` > 2000 chars | `400` | (validation) | `@Size(max=2000)` |
| Reporter non authentifié | `401` | (déclaratif) | `@Authenticated` |
| Reporter non provisionné en base (auth0Id sans User) | `404` | (générique) | Exception levée par le helper `User.findByAuth0Id` |

**Justification — pourquoi 422 sur le self-report.** RFC 4918 / WebDAV : *Unprocessable Entity = la requête est syntaxiquement valide mais sémantiquement incorrecte*. Le body est bien formé (la `reason` est valide), mais signaler son propre événement n'a pas de sens métier — `422` est le code dédié à cette classe d'erreur. Aligné avec le récent fix de SCRUM-136 ([`PATCH /me/accept|decline` → 422 `no_pending_invitation`](backend/docs/data-model.md#L191-L195)) qui a établi le pattern dans le projet.

**Justification — pourquoi 400 sur `cannot_report_draft`.** Le DRAFT n'a pas de surface publique (cf. hotfix pentest [`getById` règle de visibilité](backend/docs/data-model.md#L76-L86)). Un signalement sur un DRAFT signalerait soit (a) un signalement qu'aucun autre user ne peut comprendre (DRAFT invisible), soit (b) une fuite d'info (l'utilisateur a vu le DRAFT par un canal hors-API). On rejette avec 400 plutôt que 404 pour ne pas fermer l'oracle d'existence — le `eventId` existe, mais ne peut pas être signalé.

> **Note de design.** On pourrait argumenter que `cannot_report_draft` mériterait `403 forbidden` (l'action n'est pas autorisée pour cet état). Choix retenu : `400` — l'état du serveur empêche l'opération, ce n'est pas un défaut d'autorisation. Pattern aligné sur [`AttendanceService.attend` ligne 54](backend/src/main/java/ch/unige/events/service/AttendanceService.java#L54) qui renvoie 400 *« Cannot attend a non-published event »*.

### 11. `HandleReportRequest` — `status` requis + `moderationNote` optionnel

**Décision.** Body de `PATCH /admin/reports/{id}` :

```java
public record HandleReportRequest(
    @NotNull ReportStatus status,
    @Size(max = 2000) String moderationNote
) {}
```

Validation supplémentaire dans le service :

```java
if (status != ReportStatus.REVIEWED && status != ReportStatus.DISMISSED) {
    throw badRequest("invalid_transition", "Only REVIEWED or DISMISSED are accepted as a target status.");
}
```

**Justification — pourquoi `moderationNote` est persisté.**

| Option | Verdict |
|---|---|
| (a) Ne pas persister, juste log | ❌ Trace volatile, perdue à la rotation des logs ; pas auditable côté frontend |
| (b) **Persister dans une nouvelle colonne `moderation_note TEXT NULL`** | ✅ retenu — auditable, exposé dans `ReportDTO`, utile pour SCRUM-97 (dashboard admin peut afficher l'historique) |
| (c) Créer une table `report_moderation_log` (signalement, admin, note, timestamp, action) | ❌ Sur-ingénierie pour un seul champ ; à introduire si on veut un historique multi-actions par signalement |

L'option (b) ajoute la colonne `moderation_note` dans V9.

**Justification — pourquoi `status: ReportStatus` plutôt qu'un `action: enum DISMISS|REVIEW`.** L'OpenAPI ébauché actuellement ([lignes 2528-2536](openapi/openapi.yaml#L2528-L2536)) propose `action: DISMISS | REMOVE_EVENT`. Cette spec rejette ce design pour deux raisons :

1. **`REMOVE_EVENT` est une action complexe (cancel l'event + traiter le report) qui mélange deux préoccupations** — elle appartient au scope « modération admin » d'un futur ticket (cf. SCRUM-72 globalement, mais pas SCRUM-94). SCRUM-94 livre le primitive « passer un report en REVIEWED ou DISMISSED » ; les actions composites (cancel l'event signalé + REVIEWED tous ses reports) seront ajoutées plus tard si besoin.
2. **`status` reflète le modèle métier directement** — `Report.status` est l'enum existant, le frontend connaît déjà `ReportStatus`. Pas de mapping `action → status` à maintenir.

### 12. `ReportDTO` — projection complète, expose les UUID des relations

**Décision.** Le DTO :

```java
public record ReportDTO(
    Long id,
    Long eventId,
    UUID reporterId,
    ReportReason reason,
    String description,
    ReportStatus status,
    String moderationNote,
    LocalDateTime createdAt,
    LocalDateTime reviewedAt,
    UUID reviewedBy
) {
    public static ReportDTO from(Report r) {
        return new ReportDTO(
            r.id,
            r.event != null ? r.event.id : null,
            r.reporter != null ? r.reporter.id : null,
            r.reason,
            r.description,
            r.status,
            r.moderationNote,
            r.createdAt,
            r.reviewedAt,
            r.reviewedBy != null ? r.reviewedBy.id : null
        );
    }
}
```

**Justification.**
- **Champs choisis.** Tout ce qu'un dashboard admin (SCRUM-97) doit afficher : id du report, event signalé, qui a signalé, pourquoi, état actuel, qui a tranché, quand, avec quelle note.
- **`reporterId` et `reviewedBy` sont des `UUID` nus** (pas un `UserPublicResponse` enrichi) — pour deux raisons :
  - **N+1 évité** : un listing de 50 reports sinon = 50 lookups User. Le frontend SCRUM-97 fera un bulk `GET /users/{id}` à la demande s'il a besoin d'enrichir.
  - **Privacy** : l'enrichissement systématique exposerait avatar, displayName et faculty d'utilisateurs dans un payload admin — minimaliste par défaut.
- **`event` n'est PAS inline** — `eventId` suffit. Le dashboard admin fait un `GET /events/{id}` à la sélection si besoin. Cohérent avec [`AttendanceDTO.eventId`](backend/src/main/java/ch/unige/events/dto/attendance/AttendanceDTO.java).
- **Pas de `reporterDisplayName`** — privacy. Le frontend admin est libre de drill-down sur l'UUID si nécessaire.

### 13. Pas de cascade « cancel event quand n reports REVIEWED »

**Décision.** Le passage d'un `Report.status` à `REVIEWED` **ne déclenche AUCUNE action** sur l'event signalé. L'event reste tel quel — c'est l'admin qui décidera ensuite (manuellement, via [`PATCH /events/{id}/cancel`](backend/src/main/java/ch/unige/events/resource/EventResource.java)) si l'event doit être annulé.

**Justification.**
- L'auto-cancel existe **déjà** via [`ModerationCleanupJob`](backend/src/main/java/ch/unige/events/scheduler/ModerationCleanupJob.java) qui tourne tous les jours à 3h et cancel les events dépassant le seuil de PENDING. C'est cet axe-là qui couvre US-18 (auto-cleanup).
- US-T4 demande explicitement *« actions dismisser/supprimer »* — la suppression de l'event est une action **séparée** du traitement du signalement. La compose-action (« REVIEWED ce report ET cancel cet event ») est un sucre syntaxique qui appartient à un futur ticket si l'UX l'exige.
- SCRUM-94 reste donc strictement le primitif : transitionner le `Report.status`. Pas de side-effect.

### 14. Pas de notification émise quand un report est créé ou traité

**Décision.** Aucune notification n'est levée à `POST /events/{id}/report` ni à `PATCH /admin/reports/{id}`. Pas d'entité `Notification`, pas de Quarkus event, pas de hook async.

**Justification.** L'épic Notifications (SCRUM-99) est explicitement Sprint 8. Toute infrastructure de notif ajoutée ici introduirait des dépendances (table, Service, scheduler éventuel) qui sortent largement du scope SCRUM-94. Une fois SCRUM-99 livré, un follow-up trivial branchera ces deux events sur le pipeline notif.

### 15. Réutilisation de la contrainte unique existante pour le 409

**Décision.** Le service `create(...)` détecte le doublon via un `find("reporterId = ?1 and eventId = ?2", ...)` préalable au `persist`, **PUIS** s'appuie sur la contrainte unique en filet de sécurité. L'envelope d'erreur est `409 already_reported`.

```java
if (Report.<Report>find("reporter.id = ?1 and event.id = ?2", reporter.id, eventId).count() > 0) {
    throw conflict("already_reported", "You have already reported this event.");
}
```

**Justification.** Le check préalable produit l'envelope custom `error=already_reported` lisible côté UI. Le filet de sécurité (la contrainte unique posée en V6) protège du race condition concurrent (deux POST simultanés du même reporter sur le même event) — Hibernate jette une `PersistenceException` mappée en 409 via le mapper existant ; au pire, l'envelope est moins jolie sur ce cas extrême-rare (acceptable, cf. SCRUM-136 décision 18 sur le même trade-off).

### 16. Pagination admin = pattern `/me/favorites`

**Décision.** `GET /api/admin/reports` accepte les query params suivants :

| Param | Type | Validation | Défaut | Comportement |
|---|---|---|---|---|
| `status` | `ReportStatus` | enum strict | **`PENDING`** | Filtre sur le statut |
| `page` | `int` | `@Min(0)` | `0` | Page (0-indexée) |
| `size` | `int` | `@Positive @Max(100)` | `20` | Taille de page |

Tri : `createdAt DESC`, tie-breaker `id DESC`.

**Justification.** Pattern strictement identique à [`UserResource.getMyFavorites` ligne 264-272](backend/src/main/java/ch/unige/events/resource/UserResource.java#L264-L272). Le default `status=PENDING` correspond au cas d'usage principal du dashboard admin (filer la pile de signalements à traiter en premier).

### 17. Pas de bulk-action sur l'admin endpoint

**Décision.** `PATCH /admin/reports/{id}` traite **un seul** signalement par requête. Pas de `PATCH /admin/reports/bulk`, pas de `PATCH /admin/reports?ids=1,2,3`.

**Justification.** Le dashboard admin (SCRUM-97) traite le pile à l'unité. Si une UX bulk émerge plus tard, un endpoint dédié sera ajouté — pas un sucre syntaxique sur `{id}`.

### 18. Pas de gating de visibilité sur `GET /events/{id}/report` (POST)

**Décision.** Tout utilisateur authentifié non-créateur peut signaler un event PUBLISHED, **y compris** un admin. Pas de blacklist de profils.

**Justification.** L'admin peut être un utilisateur lambda qui croise un event problématique. Le rôle ADMIN sert à *traiter* les signalements, pas à *en être exempté*. La règle métier `cannot_report_own_event` (cf. décision 10) couvre la seule contrainte vraiment requise : on ne signale pas son propre event.

### 19. Co-organisateurs ACCEPTED — reportable ?

**Décision.** Un co-organisateur ACCEPTED **ne peut pas** signaler son propre event. Comme le créateur, il est partie prenante de l'event.

**Justification.** Cohérent avec la cascade SCRUM-136 (`isCreatorOrAcceptedCoOrganizer` réutilisé). La règle d'erreur devient :

```java
if (eventService.isCreatorOrAcceptedCoOrganizerPublic(event, reporterAuth0Id)) {
    throw unprocessable("cannot_report_own_event", "You cannot report an event you organize.");
}
```

→ 422. Évite que deux co-orgs en désaccord ne se signalent l'event mutuellement.

### 20. Idempotence — pas d'idempotence applicative sur `PATCH /admin/reports/{id}`

**Décision.** Si un admin appelle `PATCH /admin/reports/{id}` sur un report déjà non-PENDING (ex. déjà REVIEWED), le service rejette avec **`409 invalid_transition`** au lieu de no-op silencieusement.

```java
if (report.status != ReportStatus.PENDING) {
    throw conflict("invalid_transition",
        "Report is already in status " + report.status + " — only PENDING reports can be transitioned.");
}
```

**Justification.** Une transition double-cliquée par l'admin (`REVIEWED` puis `DISMISSED`) doit être tracée comme une erreur, pas absorbée. Pas d'audit log sophistiqué dans cette PR — le 409 est le signal explicite.

> **Trade-off explicite.** Une refresh tardive du dashboard admin pourrait afficher un report comme PENDING alors qu'un autre admin vient de le traiter — un clic produirait alors un 409 surprenant. Acceptable côté UX (le dashboard SCRUM-97 affichera un toast d'erreur). Si le frottement devient gênant, ouvrir un follow-up pour basculer en idempotent.

### 21. Helpers privés-static `badRequest` / `conflict` / `unprocessable` dans `ReportService`

**Décision.** Trois helpers privés-static dans le service :

```java
private static WebApplicationException badRequest(String error, String message) {
    return new WebApplicationException(
        Response.status(Response.Status.BAD_REQUEST)
            .entity(new ApiErrorResponse(error, message))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build());
}

private static WebApplicationException conflict(String error, String message) {
    return new WebApplicationException(
        Response.status(Response.Status.CONFLICT)
            .entity(new ApiErrorResponse(error, message))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build());
}

private static WebApplicationException unprocessable(String error, String message) {
    return new WebApplicationException(
        Response.status(422)
            .entity(new ApiErrorResponse(error, message))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build());
}
```

**Justification.** Pattern strictement aligné sur [`EventCoOrganizerService.badRequest/conflict` lignes 1106-1120 de la spec SCRUM-136](specs_archives/specs_claude/specs_scrum-136.md#L1106-L1120). `unprocessable` est nouveau (pas encore dans le code), aligné sur le fix de review SCRUM-136 qui a introduit le code 422.

### 22. Resources séparées : `ReportResource` (utilisateur) + `AdminReportResource` (admin)

**Décision.** Deux Resources distinctes :

| Resource | `@Path` | Auth | Endpoints |
|---|---|---|---|
| `ReportResource` | `/events` | `@Authenticated` (par méthode) | `POST /{id}/report` |
| `AdminReportResource` | `/admin/reports` | `@RolesAllowed("ADMIN")` (par classe) | `GET /` + `PATCH /{id}` |

**Justification.** Séparation par scope d'autorisation pour clarté + alignement avec le futur ajout SCRUM-95 (`AdminEventResource` pour featured events). Un seul `ReportResource` qui mélangerait `@Authenticated` et `@RolesAllowed` serait moins lisible — les annotations de classe perdraient leur sens.

---

## Analyse de l'existant

### Ce qui existe (à réutiliser tel quel)

| Élément | Fichier / ligne | Rôle dans SCRUM-94 |
|---|---|---|
| Entité `Report` (PanacheEntity) | [`Report.java`](backend/src/main/java/ch/unige/events/entity/Report.java) | À ÉTENDRE — ajouter `reason` enum, `description`, `reviewedAt`, `reviewedBy`, `moderationNote` |
| Enum `ReportStatus` | [`ReportStatus.java`](backend/src/main/java/ch/unige/events/entity/ReportStatus.java) | À RÉUTILISER tel quel |
| Migration `V6__create_reports.sql` | [`V6__create_reports.sql`](backend/src/main/resources/db/migration/V6__create_reports.sql) | **NE PAS TOUCHER** — immutable |
| `reports_status_check` posée par V7 | [`V7:18-20`](backend/src/main/resources/db/migration/V7__reconcile_check_constraints.sql#L18-L20) | **NE PAS TOUCHER** |
| Contrainte unique `uk_report_reporter_event` | [`V6:11`](backend/src/main/resources/db/migration/V6__create_reports.sql#L11) | À RÉUTILISER pour le 409 `already_reported` |
| `ModerationCleanupService.fetchPendingReportCounts` | [`ModerationCleanupService.java:46-51`](backend/src/main/java/ch/unige/events/service/ModerationCleanupService.java#L46-L51) | Lit `r.event` et `r.status` UNIQUEMENT — non impacté par la modif |
| Pattern `EventCoOrganizerResource` constructor DI | [`EventCoOrganizerResource.java:33-37`](backend/src/main/java/ch/unige/events/resource/EventCoOrganizerResource.java#L33-L37) | Modèle direct pour `ReportResource` et `AdminReportResource` |
| Pattern `EventCoOrganizerService.badRequest/conflict` | spec SCRUM-136 lignes 1106-1120 | Modèle pour les helpers de `ReportService` |
| Pattern `@Authenticated` + `identity.getPrincipal().getName()` | [`EventResource.java:34-41`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L34-L41) | Auth normalisée |
| Pattern `@RolesAllowed("ADMIN")` (Quarkus Security) | non utilisé dans le code à ce jour ; à introduire | Pattern Quarkus standard, importé depuis `jakarta.annotation.security.RolesAllowed` |
| Helper `User.findByAuth0Id` | [`User.java:53-55`](backend/src/main/java/ch/unige/events/entity/User.java#L53-L55) | Résolution `auth0Id → User` (reporter, reviewedBy) |
| Pattern `ApiErrorResponse` record | [`ApiErrorResponse.java`](backend/src/main/java/ch/unige/events/dto/ApiErrorResponse.java) | Envelope d'erreur standard |
| Helper `EventService.isCreatorOrAcceptedCoOrganizerPublic` | [`EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) (introduit en SCRUM-136) | Réutilisé pour la garde `cannot_report_own_event` (cf. décision 19) |
| Pattern de pagination `/me/favorites` | [`UserResource.java:264-272`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L264-L272) | Modèle direct pour `GET /admin/reports` |
| `MockEventFactory.build` | [`MockEventFactory.java`](backend/src/test/java/ch/unige/events/MockEventFactory.java) | Seed d'Event en test |
| Pattern `*ServiceMock` (`@Mock @ApplicationScoped extends Service`) | [`AttendanceServiceMock.java`](backend/src/test/java/ch/unige/events/service/AttendanceServiceMock.java) | Modèle direct pour `ReportServiceMock` |
| Pattern `@TestSecurity(user = "auth0\|alice")` | [`AttendanceResourceTest.java:32`](backend/src/test/java/ch/unige/events/resource/AttendanceResourceTest.java#L32) | Tests `@QuarkusTest` |
| Pattern `@TestSecurity(user="...", roles={"ADMIN"})` | non encore utilisé dans le projet | Quarkus Security test idiom standard |

### Ce qui est à modifier

| Fichier | Modification |
|---|---|
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | **Reprendre** schémas `Report`, `ReportRequest` (renommer en `CreateReportRequest`) ; **ajouter** schémas `ReportReason`, `HandleReportRequest`, `PagedReports` ; **finaliser** paths `POST /events/{id}/report`, `GET /admin/reports`, `PATCH /admin/reports/{id}` (était `PUT`). **Retirer** le TODO `admin: boolean` du schéma `UserProfileResponse` + ajouter une note sur la gestion via claim Auth0. |
| [`backend/src/main/java/ch/unige/events/entity/Report.java`](backend/src/main/java/ch/unige/events/entity/Report.java) | Ajouter `reason: ReportReason` (`@NotNull`, `@Enumerated(STRING)`), `description: String` (TEXT, nullable, ex-`reason`), `moderationNote: String` (TEXT, nullable), `reviewedAt: LocalDateTime` (nullable), `reviewedBy: User` (`@ManyToOne LAZY`, nullable). |
| [`backend/src/test/java/ch/unige/events/entity/ReportTest.java`](backend/src/test/java/ch/unige/events/entity/ReportTest.java) | Mettre à jour le test `fieldsAreAssignable` (`report.reason = "..."` en String → `report.description = "..."`). Ajouter tests pour les nouveaux champs. |
| [`backend/AGENTS.md`](backend/AGENTS.md) | Section *« Champ `admin` sur User »* (lignes 64-65) → remplacer par note sur la gestion via claim Auth0 (cf. décision 4). |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Section `Report` (lignes 226-241) — ajouter les 5 nouveaux champs, mentionner `ReportReason`, l'unicité `(reporter_id, event_id)`, la sémantique de `description` (libre) vs `reason` (enum) et la sémantique de `reviewedAt`/`reviewedBy`. Ajouter `ReportReason` au tableau « Énumérations ». |
| [`backend/docs/api-contract.md`](backend/docs/api-contract.md) | Lignes 269-271 — **finaliser** les 3 endpoints : status `Sprint 6` → `Sprint 7 (SCRUM-94)`, codes d'erreur. |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Section Sprint 7 — entrée SCRUM-94. |

### Ce qui est à créer

| Fichier | Rôle |
|---|---|
| `backend/src/main/resources/db/migration/V9__add_report_reason_and_review_fields.sql` | Migration Flyway (rename + add columns + CHECK + FK + backfill) |
| `backend/src/main/java/ch/unige/events/entity/ReportReason.java` | Enum 4 valeurs |
| `backend/src/main/java/ch/unige/events/dto/report/ReportDTO.java` | Projection sortante |
| `backend/src/main/java/ch/unige/events/dto/report/CreateReportRequest.java` | Body POST `/events/{id}/report` |
| `backend/src/main/java/ch/unige/events/dto/report/HandleReportRequest.java` | Body PATCH `/admin/reports/{id}` |
| `backend/src/main/java/ch/unige/events/service/ReportService.java` | Métier (create, listByStatus, handle) |
| `backend/src/main/java/ch/unige/events/resource/ReportResource.java` | `POST /api/events/{id}/report` |
| `backend/src/main/java/ch/unige/events/resource/AdminReportResource.java` | `GET/PATCH /api/admin/reports` |
| `backend/src/test/java/ch/unige/events/dto/report/ReportDTOTest.java` | Tests unitaires factory |
| `backend/src/test/java/ch/unige/events/service/ReportServiceMock.java` | Mock service pour tests Resource |
| `backend/src/test/java/ch/unige/events/resource/ReportResourceTest.java` | Tests `@QuarkusTest` POST utilisateur |
| `backend/src/test/java/ch/unige/events/resource/AdminReportResourceTest.java` | Tests `@QuarkusTest` GET + PATCH admin |
| `backend/src/test/java/ch/unige/events/service/ReportServiceCoverageTest.java` | Tests intégration DevServices PostgreSQL |

### Ce qui n'est PAS dans le scope

- ❌ Pas d'auto-cancel d'event au passage d'un report en `REVIEWED` (cf. décision 13).
- ❌ Pas de bulk-handle (`PATCH /admin/reports?ids=…`) — un endpoint = un report (cf. décision 17).
- ❌ Pas de cascade « cancel event + REVIEWED tous les reports liés » — out of scope.
- ❌ Pas de notification email/push à la création ou au traitement (cf. décision 14).
- ❌ Pas d'historique multi-actions (`report_moderation_log`) — la note unique suffit (cf. décision 11).
- ❌ Pas de modification du frontend — SCRUM-96 et SCRUM-97 ouvriront leurs propres PR.
- ❌ Pas de champ `admin: boolean` sur `User` — rôle Auth0 uniquement (cf. décision 4).
- ❌ Pas d'extension de `ModerationCleanupService` — le job continue de lire `r.status` uniquement, insensible à la nouvelle structure.
- ❌ Pas de modification du `creator` ou de `Event` (zéro changement entité Event).
- ❌ Pas d'override de la règle `isCreatorOrAcceptedCoOrganizer` — réutilisée tel quel via le helper public exposé en SCRUM-136.
- ❌ Pas de chiffrement / hash spécial sur `description` ou `moderationNote` — TEXT clair, aligné avec `Event.description`.
- ❌ Pas d'ajout de rate limiting sur `POST /report` — surface trop tôt pour ce vecteur d'abus, follow-up si signalé en pentest.
- ❌ Pas de version `Sprint 8` du système (notif, bulk, audit log) — out of scope.

---

## Étape 0 — `openapi/openapi.yaml` (EN PREMIER, règle d'or)

**Aucune ligne de Java ne doit être écrite avant cette étape.** [`backend/AGENTS.md`](backend/AGENTS.md#L62-L65) : *« Avant d'implémenter un endpoint : 1. L'ajouter dans `openapi/openapi.yaml` ; 2. Ensuite seulement coder Resource → Service → Entity → Test »*.

### 0.1 — Ajouter le schema `ReportReason` (section `components.schemas`, à côté de `ReportStatus`)

```yaml
    ReportReason:
      type: string
      description: |
        Catégorie de signalement choisie par l'utilisateur dans la modale frontend
        (SCRUM-96). Persistée dans `reports.reason` avec une CHECK constraint côté DB.
      enum: [SPAM, INAPPROPRIATE, FAKE, OTHER]
```

### 0.2 — Reprendre le schema `Report` (existant, à enrichir)

```yaml
    Report:
      type: object
      description: |
        Représentation d'un signalement, retournée par `GET /api/admin/reports` et
        `PATCH /api/admin/reports/{id}`. `reporterId` et `reviewedBy` sont des UUID nus
        (privacy + minimalisme — le frontend admin drill-down via `GET /users/{id}` si besoin).
      properties:
        id:
          type: integer
          format: int64
        eventId:
          type: integer
          format: int64
        reporterId:
          type: string
          format: uuid
          nullable: true
          description: |
            UUID de l'utilisateur ayant signalé. Nullable si le compte a été supprimé
            (préservation de la trace).
        reason:
          $ref: '#/components/schemas/ReportReason'
        description:
          type: string
          nullable: true
          maxLength: 2000
          description: Texte libre saisi par l'utilisateur en complément du motif catégoriel.
        status:
          $ref: '#/components/schemas/ReportStatus'
        moderationNote:
          type: string
          nullable: true
          maxLength: 2000
          description: Note de modération laissée par l'admin au moment du PATCH.
        createdAt:
          type: string
          format: date-time
        reviewedAt:
          type: string
          format: date-time
          nullable: true
          description: Posé par `PATCH /admin/reports/{id}`. Null tant que le signalement est PENDING.
        reviewedBy:
          type: string
          format: uuid
          nullable: true
          description: UUID de l'admin qui a tranché. Null tant que PENDING.
      required: [id, eventId, reason, status, createdAt]
```

### 0.3 — Renommer `ReportRequest` → `CreateReportRequest` et ajouter `HandleReportRequest`

```yaml
    CreateReportRequest:
      type: object
      description: |
        Body de `POST /api/events/{id}/report`. La catégorie de motif (`reason`) est
        obligatoire ; le texte libre (`description`) est optionnel et limité à 2000 chars.
      required: [reason]
      properties:
        reason:
          $ref: '#/components/schemas/ReportReason'
        description:
          type: string
          maxLength: 2000
          nullable: true

    HandleReportRequest:
      type: object
      description: |
        Body de `PATCH /api/admin/reports/{id}`. `status` doit être `REVIEWED` ou `DISMISSED` —
        `PENDING` n'est pas un statut cible valide. `moderationNote` est optionnel
        (texte libre, limité à 2000 chars).
      required: [status]
      properties:
        status:
          $ref: '#/components/schemas/ReportStatus'
        moderationNote:
          type: string
          maxLength: 2000
          nullable: true
```

> **Suppression nécessaire.** Le schéma `ReportRequest` actuel ([lignes 489-495 d'openapi.yaml](openapi/openapi.yaml#L489-L495)) doit être **supprimé** (renommé en `CreateReportRequest` ci-dessus). Les `$ref` qui pointent dessus sont à mettre à jour (un seul ref dans `paths./events/{id}/report.post.requestBody`).

### 0.4 — Reprendre les paths `POST /events/{id}/report`, `GET /admin/reports`, `PATCH /admin/reports/{id}`

**Remplacer entièrement les 3 paths existants** ([lignes 2437-2545 d'openapi.yaml](openapi/openapi.yaml#L2437-L2545) — qui étaient marqués `TODO Sprint 6`) par :

```yaml
  /events/{id}/report:
    post:
      summary: Signaler un événement
      description: |
        Crée un signalement sur un événement PUBLISHED. Réservé aux utilisateurs authentifiés
        non-créateurs et non-co-organisateurs ACCEPTED de l'événement.

        Codes d'erreur :
        - `400 cannot_report_draft` : l'événement est en statut DRAFT (non public).
        - `400 cannot_report_cancelled` : l'événement est en statut CANCELLED.
        - `404 not_found` : événement introuvable.
        - `409 already_reported` : l'utilisateur a déjà signalé cet événement (unique
          constraint sur `(reporter_id, event_id)`).
        - `422 cannot_report_own_event` : tentative de signalement de son propre event
          (créateur ou co-organisateur ACCEPTED, cf. cascade SCRUM-136).
      operationId: reportEvent
      tags: [events, reports]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateReportRequest'
      responses:
        '201':
          description: Signalement créé (status PENDING)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Report'
        '400':
          description: Body invalide, ou `cannot_report_draft`, ou `cannot_report_cancelled`
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Événement introuvable, ou profil utilisateur non provisionné
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '409':
          description: Cet utilisateur a déjà signalé cet événement
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '422':
          description: |
            `cannot_report_own_event` — l'utilisateur est créateur ou co-organisateur
            ACCEPTED de l'événement.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'

  /admin/reports:
    get:
      summary: Liste paginée des signalements (admin uniquement)
      description: |
        Retourne la liste des signalements, triée par `createdAt DESC` (tie-breaker `id DESC`).
        Filtre par `status` (défaut `PENDING`). Pagination identique à `/users/me/favorites`.
        Réservé au rôle ADMIN (`@RolesAllowed("ADMIN")` Quarkus Security).
      operationId: listReports
      tags: [admin, reports]
      security:
        - BearerAuth: []
      parameters:
        - name: status
          in: query
          schema:
            $ref: '#/components/schemas/ReportStatus'
          description: Filtre sur le statut. Défaut `PENDING`.
        - name: page
          in: query
          schema:
            type: integer
            default: 0
            minimum: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
            minimum: 1
            maximum: 100
      responses:
        '200':
          description: Liste paginée des signalements (tableau vide si aucun)
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Report'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '403':
          description: Appelant authentifié mais sans rôle ADMIN
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'

  /admin/reports/{id}:
    patch:
      summary: Traiter un signalement (admin uniquement)
      description: |
        Transitionne un signalement de `PENDING` vers `REVIEWED` ou `DISMISSED`. Pose
        `reviewedAt = now()` et `reviewedBy = caller.id`. Optionnellement persiste une
        `moderationNote`. Aucune action implicite n'est déclenchée sur l'événement signalé
        (cf. décision 13 de la spec — la suppression d'un event reste une action manuelle
        séparée via `PATCH /events/{id}/cancel`).

        Codes d'erreur :
        - `404 not_found` : signalement introuvable.
        - `409 invalid_transition` : le signalement n'est plus en statut PENDING (déjà traité).
        - `400 invalid_status` : `status` du body n'est ni REVIEWED ni DISMISSED.
      operationId: handleReport
      tags: [admin, reports]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/HandleReportRequest'
      responses:
        '200':
          description: Signalement traité
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Report'
        '400':
          description: Body invalide (ex. `status=PENDING` ou statut inconnu)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '403':
          description: Appelant authentifié mais sans rôle ADMIN
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Signalement introuvable, ou profil admin non provisionné
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '409':
          description: Le signalement n'est plus en statut PENDING (déjà traité)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.5 — Retirer le TODO `admin` du schéma `UserProfileResponse`

Sur les [lignes 72-74 d'openapi.yaml](openapi/openapi.yaml#L72-L74) :

```yaml
        admin:
          type: boolean
          # TODO: Sprint 6 — champ non encore implémenté dans l'entité User backend
```

→ **Supprimer ces 3 lignes**. Ajouter en remplacement à la fin de la `description` du schéma `UserProfileResponse` :

```yaml
    UserProfileResponse:
      type: object
      description: |
        ...existing description...

        **Note SCRUM-94 (rôle ADMIN).** Le champ `admin` n'est pas exposé sur ce profil.
        Le rôle ADMIN est porté par la claim Auth0
        (`https://quarkus-security.com/roles`) et consommé côté backend via
        `@RolesAllowed("ADMIN")`. Le frontend qui souhaite afficher un badge « Admin »
        doit lire la claim depuis le token Auth0 (via `auth.user['https://quarkus-security.com/roles']`),
        pas depuis ce payload profil.
      properties:
        ...existing fields without `admin`...
```

### 0.6 — Validation OpenAPI

Avant de passer à l'étape 1 :

```bash
npx @redocly/cli lint openapi/openapi.yaml
# OU
yamllint openapi/openapi.yaml
```

Aucune erreur. Vérifier que les `$ref` sur `ReportReason`, `Report`, `CreateReportRequest`, `HandleReportRequest`, `ReportStatus`, `ApiErrorResponse` résolvent.

> **Conséquence sur les frontends consommateurs.** Le frontend SCRUM-96 (modale de signalement) consommera `CreateReportRequest` (et non plus `ReportRequest`). SCRUM-97 (dashboard admin) consommera `HandleReportRequest`. Aucun générateur OpenAPI n'est utilisé dans le projet — le frontend type ses requêtes manuellement, donc le rename est gérable manuellement côté SCRUM-96/97. Mentionner dans les commentaires de PR.

---

## Étape 1 — Migration Flyway V9

**Fichier :** `backend/src/main/resources/db/migration/V9__add_report_reason_and_review_fields.sql`

```sql
-- SCRUM-94 — Enrichissement de la table reports : ajout de l'enum ReportReason,
-- de la traçabilité de modération (reviewed_at, reviewed_by, moderation_note),
-- et renommage de l'ancienne colonne `reason` (TEXT libre) en `description`.

-- 1. Renommer l'ancienne colonne reason (TEXT libre) en description.
ALTER TABLE reports RENAME COLUMN reason TO description;

-- 2. Ajouter la nouvelle colonne reason typée enum (nullable temporairement pour le backfill).
ALTER TABLE reports ADD COLUMN reason VARCHAR(32);

-- 3. Backfill : pour les rows existantes (vides en prod, mais sécurise les envs locaux).
UPDATE reports SET reason = 'OTHER' WHERE reason IS NULL;

-- 4. Passer reason à NOT NULL après backfill.
ALTER TABLE reports ALTER COLUMN reason SET NOT NULL;

-- 5. Ajouter la CHECK constraint sur l'enum ReportReason.
ALTER TABLE reports ADD CONSTRAINT reports_reason_check
    CHECK (reason IN ('SPAM', 'INAPPROPRIATE', 'FAKE', 'OTHER'));

-- 6. Ajouter les colonnes de traçabilité de modération.
ALTER TABLE reports ADD COLUMN moderation_note TEXT NULL;
ALTER TABLE reports ADD COLUMN reviewed_at TIMESTAMP NULL;
ALTER TABLE reports ADD COLUMN reviewed_by UUID NULL;

-- 7. FK reviewed_by → users(id).
ALTER TABLE reports ADD CONSTRAINT fk_reports_reviewed_by
    FOREIGN KEY (reviewed_by) REFERENCES users(id);
```

**Notes** :
- L'ordre `ADD COLUMN reason → UPDATE → ALTER NOT NULL → ADD CHECK` est obligatoire (PostgreSQL refuse `NOT NULL` ou `CHECK` sur une colonne avec rows existantes hors-conformes).
- La FK `fk_reports_reviewed_by` ne pose pas d'index explicite — PostgreSQL crée implicitement un index sur la PK référencée (`users.id`), pas sur la colonne FK. Si une feature future requiert un listing « reports traités par un admin donné », ajouter un `CREATE INDEX idx_reports_reviewed_by ON reports(reviewed_by)` dans une V<N+1> dédiée. Hors scope pour SCRUM-94.
- Pas de `CREATE INDEX` sur `reason` — les requêtes ne filtrent pas dessus dans SCRUM-94 (le dashboard admin filtre par `status`, pas par `reason`).
- Pas de version `OR REPLACE` ni de `IF NOT EXISTS` sur `ADD CONSTRAINT` — Flyway versionne, donc l'idempotence est garantie par le mécanisme de tracking. Si la migration est ré-jouée par accident, Flyway lèvera une erreur de checksum (comportement attendu, alerte précoce).

---

## Étape 2 — Enum `ReportReason`

**Fichier :** `backend/src/main/java/ch/unige/events/entity/ReportReason.java`

```java
package ch.unige.events.entity;

public enum ReportReason {
    SPAM,
    INAPPROPRIATE,
    FAKE,
    OTHER
}
```

---

## Étape 3 — Enrichir l'entité `Report`

**Fichier :** [`backend/src/main/java/ch/unige/events/entity/Report.java`](backend/src/main/java/ch/unige/events/entity/Report.java)

**État cible** :

```java
package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_report_reporter_event", columnNames = {"reporter_id", "event_id"})
        },
        indexes = {
                @Index(name = "idx_report_event", columnList = "event_id"),
                @Index(name = "idx_report_status", columnList = "status")
        }
)
public class Report extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    public Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    public User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public ReportReason reason;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(255)", nullable = false)
    public ReportStatus status = ReportStatus.PENDING;

    @Column(name = "moderation_note", columnDefinition = "TEXT")
    public String moderationNote;

    @Column(name = "reviewed_at")
    public LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    public User reviewedBy;

    @Column(updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
```

**Notes** :
- `reason` est `@NotNull` côté DB (V9) et **non-null** côté Java par convention de service (validation du body via `CreateReportRequest`). Pas d'annotation Bean Validation au niveau entité — la garde se fait au niveau DTO.
- `description` (ex-`reason` String libre) reste `nullable` côté DB. Sémantique : *« texte libre saisi en complément du motif catégoriel »*.
- `moderationNote` est `nullable` — saisi par l'admin uniquement à `PATCH /admin/reports/{id}`.
- `reviewedAt` et `reviewedBy` sont `nullable` — posés au moment de la transition. La cohérence métier (`reviewedAt` non-null ↔ `reviewedBy` non-null ↔ `status != PENDING`) est garantie par le service, **pas** par une CHECK constraint DB (acceptable — invariant trivial à maintenir au service).
- L'unique constraint `uk_report_reporter_event` continue d'être déclarée sur l'entité **et** dans V6. Cohérence Hibernate `validate` : les noms doivent matcher (`uk_report_reporter_event` est le nom posé par V6).
- `status = ReportStatus.PENDING` reste l'initialisation par défaut côté Java (cohérent avec le default V6 SQL `DEFAULT 'PENDING'`).

---

## Étape 4 — DTOs

### 4.1 — `ReportDTO`

**Fichier :** `backend/src/main/java/ch/unige/events/dto/report/ReportDTO.java`

```java
package ch.unige.events.dto.report;

import ch.unige.events.entity.Report;
import ch.unige.events.entity.ReportReason;
import ch.unige.events.entity.ReportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReportDTO(
        Long id,
        Long eventId,
        UUID reporterId,
        ReportReason reason,
        String description,
        ReportStatus status,
        String moderationNote,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        UUID reviewedBy
) {
    public static ReportDTO from(Report r) {
        return new ReportDTO(
                r.id,
                r.event != null ? r.event.id : null,
                r.reporter != null ? r.reporter.id : null,
                r.reason,
                r.description,
                r.status,
                r.moderationNote,
                r.createdAt,
                r.reviewedAt,
                r.reviewedBy != null ? r.reviewedBy.id : null
        );
    }
}
```

### 4.2 — `CreateReportRequest`

**Fichier :** `backend/src/main/java/ch/unige/events/dto/report/CreateReportRequest.java`

```java
package ch.unige.events.dto.report;

import ch.unige.events.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull ReportReason reason,
        @Size(max = 2000) String description
) {}
```

### 4.3 — `HandleReportRequest`

**Fichier :** `backend/src/main/java/ch/unige/events/dto/report/HandleReportRequest.java`

```java
package ch.unige.events.dto.report;

import ch.unige.events.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HandleReportRequest(
        @NotNull ReportStatus status,
        @Size(max = 2000) String moderationNote
) {}
```

> **Note.** La règle métier *« status doit être REVIEWED ou DISMISSED, pas PENDING »* est appliquée **dans le service** (cf. décision 11), pas via une annotation Bean Validation custom. C'est un cas où la simplicité (`if (status != REVIEWED && status != DISMISSED)`) bat l'élégance d'une annotation `@OneOf` custom.

---

## Étape 5 — `ReportService`

**Fichier :** `backend/src/main/java/ch/unige/events/service/ReportService.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.report.CreateReportRequest;
import ch.unige.events.dto.report.HandleReportRequest;
import ch.unige.events.dto.report.ReportDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Report;
import ch.unige.events.entity.ReportStatus;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ReportService {

    @Inject
    EventService eventService;

    @Transactional
    public ReportDTO create(Long eventId, String reporterAuth0Id, CreateReportRequest request) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.status == EventStatus.DRAFT) {
            throw badRequest("cannot_report_draft",
                    "Cannot report an event in DRAFT status.");
        }
        if (event.status == EventStatus.CANCELLED) {
            throw badRequest("cannot_report_cancelled",
                    "Cannot report an event in CANCELLED status.");
        }

        User reporter = User.findByAuth0Id(reporterAuth0Id)
                .orElseThrow(() -> new NotFoundException(
                        "User profile not found — call GET /users/me first"));

        // Cascade SCRUM-136 : créateur OU co-organisateur ACCEPTED ne peut pas signaler
        // son propre event.
        if (eventService.isCreatorOrAcceptedCoOrganizerPublic(event, reporterAuth0Id)) {
            throw unprocessable("cannot_report_own_event",
                    "You cannot report an event you organize.");
        }

        // Doublon : la unique constraint (reporter_id, event_id) bloque déjà au persist,
        // mais on check au préalable pour produire une envelope `error=already_reported` propre.
        if (Report.<Report>find("reporter.id = ?1 and event.id = ?2", reporter.id, eventId).count() > 0) {
            throw conflict("already_reported", "You have already reported this event.");
        }

        Report report = new Report();
        report.event = event;
        report.reporter = reporter;
        report.reason = request.reason();
        report.description = request.description();
        report.status = ReportStatus.PENDING;
        report.persist();

        return ReportDTO.from(report);
    }

    @Transactional
    public List<ReportDTO> listByStatus(ReportStatus status, int page, int size) {
        ReportStatus effective = status != null ? status : ReportStatus.PENDING;
        return Report.<Report>find(
                "status = ?1 order by createdAt desc, id desc", effective)
                .page(page, size)
                .list()
                .stream()
                .map(ReportDTO::from)
                .toList();
    }

    @Transactional
    public ReportDTO handle(Long reportId, String adminAuth0Id, HandleReportRequest request) {
        if (request.status() != ReportStatus.REVIEWED && request.status() != ReportStatus.DISMISSED) {
            throw badRequest("invalid_status",
                    "Only REVIEWED or DISMISSED are accepted as a target status.");
        }

        Report report = Report.<Report>findByIdOptional(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found"));

        if (report.status != ReportStatus.PENDING) {
            throw conflict("invalid_transition",
                    "Report is already in status " + report.status
                            + " — only PENDING reports can be transitioned.");
        }

        User admin = User.findByAuth0Id(adminAuth0Id)
                .orElseThrow(() -> new NotFoundException(
                        "Admin profile not found — call GET /users/me first"));

        report.status = request.status();
        report.moderationNote = request.moderationNote();
        report.reviewedAt = LocalDateTime.now();
        report.reviewedBy = admin;

        return ReportDTO.from(report);
    }

    private static WebApplicationException badRequest(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    private static WebApplicationException conflict(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    private static WebApplicationException unprocessable(String error, String message) {
        return new WebApplicationException(
                Response.status(422)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }
}
```

**Notes** :
- `eventService` est injecté pour accéder au helper public `isCreatorOrAcceptedCoOrganizerPublic` introduit en SCRUM-136. Pas de duplication de logique.
- `listByStatus` retourne directement la `List<ReportDTO>` (pas un wrapper paginé) — aligné sur le pattern `/me/favorites` qui retourne aussi une List nue. Si le frontend SCRUM-97 a besoin du `total`, ajouter un endpoint `GET /admin/reports/count` en follow-up.
- Le helper `unprocessable` utilise `Response.status(422)` (l'enum `Response.Status` n'expose pas explicitement `UNPROCESSABLE_ENTITY` dans toutes les versions JAX-RS — la valeur entière est portable).
- `Report.<Report>findByIdOptional(reportId)` : pattern Panache standard, voir `Event.<Event>findByIdOptional` partout dans le code.

---

## Étape 6 — `ReportResource` (utilisateur, POST `/events/{id}/report`)

**Fichier :** `backend/src/main/java/ch/unige/events/resource/ReportResource.java`

```java
package ch.unige.events.resource;

import ch.unige.events.dto.report.CreateReportRequest;
import ch.unige.events.dto.report.ReportDTO;
import ch.unige.events.service.ReportService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportResource {

    private final ReportService reportService;
    private final SecurityIdentity identity;

    @Inject
    public ReportResource(ReportService reportService, SecurityIdentity identity) {
        this.reportService = reportService;
        this.identity = identity;
    }

    @POST
    @Path("/{id}/report")
    @Authenticated
    public Response report(@PathParam("id") Long eventId,
                           @Valid @NotNull CreateReportRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        ReportDTO created = reportService.create(eventId, auth0Id, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }
}
```

**Notes** :
- Constructor injection (pattern aligné sur [`EventCoOrganizerResource:33-37`](backend/src/main/java/ch/unige/events/resource/EventCoOrganizerResource.java#L33-L37)).
- `@NotNull` sur le body : empêche le 500 sur body absent (cohérent avec le fix de SCRUM-136 sur `POST /co-organizers`).
- Code 201 sur succès, body = `ReportDTO` (nouvelle row).

---

## Étape 7 — `AdminReportResource` (admin, GET + PATCH `/admin/reports`)

**Fichier :** `backend/src/main/java/ch/unige/events/resource/AdminReportResource.java`

```java
package ch.unige.events.resource;

import ch.unige.events.dto.report.HandleReportRequest;
import ch.unige.events.dto.report.ReportDTO;
import ch.unige.events.entity.ReportStatus;
import ch.unige.events.service.ReportService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/admin/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminReportResource {

    private final ReportService reportService;
    private final SecurityIdentity identity;

    @Inject
    public AdminReportResource(ReportService reportService, SecurityIdentity identity) {
        this.reportService = reportService;
        this.identity = identity;
    }

    @GET
    public List<ReportDTO> list(
            @QueryParam("status") ReportStatus status,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        return reportService.listByStatus(status, page, size);
    }

    @PATCH
    @Path("/{id}")
    public ReportDTO handle(@PathParam("id") Long reportId,
                            @Valid @NotNull HandleReportRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        return reportService.handle(reportId, auth0Id, request);
    }
}
```

**Notes** :
- `@RolesAllowed("ADMIN")` au niveau classe : Quarkus Security renvoie automatiquement 403 si la claim `https://quarkus-security.com/roles` ne contient pas `ADMIN`. Si pas authentifié → 401 (Quarkus pré-empte avant l'annotation).
- Le path `/admin/reports` produit (avec le préfixe `/api` configuré dans `application.properties`) `/api/admin/reports`.
- `ReportStatus` en `@QueryParam` est parsé automatiquement par JAX-RS via `Enum.valueOf` ; valeur invalide → 404 par défaut (acceptable, cohérent avec le pattern existant). Si besoin d'un 400 explicite, ajouter un `ParamConverter` — hors scope.

---

## Étape 8 — Tests

Cible : **≥ 80 % de couverture JaCoCo sur les lignes nouvelles**, idéalement 100 % pour les classes Service et Resource (faible complexité). Style aligné sur [`specs_scrum-136.md` étape 7](specs_archives/specs_claude/specs_scrum-136.md#L1418-L1660).

### 8.1 — Mettre à jour `ReportTest`

**Fichier :** [`backend/src/test/java/ch/unige/events/entity/ReportTest.java`](backend/src/test/java/ch/unige/events/entity/ReportTest.java)

Le test existant `fieldsAreAssignable` assigne `report.reason = "Inappropriate content"` (String) — désormais le champ `reason` est un enum. Refactor :

```java
@Test
void fieldsAreAssignable() {
    Report report = new Report();
    report.reason = ReportReason.INAPPROPRIATE;
    report.description = "Inappropriate content in description";
    report.status = ReportStatus.REVIEWED;

    assertEquals(ReportReason.INAPPROPRIATE, report.reason);
    assertEquals("Inappropriate content in description", report.description);
    assertEquals(ReportStatus.REVIEWED, report.status);
}
```

Ajouter aussi :

```java
@Test
void newFieldsAreAssignable() {
    Report report = new Report();
    User reviewer = new User();
    LocalDateTime now = LocalDateTime.now();

    report.moderationNote = "Spam confirmé.";
    report.reviewedAt = now;
    report.reviewedBy = reviewer;

    assertEquals("Spam confirmé.", report.moderationNote);
    assertEquals(now, report.reviewedAt);
    assertSame(reviewer, report.reviewedBy);
}
```

### 8.2 — `ReportDTOTest`

**Fichier :** `backend/src/test/java/ch/unige/events/dto/report/ReportDTOTest.java`

| # | Test | Scénario |
|---|---|---|
| 1 | `from_fullReport_projectsAllFields` | Report avec tous les champs renseignés → DTO complet |
| 2 | `from_pendingReport_reviewedFieldsAreNull` | Status PENDING, reviewedAt/By/moderationNote null → DTO nullable cohérent |
| 3 | `from_nullReporter_reporterIdIsNull` | reporter == null → DTO `reporterId == null` |
| 4 | `from_nullEvent_throwsOrEventIdIsNull` | event == null → comportement défini (raisonnablement, NPE car event est `nullable=false` côté DB ; tester que le DTO renvoie `eventId=null` n'est PAS attendu — la pré-condition est event!=null) |

Test 4 — préciser le comportement : si `event == null` côté entité, c'est une violation d'invariant ; pas de garde dans `from()`. Le test vérifie qu'on ne masque pas l'incohérence : si on veut un comportement gracieux, l'expliciter.

### 8.3 — `ReportServiceMock`

**Fichier :** `backend/src/test/java/ch/unige/events/service/ReportServiceMock.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.report.CreateReportRequest;
import ch.unige.events.dto.report.HandleReportRequest;
import ch.unige.events.dto.report.ReportDTO;
import ch.unige.events.entity.ReportReason;
import ch.unige.events.entity.ReportStatus;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import ch.unige.events.dto.ApiErrorResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mock
@ApplicationScoped
public class ReportServiceMock extends ReportService {

    public static volatile boolean forceNotFoundOnCreate = false;
    public static volatile boolean forceCannotReportDraft = false;
    public static volatile boolean forceCannotReportCancelled = false;
    public static volatile boolean forceCannotReportOwn = false;
    public static volatile boolean forceAlreadyReported = false;
    public static volatile boolean forceNotFoundOnHandle = false;
    public static volatile boolean forceInvalidTransition = false;
    public static volatile boolean forceInvalidStatus = false;

    public final List<ReportDTO> reportsFixture = new ArrayList<>();

    public void reset() {
        forceNotFoundOnCreate = false;
        forceCannotReportDraft = false;
        forceCannotReportCancelled = false;
        forceCannotReportOwn = false;
        forceAlreadyReported = false;
        forceNotFoundOnHandle = false;
        forceInvalidTransition = false;
        forceInvalidStatus = false;
        reportsFixture.clear();
    }

    @Override
    public ReportDTO create(Long eventId, String reporterAuth0Id, CreateReportRequest request) {
        if (forceNotFoundOnCreate) throw new NotFoundException();
        if (forceCannotReportDraft) throw err(400, "cannot_report_draft", "draft");
        if (forceCannotReportCancelled) throw err(400, "cannot_report_cancelled", "cancelled");
        if (forceCannotReportOwn) throw err(422, "cannot_report_own_event", "own");
        if (forceAlreadyReported) throw err(409, "already_reported", "dup");
        return new ReportDTO(1L, eventId, UUID.randomUUID(),
                request.reason() != null ? request.reason() : ReportReason.OTHER,
                request.description(),
                ReportStatus.PENDING, null, LocalDateTime.now(), null, null);
    }

    @Override
    public List<ReportDTO> listByStatus(ReportStatus status, int page, int size) {
        return List.copyOf(reportsFixture);
    }

    @Override
    public ReportDTO handle(Long reportId, String adminAuth0Id, HandleReportRequest request) {
        if (forceNotFoundOnHandle) throw new NotFoundException();
        if (forceInvalidTransition) throw err(409, "invalid_transition", "tx");
        if (forceInvalidStatus) throw err(400, "invalid_status", "stat");
        return new ReportDTO(reportId, 1L, UUID.randomUUID(),
                ReportReason.SPAM, null, request.status(),
                request.moderationNote(), LocalDateTime.now(), LocalDateTime.now(), UUID.randomUUID());
    }

    private static WebApplicationException err(int status, String error, String message) {
        return new WebApplicationException(
                Response.status(status)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }
}
```

### 8.4 — `ReportResourceTest` (`@QuarkusTest`)

**Fichier :** `backend/src/test/java/ch/unige/events/resource/ReportResourceTest.java`

| # | Test | Endpoint | Auth | Setup mock | HTTP attendu |
|---|---|---|---|---|---|
| 1 | `report_validBody_returns201` | `POST /events/1/report` | `auth0\|alice` | — | 201, body `status=PENDING`, `reason=SPAM` |
| 2 | `report_eventNotFound_returns404` | `POST /events/999/report` | `auth0\|alice` | `forceNotFoundOnCreate` | 404 |
| 3 | `report_eventDraft_returns400_cannotReportDraft` | `POST /events/1/report` | `auth0\|alice` | `forceCannotReportDraft` | 400, `error=cannot_report_draft` |
| 4 | `report_eventCancelled_returns400_cannotReportCancelled` | `POST /events/1/report` | `auth0\|alice` | `forceCannotReportCancelled` | 400, `error=cannot_report_cancelled` |
| 5 | `report_ownEvent_returns422_cannotReportOwn` | `POST /events/1/report` | `auth0\|alice` | `forceCannotReportOwn` | 422, `error=cannot_report_own_event` |
| 6 | `report_alreadyReported_returns409` | `POST /events/1/report` | `auth0\|bob` | `forceAlreadyReported` | 409, `error=already_reported` |
| 7 | `report_unauthenticated_returns401` | `POST /events/1/report` | (none) | — | 401 |
| 8 | `report_missingReason_returns400` | `POST /events/1/report`, body `{}` | `auth0\|alice` | — | 400 (Bean Validation `@NotNull`) |
| 9 | `report_invalidReason_returns400` | `POST /events/1/report`, body `{"reason":"FOO"}` | `auth0\|alice` | — | 400 (parse enum) |
| 10 | `report_descriptionTooLong_returns400` | `POST /events/1/report`, body `{"reason":"SPAM","description":"A".repeat(2001)}` | `auth0\|alice` | — | 400 (`@Size(max=2000)`) |
| 11 | `report_validWithoutDescription_returns201` | `POST /events/1/report`, body `{"reason":"SPAM"}` | `auth0\|alice` | — | 201 |
| 12 | `report_emptyBody_returns400` | `POST /events/1/report` sans body | `auth0\|alice` | — | 400 (`@NotNull` body) |

Exemple représentatif :

```java
@Test
@TestSecurity(user = "auth0|alice")
void report_validBody_returns201() {
    given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"SPAM\",\"description\":\"Faux event\"}")
            .when().post("/events/{id}/report", 1L)
            .then()
            .statusCode(201)
            .body("status", equalTo("PENDING"))
            .body("reason", equalTo("SPAM"))
            .body("description", equalTo("Faux event"));
}

@Test
@TestSecurity(user = "auth0|alice")
void report_ownEvent_returns422_cannotReportOwn() {
    ReportServiceMock.forceCannotReportOwn = true;
    given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"SPAM\"}")
            .when().post("/events/{id}/report", 1L)
            .then()
            .statusCode(422)
            .body("error", equalTo("cannot_report_own_event"));
}
```

### 8.5 — `AdminReportResourceTest` (`@QuarkusTest`)

**Fichier :** `backend/src/test/java/ch/unige/events/resource/AdminReportResourceTest.java`

| # | Test | Endpoint | Auth (rôles) | Setup | HTTP attendu |
|---|---|---|---|---|---|
| 1 | `list_admin_returns200` | `GET /admin/reports` | `auth0\|admin` + role ADMIN | seed 2 fixtures | 200, taille 2 |
| 2 | `list_defaultStatusIsPending` | `GET /admin/reports` (sans param) | admin | — | 200 (mock retourne quoi qu'il en soit, mais on vérifie qu'aucun param erroné n'est rejeté) |
| 3 | `list_filterStatus_dismissed` | `GET /admin/reports?status=DISMISSED` | admin | — | 200 |
| 4 | `list_invalidStatus_returns404` | `GET /admin/reports?status=FOO` | admin | — | 404 (parse enum JAX-RS default) |
| 5 | `list_pagination` | `GET /admin/reports?page=1&size=5` | admin | — | 200 |
| 6 | `list_sizeOver100_returns400` | `GET /admin/reports?size=101` | admin | — | 400 (`@Max(100)`) |
| 7 | `list_negativePage_returns400` | `GET /admin/reports?page=-1` | admin | — | 400 (`@Min(0)`) |
| 8 | `list_unauthenticated_returns401` | `GET /admin/reports` | (none) | — | 401 |
| 9 | `list_authenticatedNotAdmin_returns403` | `GET /admin/reports` | `auth0\|alice` (sans role) | — | 403 |
| 10 | `handle_validReviewed_returns200` | `PATCH /admin/reports/1` | admin | — | 200, body `status=REVIEWED` |
| 11 | `handle_validDismissed_returns200` | `PATCH /admin/reports/1` | admin | — | 200, body `status=DISMISSED` |
| 12 | `handle_pendingStatus_returns400_invalidStatus` | `PATCH /admin/reports/1` body `{"status":"PENDING"}` | admin | `forceInvalidStatus` | 400, `error=invalid_status` |
| 13 | `handle_reportNotFound_returns404` | `PATCH /admin/reports/999` | admin | `forceNotFoundOnHandle` | 404 |
| 14 | `handle_alreadyHandled_returns409_invalidTransition` | `PATCH /admin/reports/1` | admin | `forceInvalidTransition` | 409, `error=invalid_transition` |
| 15 | `handle_unauthenticated_returns401` | `PATCH /admin/reports/1` | (none) | — | 401 |
| 16 | `handle_authenticatedNotAdmin_returns403` | `PATCH /admin/reports/1` | `auth0\|alice` | — | 403 |
| 17 | `handle_emptyBody_returns400` | `PATCH /admin/reports/1` sans body | admin | — | 400 |
| 18 | `handle_moderationNoteTooLong_returns400` | `PATCH /admin/reports/1` body `{"status":"REVIEWED","moderationNote":"A".repeat(2001)}` | admin | — | 400 |
| 19 | `handle_returnsReviewedAtAndBy` | `PATCH /admin/reports/1` body `{"status":"DISMISSED","moderationNote":"OK"}` | admin | — | 200, body `reviewedAt != null`, `reviewedBy != null`, `moderationNote=="OK"` |

Exemple représentatif :

```java
@Test
@TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
void list_admin_returns200() {
    AdminReportFixture seed = ...; // helper qui pousse 2 ReportDTO dans ReportServiceMock.reportsFixture
    given()
            .when().get("/admin/reports")
            .then()
            .statusCode(200)
            .body("size()", equalTo(2));
}

@Test
@TestSecurity(user = "auth0|alice")  // pas de roles → pas ADMIN
void list_authenticatedNotAdmin_returns403() {
    given()
            .when().get("/admin/reports")
            .then()
            .statusCode(403);
}

@Test
@TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
void handle_alreadyHandled_returns409_invalidTransition() {
    ReportServiceMock.forceInvalidTransition = true;
    given()
            .contentType(ContentType.JSON)
            .body("{\"status\":\"REVIEWED\"}")
            .when().patch("/admin/reports/{id}", 1L)
            .then()
            .statusCode(409)
            .body("error", equalTo("invalid_transition"));
}
```

### 8.6 — `ReportServiceCoverageTest` (DevServices PostgreSQL)

**Fichier :** `backend/src/test/java/ch/unige/events/service/ReportServiceCoverageTest.java`

Tests d'intégration directe sur DevServices (transactionnels, rollback automatique entre tests via `@TestTransaction`) :

| # | Test | Scénario |
|---|---|---|
| 1 | `create_validBody_persistsRow` | Seed user A + event PUBLISHED par user B ; A appelle create ; verify row in DB avec `reason=SPAM`, `status=PENDING`, `description="..."`, `reportedAt != null` |
| 2 | `create_eventDraft_throwsBadRequest` | Event DRAFT ; create lève `WebApplicationException` 400 + `error=cannot_report_draft` |
| 3 | `create_eventCancelled_throwsBadRequest` | Event CANCELLED ; idem `cannot_report_cancelled` |
| 4 | `create_ownEventByCreator_throws422` | A crée l'event ; A tente de signaler ; lève 422 + `error=cannot_report_own_event` |
| 5 | `create_ownEventByAcceptedCoOrganizer_throws422` | B est co-org ACCEPTED ; B tente de signaler ; lève 422 (cf. cascade SCRUM-136) |
| 6 | `create_byPendingCoOrganizer_works` | B est co-org PENDING (pas ACCEPTED) ; B peut signaler (cohérent avec « le PENDING n'a pas les permissions ») — **sentinel cascade** |
| 7 | `create_alreadyReported_throwsConflict` | Pré-seed une row ; rappel `create` lève 409 + `error=already_reported` |
| 8 | `create_eventNotFound_throws404` | eventId inexistant ; `NotFoundException` |
| 9 | `create_userNotProvisioned_throws404` | auth0Id inexistant en DB ; `NotFoundException` |
| 10 | `listByStatus_pending_returnsOnlyPending` | Seed 2 PENDING + 1 REVIEWED ; `listByStatus(PENDING, 0, 20)` → taille 2 |
| 11 | `listByStatus_dismissed_returnsOnlyDismissed` | Seed 1 DISMISSED ; `listByStatus(DISMISSED, ...)` → taille 1 |
| 12 | `listByStatus_nullStatus_defaultsToPending` | Seed 2 PENDING + 1 REVIEWED ; `listByStatus(null, ...)` → taille 2 (PENDING) |
| 13 | `listByStatus_orderingByCreatedAtDesc` | Seed 3 reports avec dates différentes ; vérifier l'ordre du résultat |
| 14 | `listByStatus_pagination` | Seed 5 ; `listByStatus(PENDING, 1, 2)` → taille 2 (page 1, size 2) |
| 15 | `handle_pendingToReviewed_persists` | Seed PENDING ; `handle(id, adminAuth0, {status: REVIEWED, moderationNote: "ok"})` ; lecture DB → `status=REVIEWED`, `reviewedAt != null`, `reviewedBy = admin.id`, `moderationNote = "ok"` |
| 16 | `handle_pendingToDismissed_persists` | Idem avec DISMISSED |
| 17 | `handle_alreadyReviewed_throwsConflict` | Seed REVIEWED ; `handle` → `WebApplicationException` 409 + `error=invalid_transition` |
| 18 | `handle_invalidTargetStatus_pending_throwsBadRequest` | `handle(..., {status: PENDING})` → 400 + `error=invalid_status` |
| 19 | `handle_reportNotFound_throws404` | reportId inexistant ; `NotFoundException` |
| 20 | `handle_adminNotProvisioned_throws404` | auth0Id admin inexistant en base ; `NotFoundException` |
| 21 | `handle_setsReviewedAtClose` | Capture `now` avant ; `handle` ; vérifier `reviewedAt` est entre `now` et `now()+1s` |
| 22 | `create_descriptionPersisted_correctly` | `description = "long text"` ; verify la chaîne survit en DB sans troncature |

> **Sentinel cascade** : test `create_byPendingCoOrganizer_works` est obligatoire — il vérifie que `isCreatorOrAcceptedCoOrganizerPublic` retourne `false` pour un PENDING, pas `true` (régression possible si on confond `isAcceptedFor` avec un `isInvolvedInEvent` plus large).

### 8.7 — Tests de pagination/edge cases

Le test `list_pagination` dans `AdminReportResourceTest` (Resource) couvre la surface API. Les tests intégrés dans `ReportServiceCoverageTest` couvrent la sémantique réelle du tri et du `LIMIT`/`OFFSET`. Pas de test redondant nécessaire.

### 8.8 — Vérifier que `ModerationCleanupServiceTest` reste vert

**Fichier :** [`backend/src/test/java/ch/unige/events/service/ModerationCleanupServiceTest.java`](backend/src/test/java/ch/unige/events/service/ModerationCleanupServiceTest.java)
**Fichier :** [`backend/src/test/java/ch/unige/events/service/ModerationCleanupCoverageTest.java`](backend/src/test/java/ch/unige/events/service/ModerationCleanupCoverageTest.java)

Aucun changement attendu — le job lit `r.event` et `r.status` uniquement (cf. analyse de l'existant). **Tester explicitement** dans la PR :

```bash
./mvnw test -Dtest=ModerationCleanupServiceTest,ModerationCleanupCoverageTest
```

Doit rester vert. Si un de ces tests crée des rows `Report` avec `reason = "..."` (String) en seed, **mettre à jour** pour utiliser `reason = ReportReason.OTHER` (cf. invariant V9 où `reason` devient NOT NULL).

> **À vérifier au moment de l'implémentation.** Lire les deux fichiers et identifier les seeds qui créent des `Report` à la main. Si présents, les mettre à jour pour respecter le nouveau schéma.

---

## Étape 9 — Documentation

### 9.1 — `backend/docs/data-model.md`

**Section `Report`** ([lignes 226-241](backend/docs/data-model.md#L226-L241)) — remplacer entièrement par :

```markdown
### Report

Table : `reports` (créée par la migration `V6__create_reports.sql` en SCRUM-103,
enrichie par la migration `V9__add_report_reason_and_review_fields.sql` en SCRUM-94).

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `event` | — | `Event` | `event_id` | `@ManyToOne(LAZY)`, `@JoinColumn(nullable=false)` — FK vers `events.id` |
| `reporter` | — | `User` | `reporter_id` | `@ManyToOne(LAZY)`, nullable — FK vers `users.id` |
| `reason` | `reason` | `ReportReason` | `reason` | `@Enumerated(STRING)`, not null, `length=32`, CHECK constraint — SCRUM-94 |
| `description` | `description` | `String` | `description` | nullable, `@Column(columnDefinition="TEXT")` — texte libre saisi en complément du motif catégoriel. Renommé depuis `reason` (TEXT libre) en SCRUM-94. |
| `status` | `status` | `ReportStatus` | `status` | `@Enumerated(STRING)`, not null, défaut `PENDING` |
| `moderationNote` | `moderationNote` | `String` | `moderation_note` | nullable, `@Column(columnDefinition="TEXT")` — note saisie par l'admin au moment du PATCH |
| `reviewedAt` | `reviewedAt` | `LocalDateTime` | `reviewed_at` | nullable — posé par `ReportService.handle()` au moment de la transition |
| `reviewedBy` | — | `User` | `reviewed_by` | `@ManyToOne(LAZY)`, nullable — FK vers `users.id` |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Index DB : `idx_report_event` (event_id), `idx_report_status` (status).

Contrainte unique : `uk_report_reporter_event` sur `(reporter_id, event_id)` — empêche
le double signalement et sert de filet de sécurité au check applicatif `409 already_reported`.

CHECK constraints :
- `reports_status_check` (posée par V7) : `status IN ('PENDING', 'REVIEWED', 'DISMISSED')`.
- `reports_reason_check` (posée par V9) : `reason IN ('SPAM', 'INAPPROPRIATE', 'FAKE', 'OTHER')`.

#### Sémantique des champs

- **`reason`** : motif catégoriel choisi par l'utilisateur dans la modale frontend
  (SCRUM-96). Enum `ReportReason` : `SPAM`, `INAPPROPRIATE`, `FAKE`, `OTHER`. Obligatoire.
- **`description`** : texte libre optionnel (max 2000 chars). Vit **à côté** de `reason`.
- **`reviewedAt`** + **`reviewedBy`** : posés ensemble par `ReportService.handle()` au
  moment où l'admin transitionne le report (`PENDING → REVIEWED|DISMISSED`). L'invariant
  *« reviewedAt non-null ↔ reviewedBy non-null ↔ status != PENDING »* est garanti côté service,
  pas par une CHECK DB.
- **`moderationNote`** : note libre saisie par l'admin au moment du PATCH (max 2000 chars).

#### Consommation par `ModerationCleanupService`

Le job [`ModerationCleanupService`](backend/src/main/java/ch/unige/events/service/ModerationCleanupService.java)
(cf. SCRUM-103) compte les rows `Report` avec `status = PENDING` groupées par event. Il
lit uniquement `r.event` et `r.status` — **insensible** aux ajouts de SCRUM-94.

#### Consommation par `ReportService`

- `ReportService.create(eventId, auth0Id, CreateReportRequest)` — vérifie l'existence
  de l'event, son statut PUBLISHED, l'absence de self-report (cascade SCRUM-136),
  l'absence de doublon ; persiste avec status PENDING.
- `ReportService.listByStatus(status, page, size)` — listing paginé pour le dashboard
  admin (SCRUM-97).
- `ReportService.handle(reportId, adminAuth0Id, HandleReportRequest)` — transition
  `PENDING → REVIEWED|DISMISSED` + audit (`reviewedAt`, `reviewedBy`, `moderationNote`).
```

**Section « Énumérations »** — ajouter une ligne au tableau (après `ReportStatus`) :

```markdown
| `ReportReason` | `SPAM`, `INAPPROPRIATE`, `FAKE`, `OTHER` | Sprint 7 | ✅ Implémenté (SCRUM-94 — CHECK constraint posée par V9) |
```

> **Note V10 future.** Toute modification de `ReportReason` (ajout/rename) **devra**
> passer par une migration `V<N+1>__update_report_reason_check.sql` qui drop+recrée
> la contrainte avec les nouvelles valeurs. Cohérent avec la stratégie Flyway documentée
> ci-dessus.

### 9.2 — `backend/docs/api-contract.md`

Lignes 269-271 actuelles :

```markdown
| `POST` | `/events/{id}/report` | Sprint 6 | Signaler un événement |
| `GET` | `/admin/reports` | Sprint 6 | Liste des signalements (admin) |
| `PUT` | `/admin/reports/{id}` | Sprint 6 | Modérer un signalement (admin) |
```

→ Remplacer par :

```markdown
| `POST` | `/events/{id}/report` | `@Authenticated` | Signaler un événement (créé en PENDING) — SCRUM-94 | 201, 400, 401, 404, 409, 422 |
| `GET` | `/admin/reports` | `@RolesAllowed("ADMIN")` | Liste paginée des signalements (default `status=PENDING`) — SCRUM-94 | 200, 401, 403 |
| `PATCH` | `/admin/reports/{id}` | `@RolesAllowed("ADMIN")` | Traiter un signalement (`PENDING → REVIEWED\|DISMISSED`) — SCRUM-94 | 200, 400, 401, 403, 404, 409 |
```

(Notes : `PUT` → `PATCH`, status `Sprint 6` → l'auth column + ticket SCRUM-94, ajout des codes d'erreur.)

Ajouter aussi une section dédiée plus bas (après la section sur les co-organisateurs SCRUM-136) avec le détail de chaque endpoint, codes d'erreur, et la mention de la cascade `isCreatorOrAcceptedCoOrganizer` (qui interdit le self-report d'un event où l'on est créateur OU co-org ACCEPTED).

### 9.3 — `backend/docs/sprint-context.md`

Ajouter dans la section Sprint 7 (entre les entrées SCRUM-136 et l'entrée SCRUM-164) :

```markdown
- [x] **SCRUM-94** — Modération : enrichissement de l'entité `Report` avec
      l'enum `ReportReason` (SPAM/INAPPROPRIATE/FAKE/OTHER), `description` (renommée
      depuis l'ancienne colonne `reason` libre), `moderationNote`, `reviewedAt`,
      `reviewedBy`. Migration `V9__add_report_reason_and_review_fields.sql`.
      3 endpoints : `POST /api/events/{id}/report` (`@Authenticated`),
      `GET /api/admin/reports` et `PATCH /api/admin/reports/{id}` (`@RolesAllowed("ADMIN")`).
      Pas de champ `admin: boolean` sur `User` — rôle géré exclusivement via la claim
      Auth0 (`identity.hasRole("ADMIN")` + `@RolesAllowed`). Le TODO `admin` est retiré
      d'`UserProfileResponse` dans openapi.yaml. La cascade SCRUM-136
      (`isCreatorOrAcceptedCoOrganizerPublic`) interdit le self-report d'un event où
      l'on est créateur ou co-organisateur ACCEPTED (422 `cannot_report_own_event`).
      `ModerationCleanupService` (SCRUM-103) reste insensible — il ne lit que
      `r.event` et `r.status`. Frontend SCRUM-96 (modale) et SCRUM-97 (dashboard admin)
      dépendants.
```

### 9.4 — `backend/AGENTS.md`

Section *« Champ `admin` sur User »* (lignes 64-65 actuelles) :

```markdown
### Champ `admin` sur User
Le champ `admin` (boolean) est **planifié Sprint 6** et n'existe pas encore dans l'entité. Le frontend l'attend déjà dans le contrat API — l'ajouter à l'entité et à `UserProfileResponse` au Sprint 6 (sans préfixe `is`).
```

→ Remplacer par :

```markdown
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
```

### 9.5 — `openapi/openapi.yaml`

Déjà couvert par l'étape 0 (rappel : modifier en premier, source de vérité monorepo).

---

## Edge cases à traiter explicitement

| Cas | Comportement attendu | Couvert par |
|---|---|---|
| Signaler un event PUBLISHED par un user non-créateur, non-co-org | 201 PENDING | `report_validBody_returns201` |
| Signaler un event PUBLISHED par son propre créateur | 422 `cannot_report_own_event` | `report_ownEvent_returns422_cannotReportOwn` + `create_ownEventByCreator_throws422` |
| Signaler un event PUBLISHED par un co-organisateur ACCEPTED | 422 `cannot_report_own_event` | `create_ownEventByAcceptedCoOrganizer_throws422` |
| Signaler un event PUBLISHED par un co-organisateur PENDING | 201 PENDING (PENDING n'a pas les permissions) — **sentinel cascade** | `create_byPendingCoOrganizer_works` |
| Signaler un event DRAFT | 400 `cannot_report_draft` | `report_eventDraft_returns400_cannotReportDraft` |
| Signaler un event CANCELLED | 400 `cannot_report_cancelled` | `report_eventCancelled_returns400_cannotReportCancelled` |
| Signaler un event inexistant | 404 | `report_eventNotFound_returns404` |
| Signaler deux fois le même event par le même reporter | 409 `already_reported` | `report_alreadyReported_returns409` |
| Race condition : deux POST simultanés du même reporter sur le même event | Le 1er passe, le 2e échoue avec `PersistenceException` mappée en 409 (cf. décision 15) | À surveiller, pas de test dédié (concurrence rare) |
| User non provisionné (auth0Id sans User en base) appelle POST | 404 | `create_userNotProvisioned_throws404` |
| Body `{}` (reason absent) | 400 (Bean Validation `@NotNull`) | `report_missingReason_returns400` |
| Body `{"reason":"FOO"}` (enum invalide) | 400 (parse error Jackson) | `report_invalidReason_returns400` |
| Body `description` > 2000 chars | 400 (`@Size(max=2000)`) | `report_descriptionTooLong_returns400` |
| GET /admin/reports sans rôle ADMIN | 403 (Quarkus Security via `@RolesAllowed`) | `list_authenticatedNotAdmin_returns403` |
| GET /admin/reports anonyme | 401 | `list_unauthenticated_returns401` |
| GET /admin/reports?status=FOO | 404 (parse enum default JAX-RS) | `list_invalidStatus_returns404` |
| GET /admin/reports sans `status` | Default `PENDING` | `list_defaultStatusIsPending` |
| GET /admin/reports?size=101 | 400 (`@Max(100)`) | `list_sizeOver100_returns400` |
| GET /admin/reports?page=-1 | 400 (`@Min(0)`) | `list_negativePage_returns400` |
| PATCH /admin/reports/{id} avec status PENDING | 400 `invalid_status` | `handle_pendingStatus_returns400_invalidStatus` |
| PATCH /admin/reports/{id} sur un report déjà REVIEWED | 409 `invalid_transition` | `handle_alreadyHandled_returns409_invalidTransition` |
| PATCH /admin/reports/{id} sur reportId inexistant | 404 | `handle_reportNotFound_returns404` |
| PATCH /admin/reports/{id} sans rôle ADMIN | 403 | `handle_authenticatedNotAdmin_returns403` |
| PATCH /admin/reports/{id} sans body | 400 (`@NotNull` body) | `handle_emptyBody_returns400` |
| PATCH /admin/reports/{id} avec moderationNote > 2000 | 400 (`@Size(max=2000)`) | `handle_moderationNoteTooLong_returns400` |
| PATCH /admin/reports/{id} succès → vérifier reviewedAt/By posés | `reviewedAt != null` ET `reviewedBy = admin.id` | `handle_returnsReviewedAtAndBy` + `handle_setsReviewedAtClose` |
| Reporter dont le compte est ensuite supprimé | `Report.reporter = null` (FK nullable) — la row reste, le DTO renvoie `reporterId: null` | Pas de test (suppression de compte hors scope) |
| Admin non provisionné en DB (auth0Id avec rôle ADMIN mais sans User) | 404 sur PATCH (helper `User.findByAuth0Id` lève) | `handle_adminNotProvisioned_throws404` |
| `ModerationCleanupServiceTest` continue de passer après la modif | Les tests existants restent verts (le job ne lit que event + status) | Run ciblé `./mvnw test -Dtest=ModerationCleanup*` |

---

## Critères d'acceptation (repris du ticket Jira SCRUM-94)

D'après le backlog [`backlog_s5_s10.md` lignes 649-671](backend/docs/backlog_s5_s10.md#L649-L671) :

- [ ] **Enum `ReportReason`** (SPAM, INAPPROPRIATE, FAKE, OTHER) → étape 2.
- [ ] **Entité `Report` étendue** (PanacheEntity) avec `reason` enum, `description`, `reviewedAt`, `reviewedBy`, `moderationNote` → étape 3 (réutilise l'entité existante de SCRUM-103).
- [ ] **Enum `ReportStatus`** (PENDING / REVIEWED / DISMISSED) → déjà existant, à NE PAS recréer.
- [ ] **Migration Flyway V9** (rename + add columns + CHECK + FK + backfill) → étape 1. **Divergence assumée vs. ticket Jira** qui dit *« Schéma géré par Hibernate (mode update) »* — Hibernate est désormais en `validate` (cf. SCRUM-164), Flyway est obligatoire.
- [ ] **Endpoint `POST /api/events/{id}/report`** (utilisateur connecté, ne peut pas signaler ses propres events ni ceux où il est co-org ACCEPTED) → étape 6.
- [ ] **Endpoint `GET /api/admin/reports`** (`@RolesAllowed("ADMIN")`, paginé, filtre `?status=`) → étape 7.
- [ ] **Endpoint `PATCH /api/admin/reports/{id}`** (transition vers REVIEWED ou DISMISSED + moderationNote) — **divergence assumée vs. ticket** qui dit `PATCH` mais l'OpenAPI ébauché disait `PUT`. La spec retient `PATCH` (sémantique d'update partiel).
- [ ] **Protection des endpoints admin via `@RolesAllowed("ADMIN")`** Quarkus Security → étapes 6+7.
- [ ] **`ReportService` + `ReportResource` + `AdminReportResource` + `ReportDTO`** → étapes 4+5+6+7.
- [ ] **Tests `@QuarkusTest`** : signalement, 403 si non-admin sur routes admin → étape 8.
- [ ] **Pas d'ajout de champ `admin: boolean` sur `User`** — décision SCRUM-94 (rôle Auth0 only, cf. décision 4). Divergence assumée vs. ticket qui dit *« ajout du rôle ADMIN dans l'entité User si pas déjà présent »*.

---

## Conventions du projet à respecter

- **Règle d'or `openapi-first`** : `openapi/openapi.yaml` modifié EN PREMIER (étape 0) avant toute ligne Java.
- **camelCase partout** dans le code Java, les noms JSON, les schémas OpenAPI. Hibernate convertit en `snake_case` côté DB via `CamelCaseToUnderscoresNamingStrategy`.
- **Pas de préfixe `is`** sur les booléens d'entité — non applicable ici (aucun champ booléen).
- **Hibernate `validate` mode** — toute modif de schéma passe par Flyway V9 (jamais de mutation de V6/V7/V8).
- **Architecture en couches stricte** — Resource ne touche pas l'entité directement. Logique métier dans `ReportService` uniquement.
- **Constructor injection** sur les nouvelles Resources (pattern `EventCoOrganizerResource`).
- **Doc mise à jour dans le même commit** que le code correspondant (règle [`AGENTS.md`](backend/AGENTS.md#L98)).
- **Commits atomiques** : `feat(scrum-94): ...`, `test(scrum-94): ...`, `docs(scrum-94): ...`. Combinables si le diff est petit.
- **SonarCloud** : ≥ 80 % couverture sur les lignes nouvelles, ≤ 3 % duplication, ratings A.
- **Préfixe Jira obligatoire** dans le titre de commit pour `feat`/`refactor`/`perf` (validé par [`.github/workflows/pr-title-check.yml`](.github/workflows/pr-title-check.yml)).

---

## Interdits stricts

- ❌ **Ne PAS** muter les migrations V6, V7, V8 — toute évolution passe par V9.
- ❌ **Ne PAS** supprimer la contrainte unique `uk_report_reporter_event` — elle est notre filet de sécurité 409.
- ❌ **Ne PAS** ajouter un champ `admin: boolean` sur l'entité `User` (décision 4 — rôle Auth0 only).
- ❌ **Ne PAS** étendre `ModerationCleanupService` ni `ModerationCleanupJob` — out of scope (le job continue de fonctionner sans modif).
- ❌ **Ne PAS** auto-cancel un event quand son report passe en REVIEWED (décision 13).
- ❌ **Ne PAS** envoyer d'email, créer une entité `Notification`, publier un Quarkus event (décision 14 — out of scope).
- ❌ **Ne PAS** créer un endpoint bulk-handle `PATCH /admin/reports?ids=…` (décision 17).
- ❌ **Ne PAS** introduire de TODO commenté dans le code.
- ❌ **Ne PAS** logger l'`auth0Id`, le `reporter.email`, ou la `description` en clair en INFO — DEBUG si besoin (privacy).
- ❌ **Ne PAS** modifier le frontend dans cette PR (SCRUM-96 / SCRUM-97 dans des PR séparées).
- ❌ **Ne PAS** introduire un wrapper `PagedReports` dans la signature `GET /admin/reports` — retourner une `List<ReportDTO>` nue (cohérent avec `/me/favorites`).
- ❌ **Ne PAS** persister `ReportStatus.PENDING` lors d'un PATCH (rejet via `invalid_status` 400).
- ❌ **Ne PAS** muter `Report.reporter` ou `Report.event` lors d'un PATCH (immuable post-création).
- ❌ **Ne PAS** étendre `Event` avec un champ `reportCount` ou `reportedAt` — le compte se calcule par requête à la demande (`ModerationCleanupService` le fait déjà).
- ❌ **Ne PAS** introduire un nouvel exception mapper — les helpers `badRequest`/`conflict`/`unprocessable` produisent l'envelope directement.
- ❌ **Ne PAS** casser un test existant — `ReportTest`, `ModerationCleanupServiceTest`, `ModerationCleanupCoverageTest` doivent rester verts (modifier les seeds si besoin pour respecter le nouveau schéma).
- ❌ **Ne PAS** ajouter un index dédié `idx_reports_reviewed_by` ou `idx_reports_reason` — pas de hot-path filtrant dessus dans SCRUM-94.
- ❌ **Ne PAS** snake_case côté Java.

---

## Résumé des fichiers touchés

| Fichier | Action |
|---|---|
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | Modifier — reprendre 3 paths existants, ajouter 2 schémas (`ReportReason`, `HandleReportRequest`), enrichir `Report`, renommer `ReportRequest` en `CreateReportRequest`, retirer le TODO `admin` du `UserProfileResponse` |
| `backend/src/main/resources/db/migration/V9__add_report_reason_and_review_fields.sql` | **Créer** — migration Flyway (rename + add cols + CHECK + FK + backfill) |
| `backend/src/main/java/ch/unige/events/entity/ReportReason.java` | **Créer** |
| [`backend/src/main/java/ch/unige/events/entity/Report.java`](backend/src/main/java/ch/unige/events/entity/Report.java) | Modifier — ajout `reason` (enum), `description` (renommé de l'ancien `reason` String), `moderationNote`, `reviewedAt`, `reviewedBy` |
| `backend/src/main/java/ch/unige/events/dto/report/ReportDTO.java` | **Créer** |
| `backend/src/main/java/ch/unige/events/dto/report/CreateReportRequest.java` | **Créer** |
| `backend/src/main/java/ch/unige/events/dto/report/HandleReportRequest.java` | **Créer** |
| `backend/src/main/java/ch/unige/events/service/ReportService.java` | **Créer** |
| `backend/src/main/java/ch/unige/events/resource/ReportResource.java` | **Créer** |
| `backend/src/main/java/ch/unige/events/resource/AdminReportResource.java` | **Créer** |
| [`backend/src/test/java/ch/unige/events/entity/ReportTest.java`](backend/src/test/java/ch/unige/events/entity/ReportTest.java) | Modifier — refactor `fieldsAreAssignable` + ajout `newFieldsAreAssignable` |
| `backend/src/test/java/ch/unige/events/dto/report/ReportDTOTest.java` | **Créer** — 4 tests factory |
| `backend/src/test/java/ch/unige/events/service/ReportServiceMock.java` | **Créer** — mock |
| `backend/src/test/java/ch/unige/events/resource/ReportResourceTest.java` | **Créer** — 12 tests `@QuarkusTest` |
| `backend/src/test/java/ch/unige/events/resource/AdminReportResourceTest.java` | **Créer** — 19 tests `@QuarkusTest` |
| `backend/src/test/java/ch/unige/events/service/ReportServiceCoverageTest.java` | **Créer** — 22 tests intégration DevServices |
| [`backend/src/test/java/ch/unige/events/service/ModerationCleanupServiceTest.java`](backend/src/test/java/ch/unige/events/service/ModerationCleanupServiceTest.java) | Vérifier (potentiellement modifier les seeds pour respecter le nouveau schéma `reason` enum NOT NULL) |
| [`backend/src/test/java/ch/unige/events/service/ModerationCleanupCoverageTest.java`](backend/src/test/java/ch/unige/events/service/ModerationCleanupCoverageTest.java) | Vérifier (idem) |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Modifier — section `Report` étendue + `ReportReason` au tableau Énumérations |
| [`backend/docs/api-contract.md`](backend/docs/api-contract.md) | Modifier — 3 endpoints finalisés (status, codes d'erreur, auth) |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Modifier — entrée Sprint 7 SCRUM-94 |
| [`backend/AGENTS.md`](backend/AGENTS.md) | Modifier — section `Champ admin sur User` → `Rôle ADMIN — claim Auth0, pas de champ DB` |

**Total :** 12 fichiers créés + 9 modifiés (dont 4 docs + AGENTS.md). **1 fichier de migration SQL** (V9), **0 fichier frontend**.

---

## Branche et PR

### Branche

`feature/s6-report-moderation`, basée sur `origin/main` :

```bash
git fetch origin
git checkout -b feature/s6-report-moderation origin/main --no-track
```

⚠️ **`--no-track` est OBLIGATOIRE** (cf. décision 1 de la spec et précédent ISSUE-92). Sans ce flag, la branche traque `origin/main` et `git push` envoie les commits directement sur main. Le `-u` viendra au premier push :

```bash
git push -u origin feature/s6-report-moderation
```

### PR

- **Base :** `main`.
- **Titre :** `feat(scrum-94): add report moderation entity, endpoints and admin routes`
  - `feat` impose un scope `scrum-94` en minuscules — validé par [`.github/workflows/pr-title-check.yml`](.github/workflows/pr-title-check.yml).
- **Description** (calquée sur [`.github/pull_request_template.md`](.github/pull_request_template.md)) — voir ci-dessous, **À FOURNIR PAR L'AGENT EN FIN D'IMPLÉMENTATION** (cf. section « Livrable final attendu »).

### Commits atomiques suggérés

- `feat(scrum-94): add ReportReason enum and Flyway V9 migration`
- `feat(scrum-94): extend Report entity with reason, description, review fields`
- `feat(scrum-94): add ReportService and DTOs for report moderation flow`
- `feat(scrum-94): add ReportResource and AdminReportResource endpoints`
- `test(scrum-94): cover entity, DTOs, service, and resources for moderation`
- `docs(scrum-94): document Report enrichment, endpoints and admin role policy`

Combinables si le diff total reste sous ~600 lignes — à juger.

---

## Checklist Sonar / qualité

- [ ] Coverage ≥ 80 % sur les lignes nouvelles (JaCoCo). Cible attendue : ≥ 95 % sur `ReportService`, `ReportResource`, `AdminReportResource` (faible complexité cyclomatique).
- [ ] Duplication < 3 % sur le code nouveau (les helpers `badRequest`/`conflict`/`unprocessable` sont dupliqués depuis `EventCoOrganizerService` — si SonarCloud flag, refactorer en utility class partagée).
- [ ] **Security Rating : A.** Aucun input utilisateur ne touche du SQL natif. Validation `@NotNull`/`@Size` sur le body, `@Min`/`@Max`/`@Positive` sur les query params, `@PathParam Long` parsé par JAX-RS. `@RolesAllowed("ADMIN")` sur `AdminReportResource` (gating par Quarkus Security). Pas d'XSS — les strings sont stockés/retournés tels quels (le frontend doit échapper).
- [ ] Reliability Rating : A.
- [ ] Maintainability Rating : A.
- [ ] Security Review Rating : A.

---

## Checklist finale

### Avant push

- [ ] `./mvnw verify` vert localement.
- [ ] Rapport JaCoCo `backend/target/jacoco-report/` — lignes nouvelles ≥ 80 %, idéalement 100 % sur Service et Resources.
- [ ] Les **8 tests sentinels** verts nommément (run ciblé) :
  - `report_validBody_returns201`
  - `report_ownEvent_returns422_cannotReportOwn` (sentinel décision 19 — cascade SCRUM-136)
  - `report_alreadyReported_returns409` (sentinel décision 15 — unique constraint)
  - `create_byPendingCoOrganizer_works` (sentinel cascade — PENDING n'a pas les permissions)
  - `handle_alreadyHandled_returns409_invalidTransition` (sentinel décision 20)
  - `handle_pendingStatus_returns400_invalidStatus` (sentinel décision 11)
  - `list_authenticatedNotAdmin_returns403` (sentinel `@RolesAllowed("ADMIN")`)
  - `handle_returnsReviewedAtAndBy` (sentinel décision 9 — audit)
- [ ] `ModerationCleanupServiceTest` et `ModerationCleanupCoverageTest` restent verts.
- [ ] `git diff --stat frontend/` vide.
- [ ] `openapi/openapi.yaml` modifié (vérifier `git diff openapi/`).
- [ ] `backend/src/main/resources/db/migration/V9__add_report_reason_and_review_fields.sql` présent et correctement nommé (cf. [`AGENTS.md`](backend/AGENTS.md#L54)).
- [ ] Aucune nouvelle dépendance dans `backend/pom.xml`.
- [ ] Pas de TODO commenté ajouté.
- [ ] Pas de `LOG.info` qui logue auth0Id, email, ou description en clair.
- [ ] Le TODO `admin: boolean` du schéma `UserProfileResponse` est retiré dans `openapi.yaml`.
- [ ] `backend/AGENTS.md` mis à jour (section ADMIN remplace l'ancienne section `admin: boolean`).

### Avant PR

- [ ] Branche `feature/s6-report-moderation` créée avec `--no-track` depuis `origin/main`.
- [ ] `git branch -vv` confirme que la branche track `origin/feature/s6-report-moderation` après le premier push (PAS `origin/main`).
- [ ] Commits atomiques nommés selon la convention (`feat(scrum-94): ...`, `test(scrum-94): ...`, `docs(scrum-94): ...`).
- [ ] Description de PR remplie selon le template, sections optionnelles « Why / Motivation », « Dépendances / ordre de merge », « Décisions techniques tranchées », « Notes pour le reviewer » conservées.
- [ ] Base de la PR : `main`.
- [ ] La check CI `Lint PR title` est verte.

### Avant merge

- [ ] CI verte (`./mvnw verify` côté backend).
- [ ] Review approuvée.
- [ ] SonarCloud quality gate vert.
- [ ] Lien posé dans le ticket Jira SCRUM-94.
- [ ] Les auteurs de SCRUM-96 (FRONT modale) et SCRUM-97 (FRONT dashboard) confirment que les schémas OpenAPI sont consommables. Mentionner explicitement le rename `ReportRequest → CreateReportRequest`.

---

## Livrable final attendu — titre et description de PR

**À la fin de l'implémentation (avant le `git push` final, ou immédiatement après pour permettre l'ouverture de la PR par l'utilisateur), l'agent DOIT retourner dans la réponse :**

1. **Le titre exact de la PR** (format `feat(scrum-94): …`, validé par le workflow `pr-title-check`).
2. **La description complète de la PR**, prête à coller dans GitHub, qui suit strictement le template [`.github/pull_request_template.md`](.github/pull_request_template.md). Sections obligatoires :
   - `## Résumé` (1-3 phrases mentionnant **SCRUM-94** en gras).
   - `## Why / Motivation` (contexte : US-15 + US-T4 + Modération).
   - `## Changements` avec sous-sections `### Backend`, `### Documentation`, et le cas échéant `### Infrastructure`. Bullet lists avec références de fichiers cliquables.
   - `## Tests` (résumé des tests ajoutés/modifiés).
   - `## Test plan` (checklist concrète de validation manuelle).
   - `## Documentation` (checkbox + liste des fichiers de doc modifiés).
   - `## Dépendances / ordre de merge` (mentionner SCRUM-96 et SCRUM-97 qui dépendent de cette PR ; aucune dépendance bloquante en amont).
   - `## Décisions techniques tranchées` (rappeler les 4 plus structurantes : pas de champ `admin: boolean`, Flyway V9 obligatoire vs. ticket Jira obsolète, cascade SCRUM-136 réutilisée pour `cannot_report_own_event`, `PATCH` retenu plutôt que `PUT`).
   - `## Notes pour le reviewer` (souligner les divergences vs. ticket Jira, mentionner le rename `ReportRequest → CreateReportRequest` qui impactera les frontends consommateurs SCRUM-96/97).

L'utilisateur ouvrira lui-même la PR dans GitHub à partir du titre et de la description fournis. **L'agent ne doit PAS appeler `gh pr create`.**

---

## Prompt de lancement d'implémentation

````
Tu vas implémenter SCRUM-94 — entité Report enrichie, endpoints signalement, et routes admin de modération sur le backend Quarkus de UNIGE Events.

## ÉTAPE 0 — Création de la branche (avec --no-track OBLIGATOIRE)

Avant TOUT code :

    git fetch origin
    git checkout -b feature/s6-report-moderation origin/main --no-track

Le flag `--no-track` est CRITIQUE. Sans lui, la branche traque `origin/main` et `git push` envoie les commits sur main (incident historique repris par toutes les specs).

Premier push (à faire dès qu'un commit existe) :

    git push -u origin feature/s6-report-moderation

## Source unique de vérité

`specs_archives/specs_claude/specs_scrum-94.md` — à lire INTÉGRALEMENT avant d'écrire une ligne de code. Toutes les décisions (Flyway V9 obligatoire vs ticket obsolète, pas de champ `admin: boolean` sur User, rename `reason → description` + ajout `reason` enum, cascade SCRUM-136 réutilisée via `isCreatorOrAcceptedCoOrganizerPublic`, PATCH plutôt que PUT, 422 sur self-report, 409 sur transition invalide, pas de bulk-action, pas de notif, pas d'auto-cancel d'event, pas de modif de ModerationCleanupService) y sont tranchées. Tu n'as RIEN à inventer.

## À lire avant de commencer

1. `backend/AGENTS.md` — conventions critiques (camelCase, pas de préfixe is, Flyway obligatoire, openapi-first, constructor injection sur les Resources, seuil Sonar 80%, doc mise à jour dans le même commit, conventions de PR).
2. `backend/docs/architecture.md` — architecture en couches.
3. `backend/docs/data-model.md` — pattern existant des entités (Report section actuelle à étendre).
4. `backend/docs/api-contract.md` — pattern de documentation des endpoints.
5. `backend/docs/sprint-context.md` — section Sprint 7 où une entrée SCRUM-94 sera ajoutée.
6. `openapi/openapi.yaml` — contrat API actuel. Schémas Report/ReportRequest/ReportStatus à reprendre, paths /events/{id}/report, /admin/reports, /admin/reports/{id} à finaliser.
7. Code source à inspecter avant de coder :
   - `backend/src/main/java/ch/unige/events/entity/Report.java` — entité existante (SCRUM-103) à étendre.
   - `backend/src/main/java/ch/unige/events/entity/ReportStatus.java` — enum existant à utiliser tel quel.
   - `backend/src/main/java/ch/unige/events/service/ModerationCleanupService.java` — vérifier qu'il ne lit que r.event et r.status (insensible à la modif).
   - `backend/src/main/resources/db/migration/V6__create_reports.sql` — schéma initial (immutable).
   - `backend/src/main/resources/db/migration/V7__reconcile_check_constraints.sql` — où la `reports_status_check` est posée (ne pas toucher).
   - `backend/src/main/resources/db/migration/V8__create_event_co_organizers.sql` — template de syntaxe pour V9.
   - `backend/src/main/java/ch/unige/events/service/EventService.java` — helper public `isCreatorOrAcceptedCoOrganizerPublic` à réutiliser (introduit en SCRUM-136).
   - `backend/src/main/java/ch/unige/events/resource/EventCoOrganizerResource.java` — pattern constructor DI à copier pour ReportResource et AdminReportResource.
   - `backend/src/main/java/ch/unige/events/service/AttendanceService.java` — pattern `WebApplicationException` 4xx avec ApiErrorResponse (ligne 60-66).
   - `backend/src/main/java/ch/unige/events/dto/ApiErrorResponse.java` — record envelope d'erreur.
   - `backend/src/main/java/ch/unige/events/resource/UserResource.java` — pattern pagination /me/favorites (ligne 264-272) à copier pour /admin/reports.
   - `backend/src/main/resources/application.properties` — vérifier `quarkus.oidc.roles.role-claim-path` (ligne 31) déjà configuré.
   - `backend/src/test/java/ch/unige/events/MockEventFactory.java` — factory d'Event en test.
   - `backend/src/test/java/ch/unige/events/service/AttendanceServiceMock.java` — pattern @Mock @ApplicationScoped extends Service avec static volatile boolean force*.
   - `backend/src/test/java/ch/unige/events/resource/EventCoOrganizerResourceTest.java` — pattern @QuarkusTest + @TestSecurity(user="auth0|alice") + RestAssured.
   - `backend/src/test/java/ch/unige/events/entity/ReportTest.java` — tests existants à mettre à jour (champ reason String → description).
   - `backend/src/test/java/ch/unige/events/service/ModerationCleanupServiceTest.java` + `ModerationCleanupCoverageTest.java` — vérifier qu'ils restent verts ; mettre à jour les seeds si nécessaire (reason enum NOT NULL).

## Ordre d'implémentation strict

1. **`openapi/openapi.yaml` EN PREMIER** (étape 0 de la spec) :
   - Ajouter le schéma `ReportReason` (enum 4 valeurs).
   - Reprendre le schéma `Report` (champs id, eventId, reporterId nullable, reason enum, description nullable, status, moderationNote, createdAt, reviewedAt nullable, reviewedBy nullable).
   - Renommer `ReportRequest` en `CreateReportRequest` (reason @NotNull ReportReason, description optional max 2000).
   - Ajouter `HandleReportRequest` (status @NotNull ReportStatus, moderationNote optional max 2000).
   - Reprendre les 3 paths : POST /events/{id}/report (201/400/401/404/409/422), GET /admin/reports (200/401/403, paginé, default status=PENDING), PATCH /admin/reports/{id} (200/400/401/403/404/409). Note: l'ancien path PUT devient PATCH.
   - Retirer le TODO `admin: boolean` du schéma `UserProfileResponse` + ajouter une note explicative dans la description du schéma sur la gestion via claim Auth0.
   - Vérifier la validité YAML.

2. **Migration Flyway V9** (étape 1) :
   - Créer `backend/src/main/resources/db/migration/V9__add_report_reason_and_review_fields.sql` : RENAME column `reason` → `description`, ADD COLUMN `reason VARCHAR(32)` (nullable temporairement), UPDATE backfill `reason='OTHER'`, ALTER NOT NULL, ADD CHECK reports_reason_check, ADD COLUMN `moderation_note TEXT NULL`, ADD COLUMN `reviewed_at TIMESTAMP NULL`, ADD COLUMN `reviewed_by UUID NULL` + FK fk_reports_reviewed_by → users(id). Cf. SQL exact en étape 1 de la spec.

3. **Enum ReportReason** (étape 2) :
   - `backend/src/main/java/ch/unige/events/entity/ReportReason.java` — SPAM/INAPPROPRIATE/FAKE/OTHER.

4. **Entité Report enrichie** (étape 3) :
   - Étendre `backend/src/main/java/ch/unige/events/entity/Report.java` avec `reason` (enum @NotNull), `description` (TEXT, nullable, ex-reason), `moderationNote` (TEXT, nullable), `reviewedAt` (LocalDateTime, nullable), `reviewedBy` (@ManyToOne LAZY User, nullable).
   - Conserver intacts : event @ManyToOne, reporter @ManyToOne, status, createdAt, @PrePersist, contraintes uniqueConstraints/indexes.

5. **DTOs** (étape 4) sous `backend/src/main/java/ch/unige/events/dto/report/` :
   - `ReportDTO.java` (record + factory from(Report) — projette UUID nus pour reporterId/reviewedBy).
   - `CreateReportRequest.java` (record + @NotNull ReportReason reason + @Size(max=2000) String description).
   - `HandleReportRequest.java` (record + @NotNull ReportStatus status + @Size(max=2000) String moderationNote).

6. **Service** (étape 5) :
   - `ReportService.java` @ApplicationScoped + @Transactional sur toutes les mutations.
   - Méthodes : create (avec garde DRAFT/CANCELLED → 400, isCreatorOrAcceptedCoOrganizerPublic → 422 cannot_report_own_event, doublon → 409 already_reported, persist), listByStatus (paginé, default PENDING, tri createdAt DESC), handle (vérifie status target ∈ {REVIEWED, DISMISSED} sinon 400, vérifie report.status == PENDING sinon 409 invalid_transition, pose reviewedAt + reviewedBy + moderationNote).
   - Helpers privés-static badRequest, conflict, unprocessable.
   - Inject EventService pour réutiliser isCreatorOrAcceptedCoOrganizerPublic (pas de duplication de logique).

7. **Resources** (étapes 6+7) :
   - `ReportResource.java` constructor DI, @Path("/events"), POST /{id}/report, @Authenticated, @Valid @NotNull CreateReportRequest, code 201.
   - `AdminReportResource.java` constructor DI, @Path("/admin/reports"), @RolesAllowed("ADMIN") au niveau classe, GET (paginé status/page/size), PATCH /{id} (@Valid @NotNull HandleReportRequest, retourne ReportDTO 200).

8. **Tests** (étape 8) — cible ≥ 80 % couverture, idéalement 100 % sur Service et Resources :
   - Mettre à jour `ReportTest.java` (refactor `fieldsAreAssignable`, ajouter `newFieldsAreAssignable`).
   - `ReportDTOTest.java` (4 tests).
   - `ReportServiceMock.java` (pattern AttendanceServiceMock + static volatile boolean force* + reset() + fixtures).
   - `ReportResourceTest.java` (12 tests `@QuarkusTest`).
   - `AdminReportResourceTest.java` (19 tests, dont 2 testant @RolesAllowed("ADMIN") via @TestSecurity(roles=...)).
   - `ReportServiceCoverageTest.java` (22 tests intégration DevServices, dont sentinel `create_byPendingCoOrganizer_works`).
   - Vérifier que `ModerationCleanupServiceTest` et `ModerationCleanupCoverageTest` restent verts (ajuster les seeds si besoin pour respecter `reason` NOT NULL).

9. **`./mvnw verify`** — DOIT être vert avant de toucher la doc. Corriger toute régression.

10. **Documentation** (étape 9 — même commit que le code correspondant ou commit `docs(scrum-94):` séparé) :
    - `backend/docs/data-model.md` — section Report étendue (nouveaux champs, sémantique description vs reason, CHECK constraints, consommation par ReportService et ModerationCleanupService) + ReportReason au tableau Énumérations + note V10 future.
    - `backend/docs/api-contract.md` — 3 endpoints finalisés (status, codes d'erreur, auth, ticket SCRUM-94).
    - `backend/docs/sprint-context.md` — entrée SCRUM-94 dans la section Sprint 7.
    - `backend/AGENTS.md` — section `Champ admin sur User` (lignes 64-65) → `Rôle ADMIN — claim Auth0, pas de champ DB`.
    - `openapi/openapi.yaml` est le seul fichier OpenAPI (déjà fait étape 1).

11. **Vérifications finales avant push** :
    - `git diff --stat frontend/` vide.
    - `git diff --stat openapi/` non-vide.
    - `git diff --stat backend/src/main/resources/db/migration/` non-vide (V9 ajoutée).
    - Pas de nouvelle dépendance Maven.
    - `./mvnw verify` vert.
    - JaCoCo report inspecté : couverture lignes nouvelles ≥ 80 %, idéalement 100 % sur Service et Resources.
    - Les 8 tests sentinels passent : report_validBody_returns201, report_ownEvent_returns422_cannotReportOwn, report_alreadyReported_returns409, create_byPendingCoOrganizer_works, handle_alreadyHandled_returns409_invalidTransition, handle_pendingStatus_returns400_invalidStatus, list_authenticatedNotAdmin_returns403, handle_returnsReviewedAtAndBy.
    - ModerationCleanupServiceTest + ModerationCleanupCoverageTest verts.

## Commits atomiques suggérés

- `feat(scrum-94): add ReportReason enum and Flyway V9 migration`
- `feat(scrum-94): extend Report entity with reason, description, review fields`
- `feat(scrum-94): add ReportService and DTOs for report moderation flow`
- `feat(scrum-94): add ReportResource and AdminReportResource endpoints`
- `test(scrum-94): cover entity, DTOs, service, and resources for moderation`
- `docs(scrum-94): document Report enrichment, endpoints and admin role policy`

Combinables si le diff total reste sous ~600 lignes — à juger.

## Push final

    git push  # branche déjà tracker grâce au `git push -u` initial

## Livrable FINAL attendu (à fournir à l'utilisateur dans la réponse)

**OBLIGATOIRE — sans ces deux blocs, la tâche n'est PAS terminée :**

1. **Titre EXACT de la PR** (format validé par pr-title-check) :

   `feat(scrum-94): add report moderation entity, endpoints and admin routes`

2. **Description COMPLÈTE de la PR**, prête à coller dans le textarea GitHub, qui suit strictement le template `.github/pull_request_template.md`. Sections à remplir :
   - `## Résumé` (1-3 phrases, mentionne **SCRUM-94** en gras).
   - `## Why / Motivation` (US-15 signalement + US-T4 modération admin, débloquer SCRUM-96/97 front).
   - `## Changements` avec `### Backend`, `### Infrastructure` (migration V9), `### Documentation`. Bullet lists avec [filename](path) cliquables.
   - `## Tests` (résumé des tests ajoutés/modifiés, sentinels nommés).
   - `## Test plan` (checklist concrète, ex. `./mvnw verify`, smoke manuel POST report comme student / GET admin/reports avec et sans rôle ADMIN, vérifier que ModerationCleanupJob ne casse pas).
   - `## Documentation` (checkbox `[x] Documentation mise à jour ou non applicable` + liste des fichiers doc modifiés).
   - `## Dépendances / ordre de merge` (aucune dépendance amont ; SCRUM-96 et SCRUM-97 dépendent de cette PR — mentionner explicitement le rename `ReportRequest → CreateReportRequest` qui impacte les types frontend).
   - `## Décisions techniques tranchées` (au minimum les 4 décisions structurantes : pas de champ `admin: boolean` sur User, Flyway V9 obligatoire vs ticket Jira obsolète, cascade SCRUM-136 réutilisée pour 422 cannot_report_own_event, PATCH retenu plutôt que PUT).
   - `## Notes pour le reviewer` (divergences vs ticket Jira, attention au rename de schéma OpenAPI).

L'utilisateur ouvrira lui-même la PR dans GitHub à partir du titre + description que tu fournis. **Ne PAS appeler `gh pr create` toi-même.**

## Interdits stricts

- PAS de modification des migrations V6, V7, V8 (immutables).
- PAS d'ajout de champ `admin: boolean` sur User (rôle Auth0 only).
- PAS d'auto-cancel d'event au passage d'un report en REVIEWED.
- PAS de bulk-handle endpoint.
- PAS de notification email/push.
- PAS de modification de ModerationCleanupService ni ModerationCleanupJob.
- PAS de modification du frontend.
- PAS de wrapper PagedReports — retourner List<ReportDTO> nue.
- PAS de persister status PENDING au PATCH (rejet 400 invalid_status).
- PAS de muter Report.reporter ou Report.event au PATCH.
- PAS d'extension de Event avec un champ reportCount.
- PAS de TODO commenté.
- PAS de logging d'auth0Id, email, ou description en clair en INFO.
- PAS de cassure de tests existants (ReportTest, ModerationCleanup*Test).
- PAS d'ajout d'index dédié sur reason ou reviewed_by dans V9.
- PAS de snake_case côté Java.

## Conventions à respecter

- camelCase partout en Java, JSON, OpenAPI. Hibernate convertit en snake_case côté DB.
- Pas de préfixe `is` sur les booléens (n/a — aucun booléen).
- Constructor injection sur ReportResource et AdminReportResource.
- @Transactional sur toutes les mutations Service.
- @Authenticated sur ReportResource (par méthode), @RolesAllowed("ADMIN") sur AdminReportResource (par classe).
- @PathParam Long pour eventId/reportId.
- Pagination identique à /me/favorites : @DefaultValue("0") @Min(0) page, @DefaultValue("20") @Positive @Max(100) size.
- Codes d'erreur custom dans le champ `error` de l'envelope ApiErrorResponse : `cannot_report_draft`, `cannot_report_cancelled`, `cannot_report_own_event`, `already_reported`, `invalid_status`, `invalid_transition`. 4xx standard pour les autres.
- Couverture JaCoCo ≥ 80 % sur les lignes nouvelles, duplication < 3 %, ratings A.
- Doc mise à jour dans le même commit que le code correspondant.
- Commits atomiques nommés `feat(scrum-94): ...`, `test(scrum-94): ...`, `docs(scrum-94): ...`.
- Titre PR EXACT : `feat(scrum-94): add report moderation entity, endpoints and admin routes`.

## Critères de done

- [ ] `./mvnw verify` vert localement et en CI.
- [ ] JaCoCo ≥ 80 % sur les lignes nouvelles ; idéalement 100 % sur Service et Resources.
- [ ] Les 8 tests sentinels verts nommément :
  - `report_validBody_returns201`
  - `report_ownEvent_returns422_cannotReportOwn` (cascade SCRUM-136 + 422)
  - `report_alreadyReported_returns409` (unique constraint)
  - `create_byPendingCoOrganizer_works` (sentinel cascade — PENDING ne donne pas les permissions)
  - `handle_alreadyHandled_returns409_invalidTransition`
  - `handle_pendingStatus_returns400_invalidStatus`
  - `list_authenticatedNotAdmin_returns403` (sentinel @RolesAllowed)
  - `handle_returnsReviewedAtAndBy` (audit reviewedAt/reviewedBy)
- [ ] `ModerationCleanupServiceTest` et `ModerationCleanupCoverageTest` verts.
- [ ] `git diff --stat frontend/` vide.
- [ ] `openapi/openapi.yaml` modifié EN PREMIER et cohérent avec le code.
- [ ] `V9__add_report_reason_and_review_fields.sql` présent et bien nommé.
- [ ] `backend/docs/data-model.md`, `backend/docs/api-contract.md`, `backend/docs/sprint-context.md`, `backend/AGENTS.md` mis à jour.
- [ ] Commits atomiques bien nommés.
- [ ] `git branch -vv` confirme que la branche track `origin/feature/s6-report-moderation`.
- [ ] La check CI `Lint PR title` est verte.
- [ ] SonarCloud Quality Gate vert.
- [ ] **Titre + description complète de PR fournis dans la réponse finale**, prêts à coller dans GitHub.
````
