# docs/sprint-context.md — État d'avancement

Dernière mise à jour : 2026-04-03

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

## Correctifs transverses — 2026-03-31

Terminé.

Fonctionnalités corrigées :
- Gestion unifiée des dates d’événements côté frontend pour interpréter les timestamps API UTC et afficher les heures en fuseau local navigateur (création, listing, détail, édition).
- Uniformisation de la granularité du sélecteur date/heure à la minute (`00:00` à `23:59`) sur les flux de création et d’édition.
- Protection du layout contre les chaînes longues non segmentées dans la bio profil et la description d’événement (`overflow-wrap` + `word-break`).
- Ajout de limites frontend pour le titre et la description d’événement (contrainte d’input + validation + feedback utilisateur).
- Remplacement du picker natif `datetime-local` par un sélecteur date + heure/minute (24h explicite) pour garantir une UX sans AM/PM sur création et édition.
- Renforcement du wrapping des titres d’événements longs sans espaces (détail et cartes) avec contraintes de flex-shrink (`min-width: 0`) et césure CSS robuste.
