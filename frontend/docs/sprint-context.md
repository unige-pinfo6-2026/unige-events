# docs/sprint-context.md — État d'avancement

Dernière mise à jour : 2026-05-14 (post-merge PR #158 + SCRUM-137/146 + view anonyme + polish post-test-manuel)

---

## 2026-05-14 (suite) — Polish post-test-manuel PR #170

Après ouverture de la PR #170 + tests manuels en local (devcontainer + Postgres dédiés), 4 ajustements UX/produit livrés sur la même branche :

- **Cap commentaires 2000 → 500 chars** (axe 1, `fix(scrum-146)`). Trop laxiste pour des commentaires d'événement universitaire. OpenAPI + backend `@Size(max=500)` + frontend `MAX_LENGTH=500`. Pas de migration Flyway (`content` reste `TEXT`).
- **Signalement de commentaire** (axe 2) : vérifié dans le backlog — **SCRUM-144 (`[BACK][S9]`) reste prévu pour Sprint 9**. Décision B de la spec (toast informatif "bientôt disponible") confirmée. Aucun code modifié.
- **Fallback displayName** (axe 4, `fix(scrum-146)`). Quand `displayName` est `null` (Auth0 sans claim `name`), l'UI affichait "Utilisateur" partout — impossible de distinguer les comptes non-provisionnés. Nouveau util `@/utils/displayName.ts` (helpers `userDisplayLabel` + `userInitials`) avec fallback chain `trimmedDisplayName > UUID.slice(0,8) > "Utilisateur"`. Adopté par `CommentItem` et `EventOrganizerTeam`. **Follow-up** : remplacer le UUID prefix par un @username une fois le système username livré (post-PR #170).
- **UI co-organisateurs** (axe 5, `fix(scrum-137)`) :
  - Le placeholder historique `EventForm.tsx` "Alice Martin / Bob Chen" + champ "Inviter un collaborateur…" (bande 5) est supprimé.
  - Le vrai `<CoOrganizersEditor>` est désormais injecté **dans** le flow du formulaire via une nouvelle prop `coOrganizersSection?: React.ReactNode` (avant la barre CTA Annuler/Enregistrer), au lieu d'être placé après `</EventForm>` dans `EventEditPage`. Ordre visuel cohérent.

Tests : 1388/1388 ✅. Spec inchangée, décisions A→D toujours valides.

---

## 2026-05-14 — Post-merge PR #158 : reprise développement front

Backend migration vers microservices mergée à `ad6d422f` (cf.
[`backend/docs/sprint-context.md`](../../backend/docs/sprint-context.md)).
**Aucun impact frontend** sur la PR #158 (invariant `git diff frontend/` = 0).

Cette PR (`feature/scrum-137-146-doc-and-views`) reprend le développement fonctionnel :

- **SCRUM-137** — UI co-organisateurs (frontend) :
  - `CoOrganizersEditor` dans `EventForm` mode édition (champ UUID + invitation, liste
    avec chips statut, bouton retirer).
  - `EventOrganizerTeam` dans la sidebar de `EventDetailPage` (créateur + co-orgs ACCEPTED).
  - `CoOrganizerInvitationsBadge` dans la `Navbar` + `CoOrganizerInvitationsList` dans
    `ProfilePage` (self).
  - Hook `useCoOrganizers` + `useCoOrganizerInvitations` (pas de TanStack Query, pattern
    `useFavorite`/`useAttendance`).
  - Service `coOrganizerApi.ts`, types `src/types/coOrganizer.ts`.
  - 3 skeletons manuels : `co-organizers-section`, `event-organizer-team`,
    `co-organizer-invitations`.
  - **Décision A** : invitation par UUID, pas par search libre (`GET /users/search`
    n'existe pas côté backend ; follow-up S9+).

- **SCRUM-146** — Section commentaires dans `EventDetailPage` :
  - `CommentSection` (wrapper liste + form), `CommentForm` (textarea + compteur 500 chars,
    masqué pour anonymes), `CommentItem` (replies 1 niveau, badge "Organisateur",
    actions Répondre/Supprimer/Signaler).
  - Hook `useComments` (optimistic post + delete + rollback).
  - Service `commentApi.ts`, types `src/types/comment.ts`.
  - Skeleton manuel `comments`.
  - **Décision B** : bouton "Signaler un commentaire" présent mais affiche un toast
    informatif (SCRUM-144 S9+ pour la fonction réelle).

- **Fix vue anonyme + dédup** :
  - `services/sessionId.ts` : UUID v4 généré + persisté en `localStorage` clé
    `unige_session_id`.
  - `statsApi.recordEventView(eventId)` envoie `{ sessionId }` en body.
  - `EventDetailPage` enregistre la vue **inconditionnellement** au montage (plus de
    guard `if (user)`).
  - Côté backend : migration `V11__add_event_views_session.sql`, `EventViewService` étendu,
    `EventViewResource` passe en `@PermitAll`. Dédup par `(eventId, sessionId)` pour anon,
    `(eventId, userId)` pour authentifié — partial unique indexes.

- **Documentation** :
  - `frontend/docs/architecture.md` : routes manquantes ajoutées (`/admin`,
    `/events/:id/stats`, `/403`) ; table services complétée (`adminApi`, `attendanceApi`,
    `attendeesApi`, `reportApi`, `statsApi`, `coOrganizerApi`, `commentApi`,
    `sessionId.ts`).
  - `frontend/docs/components.md` : déduplication des entrées `eventApi` ; ajout des
    nouveaux composants SCRUM-137/146.
  - `frontend/AGENTS.md` : ajout des 4 nouveaux skeletons.

Spec détaillée : [`../../specs_archives/specs_claude/specs_scrum-137-146-views-docs.md`](../../specs_archives/specs_claude/specs_scrum-137-146-views-docs.md).

---

## Sprint 7 — Modale de signalement d'événement (SCRUM-S6-report-modal) — 2026-05-03 (corrigé 2026-05-07)

Terminé.

Fonctionnalités livrées :
- **`reportApi.ts`** (`src/services/reportApi.ts`) : `reportEvent(eventId, { reason, description? })` → `POST /api/events/{id}/report`. Contrat OpenAPI aligné (`CreateReportRequest { reason: ReportReason, description?: string ≤ 2000 }`). Le 201 retourne un `Report` complet — la signature reste `Promise<void>` car aucun consommateur n'utilise le corps.
- **`useReport`** (`src/hooks/useReport.ts`) : hook gérant l'état `isOpen / submitting` de la modale + appel API. Envoie `reason` (constante `SPAM | INAPPROPRIATE | FAKE | OTHER`) et `description` (textarea optionnelle, trimée et omise si vide) **dans des champs séparés**. Toast succès "Merci pour votre signalement." + fermeture auto. Toast 409 "Vous avez déjà signalé cet événement." Toast générique sur toute autre erreur.
- **`src/types/report.ts`** : map `as const` `REPORT_REASONS` (`{ SPAM: 'Spam', INAPPROPRIATE: 'Contenu inapproprié', FAKE: 'Faux événement', OTHER: 'Autre' }`) + type `ReportReason = keyof typeof REPORT_REASONS`. Single source of truth partagée entre service, hook et modal.
- **`ReportModal`** (`src/components/event/ReportModal.tsx`) : modale de signalement avec select Motif (obligatoire) et textarea Description (optionnelle). `<option value="SPAM">Spam</option>` etc. : la valeur sur le wire est la constante backend, le label visible reste en français. Pattern `FormField` / `Select` / `Textarea` depuis `@/components/utils/FormField`. Bouton "Signaler" désactivé sans motif.
- **`EventDetailPage`** : bouton "Signaler cet événement" (icône `Flag`) dans la sidebar, conditionnel `user !== null && !isOrganizer`. `ReportModal` monté conditionnellement via `reportHook.isOpen`.
- **Tests** : suite frontend 1198/1198 verte (cf. `reportApi.test.ts`, `useReport.test.tsx`, `ReportModal.test.tsx`, `EventDetailPage.test.tsx` — couverture 100% sur les 4 fichiers touchés). Lint propre.

### Correctif 2026-05-07 — contrat backend & fuite de description

L'implémentation initiale envoyait des labels français (`"Spam"`, `"Contenu inapproprié"`…) sur le wire et concaténait la `description` dans le champ `reason` (séparateur `\n\n`), parce que le contrat OpenAPI réel (`CreateReportRequest { reason, description }` avec `ReportReason: enum [SPAM, INAPPROPRIATE, FAKE, OTHER]`) n'avait pas été consulté lors de l'écriture du frontend. Conséquence : `POST /api/events/{id}/report` répondait `400` (Jackson échouait à mapper `"Spam"` vers l'enum) et la `description` saisie par l'utilisateur n'arrivait jamais en base. Le frontend a été aligné sur l'OpenAPI ; les tests qui asseyaient le bug ont été réécrits.

## Sprint 7 — Fix nom des participants privés sur la page stats organisateur — 2026-05-03

Livré.

Sur `/events/:id/stats`, la collapsable "Voir les participants" affichait l'UUID brut au lieu du nom pour les attendees `profilePublic = false`. La cause : `AttendeesSection` faisait un N+1 sur `GET /users/{id}` qui renvoie 404 pour les profils privés (hotfix pentest 4.1), donc `userMap` contenait `null` et la cascade `user?.displayName ?? user?.email ?? attendance.userId` tombait sur l'UUID.

Fix : le backend enrichit `AttendanceDTO` avec `displayName` et `avatarUrl`, et le front lit ces champs directement depuis `attendance.*`. La route `GET /events/{id}/attendees` étant déjà restreinte au créateur / co-organisateur ACCEPTED, exposer le nom y est sûr y compris pour les profils privés. Le N+1 frontend disparaît (drop de l'import `getUserById`, du `userMap`, des `try/catch` par row).

La page détail publique `/events/:id` reste inchangée — elle continue d'afficher "Utilisateur anonyme" pour les profils privés via le flux séparé `useAttendees` → `getPublicUser`. Type `Attendance` (frontend) reçoit `displayName: string | null` et `avatarUrl: string | null` ; les tests qui construisent des `Attendance` littéraux (`AttendeeCard.test`, `AttendeesList.test`, `useAttendees.test`) ont été ajustés.

Tests : `EventStatsPage.test.tsx` réécrit (drop des mocks `getUserById`, ajout d'un test "private user shows displayName, never UUID" et d'un test fallback "Utilisateur supprimé" pour les inscriptions orphelines).

## Sprint 6 — Bloc stats publiques sur EventDetailPage (review #90 — SCRUM-92) — 2026-05-03

Livré.

Fonctionnalités livrées :
- **`EventStatsPanel`** (`src/components/event/EventStatsPanel.tsx`) : card publique "Statistiques de participation" insérée dans la sidebar d'`EventDetailPage` après `IcsExportButton`, **visible pour tous** (pas seulement l'organisateur). 3 mini-cards Vues / Inscrits / Intéressés, pattern KPI compact réutilisé d'`EventStatsPage`. Affiche `—` quand la valeur est `null`/`undefined`.
- **Backend — `EventDTO` étendu** : ajout des champs `viewCount` et `interestedCount` (Long nullable). Stratégie volontairement asymétrique pour éviter les requêtes N+1 sur les listes : seul `EventService.getById(...)` calcule ces compteurs (`countViews` / `countInterested`). Tous les autres call sites (`create`, `update`, `cancel`, `restore`, `publish`, `uploadImage`, `toEventDTOs` pour les listes, `FavoriteService.getFavorites`, `EventSearchService.search`) passent `null, null`.
- **OpenAPI** : `viewCount` et `interestedCount` ajoutés au schéma `Event` avec `nullable: true` et description précisant qu'ils ne sont remplis que sur `GET /events/{id}`.
- **Type frontend `Event`** : `viewCount?: number | null` et `interestedCount?: number | null`.
- **La page dashboard `/events/:id/stats` reste réservée à l'organisateur** (créateur ou co-organisateur ACCEPTED) — même autorisation, mêmes données enrichies (chart + capacity bar + liste des participants).
- Skeleton `event-detail` mis à jour : `STATS_CARD_H` passe de 104 à 172, builder `pushStatsCard` réécrit pour matcher le nouveau composant (titre + 3 mini-cards verticales avec icône container + valeur + label centrés). Fixture `EventDetailFixture` alignée sur la nouvelle hauteur.
- **Tests** : `EventStatsPanel.test.tsx` (5 cas — titre, libellés, formatage fr-CH avec séparateur U+202F, `—` pour null/undefined). 2 tests ajoutés dans `EventDetailPage.test.tsx` (panel rendu pour utilisateur non-organisateur, dashes affichés quand stats absentes). Côté backend : `EventDTOTest` enrichi de 2 cas (`null/null` et valeurs renseignées) ; `EventServiceCoverageTest` enrichi de 2 cas (`getById` expose les compteurs depuis `EventView`/`Favorite`, ou retourne `0` quand vide). Tous les call sites de `EventDTO.from` mis à jour.

## Sprint 7 — Polish UX S7 — 2026-05-01

Terminé. PR `chore` sans ticket Jira (`chore(frontend): polish navbar, skeletons, sticky FAB, link styling and draft redirect`) regroupant 6 frottements UX repérés au fil du sprint :

1. **Navbar dropdown profil** : l'item `Mes événements` (3 sous-liens favoris/participations/publications) est désormais rendu via le composant local `UserDropdownExpandable` à base de `Collapsible.Root` Radix, stylé en **ligne inline** (réutilise `dropdownItemClass` avec un chevron à droite, sub-links indentés à `pl-10`) et **ouvert par défaut**. Plus de "menu dans menu" ni de banner-card séparé. Sidebar mobile (`MobileNavItem`) inchangée.
2. **Skeleton `drafts-resume-strip`** étendu à 4 breakpoints (320 / 480 / 720 / 960). Bones sans flag container (sinon boneyard 1.7.7 les filtre), circle trick `r="50%"` sur les icônes Library + Chevron pour qu'elles restent à 16x16 quelle que soit la largeur du container. Marges externes `mt-6 mb-8` déplacées du fixture vers la `className` du `<Skeleton>` pour éviter le doublement de hauteur via BFC.
3. **Bouton FAB `Créer un événement`** sur `MyPublicationsPage` : retour à `position: fixed bottom-6 right-6` avec un listener `scroll`+`resize`+`ResizeObserver(document.body)` batché en `requestAnimationFrame` qui ré-ajuste le `bottom` en fonction de `footer.getBoundingClientRect().top`. Ne recouvre plus jamais le footer.
4. **Skeletons `event-edit` et `event-detail`** régénérés via `generate.mjs` pour matcher les layouts actuels — incl. la section groupée Date & heure, le champ Faculté, les champs additionnels SCRUM-117 (websiteUrl/contactEmail/registrationDeadline/tags) et la carte "Informations complémentaires" sur la page détail. Tous les flags `c=true` retirés (boneyard 1.7.7 les filtre au render) ; hiérarchie visuelle via alpha compounding. Helpers `rect()` / `circle()` ajoutés en tête de section.
5. **Liens `websiteUrl` et `contactEmail`** sur `EventDetailPage` rendus en bleu via la nouvelle CSS variable `--color-link` (sky-600 light / sky-400 dark) + classe `text-link`. Chips tags et lien organisateur sidebar volontairement laissés inchangés (cohérence visuelle).
6. **Redirect automatique** `/events/:id` → `/events/:id/edit` sur DRAFT pour le créateur (admin exclu — il doit pouvoir modérer ; co-organisateur ACCEPTED suit en follow-up SCRUM-137 frontend). 4 tests automatisés ajoutés à `EventDetailPage.test.tsx`.

## Sprint 7 — Fix overflow visuel des tags dans `EventForm` (ISSUE-122) — 2026-05-01

Livré.

- [x] **ISSUE-122** — Fix overflow visuel des tags (limite 64 → 16, `max-w-full break-all whitespace-normal` sur la chip, `maxLength` HTML sur l'`<input>` de `TagInput`). Backend `@Size(max=16)` aligné sur l'élément de `EventRequestBase.tags` ; pas de migration DB (colonne `event_tags.tag VARCHAR(64)` conservée pour compatibilité avec les tags existants > 16 chars).

## Sprint 7 — Redirect post-login vers la page d'origine (SCRUM-S7) — 2026-04-28

Terminé.

Fonctionnalités livrées :
- `PrivateRoute` passe l'URL tentée (`pathname + search + hash`) dans `state.returnTo` lors de la redirection vers `/login`.
- `LoginPage` lit `location.state?.returnTo` et le transmet à `login(returnTo)`.
- `AuthContext.login(returnTo?)` : utilise `returnTo` si fourni, sinon fallback sur `location.pathname + search + hash` courant (couvre les boutons Navbar sans argument). Garde-fou double : URL externe (`http://`, `https://`, `//`) → fallback `/` ; path `/login*` → fallback `/` (anti-boucle).
- `AuthProvider.onRedirectCallback` : même validation avant de passer à `navigate()`.
- `LoginCallbackPage` : suppression du `<Navigate to="/" />` déclenché sur `isAuthenticated` qui écrasait la navigation impérative de `onRedirectCallback`.
- `Navbar` : `onClick={login}` → `onClick={() => login()}` pour éviter que React passe le `SyntheticEvent` comme `returnTo`.
- Imports `../services/*` → `@/services/*` dans `AuthContext.tsx`.
- Tests : `PrivateRoute.test.tsx` (state.returnTo), `LoginPage.test.tsx` (avec/sans returnTo), `AuthContext.test.tsx` (returnTo explicite, fallback location courante, anti-boucle /login, anti open-redirect http:// et //), `LoginCallbackPage.test.tsx` (pas de navigation vers / sur isAuthenticated).

## Sprint 6 — Aperçu "Mes publications" sur ProfilePage (SCRUM-134 / US-23, scope réduit) — 2026-04-26

Livré.

Fonctionnalités livrées :
- **`MyPublicationsPreview`** (`src/components/profile/MyPublicationsPreview.tsx`) inséré en colonne gauche de `ProfilePage`, sous la card "À propos", uniquement pour `isOwnProfile`. Mini-tabs `Publiés` (défaut) / `Brouillons` / `Annulés` qui rappellent `useMyEvents(status)` (refetch par statut, pas de partage de state avec `MyPublicationsPage`). Affiche jusqu'à 3 événements récents via `PreviewRow`. Compte `(N)` uniquement sur l'onglet actif (le hook ne fetch qu'un statut à la fois — pas de requêtes parallèles juste pour les libellés inactifs). État vide spécifique par statut + CTA `Créer un événement` uniquement sur `Publiés` vide. Lien `Voir toutes mes publications` qui préserve l'onglet via `?status=…`.
- **`PreviewRow`** (`src/components/profile/PreviewRow.tsx`) : ligne compacte tappable (vignette + titre + date + participants + badge statut), lien `<Link>` vers `/events/{id}`.
- **Refactor : extraction de `EVENT_STATUS_VARIANTS` et `EVENT_STATUS_PARAMS`** dans `src/utils/eventStatusStyles.ts`. `MyPublicationsPage` consomme désormais le const map partagé pour ses badges (seul changement autorisé). Aucune modification de `useMyEvents`.
- **Hors scope (différé)** : onglet `Archives` sur `MyPublicationsPage` (CANCELLED ∪ PUBLISHED past-end) et action `Recréer cet événement`. Bloqués par dépendances backend (endpoint `POST /events/{id}/duplicate` non implémenté côté frontend) et par l'absence de capacité de pré-remplissage sur `EventCreatePage`. À documenter dans le worklog SCRUM-134 pour arbitrage produit.

## Sprint 6 — Filtre par tags sur la recherche (SCRUM-131 + SCRUM-132) — 2026-04-22
- Hook `useImageCropFlow` (`src/hooks/useImageCropFlow.ts`) : encapsule le flux sélection fichier → validation → FileReader → cropper → Blob → File. Préserve le nom original, reset l'input pour permettre la re-sélection du même fichier après cancel.
- `ProfileEditPage` : intégration sur l'avatar (aspect 1:1, circular) et la bannière profil (aspect 3:1). Validation MIME + taille préservée avant ouverture du cropper.
- `useEventForm` + `EventForm` : intégration sur la bannière événement (aspect 16:9). 4 nouvelles props sur `EventForm` (`cropSource`, `cropAspect`, `onCropConfirm`, `onCropCancel`). `EventCreatePage` et `EventEditPage` passent simplement les valeurs du hook à `EventForm`.
- Aucun nouvel endpoint ni modification des services — la conversion Blob → File préserve la signature `(file: File)` de `uploadPhoto`, `uploadBanner`, `uploadEventImage`.
- Aucun nouveau skeleton — la modale n'a pas d'état loading.
- Tests : 10 nouveaux pour `useImageCropFlow`, tests `handleImageChange` adaptés + 1 nouveau (`cancelCrop`) pour `useEventForm`, ~5 adaptés + 2 nouveaux pour `ProfileEditPage`, 2 nouveaux pour `EventForm`. `ImageCropper.test.tsx` non touché.

Terminé.

## Sprint 6 — Intégration ImageCropper sur avatar + bannières (SCRUM-123) — 2026-04-22

Fonctionnalités livrées :
- Backend : paramètre `?tags=` (multi-valeurs) sur `GET /api/events/search`. Sémantique OR via clause JPQL `EXISTS`. Normalisation lowercase via `EventService.normalizeTags` (réutilisée).
- Frontend : section « Mots-clés » dans `EventSearchSidebar` avec `TagInput` existant (SCRUM-128). Synchro état ↔ URL via `URLSearchParams.append('tags', t)`. Tags inclus dans `SearchParams` envoyés à l'API.
- Axios : `paramsSerializer: { indexes: null }` configuré dans `searchApi.ts` pour produire `?tags=a&tags=b` (sans crochets), format attendu par JAX-RS.
- Type `Event` enrichi du champ `tags?: string[]` (la search response l'expose désormais).
- Tests : 5 nouveaux tests REST + 6 DB-backed côté backend ; 4 sidebar + 4 hook + 1 service + mises à jour des tests existants côté frontend.
- Aucun nouveau skeleton — la sidebar n'a pas d'état loading.

Terminé.

## Sprint 5 — Mes Événements (SCRUM-93) — 2026-04-14

En cours.

Fonctionnalités livrées :
- **Split en trois pages indépendantes** après premier review :
  - `MyFavoritesPage` (`/my-events/favorites`) — grille d'`EventCard` via `getFavorites()`.
  - `MyParticipationsPage` (`/my-events/participations`) — grille d'`EventCard` avec badge "Inscrit" via `useMyParticipations` (stub `getMyParticipations()` retourne `[]` en attendant l'endpoint backend enrichi).
  - `MyPublicationsPage` (`/my-events/publications`) — dashboard organisateur avec sous-onglets `Publiés / Brouillons / Annulés` (`?status=published|draft|cancelled`). **Layout cards** (`PublicationCard` local) sur tous les breakpoints (plus de table) : bannière ou gradient fallback basé sur la catégorie, badges catégorie/statut en overlay, actions Modifier / Publier (DRAFT) / Annuler. Tri `startDate` décroissante. Bouton flottant "Créer un événement".
- `MyEventsPage` gardé uniquement comme redirect vers `/my-events/favorites`.
- `publishEvent(id)` ajouté à `eventApi.ts` (PATCH /events/{id}/publish).
- Hooks : `useMyEvents(organizerId, status)` (publish/cancel avec cache local invalidé) et `useMyParticipations()`.
- Skeleton `my-events.bones.json` partagé entre les trois pages (même grid 4 cards).
- **Navbar** : dropdown utilisateur avec sous-menu inline *nested* sous "Mes événements" (pattern `group-hover/nested` + `grid grid-rows-[0fr→1fr]` pour une expansion fluide en flow, pas en flyout). Sur mobile (sidebar), réutilise `MobileNavItem` qui gère déjà les `subLinks` via un bouton click-to-expand.
- Routes `/my-events`, `/my-events/favorites`, `/my-events/participations`, `/my-events/publications` enregistrées sous `PrivateRoute`.
## Sprint 6 — Indicateur capacité et liste d'attente (S6) — 2026-04-23

Terminé le 2026-04-23.

Fonctionnalités livrées :
- **Types TypeScript mis à jour** : `AttendanceStatus` passe à `'ATTENDING' | 'WAITLISTED'` ; `Event` reçoit `availableSpots?: number | null` et `waitlistedCount?: number` (alignement sur openapi.yaml).
- **`EventDetailPage` — indicateur visuel de capacité** : si `event.capacity != null && event.availableSpots != null`, affiche un badge coloré : rouge "Complet" (`availableSpots === 0`), orange "Presque complet" (`<= capacity * 0.1`), vert "X places disponibles". Si `waitlistedCount > 0`, affiche "X en liste d'attente". Remplace le `ComingSoonBlock` placeholder.
- **`AttendanceButtons` — gestion liste d'attente** : nouvelle prop `availableSpots` transmise au hook. Quand l'événement est complet (`isFull`) et l'utilisateur n'est pas inscrit, le bouton affiche "Rejoindre la liste d'attente". Quand `currentStatus === 'WAITLISTED'`, affiche "En liste d'attente" (style warning). Suppression du tooltip disabled au profit d'un bouton toujours cliquable.
- **`useAttendance` — support WAITLISTED** : nouvelle signature `initialAvailableSpots?: number | null` pour initialiser `isFull`. Toggle unifié : si `currentStatus !== null` (ATTENDING ou WAITLISTED) → `unattend()`; sinon → `attend()`. Après succès d'`attend()`, `currentStatus` est mis à jour depuis la réponse serveur ; si WAITLISTED, `attendingCount` n'est pas incrémenté.
- **Tests** : 831 tests passing. `useAttendance.test.ts` enrichi (WAITLISTED unattend sans décrément, réponse serveur WAITLISTED, initialAvailableSpots). `AttendanceButtons.test.tsx` refondu pour le nouveau comportement.

## Sprint 6 — Dashboard statistiques organisateur (SCRUM-92) — 2026-04-30

Terminé le 2026-04-30.

Fonctionnalités livrées :
- **`EventStatsPage`** (`/events/:id/stats`, PrivateRoute) : page réservée à l'organisateur de l'événement. Vérifie `user.id === event.creatorId` avant de charger les stats.
- **KPI cards** : 👁 Vues totales (`stats.viewCount`), ✅ Inscrits (`stats.attendingCount`), ⭐ Intéressés (`stats.interestedCount`).
- **`StatsChart`** : BarChart vertical recharts (Vues / Intéressés / Inscrits). Thème adapté aux tokens CSS (`--color-background`, `--color-border`).
- **Barre de remplissage** : `attendingCount / capacity * 100` avec couleur progressive (vert → orange → rouge).
- **Section participants** : bouton collapsible "Voir les participants" → `GET /events/{id}/attendees` → fetch users en parallèle via `getUserById`, affichage avatar + nom.
- **`statsApi.ts`** : `getEventStats()`, `getEventAttendees()` et `recordEventView()`. `EventDetailPage` enregistre une vue au montage (`POST /events/{id}/view`).
- **`useEventStats`** : auto-refresh toutes les 60 s avec `setInterval`, nettoyage à l'unmount.
- **Skeleton `event-stats`** : 2 breakpoints (300 px mobile / 600 px desktop), transition single-col → 3-col sur les KPI cards.
- Dépendance `recharts` ajoutée.

## Sprint 6 — ImageCropper réutilisable (S6) — 2026-04-18

Terminé le 2026-04-18.

Fonctionnalités livrées :
- `ImageCropper` (`src/components/utils/ImageCropper.tsx`) : composant modal générique de recadrage d'image. Utilise `ReactCrop` de `react-image-crop` avec `keepSelection`. Applique le crop via un `<canvas>` (`canvas.toBlob()`).
- Props : `src`, `aspect`, `circular?` (crop rond pour avatar), `onCropComplete`, `onCancel`.
- Bouton "Recadrer" désactivé tant qu'aucune zone n'est sélectionnée.
- Dépendance `react-image-crop` ajoutée.
- 6 tests unitaires couvrant le rendu, les callbacks et les variantes aspect/circular.

## Sprint 6 — Champs additionnels événement (SCRUM-117 / US-28) — 2026-04-23

Terminé le 2026-04-23.

Fonctionnalités livrées :
- 4 nouveaux champs optionnels exposés dans `EventForm` (`src/components/event/EventForm.tsx`) et `EventDetailPage` (`src/pages/event/EventDetailPage.tsx`) :
  - `websiteUrl` — Input `type="url"`, max 500, validation `new URL()` + protocole http(s).
  - `contactEmail` — Input `type="email"`, max 255, validation regex simple (backend `@Email` autoritatif).
  - `registrationDeadline` — date + sélecteurs d'heure réutilisant le pattern `startDate`/`endDate`, ignore `allDay`. Validation frontend uniquement : deadline strictement antérieure à `startDate`.
  - `tags` — composant `TagInput` existant, max 20 tags × 64 caractères chacun.
- Types `Event`, `CreateEventRequest`, `UpdateEventRequest` étendus (`src/types/event.ts`). Nouvelles constantes exportées : `EVENT_WEBSITE_URL_MAX_LENGTH = 500`, `EVENT_CONTACT_EMAIL_MAX_LENGTH = 255`, `EVENT_TAG_MAX_LENGTH = 64`, `EVENT_TAGS_MAX_ITEMS = 20`.
- `useEventForm` (`src/hooks/useEventForm.ts`) : validation client des 4 champs + mapping payload normalisant chaîne vide → `null` et tableau vide → `null` (sémantique PUT complète).
- `EventDetailPage` : bloc conditionnel *Informations complémentaires* (affiché uniquement si au moins un des 4 champs est renseigné) — lien externe `target="_blank" rel="noopener noreferrer"`, `mailto:`, deadline formatée via `formatEventDateTime`, chips `tags` cliquables vers `/events/search?q=<tag>` (le backend n'a pas encore de paramètre de filtre tag dédié, fallback sur le full-text `q`).
- Tests : ~29 tests unitaires ajoutés répartis sur `useEventForm.test.tsx`, `EventForm.test.tsx` et `EventDetailPage.test.tsx`. Couverture locale ≥ 94 % lignes sur les trois fichiers modifiés. Suite globale : 859 tests verts (exécution avec `TZ=UTC` requise — conformément aux tests de payload ISO existants).

## Sprint 1 — Authentification & profils

Terminé.

## Sprint 2 — Consultation & gestion des événements

Terminé.

Fonctionnalités livrées :
- HomePage avec liste paginée des événements publiés, états loading/error/empty et liens rapides.
- EventCard réutilisable pour la liste.
- EventDetailPage riche avec organisateur, actions Modifier/Supprimer réservées au créateur et modal de confirmation.
- CreateEventPage et EditEventPage basées sur EventForm.
- useEvents, useEvent et useEventForm.
- eventApi unifié pour liste, détail, création, édition, annulation et upload de bannière.
- Types Event, EventCategory, EventStatus, CreateEventRequest et UpdateEventRequest dans src/types/index.ts.

Points de cohérence importants conservés après merge :
- Le browsing d’événements déjà intégré sur main reste intact.
- Les flux create/edit/upload du formulaire sont conservés.
- Le statut initial peut être envoyé dès la création pour s’aligner sur le contrat backend.
- L’édition envoie un payload complet pour respecter la sémantique PUT documentée.

Suite prévue :
- Vue calendrier.
- Recherche et filtres avancés.
- Extraction de composants génériques de loading et d’erreur.
- Pagination des résultats de recherche.
- Endpoint backend de suggestions (fetchSuggestions est actuellement un stub).

## Sprint 3 — Recherche et filtres (SCRUM-86)

En cours / Terminé le 2026-04-03.

Fonctionnalités livrées :
- EventsSearchPage à la route `/events/search` : barre de recherche + dropdown d’autocomplétion (300ms) + layout sidebar/résultats.
- FilterSidebar props-driven : category (checkboxes toggle), faculty (select), dateFrom/dateTo (date), reset.
- useSearch hook : initialisation depuis URL, sync état→URL, debounce 300ms suggestions, debounce 2000ms recherche, selectSuggestion pour recherche immédiate.
- searchApi.ts : searchEvents via GET /api/events/search, fetchSuggestions stub (TODO backend).
- Types SearchParams et SearchResponse dans src/types/index.ts.
- Route /events/search enregistrée dans AppRouter (publique).

## Sprint 3 — Vue Calendrier (en cours)

Fonctionnalités livrées :
- CalendarPage (/calendar) : vue calendrier via react-big-calendar, vues Mois/Semaine/Jour/Agenda, navigation intégrée, messages en français.
- Événements colorés par catégorie via eventPropGetter (ACADEMIC=bleu, SPORTS=vert, CULTURAL=violet, SOCIAL=orange, CONFERENCE=teal, OTHER=gris).
- Clic sur un événement → navigation vers /events/:id.
- Tooltip natif react-big-calendar affichant le lieu au survol.
- useCalendarEvents : hook chargeant les événements du mois courant via GET /api/events?endDateFrom=, retourne les événements au format CalendarEvent (title, start, end, resource).
- Lien "Vue Calendrier" dans la Navbar.

## Sprint 4 — Favoris & Partage (SCRUM-91)

Terminé le 2026-04-09.

Fonctionnalités livrées :
- FavoritesPage (/events/favorites, PrivateRoute) : grille d'EventCard favoris, état vide illustré, retrait instantané de la liste.
- FavoriteButton : composant étoile toggle intégré dans EventCard et EventDetailPage, optimistic update avec rollback.
- useFavorite : hook d'état local favori avec toggle async et retour de succès ; redirige vers /login si utilisateur non authentifié.
- FavoritesContext : synchronisation globale de l'état favoris entre toutes les instances de FavoriteButton.
- favoriteApi.ts : getFavorites, addFavorite, removeFavorite.
- Bouton "Partager" dans EventDetailPage : copie `location.href` dans le presse-papier (avec fallback toast si `navigator.clipboard` indisponible), toast "Lien copié !" 3s via useToast.
- Lien "Mes Favoris" dans la Navbar (menu utilisateur connecté uniquement).
- Route /events/favorites enregistrée dans AppRouter sous PrivateRoute.

## Sprint 4 — Export ICS (SCRUM-100)

Terminé le 2026-04-09.

Fonctionnalités livrées :
- `icsGenerator.ts` (`src/utils/`) : `generateIcs(event)` conforme RFC 5545 (UTC, échappement, line folding 75 octets, DESCRIPTION optionnelle) et `buildGoogleCalendarUrl(event)`.
- `IcsExportButton` (`src/components/event/IcsExportButton.tsx`) : bouton "Télécharger .ics" (Blob download) + lien "Google Calendar" (nouvel onglet), affiché sur `EventDetailPage`.
- Tests unitaires `icsGenerator.test.ts` et composant `IcsExportButton.test.tsx` (couverture ≥ 80 %).

## Sprint 4 — Présence / Attendance (SCRUM-90)

Terminé le 2026-04-08.

Fonctionnalités livrées :
- `AttendanceButtons` (`src/components/event/AttendanceButtons.tsx`) : boutons "Je suis intéressé(e)" et "Je participe" sur `EventDetailPage`.
- `useAttendance` hook : mise à jour optimiste, rollback sur erreur, flag `isFull` sur 409.
- `attendanceApi.ts` : `attend` (POST) et `unattend` (DELETE) sur `/api/events/{id}/attend`.
- Types `AttendanceStatus`, `Attendance`, `AttendanceRequest` dans `src/types/attendance.ts`.
- Tests unitaires pour `attendanceApi` et `useAttendance` (couverture ≥ 80 %).
## Sprint 3 — Filtre faculty sur les événements (SCRUM-77 frontend) — 2026-04-10

Terminé.

Fonctionnalités livrées :
- `Faculty` enum ajouté dans `src/types/event.ts` (9 valeurs : SCIENCES, LETTRES, DROIT, MEDECINE, SES, PSYCHOLOGIE, THEOLOGIE, FTI, GSI) — correspond exactement à l'enum OpenAPI.
- Champ `faculty` ajouté aux types métier : sur `Event`, signature `faculty?: Faculty | null` (champ potentiellement absent dans certains mocks ou payloads) ; sur `CreateEventRequest` et `UpdateEventRequest`, signature `faculty?: Faculty | null`.
- `FacultyBadge` (`src/components/faculty/FacultyBadge.tsx`) : pill coloré hex officiel UNIGE par faculté (9 couleurs), libellé français, aria-label. Accepte `Faculty | null | undefined` : quand la valeur est absente, rend un badge neutre « Toutes facultés » (`bg-foreground/10 text-foreground/70`) plutôt qu'une absence de badge.
- `EventCard` : affiche systématiquement le `<FacultyBadge>` dans l'overlay de la bannière (sous le titre) — faculté nommée ou « Toutes facultés » neutre selon `event.faculty`.
- `EventSearchSidebar` : filtre faculté activé, sélection par chips toggle (un par valeur Faculty, libellé français, sélection unique). Remplace l'ancien select désactivé. Un chip supplémentaire « Toutes facultés » (stocké comme `facultyNone: true`) isole les événements non rattachés à une faculté précise — mutex client avec les chips Faculty nommés, mutex serveur documenté dans openapi.yaml (facultyNone gagne si les deux sont envoyés).
- `useEventSearch` : `faculty` et `facultyNone` ajoutés aux `SearchParams` envoyés à l'API. Sync URL `?faculty=` / `?facultyNone=true` (ajout / suppression, mutuellement exclusifs).
- `useEventForm` + `EventForm` : champ "Faculté concernée" select, option par défaut « Toutes facultés » (envoyée comme `null` au backend), valeur `Faculty | null` dans le payload de création/édition.
- Tests unitaires : FacultyBadge (label + couleur × 9 valeurs), EventCard (badge affiché/absent), EventSearchSidebar (chips, sélection/désélection), useEventSearch (faculty dans les params API).

## Sprint 4 — Skeleton screens Boneyard (2026-04-12)

Terminé le 2026-04-12.

Fonctionnalités livrées :
- Skeleton screens Boneyard — `EventCards`, `EventDetailPage`, `ProfilePage`, `EventsSearchPage`, `EventCalendar`, `EventEditPage`, `Navbar` (bouton utilisateur).
- Intégration de `boneyard-js` : import du registry dans `main.tsx`, générateur custom `skeleton/generate.mjs` (pas de CLI Playwright — routes protégées inaccessibles sans auth).
- `src/components/utils/Skeleton.tsx` supprimé — `SkeletonBlock` retiré, remplacé par `<Skeleton>` de `boneyard-js/react` partout.
- Fixtures locales non-exportées dans chaque composant ciblé — JSX statique reproduisant le layout réel pour établir les dimensions du container.
- `LoadingSpinner` retiré des pages/composants couverts par un skeleton — conservé dans `PrivateRoute` et `LoadingPage`.
- Règle établie : **tout futur composant ou page avec appel API doit générer son skeleton** (documenté dans `AGENTS.md` et `docs/dev-guide.md`).

## Sprint 4 — Persistance du form edit + fix layout bannière (2026-04-14)

Terminé le 2026-04-14.

Deux correctifs complémentaires au flux brouillons :

**1. Extension de la persistance sessionStorage au flux edit** (en plus du flux create déjà en place).

- `useEventForm.ts` : les helpers `readPersistedForm` / `writePersistedForm` / `clearPersistedForm` prennent maintenant une clé en paramètre. Nouvelle constante `EDIT_FORM_KEY_PREFIX = 'unige:event-edit-draft:'` pour dériver une clé par event id (`editFormKey(id)`).
- Nouvelle fonction interne `currentPersistKey()` dans le hook qui retourne la bonne clé selon le mode (`DRAFT_FORM_KEY` en create, `editFormKey(initialEvent.id)` en edit, `null` si edit-mode avant que l'event async ne soit chargé).
- Le `pendingPersistRef` stocke `{ key, values }` plutôt que `values` seul — garantit qu'un write debouncé armé sur une clé donnée ne peut jamais tirer sur une autre clé si le contexte change entre temps.
- Le `useEffect([initialEvent, mode])` tente maintenant une restauration depuis `readPersistedForm(editFormKey(initialEvent.id))` en mode edit avant de tomber sur `toFormValues(initialEvent)`. L'ordre : sessionStorage d'abord, backend ensuite.
- `EventEditPage.tsx` appelle `form.clearPersistedDraft()` sur le clic "Annuler" (mode edit publish, pas draft-edit qui n'a pas d'Annuler) et juste après une suppression réussie de brouillon dans `confirmDeleteDraft`.
- 6 nouveaux tests dans un describe block dédié `sessionStorage persistence (edit mode, per-event key)` : hydratation depuis la clé per-event, fallback sur `initialEvent` si la clé est absente, isolation depuis la clé create, debounce sur la clé per-event, nettoyage après submission réussie, isolation entre deux event ids distincts.
- Docs `components.md` section `useEventForm` mise à jour pour refléter les deux flux persistés.

**2. Fix bug layout de la bannière brouillons** — le bouton "Voir tout" volait un slot de carte sur les containers moyens (~900-1024 px) avec 3+ brouillons, faisant passer l'affichage de 2 cartes à 1 carte + bouton.

- `draftsResumeStripLayout.ts` : l'algorithme `computeStripLayout` ne réserve plus l'espace du bouton "Voir tout" avant de compter les slots. Il calcule `naturalSlots = slotsFor(innerWidth)` (sans retirer la largeur du bouton), affiche ce nombre de cartes, et ajoute le bouton en plus si `totalDrafts > displayCount`. Le rail dispose déjà d'un `overflow-x-auto` comme filet de sécurité pour les cas où le bouton débordefait vraiment.
- Simplification visible : la branche "with button" a disparu, l'algorithme est maintenant en 2 returns (early exit + happy path) au lieu de 3.
- Les 9 tests existants de `computeStripLayout` continuent de passer sans modification (vérifié mentalement sur tous les cas). **1 nouveau test régression** ajouté : `computeStripLayout(1024, 3)` doit retourner `{ displayCount: 2, showViewAll: true }` — le cas exact que le user a remonté.

## Sprint 4 — Persistance du formulaire de création (sessionStorage) (2026-04-14)

Terminé le 2026-04-14.

Garde-fou anti-refresh accidentel sur `/events/new` : les saisies en cours ne sont plus perdues quand l'utilisateur rafraîchit la page, ferme/rouvre l'onglet par erreur, ou revient en arrière après un clic involontaire.

- **`useEventForm.ts`** : ajout de 3 helpers internes `readPersistedForm` / `writePersistedForm` / `clearPersistedForm` qui sérialisent l'état `EventFormValues` sous la clé `unige:event-create-draft` dans `sessionStorage`. Toutes les opérations sont wrappées dans `try/catch` pour gérer proprement les environnements où `sessionStorage` est indisponible (mode privé, quota dépassé) — la corruption du JSON déclenche un `console.warn` + nettoyage silencieux.
- **Hydratation au montage** (create mode uniquement) : l'initialisation de `useState<EventFormValues>` lit la clé `sessionStorage` via un `useState(() => …)` synchrone et merge avec `DEFAULT_VALUES` pour tolérer les évolutions futures du shape (nouveaux champs → valeurs par défaut). Le mode `edit` reste strictement piloté par `initialEvent`, `sessionStorage` n'est jamais lu côté edit.
- **Persistance à la saisie** (create mode uniquement, **debouncée 300 ms**) : chaque appel à `setFieldValue` réarme un timer (`DRAFT_FORM_PERSIST_DEBOUNCE_MS`, aligné sur les 300 ms de `useEventSearch`) qui écrit dans `sessionStorage` après inactivité. Pattern explicitement demandé par le devops du projet ("même principe de debounce time"). Collapse les frappes rapides en une seule écriture. `schedulePersist` / `flushPersist` / `cancelPersist` encapsulés dans des refs internes (`persistTimerRef`, `pendingPersistRef`).
- **Flush sur unmount** : le `useEffect` de cleanup (existant pour `revokeObjectURL`) flush la dernière valeur en attente si le composant est démonté pendant qu'un timer est en vol — garantie que les dernières lettres tapées survivent à un refresh accidentel même dans le timing le plus défavorable.
- **Nettoyage automatique** : la clé est supprimée après chaque `submitForm('publish' | 'draft')` réussi en mode create (l'event est désormais en DB). Exposée aussi via une nouvelle méthode publique `clearPersistedDraft()` du hook, appelée par `EventCreatePage` dans l'handler `onCancel` juste avant le `navigate('/')`. La clé **n'est pas** nettoyée sur un démontage passif du composant (navigation interne sans submit / cancel) ni sur une soumission échouée — l'utilisateur retrouve ses saisies en revenant ou en retentant.
- **Limitation assumée** : la bannière image n'est pas persistée (`File` non sérialisable, `blob:` URL morte au refresh). C'est la seule donnée non-recoverable sur refresh, documenté dans la doc `useEventForm`.
- **Tests `useEventForm.test.tsx`** : nouveau describe block `sessionStorage persistence (create mode only)` — démarrage sur `DEFAULT_VALUES` clé absente, hydratation depuis un JSON persisté, **debounce vérifié via `vi.useFakeTimers()` + `vi.advanceTimersByTime(320)`** (pas d'écriture avant le fire du timer, collapse des frappes rapides en une seule écriture), flush sur unmount avec timer encore armé, nettoyage après submission réussie, isolation du mode `edit`, `clearPersistedDraft()` exposé. `sessionStorage.clear()` ajouté dans `afterEach`.
- **Test `EventCreatePage.test.tsx`** : 1 nouveau cas vérifiant que cliquer sur "Annuler" après avoir pré-seedé la clé la supprime avant `navigate('/')`. `sessionStorage.removeItem` ajouté dans `afterEach`.
- **Interaction avec le flow brouillons DB** : les deux systèmes de persistance sont orthogonaux. `sessionStorage` couvre l'état volatile pré-save (garde-fou anti-refresh), la DB couvre l'état de brouillon explicite (reprendre plus tard, multi-appareils). Un `triggerDraftSave` réussi nettoie la clé `sessionStorage` parce que l'état vit désormais en DB et apparaîtra dans le strip au prochain retour sur `/events/new`.

## Sprint 4 — Tag "Brouillon" amber sur `DraftResumeCard` (2026-04-14)

Terminé le 2026-04-14.

Mise en valeur du tag "Brouillon" affiché en haut à droite de chaque mini carte dans le panneau déplié de `DraftsResumeStrip` : le tag était jusque-là gris atténué (`text-foreground/40`), peu lisible.

- **`DraftResumeCard.tsx`** : le tag "Brouillon" ligne 1 passe d'un simple `text-foreground/40` à une pill `bg-warning/20 text-warning border-warning/40` avec border et `px-2 py-0.5 rounded-full`, uppercase `text-[10px] font-bold tracking-widest`. L'icône `FilePen` reste à gauche du texte. Le texte affiché est inchangé, donc les tests existants qui cherchent `Brouillon` passent tels quels sans modification.
- **Design token `--color-warning`** ajouté dans `index.css` (`rgb(245, 158, 11)` — amber). Expose les utilitaires Tailwind `bg-warning`, `text-warning`, `border-warning`. Ajouté à la table "Design tokens CSS" dans `AGENTS.md`. Premier cas d'usage : le tag "Brouillon". Réutilisable pour tout futur état d'avertissement non-bloquant. Choix d'implémentation conforme aux conventions AGENTS.md — aucune couleur Tailwind brute type `amber-400` n'est introduite.

## Sprint 4 — Refonte visuelle `DraftResumeCard` (2026-04-14)

Terminé le 2026-04-14.

Refonte UI des mini cartes de brouillon affichées dans le panneau déplié de `DraftsResumeStrip`, restées jusque-là très sèches (`w-64 h-10`, titre + temps relatif uniquement).

- **Nouveau format chip ~288×72 px** (`w-72 h-[72px]`) cohérent avec le langage visuel d'`EventCard` sans en dupliquer l'emprise : glassmorphism `bg-background/60 backdrop-blur-xl`, border qui s'éclaire au hover (`border-foreground/30`), lift `motion-safe:hover:-translate-y-0.5`, gradient décoratif `rounded-bl-full` dans le coin haut-droit.
- **Teinte catégorielle** via `EVENT_CATEGORIES[draft.category].color` (source canonique partagée avec `EventCard` et `EventCalendar`) : rail vertical 3 px collé au bord gauche + gradient horizontal subtil qui baigne la surface de la carte. Les cartes de catégories différentes se différencient visuellement en un coup d'œil.
- **Chaîne de fallback meta ligne 2** : `location` → `startDate` (formaté `fr-CH` `day month`) → nom de catégorie. Une ligne de meta n'est jamais vide, on surface **ce que l'utilisateur a déjà rempli** — ce qui aide à reconnaître le brouillon au lieu d'afficher des placeholders.
- **Tag `FilePen` + "Brouillon"** en haut à droite de la ligne 1, signalant l'état de façon explicite mais discrète (`text-[10px] uppercase tracking-wider text-foreground/40`).
- **Const map `titleVariants`** pour les deux classes de titre (rempli vs vide) — applique le pattern `AGENTS.md` au lieu du ternaire inline sur `className` présent dans l'ancienne version.
- **`STRIP_LAYOUT.cardWidth`** bumpé de 256 → 288 dans `src/utils/draftsResumeStripLayout.ts`. Les 9 tests de `computeStripLayout` continuent de passer tel quel (vérifié à la main sur tous les cas : largeurs 100/200/400/500/1700/2000 avec 1 à 8 brouillons). Aucun test à modifier côté layout.
- **Tests `DraftsResumeStrip`** : 4 nouveaux tests ajoutés pour couvrir le rendu du lieu, les deux branches de fallback (date courte, nom de catégorie) et la présence du tag "Brouillon" par carte. Tous les tests existants (`getByText`, `getByRole('button', { name: /.../ })`, `/Reprendre le brouillon/`, ArrowRight focus, `Brouillon sans titre` whitespace) restent verts sans modification.
- **Skeleton `drafts-resume-strip`** inchangé : il représente le header collapsed (56 px), pas les cartes — celles-ci n'apparaissent que dans le panneau déplié, qui n'est pas rendu pendant `loading`.

## Sprint 4 — Correctifs brouillons 2026-04-13 (5e passe)

Terminé le 2026-04-13 (5e passe).

Refonte de la zone CTA du `EventForm` en une rangée horizontale de vrais boutons colorés, plus discoverable que les micro-links texte précédents.

- **`Buttons.tsx` refactoré** en const map typée `buttonVariants` + base partagée (pattern `AGENTS.md`). Deux nouveaux variants ajoutés :
  - `ButtonNeutral` — gris rempli (`bg-foreground/8` + border), pour les actions de sauvegarde brouillon.
  - `ButtonDestructive` — rouge atténué (`bg-error/10` + `border-error/40` + `text-error`), pour la suppression brouillon.
  - `ButtonPrimary` (rose gradient) et `ButtonSecondary` (ghost/outline) conservés à l'identique visuellement — les usages existants (`ProfileEditPage`, `LandingPage`, `Navbar`) ne changent pas.
- **`EventForm.tsx` — zone CTA refondue** : remplacement du bloc `flex flex-col items-end` + micro-links texte par une rangée `flex flex-1 justify-end gap-3` qui remplit l'espace à droite de Capacité. Ordre de gauche à droite : `Supprimer` (si draft) · `Annuler` · `Enregistrer/Brouillon` (si save draft dispo) · `Créer l'événement` (primary, toujours à droite). Tous les boutons sont en taille `sm` pour tenir dans une seule rangée sur desktop.
- **Responsive** : sous `sm`, la rangée repasse en `flex-col items-stretch` → boutons empilés pleine largeur, ordre DOM préservé.
- **États loading inchangés** : `submitting`, `draftSaving`, `deleting` sont mutuellement exclusifs (garde-fou déjà en place dans `useEventForm`) — chaque flag n'affecte que le bouton concerné, les autres restent actifs.
- **Tests** : 8 nouveaux tests dans `Buttons.test.tsx` pour `ButtonNeutral` et `ButtonDestructive` (rendu texte, onClick, disabled, classes variant). Les tests de `EventCreatePage` / `EventEditPage` qui cliquent sur `getByRole('button', { name: ... })` continuent de fonctionner tels quels — même labels, même rôles, juste le style qui change.
- **Aucune modification de `EventCreatePage`, `EventEditPage`, `useEventForm`** — l'API externe de `EventForm` est strictement la même, seule l'implémentation du bloc CTA change.

## Sprint 4 — Correctifs brouillons 2026-04-13 (4e passe)

Terminé le 2026-04-13 (4e passe).

Refonte du bandeau brouillons en bannière collapsible animée :

- **`DraftsResumeStrip` refondu** : ancien bandeau toujours ouvert remplacé par un header fixe "Mes brouillons" (icône `Library` + `ChevronDown`) qui déplie un panneau au clic. Le panneau contient le label "Reprendre un brouillon" + les cartes + le bouton "Voir tout" à droite. État initial collapsed — l'utilisateur doit cliquer pour voir ses brouillons.
- **Librairie `@radix-ui/react-collapsible`** ajoutée au projet (premier Radix introduit — à privilégier pour les futures primitives collapsible/dialog). Gère nativement `aria-expanded`, `aria-controls`, et expose la variable CSS `--radix-collapsible-content-height` pour animer la hauteur.
- **Animations** : keyframes `drafts-panel-open` / `drafts-panel-close` déclarées dans `index.css` (~250 ms / ~200 ms, easing standard). Désactivées sous `prefers-reduced-motion` via les variantes `motion-safe:*` / `motion-reduce:*`. Rotation du chevron à 180° via `group-data-[state=open]:rotate-180`.
- **Suppression du skeleton** `drafts-resume-strip` : plus de rendu pendant `loading` (retour `null`), donc plus de consommateur pour le skeleton. Fichier `drafts-resume-strip.bones.json` supprimé, entrée retirée de `src/bones/registry.js`, table "Skeletons existants" de `components.md` et `AGENTS.md` mise à jour.
- **`ResizeObserver` déplacé** du container de la section au `panelRef` du panneau — mesure uniquement quand `open === true`, puisque Radix démonte le contenu quand le panneau est fermé (pas de `forceMount`).
- **Tests adaptés** : tous les tests qui interrogeaient les cartes doivent désormais ouvrir le panneau au préalable (`openPanel()` helper). Nouveaux tests : panneau collapsed par défaut (cartes absentes du DOM), clic toggle `aria-expanded`, deuxième clic referme, région `aria-label="Liste de mes brouillons"` visible quand ouverte, mock de `matchMedia` ajouté (Radix peut le toucher).

## Sprint 4 — Correctifs brouillons 2026-04-13 (3e passe)

Terminé le 2026-04-13 (3e passe).

Troisième vague de correctifs sur le flux brouillons, focalisée sur la suppression des brouillons et le nettoyage visuel des mini cartes :

- **`DraftResumeCard` — suppression de l'anneau de complétion** : le petit cercle rose `DraftCompletionRing` a été retiré de chaque carte. Les fichiers `DraftCompletionRing.tsx`, `computeEventCompletion.ts` et leurs tests ont été supprimés (plus aucun consommateur). Les cartes affichent désormais uniquement titre + temps relatif.
- **`DraftResumeCard` — temps relatif** : l'affichage utilise `updatedAt ?? createdAt` comme avant. Le "il y a 21 min" se met à jour à chaque re-sauvegarde du brouillon (comportement voulu, aligné sur le tri de `useMyDrafts`).
- **`EventEditPage` en mode draft — bouton "Supprimer le brouillon"** : nouveau bouton destructif (`text-error/70 hover:text-error`) dans la zone CTA, affiché uniquement en mode draft (absent du mode édition classique d'un event publié). Ouvre une modale de confirmation inline (même pattern que `EventDetailPage`, duplication acceptée pour l'instant — un composant `ConfirmDialog` partagé pourrait être extrait plus tard). Après confirmation → `deleteEvent(id)` → toast "Brouillon supprimé." → `/`. En cas d'erreur réseau, toast d'erreur et pas de redirection. Le bouton principal "Créer l'événement" reste inerte pendant la suppression (state `deleting` local).
- **`EventForm` — trois nouvelles props** : `onDelete?`, `deleting?`, `deleteLabel?`. Le bouton n'est rendu que si `onDelete` est fourni — `CreateEventPage` et le mode edit publish ne le fournissent pas → pas de bouton.

## Sprint 4 — Correctifs brouillons 2026-04-13 (suite)

Terminé le 2026-04-13 (2e passe).

Deuxième vague de correctifs sur le flux brouillons, focalisée sur la UX du strip et la confusion submit/save-draft :

- **`DraftsResumeStrip` auto-dimensionné** : le nombre de cartes affichées est désormais calculé dynamiquement en fonction de la largeur réelle du container, via un `ResizeObserver`. Plus de limite d'affichage codée en dur côté hook. Le bouton "Voir tout" apparaît au bon moment — ni trop tôt ni trop tard — et aucune carte ne peut plus être coupée en deux par le bouton.
- **`computeStripLayout`** : nouvelle fonction pure dans `src/utils/draftsResumeStripLayout.ts` qui encapsule tout le calcul (label reservé, slots sans bouton, slots avec bouton, fallback optimiste avant mesure). Totalement testable unitairement. Constantes de layout (`CARD_WIDTH`, `CARD_GAP`, `LABEL_WIDTH`, `VIEW_ALL_BUTTON_WIDTH`, etc.) centralisées dans `STRIP_LAYOUT`.
- **`useMyDrafts`** : suppression de `hasMore` du contrat (la décision d'afficher le bouton "Voir tout" appartient maintenant au composant). Fetch d'un pool plus large (`DRAFTS_FETCH_SIZE = 10`) en une seule requête, sans troncature côté hook.
- **`EditEventPage` en mode draft — wording** : le bouton secondaire "Sauvegarder en Brouillon" est renommé **"Enregistrer"** uniquement dans ce mode (l'event est déjà en brouillon, on ne le sauvegarde pas "en brouillon"). Nouvelle prop `saveDraftLabel?: string` sur `EventForm` (fallback = "Sauvegarder en Brouillon" — `CreateEventPage` reste inchangée).
- **`useEventForm` — séparation des états** : scission de l'ancien flag `submitting` en deux flags mutuellement exclusifs `submitting` (pour `handleSubmit` / `triggerPublish`) et `draftSaving` (pour `triggerDraftSave`). `EventForm` consomme les deux séparément : le bouton principal ne flip plus en "Enregistrement..." pendant un save-draft — il reste rigoureusement inchangé, ce qui évite de laisser croire à l'utilisateur qu'il vient de publier. Le bouton secondaire gère son propre état de progression. Garde-fou anti-double-clic : un appel entrant est ignoré si l'un des deux flags est déjà à `true`.

## Sprint 4 — Correctifs brouillons (2026-04-13)

Terminé le 2026-04-13.

Corrections livrées sur le flux brouillons introduit plus tôt dans le sprint :

- **Save-draft depuis `CreateEventPage`** : après un `POST /events` avec `status=DRAFT`, redirection vers `/` (landing) au lieu de `/events/:id`. Sauvegarder en brouillon signifie "je reprends plus tard" — on ne renvoie pas l'utilisateur sur l'event qu'il vient de mettre de côté. Toast "Brouillon enregistré.".
- **`DraftsResumeStrip`** : suppression du concept "Expirée" (un brouillon n'a pas de date limite). Suppression de la variante `expired` dans `DraftResumeCard` et de la logique `startDate < now()`.
- **`DraftsResumeStrip`** : ajout d'un bouton "Voir tout" (icône `ArrowRight`) tout à droite du rail, affiché **uniquement** quand `useMyDrafts` indique `hasMore === true`. Cible : `/my-events` (route à venir avec SCRUM-93 — ne pas créer la page ici).
- **`useMyDrafts`** : fetch `limit + 1 = 6` brouillons, tronque à 5 pour l'affichage, expose `hasMore` pour piloter le bouton "Voir tout".
- **`EditEventPage` mode brouillon** : quand l'event chargé a `status === 'DRAFT'`, la page bascule automatiquement en mode "terminer votre brouillon". Titre adapté, bouton principal renommé "Créer l'événement" (force `status=PUBLISHED` via le nouveau `form.triggerPublish()` du hook), bouton secondaire "Sauvegarder en Brouillon" réexposé, "Annuler" renvoie vers `/`. Publication → `/events/:id`, re-save brouillon → `/`. Pas de page dédiée : `EventEditPage` + un flag local couvrent le besoin sans duplication.
- **`useEventForm`** : nouvelle méthode `triggerPublish()` symétrique à `triggerDraftSave()`.

## Sprint 4 — Correctif UX reprise des brouillons (2026-04-13)

Terminé le 2026-04-13.

Fonctionnalités livrées :
- `DraftsResumeStrip` (`src/components/event/DraftsResumeStrip.tsx`) : bandeau compact de reprise des brouillons affiché en haut de `CreateEventPage`, entre `SectionHeader` et `EventForm`.
- `DraftResumeCard` + `DraftCompletionRing` : sous-composants visuels (carte compacte + anneau de complétion SVG).
- `useMyDrafts` (`src/hooks/useMyDrafts.ts`) : hook de chargement des brouillons de l'utilisateur via `GET /api/events?organizerId=X&status=DRAFT&size=5`, tri local par `updatedAt` DESC.
- `computeEventCompletion` + `formatRelativeTime` : utilitaires purs testables isolément.
- `getMyDrafts` dans `eventApi.ts` : helper typé autour de `getAll` (aucune modification de `getAll`).
- Skeleton `drafts-resume-strip` (`src/bones/drafts-resume-strip.bones.json`, JSON manuel) pour l'état de chargement.
- Décision architecturale : stockage en base de données (pas en localStorage) — documenté dans `specs_archives/specs_claude/specs_drafts_recovery.md`.
- Aucun nouveau endpoint backend — réutilisation stricte du filtre existant.

## Correctifs transverses — 2026-03-31

Terminé.

Fonctionnalités corrigées :
- Gestion unifiée des dates d’événements côté frontend pour interpréter les timestamps API UTC et afficher les heures en fuseau local navigateur (création, listing, détail, édition).
- Uniformisation de la granularité du sélecteur date/heure à la minute (`00:00` à `23:59`) sur les flux de création et d’édition.
- Protection du layout contre les chaînes longues non segmentées dans la bio profil et la description d’événement (`overflow-wrap` + `word-break`).
- Ajout de limites frontend pour le titre et la description d’événement (contrainte d’input + validation + feedback utilisateur).
- Remplacement du picker natif `datetime-local` par un sélecteur date + heure/minute (24h explicite) pour garantir une UX sans AM/PM sur création et édition.
- Renforcement du wrapping des titres d’événements longs sans espaces (détail et cartes) avec contraintes de flex-shrink (`min-width: 0`) et césure CSS robuste.
