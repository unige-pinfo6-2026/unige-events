# Sprint Context — unige-events-api

Dernière mise à jour : 2026-03-21

---

## Sprint 1 — TERMINÉ (6–13 mars 2025)

**Objectif :** Authentification complète (Auth0/OIDC) + base du profil utilisateur + architecture full-stack.

### Ce qui est implémenté

- **Intégration Auth0/OIDC** : `quarkus-oidc` configuré en mode `service`, validation JWT automatique, désactivé en `%test`.
- **Entité `User`** : UUID comme PK, champs `auth0Id`, `email`, `displayName`, `firstName`, `lastName`, `faculty`, `studyLevel`, `bio`, `interests`, `avatarUrl`, `profilePublic`, `createdAt`, `version` (optimistic locking).
- **Provisionnement first-login** : `UserService.getOrCreateUser()` — idempotent, race-safe (gestion des conflits `PersistenceException` + retry).
- **Endpoints profil** :
  - `GET /users/me` — profil complet de l'utilisateur connecté
  - `PUT /users/me` — mise à jour partielle du profil (champs optionnels, `@Valid`, retourne l'objet complet)
  - `GET /users/{id}` — profil public (si `profilePublic = true`, sinon 403)
- **DTOs** : `UserProfileResponse`, `UserPublicResponse`, `UpdateProfileRequest`
- **Exception mappers** : 6 mappers (409, 400, 404, 403, 401, `ConstraintViolationException`)
- **Architecture en couches** : Resource → Service → Entity validée, constructor injection, encapsulation des entités.
- **Configuration OpenAPI** : `OpenApiSecurityConfig` pour le bearer JWT.

### Bugs connus / comportements à surveiller

- `GET /users/me` : si le claim `email` est absent du JWT, une `NotAuthorizedException` est levée → retourne 401. Ce comportement est **correct et intentionnel** selon la spec. À documenter côté frontend.
- `PUT /users/me` : retourne `200` avec l'objet `UserProfileResponse` complet — **pas de `204`**. Le frontend doit utiliser cette réponse pour mettre à jour son état sans refetch.
- Hibernate tourne en mode `update` — pas de migration Flyway active. Dette à anticiper avant la prod.

---

## Sprint 2 — EN COURS (13–20 mars 2025)

**Objectif :** Création, édition et suppression d'événements (rôle Organisateur). Premières briques du listing public.

### État actuel

- **Entité `Event`** : très partielle — uniquement `id` (Long) + `title` (@NotBlank) + `description`. Les champs `startDate`, `endDate`, `location`, `category`, `bannerUrl`, `capacity`, `active`, `featured`, `views`, `creatorId` sont **planifiés mais pas implémentés**.
- **`EventResource`** : `GET /events` (liste) et `POST /events` (création) — **pas de sécurité encore, pas de pagination, pas de filtres**.
- **`EventService`** : `getAll()` et `create()` — minimal.

### À faire dans ce sprint

- [ ] Enrichir `Event` avec tous les champs planifiés (+ migration Flyway correspondante)
- [ ] `POST /events` : sécuriser avec `@Authenticated`, lier `creatorId` à l'utilisateur connecté
- [ ] `GET /events/{id}` : détail d'un événement
- [ ] `PUT /events/{id}` : modification (créateur ou admin uniquement → 403 sinon)
- [ ] `DELETE /events/{id}` : soft-delete (`active = false`)
- [ ] `GET /events` : pagination cursor-based, filtre `?category=`, `?upcoming=true`
- [ ] Créer un `EventDTO` / `EventResponse` (ne pas exposer l'entité directement)
- [ ] Écrire les tests `@QuarkusTest` pour `EventResource`

---

## Sprint 3 (planifié : 20–27 mars 2025)

**Objectif :** Découverte avancée — recherche, filtres, vue calendrier.

- [ ] `GET /events/search?q=&category=&faculty=&dateFrom=&dateTo=` — full-text ILIKE sur titre + description
- [ ] Ajout du champ `faculty` (enum) sur `Event`
- [ ] Filtre `?faculty=` dans `GET /events`

---

## Sprint 4 (planifié : 27 mars – 3 avril 2025)

**Objectif :** Engagement & Interaction — inscription, favoris, partage.

- [ ] Entité `Attendance` (userId, eventId, status INTERESTED|ATTENDING, contrainte unique)
- [ ] `POST /events/{id}/attend`, `DELETE /events/{id}/attend`
- [ ] Entité `Favorite` (userId, eventId)
- [ ] `POST /events/{id}/favorite`, `DELETE /events/{id}/favorite`, `GET /users/me/favorites`
- [ ] `GET /events/{id}/attendees` (profils publics uniquement)
- [ ] Compteurs `attendeeCount`, `interestedCount` dans `GET /events/{id}`

---

## Sprint 5 (planifié : 3–10 avril 2025)

**Objectif :** Statistiques organisateur + liste des participants.

- [ ] `GET /events/{id}/stats` (vues, interestedCount, attendingCount — créateur/admin uniquement)
- [ ] Incrémentation du compteur de vues à chaque `GET /events/{id}` (déduplication userId+eventId)

---

## Sprint 6 (planifié : 10–24 avril 2025)

**Objectif :** Administration & Modération.

- [ ] Champ `admin` (boolean) sur `User` + `@RolesAllowed("admin")` sur endpoints sensibles
- [ ] Entité `Report` (reporterId, eventId, reason, status PENDING|REVIEWED|DISMISSED)
- [ ] `POST /events/{id}/report`
- [ ] `GET /admin/reports`, `PUT /admin/reports/{id}`, `PUT /admin/events/{id}/feature`

---

## Sprint 7 (planifié : 24 avril – 8 mai 2025)

**Objectif :** Notifications, duplication, expiration automatique, polish UI.

- [ ] Entité `Notification` (userId, eventId, type, message, read)
- [ ] `GET /notifications`, `PUT /notifications/{id}/read`
- [ ] `POST /events/{id}/duplicate` (réservé au créateur)
- [ ] Job `@Scheduled` : désactivation auto des events dont `endDate < now()`

---

## Sprint 8 (planifié : 8–22 mai 2025)

**Objectif :** Tests, scalabilité, sécurité, CD, soutenance.

- [ ] Tests d'intégration `@QuarkusTest` couverture >80% sur EventResource, UserResource
- [ ] Audit OWASP Top 10, CORS configuré, secrets en env vars
- [ ] Tests E2E Playwright/Cypress (3–5 scénarios critiques)
- [ ] CD pipeline opérationnel (Kubernetes deploy automatique)
- [ ] Activation Flyway pour la prod (écrire les migrations manquantes)
- [ ] Préparation soutenance

---

## Dette technique connue

| Item | Priorité | Sprint cible |
|---|---|---|
| Migrations Flyway à écrire (V1 users, V2 events, ...) | Haute | Avant Sprint 8 |
| Désactiver mode Hibernate `update` en prod | Haute | Sprint 8 |
| Sécuriser `POST /events` avec `@Authenticated` | Haute | Sprint 2 |
| Remplacer exposition directe de l'entité `Event` par un DTO | Moyenne | Sprint 2 |
| Tests unitaires sur `UserService` | Moyenne | Sprint 2 |
