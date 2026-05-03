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
| /events/:id/stats | EventStatsPage | fait (S6) |

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
- **Bloc "Informations complémentaires" (SCRUM-117)** — affiché conditionnellement uniquement quand au moins un des 4 champs optionnels est présent :
  - `websiteUrl` → ancre `target="_blank" rel="noopener noreferrer"` avec icône `Globe` ; texte cliquable = l'URL brute.
  - `contactEmail` → ancre `mailto:` avec icône `Mail`.
  - `registrationDeadline` → libellé "Inscriptions jusqu'au" + valeur formatée via `formatEventDateTime` avec icône `CalendarClock`.
  - `tags[]` → chips cliquables via `<Link>` vers `/events/search?q=<tag>` (encodage URI côté client via `encodeURIComponent`) avec icône `Tag`. Le backend `/events/search` ne supporte pas de paramètre `tag` dédié ; on réutilise donc la recherche full-text `q` qui matche titre/description/tags.

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

### EventStatsPage

- Route `/events/:id/stats`, protégée par PrivateRoute — réservé à l'organisateur de l'événement.
- Charge l'événement via `useEvent(id)`, puis les stats via `useEventStats(id)` (auto-refresh toutes les 60 s).
- Vérifie que `user.id === event.creatorId` avant de charger les stats (évite le 403).
- KPI cards : 👁 Vues totales (`stats.viewCount`), ⭐ Intéressés (`stats.interestedCount`), ✅ Inscrits (`stats.attendingCount`).
- `StatsChart` : BarChart vertical recharts (Vues / Intéressés / Inscrits).
- Barre de progression taux de remplissage : `attendingCount / capacity * 100`.
- Section collapsible "Voir les participants" : `GET /events/{id}/attendees` → liste avec avatars et noms (fetch users en parallèle via `getUserById`).
- Skeleton `event-stats` (2 breakpoints : 300 / 600 px).

### CreateEventPage

- Réutilise EventForm via useEventForm en mode create.
- Crée un événement via createEvent().
- Permet un statut initial DRAFT ou PUBLISHED.
- Bloque la soumission si la date de début est dans le passé.
- Upload une bannière optionnelle puis redirige vers la page détail.
- Affiche un toast de succès ou d'erreur.
- Après un **save-draft** réussi : redirection vers `/` (landing page), pas vers `/events/:id` — sauvegarder en brouillon est un "je reprends plus tard".
- Intègre `DraftsResumeStrip` entre le header et le formulaire pour reprendre les brouillons existants.

### EditEventPage

- Recharge l'événement via getById(id).
- Réutilise EventForm via useEventForm en mode edit.
- Envoie un payload complet compatible avec le PUT backend via updateEvent(id, data), en conservant aussi le bannerUrl existant tant qu'une nouvelle image n'est pas envoyée.
- Réutilise les validations de formulaire, dont la date de début future.
- Remplace la bannière via uploadEventImage(id, file).
- Affiche un toast puis redirige vers /events/:id.
- **Mode brouillon (resume)** : si l'event chargé a `status === 'DRAFT'`, la page bascule automatiquement en mode "terminer le brouillon" — titre "Terminer votre brouillon", bouton principal "Créer l'événement" qui force `status=PUBLISHED` via `form.triggerPublish()`, bouton secondaire renommé **"Enregistrer"** (via la prop `saveDraftLabel` de `EventForm` — l'event est déjà en brouillon, on ne "sauvegarde pas en brouillon", on l'enregistre tel quel pour plus tard), et "Annuler" renvoie vers `/`. La publication redirige vers `/events/:id` ; un re-save en brouillon redirige vers `/`.
- Pendant un clic sur "Enregistrer", le bouton principal "Créer l'événement" reste **inchangé** (ni disabled, ni label "Enregistrement...") — seul le bouton secondaire passe en état de progression. Grâce au flag `draftSaving` séparé de `submitting` (voir `useEventForm`).
- **Bouton "Supprimer le brouillon"** (uniquement en mode draft) : ouvre une modale de confirmation inline (même pattern visuel que la suppression sur `EventDetailPage`, sans composant partagé pour l'instant). Après confirmation → appel `deleteEvent(id)` → toast "Brouillon supprimé." → navigation vers `/`. En cas d'échec réseau → toast "Impossible de supprimer ce brouillon.", on reste sur la page. Le state `deleting` local garantit que le bouton principal "Créer l'événement" reste inerte pendant l'appel (même principe que `draftSaving`).

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
- Filtre par mots-clés : section `<TagInput>` dans la sidebar (SCRUM-132), multi-tags, persistés dans l'URL via `?tags=foo&tags=bar`.

## Composants réutilisables

### ImageCropper

- Composant modal générique de recadrage d'image : `src/components/utils/ImageCropper.tsx`.
- Props : `src: string` (data URL), `aspect: number` (ex. `1` pour carré, `3` pour bannière 3:1), `circular?: boolean` (crop circulaire pour avatar), `onCropComplete: (blob: Blob) => void`, `onCancel: () => void`.
- Utilise `ReactCrop` de **react-image-crop** avec `keepSelection` et `circularCrop` optionnel.
- À la confirmation, applique le crop via un `<canvas>` (`canvas.toBlob()`) et appelle `onCropComplete` avec le `Blob` résultant.
- Overlay sombre (`bg-black/70 backdrop-blur-sm`), boutons "Recadrer" (`ButtonPrimary`) / "Annuler" (`ButtonSecondary`).
- Le bouton "Recadrer" est désactivé tant qu'aucune zone de crop n'est sélectionnée.
- Utilisable pour avatar (aspect 1, circular), bannière profil (aspect 3:1) et bannière événement (aspect 16:9).

**Intégrations actives (SCRUM-123) :**
- `ProfileEditPage` — avatar (aspect 1:1, circular) et bannière profil (aspect 3:1)
- `EventForm` (consommé par `EventCreatePage` et `EventEditPage`) — bannière événement (aspect 16:9)

Le flux d'intégration (sélection fichier → validation → FileReader → modale crop → confirm → File final) est centralisé dans le hook réutilisable `useImageCropFlow` (`@/hooks/useImageCropFlow`).

### Buttons

Composants centralisés dans `src/components/utils/Buttons.tsx`. API uniforme (`children`, `onClick`, `type`, `disabled`, `size`), variantes implémentées via une **const map typée** `buttonVariants` + classes de base partagées — pas de ternaires inline. Quatre variants exposés :

- **`ButtonPrimary`** — gradient rose (`from-accent to-pink-600`), texte blanc, shadow colorée. Utilisé pour l'action primaire positive de chaque page (créer, enregistrer, publier).
- **`ButtonSecondary`** — fond transparent + border, hover accent. Utilisé comme bouton "ghost" / annulation / navigation secondaire. Déjà en place dans `ProfileEditPage`, `LandingPage`, `EventForm` (bouton "Annuler").
- **`ButtonNeutral`** — fond gris neutre rempli (`bg-foreground/8`) + border légère, texte foreground. Action neutre de sauvegarde/brouillon. Utilisé dans `EventForm` pour "Sauvegarder en Brouillon" / "Enregistrer".
- **`ButtonDestructive`** — fond rouge atténué (`bg-error/10`) + border error + texte error. Action destructive. Utilisé dans `EventForm` pour "Supprimer le brouillon" (mode draft uniquement).

Toutes les variantes partagent `focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-background` avec un ring coloré selon le variant (accent pour primary/secondary/neutral, error pour destructive). Tailles disponibles : `sm` / `md` / `lg`, déclarées dans la const map `sizes`.

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
  - Bande 3 : CategorySelect | Faculté concernée | Capacité (spinners masqués) | **zone CTA horizontale** qui se colle à droite via `ml-auto` et rassemble les vrais boutons d'action (voir ci-dessous).
  - Bande 4 (SCRUM-117) : champs additionnels réels — ligne 1 grille 2 colonnes `websiteUrl` (Input `type="url"`, icône `Globe`, max 500 car.) + `contactEmail` (Input `type="email"`, icône `Mail`, max 255 car.) ; ligne 2 `registrationDeadline` (date + heure + minute via `renderDateTimeField`, le sélecteur horaire reste visible même quand `allDay` est actif car il ne suit pas le toggle de l'événement) ; ligne 3 `TagInput` mots-clés (max 20 tags × 16 car.) avec compteur. Les shells non-interactifs restants (récurrence S8 create-only, pièces jointes S9) sont toujours présents sous un séparateur.
  - Bande 5 : shell co-organisateurs (edit only, S8).
- **Zone CTA (Bande 3)** : rangée `flex flex-wrap items-center gap-3 ml-auto` qui prend sa largeur naturelle et se colle contre le bord droit du formulaire — l'espace à gauche est occupé par CategorySelect, Faculté et Capacité. Chaque action est un vrai bouton (plus de micro-links texte) :
  - `ButtonDestructive` "Supprimer le brouillon" — rendu uniquement si `onDelete` fourni (mode draft).
  - `ButtonSecondary` "Annuler" — rendu uniquement si `onCancel` fourni. **Absent en mode draft-edit** : l'utilisateur peut "finir plus tard" en cliquant sur "Enregistrer" (save draft), ce qui rend le bouton Annuler redondant et permet de garder les 4 boutons sur une seule ligne.
  - `ButtonNeutral` "Sauvegarder en Brouillon" / "Enregistrer" — rendu si `onSaveDraft` fourni, label contrôlé par `saveDraftLabel`.
  - `ButtonPrimary` "Créer l'événement" / "Enregistrer" — action primaire rose, toujours à droite, label = `submitLabel`.
  - Ordre sémantique de gauche à droite : destructif → annuler (si présent) → save (si présent) → primary. L'action dangereuse est bien séparée à gauche, l'action primaire bien mise en avant à droite.
  - Responsive `< sm` : la rangée repasse en `flex-col` avec `items-stretch` → les boutons s'empilent verticalement pleine largeur.
  - Les trois états loading (`submitting`, `draftSaving`, `deleting`) sont mutuellement exclusifs et n'affectent que le bouton concerné — les autres restent pleinement actifs pour ne pas induire en erreur.
- Prop `onSaveDraft?: () => Promise<void>` — passée depuis EventCreatePage ou EventEditPage (mode draft) ; affiche le bouton `ButtonNeutral` "Sauvegarder en Brouillon" quand présent.
- Champ "Faculté concernée" (Bande 3, entre CategorySelect et Capacité) : select avec option par défaut "Toutes facultés" (valeur vide, envoyée au backend comme `null`) + 9 valeurs issues de `Object.entries(FACULTIES)` (libellé `faculty.name`). Importe `FACULTIES` et `Faculty` depuis `@/types/faculty`. Sélection unique, optionnelle — le défaut signifie que l'événement n'est pas rattaché à une faculté en particulier.
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

### AttendeesList

- Section "Participants" insérée dans la **colonne principale** de `EventDetailPage`, immédiatement sous le bloc "À propos". Mêmes primitives de card que les autres blocs de la colonne (glassmorphism, heading `text-xs font-bold uppercase tracking-widest text-foreground/30`).
- Props : `eventId: number`, `isOrganizer: boolean`, `attendingCount: number`.
- **Vue non-organisateur (variante compacte)** : ligne unique inline — 1 à 5 placeholders d'avatar empilés + libellé `"X personne(s) participe(nt)"`. Padding vertical réduit (`px-6 py-4`). Aucun appel API. La compacité est dérivée automatiquement de `!isOrganizer` (const map `sectionVariants`).
- **Vue organisateur** : utilise `useAttendees(eventId)` et rend deux onglets accessibles au clavier — `"Participent"` (filtre `status === 'ATTENDING'`) et `"Liste d'attente"` (filtre `status === 'WAITLISTED'`). Chaque onglet affiche son compteur entre parenthèses.
- Liste des `AttendeeCard` rendue **en colonne unique** (`flex flex-col gap-3`) — la colonne de contenu est étroite, un layout vertical scanne mieux qu'une grille 2 colonnes. Bouton "Charger plus" en bas (visible uniquement si `hasMore === true`, désactivé pendant le chargement).
- États gérés : skeleton de chargement initial (4 placeholders empilés), message d'empty state par onglet, message d'erreur avec bouton `Réessayer`.
- Si `useAttendees` retourne `isForbidden: true` (filet de sécurité), bascule sur la vue résumé non-organisateur.

### AttendeeCard

- Carte d'un participant (`src/components/attendees/AttendeeCard.tsx`).
- Props : `attendance: Attendance`, `profile: UserPublicResponse | null`.
- Si `profile !== null` : avatar (`UserAvatar`) + `displayName` + meta `studyLevel · faculté.abbr`. Lien `/profile/{profile.id}`.
- Si `profile === null` : avatar placeholder (`aria-label="Avatar anonyme"`) + libellé "Utilisateur anonyme" — non cliquable.
- Affiche `WaitlistBadge` quand `attendance.status === 'WAITLISTED'`.

### WaitlistBadge

- Petit badge `"Liste d'attente"` réutilisable (`src/components/attendees/WaitlistBadge.tsx`), basé sur `bg-warning/10 border-warning/40 text-warning` pour rester cohérent avec `AttendanceButtons`.

### CalendarSubscribeButton

- Affiche un bloc "S'abonner au calendrier" sur la page de profil de l'utilisateur connecté.
- Sans props — charge automatiquement le token via `getCalendarToken()` au montage.
- Trois liens : abonnement Apple/Outlook (`webcal://`), abonnement Google Calendar (`https://`, nouvel onglet), téléchargement direct `.ics` (attribut `download`).
- Bouton "Révoquer et régénérer le lien" : appelle `regenerateCalendarToken()`, met à jour les trois URLs, affiche un message de confirmation.
- Gère les états loading, error et regenerating.
- Visible uniquement pour `isOwnProfile` dans `ProfilePage`.

### MyPublicationsPreview

- Composant `src/components/profile/MyPublicationsPreview.tsx` rendu uniquement pour `isOwnProfile` dans `ProfilePage`, en colonne gauche sous la card "À propos".
- Mini-tabs `Publiés` (défaut) / `Brouillons` / `Annulés` ; chaque clic rappelle `useMyEvents(status)` avec le nouveau statut (mêmes refetchs que `MyPublicationsPage`, pas de partage de state). Le compte `(N)` est affiché sur l'onglet actif uniquement — le hook ne fetch qu'un statut à la fois et on évite des requêtes parallèles juste pour les libellés des onglets inactifs.
- Affiche jusqu'à 3 événements via `PreviewRow` (l'API renvoie déjà `createdAt DESC`).
- Loading : 3 lignes skeleton inline (Tailwind `animate-pulse`). Erreur : message + bouton `Réessayer` qui appelle `refresh()` du hook.
- Empty state spécifique par statut. Le tab `Publiés` vide affiche en plus un CTA `Créer un événement` → `/events/new` ; les autres tabs vides n'affichent que le message.
- Lien `Voir toutes mes publications` → `/my-events/publications?status=<param>` où `<param>` provient de `EVENT_STATUS_PARAMS` (cf. `src/utils/eventStatusStyles.ts`), pour préserver l'onglet courant à la navigation.

### PreviewRow

- Composant `src/components/profile/PreviewRow.tsx` consommé par `MyPublicationsPreview`.
- Props : `{ event: Event }`. Lecture seule, pas d'actions.
- Rendu : `<Link>` vers `/events/{id}` enveloppant une vignette 48px (bannière ou gradient catégorie en fallback), titre `line-clamp-1`, ligne meta date + `attendingCount`, badge statut (libellé via `EVENT_STATUSES[status].name`, classes via `EVENT_STATUS_VARIANTS`).

### IcsExportButton

- Affiche un bloc "Ajouter au calendrier" sur la page détail événement.
- Props : `event` (objet `Event` complet).
- Bouton "Télécharger .ics" : génère un fichier ICS via `generateIcs`, crée un Blob et déclenche le téléchargement côté client.
- Lien "Google Calendar" : ouvre Google Calendar pré-rempli via `buildGoogleCalendarUrl`, s'ouvre dans un nouvel onglet.

### Avatar

- Affiche soit une image soit des initiales à partir de displayName.
- Réutilisé dans la navigation, les profils et la page détail événement.

### DraftsResumeStrip

- **Bannière collapsible** `@radix-ui/react-collapsible` insérée au-dessus de `EventForm` dans `CreateEventPage`.
- **Header fixe (56 px)** toujours visible quand l'utilisateur a ≥ 1 brouillon : icône `Library` (lucide-react) + texte "Mes brouillons" à gauche, chevron `ChevronDown` à droite qui pivote à 180° quand le panneau est ouvert. Tout le header est cliquable (c'est le `Collapsible.Trigger`) — Entrée/Espace togglent aussi.
- **Panneau dépliable** rendu en dessous, qui pousse le contenu de la page vers le bas (pas d'overlay). Le panneau contient le label "Reprendre un brouillon" + les `DraftResumeCard` + le bouton "Voir tout" à droite du rail.
- **État initial : collapsed.** L'utilisateur doit cliquer pour voir ses brouillons. Pas de persistance `sessionStorage`/`localStorage` — à chaque montage, le panneau repart fermé.
- **Animation** ~250 ms à l'ouverture, ~200 ms à la fermeture, via les keyframes `drafts-panel-open` / `drafts-panel-close` déclarées dans `index.css`. Elles consomment la variable CSS `--radix-collapsible-content-height` fournie par Radix. Désactivées si `prefers-reduced-motion: reduce` (via les préfixes `motion-safe:`). Le chevron utilise `motion-reduce:transition-none`.
- **États** :
  - `loading` → skeleton `drafts-resume-strip` systématiquement rendu (fichier bones manuel, même hauteur 56 px et même layout que le header collapsed : icône gauche + texte + chevron droit). Fixture interne `DraftsHeaderFixture`.
  - `error` → retour `null`. Pas de header fantôme en cas d'échec réseau.
  - `drafts.length === 0` → la bannière s'affiche quand même avec son header "Mes brouillons" cliquable ; le panneau déplié affiche un message centré **"Aucun brouillon"** (italique, `text-foreground/50`) au lieu des cartes. L'utilisateur voit toujours le même emplacement, sans CLS entre l'état vide et l'état peuplé.
  - `drafts.length >= 1` → bannière collapsed, panneau contenant les cartes + bouton "Voir tout" conditionnel.
- **Auto-dimensionné** : le nombre de cartes affichées **dans le panneau ouvert** est calculé dynamiquement en fonction de la largeur réelle du panneau (via `ResizeObserver` sur le `panelRef`, seulement quand `open === true`). Algorithme délégué à `computeStripLayout(availableWidth, totalDrafts)` (pure function dans `src/utils/draftsResumeStripLayout.ts`), totalement testable. Les constantes de layout vivent dans `STRIP_LAYOUT`.
- **Bouton "Voir tout"** à droite du rail, visible si et seulement si `computeStripLayout` conclut que le nombre de brouillons dépasse ce que le panneau peut afficher à sa largeur courante. Clic → `/my-events` (route à venir avec SCRUM-93).
- **Accessibilité** : `Collapsible.Trigger` gère nativement `aria-expanded` et `aria-controls`. Le panneau a `role="region"` + `aria-label="Liste de mes brouillons"`. Le rail des cartes a `role="toolbar"` + navigation clavier flèches gauche/droite entre cartes (inchangée). Pas de vol de focus à l'ouverture — l'utilisateur Tab dedans manuellement.

### DraftResumeCard

- Sous-composant de `DraftsResumeStrip`, carte chip-like compacte (`w-72 h-[72px]`, soit 288×72 px) d'un brouillon.
- Langage visuel aligné sur `EventCard` (glassmorphism `bg-background/60 backdrop-blur-xl`, border `border-border` → hover `border-foreground/30`, lift `motion-safe:hover:-translate-y-0.5`, gradient décoratif coin haut-droit `rounded-bl-full`) mais restreint à un format dock.
- **Teinte catégorielle** : rail vertical 3 px collé à gauche + gradient horizontal `linear-gradient(to right, {categoryColor}26, transparent 72%)` qui baigne subtilement la surface. La couleur provient de `EVENT_CATEGORIES[draft.category].color` — même source que `EventCard` et `EventCalendar`, zéro duplication.
- **Hiérarchie typographique** en deux lignes :
  - Ligne 1 : titre tronqué en `text-sm font-semibold text-foreground` (ou "Brouillon sans titre" en italique `text-foreground/60`) + tag `FilePen` + "Brouillon" en `text-[10px] uppercase tracking-wider text-foreground/40` collé à droite.
  - Ligne 2 : icône de meta teintée à la couleur de catégorie + label + `·` + temps relatif en `text-foreground/45`.
- **Chaîne de fallback meta** (priorité décroissante) : `location` non vide → `MapPin` + lieu · sinon `startDate` parseable → `Calendar` + date courte `day month` en `fr-CH` (ex. "1 mai") · sinon `Tag` + nom de catégorie. **Jamais de ligne vide** — un champ absent est remplacé, pas affiché en placeholder.
- Deux variantes de classes titre encapsulées dans une const map `titleVariants: Record<'filled' \| 'empty', string>` (pattern `AGENTS.md`, pas de ternaire inline sur les classes).
- Accessibilité : `aria-label` complet `"Reprendre le brouillon « {title} » — {category}, modifié {relativeTime}"`. Tous les éléments décoratifs (rail, gradient, corner glow, icônes meta) sont `aria-hidden`. Navigation clavier flèches gauche/droite toujours gérée par le parent `DraftsResumeStrip` (inchangée).
- Props : `draft: Event`, `onOpen: (id: number) => void`.

---

## Skeleton screens

Les skeletons sont définis dans `src/bones/*.bones.json` et consommés via `<Skeleton>` de `boneyard-js/react`. Le registry est dans `src/bones/registry.js`, importé au démarrage dans `main.tsx`.

**Règle** : toute page ou composant avec un appel API et un état `loading` doit avoir un skeleton. Voir `AGENTS.md` (section Skeleton screens) et `frontend/skeleton/README.md` pour le workflow complet.

| Skeleton `name` | Fichier bones | Composant consommateur | Généré par |
|---|---|---|---|
| `event-cards` | `event-cards.bones.json` | `EventCards`, `MyFavoritesPage`, `MyParticipationsPage` | `generate.mjs` |
| `event-detail` | `event-detail.bones.json` | `EventDetailPage` | `generate.mjs` |
| `event-edit` | `event-edit.bones.json` | `EventEditPage` | `generate.mjs` |
| `my-publications` | `my-publications.bones.json` | `MyPublicationsPage` | `generate.mjs` |
| `profile` | `profile.bones.json` | `ProfilePage` | manuel |
| `search-results` | `search-results.bones.json` | `EventsSearchPage` | `generate.mjs` |
| `event-calendar` | `event-calendar.bones.json` | `EventCalendar` | `generate.mjs` |
| `navbar-user` | `navbar-user.bones.json` | `Navbar` (`DesktopNav`) | manuel |
| `user-identity-inline` | `user-identity-inline.bones.json` | `UserIdentity` (inline) | manuel |
| `user-identity-card` | `user-identity-card.bones.json` | `UserIdentity` (card) | manuel |
| `drafts-resume-strip` | `drafts-resume-strip.bones.json` | `DraftsResumeStrip` (header collapsed, conditionnel via hint sessionStorage) | manuel |
| `event-stats` | `event-stats.bones.json` | `EventStatsPage` | manuel |

Pour régénérer les skeletons gérés par le générateur : `npm run skeleton` (depuis `frontend/`).

Pour les skeletons manuels (`profile`, `navbar-user`, `user-identity-*`) : éditer directement le JSON.


## Hooks

### useImageCropFlow

Hook utilitaire qui encapsule le flux complet « sélection fichier → validation → FileReader → ouverture du cropper → conversion Blob → File ». Utilisé par `ProfileEditPage` (×2 : avatar + bannière) et `useEventForm` (×1 : bannière événement).

Options : `aspect`, `circular?`, `validate?`, `onValidationError?`.
Résultat : `cropSource`, `handleFileSelect`, `aspect`, `circular`, `confirmCrop`, `cancelCrop`.

Garantit la **réinitialisation de l'input file** après confirm/cancel/erreur — sans cela, re-sélectionner le même fichier ne redéclenche pas l'event `change` (comportement HTML standard). Préserve le nom original du fichier lors de la conversion Blob → File.

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
- **Validation des 4 champs optionnels (SCRUM-117)** :
  - `websiteUrl` : trimmé ; vide = OK ; sinon doit être parsable par `new URL()` avec protocole `http:` ou `https:` ; longueur ≤ `EVENT_WEBSITE_URL_MAX_LENGTH` (500).
  - `contactEmail` : trimmé ; vide = OK ; sinon regex `^[^\s@]+@[^\s@]+\.[^\s@]+$` ; longueur ≤ `EVENT_CONTACT_EMAIL_MAX_LENGTH` (255). Le backend reste autoritatif via `@Email`.
  - `registrationDeadline` : optionnelle ; si fournie, doit être une date valide et **strictement antérieure à `startDate`** (comparaison front uniquement — le backend ne valide pas cette règle croisée, explicitement).
  - `tags` : tableau ; ≤ `EVENT_TAGS_MAX_ITEMS` (20) tags, chacun ≤ `EVENT_TAG_MAX_LENGTH` (16) caractères.
- Le payload envoyé normalise les chaînes trimmées vides en `null` pour `websiteUrl`/`contactEmail`/`registrationDeadline`, et un tableau vide en `null` pour `tags` — cohérent avec le contrat PUT à sémantique de remplacement complet.
- En création, envoie le statut initial choisi au backend.
- En édition, envoie un payload complet pour rester cohérent avec le PUT documenté, y compris le bannerUrl déjà présent.
- Traduit les erreurs backend techniques en messages français plus utiles, tout en réutilisant les détails de validation quand ils sont disponibles.
- Expose `triggerDraftSave()` (force `status=DRAFT` avant submit) et `triggerPublish()` (force `status=PUBLISHED` avant submit) — utilisés respectivement par le bouton "Sauvegarder en Brouillon" et par le flux draft-edit de `EditEventPage` pour publier un brouillon existant.
- **Deux flags d'état séparés** : `submitting: boolean` (vrai pendant `handleSubmit` / `triggerPublish` — le flux "publiant") et `draftSaving: boolean` (vrai pendant `triggerDraftSave`). Cette séparation permet à `EventForm` de ne pas basculer le bouton principal en "Enregistrement..." quand l'utilisateur clique sur le bouton secondaire de sauvegarde brouillon — sinon on laissait croire à une publication en cours. Les deux flags sont mutuellement exclusifs : un appel entrant est ignoré si l'un des deux est déjà à `true` (garde-fou anti-double-clic).
- Expose `triggerDraftSave()` : force `status = 'DRAFT'` via un `useRef` interne avant d'appeler `submitForm()`, indépendamment du statut sélectionné dans l'UI.
- Après upload de bannière, réutilise l'événement retourné par l'API.

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

### useMyDrafts

- Charge les brouillons de l'utilisateur connecté via `GET /api/events?organizerId=X&status=DRAFT&size=10`.
- Params : `organizerId: string | undefined`.
- Retourne `{ drafts: Event[], loading: boolean, error: string | null }` — **pas** de `hasMore`, la décision d'afficher un bouton "Voir tout" vit côté composant (`DraftsResumeStrip`) car elle dépend de la largeur mesurée.
- Constante `DRAFTS_FETCH_SIZE = 10` : pool large fetché en une requête ; le composant choisit ensuite combien en afficher selon la place disponible.
- Tri local par `updatedAt` DESC (fallback `createdAt`).
- Erreur réseau → `error` rempli + `console.warn`, `drafts = []`, pas de retry.

### useAttendees

- Charge la liste paginée des participants d'un événement pour la vue organisateur.
- Signature : `useAttendees(eventId, { enabled?, pageSize? })`. `pageSize` défaut `20`. Avec `enabled: false`, aucun fetch.
- Pour chaque `Attendance` retournée, fetch `getPublicUser(userId)` en parallèle via `Promise.allSettled` — un 403/404 sur un profil n'invalide pas le batch, le profil est mappé à `null`.
- Retourne : `attendees: AttendeeWithProfile[]`, `isLoading`, `error`, `hasMore`, `loadMore()`, `isForbidden`.
- Pagination cumulative : `loadMore()` incrémente la page et concatène en dédupliquant par `attendance.id`. `hasMore` passe à `false` dès qu'une page contient moins de `pageSize` items.
- Réponse 403 sur `/attendees` → `isForbidden = true`, pas de retry.

### useAttendance

- Gère l'état d'inscription d'un utilisateur à un événement.
- Params : `eventId`, `initialAttendingCount`, `initialInterestedCount`, `initialStatus`.
- Expose : `currentStatus`, `attendingCount`, `interestedCount`, `loading`, `error`, `isFull`, `toggle(status)`.
- Mise à jour optimiste : état local mis à jour avant la résolution de l'API, rollback si erreur.
- Erreur 409 → `isFull = true` (pas de message `error` générique dans ce cas).

## Services

### userService.ts

- `getMe()` : `GET /api/users/me` — profil complet de l'utilisateur connecté.
- `getUserById(id)` : `GET /api/users/{id}` — profil public d'un utilisateur.
- `updateProfile(data)` : `PUT /api/users/me` — mise à jour des champs de profil.
- `uploadPhoto(file)` : `POST /api/users/me/image` — upload de la photo de profil (multipart).
- `uploadBanner(file)` : `POST /api/users/me/banner` — upload de la bannière de profil (multipart).
- `deleteBanner()` : `DELETE /api/users/me/banner` — suppression de la bannière (bannerUrl → null).
- `getCalendarToken()` : `GET /api/users/me/calendar-token`.
- `regenerateCalendarToken()` : `POST /api/users/me/calendar-token/regenerate`.

### attendeesApi.ts

- `getEventAttendees(eventId, { page, size })` : `GET /api/events/{id}/attendees?page=&size=` — réservé au créateur (403 sinon).
- `getPublicUser(userId)` : `GET /api/users/{id}` — retourne `null` sur 403 (profil privé) et 404 (introuvable). Toute autre erreur est rethrown.

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
- getMyDrafts(organizerId, limit = 5) : helper typé autour de `getAll` filtrant `status=DRAFT` et `organizerId`. Utilisé par `useMyDrafts`.
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
