# SCRUM-139 — Entité `Comment` + endpoints CRUD commentaires événements

| Champ | Valeur |
|---|---|
| Ticket Jira | [SCRUM-139](https://pinfo-groupe6.atlassian.net/browse/SCRUM-139) (5 SP) |
| Sprint | S6 (calendrier produit) — préfixe Jira `[BACK][S8]` (artefact historique de re-planning, cf. décision 1) |
| Épic | [SCRUM-16](https://pinfo-groupe6.atlassian.net/browse/SCRUM-16) — Interactions communautaires |
| Story | [SCRUM-111](https://pinfo-groupe6.atlassian.net/browse/SCRUM-111) (US-22 — *« En tant qu'utilisateur connecté, je veux poster et lire des commentaires sur les événements, afin d'interagir avec la communauté autour d'un événement. »*) |
| Story Points | 5 |
| Branche | `feature/s6-comments` (cohérent avec SCRUM-94 / SCRUM-136 / SCRUM-138 — cf. décision 1) |
| Base | `origin/main` (tip à la date de rédaction : `4ba0a9f fix(scrum-138): apply Copilot review — race-case 409, list privacy, comment fix` sur `feature/s6-follow`, soit le commit `113f621 Merge pull request #152 …` en pointe de `main`) |
| Auteur spec | Elie Bussod |
| Date | 2026-05-08 |
| PR de référence | feat(scrum-139): add Comment entity and event comments CRUD endpoints |
| Frontend lié (consommateur aval) | [SCRUM-146](https://pinfo-groupe6.atlassian.net/browse/SCRUM-146) — `CommentSection.tsx` + `CommentItem.tsx` + `CommentForm.tsx` dans `EventDetailPage` (Sprint S7, Daniel). Hors scope SCRUM-139. Le contrat OpenAPI livré ici est figé pour ce ticket. |
| Likes & signalement de commentaires | [SCRUM-144](https://pinfo-groupe6.atlassian.net/browse/SCRUM-144) (entité `CommentLike`, endpoints `/comments/{id}/like`, extension `Report.commentId`, `POST /comments/{id}/report`) — Sprint S7. **Hors scope** SCRUM-139 (cf. décision 23). Les champs `likeCount: int = 0` et `likedByMe: boolean` (toujours `false` ici) sont exposés dès maintenant pour figer le contrat consommé par SCRUM-146. |
| Notifications mention `@displayName` + `NEW_COMMENT` | [SCRUM-145](https://pinfo-groupe6.atlassian.net/browse/SCRUM-145) — Sprint S7+ (dépend de SCRUM-99 — infra Notification). **Hors scope** SCRUM-139. |
| Dépendances amont | Aucune. |
| Règle d'or `openapi-first` | **APPLICABLE — 3 nouveaux paths (`POST /events/{id}/comments`, `GET /events/{id}/comments`, `DELETE /comments/{id}`) + 2 schémas neufs (`CommentDTO`, `CreateCommentRequest`) + nouveau tag `comments`.** Modifier [`openapi/openapi.yaml`](openapi/openapi.yaml) AVANT toute ligne de code Java. Voir [`backend/AGENTS.md` lignes 77-80](backend/AGENTS.md#L77-L80). |

> **Note d'implémentation (2026-05-08).** La décision 2 ci-dessous parle de
> `V15__create_comments.sql`, sous l'hypothèse que la PR SCRUM-138 (V14 follows) avait
> déjà mergé. Au moment du `checkout -b feature/s6-comments origin/main --no-track`, le
> dernier migrant sur `origin/main` était en réalité `V13` (la PR #154 SCRUM-138 est
> toujours ouverte). Le pré-check de la décision 2 (« basculer en V16 si conflit ») a
> donc été appliqué **en sens inverse** : la migration livrée s'appelle
> `V14__create_comments.sql`. Si SCRUM-138 merge avant cette PR, un commit
> `fix(scrum-139): rebase V14 → V15` sera nécessaire au moment du rebase. Le commit
> `feat(scrum-139): add V14 migration for comments table` documente ce choix dans son
> message.
>
> **Leçon Flyway-immutabilité (post-Copilot review #1, 2026-05-08).** La review Copilot
> a fait apparaître que la FK `fk_comments_parent` créée par V14 dans sa version
> initiale (RESTRICT par défaut) empêchait le DELETE physique d'un parent qui porte
> des replies. Le premier réflexe a été de **modifier V14 en place** pour ajouter
> `ON DELETE SET NULL` — légitime sur le papier puisque la PR n'est pas mergée. **C'est
> une erreur** : l'environnement de déploiement preview (`Deploy / Deploy to Preview`)
> a une **DB PostgreSQL persistante** par namespace, qui avait déjà appliqué V14 lors
> du premier déploiement réussi (commit `5848630`). Tout `helm upgrade` ultérieur
> faisait échouer Flyway avec « checksum mismatch for migration version 14 » et
> bloquait le startup de l'API. La règle [`backend/AGENTS.md`](backend/AGENTS.md)
> (« Une migration committée est immutable ») couvre exactement ce cas — y compris
> pour les PRs ouvertes dont le preview deploy a une DB persistante. **Fix correct
> appliqué (commit `e77d3b7`)** : V14 restaurée à sa forme originale (FK RESTRICT) ; un
> nouveau migrant `V15__alter_comments_parent_fk_set_null.sql` fait `DROP CONSTRAINT
> fk_comments_parent` puis `ADD CONSTRAINT … ON DELETE SET NULL`. Cette leçon impacte
> aussi la spec SCRUM-147 : son numéro de migration de départ est désormais `V16` (et
> non `V15`), parce que SCRUM-139 livrera deux migrations au merge (V14_create + V15_alter).

---

## Contexte

### Le besoin produit (US-22)

> *« En tant qu'utilisateur connecté, je veux poster et lire des commentaires sur les événements, afin d'interagir avec la communauté autour d'un événement. »* — US-22 (SCRUM-111)

L'épic SCRUM-16 (« Interactions communautaires ») vise à transformer les pages détail d'événement en surfaces de discussion : un participant qui a une question (« est-ce qu'on peut amener un +1 ? »), un retour (« super conf l'an passé, viendrai pour celle-ci »), ou un échange entre attendees doit pouvoir poster un commentaire sous l'événement et obtenir des réponses. SCRUM-139 livre **le socle backend de la fonctionnalité commentaire** : entité, endpoints, DTOs et OpenAPI ; le rendu UI (`CommentSection.tsx`, `CommentItem.tsx`, `CommentForm.tsx` dans `EventDetailPage`) est livré séparément en S7 par SCRUM-146.

### Ce qui manque aujourd'hui

| Pièce manquante | Conséquence |
|---|---|
| Aucune entité représentant un commentaire sur un événement | Impossible de stocker un commentaire en base |
| Aucun endpoint `POST /api/events/{id}/comments` | Le frontend (SCRUM-146) ne peut pas afficher un formulaire de saisie fonctionnel |
| Aucun endpoint `GET /api/events/{id}/comments` | La section commentaires de `EventDetailPage` ne peut pas être rendue |
| Aucun endpoint `DELETE /api/comments/{id}` | L'auteur d'un commentaire ne peut pas le retirer ; un organisateur ne peut pas modérer |
| Aucun mécanisme de réponse à un commentaire (`parentCommentId`) | Les threads (1 niveau d'imbrication) — exigés par SCRUM-146 — ne peuvent pas être posés |
| Aucun schéma OpenAPI `Comment*` | Le contrat consommé par SCRUM-146 (S7) n'est pas figé — bloque le typage TanStack Query côté front |

### Ce qui existe déjà à RÉUTILISER tel quel (ne pas recréer)

| Élément | Fichier / ligne | Rôle dans SCRUM-139 |
|---|---|---|
| Entité `User` (PanacheEntityBase) avec `id: UUID`, `auth0Id`, `displayName`, `avatarUrl` | [`User.java:13-39`](backend/src/main/java/ch/unige/events/entity/User.java#L13-L39) | Source du `@ManyToOne author`, projeté dans `CommentDTO.authorId` / `authorDisplayName` / `authorAvatarUrl` |
| Entité `Event` (PanacheEntity) avec `creator: User`, `status: EventStatus` | [`Event.java:21-58`](backend/src/main/java/ch/unige/events/entity/Event.java#L21-L58) | Source du `@ManyToOne event` ; le statut conditionne la visibilité (cf. décision 14) |
| Enum `EventStatus` | [`EventStatus.java`](backend/src/main/java/ch/unige/events/entity/EventStatus.java) | Valeurs `DRAFT`, `PUBLISHED`, `CANCELLED`, `EXPIRED`, `BANNED` — règles d'autorisation des `POST /comments` (cf. décision 15) |
| Pattern entité PanacheEntity Long PK avec `@ManyToOne(LAZY)` + `@JoinColumn` + `@PrePersist createdAt` | [`Report.java:19-56`](backend/src/main/java/ch/unige/events/entity/Report.java#L19-L56) | **Modèle direct** pour `Comment` (3 relations LAZY au lieu de 2) |
| Pattern migration Flyway « table avec FK + indexes + sequence increment 50 » | [`V6__create_reports.sql:1-17`](backend/src/main/resources/db/migration/V6__create_reports.sql#L1-L17) | **Modèle direct** pour `V15__create_comments.sql` |
| `EventService.getById(Long, String, boolean)` avec garde anti-oracle (DRAFT/CANCELLED/BANNED → 404) | [`EventService.java:152-181`](backend/src/main/java/ch/unige/events/service/EventService.java#L152-L181) | Garde **systématique** au début de `CommentService.post` et `getByEvent` (cf. décision 14) |
| `EventService.isCreatorOrAcceptedCoOrganizerPublic(Event, String auth0Id)` | [`EventService.java:436-438`](backend/src/main/java/ch/unige/events/service/EventService.java#L436-L438) | Cascade SCRUM-136 — réutilisée pour calculer `authorIsOrganizer` (DTO) et autoriser `DELETE /comments/{id}` (cf. décisions 16, 18) |
| Helpers d'erreur `badRequest`/`conflict`/`unprocessable` (WebApplicationException + ApiErrorResponse) | [`ReportService.java:141-163`](backend/src/main/java/ch/unige/events/service/ReportService.java#L141-L163) | Pattern à dupliquer dans `CommentService` |
| `ApiErrorResponse` record | [`ApiErrorResponse.java`](backend/src/main/java/ch/unige/events/dto/ApiErrorResponse.java) | Envelope d'erreur standard |
| Pattern `@Authenticated` + `identity.getPrincipal().getName()` | [`ReportResource.java:19-41`](backend/src/main/java/ch/unige/events/resource/ReportResource.java#L19-L41) | Pattern d'auth standard |
| Annotation `@PerUserRateLimit(name=…, max=…)` | [`PerUserRateLimit.java:30-43`](backend/src/main/java/ch/unige/events/config/PerUserRateLimit.java#L30-L43) | Annotation à apposer sur `POST /api/events/{id}/comments` (cf. décision 22) |
| Pagination `@DefaultValue("0") @Min(0) page` + `@DefaultValue("20") @Positive @Max(100) size` | [`UserResource.java:293-294`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L293-L294) | Pattern à dupliquer pour `GET /api/events/{id}/comments` |
| Split en deux Resources pour deux racines `@Path` distinctes | [`FollowResource.java:42`](backend/src/main/java/ch/unige/events/resource/FollowResource.java#L42) (`/users`) + [`FollowRequestResource.java:24`](backend/src/main/java/ch/unige/events/resource/FollowRequestResource.java#L24) (`/follow-requests`) | **Modèle direct** pour `CommentResource` (`/events`) + `CommentDirectResource` (`/comments`) — cf. décision 25 |
| Mappers d'exception standards | [`backend/src/main/java/ch/unige/events/exception/mapper/`](backend/src/main/java/ch/unige/events/exception/mapper/) | `NotFoundExceptionMapper`, `ConflictExceptionMapper`, `ForbiddenExceptionMapper`, `UnauthorizedExceptionMapper` — à réutiliser |
| Pattern test `@Mock @ApplicationScoped extends Service` + `volatile boolean force*` + `reset()` | [`ReportServiceMock.java:17-72`](backend/src/test/java/ch/unige/events/service/ReportServiceMock.java#L17-L72) | **Modèle direct** pour `CommentServiceMock` |
| Pattern `@QuarkusTest` + `@TestProfile(ShareServiceCoverageProfile.class)` + `@TestTransaction` + helpers `persistUser`/`persistEvent` | [`ReportServiceCoverageTest.java:29-63`](backend/src/test/java/ch/unige/events/service/ReportServiceCoverageTest.java#L29-L63) | Pour `CommentServiceCoverageTest` |
| Liste d'exclusion `quarkus.arc.exclude-types` du profile coverage | [`ShareServiceCoverageProfile.java:13-31`](backend/src/test/java/ch/unige/events/service/ShareServiceCoverageProfile.java#L13-L31) | **À mettre à jour** : ajouter `ch.unige.events.service.CommentServiceMock` (cf. décision 27) |
| Pattern test Resource `@QuarkusTest` + `@TestSecurity(user="auth0\|alice")` + RestAssured Hamcrest | [`ReportResourceTest.java:1-65`](backend/src/test/java/ch/unige/events/resource/ReportResourceTest.java#L1-L65) | Pour `CommentResourceTest` et `CommentDirectResourceTest` |
| Préfixe API `quarkus.http.root-path=api` | [`application.properties:1`](backend/src/main/resources/application.properties#L1) | Les `@Path` JAX-RS sont relatifs (`/events`, `/comments`) ; Quarkus apposera `/api` |
| Anti-oracle ISSUE-93 sur profil utilisateur | [`UserService.java:81-91`](backend/src/main/java/ch/unige/events/service/UserService.java#L81-L91) | Pattern d'anti-oracle à respecter (mêmes envelopes 404 entre « inexistant » et « interdit ») |

### Pourquoi maintenant

- **Sprint S6 — sprint courant**, ticket assigné à Elie sur le board Jira (initialement Antoine, réassigné), statut « En cours ». Aucune dépendance amont.
- **Aucune dépendance amont** : pas de migration ouverte, pas de refactor en vol qui toucherait `User`, `Event`, ou les conventions OpenAPI.
- **Débloque immédiatement deux tickets** :
  - SCRUM-146 (S7) — section commentaires dans `EventDetailPage`. Le contrat figé ici lui sert directement de schéma TanStack Query (`queryKey: ['events', id, 'comments']`).
  - SCRUM-144 (S7) — likes / report-comment. L'entité `Comment` doit exister pour qu'`CommentLike.commentId` puisse y faire référence FK ; et `CreateReportRequest.commentId` (extension prévue) référencera la même PK.
- **Anticipation** : exposer `likeCount: int = 0` et `likedByMe: boolean = false` dès maintenant dans le contrat (entité + DTO) — figés au format produit qu'auront les endpoints SCRUM-144. Ainsi SCRUM-146 (S7, front) ne réécrit pas son DTO TypeScript quand SCRUM-144 mergera dans le même sprint.
- **Cohérence avec SCRUM-138** : la PR (mergée le 2026-05-07) a institué le **pattern split en deux Resources avec `@Path` racines disjoints** (`FollowResource` + `FollowRequestResource`) ; SCRUM-139 doit suivre la même architecture pour ses deux racines `/events` et `/comments`.
- La règle anti-oracle 404 (ISSUE-92, mergée) est **déjà en place** sur `GET /events/{id}` — SCRUM-139 la **délègue intégralement** à `EventService.getById(...)` au lieu de la dupliquer dans `CommentService`.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### 1. Branche `feature/s6-comments` — pas `feature/SCRUM-139-comments`

**Décision.** La branche s'appelle `feature/s6-comments`, conformément au nom suggéré dans [`backlog_s5_s10.md` ligne 1135](backend/docs/backlog_s5_s10.md#L1135). Le ticket porte le préfixe `[BACK][S8]` mais le backlog le rattache au sprint S6 (artefact historique : titre frappé en S8 puis re-planifié en S6).

**Justification.** Cohérence intra-projet avec `feature/s6-report-moderation` (SCRUM-94), `feature/s6-co-organizers` (SCRUM-136) et `feature/s6-follow` (SCRUM-138). Toutes les branches « historiquement S6 » du backlog suivent le même préfixe. La règle racine [`AGENTS.md`](AGENTS.md) autorise le format `feature/SCRUM-XX-description` mais les specs récentes du repo retiennent l'alias backlog quand il existe — pour la traçabilité review/merge.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) `feature/s6-comments` | Cohérent avec les 3 dernières branches S6 mergées (SCRUM-94/136/138) ; nom court ; lisible dans `git log` | Décale du format AGENTS.md mais autorisé | ✅ retenu |
| (b) `feature/SCRUM-139-comments` | Strict respect de la convention `feature/SCRUM-XX-...` | Inconsistant avec le sprint en cours ; rendrait la PR isolée du pattern de groupe | ❌ |

### 2. Numérotation Flyway → `V15__create_comments.sql`

**Décision.** Nouveau fichier `backend/src/main/resources/db/migration/V15__create_comments.sql`. Le dernier migrant existant en `main` au moment de la rédaction est [`V14__create_follows.sql`](backend/src/main/resources/db/migration/V14__create_follows.sql) (mergé via la PR SCRUM-138). `V15` est donc libre.

**Justification.** Une migration committée est immutable ([`backend/AGENTS.md` lignes 54-57](backend/AGENTS.md#L54-L57)). **Avant** de coder le SQL : exécuter
```bash
docker exec -w /workspace unige-events-app-1 bash -c "ls backend/src/main/resources/db/migration | sort"
```
une dernière fois. Si une PR concurrente a déjà mergé un `V15`, basculer en `V16` et adapter toutes les références `V15` de cette spec.

### 3. PK `Comment.id` → `Long` via `PanacheEntity`

**Décision.** `public class Comment extends PanacheEntity` — PK `Long` séquentielle gérée par `comments_seq` (sequence Hibernate par défaut, increment 50, cohérent avec `events_seq`, `reports_seq`, `follows_seq`).

**Justification.** Cohérent avec `Event`, `Report`, `Favorite`, `Attendance`, `Follow`, `EventCoOrganizer`, `EventView` — tous PanacheEntity Long. Une PK UUID ne servirait à rien (le `commentId` est l'identifiant naturel public consommé par `DELETE /comments/{commentId}` et par `CommentDTO.parentCommentId`). Le `Long` est sérialisé en JSON sous forme entière — directement consommable par TanStack Query côté front (SCRUM-146).

### 4. Relation `author` → `@ManyToOne(LAZY) User author`, **pas un UUID brut**

**Décision.**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id", nullable = false)
public User author;
```

**Justification.** Cohérent avec `Report.reporter` ([`Report.java:25-27`](backend/src/main/java/ch/unige/events/entity/Report.java#L25-L27)) qui est l'entité fonctionnellement la plus proche (jointure user→event avec contenu textuel). Le backlog ([`backlog_s5_s10.md` ligne 1116](backend/docs/backlog_s5_s10.md#L1116)) est explicite : `author (@ManyToOne(fetch=LAZY) User, @JoinColumn("author_id"))`.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) `@ManyToOne(LAZY) User author` | Backlog explicite ; cohérent avec `Report.reporter`, `Event.creator`, `EventCoOrganizer.user` ; navigation directe `comment.author.displayName` dans `CommentDTO.from` ; FK garantie côté DB | Risque N+1 si listing massif sans EAGER fetch (mitigé par batch-load décision 27) | ✅ retenu |
| (b) `UUID authorId` (cohérent avec `Favorite`, `Follow`, `Attendance` qui sont des **tables de jointure** sans payload textuel) | Pas de proxy LAZY ; pas de N+1 par défaut | Force un lookup `User.findById(authorId)` à chaque projection DTO ; perte de cohérence avec `Report.reporter` (le pattern « user qui a une intent » dans le projet) | ❌ |

Le backlog tranche explicitement (a) ; on s'y conforme.

### 5. Relation `event` → `@ManyToOne(LAZY) Event event`, FK `event_id` NOT NULL

**Décision.**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "event_id", nullable = false)
public Event event;
```

**Justification.** Mirror exact de `Report.event` ([`Report.java:21-23`](backend/src/main/java/ch/unige/events/entity/Report.java#L21-L23)). La FK `NOT NULL` enferme l'invariant : un commentaire **doit** être attaché à un event (pas d'orphelin). Côté SQL la FK sera `fk_comments_event` sans `ON DELETE CASCADE` (pattern défensif assumé — cohérent avec `Report`, `Follow`, `Favorite`).

### 6. Relation `parentComment` → `@ManyToOne(LAZY) Comment parentComment`, **nullable**

**Décision.**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_comment_id", nullable = true)
public Comment parentComment;
```

**Justification.** Auto-référence sur `comments`. `null` = commentaire top-level. Non-null = reply à un commentaire top-level (1 niveau max — cf. décision 7). FK `fk_comments_parent` sans cascade : si un parent est DELETE, ses replies restent (cf. décision 17 — choix tranché vs cascade).

### 7. Profondeur des replies : **1 niveau max**, sinon `422 replies_too_deep`

**Décision.** Au moment du `POST /events/{eventId}/comments` avec `parentCommentId` non null, le service vérifie que **le commentaire référencé n'a lui-même pas de parent** (`parentComment.parentComment == null`). Sinon → `422 Unprocessable Entity` avec envelope `{ "error": "replies_too_deep", "message": "..." }`.

**Justification.** Backlog [`backlog_s5_s10.md` ligne 1125](backend/docs/backlog_s5_s10.md#L1125) (« valider profondeur max 1 niveau »). Le frontend (SCRUM-146 [`backlog_s5_s10.md` ligne 1508](backend/docs/backlog_s5_s10.md#L1508)) affiche les replies indentées d'un niveau **uniquement**. Permettre des replies-de-replies forcerait un rendu récursif côté front + UX dégradé sur mobile (indentation cumulative). Tranche aussi sur la complexité backend : pas besoin de CTE récursive PostgreSQL (`WITH RECURSIVE`) pour matérialiser un thread.

### 8. Champ `content` → `TEXT NOT NULL` + `@NotBlank` + `@Size(max=2000)` + trim côté service

**Décision.**
```java
@Column(columnDefinition = "TEXT", nullable = false)
@NotBlank
@Size(max = 2000)
public String content;
```

Le service **trim** systématiquement le `content` reçu **avant** persistance (`request.content().strip()` — Java 11+). Si le résultat post-trim est vide, l'erreur `400` Bean Validation aura déjà été levée par `@NotBlank` (qui matche aussi sur les whitespace-only strings).

**Justification.** Backlog [`backlog_s5_s10.md` ligne 1115](backend/docs/backlog_s5_s10.md#L1115) (« `content` (String TEXT, @NotBlank, @Size(max=2000)) »). Choix `TEXT` vs `VARCHAR(2000)` :

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) `@Column(columnDefinition = "TEXT")` | Pas de tronquage silencieux côté DB si la limite logique passe à 5000 chars ; cohérent avec `Event.description` ([`Event.java:25-26`](backend/src/main/java/ch/unige/events/entity/Event.java#L25-L26)) post-V9 et avec `Report.description` ([`Report.java:33-34`](backend/src/main/java/ch/unige/events/entity/Report.java#L33-L34)) | Légère sur-allocation théorique côté toast (ignorable sur PostgreSQL TEXT) | ✅ retenu |
| (b) `@Column(length = 2000)` (donc VARCHAR) | Cohérent stricto-sensu avec la limite Bean Validation | Verrouille le schéma à 2000 chars : passer à 5000 plus tard = nouvelle migration ALTER COLUMN | ❌ |

### 9. Champ `likeCount` → `int` default `0`, **lecture seule en S6**

**Décision.**
```java
@Column(nullable = false)
public int likeCount = 0;
```

Le service `CommentService` n'incrémente **jamais** ce champ en S6. La mutation est livrée par SCRUM-144 (S7) via `CommentService.like(...)` / `.unlike(...)`. Le champ est exposé en lecture dans `CommentDTO.likeCount` dès maintenant **avec une valeur toujours `0`**.

**Justification.** Figer le contrat dès S6 évite à SCRUM-146 (front S7) de re-typer son `CommentDTO` TypeScript quand SCRUM-144 mergera. Le backlog [`backlog_s5_s10.md` ligne 1119](backend/docs/backlog_s5_s10.md#L1119) liste explicitement `likeCount (int, default 0)` dans la définition de l'entité S6.

### 10. Champ `createdAt` → `LocalDateTime` `@PrePersist`, immutable

**Décision.**
```java
@Column(updatable = false)
public LocalDateTime createdAt;

@PrePersist
public void prePersist() {
    createdAt = LocalDateTime.now();
}
```

**Justification.** Mirror exact de [`Report.java:50-56`](backend/src/main/java/ch/unige/events/entity/Report.java#L50-L56) et [`Follow.java:38-43`](backend/src/main/java/ch/unige/events/entity/Follow.java#L38-L43). Pas de `updatedAt` — un commentaire est immutable côté contrat (pas d'endpoint `PUT`/`PATCH` ; éditer = supprimer + reposter, choix UX assumé pour S6).

### 11. Indexes SQL → 2 indexes simples + 1 composite

**Décision.** Trois indexes dans `V15__create_comments.sql` :

```sql
CREATE INDEX IF NOT EXISTS idx_comment_event           ON comments(event_id);
CREATE INDEX IF NOT EXISTS idx_comment_parent          ON comments(parent_comment_id);
CREATE INDEX IF NOT EXISTS idx_comment_event_created   ON comments(event_id, created_at DESC);
```

**Justification.**
- `idx_comment_event` : fallback générique pour `Comment.find("event.id = ?1", eventId)`.
- `idx_comment_parent` : nécessaire au batch-load des replies (cf. décision 27 — `WHERE parent_comment_id IN (?, ?, ?, ...)`).
- `idx_comment_event_created` (composite) : matche exactement la requête de listing top-level du `getByEvent` (cf. décision 20) — `WHERE event_id = ? AND parent_comment_id IS NULL ORDER BY created_at DESC, id DESC`. PostgreSQL utilise l'index composite `(event_id, created_at DESC)` pour servir le `ORDER BY` sans scan séquentiel.

Pas d'index sur `author_id` en S6 — aucun listing « commentaires d'un user » n'est planifié dans le backlog. À ajouter ultérieurement si SCRUM-XYZ (S9+) introduit `GET /users/{id}/comments`.

### 12. Endpoints exacts (3 paths, sans préfixe `/api`)

**Décision.** Le préfixe `/api` est apposé par Quarkus via `quarkus.http.root-path=api` ([`application.properties:1`](backend/src/main/resources/application.properties#L1)). Les `@Path` sont donc relatifs.

| Méthode | Path JAX-RS (sans `/api`) | Path public (avec `/api`) | Body | Réponse |
|---|---|---|---|---|
| `POST` | `/events/{eventId}/comments` | `/api/events/{eventId}/comments` | `CreateCommentRequest{ content, parentCommentId? }` | `201 Created` + `CommentDTO` |
| `GET` | `/events/{eventId}/comments` | `/api/events/{eventId}/comments` | — (query : `page`, `size`) | `200 OK` + `List<CommentDTO>` |
| `DELETE` | `/comments/{commentId}` | `/api/comments/{commentId}` | — | `204 No Content` |

**Justification.** Aligne avec le backlog [`backlog_s5_s10.md` ligne 1129](backend/docs/backlog_s5_s10.md#L1129). La répartition des deux racines `@Path` (`/events` pour POST/GET, `/comments` pour DELETE) impose le **split en deux Resources** (cf. décision 25).

### 13. Authentification & autorisation par endpoint

**Décision.**

| Endpoint | Annotation | Justification |
|---|---|---|
| `POST /events/{eventId}/comments` | `@Authenticated` + `@PerUserRateLimit` | Mutation — exige un compte ; rate-limité (anti-spam) cf. décision 22 |
| `GET /events/{eventId}/comments` | `@PermitAll` | Cohérent avec `GET /events/{id}` lui-même `@PermitAll`. La visibilité des **events** filtre déjà ce qui est lisible : sur un DRAFT non-créateur, `getById` retourne 404 (anti-oracle) — donc le listing des commentaires retourne 404 aussi. Pas besoin de re-checker côté `Comment` |
| `DELETE /comments/{commentId}` | `@Authenticated` | Mutation — exige un compte (auteur, créateur, co-org ACCEPTED — cf. décision 16) |

### 14. Visibilité event : passer **systématiquement** par `EventService.getById(...)`

**Décision.** `CommentService.post(...)` et `CommentService.getByEvent(...)` commencent **toujours** par :
```java
EventDTO eventDto = eventService.getById(eventId, auth0Id, isAdmin);
```
Cet appel **délègue intégralement** la garde anti-oracle (DRAFT/CANCELLED/BANNED → 404) à la logique existante ([`EventService.java:152-181`](backend/src/main/java/ch/unige/events/service/EventService.java#L152-L181)).

**Justification.** Une seule source de vérité pour la visibilité d'un event. Si SCRUM-XYZ ajoute demain un nouveau statut caché (ex. `MODERATING`), il sera couvert ici sans changement. Anti-oracle ISSUE-92 : un appelant qui spam des `commentId` ne peut pas inférer si un event DRAFT existe — la 404 est identique à un `eventId` inexistant.

Le service récupère ensuite l'`Event` via `Event.<Event>findByIdOptional(eventId).orElseThrow(NotFoundException::new)` **après** la garde, pour avoir l'objet entité utilisable côté JPA (le DTO ne suffit pas pour positionner la FK `comment.event`).

Pour `GET /events/{eventId}/comments` (`@PermitAll`), `auth0Id` peut être `null` côté caller anonyme. `EventService.getById` accepte ce cas : un PUBLISHED reste visible, un DRAFT/CANCELLED/BANNED retourne 404. Aucun branchement spécial à coder côté `CommentService`.

### 15. Cas POST sur event non-PUBLISHED / non-existant

**Décision.** Au-delà de la garde de visibilité (décision 14, qui couvre DRAFT/CANCELLED/BANNED non-créateur → 404), un commentaire ne peut être posté que si l'event est en statut acceptable :

| `event.status` | Auteur | Réponse |
|---|---|---|
| `PUBLISHED` | tout `@Authenticated` | `201 Created` + `CommentDTO` |
| `DRAFT` | non-créateur (et pas ADMIN) | `404 not_found` (hérité de `getById`) |
| `DRAFT` | créateur **ou** co-org ACCEPTED **ou** ADMIN | `400 cannot_comment_draft_event` (event pas encore public — pas de discussion possible) |
| `CANCELLED` | tout authentifié | `400 cannot_comment_cancelled_event` |
| `EXPIRED` | tout authentifié | `400 cannot_comment_expired_event` |
| `BANNED` | tout monde, admin compris | `404 not_found` (hérité de `getById` — invisible) |
| event id inconnu | tout authentifié | `404 not_found` (hérité de `getById`) |

**Justification.** Un commentaire est une interaction **publique** ; il n'a de sens que sur un event **PUBLISHED**. Pour DRAFT (cas créateur), 400 et pas 422 : c'est un état métier transitoire, le créateur peut publier puis recommenter ; 400 expose mieux l'action requise (« publie d'abord »). Pour CANCELLED / EXPIRED, 400 + envelope claire (`cannot_comment_cancelled_event` / `cannot_comment_expired_event`) — le frontend SCRUM-146 affichera un toast non-bloquant.

### 16. Cas DELETE : **auteur OU créateur OU co-org ACCEPTED OU ADMIN**

**Décision.** `DELETE /comments/{commentId}` est autorisé pour :

1. l'**auteur** du commentaire (`comment.author.id == caller.id`),
2. le **créateur** de l'event ciblé (`comment.event.creator.id == caller.id`),
3. un **co-organisateur ACCEPTED** de l'event (réutiliser [`EventService.isCreatorOrAcceptedCoOrganizerPublic(event, auth0Id)`](backend/src/main/java/ch/unige/events/service/EventService.java#L436-L438) — cascade SCRUM-136),
4. un utilisateur **ADMIN** (claim Auth0 — cf. [`backend/AGENTS.md` lignes 64-75](backend/AGENTS.md#L64-L75)).

Sinon → `403 Forbidden` envelope `{ "error": "forbidden", "message": "..." }`. Si le `commentId` n'existe pas → `404 not_found` envelope `{ "error": "comment_not_found", "message": "..." }`.

**Justification.** Backlog [`backlog_s5_s10.md` ligne 1127](backend/docs/backlog_s5_s10.md#L1127) (« auteur OU organisateur de l'event ») + cascade SCRUM-136 (le co-organisateur ACCEPTED hérite des privilèges modération, cf. [`EventService.java:188`](backend/src/main/java/ch/unige/events/service/EventService.java#L188), [`EventService.java:251`](backend/src/main/java/ch/unige/events/service/EventService.java#L251)) + la dimension **admin modération globale** (un admin peut nettoyer un commentaire offensant sans dépendre de l'organisateur). 403 vs 404 : on a authentifié l'appelant, l'autorisation est donc l'info qu'on lui doit ; un 404 anti-oracle n'est pas requis ici car l'existence du `commentId` n'est pas un secret (un attaquant qui veut énumérer les commentaires d'un event passe par `GET /events/{id}/comments`, qui est `@PermitAll` et liste déjà tout).

### 17. Suppression : **DELETE physique**, pas de soft-delete

**Décision.** `DELETE /comments/{commentId}` exécute un `Comment.delete()` JPA — la row part définitivement de la table. Si le commentaire avait des replies (commentaire top-level supprimé), les replies **restent** (`parent_comment_id` pointe vers une row inexistante).

**Justification.**

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) DELETE physique avec FK `ON DELETE SET NULL` | Cohérent avec `Follow` (reject = DELETE row, SCRUM-138 décision 5), `EventCoOrganizer.decline` (DELETE row), `Favorite` (DELETE row) ; simplicité backend. Replies survivent et remontent automatiquement en top-level (`parent_comment_id` → NULL) — visibles dans le listing sans logique de filtrage spéciale. **Correction post-Copilot review** : la version initiale supposait une FK sans cascade, ce qui aurait fait échouer le DELETE côté DB (RESTRICT par défaut) ; `ON DELETE SET NULL` est la clause qui matérialise réellement la décision « DELETE physique avec replies préservées ». | ✅ retenu |
| (b) Soft-delete (`deletedAt: LocalDateTime`) | Permet l'audit + l'undelete | Sur-ingénierie pour MVP ; aucune exigence métier d'audit S6 ; force un filtre `WHERE deleted_at IS NULL` partout | ❌ |
| (c) DELETE physique + cascade `ON DELETE` sur les replies | Pas d'orphelins | Casse l'historique conversationnel (un mod supprime un thread entier en supprimant la racine — UX brutale) | ❌ |

**Comportement front** (SCRUM-146 — informatif, pas dans le scope SCRUM-139) : avec `ON DELETE SET NULL`, une reply dont le parent est supprimé arrive avec `parentCommentId: null` dans le payload `getByEvent` et est rendue comme un commentaire top-level normal. Le contexte conversationnel est perdu mais le contenu reste visible. Si SCRUM-146 souhaite afficher un indicateur explicite « ↳ commentaire orphelin », il pourra ajouter un champ DTO dans une PR séparée — pas dans le scope SCRUM-139.

### 18. DTO sortant `CommentDTO` (record) — projection complète + replies imbriquées

**Décision.**

```java
public record CommentDTO(
        Long id,
        String content,
        UUID authorId,
        String authorDisplayName,
        String authorAvatarUrl,
        boolean authorIsOrganizer,
        int likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        Long parentCommentId,
        List<CommentDTO> replies
) {
    public static CommentDTO from(Comment c, boolean authorIsOrganizer) {
        return new CommentDTO(
                c.id,
                c.content,
                c.author != null ? c.author.id : null,
                c.author != null ? c.author.displayName : null,
                c.author != null ? c.author.avatarUrl : null,
                authorIsOrganizer,
                c.likeCount,
                false,
                c.createdAt,
                c.parentComment != null ? c.parentComment.id : null,
                List.of()
        );
    }

    public static CommentDTO fromTopLevelWithReplies(
            Comment top,
            List<Comment> replies,
            boolean topAuthorIsOrganizer,
            java.util.Map<UUID, Boolean> repliesAuthorIsOrganizer
    ) {
        List<CommentDTO> replyDTOs = replies.stream()
                .map(r -> CommentDTO.from(
                        r,
                        r.author != null
                                && repliesAuthorIsOrganizer.getOrDefault(r.author.id, false)))
                .toList();
        return new CommentDTO(
                top.id,
                top.content,
                top.author != null ? top.author.id : null,
                top.author != null ? top.author.displayName : null,
                top.author != null ? top.author.avatarUrl : null,
                topAuthorIsOrganizer,
                top.likeCount,
                false,
                top.createdAt,
                null,
                replyDTOs
        );
    }
}
```

**Notes :**
- `authorIsOrganizer` (boolean) : `true` si l'auteur du commentaire est le **créateur** de l'event ciblé OU un co-organisateur ACCEPTED. Calculé en bulk côté `getByEvent` (cf. décision 27 — un seul check, pas un par row), individuellement côté `post`. Le préfixe `is` est volontairement conservé (cf. décision 24).
- `likedByMe` (boolean) : **toujours `false` en S6**. Sera enrichi par SCRUM-144 (S7) via un join sur `CommentLike`.
- `parentCommentId` (Long, nullable) : `null` pour les top-level, valeur du `comment.parentComment.id` pour les replies.
- `replies` (List<CommentDTO>) : peuplé **uniquement** sur les top-level (sortie de `getByEvent`). Sur la sortie de `POST /comments`, toujours `List.of()` (un commentaire fraîchement créé n'a pas encore de replies).

**Justification.** Forme exigée par le backlog [`backlog_s5_s10.md` ligne 1130](backend/docs/backlog_s5_s10.md#L1130). Les deux factories séparées évitent la duplication entre `post()` (qui retourne 1 commentaire isolé) et `getByEvent()` (qui retourne des top-level avec leurs replies imbriquées). Le pattern factory enrichie (`from(User, ...)` + `fromAnonymous(User)`) sur [`UserPublicResponse.java:9-88`](backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java#L9-L88) sert de modèle direct à cette double factory.

### 19. DTO entrant `CreateCommentRequest` (record) — content + parentCommentId

**Décision.**

```java
public record CreateCommentRequest(
        @NotBlank
        @Size(max = 2000)
        String content,
        Long parentCommentId
) {}
```

**Justification.** Bean Validation enforced à l'entrée de la Resource via `@Valid` (cf. [`ReportResource.java:36-37`](backend/src/main/java/ch/unige/events/resource/ReportResource.java#L36-L37)). `parentCommentId` est nullable (`null` = top-level). Pas de wrapping `Optional<Long>` — Jackson désérialise `null` directement.

**Annotation Resource :** `@Valid @NotNull CreateCommentRequest request` — cohérent avec [`ReportResource.java:36-37`](backend/src/main/java/ch/unige/events/resource/ReportResource.java#L36-L37). Le `@NotNull` garde un body absent → 400 `Body cannot be null`.

### 20. Pagination `getByEvent` → page (top-level) + replies inline

**Décision.** La pagination s'applique **aux commentaires top-level uniquement**. Les replies de chaque top-level retourné sont chargées **toutes**, en bulk, et imbriquées dans `replies[]`.

```java
@QueryParam("page") @DefaultValue("0") @Min(0) int page,
@QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size
```

Tri : `ORDER BY c.createdAt DESC, c.id DESC` (id en départage pour stabilité des fenêtres de pagination quand deux commentaires partagent la milliseconde). Réponse : `List<CommentDTO>` brut, pas de `PagedResponse` wrapper (cohérent avec le reste du projet — `GET /events`, `GET /follow-requests`).

**Justification.** Cas typique d'une `EventDetailPage` : un thread top-level peut avoir 0 à 50 replies, mais l'utilisateur scrollé voit 5-10 top-level paginés. Tronquer les replies à mi-chemin créerait un puzzle UX (« où sont mes 3 dernières replies ? »). Les replies étant capées par la profondeur 1 (décision 7), le payload reste borné. Pattern de pagination strictement aligné sur [`UserResource.java:293-294`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L293-L294).

### 21. Codes d'erreur normalisés via `ApiErrorResponse{error, message}`

**Décision.** Tous les codes d'erreur métier passent par les helpers `badRequest`/`unprocessable`/`forbidden`/`notFound` qui jettent une `WebApplicationException` avec `ApiErrorResponse(error, message)` en body.

| HTTP | `error` (slug) | Quand | Helper |
|---|---|---|---|
| `400` | `cannot_comment_draft_event` | POST sur event DRAFT par créateur/co-org/admin | `badRequest(...)` |
| `400` | `cannot_comment_cancelled_event` | POST sur event CANCELLED | `badRequest(...)` |
| `400` | `cannot_comment_expired_event` | POST sur event EXPIRED | `badRequest(...)` |
| `403` | `forbidden` | DELETE par tiers | `forbidden(...)` |
| `404` | `not_found` | event invisible (DRAFT non-créateur, CANCELLED non-créateur, BANNED, id inconnu) | hérité de `EventService.getById` — `NotFoundException` standard, mappé par `NotFoundExceptionMapper` |
| `404` | `comment_not_found` | DELETE sur `commentId` inexistant | `notFound("comment_not_found", ...)` (helper) |
| `404` | `parent_comment_not_found` | POST avec `parentCommentId` inexistant | `notFound("parent_comment_not_found", ...)` |
| `422` | `replies_too_deep` | POST avec parent qui a déjà un parent | `unprocessable(...)` |
| `422` | `parent_comment_not_in_event` | POST avec `parentCommentId` qui appartient à un autre event | `unprocessable(...)` |
| `429` | `rate_limited` | rate limit `comments.post` dépassé | `RateLimitInterceptor` standard |

**Note.** L'envelope d'un `404 not_found` standard (event invisible) est produite par `NotFoundExceptionMapper` — `error: "not_found"`. Pour les 404 spécialisés (`comment_not_found`, `parent_comment_not_found`), on jette `WebApplicationException` directement avec l'envelope custom (le mapper ne s'active pas dans ce cas).

### 22. Rate limiting → `@PerUserRateLimit(name="comments.post", max=10, windowSeconds=60)` sur POST uniquement

**Décision.**
```java
@POST
@Path("/{eventId}/comments")
@Authenticated
@PerUserRateLimit(name = "comments.post", max = 10, windowSeconds = 60)
public Response postComment(@PathParam("eventId") Long eventId,
                            @Valid @NotNull CreateCommentRequest request) { ... }
```

Réponse `429 Too Many Requests` + header `Retry-After` (cf. [`PerUserRateLimit.java:30-43`](backend/src/main/java/ch/unige/events/config/PerUserRateLimit.java#L30-L43) + `RateLimitInterceptor`).

**Justification.** Anti-spam minimal — un utilisateur peut poster 10 commentaires/minute, suffisant pour des échanges live mais bloquant pour un bot. Cohérent avec `users.updateMe` ([`UserResource.java:229`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L229) — max 10) et `follows.follow` ([`FollowResource.java:55`](backend/src/main/java/ch/unige/events/resource/FollowResource.java#L55) — max 30, plus permissif car action plus légère).

GET et DELETE **non rate-limités** :
- GET : `@PermitAll` ; rate-limiter par utilisateur n'a pas de sens (un anonyme n'a pas d'identifiant). Si abus DDoS détecté plus tard → couche infra Nginx, pas application.
- DELETE : action explicite, peu d'incitation à abuser ; un troll potentiel est déjà filtré par l'autorisation (auteur/créateur/co-org/admin uniquement).

### 23. Notifications de commentaire / mention — **hors scope**

**Décision.** SCRUM-139 **n'émet aucune notification**. SCRUM-145 (S7+) ajoutera :
- `NEW_COMMENT` (notification au créateur de l'event quand un nouveau commentaire arrive),
- `COMMENT_MENTION` (notification à un user mentionné via `@displayName` dans le contenu).

Ces deux types dépendent de l'infrastructure `Notification` livrée par SCRUM-99 (S7). Si SCRUM-99 n'est pas mergée au moment de SCRUM-145, ce dernier sera lui-même bloqué — mais SCRUM-139 reste indépendant et mergeable.

**Justification.** Découpler la persistance de la diffusion. Pas de fan-out asynchrone (Quarkus event bus, queue) à câbler dans cette PR. Aucune ligne de code « TODO: emit notification later » — le hook sera ajouté propre dans SCRUM-145.

### 24. Nommage du champ `authorIsOrganizer` — exception assumée à la règle « pas de préfixe `is` »

**Décision.** Le DTO conserve le nom `authorIsOrganizer` (boolean). Le préfixe `is` est conservé volontairement, malgré la règle [`backend/AGENTS.md` lignes 36-39](backend/AGENTS.md#L36-L39).

**Justification.** La règle « pas de préfixe `is` » est ciblée explicitement aux **entités JPA** (« champs des entités JPA », « Lombok génère `isIsActive()` »). `CommentDTO` est un **record DTO**, pas une entité — Lombok ne s'y applique pas. De plus :

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) `authorIsOrganizer` (boolean) | Sémantique sans ambiguïté (« est-ce que l'auteur est l'organisateur ? ») ; matche le backlog [`backlog_s5_s10.md` ligne 1130](backend/docs/backlog_s5_s10.md#L1130) | Préfixe `is` (déconseillé sur entités JPA — non applicable ici) | ✅ retenu |
| (b) `organizer` (boolean) | Matche strictement la convention sur entités | Ambigu — un lecteur peut comprendre « est-ce que ce commentaire est de l'organisateur (= l'event) ? » ou « est-ce que l'auteur est organisateur en général ? » | ❌ |
| (c) `authorRole: AuthorRole` (enum `AUTHOR / ORGANIZER`) | Riche sémantique ; extensible (futurs rôles : `ADMIN`, `MODERATOR`...) | Sur-ingénierie pour 2 valeurs ; force la création d'un enum dédié + sérialisation JSON enum | ❌ |

Le nom `authorIsOrganizer` matche **exactement** le backlog ; le frontend SCRUM-146 lit déjà ce champ (`authorIsOrganizer ? <Badge>Organisateur</Badge> : null`). Tout autre nommage forcerait une re-coordination front+back.

### 25. Split en deux Resources : `CommentResource` + `CommentDirectResource`

**Décision.** Deux classes Resource avec `@Path` racines **disjoints** :

```java
// CommentResource — racine /events
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CommentResource {
    @POST @Path("/{eventId}/comments") ...
    @GET  @Path("/{eventId}/comments") ...
}

// CommentDirectResource — racine /comments
@Path("/comments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CommentDirectResource {
    @DELETE @Path("/{commentId}") ...
}
```

**Justification.** Pattern institué par SCRUM-138 — split [`FollowResource.java:42`](backend/src/main/java/ch/unige/events/resource/FollowResource.java#L42) (`/users`) + [`FollowRequestResource.java:24`](backend/src/main/java/ch/unige/events/resource/FollowRequestResource.java#L24) (`/follow-requests`). Quarkus / RESTEasy Reactive admet plusieurs Resources avec des `@Path` racines distincts, mais une seule Resource avec `@Path("/")` qui mélangerait `/events` et `/comments` produit des comportements de routing imprévisibles (matcher éventail, conflits de paramètres).

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) Deux Resources avec `@Path` distincts (`/events`, `/comments`) | Pattern projet ; séparation claire ; pas de surprise de routing | 1 fichier supplémentaire | ✅ retenu |
| (b) Une seule Resource avec `@Path("/")` et chemins absolus dans chaque méthode | 1 seul fichier | Casse le pattern projet ; pas testé en CI | ❌ |
| (c) Rerouter `DELETE /comments/{id}` sous `/events/{eventId}/comments/{commentId}` | 1 seule Resource ; symétrie POST/DELETE | Force le client à connaître l'`eventId` du commentaire pour le supprimer (alors que `commentId` est l'identifiant naturel global). Casse l'API REST orthodoxe | ❌ |

### 26. Pas de `@Transactional` sur les lectures pures du `CommentService`

**Décision.** `CommentService.getByEvent(...)` n'est **pas** annoté `@Transactional`. Seules les mutations (`post`, `delete`) le sont.

**Justification.** Cohérent avec SCRUM-138 décision 23 (`FollowService.countFollowers`, `getFollowers` — non transactional). Hibernate ouvre une session JPA en lecture pour le rendu Panache sans avoir besoin de wrapper transactionnel. Réduit la pression sur le pool DB en cas de listing massif (`size=100`).

### 27. Batch-load des replies + bulk `authorIsOrganizer` — **pas de N+1**

**Décision.** Dans `getByEvent(...)`, après avoir matérialisé la page de top-level :

```java
List<Comment> topLevels = Comment.<Comment>find(
        "event.id = ?1 and parentComment is null order by createdAt desc, id desc",
        eventId
).page(page, size).list();

if (topLevels.isEmpty()) return List.of();

List<Long> topLevelIds = topLevels.stream().map(c -> c.id).toList();
List<Comment> replies = Comment.list(
        "parentComment.id in ?1 order by createdAt asc, id asc",
        topLevelIds
);
Map<Long, List<Comment>> repliesByParent = replies.stream()
        .collect(Collectors.groupingBy(r -> r.parentComment.id));
```

Ensuite mapper chaque top-level vers son `CommentDTO.fromTopLevelWithReplies(top, repliesByParent.getOrDefault(top.id, List.of()), ...)`. **2 requêtes SQL au total** (1 page top-level + 1 batch replies), peu importe la taille de la page.

**Calcul de `authorIsOrganizer` en bulk.** Pour éviter d'appeler N fois `EventService.isCreatorOrAcceptedCoOrganizerPublic`, on charge une **seule fois** :
- (a) le `creator.id` de l'event ciblé (déjà en mémoire via l'entité `event` chargée à la décision 14),
- (b) la liste des `userId` co-organisateurs ACCEPTED via `EventCoOrganizer.<EventCoOrganizer>find("eventId = ?1 and status = ?2", eventId, CoOrganizerStatus.ACCEPTED).list()`.

Puis on construit un `Set<UUID> organizerUserIds = {creator.id} ∪ {coOrgs}` testé en O(1) pour chaque commentaire (top-level + replies) de la page. Pour `post()` (un seul commentaire), on appelle directement `eventService.isCreatorOrAcceptedCoOrganizerPublic(event, comment.author.auth0Id)` — pas de bulk nécessaire.

**Justification.** Sans le batch, on aurait `1 + N` requêtes (N = nombre de top-level dans la page). Sur une page `size=20` avec une moyenne de 5 replies/top-level, le batch SQL ramène ~100 rows en 1 query au lieu de 20 queries séparées. Pattern aligné sur `AttendanceService.getAttendees` et [`FollowResource.java:129-141`](backend/src/main/java/ch/unige/events/resource/FollowResource.java#L129-L141) (`resolveUsers`).

### 28. Helpers d'erreurs locaux à `CommentService` — duplication assumée (KISS)

**Décision.** Les helpers `badRequest`, `unprocessable`, `forbidden`, `notFound` sont **dupliqués** comme méthodes statiques package-private de `CommentService`, au lieu d'être extraits dans une utility class partagée.

**Justification.** Pattern actuel du projet : `ReportService` ([`ReportService.java:141-163`](backend/src/main/java/ch/unige/events/service/ReportService.java#L141-L163)), `FollowService` ([`FollowService.java:156-178`](backend/src/main/java/ch/unige/events/service/FollowService.java#L156-L178)), `EventCoOrganizerService` les dupliquent tous. Les extraire vaut la peine quand un 4e service les utilise — pas avant. Décision SCRUM-139 : on duplique, on **n'extrait pas** ; un éventuel ticket de refactor (`refactor: extract ApiErrors helper`) pourra le faire en une PR séparée si le besoin émerge.

---

## Analyse de l'existant

### 4.1 Entités & migrations

| Domaine | Fichier | Lignes | Pattern à mimer | Note d'adaptation pour `Comment` |
|---|---|---|---|---|
| Entité PanacheEntity Long PK | [`Report.java`](backend/src/main/java/ch/unige/events/entity/Report.java) | 19 | `extends PanacheEntity` (PK Long auto-générée par sequence increment 50) | Identique pour `Comment` |
| `@ManyToOne(LAZY) + @JoinColumn` (event) | [`Report.java`](backend/src/main/java/ch/unige/events/entity/Report.java) | 21-23 | `nullable = false` | Identique pour `Comment.event` |
| `@ManyToOne(LAZY) + @JoinColumn` (user) | [`Report.java`](backend/src/main/java/ch/unige/events/entity/Report.java) | 25-27 | `nullable` permis (`Report.reporter` est nullable) | Pour `Comment.author` : `nullable = false` (un commentaire orphelin n'a pas de sens fonctionnel) |
| `@ManyToOne(LAZY)` self-reference | absent du code-base | — | Invention pour SCRUM-139 | Pour `Comment.parentComment` : `nullable = true`, FK `fk_comments_parent` sans cascade (cf. décision 17) |
| `@PrePersist createdAt` | [`Report.java`](backend/src/main/java/ch/unige/events/entity/Report.java) | 50-56 | `@Column(updatable = false) public LocalDateTime createdAt; @PrePersist prePersist() { createdAt = now(); }` | Identique pour `Comment.prePersist()` |
| Entité `Event` (statut, créateur) | [`Event.java`](backend/src/main/java/ch/unige/events/entity/Event.java) | 21-58 | `@ManyToOne(LAZY) creator`, `EventStatus status` | À utiliser pour la garde de visibilité (cf. décision 14) et pour `authorIsOrganizer` |
| Migration Flyway « table avec FK + indexes + sequence » | [`V6__create_reports.sql`](backend/src/main/resources/db/migration/V6__create_reports.sql) | 1-17 | `CREATE SEQUENCE … START WITH 1 INCREMENT BY 50; CREATE TABLE … CONSTRAINT pk_… PRIMARY KEY … CONSTRAINT fk_… FOREIGN KEY … REFERENCES …; CREATE INDEX …` | `V15__create_comments.sql` mirror exact (sans `UNIQUE` constraint — pas de doublon métier sur les commentaires) |
| Migration Flyway récente avec FK UUID + index | [`V14__create_follows.sql`](backend/src/main/resources/db/migration/V14__create_follows.sql) | 1-23 | Style de fichier complet (commentaire d'en-tête, `IF NOT EXISTS`, FK sans cascade) | Style de fichier à dupliquer |

### 4.2 Services & Resources

| Domaine | Fichier | Lignes | Pattern à mimer | Note d'adaptation |
|---|---|---|---|---|
| `EventService.getById` (anti-oracle DRAFT/CANCELLED/BANNED → 404) | [`EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | 152-181 | Garde anti-oracle (`event.status != PUBLISHED && !isAdmin && !isCreator…` → `NotFoundException`) | À **appeler** depuis `CommentService.post` et `getByEvent` (PAS dupliquer la logique) |
| `EventService.isCreatorOrAcceptedCoOrganizerPublic(Event, String auth0Id)` | [`EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | 436-438 | Wrapper public de la cascade SCRUM-136 | À **appeler** depuis `CommentService.delete` et la projection `authorIsOrganizer` |
| `UserService.getPublicProfile` (anti-oracle ISSUE-93 sur profil privé) | [`UserService.java`](backend/src/main/java/ch/unige/events/service/UserService.java) | 81-91 | Pattern anti-oracle 404 (envelope identique à « inexistant ») | Pas réutilisé directement ici, mais sert de référence philosophique pour la décision 14 |
| Resource header `@Path` + `@Produces`/`@Consumes` + injection `SecurityIdentity` | [`ReportResource.java`](backend/src/main/java/ch/unige/events/resource/ReportResource.java) | 19-31 | Constructor injection `final` ; `identity.getPrincipal().getName()` | Identique pour `CommentResource` et `CommentDirectResource` |
| Resource POST avec `@Authenticated` + body `@Valid @NotNull` | [`ReportResource.java`](backend/src/main/java/ch/unige/events/resource/ReportResource.java) | 33-41 | `@POST @Path("/{id}/...") @Authenticated public Response …(@Valid @NotNull CreateReportRequest request)` | Identique pour `CommentResource.postComment` (+ `@PerUserRateLimit`) |
| Service `@ApplicationScoped` + `@Inject EventService` | [`ReportService.java`](backend/src/main/java/ch/unige/events/service/ReportService.java) | 23-27 | injection field-style | Identique pour `CommentService` |
| Service `create` qui passe par `EventService` puis bloque selon statut | [`ReportService.java`](backend/src/main/java/ch/unige/events/service/ReportService.java) | 30-73 | charge l'event, branche par statut, jette `badRequest`, persiste | **Modèle direct** pour `CommentService.post` |
| Helpers d'erreurs static package-private | [`ReportService.java`](backend/src/main/java/ch/unige/events/service/ReportService.java) | 141-163 | `WebApplicationException` + `ApiErrorResponse` + `MediaType.APPLICATION_JSON_TYPE` | Dupliquer dans `CommentService` (cf. décision 28) |
| Mappers d'exception standards | [`backend/src/main/java/ch/unige/events/exception/mapper/`](backend/src/main/java/ch/unige/events/exception/mapper/) | — | `NotFoundExceptionMapper`, `ConflictExceptionMapper`, `ForbiddenExceptionMapper`, `UnauthorizedExceptionMapper` | Aucune nouvelle classe mapper à créer — réutiliser |
| `@PerUserRateLimit(name=…, max=…)` (annotation interceptor binding) | [`PerUserRateLimit.java`](backend/src/main/java/ch/unige/events/config/PerUserRateLimit.java) | 30-43 | `@Nonbinding String name() / int max() / int windowSeconds() default 60` | Annoter `POST /events/{id}/comments` avec `name="comments.post"`, `max=10` (cf. décision 22) |
| Usage rate-limit existant pour mutation user | [`UserResource.java`](backend/src/main/java/ch/unige/events/resource/UserResource.java) | 229 | `@PerUserRateLimit(name = "users.updateMe", max = 10)` | Référence directe pour la calibration `comments.post` (max 10) |
| Pagination `@DefaultValue` + `@Min`/`@Positive`/`@Max` | [`UserResource.java`](backend/src/main/java/ch/unige/events/resource/UserResource.java) | 293-294 | `page=0/size=20`, max=100 | Identique pour `GET /events/{id}/comments` |
| Split en deux Resources sur racines `@Path` distinctes | [`FollowResource.java`](backend/src/main/java/ch/unige/events/resource/FollowResource.java) + [`FollowRequestResource.java`](backend/src/main/java/ch/unige/events/resource/FollowRequestResource.java) | 42 / 24 | `@Path("/users")` + `@Path("/follow-requests")` | **Modèle direct** pour `CommentResource` (`/events`) + `CommentDirectResource` (`/comments`) — cf. décision 25 |
| Préfixe API `/api` apposé par config | [`application.properties`](backend/src/main/resources/application.properties) | 1 | `quarkus.http.root-path=api` | Les `@Path` JAX-RS restent relatifs (`/events`, `/comments`) |
| DTO factory enrichie + factory réduite | [`UserPublicResponse.java`](backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java) | 9-88 | `from(User)` / `from(User, …)` / `fromAnonymous(User)` — 3 factories | Modèle direct pour `CommentDTO.from(...)` + `CommentDTO.fromTopLevelWithReplies(...)` |

### 4.3 Tests

| Domaine | Fichier | Lignes | Pattern à mimer | Note d'adaptation |
|---|---|---|---|---|
| Test entité `@QuarkusTest` + `@PrePersist` | [`ReportTest.java`](backend/src/test/java/ch/unige/events/entity/ReportTest.java) | 3-27 | `report.prePersist()` puis `assertNotNull(createdAt)` ; `defaultStatus_isPending()` ; `fieldsAreAssignable()` | `CommentTest.java` |
| Service mock `@Mock @ApplicationScoped extends Service` | [`ReportServiceMock.java`](backend/src/test/java/ch/unige/events/service/ReportServiceMock.java) | 17-72 | `volatile boolean force*` + `reset()` | `CommentServiceMock.java` |
| Service coverage `@TestProfile(ShareServiceCoverageProfile.class)` + `@TestTransaction` | [`ReportServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/ReportServiceCoverageTest.java) | 29-63 | helpers `persistUser`/`persistEvent` ; assertions `WebApplicationException.getResponse().getStatus()` | `CommentServiceCoverageTest.java` |
| Profile coverage `quarkus.arc.exclude-types` | [`ShareServiceCoverageProfile.java`](backend/src/test/java/ch/unige/events/service/ShareServiceCoverageProfile.java) | 13-31 | liste de mocks à exclure | **Ajouter `ch.unige.events.service.CommentServiceMock`** (cf. décision 27) |
| Test Resource `@QuarkusTest` + `@TestSecurity(user="auth0\|alice")` + RestAssured | [`ReportResourceTest.java`](backend/src/test/java/ch/unige/events/resource/ReportResourceTest.java) | 1-65 | JSON Hamcrest matchers ; `BeforeEach reset()` ; `body("error", equalTo("…"))` | `CommentResourceTest.java` + `CommentDirectResourceTest.java` |

---

## Plan d'implémentation par étape (ordre strict — openapi-first)

### Étape 0 — `openapi/openapi.yaml` (EN PREMIER, règle d'or)

**Règle d'or projet** : modifier `openapi/openapi.yaml` AVANT toute ligne de code Java ([`backend/AGENTS.md` lignes 77-80](backend/AGENTS.md#L77-L80)).

**0.1 — Tag `comments`.** Le projet n'a pas de section `tags:` globale ; les tags sont déclarés par usage dans les `paths`. Les 3 nouveaux endpoints utiliseront `tags: [comments]`.

**0.2 — Schéma `CommentDTO`** (à insérer dans `components.schemas`, juste après `FollowDTO` ligne ~907) :

```yaml
    CommentDTO:
      type: object
      description: |
        Représentation d'un commentaire d'événement (SCRUM-139).
        - `POST /events/{eventId}/comments` (201) → `CommentDTO` (replies vide).
        - `GET /events/{eventId}/comments` (200) → `List<CommentDTO>` paginée sur les top-level,
          replies imbriquées dans `replies[]` (max 1 niveau de profondeur).

        `authorIsOrganizer` est `true` quand l'auteur est le créateur de l'event OU un
        co-organisateur ACCEPTED (cascade SCRUM-136).

        `likedByMe` est toujours `false` en S6 — sera enrichi par SCRUM-144 (S7) une fois
        l'entité `CommentLike` livrée.
      properties:
        id:
          type: integer
          format: int64
          description: PK séquentielle (Long, sequence `comments_seq`).
        content:
          type: string
          maxLength: 2000
          description: Contenu textuel du commentaire (TEXT côté DB, trimmé côté service).
        authorId:
          type: string
          format: uuid
          nullable: true
          description: |
            UUID du `User` auteur. Nullable seulement par défense (en pratique l'auteur est NOT NULL en base).
        authorDisplayName:
          type: string
          nullable: true
          description: |
            `displayName` de l'auteur — projeté pour éviter un round-trip `GET /users/{id}` côté front.
        authorAvatarUrl:
          type: string
          nullable: true
          description: URL absolue de l'avatar de l'auteur.
        authorIsOrganizer:
          type: boolean
          description: |
            `true` si l'auteur est le créateur de l'event ou un co-organisateur ACCEPTED.
            Front affiche un badge « Organisateur » (SCRUM-146).
        likeCount:
          type: integer
          format: int32
          minimum: 0
          description: Nombre de likes — toujours `0` en S6 (mutation livrée par SCRUM-144).
        likedByMe:
          type: boolean
          description: |
            Toujours `false` en S6. SCRUM-144 (S7) le remontera à `true` quand le `CommentLike`
            existera pour le caller.
        createdAt:
          type: string
          format: date-time
        parentCommentId:
          type: integer
          format: int64
          nullable: true
          description: |
            `null` pour un commentaire top-level. Renseigné pour les replies (max 1 niveau).
        replies:
          type: array
          items:
            $ref: '#/components/schemas/CommentDTO'
          description: |
            Vide pour les replies elles-mêmes (1 niveau max). Chargées en bulk côté backend
            pour éviter le N+1 (cf. spec SCRUM-139 décision 27).
      required: [id, content, authorIsOrganizer, likeCount, likedByMe, createdAt, replies]
```

**0.3 — Schéma `CreateCommentRequest`** :

```yaml
    CreateCommentRequest:
      type: object
      description: |
        Body de `POST /api/events/{eventId}/comments` (SCRUM-139).
        `parentCommentId` optionnel (null = commentaire top-level).
      required: [content]
      properties:
        content:
          type: string
          minLength: 1
          maxLength: 2000
          description: Texte du commentaire. Trimmé côté service avant persistance.
        parentCommentId:
          type: integer
          format: int64
          nullable: true
          description: |
            ID du commentaire parent (top-level). `null` pour un commentaire de premier niveau.
            Si renseigné, le service vérifie que le parent existe ET appartient au même event ET
            n'est pas lui-même une reply (max 1 niveau, cf. décision 7).
```

**0.4 — Path `/events/{eventId}/comments` (POST + GET)** — inséré à proximité de `/events/{id}/report` (ligne ~3300) :

```yaml
  /events/{eventId}/comments:
    post:
      summary: Poster un commentaire sur un événement (SCRUM-139)
      description: |
        Crée un commentaire (`Comment`) sur l'event ciblé (`eventId`), au nom du caller authentifié.

        Si `parentCommentId` est renseigné, le commentaire est une **reply** au commentaire
        identifié — celui-ci doit appartenir au même event ET être un top-level (profondeur max 1).

        **Visibilité** : passe par `EventService.getById(eventId, callerAuth0Id, isAdmin)`.
        Un event invisible (DRAFT non-créateur, CANCELLED non-créateur, BANNED, id inconnu)
        retourne `404 not_found` (anti-oracle ISSUE-92).

        Codes d'erreur :
        - `400 cannot_comment_draft_event` : POST par créateur/co-org/admin sur event DRAFT.
        - `400 cannot_comment_cancelled_event` : event CANCELLED.
        - `400 cannot_comment_expired_event` : event EXPIRED.
        - `401 unauthorized` : token absent ou invalide.
        - `404 not_found` : event invisible (DRAFT non-créateur, CANCELLED non-créateur, BANNED, id inconnu).
        - `404 parent_comment_not_found` : `parentCommentId` inexistant.
        - `422 replies_too_deep` : `parentCommentId` réfère un commentaire qui a déjà un parent.
        - `422 parent_comment_not_in_event` : `parentCommentId` réfère un commentaire d'un autre event.
        - `429 rate_limited` : `@PerUserRateLimit(name="comments.post", max=10, windowSeconds=60)` dépassé.
      operationId: postComment
      tags: [comments]
      security:
        - BearerAuth: []
      parameters:
        - name: eventId
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
              $ref: '#/components/schemas/CreateCommentRequest'
      responses:
        '201':
          description: Commentaire créé (replies vide)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CommentDTO'
        '400':
          description: Event non commentable (DRAFT/CANCELLED/EXPIRED)
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
          description: Event invisible OU `parentCommentId` inexistant
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '422':
          description: Profondeur de reply dépassée OU parent dans un autre event
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '429':
          $ref: '#/components/responses/RateLimited'

    get:
      summary: Lister les commentaires d'un événement (SCRUM-139)
      description: |
        Retourne une page paginée des commentaires top-level de l'event ciblé,
        avec leurs replies imbriquées dans `replies[]` (max 1 niveau de profondeur).
        Tri : `createdAt DESC, id DESC` (commentaires les plus récents en premier).

        **Visibilité** : héritée de `EventService.getById` — événement invisible → `404`.
        Endpoint `@PermitAll` : un anonyme reçoit la liste (l'event PUBLISHED est public).

        **Pagination** : `page` (default 0, ≥ 0), `size` (default 20, > 0, ≤ 100).
      operationId: getEventComments
      tags: [comments]
      security:
        - {}
        - BearerAuth: []
      parameters:
        - name: eventId
          in: path
          required: true
          schema:
            type: integer
            format: int64
        - name: page
          in: query
          required: false
          schema:
            type: integer
            minimum: 0
            default: 0
        - name: size
          in: query
          required: false
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
      responses:
        '200':
          description: Page de commentaires (top-level + replies imbriquées)
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/CommentDTO'
        '400':
          description: Pagination invalide (`size > 100`, `page < 0`)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ValidationErrorResponse'
        '404':
          description: Event invisible (DRAFT non-créateur, CANCELLED non-créateur, BANNED, id inconnu)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

**0.5 — Path `/comments/{commentId}` (DELETE)** — inséré à la fin de `paths:`, juste après le bloc follow-requests :

```yaml
  /comments/{commentId}:
    delete:
      summary: Supprimer un commentaire (SCRUM-139)
      description: |
        Supprime physiquement le commentaire (`commentId`). DELETE physique — la row part
        définitivement. Si le commentaire avait des replies, celles-ci restent (le front
        sont conservées avec leur `parent_comment_id` mis à NULL via la clause
        `ON DELETE SET NULL` de la FK `fk_comments_parent` ; au prochain
        `GET /events/{eventId}/comments` elles apparaissent en top-level avec
        `parentCommentId: null`).

        **Autorisé pour** :
        1. l'auteur du commentaire (`comment.author.id == caller.id`),
        2. le créateur de l'event (`comment.event.creator.id == caller.id`),
        3. un co-organisateur ACCEPTED de l'event (cascade SCRUM-136),
        4. un utilisateur ADMIN (claim Auth0).

        Codes d'erreur :
        - `401 unauthorized` : token absent ou invalide.
        - `403 forbidden` : caller authentifié mais non autorisé.
        - `404 comment_not_found` : `commentId` inexistant.
      operationId: deleteComment
      tags: [comments]
      security:
        - BearerAuth: []
      parameters:
        - name: commentId
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '204':
          description: Commentaire supprimé (no body)
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '403':
          description: Caller non autorisé (`error=forbidden`)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Commentaire inexistant (`error=comment_not_found`)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

**Checks intermédiaires :**
- YAML valide (lint via `yamllint openapi/openapi.yaml` ou simplement `git diff` qui doit afficher le diff complet sans erreur de parsing).
- `git diff --stat openapi/` non-vide.

**Commit suggéré.** `docs(scrum-139): add OpenAPI paths and schemas for comments`

---

### Étape 1 — Migration Flyway `V15__create_comments.sql`

**Fichier à créer.** `backend/src/main/resources/db/migration/V15__create_comments.sql`

**Pré-check obligatoire** :
```bash
docker exec -w /workspace unige-events-app-1 bash -c "ls backend/src/main/resources/db/migration | sort"
```
Si `V15` est déjà pris (PR concurrente mergée), basculer en `V16` et adapter toutes les références ci-dessous.

**Contenu** :

```sql
-- SCRUM-139 — Création de la table comments : commentaires sur événements,
-- avec support du threading (1 niveau, parent_comment_id auto-référent nullable).
-- FK vers events(id) et users(id) sans cascade — pattern défensif assumé.

CREATE SEQUENCE IF NOT EXISTS comments_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS comments (
    id                BIGINT       NOT NULL DEFAULT nextval('comments_seq'),
    event_id          BIGINT       NOT NULL,
    author_id         UUID         NOT NULL,
    parent_comment_id BIGINT,
    content           TEXT         NOT NULL,
    like_count        INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    CONSTRAINT pk_comments         PRIMARY KEY (id),
    CONSTRAINT fk_comments_event   FOREIGN KEY (event_id)          REFERENCES events(id),
    CONSTRAINT fk_comments_author  FOREIGN KEY (author_id)         REFERENCES users(id),
    CONSTRAINT fk_comments_parent  FOREIGN KEY (parent_comment_id) REFERENCES comments(id)
);

-- Indexes — cf. spec SCRUM-139 décision 11.
CREATE INDEX IF NOT EXISTS idx_comment_event         ON comments(event_id);
CREATE INDEX IF NOT EXISTS idx_comment_parent        ON comments(parent_comment_id);
CREATE INDEX IF NOT EXISTS idx_comment_event_created ON comments(event_id, created_at DESC);
```

**Checks intermédiaires.**
- `./mvnw verify` (via devcontainer) — Flyway doit appliquer V15 au démarrage de DevServices PostgreSQL.
- `git diff --stat backend/src/main/resources/db/migration/` doit montrer `V15__create_comments.sql` ajouté.
- V1..V14 strictement inchangées (`git diff backend/src/main/resources/db/migration/V1*.sql backend/src/main/resources/db/migration/V14*.sql` doit être vide).

**Commit suggéré.** `feat(scrum-139): add V15 migration for comments table`

---

### Étape 2 — Entité `Comment`

**Fichier à créer.** `backend/src/main/java/ch/unige/events/entity/Comment.java`

**Contenu** :

```java
package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "comments",
        indexes = {
                @Index(name = "idx_comment_event",         columnList = "event_id"),
                @Index(name = "idx_comment_parent",        columnList = "parent_comment_id"),
                @Index(name = "idx_comment_event_created", columnList = "event_id, created_at DESC")
        }
)
public class Comment extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    public Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    public User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    public Comment parentComment;

    @Column(columnDefinition = "TEXT", nullable = false)
    @NotBlank
    @Size(max = 2000)
    public String content;

    @Column(name = "like_count", nullable = false)
    public int likeCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
```

**Notes.**
- Pas de finder statique en S6 — toutes les requêtes vivent côté `CommentService` via `Comment.<Comment>find(...)`. Si SCRUM-144 / SCRUM-146 demandent un finder réutilisable, il sera ajouté au moment où le besoin émerge (KISS).
- Le `if (createdAt == null)` est volontairement défensif (cohérent avec [`Follow.java:38-43`](backend/src/main/java/ch/unige/events/entity/Follow.java#L38-L43) et [`EventCoOrganizer.java:46-53`](backend/src/main/java/ch/unige/events/entity/EventCoOrganizer.java#L46-L53)) — permet aux fixtures de tests d'imposer un `createdAt` déterministe.

**Checks intermédiaires.**
- `./mvnw compile` doit passer.
- Hibernate `validate` au démarrage doit confirmer que l'entité matche le schéma V15.

**Commit suggéré.** `feat(scrum-139): add Comment entity with author, event, parent relations`

---

### Étape 3 — DTOs `CommentDTO` + `CreateCommentRequest`

**Fichiers à créer.**
- `backend/src/main/java/ch/unige/events/dto/comment/CommentDTO.java`
- `backend/src/main/java/ch/unige/events/dto/comment/CreateCommentRequest.java`

**`CommentDTO.java`** :

```java
package ch.unige.events.dto.comment;

import ch.unige.events.entity.Comment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CommentDTO(
        Long id,
        String content,
        UUID authorId,
        String authorDisplayName,
        String authorAvatarUrl,
        boolean authorIsOrganizer,
        int likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        Long parentCommentId,
        List<CommentDTO> replies
) {

    public static CommentDTO from(Comment c, boolean authorIsOrganizer) {
        return new CommentDTO(
                c.id,
                c.content,
                c.author != null ? c.author.id : null,
                c.author != null ? c.author.displayName : null,
                c.author != null ? c.author.avatarUrl : null,
                authorIsOrganizer,
                c.likeCount,
                false,
                c.createdAt,
                c.parentComment != null ? c.parentComment.id : null,
                List.of()
        );
    }

    public static CommentDTO fromTopLevelWithReplies(
            Comment top,
            List<Comment> replies,
            boolean topAuthorIsOrganizer,
            Map<UUID, Boolean> repliesAuthorIsOrganizer
    ) {
        List<CommentDTO> replyDTOs = replies.stream()
                .map(r -> CommentDTO.from(
                        r,
                        r.author != null
                                && repliesAuthorIsOrganizer.getOrDefault(r.author.id, false)))
                .toList();
        return new CommentDTO(
                top.id,
                top.content,
                top.author != null ? top.author.id : null,
                top.author != null ? top.author.displayName : null,
                top.author != null ? top.author.avatarUrl : null,
                topAuthorIsOrganizer,
                top.likeCount,
                false,
                top.createdAt,
                null,
                replyDTOs
        );
    }
}
```

**`CreateCommentRequest.java`** :

```java
package ch.unige.events.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank
        @Size(max = 2000)
        String content,
        Long parentCommentId
) {}
```

**Commit suggéré.** `feat(scrum-139): add CommentDTO and CreateCommentRequest`

---

### Étape 4 — Service `CommentService`

**Fichier à créer.** `backend/src/main/java/ch/unige/events/service/CommentService.java`

**Contenu (forme — les corps des helpers privés sont conformes au pattern `ReportService.java:141-163`)** :

```java
package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.comment.CommentDTO;
import ch.unige.events.dto.comment.CreateCommentRequest;
import ch.unige.events.entity.Comment;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCoOrganizer;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class CommentService {

    @Inject EventService eventService;
    @Inject SecurityIdentity identity;

    // ── Mutations (toutes @Transactional) ──────────────────────────────────────

    @Transactional
    public CommentDTO post(String auth0Id, Long eventId, CreateCommentRequest request) {
        boolean isAdmin = identity.hasRole("ADMIN");

        // Garde anti-oracle ISSUE-92 : event invisible → 404 (cf. décision 14).
        eventService.getById(eventId, auth0Id, isAdmin);

        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        // Branchement par statut métier (cf. décision 15).
        if (event.status == EventStatus.DRAFT) {
            throw badRequest("cannot_comment_draft_event",
                    "An event must be PUBLISHED before it accepts comments.");
        }
        if (event.status == EventStatus.CANCELLED) {
            throw badRequest("cannot_comment_cancelled_event",
                    "Cannot comment a cancelled event.");
        }
        if (event.status == EventStatus.EXPIRED) {
            throw badRequest("cannot_comment_expired_event",
                    "Cannot comment an expired event.");
        }

        User author = User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        Comment parent = null;
        if (request.parentCommentId() != null) {
            parent = Comment.<Comment>findByIdOptional(request.parentCommentId())
                    .orElseThrow(() -> notFound("parent_comment_not_found",
                            "The parent comment does not exist."));
            if (parent.event == null || !parent.event.id.equals(eventId)) {
                throw unprocessable("parent_comment_not_in_event",
                        "The parent comment belongs to a different event.");
            }
            if (parent.parentComment != null) {
                throw unprocessable("replies_too_deep",
                        "Replies are limited to one level of depth.");
            }
        }

        Comment comment = new Comment();
        comment.event = event;
        comment.author = author;
        comment.parentComment = parent;
        comment.content = request.content().strip();
        comment.likeCount = 0;
        comment.persist();

        boolean authorIsOrganizer = eventService.isCreatorOrAcceptedCoOrganizerPublic(event, auth0Id);
        return CommentDTO.from(comment, authorIsOrganizer);
    }

    @Transactional
    public void delete(String auth0Id, Long commentId) {
        Comment comment = Comment.<Comment>findByIdOptional(commentId)
                .orElseThrow(() -> notFound("comment_not_found",
                        "The comment does not exist."));

        boolean isAdmin = identity.hasRole("ADMIN");
        boolean isAuthor = comment.author != null
                && comment.author.auth0Id != null
                && comment.author.auth0Id.equals(auth0Id);
        boolean isOrganizer = eventService.isCreatorOrAcceptedCoOrganizerPublic(comment.event, auth0Id);

        if (!isAdmin && !isAuthor && !isOrganizer) {
            throw forbidden("forbidden",
                    "Only the author, an event organizer or an admin can delete this comment.");
        }

        comment.delete();
    }

    // ── Lectures (non-transactional, cf. décision 26) ──────────────────────────

    public List<CommentDTO> getByEvent(Long eventId, String auth0Id, int page, int size) {
        boolean isAdmin = auth0Id != null && identity.hasRole("ADMIN");

        // Garde anti-oracle ISSUE-92 héritée de getById (cf. décision 14).
        eventService.getById(eventId, auth0Id, isAdmin);

        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        List<Comment> topLevels = Comment.<Comment>find(
                "event.id = ?1 and parentComment is null order by createdAt desc, id desc",
                eventId
        ).page(page, size).list();

        if (topLevels.isEmpty()) {
            return List.of();
        }

        List<Long> topLevelIds = topLevels.stream().map(c -> c.id).toList();
        List<Comment> replies = Comment.list(
                "parentComment.id in ?1 order by createdAt asc, id asc",
                topLevelIds
        );
        Map<Long, List<Comment>> repliesByParent = replies.stream()
                .collect(Collectors.groupingBy(r -> r.parentComment.id));

        Set<UUID> organizerUserIds = computeOrganizerUserIds(event);

        return topLevels.stream()
                .map(top -> {
                    boolean topIsOrg = top.author != null
                            && organizerUserIds.contains(top.author.id);
                    List<Comment> rs = repliesByParent.getOrDefault(top.id, List.of());
                    Map<UUID, Boolean> repliesAuthorIsOrganizer = new HashMap<>();
                    for (Comment r : rs) {
                        if (r.author != null) {
                            repliesAuthorIsOrganizer.put(
                                    r.author.id,
                                    organizerUserIds.contains(r.author.id));
                        }
                    }
                    return CommentDTO.fromTopLevelWithReplies(top, rs, topIsOrg, repliesAuthorIsOrganizer);
                })
                .toList();
    }

    private static Set<UUID> computeOrganizerUserIds(Event event) {
        Set<UUID> ids = new HashSet<>();
        if (event.creator != null && event.creator.id != null) {
            ids.add(event.creator.id);
        }
        List<EventCoOrganizer> coOrgs = EventCoOrganizer.<EventCoOrganizer>find(
                "eventId = ?1 and status = ?2",
                event.id, CoOrganizerStatus.ACCEPTED
        ).list();
        coOrgs.forEach(co -> ids.add(co.userId));
        return ids;
    }

    // ── Helpers d'erreurs (dupliqués depuis ReportService — cf. décision 28) ───

    static WebApplicationException badRequest(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    static WebApplicationException unprocessable(String error, String message) {
        return new WebApplicationException(
                Response.status(422)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    static WebApplicationException forbidden(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.FORBIDDEN)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    static WebApplicationException notFound(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }
}
```

**Notes critiques.**
- L'injection `SecurityIdentity` au niveau du service permet de lire la claim ADMIN sans la propager via la signature (`getByEvent(...)` reste signé `(Long, String, int, int)` côté Resource — pas de paramètre `boolean isAdmin` exposé sur le endpoint).
- `comment.event` est garanti non-null par la FK `NOT NULL` (décision 5) — pas besoin de null-check défensif côté `delete`.
- Le branchement statut DRAFT (`cannot_comment_draft_event`) ne peut être atteint que par un créateur / co-org ACCEPTED / ADMIN (les autres ont déjà reçu un 404 via `getById`). C'est cohérent avec la matrice de la décision 15.

**Checks intermédiaires.**
- `./mvnw compile` doit passer.
- Pas de cycle d'injection (CommentService → EventService est OK ; EventService ne consomme pas CommentService).

**Commit suggéré.** `feat(scrum-139): add CommentService with anti-oracle visibility and depth check`

---

### Étape 5 — Resources `CommentResource` + `CommentDirectResource`

**Fichiers à créer.**
- `backend/src/main/java/ch/unige/events/resource/CommentResource.java`
- `backend/src/main/java/ch/unige/events/resource/CommentDirectResource.java`

**`CommentResource.java`** (POST + GET sous `/events`) :

```java
package ch.unige.events.resource;

import ch.unige.events.config.PerUserRateLimit;
import ch.unige.events.dto.comment.CommentDTO;
import ch.unige.events.dto.comment.CreateCommentRequest;
import ch.unige.events.service.CommentService;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Comment endpoints rooted under {@code /events}. The complementary
 * {@code DELETE /comments/{id}} lives in {@link CommentDirectResource} so each
 * Resource keeps a single, unambiguous class-level @Path
 * (cf. SCRUM-139 décision 25).
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CommentResource {

    @Inject SecurityIdentity identity;
    @Inject CommentService commentService;

    @POST
    @Path("/{eventId}/comments")
    @Authenticated
    @PerUserRateLimit(name = "comments.post", max = 10, windowSeconds = 60)
    public Response postComment(@PathParam("eventId") Long eventId,
                                @Valid @NotNull CreateCommentRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        CommentDTO created = commentService.post(auth0Id, eventId, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{eventId}/comments")
    @PermitAll
    public List<CommentDTO> getEventComments(
            @PathParam("eventId") Long eventId,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
        return commentService.getByEvent(eventId, auth0Id, page, size);
    }
}
```

**`CommentDirectResource.java`** (DELETE sous `/comments`) :

```java
package ch.unige.events.resource;

import ch.unige.events.service.CommentService;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Direct comment endpoints rooted under {@code /comments}. Hosted separately
 * from {@link CommentResource} (which is rooted under {@code /events}) because
 * each Resource keeps a single unambiguous class-level @Path
 * (cf. SCRUM-139 décision 25, pattern institué par SCRUM-138).
 */
@Path("/comments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CommentDirectResource {

    @Inject SecurityIdentity identity;
    @Inject CommentService commentService;

    @DELETE
    @Path("/{commentId}")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response deleteComment(@PathParam("commentId") Long commentId) {
        String auth0Id = identity.getPrincipal().getName();
        commentService.delete(auth0Id, commentId);
        return Response.noContent().build();
    }
}
```

**Note.** `@Consumes(MediaType.WILDCARD)` sur le DELETE est aligné sur le pattern [`FollowResource.java:67`](backend/src/main/java/ch/unige/events/resource/FollowResource.java#L67) — un DELETE n'a pas de body, on accepte n'importe quel `Content-Type` (y compris l'absence) sans renvoyer un 415.

**Checks intermédiaires.**
- `./mvnw compile` doit passer.
- Endpoints visibles dans Swagger UI à `http://localhost:8080/q/swagger-ui` (en `quarkus:dev`).

**Commit suggéré.** `feat(scrum-139): add CommentResource POST/GET and CommentDirectResource DELETE`

---

### Étape 6 — Tests

**Fichiers à créer.**
- `backend/src/test/java/ch/unige/events/entity/CommentTest.java`
- `backend/src/test/java/ch/unige/events/service/CommentServiceMock.java`
- `backend/src/test/java/ch/unige/events/service/CommentServiceCoverageTest.java`
- `backend/src/test/java/ch/unige/events/resource/CommentResourceTest.java`
- `backend/src/test/java/ch/unige/events/resource/CommentDirectResourceTest.java`

**Fichier à modifier.**
- [`backend/src/test/java/ch/unige/events/service/ShareServiceCoverageProfile.java`](backend/src/test/java/ch/unige/events/service/ShareServiceCoverageProfile.java) — **ajouter `"ch.unige.events.service.CommentServiceMock"` à la liste d'exclusion** (sinon le mock prend le dessus dans le coverage test et casse les assertions DB-backed).

**Sentinels obligatoires (≥ 25, numérotés)** :

| # | Test | Cas | Code attendu | Type de test |
|---|---|---|---|---|
| 1 | `CommentTest.prePersist_setsCreatedAt` | `comment.prePersist()` initialise `createdAt` quand null | `assertNotNull(createdAt)` | Entity unit |
| 2 | `CommentResourceTest.post_validBody_returns201` | POST sur event PUBLISHED par utilisateur connecté | `201` + body conforme `CommentDTO` (replies vide, parentCommentId null, likeCount=0, likedByMe=false) | Resource (Mock) |
| 3 | `CommentResourceTest.post_emptyContent_returns400` | POST avec `content=""` | `400` (Bean Validation `@NotBlank`) | Resource (Mock) |
| 4 | `CommentResourceTest.post_contentTooLong_returns400` | POST avec content de 2001 chars | `400` (Bean Validation `@Size(max=2000)`) | Resource (Mock) |
| 5 | `CommentServiceCoverageTest.post_eventDraftByNonCreator_returns404_antiOracle` | POST sur DRAFT par non-créateur | `404 not_found` (assertion forte : envelope identique à un eventId inconnu — cf. décision 14) | Service coverage (DB) |
| 6 | `CommentServiceCoverageTest.post_eventCancelled_returns400_cannotCommentCancelled` | POST sur event CANCELLED | `400 cannot_comment_cancelled_event` | Service coverage (DB) |
| 7 | `CommentServiceCoverageTest.post_eventBanned_returns404_antiOracle` | POST sur event BANNED par n'importe qui | `404 not_found` (hérité de `getById` — cf. SCRUM-97) | Service coverage (DB) |
| 8 | `CommentServiceCoverageTest.post_replyToReply_returns422_repliesTooDeep` | POST avec `parentCommentId` d'un commentaire qui a déjà un parent | `422 replies_too_deep` | Service coverage (DB) |
| 9 | `CommentServiceCoverageTest.post_parentInOtherEvent_returns422_parentNotInEvent` | POST avec `parentCommentId` pointant un commentaire d'un autre event | `422 parent_comment_not_in_event` | Service coverage (DB) |
| 10 | `CommentServiceCoverageTest.post_unknownParent_returns404_parentNotFound` | POST avec `parentCommentId` inexistant | `404 parent_comment_not_found` | Service coverage (DB) |
| 11 | `CommentResourceTest.post_rateLimitedAt11thCallIn60s_returns429` | 11 POST en < 60 s par le même user | la 11e renvoie `429` (couverture noire — délégué à l'interceptor existant ; sentinel skippable si le helper `RateLimitTest` ne s'étend pas trivialement à `comments.post` — dans ce cas, **noter explicitement « couvert par les tests `users.updateMe` » dans le commit**) | Resource (Mock) — best effort |
| 12 | `CommentResourceTest.post_unauthenticated_returns401` | POST sans token JWT | `401` (mapper standard via `@Authenticated`) | Resource (Mock) |
| 13 | `CommentResourceTest.get_publishedEvent_returns200_paginatedTopLevelWithReplies` | GET sur event PUBLISHED | `200` + tri `createdAt DESC, id DESC` + replies imbriquées dans `replies[]` | Resource (Mock) |
| 14 | `CommentResourceTest.get_publishedEventByAnonymous_returns200_permitAll` | GET sur event PUBLISHED sans token | `200` (vérifie `@PermitAll`) | Resource (Mock) |
| 15 | `CommentServiceCoverageTest.get_draftEventByNonCreator_returns404_antiOracle` | GET sur event DRAFT par non-créateur | `404 not_found` (hérité de `getById`) | Service coverage (DB) |
| 16 | `CommentServiceCoverageTest.get_draftEventByCreator_returns200` | GET sur event DRAFT par son créateur | `200` + page de commentaires | Service coverage (DB) |
| 17 | `CommentServiceCoverageTest.get_pageSizeOrdersCorrectly` | GET avec `page=0&size=2` sur 5 commentaires | retourne 2 max + ordre `createdAt DESC, id DESC` | Service coverage (DB) |
| 18 | `CommentResourceTest.get_sizeOver100_returns400` | GET avec `size=101` | `400` (Bean Validation `@Max(100)`) | Resource (Mock) |
| 19 | `CommentDirectResourceTest.delete_byAuthor_returns204` | DELETE par l'auteur du commentaire | `204` + row absente | Resource + Service coverage |
| 20 | `CommentServiceCoverageTest.delete_byEventCreator_returns204` | DELETE par le créateur de l'event (différent de l'auteur) | `204` (autorisation par cascade créateur — cf. décision 16) | Service coverage (DB) |
| 21 | `CommentServiceCoverageTest.delete_byAcceptedCoOrganizer_returns204` | DELETE par un co-organisateur ACCEPTED | `204` (cascade SCRUM-136 — `isCreatorOrAcceptedCoOrganizerPublic`) | Service coverage (DB) |
| 22 | `CommentServiceCoverageTest.delete_byPendingCoOrganizer_returns403` | DELETE par un co-organisateur PENDING | `403 forbidden` (PENDING n'est pas dans la cascade) | Service coverage (DB) |
| 23 | `CommentDirectResourceTest.delete_byThirdParty_returns403_forbidden` | DELETE par un tiers (ni auteur, ni créateur, ni co-org, ni admin) | `403` + `error=forbidden` | Resource (Mock) |
| 24 | `CommentDirectResourceTest.delete_unknownComment_returns404_commentNotFound` | DELETE sur `commentId` inexistant par utilisateur connecté | `404` + `error=comment_not_found` | Resource (Mock) |
| 25 | `CommentDirectResourceTest.delete_unauthenticated_returns401` | DELETE sans token | `401` | Resource (Mock) |
| 26 | `CommentServiceCoverageTest.delete_byAdmin_returns204` | DELETE par un user qui a la claim ADMIN | `204` (cascade admin — cf. décision 16) | Service coverage (DB) |

**Anti-tests / pièges à éviter** :
- **Anti-test (mémoire SCRUM-138)** : ne **PAS** mettre l'attribut `attributes = "email=..."` dans `@TestSecurity` sauf pour un test qui déclenche `getOrCreateUser` — sinon le claim email leak entre tests via le proxy CDI et casse les assertions sur `comment.author.email`.
- **Profile coverage** : `ShareServiceCoverageProfile` doit **inclure** `"ch.unige.events.service.CommentServiceMock"` dans `quarkus.arc.exclude-types`. Sinon le mock CDI shadow le service réel pendant le coverage test et toutes les assertions DB-backed deviennent triviales.

**Patterns de test (snippets-clés)** :

```java
// CommentTest.java
@QuarkusTest
class CommentTest {

    @Test
    void prePersist_setsCreatedAt() {
        Comment c = new Comment();
        assertNull(c.createdAt);

        c.prePersist();

        assertNotNull(c.createdAt);
    }

    @Test
    void prePersist_doesNotOverrideExistingCreatedAt() {
        Comment c = new Comment();
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 12, 0);
        c.createdAt = fixed;

        c.prePersist();

        assertEquals(fixed, c.createdAt);
    }

    @Test
    void fieldsAreAssignable() {
        Comment c = new Comment();
        c.content = "Hello";
        c.likeCount = 0;
        assertEquals("Hello", c.content);
        assertEquals(0, c.likeCount);
    }
}
```

```java
// CommentServiceMock.java — pattern aligné sur ReportServiceMock
@Mock
@ApplicationScoped
public class CommentServiceMock extends CommentService {

    public static volatile boolean forceCannotCommentDraft = false;
    public static volatile boolean forceCannotCommentCancelled = false;
    public static volatile boolean forceCannotCommentExpired = false;
    public static volatile boolean forceRepliesTooDeep = false;
    public static volatile boolean forceParentNotInEvent = false;
    public static volatile boolean forceParentNotFound = false;
    public static volatile boolean forceNotFoundOnEvent = false;
    public static volatile boolean forceCommentNotFound = false;
    public static volatile boolean forceForbiddenOnDelete = false;
    public static volatile List<CommentDTO> nextGetByEventResponse = List.of();

    public void reset() { /* reset all flags + nextGetByEventResponse */ }

    @Override public CommentDTO post(String auth0Id, Long eventId, CreateCommentRequest request) { /* branche selon flags */ }
    @Override public List<CommentDTO> getByEvent(Long eventId, String auth0Id, int page, int size) { /* renvoie nextGetByEventResponse, sauf forceNotFoundOnEvent */ }
    @Override public void delete(String auth0Id, Long commentId) { /* branche selon flags */ }
}
```

**Checks intermédiaires.**
- `./mvnw verify` (via devcontainer) doit passer.
- Couverture JaCoCo > 90 % sur `Comment`, `CommentDTO`, `CommentService`, `CommentResource`, `CommentDirectResource`.
- Sentinels listés ci-dessus tous verts nommément.

**Commit suggéré.** `test(scrum-139): cover entity, service mock and coverage, resource sentinels`

---

### Étape 7 — Documentation

**Fichiers à modifier.**

**`backend/docs/data-model.md`** — ajouter une nouvelle section `### Comment` (entre `### Follow` et `### Report`, ou à la fin si l'ordre alphabétique n'est pas respecté). Inclure :
- Tableau des champs Java / nom JSON / type / colonne DB / contraintes (cf. format de la section `### Event` lignes 44-71).
- Section sur les indexes DB (`idx_comment_event`, `idx_comment_parent`, `idx_comment_event_created`).
- Note sur la profondeur max 1 niveau (parentComment.parentComment doit être null).
- Note sur la sémantique du DELETE physique (replies orphelines, fallback front).
- Référence à la cascade SCRUM-136 pour le calcul de `authorIsOrganizer` (côté DTO).

**`backend/docs/api-contract.md`** — ajouter 3 lignes dans la table « Endpoints implémentés » :

| `POST` | `/events/{id}/comments` | `@Authenticated` + `@PerUserRateLimit(max=10)` | Poster un commentaire (top-level ou reply 1 niveau) | 201, 400, 401, 404, 422, 429 |
| `GET` | `/events/{id}/comments` | `@PermitAll` | Lister les commentaires d'un event (paginé, top-level + replies imbriquées) | 200, 400, 404 |
| `DELETE` | `/comments/{id}` | `@Authenticated` | Supprimer un commentaire (auteur, créateur, co-org ACCEPTED, ou ADMIN) | 204, 401, 403, 404 |

Puis ajouter une section dédiée `### Comments (SCRUM-139)` qui détaille les règles métier (visibilité event, profondeur max 1, DELETE physique, cascade d'autorisation, rate limit).

**`backend/docs/sprint-context.md`** — insérer un bloc `## Sprint 6 — Entité Comment + 3 endpoints CRUD (SCRUM-139)` au-dessus du bloc SCRUM-138 (le plus récent), au format identique aux autres entrées « Livré » du même sprint.

**Pas de modification frontend.** `git diff --stat frontend/` doit rester strictement vide.

**Commit suggéré.** `docs(scrum-139): document Comment entity in data-model and api-contract`

---

## Ordre d'implémentation strict

1. **Branchement.** `git fetch origin && git checkout -b feature/s6-comments origin/main --no-track` (le `--no-track` est non négociable — évite que `git push` ouvre une PR sur `main` par accident).
2. **Étape 0 — OpenAPI EN PREMIER.** Modifier `openapi/openapi.yaml`. Vérifier la validité YAML. ✅ checkpoint : `git diff --stat openapi/` non-vide.
3. **Étape 1 — Migration Flyway V15.** Pré-check `ls backend/src/main/resources/db/migration | sort` (basculer en V16 si conflit). ✅ checkpoint : `./mvnw verify` (via devcontainer) — Flyway applique V15 sur DevServices PostgreSQL.
4. **Étape 2 — Entité `Comment`.** ✅ checkpoint : `./mvnw compile`.
5. **Étape 3 — DTOs `CommentDTO` + `CreateCommentRequest`.** ✅ checkpoint : `./mvnw compile`.
6. **Étape 4 — `CommentService`.** ✅ checkpoint : `./mvnw verify` (les tests existants — Report/Follow/etc. — doivent rester verts ; pas de cycle d'injection).
7. **Étape 5 — `CommentResource` + `CommentDirectResource`.** ✅ checkpoint : `./mvnw compile` ; smoke en `quarkus:dev` sur Swagger UI.
8. **Étape 6 — Tests.** Mettre à jour `ShareServiceCoverageProfile` AVANT d'écrire `CommentServiceCoverageTest` (sinon le test démarre avec le mock injecté). ✅ checkpoint final : `./mvnw verify` vert + JaCoCo > 90 % sur le diff + tous les sentinels listés verts nommément.
9. **Étape 7 — Documentation.** Mise à jour `data-model.md`, `api-contract.md`, `sprint-context.md`. **Aucune** modification frontend. ✅ checkpoint : `git diff --stat frontend/` strictement vide.

---

## Commits atomiques suggérés

Format strictement conforme à [`.github/workflows/pr-title-check.yml`](`.github/workflows/pr-title-check.yml`) (regex `^([a-z]+)\(([^)]+)\): `, scope `scrum-139` obligatoire pour `feat`/`refactor`/`perf`) :

1. `docs(scrum-139): add OpenAPI paths and schemas for comments`
2. `feat(scrum-139): add V15 migration for comments table`
3. `feat(scrum-139): add Comment entity with author, event, parent relations`
4. `feat(scrum-139): add CommentDTO and CreateCommentRequest`
5. `feat(scrum-139): add CommentService with anti-oracle visibility and depth check`
6. `feat(scrum-139): add CommentResource POST/GET and CommentDirectResource DELETE`
7. `test(scrum-139): cover entity, service mock and coverage, resource sentinels`
8. `docs(scrum-139): document Comment entity in data-model and api-contract`
9. (post-PR, si applicable) `fix(scrum-139): apply Copilot review — <description>`

Astuce : commiter avec un message conforme dès le **premier** commit de la branche → GitHub pré-remplit automatiquement le titre de PR avec.

---

## Workflow Git / PR / Copilot / CI (obligatoire)

### Pré-requis local

- **Java 21 absent du host** (mémoire `pr_access_workaround.md`). Tout `./mvnw verify` / `mvn` / `gh` / `git` lourd passe par :
  ```bash
  docker exec -w /workspace unige-events-app-1 bash -c "cd /workspace/backend && ./mvnw verify"
  ```
- Validation par étape : `./mvnw verify` doit passer après chaque commit fonctionnel.

### Avant ouverture PR

- `git diff --stat frontend/` strictement **vide**.
- `git diff --stat openapi/` **non-vide**.
- `git diff --stat backend/src/main/resources/db/migration/` contient `V15__create_comments.sql` (ou V16 si bascule).
- `pom.xml` strictement **inchangé** (aucune nouvelle dépendance).
- `ShareServiceCoverageProfile.java` modifié (ajout de `CommentServiceMock` à l'exclusion).

### Ouverture PR

1. `gh pr create` exécuté **depuis `/workspace`** dans le devcontainer.
2. Le body PR transite par fichier dédié pour éviter les soucis d'échappement de heredoc :
   ```bash
   docker exec -w /workspace unige-events-app-1 bash -c "cat > /tmp/pr-body-scrum-139.md" < /tmp/pr-body-scrum-139.md
   docker exec -w /workspace unige-events-app-1 bash -c "gh pr create --title 'feat(scrum-139): add Comment entity and event comments CRUD endpoints' --body-file /tmp/pr-body-scrum-139.md --base main --head feature/s6-comments"
   ```
   (`docker cp` peut échouer côté host selon la configuration ; le `cat` piped est l'équivalent fiable.)
3. **Titre PR EXACT** (à copier-coller, validé par `pr-title-check.yml`) :
   ```
   feat(scrum-139): add Comment entity and event comments CRUD endpoints
   ```

### Reviewer Copilot

```bash
gh pr edit <PR_NUM> --add-reviewer copilot-pull-request-reviewer
```
Si l'app n'est pas configurée comme collaborator, fallback :
```bash
gh pr comment <PR_NUM> --body "@copilot review please"
```

### Traitement commentaires Copilot

- Récupérer : `gh api repos/unige-pinfo6-2026/unige-events/pulls/<PR_NUM>/comments --paginate`.
- Pour CHAQUE commentaire :
  - Juger pertinence (alignement avec les conventions projet et les décisions tranchées de cette spec).
  - Si pertinent → corriger dans un commit `fix(scrum-139): …` + push + **répondre au commentaire** avec un lien vers le SHA.
  - Si non-pertinent → **répondre poliment** en justifiant pourquoi la remarque n'est pas appliquée.
  - **Ne jamais ignorer silencieusement un commentaire.**

### Surveillance CI

```bash
gh pr checks <PR_NUM> --watch
```
Jusqu'à **toutes vertes** ET **SonarCloud Quality Gate vert**. Si une check échoue :
- Lire les logs : `gh run view <RUN_ID> --log-failed`.
- **Fix root cause** : pas de `--no-verify`, pas de `@Disabled`, pas de skip de check sous prétexte de fix « ultérieur ».
- Commit + push, surveiller à nouveau.

### Ne PAS merger

L'utilisateur (Elie) merge lui-même après validation finale.

---

## Critères de done

- [ ] OpenAPI `openapi/openapi.yaml` met à jour 3 paths + 2 schémas (`CommentDTO`, `CreateCommentRequest`) + tag `comments`.
- [ ] `V15__create_comments.sql` (ou `V16` si bascule) créée, V1..V14 strictement intactes.
- [ ] `Comment.java`, `CommentDTO.java`, `CreateCommentRequest.java`, `CommentService.java`, `CommentResource.java`, `CommentDirectResource.java` créés.
- [ ] Tests : 5 fichiers (`CommentTest`, `CommentServiceMock`, `CommentServiceCoverageTest`, `CommentResourceTest`, `CommentDirectResourceTest`), **≥ 25 sentinels** verts, **> 90 %** coverage JaCoCo sur le diff.
- [ ] `CommentServiceMock` ajouté à la liste d'exclusion de [`ShareServiceCoverageProfile.java`](backend/src/test/java/ch/unige/events/service/ShareServiceCoverageProfile.java).
- [ ] `backend/docs/data-model.md`, `backend/docs/api-contract.md`, `backend/docs/sprint-context.md` mis à jour dans le **même commit** que le code (ou dans un commit `docs(scrum-139):` dédié).
- [ ] `git diff --stat frontend/` strictement vide.
- [ ] `pom.xml` strictement inchangé.
- [ ] `./mvnw verify` passe (via devcontainer).
- [ ] Sentinels nommément verts (extraits clés) : `prePersist_setsCreatedAt`, `post_validBody_returns201`, `post_eventDraftByNonCreator_returns404_antiOracle`, `post_eventBanned_returns404_antiOracle`, `post_replyToReply_returns422_repliesTooDeep`, `post_parentInOtherEvent_returns422_parentNotInEvent`, `post_unknownParent_returns404_parentNotFound`, `get_publishedEventByAnonymous_returns200_permitAll`, `get_draftEventByNonCreator_returns404_antiOracle`, `delete_byAuthor_returns204`, `delete_byEventCreator_returns204`, `delete_byAcceptedCoOrganizer_returns204`, `delete_byPendingCoOrganizer_returns403`, `delete_byThirdParty_returns403_forbidden`, `delete_unknownComment_returns404_commentNotFound`, `delete_byAdmin_returns204`.
- [ ] Branche `feature/s6-comments` créée depuis `origin/main` avec `--no-track`.
- [ ] PR ouverte, **titre EXACT** : `feat(scrum-139): add Comment entity and event comments CRUD endpoints`.
- [ ] Body PR conforme `.github/pull_request_template.md` (sections obligatoires Résumé / Changements / Tests / Test plan / Documentation).
- [ ] Reviewer Copilot demandé.
- [ ] Tous les checks GitHub Actions verts (`Lint PR title`, build backend, build frontend no-op, Sonar) + **SonarCloud Quality Gate vert**.
- [ ] PR **non mergée** par l'agent — l'utilisateur (Elie) merge lui-même.

---

## Interdits stricts

- ❌ PAS de modification frontend (`git diff --stat frontend/` strictement vide).
- ❌ PAS de modification des migrations V1..V14 (immutables).
- ❌ PAS de notification émise (Quarkus event, Notification entity, fan-out async). Délégué à SCRUM-145 (S7+).
- ❌ PAS d'incrémentation de `likeCount` (mutation déléguée à SCRUM-144 S7).
- ❌ PAS d'endpoint `PUT /comments/{id}` (édition non supportée — UX = supprimer + reposter).
- ❌ PAS de soft-delete (`deletedAt`) — DELETE physique uniquement.
- ❌ PAS de cascade `ON DELETE` sur les FK de `comments` (replies restent orphelines).
- ❌ PAS de profondeur > 1 niveau (`parentComment.parentComment` non-null → 422).
- ❌ PAS d'extraction préventive de `ApiErrors` en util statique partagée — duplication acceptée dans `CommentService` (cf. décision 28).
- ❌ PAS de TODO commenté dans le code livré.
- ❌ PAS de `--no-verify`, pas de `@Disabled`, pas de skip de check CI sous prétexte de fix « ultérieur ».
- ❌ PAS de force-push sur `feature/s6-comments` pendant la review (utiliser des commits additifs).
- ❌ PAS de merge de la PR par l'agent — Elie s'en charge.

---

## Conventions à respecter

- camelCase partout en Java, JSON, OpenAPI. Hibernate convertit en snake_case côté DB.
- Pas de préfixe `is` sur les booléens d'**entités JPA** (n/a — aucun nouveau booléen sur `Comment`). Le DTO `CommentDTO.authorIsOrganizer` conserve le préfixe `is` (cf. décision 24 — DTO, pas entité).
- Constructor injection ou `@Inject` field-style (pattern existant) sur `CommentResource` / `CommentDirectResource` — homogène avec les autres Resources du projet.
- `@Transactional` sur toutes les **mutations** Service ; lectures non-transactionnelles (cf. décision 26).
- `@Authenticated` sur `POST /events/{id}/comments` et `DELETE /comments/{id}`. `@PermitAll` sur `GET /events/{id}/comments`.
- `@PerUserRateLimit(name="comments.post", max=10, windowSeconds=60)` uniquement sur le POST.
- `@PathParam Long eventId` pour `/events/{eventId}/comments`, `@PathParam Long commentId` pour `/comments/{commentId}`.
- Pagination identique au reste du projet : `@DefaultValue("0") @Min(0) page`, `@DefaultValue("20") @Positive @Max(100) size`.
- Codes d'erreur custom dans le champ `error` de l'envelope `ApiErrorResponse` : `cannot_comment_draft_event`, `cannot_comment_cancelled_event`, `cannot_comment_expired_event`, `replies_too_deep`, `parent_comment_not_in_event`, `parent_comment_not_found`, `comment_not_found`, `forbidden`. Codes 4xx/5xx standards pour les autres.
- Couverture JaCoCo ≥ 80 % sur les lignes nouvelles (cible **> 90 %** sur les fichiers SCRUM-139), duplication < 3 %, ratings A.
- Doc mise à jour dans le **même commit** que le code correspondant (ou commit `docs(scrum-139):` dédié).
- Commits atomiques `feat(scrum-139): …`, `test(scrum-139): …`, `docs(scrum-139): …`, `fix(scrum-139): …`.
- Titre PR EXACT : `feat(scrum-139): add Comment entity and event comments CRUD endpoints`.

---

## Livrable FINAL attendu

### Titre PR EXACT

```
feat(scrum-139): add Comment entity and event comments CRUD endpoints
```

### Description PR (à coller dans le textarea — respecte strictement [`.github/pull_request_template.md`](.github/pull_request_template.md))

```markdown
## Résumé

SCRUM-139 livre le socle backend des commentaires d'événement (US-22, épic SCRUM-16) :
entité `Comment` (PanacheEntity, threading 1 niveau via `parentComment` auto-référent),
3 endpoints REST (`POST /api/events/{id}/comments`, `GET /api/events/{id}/comments`,
`DELETE /api/comments/{id}`), DTOs `CommentDTO` / `CreateCommentRequest`, migration Flyway V15,
contrat OpenAPI complet. Débloque SCRUM-146 (front S7) et SCRUM-144 (likes/report-comment S7).

## Changements

### OpenAPI
- 3 nouveaux paths (`POST` + `GET /events/{id}/comments`, `DELETE /comments/{id}`).
- 2 nouveaux schémas (`CommentDTO`, `CreateCommentRequest`).
- Nouveau tag `comments`.
- Codes d'erreur documentés : 400 (`cannot_comment_draft|cancelled|expired_event`), 401, 403 (`forbidden`), 404 (`not_found`, `comment_not_found`, `parent_comment_not_found`), 422 (`replies_too_deep`, `parent_comment_not_in_event`), 429 (`rate_limited`).

### Migration
- `V15__create_comments.sql` : table `comments` (PK Long via sequence increment 50, FK NOT NULL vers `events.id` et `users.id`, FK nullable auto-référente vers `comments.id`, `content TEXT NOT NULL`, `like_count INTEGER NOT NULL DEFAULT 0`, `created_at TIMESTAMP NOT NULL`). 3 indexes : `idx_comment_event`, `idx_comment_parent`, `idx_comment_event_created` (composite descendant pour le tri du listing).

### Entité
- `Comment` (PanacheEntity, Long PK) avec `@ManyToOne(LAZY)` sur `event`, `author`, `parentComment` (nullable). `content` `@NotBlank @Size(max=2000)` mappé en TEXT. `likeCount` int default 0 (lecture seule en S6). `@PrePersist` initialise `createdAt`.

### DTOs
- `CommentDTO` (record) avec 11 champs ; deux factories : `from(Comment, boolean authorIsOrganizer)` (commentaire isolé), `fromTopLevelWithReplies(Comment, List<Comment>, boolean, Map<UUID, Boolean>)` (top-level + replies imbriquées).
- `CreateCommentRequest` (record) avec `content` (@NotBlank @Size(max=2000)) + `parentCommentId` (nullable).

### Service
- `CommentService` (@ApplicationScoped). `post()` et `delete()` `@Transactional` ; `getByEvent()` non-transactional.
- Visibilité event déléguée à `EventService.getById(...)` — anti-oracle ISSUE-92 (DRAFT/CANCELLED/BANNED non-créateur → 404, pas de leak).
- DELETE autorisé pour : auteur OU créateur OU co-organisateur ACCEPTED (cascade SCRUM-136 via `isCreatorOrAcceptedCoOrganizerPublic`) OU ADMIN.
- Profondeur replies : 1 niveau max (`parentComment.parentComment` doit être null → sinon 422 `replies_too_deep`).
- Batch-load des replies (1 query par page) + bulk `Set<UUID>` des co-organisateurs ACCEPTED pour calculer `authorIsOrganizer` sans N+1.
- Helpers d'erreurs `badRequest`/`unprocessable`/`forbidden`/`notFound` package-private locaux (pattern projet, cf. `ReportService`).

### Resources
- `CommentResource` (`@Path("/events")`) — POST + GET sur `/events/{eventId}/comments`. POST `@Authenticated` + `@PerUserRateLimit(name="comments.post", max=10, windowSeconds=60)`. GET `@PermitAll`.
- `CommentDirectResource` (`@Path("/comments")`) — DELETE sur `/comments/{commentId}` `@Authenticated`. Split en deux Resources pour respecter l'unicité du `@Path` racine (pattern SCRUM-138).

### Tests
- `CommentTest` — entité (`prePersist_setsCreatedAt`, idempotence, assignabilité).
- `CommentServiceMock` — pattern aligné `ReportServiceMock`, flags `force*` + `reset()`.
- `CommentServiceCoverageTest` — `@TestProfile(ShareServiceCoverageProfile.class)` + `@TestTransaction`, helpers `persistUser`/`persistEvent`. Sentinels DB-backed : anti-oracle 404, profondeur 422, parent-not-in-event, parent-not-found, cascade DELETE (auteur/créateur/co-org ACCEPTED/PENDING/admin), pagination DESC.
- `CommentResourceTest` + `CommentDirectResourceTest` — RestAssured + `@TestSecurity(user="auth0|alice")`. Sentinels mock-backed : 201 valide, 400 Bean Validation (empty/too long/size>100), 401, 403, 404 comment_not_found.
- `ShareServiceCoverageProfile` enrichi de `CommentServiceMock` dans la liste d'exclusion.

### Documentation
- `backend/docs/data-model.md` — section `### Comment` (champs, indexes, profondeur 1, DELETE physique, cascade authorIsOrganizer).
- `backend/docs/api-contract.md` — 3 lignes ajoutées dans la table « Endpoints implémentés » + section `### Comments (SCRUM-139)`.
- `backend/docs/sprint-context.md` — bloc `## Sprint 6 — Entité Comment + 3 endpoints CRUD (SCRUM-139)`.
- `openapi/openapi.yaml` — paths + schémas (cf. section OpenAPI ci-dessus).

## Tests

≥ 25 sentinels couvrent les chemins critiques :
- POST 201 valide, 400 Bean Validation (content vide / trop long), 400 cancel/expired, 404 anti-oracle (DRAFT non-créateur, BANNED), 422 replies_too_deep, 422 parent_comment_not_in_event, 404 parent_comment_not_found, 429 rate-limited, 401 unauthenticated.
- GET 200 paginé top-level + replies imbriquées, 200 anonyme (`@PermitAll`), 404 anti-oracle DRAFT non-créateur, 200 DRAFT créateur, 400 size>100.
- DELETE 204 par auteur / créateur / co-org ACCEPTED / admin, 403 par co-org PENDING / tiers, 404 commentId inconnu, 401 unauthenticated.

Lancer : `./mvnw verify` (via devcontainer Quarkus DevServices PostgreSQL).
Couverture JaCoCo > 90 % sur les fichiers SCRUM-139.

## Test plan

- [ ] `./mvnw verify` vert (devcontainer).
- [ ] `git diff --stat frontend/` strictement vide.
- [ ] `git diff --stat openapi/` non-vide.
- [ ] `git diff --stat backend/src/main/resources/db/migration/` contient `V15__create_comments.sql`.
- [ ] `pom.xml` inchangé.
- [ ] Smoke manuel sur `quarkus:dev` (DevServices) :
  - [ ] `POST /api/events/{publishedId}/comments` avec `content="Hello"` → 201, body conforme `CommentDTO`.
  - [ ] `POST /api/events/{publishedId}/comments` avec `parentCommentId` valide (top-level) → 201, `parentCommentId` reflété.
  - [ ] `POST /api/events/{publishedId}/comments` avec `parentCommentId` d'une reply → 422 `replies_too_deep`.
  - [ ] `POST /api/events/{draftId}/comments` par non-créateur → 404 `not_found`.
  - [ ] `GET /api/events/{publishedId}/comments` anonyme → 200, top-level paginés DESC, replies imbriquées.
  - [ ] `DELETE /api/comments/{ownId}` par l'auteur → 204.
  - [ ] `DELETE /api/comments/{otherId}` par tiers → 403 `forbidden`.
- [ ] `gh pr checks` — toutes vertes.
- [ ] SonarCloud Quality Gate vert.
- [ ] Review Copilot demandée et **chaque commentaire traité** (apply OU justifié).

## Documentation

- [x] `backend/docs/data-model.md` — section `### Comment`.
- [x] `backend/docs/api-contract.md` — 3 endpoints + section `### Comments (SCRUM-139)`.
- [x] `backend/docs/sprint-context.md` — entrée SCRUM-139 dans le sprint S6.
- [x] `openapi/openapi.yaml` — paths + schémas.

<!-- Optionnel : Why / Motivation -->
## Why / Motivation

US-22 est l'une des deux features structurantes de l'épic « Interactions communautaires »
(SCRUM-16). Sans ce socle, la page détail d'événement reste un panneau d'affichage
mono-directionnel : pas de question des participants, pas de retour, pas d'échange. Le
contrat figé ici débloque SCRUM-146 (front S7), SCRUM-144 (likes/report-comment S7) et
SCRUM-145 (notifications S7+).

<!-- Optionnel : Dépendances / ordre de merge -->
## Dépendances / ordre de merge

Aucune dépendance amont. **Cette PR débloque** : SCRUM-146 (S7, front), SCRUM-144 (S7,
likes + report-comment — l'entité `Comment` est référencée par `CommentLike.commentId` et
`Report.commentId`), SCRUM-145 (S7+, notifications NEW_COMMENT / COMMENT_MENTION — dépend
aussi de SCRUM-99 infra Notification).

<!-- Optionnel : Décisions techniques tranchées -->
## Décisions techniques tranchées

Toutes les décisions sont consignées dans [`specs_archives/specs_claude/specs_scrum-139.md`](specs_archives/specs_claude/specs_scrum-139.md). Highlights :
- `@ManyToOne(LAZY) User author` (pas `UUID authorId`) — backlog explicite + cohérence `Report.reporter`.
- Profondeur replies max 1 niveau → 422 `replies_too_deep`.
- Visibilité POST/GET déléguée à `EventService.getById` (anti-oracle ISSUE-92).
- DELETE autorisé pour auteur / créateur / co-org ACCEPTED / ADMIN. Tiers → 403.
- DELETE physique (pas de soft-delete). FK `ON DELETE SET NULL` : replies survivent et remontent en top-level (`parentCommentId: null`).
- 2 Resources avec `@Path` racines disjoints (pattern SCRUM-138 `FollowResource` + `FollowRequestResource`).
- `likeCount` exposé en lecture mais **jamais muté** ici — mutation déléguée à SCRUM-144.
- Notifications hors scope — déléguées à SCRUM-145.

<!-- Optionnel : Notes pour le reviewer -->
## Notes pour le reviewer

- L'anti-oracle ISSUE-92 est respecté à l'identique sur les 3 endpoints. Zoom sur
  `CommentService.post` et `getByEvent` — passage par `eventService.getById(...)` en
  toute première ligne, AVANT toute autre logique.
- Le DTO `CommentDTO.authorIsOrganizer` conserve le préfixe `is` (DTO, pas entité — règle
  AGENTS.md ciblée explicitement sur les entités JPA, cf. spec décision 24).
- Aucune cascade FK `ON DELETE` sur `comments.parent_comment_id` — choix tranché (cf. spec
  décision 17). Un job futur de purge des orphelins peut être ajouté en S9+ si besoin.
- `@PerUserRateLimit(name="comments.post", max=10, windowSeconds=60)` sur POST uniquement
  — anti-spam ; pas sur GET (`@PermitAll`) ni DELETE.
```

---

## Prompt de lancement d'implémentation

```
Tu vas implémenter la feature SCRUM-139 du projet UNIGE Events. La spec d'implémentation
complète et figée vit dans `specs_archives/specs_claude/specs_scrum-139.md` — c'est la
**source unique de vérité**. Toute déviation par rapport à cette spec doit être justifiée
auprès de l'utilisateur AVANT exécution.

## Working directory et environnement

- Working directory : `/workspace` dans le devcontainer Linux Debian (host : MAC via SSH).
- Java 21 absent du host → tout `./mvnw verify` / `mvn` / `gh` / `git` lourd passe par :
  `docker exec -w /workspace unige-events-app-1 bash -c "cd /workspace/backend && ./mvnw verify"`.

## Contexte projet à relire AVANT d'écrire la moindre ligne

1. `AGENTS.md`, `backend/AGENTS.md`, `backend/CLAUDE.md` — règles d'or projet (openapi-first,
   Flyway immutable, camelCase, pas de préfixe `is` sur entités, conventions PR).
2. `backend/docs/data-model.md`, `backend/docs/api-contract.md`, `backend/docs/sprint-context.md`,
   `backend/docs/architecture.md`, `openapi/openapi.yaml`.
3. `specs_archives/specs_claude/specs_scrum-139.md` — la spec, intégralement.
4. `specs_archives/specs_claude/specs_scrum-138.md` — référence de pattern (split en 2
   Resources, structure de spec, workflow PR).

## Branche cible

`feature/s6-comments` créée depuis `origin/main` avec `--no-track` (NON NÉGOCIABLE) :

```
git fetch origin && git checkout -b feature/s6-comments origin/main --no-track
```

## Ordre d'exécution strict (Étapes 0 → 8)

0. **OpenAPI EN PREMIER** — `openapi/openapi.yaml` : 3 paths + 2 schémas + tag `comments`.
   Vérifier la validité YAML.
1. **Migration Flyway** — pré-check `ls backend/src/main/resources/db/migration | sort` ;
   créer `V15__create_comments.sql` (basculer en `V16` si conflit, adapter toutes les
   références).
2. **Entité** — `Comment.java` (3 `@ManyToOne(LAZY)`, `@PrePersist`, indexes via
   `@Table(indexes=...)`).
3. **DTOs** — `CommentDTO.java` (record + 2 factories) + `CreateCommentRequest.java`
   (record + Bean Validation).
4. **Service** — `CommentService.java` (@ApplicationScoped, @Transactional sur mutations,
   garde anti-oracle via `EventService.getById`, batch-load replies + bulk
   `authorIsOrganizer`, helpers d'erreurs locaux).
5. **Resources** — `CommentResource.java` (`@Path("/events")`, POST + GET) +
   `CommentDirectResource.java` (`@Path("/comments")`, DELETE). Split obligatoire (cf.
   décision 25).
6. **Tests** — `CommentTest`, `CommentServiceMock`, `CommentServiceCoverageTest`,
   `CommentResourceTest`, `CommentDirectResourceTest`. AVANT d'écrire le coverage test :
   ajouter `"ch.unige.events.service.CommentServiceMock"` à
   `ShareServiceCoverageProfile.java`. ≥ 25 sentinels listés en spec section Étape 6,
   tous verts nommément. Couverture JaCoCo > 90 %.
7. **Documentation** — `backend/docs/data-model.md` (section `### Comment`),
   `backend/docs/api-contract.md` (3 lignes + section `### Comments (SCRUM-139)`),
   `backend/docs/sprint-context.md` (entrée S6).
8. **Vérification finale locale** — `./mvnw verify` vert + JaCoCo > 90 % + checks invariants
   (`git diff --stat frontend/` vide, `pom.xml` inchangé, V15 présente).

À chaque étape, commit + push autorisés (et recommandés). Format commits :
`feat(scrum-139): …`, `test(scrum-139): …`, `docs(scrum-139): …`, `fix(scrum-139): …`.

## Contraintes

- **PAS de modification frontend** (`git diff --stat frontend/` strictement vide).
- **OpenAPI en PREMIER** (avant toute ligne de Java).
- **Hors scope** : likes (SCRUM-144 S7), notifications NEW_COMMENT/COMMENT_MENTION
  (SCRUM-145 S7+), report de commentaires (SCRUM-144 S7), édition de commentaires
  (PUT non supporté), front (SCRUM-146 S7).
- **Pas de cascade `ON DELETE`** sur les FK de `comments` (replies orphelines tolérées).
- **Profondeur replies = 1 niveau max** ; sinon 422 `replies_too_deep`.
- **Pas de soft-delete** ; DELETE physique uniquement.
- **`likeCount` lecture seule** en S6 (mutation = SCRUM-144).
- Note : `openapi/openapi.yaml` contient un path `/events/{id}/view` dupliqué (lignes 3195
  et 3273) — c'est un artefact pré-existant **hors scope** SCRUM-139, ne pas le toucher.

## Workflow PR / Copilot / CI

1. Ouvrir la PR avec **titre EXACT** :
   `feat(scrum-139): add Comment entity and event comments CRUD endpoints`
   (validé par `.github/workflows/pr-title-check.yml`).
2. Body PR : copier-coller le bloc fourni dans la spec section « Livrable FINAL attendu »
   — respecte strictement `.github/pull_request_template.md`. Le body transite par
   `cat … | docker exec -i unige-events-app-1 bash -c "cat > /tmp/pr-body-scrum-139.md"`
   puis `gh pr create --body-file /tmp/pr-body-scrum-139.md` depuis le devcontainer.
3. Demander la review à Copilot :
   `gh pr edit <PR_NUM> --add-reviewer copilot-pull-request-reviewer`. Fallback si app non
   collaborator : `gh pr comment <PR_NUM> --body "@copilot review please"`.
4. Pour CHAQUE commentaire de Copilot :
   - Récupérer via `gh api repos/unige-pinfo6-2026/unige-events/pulls/<PR_NUM>/comments --paginate`.
   - Juger pertinence (alignement avec les conventions projet et les décisions tranchées).
   - Si pertinent → corriger dans un commit `fix(scrum-139): …` + push + répondre au
     commentaire avec un lien vers le SHA.
   - Si non-pertinent → répondre poliment en justifiant pourquoi la remarque n'est pas
     appliquée.
   - **Ne jamais ignorer silencieusement un commentaire.**
5. Surveiller la CI : `gh pr checks <PR_NUM> --watch`. Si une check échoue, lire les logs
   (`gh run view <RUN_ID> --log-failed`), corriger la cause **racine** (PAS de
   `--no-verify`, PAS de skip, PAS de `@Disabled`), commit + push, surveiller à nouveau
   jusqu'à ce que **toutes** les checks soient vertes ET que le Quality Gate Sonar soit
   vert.
6. **Ne PAS merger** la PR — Elie s'en charge après validation finale.

## Critères de done (rappel)

- [ ] Branche `feature/s6-comments` créée depuis `origin/main` avec `--no-track`.
- [ ] OpenAPI modifié EN PREMIER.
- [ ] V15 (ou V16) présente, V1..V14 intactes.
- [ ] Comment, CommentDTO, CreateCommentRequest, CommentService, CommentResource,
      CommentDirectResource créés.
- [ ] CommentServiceMock ajouté à `ShareServiceCoverageProfile`.
- [ ] ≥ 25 sentinels verts, JaCoCo > 90 % sur le diff.
- [ ] `./mvnw verify` vert (devcontainer).
- [ ] Doc mise à jour : `data-model.md`, `api-contract.md`, `sprint-context.md`.
- [ ] `git diff --stat frontend/` strictement vide.
- [ ] `pom.xml` inchangé.
- [ ] PR ouverte, titre EXACT, body conforme template, Copilot reviewer demandé.
- [ ] Tous les checks GitHub Actions verts + SonarCloud Quality Gate vert.
- [ ] PR **non mergée** — Elie merge lui-même.

Procède maintenant. Reporte ton avancement à chaque étape complétée.
```
