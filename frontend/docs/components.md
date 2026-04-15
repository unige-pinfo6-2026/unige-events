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
| /events/favorites | FavoritesPage | fait |
| /my-events | MyEventsPage | redirect → /my-events/favorites |
| /my-events/favorites | MyFavoritesPage | fait |
| /my-events/participations | MyParticipationsPage | fait (stub backend) |
| /my-events/publications | MyPublicationsPage | fait |

### LandingPage

- Page publique d'accueil avec Hero, section Events, Features, FAQ et GetStarted.
- Affiche les événements publiés via EventCards (composant réutilisable).
- Structure SectionWrapper/SectionHeader partagée entre toutes les sections.

### EventDetailPage

- Charge l'événement via useEvent(id).
- Affiche la bannière, la catégorie, le titre, les dates, le lieu, la capacité et la description.
- Charge l'organisateur via getUserById(event.creatorId).
- Intègre FavoriteButton (étoile toggle) dans le coin supérieur droit de la bannière.
- Bouton "Partager" : copie `location.href` dans le presse-papier ; toast "Lien copié !" via useToast, 3 secondes.
- Affiche Modifier et Supprimer uniquement pour l'organisateur.
- Ouvre une confirmation avant deleteEvent(id) puis redirige vers /.
- Utilise une UI localisée en français.

### FavoritesPage

- Route `/events/favorites`, protégée par PrivateRoute.
- Charge la liste des favoris via getFavorites() (GET /api/users/me/favorites).
- Grille d'EventCard avec FavoriteButton en état favori (étoile pleine).
- Retirer un favori depuis la liste le supprime instantanément de l'affichage via onFavoriteRemove.
- État vide : illustration étoile + message "Vous n'avez aucun favori pour le moment".

### MyEventsPage — redirect

- `/my-events` est un simple redirect vers `/my-events/favorites` (PrivateRoute).
- Les trois pages enfants (favoris, participations, publications) sont indépendantes et accessibles via le dropdown utilisateur dans la Navbar (sous-menu nested sous "Mes événements").

### MyFavoritesPage

- Route `/my-events/favorites`, protégée par PrivateRoute.
- Charge `getFavorites()` (GET /api/users/me/favorites).
- Grille d'`EventCard` (étoile pleine, retrait instantané via `onFavoriteRemove`).
- État vide : icône étoile + "Aucun favori pour le moment".
- Skeleton `my-events`.

### MyParticipationsPage

- Route `/my-events/participations`, protégée par PrivateRoute.
- Charge `useMyParticipations()` — actuellement câblé au stub `getMyParticipations()` (retourne `[]` en attendant un endpoint backend enrichi).
- Grille d'`EventCard` avec badge "Inscrit" en overlay sur chaque card. Synchronise l'état favori via `useFavoritesContext`.
- État vide : icône calendrier + "Vous ne participez à aucun événement".
- Skeleton `my-events`.

### MyPublicationsPage

- Route `/my-events/publications`, protégée par PrivateRoute — dashboard organisateur (SCRUM-93).
- Charge `useMyEvents(user.id, status)` via `GET /api/events?organizerId=<id>&status=<STATUS>`.
- Sous-onglets statut persistés en query string (`?status=published|draft|cancelled`) via `useSearchParams` (const map `STATUS_TABS`).
- **Layout cards sur tous les breakpoints** (`grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5`) — pas de table.
- `PublicationCard` local non-exporté : bannière de l'événement (ou gradient fallback basé sur la couleur de catégorie), badge catégorie en top-left, badge statut en top-right (via const map `statusVariants`), titre `line-clamp-1` avec tooltip `title`, date + participants, actions en footer (Modifier, Publier si DRAFT, Annuler).
- Actions : Modifier (`/events/:id/edit`), Publier (DRAFT → `PATCH /events/{id}/publish`), Annuler (`DELETE /events/{id}` avec `ConfirmModal` local). Tri par `startDate` décroissante.
- Bouton flottant "Créer un événement" en bas-droite.
- Skeleton `my-events`.

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
- Props : `{ id: Faculty }` — importe `Faculty` depuis `@/types/faculty`.
- Rend un `<span>` pill avec la couleur de fond hex officielle UNIGE via `style={{ backgroundColor: faculty.color }}` (inline style — pas de classe Tailwind dynamique, Tailwind ne peut pas générer `bg-[#...]` à la compilation).
- Libellé : `faculty.abbr` issu de `FACULTIES[id]`.
- `aria-label` : `faculty.name` (nom complet de la faculté).
- Les couleurs et libellés sont centralisés dans `FACULTIES` (`@/types/faculty`) — ne pas les redéfinir dans le composant.

| ID              | Couleur       | Abréviation |
|-----------------|---------------|-------------|
| SCIENCES        | `#318063`     | Sciences    |
| MEDICINE        | `#9a0050`     | Médecine    |
| LETTERS         | `#046fcb`     | Lettres     |
| SOCIAL_SCIENCES | `#fcb000`     | SdS         |
| GSEM            | `#425878`     | GSEM        |
| LAW             | `#ba0c2f`     | Droit       |
| THEOLOGY        | `#490674`     | Théologie   |
| PSYCHOLOGY      | `#00b1ae`     | Psychologie |
| FTI             | `#fe5900`     | FTI         |

### EventCard

- Carte cliquable d'un événement (design glassmorphism, variables CSS thème).
- Affiche bannière, badge catégorie, titre, date, lieu, capacité.
- Affiche systématiquement un `<FacultyBadge>` dans l'overlay de la bannière, directement sous le titre (même bloc flex-col que le titre). Quand `event.faculty` est défini, le badge montre la faculté ; sinon il affiche « Toutes facultés » avec un style neutre.
- Intègre FavoriteButton dans le coin supérieur droit de la bannière.
- Props optionnelles : `favorited` (booléen, défaut false), `onFavoriteRemove` (callback après retrait).
- Utilise les icônes Lucide et les variables `bg-background`, `text-foreground`, `border-border`.

### FavoriteButton

- Bouton icône étoile toggle réutilisable (composant `components/event/`).
- Props : `eventId`, `initialFavorited` (défaut false), `onRemove` (callback appelé après retrait réussi).
- Utilise useFavorite pour l'état local et les appels POST/DELETE /api/events/{id}/favorite.
- Optimistic update avec rollback si l'API échoue.
- Stoppe la propagation du clic pour ne pas déclencher la navigation depuis EventCard.

### EventCards

- Composant réutilisable qui orchestre `useEvents()` et affiche la grille d'`EventCard`.
- Gère les états loading, error et empty de façon autonome.
- Inclut un bouton "Charger plus" quand `hasMore` est vrai.
- Utilisé dans `LandingPage` (section Events).

### CategorySelect

- Sélecteur de catégorie natif `<select>` avec point de couleur pour la catégorie sélectionnée.
- Le point de couleur utilise `style={{ backgroundColor }}` — seule valeur inline autorisée (couleur hex dynamique).
- Props : `value: '' | EventCategory`, `onChange: (category: EventCategory) => void`, `error?: string`, `id?: string`.
- Remplace CategoryPills dans `EventForm` (Bande 3).

### EventForm

- Formulaire partagé entre création et édition.
- Prop `mode: 'create' | 'edit'` — contrôle l'affichage de la Bande 4 récurrence (create only) et la Bande 5 co-organisateurs (edit only).
- Layout v3b en **5 bandes horizontales** (CSS grid + flex) sans card glassmorphism.
  - Bande 1 : bannière cliquable (colonne gauche alignée via `pt-7 max-lg:pt-0`) | Titre + Description.
  - Bande 2 : Lieu (avec icône MapPin) | Début (avec shell checkbox "Toute la journée" S5) | Fin.
  - Bande 3 : CategorySelect | Capacité (spinners masqués) | zone CTA avec lien "Sauvegarder en Brouillon" (optionnel).
  - Bande 4 : shells non-interactifs S5/S6/S8/S9 (websiteUrl, email, deadline, mots-clés, récurrence create-only, pièces jointes).
  - Bande 5 : shell co-organisateurs (edit only, S8).
- Prop `onSaveDraft?: () => Promise<void>` — passée uniquement depuis EventCreatePage ; affiche le lien "Sauvegarder en Brouillon" quand présent.
- Champ "Faculté concernée" (Bande 3, à côté de CategorySelect et Capacité) : select avec option par défaut "Toutes facultés" (valeur vide, envoyée au backend comme `null`) + 9 valeurs issues de `Object.entries(FACULTIES)` (libellé `faculty.name`). Importe `FACULTIES` et `Faculty` depuis `@/types/faculty`. Sélection unique, optionnelle — le défaut signifie que l'événement n'est pas rattaché à une faculté en particulier.
- Reçoit ses valeurs, erreurs et callbacks depuis useEventForm.
- `ComingSoonBlock` : composant local non-exporté pour les shells backlog — icône + label + badge sprint + contenu mock.

### FilterSidebar

- Composant props-driven pour les filtres de la page de recherche.
- Filtres : `category` (checkboxes à sélection exclusive, toggle), `faculty` (chips toggle, sélection unique), `facultyNone` (chip « Toutes facultés »), `dateFrom`/`dateTo` (date inputs), bouton reset.
- Le filtre `faculty` : une rangée de chips cliquables commençant par un chip « Toutes facultés » puis un chip par valeur `Faculty`, libellé français. Cliquer le chip actif le désélectionne. La valeur est transmise au paramètre `?faculty=` de l'URL et à l'API.
- Le chip « Toutes facultés » (stocké comme `filters.facultyNone: true`) isole les événements dont `faculty` vaut `null` — c'est-à-dire ceux qui n'ont pas été rattachés à une faculté précise. Il est transmis au backend via `?facultyNone=true`.
- **Mutex client/serveur** : sélectionner « Toutes facultés » remet `faculty: undefined` ; sélectionner une faculté nommée remet `facultyNone: undefined`. Côté serveur, si les deux arrivent dans la même requête, `facultyNone` gagne (règle documentée dans openapi.yaml).
- Importe `FACULTIES` et `Faculty` depuis `@/types/faculty` (plus de `FACULTY_LABELS` ni d'enum `Faculty` dans `@/types/event`). Les libellés des chips utilisent `faculty.abbr`.
- Les changements de filtres appellent `setFilters` immédiatement sans debounce côté composant.

### Dropdown

- `src/components/utils/Dropdown.tsx` — wrapper hover CSS pur, aucun state.
- Variantes déclarées via const map typée (pattern `Blobs.tsx`) : `const aligns = { left, right }`, type inféré `keyof typeof aligns`.
- Inclut automatiquement le `ChevronDown` (rotation au hover via `group-hover:rotate-180`) — ne pas l'ajouter dans le `trigger`.
- Props : `trigger` (ReactNode affiché en permanence), `children` (contenu du panel), `align` (`keyof typeof aligns`, défaut `'left'`).
- Utilisé dans `Navbar` : `UserDropdownMenu` (`align="right"`), `NavItem` (`align` par défaut).

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

---

## Skeleton screens

Les skeletons sont définis dans `src/bones/*.bones.json` et consommés via `<Skeleton>` de `boneyard-js/react`. Le registry est dans `src/bones/registry.js`, importé au démarrage dans `main.tsx`.

**Règle** : toute page ou composant avec un appel API et un état `loading` doit avoir un skeleton. Voir `AGENTS.md` (section Skeleton screens) et `frontend/skeleton/README.md` pour le workflow complet.

| Skeleton `name` | Fichier bones | Composant consommateur | Généré par |
|---|---|---|---|
| `event-cards` | `event-cards.bones.json` | `EventCards` | `generate.mjs` |
| `event-detail` | `event-detail.bones.json` | `EventDetailPage` | manuel |
| `event-edit` | `event-edit.bones.json` | `EventEditPage` | `generate.mjs` |
| `profile` | `profile.bones.json` | `ProfilePage` | manuel |
| `search-results` | `search-results.bones.json` | `EventsSearchPage` | `generate.mjs` |
| `event-calendar` | `event-calendar.bones.json` | `EventCalendar` | `generate.mjs` |
| `navbar-user` | `navbar-user.bones.json` | `Navbar` (`DesktopNav`) | manuel |
| `my-events` | `my-events.bones.json` | `MyFavoritesPage, MyParticipationsPage, MyPublicationsPage` | manuel |

Pour régénérer les skeletons gérés par le générateur : `npm run skeleton` (depuis `frontend/`).

Pour les skeletons manuels (`event-detail`, `profile`, `navbar-user`) : éditer directement le JSON.


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
- Expose `triggerDraftSave()` : force `status = 'DRAFT'` via un `useRef` interne avant d'appeler `submitForm()`, indépendamment du statut sélectionné dans l'UI.
- Après upload de bannière, réutilise l'événement retourné par l'API.

### useMyEvents

- Hook pour l'onglet Mes Publications : charge les événements de l'organisateur pour un statut donné (`PUBLISHED | DRAFT | CANCELLED`).
- Signature : `useMyEvents(organizerId: string | null, status: EventStatus)`.
- Retourne `events`, `loading`, `error`, `refresh`, `publish(id)`, `cancel(id)`.
- `publish` appelle `PATCH /events/{id}/publish` puis retire localement l'événement de la liste courante.
- `cancel` appelle `DELETE /events/{id}` puis retire localement l'événement.
- Tri par `startDate` décroissante.

### useMyParticipations

- Hook pour l'onglet Mes Participations : charge les événements auxquels l'utilisateur s'est inscrit.
- Retourne `events`, `loading`, `error`, `refresh`.
- Implémenté via le stub `getMyParticipations()` — retourne `[]` tant que le backend n'expose pas d'endpoint enrichi.

### useFavorite

- Gère l'état favori d'un événement unique avec optimistic update.
- Params : `eventId`, `initialFavorited` (défaut false).
- Retourne `favorited`, `loading`, `toggle` (async, retourne boolean succès).
- En cas d'erreur API, rollback de l'état local.

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
- `getMyAttendance(eventId)` : filtre `GET /api/users/me/attendances` pour retourner le statut de l'utilisateur sur un événement.
- `getMyParticipations()` : **stub** retournant `[]`. TODO : remplacer par l'appel réel quand le backend exposera un endpoint d'événements participés enrichis.

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
- publishEvent(id) : passe l'événement de DRAFT à PUBLISHED via `PATCH /api/events/{id}/publish`.
- getMyEvents(params) : liste des événements créés par l'utilisateur authentifié via `GET /api/users/me/events?status=&page=&size=`. Identité dérivée du JWT, tri serveur `createdAt DESC`, tous statuts (DRAFT, PUBLISHED, CANCELLED) retournés par défaut. Consommé par `useMyEvents`.

### searchApi.ts

- searchEvents(params) : recherche full-text d’événements via `GET /api/events/search`.
- fetchSuggestions(query) : stub retournant un tableau vide (TODO — pas d’endpoint de suggestions dans openapi.yaml).

### favoriteApi.ts

- getFavorites() : liste des événements favoris via `GET /api/users/me/favorites`.
- addFavorite(eventId) : ajouter un favori via `POST /api/events/{id}/favorite`.
- removeFavorite(eventId) : retirer un favori via `DELETE /api/events/{id}/favorite`.
