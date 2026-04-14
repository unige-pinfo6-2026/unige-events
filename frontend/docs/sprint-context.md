# docs/sprint-context.md — État d'avancement

Dernière mise à jour : 2026-04-14

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

## Correctifs transverses — 2026-03-31

Terminé.

Fonctionnalités corrigées :
- Gestion unifiée des dates d’événements côté frontend pour interpréter les timestamps API UTC et afficher les heures en fuseau local navigateur (création, listing, détail, édition).
- Uniformisation de la granularité du sélecteur date/heure à la minute (`00:00` à `23:59`) sur les flux de création et d’édition.
- Protection du layout contre les chaînes longues non segmentées dans la bio profil et la description d’événement (`overflow-wrap` + `word-break`).
- Ajout de limites frontend pour le titre et la description d’événement (contrainte d’input + validation + feedback utilisateur).
- Remplacement du picker natif `datetime-local` par un sélecteur date + heure/minute (24h explicite) pour garantir une UX sans AM/PM sur création et édition.
- Renforcement du wrapping des titres d’événements longs sans espaces (détail et cartes) avec contraintes de flex-shrink (`min-width: 0`) et césure CSS robuste.
