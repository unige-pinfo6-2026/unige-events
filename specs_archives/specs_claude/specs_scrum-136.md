# Specs SCRUM-136 — Entité `EventCoOrganizer` + endpoints d'invitation co-organisateurs

> **Branche :** `feature/s6-co-organizers` (nom historique du backlog ; reste utilisé pour la PR — cf. décision 1)
> **Base :** `origin/main`
> **Sprint :** S7 (28 avr.–8 mai 2026) — Feature 6c / Tâche 1 sur 2
> **Ticket Jira :** [SCRUM-136](https://pinfo-groupe6.atlassian.net/browse/SCRUM-136) (5 SP, assigné Antoine)
> **Story Points :** 5
> **Épic :** SCRUM-14 · **Story :** [SCRUM-118](https://pinfo-groupe6.atlassian.net/browse/SCRUM-118) (US-29)
> **Frontend lié :** [SCRUM-137](https://pinfo-groupe6.atlassian.net/browse/SCRUM-137) — `feature/s6-co-organizers-front` (dépend de cette PR)
> **Règle d'or openapi-first :** **APPLICABLE — 6 nouveaux endpoints + 2 schémas + 1 enum à ajouter.** Modifier `openapi/openapi.yaml` AVANT toute ligne de code Java. Voir [`backend/AGENTS.md`](backend/AGENTS.md#L62-L65).

---

## Contexte

### Le besoin produit (US-29)

> *« En tant qu'organisateur, je veux ajouter des co-organisateurs à mon événement, afin de partager la gestion avec mes collègues. »*

L'épic SCRUM-14 (Création & gestion d'événements) repose aujourd'hui sur un seul porteur par événement : la colonne `events.creator_id` est mono-valuée et toutes les méthodes d'écriture de [`EventService`](backend/src/main/java/ch/unige/events/service/EventService.java) vérifient le créateur exclusif via le helper privé [`isCreator(Event, String)`](backend/src/main/java/ch/unige/events/service/EventService.java#L351-L355). Conséquence : un événement co-piloté par deux étudiants oblige l'un des deux à céder son compte ou à attendre l'autre pour publier, modifier le banner, charger une nouvelle bannière, voir les stats ou la liste des participants. Le besoin est récurrent côté associations universitaires (SciencesXChange, GenevaSings, etc.) et bloque la migration de plusieurs événements vers la plateforme.

### Ce qui manque aujourd'hui

| Pièce manquante | Conséquence |
|---|---|
| Aucune table de jointure « event ↔ co-organisateur » | Impossible de modéliser un cas N:M utilisateur/événement à part Attendance/Favorite |
| Aucun endpoint d'invitation / acceptation | Pas de boucle social qui propose puis confirme la collaboration |
| `EventService.update/cancel/publish/uploadImage` strict-creator | Le bénéfice fonctionnel attendu (déléguer la gestion) n'est pas accessible sans la cascade |
| `AttendanceService.getAttendees` / `EventStatsService.getStats` strict-creator | Un co-organisateur ne pourrait ni voir les inscrits, ni consulter les stats |
| Aucune surface API pour qu'un utilisateur découvre ses invitations en attente | Le frontend (SCRUM-137 Navbar/ProfilePage) ne peut afficher d'indicateur PENDING |

SCRUM-136 livre le **socle backend complet** : entité, enum, DTO, Service, Resource, cascade `EventService`, OpenAPI, doc, tests. SCRUM-137 livrera l'UI (recherche utilisateur, invitation depuis `EventForm`, badge PENDING, boutons Accepter/Décliner).

### Pourquoi maintenant

- Sprint 7 (28 avr.–8 mai 2026) — sprint courant, capacité disponible.
- Aucune dépendance technique : ni `Notification` (SCRUM-99), ni `Report` (SCRUM-94), ni `Featured` (SCRUM-95) ne sont prérequis. Confirmé dans le backlog : *« Dépendances : aucune »* ([backlog ligne 647](backend/docs/backlog_s5_s10.md#L647)).
- SCRUM-164 (CHECK constraints) vient d'être livrée sur `feature/s7-schema-fixup-checks` (`0a229cc..62eb07c`) — la table `event_co_organizers` créée ex nihilo par Hibernate aura sa propre `event_co_organizers_status_check` posée à la création initiale (cf. décision 14 ci-dessous), donc aucune intervention de `SchemaFixup` n'est nécessaire pour cette PR. Mais toute future addition au `CoOrganizerStatus` exigera une mise à jour de [`SchemaFixup`](backend/src/main/java/ch/unige/events/config/SchemaFixup.java) — à mentionner dans la doc.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### 1. Branche `feature/s6-co-organizers` — pas `feature/s7-...`

**Décision.** La branche s'appelle `feature/s6-co-organizers`, conformément au nom suggéré dans [`backlog_s5_s10.md` ligne 646](backend/docs/backlog_s5_s10.md#L646). Le préfixe `s6` est un artefact historique (le ticket a été créé en S6, avant son report en S7) — l'équipe a choisi de ne pas renommer pour respecter la traçabilité du backlog.

**Justification.** L'autre option (`feature/s7-co-organizers`) serait cohérente avec la branche SCRUM-164 (`feature/s7-schema-fixup-checks`) mais désaligne des autres tickets S6 du backlog (`feature/s6-co-organizers-front` SCRUM-137, `feature/s6-follow` SCRUM-138, `feature/s6-comments` SCRUM-139). Garder la cohérence intra-Story S6 prime sur la cohérence intra-sprint.

### 2. Périmètre cascade — quelles méthodes `EventService` migrent

**Décision.** Création d'un nouveau helper privé `isCreatorOrAcceptedCoOrganizer(Event, String auth0Id)` à côté de l'existant [`isCreator`](backend/src/main/java/ch/unige/events/service/EventService.java#L351-L355) (qu'on **ne touche pas** — sa sémantique reste « créateur strict »). Cascade :

| Méthode | Call-site avant | Décision | Justification |
|---|---|---|---|
| `EventService.update` | [`EventService.java:154`](backend/src/main/java/ch/unige/events/service/EventService.java#L154) | **MIGRE** vers `isCreatorOrAcceptedCoOrganizer` | Cœur du « partager la gestion » de US-29 |
| `EventService.cancel` | [`EventService.java:205`](backend/src/main/java/ch/unige/events/service/EventService.java#L205) | **MIGRE** | Pendant naturel d'`update`. Annulation = lifecycle, pas destruction |
| `EventService.restore` | [`EventService.java:223`](backend/src/main/java/ch/unige/events/service/EventService.java#L223) | **MIGRE** | Idem cancel — symétrie nécessaire |
| `EventService.publish` | [`EventService.java:247`](backend/src/main/java/ch/unige/events/service/EventService.java#L247) | **MIGRE** | Explicitement listé par le ticket Jira |
| `EventService.uploadImage` | [`EventService.java:297`](backend/src/main/java/ch/unige/events/service/EventService.java#L297) | **MIGRE** | Bannière fait partie du contenu éditable |
| `EventService.getById` (visibilité DRAFT/CANCELLED) | [`EventService.java:141`](backend/src/main/java/ch/unige/events/service/EventService.java#L141) | **MIGRE** | Un co-org ACCEPTED doit pouvoir lire l'event en édition |
| `EventService.delete` (hard-delete d'un CANCELLED) | [`EventService.java:189`](backend/src/main/java/ch/unige/events/service/EventService.java#L189) | **GARDE** strict-creator | Action irréversible, hors scope « partage de gestion » |
| `AttendanceService.getAttendees` | [`AttendanceService.java:143`](backend/src/main/java/ch/unige/events/service/AttendanceService.java#L143) | **MIGRE** | Un co-org doit pouvoir consulter les inscrits |
| `EventStatsService.getStats` | [`EventStatsService.java:26`](backend/src/main/java/ch/unige/events/service/EventStatsService.java#L26) | **MIGRE** | Un co-org doit pouvoir consulter les stats |

**Justification.** Le ticket Jira écrit *« modifier `update()`, `delete()`, `publish()` »*. L'extension à `cancel/restore/uploadImage/getById/getAttendees/getStats` rend l'expérience cohérente : un co-org qui peut publier mais pas voir les inscrits ou les stats serait amputé. La restriction sur `delete()` (vs ce qu'écrit le ticket) est un raffinement explicite — la suppression physique est irréversible et la sémantique « partager la gestion » s'arrête à la modification réversible. Cette divergence par rapport au libellé du ticket est listée dans la PR (section « Décisions techniques tranchées »).

### 3. Helper dédié — pas de mutation d'`isCreator`

**Décision.** Le helper `isCreator` reste inchangé. Un nouvel helper privé est ajouté dans `EventService` :

```java
private boolean isCreatorOrAcceptedCoOrganizer(Event event, String auth0Id) {
    if (isCreator(event, auth0Id)) {
        return true;
    }
    return User.findByAuth0Id(auth0Id)
            .map(user -> EventCoOrganizer.isAcceptedFor(event.id, user.id))
            .orElse(false);
}
```

**Justification.** Trois raisons :
1. **Lisibilité du diff** : un appelant qui lit `if (!isCreator(event, auth0Id))` aujourd'hui sait précisément ce que la garde signifie. Ré-utiliser le même nom mais en élargissant la sémantique casserait toutes les mental-maps des reviewers.
2. **Cohérence avec `delete()`** : `delete()` reste strict-creator → `isCreator` reste utilisé tel quel. Pas de stub artificiel.
3. **Pattern existant** : la base de code introduit déjà des helpers ad-hoc par cas (`countAttending`, `countWaitlisted`, `computeAvailableSpots`, `normalizeTags` dans le même fichier).

Le helper résout l'auth0Id → User une seule fois et délègue la lookup à un helper statique sur l'entité (cf. décision 4).

### 4. Helper statique `EventCoOrganizer.isAcceptedFor(Long eventId, UUID userId)` — pattern Panache

**Décision.** L'entité `EventCoOrganizer` expose un helper statique qui répond la question *« cet utilisateur est-il un co-organisateur ACCEPTED de cet event ? »* en une seule requête JPQL existence-only :

```java
public static boolean isAcceptedFor(Long eventId, UUID userId) {
    return count("eventId = ?1 and userId = ?2 and status = ?3",
            eventId, userId, CoOrganizerStatus.ACCEPTED) > 0;
}
```

**Justification.** Strictement aligné sur les helpers existants : [`User.findByAuth0Id`](backend/src/main/java/ch/unige/events/entity/User.java#L53-L55), [`Favorite.findByUserAndEvent`](backend/src/main/java/ch/unige/events/entity/Favorite.java#L35-L37), [`Attendance.findByEvent`](backend/src/main/java/ch/unige/events/entity/Attendance.java#L41-L45). Pas de Repository séparé (banni par [AGENTS.md](backend/AGENTS.md#L42)) ; pas de service de lookup pour une simple question d'existence ; `count` est plus efficace qu'un `findFirst` qu'on jette ensuite.

### 5. Sémantique du DECLINE — supprimer la row, autoriser la ré-invitation

**Décision.** `decline(Long eventId, String userAuth0Id)` **supprime physiquement** la row `EventCoOrganizer` au lieu de poser `status = DECLINED`. Conséquence : le créateur peut ré-inviter le même utilisateur après un refus, sans 409.

**Justification.** Trois alternatives ont été pesées :

| Option | Conséquence | Verdict |
|---|---|---|
| (a) `status = DECLINED` figé | La contrainte unique `(eventId, userId)` bloque toute ré-invitation. Le créateur n'a plus aucun recours si le contexte change | ❌ trop rigide, casse l'UX |
| (b) `status = DECLINED` + UPSERT au `invite()` | Permet ré-invitation mais introduit un état mort `DECLINED` qui pollue les listings et les tests | ❌ complexité pour zéro valeur produit |
| (c) **DELETE de la row** | Ré-invitation = nouvelle invitation propre (status PENDING, nouveau `invitedAt`). Aucun état résiduel | ✅ retenu |

La valeur `DECLINED` reste néanmoins définie dans l'enum `CoOrganizerStatus` — elle est utilisée comme valeur de retour transitoire dans `CoOrganizerInvitationDTO` au moment où l'utilisateur appelle `PATCH /me/decline`, **avant** la suppression effective. Pas d'effet sur la DB ni sur les listings ultérieurs.

> **Trade-off explicite.** L'utilisateur qui a refusé une invitation n'a aucune trace dans la DB de l'avoir fait. S'il demande *« qui m'avait invité ? »*, on ne peut pas lui répondre (au-delà des logs applicatifs). Acceptable au stade S7 — à reconsidérer si une feature de notifications historiques (SCRUM-99) le justifie.

### 6. Auto-invitation interdite — 400 `cannot_invite_self`

**Décision.** `EventCoOrganizerService.invite(...)` rejette explicitement le cas où le `targetUserId` correspond à l'utilisateur de `inviterAuth0Id` :

```java
if (inviter.id.equals(targetUserId)) {
    throw new WebApplicationException(
        Response.status(Response.Status.BAD_REQUEST)
            .entity(new ApiErrorResponse(
                "cannot_invite_self",
                "The event creator cannot invite themselves as co-organizer."))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build());
}
```

**Justification.** Le créateur a déjà toutes les permissions sur son événement via `isCreator`. Un auto-invite créerait une row redondante qui ne change rien aux permissions, et risquerait de dérouter l'UI (le créateur s'auto-mentionnerait dans l'équipe organisatrice). Le code d'erreur dédié (vs un 409 générique) permet au frontend de masquer le bouton « Inviter » sur soi-même.

### 7. Visibilité de `GET /events/{id}/co-organizers` — authentifié, payload complet

**Décision.** L'endpoint est `@Authenticated` (anonyme → 401). Les utilisateurs authentifiés reçoivent **toutes** les rows (PENDING + ACCEPTED — DECLINED supprimées par décision 5).

**Justification.** Trois alternatives ont été pesées :

| Alternative | Verdict |
|---|---|
| (a) `@PermitAll`, anonyme voit ACCEPTED uniquement | ❌ Élargit la surface anti-oracle (cf. ISSUE-92, [`specs_issue-92-hide-draft-events.md`](specs_archives/specs_claude/specs_issue-92-hide-draft-events.md)) sans valeur produit immédiate — la SCRUM-137 frontend appelle l'endpoint depuis `EventDetailPage.tsx` côté authentifié |
| (b) Authentifié + filtrage par rôle (créateur/co-org/ADMIN voient PENDING, autres voient ACCEPTED uniquement) | ❌ Complexité de projection sans gain de privacy : la status PENDING n'est pas un secret, elle reflète juste un workflow interne |
| (c) **Authentifié, payload complet** | ✅ retenu |

Le payload complet est volontairement minimaliste (cf. décision 9 sur `CoOrganizerDTO`) — on ne fuite pas l'email ni la bio.

> **Note de cohérence.** [`GET /events/{id}/attendees`](backend/src/main/java/ch/unige/events/resource/AttendanceResource.java#L54-L63) est **strict-creator** (403 sinon). On choisit explicitement de ne pas répliquer ce gating pour les co-organisateurs : la liste des co-organisateurs est destinée à être exposée publiquement à terme (fil de l'event), tandis que la liste des participants est confidentielle (RGPD : qui assiste à quel événement). La différence est intentionnelle.

### 8. `GET /users/me/co-organizer-invitations` — par défaut PENDING, paginé, événement enrichi

**Décision.** L'endpoint retourne par défaut les invitations PENDING uniquement, paginées (`page`, `size`), enrichies d'un `EventDTO` complet. Filtrage optionnel `?status=` (ACCEPTED autorisé pour audit ; DECLINED ne renvoie jamais rien puisque les rows DECLINED sont supprimées).

```
GET /api/users/me/co-organizer-invitations?status=PENDING&page=0&size=20
→ 200 [
    {
      "id": 42,
      "event": { ...EventDTO complet... },
      "status": "PENDING",
      "invitedAt": "2026-04-29T10:30:00"
    }, ...
  ]
```

**Justification.**
- **PENDING par défaut** : c'est ce que la SCRUM-137 utilise pour le badge Navbar (« vous avez X invitations en attente »).
- **Pagination identique aux autres `/me/*`** : default page=0/size=20, max size=100, `@Min`/`@Positive`/`@Max` sur les query params (cohérence avec [`/me/favorites`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L264-L272)).
- **EventDTO enrichi** : sans cela, le frontend devrait re-fetch chaque event individuellement pour afficher titre/date/banner — un N+1 réseau gratuit. Le bulk count d'attendances est déjà résolu par `EventService.toEventDTOs(events)` qu'on réutilise.
- **Events CANCELLED inclus** : si un event annulé après l'invitation, l'invitation reste visible dans la liste (l'utilisateur invité a le droit de comprendre pourquoi son badge a clignoté). Le DTO portera `status: "CANCELLED"` côté event, et l'UI pourra dégrader.

### 9. Forme de `CoOrganizerDTO` — projection minimaliste user-public

**Décision.** Le DTO retourné par `GET /events/{id}/co-organizers` :

```java
public record CoOrganizerDTO(
    Long id,
    UUID userId,
    String displayName,
    String avatarUrl,
    CoOrganizerStatus status,
    LocalDateTime invitedAt
) {
    public static CoOrganizerDTO from(EventCoOrganizer entity, User user) { ... }
}
```

**Justification.** Champs choisis pour matcher exactement ce dont l'UI SCRUM-137 a besoin (avatar + nom + chip de statut). Champs **explicitement exclus** :
- `email` — privacy (cohérent avec [`UserPublicResponse.fromAnonymous`](backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java#L36-L47) qui ne projette jamais l'email).
- `faculty` / `studyLevel` / `bio` — non utilisés par la chip frontend ; à ré-introduire si SCRUM-137 le demande, mais pas spéculativement.
- `eventId` — implicite (l'appelant a fourni l'`{id}` dans le path).

### 10. Forme de `CoOrganizerInvitationDTO` — pour `/me/co-organizer-invitations`

**Décision.** DTO différent (et plus riche) que `CoOrganizerDTO` :

```java
public record CoOrganizerInvitationDTO(
    Long id,
    EventDTO event,
    CoOrganizerStatus status,
    LocalDateTime invitedAt
) {
    public static CoOrganizerInvitationDTO from(EventCoOrganizer entity, EventDTO event) { ... }
}
```

**Justification.** Le contexte est l'inverse : ici l'invité connaît son propre identité (JWT) mais a besoin de l'event ; alors que `CoOrganizerDTO` connaît l'event (path) mais a besoin du user. Deux DTO distincts évitent un méga-DTO avec des champs nullables selon le contexte d'appel.

### 11. Endpoints — PATCH pour les actions sur soi (cohérent avec `/publish`, `/cancel`)

**Décision.** L'API ressemble à :

| Méthode | Path | Auth | Code 2xx | Action |
|---|---|---|---|---|
| `POST` | `/events/{id}/co-organizers` | `@Authenticated` (créateur ou ADMIN) | `201 CoOrganizerDTO` | Inviter `{ "userId": "<uuid>" }` |
| `DELETE` | `/events/{id}/co-organizers/{userId}` | `@Authenticated` (créateur ou ADMIN) | `204` | Retirer un co-organisateur (peu importe son statut) |
| `GET` | `/events/{id}/co-organizers` | `@Authenticated` | `200 List<CoOrganizerDTO>` | Lister les co-organisateurs (PENDING + ACCEPTED) |
| `PATCH` | `/events/{id}/co-organizers/me/accept` | `@Authenticated` | `200 CoOrganizerDTO` | L'invité accepte sa propre invitation |
| `PATCH` | `/events/{id}/co-organizers/me/decline` | `@Authenticated` | `204` | L'invité décline → suppression de la row |
| `GET` | `/users/me/co-organizer-invitations` | `@Authenticated` | `200 List<CoOrganizerInvitationDTO>` | Mes invitations (PENDING par défaut) |

**Justification.**
- `PATCH /me/accept` et `PATCH /me/decline` : cohérent avec [`PATCH /events/{id}/publish`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L130-L138), [`PATCH /events/{id}/cancel`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L108-L115), [`PATCH /events/{id}/restore`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L117-L124) — toutes des transitions de statut idempotentes.
- `/me` au lieu de `/{userId}` : l'identité vient du JWT (cf. décision 9 de [`specs_scrum-133.md`](specs_archives/specs_claude/specs_scrum-133.md#L211) — pas de spoofing possible).
- `DELETE /events/{id}/co-organizers/{userId}` retourne `204` (pas de body) : convention REST standard, identique à [`DELETE /events/{id}/favorite`](backend/src/main/java/ch/unige/events/resource/FavoriteResource.java).
- `POST` retourne `201` : convention création, identique à [`POST /events`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L72-L78).
- `accept` retourne `200 CoOrganizerDTO` (pas `204`) : permet à l'UI de mettre à jour le chip sans refetch.
- `decline` retourne `204` : la row est supprimée, il n'y a plus rien à représenter.

### 12. Admin peut inviter / retirer à la place du créateur

**Décision.** Le rôle ADMIN peut :
- inviter (`POST`) à la place du créateur ;
- retirer (`DELETE`) à la place du créateur.

L'ADMIN ne peut pas accepter/décliner pour autrui (`PATCH /me/accept|decline` reste strictement self).

**Justification.** Cohérent avec le pattern existant : `EventService.publish` et `EventService.uploadImage` acceptent `isAdmin || isCreator`. Modération oblige : un admin doit pouvoir corriger une invitation problématique sans dépendre du créateur.

L'accept/decline reste self-only pour des raisons de consentement — un admin ne peut pas faire dire « oui » à autrui.

### 13. Body de POST `/co-organizers` — `{ "userId": "<uuid>" }` (pas `email`)

**Décision.** Le body de l'invitation est `InviteCoOrganizerRequest` :

```java
public record InviteCoOrganizerRequest(@NotNull UUID userId) {}
```

**Justification.** Le frontend SCRUM-137 dispose déjà d'un champ de recherche utilisateur (qui appellera `GET /api/users/search?q=` une fois SCRUM-138 / Follow disponible — d'ici là, le frontend utilisera l'UUID via auto-complétion sur `displayName`). Inviter par email ouvrirait deux complexités :
- résolution email → user (avec gestion des emails non-provisionnés sur la plateforme) ;
- gestion d'invitations vers des emails non-existants (à inviter d'abord, ce qui sort largement du scope).

L'UUID force l'invitation à porter sur un utilisateur réel et provisionné. C'est une contrainte assumée (cf. critère d'acceptation Jira *« 404 si user inexistant »*).

### 14. Schéma DB — créé par Hibernate, contrainte CHECK posée à la création initiale

**Décision.** La table `event_co_organizers` est créée automatiquement par Hibernate au premier démarrage post-déploiement. Aucun fichier SQL de migration. Aucune entrée à ajouter à [`SchemaFixup`](backend/src/main/java/ch/unige/events/config/SchemaFixup.java) **dans cette PR**.

**Justification.** Hibernate `update` mode pose le `event_co_organizers_status_check` à la création initiale de la table — c'est le seul moment où il génère une CHECK pour `@Enumerated(STRING)`. Sur les bases existantes (DevServices test, dev local, staging), la table est créée *de novo* avec la CHECK correcte. Pas de drift à reconcilier (cf. SCRUM-164, [`specs_scrum-164.md`](specs_archives/specs_claude/specs_scrum-164.md)).

> **Note importante** à documenter dans `data-model.md` (et à respecter en production). Toute future modification du `CoOrganizerStatus` (ajout d'une valeur, rename) — qui vient toujours après la création initiale de la table — **devra ajouter** un bloc `RECREATE_EVENT_CO_ORGANIZERS_STATUS_CHECK` dans `SchemaFixup` au moment du changement (et un test associé dans [`SchemaFixupTest`](backend/src/test/java/ch/unige/events/config/SchemaFixupTest.java)). Hors scope de SCRUM-136.

### 15. Pas de notification — hors scope

**Décision.** Aucune notification email, aucune entité `Notification`, aucun envoi d'event Quarkus. Une invitation est silencieuse côté backend ; le frontend (SCRUM-137) ira la chercher via `GET /me/co-organizer-invitations`.

**Justification.** L'épic Notifications (SCRUM-99) est explicitement Sprint 7+ et non démarré. Construire un envoi email ad hoc ici introduirait une dépendance Mailer Quarkus, des secrets SMTP, du HTML templating — multiplie le scope par ~3. La SCRUM-137 livre un badge Navbar qui suffit fonctionnellement à la première itération de US-29.

### 16. Pas de soft-delete sur `EventCoOrganizer`

**Décision.** Le retrait par le créateur (`DELETE /co-organizers/{userId}`) et le decline par l'invité suppriment **physiquement** la row. Pas de champ `removedAt` ni de status `REMOVED`.

**Justification.** Symétrique à [`Favorite`](backend/src/main/java/ch/unige/events/entity/Favorite.java) (suppression physique autorisée, pas de soft-delete) et à `Attendance` (un `removeAttendance` supprime la row). La row `EventCoOrganizer` n'a pas de valeur historique métier à elle seule — l'event a un créateur immuable, et l'audit social (qui a invité qui) sortirait largement du scope si on voulait le bâtir proprement.

### 17. Pas de modification de `Event.creator` — relation `@ManyToOne` reste mono-valuée

**Décision.** L'entité `Event` ne change pas. La relation `@ManyToOne creator` reste mono-valuée. La nouvelle entité `EventCoOrganizer` se relie à `Event` par `Long eventId` (pattern `Attendance` / `Favorite`), **pas** via `@ManyToOne(Event)`.

**Justification.**
- Aligné sur les autres tables de jointure (`Attendance`, `Favorite`, `EventView`) qui utilisent `Long eventId` + `UUID userId` plutôt que des `@ManyToOne` JPA.
- Évite une cascade JPA que personne n'utilise (le retrait d'un co-organisateur n'est pas couplé au cycle de vie de l'event).
- Pas de risque de N+1 lazy loading : on ne chargera jamais `event.coOrganizers` — on requête directement la table.
- La FK PostgreSQL existe quand même (Hibernate génère `event_id` avec FK implicite vers `events.id` à condition qu'on déclare la colonne avec `@Column(name = "event_id", nullable = false)` — pas besoin d'`@JoinColumn`).

### 18. Indexes DB — `idx_event_co_organizers_user`, `idx_event_co_organizers_event`

**Décision.** L'entité `EventCoOrganizer` déclare deux index B-tree (en plus de l'unique constraint composite) :

```java
@Table(name = "event_co_organizers",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_event_co_organizers_event_user",
           columnNames = {"event_id", "user_id"}),
       indexes = {
           @Index(name = "idx_event_co_organizers_event", columnList = "event_id"),
           @Index(name = "idx_event_co_organizers_user", columnList = "user_id")
       })
```

**Justification.** Les deux requêtes hot-path nécessitent un accès indexé :
- `getCoOrganizers(eventId)` → filtrage par `event_id`.
- `getMyInvitations(userId, status)` → filtrage par `user_id`.
- L'unique constraint composite `(event_id, user_id)` couvre déjà la première (composé préfixe-aligné), mais PAS la deuxième (PostgreSQL ne peut pas utiliser un préfixe non-leading). D'où l'index dédié sur `user_id`.

Coût : 2 indexes × volume très faible (≤ N×3 rows par event en moyenne dans la pratique). Négligeable.

---

## Analyse de l'existant

### Ce qui existe (à réutiliser tel quel)

| Élément | Fichier / ligne | Rôle |
|---|---|---|
| Pattern PanacheEntity + table de jointure unique | [`Attendance.java:13-20`](backend/src/main/java/ch/unige/events/entity/Attendance.java#L13-L20) | Modèle direct pour `EventCoOrganizer` |
| Pattern `@PrePersist` pour `createdAt`/`invitedAt` | [`Attendance.java:36-39`](backend/src/main/java/ch/unige/events/entity/Attendance.java#L36-L39) | Reproduire pour `invitedAt` |
| Helpers `User.findByAuth0Id` / `findByEmail` | [`User.java:53-59`](backend/src/main/java/ch/unige/events/entity/User.java#L53-L59) | Résolution auth0Id → User dans tous les services |
| Helper `User.findByIdOptional(UUID)` | hérité de `PanacheEntityBase` | Résolution `targetUserId` (validation existence) |
| Pattern injection `SecurityIdentity` (constructor DI) | [`EventResource.java:34-41`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L34-L41) | Resource du même style |
| Pattern injection `SecurityIdentity` (field DI) | [`UserResource.java:49-55`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L49-L55) | UserResource utilise les deux styles ; on suivra le constructor DI sur la nouvelle Resource |
| Mapper `ApiErrorResponse` + WebApplicationException 4xx | [`AttendanceService.java:57-66`](backend/src/main/java/ch/unige/events/service/AttendanceService.java#L57-L66) | Pattern pour 400 `cannot_invite_self` et 409 `already_invited` |
| Exception mappers prêts (`NotFoundException`/`ForbiddenException`) | [`backend/docs/architecture.md` ligne 137-142](backend/docs/architecture.md#L137-L142) | Aucun nouveau mapper à écrire |
| Pattern `EventDTO.from(Event, long, Long, long)` | [`EventDTO.java:36-66`](backend/src/main/java/ch/unige/events/dto/event/EventDTO.java#L36-L66) | Réutilisé pour enrichir `CoOrganizerInvitationDTO` |
| Helpers `countAttending` / `countWaitlisted` / `computeAvailableSpots` / `toEventDTOs` | [`EventService.java:306-334`](backend/src/main/java/ch/unige/events/service/EventService.java#L306-L334) | Réutilisés pour le bulk-mapping `events → EventDTO[]` côté `getMyInvitations` |
| `Attendance.countGroupedByStatus` | [`Attendance.java:60-77`](backend/src/main/java/ch/unige/events/entity/Attendance.java#L60-L77) | Réutilisé via `toEventDTOs` |
| `MockEventFactory.build` | [`MockEventFactory.java:21-39`](backend/src/test/java/ch/unige/events/MockEventFactory.java#L21-L39) | Seed d'Event en test |
| Pattern `*ServiceMock` (`@Mock @ApplicationScoped extends Service`) | [`AttendanceServiceMock.java:22-126`](backend/src/test/java/ch/unige/events/service/AttendanceServiceMock.java) | Modèle direct pour `EventCoOrganizerServiceMock` |
| Pattern `@TestSecurity(user = "auth0|alice")` | [`AttendanceResourceTest.java:32`](backend/src/test/java/ch/unige/events/resource/AttendanceResourceTest.java#L32) | Réutilisé pour tous les tests |
| Pattern `static volatile boolean force*` | [`AttendanceServiceMock.java:31-37`](backend/src/test/java/ch/unige/events/service/AttendanceServiceMock.java#L31-L37) | Réutilisé pour les forcings d'erreur |

### Ce qui est à modifier

| Fichier | Modification |
|---|---|
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | **+6 paths**, **+2 schemas** (`CoOrganizer`, `CoOrganizerInvitation`, `InviteCoOrganizerRequest`), **+1 enum** (`CoOrganizerStatus`). Mise à jour des descriptions de `/events/{id}/attendees`, `/events/{id}/stats`, `/events/{id}` (visibilité co-org), `/events/{id}/cancel`, `/events/{id}/restore`, `/events/{id}/publish`, `/events/{id}/image`, `PUT /events/{id}` pour mentionner la cascade ACCEPTED |
| [`EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | Ajout helper `isCreatorOrAcceptedCoOrganizer`, migration des 6 call-sites listés en décision 2 |
| [`AttendanceService.java`](backend/src/main/java/ch/unige/events/service/AttendanceService.java) | Migration de `getAttendees` (ligne 143) — extension à co-org ACCEPTED |
| [`EventStatsService.java`](backend/src/main/java/ch/unige/events/service/EventStatsService.java) | Migration de `getStats` (ligne 26) — extension à co-org ACCEPTED |
| [`UserResource.java`](backend/src/main/java/ch/unige/events/resource/UserResource.java) | Ajout endpoint `GET /me/co-organizer-invitations` — injection `EventCoOrganizerService` |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Ajout entrée entité `EventCoOrganizer` + enum `CoOrganizerStatus` + section « Permissions co-organisateur » |
| [`backend/docs/api-contract.md`](backend/docs/api-contract.md) | Ajout des 6 endpoints dans le tableau « Endpoints implémentés » + section dédiée |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Entrée Sprint 7 SCRUM-136 |

### Ce qui est à créer

| Fichier | Rôle |
|---|---|
| `backend/src/main/java/ch/unige/events/entity/EventCoOrganizer.java` | Entité PanacheEntity (eventId, userId, status, invitedAt) + helper `isAcceptedFor` |
| `backend/src/main/java/ch/unige/events/entity/CoOrganizerStatus.java` | Enum `PENDING / ACCEPTED / DECLINED` |
| `backend/src/main/java/ch/unige/events/dto/coorganizer/CoOrganizerDTO.java` | DTO pour `GET /events/{id}/co-organizers` |
| `backend/src/main/java/ch/unige/events/dto/coorganizer/CoOrganizerInvitationDTO.java` | DTO pour `GET /me/co-organizer-invitations` |
| `backend/src/main/java/ch/unige/events/dto/coorganizer/InviteCoOrganizerRequest.java` | Body de `POST /events/{id}/co-organizers` |
| `backend/src/main/java/ch/unige/events/service/EventCoOrganizerService.java` | Service métier |
| `backend/src/main/java/ch/unige/events/resource/EventCoOrganizerResource.java` | Resource JAX-RS sous `/events/{id}/co-organizers` |
| `backend/src/test/java/ch/unige/events/service/EventCoOrganizerServiceMock.java` | Mock pour les tests Resource |
| `backend/src/test/java/ch/unige/events/resource/EventCoOrganizerResourceTest.java` | Tests `@QuarkusTest` Resource |
| `backend/src/test/java/ch/unige/events/service/EventCoOrganizerServiceCoverageTest.java` | Tests intégration DevServices PostgreSQL |
| `backend/src/test/java/ch/unige/events/dto/coorganizer/CoOrganizerDTOTest.java` | Tests unitaires factories |

### Ce qui n'est PAS dans le scope

- ❌ Pas de notification email / push.
- ❌ Pas de transfert d'ownership (`creator` reste immuable).
- ❌ Pas de `bulk invite` (un endpoint = une invitation).
- ❌ Pas de durée de vie / expiration automatique d'une invitation PENDING.
- ❌ Pas de relance automatique (le frontend SCRUM-137 affiche le badge, c'est tout).
- ❌ Pas d'extension de `EventDTO` avec `coOrganizers[]` inline — endpoint séparé pour éviter de gonfler le DTO le plus utilisé.
- ❌ Pas de modification du frontend dans cette PR — SCRUM-137 le fait dans `feature/s6-co-organizers-front`.
- ❌ Pas de modification de `Event.creator` (mono-valué reste).
- ❌ Pas d'ajout de `event_co_organizers_status_check` à `SchemaFixup` (création ex nihilo, cf. décision 14).
- ❌ Pas d'invitation par email (cf. décision 13).
- ❌ Pas de mutation d'`isCreator` (cf. décision 3).

---

## Étape 0 — `openapi/openapi.yaml` (EN PREMIER, règle d'or)

**Aucune ligne de Java ne doit être écrite avant cette étape.** [`backend/AGENTS.md`](backend/AGENTS.md#L62-L65) : *« Avant d'implémenter un endpoint : 1. L'ajouter dans `openapi/openapi.yaml` ; 2. Ensuite seulement coder Resource → Service → Entity → Test »*.

### 0.1 — Ajouter le schéma `CoOrganizerStatus` (section `components.schemas`, à côté de `AttendanceStatus`)

```yaml
    CoOrganizerStatus:
      type: string
      description: |
        Statut d'une invitation à co-organiser un événement.
        - `PENDING` : invitation envoyée, en attente de réponse.
        - `ACCEPTED` : invitation acceptée — le co-organisateur peut désormais éditer / publier / annuler /
          consulter les inscrits / les stats de l'événement.
        - `DECLINED` : valeur transitoire renvoyée par `PATCH /events/{id}/co-organizers/me/decline` ;
          la row est supprimée juste après. N'apparaît jamais dans `GET /events/{id}/co-organizers` ni
          dans `GET /users/me/co-organizer-invitations`.
      enum: [PENDING, ACCEPTED, DECLINED]
```

### 0.2 — Ajouter les schémas `CoOrganizer`, `CoOrganizerInvitation`, `InviteCoOrganizerRequest`

```yaml
    CoOrganizer:
      type: object
      description: |
        Représentation d'un co-organisateur dans la liste d'un événement
        (renvoyée par `GET /events/{id}/co-organizers`). Projection minimaliste —
        `email`, `bio`, `faculty`, `studyLevel` ne sont pas exposés (privacy).
      properties:
        id:
          type: integer
          format: int64
          description: ID interne de la row EventCoOrganizer (utilisé par DELETE par admin si besoin)
        userId:
          type: string
          format: uuid
        displayName:
          type: string
          nullable: true
        avatarUrl:
          type: string
          format: uri
          nullable: true
        status:
          $ref: '#/components/schemas/CoOrganizerStatus'
        invitedAt:
          type: string
          format: date-time
      required: [id, userId, status, invitedAt]

    CoOrganizerInvitation:
      type: object
      description: |
        Représentation d'une invitation à co-organiser, du point de vue de l'utilisateur invité
        (renvoyée par `GET /users/me/co-organizer-invitations`). Inclut l'`Event` enrichi pour
        permettre à l'UI d'afficher titre / dates / banner sans refetch.
      properties:
        id:
          type: integer
          format: int64
        event:
          $ref: '#/components/schemas/Event'
        status:
          $ref: '#/components/schemas/CoOrganizerStatus'
        invitedAt:
          type: string
          format: date-time
      required: [id, event, status, invitedAt]

    InviteCoOrganizerRequest:
      type: object
      description: |
        Body de `POST /events/{id}/co-organizers`. Identifie l'utilisateur à inviter par UUID.
        Inviter par email est explicitement non supporté en S7.
      required: [userId]
      properties:
        userId:
          type: string
          format: uuid
          description: UUID de l'utilisateur à inviter (doit être provisionné en base — sinon 404)
```

### 0.3 — Ajouter les paths sous `paths:` (placement à côté de `/events/{id}/attendees`, ligne ~1809)

```yaml
  /events/{id}/co-organizers:
    post:
      summary: Inviter un utilisateur comme co-organisateur
      description: |
        Crée une invitation `PENDING`. Réservé au créateur de l'événement ou à un admin.
        L'utilisateur invité doit déjà être provisionné en base.

        Codes d'erreur :
        - `400 cannot_invite_self` : tentative d'auto-invitation par le créateur.
        - `403 forbidden` : appelant non créateur et non admin.
        - `404 not_found` : événement introuvable, ou `userId` cible introuvable.
        - `409 already_invited` : l'utilisateur a déjà une invitation PENDING ou ACCEPTED sur cet event.
      operationId: inviteCoOrganizer
      tags: [events, co-organizers]
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
              $ref: '#/components/schemas/InviteCoOrganizerRequest'
      responses:
        '201':
          description: Invitation créée (status PENDING)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CoOrganizer'
        '400':
          description: Body invalide ou `cannot_invite_self`
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
          description: Appelant non créateur de l'événement et non admin
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Événement ou utilisateur cible introuvable
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '409':
          description: Une invitation PENDING ou ACCEPTED existe déjà pour ce couple (event, user)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'

    get:
      summary: Lister les co-organisateurs d'un événement
      description: |
        Retourne la liste de tous les co-organisateurs (PENDING + ACCEPTED) d'un événement,
        triée par `invitedAt` ASC. Authentifié uniquement.
        Les rows DECLINED sont supprimées physiquement (cf. `PATCH /me/decline`) — elles
        n'apparaissent donc jamais ici.
      operationId: listCoOrganizers
      tags: [events, co-organizers]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: Liste des co-organisateurs (tableau vide si aucun — jamais 404)
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/CoOrganizer'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Événement introuvable
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'

  /events/{id}/co-organizers/{userId}:
    delete:
      summary: Retirer un co-organisateur
      description: |
        Supprime physiquement la row `EventCoOrganizer`. Réservé au créateur ou à un admin.
        Idempotent côté résultat : retourne 204 même si l'utilisateur n'avait pas d'invitation
        (mais retourne 404 si l'événement lui-même n'existe pas — pour éviter de masquer un
        ID erroné côté client).
      operationId: removeCoOrganizer
      tags: [events, co-organizers]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Co-organisateur retiré (ou n'avait pas d'invitation — idempotent)
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '403':
          description: Appelant non créateur et non admin
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Événement introuvable
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'

  /events/{id}/co-organizers/me/accept:
    patch:
      summary: Accepter une invitation à co-organiser
      description: |
        Bascule le status `PENDING` → `ACCEPTED`. L'utilisateur authentifié doit avoir une
        invitation PENDING sur cet événement. À partir de là, il peut éditer / publier /
        annuler / restaurer / charger une bannière / consulter les inscrits / les stats.

        Si l'invitation est déjà ACCEPTED → 200 idempotent (renvoie l'état actuel sans
        modification).
        Si aucune invitation → 404.
      operationId: acceptCoOrganizerInvitation
      tags: [events, co-organizers]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: Invitation acceptée (ou déjà ACCEPTED — idempotent)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CoOrganizer'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: |
            Aucune invitation pour l'utilisateur courant sur cet événement (envelope identique
            à un événement inexistant — pas d'oracle).
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'

  /events/{id}/co-organizers/me/decline:
    patch:
      summary: Décliner une invitation à co-organiser
      description: |
        Supprime physiquement la row `EventCoOrganizer` correspondant à l'utilisateur courant
        (cf. décision 5 de la spec : DECLINE = DELETE row, autorise une ré-invitation
        ultérieure sans 409). Si aucune invitation → 404.
      operationId: declineCoOrganizerInvitation
      tags: [events, co-organizers]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '204':
          description: Invitation déclinée (row supprimée)
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Aucune invitation pour l'utilisateur courant sur cet événement
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'

  /users/me/co-organizer-invitations:
    get:
      summary: Mes invitations à co-organiser
      description: |
        Retourne les invitations adressées à l'utilisateur courant. Par défaut `status=PENDING`.
        Chaque entrée enrichit l'événement complet (`Event` schema) pour que le frontend puisse
        afficher titre / dates / banner sans refetch.

        Les invitations sur des événements `CANCELLED` apparaissent normalement (l'invité a le
        droit de comprendre pourquoi son badge a clignoté).
      operationId: getMyCoOrganizerInvitations
      tags: [users, co-organizers]
      security:
        - BearerAuth: []
      parameters:
        - name: status
          in: query
          description: Filtre optionnel sur un statut. Par défaut `PENDING`.
          schema:
            $ref: '#/components/schemas/CoOrganizerStatus'
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
          description: Liste paginée des invitations (tableau vide si aucune)
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/CoOrganizerInvitation'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Profil utilisateur introuvable (non provisionné — appeler GET /users/me d'abord)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.4 — Mettre à jour les descriptions des endpoints touchés par la cascade

Les paths suivants doivent ajouter une mention « *Le créateur de l'événement OU un co-organisateur ACCEPTED peut effectuer cette action* » dans leur description :

| Path | Ligne actuelle | Mention à ajouter |
|---|---|---|
| `PUT /events/{id}` | description existante | « Réservé au créateur **ou à un co-organisateur ACCEPTED** » |
| `DELETE /events/{id}` | description existante | « Réservé au créateur (suppression physique d'un CANCELLED — non délégable aux co-organisateurs) » |
| `PATCH /events/{id}/cancel` | ligne ~1481 | « Créateur **ou co-organisateur ACCEPTED** » |
| `PATCH /events/{id}/restore` | ligne ~1530 | « Créateur **ou co-organisateur ACCEPTED** » |
| `PATCH /events/{id}/publish` | ligne ~1579 | « Créateur (ou co-organisateur ACCEPTED) ou ADMIN » |
| `POST /events/{id}/image` | ligne ~1650 | « Créateur (ou co-organisateur ACCEPTED) ou ADMIN » |
| `GET /events/{id}` | ligne ~1337 | Ajouter à la règle de visibilité 4.12 : « Un co-organisateur ACCEPTED peut aussi voir un DRAFT/CANCELLED » |
| `GET /events/{id}/attendees` | ligne ~1809 | « Créateur **ou co-organisateur ACCEPTED** » |
| `GET /events/{id}/stats` | ligne ~2001 | « Créateur **ou co-organisateur ACCEPTED** » |

### 0.5 — Validation OpenAPI

Avant de passer à l'étape 1 :

```bash
# Pré-commit hook si activé
.github/hooks/pre-commit  # (si présent)
# Ou manuellement
npx @redocly/cli lint openapi/openapi.yaml  # ou équivalent installé
```

Aucune erreur de schéma. Les `$ref` sur `CoOrganizerStatus`, `Event`, `ApiErrorResponse` doivent résoudre.

---

## Étape 1 — Entité `EventCoOrganizer` + enum `CoOrganizerStatus`

### 1.1 — `backend/src/main/java/ch/unige/events/entity/CoOrganizerStatus.java`

```java
package ch.unige.events.entity;

public enum CoOrganizerStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
```

### 1.2 — `backend/src/main/java/ch/unige/events/entity/EventCoOrganizer.java`

```java
package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(
    name = "event_co_organizers",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_event_co_organizers_event_user",
        columnNames = {"event_id", "user_id"}
    ),
    indexes = {
        @Index(name = "idx_event_co_organizers_event", columnList = "event_id"),
        @Index(name = "idx_event_co_organizers_user", columnList = "user_id")
    }
)
public class EventCoOrganizer extends PanacheEntity {

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CoOrganizerStatus status;

    @Column(updatable = false)
    public LocalDateTime invitedAt;

    @PrePersist
    public void prePersist() {
        invitedAt = LocalDateTime.now();
    }

    public static boolean isAcceptedFor(Long eventId, UUID userId) {
        return count("eventId = ?1 and userId = ?2 and status = ?3",
                eventId, userId, CoOrganizerStatus.ACCEPTED) > 0;
    }

    public static Optional<EventCoOrganizer> findByEventAndUser(Long eventId, UUID userId) {
        return find("eventId = ?1 and userId = ?2", eventId, userId).firstResultOptional();
    }

    public static List<EventCoOrganizer> findByEvent(Long eventId) {
        return list("eventId = ?1 order by invitedAt asc", eventId);
    }

    public static List<EventCoOrganizer> findByUser(UUID userId, CoOrganizerStatus status, int page, int size) {
        return find("userId = ?1 and status = ?2 order by invitedAt desc", userId, status)
                .page(page, size)
                .list();
    }
}
```

**Notes** :
- L'unique constraint `uq_event_co_organizers_event_user` empêche deux rows pour le même couple — appui de la décision 5 (DECLINE supprime la row pour autoriser ré-invitation).
- `invitedAt` n'est PAS `nullable=false` au niveau colonne mais l'est implicitement via `@PrePersist`. Cohérent avec [`Attendance.createdAt`](backend/src/main/java/ch/unige/events/entity/Attendance.java#L33-L39).
- Le helper `findByUser(UUID, CoOrganizerStatus, int, int)` est utilisé par `getMyInvitations` ; le helper `findByEvent` est utilisé par `getCoOrganizers`.

---

## Étape 2 — DTOs

### 2.1 — `backend/src/main/java/ch/unige/events/dto/coorganizer/CoOrganizerDTO.java`

```java
package ch.unige.events.dto.coorganizer;

import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.EventCoOrganizer;
import ch.unige.events.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record CoOrganizerDTO(
        Long id,
        UUID userId,
        String displayName,
        String avatarUrl,
        CoOrganizerStatus status,
        LocalDateTime invitedAt
) {
    public static CoOrganizerDTO from(EventCoOrganizer entity, User user) {
        return new CoOrganizerDTO(
                entity.id,
                entity.userId,
                user != null ? user.displayName : null,
                user != null ? user.avatarUrl : null,
                entity.status,
                entity.invitedAt
        );
    }
}
```

### 2.2 — `backend/src/main/java/ch/unige/events/dto/coorganizer/CoOrganizerInvitationDTO.java`

```java
package ch.unige.events.dto.coorganizer;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.EventCoOrganizer;

import java.time.LocalDateTime;

public record CoOrganizerInvitationDTO(
        Long id,
        EventDTO event,
        CoOrganizerStatus status,
        LocalDateTime invitedAt
) {
    public static CoOrganizerInvitationDTO from(EventCoOrganizer entity, EventDTO event) {
        return new CoOrganizerInvitationDTO(entity.id, event, entity.status, entity.invitedAt);
    }
}
```

### 2.3 — `backend/src/main/java/ch/unige/events/dto/coorganizer/InviteCoOrganizerRequest.java`

```java
package ch.unige.events.dto.coorganizer;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InviteCoOrganizerRequest(@NotNull UUID userId) {}
```

---

## Étape 3 — `EventCoOrganizerService`

**Fichier :** `backend/src/main/java/ch/unige/events/service/EventCoOrganizerService.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.coorganizer.CoOrganizerDTO;
import ch.unige.events.dto.coorganizer.CoOrganizerInvitationDTO;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCoOrganizer;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class EventCoOrganizerService {

    @Inject
    EventService eventService;

    @Transactional
    public CoOrganizerDTO invite(Long eventId, String inviterAuth0Id, UUID targetUserId, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        User inviter = User.findByAuth0Id(inviterAuth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        if (!isAdmin && !isCreator(event, inviterAuth0Id)) {
            throw new ForbiddenException("Only the event creator (or an admin) can invite co-organizers");
        }

        if (inviter.id.equals(targetUserId) && isCreator(event, inviterAuth0Id)) {
            throw badRequest("cannot_invite_self",
                    "The event creator cannot invite themselves as co-organizer.");
        }

        User target = User.<User>findByIdOptional(targetUserId)
                .orElseThrow(() -> new NotFoundException("Target user not found"));

        if (EventCoOrganizer.findByEventAndUser(eventId, targetUserId).isPresent()) {
            throw conflict("already_invited",
                    "This user already has a PENDING or ACCEPTED invitation on this event.");
        }

        EventCoOrganizer invitation = new EventCoOrganizer();
        invitation.eventId = eventId;
        invitation.userId = targetUserId;
        invitation.status = CoOrganizerStatus.PENDING;
        invitation.persist();

        return CoOrganizerDTO.from(invitation, target);
    }

    @Transactional
    public CoOrganizerDTO accept(Long eventId, String userAuth0Id) {
        User user = User.findByAuth0Id(userAuth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        EventCoOrganizer invitation = EventCoOrganizer.findByEventAndUser(eventId, user.id)
                .orElseThrow(NotFoundException::new);

        if (invitation.status != CoOrganizerStatus.ACCEPTED) {
            invitation.status = CoOrganizerStatus.ACCEPTED;
        }
        return CoOrganizerDTO.from(invitation, user);
    }

    @Transactional
    public void decline(Long eventId, String userAuth0Id) {
        User user = User.findByAuth0Id(userAuth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        EventCoOrganizer invitation = EventCoOrganizer.findByEventAndUser(eventId, user.id)
                .orElseThrow(NotFoundException::new);

        invitation.delete();
    }

    @Transactional
    public void remove(Long eventId, String requesterAuth0Id, UUID targetUserId, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        if (!isAdmin && !isCreator(event, requesterAuth0Id)) {
            throw new ForbiddenException("Only the event creator (or an admin) can remove co-organizers");
        }

        EventCoOrganizer.findByEventAndUser(eventId, targetUserId)
                .ifPresent(EventCoOrganizer::delete);
        // Idempotent : pas de 404 si la row n'existe pas — l'event lui-même existe (vérifié au-dessus).
    }

    @Transactional
    public List<CoOrganizerDTO> getCoOrganizers(Long eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        List<EventCoOrganizer> rows = EventCoOrganizer.findByEvent(eventId);
        if (rows.isEmpty()) {
            return List.of();
        }

        // Bulk-fetch des Users pour éviter le N+1.
        List<UUID> userIds = rows.stream().map(r -> r.userId).distinct().toList();
        Map<UUID, User> usersById = new HashMap<>();
        User.<User>list("id IN ?1", userIds).forEach(u -> usersById.put(u.id, u));

        return rows.stream()
                .map(r -> CoOrganizerDTO.from(r, usersById.get(r.userId)))
                .toList();
    }

    @Transactional
    public List<CoOrganizerInvitationDTO> getMyInvitations(String auth0Id, CoOrganizerStatus status, int page, int size) {
        User user = User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        CoOrganizerStatus effective = status != null ? status : CoOrganizerStatus.PENDING;
        List<EventCoOrganizer> invitations = EventCoOrganizer.findByUser(user.id, effective, page, size);
        if (invitations.isEmpty()) {
            return List.of();
        }

        // Bulk-fetch des Events + comptes ATTENDING/WAITLISTED via les helpers existants
        // (cf. EventService.toEventDTOs). Délégué à EventService pour respecter la cohérence
        // de la projection EventDTO et éviter une duplication de logique métier.
        List<Long> eventIds = invitations.stream().map(i -> i.eventId).toList();
        Map<Long, EventDTO> eventsById = eventService.findByIdsAsDTO(eventIds);

        return invitations.stream()
                .map(i -> {
                    EventDTO event = eventsById.get(i.eventId);
                    return event != null ? CoOrganizerInvitationDTO.from(i, event) : null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static boolean isCreator(Event event, String auth0Id) {
        return event.creator != null
                && event.creator.auth0Id != null
                && event.creator.auth0Id.equals(auth0Id);
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
}
```

**Note importante.** `eventService.findByIdsAsDTO(List<Long>)` est un nouvel helper public sur `EventService` (cf. étape 4.2 ci-dessous) qui réutilise `toEventDTOs` en lui passant la liste filtrée d'`Event` chargés via `Event.list("id IN ?1", ids)`. Il évite à `EventCoOrganizerService` de devoir injecter `EntityManager` + dupliquer la logique des bulk counts d'attendances.

---

## Étape 4 — `EventService` — helper de cascade + helper bulk DTO

### 4.1 — Ajouter `isCreatorOrAcceptedCoOrganizer` et migrer les call-sites

**Fichier :** [`backend/src/main/java/ch/unige/events/service/EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java)

**AVANT (call-site `update`)** :

```java
@Transactional
public EventDTO update(Long id, String auth0Id, UpdateEventRequest request) {
    Event event = Event.<Event>findByIdOptional(id)
            .orElseThrow(NotFoundException::new);

    if (!isCreator(event, auth0Id)) {
        throw new ForbiddenException("Only the event creator can update this event");
    }
    // ...
}
```

**APRÈS** :

```java
@Transactional
public EventDTO update(Long id, String auth0Id, UpdateEventRequest request) {
    Event event = Event.<Event>findByIdOptional(id)
            .orElseThrow(NotFoundException::new);

    if (!isCreatorOrAcceptedCoOrganizer(event, auth0Id)) {
        throw new ForbiddenException("Only the event creator or an accepted co-organizer can update this event");
    }
    // ...
}
```

**Application identique sur** : `cancel` (ligne 205), `restore` (ligne 223), `uploadImage` (ligne 297). Pour `publish` (ligne 247), conserver le `!isAdmin` en garde :

```java
if (!isAdmin && !isCreatorOrAcceptedCoOrganizer(event, auth0Id)) {
    throw new ForbiddenException("Only the event creator, an accepted co-organizer, or an admin can publish this event");
}
```

Pour `getById` (ligne 141), idem avec `!isAdmin` :

```java
if (event.status != EventStatus.PUBLISHED && !isAdmin && !isCreatorOrAcceptedCoOrganizer(event, auth0Id)) {
    throw new NotFoundException();
}
```

**Pour `delete` (ligne 189)** : ne PAS migrer. Conserver `isCreator` strict (cf. décision 2).

### 4.2 — Ajouter le helper `isCreatorOrAcceptedCoOrganizer` + l'helper public `findByIdsAsDTO`

À ajouter à la fin de `EventService.java` (après les helpers privés existants `countAttending`, `countWaitlisted`, `computeAvailableSpots`, `normalizeTags`, `isCreator`) :

```java
private boolean isCreatorOrAcceptedCoOrganizer(Event event, String auth0Id) {
    if (isCreator(event, auth0Id)) {
        return true;
    }
    return User.findByAuth0Id(auth0Id)
            .map(user -> EventCoOrganizer.isAcceptedFor(event.id, user.id))
            .orElse(false);
}

@Transactional
public Map<Long, EventDTO> findByIdsAsDTO(List<Long> ids) {
    if (ids.isEmpty()) {
        return Map.of();
    }
    List<Event> events = Event.<Event>list("id IN ?1", ids);
    List<EventDTO> dtos = toEventDTOs(events);
    Map<Long, EventDTO> result = new HashMap<>();
    for (EventDTO dto : dtos) {
        result.put(dto.id(), dto);
    }
    return result;
}
```

**Notes** :
- `isCreatorOrAcceptedCoOrganizer` n'est **pas** `static` — il fait une lookup DB qui doit s'exécuter dans le contexte transactionnel courant. `isCreator` reste `static` (vérification mémoire pure).
- `findByIdsAsDTO` est `public` (et non `private`) car appelé depuis `EventCoOrganizerService.getMyInvitations`. `@Transactional` est ajouté pour garantir la session JPA quand l'helper est invoqué depuis un autre service.
- L'import à ajouter : `import ch.unige.events.entity.EventCoOrganizer;`.

### 4.3 — Migrer `AttendanceService.getAttendees`

**Fichier :** [`backend/src/main/java/ch/unige/events/service/AttendanceService.java`](backend/src/main/java/ch/unige/events/service/AttendanceService.java#L138-L150)

**AVANT** :

```java
@Transactional
public List<AttendanceDTO> getAttendees(String auth0Id, Long eventId, int page, int size) {
    Event event = Event.<Event>findByIdOptional(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));

    if (event.creator == null || event.creator.auth0Id == null || !event.creator.auth0Id.equals(auth0Id)) {
        throw new ForbiddenException("Only the event creator can view attendees");
    }
    // ...
}
```

**APRÈS** :

```java
@Inject EventService eventService;  // déjà présent ? Sinon ajouter.

@Transactional
public List<AttendanceDTO> getAttendees(String auth0Id, Long eventId, int page, int size) {
    Event event = Event.<Event>findByIdOptional(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));

    if (!eventService.isCreatorOrAcceptedCoOrganizerPublic(event, auth0Id)) {
        throw new ForbiddenException("Only the event creator or an accepted co-organizer can view attendees");
    }
    // ...
}
```

**Décision corollaire** : pour permettre à `AttendanceService` et `EventStatsService` de réutiliser le même helper sans duplication, exposer une méthode publique `EventService.isCreatorOrAcceptedCoOrganizerPublic(Event, String)` qui délègue à la version privée. Le suffixe `Public` est intentionnel pour distinguer du privé même-classe.

Alternativement (si l'équipe préfère ne pas exposer publiquement) : dupliquer le 3-liner dans `AttendanceService` et `EventStatsService`. **Recommandation : exposer publiquement** — la règle métier n'a aucune raison d'être ré-écrite trois fois, et un changement futur (ex. inclure ADMIN sans le passer en paramètre) doit être atomique.

### 4.4 — Migrer `EventStatsService.getStats`

**Fichier :** [`backend/src/main/java/ch/unige/events/service/EventStatsService.java`](backend/src/main/java/ch/unige/events/service/EventStatsService.java#L19-L29)

**AVANT** :

```java
@Transactional
public EventStatsDTO getStats(String auth0Id, Long eventId) {
    // ...
    User caller = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);
    Event event = Event.<Event>findByIdOptional(eventId).orElseThrow(NotFoundException::new);
    if (event.creator == null || !event.creator.id.equals(caller.id)) {
        throw new ForbiddenException("Only the event creator can view stats");
    }
    // ...
}
```

**APRÈS** :

```java
@Inject EventService eventService;

@Transactional
public EventStatsDTO getStats(String auth0Id, Long eventId) {
    User caller = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);
    Event event = Event.<Event>findByIdOptional(eventId).orElseThrow(NotFoundException::new);
    if (!eventService.isCreatorOrAcceptedCoOrganizerPublic(event, auth0Id)) {
        throw new ForbiddenException("Only the event creator or an accepted co-organizer can view stats");
    }
    // ...
}
```

---

## Étape 5 — `EventCoOrganizerResource`

**Fichier :** `backend/src/main/java/ch/unige/events/resource/EventCoOrganizerResource.java`

```java
package ch.unige.events.resource;

import ch.unige.events.dto.coorganizer.CoOrganizerDTO;
import ch.unige.events.dto.coorganizer.InviteCoOrganizerRequest;
import ch.unige.events.service.EventCoOrganizerService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventCoOrganizerResource {

    private final EventCoOrganizerService service;
    private final SecurityIdentity identity;

    @Inject
    public EventCoOrganizerResource(EventCoOrganizerService service, SecurityIdentity identity) {
        this.service = service;
        this.identity = identity;
    }

    @POST
    @Path("/{id}/co-organizers")
    @Authenticated
    public Response invite(@PathParam("id") Long eventId, @Valid InviteCoOrganizerRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        boolean isAdmin = identity.hasRole("ADMIN");
        CoOrganizerDTO created = service.invite(eventId, auth0Id, request.userId(), isAdmin);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{id}/co-organizers")
    @Authenticated
    public List<CoOrganizerDTO> list(@PathParam("id") Long eventId) {
        return service.getCoOrganizers(eventId);
    }

    @DELETE
    @Path("/{id}/co-organizers/{userId}")
    @Authenticated
    public Response remove(@PathParam("id") Long eventId, @PathParam("userId") UUID userId) {
        String auth0Id = identity.getPrincipal().getName();
        boolean isAdmin = identity.hasRole("ADMIN");
        service.remove(eventId, auth0Id, userId, isAdmin);
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{id}/co-organizers/me/accept")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response accept(@PathParam("id") Long eventId) {
        String auth0Id = identity.getPrincipal().getName();
        CoOrganizerDTO accepted = service.accept(eventId, auth0Id);
        return Response.ok(accepted).build();
    }

    @PATCH
    @Path("/{id}/co-organizers/me/decline")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response decline(@PathParam("id") Long eventId) {
        String auth0Id = identity.getPrincipal().getName();
        service.decline(eventId, auth0Id);
        return Response.noContent().build();
    }
}
```

**Notes** :
- `@Consumes(MediaType.WILDCARD)` sur les `PATCH /me/accept|decline` : aucun body attendu, mais JAX-RS exige un MediaType pour les méthodes annotées `@Consumes(application/json)` au niveau classe. Pattern identique à [`DELETE /me/banner`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L255-L262).
- `@Path("/events")` au niveau classe + sous-path `/{id}/co-organizers` : conforme au pattern [`AttendanceResource`](backend/src/main/java/ch/unige/events/resource/AttendanceResource.java#L20-L23) qui mélange `/{id}/attend` et `/{id}/attendees`.

---

## Étape 6 — `UserResource` — ajout `/me/co-organizer-invitations`

**Fichier :** [`backend/src/main/java/ch/unige/events/resource/UserResource.java`](backend/src/main/java/ch/unige/events/resource/UserResource.java)

Ajouter le champ injecté en haut de la classe (après `@Inject EventService eventService;` ligne 55) :

```java
@Inject EventCoOrganizerService coOrganizerService;
```

Ajouter la méthode à la fin de la classe (après `getMyEvents` ligne 304) :

```java
@GET
@Path("/me/co-organizer-invitations")
@Authenticated
public List<CoOrganizerInvitationDTO> getMyCoOrganizerInvitations(
        @QueryParam("status") CoOrganizerStatus status,
        @QueryParam("page") @DefaultValue("0") @Min(0) int page,
        @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
    return coOrganizerService.getMyInvitations(
            identity.getPrincipal().getName(), status, page, size);
}
```

Imports à ajouter en haut :

```java
import ch.unige.events.dto.coorganizer.CoOrganizerInvitationDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.service.EventCoOrganizerService;
```

---

## Étape 7 — Tests

Cible : **≥ 80 % de couverture JaCoCo sur les lignes nouvelles**, idéalement 100 % pour les classes Service et Resource (faible complexité). Style aligné sur [`specs_scrum-133.md`](specs_archives/specs_claude/specs_scrum-133.md) et [`specs_scrum-126-129.md`](specs_archives/specs_claude/specs_scrum-126-129.md).

### 7.1 — `EventCoOrganizerServiceMock`

**Fichier :** `backend/src/test/java/ch/unige/events/service/EventCoOrganizerServiceMock.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.coorganizer.CoOrganizerDTO;
import ch.unige.events.dto.coorganizer.CoOrganizerInvitationDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.*;
import java.util.UUID;

@Mock
@ApplicationScoped
public class EventCoOrganizerServiceMock extends EventCoOrganizerService {

    public static volatile boolean forceNotFoundOnInvite = false;
    public static volatile boolean forceForbiddenOnInvite = false;
    public static volatile boolean forceConflictOnInvite = false;
    public static volatile boolean forceCannotInviteSelf = false;
    public static volatile boolean forceNotFoundOnAccept = false;
    public static volatile boolean forceNotFoundOnDecline = false;
    public static volatile boolean forceForbiddenOnRemove = false;
    public static volatile boolean forceNotFoundOnList = false;

    public final List<CoOrganizerDTO> coOrganizersFixture = new ArrayList<>();
    public final List<CoOrganizerInvitationDTO> myInvitationsFixture = new ArrayList<>();

    public void reset() {
        forceNotFoundOnInvite = false;
        forceForbiddenOnInvite = false;
        forceConflictOnInvite = false;
        forceCannotInviteSelf = false;
        forceNotFoundOnAccept = false;
        forceNotFoundOnDecline = false;
        forceForbiddenOnRemove = false;
        forceNotFoundOnList = false;
        coOrganizersFixture.clear();
        myInvitationsFixture.clear();
    }

    @Override
    public CoOrganizerDTO invite(Long eventId, String inviterAuth0Id, UUID targetUserId, boolean isAdmin) {
        if (forceNotFoundOnInvite) throw new NotFoundException();
        if (forceForbiddenOnInvite) throw new ForbiddenException();
        if (forceCannotInviteSelf) throw badRequest("cannot_invite_self", "self");
        if (forceConflictOnInvite) throw conflict("already_invited", "dup");
        return new CoOrganizerDTO(1L, targetUserId, "Mocked", null,
                CoOrganizerStatus.PENDING, java.time.LocalDateTime.now());
    }

    @Override
    public CoOrganizerDTO accept(Long eventId, String userAuth0Id) {
        if (forceNotFoundOnAccept) throw new NotFoundException();
        return new CoOrganizerDTO(1L, UUID.randomUUID(), "Mocked", null,
                CoOrganizerStatus.ACCEPTED, java.time.LocalDateTime.now());
    }

    @Override
    public void decline(Long eventId, String userAuth0Id) {
        if (forceNotFoundOnDecline) throw new NotFoundException();
    }

    @Override
    public void remove(Long eventId, String requesterAuth0Id, UUID targetUserId, boolean isAdmin) {
        if (forceForbiddenOnRemove) throw new ForbiddenException();
    }

    @Override
    public List<CoOrganizerDTO> getCoOrganizers(Long eventId) {
        if (forceNotFoundOnList) throw new NotFoundException();
        return List.copyOf(coOrganizersFixture);
    }

    @Override
    public List<CoOrganizerInvitationDTO> getMyInvitations(
            String auth0Id, CoOrganizerStatus status, int page, int size) {
        return List.copyOf(myInvitationsFixture);
    }
}
```

> **Note.** Les helpers `badRequest` et `conflict` sont privés-static dans `EventCoOrganizerService`. Pour le mock, soit (a) les remonter en `protected static`, soit (b) inliner la `WebApplicationException`. Recommandation : (a) — c'est le moindre des deux maux côté lisibilité, et le mock teste exactement la même envelope d'erreur que la prod.

### 7.2 — `EventCoOrganizerResourceTest` (`@QuarkusTest`)

**Fichier :** `backend/src/test/java/ch/unige/events/resource/EventCoOrganizerResourceTest.java`

Tests à couvrir (matrice exhaustive) :

| # | Test | Endpoint | Auth | Setup | HTTP attendu |
|---|---|---|---|---|---|
| 1 | `invite_byCreator_returns201` | `POST /events/1/co-organizers` | `auth0\|alice` | (créateur seedé) | 201, body `status=PENDING` |
| 2 | `invite_byNonCreator_returns403` | `POST /events/1/co-organizers` | `auth0\|bob` | `forceForbiddenOnInvite` | 403 |
| 3 | `invite_eventNotFound_returns404` | `POST /events/999/co-organizers` | `auth0\|alice` | `forceNotFoundOnInvite` | 404 |
| 4 | `invite_self_returns400` | `POST /events/1/co-organizers` | `auth0\|alice` | `forceCannotInviteSelf` | 400, `error=cannot_invite_self` |
| 5 | `invite_alreadyInvited_returns409` | `POST /events/1/co-organizers` | `auth0\|alice` | `forceConflictOnInvite` | 409, `error=already_invited` |
| 6 | `invite_targetUserNotFound_returns404` | `POST /events/1/co-organizers` | `auth0\|alice` | mock 404 | 404 |
| 7 | `invite_unauthenticated_returns401` | `POST /events/1/co-organizers` | (none) | — | 401 |
| 8 | `invite_missingUserId_returns400` | `POST /events/1/co-organizers` body `{}` | `auth0\|alice` | — | 400 (validation @NotNull) |
| 9 | `invite_byAdmin_returns201` | `POST /events/1/co-organizers` | `auth0\|admin` role ADMIN | — | 201 |
| 10 | `accept_pendingInvitation_returns200` | `PATCH /events/1/co-organizers/me/accept` | `auth0\|bob` | — | 200, `status=ACCEPTED` |
| 11 | `accept_noInvitation_returns404` | `PATCH /events/1/co-organizers/me/accept` | `auth0\|carol` | `forceNotFoundOnAccept` | 404 |
| 12 | `accept_unauthenticated_returns401` | — | — | — | 401 |
| 13 | `decline_pendingInvitation_returns204` | `PATCH /events/1/co-organizers/me/decline` | `auth0\|bob` | — | 204 |
| 14 | `decline_noInvitation_returns404` | — | — | `forceNotFoundOnDecline` | 404 |
| 15 | `remove_byCreator_returns204` | `DELETE /events/1/co-organizers/{uuid}` | `auth0\|alice` | — | 204 |
| 16 | `remove_byNonCreator_returns403` | — | `auth0\|bob` | `forceForbiddenOnRemove` | 403 |
| 17 | `remove_byAdmin_returns204` | — | `auth0\|admin` role ADMIN | — | 204 |
| 18 | `remove_idempotentWhenNoInvitation_returns204` | — | `auth0\|alice` | — (no fixture) | 204 |
| 19 | `list_emptyEvent_returnsEmptyArray` | `GET /events/1/co-organizers` | `auth0\|alice` | — | 200, `[]` |
| 20 | `list_withInvitations_returnsArray` | — | `auth0\|alice` | seed 2 dans fixture | 200, taille 2 |
| 21 | `list_unauthenticated_returns401` | — | (none) | — | 401 |
| 22 | `list_eventNotFound_returns404` | — | `auth0\|alice` | `forceNotFoundOnList` | 404 |

Exemple représentatif (pattern AttendanceResourceTest) :

```java
@Test
@TestSecurity(user = "auth0|alice")
void invite_byCreator_returns201() {
    UUID targetUserId = UUID.randomUUID();
    given()
            .contentType(ContentType.JSON)
            .body("{\"userId\":\"" + targetUserId + "\"}")
            .when().post("/events/{id}/co-organizers", 1L)
            .then()
            .statusCode(201)
            .body("status", equalTo("PENDING"))
            .body("userId", equalTo(targetUserId.toString()));
}

@Test
@TestSecurity(user = "auth0|alice")
void invite_self_returns400() {
    EventCoOrganizerServiceMock.forceCannotInviteSelf = true;
    given()
            .contentType(ContentType.JSON)
            .body("{\"userId\":\"" + UUID.randomUUID() + "\"}")
            .when().post("/events/{id}/co-organizers", 1L)
            .then()
            .statusCode(400)
            .body("error", equalTo("cannot_invite_self"));
}
```

### 7.3 — `EventCoOrganizerServiceCoverageTest` (DevServices PostgreSQL)

**Fichier :** `backend/src/test/java/ch/unige/events/service/EventCoOrganizerServiceCoverageTest.java`

Tests d'intégration directe sur DevServices :

| # | Test | Scénario |
|---|---|---|
| 1 | `invite_persistsRowWithStatusPending` | Seed user + event ; `service.invite(eventId, creatorAuth0, targetUserId, false)` ; verify row in DB |
| 2 | `invite_byNonCreator_throwsForbidden` | Idem, mais auth0Id ≠ creator | `assertThrows(ForbiddenException.class)` |
| 3 | `invite_self_throwsBadRequest` | targetUserId = creator.id | `WebApplicationException` 400 + `error=cannot_invite_self` |
| 4 | `invite_alreadyInvited_throwsConflict` | Pré-seed une row ; ré-invitation | `WebApplicationException` 409 + `error=already_invited` |
| 5 | `invite_targetUserNotFound_throws404` | `targetUserId` UUID random non provisionné | `NotFoundException` |
| 6 | `accept_transitionsPendingToAccepted` | Seed PENDING ; `accept` ; lecture DB → `status=ACCEPTED` |
| 7 | `accept_idempotentOnAccepted` | Seed ACCEPTED ; second `accept` ; pas d'exception, status reste ACCEPTED |
| 8 | `accept_noInvitation_throws404` | Aucune row ; `accept` | `NotFoundException` |
| 9 | `decline_deletesRow` | Seed PENDING ; `decline` ; `EventCoOrganizer.findByEventAndUser(...).isEmpty()` |
| 10 | `decline_thenReinvite_works` | `decline` ; `invite` ; nouvelle row PENDING (vérification décision 5) |
| 11 | `remove_byCreator_deletesRow` | Seed PENDING ; `remove(eventId, creatorAuth0, targetUserId, false)` ; row absente |
| 12 | `remove_byAdmin_deletesRow` | Idem, mais `isAdmin=true` ; row absente |
| 13 | `remove_byNonCreator_nonAdmin_throwsForbidden` | `remove` par random user ; `ForbiddenException` |
| 14 | `remove_idempotent_noOp` | Aucune row ; `remove` ; pas d'exception |
| 15 | `getCoOrganizers_returnsRowsOrderedByInvitedAt` | Seed 2 rows ; vérifier l'ordre |
| 16 | `getCoOrganizers_includesPendingAndAccepted` | Seed 1 PENDING + 1 ACCEPTED ; taille = 2 |
| 17 | `getCoOrganizers_eventNotFound_throws404` | eventId inexistant ; `NotFoundException` |
| 18 | `getMyInvitations_defaultsToPending` | Seed 1 PENDING + 1 ACCEPTED pour user A ; `getMyInvitations(authA, null, 0, 20)` ; taille 1 (PENDING) |
| 19 | `getMyInvitations_filterAccepted_returnsAccepted` | Idem, status=ACCEPTED ; taille 1 |
| 20 | `getMyInvitations_includesEventDTO` | Vérifier `result.get(0).event().title()` non null |
| 21 | `getMyInvitations_pagination` | Seed 5 PENDING ; size=2 → taille 2 |
| 22 | `getMyInvitations_userNotProvisioned_throws404` | auth0Id inexistant ; `NotFoundException` |

### 7.4 — Tests de cascade (`EventServiceCoverageTest`, `AttendanceServiceCoverageTest`, `EventStatsServiceCoverageTest`)

Ajouter dans chaque fichier les tests qui exercent la cascade `isCreatorOrAcceptedCoOrganizer` :

| Fichier | Test | Scénario |
|---|---|---|
| `EventServiceCoverageTest` | `update_byAcceptedCoOrganizer_succeeds` | Seed event créateur A + co-org B ACCEPTED ; B appelle update → succès |
| | `update_byPendingCoOrganizer_throws403` | Co-org B PENDING ; update → ForbiddenException |
| | `update_byDeclinedCoOrganizer_throws403` | (impossible — DECLINED supprime la row, donc B sans row) | identique à `byNonCreator_throws403` |
| | `cancel_byAcceptedCoOrganizer_succeeds` | Idem update sur cancel |
| | `restore_byAcceptedCoOrganizer_succeeds` | Idem |
| | `publish_byAcceptedCoOrganizer_succeeds` | Idem (sur DRAFT valide) |
| | `uploadImage_byAcceptedCoOrganizer_succeeds` | Idem |
| | **`delete_byAcceptedCoOrganizer_throws403`** | Sentinel de la décision 2 — co-org ACCEPTED ne peut PAS hard-delete |
| | `getById_draftByAcceptedCoOrganizer_returns200` | DRAFT visible pour co-org ACCEPTED |
| `AttendanceServiceCoverageTest` | `getAttendees_byAcceptedCoOrganizer_succeeds` | Co-org ACCEPTED voit la liste |
| | `getAttendees_byNonCoOrganizer_throws403` | Random user ne voit pas |
| `EventStatsServiceCoverageTest` | `getStats_byAcceptedCoOrganizer_succeeds` | Co-org ACCEPTED voit les stats |
| | `getStats_byPendingCoOrganizer_throws403` | Co-org PENDING ne voit pas |

### 7.5 — Test unitaire des DTOs

**Fichier :** `backend/src/test/java/ch/unige/events/dto/coorganizer/CoOrganizerDTOTest.java`

| Test | Scénario |
|---|---|
| `from_withUser_projectsAllFields` | `User` non null ; tous les champs renseignés correctement |
| `from_withNullUser_setsDisplayNameAndAvatarToNull` | `User` null ; `displayName` et `avatarUrl` à null |
| `from_invitation_includesEvent` | `CoOrganizerInvitationDTO.from` projette l'EventDTO complet |

### 7.6 — Test entité `EventCoOrganizer`

**Fichier :** `backend/src/test/java/ch/unige/events/entity/EventCoOrganizerTest.java`

| Test | Scénario |
|---|---|
| `prePersist_setsInvitedAt` | `entity.persist()` ; vérifier `invitedAt != null` |
| `isAcceptedFor_acceptedRow_returnsTrue` | Seed ACCEPTED ; helper retourne true |
| `isAcceptedFor_pendingRow_returnsFalse` | Seed PENDING ; helper retourne false |
| `isAcceptedFor_noRow_returnsFalse` | aucune row ; false |
| `uniqueConstraint_blocksDuplicateInvitation` | Seed 2 rows même (eventId, userId) ; deuxième `persist` lève `PersistenceException` |

### 7.7 — `UserResourceTest` — ajout pour `/me/co-organizer-invitations`

À ajouter dans le fichier existant [`UserResourceTest.java`](backend/src/test/java/ch/unige/events/resource/UserResourceTest.java) :

| Test | Scénario | HTTP |
|---|---|---|
| `getMyCoOrganizerInvitations_default_returnsPending` | Seed 1 PENDING ; appel sans param | 200, taille 1, `status=PENDING` |
| `getMyCoOrganizerInvitations_filterAccepted_returnsAccepted` | Seed 1 ACCEPTED ; `?status=ACCEPTED` | 200, taille 1 |
| `getMyCoOrganizerInvitations_pagination` | Seed 3 ; `?size=2` | 200, taille 2 |
| `getMyCoOrganizerInvitations_unauthenticated_returns401` | — | 401 |
| `getMyCoOrganizerInvitations_userNotProvisioned_returns404` | mock NotFound | 404 |

---

## Étape 8 — Documentation

### 8.1 — `backend/docs/data-model.md`

**À ajouter (après la section `EventView`, avant la section « Conventions de nommage »)** :

```markdown
### EventCoOrganizer

Table : `event_co_organizers`

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `eventId` | — | `Long` | `event_id` | not null |
| `userId` | `userId` | `UUID` | `user_id` | not null |
| `status` | `status` | `CoOrganizerStatus` | `status` | not null, `@Enumerated(STRING)` |
| `invitedAt` | `invitedAt` | `LocalDateTime` | `invited_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Contrainte unique : `uq_event_co_organizers_event_user` sur `(event_id, user_id)`.
Index : `idx_event_co_organizers_event` (`event_id`), `idx_event_co_organizers_user` (`user_id`).

Suppression physique autorisée (pas de soft-delete) — symétrique à `Favorite`.

#### Sémantique du `DECLINE`

`PATCH /events/{id}/co-organizers/me/decline` **supprime physiquement** la row au lieu de la marquer
`DECLINED`. La valeur `DECLINED` reste définie dans l'enum mais n'apparaît jamais en base.
Cette décision permet au créateur de ré-inviter la même personne après un refus, sans 409
(la contrainte unique étant strictement basée sur la présence d'une row, pas sur son statut).

#### Helpers statiques

- `EventCoOrganizer.isAcceptedFor(Long eventId, UUID userId)` — réponse boolean en
  une seule requête `count`. Utilisé par `EventService.isCreatorOrAcceptedCoOrganizer`.
- `EventCoOrganizer.findByEventAndUser(Long, UUID)` — résolution unitaire pour accept/decline/remove.
- `EventCoOrganizer.findByEvent(Long eventId)` — listing par event, tri `invitedAt ASC`.
- `EventCoOrganizer.findByUser(UUID userId, CoOrganizerStatus status, int page, int size)` — listing
  par user filtré sur un statut, paginé, tri `invitedAt DESC`.

#### Permissions « créateur ou co-organisateur ACCEPTED »

Depuis SCRUM-136, le helper privé `EventService.isCreatorOrAcceptedCoOrganizer(Event, String)`
unifie la garde d'autorisation pour les opérations de gestion d'événement déléguables :
`update`, `cancel`, `restore`, `publish`, `uploadImage`, `getById` (visibilité DRAFT/CANCELLED),
`AttendanceService.getAttendees`, `EventStatsService.getStats`.

`EventService.delete` (suppression physique d'un event CANCELLED) reste **strict-creator** —
non délégable aux co-organisateurs (action irréversible, hors scope du « partage de gestion »).
```

Ajouter aussi sous la section « Énumérations » :

```markdown
| `CoOrganizerStatus` | `PENDING`, `ACCEPTED`, `DECLINED` | Sprint 7 | ✅ Implémenté (SCRUM-136 — `DECLINED` est transitoire et n'apparaît jamais en base, cf. ci-dessus) |
```

Ajouter une **note** dans la section « Réconciliation des contraintes CHECK — `SchemaFixup` » :

```markdown
> **À surveiller pour `event_co_organizers_status_check`.** Hibernate pose la contrainte
> `event_co_organizers_status_check` à la création initiale de la table en S7 (création
> ex nihilo). Toute future modification du `CoOrganizerStatus` (ajout d'une valeur, rename)
> exigera l'ajout d'un bloc `RECREATE_EVENT_CO_ORGANIZERS_STATUS_CHECK` dans
> `SchemaFixup` à ce moment-là — Hibernate `update` ne reconcilie jamais les CHECK
> rétroactivement.
```

### 8.2 — `backend/docs/api-contract.md`

Ajouter au tableau « Endpoints implémentés » :

```markdown
| `POST` | `/events/{id}/co-organizers` | `@Authenticated` | Inviter un co-organisateur (créateur ou ADMIN) | 201, 400, 401, 403, 404, 409 |
| `GET` | `/events/{id}/co-organizers` | `@Authenticated` | Lister les co-organisateurs (PENDING + ACCEPTED) | 200, 401, 404 |
| `DELETE` | `/events/{id}/co-organizers/{userId}` | `@Authenticated` | Retirer un co-organisateur (créateur ou ADMIN) | 204, 401, 403, 404 |
| `PATCH` | `/events/{id}/co-organizers/me/accept` | `@Authenticated` | Accepter sa propre invitation | 200, 401, 404 |
| `PATCH` | `/events/{id}/co-organizers/me/decline` | `@Authenticated` | Décliner sa propre invitation (suppression de la row) | 204, 401, 404 |
| `GET` | `/users/me/co-organizer-invitations` | `@Authenticated` | Mes invitations (default `status=PENDING`) | 200, 401, 404 |
```

Ajouter une section dédiée plus bas (après la section `GET /users/me/events`) avec le détail de chaque endpoint, les codes d'erreur, et la note sur la cascade `isCreatorOrAcceptedCoOrganizer`.

### 8.3 — `backend/docs/sprint-context.md`

Ajouter dans la section Sprint 7 :

```markdown
- [x] **SCRUM-136** — Co-organisateurs : entité `EventCoOrganizer` (eventId, userId,
  status PENDING/ACCEPTED/DECLINED, invitedAt, unique (event_id, user_id)) + 6 endpoints
  REST (`POST/GET /events/{id}/co-organizers`, `DELETE /events/{id}/co-organizers/{userId}`,
  `PATCH /events/{id}/co-organizers/me/accept|decline`, `GET /users/me/co-organizer-invitations`).
  Cascade d'autorisation `isCreatorOrAcceptedCoOrganizer` sur `EventService.update/cancel/
  restore/publish/uploadImage/getById`, `AttendanceService.getAttendees`,
  `EventStatsService.getStats`. `EventService.delete` reste strict-creator (action
  irréversible). DECLINE supprime physiquement la row pour autoriser la ré-invitation.
  Hors scope : notifications email, transfert d'ownership, invitation par email.
  Frontend SCRUM-137 dépendant.
```

---

## Edge cases à traiter explicitement

| Cas | Comportement attendu | Couvert par |
|---|---|---|
| Inviter un user déjà invité (PENDING ou ACCEPTED) | 409 `already_invited` | `invite_alreadyInvited_returns409` |
| Inviter un user dont l'invitation précédente a été DECLINED | 201 PENDING (la row précédente a été supprimée) | `decline_thenReinvite_works` |
| Créateur s'auto-invite | 400 `cannot_invite_self` | `invite_self_returns400` |
| Créateur s'auto-invite via `isAdmin=true` | 400 `cannot_invite_self` (la garde est faite avant le check ADMIN) | (test à ajouter dans coverage) |
| Admin invite à la place du créateur | 201 PENDING | `invite_byAdmin_returns201` |
| User non provisionné (auth0Id sans User en base) appelle `/me/accept` | 404 `not_found` | `accept_userNotProvisioned_returns404` |
| User non provisionné appelle `/me/co-organizer-invitations` | 404 | `getMyCoOrganizerInvitations_userNotProvisioned_returns404` |
| `accept` appelé alors que la row est déjà ACCEPTED | 200 idempotent (renvoie l'état) | `accept_idempotentOnAccepted` |
| `decline` appelé alors qu'aucune row n'existe | 404 | `decline_noInvitation_returns404` |
| `remove` sur un userId qui n'a aucune row | 204 idempotent (pas d'erreur — alignement sur DELETE /favorite) | `remove_idempotent_noOp` |
| `remove` sur un eventId inexistant | 404 (l'event lui-même n'existe pas) | `remove_eventNotFound_returns404` |
| `getCoOrganizers` sur un event sans co-organisateurs | 200 `[]` (jamais 404 si l'event existe) | `list_emptyEvent_returnsEmptyArray` |
| `getCoOrganizers` sur un event inexistant | 404 | `list_eventNotFound_returns404` |
| Invitation sur un event CANCELLED | Autorisé (le créateur peut composer son équipe même sur un event annulé — pas de blocage applicatif). À surveiller en review produit. | (pas de garde explicite — comportement par défaut) |
| Co-org ACCEPTED tente `delete` (hard) | 403 `forbidden` (cascade volontairement non appliquée) | `delete_byAcceptedCoOrganizer_throws403` |
| Co-org ACCEPTED tente `update` | 200 (cascade appliquée) | `update_byAcceptedCoOrganizer_succeeds` |
| Co-org PENDING tente `update` | 403 (PENDING n'a pas les permissions) | `update_byPendingCoOrganizer_throws403` |
| Frontend appelle `/me/co-organizer-invitations` sans `?status=` | Renvoie PENDING par défaut | `getMyCoOrganizerInvitations_default_returnsPending` |
| Frontend passe `?status=DECLINED` | Renvoie `[]` (aucune row DECLINED en base) | (à ajouter en test coverage) |
| Concurrence : deux invitations simultanées sur le même couple | La 2e échoue avec `PersistenceException` → 409 (mappé par `ConflictExceptionMapper` ou via `findByEventAndUser` existant) | À surveiller — le check `isPresent()` n'est pas atomique. Cf. `AttendanceService.attend` qui prend un PESSIMISTIC_WRITE. **Recommandation : laisser tel quel pour S7 ; PR future si nécessaire.** |

---

## Critères d'acceptation (repris du ticket Jira SCRUM-136)

D'après le backlog [`backlog_s5_s10.md` lignes 615-647](backend/docs/backlog_s5_s10.md#L615-L647) :

- [ ] **Entité `EventCoOrganizer`** (PanacheEntity) avec `eventId`, `userId`, `status`, `invitedAt`, contrainte unique `(eventId, userId)` → étape 1.2.
- [ ] **Enum `CoOrganizerStatus`** (PENDING / ACCEPTED / DECLINED) → étape 1.1.
- [ ] **`EventCoOrganizerService`** : `invite`, `accept`, `decline`, `remove`, `getCoOrganizers` → étape 3.
- [ ] **`EventCoOrganizerResource`** (`@Path("/events/{id}/co-organizers")`) : POST /, DELETE /{userId}, GET /, PATCH /me/accept, PATCH /me/decline → étape 5.
- [ ] **`UserResource`** : ajout `GET /api/users/me/co-organizer-invitations` → étape 6.
- [ ] **`EventService`** : modification de `update()`, `delete()`, `publish()` pour accepter les co-organisateurs ACCEPTED → étape 4 (avec **divergence assumée** sur `delete()` qui reste strict-creator, cf. décision 2).
- [ ] **OpenAPI** : tous les endpoints + `CoOrganizerDTO` → étape 0.
- [ ] **Tests** : invitation → 201, double invitation → 409, acceptation → ACCEPTED → étape 7.

---

## Conventions du projet à respecter

- **Règle d'or `openapi-first`** : `openapi/openapi.yaml` modifié EN PREMIER (étape 0) avant toute ligne Java.
- **camelCase partout** dans le code Java, les noms JSON, les schémas OpenAPI. Hibernate convertit en `snake_case` côté DB via `CamelCaseToUnderscoresNamingStrategy` (donc `event_co_organizers` est généré automatiquement à partir de `EventCoOrganizer`).
- **Pas de préfixe `is`** sur les booléens d'entité — non applicable ici (aucun champ booléen sur `EventCoOrganizer`).
- **Hibernate update mode** — pas de migration SQL, la nouvelle table est créée automatiquement au démarrage.
- **Architecture en couches stricte** — Resource ne touche pas l'entité directement. Logique métier dans `EventCoOrganizerService` uniquement.
- **Constructor injection** sur la nouvelle Resource (pattern [`EventResource`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L34-L41)). Field injection conservée sur `UserResource` (pattern existant — pas de refactor opportuniste).
- **Doc mise à jour dans le même commit** que le code correspondant (règle [`AGENTS.md`](backend/AGENTS.md#L98)).
- **Commits atomiques** : `feat(scrum-136): ...`, `test(scrum-136): ...`, `docs(scrum-136): ...`. Combinables si le diff est petit.
- **SonarCloud** : ≥ 80 % couverture sur les lignes nouvelles, ≤ 3 % duplication, ratings A.
- **Préfixe Jira obligatoire** dans le titre de commit pour `feat`/`refactor`/`perf` (validé par [`.github/workflows/pr-title-check.yml`](.github/workflows/pr-title-check.yml)).

---

## Interdits stricts

- ❌ **Ne PAS** modifier `Event.creator` ni introduire un champ `coOrganizers` sur `Event`.
- ❌ **Ne PAS** modifier l'helper privé `EventService.isCreator` — créer un nouvel helper.
- ❌ **Ne PAS** étendre la cascade à `EventService.delete` (suppression physique reste strict-creator).
- ❌ **Ne PAS** persister un `CoOrganizerStatus.DECLINED` en base (le `decline()` supprime la row).
- ❌ **Ne PAS** envoyer d'email, créer une entité `Notification`, ni publier un event Quarkus.
- ❌ **Ne PAS** ajouter `event_co_organizers_status_check` à `SchemaFixup` dans cette PR (création ex nihilo, cf. décision 14).
- ❌ **Ne PAS** étendre `CoOrganizerDTO` avec `email` ou `bio` — privacy.
- ❌ **Ne PAS** introduire un `@ManyToOne(Event)` ni un `@ManyToOne(User)` sur `EventCoOrganizer` — pattern Long+UUID comme `Attendance`/`Favorite`.
- ❌ **Ne PAS** créer un endpoint d'invitation par email.
- ❌ **Ne PAS** créer un endpoint « bulk invite » (1 endpoint = 1 invitation).
- ❌ **Ne PAS** modifier le frontend dans cette PR (SCRUM-137 le fait dans une PR séparée).
- ❌ **Ne PAS** générer un script SQL de création de table — Hibernate s'en charge.
- ❌ **Ne PAS** introduire un cache (TanStack-Query côté front, Quarkus Cache côté back) — out of scope.
- ❌ **Ne PAS** poser un verrou pessimiste sur `Event` au moment de l'`invite` — le check `findByEventAndUser` + `persist` peut tolérer une 409 résiduelle (cas concurrence rare, cf. dernier edge case).
- ❌ **Ne PAS** casser un test existant — la cascade ne doit pas changer le comportement strict-creator pour les anciens scénarios (run `./mvnw verify` après chaque étape).
- ❌ **Ne PAS** logger l'`auth0Id` ni le `userId` en clair dans des logs INFO — utiliser DEBUG si besoin.
- ❌ **Ne PAS** introduire de TODO commenté.

---

## Résumé des fichiers touchés

| Fichier | Action |
|---|---|
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | Modifier — +6 paths, +3 schemas (`CoOrganizer`, `CoOrganizerInvitation`, `InviteCoOrganizerRequest`), +1 enum (`CoOrganizerStatus`), descriptions de 9 endpoints existants enrichies |
| `backend/src/main/java/ch/unige/events/entity/CoOrganizerStatus.java` | **Créer** |
| `backend/src/main/java/ch/unige/events/entity/EventCoOrganizer.java` | **Créer** |
| `backend/src/main/java/ch/unige/events/dto/coorganizer/CoOrganizerDTO.java` | **Créer** |
| `backend/src/main/java/ch/unige/events/dto/coorganizer/CoOrganizerInvitationDTO.java` | **Créer** |
| `backend/src/main/java/ch/unige/events/dto/coorganizer/InviteCoOrganizerRequest.java` | **Créer** |
| `backend/src/main/java/ch/unige/events/service/EventCoOrganizerService.java` | **Créer** |
| `backend/src/main/java/ch/unige/events/resource/EventCoOrganizerResource.java` | **Créer** |
| [`backend/src/main/java/ch/unige/events/service/EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | Modifier — ajout `isCreatorOrAcceptedCoOrganizer` + `isCreatorOrAcceptedCoOrganizerPublic` + `findByIdsAsDTO`, migration des 6 call-sites (update/cancel/restore/publish/uploadImage/getById) |
| [`backend/src/main/java/ch/unige/events/service/AttendanceService.java`](backend/src/main/java/ch/unige/events/service/AttendanceService.java) | Modifier — `getAttendees` migre vers `isCreatorOrAcceptedCoOrganizerPublic` |
| [`backend/src/main/java/ch/unige/events/service/EventStatsService.java`](backend/src/main/java/ch/unige/events/service/EventStatsService.java) | Modifier — `getStats` migre vers `isCreatorOrAcceptedCoOrganizerPublic` |
| [`backend/src/main/java/ch/unige/events/resource/UserResource.java`](backend/src/main/java/ch/unige/events/resource/UserResource.java) | Modifier — injection `EventCoOrganizerService` + endpoint `GET /me/co-organizer-invitations` |
| `backend/src/test/java/ch/unige/events/entity/EventCoOrganizerTest.java` | **Créer** — 5 tests entité |
| `backend/src/test/java/ch/unige/events/dto/coorganizer/CoOrganizerDTOTest.java` | **Créer** — 3 tests DTO |
| `backend/src/test/java/ch/unige/events/service/EventCoOrganizerServiceMock.java` | **Créer** — mock du service pour les tests Resource |
| `backend/src/test/java/ch/unige/events/service/EventCoOrganizerServiceCoverageTest.java` | **Créer** — 22 tests d'intégration DevServices |
| `backend/src/test/java/ch/unige/events/resource/EventCoOrganizerResourceTest.java` | **Créer** — 22 tests `@QuarkusTest` |
| [`backend/src/test/java/ch/unige/events/resource/UserResourceTest.java`](backend/src/test/java/ch/unige/events/resource/UserResourceTest.java) | Modifier — +5 tests `getMyCoOrganizerInvitations_*` |
| [`backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java) | Modifier — +9 tests cascade (update/cancel/restore/publish/uploadImage/delete-strict/getById par co-org) |
| [`backend/src/test/java/ch/unige/events/service/AttendanceServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/AttendanceServiceCoverageTest.java) | Modifier — +2 tests cascade `getAttendees` |
| [`backend/src/test/java/ch/unige/events/service/EventStatsServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/EventStatsServiceCoverageTest.java) | Modifier — +2 tests cascade `getStats` |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Modifier — entité `EventCoOrganizer` + enum + section permissions + note `SchemaFixup` |
| [`backend/docs/api-contract.md`](backend/docs/api-contract.md) | Modifier — 6 nouveaux endpoints + section dédiée |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Modifier — entrée Sprint 7 SCRUM-136 |

**Total :** 8 fichiers créés + 11 modifiés (dont 4 docs). **0 fichier de migration SQL**, **0 fichier frontend**.

---

## Branche et PR

### Branche

`feature/s6-co-organizers`, basée sur `origin/main` :

```bash
git fetch origin
git checkout -b feature/s6-co-organizers origin/main --no-track
```

⚠️ **`--no-track` est OBLIGATOIRE.** Sans ce flag, la branche traque `origin/main` et `git push` envoie les commits directement sur main (incident documenté sur ISSUE-92, cf. commit de revert `9c2e6d4` sur main). Le `-u` viendra au premier push pour configurer le bon upstream :

```bash
git push -u origin feature/s6-co-organizers
```

### PR

- **Base :** `main`.
- **Titre :** `feat(scrum-136): add EventCoOrganizer entity and invitation endpoints`
  - `feat` impose un scope `scrum-136` en minuscules — validé par [`.github/workflows/pr-title-check.yml`](.github/workflows/pr-title-check.yml).
- **Description** (calquée sur [`.github/pull_request_template.md`](.github/pull_request_template.md)) :

  ```markdown
  ## Résumé

  **SCRUM-136** — Implémentation backend de US-29 (« partager la gestion d'un événement
  avec un ou plusieurs co-organisateurs »). Nouvelle entité `EventCoOrganizer`,
  enum `CoOrganizerStatus`, service + resource + 6 endpoints REST, cascade
  d'autorisation sur 6 méthodes existantes de `EventService` + `getAttendees` +
  `getStats`. Aucune notification, aucune migration SQL. Frontend (SCRUM-137)
  dépendant — PR séparée.

  ## Why / Motivation

  Aujourd'hui un événement est strictement mono-créateur. Toute opération de gestion
  (édition, publication, banner, vue des inscrits, stats) passe par `isCreator` privé
  dans `EventService` — bloquant pour les associations universitaires qui co-pilotent
  un event à plusieurs. SCRUM-136 livre le socle backend ; SCRUM-137 livrera l'UI.

  ## Changements

  ### Backend

  - **Entité** `EventCoOrganizer` (PanacheEntity, contrainte unique `(event_id, user_id)`,
    indexes sur `event_id` et `user_id`).
  - **Enum** `CoOrganizerStatus` (`PENDING`, `ACCEPTED`, `DECLINED` — DECLINED transitoire,
    jamais persistée).
  - **Service** `EventCoOrganizerService` : `invite`, `accept`, `decline`, `remove`,
    `getCoOrganizers`, `getMyInvitations`.
  - **Resource** `EventCoOrganizerResource` sous `/events/{id}/co-organizers` :
    POST, GET, DELETE /{userId}, PATCH /me/accept, PATCH /me/decline.
  - **`UserResource`** : nouvel endpoint `GET /me/co-organizer-invitations` (default
    `status=PENDING`, paginé).
  - **DTOs** `CoOrganizerDTO`, `CoOrganizerInvitationDTO`, `InviteCoOrganizerRequest`.
  - **Cascade `EventService.isCreatorOrAcceptedCoOrganizer`** sur :
    `update`, `cancel`, `restore`, `publish`, `uploadImage`, `getById`,
    `AttendanceService.getAttendees`, `EventStatsService.getStats`.
    `EventService.delete` reste strict-creator (action irréversible — divergence
    assumée par rapport au libellé du ticket Jira).

  ### Documentation

  - `backend/docs/data-model.md` : entité `EventCoOrganizer`, enum, section permissions,
    note `SchemaFixup` future.
  - `backend/docs/api-contract.md` : 6 nouveaux endpoints + section dédiée.
  - `backend/docs/sprint-context.md` : entrée Sprint 7 SCRUM-136.
  - `openapi/openapi.yaml` (source de vérité monorepo) : 6 paths, 3 schémas, 1 enum.

  ## Tests

  - `./mvnw verify` vert.
  - `EventCoOrganizerServiceCoverageTest` : 22 tests intégration DevServices PostgreSQL.
  - `EventCoOrganizerResourceTest` : 22 tests `@QuarkusTest`.
  - `EventCoOrganizerTest` : 5 tests entité (PrePersist, isAcceptedFor, contrainte unique).
  - `CoOrganizerDTOTest` : 3 tests DTO.
  - Cascade : 9 nouveaux tests dans `EventServiceCoverageTest` + 2 dans
    `AttendanceServiceCoverageTest` + 2 dans `EventStatsServiceCoverageTest`.
  - `UserResourceTest` : +5 tests pour `/me/co-organizer-invitations`.
  - Couverture JaCoCo cible ≥ 80 % sur les lignes nouvelles ; les classes Service
    et Resource visent 100 %.

  ## Test plan

  - [ ] `./mvnw verify` localement vert.
  - [ ] Couverture JaCoCo ≥ 80 % sur `EventCoOrganizerService` et `EventCoOrganizerResource`.
  - [ ] Le test `delete_byAcceptedCoOrganizer_throws403` est rouge si on étend la
    cascade à `delete` par erreur (sentinel anti-régression).
  - [ ] Le test `decline_thenReinvite_works` est rouge si la décision 5 est
    mal implémentée (DECLINE doit supprimer la row).
  - [ ] Le test `invite_self_returns400` retourne `error=cannot_invite_self`.
  - [ ] `git diff --stat frontend/` vide.

  ## Documentation

  - [x] `backend/docs/data-model.md` mis à jour.
  - [x] `backend/docs/api-contract.md` mis à jour.
  - [x] `backend/docs/sprint-context.md` mis à jour.
  - [x] `openapi/openapi.yaml` mis à jour (source de vérité monorepo).

  ## Dépendances / ordre de merge

  - **Aucune dépendance bloquante** — tickets indépendants au sprint 7 (SCRUM-94, SCRUM-95
    n'impactent pas les fichiers touchés).
  - **SCRUM-137** (FRONT co-organisateurs) dépend de cette PR — à merger avant que la
    PR frontend ne s'ouvre.
  - Conflit potentiel léger sur `EventService.java` si une autre PR S7 modifie un
    des call-sites migrés. À surveiller.

  ## Décisions techniques tranchées

  - `delete()` reste strict-creator (divergence assumée vs libellé Jira) — action
    irréversible, hors scope « partage de gestion ».
  - DECLINE supprime la row (autorise ré-invitation après refus, sans 409).
  - Helper dédié `isCreatorOrAcceptedCoOrganizer` (ne pas muter `isCreator`).
  - Pas de notification email, pas de transfert d'ownership, pas d'invitation par
    email (UUID uniquement).
  - Pas de gating de visibilité sur `GET /events/{id}/co-organizers` au-delà de
    `@Authenticated` (PENDING status n'est pas un secret).
  - Pas d'ajout à `SchemaFixup` (table créée ex nihilo) — note posée pour les futures
    modifications de l'enum.

  ## Notes pour le reviewer

  - L'extension de la cascade à `cancel/restore/uploadImage/getById/getAttendees/getStats`
    va au-delà du libellé strict du ticket. Si l'équipe préfère un périmètre plus
    serré (uniquement update/publish), me le signaler — le diff est mécaniquement
    réversible.
  - Le helper `isCreatorOrAcceptedCoOrganizerPublic` est nommé avec le suffixe `Public`
    pour distinguer du privé même-classe. À ouvrir une issue si l'équipe préfère un
    nom alternatif (ex. `canManageEvent(Event, String)`).
  ```

### Commits atomiques suggérés

- `feat(scrum-136): add EventCoOrganizer entity and CoOrganizerStatus enum`
- `feat(scrum-136): add EventCoOrganizerService with invite / accept / decline / remove`
- `feat(scrum-136): add EventCoOrganizerResource and /me/co-organizer-invitations endpoint`
- `refactor(scrum-136): extend creator-or-co-organizer cascade on EventService and friends`
- `test(scrum-136): cover entity, DTOs, service and resource for co-organizers`
- `test(scrum-136): cover cascade authorization on update/publish/getAttendees/getStats`
- `docs(scrum-136): document EventCoOrganizer entity, endpoints and permissions`

Combinables si le diff total reste sous ~600 lignes — à juger.

---

## Checklist Sonar / qualité

- [ ] Coverage ≥ 80 % sur les lignes nouvelles (JaCoCo). Cible attendue : ≥ 95 % sur les classes Service et Resource (faible complexité cyclomatique).
- [ ] Duplication < 3 % sur le code nouveau (le helper `isCreator` est inliné dans `EventCoOrganizerService` une seule fois — si SonarCloud flag, refactorer en helper protected partagé).
- [ ] **Security Rating : A.** Aucun input utilisateur ne touche du SQL natif. Validation `@NotNull` sur le body, `@Min`/`@Max`/`@Positive` sur les query params, `@PathParam UUID` parsé par JAX-RS.
- [ ] Reliability Rating : A.
- [ ] Maintainability Rating : A.
- [ ] Security Review Rating : A.

---

## Checklist finale

### Avant push

- [ ] `./mvnw verify` vert localement.
- [ ] Rapport JaCoCo `backend/target/jacoco-report/` — lignes nouvelles ≥ 80 %.
- [ ] Les **8 tests sentinels** verts nommément (run ciblé) :
  - `invite_byCreator_returns201`
  - `invite_self_returns400` (sentinel décision 6)
  - `invite_alreadyInvited_returns409`
  - `decline_thenReinvite_works` (sentinel décision 5)
  - `accept_idempotentOnAccepted`
  - `delete_byAcceptedCoOrganizer_throws403` (sentinel décision 2 — `delete` strict-creator)
  - `update_byAcceptedCoOrganizer_succeeds` (sentinel cascade)
  - `getMyCoOrganizerInvitations_default_returnsPending`
- [ ] `git diff --stat frontend/` vide.
- [ ] `openapi/openapi.yaml` modifié (vérifier `git diff openapi/`).
- [ ] Aucune nouvelle dépendance dans `backend/pom.xml`.
- [ ] Pas de TODO commenté ajouté.
- [ ] Pas de `LOG.info` qui logue auth0Id ou userId en clair.

### Avant PR

- [ ] Branche `feature/s6-co-organizers` créée avec `--no-track` depuis `origin/main`.
- [ ] `git branch -vv` confirme que la branche track `origin/feature/s6-co-organizers` après le premier push (PAS `origin/main`).
- [ ] Commits atomiques nommés selon la convention (`feat(scrum-136): ...`, `test(scrum-136): ...`, `docs(scrum-136): ...`).
- [ ] Description de PR remplie selon le template, sections optionnelles « Why / Motivation », « Dépendances / ordre de merge », « Décisions techniques tranchées », « Notes pour le reviewer » conservées.
- [ ] Base de la PR : `main`.
- [ ] La check CI `Lint PR title` est verte.

### Avant merge

- [ ] CI verte (`./mvnw verify` côté backend).
- [ ] Review approuvée.
- [ ] SonarCloud quality gate vert.
- [ ] Lien posé dans le ticket Jira SCRUM-136.
- [ ] L'auteur de SCRUM-137 (frontend) confirme que les schemas OpenAPI sont consommables.

---

## Prompt de lancement d'implémentation

````
Tu vas implémenter SCRUM-136 — entité EventCoOrganizer et endpoints d'invitation co-organisateurs sur le backend Quarkus de UNIGE Events.

## ÉTAPE 0 — Création de la branche (avec --no-track OBLIGATOIRE)

Avant TOUT code :

    git fetch origin
    git checkout -b feature/s6-co-organizers origin/main --no-track

Le flag `--no-track` est CRITIQUE. Sans lui, la branche traque `origin/main` et `git push` envoie les commits sur main (incident documenté, cf. commit de revert 9c2e6d4 sur main, repris par toutes les specs récentes du repo). Le `-u` viendra au premier push pour set-up le bon upstream.

## Source unique de vérité

`specs_archives/specs_claude/specs_scrum-136.md` — à lire INTÉGRALEMENT avant d'écrire une ligne de code. Toutes les décisions (cascade qui migre / qui reste strict-creator, DECLINE = DELETE row, helper dédié pas mutation d'isCreator, pattern Long+UUID pas @ManyToOne, pas de notif, pas de soft-delete, payload minimaliste pour le DTO, admin peut inviter à la place du créateur, accept/decline self-only, PATCH plutôt que POST sur les actions, pagination & default PENDING sur /me/co-organizer-invitations, pas d'invitation par email, pas d'extension à SchemaFixup) y sont tranchées. Tu n'as RIEN à inventer.

## À lire avant de commencer

1. `backend/AGENTS.md` — conventions critiques (camelCase, pas de préfixe is, pas de Flyway, openapi-first, constructor injection sur les Resources, seuil Sonar 80%, doc mise à jour dans le même commit, conventions de PR).
2. `backend/docs/architecture.md` — architecture en couches Resource → Service → Entity (jamais de saut de couche).
3. `backend/docs/data-model.md` — pattern existant des entités (Event, User, Attendance, Favorite). C'est ici que la nouvelle entité EventCoOrganizer sera documentée.
4. `backend/docs/api-contract.md` — pattern de documentation des endpoints (à enrichir).
5. `backend/docs/sprint-context.md` — section Sprint 7 (vers ligne 164) où une entrée SCRUM-136 sera ajoutée.
6. `backend/docs/dev-guide.md` — workflow d'ajout d'endpoint spec-first.
7. `openapi/openapi.yaml` — contrat API actuel (2269 lignes). Schemas actuels en haut, paths sous `paths:` ligne 671. C'est le PREMIER fichier à modifier (étape 0 de la spec).
8. Code source à inspecter avant de coder :
   - `backend/src/main/java/ch/unige/events/entity/Attendance.java` — modèle direct pour EventCoOrganizer (PanacheEntity, contrainte unique, @PrePersist, helper countGroupedByStatus à inspirer pour findByEventAndUser etc.).
   - `backend/src/main/java/ch/unige/events/entity/User.java` — id UUID, helper findByAuth0Id, hérite de PanacheEntityBase.
   - `backend/src/main/java/ch/unige/events/entity/Event.java` — relation @ManyToOne LAZY creator (immuable).
   - `backend/src/main/java/ch/unige/events/service/EventService.java` — TOUS les call-sites d'isCreator (lignes 141, 154, 189, 205, 223, 247, 297) à inspecter individuellement avant de migrer. La méthode delete() ligne 189 ne migre PAS.
   - `backend/src/main/java/ch/unige/events/service/AttendanceService.java` — getAttendees ligne 143 (à migrer).
   - `backend/src/main/java/ch/unige/events/service/EventStatsService.java` — getStats ligne 26 (à migrer).
   - `backend/src/main/java/ch/unige/events/resource/EventResource.java` — pattern constructor DI + @Authenticated/@PermitAll + identity.hasRole("ADMIN").
   - `backend/src/main/java/ch/unige/events/resource/UserResource.java` — pattern field DI + extension avec un nouveau /me/* endpoint.
   - `backend/src/main/java/ch/unige/events/resource/AttendanceResource.java` — pattern @Path("/events") + sous-paths /{id}/attend et /{id}/attendees.
   - `backend/src/main/java/ch/unige/events/dto/event/EventDTO.java` — pattern record + factory from(...).
   - `backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java` — pattern projection privacy-aware (fromAnonymous).
   - `backend/src/main/java/ch/unige/events/dto/ApiErrorResponse.java` — record { error, message } pour les codes d'erreur custom.
   - `backend/src/test/java/ch/unige/events/MockEventFactory.java` — factory d'Event in-memory pour les mocks.
   - `backend/src/test/java/ch/unige/events/service/AttendanceServiceMock.java` — pattern @Mock @ApplicationScoped extends Service avec static volatile boolean force*.
   - `backend/src/test/java/ch/unige/events/resource/AttendanceResourceTest.java` — pattern @QuarkusTest + @TestSecurity(user="auth0|alice") + RestAssured given/when/then.

## Ordre d'implémentation strict

1. **`openapi/openapi.yaml` EN PREMIER** (étape 0 de la spec) :
   - Ajouter le schema CoOrganizerStatus (à côté d'AttendanceStatus).
   - Ajouter les schemas CoOrganizer, CoOrganizerInvitation, InviteCoOrganizerRequest.
   - Ajouter les 6 paths : POST/GET /events/{id}/co-organizers, DELETE /events/{id}/co-organizers/{userId}, PATCH /events/{id}/co-organizers/me/accept, PATCH /events/{id}/co-organizers/me/decline, GET /users/me/co-organizer-invitations.
   - Enrichir les descriptions des 9 endpoints touchés par la cascade (cf. tableau étape 0.4 de la spec).
   - Vérifier la validité du YAML via le linter local si disponible.

2. **Entité + enum** (étape 1) :
   - `backend/src/main/java/ch/unige/events/entity/CoOrganizerStatus.java` — enum 3 valeurs.
   - `backend/src/main/java/ch/unige/events/entity/EventCoOrganizer.java` — PanacheEntity, contrainte unique, indexes, @PrePersist, helpers statiques (isAcceptedFor, findByEventAndUser, findByEvent, findByUser).

3. **DTOs** (étape 2) sous `backend/src/main/java/ch/unige/events/dto/coorganizer/` :
   - `CoOrganizerDTO.java` (record + factory from(entity, user)).
   - `CoOrganizerInvitationDTO.java` (record + factory from(entity, eventDTO)).
   - `InviteCoOrganizerRequest.java` (record + @NotNull UUID userId).

4. **Service** (étape 3) :
   - `EventCoOrganizerService.java` @ApplicationScoped + @Transactional sur toutes les mutations.
   - Méthodes : invite (avec cannot_invite_self / already_invited / forbidden / 404), accept (idempotent), decline (DELETE row), remove (idempotent), getCoOrganizers (bulk-fetch des Users via User.list("id IN ?1", ...)), getMyInvitations (default PENDING, paginé, enrichi via eventService.findByIdsAsDTO).
   - Helpers privés-static badRequest et conflict pour les WebApplicationException.

5. **Cascade EventService** (étape 4) :
   - Ajouter `private boolean isCreatorOrAcceptedCoOrganizer(Event, String auth0Id)` qui délègue à isCreator puis à User.findByAuth0Id().map(u -> EventCoOrganizer.isAcceptedFor(event.id, u.id)).orElse(false).
   - Exposer `public boolean isCreatorOrAcceptedCoOrganizerPublic(Event, String)` qui délègue au privé (réutilisé par AttendanceService et EventStatsService).
   - Ajouter `public Map<Long, EventDTO> findByIdsAsDTO(List<Long> ids)` @Transactional qui réutilise toEventDTOs.
   - Migrer 6 call-sites d'isCreator dans EventService : update, cancel, restore, publish (avec !isAdmin || ...), uploadImage (avec !isAdmin || ...), getById (avec !isAdmin || ... — pour la visibilité DRAFT/CANCELLED).
   - NE PAS migrer delete() — reste strict-creator.
   - Migrer AttendanceService.getAttendees (injecter EventService, déléguer au helper public).
   - Migrer EventStatsService.getStats (idem).

6. **Resource** (étape 5) :
   - `EventCoOrganizerResource.java` constructor DI, sous @Path("/events"), 5 endpoints (POST, GET, DELETE, PATCH /me/accept, PATCH /me/decline).
   - Code 201 sur POST, 200 sur GET et PATCH /me/accept, 204 sur DELETE et PATCH /me/decline.
   - identity.hasRole("ADMIN") pour le flag isAdmin.

7. **UserResource** (étape 6) :
   - Injecter EventCoOrganizerService.
   - Ajouter GET /me/co-organizer-invitations avec @QueryParam status / page / size + @DefaultValue / @Min / @Positive / @Max identiques au pattern /me/favorites.

8. **Tests** (étape 7) — cible ≥ 80 % couverture sur les lignes nouvelles, idéalement 100 % sur Service et Resource :
   - `EventCoOrganizerServiceMock.java` (pattern AttendanceServiceMock) avec static volatile boolean force* + reset() + fixtures coOrganizersFixture / myInvitationsFixture.
   - `EventCoOrganizerResourceTest.java` (22 tests — cf. tableau étape 7.2 de la spec).
   - `EventCoOrganizerServiceCoverageTest.java` DevServices PostgreSQL (22 tests — cf. tableau étape 7.3).
   - `EventCoOrganizerTest.java` entité (5 tests — PrePersist, isAcceptedFor x3, contrainte unique).
   - `CoOrganizerDTOTest.java` (3 tests — projection avec/sans User, EventDTO inclus).
   - Tests de cascade dans EventServiceCoverageTest (+9), AttendanceServiceCoverageTest (+2), EventStatsServiceCoverageTest (+2).
   - UserResourceTest +5 tests pour /me/co-organizer-invitations.

9. **`./mvnw verify`** — DOIT être vert avant de toucher la doc. Corriger toute régression sur les tests existants (la cascade ne doit PAS casser les tests strict-creator existants — vérifier que l'auth0Id passé continue à matcher le creator).

10. **Documentation** (étape 8 — même commit que le code correspondant ou commit `docs(scrum-136):` séparé) :
    - `backend/docs/data-model.md` — entité EventCoOrganizer (table, champs, contraintes, indexes, helpers, sémantique DECLINE), enum CoOrganizerStatus, section permissions, note SchemaFixup pour les futures modifs.
    - `backend/docs/api-contract.md` — 6 nouveaux endpoints dans le tableau + section dédiée par endpoint.
    - `backend/docs/sprint-context.md` — entrée SCRUM-136 dans la section Sprint 7.
    - `openapi/openapi.yaml` est le seul fichier OpenAPI (déjà fait étape 1).

11. **Vérifications finales avant push** :
    - `git diff --stat frontend/` vide.
    - `git diff --stat openapi/` non-vide (modifications sur paths + components.schemas).
    - Pas de nouvelle dépendance Maven.
    - `./mvnw verify` vert.
    - JaCoCo report inspecté : couverture lignes nouvelles ≥ 80 %, idéalement 100 % sur Service et Resource.
    - Les 8 tests sentinels passent : invite_byCreator, invite_self (cannot_invite_self), invite_alreadyInvited, decline_thenReinvite, accept_idempotentOnAccepted, delete_byAcceptedCoOrganizer_throws403, update_byAcceptedCoOrganizer_succeeds, getMyCoOrganizerInvitations_default_returnsPending.

## Interdits stricts

- PAS de modification de Event.creator ni d'ajout d'un champ `coOrganizers` sur Event.
- PAS de mutation du helper privé EventService.isCreator — créer un nouvel helper.
- PAS d'extension de la cascade à EventService.delete (suppression physique reste strict-creator).
- PAS de persistance d'un CoOrganizerStatus.DECLINED en base (decline() supprime la row).
- PAS d'envoi d'email, PAS d'entité Notification, PAS de publication d'event Quarkus, PAS de scheduler.
- PAS d'ajout à SchemaFixup dans cette PR (création ex nihilo, Hibernate pose la CHECK initiale).
- PAS d'extension de CoOrganizerDTO avec email/bio (privacy).
- PAS de @ManyToOne(Event) ni @ManyToOne(User) sur EventCoOrganizer — pattern Long+UUID.
- PAS d'endpoint d'invitation par email.
- PAS d'endpoint « bulk invite ».
- PAS de modification du frontend (SCRUM-137 séparé).
- PAS de migration SQL — Hibernate update mode.
- PAS de cache (TanStack Query, Quarkus Cache).
- PAS de verrou pessimiste sur Event au moment de l'invite (concurrence rare, OK 409).
- PAS de cassure des tests existants — la cascade ne doit pas changer le comportement strict-creator pour les anciens scénarios.
- PAS de logging d'auth0Id ou userId en clair en INFO — DEBUG si besoin.
- PAS de TODO commenté.
- PAS de snake_case côté Java.

## Conventions à respecter

- camelCase partout en Java, JSON, OpenAPI. Hibernate convertit en snake_case côté DB automatiquement.
- Pas de préfixe `is` sur les booléens (n/a — aucun booléen sur EventCoOrganizer).
- Constructor injection sur EventCoOrganizerResource (pattern EventResource). Field injection conservée sur UserResource (pattern existant — pas de refactor opportuniste).
- @Transactional sur toutes les mutations Service.
- @Authenticated sur tous les endpoints.
- @PathParam UUID pour les userId, Long pour les eventId.
- Pagination identique aux autres /me/* : @DefaultValue("0") @Min(0) page, @DefaultValue("20") @Positive @Max(100) size.
- Codes d'erreur custom dans le champ `error` de l'envelope ApiErrorResponse : `cannot_invite_self`, `already_invited`. 4xx standard pour les autres.
- Couverture JaCoCo ≥ 80 % sur les lignes nouvelles, duplication < 3 %, ratings A.
- Doc mise à jour dans le même commit que le code correspondant.
- Commits atomiques nommés `feat(scrum-136): ...`, `refactor(scrum-136): ...`, `test(scrum-136): ...`, `docs(scrum-136): ...`.
- Titre PR : `feat(scrum-136): add EventCoOrganizer entity and invitation endpoints`.

## Critères de done

- [ ] `./mvnw verify` vert localement et en CI.
- [ ] JaCoCo ≥ 80 % sur les lignes nouvelles ; idéalement 100 % sur Service et Resource.
- [ ] Les 8 tests sentinels verts nommément :
  - `invite_byCreator_returns201`
  - `invite_self_returns400` (envelope `error=cannot_invite_self`)
  - `invite_alreadyInvited_returns409` (envelope `error=already_invited`)
  - `decline_thenReinvite_works` (sentinel décision 5 — DECLINE supprime la row)
  - `accept_idempotentOnAccepted` (200 sans erreur si déjà ACCEPTED)
  - `delete_byAcceptedCoOrganizer_throws403` (sentinel décision 2 — delete reste strict-creator)
  - `update_byAcceptedCoOrganizer_succeeds` (sentinel cascade applicable)
  - `getMyCoOrganizerInvitations_default_returnsPending` (filtre par défaut)
- [ ] `git diff --stat frontend/` vide.
- [ ] `openapi/openapi.yaml` modifié EN PREMIER et cohérent avec le code.
- [ ] `backend/docs/data-model.md`, `backend/docs/api-contract.md`, `backend/docs/sprint-context.md` mis à jour dans la même PR.
- [ ] PR ouverte avec base `main`, titre `feat(scrum-136): add EventCoOrganizer entity and invitation endpoints`, description complète selon le template (Résumé, Why, Changements Backend + Documentation, Tests, Test plan, Documentation, Dépendances/ordre de merge mentionnant SCRUM-137, Décisions techniques tranchées, Notes pour le reviewer mentionnant la divergence sur `delete()`).
- [ ] Commits atomiques bien nommés.
- [ ] `git branch -vv` confirme que la branche track `origin/feature/s6-co-organizers` (PAS `origin/main`) après le premier push.
- [ ] La check CI `Lint PR title` est verte.
- [ ] SonarCloud Quality Gate vert.
````
