# UNIGE Events — Backlog Sprints 5 à 10

> Groupe 6 · PINFO UNIGE · Printemps 2026

> Stack : Java 21 / Quarkus / PostgreSQL · React 19 / TypeScript / TailwindCSS


---

## Sprint 5 — 14–18 avr. 2026
**Thème :** Fondations backend Event/User + composants front utilitaires  
**Total estimé :** 24 SP

### 🚀 [SCRUM-44] [S5] Je veux consulter les stats de mes événements et la liste de mes inscrits (US-14, T3)
**Type :** Feature · **Story Points :** — SP

**Sprint 5 · Epic 2 & 4 — Gestion avancée organisateur**

En tant qu'organisateur, je veux visualiser les statistiques de participation (vues, inscriptions, intérêt) pour mes événements et voir la liste de mes inscrits, afin d'adapter ma communication et planifier la logistique.

**Critères d'acceptation :**

* Dashboard avec graphiques par événement (vues, intéressés, inscrits)
* Page "Mes événements" avec tabs À venir / Passés / Annulés
* Vue dédupliquée des vues par userId+eventId
* Profils privés affichés comme "Utilisateur anonyme"

**Tâches liées :** SCRUM-30 (BACK), SCRUM-31 (FRONT)

### 📖 [SCRUM-113] [S5][US-24] En tant qu'utilisateur, je veux uploader une bannière sur mon profil, afin de personnaliser visuellement ma page publique.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-117] [S5][US-28] En tant qu'organisateur, je veux enrichir mon événement (URL, email contact, mots-clés, fichiers joints, deadline inscription), afin de fournir une information complète aux participants.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-64] [US-14] En tant qu’organisateur, je veux voir combien de personnes ont marqué leur intérêt ou leur participation afin de planifier la logistique (taille du lieu, ressources).
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-71] [US-T3] En tant qu’organisateur, je veux visualiser les statistiques de participation (vues, inscriptions, check-in) pour chaque événement afin d'adapter notre communication
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 🔧 [SCRUM-120] [BACK][S5] Champ bannerUrl sur User + endpoint upload/suppression bannière profil
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S5 | **Assigné** : Elie | **SP** : 2 | **Épic** : SCRUM-13 | **Story** : SCRUM-113 (US-24)

\[BACK\] Sprint 5 — Feature 4 / Tâche 1

1. **Entité** `User` : ajouter le champ `bannerUrl` (String, nullable). Créer une migration Flyway `V<N>__add_user_banner_url.sql` dans `backend/src/main/resources/db/migration/` (Hibernate est en `validate` depuis SCRUM-164 — voir [`backend/AGENTS.md`](backend/AGENTS.md) section « Schéma de base de données — Flyway »).
2. **Endpoint upload** : `POST /api/users/me/banner` — multipart/form-data, champ `file`. Déléguer à `FileStorageService.store()` (même pattern que `POST /api/users/me/image`). Retourner le `UserProfileResponse` mis à jour.
3. **Endpoint suppression** : `DELETE /api/users/me/banner` — remet `bannerUrl = null`, retourner `UserProfileResponse` mis à jour.
4. **OpenAPI** : ajouter `bannerUrl` dans le schéma `User` et `UserPublicResponse`. Ajouter les deux nouveaux paths.
5. **Service** : `UserService.uploadBanner()` et `UserService.deleteBanner()` — `@Transactional`.
6. **Tests** `@QuarkusTest` : upload réussi → 200 + bannerUrl non-null ; suppression → 200 + bannerUrl null.

Fichiers touchés : `User.java`, `UserService.java`, `UserResource.java`, `UserProfileResponse.java`, `openapi.yaml`
Branche suggérée : `feature/s5-user-banner`
Dépendances : `FileStorageService` existant

### 🔧 [SCRUM-121] [FRONT][S5] Affichage et upload de la bannière profil
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S5 | **Assigné** : Viona | **SP** : 2 | **Épic** : SCRUM-13 | **Story** : SCRUM-113 (US-24)

\[FRONT\] Sprint 5 — Feature 4 / Tâche 2

1. `ProfilePage.tsx` : afficher la bannière en haut du profil (div `h-48 bg-cover bg-center rounded-3xl`, fallback gradient si `bannerUrl` null).
2. `ProfileEditPage.tsx` : ajouter zone d'upload bannière (bouton "Changer la bannière" → `<input type="file" accept="image/*">`). Appel `POST /api/users/me/banner` avec `FormData`. Afficher preview avant confirmation. Bouton "Supprimer la bannière" → `DELETE /api/users/me/banner`.
3. Utiliser TanStack Query `useMutation` pour les appels upload/delete. Invalider `queryKey: ['user', 'me']` après succès.
4. Ajouter types TypeScript : champ `bannerUrl?: string | null` dans `User` (types régénérés depuis openapi.yaml).

Fichiers touchés : `ProfilePage.tsx`, `ProfileEditPage.tsx`, `src/types/user.ts`
Branche suggérée : `feature/s5-user-banner-front`
Dépendances : SCRUM-120 (BACK bannerUrl)

### 🔧 [SCRUM-124] [BACK][S5] Champ allDay sur Event (option toute la journée)
**Type :** Tâche · **Story Points :** 1 SP

**Sprint** : S5 | **Assigné** : Antoine | **SP** : 1 | **Épic** : SCRUM-14 | **Story** : SCRUM-117 (US-28)

\[BACK\] Sprint 5 — Feature 6b / Tâche 1

1. **Entité** `Event` : ajouter `allDay` (boolean, default false).
2. `CreateEventRequest` et `UpdateEventRequest` : ajouter `allDay` (Boolean, nullable → par défaut false si absent).
3. `EventService.create()` et `EventService.update()` : mapper `allDay`.
4. `EventDTO` : ajouter `allDay`.
5. **OpenAPI** : ajouter `allDay` dans les schémas `Event`, `CreateEventRequest`, `UpdateEventRequest`.
6. **Test** : créer un événement avec `allDay=true` → vérifier la persistance.

Fichiers touchés : `Event.java`, `CreateEventRequest.java`, `UpdateEventRequest.java`, `EventDTO.java`, `EventService.java`, `openapi.yaml`
Branche suggérée : `feature/s5-event-allday`
Dépendances : aucune

### 🔧 [SCRUM-125] [FRONT][S5] UI "Toute la journée" dans EventForm
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S5 | **Assigné** : Daniel | **SP** : 2 | **Épic** : SCRUM-14 | **Story** : SCRUM-117 (US-28)

\[FRONT\] Sprint 5 — Feature 6b / Tâche 2

1. `EventForm.tsx` : ajouter une checkbox "Toute la journée" (booléen `allDay`) près des sélecteurs d'heure. Si cochée : masquer les selects heure (CSS `hidden`), forcer `startDate` à `T00:00` et `endDate` à `T23:59` automatiquement.
2. `useEventForm.ts` (hook) : ajouter `allDay` dans `EventFormValues`, mettre à jour la validation Zod (si `allDay = true`, les heures ne sont pas requises).
3. `EventDetailPage.tsx` et `EventCard.tsx` : si `event.allDay = true`, afficher uniquement la date (sans heure). Utiliser `formatEventDateTime()` adapté.
4. Mise à jour des types TypeScript depuis openapi.yaml.

Fichiers touchés : `EventForm.tsx`, `useEventForm.ts`, `EventDetailPage.tsx`, `EventCard.tsx`
Branche suggérée : `feature/s5-event-allday-front`
Dépendances : SCRUM-124 (\[BACK\]\[S5\] allDay)

### 🔧 [SCRUM-126] [BACK][S5] Champs websiteUrl, contactEmail, registrationDeadline et tags sur Event
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S5 | **Assigné** : Antoine | **SP** : 3 | **Épic** : SCRUM-14 | **Story** : SCRUM-117 (US-28)

\[BACK\] Sprint 5 — Feature 6d / Tâche 1

Ajouter les champs textuels et la collection de tags à `Event` :

1. **Entité** `Event` :

    * `websiteUrl` (String, nullable, @URL)
    * `contactEmail` (String, nullable, @Email)
    * `registrationDeadline` (LocalDateTime, nullable)
    * `tags` (List<String>, @ElementCollection(fetch = FetchType.EAGER), @CollectionTable(name = "event_tags"))
    
2. **DTOs** : ajouter ces 4 champs dans `CreateEventRequest`, `UpdateEventRequest`, `EventDTO`.
3. `EventService` : mapper les champs dans `create()` et `update()`.
4. `AttendanceService.attend()` : si `event.registrationDeadline != null && LocalDateTime.now().isAfter(event.registrationDeadline)` → lever `WebApplicationException(Response.Status.CONFLICT)` avec message "La deadline d'inscription est dépassée."
5. **OpenAPI** : ajouter les 4 champs dans les schémas concernés.
6. **Tests** : inscription après deadline → 409. Tags dans la réponse `EventDTO`.

Fichiers touchés : `Event.java`, `CreateEventRequest.java`, `UpdateEventRequest.java`, `EventDTO.java`, `EventService.java`, `AttendanceService.java`, `openapi.yaml`
Branche suggérée : `feature/s5-event-extra-fields`
Dépendances : aucune

### 🔧 [SCRUM-128] [FRONT][S5] Composant TagInput réutilisable
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S5 | **Assigné** : Viona | **SP** : 2 | **Épic** : SCRUM-14 | **Story** : SCRUM-117 (US-28) / SCRUM-119 (US-30)

\[FRONT\] Sprint 5 — Feature 6d / Tâche 3

Créer un composant `TagInput.tsx` (dans `src/components/utils/`) :

* Affiche une liste de tags sous forme de chips avec bouton ×
* Input text : Entrée ou virgule → ajoute le tag ; Backspace → supprime le dernier
* Props : `value: string[]`, `onChange: (tags: string[]) => void`, `placeholder?: string`, `maxTags?: number`
* Styles TailwindCSS : chips arrondies avec couleur neutre, input inline
* Compatible React Hook Form via `Controller`

Fichiers créés : `src/components/utils/TagInput.tsx`
Branche suggérée : `feature/s5-tag-input`
Dépendances : aucune

### 🔧 [SCRUM-129] [BACK][S5] Renforcement vérification capacité + WAITLISTED + liste d'attente
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S5 | **Assigné** : Elie | **SP** : 3 | **Épic** : SCRUM-16 | **Story** : SCRUM-117 (US-28)

\[BACK\] Sprint 5 — Feature 6e / Tâche 1

1. `AttendanceStatus` : ajouter `WAITLISTED` à l'enum.
2. `AttendanceService.attend()` : si `event.capacity != null` et `status = ATTENDING` :

    * Compter `Attendance.count("eventId = ?1 AND status = ?2", eventId, ATTENDING)`
    * Si count >= capacity → créer `Attendance` avec `status = WAITLISTED`
    
3. `AttendanceService.removeAttendance()` : si l'inscription supprimée était ATTENDING et que l'événement a une capacité → chercher le premier WAITLISTED (ORDER BY createdAt ASC) → le promouvoir à ATTENDING (log si infrastructure notif pas encore disponible).
4. `EventDTO` / `EventService.getById()` : ajouter `availableSpots` (Long, nullable — null si pas de capacité) = `capacity - attendingCount` (minimum 0), et `waitlistedCount` (Long).
5. **OpenAPI** : ajouter `WAITLISTED` à `AttendanceStatus`, ajouter `availableSpots` et `waitlistedCount` dans le schéma `Event`.
6. **Tests** : remplissage jusqu'à capacité → 200 WAITLISTED ; désistement → promotion automatique.

Fichiers touchés : `AttendanceStatus.java`, `AttendanceService.java`, `EventDTO.java`, `EventService.java`, `openapi.yaml`
Branche suggérée : `feature/s5-capacity-waitlist`
Dépendances : aucune

### 🔧 [SCRUM-78] [BACK][S5] Endpoint GET /api/events/{id}/stats (agrégation DB)
**Type :** Tâche · **Story Points :** 3 SP

**\[BACK\] Sprint 5 – Tâche 1/2**

Créer l'endpoint d'agrégation des statistiques d'un événement :

* `GET /api/events/{id}/stats` → retourne `{ attendingCount, interestedCount, viewCount }`
* Agrégation SQL via `PanacheQuery` / JPQL (COUNT sur Attendance, Favorite, EventView)
* `EventStatsResource` + `EventStatsService`
* Tests `@QuarkusTest` couvrant les compteurs

**Fichiers touchés :** `EventStatsResource.java`, `EventStatsService.java`, `EventStatsDTO.java`
**Branche suggérée :** `feature/s5-stats-endpoint`

### 🔧 [SCRUM-79] [BACK][S5] Compteur de vues EventView (déduplication userId+eventId)
**Type :** Tâche · **Story Points :** 3 SP

**\[BACK\] Sprint 5 – Tâche 2/2**

Implémenter le tracking des vues sur les pages événement :

* Entité `EventView` (eventId, userId, viewedAt)
* Contrainte d'unicité `(eventId, userId)` pour déduplication : un user = 1 vue par event
* Créer une migration Flyway `V<N>__create_event_views.sql` dans `backend/src/main/resources/db/migration/` (Hibernate est en `validate` depuis SCRUM-164 — voir [`backend/AGENTS.md`](backend/AGENTS.md) section « Schéma de base de données — Flyway »)
* `POST /api/events/{id}/view` appelé depuis le front à l'ouverture de la page détail
* Service `EventViewService` avec logique upsert (ignore si déjà existant)
* Tests `@QuarkusTest` : 1ère vue comptée, 2ème ignorée

**Fichiers touchés :** `EventView.java`, `EventViewService.java`, `EventViewResource.java`
**Branche suggérée :** `feature/s5-event-view-counter`

### 🔧 [SCRUM-93] [FRONT][S5] Page Mes Événements organisateur (liste, onglets statut, actions)
**Type :** Tâche · **Story Points :** 3 SP

**\[FRONT\] Sprint 5 – Tâche 2/2**

Créer la page "Mes Événements" pour l'organisateur :

* `MyEventsPage.tsx` : page `/my-events` accessible aux utilisateurs avec rôle ORGANIZER
* Onglets/tabs : "Publiés" | "Brouillons" | "Annulés" — filtrage via `GET /api/events?organizerId=me&status=`
* Tableau ou liste de cards avec colonnes : Titre, Date, Participants, Vues, Statut, Actions
* Actions par événement :

    * "Modifier" → `/events/:id/edit`
    * "Statistiques" → `/events/:id/stats` (lien vers SCRUM-92)
    * "Publier" (si DRAFT) → `PATCH /api/events/{id}/publish`
    * "Annuler" → `DELETE /api/events/{id}` avec confirmation modale
    
* `useMyEvents.ts` : hook pour fetch + invalidation cache après action
* Bouton flottant "Créer un événement" → `/events/new`
* Lien "Mes Événements" dans la navbar (organisateur uniquement)
* Tri par date décroissante par défaut

**Fichiers touchés :** `MyEventsPage.tsx`, `useMyEvents.ts`, `Navbar.tsx` (lien conditionnel), `App.tsx` (route `/my-events`)
**Branche suggérée :** `feature/s5-my-events-page`

---

## Sprint 6 — 21–25 avr. 2026
**Thème :** Enrichissement formulaire + recherche tags + archive + stats  
**Total estimé :** 27 SP

### 🚀 [SCRUM-159] [S6] Je veux enrichir le formulaire événement, filtrer par mots-clés et retrouver mes événements passés (US-23, 25, 30)
**Type :** Feature · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-112] [S6][US-23] En tant qu'organisateur, je veux retrouver tous mes événements passés dans une archive, afin de pouvoir réutiliser un événement existant comme template pour en créer un nouveau rapidement.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-114] [S6][US-25] En tant qu'utilisateur, je veux recadrer mon avatar et mes bannières directement dans l'interface, afin d'optimiser le rendu sans outil externe.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-119] [S6][US-30] En tant qu'utilisateur, je veux filtrer les événements par mots-clés, afin de trouver plus précisément les événements qui m'intéressent.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-161] [S6][US-19] En tant qu'organisateur, je veux définir une capacité maximale pour mon événement et gérer une liste d'attente automatique, afin de contrôler le nombre de participants.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 🔧 [SCRUM-101] [FRONT][S6] Liste des inscrits avec profils publics sur page détail organisateur
**Type :** Tâche · **Story Points :** 3 SP

**\[FRONT\] Sprint 5 – Tâche manquante (US-07)**

Afficher la liste des inscrits avec leurs profils publics sur la page détail organisateur :

* `AttendeesList.tsx` : section "Participants" en bas de `EventDetailPage.tsx`, visible pour l'organisateur
* Appel `GET /api/events/{id}/attendees` (paginé, `page` + `size`)
* Affichage en grille d'avatars avec :

    * Profils publics (`isProfilePublic=true`) : avatar, nom, faculté, niveau d'études
    * Profils privés : avatar générique + "Utilisateur anonyme"
    
* Onglets : "Participent" (ATTENDING) | "Intéressés" (INTERESTED) — filtre `?status=`
* Pagination : "Charger plus" (load more) ou pagination numérotée
* Clic sur un profil public → `/users/:id` (page profil, si elle existe)
* `useAttendees.ts` : hook avec pagination
* Pour les non-organisateurs : la section n'affiche qu'un résumé (avatars empilés + compteur "X personnes participent")

**US couverte :** US-07 — Je veux voir les profils publics des participants qui choisissent de les partager

**Fichiers touchés :** `AttendeesList.tsx`, `AttendeeCard.tsx`, `useAttendees.ts`, `attendeesApi.ts`, `EventDetailPage.tsx` (intégration section)
**Branche suggérée :** `feature/s5-attendees-list`

### 🔧 [SCRUM-122] [FRONT][S6] Composant ImageCropper réutilisable (avatar + bannières)
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S5 | **Assigné** : Viona | **SP** : 3 | **Épic** : SCRUM-13 | **Story** : SCRUM-114 (US-25)

\[FRONT\] Sprint 5 — Feature 4 / Tâche 3

Créer un composant générique de recadrage d'image utilisable pour avatar, bannière profil et bannière événement.

1. **Installer** `react-image-crop` (`npm install react-image-crop`).
2. `ImageCropper.tsx` (nouveau, dans `src/components/utils/`) : composant modal qui reçoit :

    * `src: string` (data URL de l'image chargée)
    * `aspect: number` (ex. 1 pour carré, 3 pour bannière 3:1)
    * `circular?: boolean` (pour avatar)
    * `onCropComplete: (blob: Blob) => void`
    * `onCancel: () => void`
    
3. Utiliser `ReactCrop` de `react-image-crop` avec `keepSelection`. À la confirmation, utiliser un `<canvas>` pour appliquer le crop (`canvas.toBlob()`).
4. Styles : modal centré, overlay sombre, boutons "Recadrer" / "Annuler".
5. **Tests** : vérifier que `onCropComplete` est appelé avec un Blob non-null.

Fichiers créés/modifiés : `src/components/utils/ImageCropper.tsx`
Branche suggérée : `feature/s5-image-cropper`
Dépendances : aucune

### 🔧 [SCRUM-123] [FRONT][S6] Intégration du crop sur avatar, bannière profil et bannière événement
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S5 | **Assigné** : Daniel | **SP** : 2 | **Épic** : SCRUM-13 | **Story** : SCRUM-114 (US-25)

\[FRONT\] Sprint 5 — Feature 4 / Tâche 4

Intégrer `ImageCropper.tsx` dans les trois points d'upload :

1. `ProfileEditPage.tsx` — avatar : aspect 1:1, circular=true. Déclencher `ImageCropper` après sélection fichier, avant l'envoi à `POST /api/users/me/image`.
2. `ProfileEditPage.tsx` — bannière : aspect 3:1, circular=false. Déclencher `ImageCropper` avant `POST /api/users/me/banner`.
3. `EventCreatePage.tsx` / `EventEditPage.tsx` — bannière événement : aspect 16:9. Déclencher `ImageCropper` avant `POST /api/events/{id}/image`.
4. Flux : sélection fichier → FileReader → `ImageCropper` modal → confirm → Blob → FormData → upload API.

Fichiers touchés : `ProfileEditPage.tsx`, `EventCreatePage.tsx`, `EventEditPage.tsx`
Branche suggérée : `feature/s5-image-crop-integration`
Dépendances : SCRUM-122 (ImageCropper.tsx)

### 🔧 [SCRUM-127] [FRONT][S6] Champs additionnels dans EventForm (websiteUrl, contactEmail, deadline, tags)
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S5 | **Assigné** : Daniel | **SP** : 3 | **Épic** : SCRUM-14 | **Story** : SCRUM-117 (US-28)

\[FRONT\] Sprint 5 — Feature 6d / Tâche 2

1. `EventForm.tsx` : ajouter les champs :

    * `websiteUrl` : `<Input type="url">` optionnel
    * `contactEmail` : `<Input type="email">` optionnel
    * `registrationDeadline` : sélecteur date+heure (même pattern que startDate/endDate)
    * `tags` : composant `TagInput` (voir tâche SCRUM-128)
    
2. `useEventForm.ts` : ajouter les champs dans `EventFormValues` et le schéma Zod (URL valide, email valide, deadline < startDate).
3. `EventDetailPage.tsx` : afficher site web (lien externe), email contact, tags (chips cliquables → filtrage par tag).
4. Régénérer les types TypeScript depuis openapi.yaml.

Fichiers touchés : `EventForm.tsx`, `useEventForm.ts`, `EventDetailPage.tsx`
Branche suggérée : `feature/s5-event-extra-fields-front`
Dépendances : SCRUM-126 (BACK champs additionnels), SCRUM-128 (TagInput)

### 🔧 [SCRUM-130] [FRONT][S6] Indicateur places restantes et liste d'attente sur EventDetailPage
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S5 | **Assigné** : Viona | **SP** : 2 | **Épic** : SCRUM-16 | **Story** : SCRUM-117 (US-28)

\[FRONT\] Sprint 5 — Feature 6e / Tâche 2

1. `EventDetailPage.tsx` : si `event.capacity != null`, afficher un indicateur visuel :

    * `availableSpots === 0` → badge rouge "Complet"
    * `availableSpots <= capacity * 0.1` → badge orange "Presque complet"
    * Sinon → badge vert "X places disponibles"
    * Si `waitlistedCount > 0` → "X personnes en liste d'attente"
    
2. `AttendanceButtons.tsx` : si événement complet (availableSpots === 0) → bouton "ATTENDING" devient "Rejoindre la liste d'attente" et appelle le même `POST /api/events/{id}/attend`. Afficher le statut WAITLISTED si l'utilisateur est en liste d'attente.
3. Mise à jour des types TypeScript depuis openapi.yaml.

Fichiers touchés : `EventDetailPage.tsx`, `AttendanceButtons.tsx`
Branche suggérée : `feature/s5-capacity-front`
Dépendances : SCRUM-129 (BACK capacité)

### 🔧 [SCRUM-131] [BACK][S6] Filtrage par tags dans EventSearchService
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S5 | **Assigné** : Antoine | **SP** : 2 | **Épic** : SCRUM-15 | **Story** : SCRUM-119 (US-30)

\[BACK\] Sprint 5 — Feature 6f / Tâche 1

Étendre `GET /api/events/search` pour filtrer par tags :

1. `EventSearchResource` : ajouter `@QueryParam("tags") List<String> tags`.
2. `EventSearchService.search()` : si `tags` non vide, ajouter une condition JPQL :
  `"EXISTS (SELECT t FROM Event e2 JOIN e2.tags t WHERE e2.id = e.id AND LOWER(t) IN :tags)"`
  (Convertir les tags en lowercase côté service.)
3. **OpenAPI** : ajouter le paramètre `tags` (array, query) dans `GET /api/events/search`.
4. **Tests** : événement avec tag "sport" → retrouvé avec `?tags=sport`. Événement sans tag → non retrouvé.

Fichiers touchés : `EventSearchResource.java`, `EventSearchService.java`, `openapi.yaml`
Branche suggérée : `feature/s5-search-tags`
Dépendances : SCRUM-126 (champ `tags` sur Event) — doit être mergé avant

### 🔧 [SCRUM-132] [FRONT][S6] Filtre mots-clés dans EventSearchSidebar
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S5 | **Assigné** : Daniel | **SP** : 2 | **Épic** : SCRUM-15 | **Story** : SCRUM-119 (US-30)

\[FRONT\] Sprint 5 — Feature 6f / Tâche 2

1. `EventSearchSidebar.tsx` : ajouter un `TagInput` (composant SCRUM-128) pour saisir des mots-clés de recherche.
2. `EventsSearchPage.tsx` : passer le paramètre `tags` à l'appel `GET /api/events/search?...&tags=tag1&tags=tag2`.
3. URL params : encoder les tags dans l'URL pour bookmarkabilité.
4. Mise à jour des types/hooks TanStack Query pour inclure `tags` dans la query key.

Fichiers touchés : `EventSearchSidebar.tsx`, `EventsSearchPage.tsx`
Branche suggérée : `feature/s5-search-tags-front`
Dépendances : SCRUM-128 (TagInput), SCRUM-131 (BACK search tags)

### 🔧 [SCRUM-133] [BACK][S6] Endpoint GET /api/users/me/events (tous statuts)
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S6 | **Assigné** : Elie | **SP** : 2 | **Épic** : SCRUM-14 | **Story** : SCRUM-112 (US-23)

\[BACK\] Sprint 6 — Feature 3 / Tâche 1

1. `UserResource` : ajouter `GET /api/users/me/events?status=&page=&size=`.
2. `EventService.getMyEvents()` : `Event.find("creator.id = :id ORDER BY createdAt DESC", ...)` avec filtre optionnel sur `status`. Retourner des `EventDTO`. Inclure TOUS les statuts (DRAFT, PUBLISHED, CANCELLED) sans filtrage par défaut.
3. **OpenAPI** : documenter le nouvel endpoint.
4. **Tests** : créer 3 événements (statuts différents), vérifier que tous remontent.

Fichiers touchés : `UserResource.java`, `EventService.java`, `openapi.yaml`
Branche suggérée : `feature/s6-my-events`
Dépendances : aucune

### 🔧 [SCRUM-134] [FRONT][S6] Onglet Archive dans le dashboard organisateur
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S6 | **Assigné** : Daniel | **SP** : 3 | **Épic** : SCRUM-14 | **Story** : SCRUM-112 (US-23)

\[FRONT\] Sprint 6 — Feature 3 / Tâche 2

1. Dans la page dashboard organisateur (SCRUM-93, `/profile/me/events` ou onglet dans `ProfilePage`) : ajouter un onglet "Archives" avec la liste retournée par `GET /api/users/me/events`.
2. Afficher pour chaque événement : titre, date originale, statut (badge coloré), nombre de participants (`attendingCount`), bouton "Recréer cet événement".
3. Filtres : onglet "Actifs" (PUBLISHED) / "Archives" (CANCELLED + events dont endDate < now) / "Brouillons" (DRAFT).
4. Utiliser TanStack Query pour le fetch (`queryKey: ['user', 'me', 'events']`).

Fichiers touchés : `ProfilePage.tsx` ou nouvelle page `OrganizerDashboardPage.tsx`
Branche suggérée : `feature/s6-archive-front`
Dépendances : SCRUM-133 (BACK endpoint /me/events)

### 🔧 [SCRUM-92] [FRONT][S6] Dashboard statistiques organisateur (vues, participants, graphiques)
**Type :** Tâche · **Story Points :** 5 SP

**\[FRONT\] Sprint 5 – Tâche 1/2**

Créer le dashboard de statistiques pour l'organisateur d'un événement :

* `EventStatsPage.tsx` : page `/events/:id/stats` accessible uniquement à l'organisateur de l'event
* Appel `GET /api/events/{id}/stats` → récupère `{ attendingCount, interestedCount, viewCount }`
* Affichage sous forme de cartes KPI :

    * 👁 Vues totales
    * ✅ Participants (ATTENDING)
    * ⭐ Intéressés
    
* `StatsChart.tsx` : graphique barres ou doughnut (recharts ou Chart.js) montrant la répartition Attending / Interested / Capacity restante
* Indicateur de taux de remplissage : barre de progression `attendingCount / capacity * 100`
* Bouton "Voir les participants" → `GET /api/events/{id}/attendees` → liste déroulante des noms/avatars
* Hook `useEventStats.ts` : chargement stats + rafraîchissement toutes les 60s

**Fichiers touchés :** `EventStatsPage.tsx`, `StatsChart.tsx`, `useEventStats.ts`, `statsApi.ts`, `App.tsx` (route `/events/:id/stats`)
**Branche suggérée :** `feature/s5-stats-dashboard`

---

## Sprint 7 — 28 avr.–2 mai 2026
**Thème :** Admin/modération + co-organisateurs + jobs planifiés + UX polish  
**Total estimé :** 47 SP

### 🚀 [SCRUM-45] [S7] Je veux modérer les événements et mettre en avant certains contenus (US-15, T4, T5)
**Type :** Feature · **Story Points :** — SP

**Sprint 6 · Epic 5 — Administration & Qualité**

En tant qu'administrateur, je veux disposer d'un tableau de bord de modération pour gérer les signalements, supprimer des événements problématiques et mettre en avant des événements stratégiques sur la page d'accueil.

**Critères d'acceptation :**

* Rôle admin : seul un admin peut accéder aux routes /admin/\*
* Un utilisateur ne peut signaler le même event qu'une seule fois
* Actions de modération : Rejeter le signalement / Supprimer l'event (soft-delete)
* Section "Événements mis en avant" sur la page d'accueil (max 5)

**Tâches liées :** SCRUM-32 (BACK), SCRUM-33 (FRONT)

### 📖 [SCRUM-118] [S7][US-29] En tant qu'organisateur, je veux ajouter des co-organisateurs à mon événement, afin de partager la gestion avec mes collègues.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-65] [S7][US-15] En tant qu'utilisateur, je veux signaler un événement contenant des informations inappropriées ou fausses afin que la plateforme reste sûre et digne de confiance.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-66] [S7][US-16] En tant qu'utilisateur, je veux que les événements expirent ou soient masqués automatiquement après leur date afin que la liste reste à jour
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-68] [S7][US-18] En tant qu'utilisateur, je veux que la plateforme supprime automatiquement le contenu répétitivement signalé et effectue un nettoyage périodique des données afin qu'elle reste stable et sécurisée
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-72] [S7][US-T4] En tant qu'admin, je veux un flux de modération simple (liste des signalements, actions dismisser/supprimer) afin d'assurer la qualité des événements publiés
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-73] [S7][US-T5] En tant qu'admin, je veux pouvoir mettre en avant certains événements stratégiques sur la page d'accueil afin d'augmenter leur visibilité.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 🔧 [SCRUM-102] [FRONT][S7] Section « À la une » sur la page d'accueil (remplacement Événements à venir)
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S7 | **Assigné** : — | **SP** : 3 | **Épic** : SCRUM-16 | **Story** : SCRUM-73 (US-T5)

\[FRONT\] Sprint 7 — Section « À la une »

**Problème actuel :**
La section « Événements à venir » de la `LandingPage` affiche les événements publiés récents via `useEvents()` (filtre `status=PUBLISHED` + `endDateFrom=now`, tri chronologique). Il n'y a aucune logique de pertinence — les événements sont simplement listés par date.

**Comportement cible :**
Remplacer la section « Événements à venir » par une section **« À la une »** qui affiche les **6 événements les plus pertinents**, sélectionnés par un double mécanisme :

1. **Admin override** : un événement flaggé `featured = true` par un admin (via SCRUM-95) apparaît en priorité, trié par `featuredAt DESC`.
2. **Popularité automatique** : les slots restants (si < 6 featured) sont remplis par les événements PUBLISHED à venir triés par score de popularité = `attendingCount + favoriteCount` décroissant.

Ce mécanisme garantit que la section fonctionne **dès le départ sans intervention admin** (popularité pure), tout en permettant à un admin de booster un événement stratégique quand nécessaire.

**Implémentation :**

1. **Backend nécessaire (SCRUM-95)** : `GET /api/events/featured` doit être adapté pour retourner :
    * D'abord les events avec `featured = true`, triés par `featuredAt DESC`
    * Puis les events PUBLISHED à venir triés par `attendingCount + favoriteCount` DESC pour compléter jusqu'à 6
    * Paramètre `?limit=6` (défaut 6, max 12)
    * → **Coordonner avec l'assigné de SCRUM-95 pour intégrer la logique de popularité dans l'endpoint**
2. **`LandingPage.tsx`** : remplacer le composant `EventCards` (section « Événements à venir ») par un nouveau composant `FeaturedEventsSection`.
    * Même grille `EventCard` existante (pas de nouveau composant carte) — on réutilise les cards actuelles.
    * Titre de section : « À la une » (via `SectionHeader`).
    * Affiche exactement **6 événements** max (2 rangées × 3 colonnes desktop, responsive comme la grille actuelle).
    * **Pas de bouton « Charger plus »** — c'est une sélection curatée, pas une liste paginée.
    * Si aucun événement disponible → section masquée (pas d'espace vide).
    * Badge optionnel « ✨ À la une » en overlay sur les events qui ont `featured = true` (distinguer les choix admin des events populaires).
3. **`useFeaturedEvents.ts`** : nouveau hook qui appelle `GET /api/events/featured?limit=6`.
    * Retourne `{ events, loading, error }`.
    * Pas de pagination, pas de `loadMore`.
4. **Suppression du code obsolète** :
    * Le composant `EventCards` (qui orchestrait `useEvents` + bouton « Charger plus ») n'est plus utilisé dans la `LandingPage`. Vérifier s'il est consommé ailleurs avant de le supprimer.
    * Le hook `useEvents` reste — il est potentiellement utilisé par d'autres pages.
5. **Skeleton** : réutiliser le skeleton `event-cards` existant (même grille, 6 cards au lieu de 12 — ajuster le nombre de bones si nécessaire).
6. **Tests :**
    * `FeaturedEventsSection.test.tsx` : rendu avec 6 events, rendu vide (section masquée), présence du badge « À la une » sur les events featured.
    * `LandingPage.test.tsx` : vérifier que la section affiche « À la une » et non « Événements à venir ».
7. **Documentation :**
    * `docs/components.md` : mettre à jour la section LandingPage + ajouter `FeaturedEventsSection`.

Fichiers créés : `src/components/event/FeaturedEventsSection.tsx`, `src/hooks/useFeaturedEvents.ts`
Fichiers touchés : `src/pages/LandingPage.tsx`, `docs/components.md`
Branche suggérée : `feature/s7-featured-homepage`
Dépendances : SCRUM-95 (backend featured events — endpoint doit inclure la logique popularité)

### 🔧 [SCRUM-103] [BACK][S7] Job nettoyage auto des événements répétitivement signalés (@Scheduled)
**Type :** Tâche · **Story Points :** 3 SP

**\[BACK\] Sprint 6 – Tâche manquante (US-18)**

Implémenter le nettoyage automatique des événements répétitivement signalés :

* Job Quarkus Scheduler `@Scheduled(cron="0 0 3 * * ?")` (tous les jours à 3h du matin)
* Logique : si un `Event` possède **3 signalements ou plus** avec `status=PENDING` (non traités) → suppression logique automatique (`isActive=false` ou `status=CANCELLED`)
* Le seuil (3) est configurable via `application.properties` (`app.moderation.auto-hide-threshold=3`)
* Notification optionnelle à l'organisateur : création d'une `Notification` de type `EVENT_AUTO_REMOVED`
* Log tracé à chaque exécution : nombre d'events traités, IDs concernés
* `ModerationCleanupJob.java` + `ModerationCleanupService.java`
* Tests : test unitaire sur `ModerationCleanupService` avec des events fictifs (2 reports → pas masqué, 3+ → masqué)

**US couverte :** US-18 — Je veux que la plateforme supprime automatiquement le contenu répétitivement signalé

**Fichiers touchés :** `ModerationCleanupJob.java`, `ModerationCleanupService.java`, `application.properties` (seuil configurable)
**Branche suggérée :** `feature/s6-moderation-cleanup-job`
**Note :** Fichiers totalement distincts de SCRUM-94 (ReportResource) et SCRUM-95 (FeaturedService) — zéro conflit de branche

### 🔧 [SCRUM-135] [FRONT][S7] Re-création d'événement depuis un template (archive)
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S6 | **Assigné** : Daniel | **SP** : 3 | **Épic** : SCRUM-14 | **Story** : SCRUM-112 (US-23)

\[FRONT\] Sprint 6 — Feature 3 / Tâche 3

1. **Bouton "Recréer cet événement"** dans la liste archive : navigue vers `/events/new` avec `useNavigate('/events/new', { state: { template: event } })`.
2. `EventCreatePage.tsx` : à l'initialisation, lire `location.state?.template` (type `Event`). Si présent, appeler `useEventForm` avec les valeurs pré-remplies (titre, description, catégorie, faculté, lieu, mots-clés, websiteUrl, contactEmail — mais `startDate`, `endDate`, `registrationDeadline` vides).
3. Afficher un bandeau informatif "Pré-rempli depuis l'événement \[titre\]" avec un bouton "Effacer le template".
4. Le comportement de soumission est identique à la création standard.

Fichiers touchés : `EventCreatePage.tsx`, `useEventForm.ts`
Branche suggérée : `feature/s6-event-template`
Dépendances : SCRUM-134 (archive front), SCRUM-99 (duplication — coordonner pour cohérence)

### 🔧 [SCRUM-136] [BACK][S7] Entité EventCoOrganizer + endpoints invitation co-organisateurs
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S6 | **Assigné** : Antoine | **SP** : 5 | **Épic** : SCRUM-14 | **Story** : SCRUM-118 (US-29)

\[BACK\] Sprint 6 — Feature 6c / Tâche 1

1. **Entité** `EventCoOrganizer` (PanacheEntity) :

    * `eventId` (Long, @Column(nullable=false))
    * `userId` (UUID, @Column(nullable=false))
    * `status` (CoOrganizerStatus : PENDING / ACCEPTED / DECLINED)
    * `invitedAt` (LocalDateTime, @PrePersist)
    * Contrainte unique `(eventId, userId)`
    
2. **Enum** `CoOrganizerStatus`
3. `EventCoOrganizerService` (@ApplicationScoped, @Transactional) :

    * `invite(Long eventId, String inviterAuth0Id, UUID targetUserId)` : vérifie que l'inviteur est le créateur, crée `EventCoOrganizer` status PENDING. Lance 409 si déjà invité.
    * `accept(Long eventId, String userAuth0Id)`
    * `decline(Long eventId, String userAuth0Id)`
    * `remove(Long eventId, String requesterAuth0Id, UUID targetUserId)` : seul le créateur peut retirer
    * `getCoOrganizers(Long eventId)` : retourner liste `CoOrganizerDTO` (userId, displayName, avatarUrl, status)
    
4. `EventCoOrganizerResource` (@Path("/events/{id}/co-organizers")) : POST /, DELETE /{userId}, GET /, PATCH /me/accept, PATCH /me/decline
5. `UserResource` : ajouter `GET /api/users/me/co-organizer-invitations`
6. `EventService` : modifier `update()`, `delete()`, `publish()` pour accepter les co-organisateurs ACCEPTED.
7. **OpenAPI** : documenter tous les endpoints + `CoOrganizerDTO`.
8. **Tests** : invitation → 201 ; double invitation → 409 ; acceptation → status ACCEPTED.

Fichiers créés/touchés : `EventCoOrganizer.java`, `CoOrganizerStatus.java`, `EventCoOrganizerService.java`, `EventCoOrganizerResource.java`, `CoOrganizerDTO.java`, `EventService.java`, `UserResource.java`, `openapi.yaml`
Branche suggérée : `feature/s6-co-organizers`
Dépendances : aucune

### 🔧 [SCRUM-94] [BACK][S7] Entité Report + endpoints signalement + routes admin modération
**Type :** Tâche · **Story Points :** 5 SP

**\[BACK\] Sprint 6 – Tâche 1/2**

Implémenter le rôle admin et le système de signalement/modération :

* Enum `UserRole` : STUDENT, ORGANIZER, ADMIN (ajout du rôle ADMIN dans l'entité User si pas déjà présent)
* Entité `Report` (PanacheEntity) : `reporterId`, `eventId`, `reason` (enum : SPAM, INAPPROPRIATE, FAKE, OTHER), `description`, `status` (PENDING/REVIEWED/DISMISSED), `createdAt`, `reviewedAt`, `reviewedBy`
* Enum `ReportReason` + `ReportStatus`
* Schéma géré par Hibernate (mode update) — aucune migration nécessaire
* Endpoints signalement :

    * `POST /api/events/{id}/report` → créer un signalement (utilisateur connecté, ne peut pas signaler ses propres events)
    * `GET /api/admin/reports` → liste tous les signalements en attente (ADMIN uniquement), paginée, filtre `?status=`
    * `PATCH /api/admin/reports/{id}` → traiter un signalement (REVIEWED ou DISMISSED) + note de modération
    
* Protection des endpoints admin via `@RolesAllowed("ADMIN")` Quarkus Security
* `ReportService` + `ReportResource` + `AdminReportResource`
* Tests `@QuarkusTest` : signalement, 403 si non-admin sur routes admin

**Fichiers touchés :** `Report.java`, `ReportReason.java`, `ReportStatus.java`, `ReportService.java`, `ReportResource.java`, `AdminReportResource.java`, `ReportDTO.java`
**Branche suggérée :** `feature/s6-report-moderation`

### 🔧 [SCRUM-95] [BACK][S7] Featured events : champ + endpoints admin + GET /api/events/featured (admin override + popularité)
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S7 | **Assigné** : Antoine | **SP** : 3 | **Épic** : SCRUM-16 | **Story** : SCRUM-73 (US-T5)

\[BACK\] Sprint 7 — Featured events avec logique de popularité

**Contexte :**
La page d'accueil va remplacer la section « Événements à venir » (tri chronologique) par une section « À la une » (SCRUM-102) affichant les 6 événements les plus pertinents. L'endpoint `GET /api/events/featured` doit supporter un **double mécanisme** de sélection :

1. **Admin override** : les events flaggés `featured = true` par un admin apparaissent en priorité.
2. **Popularité automatique** : les slots restants sont remplis par les events PUBLISHED à venir triés par score = `attendingCount + favoriteCount` décroissant.

Ce mécanisme garantit que la section fonctionne **dès le départ sans admin actif** (popularité pure), tout en permettant à un admin de booster un event stratégique.

**Implémentation :**

1. **Entité `Event`** : ajouter :
    * `featured` (boolean, default `false`)
    * `featuredAt` (LocalDateTime, nullable)
    * Créer une migration Flyway `V<N>__add_event_featured.sql` dans `backend/src/main/resources/db/migration/` (Hibernate est en `validate` depuis SCRUM-164 — voir [`backend/AGENTS.md`](backend/AGENTS.md) section « Schéma de base de données — Flyway »).
2. **Endpoints admin** (dans `AdminEventResource`, fichier dédié distinct de `AdminReportResource` de SCRUM-94) :
    * `PATCH /api/admin/events/{id}/feature` → `featured = true`, `featuredAt = now()`. `@RolesAllowed("ADMIN")`. Retourne `EventDTO`.
    * `PATCH /api/admin/events/{id}/unfeature` → `featured = false`, `featuredAt = null`. `@RolesAllowed("ADMIN")`. Retourne `EventDTO`.
3. **Endpoint public `GET /api/events/featured`** (`@PermitAll`) :
    * Paramètre `?limit=` (défaut 6, max 12).
    * **Logique de sélection (requête en deux phases)** :
        * Phase 1 : sélectionner les events avec `featured = true AND status = PUBLISHED AND endDate >= now()`, triés par `featuredAt DESC`, limités à `limit`.
        * Phase 2 : si phase 1 retourne < `limit` résultats, compléter avec les events `featured = false AND status = PUBLISHED AND endDate >= now()`, triés par `(attendingCount + favoriteCount) DESC`, en excluant les IDs déjà retournés en phase 1, limités à `limit - phase1.size()`.
    * Le score de popularité est calculé via des sous-requêtes COUNT (même pattern que `attendingCount` / `waitlistedCount` dans `EventService.getAll()`).
    * Retourne `List<EventDTO>` (les events featured admin ont `featured: true` dans le DTO — le frontend distingue visuellement les deux types).
4. **Filtre sur `GET /api/events`** : ajout du paramètre optionnel `?featured=true` pour filtrer (utilisé par le dashboard admin SCRUM-97).
5. **`FeaturedService`** (`@ApplicationScoped`) :
    * `getFeatured(int limit)` : logique deux phases décrite ci-dessus.
    * `feature(Long eventId)` / `unfeature(Long eventId)` : `@Transactional`.
6. **`EventDTO`** : vérifier que `featured` (boolean) est bien mappé dans `EventDTO.from()`.
7. **OpenAPI** : documenter `featured` / `featuredAt` dans le schéma `Event`, les deux endpoints admin, et le paramètre `limit` de `GET /api/events/featured`.
8. **Tests `@QuarkusTest`** :
    * Feature un event → 200, `featured = true`. Unfeature → 200, `featured = false`. 403 si non-admin.
    * `GET /api/events/featured` sans aucun featured → retourne les 6 events les plus populaires.
    * `GET /api/events/featured` avec 2 featured + events populaires → 2 featured en tête + 4 populaires derrière.
    * `GET /api/events/featured?limit=3` → max 3 résultats.

Fichiers créés/touchés : `Event.java` (champs featured/featuredAt), `FeaturedService.java`, `AdminEventResource.java`, `EventDTO.java`, `EventService.java`, `openapi.yaml`
Branche suggérée : `feature/s7-featured-events`
Dépendances : aucune (SCRUM-102 front dépend de cette tâche)

### 🔧 [SCRUM-96] [FRONT][S7] Modale de signalement d'événement (ReportModal)
**Type :** Tâche · **Story Points :** 3 SP

**\[FRONT\] Sprint 6 – Tâche 1/2**

Implémenter le formulaire de signalement côté utilisateur :

* `ReportModal.tsx` : modale de signalement accessible depuis `EventDetailPage.tsx`

    * Bouton "Signaler cet événement" visible pour tout utilisateur connecté (sauf l'organisateur de l'event)
    * Champs : `reason` (select : Spam, Contenu inapproprié, Faux événement, Autre), `description` (textarea optionnelle)
    * Validation : reason obligatoire
    * Appel `POST /api/events/{id}/report` → feedback toast "Merci pour votre signalement"
    * Fermeture automatique après succès
    
* `useReport.ts` : hook gérant l'état de la modale + appel API
* Intégration dans `EventDetailPage.tsx` uniquement (bouton conditionnel basé sur `currentUser.id !== event.organizerId`)

**Fichiers touchés :** `ReportModal.tsx`, `useReport.ts`, `reportApi.ts`, `EventDetailPage.tsx` (ajout bouton + modale)
**Branche suggérée :** `feature/s6-report-modal`

### 🔧 [SCRUM-97] [FRONT][S7] Dashboard admin (modération signalements + gestion featured events)
**Type :** Tâche · **Story Points :** 5 SP

**\[FRONT\] Sprint 6 – Tâche 2/2**

Créer le dashboard d'administration (modération + featured) :

* `AdminPage.tsx` : page `/admin` accessible uniquement au rôle ADMIN (guard de route)
* Section **Modération des signalements** :

    * Tableau des signalements en attente : `GET /api/admin/reports?status=PENDING`
    * Colonnes : Événement signalé, Raison, Signalé par, Date, Actions
    * Boutons "Valider" (REVIEWED) et "Ignorer" (DISMISSED) → `PATCH /api/admin/reports/{id}`
    * Onglets : "En attente" | "Traités"
    
* Section **Événements mis en avant** :

    * Liste des events actuellement featured : `GET /api/events?featured=true`
    * Bouton "Retirer de la mise en avant" → `PATCH /api/admin/events/{id}/unfeature`
    * Barre de recherche pour retrouver un événement à mettre en avant → `PATCH /api/admin/events/{id}/feature`
    
* `useAdminReports.ts` + `useAdminFeatured.ts` : hooks séparés pour chaque section
* Lien "/admin" dans la navbar visible uniquement pour le rôle ADMIN
* Guard de route `AdminRoute.tsx` : redirige vers 403 si non-admin

**Fichiers touchés :** `AdminPage.tsx`, `AdminRoute.tsx`, `useAdminReports.ts`, `useAdminFeatured.ts`, `adminApi.ts`, `Navbar.tsx` (lien conditionnel admin), `App.tsx` (route protégée `/admin`)
**Branche suggérée :** `feature/s6-admin-dashboard`

### 🔧 [SCRUM-98] [BACK][S7] Job expiration automatique des événements passés (@Scheduled Quarkus)
**Type :** Tâche · **Story Points :** 3 SP

**\[BACK\] Sprint 7 – Tâche 1/2**

Implémenter le job d'expiration automatique des événements passés :

* Quarkus Scheduler (`@Scheduled`) : job tournant toutes les heures

    * Sélectionne tous les `Event` avec `status=PUBLISHED` ET `endDate < now()`
    * Passe leur `status` à `EXPIRED` (nouveau statut à ajouter à l'enum `EventStatus`)
    
* Créer une migration Flyway `V<N>__add_event_status_expired.sql` dans `backend/src/main/resources/db/migration/` qui drop+recrée la contrainte `events_status_check` avec la valeur `EXPIRED` ajoutée (Hibernate est en `validate` depuis SCRUM-164 — l'ajout d'une valeur d'enum impose de mettre à jour la CHECK, cf. pattern V1/V7 du repo et [`backend/AGENTS.md`](backend/AGENTS.md) section « Schéma de base de données — Flyway »)
* `EventExpirationJob.java` : classe dédiée annotée `@ApplicationScoped` + `@Scheduled(every="1h")`
* `EventExpirationService.java` : logique de sélection + update en batch (pour éviter les N+1)
* Les événements EXPIRED n'apparaissent plus dans `GET /api/events` par défaut (filtre automatique sur status IN (PUBLISHED))
* Tests : test unitaire sur `EventExpirationService` avec données mockées

**Fichiers touchés :** `EventStatus.java` (valeur EXPIRED), `EventExpirationJob.java`, `EventExpirationService.java`, `EventService.java` (filtre status)
**Branche suggérée :** `feature/s7-expiration-job`

### 🔧 [SCRUM-164] [BACK][S7] Recréer les contraintes CHECK orphelines sur events (faculty, category, status) — ✅ RÉSOLU
**Type :** Tâche · **Story Points :** 2 SP · **Statut : Résolu**

**Sprint** : S7 | **Assigné** : Elie | **SP** : 2 | **Épic** : — | **Story** : —

Résolu en remplaçant le bean `SchemaFixup` par une migration Flyway `V1__reconcile_check_constraints.sql` qui drop+recrée les quatre contraintes (`events_faculty_check`, `events_category_check`, `events_status_check`, `attendances_status_check`) avec les valeurs courantes des enums Java. Hibernate est passé en `validate` (dev/prod). Voir `backend/docs/data-model.md` section « Gestion du schéma — Flyway ».

### 🔧 [SCRUM-165] [FRONT][S7] Redirection post-login vers la page d'origine (returnTo)
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S7 | **Assigné** : — | **SP** : 2 | **Épic** : SCRUM-13 | **Story** : —

\[FRONT\] Sprint 7 — Redirect post-login

**Problème actuel :**
Quand un utilisateur non authentifié accède à une route protégée (ex. `/profile/me`, `/events/new`, `/my-events/favorites`), `PrivateRoute` le redirige vers `/login`. Mais après authentification Auth0, l'utilisateur est renvoyé vers `/profile/me` (valeur **hardcodée** dans `AuthContext.login()` via `appState.returnTo`). La page d'origine souhaitée est perdue.

**Comportement cible :**
Après login, l'utilisateur doit être redirigé vers la page qu'il tentait d'atteindre avant d'être intercepté par `PrivateRoute`.

**Infrastructure existante déjà compatible :**
- `Auth0ProviderWithNavigate.onRedirectCallback(appState)` lit déjà `appState?.returnTo` et navigue dessus (fallback `/`). Le mécanisme Auth0 est prêt.
- Il manque **uniquement** la propagation de l'URL d'origine depuis `PrivateRoute` jusqu'à `loginWithRedirect`.

**Implémentation :**

1. **`PrivateRoute.tsx`** : remplacer `<Navigate to="/login" />` par `<Navigate to="/login" state={{ returnTo: location.pathname + location.search }} />`. Importer `useLocation` depuis `react-router-dom`.
2. **`LoginPage.tsx`** : lire `location.state?.returnTo` via `useLocation()`. Passer cette valeur à `login(returnTo)`.
3. **`AuthContext.tsx`** : modifier `login` pour accepter un paramètre optionnel `returnTo?: string` (défaut `'/'`). Appeler `loginWithRedirect({ appState: { returnTo: returnTo ?? '/' } })` au lieu du `/profile/me` hardcodé.
4. **Cas limites :**
    * Si `returnTo` est absent ou vide → fallback vers `/` (landing page, plus `/profile/me`).
    * Si `returnTo` contient `/login` ou `/login/callback` → ignorer et utiliser `/` (éviter les boucles).
    * Les query params et fragments doivent être préservés dans le `returnTo` (ex. `/events/search?q=sport`).
5. **Tests :**
    * `PrivateRoute.test.tsx` : vérifier que `Navigate` reçoit `state.returnTo` contenant le pathname courant quand l'utilisateur n'est pas authentifié.
    * `LoginPage.test.tsx` : vérifier que `login()` est appelé avec le `returnTo` issu de `location.state` quand il est présent ; avec `/` quand il est absent.
    * `AuthContext.test.tsx` (ou tests existants) : vérifier que `loginWithRedirect` reçoit `appState.returnTo` dynamique au lieu du hardcodé.

Fichiers touchés : `src/components/PrivateRoute.tsx`, `src/pages/login/LoginPage.tsx`, `src/contexts/AuthContext.tsx`
Branche suggérée : `feature/s7-login-redirect-returnto`
Dépendances : aucune

### 🔧 [SCRUM-166] [FRONT][S7] Pages légales /legal/privacy et /legal/terms
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S7 | **Assigné** : — | **SP** : 2 | **Épic** : SCRUM-13 | **Story** : —

\[FRONT\] Sprint 7 — Pages légales

**Problème actuel :**
Le `Footer` affiche deux liens — « Politique de confidentialité » (`/privacy`) et « Conditions générales » (`/terms`) — mais aucune route ni page n'existe pour ces chemins. Cliquer dessus tombe sur le catch-all `NotFoundPage` (404).

**Comportement cible :**
Créer deux pages statiques accessibles publiquement, visuellement cohérentes avec le reste de l'application, et corriger les liens du footer.

**Implémentation :**

1. **Création des pages :**
    * `src/pages/legal/PrivacyPage.tsx` — route `/legal/privacy`
    * `src/pages/legal/TermsPage.tsx` — route `/legal/terms`
2. **Layout et style :**
    * Utiliser `SectionWrapper` (padding `md`, size `md` soit `max-w-3xl`) pour centrer le contenu comme une page de lecture.
    * Utiliser `SectionHeader` (heading `md`, align `center`) avec le titre de la page.
    * Ajouter un composant `Blobs` (ex. `BlobsSubtle`) en background pour la cohérence visuelle.
    * Le contenu textuel est structuré en sections `<h2>` + paragraphes `<p>` avec les classes Tailwind existantes : `text-foreground/70` pour le corps, `text-foreground font-semibold` pour les sous-titres, `space-y-4` pour l'espacement.
    * Les deux pages suivent exactement le même layout — seul le contenu textuel diffère.
3. **Contenu :**
    * **Privacy** : sections typiques — collecte de données (Auth0, email, profil), utilisation (personnalisation, fonctionnement de la plateforme), partage (aucun partage avec des tiers), cookies (Auth0 session uniquement), droits utilisateur (accès, modification, suppression via le profil), contact.
    * **Terms** : sections typiques — objet de la plateforme (UNIGE Events, plateforme universitaire), inscription (via Auth0, compte UNIGE), contenu utilisateur (événements créés, responsabilité de l'organisateur), modération (signalement, suppression automatique), propriété intellectuelle, limitation de responsabilité (projet académique PINFO), contact.
    * Contexte UNIGE : mentionner que c'est un projet académique du cours PINFO (Université de Genève), pas un service commercial. Cela simplifie les clauses légales.
    * Langue : **français** (cohérent avec toute l'UI).
4. **Mise à jour du Footer :**
    * `Footer.tsx` : changer les `href` de `/privacy` vers `/legal/privacy` et de `/terms` vers `/legal/terms`.
    * Remplacer les `<a>` (`TextLink`) par des `<Link>` de `react-router-dom` pour une navigation SPA sans rechargement.
5. **Mise à jour du routeur :**
    * `AppRouter.tsx` : ajouter deux routes publiques (hors `PrivateRoute`) :
        * `<Route path="/legal/privacy" element={<PrivacyPage />} />`
        * `<Route path="/legal/terms" element={<TermsPage />} />`
6. **Tests :**
    * `PrivacyPage.test.tsx` : vérifier le rendu du titre « Politique de confidentialité » et la présence des sections clés (collecte, utilisation, droits).
    * `TermsPage.test.tsx` : vérifier le rendu du titre « Conditions générales » et la présence des sections clés (objet, inscription, modération).
    * `Footer.test.tsx` (si existant, sinon ajouter) : vérifier que les liens pointent vers `/legal/privacy` et `/legal/terms`.
7. **Documentation :**
    * `docs/architecture.md` : ajouter les deux routes dans la table de routage (publiques).
    * `docs/components.md` : ajouter les deux pages dans la section Pages.

Fichiers créés : `src/pages/legal/PrivacyPage.tsx`, `src/pages/legal/TermsPage.tsx`
Fichiers touchés : `src/components/Footer.tsx`, `src/router/AppRouter.tsx`, `docs/architecture.md`, `docs/components.md`
Branche suggérée : `feature/s7-legal-pages`
Dépendances : aucune

### 🔧 [SCRUM-XXX] [FULLSTACK][S7] Identifier les profils utilisateur par `username` plutôt que par UUID
**Type :** Tâche · **Story Points :** 8 SP

**Sprint** : S7 | **Assigné** : — | **SP** : 8 | **Épic** : SCRUM-13 | **Story** : —

\[FULLSTACK\] Sprint 7 — URL de profil user-friendly

**Problème actuel :**
L'URL d'un profil utilisateur est aujourd'hui `/profile/<uuid>`, par exemple `/profile/19f3ab78-0fbf-4cfb-896e-5c0346fabed5`. Cette URL est illisible, impossible à mémoriser ou à partager oralement, et expose un identifiant interne (UUID) au public alors qu'il devrait rester un détail d'implémentation.

Côté backend, l'entité `User` (`backend/src/main/java/ch/unige/events/entity/User.java`) ne possède aucun champ `username`. Côté frontend, le type `User` (`frontend/src/types/user.ts:14`) déclare déjà `username?: string` mais il n'est jamais peuplé par l'API ni utilisé. La route React Router `/profile/:id` accepte uniquement un UUID, et `ProfilePage.tsx:80` détecte le profil propre via `id === currentUser.auth0Id` (incohérence pré-existante : le route param est censé être l'UUID DB, pas l'auth0Id — à clarifier dans le cadre du refactor).

**Comportement cible :**
Chaque utilisateur dispose d'un identifiant public `username` unique, permettant l'accès au profil via `/profile/<username>` (ex. `/profile/jean.dupont`). L'utilisateur peut le définir lui-même depuis la page d'édition de profil ; à défaut, un username est généré automatiquement et garanti unique. L'UUID reste la clé primaire DB et l'identifiant interne ; le username est un identifiant public-facing avec une contrainte d'unicité indépendante.

**Implémentation :**

### Backend

1. **Entité `User` :**
    * Ajouter `@Column(nullable = false, unique = true) public String username` dans `User.java`.
    * Ajouter `findByUsername(String username)` aux finders statiques (équivalent `findByEmail`).
    * Validation Bean Validation : `@NotBlank`, `@Pattern(regexp = "^[a-z0-9._-]{3,30}$")`. Le pattern interdit les majuscules, espaces, caractères Unicode étendus.

2. **Migration + back-fill :**
    * Hibernate étant en `validate`, écrire une migration Flyway `V<N>__add_user_username.sql` dans `backend/src/main/resources/db/migration/` qui :
        1. Ajoute la colonne `username` en `nullable = true`.
        2. Back-fill via génération auto pour tous les `users` existants (voir stratégie ci-dessous).
        3. Bascule la colonne en `NOT NULL UNIQUE`.
    * Stratégie de génération automatique : slug du `displayName` (lowercase, ASCII fold sur les accents, espaces → `.`, retrait des chars hors `[a-z0-9._-]`) ; si `displayName` est null/vide, fallback sur `firstName + "." + lastName` ; si tout est vide, fallback `user`.
    * Anti-collision : **suffixe numérique incrémental** (`jean.dupont`, `jean.dupont2`, `jean.dupont3`…). Préféré au suffixe random car prévisible, lisible, et stable (un re-back-fill ne change pas les usernames existants). Implémentation : boucle `WHILE EXISTS(SELECT 1 FROM users WHERE username = ?)` avec compteur, dans une transaction sérialisable pour éviter les races.
    * Blocklist : interdire les usernames réservés (`me`, `admin`, `api`, `login`, `logout`, `signup`, `register`, `settings`) à l'auto-gen comme à l'update manuel.

3. **Endpoints :**
    * **`PATCH /api/users/me/username`** (nouveau) — body `{ "username": "..." }`. Valide le pattern, vérifie l'unicité, retourne `409 USERNAME_TAKEN` si conflit, `400 USERNAME_INVALID` si pattern KO, `400 USERNAME_RESERVED` si blocklist. Réponse `200` avec le `UserDTO` mis à jour.
    * **`GET /api/users/by-username/{username}`** (nouveau) — lookup case-insensitive (lowercase normalisé). Retourne `UserDTO` ou `404`. Respecte la même règle de visibilité que `GET /api/users/{id}` (champ `profilePublic`).
    * **`HEAD /api/users/by-username/{username}/exists`** (optionnel) — endpoint léger pour le check d'unicité côté frontend (debounce sur l'edit). `200 OK` si pris, `404` si libre. **`[À ARBITRER]`** — alternative : réutiliser le GET ; plus REST mais plus coûteux.

4. **OpenAPI :**
    * `openapi/openapi.yaml` mis à jour **en premier** (règle projet) : ajout du champ `username` dans le schéma `User`, nouveaux endpoints, codes d'erreur documentés.

### Frontend

5. **Types et services (`src/types/user.ts`, `src/services/userService.ts`) :**
    * Le champ `username` existe déjà dans `User` mais devient **non-optionnel** (`username: string`) une fois le back-fill appliqué.
    * Ajouter `getUserByUsername(username: string): Promise<User | null>` qui appelle `GET /api/users/by-username/{username}`.
    * Ajouter `updateUsername(username: string): Promise<User>` qui appelle `PATCH /api/users/me/username`.
    * Ajouter `checkUsernameAvailable(username: string): Promise<boolean>` (HEAD endpoint si retenu, sinon dérivé de `getUserByUsername`).
    * `getUserById` reste pour le redirect transitoire UUID → username.

6. **Routing (`src/router/AppRouter.tsx`) :**
    * Route `/profile/:username` en remplacement de `/profile/:id`.
    * `/profile/me` reste un alias résolu côté composant.
    * **Redirect transitoire UUID → username** : ajouter dans `ProfilePage` une détection regex UUID v4 sur le param ; si UUID détecté, lookup via `getUserById(uuid)`, puis `<Navigate to="/profile/${user.username}" replace />`. Cela préserve les vieux liens externes/en cache. **`[À ARBITRER]`** — durée de vie de ce redirect : permanent ou à supprimer dans 1-2 sprints ?

7. **`ProfilePage.tsx` :**
    * `useParams<{ username: string }>()` au lieu de `{ id: string }`.
    * Logique `isOwnProfile` : `username === 'me' || username === currentUser?.username`.
    * Lookup via `getUserByUsername(username)` (sauf cas `me` ou redirect UUID).

8. **`ProfileEditPage.tsx` :**
    * Ajouter un `FormField` "Nom d'utilisateur" en haut du formulaire (champ visible dès le chargement, valeur initiale = `user.username`).
    * Validation côté client en miroir du backend : pattern `[a-z0-9._-]`, 3-30 chars.
    * Vérification d'unicité **debounced** (300-500ms) via `checkUsernameAvailable` — feedback inline : ✅ disponible / ❌ déjà pris / ⏳ vérification…
    * Le username est mis à jour via `updateUsername` séparément du `updateProfile` global, pour pouvoir afficher proprement les erreurs `USERNAME_TAKEN` / `USERNAME_INVALID` sans bloquer le reste du formulaire. Si non modifié, ne pas re-soumettre.

9. **Mise à jour de tous les liens internes vers profil :**
    * `src/components/user/UserIdentity.tsx:42` : `/profile/${user?.username}` au lieu de `/profile/${user?.id}`.
    * `src/pages/event/EventDetailPage.tsx:441` : `/profile/${organizer.username}` au lieu de `/profile/${organizer.id}`.
    * `src/components/Navbar.tsx:42` : `/profile/me` reste inchangé (alias).
    * Vérifier que les `EventDTO` côté backend incluent bien `username` dans `creator` pour pouvoir construire les liens sans round-trip.

10. **Documentation :**
    * `frontend/docs/components.md` : MAJ section services + section pages (route `/profile/:username`).
    * `frontend/docs/types.md` : `username` passe de optional à required.
    * `frontend/docs/architecture.md` : MAJ table de routage.
    * `backend/docs/data-model.md` : ajouter `username` dans la section `User`, documenter le pattern et la stratégie de génération.
    * `backend/docs/api-contract.md` : nouveaux endpoints documentés.
    * `frontend/docs/sprint-context.md` + `backend/docs/sprint-context.md` : tâche listée en fin de S7.

**Cas limites :**
* **Username pris au moment du PATCH** (race entre check d'unicité debounced et submit) → backend retourne `409 USERNAME_TAKEN`, frontend affiche l'erreur sans naviguer.
* **Username modifié pendant qu'un onglet ouvert affichait l'ancien lien** → `GET /by-username/{ancien}` retourne `404`, ProfilePage affiche "Profil introuvable". Acceptable (impossible de rediriger sans historique).
* **Migration sur user existant avec `displayName`, `firstName` et `lastName` tous vides** (Auth0 sans onboarding terminé) → fallback `user` avec suffixe numérique (`user`, `user2`…).
* **Username avec accents lors du back-fill** (`displayName = "François Müller"`) → ASCII fold → `francois.muller`. Tester explicitement.
* **Username = mot réservé** (`me`, `admin`, etc.) : interdire via blocklist côté backend (autoriserait sinon une collision avec le route alias `/profile/me`).
* **Changement de username post-déploiement** : faut-il garder l'historique pour un redirect 301 sur l'ancien ? **`[À ARBITRER]`** — préférence : non au S7, à ajouter ultérieurement si besoin.

**Tests :**

* **Backend (UserResourceTest, UserServiceCoverageTest) :**
    * `PATCH /users/me/username` happy path, `409` si pris, `400` si pattern invalide, `400` si dans la blocklist, `401` si non authentifié.
    * `GET /users/by-username/{username}` happy path, `404` si inexistant, case-insensitive (`Jean.Dupont` trouve `jean.dupont`), respect du `profilePublic`.
    * Génération auto : slug correct depuis `displayName`, fallback firstName/lastName, fallback `user`, ASCII fold sur accents, suffixe numérique correct sur collision (test avec 5 users `Jean Dupont` consécutifs).
    * Migration : test d'intégration qui pré-crée des users sans username, lance le back-fill, vérifie que tous ont un username unique non-null.
    * Blocklist : `me`, `admin`, etc. rejetés en update.

* **Frontend :**
    * `userService.test.ts` : couverture des 3 nouvelles fonctions (URL, params, retour).
    * `ProfilePage.test.tsx` : lookup par username, redirect UUID → username, gestion `404`, alias `me`.
    * `ProfileEditPage.test.tsx` : champ username pré-rempli, validation pattern, debounce du check d'unicité, gestion `409`, gestion succès.
    * `EventDetailPage.test.tsx` / `UserIdentity.test.tsx` : lien organizer/user pointe vers `/profile/<username>`.
    * `AppRouter.test.tsx` : route `/profile/:username` et redirect UUID transitoire.

**Fichiers touchés :**

Backend : `src/main/java/ch/unige/events/entity/User.java`, `service/UserService.java`, `resource/UserResource.java`, `dto/UserDTO.java`, nouvelle migration sous `src/main/resources/db/migration/`, tests associés, `openapi/openapi.yaml`, `backend/docs/data-model.md`, `backend/docs/api-contract.md`, `backend/docs/sprint-context.md`.

Frontend : `src/types/user.ts`, `src/services/userService.ts`, `src/router/AppRouter.tsx`, `src/pages/profile/ProfilePage.tsx`, `src/pages/profile/ProfileEditPage.tsx`, `src/components/user/UserIdentity.tsx`, `src/pages/event/EventDetailPage.tsx`, tests associés, `frontend/docs/components.md`, `frontend/docs/types.md`, `frontend/docs/architecture.md`, `frontend/docs/sprint-context.md`.

Branche suggérée : `feature/s7-profile-username-url`
Dépendances : aucune. Compatible avec les tickets S7 en cours (SCRUM-118 co-organisateurs touche `User` mais sur des champs différents — résolution de conflit triviale).

**Points à arbitrer (recommandations PO) :**
* Préfixe `@` dans l'URL (`/profile/@jean.dupont` vs `/profile/jean.dupont`) — recommandation : **sans `@`**, plus simple.
* Endpoint dédié `PATCH /users/me/username` vs extension de `PUT /users/me` — recommandation : **endpoint dédié** (granularité d'erreur, appel indépendant pour le live-check).
* `HEAD /by-username/{username}/exists` vs réutilisation du GET — recommandation : **endpoint dédié léger**, mais OK de réutiliser le GET si on veut minimiser la surface API.
* Durée de vie du redirect transitoire UUID → username — recommandation : **permanent** (peu de coût, robuste aux liens en cache).
* Case-sensitivity du username — recommandation : **stockage lowercase, lookup case-insensitive**.
* Historique des anciens usernames pour redirects après changement — recommandation : **non au S7**.
* Blocklist exacte des usernames réservés — recommandation : `me`, `admin`, `api`, `login`, `logout`, `signup`, `register`, `settings` au minimum.

---

## Sprint 8 — 5–9 mai 2026
**Thème :** Notifications + Follow/Comment back + Récurrence + Duplication  
**Total estimé :** 37 SP

### 🚀 [SCRUM-46] [S8] Je veux des notifications, dupliquer des events et l'expiration automatique (US-16, 17, 18, T2)
**Type :** Feature · **Story Points :** — SP

**Sprint 7 · Epic 4 & 5 — Finitions fonctionnelles**

En tant qu'utilisateur, je veux recevoir des notifications pour les événements auxquels je suis inscrit, pouvoir dupliquer un événement récurrent, et que les événements passés disparaissent automatiquement de la liste, afin de rester informé et gagner du temps.

**Critères d'acceptation :**

* Job planifié (@Scheduled) désactive automatiquement les events dont endDate < now()
* Duplication réservée au créateur, retourne un draft avec "\[COPY\]" dans le titre
* Notifications in-app : types EVENT_UPDATED, EVENT_CANCELLED, EVENT_REMINDER
* Badge dans le header avec compteur de non-lus, polling 30s

**Tâches liées :** SCRUM-34 (BACK), SCRUM-35 (FRONT)

### 📖 [SCRUM-110] [S8][US-21] En tant qu'utilisateur, je veux suivre d'autres utilisateurs (ou envoyer une demande de suivi sur un profil privé), afin de rester informé de leur activité.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-111] [S8][US-22] En tant qu'utilisateur connecté, je veux poster et lire des commentaires sur les événements, afin d'interagir avec la communauté autour d'un événement.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-116] [S8][US-27] En tant qu'organisateur, je veux créer des événements récurrents, afin de ne pas dupliquer manuellement chaque occurrence.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-67] [S8][US-17] En tant qu'utilisateur, je veux recevoir des notifications pour les événements que j'ai marqués ('Interested'/'Attending') lorsqu'ils approchent, sont mis à jour ou annulés, afin de rester informé
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-70] [S8][US-T2] En tant qu'organisateur, je veux dupliquer un événement afin de gagner du temps pour mes événements récurrents
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 🔧 [SCRUM-137] [FRONT][S8] UI co-organisateurs dans EventForm et EventDetailPage
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S6 | **Assigné** : Viona | **SP** : 3 | **Épic** : SCRUM-14 | **Story** : SCRUM-118 (US-29)

\[FRONT\] Sprint 6 — Feature 6c / Tâche 2

1. `EventForm.tsx` (section édition uniquement — l'event doit exister) : ajouter section "Co-organisateurs" avec :

    * Champ de recherche utilisateur (appel `GET /api/users/search?q=`)
    * Liste des co-organisateurs ajoutés avec statut (chip "En attente" / "Accepté") et bouton ×
    * Bouton "Inviter" → `POST /api/events/{id}/co-organizers`
    
2. `EventDetailPage.tsx` : section "Équipe organisatrice" affichant l'organisateur principal + co-organisateurs ACCEPTED (avatar, displayName).
3. **Badge/indicateur dans ProfilePage ou Navbar** : si l'utilisateur a des invitations en attente (`GET /api/users/me/co-organizer-invitations`), afficher un indicateur. Boutons Accepter / Décliner.

Fichiers touchés : `EventForm.tsx`, `EventDetailPage.tsx`, `ProfilePage.tsx`
Branche suggérée : `feature/s6-co-organizers-front`
Dépendances : SCRUM-136 (BACK co-organisateurs)

### 🔧 [SCRUM-138] [BACK][S8] Entité Follow + endpoints follow/unfollow/demandes/listes
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S6 | **Assigné** : Elie | **SP** : 5 | **Épic** : SCRUM-13 | **Story** : SCRUM-109 (US-20) / SCRUM-110 (US-21)

\[BACK\] Sprint 6 — Feature 1 / Tâche 1

1. **Entité** `Follow` (PanacheEntity) :

    * `followerId` (UUID, @Column nullable=false)
    * `followedId` (UUID, @Column nullable=false)
    * `status` (FollowStatus : PENDING / ACCEPTED)
    * `createdAt` (LocalDateTime, @PrePersist)
    * Contrainte unique `(followerId, followedId)`
    
2. **Enum** `FollowStatus`
3. `FollowService` (@ApplicationScoped, @Transactional) :

    * `follow(String followerAuth0Id, UUID followedId)` : si `profilePublic = true` → ACCEPTED direct ; si privé → PENDING. 409 si déjà suivi.
    * `unfollow(String followerAuth0Id, UUID followedId)`
    * `acceptRequest(String targetAuth0Id, Long followId)`
    * `rejectRequest(String targetAuth0Id, Long followId)`
    * `getFollowers(UUID userId, int page, int size)`
    * `getFollowing(UUID userId, int page, int size)`
    * `getPendingRequests(String auth0Id)`
    
4. `FollowResource` : POST /api/users/{id}/follow, DELETE /api/users/{id}/follow, GET /api/users/{id}/followers, GET /api/users/{id}/following, GET /api/users/me/follow-requests, PATCH /api/follow-requests/{followId}/accept, PATCH /api/follow-requests/{followId}/reject
5. `UserPublicResponse` : enrichir avec `followerCount`, `followingCount`, `followStatus` (null / "PENDING" / "ACCEPTED").
6. **OpenAPI** : documenter tous les endpoints + `FollowDTO`.
7. **Tests** : suivi profil public → ACCEPTED ; suivi profil privé → PENDING ; double suivi → 409.

Fichiers créés/touchés : `Follow.java`, `FollowStatus.java`, `FollowService.java`, `FollowResource.java`, `FollowDTO.java`, `UserPublicResponse.java`, `openapi.yaml`
Branche suggérée : `feature/s6-follow`
Dépendances : aucune

### 🔧 [SCRUM-139] [BACK][S8] Entité Comment + endpoints CRUD commentaires événements
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S6 | **Assigné** : Antoine | **SP** : 5 | **Épic** : SCRUM-16 | **Story** : SCRUM-111 (US-22)

\[BACK\] Sprint 6 — Feature 2 / Tâche 1

1. **Entité** `Comment` (PanacheEntity) :

    * `content` (String TEXT, @NotBlank, @Size(max=2000))
    * `author` (@ManyToOne(fetch=LAZY) User, @JoinColumn("author_id"))
    * `event` (@ManyToOne(fetch=LAZY) Event, @JoinColumn("event_id"))
    * `parentComment` (@ManyToOne(fetch=LAZY) Comment nullable, @JoinColumn("parent_comment_id"))
    * `likeCount` (int, default 0)
    * `createdAt` (LocalDateTime, @PrePersist)
    * Index sur `event_id` + `parent_comment_id`
    
2. `CommentService` (@ApplicationScoped, @Transactional) :

    * `post(String auth0Id, Long eventId, String content, Long parentCommentId)` : valider profondeur max 1 niveau
    * `getByEvent(Long eventId, int page, int size)` : top-level + replies imbriquées
    * `delete(String auth0Id, Long commentId)` : auteur OU organisateur de l'event
    
3. `CommentResource` : POST /api/events/{id}/comments, GET /api/events/{id}/comments, DELETE /api/comments/{id}
4. `CommentDTO` : id, content, authorId, authorDisplayName, authorAvatarUrl, authorIsOrganizer (boolean), likeCount, likedByMe (boolean), createdAt, replies (List<CommentDTO>)
5. **OpenAPI** : documenter endpoints + `CommentDTO` + `CreateCommentRequest`.
6. **Tests** : poster → 201 ; supprimer par auteur → 204 ; supprimer par tiers → 403 ; supprimer par organisateur → 204 ; répondre à une réponse → 400.

Fichiers créés/touchés : `Comment.java`, `CommentService.java`, `CommentResource.java`, `CommentDTO.java`, `CreateCommentRequest.java`, `openapi.yaml`
Branche suggérée : `feature/s6-comments`
Dépendances : aucune

### 🔧 [SCRUM-147] [BACK][S8] Récurrence sur Event + génération d'occurrences
**Type :** Tâche · **Story Points :** 8 SP

**Sprint** : S7 | **Assigné** : Antoine | **SP** : 8 | **Épic** : SCRUM-14 | **Story** : SCRUM-116 (US-27)

\[BACK\] Sprint 7 — Feature 6a / Tâche 1

1. **Entité** `Event` : ajouter :

    * `parentEventId` (Long, nullable — référence vers l'événement parent)
    * `recurrenceRule` (String, nullable — ex. `FREQ=WEEKLY;BYDAY=MO;UNTIL=20260601`)
    
2. `RecurrenceFrequency` (enum) : `WEEKLY`, `BIWEEKLY`, `MONTHLY`
3. `CreateEventRequest` : ajouter section optionnelle `recurrence` :
  `{ "frequency": "WEEKLY", "endDate": "2026-06-01", "maxOccurrences": 10 }`
4. `EventService.createRecurring()` (@Transactional) :

    * Créer l'événement parent (template)
    * Générer N occurrences (max 52) avec `startDate`/`endDate` décalées selon la fréquence
    * Chaque occurrence est un `Event` standard avec `parentEventId` = id du parent
    * Commit atomique : si une occurrence échoue, tout rollback
    
5. `EventService.create()` : déléguer à `createRecurring()` si `recurrenceRule` présent dans le body.
6. `GET /api/events/{id}/occurrences` → liste des événements avec `parentEventId = id`
7. **OpenAPI** : ajouter `parentEventId`, `recurrenceRule` dans le schéma `Event`, la structure `recurrence` dans `CreateEventRequest`, le nouvel endpoint.
8. **Tests** : création récurrente hebdo sur 4 semaines → 4 occurrences + 1 parent ; vérifier les dates.

Fichiers touchés : `Event.java`, `EventService.java`, `EventResource.java`, `CreateEventRequest.java`, `EventDTO.java`, `openapi.yaml`
Branche suggérée : `feature/s7-recurrence`
Dépendances : aucune

### 🔧 [SCRUM-80] [FRONT][S8] Cloche notifications + badge header
**Type :** Tâche · **Story Points :** 5 SP

**\[FRONT\] Sprint 7 – Tâche 1/2**

Intégrer le centre de notifications in-app dans le header :

* Icône cloche dans la navbar avec badge rouge affichant le nombre de notifs non lues
* Dropdown/panel listant les notifications (type, message, date, lien vers l'event)
* Polling `GET /api/notifications` toutes les 30 s (ou SSE si dispo)
* Action « Tout marquer comme lu » → `PUT /api/notifications/read-all`
* Mise à jour optimiste du badge au clic

**Fichiers touchés :** `NotificationBell.tsx`, `NotificationPanel.tsx`, `useNotifications.ts` (hook), `Header.tsx`
**Branche suggérée :** `feature/s7-notification-bell`

### 🔧 [SCRUM-81] [FRONT][S8] Bouton Dupliquer + gestion globale des erreurs (Error Boundaries, 404/403)
**Type :** Tâche · **Story Points :** 3 SP

**\[FRONT\] Sprint 7 – Tâche 2/2**

Bouton Dupliquer sur la page détail organisateur + gestion globale des erreurs :

* Bouton « Dupliquer » sur la page détail d'un event (organisateur uniquement) → `POST /api/events/{id}/duplicate`
* Redirection vers le formulaire pré-rempli après duplication
* **Error Boundaries React** : composant `AppErrorBoundary` englobant les routes principales
* Pages dédiées `404.tsx` (route inconnue) et `403.tsx` (accès non autorisé)
* Intercepteur Axios/fetch global pour rediriger vers 403/404 selon le code HTTP reçu

**Fichiers touchés :** `DuplicateButton.tsx`, `AppErrorBoundary.tsx`, `NotFoundPage.tsx`, `ForbiddenPage.tsx`, `apiClient.ts` (intercepteur)
**Branche suggérée :** `feature/s7-duplicate-error-handling`

### 🔧 [SCRUM-99] [BACK][S8] Endpoint duplication d'événement + système de notifications in-app
**Type :** Tâche · **Story Points :** 8 SP

**\[BACK\] Sprint 7 – Tâche 2/2**

Implémenter la duplication d'événements et le système de notifications in-app :

**Duplication :**

* `POST /api/events/{id}/duplicate` → crée une copie de l'événement avec `status=DRAFT`, `title="Copie de {titre}"`, `startDate` et `endDate` décalées de +7 jours par défaut
* Retourne 201 + le nouvel `EventDTO` (avec le nouvel id)
* Seul l'organisateur propriétaire peut dupliquer (403 sinon)

**Notifications :**

* Entité `Notification` (PanacheEntity) : `userId`, `type` (enum), `message`, `eventId` (nullable), `read` (boolean, default false), `createdAt`
* Enum `NotificationType` : EVENT_CANCELLED, EVENT_UPDATED, EVENT_REMINDER, NEW_ATTENDEE
* Créer une migration Flyway `V<N>__create_notifications.sql` dans `backend/src/main/resources/db/migration/` (Hibernate est en `validate` depuis SCRUM-164 — voir [`backend/AGENTS.md`](backend/AGENTS.md) section « Schéma de base de données — Flyway ». Pour `NotificationType`, poser la CHECK constraint sur la colonne `type` avec les 4 valeurs ; toute future modification de l'enum exigera un nouveau `V<N+1>__…` qui drop+recrée la CHECK.)
* Endpoints :

    * `GET /api/notifications` → liste des notifs de l'utilisateur connecté (non lues en premier), paginée
    * `PUT /api/notifications/{id}/read` → marquer une notif comme lue
    * `PUT /api/notifications/read-all` → tout marquer comme lu
    
* `NotificationService` : méthode `notifyAttendees(eventId, type, message)` appelée depuis `EventService` lors d'un update ou cancel
* Tests `@QuarkusTest` : duplication (vérification offset dates), création notif, marquage lu

**Fichiers touchés :** `Notification.java`, `NotificationType.java`, `NotificationService.java`, `NotificationResource.java`, `NotificationDTO.java`, `EventService.java` (appel notifyAttendees)
**Branche suggérée :** `feature/s7-duplicate-notifications`

---

## Sprint 9 — 12–16 mai 2026
**Thème :** Follow/Comment front + Attachments + Profil public + Feed timeline  
**Total estimé :** 44 SP

### 🚀 [SCRUM-160] [S9] Je veux consulter les profils publics, suivre des utilisateurs et interagir via commentaires et fichiers joints (US-20, 21, 22, 28, 31, 32)
**Type :** Feature · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-109] [S9][US-20] En tant qu'utilisateur connecté, je veux consulter le profil public d'un autre utilisateur, afin de découvrir ses événements et décider de le suivre.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-162] [S9][US-31] En tant qu'organisateur, je veux joindre des fichiers (PDF, documents) à mon événement, afin que les participants aient accès aux ressources nécessaires.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-163] [S9][US-32] En tant qu'utilisateur, je veux recevoir des notifications lorsque quelqu'un me suit ou accepte ma demande de suivi, afin d'être informé de l'évolution de mon réseau.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 🔧 [SCRUM-167] [FRONT][S9] Page Feed — fil chronologique d'événements (timeline verticale + EventFeedCard)
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S9 | **Assigné** : — | **SP** : 5 | **Épic** : SCRUM-16 | **Story** : —

\[FRONT\] Sprint 9 — Page Feed timeline

**Concept :**
Nouvelle page `/feed` accessible depuis la navbar, présentant tous les événements à venir sous forme de **fil chronologique vertical** (style réseau social / timeline). Les événements sont groupés par **date calendrier** (`startDate`), du plus proche au plus lointain, avec une timeline visuelle à gauche et des cartes événement larges à droite.

**Layout de la timeline :**
```
┌──────────────────────────────────────────────┐
│  [Toggle: Tous | Mes abonnements]             │
├──────────────────────────────────────────────┤
│                                              │
│  ● ── Aujourd'hui, 28 avril 2026 ────────── │
│  │                                           │
│  │   ┌─────────────────────────────────┐     │
│  │   │  EventFeedCard (large)          │     │
│  │   │  Bannière | Titre, lieu, heure  │     │
│  │   │  Catégorie, faculté, capacity   │     │
│  │   └─────────────────────────────────┘     │
│  │                                           │
│  │   ┌─────────────────────────────────┐     │
│  │   │  EventFeedCard (large)          │     │
│  │   └─────────────────────────────────┘     │
│  │                                           │
│  ● ── Mercredi 30 avril 2026 ────────────── │
│  │                                           │
│  │   ┌─────────────────────────────────┐     │
│  │   │  EventFeedCard (large)          │     │
│  │   └─────────────────────────────────┘     │
│  │                                           │
│  ● ── Vendredi 2 mai 2026 ──────────────── │
│  │   ...                                     │
│  ▼   (infinite scroll)                       │
└──────────────────────────────────────────────┘
```

**Implémentation :**

1. **Page `FeedPage.tsx`** — route `/feed`, publique (les events sont publics) :
    * En-tête avec titre « Fil d'événements » et toggle segmenté « Tous » / « Mes abonnements ».
    * Le toggle « Mes abonnements » est **désactivé visuellement** (grisé + tooltip « Bientôt disponible ») tant que le filtre backend `followedOnly` n'est pas implémenté (SCRUM-168). Il est activable dès que l'endpoint le supporte.
    * Layout : timeline verticale à gauche + contenu à droite.
2. **Composant `Timeline.tsx`** — structure visuelle du fil :
    * **Trait vertical** : `div` fin (2-3px) coloré `bg-border` (ou `bg-accent/20`), positionné à gauche via CSS (`absolute` ou `border-left` sur le container).
    * **Marqueur de date** : un point (`●`, `w-3 h-3 rounded-full bg-accent`) positionné sur le trait, suivi d'un label de date formaté en français (`Intl.DateTimeFormat('fr-CH', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })`). Cas spéciaux : « Aujourd'hui », « Demain » pour les deux premières dates si applicables.
    * **Segment entre marqueurs** : le trait continue entre les groupes de cartes.
    * Responsive : sur mobile (`< md`), le trait passe en bordure gauche fine et les cartes prennent toute la largeur.
3. **Composant `EventFeedCard.tsx`** — carte événement large pour le fil :
    * Layout horizontal sur desktop : bannière à gauche (aspect 16:9, `w-48 h-28` ou similaire) + infos à droite.
    * Infos : titre (tronqué `line-clamp-2`), lieu (`MapPin`), heure de début (`Clock`), catégorie + faculté (badges existants).
    * Actions inline : `FavoriteButton` (étoile), indicateur capacité (« X places restantes » ou « Complet »).
    * Glassmorphism cohérent avec `EventCard` existant (`bg-background/60 backdrop-blur-xl border-border`).
    * Clic sur la carte → `/events/:id`.
    * Responsive : sur mobile, layout vertical (bannière en haut, infos en dessous).
4. **Hook `useFeed.ts`** — chargement paginé :
    * Appelle `GET /api/events?status=PUBLISHED&endDateFrom=<now>&page=X&size=20` trié par `startDate ASC`.
    * Le frontend **groupe les résultats par date** (`startDate` tronqué au jour) côté client.
    * Infinite scroll via `IntersectionObserver` sur un sentinel en bas de page.
    * Gère la fusion des pages : si la dernière carte de la page N et la première de la page N+1 tombent le même jour, elles apparaissent dans le même groupe.
    * Retourne `{ groups: Array<{ date: string, events: Event[] }>, loading, error, hasMore, loadMore }`.
    * Futur : paramètre `followedOnly?: boolean` transmis à l'API quand SCRUM-168 est implémenté.
5. **Dates sans événements** : les dates intermédiaires sans événements sont **sautées**. Le fil passe directement au prochain jour qui a des événements.
6. **Route + Navbar** :
    * `AppRouter.tsx` : ajouter `<Route path="/feed" element={<FeedPage />} />` (publique).
    * `Navbar.tsx` : ajouter un lien « Fil » (icône `Rss` ou `LayoutList` de lucide-react) dans la navigation principale.
7. **Skeleton** : créer un skeleton `feed` (bones manuels) reproduisant 2-3 marqueurs de date + 3-4 cartes large.
8. **État vide** : si aucun événement à venir → message centré « Aucun événement à venir pour le moment » avec illustration.
9. **Tests** : `FeedPage.test.tsx`, `EventFeedCard.test.tsx`, `Timeline.test.tsx`, `useFeed.test.ts` (groupement par date, fusion inter-pages, loadMore).
10. **Documentation** : `docs/architecture.md` (route `/feed`), `docs/components.md` (`FeedPage`, `Timeline`, `EventFeedCard`, `useFeed`).

Fichiers créés : `src/pages/FeedPage.tsx`, `src/components/feed/Timeline.tsx`, `src/components/feed/EventFeedCard.tsx`, `src/hooks/useFeed.ts`
Fichiers touchés : `src/router/AppRouter.tsx`, `src/components/Navbar.tsx`, `docs/architecture.md`, `docs/components.md`
Branche suggérée : `feature/s9-feed-timeline`
Dépendances : aucune pour la v1. Le toggle « Mes abonnements » dépend de SCRUM-138 (Follow, S8) + SCRUM-168 (filtre backend)

### 🔧 [SCRUM-168] [BACK][S9] Filtre followedOnly sur GET /api/events (feed abonnements)
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S9 | **Assigné** : — | **SP** : 3 | **Épic** : SCRUM-13 | **Story** : SCRUM-110 (US-21)

\[BACK\] Sprint 9 — Filtre followedOnly pour le feed

**Contexte :**
La page Feed (SCRUM-167) affiche les événements à venir en fil chronologique. Un toggle « Tous » / « Mes abonnements » permet de ne voir que les événements créés par les utilisateurs que l'on suit. Ce filtre nécessite un paramètre backend.

**Prérequis :** L'entité `Follow` et les endpoints follow/unfollow doivent être implémentés (SCRUM-138, Sprint 8).

**Implémentation :**

1. **`GET /api/events`** : ajouter le paramètre optionnel `?followedOnly=true` (`@QueryParam`).
    * Si `followedOnly=true` et l'utilisateur est authentifié :
        * Récupérer la liste des `followedId` via `Follow.find("followerId = ?1 AND status = ?2", userId, FollowStatus.ACCEPTED)` → extraire les UUIDs.
        * Ajouter une condition JPQL `e.creator.id IN :followedIds` au filtre existant.
        * Si l'utilisateur ne suit personne → retourner une liste vide (pas d'erreur).
    * Si `followedOnly=true` et l'utilisateur n'est **pas** authentifié → 401.
    * Si `followedOnly` absent ou `false` → comportement inchangé (tous les events).
2. **`EventService.getAll()`** : ajouter le paramètre `followedIds: List<UUID>` (nullable). Si non-null et non-vide, ajouter la condition JPQL. Si non-null et vide, court-circuiter avec un résultat vide.
3. **`EventResource.getAll()`** : lire `followedOnly` depuis `@QueryParam`. Si `true`, récupérer l'utilisateur authentifié via `SecurityIdentity`, puis charger les IDs suivis via `FollowService` ou `Follow.findAcceptedFollowedIds(UUID followerId)`.
4. **OpenAPI** : ajouter le paramètre `followedOnly` (boolean, optional, default false) sur `GET /api/events`. Documenter le comportement 401 si non authentifié.
5. **Tests `@QuarkusTest`** :
    * `followedOnly=true` authentifié, suit 2 users avec events → retourne uniquement ces events.
    * `followedOnly=true` authentifié, ne suit personne → retourne `[]`.
    * `followedOnly=true` non authentifié → 401.
    * `followedOnly` absent → comportement inchangé.
    * Combinaison avec les autres filtres (`status`, `category`, `endDateFrom`, etc.).

Fichiers touchés : `EventResource.java`, `EventService.java`, `openapi.yaml`
Branche suggérée : `feature/s9-events-followed-only`
Dépendances : SCRUM-138 (entité Follow, Sprint 8)

### 🔧 [SCRUM-140] [BACK][S9] Notifications Follow (NEW_FOLLOWER, FOLLOW_REQUEST, FOLLOW_ACCEPTED)
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S7 | **Assigné** : Elie | **SP** : 3 | **Épic** : SCRUM-13 | **Story** : SCRUM-110 (US-21)

\[BACK\] Sprint 7 — Feature 1 / Tâche 2

Une fois l'infrastructure `Notification` disponible (SCRUM-99, S7), brancher les notifications de suivi :

1. `FollowService.follow()` :

    * Si profil public → créer `Notification` type `NEW_FOLLOWER` pour la cible
    * Si profil privé → créer `Notification` type `FOLLOW_REQUEST` pour la cible (avec `followId` dans les métadonnées)
    
2. `FollowService.acceptRequest()` → créer `Notification` type `FOLLOW_ACCEPTED` pour l'initiateur
3. Ajouter les types `NEW_FOLLOWER`, `FOLLOW_REQUEST`, `FOLLOW_ACCEPTED` à l'enum `NotificationType`
4. Le corps de notification doit inclure `actorId` (UUID) et `followId` (pour afficher Accepter/Refuser depuis la cloche).
5. **OpenAPI** : mettre à jour le schéma `Notification` avec les nouveaux types.
6. **Tests** : vérifier que les notifications sont créées dans chaque scénario.

Fichiers touchés : `FollowService.java`, `NotificationType.java`, `openapi.yaml`
Branche suggérée : `feature/s7-follow-notifications`
Dépendances : SCRUM-138 (Entité Follow, S6), Infrastructure Notification (SCRUM-99, S7)

### 🔧 [SCRUM-141] [FRONT][S9] Page profil public complète (/profile/:id)
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S7 | **Assigné** : Viona | **SP** : 5 | **Épic** : SCRUM-13 | **Story** : SCRUM-109 (US-20)

\[FRONT\] Sprint 7 — Feature 1 / Tâche 3

Compléter `ProfilePage.tsx` pour afficher le profil complet :

1. **Profil public** (`profilePublic = true` ou `followStatus = ACCEPTED`) : afficher bannière, avatar, displayName, faculté, niveau d'étude, bio, compteurs followers/following, liste des événements créés (`GET /api/events?organizerId=`), liste des participations publiques.
2. **Profil privé** (non follower) : afficher uniquement l'avatar et "Ce profil est privé." avec bouton "Demande de suivi envoyée" si PENDING.
3. **Route** `/profile/:id` : vérifier que l'UUID est passé correctement.
4. Layout : bannière full-width en haut, avatar en chevauchement (style LinkedIn), infos en dessous.
5. Utiliser TanStack Query avec `queryKey: ['user', id]`.

Fichiers touchés : `ProfilePage.tsx`
Branche suggérée : `feature/s7-profile-public`
Dépendances : SCRUM-120 (bannerUrl), SCRUM-138 (followStatus dans UserPublicResponse)

### 🔧 [SCRUM-142] [FRONT][S9] Bouton Suivre / Ne plus suivre + gestion demandes reçues
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S7 | **Assigné** : Viona | **SP** : 3 | **Épic** : SCRUM-13 | **Story** : SCRUM-110 (US-21)

\[FRONT\] Sprint 7 — Feature 1 / Tâche 4

1. `FollowButton.tsx` (nouveau composant) : bouton à 3 états :

    * `null` → "Suivre" (POST /api/users/{id}/follow)
    * `PENDING` → "Demande envoyée" (grisé, tooltip "Cliquer pour annuler" → DELETE)
    * `ACCEPTED` → "Abonné" (hover → "Se désabonner" → DELETE)
    
2. **Intégration dans** `ProfilePage.tsx` : afficher `FollowButton` si ce n'est pas son propre profil.
3. **Panneau demandes reçues** : dans `ProfilePage.tsx` (vue `me`) : lister les demandes PENDING (`GET /api/users/me/follow-requests`) avec boutons Accepter / Refuser.
4. TanStack Query : invalider `['user', id]` après follow/unfollow pour mettre à jour les compteurs.

Fichiers créés/touchés : `src/components/user/FollowButton.tsx`, `ProfilePage.tsx`
Branche suggérée : `feature/s7-follow-button`
Dépendances : SCRUM-138 (BACK Follow), SCRUM-141 (ProfilePage)

### 🔧 [SCRUM-143] [FRONT][S9] Pages / modal listes followers et following
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S7 | **Assigné** : Daniel | **SP** : 2 | **Épic** : SCRUM-13 | **Story** : SCRUM-110 (US-21)

\[FRONT\] Sprint 7 — Feature 1 / Tâche 5

1. Cliquer sur le compteur "X followers" ou "X abonnements" dans `ProfilePage.tsx` → ouvre une modal ou navigue vers `/profile/:id/followers` | `/profile/:id/following`.
2. Lister les utilisateurs avec `UserAvatar` + `displayName` + bouton `FollowButton` pour chacun.
3. Pagination infinie ou bouton "Charger plus" (TanStack Query `useInfiniteQuery`).

Fichiers créés : `src/pages/profile/FollowersPage.tsx` (ou modal dans ProfilePage)  
Branche suggérée : `feature/s7-follow-lists`
Dépendances : SCRUM-138 (BACK Follow), SCRUM-142 (FollowButton)

### 🔧 [SCRUM-144] [BACK][S9] Likes de commentaires + signalement commentaires
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S7 | **Assigné** : Elie | **SP** : 3 | **Épic** : SCRUM-16 | **Story** : SCRUM-111 (US-22)

\[BACK\] Sprint 7 — Feature 2 / Tâche 2

1. **Entité** `CommentLike` (PanacheEntity) :

    * `userId` (UUID), `commentId` (Long)
    * Contrainte unique `(userId, commentId)`
    
2. `CommentService` :

    * `like(String auth0Id, Long commentId)` : créer `CommentLike`, incrémenter `comment.likeCount` (@Transactional)
    * `unlike(String auth0Id, Long commentId)` : supprimer `CommentLike`, décrémenter `likeCount`
    
3. `CommentResource` :

    * `POST /api/comments/{id}/like`
    * `DELETE /api/comments/{id}/like`
    
4. **Signalement commentaire** : réutiliser/étendre l'entité `Report` (planifiée S6, SCRUM-17) en ajoutant `commentId` (Long, nullable). Créer `POST /api/comments/{id}/report`.
5. **OpenAPI** : documenter les nouveaux endpoints.
6. **Tests** : liker 2 fois → 409 ; unliker sans like → 404.

Fichiers créés/touchés : `CommentLike.java`, `CommentService.java`, `CommentResource.java`, `Report.java`, `openapi.yaml`
Branche suggérée : `feature/s7-comment-likes`
Dépendances : SCRUM-139 (Entité Comment, S6), Entité Report (SCRUM-94, S6)

### 🔧 [SCRUM-145] [BACK][S9] Notifications de mention dans les commentaires
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S7 | **Assigné** : Elie | **SP** : 2 | **Épic** : SCRUM-16 | **Story** : SCRUM-111 (US-22)

\[BACK\] Sprint 7 — Feature 2 / Tâche 3

1. `CommentService.post()` : après persistance, parser le `content` pour trouver les mentions `@displayName` (regex `@(\w[\w\s]*)` jusqu'à espace ou ponctuation).
2. Pour chaque mention : chercher l'utilisateur par `displayName` (exact match). Si trouvé ET ce n'est pas l'auteur → créer `Notification` type `COMMENT_MENTION` (infrastructure SCRUM-99 requise).
3. Ajouter `COMMENT_MENTION` et `NEW_COMMENT` (pour l'organisateur) à `NotificationType`.
4. Prévoir `GET /api/users/search?q=` pour l'autocomplétion frontend (si pas déjà existant via UserResource).
5. **Tests** : post avec `@Alice` → notification créée pour Alice.

Fichiers touchés : `CommentService.java`, `NotificationType.java`, `UserResource.java` (si endpoint search manquant)  
Branche suggérée : `feature/s7-comment-mentions`
Dépendances : SCRUM-139 (Comment, S6), Infrastructure Notification (SCRUM-99, S7)

### 🔧 [SCRUM-146] [FRONT][S9] Section commentaires dans EventDetailPage
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S7 | **Assigné** : Daniel | **SP** : 5 | **Épic** : SCRUM-16 | **Story** : SCRUM-111 (US-22)

\[FRONT\] Sprint 7 — Feature 2 / Tâche 4

1. `CommentSection.tsx` (nouveau) : section principale dans `EventDetailPage.tsx`. Contient liste + formulaire.
2. `CommentItem.tsx` (nouveau) :

    * Afficher avatar, displayName, badge "Organisateur" (si `authorIsOrganizer`), contenu, date relative, compteur likes, bouton "Répondre", "Supprimer" (si auteur ou organisateur), "Signaler"
    * Afficher les replies imbriquées (max 1 niveau)
    
3. `CommentForm.tsx` (nouveau) : `<textarea>` avec compteur caractères (max 2000), bouton Envoyer. Masqué si non connecté → message "Connectez-vous pour commenter."
4. TanStack Query : `queryKey: ['events', id, 'comments']`, mutation `postComment` + `deleteComment`.
5. Optimistic update : ajouter le commentaire localement avant confirmation serveur.

Fichiers créés/touchés : `src/components/event/CommentSection.tsx`, `src/components/event/CommentItem.tsx`, `src/components/event/CommentForm.tsx`, `EventDetailPage.tsx`
Branche suggérée : `feature/s7-comments-front`
Dépendances : SCRUM-139 (BACK Comment, S6)

### 🔧 [SCRUM-148] [BACK][S9] Entité EventAttachment + endpoint upload fichiers joints
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S7 | **Assigné** : Antoine | **SP** : 5 | **Épic** : SCRUM-14 | **Story** : SCRUM-117 (US-28)

\[BACK\] Sprint 7 — Feature 6d / Tâche 4

1. **Entité** `EventAttachment` (PanacheEntity) :

    * `eventId` (Long, @Column nullable=false)
    * `fileName` (String)
    * `fileUrl` (String) — chemin S3/local via `FileStorageService`
    * `fileSize` (Long) — taille en octets
    * `mimeType` (String)
    * `uploadedAt` (LocalDateTime, @PrePersist)
    
2. `EventAttachmentService` (@ApplicationScoped, @Transactional) :

    * `upload(String auth0Id, Long eventId, FileUpload file)` : vérifier créateur ou co-organisateur ; whitelist mimeType (PDF, DOC, DOCX, XLSX) ; max 10 MB ; max 5 fichiers par événement.
    * `delete(String auth0Id, Long attachmentId)`
    * `getByEvent(Long eventId)`
    
3. `EventAttachmentResource` :

    * `POST /api/events/{id}/attachments` (multipart/form-data)
    * `DELETE /api/events/{id}/attachments/{attachmentId}`
    * `GET /api/events/{id}/attachments`
    
4. `EventDTO` : ajouter `attachments: List<AttachmentDTO>`.
5. **OpenAPI** : documenter endpoints + `AttachmentDTO`.
6. **Tests** : upload PDF → 201 ; upload > 10MB → 400 ; upload par non-organisateur → 403.

Fichiers créés/touchés : `EventAttachment.java`, `EventAttachmentService.java`, `EventAttachmentResource.java`, `AttachmentDTO.java`, `EventDTO.java`, `openapi.yaml`
Branche suggérée : `feature/s7-attachments`
Dépendances : `FileStorageService` existant

### 🔧 [SCRUM-149] [FRONT][S9] Upload et affichage des fichiers joints sur EventDetailPage
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S7 | **Assigné** : Daniel | **SP** : 3 | **Épic** : SCRUM-14 | **Story** : SCRUM-117 (US-28)

\[FRONT\] Sprint 7 — Feature 6d / Tâche 5

1. `EventEditPage.tsx` (uniquement en édition, l'event doit exister) : section "Fichiers joints" avec :

    * `<input type="file" multiple accept=".pdf,.doc,.docx,.xlsx">` + liste des fichiers sélectionnés
    * Bouton "Uploader" → itérer sur les fichiers → `POST /api/events/{id}/attachments`
    * Liste des fichiers déjà uploadés avec bouton × → `DELETE /api/events/{id}/attachments/{id}`
    
2. `EventDetailPage.tsx` : section "Documents" si `event.attachments.length > 0` — liste avec nom, taille, et lien téléchargement (`<a href={fileUrl} download>`).

Fichiers touchés : `EventEditPage.tsx`, `EventDetailPage.tsx`
Branche suggérée : `feature/s7-attachments-front`
Dépendances : SCRUM-148 (BACK EventAttachment)

### 🔧 [SCRUM-151] [FRONT][S9] UI événements récurrents dans EventForm
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S8 | **Assigné** : Daniel | **SP** : 5 | **Épic** : SCRUM-14 | **Story** : SCRUM-116 (US-27)

\[FRONT\] Sprint 8 — Feature 6a / Tâche 2

1. `EventForm.tsx` : ajouter section "Récurrence" avec :

    * Switch "Événement unique / Récurrent"
    * Si récurrent : `<Select>` fréquence (Chaque semaine / Toutes les 2 semaines / Chaque mois)
    * Date de fin de récurrence OU nombre d'occurrences (radio)
    
2. `useEventForm.ts` : ajouter `recurrence?: { frequency: 'WEEKLY'|'BIWEEKLY'|'MONTHLY', endDate?: string, maxOccurrences?: number }` dans `EventFormValues`.
3. `EventCard.tsx` : si `event.parentEventId != null` → afficher badge "Récurrent" (icône `RefreshCw` lucide).
4. `EventDetailPage.tsx` : si `parentEventId != null` → lien "Voir toutes les occurrences" → `GET /api/events/{parentEventId}/occurrences`.

Fichiers touchés : `EventForm.tsx`, `useEventForm.ts`, `EventCard.tsx`, `EventDetailPage.tsx`
Branche suggérée : `feature/s8-recurrence-front`
Dépendances : SCRUM-147 (BACK récurrence S7)

---

## Sprint 10 — 18–22 mai 2026
**Thème :** Messagerie complète + commentaires avancés + tests qualité finaux  
**Total estimé :** 62 SP

### 🚀 [SCRUM-50] [S10] Qualité, tests, sécurité, CD et préparation soutenance (US-T6)
**Type :** Feature · **Story Points :** — SP

**Critères d'acceptation:**

\- Tests @QuarkusTest couverture > 80%

\- Tests E2E (Playwright) sur les scénarios critiques

\- Test de charge k6 : p95 < 500ms pour 500 utilisateurs simultanés

\- Pipeline CD opérationnel (deploy automatique + rollback)

\- Lighthouse score > 80 sur mobile\\n- Revue sécurité OWASP Top 10

\- Soutenance préparée (slides + démo live)

### 📖 [SCRUM-115] [S10][US-26] En tant qu'utilisateur, je veux envoyer et recevoir des messages privés à d'autres utilisateurs, afin d'échanger directement sur la plateforme.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 📖 [SCRUM-74] [S10][US-T6] En tant qu'admin, je veux suivre des métriques clés (profils créés, événements publiés, engagement) afin de piloter la croissance de la plateforme.
**Type :** User Story · **Story Points :** — SP

*Pas de description renseignée.*

### 🔧 [SCRUM-150] [FRONT][S10] Likes, threads et autocomplétion mentions dans commentaires
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S8 | **Assigné** : Viona | **SP** : 5 | **Épic** : SCRUM-16 | **Story** : SCRUM-111 (US-22)

\[FRONT\] Sprint 8 — Feature 2 / Tâche 5

1. **Likes** : bouton cœur dans `CommentItem.tsx`. `useMutation` pour `POST/DELETE /api/comments/{id}/like`. État `likedByMe` depuis `CommentDTO`. Optimistic update sur `likeCount`.
2. **Threads (réponses)** : cliquer "Répondre" → afficher `CommentForm` préfixé `@displayName` en dessous du `CommentItem`. Envoyer avec `parentCommentId`. Les replies s'affichent indentées sous le parent.
3. **Autocomplétion mentions** : dans `CommentForm.tsx`, détecter la saisie `@` → appeler `GET /api/users/search?q=<texte>` avec debounce 300ms → dropdown `MentionAutocomplete.tsx`. Sélection → remplacer `@texte` par `@displayName` dans le textarea.
4. **Signalement** : bouton "Signaler" dans `CommentItem.tsx` → modal de confirmation → `POST /api/comments/{id}/report`.

Fichiers créés/touchés : `CommentItem.tsx`, `CommentForm.tsx`, `src/components/utils/MentionAutocomplete.tsx`
Branche suggérée : `feature/s8-comments-advanced`
Dépendances : SCRUM-146 (commentaires front S7), SCRUM-144 (likes back S7), SCRUM-145 (mentions back S7)

### 🔧 [SCRUM-152] [BACK][S10] Entités Conversation + Message + endpoints CRUD messagerie
**Type :** Tâche · **Story Points :** 8 SP

**Sprint** : S8 | **Assigné** : Elie | **SP** : 8 | **Épic** : SCRUM-108 | **Story** : SCRUM-115 (US-26)

\[BACK\] Sprint 8 — Feature 5 / Tâche 1

1. **Entité** `Conversation` (PanacheEntity) :

    * `participant1Id` (UUID — toujours `min(p1, p2)` UUID lexicographique)
    * `participant2Id` (UUID — toujours `max(p1, p2)`)
    * `createdAt`, `lastMessageAt`
    * Contrainte unique `(participant1Id, participant2Id)`
    
2. **Entité** `Message` (PanacheEntity) :

    * `conversationId` (Long), `senderId` (UUID)
    * `content` (String TEXT, max 4000 chars)
    * `createdAt` (LocalDateTime, @PrePersist)
    * `read` (boolean, default false)
    * Index sur `(conversationId, createdAt)`
    
3. `ConversationService` (@ApplicationScoped, @Transactional) :

    * `getOrCreate(String myAuth0Id, UUID recipientId)` : normaliser les UUIDs, créer si inexistant
    * `getMyConversations(String auth0Id, int page, int size)` : triées par `lastMessageAt DESC`
    * `getMessages(String auth0Id, Long conversationId, int page, int size)`
    * `sendMessage(String auth0Id, Long conversationId, String content)` : vérifier `messagingPreference` du destinataire
    * `markAsRead(String auth0Id, Long conversationId)`
    * `getUnreadCount(String auth0Id)`
    
4. `ConversationResource` : GET /api/conversations, POST /api/conversations, GET /api/conversations/{id}/messages, POST /api/conversations/{id}/messages, PATCH /api/conversations/{id}/read
5. `UserResource` : ajouter `GET /api/users/me/unread-message-count`
6. **OpenAPI** : documenter tous les endpoints + `ConversationDTO`, `MessageDTO`, `SendMessageRequest`.
7. **Tests** : création conversation ; envoi message ; messagingPreference FOLLOWERS_ONLY + non follower → 403.

Fichiers créés/touchés : `Conversation.java`, `Message.java`, `ConversationService.java`, `ConversationResource.java`, `ConversationDTO.java`, `MessageDTO.java`, `UserResource.java`, `openapi.yaml`
Branche suggérée : `feature/s8-messaging`
Dépendances : SCRUM-138 (Follow, pour vérification FOLLOWERS_ONLY)

### 🔧 [SCRUM-153] [BACK][S10] Préférences messagerie sur User + vérification accès FOLLOWERS_ONLY
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S8 | **Assigné** : Elie | **SP** : 3 | **Épic** : SCRUM-108 | **Story** : SCRUM-115 (US-26)

\[BACK\] Sprint 8 — Feature 5 / Tâche 2

1. **Enum** `MessagingPreference` : `EVERYONE` / `FOLLOWERS_ONLY`
2. **Entité** `User` : ajouter `messagingPreference` (MessagingPreference, default EVERYONE).
3. `UpdateProfileRequest` : ajouter `messagingPreference`.
4. `UserProfileResponse` : ajouter `messagingPreference`.
5. `ConversationService.sendMessage()` : si `recipient.messagingPreference = FOLLOWERS_ONLY` → vérifier qu'il existe un `Follow` ACCEPTED où `followedId = recipient.id AND followerId = sender.id` → sinon 403.
6. **OpenAPI** : ajouter `MessagingPreference` enum, champ dans `User`, `UpdateProfileRequest`.
7. **Tests** : FOLLOWERS_ONLY + non follower → 403 ; FOLLOWERS_ONLY + follower ACCEPTED → 200.

Fichiers touchés : `User.java`, `MessagingPreference.java`, `UserService.java`, `UpdateProfileRequest.java`, `UserProfileResponse.java`, `ConversationService.java`, `openapi.yaml`
Branche suggérée : `feature/s8-messaging-prefs`
Dépendances : SCRUM-138 (Follow), SCRUM-152 (Conversation/Message)

### 🔧 [SCRUM-154] [FRONT][S10] Page Messagerie — layout, liste des conversations et badge navbar
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S8 | **Assigné** : Viona | **SP** : 5 | **Épic** : SCRUM-108 | **Story** : SCRUM-115 (US-26)

\[FRONT\] Sprint 8 — Feature 5 / Tâche 3

1. Nouvelle route `/messages` → `MessagingPage.tsx`
2. `MessagingPage.tsx` : layout deux colonnes (md+) ou colonne unique (mobile) :

    * Colonne gauche : `ConversationList.tsx` — liste des conversations triées par `lastMessageAt`, avec avatar, prévisualisation dernier message, badge "N non lus" rouge.
    * Colonne droite : `ConversationView.tsx` (chargée quand une conv est sélectionnée ou via `?conversationId=`).
    
3. `ConversationList.tsx` : TanStack Query `useQuery(['conversations'])` + polling toutes les 15s. Cliquer → sélectionner + marquer comme lu (`PATCH /read`).
4. `Navbar.tsx` : ajouter icône enveloppe avec badge `unreadCount` (polling `GET /api/users/me/unread-message-count` toutes les 30s).
5. `ProfilePage.tsx` : bouton "Envoyer un message" → `POST /api/conversations` puis naviguer vers `/messages?conversationId=...`.
6. `AppRouter.tsx` : ajouter la route `/messages`.

Fichiers créés/touchés : `MessagingPage.tsx`, `ConversationList.tsx`, `AppRouter.tsx`, `Navbar.tsx`, `ProfilePage.tsx`
Branche suggérée : `feature/s8-messaging-front`
Dépendances : SCRUM-152 (BACK messagerie), SCRUM-141 (ProfilePage)

### 🔧 [SCRUM-155] [FRONT][S10] Interface de conversation (fil de messages + envoi)
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S8 | **Assigné** : Daniel | **SP** : 5 | **Épic** : SCRUM-108 | **Story** : SCRUM-115 (US-26)

\[FRONT\] Sprint 8 — Feature 5 / Tâche 4

1. `ConversationView.tsx` : affiche le fil de messages d'une conversation.

    * `useQuery(['conversations', id, 'messages'])` — polling toutes les 5s (remplacé par SSE/WebSocket en S9)
    * Messages groupés par date. Messages de l'utilisateur à droite (fond coloré), de l'autre à gauche.
    * Auto-scroll vers le bas à chaque nouveau message.
    
2. `MessageInput.tsx` : `<textarea>` (Shift+Enter = saut de ligne, Enter = envoyer), bouton Envoyer. `useMutation` pour `POST /api/conversations/{id}/messages`. Optimistic update.
3. **Pagination** : charger les 50 derniers messages, bouton "Charger plus" en haut (messages plus anciens).
4. **Gestion d'erreur** : si `messagingPreference` bloque l'envoi → afficher message d'erreur explicite.

Fichiers créés : `ConversationView.tsx`, `MessageInput.tsx`
Branche suggérée : `feature/s8-conversation-view`
Dépendances : SCRUM-154 (MessagingPage), SCRUM-152 (BACK messagerie)

### 🔧 [SCRUM-156] [BACK][S10] Amélioration temps réel messagerie (Long Polling ou SSE)
**Type :** Tâche · **Story Points :** 5 SP

**Sprint** : S9 | **Assigné** : Antoine | **SP** : 5 | **Épic** : SCRUM-108 | **Story** : SCRUM-115 (US-26)

\[BACK\] Sprint 9 — Feature 5 / Tâche 5

Améliorer la réactivité de la messagerie. Trois options par ordre de complexité :

**Option A — Long Polling (recommandée si temps limité)** :

* `GET /api/conversations/{id}/messages/poll?since=<ISO_DATETIME>` : endpoint qui attend jusqu'à 20s qu'un nouveau message arrive. Retourne immédiatement si un message est reçu.

**Option B — Server-Sent Events (moderne, Quarkus-natif)** :

* Utiliser `@Produces(MediaType.SERVER_SENT_EVENTS)` avec RESTEasy Reactive pour `GET /api/conversations/{id}/events`.
* Côté frontend : `EventSource` API.

**Option C — WebSocket (Quarkus WebSockets Next)** :

* `@WebSocket("/api/ws/conversations/{id}")` — plus complexe à gérer côté auth (JWT dans header WebSocket).

**Recommandation** : implémenter Option A si le temps est limité, Option B si Antoine a de la bande passante.

Fichiers touchés : selon option choisie  
Branche suggérée : `feature/s9-messaging-realtime`
Dépendances : SCRUM-152 (messagerie S8)

### 🔧 [SCRUM-157] [FRONT][S10] Badge non-lus + intégration temps réel messagerie
**Type :** Tâche · **Story Points :** 3 SP

**Sprint** : S9 | **Assigné** : Viona | **SP** : 3 | **Épic** : SCRUM-108 | **Story** : SCRUM-115 (US-26)

\[FRONT\] Sprint 9 — Feature 5 / Tâche 6

1. Remplacer le polling de `ConversationView.tsx` par la connexion Long Polling / SSE / WebSocket selon la solution choisie côté backend (SCRUM-156).
2. À la réception d'un nouveau message → mettre à jour le cache TanStack Query de la conversation (`queryClient.setQueryData`).
3. Mettre à jour le badge non-lus dans `Navbar.tsx` + `ConversationList.tsx` en temps réel.
4. Notification toast si un message arrive dans une conversation qui n'est pas actuellement visible.
5. Marquer automatiquement comme lu quand la `ConversationView` est active et visible (`IntersectionObserver` ou état `focused`).

Fichiers touchés : `ConversationView.tsx`, `ConversationList.tsx`, `Navbar.tsx`
Branche suggérée : `feature/s9-messaging-realtime-front`
Dépendances : SCRUM-156 (BACK temps réel), SCRUM-155 (ConversationView)

### 🔧 [SCRUM-158] [FRONT][S10] Préférences messagerie dans ProfileEditPage
**Type :** Tâche · **Story Points :** 2 SP

**Sprint** : S9 | **Assigné** : Daniel | **SP** : 2 | **Épic** : SCRUM-108 | **Story** : SCRUM-115 (US-26)

\[FRONT\] Sprint 9 — Feature 5 / Tâche 7

1. `ProfileEditPage.tsx` : ajouter dans la section "Paramètres de confidentialité" un `<Select>` "Qui peut m'envoyer des messages ?" avec les options :

    * "Tout le monde" (EVERYONE)
    * "Uniquement mes abonnés" (FOLLOWERS_ONLY)
    
2. Appel `PUT /api/users/me` avec le champ `messagingPreference`.
3. Mise à jour des types TypeScript depuis openapi.yaml.

Fichiers touchés : `ProfileEditPage.tsx`
Branche suggérée : `feature/s9-messaging-prefs-front`
Dépendances : SCRUM-153 (BACK prefs messagerie, S8)

### 🔧 [SCRUM-37] [BACK][S10] Tests d'intégration @QuarkusTest (couverture > 80%) + revue sécurité OWASP
**Type :** Tâche · **Story Points :** 13 SP

**\[BACK\] Sprint 8**

\- Tests d'intégration @QuarkusTest + RestAssured : couverture > 80% sur EventResource, UserResource, AuthResource

\- Utilisation de quarkus-devservices-postgresql

\- Scénarios : happy path + cas d'erreur (401, 403, 404)

\- Revue sécurité : CORS configuré, secrets en env vars, vérification native queries, audit OWASP Top 10"

### 🔧 [SCRUM-38] [FRONT][S10] Tests E2E Playwright + optimisations performances (Lighthouse > 80)
**Type :** Tâche · **Story Points :** 8 SP

**\[FRONT\] Sprint 8**

\- Tests E2E (Playwright ou Cypress) : 3-5 scénarios critiques (login → créer event → s'inscrire → voir dans favoris), intégrés dans le pipeline CI

\- Optimisations perf : lazy loading routes, pagination virtuelle liste events, compression images

\- Objectif Lighthouse > 80 sur mobile, bundle size analysé"

### 🔧 [SCRUM-39] [BOTH][S10] Test de charge k6/Artillery (500 users, p95 < 500ms) + préparation soutenance
**Type :** Tâche · **Story Points :** 5 SP

**\[BACK + FRONT\] Sprint 8 — Transverse**

‌

\- Test de charge (k6 ou Artillery) : 500 utilisateurs simultanés sur `GET /api/events` et `POST /api/events/{id}/attend`

\- Objectif : p95 < 500ms. Résultats documentés, goulots identifiés et traités

\- Préparation soutenance : démo live, slides, métriques (velocity, burndown, couverture, bugs), retour d'expérience\\n- Chaque membre prépare sa partie selon son rôle dans le projet"
