# docs/architecture.md — Architecture Frontend

## Rôle dans le système global

unige-events-web est la SPA du projet UNIGE Events. Elle est servie par Nginx en production et communique avec unige-events-api via des appels REST JSON sur /api.

## Architecture MVC Frontend

View : src/pages et src/components
Controller : src/services et src/router
Model : src/hooks, src/contexts et src/types

## Table de routage

| Route | Composant | Fichier | Protection |
|---|---|---|---|
| / | redirect | — | redirect vers /home |
| /login | LoginPage | pages/LoginPage.tsx | publique |
| /callback | CallbackPage | pages/CallbackPage.tsx | publique |
| /home | HomePage | pages/HomePage.tsx | PrivateRoute |
| /profile/me/edit | ProfileEditPage | pages/ProfileEditPage.tsx | PrivateRoute |
| /profile/:id | ProfilePage | pages/ProfilePage.tsx | PrivateRoute |
| /events/new | CreateEventPage | pages/CreateEventPage.tsx | PrivateRoute |
| /events/search | EventsSearchPage | pages/event/EventsSearchPage.tsx | publique |
| /events/:id | EventDetailPage | pages/event/EventDetailPage.tsx | publique |
| /events/:id/edit | EditEventPage | pages/event/EventEditPage.tsx | PrivateRoute |
| /calendar | CalendarPage | pages/CalendarPage.tsx | publique |
| /events/favorites | FavoritesPage | pages/event/favorites/FavoritesPage.tsx | PrivateRoute |
| /admin/* | AdminDashboard | à créer | PrivateRoute + rôle admin |
| * | redirect | — | redirect vers /home |

Note : /profile/me/edit doit rester déclaré avant /profile/:id pour éviter que me soit capturé comme paramètre dynamique.

## Couche services

| Fichier | Rôle | Endpoints appelés |
|---|---|---|
| services/api.ts | Instance Axios centrale et intercepteur Bearer | — |
| services/tokenStore.ts | Lecture et écriture du token access_token | — |
| services/userService.ts | Lecture et mise à jour du profil utilisateur | GET /api/users/me, GET /api/users/{id}, PUT /api/users/me |
| services/eventApi.ts | Liste, détail, création, édition, annulation et upload de bannière | GET /api/events, GET /api/events/{id}, POST /api/events, PUT /api/events/{id}, DELETE /api/events/{id}, POST /api/events/{id}/image |
| services/searchApi.ts | Recherche full-text d'événements ; stub suggestions | GET /api/events/search |
| services/favoriteApi.ts | Liste, ajout et retrait des favoris | GET /api/users/me/favorites, POST/DELETE /api/events/{id}/favorite |

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
