# docs/architecture.md — Architecture Frontend

> **Mise à jour 2026-05-14 — post-merge PR #158.** Routes manquantes dans la table : `/admin` (AdminRoute), `/events/:id/stats` (PrivateRoute), `/403` (publique). Services manquants dans la table « Couche services » : `adminApi`, `attendanceApi`, `attendeesApi`, `reportApi`, `statsApi`. Cette PR (`feature/scrum-137-146-doc-and-views`) ajoute en plus `coOrganizerApi` (SCRUM-137) et `commentApi` (SCRUM-146).

## Rôle dans le système global

unige-events-web est la SPA du projet UNIGE Events. Elle est servie par Nginx en production et communique avec unige-events-api via des appels REST JSON sur /api.

## Architecture MVC Frontend

View : src/pages et src/components
Controller : src/services et src/router
Model : src/hooks, src/contexts et src/types

## Table de routage

| Route | Composant | Fichier | Protection |
|---|---|---|---|
| / | LandingPage | pages/LandingPage.tsx | publique |
| /login | LoginPage | pages/login/LoginPage.tsx | publique |
| /login/callback | LoginCallbackPage | pages/login/callback/LoginCallbackPage.tsx | publique |
| /events | EventsPage | pages/event/EventsPage.tsx | publique |
| /events/search | EventsSearchPage | pages/event/EventsSearchPage.tsx | publique |
| /events/:id | EventDetailPage | pages/event/EventDetailPage.tsx | publique |
| /events/new | EventCreatePage | pages/event/EventCreatePage.tsx | PrivateRoute |
| /events/:id/edit | EventEditPage | pages/event/EventEditPage.tsx | PrivateRoute |
| /events/:id/stats | EventStatsPage | pages/event/EventStatsPage.tsx | PrivateRoute (créateur ou co-org ACCEPTED) |
| /events/favorites | FavoritesPage | pages/event/favorites/FavoritesPage.tsx | PrivateRoute |
| /calendar | CalendarPage | pages/calendar/CalendarPage.tsx | publique |
| /legal | redirect | — | redirect → /legal/privacy |
| /legal/privacy | PrivacyPage | pages/legal/PrivacyPage.tsx | publique |
| /legal/terms | TermsPage | pages/legal/TermsPage.tsx | publique |
| /profile | redirect | — | redirect → /profile/me |
| /profile/me/edit | ProfileEditPage | pages/profile/ProfileEditPage.tsx | PrivateRoute |
| /profile/:id | ProfilePage | pages/profile/ProfilePage.tsx | PrivateRoute |
| /my-events | MyEventsPage | pages/my-events/MyEventsPage.tsx | PrivateRoute |
| /my-events/favorites | MyFavoritesPage | pages/my-events/MyFavoritesPage.tsx | PrivateRoute |
| /my-events/participations | MyParticipationsPage | pages/my-events/MyParticipationsPage.tsx | PrivateRoute |
| /my-events/publications | MyPublicationsPage | pages/my-events/MyPublicationsPage.tsx | PrivateRoute |
| /admin | AdminPage | pages/admin/AdminPage.tsx | AdminRoute (claim Auth0 `ADMIN`) |
| /403 | ForbiddenPage | pages/ForbiddenPage.tsx | publique |
| * | NotFoundPage | pages/NotFoundPage.tsx | publique |

Note : /profile/me/edit doit rester déclaré avant /profile/:id pour éviter que me soit capturé comme paramètre dynamique.

## Couche services

| Fichier | Rôle | Endpoints appelés |
|---|---|---|
| services/api.ts | Instance Axios centrale et intercepteur Bearer | — |
| services/tokenStore.ts | Lecture et écriture du token access_token | — |
| services/sessionId.ts | Génération + persistance d'un UUID v4 anonyme en `localStorage` pour la déduplication des vues anonymes | — |
| services/userService.ts | Lecture et mise à jour du profil utilisateur | GET /api/users/me, GET /api/users/{id}, PUT /api/users/me |
| services/eventApi.ts | Liste, détail, création, édition, annulation, upload de bannière et publication DRAFT→PUBLISHED | GET /api/events, GET /api/events/{id}, POST /api/events, PUT /api/events/{id}, DELETE /api/events/{id}, POST /api/events/{id}/image, PATCH /api/events/{id}/publish |
| services/searchApi.ts | Recherche full-text d'événements ; stub suggestions | GET /api/events/search |
| services/favoriteApi.ts | Liste, ajout et retrait des favoris | GET /api/users/me/favorites, POST/DELETE /api/events/{id}/favorite |
| services/attendanceApi.ts | Inscription / désinscription / liste des participations | POST/DELETE /api/events/{id}/attend, GET /api/users/me/attendances, GET /api/users/me/participations |
| services/attendeesApi.ts | Liste paginée des participants côté organisateur + lookup user public | GET /api/events/{id}/attendees, GET /api/users/{id} |
| services/statsApi.ts | Stats organisateur + enregistrement de vue (anonyme OK, dédup par `sessionId`) | GET /api/events/{id}/stats, POST /api/events/{id}/view |
| services/reportApi.ts | Signalement d'événement | POST /api/events/{id}/report |
| services/adminApi.ts | Dashboard modération + featured | GET/PATCH /api/admin/reports, POST/DELETE /api/admin/events/{id}/feature |
| services/coOrganizerApi.ts | Invitation / acceptation / refus de co-organisateur (SCRUM-137) | POST/GET/DELETE /api/events/{id}/co-organizers, PATCH /api/events/{id}/co-organizers/me/{accept,decline}, GET /api/users/me/co-organizer-invitations |
| services/commentApi.ts | CRUD commentaires (SCRUM-146) | POST/GET /api/events/{id}/comments, DELETE /api/comments/{id} |

## Convention URL — filtre faculty

Le filtre faculté sur la page de recherche utilise un paramètre query unique : `?faculty=SCIENCES`.
- Valeur unique, correspondant exactement à une valeur de l'enum `Faculty` (`src/types/event.ts`).
- Absent du paramètre URL quand aucune faculté n'est sélectionnée (jamais `?faculty=` vide).
- Synchronisé dans les deux sens par `useSearch` (initialisation depuis URL → état, état → URL via `replace`).
- Transmis tel quel à `GET /api/events/search?faculty=…`.

## Règles de cohérence

- Toutes les routes protégées passent par PrivateRoute.
- Tous les appels API passent par services/api.ts.
- Les pages de données gèrent toujours loading, error et data sans rendre de valeurs nulles ou indéfinies.
- Les routes événements actives aujourd’hui sont /events/new, /events/:id et /events/:id/edit. Il n’existe pas encore de page dédiée en /events.
