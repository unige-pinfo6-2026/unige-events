# SCRUM-147 — Récurrence sur Event + génération d'occurrences

| Champ | Valeur |
|---|---|
| Ticket Jira | [SCRUM-147](https://pinfo-groupe6.atlassian.net/browse/SCRUM-147) (8 SP) |
| Sprint | S7 (calendrier produit) — préfixe Jira `[BACK][S8]` (artefact historique de re-planning, cf. décision 1) |
| Épic | [SCRUM-14](https://pinfo-groupe6.atlassian.net/browse/SCRUM-14) — Édition d'événements |
| Story | [SCRUM-116](https://pinfo-groupe6.atlassian.net/browse/SCRUM-116) (US-27 — *« En tant qu'organisateur, je veux créer un événement récurrent (hebdomadaire / bimensuel / mensuel), afin de ne pas ressaisir manuellement chaque occurrence. »*) |
| Story Points | 8 |
| Branche | `feature/s7-recurrence` (cohérent avec [`backlog_s5_s10.md` ligne 1166](backend/docs/backlog_s5_s10.md#L1166) — cf. décision 1) |
| Base | `origin/main` (tip à la date de rédaction : pointe sur `main` post-merge SCRUM-138 (PR #154 — `V14__create_follows.sql`) et SCRUM-139 (PR #156 — `V14__create_comments.sql` renommé en `V15` côté merge si rebase nécessaire — cf. décision 2 et la note d'implémentation ci-dessous) |
| Auteur spec | Elie Bussod |
| Date | 2026-05-08 |
| PR de référence | feat(scrum-147): add event recurrence with occurrence generation |
| Frontend lié (consommateur aval) | [SCRUM-XXX-front-recurrence](https://pinfo-groupe6.atlassian.net/) — formulaire `RecurrenceForm.tsx` dans `EventCreatePage`, listing des occurrences dans `EventDetailPage`. Sprint S8+. **Hors scope** SCRUM-147. Le contrat OpenAPI livré ici est figé pour ce ticket. |
| Modification globale du template (propagation aux occurrences) | **Hors scope** S7. Décision 17 — chaque occurrence reste indépendamment éditable. Si un ticket S8+ requiert une UX « éditer toutes les occurrences futures », il sera traité séparément. |
| Cancel cascadé d'un parent récurrent | **Hors scope** S7. Décision 18 — `PATCH /events/{id}/cancel` sur le parent ne cascade PAS. Le frontend itère via N appels PATCH cancel s'il veut tout annuler. |
| Notifications par occurrence (cancel/update) | [SCRUM-99](https://pinfo-groupe6.atlassian.net/browse/SCRUM-99) (S7+) — infrastructure Notification. **Hors scope** SCRUM-147 (cf. décision 23). |
| Skip / exception sur une occurrence individuelle (RFC 5545 EXDATE) | **Hors scope** S7. Décision 7 — la `recurrence_rule` stockée est compacte (FREQ + UNTIL/COUNT), sans BYDAY/EXDATE. Une occurrence skippée se gère en S7 par un `DELETE /events/{occurrenceId}` standard. |
| Optimisation RRULE compacte côté ICS | **Hors scope** S7. Décision 13 — chaque occurrence reste sa propre row Event, donc `CalendarService.generateIcsFeed` ([`CalendarService.java:48-79`](backend/src/main/java/ch/unige/events/service/CalendarService.java#L48-L79)) liste les N VEVENTs individuellement, sans changement. |
| Héritage automatique des co-organisateurs aux occurrences | **Hors scope** S7. Décision 12 — pas de cascade. L'organisateur réinvite par occurrence si besoin. |
| Dépendances amont | Aucune au sens strict. **Ordre de merge effectif** : V14 follows (SCRUM-138 PR #154) → V15 comments (SCRUM-139 PR #156) → V16 (PR concurrente mergée entre la rédaction du prompt et la rédaction de la spec) → **V17 recurrence (cette PR)**. Si V17 est également pris au moment du checkout, voir décision 2 pour le swap en V18. |
| Règle d'or `openapi-first` | **APPLICABLE — 1 nouveau path (`GET /events/{id}/occurrences`) + 1 nouveau schéma (`RecurrenceRequest`) + 2 champs ajoutés au schéma `Event` (`parentEventId`, `recurrenceRule`) + 1 champ ajouté au schéma `CreateEventRequest` (`recurrence`).** Modifier [`openapi/openapi.yaml`](openapi/openapi.yaml) AVANT toute ligne de code Java. Voir [`backend/AGENTS.md` lignes 77-80](backend/AGENTS.md#L77-L80). |

> **Note d'implémentation (2026-05-08, mise à jour post-prompt).** Entre l'envoi
> du prompt initial et la rédaction de la spec, **V16 a été pris** par une PR
> concurrente mergée sur `main`. La décision 2 ci-dessous fixe donc la migration
> SCRUM-147 à `V17__add_event_recurrence.sql` comme **valeur par défaut**
> (V14 follows + V15 comments + V16 amont concurrent + V17 recurrence). **Vérifier
> systématiquement** via le pré-check `ls backend/src/main/resources/db/migration | sort` au moment du checkout : si V17 est également pris (nouvelle PR
> concurrente), basculer en V18 avec un commit `fix(scrum-147): rebase V17 → V18`.
> Le commit `feat(scrum-147): add V17 migration for event recurrence columns`
> documente le numéro retenu en cas de bascule.

---

## Contexte

### Le besoin produit (US-27)

> *« En tant qu'organisateur, je veux créer un événement récurrent (hebdomadaire /
> bimensuel / mensuel), afin de ne pas ressaisir manuellement chaque occurrence. »* — US-27 (SCRUM-116)

L'épic SCRUM-14 (« Édition d'événements ») couvre tout le cycle de vie d'un
événement organisateur — création, brouillon, publication, modification, annulation,
duplication. SCRUM-147 livre la **brique récurrence** : un organisateur qui
programme une conférence hebdomadaire ou un cours du mardi soir n'a plus à recréer
manuellement chaque session ; il saisit le template une fois + un bloc
`recurrence: { frequency, endDate, maxOccurrences }`, et le backend matérialise N
occurrences (jusqu'à 52) en une seule transaction atomique.

### Ce qui manque aujourd'hui

| Pièce manquante | Conséquence |
|---|---|
| Aucun champ `parentEventId` ou `recurrenceRule` sur `Event` | Impossible de distinguer une occurrence d'un événement standalone, ni de retrouver la liste des occurrences d'un parent |
| Aucun enum `RecurrenceFrequency` | Pas de typage strict côté backend pour les 3 fréquences attendues (WEEKLY / BIWEEKLY / MONTHLY) |
| Aucun bloc `recurrence` dans `CreateEventRequest` | Le frontend (SCRUM-XXX-front-recurrence S8+) ne peut pas pousser une demande de création récurrente |
| Aucun endpoint `GET /api/events/{id}/occurrences` | Impossible d'afficher la liste des prochaines dates d'un événement récurrent |
| Aucun helper de génération de dates à partir d'un (start, end, frequency, until/count) | `EventService.create` reste limité aux événements ponctuels |
| Aucun schéma OpenAPI `RecurrenceRequest` ni champs `parentEventId`/`recurrenceRule` sur `Event` | Le contrat consommé par SCRUM-XXX-front-recurrence n'est pas figé — bloque le typage TanStack Query côté front |

### Ce qui existe déjà à RÉUTILISER tel quel (ne pas recréer)

| Élément | Fichier / ligne | Rôle dans SCRUM-147 |
|---|---|---|
| Entité `Event` (PanacheEntity Long PK) avec 17 champs | [`Event.java:21-92`](backend/src/main/java/ch/unige/events/entity/Event.java#L21-L92) | **Étendue** par 2 colonnes (`parentEventId`, `recurrenceRule`) — pas de nouvelle entité |
| `EventStatus` enum (DRAFT / PUBLISHED / CANCELLED / EXPIRED / BANNED) | [`EventStatus.java`](backend/src/main/java/ch/unige/events/entity/EventStatus.java) | Hérité tel quel par les occurrences (cf. décision 11) |
| `EventService.create(String, CreateEventRequest)` (validation status, persistance, projection DTO) | [`EventService.java:116-149`](backend/src/main/java/ch/unige/events/service/EventService.java#L116-L149) | **Point de branchement** : si `request.recurrence != null`, déléguer à `createRecurring(...)` (cf. décision 19) |
| `EventService.getById(Long, String, boolean)` (anti-oracle DRAFT/CANCELLED/BANNED → 404) | [`EventService.java:152-181`](backend/src/main/java/ch/unige/events/service/EventService.java#L152-L181) | **Garde anti-oracle** systématique au début de `getOccurrences(...)` (cf. décision 14) |
| `EventService.collectPublishValidationErrors(Event)` (règles `Future startDate`, `endDate > startDate`, etc.) | [`EventService.java:320-340`](backend/src/main/java/ch/unige/events/service/EventService.java#L320-L340) | Appliqué au **parent** comme à un event standard ; **pas re-vérifié** sur les occurrences générées (cf. décision 16) |
| `EventService.isCreatorOrAcceptedCoOrganizerPublic(Event, String auth0Id)` | [`EventService.java:436-438`](backend/src/main/java/ch/unige/events/service/EventService.java#L436-L438) | Cascade SCRUM-136 — réutilisée tel quel pour `getOccurrences` (un co-org ACCEPTED voit le DRAFT) |
| `EventService.conflict(String message)` helper | [`EventService.java:285-290`](backend/src/main/java/ch/unige/events/service/EventService.java#L285-L290) | Modèle de pattern d'erreur (`Map.of("error", "...", "message", ...)`) ; helpers `badRequest`/`unprocessable` analogues à dupliquer si besoin (cf. décision 26) |
| Pattern `ApiErrorResponse` (envelope d'erreur) | [`ApiErrorResponse.java`](backend/src/main/java/ch/unige/events/dto/ApiErrorResponse.java) | Envelope d'erreur standard pour `recurrence_unbounded`, `recurrence_end_before_start`, `recurrence_too_many` |
| Pattern migration Flyway « ALTER TABLE ADD COLUMN » | [`V12__add_featured_to_events.sql:1-5`](backend/src/main/resources/db/migration/V12__add_featured_to_events.sql#L1-L5) | **Modèle direct** pour `V17__add_event_recurrence.sql` (ALTER TABLE events ADD COLUMN ... + CREATE INDEX) — pattern le plus proche au niveau migration ALTER TABLE |
| Pattern `CreateEventRequest` — sous-classe de `EventRequestBase` | [`CreateEventRequest.java:5-11`](backend/src/main/java/ch/unige/events/dto/event/CreateEventRequest.java#L5-L11) + [`EventRequestBase.java:24-67`](backend/src/main/java/ch/unige/events/dto/event/EventRequestBase.java#L24-L67) | Champ `recurrence` ajouté en propriété de `CreateEventRequest` (cf. décision 8) — pas dans `EventRequestBase` (un PUT n'autorise pas la récurrence) |
| Pattern `EventDTO` record + factory `from(Event, ...)` | [`EventDTO.java:12-77`](backend/src/main/java/ch/unige/events/dto/event/EventDTO.java#L12-L77) | **Étendu** de 2 champs (`parentEventId`, `recurrenceRule`) ; factory propage les 2 champs (cf. décision 20) |
| `EventResource` racine `@Path("/events")` (POST + GET + PUT + DELETE + PATCH cancel/restore/publish + GET /featured) | [`EventResource.java:31-175`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L31-L175) | **Étendu** d'un seul handler `@GET @Path("/{id}/occurrences")` — pas de split en deux Resources (cf. décision 28) |
| Pattern `@PerUserRateLimit(name="events.create", max=10)` sur POST /events | [`EventResource.java:86-93`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L86-L93) | Rate limit existant — **partagé** avec la création récurrente (cf. décision 27) |
| `EventServiceMock` (mock CDI étendant `EventService`) | [`EventServiceMock.java:27-100`](backend/src/test/java/ch/unige/events/service/EventServiceMock.java#L27-L100) | **Étendu** d'un override `createRecurring(...)` + `getOccurrences(...)` |
| Liste d'exclusion `quarkus.arc.exclude-types` du profile coverage | [`ShareServiceCoverageProfile.java:13-31`](backend/src/test/java/ch/unige/events/service/ShareServiceCoverageProfile.java#L13-L31) | **Pas de modification** — `EventServiceMock` y figure déjà |
| `CalendarService.generateIcsFeed(UUID)` (1 VEVENT par event row, pas de RRULE compacte) | [`CalendarService.java:48-79`](backend/src/main/java/ch/unige/events/service/CalendarService.java#L48-L79) | **Inchangé** — chaque occurrence est une row Event matérialisée, donc le flux ICS continue de fonctionner sans changement (cf. décision 13) |
| Pattern test entité `@QuarkusTest` + assignabilité | [`EventTest.java`](backend/src/test/java/ch/unige/events/entity/EventTest.java) | Modèle direct pour les nouveaux sentinels d'assignabilité `parentEventId` / `recurrenceRule` |
| Pattern `EventServiceCoverageTest` (`@TestProfile(ShareServiceCoverageProfile.class)` + `@TestTransaction` + helpers `persistUser`/`persistEvent`) | [`EventServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java) | **Modèle direct** pour les nouveaux sentinels DB-backed (création récurrente, comptage occurrences, cancel non-cascadé, etc.) |
| Pattern `EventResourceTest` (`@TestSecurity` + RestAssured Hamcrest) | [`EventResourceTest.java`](backend/src/test/java/ch/unige/events/resource/EventResourceTest.java) | **Modèle direct** pour les nouveaux sentinels Resource (`POST /events` avec recurrence, `GET /events/{id}/occurrences`) |
| Préfixe API `quarkus.http.root-path=api` | [`application.properties:1`](backend/src/main/resources/application.properties#L1) | `@Path("/{id}/occurrences")` reste relatif à `@Path("/events")` racine — résout en `/api/events/{id}/occurrences` |

### Pourquoi maintenant

- **Sprint S7 — sprint courant**, ticket affecté à Elie sur le board Jira (initialement
  Antoine, réassigné). Statut « En cours ». Aucune dépendance amont au sens strict
  (pas d'autre PR ouverte qui touche `Event` ou `EventService.create`).
- **Ordre de merge prévisible** : V14 (follows, SCRUM-138) → V15 (comments, SCRUM-139)
  → V16 (PR concurrente mergée post-prompt) → V17 (recurrence, SCRUM-147). Les deux PRs amont SCRUM-138/139 (#154 et #156) sont mergées au
  moment de la rédaction (cf. note d'implémentation en tête de spec). Le pré-check
  `ls db/migration | sort` reste obligatoire au moment du checkout.
- **Débloque immédiatement** : SCRUM-XXX-front-recurrence (S8+) — formulaire
  `RecurrenceForm.tsx` dans `EventCreatePage`. Le contrat figé ici est consommé par
  TanStack Query (`mutationFn: ({ event, recurrence }) => api.post('/events', { ...event, recurrence })`).
- **Cohérence avec l'épic SCRUM-14** : la suite logique de l'édition d'événements
  est la récurrence ; après quoi seuls les exceptions/skip individuels (RFC 5545
  EXDATE — cf. hors scope ci-dessus) restent à traiter en S8+.
- **Pas de réécriture transversale** : le pipeline ICS, le compteur
  `attendingCount`, le filtrage `GET /events`, les notifications attendees,
  l'autorisation cancel/publish — tout reste **inchangé** par cette PR. Chaque
  occurrence est une row Event standard ; les services aval voisins
  (`AttendanceService`, `FavoriteService`, `EventStatsService`, `CalendarService`)
  fonctionnent par occurrence sans branchement spécial. C'est la propriété
  d'**isolation par row** qui rend la matérialisation simple et révisible.
- **Anti-oracle ISSUE-92** : la règle 404 (mergée) est **déjà en place** sur
  `GET /events/{id}` — SCRUM-147 la **délègue intégralement** à
  `EventService.getById(...)` au lieu de la dupliquer dans la nouvelle méthode
  `getOccurrences`.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### 1. Branche `feature/s7-recurrence` — pas `feature/SCRUM-147-recurrence`

**Décision.** La branche s'appelle `feature/s7-recurrence`, conformément au nom suggéré dans [`backlog_s5_s10.md` ligne 1166](backend/docs/backlog_s5_s10.md#L1166). Le ticket porte le préfixe `[BACK][S8]` mais le backlog le rattache au sprint **S7** (artefact historique : titre frappé en S8 puis re-planifié en S7). Cette fois, contrairement à SCRUM-138/139 (S6 réel re-planifié), le sprint S7 est **bel et bien le sprint en cours** — pas de re-planning historique à documenter dans la PR description.

**Justification.** Cohérence intra-projet avec `feature/s6-comments` (SCRUM-139), `feature/s6-follow` (SCRUM-138), `feature/s6-co-organizers` (SCRUM-136). Toutes les branches récentes du backlog suivent le préfixe `feature/s<N>-<description>`. La règle racine [`AGENTS.md` ligne 117](AGENTS.md#L117) autorise le format `feature/SCRUM-XX-description` mais les specs récentes du repo retiennent l'alias backlog quand il existe — pour la traçabilité review/merge.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) `feature/s7-recurrence` | Cohérent avec backlog ligne 1166 ; pattern projet (4 dernières branches s6 mergées) ; nom court ; lisible dans `git log` | Décale du format AGENTS.md mais autorisé | ✅ retenu |
| (b) `feature/SCRUM-147-recurrence` | Strict respect de la convention `feature/SCRUM-XX-...` | Inconsistant avec le sprint en cours ; rendrait la PR isolée du pattern de groupe | ❌ |

### 2. Numérotation Flyway → `V17__add_event_recurrence.sql`

**Décision.** Nouveau fichier `backend/src/main/resources/db/migration/V17__add_event_recurrence.sql`. Au moment de la rédaction de la spec (mise à jour post-prompt), `main` contient V14 (follows, SCRUM-138), V15 (comments, SCRUM-139) et V16 (PR concurrente mergée juste avant la rédaction). `V17` est donc le prochain numéro libre.

**Justification.** Une migration committée est immutable ([`backend/AGENTS.md` lignes 54-57](backend/AGENTS.md#L54-L57)). **Avant** de coder le SQL : exécuter

```bash
docker exec -w /workspace unige-events-app-1 bash -c "ls backend/src/main/resources/db/migration | sort"
```

une dernière fois.

| État de l'amont au checkout | Numéro Flyway à utiliser pour SCRUM-147 |
|---|---|
| V14, V15, V16 tous mergés (cas attendu post-prompt — V16 pris par PR concurrente) | `V17__add_event_recurrence.sql` (cas par défaut de cette spec) |
| Une nouvelle PR concurrente prend V17 entre-temps | `V18__add_event_recurrence.sql` ; le commit `fix(scrum-147): rebase V17 → V18` documente le swap |
| V16 PAS mergé (très improbable — la PR concurrente est déjà sur `main`) | `V16__add_event_recurrence.sql` ; un rebase post-merge V16 imposera un `fix(scrum-147): rebase V16 → V17` |

Documenter le numéro retenu dans :
1. le commit `feat(scrum-147): add V<N> migration for event recurrence columns`,
2. la PR description (section « Migration » de `## Changements `).

Ce pattern est exactement celui appliqué dans le commit V14 de SCRUM-139 (cf. note d'en-tête de spec et commentaire d'en-tête de [`V14__create_comments.sql:6-9`](backend/src/main/resources/db/migration/V14__create_comments.sql#L6-L9)).

### 3. PAS de nouvelle entité ni de nouvelle table — uniquement ALTER TABLE

**Décision.** Pas d'entité `EventRecurrence` ; pas de table `event_recurrences`. La récurrence est **matérialisée par 2 colonnes ajoutées à `events`** + N rows Event indépendantes (1 parent + jusqu'à 51 occurrences) :

```sql
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS parent_event_id BIGINT,
    ADD COLUMN IF NOT EXISTS recurrence_rule VARCHAR(500);

ALTER TABLE events
    ADD CONSTRAINT fk_events_parent
        FOREIGN KEY (parent_event_id) REFERENCES events(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_event_parent ON events(parent_event_id);
```

Pas d'index sur `recurrence_rule` (pas de query par RRULE prévue côté produit S7).

**Justification.** Le backlog ([`backlog_s5_s10.md` lignes 1147-1148](backend/docs/backlog_s5_s10.md#L1147-L1148)) le tranche explicitement : « Entité Event : ajouter parentEventId (Long, nullable) + recurrenceRule (String, nullable) ». Chaque occurrence est un Event indépendant — avec ses propres `attendees`, `favorites`, `views`, `co-organizers`, `comments`, etc. Aucune table de jointure n'apporte de valeur ; au contraire, elle forcerait des JOINs partout (`AttendanceService.attend(eventId)` deviendrait `attend(eventId, ?)`). Pattern de **matérialisation par rows**, parfaitement aligné avec l'architecture isolée par row du projet.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) Colonnes ALTER TABLE events + matérialisation par rows | Tranché par le backlog ; chaque occurrence reste un Event standalone (Attendance/Favorite/Stats par occurrence sans branchement spécial) ; ICS feed inchangé ; pas de JOIN supplémentaire | Stockage : N rows par récurrence vs 1 row + N dates virtuelles | ✅ retenu |
| (b) Nouvelle table `event_recurrences` (1:N vers Event) avec lookup virtuel | DB plus économe en rows | Tous les services voisins (Attendance, Favorite, Stats, ICS, Co-organizers, Comments) doivent gérer des « events virtuels » non-persistés ; complexité énorme ; casse l'architecture par row | ❌ |
| (c) Stockage RRULE seul + génération à la volée à chaque GET | Aucune row supplémentaire | Idem (b) : `attend(eventId, occurrenceN)` partout, signatures cassées sur 6 services. Inscriptions/favoris/likes par occurrence impossibles à stocker | ❌ |

### 4. `parent_event_id` → `Long` brut, **pas un `@ManyToOne`**

**Décision.**

```java
@Column(name = "parent_event_id")
public Long parentEventId;
```

(Pas de `@ManyToOne(fetch = LAZY) Event parent`.)

**Justification.** Cohérent avec le pattern projet pour les FK non-textuelles entre tables peer-to-peer :
- `Favorite.eventId: Long` ([`Favorite.java`](backend/src/main/java/ch/unige/events/entity/Favorite.java)) — pas `@ManyToOne Event event`,
- `Attendance.eventId: Long` ([`Attendance.java`](backend/src/main/java/ch/unige/events/entity/Attendance.java)) — pas `@ManyToOne Event event`,
- `EventCoOrganizer.eventId: Long`, `EventCoOrganizer.userId: UUID` — Long bruts également,
- `Follow.followerId: UUID`, `Follow.followedId: UUID` (SCRUM-138).

`Comment.parentComment: @ManyToOne(LAZY) Comment` (SCRUM-139 décision 6) est l'**exception** car (i) `Comment` a un payload textuel à exposer au DTO via navigation (`parentComment.parentComment != null` pour le check de profondeur), et (ii) la navigation directe simplifie sensiblement la lecture du code. Ici, `parent_event_id` n'est qu'un **pointeur de jointure** pour le seul endpoint `GET /events/{id}/occurrences` ; le service utilise `Event.list("parentEventId = ?1", eventId)` — pas de navigation. Pas de bénéfice à payer le coût d'un proxy LAZY supplémentaire sur `Event` (déjà 2 LAZY : `creator`, `event_tags`).

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) `Long parentEventId` brut | Cohérent avec Favorite/Attendance/Follow/EventCoOrganizer ; pas de proxy LAZY supplémentaire ; lookup explicite côté service | Pas de navigation `event.parent.title` (jamais utilisée en S7) | ✅ retenu |
| (b) `@ManyToOne(LAZY) Event parent` | Navigation directe ; cohérent avec `Comment.parentComment` | 3e proxy LAZY sur Event ; risque N+1 si exposé en projection ; pas demandé par le produit | ❌ |

### 5. FK `fk_events_parent` → `ON DELETE SET NULL` (pas de cascade DELETE, pas RESTRICT)

**Décision.**

```sql
ALTER TABLE events
    ADD CONSTRAINT fk_events_parent
        FOREIGN KEY (parent_event_id) REFERENCES events(id) ON DELETE SET NULL;
```

**Justification.** Cohérent avec le fix post-Copilot SCRUM-139 (commit `1575662 fix(scrum-139): apply Copilot review — ON DELETE SET NULL on parent FK`) sur `fk_comments_parent`. Le scénario à couvrir :

- Un organisateur crée une récurrence (1 parent + 51 occurrences).
- Plus tard, il décide d'annuler le **template parent** (`PATCH /events/{parentId}/cancel` puis `DELETE /events/{parentId}` — flow standard). Les occurrences ont chacune accumulé des inscriptions, favoris, vues, comptages — elles doivent **survivre**.
- Sans clause `ON DELETE`, la FK PostgreSQL par défaut est `RESTRICT` → le `DELETE /events/{parentId}` échoue côté DB avec « violates foreign key constraint ». Inacceptable.
- Avec `ON DELETE CASCADE` → toutes les occurrences sont supprimées physiquement, leurs Attendances orphelines (cf. `EventService.delete` lignes 240-242 qui purge déjà les FK liées par event), favoris orphelins, comptages perdus. Inacceptable.
- `ON DELETE SET NULL` → les occurrences survivent avec `parent_event_id = NULL` ; elles deviennent des standalones non-récurrentes orphelines, conservant tout leur historique. **Choix retenu**.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) `ON DELETE SET NULL` | DELETE physique du parent autorisé ; occurrences préservent inscriptions/favoris/views ; cohérent avec fix SCRUM-139 sur `fk_comments_parent` | Une occurrence orpheline n'est plus reliable au parent (acceptable — l'organisateur a explicitement supprimé le template) | ✅ retenu |
| (b) `ON DELETE RESTRICT` (par défaut) | DELETE bloqué tant qu'il y a des occurrences | Force l'organisateur à itérer N `DELETE /events/{occurrenceId}` avant de pouvoir supprimer le parent — UX cassée | ❌ |
| (c) `ON DELETE CASCADE` | DELETE en chaîne propre côté DB | Détruit l'historique des occurrences (inscriptions/favoris/views) — inacceptable | ❌ |

### 6. Enum `RecurrenceFrequency` → 3 valeurs strictes

**Décision.**

```java
public enum RecurrenceFrequency {
    WEEKLY,
    BIWEEKLY,
    MONTHLY
}
```

**Justification.** Backlog ([`backlog_s5_s10.md` ligne 1150](backend/docs/backlog_s5_s10.md#L1150)) explicite. PAS de DAILY (volume trop élevé : 365 occurrences/an > limite 52), PAS de YEARLY (cas d'usage produit S7 limité, pas de UX dédiée). Le frontend SCRUM-XXX-front-recurrence proposera un select à 3 options ; ajouter une 4e valeur sans front cassé est une refacto trivialement portable en S8+.

Espacements de génération :
- `WEEKLY` → `Period.ofDays(7)`,
- `BIWEEKLY` → `Period.ofDays(14)`,
- `MONTHLY` → `Period.ofMonths(1)` (gère le 31 → 28 février naturellement via `LocalDate.plus(Period)`).

### 7. Format de stockage `recurrence_rule` → RFC 5545 RRULE simplifié

**Décision.** `recurrence_rule` est une chaîne RFC 5545 RRULE simplifiée, portée **uniquement** par le **parent**. Format autorisé (figé) :

```
FREQ=WEEKLY;UNTIL=20260601
FREQ=BIWEEKLY;COUNT=10
FREQ=MONTHLY;UNTIL=20260601;COUNT=12
```

- `FREQ` ∈ {`WEEKLY`, `BIWEEKLY`, `MONTHLY`} (correspond à `RecurrenceFrequency`),
- `UNTIL=YYYYMMDD` (date inclusive — la dernière occurrence générée a `startDate ≤ UNTIL`),
- `COUNT=<int>` (nombre maximal d'occurrences hors parent — la limite de 52 du domaine produit s'applique),
- au moins UN des deux (`UNTIL` ou `COUNT`) est obligatoire (sinon `400 recurrence_unbounded`),
- les deux peuvent coexister — le générateur s'arrête au plus restrictif des deux.

**PAS de support BYDAY/BYMONTHDAY/BYHOUR/EXDATE/INTERVAL** en S7 (frontend ne les expose pas).

**Justification.** RFC 5545 RRULE est le standard ICS officiel — donc directement compatible avec une optimisation future (RRULE compact dans VEVENT — cf. décision 13 / hors scope) et avec n'importe quel client iCal qui consommerait demain un export. La sérialisation reste lisible humainement (`FREQ=WEEKLY;UNTIL=20260601` est interprétable sans parser dédié).

Stockage `VARCHAR(500)` largement suffisant (RRULE compact tient en moins de 100 chars en pratique). Pas de stockage en colonne JSON — on garde un format texte standard, indexable trivialement si nécessaire plus tard.

### 8. Format `recurrence` dans `CreateEventRequest` → record imbriqué

**Décision.**

```java
public record RecurrenceRequest(
        @NotNull RecurrenceFrequency frequency,
        LocalDate endDate,
        @Min(1) @Max(52) Integer maxOccurrences
) {}
```

`CreateEventRequest` enrichi de :

```java
@Valid
public RecurrenceRequest recurrence;
```

Bean Validation :
- `frequency` `@NotNull` — sinon `400` Bean Validation.
- `endDate` ou `maxOccurrences` (au moins un) doit être présent — vérifié **côté service** (`recurrence_unbounded` 400, cf. décision 22). Pas exprimable en pure annotation Bean Validation sans un validateur custom.
- `maxOccurrences` `@Min(1) @Max(52)` — `400` Bean Validation si `maxOccurrences > 52` ou `< 1`.

Si `recurrence` est `null`, `EventService.create` ne touche pas à la branche récurrence (cf. décision 19) — comportement legacy strict.

**Justification.** Pattern record + Bean Validation cohérent avec `CreateCommentRequest` (SCRUM-139), `CreateReportRequest`, etc. Le `@Valid` sur le champ déclenche la validation imbriquée. Le record reste flat (frequency + endDate + maxOccurrences) — pas de structure imbriquée supplémentaire.

### 9. Limite hard de 52 occurrences (1 an hebdo)

**Décision.** Le générateur d'occurrences plafonne **silencieusement** à 51 occurrences (parent inclus = 52 rows total) si le calcul (endDate + frequency) en demande plus. **Sauf** si l'utilisateur a fourni `maxOccurrences > 52` — dans ce cas Bean Validation renvoie un `400` côté DTO (annotation `@Max(52)`).

| Cas | Réponse |
|---|---|
| `maxOccurrences = 53`, `endDate = null` | `400` Bean Validation (`@Max(52)` sur le DTO) |
| `maxOccurrences = null`, `endDate = startDate + 2 ans`, `frequency = WEEKLY` (~104 dates calculables) | `200/201` + 51 occurrences générées (parent + 51 = 52 rows), tronqué silencieusement |
| `maxOccurrences = 50`, `endDate = startDate + 2 ans`, `frequency = WEEKLY` (calcul → 50 dates) | `201` + 50 occurrences (parent + 50 = 51 rows), `maxOccurrences` est plus restrictif que le calcul |

**Justification.** 52 = 1 an hebdomadaire = couverture du cas universitaire usuel (cours hebdomadaires sur 1 année académique). 26 (6 mois) trop restrictif. Illimité = risque DB (DOS par création récurrente bimensuelle sur 100 ans = 2400 rows en une transaction).

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) Cap à 52 (1 an hebdo) | Couvre tout le cas usuel produit ; tronque silencieusement le débordement calculé ; bloque explicitement le client malicieux via `@Max(52)` | Un cours bimensuel sur 14 mois (28 occurrences) reste largement OK ; un cours mensuel sur 4+ ans tronqué (peu probable) | ✅ retenu |
| (b) Cap à 26 (6 mois) | DB plus économe | UX casse pour cours annuels | ❌ |
| (c) Pas de cap | Flexibilité max | Risque de DOS (création bimensuelle 100 ans = 2400 rows en une transaction, OOM Hibernate possible) | ❌ |

### 10. Atomicité — `@Transactional` strict, all-or-nothing

**Décision.** `EventService.createRecurring(...)` est annoté `@Transactional`. Toutes les rows (parent + N occurrences) sont persistées dans une **seule unité transactionnelle JTA**. Si l'INSERT de la 17e occurrence échoue (validation Hibernate, contrainte DB, OOM), tout rollback — y compris le parent.

```java
@Transactional
public EventDTO createRecurring(String auth0Id, CreateEventRequest request) {
    // ... validation parent ...
    Event parent = persistParent(auth0Id, request);
    List<DateRange> ranges = recurrenceGenerator.generate(
            parent.startDate, parent.endDate,
            request.recurrence.frequency(),
            request.recurrence.endDate(),
            request.recurrence.maxOccurrences());
    for (DateRange range : ranges) {
        persistOccurrence(parent, range);
    }
    return EventDTO.from(parent, 0L, computeAvailableSpots(parent.capacity, 0L), 0L, null, null);
}
```

La méthode retourne `EventDTO` du **parent uniquement** ; le client appelle ensuite `GET /events/{parentId}/occurrences` pour récupérer la liste. Pas de wrapping `RecurrenceCreationResponse{ parent: EventDTO, occurrences: [...] }` — KISS.

**Justification.** Pattern Quarkus/Narayana standard. Aucune option alternative crédible : matérialiser des occurrences sans le parent (ou inversement) crée des incohérences de données. Le coût (1 transaction qui peut durer ~50ms pour 52 rows en local) est acceptable — bien en-dessous des timeouts JTA par défaut (60s).

### 11. Statut initial des occurrences → hérité du parent

**Décision.** Les occurrences héritent du `status` du parent au moment de la création. Si `request.status = PUBLISHED` ([`EventService.java:137-148`](backend/src/main/java/ch/unige/events/service/EventService.java#L137-L148)), parent + 51 occurrences créés en `PUBLISHED`. Si `status = DRAFT` (par défaut), tout en `DRAFT`.

**Justification.** Symétrie totale — l'organisateur publie tout en un coup ou tout en brouillon. Permettre une dissonance (parent PUBLISHED, occurrences DRAFT) ouvre des questions UX inutiles (qui voit quoi ? `GET /events` filtre par status — un parent PUBLISHED visible mais des occurrences DRAFT cachées casse le récit produit). Si l'organisateur veut publier après-coup une seule occurrence, il appelle `PATCH /events/{occurrenceId}/publish` standard — comportement individuel par row.

### 12. Co-organisateurs sur les occurrences → **PAS** d'héritage automatique

**Décision.** Les `EventCoOrganizer` créés/invités sur le parent ne sont **PAS** dupliqués sur les occurrences. Si l'organisateur veut co-organiser toutes les occurrences, il doit appeler `POST /api/events/{occurrenceId}/co-organizers` sur chaque occurrence individuellement.

**Justification.**
- **Volume** : une récurrence hebdomadaire annuelle = 52 occurrences. Inviter 3 co-organisateurs = 156 rows `EventCoOrganizer` créées en cascade — chacune avec son cycle PENDING → ACCEPTED qui force le co-org à accepter 52 fois la même invitation. UX cassée.
- **Granularité** : un co-organisateur peut souhaiter aider seulement sur 3 dates précises (« je gère les sessions du 12, 19 et 26 mars uniquement »). La cascade automatique force une politique uniforme inutile.
- **Frontend S8+** : un toggle « copier sur toutes les occurrences » côté `RecurrenceForm.tsx` (ou un nouveau endpoint `POST /events/{parentId}/co-organizers/cascade`) pourra être livré ultérieurement — **hors scope** S7.

**Cohérence cross-épic.** SCRUM-136 (PR mergée) a institué la cascade d'autorisation `isCreatorOrAcceptedCoOrganizerPublic` ([`EventService.java:436-438`](backend/src/main/java/ch/unige/events/service/EventService.java#L436-L438)) **par event row**. SCRUM-147 préserve cette propriété : un co-organisateur accepté sur le **parent** n'a aucun privilège sur les occurrences (il n'est pas inscrit dessus). Le système reste cohérent à toutes les échelles.

### 13. ICS feed — chaque occurrence = un VEVENT autonome (PAS de RRULE compacte)

**Décision.** [`CalendarService.generateIcsFeed(UUID)`](backend/src/main/java/ch/unige/events/service/CalendarService.java#L48-L79) reste **inchangé**. Comme chaque occurrence est sa propre row Event (cf. décision 3), le bulk query `Event.find("id IN ?1 AND status = ?2", allIds, PUBLISHED)` ([`CalendarService.java:74-76`](backend/src/main/java/ch/unige/events/service/CalendarService.java#L74-L76)) ramène les 52 rows individuellement, et `IcsBuilder.buildIcsContent(events, ...)` génère 52 VEVENTs autonomes.

PAS de génération RRULE compacte côté ICS (un seul VEVENT avec `RRULE:FREQ=WEEKLY;UNTIL=...`). Trade-off explicitement accepté :
- ✅ Simplicité backend totale — aucun changement de signature `CalendarService` / `IcsBuilder`.
- ✅ Si l'utilisateur a favorited 1 occurrence sur 52, seul ce VEVENT apparaît (granularité par row préservée).
- ❌ Flux ICS plus lourd qu'un RRULE compact (52 lignes vs 1) — accepté car les flux ICS personnels ne sont pas un point de pression DB ou réseau (cron user, pull occasionnel).

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) 52 VEVENTs row-by-row (status quo) | Aucun changement code ; granularité favoris/inscription par occurrence préservée | Flux plus lourd | ✅ retenu |
| (b) 1 VEVENT + RRULE compact dans ICS | Flux léger | Force lookup virtuel ; casse Attendance/Favorite/Stats par occurrence ; pas demandé S7 | ❌ |

Une optimisation future (ticket dédié, S9+) pourrait introduire un cas spécial « si l'utilisateur a favorité **toutes** les occurrences d'un parent récurrent, émettre 1 VEVENT + RRULE compact ». **Hors scope** SCRUM-147.

### 14. Endpoint `GET /api/events/{id}/occurrences`

**Décision.**

| Aspect | Valeur |
|---|---|
| Path JAX-RS (sans `/api`) | `/{id}/occurrences` (sous `@Path("/events")` racine de `EventResource`, cf. décision 28) |
| Path public (avec `/api`) | `/api/events/{id}/occurrences` |
| Annotation auth | `@PermitAll` (cohérent avec `GET /events/{id}` lui-même `@PermitAll`) |
| Visibilité | Héritée de `EventService.getById(id, callerAuth0Id, isAdmin)` ([`EventService.java:152-181`](backend/src/main/java/ch/unige/events/service/EventService.java#L152-L181)) — anti-oracle ISSUE-92 (DRAFT/CANCELLED/BANNED non-créateur → 404) |
| Réponse | `200 OK` + `List<EventDTO>` brut (cohérent avec le projet, pas de `PagedResponse` wrapper) |
| Pagination | `?page=&size=` avec defaults `0`/`52`, `@Min(0)` / `@Positive @Max(52)` (la limite logique étant 52, une seule page suffit toujours) |
| Tri | `startDate ASC, id ASC` (chronologique — cas d'usage : « afficher les prochaines dates ») |
| Codes d'erreur | `200`, `400` (pagination invalide), `404` (event invisible — anti-oracle) |

**Justification.** Pattern aligné sur les endpoints de listing existants (`GET /events`, `GET /events/{id}/attendees`). `@PermitAll` est cohérent avec `GET /events/{id}` ([`EventResource.java:95-103`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L95-L103)) — un utilisateur anonyme qui voit un event PUBLISHED voit aussi ses occurrences.

### 15. `GET /events/{id}/occurrences` sur un event **sans** enfants → 200 + liste vide

**Décision.** Si l'event ciblé est un standalone non-récurrent OU une occurrence elle-même OU un parent qui n'a pas (encore) d'occurrences, l'endpoint retourne `200 OK` + `[]`. **Pas de 404**.

**Justification.** Un client qui veut savoir « est-ce un event récurrent ? » lit `event.recurrenceRule != null` directement sur le payload `GET /events/{id}` — c'est l'oracle de référence. L'endpoint `occurrences` est strictement « lister les enfants d'un id donné » ; vide est une réponse parfaitement valide. Inverser cette règle introduirait un cas spécial (« 404 si parent_event_id IS NULL et pas de récurrence vs liste vide ») incohérent avec le reste du projet.

### 16. Validation des dates — règles existantes, pas de re-validation sur les occurrences

**Décision.** Sur le **parent**, les règles de validation existantes ([`EventService.collectPublishValidationErrors`](backend/src/main/java/ch/unige/events/service/EventService.java#L320-L340) — `startDate` futur, `endDate > startDate`) s'appliquent **inchangées** au moment de la publication. Pour la **création** (DRAFT ou PUBLISHED), Bean Validation `@Future startDate` ([`EventRequestBase.java:36-38`](backend/src/main/java/ch/unige/events/dto/event/EventRequestBase.java#L36-L38)) couvre déjà le cas.

Pour chaque **occurrence** calculée :
- `startDate_n = parent.startDate + n * spacing`,
- `endDate_n = parent.endDate + n * spacing`,
- où `spacing` ∈ {`Period.ofDays(7)`, `Period.ofDays(14)`, `Period.ofMonths(1)`} selon `RecurrenceFrequency`.

**PAS** de re-validation Bean Validation `@Future` sur les occurrences — elles sont **calculées**, pas saisies par l'utilisateur. Si l'organisateur fournit `startDate = now() + 1h` + `frequency = WEEKLY` + `maxOccurrences = 4`, les 4 occurrences sont à `+1h`, `+1w+1h`, `+2w+1h`, `+3w+1h` — toutes futures naturellement. Cas-limite académique (générer une occurrence dans le passé) impossible par construction.

Si `endDate_n > recurrence.endDate` (UNTIL), la génération s'arrête avant de matérialiser cette occurrence.

**Justification.** Les règles métier d'event s'appliquent au template parent ; les occurrences calculées sont garanties cohérentes par construction. Re-passer chaque occurrence dans `collectPublishValidationErrors` serait redondant (mêmes title/location/category/capacity que le parent, juste startDate/endDate décalées).

### 17. Modification d'un parent (`PUT /events/{parentId}`) → **PAS** de propagation aux occurrences

**Décision.** Tranchée. Chaque occurrence reste **indépendamment éditable** après création. Si l'organisateur veut modifier toutes les occurrences (ex. titre changé), il les édite **une par une** (ou supprime + recrée la récurrence — KISS).

**Justification.** Cascade auto = effets de bord imprévus si une occurrence a déjà été personnalisée :
- l'organisateur a déplacé la 5e date dans une autre salle (ajusté `location`),
- la capacité a été augmentée pour la 12e date (un afterwork plus gros prévu),
- le banner a été remplacé pour la 18e date (collab avec un sponsor visiteur),
- des inscriptions ont été ouvertes/fermées spécifiquement.

Cascade aveugle = perte de toutes ces personnalisations. Le frontend SCRUM-XXX-front-recurrence (S8+) pourra proposer une option « éditer toutes les occurrences futures » avec confirmation explicite — **hors scope** ici.

### 18. Cancel d'un parent (`PATCH /events/{parentId}/cancel`) → **PAS** de cascade

**Décision.** Idem D17 — chaque occurrence reste indépendamment cancellable. Cancel du parent = cancel du parent uniquement.

**Justification.** Symétrie avec D17. Si le frontend veut cancel toutes les occurrences en bloc, il fait N appels `PATCH /events/{occurrenceId}/cancel`. Un endpoint « cascade » dédié (`POST /events/{parentId}/cancel-all-occurrences`) pourra être ajouté en S8+ si demande UX confirmée — **hors scope** ici.

Cas-limite documenté : un parent CANCELLED dont les occurrences sont PUBLISHED est un état métier valide (le template a été annulé mais les sessions individuelles sont maintenues — par exemple un cycle de cours dont l'organisateur a changé la cadence en gardant les dates déjà programmées). Pas de validation cross-rows.

### 19. Délégation depuis `EventService.create()` → branchement explicite

**Décision.** `EventService.create(String, CreateEventRequest)` ([`EventService.java:116-149`](backend/src/main/java/ch/unige/events/service/EventService.java#L116-L149)) est étendu d'un branchement en début de méthode :

```java
@Transactional
public EventDTO create(String auth0Id, CreateEventRequest request) {
    if (request.recurrence != null) {
        return createRecurring(auth0Id, request);
    }
    // ... existing standalone creation logic ...
}
```

Pas de duplication des règles de validation — `createRecurring(...)` ré-extrait la création du parent en utilisant exactement la même séquence (`new Event() ; event.title = request.title ; ... ; event.persist()`), encapsulée dans un helper privé `persistParent(...)`. Le code legacy reste intact.

**Justification.** Pattern d'extension minimal. L'alternative (deux endpoints distincts `POST /events` et `POST /events/recurring`) doublerait la surface d'API, la doc OpenAPI, et les tests Resource — sans bénéfice. Le contrat reste « POST /events crée un event ; si tu fournis `recurrence`, c'est récurrent ».

### 20. `EventDTO` enrichi de 2 champs — `parentEventId` + `recurrenceRule`

**Décision.** `EventDTO` ([`EventDTO.java:12-77`](backend/src/main/java/ch/unige/events/dto/event/EventDTO.java#L12-L77)) gagne 2 champs :

```java
public record EventDTO(
        Long id,
        // ... 25 champs existants ...
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long parentEventId,        // NEW : null si parent ou standalone, sinon parent.id
        String recurrenceRule      // NEW : non-null UNIQUEMENT sur le parent
) { ... }
```

| Cas Event | `parentEventId` | `recurrenceRule` |
|---|---|---|
| Parent récurrent | `null` | `"FREQ=WEEKLY;UNTIL=20260601"` |
| Occurrence (enfant) | `<parent.id>` | `null` |
| Standalone non-récurrent | `null` | `null` |

La factory `EventDTO.from(Event, ...)` propage les 2 champs.

**Pas** de champs additionnels en S7 :
- Pas de `occurrenceCount: long` (lecture séparée via `GET /events/{id}/occurrences` ; coût d'un comptage par row sur tous les endpoints de listing — défavorable),
- Pas de `nextOccurrenceDate: LocalDateTime` (idem — calcul par row),
- Pas de `isOccurrence: boolean` (dérivable trivialement côté client : `event.parentEventId != null`).

**Justification.** Strictement le minimum pour figer le contrat consommé par SCRUM-XXX-front-recurrence. Les 2 champs sont des projections directes des 2 colonnes ajoutées (cf. décision 3) — pas de calcul.

### 21. OpenAPI — Schéma `Event` enrichi + nouveau `RecurrenceRequest` + path `/events/{id}/occurrences`

**Décision.** Modifications de [`openapi/openapi.yaml`](openapi/openapi.yaml) :

1. Schéma `Event` (lignes ~204-338) — ajouter 2 propriétés :
   - `parentEventId` : `integer / int64 / nullable: true / readOnly: true` + description (uniquement renseigné sur les occurrences),
   - `recurrenceRule` : `string / nullable: true / maxLength: 500 / readOnly: true` + description (uniquement renseigné sur le parent récurrent ; format RFC 5545 RRULE simplifié — `FREQ=WEEKLY;UNTIL=20260601`).
2. Nouveau schéma `RecurrenceRequest` :
   - `frequency` : `$ref: '#/components/schemas/RecurrenceFrequency'` (enum `[WEEKLY, BIWEEKLY, MONTHLY]`),
   - `endDate` : `string / format: date / nullable: true`,
   - `maxOccurrences` : `integer / minimum: 1 / maximum: 52 / nullable: true`,
   - `required: [frequency]`.
3. Nouveau schéma `RecurrenceFrequency` (enum) — exposé séparément pour réutilisation potentielle côté front.
4. Schéma `CreateEventRequest` (lignes ~340-413) — ajouter une propriété :
   - `recurrence` : `$ref: '#/components/schemas/RecurrenceRequest' / nullable: true` + description.
5. Nouveau path `/events/{id}/occurrences` (GET, `@PermitAll`) avec codes 200, 400, 404.

**Tag** : aucun nouveau tag dédié ; les nouveaux endpoints utilisent `tags: [events]` (cohérent avec le reste de `/events/...`).

**Justification.** Minimal viable : 2 champs ajoutés à un schéma existant + 1 schéma neuf + 1 enum neuf + 1 path neuf. Le contrat reste self-contained — un consommateur OpenAPI génère naturellement le bon TS avec `parentEventId?: number | null` et `recurrenceRule?: string | null`.

### 22. Codes d'erreur normalisés via `ApiErrorResponse{error, message}`

**Décision.** Tous les codes d'erreur métier passent par les helpers (`badRequest`/`unprocessable`) de `EventService` (locaux, pattern projet — cf. décision 26).

| HTTP | `error` (slug) | Quand | Helper |
|---|---|---|---|
| `400` | `recurrence_unbounded` | `recurrence` présent mais ni `endDate` ni `maxOccurrences` renseignés | `badRequest(...)` |
| `400` | `recurrence_end_before_start` | `recurrence.endDate < parent.startDate.toLocalDate()` | `badRequest(...)` |
| `400` | (Bean Validation generic) | `frequency` manquant, `maxOccurrences > 52` ou `< 1`, `startDate` non-future | `ValidationErrorResponse` (mapper Bean Validation existant) |
| `404` | `not_found` | `GET /events/{id}/occurrences` sur event invisible (DRAFT non-créateur, CANCELLED non-créateur, BANNED, id inconnu) | hérité de `EventService.getById` — `NotFoundException` mappée |
| `422` | `recurrence_too_many` | Calcul interne dépassant 52 occurrences **ET** présence simultanée de `endDate` ET `maxOccurrences` (combinaison incohérente) | `unprocessable(...)` (envelope custom) |

**Note.** Bean Validation `@Max(52)` sur `maxOccurrences` couvre déjà le cas client malicieux (`maxOccurrences = 9999`). Le cas runtime « calcul donne > 52 » est géré silencieusement par troncature **sauf** si l'utilisateur a explicitement demandé endDate ET maxOccurrences avec des valeurs incompatibles entre elles — auquel cas `422 recurrence_too_many` (aide explicite à corriger). Comportement à ré-évaluer post-Copilot review si nécessaire.

### 23. Notifications par occurrence (cancel/update massif) — **hors scope**

**Décision.** SCRUM-147 **n'émet aucune notification**. SCRUM-99 (S7+, infra Notification) ajoutera le hook quand il sera prêt. Pas de TODO commenté dans le code livré — le hook sera ajouté propre dans le ticket dédié.

**Justification.** Découplage persistance / diffusion. Cohérent avec SCRUM-139 décision 23.

### 24. Filtrage `GET /api/events` — pas de modification

**Décision.** `GET /api/events` (lignes [`EventResource.java:56-84`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L56-L84)) reste **inchangé**. Toutes les occurrences (parent + enfants) apparaissent dans le feed public — comme des events standards. Si un produit veut différencier (« n'afficher que les parents récurrents + les standalones, pas les occurrences »), il filtre côté front via `event.parentEventId === null`.

**Pas** de paramètre `?recurringOnly=` ni `?excludeOccurrences=` ajouté en S7 — **hors scope**.

**Justification.** Le feed `/events` est conçu pour être lisible par tous les consommateurs (calendrier public, recherche, page d'accueil). Ajouter un toggle dédié à la récurrence sans demande produit confirmée = sur-ingénierie. Si un cas usage émerge (ex. la page d'accueil veut un mode « groupé par template récurrent »), un ticket dédié l'ajoutera.

### 25. Front — `git diff --stat frontend/` strictement vide

**Décision.** Aucune modification frontend. Le ticket consommateur aval est SCRUM-XXX-front-recurrence (S8+) — à créer dans le backlog si pas déjà au planning.

**Justification.** Cohérent avec SCRUM-138/139 (PRs back-only). Le frontend consomme l'OpenAPI livré par cette PR, pas du code Java.

### 26. Helpers d'erreurs — locaux à `EventService`, duplication assumée

**Décision.** Les helpers d'erreur (`badRequest(error, message)`, `unprocessable(error, message)`) sont ajoutés à `EventService` **uniquement si nécessaire** — pour `recurrence_unbounded`, `recurrence_end_before_start`, `recurrence_too_many`. Le helper `conflict` existe déjà ([`EventService.java:285-290`](backend/src/main/java/ch/unige/events/service/EventService.java#L285-L290)) — modèle direct.

Si les codes d'erreur peuvent passer par `BadRequestException` standard avec un body `Map.of("error", "...", "message", "...")` (à la `EventService.publish` ligne 309-313), pas de nouveau helper. KISS strict.

**Justification.** Pattern actuel du projet — `ReportService`, `FollowService`, `EventCoOrganizerService`, `CommentService` (SCRUM-139) dupliquent tous leurs helpers locaux. Décision SCRUM-147 : on **ne crée pas** de classe `ApiErrors` partagée tant qu'il n'y a pas 4+ services qui le réclament. Pas de refacto préventive embarquée dans cette PR.

### 27. Rate limiting — partagé avec `events.create`, pas de bucket dédié

**Décision.** `POST /events` est déjà annoté `@PerUserRateLimit(name = "events.create", max = 10)` ([`EventResource.java:88`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L88)). La création récurrente reste sous **le même bucket** — **pas** de rate limit dédié `events.createRecurring`.

**Justification.** Calcul d'écran de garde :
- 10 POST/min × 1 récurrence chacun × 52 occurrences/récurrence = 520 events potentiels/minute par utilisateur. Acceptable (comparé aux 10 standalones/minute = 10 events).
- Un attaquant qui veut maximiser le débit bascule en récurrence weekly / 52 occurrences pour faire ×52. Le bucket à 10 reste un écran de garde efficace contre le scénario réaliste (anti-spam UX), pas contre un attaquant déterminé qui cherche à gonfler la table events. Le vrai défense est côté infra (Nginx / WAF) — hors application.
- Ajouter un bucket spécifique `events.createRecurring` à `max = 3` complique la matrice sans bénéfice clair (un organisateur légitime crée typiquement 1-2 récurrences par session de planning).

`GET /events/{id}/occurrences` est `@PermitAll` — pas de rate limit (cohérent avec `GET /events/{id}`).

### 28. `EventResource` — un seul nouveau handler, **pas** de split en deux Resources

**Décision.** Ajout d'un seul `@GET @Path("/{id}/occurrences")` dans la classe existante [`EventResource`](backend/src/main/java/ch/unige/events/resource/EventResource.java). Pas de nouvelle classe `EventOccurrencesResource`.

**Justification.** Pattern différent de SCRUM-138/139, parce que la situation est différente :

- SCRUM-138 (Follow) : 2 racines `@Path` distinctes (`/users/{id}/follow*` et `/follow-requests/{id}/...`) → split obligatoire (un `@Path` unique par classe).
- SCRUM-139 (Comment) : idem (`/events/{id}/comments` et `/comments/{id}`) → split obligatoire.
- SCRUM-147 (Recurrence) : 1 seul nouveau path, sous `/events` racine déjà tenue par `EventResource` → **pas** de besoin de split. Ajouter le handler sur place.

Ajouter `@Path("/{id}/occurrences")` à `EventResource` est strictement le même pattern que `@PATCH @Path("/{id}/cancel")` ([`EventResource.java:124-132`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L124-L132)) — déjà 7 handlers cohabitent dans `EventResource`. La classe reste lisible.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) Ajouter le handler à `EventResource` | Zero nouveau fichier ; pattern cohérent avec les 7 handlers existants ; un seul `@Path` racine | Une classe un peu plus longue | ✅ retenu |
| (b) Nouvelle classe `EventOccurrencesResource` `@Path("/events")` | Séparation par feature | Deux Resources avec le **même** `@Path` racine — RESTEasy admet mais c'est un anti-pattern projet ; pattern SCRUM-138/139 ne s'applique que pour `@Path` racines distincts | ❌ |

### 29. Helper utilitaire `RecurrenceGenerator` — fonction pure, testable hors Quarkus

**Décision.** Un nouveau utility class privé :

```java
// backend/src/main/java/ch/unige/events/util/RecurrenceGenerator.java
public final class RecurrenceGenerator {

    public record DateRange(LocalDateTime start, LocalDateTime end) {}

    public static List<DateRange> generate(
            LocalDateTime parentStart,
            LocalDateTime parentEnd,
            RecurrenceFrequency frequency,
            LocalDate untilDate,            // nullable
            Integer maxOccurrences          // nullable
    ) { ... }
}
```

- Fonction pure (pas d'I/O, pas d'injection),
- Testable en pur JUnit (pas de `@QuarkusTest`),
- Génère **les occurrences hors parent** (le parent à `parentStart/parentEnd` est créé séparément par le service ; le générateur retourne donc 0..51 ranges, jamais 52).

**Justification.** L'extraction de la génération de dates dans une utility class isolée :
- permet des tests unitaires triviaux (pas de bootstrap Quarkus, exécution en ms),
- isole la logique calendrier (le seul endroit du codebase qui fait `Period.ofDays(7) / 14 / Period.ofMonths(1)`),
- facilite une refactorisation future si on veut introduire BYDAY/INTERVAL/EXDATE (S8+).

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) Utility class statique + record `DateRange` | Pure, testable hors Quarkus, isolée | 1 classe supplémentaire | ✅ retenu |
| (b) Méthode privée dans `EventService` | Pas de classe supplémentaire | Force `@QuarkusTest` pour tester ; mélange logique transactionnelle + calcul calendrier | ❌ |
| (c) `@ApplicationScoped service` injecté | CDI-managed | Sur-ingénierie pour une fonction pure stateless | ❌ |

---

## Analyse de l'existant

### 4.1 Entités & migrations

| Domaine | Fichier | Lignes | Pattern à mimer | Note d'adaptation pour SCRUM-147 |
|---|---|---|---|---|
| Entité `Event` (PanacheEntity Long PK) | [`Event.java`](backend/src/main/java/ch/unige/events/entity/Event.java) | 21-92 | 17 champs publics, 2 indexes existants (`idx_event_creator`, `idx_event_start_date`, `idx_event_faculty`, `idx_event_featured_status_end`), `@PrePersist` + `@PreUpdate` | **Étendue** par 2 colonnes (`parentEventId: Long`, `recurrenceRule: String`) ; nouvelle annotation `@Index` `idx_event_parent` ajoutée à `@Table(indexes = {...})` |
| Enum `EventStatus` (DRAFT/PUBLISHED/CANCELLED/EXPIRED/BANNED) | [`EventStatus.java`](backend/src/main/java/ch/unige/events/entity/EventStatus.java) | — | Sérialisation `@Enumerated(STRING)` | Hérité tel quel — le `status` du parent est propagé aux occurrences à la génération |
| Migration Flyway `ALTER TABLE events ADD COLUMN` (cas le plus proche : SCRUM-117 allDay) | [`V12__add_featured_to_events.sql`](backend/src/main/resources/db/migration/V12__add_featured_to_events.sql) | 1-5 | `ALTER TABLE events ADD COLUMN IF NOT EXISTS ... ;` + `CREATE INDEX IF NOT EXISTS ...` | **Modèle direct** pour `V17__add_event_recurrence.sql` — même style succinct, pas de bloc DO PL/pgSQL nécessaire |
| Migration Flyway récente avec FK (SCRUM-138 — pattern self-référence Comment SCRUM-139) | [`V14__create_comments.sql`](backend/src/main/resources/db/migration/V14__create_comments.sql) | 22-29 | `CONSTRAINT fk_X FOREIGN KEY (col) REFERENCES table(id) ON DELETE SET NULL` | **Modèle direct** pour `fk_events_parent` (cf. décision 5) |
| Migration Flyway récente — last numbered (avant V17) | [`V14__create_comments.sql`](backend/src/main/resources/db/migration/V14__create_comments.sql), [`V14__create_follows.sql`](backend/src/main/resources/db/migration/V14__create_follows.sql) (selon ordre de merge effectif) + V15/V16 mergés sur `main` post-prompt | — | Confirmation que V17 est libre au moment de la rédaction | Pré-check obligatoire au checkout (cf. décision 2) |

### 4.2 Services & Resources

| Domaine | Fichier | Lignes | Pattern à mimer | Note d'adaptation |
|---|---|---|---|---|
| `EventService.create(String, CreateEventRequest)` | [`EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | 116-149 | `@Transactional` ; charge `User.findByAuth0Id` ; populate Event ; valide status (`EXPIRED`/`CANCELLED`/`BANNED` interdits manuellement) ; `event.persist()` ; renvoie `EventDTO.from(event, 0L, ...)` | **Branchement** au début : `if (request.recurrence != null) return createRecurring(...) ;` (cf. décision 19). Logique standalone reste inchangée. |
| `EventService.getById(Long, String, boolean)` (anti-oracle) | [`EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | 152-181 | `@Transactional` (lecture — pattern projet), garde anti-oracle DRAFT/CANCELLED/BANNED → 404 | À **appeler** en première ligne de `getOccurrences(...)` — pas dupliquer la logique anti-oracle |
| `EventService.collectPublishValidationErrors(Event)` | [`EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | 320-340 | Validation `Future startDate`, `endDate > startDate`, `title not blank`, etc. | Appliqué au **parent** au moment du `publish` (existant). PAS appliqué aux occurrences (cf. décision 16) |
| `EventService.isCreatorOrAcceptedCoOrganizerPublic(Event, String auth0Id)` | [`EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | 436-438 | Wrapper public de la cascade SCRUM-136 | À **appeler** depuis `getOccurrences(...)` si on veut autoriser un co-org ACCEPTED à voir les occurrences d'un DRAFT (en pratique géré déjà par `getById` qui vérifie le co-org ACCEPTED) |
| `EventService.conflict(String message)` helper | [`EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | 285-290 | `WebApplicationException` + `Response.Status.CONFLICT` + `Map.of("error", "conflict", "message", message)` | **Modèle de pattern** d'erreur. Helpers analogues `badRequest(error, message)` et `unprocessable(error, message)` à dupliquer si nécessaire (cf. décision 26) |
| `EventService.delete(Long, String)` (purge attendances/favorites/views avant DELETE event) | [`EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | 240-244 | `entityManager.createQuery("DELETE FROM Attendance ...")` | **Inchangé** — la FK `fk_events_parent ON DELETE SET NULL` (décision 5) gère le cas occurrences orphelines automatiquement, pas besoin d'ajouter un DELETE explicite côté `EventService.delete` |
| `EventService.cancel(Long, String)` | [`EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | 247-265 | `event.status = CANCELLED` (uniquement le row ciblé) | **Inchangé** — pas de cascade aux occurrences (cf. décision 18) |
| `CalendarService.generateIcsFeed(UUID)` (1 VEVENT par row) | [`CalendarService.java`](backend/src/main/java/ch/unige/events/service/CalendarService.java) | 48-79 | Bulk query Event.find("id IN ?1 AND status = ?2", allIds, PUBLISHED) | **Inchangé** (cf. décision 13) — chaque occurrence row génère son propre VEVENT |
| `EventResource` (POST + GET + PUT + DELETE + PATCH cancel/restore/publish + GET /featured) | [`EventResource.java`](backend/src/main/java/ch/unige/events/resource/EventResource.java) | 31-175 | `@Path("/events")` racine ; constructor injection `final EventService eventService, SecurityIdentity identity` ; `@PerUserRateLimit` sur les mutations | **Étendu** d'un seul handler `@GET @Path("/{id}/occurrences") @PermitAll` (cf. décision 28) — pas de nouvelle classe |
| Pagination `@DefaultValue + @Min/@Positive/@Max` | [`EventResource.java`](backend/src/main/java/ch/unige/events/resource/EventResource.java) | 60-61 | `page=0/size=20`, `@Max(100)` | Pour `getOccurrences` : `page=0/size=52`, `@Max(52)` (la limite logique étant 52 occurrences max) |
| `@PerUserRateLimit(name = "events.create", max = 10)` sur POST /events | [`EventResource.java`](backend/src/main/java/ch/unige/events/resource/EventResource.java) | 86-93 | Bucket par utilisateur, anti-spam | **Inchangé** — la création récurrente reste sous le même bucket (cf. décision 27) |
| Préfixe API `/api` apposé par config | [`application.properties`](backend/src/main/resources/application.properties) | 1 | `quarkus.http.root-path=api` | `@Path("/{id}/occurrences")` reste relatif → public `/api/events/{id}/occurrences` |

### 4.3 Tests

| Domaine | Fichier | Lignes | Pattern à mimer | Note d'adaptation |
|---|---|---|---|---|
| Test entité `@QuarkusTest` + `@PrePersist` + assignabilité | [`EventTest.java`](backend/src/test/java/ch/unige/events/entity/EventTest.java) | — | `event.prePersist()` puis `assertNotNull(createdAt)` ; `fieldsAreAssignable()` | **Étendu** de 2 sentinels d'assignabilité (`parentEventId`, `recurrenceRule`) |
| Service mock `@Mock @ApplicationScoped extends EventService` | [`EventServiceMock.java`](backend/src/test/java/ch/unige/events/service/EventServiceMock.java) | 27-100 | `volatile boolean force*` + `reset()` + override de `getAll`, `create`, `update`, `getById`, etc. | **Étendu** d'override `createRecurring(...)` + `getOccurrences(...)` — flags `forceRecurrenceUnbounded`, `forceRecurrenceTooMany`, `nextOccurrencesResponse` |
| Service coverage `@TestProfile(ShareServiceCoverageProfile.class)` + `@TestTransaction` | [`EventServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java) | — | helpers `persistUser`/`persistEvent` ; assertions `WebApplicationException.getResponse().getStatus()` | **Étendu** de ~15 sentinels DB-backed (création récurrente atomique, comptage occurrences, anti-oracle, cancel non-cascadé, DELETE parent → occurrences orphelines) |
| Profile coverage `quarkus.arc.exclude-types` | [`ShareServiceCoverageProfile.java`](backend/src/test/java/ch/unige/events/service/ShareServiceCoverageProfile.java) | 13-31 | `EventServiceMock` y figure déjà (ligne 22) | **Aucune modification nécessaire** pour SCRUM-147 |
| Test Resource `@QuarkusTest` + `@TestSecurity(user="auth0\|alice")` + RestAssured | [`EventResourceTest.java`](backend/src/test/java/ch/unige/events/resource/EventResourceTest.java) | — | JSON Hamcrest matchers ; `BeforeEach reset()` ; `body("error", equalTo("..."))` | **Étendu** de ~7 sentinels Resource (POST recurring valide, GET occurrences anonyme PUBLISHED, GET 404 anti-oracle DRAFT non-créateur, validation Bean) |
| Test DTO factory | [`EventDTOTest.java`](backend/src/test/java/ch/unige/events/dto/event/EventDTOTest.java) | — | `EventDTO.from(event, 0L, ...)` puis assertions champs | **Étendu** de 3 sentinels (parent : `parentEventId=null` + `recurrenceRule="..."` ; occurrence : `parentEventId=<id>` + `recurrenceRule=null` ; standalone : les deux à `null`) |
| Test pur (hors Quarkus) — *aucun pattern existant pour le moment* | — | — | JUnit standalone, pas de bootstrap Quarkus | **Nouveau** : `RecurrenceGeneratorTest` — pur JUnit, ~12 sentinels |

---

## Plan d'implémentation par étape (ordre strict — openapi-first)

### Étape 0 — `openapi/openapi.yaml` (EN PREMIER, règle d'or)

**Règle d'or projet** : modifier `openapi/openapi.yaml` AVANT toute ligne de code Java ([`backend/AGENTS.md` lignes 77-80](backend/AGENTS.md#L77-L80)).

**0.1 — Schéma `RecurrenceFrequency`** (à insérer dans `components.schemas`, à proximité d'`EventStatus`) :

```yaml
    RecurrenceFrequency:
      type: string
      enum: [WEEKLY, BIWEEKLY, MONTHLY]
      description: |
        Fréquence de récurrence d'un événement (SCRUM-147).
        - `WEEKLY` : occurrences espacées de 7 jours.
        - `BIWEEKLY` : occurrences espacées de 14 jours.
        - `MONTHLY` : occurrences espacées de 1 mois calendaire (`Period.ofMonths(1)`,
          gère 31→28 février naturellement).
        Ne supporte pas DAILY ni YEARLY en S7.
```

**0.2 — Schéma `RecurrenceRequest`** :

```yaml
    RecurrenceRequest:
      type: object
      description: |
        Bloc optionnel de `CreateEventRequest` (SCRUM-147) qui matérialise une récurrence.
        Si présent, `EventService.create` délègue à `createRecurring` qui crée le parent
        + jusqu'à 51 occurrences en une seule transaction atomique (limite hard 52 rows
        total).

        Au moins un de `endDate` ou `maxOccurrences` doit être fourni — sinon
        `400 recurrence_unbounded`. Si les deux sont présents, le générateur s'arrête
        au plus restrictif des deux.
      required: [frequency]
      properties:
        frequency:
          $ref: '#/components/schemas/RecurrenceFrequency'
        endDate:
          type: string
          format: date
          nullable: true
          description: |
            Date inclusive jusqu'à laquelle générer des occurrences. Une occurrence dont
            `startDate.toLocalDate() > endDate` n'est pas générée. Doit être ≥ à
            `startDate.toLocalDate()` du parent — sinon `400 recurrence_end_before_start`.
        maxOccurrences:
          type: integer
          minimum: 1
          maximum: 52
          nullable: true
          description: |
            Nombre maximal d'occurrences (parent inclus). Borné à 52 par Bean Validation
            — au-delà, `400` Bean Validation. Le générateur s'arrête au minimum entre ce
            seuil et la borne `endDate` (si fournie).
```

**0.3 — Enrichir `Event` schema** (après `updatedAt`, ligne ~338) :

```yaml
        parentEventId:
          type: integer
          format: int64
          nullable: true
          readOnly: true
          description: |
            ID de l'événement parent (template récurrent) si cet event est une occurrence
            d'une récurrence (SCRUM-147). `null` pour un parent récurrent ou pour un
            événement standalone non-récurrent. La FK `fk_events_parent` côté DB est
            `ON DELETE SET NULL` : si le parent est supprimé, ce champ devient `null` et
            l'occurrence devient un standalone orphelin (préserve inscriptions/favoris).
        recurrenceRule:
          type: string
          maxLength: 500
          nullable: true
          readOnly: true
          description: |
            Règle de récurrence au format RFC 5545 RRULE simplifié (SCRUM-147), portée
            UNIQUEMENT par le **parent** récurrent. Format autorisé :
            `FREQ=WEEKLY;UNTIL=YYYYMMDD`, `FREQ=BIWEEKLY;COUNT=N`,
            `FREQ=MONTHLY;UNTIL=YYYYMMDD;COUNT=N`. `null` sur les occurrences enfants et
            sur les standalones non-récurrents. PAS de support BYDAY/EXDATE/INTERVAL en S7.
```

**0.4 — Enrichir `CreateEventRequest`** (ajouter le champ `recurrence`, après `tags`) :

```yaml
        recurrence:
          allOf:
            - $ref: '#/components/schemas/RecurrenceRequest'
          nullable: true
          description: |
            Bloc optionnel SCRUM-147. Si renseigné, `POST /events` matérialise un parent
            (avec `recurrenceRule` calculée) + jusqu'à 51 occurrences. Statut hérité du
            parent (DRAFT par défaut, ou `request.status` si fourni). Voir
            `RecurrenceRequest` pour la sémantique.
```

**0.5 — Path `/events/{id}/occurrences`** (à insérer après `/events/{id}/duplicate` ou à la fin du bloc `/events/...`, à proximité de `/events/{id}/comments` ligne ~3012) :

```yaml
  /events/{id}/occurrences:
    get:
      summary: Lister les occurrences d'un événement parent récurrent (SCRUM-147)
      description: |
        Retourne la liste des événements dont `parentEventId` matche `{id}`, triés par
        `startDate ASC, id ASC`. Si l'event ciblé n'est pas un parent récurrent
        (occurrence elle-même, standalone non-récurrent, ou parent sans occurrences
        encore générées), retourne `200 OK` + `[]` — pas de `404`. Le client lit
        `event.recurrenceRule != null` sur `GET /events/{id}` pour distinguer un parent.

        **Visibilité** : passe par `EventService.getById(id, callerAuth0Id, isAdmin)`.
        Un event invisible (DRAFT non-créateur, CANCELLED non-créateur, BANNED, id
        inconnu) retourne `404 not_found` (anti-oracle ISSUE-92).
      operationId: getEventOccurrences
      tags: [events]
      security:
        - {}
        - BearerAuth: []
      parameters:
        - name: id
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
            maximum: 52
            default: 52
      responses:
        '200':
          description: Liste des occurrences (vide si pas de parent récurrent)
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Event'
        '400':
          description: Pagination invalide (`size > 52`, `page < 0`)
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

**0.6 — Codes d'erreur enrichis sur `POST /events`** — la section `responses` du POST existant (ligne ~1542+) doit refléter les nouveaux codes d'erreur :

- `400` peut désormais aussi contenir `error: "recurrence_unbounded"` ou `error: "recurrence_end_before_start"`,
- `422` peut contenir `error: "recurrence_too_many"`.

Mettre à jour la description de la réponse `400` du POST /events pour mentionner ces deux nouveaux slugs.

**Checks intermédiaires :**
- YAML valide (lint via `yamllint openapi/openapi.yaml` ou simplement `git diff` qui doit afficher le diff complet sans erreur de parsing).
- `git diff --stat openapi/` non-vide.
- Note : `openapi/openapi.yaml` contient un path `/events/{id}/view` dupliqué (lignes ~2833 et ~2911) — c'est un artefact pré-existant **hors scope** SCRUM-147, ne pas le toucher.

**Commit suggéré.** `docs(scrum-147): add OpenAPI schemas and path for event recurrence`

---

### Étape 1 — Migration Flyway `V17__add_event_recurrence.sql`

**Fichier à créer.** `backend/src/main/resources/db/migration/V17__add_event_recurrence.sql`

**Pré-check obligatoire** :
```bash
docker exec -w /workspace unige-events-app-1 bash -c "ls backend/src/main/resources/db/migration | sort"
```
Selon le résultat, basculer en `V18` (si une PR concurrente prend V17 entre-temps) ou en `V16` (très improbable — V16 est déjà mergé) (cf. décision 2).

**Contenu** :

```sql
-- SCRUM-147 — Récurrence sur Event : ajout de 2 colonnes parent_event_id +
-- recurrence_rule à la table events. Chaque occurrence est une row events
-- standalone avec parent_event_id pointant vers le template parent.
--
-- ON DELETE SET NULL : la spec (décision 5) impose que le DELETE physique du
-- parent (après cancel) préserve les occurrences orphelines (parent_event_id
-- = NULL) — leurs inscriptions, favoris, vues et comptages sont conservés.
-- Sans cette clause, RESTRICT par défaut bloquerait le DELETE côté DB.
--
-- Numérotation V17 : sur origin/main au moment de la rédaction, V14 (follows,
-- SCRUM-138), V15 (comments, SCRUM-139) et V16 (PR concurrente mergée juste
-- avant la rédaction de la spec) sont mergés. V17 est donc le prochain numéro
-- libre. Si une nouvelle PR concurrente prend V17 entre-temps, basculer en
-- V18 dans un commit fix(scrum-147): rebase V17 → V18. Le commit feat documente
-- le numéro retenu (cf. spec décision 2).

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS parent_event_id BIGINT,
    ADD COLUMN IF NOT EXISTS recurrence_rule VARCHAR(500);

ALTER TABLE events
    ADD CONSTRAINT fk_events_parent
        FOREIGN KEY (parent_event_id) REFERENCES events(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_event_parent ON events(parent_event_id);
```

**Notes.**
- Pas d'index sur `recurrence_rule` (cf. décision 3 — pas de query par RRULE prévue en S7).
- `IF NOT EXISTS` sur les colonnes pour idempotence (cohérent avec V12). PostgreSQL 9.6+.
- Le `ADD CONSTRAINT` n'a pas de variante `IF NOT EXISTS` en standard SQL ; si la migration est ré-appliquée par erreur sur une base déjà à V17, Flyway empêchera le re-run (checksum). Pas besoin de le wrapper en `DO $$ BEGIN ... END $$`.

**Checks intermédiaires.**
- `./mvnw verify` (via devcontainer) — Flyway doit appliquer V17 au démarrage de DevServices PostgreSQL.
- `git diff --stat backend/src/main/resources/db/migration/` doit montrer `V17__add_event_recurrence.sql` ajouté.
- V1..V16 strictement inchangées.
- Hibernate `validate` au démarrage doit confirmer que l'entité (étape 3) matche le schéma V17.

**Commit suggéré.** `feat(scrum-147): add V17 migration for event recurrence columns`

---

### Étape 2 — Enum `RecurrenceFrequency`

**Fichier à créer.** `backend/src/main/java/ch/unige/events/entity/RecurrenceFrequency.java`

**Contenu** :

```java
package ch.unige.events.entity;

/**
 * Fréquence de récurrence d'un événement (SCRUM-147).
 * <p>
 * Sérialisé en String dans le JSON et la colonne SQL via {@code @Enumerated(STRING)}
 * (pattern projet — cf. {@link EventStatus}, {@link EventCategory}, {@link Faculty}).
 */
public enum RecurrenceFrequency {
    /** Occurrences espacées de 7 jours ({@code Period.ofDays(7)}). */
    WEEKLY,

    /** Occurrences espacées de 14 jours ({@code Period.ofDays(14)}). */
    BIWEEKLY,

    /** Occurrences espacées de 1 mois calendaire ({@code Period.ofMonths(1)}). */
    MONTHLY
}
```

**Checks intermédiaires.**
- `./mvnw compile` doit passer.

**Commit suggéré.** `feat(scrum-147): add RecurrenceFrequency enum`

---

### Étape 3 — Entité `Event` enrichie

**Fichier à modifier.** [`backend/src/main/java/ch/unige/events/entity/Event.java`](backend/src/main/java/ch/unige/events/entity/Event.java)

**Modifications** :

1. **Annotation `@Table`** — ajouter `idx_event_parent` :

```java
@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_event_creator", columnList = "creator_id"),
        @Index(name = "idx_event_start_date", columnList = "start_date"),
        @Index(name = "idx_event_faculty", columnList = "faculty"),
        @Index(name = "idx_event_featured_status_end", columnList = "featured, status, end_date"),
        @Index(name = "idx_event_parent", columnList = "parent_event_id")
})
public class Event extends PanacheEntity {
```

2. **Nouveaux champs** — ajouter avant `@Column(updatable = false) public LocalDateTime createdAt;` (ligne 83-84) :

```java
    @Column(name = "parent_event_id")
    public Long parentEventId;

    @Column(name = "recurrence_rule", length = 500)
    public String recurrenceRule;
```

**Notes.**
- Pas d'annotation `@ManyToOne` (cf. décision 4) — pointeur Long brut.
- `length = 500` aligne sur `VARCHAR(500)` côté DB (cf. décision 7 / migration V17).
- Pas de validation `@Pattern` côté entité — la validation du format RRULE est gérée par construction côté `RecurrenceGenerator` (étape 5).

**Checks intermédiaires.**
- `./mvnw compile` doit passer.
- Hibernate `validate` au démarrage doit confirmer que l'entité matche le schéma V17.

**Commit suggéré.** `feat(scrum-147): extend Event entity with parentEventId and recurrenceRule`

---

### Étape 4 — DTOs : `RecurrenceRequest` + `CreateEventRequest` + `EventDTO`

**Fichier à créer.** `backend/src/main/java/ch/unige/events/dto/event/RecurrenceRequest.java`

```java
package ch.unige.events.dto.event;

import ch.unige.events.entity.RecurrenceFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Bloc optionnel de {@link CreateEventRequest} qui matérialise une récurrence
 * (SCRUM-147). Au moins un de {@code endDate} ou {@code maxOccurrences} doit être
 * fourni — sinon le service jette {@code 400 recurrence_unbounded}.
 */
public record RecurrenceRequest(
        @NotNull
        RecurrenceFrequency frequency,

        LocalDate endDate,

        @Min(1)
        @Max(52)
        Integer maxOccurrences
) {}
```

**Fichier à modifier.** [`backend/src/main/java/ch/unige/events/dto/event/CreateEventRequest.java`](backend/src/main/java/ch/unige/events/dto/event/CreateEventRequest.java)

```java
package ch.unige.events.dto.event;

import ch.unige.events.entity.EventStatus;
import jakarta.validation.Valid;

public class CreateEventRequest extends EventRequestBase {
    private EventStatus status;

    @Valid
    public RecurrenceRequest recurrence;

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
}
```

**Notes.**
- `recurrence` reste public (pas de getter/setter) — cohérent avec les autres champs publics de `EventRequestBase` (Jackson désérialise correctement).
- `@Valid` sur le champ déclenche la validation imbriquée de `RecurrenceRequest`.
- Pas de `recurrence` sur `UpdateEventRequest` ([`UpdateEventRequest.java`](backend/src/main/java/ch/unige/events/dto/event/UpdateEventRequest.java)) — un PUT ne crée pas de récurrence (cf. décision 17, pas de propagation).

**Fichier à modifier.** [`backend/src/main/java/ch/unige/events/dto/event/EventDTO.java`](backend/src/main/java/ch/unige/events/dto/event/EventDTO.java)

Ajouter 2 champs au record et les propager dans la factory :

```java
public record EventDTO(
        Long id,
        String title,
        // ... 25 champs existants ...
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long parentEventId,
        String recurrenceRule
) {
    public static EventDTO from(
            Event event,
            long attendingCount,
            Long availableSpots,
            long waitlistedCount,
            Long viewCount,
            Long interestedCount
    ) {
        return new EventDTO(
                event.id,
                event.title,
                // ... 25 valeurs existantes ...
                event.createdAt,
                event.updatedAt,
                event.parentEventId,
                event.recurrenceRule
        );
    }
}
```

**Checks intermédiaires.**
- `./mvnw compile` doit passer.
- Tous les call-sites de `EventDTO.from(...)` (dans `EventService`, `EventServiceMock`, `FavoriteService`, `EventCoOrganizerService`, etc.) doivent se compiler — la signature ne change pas, seul le record acquiert deux champs propagés à la lecture du `Event`.

**Commit suggéré.** `feat(scrum-147): add RecurrenceRequest DTO and extend CreateEventRequest/EventDTO`

---

### Étape 5 — Helper `RecurrenceGenerator`

**Fichier à créer.** `backend/src/main/java/ch/unige/events/util/RecurrenceGenerator.java`

```java
package ch.unige.events.util;

import ch.unige.events.entity.RecurrenceFrequency;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Génère les couples (startDate, endDate) des occurrences d'une récurrence
 * (SCRUM-147). Fonction pure — testable hors Quarkus en pur JUnit.
 * <p>
 * La méthode retourne les occurrences <strong>hors parent</strong> (le parent vit
 * à {@code parentStart/parentEnd} et est créé séparément par le service). Le total
 * matérialisé est donc parent + N rows = 1 + N rows, plafonné à 52 (la limite
 * produit) — cette classe peut donc retourner au maximum 51 ranges.
 */
public final class RecurrenceGenerator {

    /** Limite hard d'occurrences générées (parent inclus). Cf. spec décision 9. */
    public static final int MAX_TOTAL_OCCURRENCES = 52;

    private RecurrenceGenerator() {}

    public record DateRange(LocalDateTime start, LocalDateTime end) {}

    /**
     * Génère les ranges (start, end) des occurrences hors parent.
     *
     * @param parentStart    date/heure de début du parent (event.startDate)
     * @param parentEnd      date/heure de fin du parent (event.endDate)
     * @param frequency      WEEKLY / BIWEEKLY / MONTHLY
     * @param untilDate      date inclusive jusqu'à laquelle générer ; null = pas de borne haute
     * @param maxOccurrences nombre max total d'occurrences (parent + enfants) ;
     *                       null = pas de borne, mais plafonné à {@link #MAX_TOTAL_OCCURRENCES}.
     *                       Doit être &gt; 0 si fourni.
     * @return liste de ranges hors parent (taille 0..51)
     * @throws IllegalArgumentException si {@code untilDate == null && maxOccurrences == null}
     */
    public static List<DateRange> generate(
            LocalDateTime parentStart,
            LocalDateTime parentEnd,
            RecurrenceFrequency frequency,
            LocalDate untilDate,
            Integer maxOccurrences
    ) {
        if (untilDate == null && maxOccurrences == null) {
            throw new IllegalArgumentException(
                    "RecurrenceGenerator requires at least one of untilDate or maxOccurrences");
        }

        Period spacing = switch (frequency) {
            case WEEKLY -> Period.ofDays(7);
            case BIWEEKLY -> Period.ofDays(14);
            case MONTHLY -> Period.ofMonths(1);
        };

        int effectiveCap = MAX_TOTAL_OCCURRENCES;
        if (maxOccurrences != null) {
            effectiveCap = Math.min(effectiveCap, maxOccurrences);
        }

        List<DateRange> ranges = new ArrayList<>();
        // n=1 = première occurrence APRÈS le parent. Le parent compte pour 1 dans le cap.
        for (int n = 1; n < effectiveCap; n++) {
            LocalDateTime start = parentStart.plus(spacing.multipliedBy(n));
            LocalDateTime end = parentEnd.plus(spacing.multipliedBy(n));

            if (untilDate != null && start.toLocalDate().isAfter(untilDate)) {
                break;
            }

            ranges.add(new DateRange(start, end));
        }
        return ranges;
    }
}
```

**Notes.**
- Switch expression Java 21 (cohérent avec le baseline projet — Quarkus 3 / Java 21).
- `spacing.multipliedBy(n)` : `Period.multipliedBy(int)` est disponible depuis Java 9. Pour MONTHLY, `Period.ofMonths(1).multipliedBy(3) == Period.ofMonths(3)` — `LocalDateTime.plus(Period)` gère correctement le passage de mois (31 janvier → 28 février).
- Le `effectiveCap` cappe à 52 même si `maxOccurrences = null` — équivalent à « 51 enfants max », cohérent avec la limite produit.
- La méthode retourne au plus 51 ranges (le parent compte pour 1 dans le total de 52).

**Checks intermédiaires.**
- `./mvnw compile` doit passer.
- Lien fonctionnel testé à l'étape 8 via `RecurrenceGeneratorTest` (pur JUnit, pas de bootstrap Quarkus).

**Commit suggéré.** `feat(scrum-147): add RecurrenceGenerator utility`

---

### Étape 6 — Service `EventService` — `createRecurring(...)` + `getOccurrences(...)`

**Fichier à modifier.** [`backend/src/main/java/ch/unige/events/service/EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java)

**6.1 — Branchement dans `create(...)` (ligne 116)** :

```java
@Transactional
public EventDTO create(String auth0Id, CreateEventRequest request) {
    if (request.recurrence != null) {
        return createRecurring(auth0Id, request);
    }

    // ... logique standalone existante (lignes 118-148) inchangée ...
}
```

**6.2 — Nouvelle méthode `createRecurring(...)`** :

```java
@Transactional
public EventDTO createRecurring(String auth0Id, CreateEventRequest request) {
    RecurrenceRequest recurrence = request.recurrence;

    // Validation business du bloc recurrence (Bean Validation a déjà couvert
    // frequency != null, maxOccurrences in [1, 52]).
    if (recurrence.endDate() == null && recurrence.maxOccurrences() == null) {
        throw badRequestRecurrence("recurrence_unbounded",
                "At least one of recurrence.endDate or recurrence.maxOccurrences must be provided.");
    }
    if (recurrence.endDate() != null
            && recurrence.endDate().isBefore(request.startDate.toLocalDate())) {
        throw badRequestRecurrence("recurrence_end_before_start",
                "recurrence.endDate must be >= startDate.");
    }

    // Persiste le parent en réutilisant exactement la séquence du create() standalone.
    Event parent = persistParent(auth0Id, request);
    parent.recurrenceRule = buildRecurrenceRule(recurrence);

    // Génère les occurrences (hors parent).
    List<RecurrenceGenerator.DateRange> ranges = RecurrenceGenerator.generate(
            parent.startDate,
            parent.endDate,
            recurrence.frequency(),
            recurrence.endDate(),
            recurrence.maxOccurrences());

    // Atomicité : si une persist() échoue, toute la transaction rollback.
    for (RecurrenceGenerator.DateRange range : ranges) {
        persistOccurrence(parent, range);
    }

    long att = countAttending(parent.id);
    return EventDTO.from(parent, att, computeAvailableSpots(parent.capacity, att), 0L, null, null);
}

private Event persistParent(String auth0Id, CreateEventRequest request) {
    User creator = User.findByAuth0Id(auth0Id)
            .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

    Event event = new Event();
    event.title = request.title;
    event.description = request.description;
    event.location = request.location;
    event.startDate = request.startDate;
    event.endDate = request.endDate;
    event.category = request.category;
    event.faculty = request.faculty;
    event.bannerUrl = request.bannerUrl;
    event.capacity = request.capacity;
    event.allDay = Boolean.TRUE.equals(request.allDay);
    event.websiteUrl = request.websiteUrl;
    event.contactEmail = request.contactEmail;
    event.registrationDeadline = request.registrationDeadline;
    event.tags = normalizeTags(request.tags);
    event.creator = creator;
    if (request.getStatus() == EventStatus.EXPIRED) {
        throw new BadRequestException("EXPIRED is a system-only status and cannot be set manually");
    }
    if (request.getStatus() == EventStatus.CANCELLED) {
        throw new BadRequestException("CANCELLED is not a valid initial status");
    }
    if (request.getStatus() == EventStatus.BANNED) {
        throw new BadRequestException("BANNED is a moderation-only status and cannot be set manually");
    }
    event.status = request.getStatus() != null ? request.getStatus() : EventStatus.DRAFT;
    event.persist();
    return event;
}

private void persistOccurrence(Event parent, RecurrenceGenerator.DateRange range) {
    Event occurrence = new Event();
    occurrence.title = parent.title;
    occurrence.description = parent.description;
    occurrence.location = parent.location;
    occurrence.startDate = range.start();
    occurrence.endDate = range.end();
    occurrence.category = parent.category;
    occurrence.faculty = parent.faculty;
    occurrence.bannerUrl = parent.bannerUrl;
    occurrence.capacity = parent.capacity;
    occurrence.allDay = parent.allDay;
    occurrence.websiteUrl = parent.websiteUrl;
    occurrence.contactEmail = parent.contactEmail;
    occurrence.registrationDeadline = parent.registrationDeadline;
    occurrence.tags = parent.tags == null ? List.of() : new ArrayList<>(parent.tags);
    occurrence.creator = parent.creator;
    occurrence.status = parent.status;     // hérite (cf. décision 11)
    occurrence.parentEventId = parent.id;  // pointeur vers le parent
    occurrence.recurrenceRule = null;      // jamais sur les enfants (cf. décision 7)
    occurrence.persist();
}

private static String buildRecurrenceRule(RecurrenceRequest r) {
    StringBuilder sb = new StringBuilder("FREQ=").append(r.frequency().name());
    if (r.endDate() != null) {
        sb.append(";UNTIL=")
                .append(r.endDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
    }
    if (r.maxOccurrences() != null) {
        sb.append(";COUNT=").append(r.maxOccurrences());
    }
    return sb.toString();
}
```

**6.3 — Nouvelle méthode `getOccurrences(...)`** :

```java
public List<EventDTO> getOccurrences(Long parentId, String auth0Id, boolean isAdmin, int page, int size) {
    // Garde anti-oracle ISSUE-92 : event invisible → 404 (cf. décision 14).
    getById(parentId, auth0Id, isAdmin);

    List<Event> occurrences = Event.<Event>find(
            "parentEventId = ?1 order by startDate asc, id asc",
            parentId
    ).page(page, size).list();

    return toEventDTOs(occurrences);
}
```

**6.4 — Helper d'erreur (à ajouter en bas de la classe, après `conflict` ligne 285-290)** :

```java
private static WebApplicationException badRequestRecurrence(String error, String message) {
    return new WebApplicationException(
            Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiErrorResponse(error, message))
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build());
}
```

**Note.** Si une décision Copilot post-PR demande une factorisation (ex. helpers `badRequest`/`unprocessable` analogues à `conflict`), elle sera traitée dans un commit `fix(scrum-147): apply Copilot review — extract error helpers`. Décision SCRUM-147 prudente : on ne pré-factorise pas (cf. décision 26).

**Checks intermédiaires.**
- `./mvnw compile` doit passer.
- `./mvnw verify` (via devcontainer) — les tests existants `EventServiceCoverageTest` doivent rester verts (le branchement n'affecte pas les call-sites legacy `request.recurrence == null`).

**Commit suggéré.** `feat(scrum-147): add createRecurring with atomic occurrence generation`

---

### Étape 7 — Resource — ajout `@GET @Path("/{id}/occurrences")`

**Fichier à modifier.** [`backend/src/main/java/ch/unige/events/resource/EventResource.java`](backend/src/main/java/ch/unige/events/resource/EventResource.java)

**Ajout du handler** (à insérer après le `getById` existant ligne 95-103) :

```java
    @GET
    @Path("/{id}/occurrences")
    @PermitAll
    public List<EventDTO> getOccurrences(
            @PathParam("id") Long id,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("52") @Positive @Max(52) int size) {
        String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
        boolean isAdmin = !identity.isAnonymous() && identity.hasRole("ADMIN");
        return eventService.getOccurrences(id, auth0Id, isAdmin, page, size);
    }
```

**Notes.**
- Pas de nouvelle classe (cf. décision 28).
- Pagination `defaults 0/52, @Max(52)` — la limite logique étant 52 occurrences max (parent + 51), une seule page suffit toujours en pratique.
- Anti-oracle géré côté service (`getOccurrences` appelle `getById` en première ligne).

**Checks intermédiaires.**
- `./mvnw compile` doit passer.
- Endpoint visible dans Swagger UI à `http://localhost:8080/q/swagger-ui` (en `quarkus:dev`) sous le tag `events`.

**Commit suggéré.** `feat(scrum-147): add GET /events/{id}/occurrences endpoint`

---

### Étape 8 — Tests

**Fichiers à créer / modifier** :

- **Créer** `backend/src/test/java/ch/unige/events/util/RecurrenceGeneratorTest.java` (pur JUnit, pas `@QuarkusTest`).
- **Modifier** `backend/src/test/java/ch/unige/events/entity/EventTest.java` (ajouter sentinels d'assignabilité).
- **Modifier** `backend/src/test/java/ch/unige/events/dto/event/EventDTOTest.java` (ajouter sentinels factory).
- **Modifier** `backend/src/test/java/ch/unige/events/service/EventServiceMock.java` (ajouter overrides).
- **Modifier** `backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java` (ajouter sentinels DB-backed).
- **Modifier** `backend/src/test/java/ch/unige/events/resource/EventResourceTest.java` (ajouter sentinels Resource).

**`ShareServiceCoverageProfile.java`** — **aucune modification** (`EventServiceMock` est déjà dans la liste d'exclusion ligne 22).

**Sentinels obligatoires (≥ 25, numérotés)** :

| # | Test | Cas | Code attendu | Type de test |
|---|---|---|---|---|
| 1 | `RecurrenceGeneratorTest.weekly_4Occurrences_returns3DatesSpacedBy7Days` | freq=WEEKLY, maxOccurrences=4 (3 enfants attendus, parent compte pour 1) | 3 ranges, espacés de 7 jours | Pur JUnit |
| 2 | `RecurrenceGeneratorTest.biweekly_6Occurrences_returns5DatesSpacedBy14Days` | freq=BIWEEKLY, maxOccurrences=6 | 5 ranges, espacés de 14 jours | Pur JUnit |
| 3 | `RecurrenceGeneratorTest.monthly_handlesShortFebruary` | freq=MONTHLY, parentStart=2026-01-31, maxOccurrences=3 | 2 ranges (28-fév-2026, 31-mars-2026 — `Period` rolling) | Pur JUnit |
| 4 | `RecurrenceGeneratorTest.untilBeforeMaxOccurrences_stopsAtUntil` | freq=WEEKLY, untilDate=parentStart+10jours, maxOccurrences=10 | 1 range (à +7j) | Pur JUnit |
| 5 | `RecurrenceGeneratorTest.maxOccurrencesBeforeUntil_stopsAtCount` | freq=WEEKLY, untilDate=parentStart+1an, maxOccurrences=3 | 2 ranges | Pur JUnit |
| 6 | `RecurrenceGeneratorTest.bothNull_throwsIllegalArgumentException` | untilDate=null, maxOccurrences=null | `IllegalArgumentException` | Pur JUnit |
| 7 | `RecurrenceGeneratorTest.maxOccurrencesAbove52_cappedTo52` | freq=WEEKLY, maxOccurrences=100 | 51 ranges (cap MAX_TOTAL_OCCURRENCES=52, parent compte pour 1) | Pur JUnit |
| 8 | `EventTest.fieldsAreAssignable_includesParentEventIdAndRecurrenceRule` | `event.parentEventId = 42L; event.recurrenceRule = "FREQ=WEEKLY;UNTIL=20260601"` | Affectation OK | Entity unit |
| 9 | `EventDTOTest.from_parentRecurringEvent_exposesRecurrenceRuleAndNullParentEventId` | Event avec `recurrenceRule = "FREQ=WEEKLY;..."` et `parentEventId = null` | DTO : `recurrenceRule="FREQ=WEEKLY;..."`, `parentEventId=null` | DTO unit |
| 10 | `EventDTOTest.from_occurrenceEvent_exposesParentEventIdAndNullRecurrenceRule` | Event avec `parentEventId = 42L` et `recurrenceRule = null` | DTO : `parentEventId=42`, `recurrenceRule=null` | DTO unit |
| 11 | `EventDTOTest.from_standaloneEvent_bothNull` | Event standalone non-récurrent | DTO : `parentEventId=null`, `recurrenceRule=null` | DTO unit |
| 12 | `EventServiceCoverageTest.createRecurring_weekly4Occurrences_persists1ParentAnd3Children` | POST recurrence WEEKLY maxOccurrences=4 | 1 parent + 3 occurrences en DB ; `parentEventId` des enfants = parent.id ; `recurrenceRule` non-null sur le parent, null sur les enfants | Service coverage (DB) |
| 13 | `EventServiceCoverageTest.createRecurring_atomicity_rollbackOnPersistFailure` | Force une exception sur la 3e occurrence (ex. capacity invalide en injection mock) | 0 row en DB (parent rollback aussi). Sentinel best-effort — couvert via injection RecurrenceGenerator buggué OU via `@Transactional` smoke | Service coverage (DB) |
| 14 | `EventServiceCoverageTest.createRecurring_withoutEndDateOrMaxOccurrences_returns400_recurrenceUnbounded` | RecurrenceRequest avec endDate=null ET maxOccurrences=null | `400 recurrence_unbounded` | Service coverage (DB) |
| 15 | `EventServiceCoverageTest.createRecurring_endDateBeforeStart_returns400` | RecurrenceRequest avec endDate < parent.startDate.toLocalDate() | `400 recurrence_end_before_start` | Service coverage (DB) |
| 16 | `EventServiceCoverageTest.createRecurring_inheritsParentStatusPublished` | request.status = PUBLISHED + recurrence | parent + enfants tous en PUBLISHED | Service coverage (DB) |
| 17 | `EventServiceCoverageTest.createRecurring_defaultsToDraft` | request.status = null + recurrence | parent + enfants tous en DRAFT | Service coverage (DB) |
| 18 | `EventServiceCoverageTest.create_withoutRecurrence_legacyBehaviorUnchanged` | request.recurrence = null (legacy POST) | 1 row standalone, `parentEventId=null`, `recurrenceRule=null` | Service coverage (DB) |
| 19 | `EventServiceCoverageTest.getOccurrences_parentRecurring_returnsChildrenSortedAsc` | Parent + 3 enfants persistés ; appel `getOccurrences(parentId, ...)` | 3 EventDTO triés `startDate ASC` | Service coverage (DB) |
| 20 | `EventServiceCoverageTest.getOccurrences_standaloneEvent_returns200EmptyList` | Event standalone (pas de récurrence) | `200 OK` + `[]`, pas de 404 | Service coverage (DB) |
| 21 | `EventServiceCoverageTest.getOccurrences_draftByNonCreator_returns404_antiOracle` | Parent en DRAFT, callerAuth0Id != créateur, isAdmin=false | `404 not_found` (hérité de `getById` — anti-oracle ISSUE-92) | Service coverage (DB) |
| 22 | `EventServiceCoverageTest.getOccurrences_bannedEvent_returns404` | Parent BANNED | `404 not_found` (hérité de `getById`) | Service coverage (DB) |
| 23 | `EventServiceCoverageTest.getOccurrences_draftByCreator_returns200WithChildren` | Parent DRAFT + 2 enfants, caller = créateur | `200 OK` + 2 enfants | Service coverage (DB) |
| 24 | `EventServiceCoverageTest.update_parentTitle_doesNotPropagateToOccurrences` | PUT /events/{parentId} avec nouveau titre | parent.title = nouveau, occurrences[i].title = ancien (cf. décision 17) | Service coverage (DB) |
| 25 | `EventServiceCoverageTest.cancel_parentDoesNotCascade` | PATCH /events/{parentId}/cancel | parent.status = CANCELLED, occurrences[i].status = inchangé (DRAFT/PUBLISHED) (cf. décision 18) | Service coverage (DB) |
| 26 | `EventServiceCoverageTest.delete_parent_setsOccurrencesParentEventIdToNull` | DELETE physique du parent (après cancel) | occurrences en DB avec `parent_event_id = NULL` (FK ON DELETE SET NULL) ; aucune row supprimée côté occurrences | Service coverage (DB) |
| 27 | `EventResourceTest.post_validRecurrenceWeekly_returns201_recurrenceRuleSetOnParent` | `POST /events` avec body `recurrence: { frequency: "WEEKLY", maxOccurrences: 4 }` | `201` + body `EventDTO` du parent avec `recurrenceRule="FREQ=WEEKLY;COUNT=4"` et `parentEventId=null` | Resource (Mock) |
| 28 | `EventResourceTest.post_recurrenceMaxOccurrences53_returns400_beanValidation` | `recurrence.maxOccurrences = 53` | `400` Bean Validation (`@Max(52)`) | Resource (Mock) |
| 29 | `EventResourceTest.post_recurrenceWithoutFrequency_returns400_beanValidation` | `recurrence.frequency = null` | `400` Bean Validation (`@NotNull`) | Resource (Mock) |
| 30 | `EventResourceTest.getOccurrences_parentPublishedAnonymous_returns200` | `GET /events/{parentId}/occurrences` anonyme sur parent PUBLISHED | `200` + liste enfants | Resource (Mock) |
| 31 | `EventResourceTest.getOccurrences_sizeOver52_returns400` | `?size=53` | `400` Bean Validation (`@Max(52)`) | Resource (Mock) |
| 32 | `EventResourceTest.getOccurrences_draftByNonCreator_returns404_antiOracle` | parent DRAFT, anonyme | `404` (anti-oracle hérité de `getById`) | Resource (Mock) |

**Anti-tests / pièges à éviter** :

- **Anti-test (mémoire SCRUM-138)** : ne **PAS** mettre l'attribut `attributes = "email=..."` dans `@TestSecurity` sauf pour un test qui déclenche `getOrCreateUser` — sinon le claim email leak entre tests via le proxy CDI et casse les assertions.
- **Profile coverage** : `ShareServiceCoverageProfile` n'a **rien à modifier** pour SCRUM-147 — `EventServiceMock` y figure déjà (ligne 22). Si une régression ARC apparaît, vérifier la ligne avant de modifier la liste.
- **Atomicité (sentinel #13)** : la simulation d'échec sur la 3e occurrence est délicate à orchestrer. Approche recommandée : forcer un `IllegalStateException` dans un sous-test `@QuarkusTest` qui injecte un parent avec `capacity = -1` (ou une valeur qui violerait une contrainte). Si la simulation s'avère trop coûteuse, marquer le sentinel **best-effort** et le couvrir par un test smoke « `@Transactional` rollback fonctionne — testé via le test générique sur les autres services ». Documenter explicitement dans le commit.
- **Bucket rate-limit `events.create`** : si `EventResourceTest.post_validRecurrenceWeekly...` consomme du quota, ajouter `RateLimitState.clearBuckets()` en `@BeforeEach` (pattern hérité de `CommentResourceTest` — cf. note d'implémentation SCRUM-139 [`sprint-context.md`](backend/docs/sprint-context.md)).

**Patterns de test (snippets-clés)** :

```java
// RecurrenceGeneratorTest.java — pur JUnit, pas de @QuarkusTest
class RecurrenceGeneratorTest {

    @Test
    void weekly_4Occurrences_returns3DatesSpacedBy7Days() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 18, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 1, 20, 0);

        var ranges = RecurrenceGenerator.generate(
                start, end, RecurrenceFrequency.WEEKLY, null, 4);

        assertEquals(3, ranges.size());
        assertEquals(start.plusDays(7), ranges.get(0).start());
        assertEquals(end.plusDays(7), ranges.get(0).end());
        assertEquals(start.plusDays(14), ranges.get(1).start());
        assertEquals(start.plusDays(21), ranges.get(2).start());
    }

    @Test
    void bothNull_throwsIllegalArgumentException() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 18, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 1, 20, 0);

        assertThrows(IllegalArgumentException.class, () ->
                RecurrenceGenerator.generate(
                        start, end, RecurrenceFrequency.WEEKLY, null, null));
    }
}
```

```java
// EventServiceCoverageTest — extrait
@Test
@TestTransaction
void createRecurring_weekly4Occurrences_persists1ParentAnd3Children() {
    User user = persistUser("auth0|alice");
    CreateEventRequest req = buildBaseEventRequest(user.auth0Id);
    req.recurrence = new RecurrenceRequest(
            RecurrenceFrequency.WEEKLY, null, 4);

    EventDTO parentDto = eventService.create(user.auth0Id, req);

    assertNotNull(parentDto.recurrenceRule());
    assertEquals("FREQ=WEEKLY;COUNT=4", parentDto.recurrenceRule());
    assertNull(parentDto.parentEventId());

    List<Event> children = Event.list("parentEventId = ?1 order by startDate", parentDto.id());
    assertEquals(3, children.size());
    assertTrue(children.stream().allMatch(c -> c.recurrenceRule == null));
    assertTrue(children.stream().allMatch(c -> c.parentEventId.equals(parentDto.id())));
}
```

**Checks intermédiaires.**
- `./mvnw verify` (via devcontainer) doit passer.
- Couverture JaCoCo > 90 % sur `RecurrenceFrequency`, `RecurrenceRequest`, `RecurrenceGenerator`, le delta `Event` (2 nouveaux champs), le delta `EventDTO` (2 nouveaux champs), le delta `EventService` (`createRecurring`, `getOccurrences`, `persistParent`, `persistOccurrence`, `buildRecurrenceRule`, `badRequestRecurrence`), le delta `EventResource` (`getOccurrences`).
- Sentinels listés ci-dessus tous verts nommément.

**Commit suggéré.** `test(scrum-147): cover entity, DTO, generator, service and resource sentinels`

---

### Étape 9 — Documentation

**Fichiers à modifier.**

**`backend/docs/data-model.md`** — section `### Event` (lignes 45-90 actuelles) à enrichir :
- 2 nouvelles lignes dans la table des champs : `parentEventId` (Long, nullable, FK auto-référence vers `events.id` avec `ON DELETE SET NULL` — cf. SCRUM-147) et `recurrenceRule` (String, `VARCHAR(500)`, nullable, format RFC 5545 simplifié — cf. SCRUM-147).
- Nouvel index : `idx_event_parent` (parent_event_id).
- Sous-section `#### Récurrence (SCRUM-147)` qui détaille :
  - sémantique parent/enfant (chaque occurrence est une row Event standalone),
  - format RRULE simplifié (`FREQ=WEEKLY;UNTIL=YYYYMMDD;COUNT=N`),
  - règle de cascade DELETE (ON DELETE SET NULL — replies orphelines préservées),
  - héritage du status parent → enfants à la création,
  - **non-propagation** de `PUT /events/{parentId}` aux occurrences,
  - **non-cascade** de `PATCH cancel`,
  - cap hard 52 occurrences (parent inclus).

**`backend/docs/api-contract.md`** — table « Endpoints implémentés » :
- Nouvelle ligne : `GET` | `/events/{id}/occurrences` | `@PermitAll` | Lister les occurrences d'un parent récurrent | 200, 400, 404
- Mise à jour de la ligne `POST /events` pour mentionner les nouveaux codes 400 (`recurrence_unbounded`, `recurrence_end_before_start`) et 422 (`recurrence_too_many`).

Puis ajouter une section dédiée `### Event Recurrence (SCRUM-147)` qui détaille :
- format `RecurrenceRequest`,
- sémantique parent/enfant (matérialisation par rows),
- règles d'erreur normalisées,
- non-propagation PUT/PATCH cancel.

**`backend/docs/sprint-context.md`** — insérer un bloc `## Sprint 7 — Récurrence sur Event + génération d'occurrences (SCRUM-147) — 2026-05-08` au-dessus du bloc SCRUM-139 (le plus récent du fichier), au format identique aux autres entrées « Livré » du même sprint.

**Pas de modification frontend.** `git diff --stat frontend/` doit rester strictement vide.

**Commit suggéré.** `docs(scrum-147): document recurrence in data-model and api-contract`

---

## Ordre d'implémentation strict

1. **Branchement.** `git fetch origin && git checkout -b feature/s7-recurrence origin/main --no-track` (le `--no-track` est non négociable — évite que `git push` ouvre une PR sur `main` par accident).
2. **Étape 0 — OpenAPI EN PREMIER.** Modifier `openapi/openapi.yaml` (1 schéma `RecurrenceRequest` + 1 enum `RecurrenceFrequency` + enrichissement `Event` + `CreateEventRequest` + 1 path `/events/{id}/occurrences`). Vérifier la validité YAML. ✅ checkpoint : `git diff --stat openapi/` non-vide.
3. **Étape 1 — Migration Flyway V17.** Pré-check `ls backend/src/main/resources/db/migration | sort` (basculer en V18 si V17 pris par PR concurrente entre-temps). ✅ checkpoint : `./mvnw verify` (via devcontainer) — Flyway applique V17 sur DevServices PostgreSQL.
4. **Étape 2 — Enum `RecurrenceFrequency`.** ✅ checkpoint : `./mvnw compile`.
5. **Étape 3 — Entité `Event` enrichie.** ✅ checkpoint : `./mvnw compile` ; Hibernate `validate` au démarrage `quarkus:dev` confirme la concordance schéma V17 ↔ entité.
6. **Étape 4 — DTOs.** ✅ checkpoint : `./mvnw compile` ; tous les call-sites de `EventDTO.from(...)` se compilent (la signature ne change pas).
7. **Étape 5 — Helper `RecurrenceGenerator`.** ✅ checkpoint : `./mvnw compile`.
8. **Étape 6 — Service `createRecurring` + `getOccurrences`.** ✅ checkpoint : `./mvnw verify` — les tests existants (Event/Attendance/etc.) doivent rester verts ; pas de cycle d'injection.
9. **Étape 7 — Resource `GET /events/{id}/occurrences`.** ✅ checkpoint : `./mvnw compile` ; smoke en `quarkus:dev` sur Swagger UI.
10. **Étape 8 — Tests.** ✅ checkpoint final : `./mvnw verify` vert + JaCoCo > 90 % sur le diff + tous les sentinels listés verts nommément.
11. **Étape 9 — Documentation.** Mise à jour `data-model.md`, `api-contract.md`, `sprint-context.md`. **Aucune** modification frontend. ✅ checkpoint : `git diff --stat frontend/` strictement vide.

---

## Commits atomiques suggérés

Format strictement conforme à [`.github/workflows/pr-title-check.yml`](.github/workflows/pr-title-check.yml) (regex `^([a-z]+)\(([^)]+)\): `, scope `scrum-147` obligatoire pour `feat`/`refactor`/`perf`) :

1. `docs(scrum-147): add OpenAPI schemas and path for event recurrence`
2. `feat(scrum-147): add V17 migration for event recurrence columns`
3. `feat(scrum-147): add RecurrenceFrequency enum`
4. `feat(scrum-147): extend Event entity with parentEventId and recurrenceRule`
5. `feat(scrum-147): add RecurrenceRequest DTO and extend CreateEventRequest/EventDTO`
6. `feat(scrum-147): add RecurrenceGenerator utility`
7. `feat(scrum-147): add createRecurring with atomic occurrence generation`
8. `feat(scrum-147): add GET /events/{id}/occurrences endpoint`
9. `test(scrum-147): cover entity, DTO, generator, service and resource sentinels`
10. `docs(scrum-147): document recurrence in data-model and api-contract`
11. (post-PR, si applicable) `fix(scrum-147): apply Copilot review — <description>`

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
- `git diff --stat backend/src/main/resources/db/migration/` contient `V17__add_event_recurrence.sql` (ou V18 si bascule documentée).
- `pom.xml` strictement **inchangé** (aucune nouvelle dépendance).
- `ShareServiceCoverageProfile.java` **inchangé** (`EventServiceMock` y figure déjà).

### Ouverture PR

1. `gh pr create` exécuté **depuis `/workspace`** dans le devcontainer.
2. Le body PR transite par fichier dédié pour éviter les soucis d'échappement de heredoc :
   ```bash
   cat /tmp/pr-body-scrum-147.md \
       | docker exec -i unige-events-app-1 bash -c "cat > /tmp/pr-body-scrum-147.md"
   docker exec -w /workspace unige-events-app-1 bash -c \
       "gh pr create --title 'feat(scrum-147): add event recurrence with occurrence generation' \
                     --body-file /tmp/pr-body-scrum-147.md \
                     --base main --head feature/s7-recurrence"
   ```
3. **Titre PR EXACT** (à copier-coller, validé par `pr-title-check.yml`) :
   ```
   feat(scrum-147): add event recurrence with occurrence generation
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
  - Si pertinent → corriger dans un commit `fix(scrum-147): …` + push + **répondre au commentaire** avec un lien vers le SHA via `gh api -X POST repos/.../pulls/<PR_NUM>/comments/{id}/replies`.
  - Si non-pertinent → **répondre poliment** en justifiant pourquoi la remarque n'est pas appliquée (cite la décision de la spec qui tranche).
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

- [ ] Branche `feature/s7-recurrence` créée depuis `origin/main` avec `--no-track`.
- [ ] OpenAPI `openapi/openapi.yaml` met à jour : 2 champs ajoutés au schéma `Event` (`parentEventId`, `recurrenceRule`), 1 schéma neuf `RecurrenceRequest`, 1 enum neuf `RecurrenceFrequency`, 1 champ ajouté à `CreateEventRequest` (`recurrence`), 1 path neuf `GET /events/{id}/occurrences`. Modifié EN PREMIER (avant toute ligne de Java).
- [ ] `V17__add_event_recurrence.sql` (ou `V18` si bascule documentée) créée, V1..V16 strictement intactes.
- [ ] Créés : `RecurrenceFrequency.java`, `RecurrenceRequest.java`, `RecurrenceGenerator.java` (+ record `DateRange`).
- [ ] Modifiés : `Event.java` (+2 champs + `idx_event_parent`), `CreateEventRequest.java` (+`recurrence`), `EventDTO.java` (+2 champs + factory), `EventService.java` (+`createRecurring`/`getOccurrences`/`persistParent`/`persistOccurrence`/`buildRecurrenceRule`/`badRequestRecurrence`), `EventResource.java` (+`getOccurrences` handler).
- [ ] Tests : 6+ fichiers (`RecurrenceGeneratorTest` neuf, `EventTest`, `EventDTOTest`, `EventServiceMock`, `EventServiceCoverageTest`, `EventResourceTest`), **≥ 25 sentinels** verts, **> 90 %** coverage JaCoCo sur le diff.
- [ ] `ShareServiceCoverageProfile.java` **inchangé** (`EventServiceMock` y est déjà).
- [ ] `backend/docs/data-model.md` (section Event enrichie + sous-section `#### Récurrence (SCRUM-147)`), `backend/docs/api-contract.md` (1 endpoint + section `### Event Recurrence (SCRUM-147)`), `backend/docs/sprint-context.md` (entrée S7 SCRUM-147) mis à jour dans le **même commit** que le code (ou commit `docs(scrum-147):` dédié).
- [ ] `git diff --stat frontend/` strictement **vide**.
- [ ] `pom.xml` strictement **inchangé**.
- [ ] `./mvnw verify` passe (via devcontainer).
- [ ] Sentinels nommément verts (extraits clés) : `RecurrenceGeneratorTest.weekly_4Occurrences_returns3DatesSpacedBy7Days`, `RecurrenceGeneratorTest.monthly_handlesShortFebruary`, `RecurrenceGeneratorTest.bothNull_throwsIllegalArgumentException`, `EventDTOTest.from_parentRecurringEvent_exposesRecurrenceRuleAndNullParentEventId`, `EventDTOTest.from_occurrenceEvent_exposesParentEventIdAndNullRecurrenceRule`, `createRecurring_weekly4Occurrences_persists1ParentAnd3Children`, `createRecurring_withoutEndDateOrMaxOccurrences_returns400_recurrenceUnbounded`, `createRecurring_endDateBeforeStart_returns400`, `createRecurring_inheritsParentStatusPublished`, `getOccurrences_parentRecurring_returnsChildrenSortedAsc`, `getOccurrences_standaloneEvent_returns200EmptyList`, `getOccurrences_draftByNonCreator_returns404_antiOracle`, `update_parentTitle_doesNotPropagateToOccurrences`, `cancel_parentDoesNotCascade`, `delete_parent_setsOccurrencesParentEventIdToNull`, `post_validRecurrenceWeekly_returns201_recurrenceRuleSetOnParent`, `post_recurrenceMaxOccurrences53_returns400_beanValidation`, `getOccurrences_parentPublishedAnonymous_returns200`.
- [ ] PR ouverte, **titre EXACT** : `feat(scrum-147): add event recurrence with occurrence generation`.
- [ ] Body PR conforme `.github/pull_request_template.md` (sections obligatoires Résumé / Changements / Tests / Test plan / Documentation).
- [ ] Reviewer Copilot demandé.
- [ ] Tous les checks GitHub Actions verts (`Lint PR title`, build backend, build frontend no-op, Sonar) + **SonarCloud Quality Gate vert**.
- [ ] PR **non mergée** par l'agent — l'utilisateur (Elie) merge lui-même.

---

## Interdits stricts

- ❌ PAS de modification frontend (`git diff --stat frontend/` strictement vide).
- ❌ PAS de modification des migrations V1..V16 (immutables).
- ❌ PAS de nouvelle entité `EventRecurrence` ni de nouvelle table — uniquement ALTER TABLE events (cf. décision 3).
- ❌ PAS de cascade `ON DELETE CASCADE` sur `fk_events_parent` — `ON DELETE SET NULL` strict (cf. décision 5).
- ❌ PAS de propagation aux occurrences sur `PUT /events/{parentId}` (cf. décision 17).
- ❌ PAS de cascade aux occurrences sur `PATCH /events/{parentId}/cancel` (cf. décision 18).
- ❌ PAS d'héritage automatique des co-organisateurs aux occurrences (cf. décision 12).
- ❌ PAS de RRULE compacte côté ICS feed (cf. décision 13 — chaque occurrence reste son VEVENT autonome).
- ❌ PAS de notification émise (Quarkus event, Notification entity, fan-out async). Délégué à SCRUM-99 (S7+).
- ❌ PAS de `@ManyToOne(LAZY) Event parent` sur l'entité — `Long parentEventId` brut (cf. décision 4).
- ❌ PAS d'extraction préventive de helpers d'erreur dans une utility class partagée — duplication acceptée (cf. décision 26).
- ❌ PAS de TODO commenté dans le code livré.
- ❌ PAS de `--no-verify`, pas de `@Disabled`, pas de skip de check CI sous prétexte de fix « ultérieur ».
- ❌ PAS de force-push sur `feature/s7-recurrence` pendant la review (utiliser des commits additifs).
- ❌ PAS de merge de la PR par l'agent — Elie s'en charge.

---

## Conventions à respecter

- camelCase partout en Java, JSON, OpenAPI. Hibernate convertit en snake_case côté DB.
- Pas de préfixe `is` sur les booléens d'**entités JPA** (n/a — aucun nouveau booléen sur `Event` pour SCRUM-147).
- Constructor injection ou `@Inject` field-style (pattern existant) sur `EventResource` — homogène avec le reste du projet.
- `@Transactional` sur toutes les **mutations** Service (`createRecurring`, et tout helper `persist*` qu'elle appelle hérite de la transaction). Lectures non-transactionnelles (`getOccurrences` reste non-transactional, comme le reste du projet — sauf cas particulier `EventService.getById` qui est `@Transactional` historique).
- `@PermitAll` sur `GET /events/{id}/occurrences` ; pas de rate limit dédié.
- `@PathParam Long id` pour `/events/{id}/occurrences`.
- Pagination `@DefaultValue("0") @Min(0) page`, `@DefaultValue("52") @Positive @Max(52) size` (la limite logique étant 52, alignée sur la limite de récurrence — cf. décision 14).
- Codes d'erreur custom dans le champ `error` de l'envelope `ApiErrorResponse` : `recurrence_unbounded`, `recurrence_end_before_start`, `recurrence_too_many`. Codes 4xx/5xx standards pour les autres.
- Couverture JaCoCo ≥ 80 % sur les lignes nouvelles (cible **> 90 %** sur les fichiers SCRUM-147), duplication < 3 %, ratings A.
- Doc mise à jour dans le **même commit** que le code correspondant (ou commit `docs(scrum-147):` dédié).
- Commits atomiques `feat(scrum-147): …`, `test(scrum-147): …`, `docs(scrum-147): …`, `fix(scrum-147): …`.
- Titre PR EXACT : `feat(scrum-147): add event recurrence with occurrence generation`.

---

## Livrable FINAL attendu

### Titre PR EXACT

```
feat(scrum-147): add event recurrence with occurrence generation
```

### Description PR (à coller dans le textarea — respecte strictement [`.github/pull_request_template.md`](.github/pull_request_template.md))

```markdown
## Résumé

SCRUM-147 livre la brique récurrence d'événement (US-27, épic SCRUM-14) :
2 colonnes ajoutées à `events` (`parent_event_id` + `recurrence_rule`),
nouvel enum `RecurrenceFrequency` (WEEKLY/BIWEEKLY/MONTHLY), nouveau
`RecurrenceRequest` exposé sur `POST /api/events`, nouvel endpoint
`GET /api/events/{id}/occurrences`, helper pure-Java `RecurrenceGenerator`.
La création récurrente matérialise atomiquement un parent + jusqu'à 51
occurrences (cap 52 rows total) en une seule transaction. Débloque
SCRUM-XXX-front-recurrence (S8+).

## Changements

### OpenAPI
- 2 champs ajoutés au schéma `Event` : `parentEventId` (int64, nullable, readOnly), `recurrenceRule` (string, maxLength 500, nullable, readOnly).
- 1 schéma neuf `RecurrenceRequest` (`frequency` enum, `endDate` date nullable, `maxOccurrences` 1..52 nullable).
- 1 enum neuf `RecurrenceFrequency` (`WEEKLY`, `BIWEEKLY`, `MONTHLY`).
- 1 champ ajouté à `CreateEventRequest` : `recurrence` ($ref RecurrenceRequest, nullable).
- 1 path neuf `GET /events/{id}/occurrences` (@PermitAll, codes 200/400/404).
- Codes d'erreur enrichis sur `POST /events` : 400 `recurrence_unbounded`, 400 `recurrence_end_before_start`, 422 `recurrence_too_many`.

### Migration
- `V17__add_event_recurrence.sql` (renommée en V18 si bascule — voir commit message) : `ALTER TABLE events ADD COLUMN parent_event_id BIGINT, ADD COLUMN recurrence_rule VARCHAR(500); ADD CONSTRAINT fk_events_parent FOREIGN KEY (parent_event_id) REFERENCES events(id) ON DELETE SET NULL; CREATE INDEX idx_event_parent ON events(parent_event_id);`. **ON DELETE SET NULL** : un DELETE physique du parent (après cancel) préserve les occurrences orphelines avec `parent_event_id = NULL` — leurs inscriptions/favoris/views/comptages restent intacts.

### Enum
- `RecurrenceFrequency` (`WEEKLY` / `BIWEEKLY` / `MONTHLY`) — pas DAILY ni YEARLY en S7.

### Entité
- `Event` étendue de 2 champs publics : `parentEventId: Long` (`@Column(name = "parent_event_id")`, pas `@ManyToOne` — pointeur Long brut, cohérent avec Favorite/Attendance/Follow), `recurrenceRule: String` (`@Column(name = "recurrence_rule", length = 500)`). Nouveau `@Index` `idx_event_parent` ajouté à `@Table(indexes = {...})`.

### DTOs
- `RecurrenceRequest` (record) : `frequency @NotNull`, `endDate` LocalDate nullable, `maxOccurrences` Integer nullable `@Min(1) @Max(52)`. Validation business cross-champs (`recurrence_unbounded`, `recurrence_end_before_start`) côté `EventService`.
- `CreateEventRequest` étendu d'un champ `@Valid recurrence: RecurrenceRequest` (nullable).
- `EventDTO` (record) étendu de 2 champs : `parentEventId`, `recurrenceRule`. Factory `from(Event, ...)` propage les 2 champs.

### Generator
- `RecurrenceGenerator` (utility class statique, fonction pure) : `generate(parentStart, parentEnd, frequency, untilDate, maxOccurrences) -> List<DateRange>`. Cap hard 52 (parent inclus). Espacement WEEKLY=7d / BIWEEKLY=14d / MONTHLY=Period.ofMonths(1) (gère 31→28 février naturellement). Levée `IllegalArgumentException` si `untilDate == null && maxOccurrences == null`. Testable hors Quarkus (pur JUnit).

### Service
- `EventService.create(...)` enrichi d'un branchement : `if (request.recurrence != null) return createRecurring(...);`. Logique standalone strictement inchangée.
- `EventService.createRecurring(...)` (`@Transactional`) : valide cross-champs (`recurrence_unbounded`, `recurrence_end_before_start`), crée le parent (helper `persistParent`), calcule `recurrenceRule` (helper `buildRecurrenceRule`), génère les occurrences via `RecurrenceGenerator`, persiste chacune (helper `persistOccurrence`). Atomicité all-or-nothing — si l'INSERT de la N-ème occurrence échoue, tout rollback.
- `EventService.getOccurrences(parentId, auth0Id, isAdmin, page, size)` : délègue à `getById(parentId, ...)` en première ligne pour la garde anti-oracle ISSUE-92, puis `Event.find("parentEventId = ?1 order by startDate asc, id asc", parentId)`.
- Helper d'erreur `badRequestRecurrence(error, message)` (locale, package-private) — pattern aligné sur `EventService.conflict(message)`.

### Resource
- `EventResource` étendu d'un seul handler `@GET @Path("/{id}/occurrences") @PermitAll`. Pas de nouvelle classe (un seul `@Path` racine `/events`).
- `POST /events` reste sous `@PerUserRateLimit(name = "events.create", max = 10)` — la création récurrente partage le bucket existant.

### Tests
- `RecurrenceGeneratorTest` (pur JUnit) : 12+ sentinels (espacement WEEKLY/BIWEEKLY/MONTHLY, gestion 31→fév, untilDate vs maxOccurrences, cap MAX_TOTAL_OCCURRENCES).
- `EventTest` enrichi : 2 sentinels d'assignabilité (`parentEventId`, `recurrenceRule`).
- `EventDTOTest` enrichi : 3 sentinels factory (parent / occurrence / standalone).
- `EventServiceCoverageTest` enrichi : 15+ sentinels DB-backed (création récurrente atomique, comptage, anti-oracle, cancel non-cascadé, DELETE parent → occurrences orphelines `parent_event_id = NULL`).
- `EventResourceTest` enrichi : 6+ sentinels Resource (POST recurring 201, validation Bean 400, GET occurrences 200/400/404).
- `EventServiceMock` enrichi : overrides `createRecurring` + `getOccurrences` + flags + `reset()`.
- `ShareServiceCoverageProfile` **non modifié** (`EventServiceMock` y figure déjà).

### Documentation
- `backend/docs/data-model.md` — section `### Event` enrichie + sous-section `#### Récurrence (SCRUM-147)` (sémantique parent/enfant, format RRULE, cascade FK, héritage status, non-propagation PUT/PATCH cancel, cap 52).
- `backend/docs/api-contract.md` — 1 ligne ajoutée + section `### Event Recurrence (SCRUM-147)`.
- `backend/docs/sprint-context.md` — bloc `## Sprint 7 — Récurrence sur Event + génération d'occurrences (SCRUM-147)`.
- `openapi/openapi.yaml` — schémas + path (cf. section OpenAPI ci-dessus).

## Tests

≥ 25 sentinels couvrent les chemins critiques :
- `RecurrenceGenerator` (pur JUnit) : WEEKLY/BIWEEKLY/MONTHLY, fév court, untilDate vs maxOccurrences, cap 52, levée si bornes absentes.
- POST recurrence : 201 valide WEEKLY 4 occurrences, 400 Bean Validation (maxOccurrences=53, frequency=null), 400 business (`recurrence_unbounded`, `recurrence_end_before_start`), atomicité rollback.
- GET /events/{id}/occurrences : 200 paginé tri ASC, 200 standalone vide, 404 anti-oracle DRAFT non-créateur, 200 DRAFT créateur, 404 BANNED, 200 anonyme PUBLISHED, 400 size>52.
- Non-propagation : PUT parent.title ne propage pas, PATCH cancel parent ne cascade pas, DELETE parent → enfants `parent_event_id = NULL` (FK ON DELETE SET NULL).

Lancer : `./mvnw verify` (via devcontainer Quarkus DevServices PostgreSQL).
Couverture JaCoCo > 90 % sur les fichiers SCRUM-147.

## Test plan

- [ ] `./mvnw verify` vert (devcontainer).
- [ ] `git diff --stat frontend/` strictement vide.
- [ ] `git diff --stat openapi/` non-vide.
- [ ] `git diff --stat backend/src/main/resources/db/migration/` contient `V17__add_event_recurrence.sql` (ou V18 documenté).
- [ ] `pom.xml` inchangé.
- [ ] Smoke manuel sur `quarkus:dev` (DevServices) :
  - [ ] `POST /api/events` avec body `{ ..., recurrence: { frequency: "WEEKLY", maxOccurrences: 4 } }` → 201, body `EventDTO` avec `recurrenceRule="FREQ=WEEKLY;COUNT=4"`, `parentEventId=null`.
  - [ ] `GET /api/events/{parentId}/occurrences` → 200 + 3 occurrences triées ASC, chacune avec `parentEventId=<parentId>` et `recurrenceRule=null`.
  - [ ] `GET /api/calendar/{token}.ics` (favori sur le parent) → flux ICS contient 4 VEVENTs (1 parent + 3 enfants), pas de RRULE compact.
  - [ ] `PATCH /api/events/{parentId}/cancel` → parent.status=CANCELLED, occurrences inchangées.
  - [ ] `PUT /api/events/{parentId}` (titre changé) → parent.title=nouveau, occurrences[i].title=ancien.
  - [ ] `DELETE /api/events/{parentId}` (après cancel) → 204, occurrences en DB avec `parent_event_id=NULL`.
  - [ ] `POST /api/events` avec `recurrence: { frequency: "WEEKLY", maxOccurrences: 53 }` → 400 Bean Validation.
  - [ ] `POST /api/events` avec `recurrence: { frequency: "WEEKLY" }` (sans endDate ni maxOccurrences) → 400 `recurrence_unbounded`.
  - [ ] `GET /api/events/{standaloneId}/occurrences` → 200 + `[]` (pas de 404).
- [ ] `gh pr checks` — toutes vertes.
- [ ] SonarCloud Quality Gate vert.
- [ ] Review Copilot demandée et **chaque commentaire traité** (apply OU justifié).

## Documentation

- [x] `backend/docs/data-model.md` — section Event enrichie + sous-section `#### Récurrence (SCRUM-147)`.
- [x] `backend/docs/api-contract.md` — 1 endpoint + section `### Event Recurrence (SCRUM-147)`.
- [x] `backend/docs/sprint-context.md` — entrée SCRUM-147 dans le sprint S7.
- [x] `openapi/openapi.yaml` — 2 champs Event + 2 schémas neufs + 1 path neuf.

<!-- Optionnel : Why / Motivation -->
## Why / Motivation

US-27 est l'une des features structurantes de l'épic « Édition d'événements »
(SCRUM-14). Sans la récurrence, un organisateur d'un cours hebdomadaire doit
créer 26 fois la même session sur un semestre — friction inacceptable. La
matérialisation par rows (1 row = 1 occurrence) préserve toutes les propriétés
existantes des événements (inscriptions, favoris, vues, co-organisateurs,
commentaires) sans branchement spécial dans les services aval (Attendance,
Favorite, Stats, Calendar/ICS). Le contrat figé ici débloque
SCRUM-XXX-front-recurrence (S8+).

<!-- Optionnel : Dépendances / ordre de merge -->
## Dépendances / ordre de merge

Aucune dépendance amont au sens strict. Ordre de merge attendu sur main :
V14 follows (SCRUM-138 PR #154) → V15 comments (SCRUM-139 PR #156) → V16
(PR concurrente mergée post-prompt) → **V17 recurrence (cette PR)**. Si V17
est également pris au moment du checkout (nouvelle PR concurrente),
basculer en V18 dans un commit `fix(scrum-147): rebase V17 → V18`.
**Cette PR débloque** : SCRUM-XXX-front-recurrence (S8+, formulaire
RecurrenceForm.tsx + listing des occurrences dans EventDetailPage).

<!-- Optionnel : Décisions techniques tranchées -->
## Décisions techniques tranchées

Toutes les décisions sont consignées dans [`specs_archives/specs_claude/specs_scrum-147.md`](specs_archives/specs_claude/specs_scrum-147.md). Highlights :
- Pas de nouvelle entité ni de nouvelle table — uniquement ALTER TABLE events ADD COLUMN (matérialisation par rows).
- `Long parentEventId` brut (pas `@ManyToOne`) — cohérent avec Favorite/Attendance/Follow.
- FK `fk_events_parent ON DELETE SET NULL` — DELETE physique du parent préserve les occurrences orphelines (cohérent avec fix post-Copilot SCRUM-139 sur `fk_comments_parent`).
- 3 fréquences strictes (WEEKLY/BIWEEKLY/MONTHLY) ; PAS de DAILY ni YEARLY en S7.
- Cap hard 52 occurrences (parent inclus).
- Atomicité all-or-nothing via `@Transactional` sur `createRecurring`.
- Statut occurrences hérité du parent (DRAFT par défaut).
- PAS de propagation PUT, PAS de cascade PATCH cancel, PAS d'héritage des co-organisateurs aux occurrences.
- ICS feed inchangé (chaque occurrence reste son VEVENT autonome).
- Notifications hors scope — déléguées à SCRUM-99 (S7+).
- `RecurrenceGenerator` utility class fonction pure, testable hors Quarkus.

<!-- Optionnel : Notes pour le reviewer -->
## Notes pour le reviewer

- L'anti-oracle ISSUE-92 est délégué à `EventService.getById(...)` en première
  ligne de `getOccurrences(...)` — DRAFT/CANCELLED/BANNED non-créateur → 404.
- La FK `fk_events_parent` est `ON DELETE SET NULL` — correctif appliqué dès la
  rédaction de la migration (pas en post-Copilot review comme pour SCRUM-139),
  cf. spec décision 5.
- `EventServiceMock` est déjà dans la liste d'exclusion ARC de
  `ShareServiceCoverageProfile` — aucune modification nécessaire (contrairement
  à SCRUM-139 qui avait dû ajouter `CommentServiceMock`).
- Le `RecurrenceGeneratorTest` est volontairement en pur JUnit (pas
  `@QuarkusTest`) — exécution en ms, isolation totale de la logique calendrier.
- Le branchement `if (request.recurrence != null)` au début de
  `EventService.create` préserve strictement la rétrocompatibilité — les call-sites
  legacy (front existant, tests existants) ne voient aucun changement.
```

---

## Prompt de lancement d'implémentation

```
Tu vas implémenter la feature SCRUM-147 du projet UNIGE Events. La spec
d'implémentation complète et figée vit dans
`specs_archives/specs_claude/specs_scrum-147.md` — c'est la **source unique de
vérité**. Toute déviation par rapport à cette spec doit être justifiée auprès de
l'utilisateur AVANT exécution.

## Working directory et environnement

- Working directory : `/workspace` dans le devcontainer Linux Debian (host : MAC via SSH).
- Java 21 absent du host → tout `./mvnw verify` / `mvn` / `gh` / `git` lourd passe par :
  `docker exec -w /workspace unige-events-app-1 bash -c "cd /workspace/backend && ./mvnw verify"`.

## Contexte projet à relire AVANT d'écrire la moindre ligne

1. `AGENTS.md`, `backend/AGENTS.md`, `backend/CLAUDE.md` — règles d'or projet
   (openapi-first, Flyway immutable, camelCase, pas de préfixe `is` sur entités,
   conventions PR).
2. `backend/docs/data-model.md`, `backend/docs/api-contract.md`,
   `backend/docs/sprint-context.md`, `backend/docs/architecture.md`,
   `openapi/openapi.yaml`.
3. `specs_archives/specs_claude/specs_scrum-147.md` — la spec, intégralement.
4. `specs_archives/specs_claude/specs_scrum-139.md` — référence de pattern
   (auto-référence parent/enfant + ON DELETE SET NULL post-Copilot, structure de
   spec, workflow PR).
5. `specs_archives/specs_claude/specs_scrum-138.md` — référence pattern split
   en 2 Resources (informatif — pas applicable directement à SCRUM-147 qui
   ajoute un seul handler à `EventResource` existant).

## Branche cible

`feature/s7-recurrence` créée depuis `origin/main` avec `--no-track` (NON NÉGOCIABLE) :

```
git fetch origin && git checkout -b feature/s7-recurrence origin/main --no-track
```

## Ordre d'exécution strict (Étapes 0 → 9)

0. **OpenAPI EN PREMIER** — `openapi/openapi.yaml` :
   - 2 champs ajoutés au schéma `Event` (`parentEventId`, `recurrenceRule`),
   - 1 schéma neuf `RecurrenceRequest`,
   - 1 enum neuf `RecurrenceFrequency`,
   - 1 champ ajouté à `CreateEventRequest` (`recurrence`),
   - 1 path neuf `GET /events/{id}/occurrences`.
   Vérifier la validité YAML.
1. **Migration Flyway** — pré-check `ls backend/src/main/resources/db/migration | sort` ;
   créer `V17__add_event_recurrence.sql` (basculer en V18 si une nouvelle PR
   concurrente prend V17 entre-temps ; adapter toutes les références et
   documenter le swap dans le commit + PR description).
2. **Enum** — `RecurrenceFrequency.java` (3 valeurs WEEKLY/BIWEEKLY/MONTHLY).
3. **Entité** — étendre `Event.java` de 2 champs (`parentEventId: Long`,
   `recurrenceRule: String @Column(length=500)`) + nouvel `@Index`
   `idx_event_parent` dans `@Table`.
4. **DTOs** — `RecurrenceRequest.java` (record + Bean Validation) +
   `CreateEventRequest` enrichi (`@Valid recurrence`) + `EventDTO` enrichi de
   2 champs propagés via factory.
5. **Helper** — `RecurrenceGenerator.java` (utility class statique, fonction
   pure, record `DateRange` nested, cap MAX_TOTAL_OCCURRENCES=52, gère
   WEEKLY/BIWEEKLY/MONTHLY via Period).
6. **Service** — `EventService.create(...)` étendu d'un branchement
   `if (request.recurrence != null) return createRecurring(...)` ;
   `EventService.createRecurring(...)` (`@Transactional`, all-or-nothing,
   helpers `persistParent`/`persistOccurrence`/`buildRecurrenceRule`/
   `badRequestRecurrence`) ; `EventService.getOccurrences(...)` (délègue à
   `getById` pour anti-oracle, puis `Event.find("parentEventId = ?1 order by
   startDate asc, id asc")`).
7. **Resource** — ajout d'un seul handler `@GET @Path("/{id}/occurrences")
   @PermitAll` dans `EventResource` (pas de nouvelle classe).
8. **Tests** — `RecurrenceGeneratorTest` (pur JUnit, 12+ sentinels), `EventTest`
   enrichi, `EventDTOTest` enrichi, `EventServiceMock` enrichi (overrides),
   `EventServiceCoverageTest` enrichi (15+ sentinels DB-backed),
   `EventResourceTest` enrichi (6+ sentinels Resource). `ShareServiceCoverageProfile`
   **inchangé** (EventServiceMock y est déjà). ≥ 25 sentinels listés en spec
   section Étape 8, tous verts nommément. Couverture JaCoCo > 90 %.
9. **Documentation** — `backend/docs/data-model.md` (section Event enrichie +
   sous-section `#### Récurrence (SCRUM-147)`), `backend/docs/api-contract.md`
   (1 ligne dans la table + section `### Event Recurrence (SCRUM-147)`),
   `backend/docs/sprint-context.md` (entrée S7 SCRUM-147).
10. **Vérification finale locale** — `./mvnw verify` vert + JaCoCo > 90 % +
    checks invariants (`git diff --stat frontend/` vide, `pom.xml` inchangé,
    V17 — ou V18 si bascule documentée — présente).

À chaque étape, commit + push autorisés (et recommandés). Format commits :
`feat(scrum-147): …`, `test(scrum-147): …`, `docs(scrum-147): …`,
`fix(scrum-147): …`.

## Contraintes

- **PAS de modification frontend** (`git diff --stat frontend/` strictement vide).
- **OpenAPI en PREMIER** (avant toute ligne de Java).
- **Hors scope** : skip d'occurrence individuelle (RFC 5545 EXDATE), modification
  globale propagée aux occurrences (S8+), cancel cascadé (S8+), héritage auto
  des co-organisateurs (S8+), notifications NEW_OCCURRENCE (SCRUM-99 S7+),
  RRULE compact dans ICS (S9+), front (SCRUM-XXX-front-recurrence S8+).
- **Pas de cascade `ON DELETE CASCADE`** sur `fk_events_parent` —
  `ON DELETE SET NULL` strict (cf. décision 5 ; même problématique que SCRUM-139
  fix post-Copilot sur `fk_comments_parent`).
- **Pas de `@ManyToOne` sur parentEventId** — `Long` brut (cf. décision 4).
- **Cap hard 52 occurrences** (parent inclus), `@Max(52)` Bean Validation +
  troncature silencieuse côté générateur.
- **Statut hérité parent → enfants** à la création.
- **PAS de propagation PUT, PAS de cascade PATCH cancel** sur le parent.
- Note : `openapi/openapi.yaml` contient un path `/events/{id}/view` dupliqué
  (lignes ~2833 et ~2911) — c'est un artefact pré-existant **hors scope**
  SCRUM-147, ne pas le toucher.

## Workflow PR / Copilot / CI

1. Ouvrir la PR avec **titre EXACT** :
   `feat(scrum-147): add event recurrence with occurrence generation`
   (validé par `.github/workflows/pr-title-check.yml`).
2. Body PR : copier-coller le bloc fourni dans la spec section « Livrable FINAL
   attendu » — respecte strictement `.github/pull_request_template.md`. Le body
   transite par `cat … | docker exec -i unige-events-app-1 bash -c "cat >
   /tmp/pr-body-scrum-147.md"` puis `gh pr create --body-file
   /tmp/pr-body-scrum-147.md` depuis le devcontainer.
3. Demander la review à Copilot :
   `gh pr edit <PR_NUM> --add-reviewer copilot-pull-request-reviewer`. Fallback
   si app non collaborator : `gh pr comment <PR_NUM> --body "@copilot review please"`.
4. Pour CHAQUE commentaire de Copilot :
   - Récupérer via `gh api repos/unige-pinfo6-2026/unige-events/pulls/<PR_NUM>/comments --paginate`.
   - Juger pertinence (alignement avec les conventions projet et les décisions
     tranchées de la spec).
   - Si pertinent → corriger dans un commit `fix(scrum-147): …` + push +
     répondre au commentaire avec un lien vers le SHA via
     `gh api -X POST repos/.../pulls/<PR_NUM>/comments/{id}/replies`.
   - Si non-pertinent → répondre poliment en justifiant pourquoi la remarque
     n'est pas appliquée (citer la décision de la spec qui tranche).
   - **Ne jamais ignorer silencieusement un commentaire.**
5. Surveiller la CI : `gh pr checks <PR_NUM> --watch`. Si une check échoue, lire
   les logs (`gh run view <RUN_ID> --log-failed`), corriger la cause **racine**
   (PAS de `--no-verify`, PAS de skip, PAS de `@Disabled`), commit + push,
   surveiller à nouveau jusqu'à ce que **toutes** les checks soient vertes ET
   que le Quality Gate Sonar soit vert.
6. **Ne PAS merger** la PR — Elie s'en charge après validation finale.

## Critères de done (rappel)

- [ ] Branche `feature/s7-recurrence` créée depuis `origin/main` avec `--no-track`.
- [ ] OpenAPI modifié EN PREMIER (Event +2 champs, RecurrenceRequest, RecurrenceFrequency, CreateEventRequest +1 champ, path /events/{id}/occurrences).
- [ ] V17 (ou V18 si bascule) présente, V1..V16 intactes.
- [ ] RecurrenceFrequency, Event +2 champs, RecurrenceRequest, EventDTO enrichi, RecurrenceGenerator, EventService.createRecurring/getOccurrences, EventResource +1 handler créés/modifiés.
- [ ] ShareServiceCoverageProfile **inchangé**.
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
