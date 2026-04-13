# docs/components.md — Pages, composants et services

## Pages

| Route | Composant | État |
|---|---|---|
| / | LandingPage | fait |
| /events/new | CreateEventPage | fait |
| /events/:id | EventDetailPage | fait |
| /events/:id/edit | EditEventPage | fait |
| /profile/:id | ProfilePage | fait |
| /profile/me/edit | ProfileEditPage | fait |
| /events/search | EventsSearchPage | fait |
| /calendar | CalendarPage | fait |

### LandingPage

- Page publique d'accueil avec Hero, section Events, Features, FAQ et GetStarted.
- Affiche les événements publiés via EventCards (composant réutilisable).
- Structure SectionWrapper/SectionHeader partagée entre toutes les sections.

### EventDetailPage

- Charge l'événement via useEvent(id).
- Affiche la bannière, la catégorie, le titre, les dates, le lieu, la capacité et la description.
- Charge l'organisateur via getUserById(event.creatorId).
- Affiche Modifier et Supprimer uniquement pour l'organisateur.
- Ouvre une confirmation avant deleteEvent(id) puis redirige vers /.
- Utilise une UI localisée en français.

### CreateEventPage

- Réutilise EventForm via useEventForm en mode create.
- Crée un événement via createEvent().
- Permet un statut initial DRAFT ou PUBLISHED.
- Bloque la soumission si la date de début est dans le passé.
- Upload une bannière optionnelle puis redirige vers la page détail.
- Affiche un toast de succès ou d'erreur.

### EditEventPage

- Recharge l'événement via getById(id).
- Réutilise EventForm via useEventForm en mode edit.
- Envoie un payload complet compatible avec le PUT backend via updateEvent(id, data), en conservant aussi le bannerUrl existant tant qu'une nouvelle image n'est pas envoyée.
- Réutilise les validations de formulaire, dont la date de début future.
- Remplace la bannière via uploadEventImage(id, file).
- Affiche un toast puis redirige vers /events/:id.

### EventsSearchPage

- Route `/events/search`, layout sidebar gauche + résultats droite.
- Barre de recherche texte avec bouton loupe.
- Dropdown d'autocomplétion : affiche jusqu'à 5 suggestions (debounce 300ms via useSearch), se ferme au clic extérieur ou Escape.
- Cliquer une suggestion appelle `selectSuggestion` → déclenche immédiatement une recherche.
- Les résultats s'actualisent automatiquement après 2000ms d'inactivité (debounce via useSearch).
- Compteur de résultats toujours visible : "X événement(s) trouvé(s)".
- État vide : "Aucun résultat — essayez de modifier vos filtres ou votre recherche".
- Gère les états loading (spinner), error et success.
- Résultats affichés via `EventCard`.

## Composants réutilisables

### Toast

- Notification fixe en bas à droite, thème-compatible (`bg-background/90 backdrop-blur-xl`).
- Props : `type` (`'success' | 'error'`), `message`.
- Utilisé dans `EventCreatePage`, `EventEditPage`, `ProfileEditPage`.

### FormField

- Wrapper réutilisable : label + slot enfant + message d'erreur.
- Exporte aussi `inputClass(error?)` — classe CSS cohérente pour tous les inputs/selects/textareas.
- Utilisé dans `EventForm` et `ProfileEditPage`.

### FacultyBadge

- Composant `src/components/faculty/FacultyBadge.tsx`.
- Props : `{ faculty: Faculty }` (enum de `@/types/event`).
- Rend un `<span>` pill Tailwind avec couleur distincte par valeur (9 couleurs).
- Utilise `FACULTY_LABELS: Record<Faculty, string>` depuis `@/types/event` pour les libellés français.
- `aria-label="Faculté : <label>"` sur le span.

| Valeur      | Classe Tailwind                       | Libellé    |
|-------------|---------------------------------------|------------|
| SCIENCES    | `bg-blue-100 text-blue-800`           | Sciences   |
| LETTRES     | `bg-purple-100 text-purple-800`       | Lettres    |
| DROIT       | `bg-red-100 text-red-800`             | Droit      |
| MEDECINE    | `bg-green-100 text-green-800`         | Médecine   |
| SES         | `bg-yellow-100 text-yellow-800`       | SES        |
| PSYCHOLOGIE | `bg-pink-100 text-pink-800`           | Psychologie|
| THEOLOGIE   | `bg-orange-100 text-orange-800`       | Théologie  |
| FTI         | `bg-cyan-100 text-cyan-800`           | FTI        |
| GSI         | `bg-indigo-100 text-indigo-800`       | GSI        |

### EventCard

- Carte cliquable d'un événement (design glassmorphism, variables CSS thème).
- Affiche bannière, badge catégorie, titre, date, lieu, capacité.
- Si `event.faculty` est non-null, affiche un `<FacultyBadge>` dans l'overlay de la bannière, directement sous le titre (même bloc flex-col que le titre).
- Utilise les icônes Lucide et les variables `bg-background`, `text-foreground`, `border-border`.

### EventCards

- Composant réutilisable qui orchestre `useEvents()` et affiche la grille d'`EventCard`.
- Gère les états loading, error et empty de façon autonome.
- Inclut un bouton "Charger plus" quand `hasMore` est vrai.
- Utilisé dans `LandingPage` (section Events).

### EventForm

- Formulaire partagé entre création et édition.
- Centralise les champs titre, description, lieu, dates, catégorie, **faculté**, capacité, statut et bannière.
- Champ "Faculté concernée" : select avec option nulle ("Aucune faculté") + 9 valeurs `Faculty`. Sélection unique, optionnelle.
- Garde le placeholder et l'aperçu de bannière contenus proprement dans la carte, y compris sur mobile et avec des noms de fichiers longs.
- Reçoit ses valeurs, erreurs et callbacks depuis useEventForm.

### FilterSidebar

- Composant props-driven pour les filtres de la page de recherche.
- Filtres : `category` (checkboxes à sélection exclusive, toggle), `faculty` (chips toggle, sélection unique), `dateFrom`/`dateTo` (date inputs), bouton reset.
- Le filtre `faculty` : une rangée de chips cliquables, un par valeur `Faculty`, libellé français. Cliquer le chip actif le désélectionne. La valeur est transmise au paramètre `?faculty=` de l'URL et à l'API.
- Réutilise `Faculty` enum et `FACULTY_LABELS` de `@/types/event` et `@/components/faculty/FacultyBadge`.
- Les changements de filtres appellent `setFilters` immédiatement sans debounce côté composant.

### AttendanceButtons

- Affiche les boutons "Je suis intéressé(e)" et "Je participe" sur la page détail événement.
- Props : `eventId`, `initialAttendingCount`, `initialInterestedCount`, `initialStatus`.
- Gère les mises à jour optimistes via `useAttendance` : clic → état local mis à jour immédiatement, rollback en cas d'erreur.
- Bouton ATTENDING désactivé avec tooltip "Événement complet" quand `isFull === true` et l'utilisateur n'est pas déjà ATTENDING.
- Affiche un compteur live : "X personnes participent · Y intéressées".
- Affiche un message d'erreur inline en cas d'erreur non-409.

### CalendarSubscribeButton

- Affiche un bloc "S'abonner au calendrier" sur la page de profil de l'utilisateur connecté.
- Sans props — charge automatiquement le token via `getCalendarToken()` au montage.
- Trois liens : abonnement Apple/Outlook (`webcal://`), abonnement Google Calendar (`https://`, nouvel onglet), téléchargement direct `.ics` (attribut `download`).
- Bouton "Révoquer et régénérer le lien" : appelle `regenerateCalendarToken()`, met à jour les trois URLs, affiche un message de confirmation.
- Gère les états loading, error et regenerating.
- Visible uniquement pour `isOwnProfile` dans `ProfilePage`.

### IcsExportButton

- Affiche un bloc "Ajouter au calendrier" sur la page détail événement.
- Props : `event` (objet `Event` complet).
- Bouton "Télécharger .ics" : génère un fichier ICS via `generateIcs`, crée un Blob et déclenche le téléchargement côté client.
- Lien "Google Calendar" : ouvre Google Calendar pré-rempli via `buildGoogleCalendarUrl`, s'ouvre dans un nouvel onglet.

### Avatar

- Affiche soit une image soit des initiales à partir de displayName.
- Réutilisé dans la navigation, les profils et la page détail événement.


## Hooks

### useEvents

- Charge les événements publiés par pages de 12.
- Retourne events, loading, error, hasMore et loadMore.

### useEvent

- Charge un événement unique à partir de son identifiant.
- Retourne event, loading et error.

### useCalendarEvents

- Charge les événements publiés du mois affiché via GET /api/events avec endDateFrom = 1er jour du mois.
- Filtre côté client les événements démarrant avant la fin du mois.
- Accepte une date courante en paramètre ; retourne events (format react-big-calendar), loading, error.
- Se recharge automatiquement à chaque changement de date.

### useEventForm

- Centralise l'état du formulaire, la validation, l'aperçu local de bannière et la soumission.
- Valide les champs requis, l'ordre des dates, la capacité positive et la date de début dans le futur.
- En création, envoie le statut initial choisi au backend.
- En édition, envoie un payload complet pour rester cohérent avec le PUT documenté, y compris le bannerUrl déjà présent.
- Traduit les erreurs backend techniques en messages français plus utiles, tout en réutilisant les détails de validation quand ils sont disponibles.
- Après upload de bannière, réutilise l'événement retourné par l'API.

### useEventSearch

- Initialise l'état depuis les query params URL au montage.
- Synchronise état → URL (replace) via `useSearchParams` React Router v6.
- Debounce 300ms sur `query` → `fetchSuggestions` → met à jour `suggestions` (max 5).
- Debounce 2000ms sur `query` + `filters` → `searchEvents` → met à jour `results`.
- Expose : `query`, `setQuery`, `filters`, `setFilters`, `results`, `suggestions`, `loading`, `error`, `resetFilters()`, `selectSuggestion(text)`.
- `selectSuggestion` définit `query`, vide `suggestions`, et déclenche immédiatement une recherche.

### useAttendance

- Gère l'état d'inscription d'un utilisateur à un événement.
- Params : `eventId`, `initialAttendingCount`, `initialInterestedCount`, `initialStatus`.
- Expose : `currentStatus`, `attendingCount`, `interestedCount`, `loading`, `error`, `isFull`, `toggle(status)`.
- Mise à jour optimiste : état local mis à jour avant la résolution de l'API, rollback si erreur.
- Erreur 409 → `isFull = true` (pas de message `error` générique dans ce cas).

## Services

### attendanceApi.ts

- `attend(eventId, status)` : `POST /api/events/{id}/attend` avec body `{ status }` — upsert.
- `unattend(eventId)` : `DELETE /api/events/{id}/attend`.

### icsGenerator.ts

- `generateIcs(event)` : retourne une chaîne RFC 5545 (.ics) avec VCALENDAR, VEVENT, UID, DTSTART, DTEND, SUMMARY, LOCATION et DESCRIPTION optionnelle. Échappe les caractères spéciaux et applique le line folding à 75 octets.
- `buildGoogleCalendarUrl(event)` : retourne l'URL Google Calendar pré-remplie (action=TEMPLATE, text, dates, location, details optionnel).

### eventApi.ts

- getAll(params) : liste paginée d'événements.
- getById(id) : détail d'un événement.
- createEvent(data) : création d'événement.
- updateEvent(id, data) : mise à jour d'événement.
- uploadEventImage(id, file) : upload de bannière et retour de l'événement mis à jour.
- deleteEvent(id) : annulation soft-delete d'un événement.
- getAll(params) : liste paginée d’événements.
- getById(id) : détail d’un événement.
- createEvent(data) : création d’événement.
- updateEvent(id, data) : mise à jour d’événement.
- uploadEventImage(id, file) : upload de bannière et retour de l’événement mis à jour.
- deleteEvent(id) : annulation soft-delete d’un événement.

### searchApi.ts

- searchEvents(params) : recherche full-text d’événements via `GET /api/events/search`.
- fetchSuggestions(query) : stub retournant un tableau vide (TODO — pas d’endpoint de suggestions dans openapi.yaml).
