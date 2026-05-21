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
| /profile/:username/followers | FollowListPage | fait (SCRUM-142) |
| /profile/:username/following | FollowListPage | fait (SCRUM-142) |
| /events/search | EventsSearchPage | fait |
| /calendar | CalendarPage | fait |
| /events/favorites | FavoritesPage | fait |
| /my-events | MyEventsPage | redirect → /my-events/favorites |
| /my-events/favorites | MyFavoritesPage | fait |
| /my-events/participations | MyParticipationsPage | fait (stub backend) |
| /my-events/publications | MyPublicationsPage | fait |
| /events/:id/stats | EventStatsPage | fait (S6) |
| /legal/privacy | PrivacyPage | fait |
| /legal/terms | TermsPage | fait |

### LandingPage

- Page publique d'accueil avec Hero, section "À la une", Features, FAQ et GetStarted.
- Section "À la une" rendue par `FeaturedEventsSection` (jusqu'à 6 événements curated par le backend).
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
- **Bouton "Signaler cet événement"** — visible pour tout utilisateur connecté qui n'est PAS l'organisateur de l'event (`user !== null && !isOrganizer`). Ouvre `ReportModal` via `useReport`. Non affiché aux utilisateurs anonymes ni au créateur/co-organisateur ACCEPTED de l'event. Le hook gère un toast 422 défensif `cannot_report_own_event` au cas où le statut organisateur changerait pendant le flow.
- **Bloc "Informations complémentaires" (SCRUM-117)** — affiché conditionnellement uniquement quand au moins un des 4 champs optionnels est présent :
  - `websiteUrl` → ancre `target="_blank" rel="noopener noreferrer"` avec icône `Globe` ; texte cliquable = l'URL brute, rendue via la classe `text-link` (token CSS `--color-link`, sky-600 light / sky-400 dark).
  - `contactEmail` → ancre `mailto:` avec icône `Mail`, mêmes styles `text-link`.
  - `registrationDeadline` → libellé "Inscriptions jusqu'au" + valeur formatée via `formatEventDateTime` avec icône `CalendarClock`.
  - `tags[]` → chips cliquables via `<Link>` vers `/events/search?q=<tag>` (encodage URI côté client via `encodeURIComponent`) avec icône `Tag`. Le backend `/events/search` ne supporte pas de paramètre `tag` dédié ; on réutilise donc la recherche full-text `q` qui matche titre/description/tags. Les chips ne sont **pas** stylées en `text-link` — elles conservent leur look "pill discrète".
- **Bouton "Dupliquer l'événement"** — visible pour l'organisateur (créateur + co-organisateur ACCEPTED) uniquement quand `event.status !== 'CANCELLED'`. Délégué à `DuplicateButton` (`src/components/event/DuplicateButton.tsx`). Redirige vers `/events/{cloneId}/edit` après succès via `POST /api/events/{id}/duplicate`.
- **Redirect créateur sur DRAFT** — la page détail d'un brouillon n'a pas de surface fonctionnelle pour son créateur (aucun bouton participer, aucun inscrit à voir). Un `useEffect` qui dépend de `event` et `user` redirige automatiquement vers `/events/:id/edit` avec `{ replace: true }` quand `event.status === 'DRAFT'` et `user.id === event.creatorId`. L'admin (`user.admin === true`) reste sur la page détail (cas modération). Co-organisateur ACCEPTED non couvert dans cette PR — follow-up SCRUM-137 frontend.
- **Section "Documents" (SCRUM-149)** — `EventDocumentsList` rendu dans la colonne principale (entre `AttendeesList` et "Informations complémentaires") quand `event.attachments.length > 0`. **Visible pour tous** : pas d'auth gate, les non-authentifiés voient et téléchargent. Le lien utilise `attachment.downloadUrl` (endpoint API same-origin `/api/events/{id}/attachments/{aid}/download` qui streame depuis MinIO avec `Content-Disposition: attachment`) — différent de `bannerUrl` / `avatarUrl` qui, eux, sont des URLs S3 directes ; voir `frontend/docs/types.md` pour le détail.

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
- Bouton "Créer un événement" en `position: sticky bottom-6 self-end` à la fin du `SectionWrapper` : reste visible en bas-droite tant qu'on scrolle dans la page mais s'arrête à la fin du contenu, sans recouvrir le footer (vs l'ancien `position: fixed` qui flottait par-dessus le footer en bas de page).
- Skeleton `my-events`.

### EventStatsPage

- Route `/events/:id/stats`, protégée par PrivateRoute — réservé à l'organisateur de l'événement.
- Charge l'événement via `useEvent(id)`, puis les stats via `useEventStats(id)` (auto-refresh toutes les 60 s).
- Vérifie que `user.id === event.creatorId` avant de charger les stats (évite le 403).
- KPI cards : 👁 Vues totales (`stats.viewCount`), ⭐ Intéressés (`stats.interestedCount`), ✅ Inscrits (`stats.attendingCount`).
- `StatsChart` : BarChart vertical recharts (Vues / Intéressés / Inscrits).
- Barre de progression taux de remplissage : `attendingCount / capacity * 100`.
- Section collapsible "Voir les participants" : `GET /events/{id}/attendees` → liste avec avatars et noms (fetch users en parallèle via `getUserById`).
- Skeleton `event-stats` généré par `skeleton/generate.mjs` (3 breakpoints container width : 343 / 720 / 960). Le fixture reflète exactement le layout : bouton "Rafraîchir" en haut, grille KPI (3 cols ≥sm, stackée mobile), card chart `h-[260px]`, capacity bar `h-[100px]`, attendees toggle `h-12`.

### PrivacyPage

- Route `/legal/privacy`, publique.
- Page statique de lecture (SectionWrapper size `md` = `max-w-3xl`, BlobsSubtle).
- Contenu : politique de confidentialité inspirée du cadre légal UNIGE (LIPAD, RIPAD, RGPD).
- Sections : cadre légal, responsable du traitement, données collectées, finalités, base légale, principes, partage, cookies, durée de conservation, droits, sécurité, contact.
- Lien interne vers `/profile/me/edit` pour l'exercice du droit de rectification.
- Mention explicite du caractère académique (projet PINFO).
- Composé via les helpers `LegalSection` / `LegalParagraph` / `LegalList` / `LegalBackLink` partagés (`@/components/legal/`) — chaque rubrique = un `<LegalSection title="...">` avec son contenu typé.

### TermsPage

- Route `/legal/terms`, publique.
- Page statique de lecture (même layout que PrivacyPage).
- Contenu : conditions générales d'utilisation de la plateforme.
- Sections : objet, description de la plateforme, inscription, contenu utilisateur, modération, propriété intellectuelle, disponibilité, limitation de responsabilité, données personnelles (renvoi vers `/legal/privacy`), modification des conditions, droit applicable, contact.
- Lien externe vers le dépôt GitHub (open source).
- Mention explicite du caractère académique (projet PINFO).
- Réutilise les mêmes helpers `LegalSection` / `LegalParagraph` / `LegalList` / `LegalBackLink` que `PrivacyPage`.

### CreateEventPage

- Réutilise EventForm via useEventForm en mode create.
- Crée un événement via createEvent().
- Permet un statut initial DRAFT ou PUBLISHED.
- Bloque la soumission si la date de début est dans le passé.
- Upload une bannière optionnelle puis redirige vers la page détail.
- Affiche un toast de succès ou d'erreur.
- Après un **save-draft** réussi : redirection vers `/` (landing page), pas vers `/events/:id` — sauvegarder en brouillon est un "je reprends plus tard".
- Intègre `DraftsResumeStrip` entre le header et le formulaire pour reprendre les brouillons existants.
- **"Fichiers joints" (SCRUM-149 follow-up)** — section `PendingAttachmentsEditor` injectée via `attachmentsSection` d'`EventForm`. Stage des fichiers localement (validation client miroir backend), puis dans `onSuccess` après `createEvent` réussi, itère `uploadEventAttachment(event.id, file)` sur chaque entrée valide. Pattern identique aux co-organisateurs : best-effort, échec individuel toasté, ne bloque pas la navigation finale.

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
- Skeleton `event-edit` réaligné avec les 5 bandes actuelles d'`EventForm` (Banner+Titre/Description, Lieu, section Date & heure, Catégorie/Faculté/Capacité, Champs additionnels SCRUM-117, Co-organisateurs SCRUM-137 shell, CTA bar) — voir `skeleton/generate.mjs:genEventEdit`.
- **"Fichiers joints" (SCRUM-149)** — `EventAttachmentsEditor` est injecté via la prop `attachmentsSection` d'`EventForm` (uniquement en edit, car l'event doit avoir un ID pour `POST /events/{id}/attachments`). Le bloc remplace le placeholder "Pièces jointes" affiché en création. Stage-then-upload UX : files staged localement → bouton "Uploader" itère un POST par fichier → liste des uploadés rafraîchie via `setEvent({...event, attachments: next})`. Suppression par × → `DELETE /events/{id}/attachments/{attachmentId}`. Validations client miroir backend (PDF/DOC/DOCX/XLSX, 10 MiB, 5 max) + passthrough des codes backend `attachment_invalid_size` / `_type` / `_limit_exceeded`.

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

### ReportModal

- `src/components/event/ReportModal.tsx` — modale de signalement d'un événement.
- Props : `onClose: () => void`, `onSubmit: (reason: ReportReason, description?: string) => Promise<void>`, `submitting: boolean`.
- Champs : select `Motif` (obligatoire : Spam / Contenu inapproprié / Faux événement / Autre) + textarea `Description` (optionnelle).
- Bouton "Signaler" désactivé tant qu'aucun motif n'est sélectionné ou que `submitting` est `true`.
- Fermeture via bouton ✕, bouton "Annuler", ou automatiquement après succès (géré par `useReport`).
- Utilise `FormField`, `Select`, `Textarea` depuis `@/components/utils/FormField`.

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

### DuplicateButton

- Composant `src/components/event/DuplicateButton.tsx`.
- Props : `{ eventId: number }`.
- Appelle `POST /api/events/{id}/duplicate` via `duplicateEvent(eventApi)` ; en cas de succès, affiche un toast et redirige vers `/events/{cloneId}/edit` (formulaire pré-rempli).
- Affiché uniquement dans la section "Actions organisateur" de `EventDetailPage` (créateur + co-organisateur ACCEPTED), exclusivement quand `event.status !== 'CANCELLED'`.
- État `loading` désactive le bouton et change le texte en "Duplication…".

### AppErrorBoundary

- Composant `src/components/AppErrorBoundary.tsx`.
- React class component (`getDerivedStateFromError` + `componentDidCatch`).
- Encapsule toute l'application dans `App.tsx` — intercepte les erreurs JS inattendues pendant le rendu.
- Affiche un écran de repli stylisé (Blobs + gradient "Oops" + bouton "Recharger la page" + lien "Retour à l'accueil").
- Log les erreurs dans `console.error`.

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
- Utilisé dans `EventsPage` (route `/events`). N'est plus consommé par `LandingPage` depuis SCRUM-73 — la section Events de la landing utilise désormais `FeaturedEventsSection`.

### FeaturedEventsSection

- Section "À la une" de la `LandingPage` (SCRUM-73). Source : `useFeaturedEvents()` → `GET /api/events/featured?limit=6`.
- Backend (SCRUM-95) renvoie une liste **curated** : phase 1 = événements `featured=true` PUBLISHED triés par `featuredAt DESC`, phase 2 = remplit les slots restants avec les PUBLISHED à venir triés par popularité (`attendingCount + favoriteCount` DESC). La liste est déjà ordonnée et capée à 6 — pas de pagination, pas de "Charger plus".
- Réutilise `EventCard` et la même grille `auto-fit` que l'ancienne section (`grid-cols-[repeat(auto-fit,minmax(280px,320px))]`) → 3 colonnes × 2 lignes en desktop.
- Badge "✨ À la une" (gradient accent → pink) overlayé en `top-center` uniquement sur les cards où `event.featured === true`. Pattern wrapper `relative` + `<span absolute>` autour de `EventCard` — n'introduit pas de prop sur `EventCard` puisque le badge ne sert qu'ici.
- États : skeleton `event-cards` (déjà calibré pour 6 bones via `autoFitLayout(cw, 6)` dans `skeleton/generate.mjs`) en loading ; `InfoMessage type="error"` en cas d'erreur ; **section entièrement masquée (return null)** si la liste est vide — pas de header, pas d'espace résiduel.

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

### NotificationBell (SCRUM-80)

- `src/components/utils/NotificationBell.tsx` — déclencheur visuel de la cloche dans la navbar.
- Props : `unreadCount: number`.
- Affiche un badge rouge superposé quand `unreadCount > 0` ; format `99+` au-delà de 99. `aria-label` géré singulier/pluriel ("1 notification non lue" / "5 notifications non lues").

### NotificationPanel (SCRUM-80)

- `src/components/utils/NotificationPanel.tsx` — contenu du dropdown ouvert par la cloche.
- Props : `notifications: Notification[]`, `loading: boolean`, `error: string | null`, `onMarkAllRead: () => void`.
- États : skeleton `notification-panel` (loading) ; message d'erreur ; empty state "Aucune notification" ; liste avec scroll vertical bornée à 360 px.
- Bouton "Tout marquer comme lu" rendu uniquement quand au moins une notif est unread.
- Chaque item affiche un pill coloré + libellé (`NOTIFICATION_TYPES`), un message, et un timestamp relatif (`relativeTime` : "À l'instant" / "Il y a N min" / "Il y a Nh" / "Il y a Nj" / date `fr-CH` au-delà de 7 j).
- Mapping `typeStyles` couvre les 9 valeurs `NotificationType` du contrat backend (phase 1 + phase 2 + phase 3 réservées). Un type inconnu (10e valeur future non encore mappée) tombe sur un style neutre `Bell` + label "Notification" — pas de crash.
- Les items avec `eventId !== null` deviennent un `<Link>` vers `/events/{eventId}` ; les items follow / mention (`eventId = null`) restent inertes.

### NotificationsDropdown (SCRUM-80)

- `src/components/utils/NotificationsDropdown.tsx` — assemblage `Dropdown` (`align="right"`) + `NotificationBell` + `NotificationPanel`.
- Branche `useNotifications` (state notifications, badge, callbacks) sur le rendu — pas d'état local propre.
- Monté dans `Navbar.tsx` à droite du dropdown user (desktop + mobile).

### Navbar

- Barre de navigation principale du site (`src/components/Navbar.tsx`), exporte également `MobileMenu` pour la sidebar tactile.
- Les listes `navLinks`, `actionButtons`, `myEventsSubLinks` et `userMenuItems` sont déclarées une seule fois en const arrays typées et partagées entre desktop et mobile (DRY).
- **Dropdown profil desktop — pattern banner-card pour "Mes événements"** : l'item `userMenuItems` qui possède des `subLinks` est rendu via le composant local `UserDropdownBanner` à base de `Collapsible.Root` (Radix UI). Le banner-card (`rounded-2xl border border-border/40 bg-foreground/[0.02]`) regroupe les 3 sous-liens (favoris / participations / publications) sous un header cliquable avec chevron pivotant. **Ouvert par défaut** (`defaultOpen`) pour réduire les clics — l'utilisateur vient juste d'ouvrir son profil. L'animation utilise les keyframes `collapsible-open` / `collapsible-close` (200 ms, ease) déclarées dans `index.css` et désactivées sous `motion-reduce`. Pattern source : `DraftsResumeStrip`.
- **Sidebar mobile (`MobileNavItem`)** inchangée : la version tactile a son propre expand/collapse en colonne et n'a pas le défaut "menu dans menu" du dropdown desktop.
- L'item plat `Administration` (visible si `user.admin`) reste un simple `<Link>` dans le dropdown — il n'a pas de `subLinks` et donc pas de banner-card.

### AttendanceButtons

- Affiche les boutons "Je suis intéressé(e)" et "Je participe" sur la page détail événement.
- Props : `eventId`, `initialAttendingCount`, `initialInterestedCount`, `initialStatus`.
- Gère les mises à jour optimistes via `useAttendance` : clic → état local mis à jour immédiatement, rollback en cas d'erreur.
- Bouton ATTENDING désactivé avec tooltip "Événement complet" quand `isFull === true` et l'utilisateur n'est pas déjà ATTENDING.
- Affiche un compteur live : "X participants · Y intéressé(e)s".
- Affiche un message d'erreur inline en cas d'erreur non-409.

### AttendeesList

- Section "Participants" insérée dans la **colonne principale** de `EventDetailPage`, immédiatement sous le bloc "À propos". Mêmes primitives de card que les autres blocs de la colonne (glassmorphism, heading `text-xs font-bold uppercase tracking-widest text-foreground/30`).
- Props : `isAuthenticated: boolean`, `attendingCount: number`, `attendeesHook: UseAttendeesResult`.
- **Vue non-authentifiée (variante compacte)** : ligne unique inline — 1 à 5 placeholders d'avatar empilés + libellé `"X participant(s)"`. Padding vertical réduit (`px-6 py-4`). **Aucun appel API.** La compacité est dérivée automatiquement de `!isAuthenticated` (const map `sectionVariants`).
- **Vue authentifiée (SCRUM-S7)** : rendue pour **tous** les utilisateurs connectés (créateurs, co-organisateurs, admins, et autres comptes). Deux onglets accessibles au clavier — `"Participants"` (filtre `status === 'ATTENDING'`) et `"Liste d'attente"` (filtre `status === 'WAITLISTED'`). Chaque onglet affiche son compteur entre parenthèses.
- Liste des `AttendeeCard` rendue **en colonne unique** (`flex flex-col gap-3`). Bouton "Charger plus" en bas (visible uniquement si `hasMore === true`, désactivé pendant le chargement).
- États gérés : skeleton de chargement initial (4 placeholders empilés), message d'empty state par onglet, message d'erreur avec bouton `Réessayer`.
- Le filtre de confidentialité est appliqué **côté backend** (cf. spec `GET /events/{id}/attendees`). Les lignes anonymisées (profil privé vu par un non-organisateur) arrivent avec `displayName=null`/`userId=null` et sont rendues par `AttendeeCard` comme "Utilisateur anonyme" — pas de logique de confidentialité côté frontend, pas de N+1 vers `/users/{id}`.

### AttendeeCard

- Carte d'un participant (`src/components/attendees/AttendeeCard.tsx`).
- Prop unique : `attendance: Attendance` (l'enrichissement de profil est déjà projeté dans le DTO côté backend, plus de prop `profile` séparée).
- Si `attendance.displayName !== null` (identité exposée) : avatar (`UserAvatar`) + `displayName`. Lien vers `/profile/{attendance.userId}` si `userId` est non-null.
- Si `attendance.displayName === null` (ligne anonymisée par le backend : profil privé vu par un non-organisateur, ou inscription orpheline) : avatar placeholder (`aria-label="Avatar anonyme"`) + libellé "Utilisateur anonyme" / sous-titre "Profil privé" — non cliquable.
- Affiche `WaitlistBadge` quand `attendance.status === 'WAITLISTED'`.

### WaitlistBadge

- Petit badge `"Liste d'attente"` réutilisable (`src/components/attendees/WaitlistBadge.tsx`), basé sur `bg-warning/10 border-warning/40 text-warning` pour rester cohérent avec `AttendanceButtons`.

### EventStatsPanel

- Card publique "Statistiques de participation" (`src/components/event/EventStatsPanel.tsx`) insérée dans la **sidebar** de `EventDetailPage`, après `IcsExportButton` et avant les actions organisateur. Visible pour **tous** les utilisateurs (pas seulement l'organisateur) — la page `/events/:id/stats` reste réservée à l'organisateur pour les visualisations avancées (chart + capacity bar + liste des participants).
- Props : `viewCount: number | null | undefined`, `interestedCount: number | null | undefined`, `attendingCount: number`.
- Layout : titre avec icône `BarChart2` puis `grid grid-cols-3 gap-2` de 3 mini-cards (icône + valeur + label). Pattern KPI compact réutilisé d'`EventStatsPage` : gradient `blue` pour Vues, `green` pour Inscrits, `purple` pour Intéressés.
- Affiche `'—'` quand `viewCount`/`interestedCount` sont `null` ou `undefined` (cas des endpoints de liste/recherche qui ne calculent pas ces compteurs). `attendingCount` est toujours présent dans le DTO Event public.
- Formatage `toLocaleString('fr-CH')` (séparateur U+202F entre milliers).

### CalendarSubscribeButton

- Affiche un bloc "S'abonner au calendrier" sur la page de profil de l'utilisateur connecté.
- Sans props — charge automatiquement le token via `getCalendarToken()` au montage.
- Trois liens : abonnement Apple/Outlook (`webcal://`), abonnement Google Calendar (`https://`, nouvel onglet), téléchargement direct `.ics` (attribut `download`).
- Bouton "Révoquer et régénérer le lien" : appelle `regenerateCalendarToken()`, met à jour les trois URLs, affiche un message de confirmation.
- Gère les états loading, error et regenerating.
- Visible uniquement pour `isOwnProfile` dans `ProfilePage`.

### ProfileStats (SCRUM-141 + SCRUM-142)

- Composant `src/components/profile/ProfileStats.tsx` rendu sous le header de `ProfilePage` pour tout profil public.
- Props : `followerCount: number`, `followingCount: number`, `linkUsername?: string`.
- Affiche deux tuiles compteur (followers / abonnements) avec valeur formatée fr-CH (séparateur U+202F entre milliers) + icône `Users`.
- Quand `linkUsername` est fourni (SCRUM-142), les tuiles deviennent des `<Link>` vers `/profile/{linkUsername}/followers` et `/.../following` avec `aria-label="Voir les followers (N)"` / `"Voir les abonnements (N)"`. Quand `linkUsername` est omis, les tuiles restent inertes.
- Singulier `follower` quand `followerCount === 1`, sinon pluriel `followers`. `abonnements` toujours au pluriel.

### ProfileEventsList (SCRUM-141)

- Composant `src/components/profile/ProfileEventsList.tsx` rendu en bas de `ProfilePage` (colonne gauche du grid 2-colonnes événements + participations).
- Props : `events: Event[]`, `loading: boolean`, `error: string | null`.
- Réutilise `PreviewRow` pour la cohérence visuelle avec `MyPublicationsPreview`.
- États : skeleton de chargement (3 lignes), empty state ("Aucun événement organisé pour le moment.") avec icône `CalendarOff`, message d'erreur.
- Consomme `useOrganizerEvents(id)` côté `ProfilePage` (qui appelle `GET /events?organizerId=…&status=PUBLISHED`).

### ProfileParticipations (SCRUM-141)

- Composant `src/components/profile/ProfileParticipations.tsx` rendu en bas de `ProfilePage` (colonne droite du grid événements + participations).
- Placeholder uniquement — affiche "Aucune participation publique à afficher." avec icône `Ticket`.
- **TODO (follow-up ticket)** : le backend n'expose pas encore d'endpoint listant les participations publiques d'un utilisateur arbitraire (`/users/me/participations` existe mais est restreint au caller). Quand l'endpoint atterrira, brancher un hook `useUserParticipations(id)` qui appelle `/users/{id}/participations` (à créer côté backend, avec filtre de confidentialité miroir de `GET /events/{id}/attendees`).

### ProfileHeader (SCRUM-141 redesign)

- Composant `src/components/profile/ProfileHeader.tsx` partagé entre `PublicProfileView` et `ProfilePrivateState` — banner (avec fallback gradient) + avatar overlapping en bas-gauche + displayName + sous-titre faculté/niveau d'étude.
- Props : `profile: UserPublicResponse`, `actions?: ReactNode` (slot droit pour `Modifier` / `FollowButton`).
- Extrait pour garantir un cadre visuellement identique au pixel près entre la vue publique et la vue privée : la vue privée doit donner l'impression d'un vrai compte verrouillé, pas d'un état d'erreur.

### ProfilePrivateState (SCRUM-141 redesign)

- Composant `src/components/profile/ProfilePrivateState.tsx` rendu par `ProfilePage` dans deux cas :
  - `getUserByUsername(username)` retourne `null` (404 backend — user inexistant) → fallback bannière dégradée sans avatar / displayName.
  - Backend retourne 200 avec une projection restreinte (`profilePublic = false` ; id + username + displayName + avatarUrl peuplés, bannerUrl / bio / faculty / studyLevel / interests à null) — SCRUM-169 Décision E revised. → `ProfileHeader` rendu avec la projection restreinte (banner gradient car bannerUrl null, avatar utilisateur ou initiales, displayName).
- Props : `profile?: UserPublicResponse | null`.
- Layout : `ProfileHeader` (ou bannière gradient seule si pas de profil) + zone de contenu remplacée par un grand cadenas `Lock` centré + titre `Compte privé`. **Pas** de bio, **pas** de compteurs, **pas** d'événements, **pas** de participations, **pas** de FollowButton, **pas** de badge PENDING.

### FollowButton (SCRUM-110)

- Composant `src/components/user/FollowButton.tsx` — bouton à trois états piloté par `followStatus` de la cible.
- Props : `targetId: string`, `followStatus?: FollowStatus | null`, `onMutated?: () => void`.
- Variantes (const map typée `followButtonVariants`) :
  - `idle` (followStatus = `null`) → bouton primary "Suivre" (gradient pink → accent) ; click `POST /api/users/{id}/follow`. Optimiste : passe immédiatement en `PENDING`. Le parent refetch via `onMutated` resynchronise le vrai statut (auto-ACCEPTED pour profil public, PENDING pour profil privé).
  - `pending` → bouton muté "Demande envoyée" avec tooltip natif `title="Cliquer pour annuler"` ; click `DELETE /api/users/{id}/follow` (idempotent côté backend — pas de 404 sur cancel).
  - `accepted` → bouton secondaire "Abonné" par défaut, swap CSS via `group` / `group-hover:hidden` → "Se désabonner" au survol ; click `DELETE`.
- Accessibilité : `aria-pressed` (toggle pattern WAI-ARIA), `aria-label` distinct par état, `title` pour le tooltip natif.
- Concurrence : guard `pending` local empêche le double-click pendant le round-trip.
- Erreurs : toast `error` "Impossible de mettre à jour le suivi." + rollback de l'état local.
- Intégré dans `ProfilePage.tsx` : rendu **uniquement** sur `/profile/<uuid>` quand l'appelant est authentifié ET `uuid !== currentUser.id`. Pas affiché sur `/profile/me`, ni sur `/profile/<own-uuid>` (rendu en vue publique standard sans widgets owner), ni pour les viewers anonymes.

### FollowRequestsPanel (SCRUM-110)

- Composant `src/components/profile/FollowRequestsPanel.tsx` rendu uniquement sur `/profile/me`, après `MyPublicationsPreview` dans la colonne gauche.
- Section card glassmorphism standard, heading "Demandes de suivi reçues" + badge compteur quand ≥ 1 demande.
- Pour chaque ligne : avatar + displayName du demandeur (résolu via `getPublicProfile` par row côté hook — voir `useMyFollowRequests`) + boutons `Accepter` (emerald) / `Refuser` (neutre).
- États : skeleton de chargement initial (2 lignes), empty state "Aucune demande de suivi en attente." avec icône `UserPlus`, error state, toasts d'erreur sur accept/reject.
- Accept / Reject sont optimistes (suppression immédiate de la row), rollback si l'API échoue.
- Limite connue (suivi follow-up) : après accept, le `followerCount` propre de l'owner sur `/profile/<own-uuid>` reste stale jusqu'au prochain reload — `useAuth` n'expose pas de `refresh` pour le `User` mis en cache.

### FollowListPage (SCRUM-142)

- Page `src/pages/profile/FollowListPage.tsx` montée sur `/profile/:username/followers` et `/profile/:username/following` (deux entrées de route distinctes avec une prop `mode: 'followers' | 'following'`).
- Résolution `:username` : alias `me` ou match avec `currentUser.username` → utilise `useAuth.user` sans round-trip. Sinon `getUserByUsername(username)` → résout uuid + displayName + compteurs. 404 (anti-oracle ISSUE-93) → `ProfilePrivateState`.
- Pagination « Charger plus » : hook `useFollowList(uuid, mode)` qui consomme `GET /users/{id}/followers` ou `/following` (page=0, size=20, max 100). `hasMore` flippe à `false` dès qu'un batch est court.
- En-tête : back link vers `/profile/{username}` + h1 `Followers de X` / `Abonnements de X` + tabs `<NavLink>` vers l'autre mode (les deux compteurs sont rendus dans les tabs).
- Skeleton `name="follow-list"` (manuel, `src/bones/follow-list.bones.json`) wrapped dans `max-w-3xl mx-auto px-6 lg:px-8 py-12 lg:py-16` pour borner la largeur mesurée par boneyard.
- Limites connues :
  - Les items de la liste sont projetés par le backend avec `followStatus = null` (cf. openapi spec `/users/{id}/followers`). Aucun `FollowButton` n'est rendu par row pour éviter l'UX « Suivre » menteur — chaque row link vers `/profile/{username}` où le `FollowButton` reçoit le bon `followStatus`.
  - Sur `/profile/me/(followers|following)`, les compteurs des tabs affichent `0/0` parce que le payload `User` self ne porte ni `followerCount` ni `followingCount` (cf. note `MeProfileView` dans `ProfilePage`). Suivi du même follow-up que `ProfileStats` sur `/me`.

### FollowListRow (SCRUM-142)

- Composant `src/components/profile/FollowListRow.tsx`. Une ligne = `<UserAvatar>` 48px + `displayName` + `@username · studyLevel · facultyAbbr`.
- Le row entier est un `<Link>` vers `/profile/{username}`. Pas de `FollowButton` (cf. note SCRUM-142 ci-dessus).

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

### UsernameAutocomplete

- **Composant SCRUM-137 polish** (`src/components/user/UsernameAutocomplete.tsx`). Remplace l'`<Input>` du champ d'invitation co-organisateur dans `CoOrganizersEditor` (live) et `PendingCoOrganizersEditor` (staged) ; affiche une dropdown de suggestions au-dessous de l'input dès que l'utilisateur tape ≥ 2 caractères.
- **Props** : `value`, `onChange`, `onSelect(user)`, `placeholder?`, `error?`, `excludeUsernames?`, `inputId?`, `autoFocus?`, `disabled?`. Le parent garde le contrôle de la valeur (`value`/`onChange`) pour que le submit existant continue à fonctionner si l'utilisateur tape un username complet sans cliquer une suggestion.
- **Fetch paresseux + debounce** : `useDebounce(value, 300ms)` puis appel `GET /users/search?q=<prefix>&limit=8` via `searchUsernames`. Skip si `prefix.length < 2` ou si on vient juste de fournir un username via `onSelect` (le state flippe au handle pické, on ne re-fetch pas immédiatement). Cache en mémoire (Map prefix → résultats, cap 50 entrées). Compteur monotone qui ignore les réponses obsolètes (même pattern que `useOccurrences`).
- **Filtre client `excludeUsernames`** : appliqué après le fetch, comparaison case-insensitive. `CoOrganizersEditor` passe `[user.username, ...accepted.co-orgs]`, `PendingCoOrganizersEditor` passe `[user.username, ...staged]`. La dropdown ne propose donc jamais d'inviter soi-même ni de doubler une invitation.
- **Accessibilité** : ARIA combobox + listbox (`role="combobox"`, `role="listbox"`, `role="option"`, `aria-autocomplete="list"`, `aria-expanded`, `aria-activedescendant`). Navigation clavier ↑/↓ pour parcourir, Enter pour sélectionner, Escape pour fermer. Click outside ferme aussi la dropdown.
- **États** : loading → 3 lignes squelettes via `Skeleton` boneyard inline (pas de `.bones.json` dédié) ; error → message inline rouge ; data vide → "Aucun résultat." italique. Chaque ligne rend l'avatar via `UserAvatar`, le handle `@username` en gras et le `displayName` en sous-texte.

### DraftsResumeStrip

- **Bannière collapsible** `@radix-ui/react-collapsible` insérée au-dessus de `EventForm` dans `CreateEventPage`.
- **Header fixe (56 px)** toujours visible quand l'utilisateur a ≥ 1 brouillon : icône `Library` (lucide-react) + texte "Mes brouillons" à gauche, chevron `ChevronDown` à droite qui pivote à 180° quand le panneau est ouvert. Tout le header est cliquable (c'est le `Collapsible.Trigger`) — Entrée/Espace togglent aussi.
- **Panneau dépliable** rendu en dessous, qui pousse le contenu de la page vers le bas (pas d'overlay). Le panneau contient le label "Reprendre un brouillon" + les `DraftResumeCard` + le bouton "Voir tout" à droite du rail.
- **État initial : collapsed.** L'utilisateur doit cliquer pour voir ses brouillons. Pas de persistance `sessionStorage`/`localStorage` — à chaque montage, le panneau repart fermé.
- **Animation** ~250 ms à l'ouverture, ~200 ms à la fermeture, via les keyframes `drafts-panel-open` / `drafts-panel-close` déclarées dans `index.css`. Elles consomment la variable CSS `--radix-collapsible-content-height` fournie par Radix. Désactivées si `prefers-reduced-motion: reduce` (via les préfixes `motion-safe:`). Le chevron utilise `motion-reduce:transition-none`.
- **États** :
  - `loading` → skeleton `drafts-resume-strip` systématiquement rendu (fichier bones manuel, hauteur 56 px alignée sur la fixture, déclaré sur 3 container-width breakpoints `320 / 720 / 1216` pour scaler proprement de mobile à desktop). Fixture interne `DraftsHeaderFixture`.
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
| `event-stats` | `event-stats.bones.json` | `EventStatsPage` | generate.mjs |
| `follow-list` | `follow-list.bones.json` | `FollowListPage` (SCRUM-142) | manuel |
| `notification-panel` | `notification-panel.bones.json` | `NotificationPanel` (SCRUM-80) | manuel |

Pour régénérer les skeletons gérés par le générateur : `npm run skeleton` (depuis `frontend/`).

Pour les skeletons manuels (`profile`, `navbar-user`, `user-identity-*`) : éditer directement le JSON.


## Hooks

### useReport

- `src/hooks/useReport.ts` — gère l'état de la modale de signalement et l'appel API.
- Retourne : `{ isOpen, submitting, open, close, submit }`.
- `submit(reason, description?)` : appelle `POST /events/{id}/report` avec un body `{ reason, description? }` (les deux champs sont envoyés séparément, conformément à `CreateReportRequest` dans `openapi.yaml`). `description` est trimée et omise si vide. Toast succès "Merci pour votre signalement." + fermeture auto. Toast erreur "Vous avez déjà signalé cet événement." sur 409, toast générique sinon.
- Le type `ReportReason` et la map `REPORT_REASONS` (clés = constantes backend `SPAM | INAPPROPRIATE | FAKE | OTHER`, valeurs = libellés français) vivent dans `src/types/report.ts` — pattern `as const` + `keyof typeof` (cf. `src/types/faculty.ts`).

### useImageCropFlow

Hook utilitaire qui encapsule le flux complet « sélection fichier → validation → FileReader → ouverture du cropper → conversion Blob → File ». Utilisé par `ProfileEditPage` (×2 : avatar + bannière) et `useEventForm` (×1 : bannière événement).

Options : `aspect`, `circular?`, `validate?`, `onValidationError?`.
Résultat : `cropSource`, `handleFileSelect`, `aspect`, `circular`, `confirmCrop`, `cancelCrop`.

Garantit la **réinitialisation de l'input file** après confirm/cancel/erreur — sans cela, re-sélectionner le même fichier ne redéclenche pas l'event `change` (comportement HTML standard). Préserve le nom original du fichier lors de la conversion Blob → File.

### useEvents

- Charge les événements publiés par pages de 12.
- Retourne events, loading, error, hasMore et loadMore.

### useFeaturedEvents

- Charge les événements "À la une" via `GET /api/events/featured?limit=6` (SCRUM-73).
- Liste curated par le backend (featured admin + popularité). Pas de pagination, pas de loadMore.
- Retourne `{ events, loading, error }`. Consommé par `FeaturedEventsSection`.

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

- Charge la liste paginée des participants d'un événement pour tout utilisateur authentifié (SCRUM-S7 — la confidentialité est filtrée côté backend, plus de gate par rôle côté frontend).
- Signature : `useAttendees(eventId, { enabled?, pageSize? })`. `pageSize` défaut `20`. Avec `enabled: false`, aucun fetch — utilisé pour les viewers non-authentifiés sur `EventDetailPage`.
- **Pas** de N+1 vers `/users/{id}` : le DTO `Attendance` renvoyé par `GET /events/{id}/attendees` porte déjà `displayName`/`avatarUrl`/`userId`, anonymisés à `null` pour les profils privés vus par un non-organisateur. Le composant `AttendeeCard` interprète les nuls.
- Retourne : `attendees: Attendance[]`, `isLoading`, `error`, `hasMore`, `loadMore()`, `refetch()`.
- Pagination cumulative : `loadMore()` incrémente la page et concatène en dédupliquant par `attendance.id`. `hasMore` passe à `false` dès qu'une page contient moins de `pageSize` items.

### useUserProfile (SCRUM-141 + SCRUM-110)

- Charge le profil public d'un utilisateur via `GET /api/users/{id}` (cf. `getPublicProfile`).
- Signature : `useUserProfile(id: string | undefined)`. Avec `id === undefined`, aucun fetch.
- Retourne : `profile: UserPublicResponse | null`, `isNotFound: boolean`, `loading: boolean`, `error: string | null`, `refetch: () => void` (SCRUM-110 — bump d'un compteur monotone qui re-déclenche l'effect, utilisé par `FollowButton` après une mutation pour resynchroniser `followStatus` + `followerCount`).
- 404 backend → `isNotFound = true` (l'anti-oracle ISSUE-93 confond "user inexistant" et "profil privé non accessible" — la page rend la même UI privée pour les deux). Autre erreur → `error` rempli.
- Discard automatique des réponses stales sur changement de prop `id` ou unmount via flag `cancelled`.

### useMyFollowRequests (SCRUM-110)

- Charge les demandes de suivi `PENDING` reçues par l'utilisateur connecté via `GET /api/users/me/follow-requests`, puis résout `GET /users/{followerId}` par row pour afficher avatar + displayName (le backend renvoie un `FollowDTO` id-only — voir OpenAPI `FollowDTO`).
- Retourne : `rows: FollowRequestRow[]` (`{ request: FollowDTO, follower: UserPublicResponse | null }`), `loading`, `error`, `accept(id)`, `reject(id)`, `refresh()`.
- Per-row resolve via `Promise.allSettled` — un 404 / network failure sur un profil ne casse pas la liste, le row a `follower: null` et le composant affiche un fallback neutre "Utilisateur".
- `accept(id)` / `reject(id)` : optimistes (suppression immédiate de la row), refresh sur succès, rollback si l'API échoue (re-throw pour que le composant toast).
- Stale-response guard via `requestIdRef` monotone bumpé à chaque refresh / unmount.

### useFollowList (SCRUM-142)

- Charge la liste paginée des followers ou abonnements d'un utilisateur via `GET /api/users/{id}/followers` ou `/following` (cf. `getFollowers` / `getFollowing` dans `followApi`).
- Signature : `useFollowList(targetId: string | undefined, mode: 'followers' | 'following')`. `targetId === undefined` garde le hook en loading sans fetcher.
- Retourne : `users: UserPublicResponse[]`, `loading`, `loadingMore` (vrai seulement pendant le fetch d'une page suivante), `isNotFound` (404 backend — privé ou inexistant, anti-oracle ISSUE-93), `error`, `hasMore` (vrai tant que le dernier batch est plein, taille `FOLLOW_LIST_PAGE_SIZE = 20`), `loadMore()`.
- Pagination "load more" : `loadMore()` bump le `page` index, le fetch effect re-run et append les nouveaux items à `users`. Pas de refetch des pages précédentes.
- Reset complet (`users`, `page`, états) quand `targetId` ou `mode` change.
- Stale-response guard via `requestIdRef` monotone bumpé sur dep change / unmount — les promesses tardives d'une cible précédente sont ignorées.

### useOrganizerEvents (SCRUM-141)

- Liste les événements publiés organisés par un utilisateur via `GET /api/events?organizerId={id}&status=PUBLISHED`.
- Signature : `useOrganizerEvents(organizerId: string | undefined)`. Avec `organizerId === undefined`, aucun fetch.
- Retourne : `events: Event[]`, `loading: boolean`, `error: string | null`.
- Le backend force `status=PUBLISHED` quand `organizerId` est présent — pas de risque de fuiter des brouillons / annulés vers les viewers tiers.

### useAttendance

- Gère l'état d'inscription d'un utilisateur à un événement.
- Params : `eventId`, `initialAttendingCount`, `initialInterestedCount`, `initialStatus`.
- Expose : `currentStatus`, `attendingCount`, `interestedCount`, `loading`, `error`, `isFull`, `toggle(status)`.
- Mise à jour optimiste : état local mis à jour avant la résolution de l'API, rollback si erreur.
- Erreur 409 → `isFull = true` (pas de message `error` générique dans ce cas).

### useNotifications (SCRUM-80)

- `src/hooks/useNotifications.ts` — backbone du dropdown de notifications dans la navbar.
- Aucun paramètre. Expose `notifications: Notification[]`, `unreadCount: number`, `loading: boolean`, `error: string | null`, `markAllAsRead: () => void`, `markOneAsRead: (id: number) => void`.
- Source de vérité du badge : header HTTP `X-Unread-Count` de la réponse `GET /api/users/me/notifications` — la valeur reste correcte même quand la page courante ne contient pas toutes les unread (pagination).
- Fetch initial au mount + polling silencieux toutes les 30 s (le flag `silent` évite le flash du `loading=true` sur les ticks suivants).
- 401 → ignoré silencieusement (user non authentifié). Toute autre erreur fixe `error = "Impossible de charger les notifications."`.
- `markAllAsRead` / `markOneAsRead` sont optimistes (flip immédiat + décrément du badge) ; en cas d'erreur API, le hook re-fetche pour resynchroniser le state autoritaire.
- `mountedRef` empêche les setState après unmount (fetch tardif au-delà du cleanup).

## Services

### userService.ts

- `getMe()` : `GET /api/users/me` — profil complet de l'utilisateur connecté.
- `getUserById(id)` : `GET /api/users/{id}` — profil public d'un utilisateur. Conservé pour le redirect transitoire UUID → username (SCRUM-169 Décision I).
- `getUserByUsername(username)` : `GET /api/users/by-username/{username}` — lookup case-insensitive (SCRUM-169). Retourne `UserPublicResponse | null` ; le 404 (privé ou inexistant, ISSUE-93 anti-oracle) devient `null`, les autres erreurs sont rethrown.
- `getPublicProfile(id)` : `GET /api/users/{id}` — variante UUID-based (SCRUM-141, hooks legacy). Retourne `UserPublicResponse | null` ; même sémantique 404 → null que `getUserByUsername`.
- `updateProfile(data)` : `PUT /api/users/me` — mise à jour des champs de profil.
- `updateUsername(username)` : `PATCH /api/users/me/username` — change le username (SCRUM-169). Endpoint dédié pour granularité d'erreur (409 `username_taken`, 400 `username_invalid`/`username_reserved`).
- `checkUsernameAvailable(username)` : `HEAD /api/users/by-username/{username}` — check d'unicité pour le debounce frontend (SCRUM-169). **Inverse la sémantique HTTP** : retourne `true` sur 404 (libre), `false` sur 200 (pris).
- `searchUsernames(q, limit?)` : `GET /api/users/search?q=<prefix>&limit=<n>` — prefix scan (SCRUM-137 polish, autocomplete d'invitation co-org). Retourne un `UserPublicResponse[]` (projection minimale). `@Authenticated` côté backend, rate-limit 60 req/min.
- `uploadPhoto(file)` : `POST /api/users/me/image` — upload de la photo de profil (multipart).
- `uploadBanner(file)` : `POST /api/users/me/banner` — upload de la bannière de profil (multipart).
- `deleteBanner()` : `DELETE /api/users/me/banner` — suppression de la bannière (bannerUrl → null).
- `getCalendarToken()` : `GET /api/users/me/calendar-token`.
- `regenerateCalendarToken()` : `POST /api/users/me/calendar-token/regenerate`.

### followApi.ts (SCRUM-110)

Wraps les endpoints `SCRUM-138` follow via l'instance axios partagée :

- `followUser(targetId): Promise<FollowDTO>` : `POST /api/users/{id}/follow` — status auto-résolu côté backend (`ACCEPTED` si cible publique, `PENDING` si privée). Erreurs : `409 already_following`, `422 cannot_follow_self`, `429 rate_limited` propagées.
- `unfollowUser(targetId): Promise<void>` : `DELETE /api/users/{id}/follow` — **idempotent**, le backend renvoie 204 même sans row. Pas de 404 sur cancel/unfollow.
- `getMyFollowRequests(): Promise<FollowDTO[]>` : `GET /api/users/me/follow-requests` — première page (défaut backend `page=0&size=20`) des demandes PENDING reçues. Le wrapper ne propage pas (encore) les query params de pagination ; à étendre si une UI "Voir plus" / archive est ajoutée.
- `acceptFollowRequest(followId): Promise<FollowDTO>` : `PATCH /api/follow-requests/{followId}/accept` — bascule la row vers ACCEPTED. 403 si caller ≠ target, 409 `invalid_transition` si déjà ACCEPTED.
- `rejectFollowRequest(followId): Promise<void>` : `PATCH /api/follow-requests/{followId}/reject` — supprime physiquement la row (re-follow ultérieur possible sans 409). 204.

### attendeesApi.ts

- `getEventAttendees(eventId, { page, size })` : `GET /api/events/{id}/attendees?page=&size=` — accessible à tout utilisateur authentifié (SCRUM-S7). Le filtre de confidentialité est appliqué côté backend au niveau du DTO : pour un appelant non-organisateur, les lignes correspondant à un profil privé reviennent avec `userId=null`, `displayName=null`, `avatarUrl=null` (empêche le caller de sonder `/users/{id}` pour désanonymiser). Les créateurs / co-organisateurs ACCEPTED / admins reçoivent l'identité réelle pour toutes les lignes.

### attendanceApi.ts

- `attend(eventId, status)` : `POST /api/events/{id}/attend` avec body `{ status }` — upsert.
- `unattend(eventId)` : `DELETE /api/events/{id}/attend`.
- `getMyAttendance(eventId)` : filtre `GET /api/users/me/attendances` pour retourner le statut de l'utilisateur sur un événement.
- `getMyParticipations()` : **stub** retournant `[]`. TODO : remplacer par l'appel réel quand le backend exposera un endpoint d'événements participés enrichis.

### attachmentApi.ts (SCRUM-149)

Wraps les endpoints attachments via l'instance axios partagée (`@/services/api`) — pattern multipart identique à `uploadEventImage` / `uploadPhoto` (champ `file`, content-type déduit par Axios).

- `uploadEventAttachment(eventId, file)` : `POST /api/events/{eventId}/attachments` — multipart `file`, retourne `Attachment` (201). Propage les erreurs telles quelles (status / code envelope) pour que l'appelant les mappe en messages français per-row.
- `deleteEventAttachment(eventId, attachmentId)` : `DELETE /api/events/{eventId}/attachments/{attachmentId}` — 204, pas de body. Propage les erreurs.

Utilitaire associé : `formatFileSize(bytes)` (`src/utils/formatFileSize.ts`) — sortie `"X B" | "X.X KB" | "X.X MB"` (bases 1024, cohérent avec le cap backend de 10 MiB).

> Note historique : un helper `downloadAttachment` (fetch → blob → ancre synthétique) avait été ajouté pour forcer le téléchargement quand `fileUrl` pointait sur MinIO en cross-origin. Il a été supprimé en SCRUM-149 follow-up — le backend expose désormais un endpoint same-origin `GET /api/events/{eventId}/attachments/{id}/download` qui streame avec `Content-Disposition: attachment`, donc une simple ancre HTML suffit.

### reportApi.ts

- `reportEvent(eventId, { reason, description? })` : `POST /api/events/{id}/report` — signale un événement avec un motif catégoriel obligatoire (`reason: ReportReason` = `SPAM | INAPPROPRIATE | FAKE | OTHER`) et un texte libre optionnel (`description: string`, max 2000 chars). Conforme au schéma `CreateReportRequest` d'`openapi.yaml`. Le backend répond `201` avec un `Report` complet ; le service ignore intentionnellement le corps (`Promise<void>`) car aucun consommateur n'en a besoin pour l'instant. Lance une erreur 409 si l'utilisateur a déjà signalé cet événement, 422 si l'utilisateur en est l'organisateur, 400 si le motif est invalide.

### icsGenerator.ts

- `generateIcs(event)` : retourne une chaîne RFC 5545 (.ics) avec VCALENDAR, VEVENT, UID, DTSTART, DTEND, SUMMARY, LOCATION et DESCRIPTION optionnelle. Échappe les caractères spéciaux et applique le line folding à 75 octets.
- `buildGoogleCalendarUrl(event)` : retourne l'URL Google Calendar pré-remplie (action=TEMPLATE, text, dates, location, details optionnel).

### eventApi.ts

- `getAll(params)` : liste paginée d'événements.
- `getById(id)` : détail d'un événement.
- `createEvent(data)` : création d'événement.
- `updateEvent(id, data)` : mise à jour d'événement.
- `uploadEventImage(id, file)` : upload de bannière et retour de l'événement mis à jour.
- `deleteEvent(id)` : annulation soft-delete d'un événement.
- `publishEvent(id)` : passe l'événement de DRAFT à PUBLISHED via `PATCH /api/events/{id}/publish`.
- `getMyDrafts(organizerId, limit = 5)` : helper typé autour de `getAll` filtrant `status=DRAFT` et `organizerId`. Utilisé par `useMyDrafts`.
- `getMyEvents(params)` : liste des événements créés par l'utilisateur authentifié via `GET /api/users/me/events?status=&page=&size=`. Identité dérivée du JWT, tri serveur `createdAt DESC`, tous statuts (DRAFT, PUBLISHED, CANCELLED) retournés par défaut. Consommé par `useMyEvents`.
- `getFeatured(limit)` : liste curated des événements "À la une" via `GET /events/featured?limit=<n>`. Utilisé par `useFeaturedEvents`.
- `getOccurrences(parentId, params?)` : `GET /events/{parentId}/occurrences` (SCRUM-151). Retourne la liste triée chronologiquement des occurrences enfants d'un parent récurrent (backend cap dur de 52). Consommé par `useOccurrences`. Pas de pagination exposée côté UI (Décision K).
- `duplicateEvent(id)` : `POST /events/{id}/duplicate`. Crée un clone DRAFT avec titre prefixé "Copie de …", dates +7 jours et recurrenceRule/parentEventId/shareCode réinitialisés. Retourne le clone. Utilisé par `DuplicateButton`.

### searchApi.ts

- searchEvents(params) : recherche full-text d’événements via `GET /api/events/search`.
- fetchSuggestions(query) : stub retournant un tableau vide (TODO — pas d’endpoint de suggestions dans openapi.yaml).

### favoriteApi.ts

- getFavorites() : liste des événements favoris via `GET /api/users/me/favorites`.
- addFavorite(eventId) : ajouter un favori via `POST /api/events/{id}/favorite`.
- removeFavorite(eventId) : retirer un favori via `DELETE /api/events/{id}/favorite`.

### notificationApi.ts (SCRUM-80)

- `getNotifications({ page?, size? } = {})` : `GET /api/users/me/notifications?page=&size=` (SCRUM-99 backend). Retourne `{ notifications: Notification[]; unreadCount: number }` — l'unread count vient du header HTTP `X-Unread-Count` (source de vérité cross-pages), pas de la longueur du tableau. Header absent / non numérique → fallback `0`.
- `markAllRead()` : `PATCH /api/users/me/notifications/read-all`. Retourne `{ updated: number }` (cf. schéma OpenAPI `ReadAllResponse`). Idempotent — un appel répété renvoie `{ updated: 0 }`.
- `markNotificationRead(id)` : `PATCH /api/users/me/notifications/{id}/read`. Idempotent (204 même si déjà lu) ; 404 anti-oracle si la notif appartient à un autre user.
- Constante exportée `NOTIFICATIONS_PAGE_SIZE = 20` (alignée sur le défaut backend).

### coOrganizerApi.ts (SCRUM-137)

- `inviteCoOrganizer(eventId, userId)` : `POST /api/events/{id}/co-organizers` avec body `{ userId }`. Retourne le `CoOrganizer` créé (status `PENDING`). 404 si l'UUID utilisateur n'existe pas (cf. Décision A de la spec — invitation par UUID, pas par search).
- `listCoOrganizers(eventId)` : `GET /api/events/{id}/co-organizers`. Retourne la liste des co-organisateurs (PENDING + ACCEPTED).
- `removeCoOrganizer(eventId, userId)` : `DELETE /api/events/{id}/co-organizers/{userId}`. Idempotent.
- `acceptInvitation(eventId)` : `PATCH /api/events/{id}/co-organizers/me/accept`. Retourne le `CoOrganizer` mis à jour (status `ACCEPTED`).
- `declineInvitation(eventId)` : `PATCH /api/events/{id}/co-organizers/me/decline`. Supprime physiquement la row (re-invitation possible).
- `getMyInvitations(status?, page?, size?)` : `GET /api/users/me/co-organizer-invitations`. Retourne la liste paginée des invitations.

### commentApi.ts (SCRUM-146)

- `getEventComments(eventId, page = 0, size = 20)` : `GET /api/events/{eventId}/comments`. Retourne `List<CommentDTO>` paginée sur les top-level avec leurs replies incluses.
- `postComment(eventId, content, parentCommentId?)` : `POST /api/events/{eventId}/comments`. Retourne le `CommentDTO` créé (replies vide).
- `deleteComment(commentId)` : `DELETE /api/comments/{commentId}`. 204 No Content. 403 si non-autorisé (ni auteur, ni créateur, ni co-org ACCEPTED, ni admin).

### sessionId.ts (vue anonyme)

- `getOrCreateSessionId()` : retourne un UUID v4 lu depuis `localStorage['unige_session_id']`, créé et persisté à la première invocation. Utilisé par `statsApi.recordEventView` pour dédupliquer les vues anonymes côté serveur via un `INSERT ... ON CONFLICT (event_id, session_id) DO UPDATE SET viewed_at = ...`.

## Composants SCRUM-137 (co-organisateurs UI)

### CoOrganizersEditor

- `src/components/event/CoOrganizersEditor.tsx` — section "Co-organisateurs" dans `EventForm` mode édition.
- Props : `eventId: number`.
- Champ texte UUID + bouton "Inviter" (validation regex UUID v4 côté client + 404 mapping côté serveur si l'utilisateur n'existe pas).
- Liste des co-orgs avec chip statut (`PENDING` orange / `ACCEPTED` vert) + bouton × pour retirer.
- Skeleton `co-organizers-section.bones.json` pendant chargement initial.

### EventAttachmentsEditor (SCRUM-149)

- `src/components/event/EventAttachmentsEditor.tsx` — section "Fichiers joints" embarquée dans `EventForm` via la prop `attachmentsSection`, injectée par `EventEditPage` (event existant) — la voie create est gérée par `PendingAttachmentsEditor` ci-dessous.
- Props : `eventId: number`, `attachments: Attachment[]`, `onChange: (next: Attachment[]) => void`.
- UX stage-then-upload : `<input type="file" multiple accept=".pdf,.doc,.docx,.xlsx,.png,.jpg,.jpeg">` → "Fichiers à uploader" → bouton "Uploader" itère **une requête `POST /events/{id}/attachments` par fichier**, séquentielle. Files réussies → `onChange([...attachments, ...succeeded])`, files échouées → restent dans la staging list avec leur message d'erreur per-row (mapping des codes backend `attachment_invalid_size` / `_type` / `_limit_exceeded` + status `413` / `415`).
- Validations client miroir backend (`@/types/attachment` : `ATTACHMENT_MAX_BYTES`, `ATTACHMENT_MAX_PER_EVENT`, `isAcceptedAttachmentFile`). Garde extension-fallback quand `file.type` est vide (drag-and-drop / OS).
- Liste des uploadés : ancre `<a href={attachment.downloadUrl} download={fileName}>` — pointe sur l'endpoint API same-origin `/api/events/{eventId}/attachments/{id}/download` qui streame depuis MinIO avec `Content-Disposition: attachment` (le browser force le téléchargement). `fileUrl` n'est **pas** utilisé côté frontend (cf. types.md). Bouton télécharger dédié + bouton × → `DELETE /events/{id}/attachments/{id}`. Suppression échouée → message inline, row préservée.

### PendingAttachmentsEditor (SCRUM-149)

- `src/components/event/PendingAttachmentsEditor.tsx` — pendant create du `EventAttachmentsEditor`. Injecté par `EventCreatePage` via `attachmentsSection` du `EventForm`.
- Props : `pending: PendingAttachment[]`, `onAdd(entries)`, `onRemove(id)` — le parent possède l'état (même contrat que `PendingCoOrganizersEditor`).
- Stage uniquement (pas d'appel API tant que l'event n'a pas d'ID). Mêmes validations client que la version live (mime / size / overflow). Les entrées rejetées affichent un badge "Refusé" + le message d'erreur ; les valides affichent **"Sera publié"** (libellé explicite — pas de bouton "Uploader" à cliquer, l'upload est automatique au moment de la création).
- Sous-titre de la section : "Les documents sélectionnés seront automatiquement publiés à la création de l'événement — aucun bouton à cliquer." pour lever toute ambiguïté UX.
- `EventCreatePage.onSuccess` filtre les entrées sans erreur et itère `uploadEventAttachment(eventId, file)` — best-effort, échec individuel toasté, ne bloque pas la navigation vers `/events/:id`.

### EventDocumentsList (SCRUM-149)

- `src/components/event/EventDocumentsList.tsx` — section "Documents" pour `EventDetailPage`.
- Props : `attachments: Attachment[]`.
- Retourne `null` quand la liste est vide → l'appelant peut le rendre sans condition externe (mais `EventDetailPage` le gate quand même pour éviter de réserver de l'espace dans la grille mobile).
- Une row par attachment : ancre `<a href={attachment.downloadUrl} download={fileName}>` avec icône `FileText` (lucide-react) + filename + taille via `formatFileSize` + icône `Download`. aria-label `Télécharger <fileName>`. L'URL est same-origin et le backend ajoute `Content-Disposition: attachment` → téléchargement forcé sans JS.
- Visible pour tous (auth ET non-auth) — pas d'auth gate.

### EventOrganizerTeam

- `src/components/event/EventOrganizerTeam.tsx` — section "Équipe organisatrice" dans la sidebar de `EventDetailPage`.
- Affiche le créateur principal (badge "Organisateur") + co-organisateurs ACCEPTED (badge "Co-organisateur"), chacun cliquable vers `/profile/{id}`.
- Charge `listCoOrganizers(eventId)` au montage. Skeleton `event-organizer-team.bones.json` pendant loading.

### CoOrganizerInvitationsBadge

- `src/components/user/CoOrganizerInvitationsBadge.tsx` — petit badge rouge dans le dropdown user de la `Navbar`.
- Affiche le compteur des invitations `PENDING` (issue de `useCoOrganizerInvitations`). Masqué si compteur = 0.
- Clic → ouvre la liste (modale ou redirige vers `/profile/me`).

### CoOrganizerInvitationsList

- `src/components/user/CoOrganizerInvitationsList.tsx` — liste des invitations PENDING.
- Une card par invitation : titre event + date + boutons Accepter / Décliner.
- Optimistic update : retire la row de la liste avant confirmation serveur, rollback sur erreur.
- Skeleton `co-organizer-invitations.bones.json` pendant loading.
- Monté dans `ProfilePage` (uniquement si `isOwnProfile`) et potentiellement dans une modale navbar.

## Composants SCRUM-146 (commentaires UI)

### CommentSection

- `src/components/event/CommentSection.tsx` — section principale insérée dans `EventDetailPage`.
- Props : `eventId: number`, `eventStatus: EventStatus`.
- Charge `getEventComments(eventId)` via `useComments`. Affiche le `CommentForm` (caché pour anonymes ou statut ≠ PUBLISHED) + liste de `CommentItem`.
- État vide : "Aucun commentaire — soyez le premier."
- Pagination "Charger plus" via `useComments.loadMore()`.
- Skeleton `comments.bones.json` pendant loading initial.

### CommentForm

- `src/components/event/CommentForm.tsx` — formulaire textarea + bouton Envoyer.
- Props : `eventId`, `parentCommentId?`, `onPost(content)`, `onCancel?` (pour le mode reply).
- Compteur live de caractères (max 500). Bouton désactivé si vide ou loading.
- Affichage conditionnel : si user anonyme, remplace le form par un message "Connectez-vous pour commenter" + lien vers `/login`.

### CommentItem

- `src/components/event/CommentItem.tsx` — card commentaire avec avatar, displayName, badge "Organisateur" si `authorIsOrganizer`, contenu, date relative (`formatRelativeDate`).
- Actions :
  - **Like (SCRUM-147)** — icône `Heart` lucide, count rendu uniquement si > 0. Visible pour tous (incluant anonymes, où le bouton est `disabled` + tooltip *"Connectez-vous pour aimer"*). Optimistic toggle + rollback via le hook `useCommentLike`. Idempotent côté backend (200 sur double-like, 204 sur unlike-inexistant).
  - **Répondre** — toggle un sous-formulaire `CommentForm` pré-rempli par `@<parentAuthorUsername> ` (SCRUM-147 — pinge le parent author par défaut). Cache le bouton sur les replies (depth-1 enforcement) et pour les anonymes.
  - **Supprimer** — visible si user est auteur OR créateur event OR co-org ACCEPTED OR admin. `ConfirmDialog` avant.
  - **Signaler (SCRUM-147)** — ouvre `ReportModal` avec `target='comment'` (réutilisation du composant event-report). Caché pour l'auteur lui-même (filet ; backend renvoie 422 cannot_report_own_comment) et pour les anonymes. Le hook `useReportComment` mappe 409 → toast *"déjà signalé"* + close ; 422 → toast spécifique + close ; 5xx → toast retry + modal stays open.
- Replies imbriquées **max 1 niveau** : un `CommentItem` rend ses replies via `CommentItem` enfants, mais sans bouton "Répondre" sur les enfants (limite SCRUM-139 backend).

### CommentForm (étendu SCRUM-147)

- `src/components/event/CommentForm.tsx` — toujours textarea + counter + submit, ajouts SCRUM-147 :
  - Nouveau prop optionnel `initialValue?: string` — seed la valeur au mount (utilisé par les replies pour pré-remplir `@<parentAuthorUsername> `). Ignoré après le mount pour ne pas clobber le typing.
  - Wrap le textarea dans un container `relative` qui héberge le `MentionAutocomplete` inline.
  - Refforward sur la `Textarea` (FormField.tsx — React 19 `ref` prop) pour permettre à l'autocomplete de lire `selectionStart`.

### MentionAutocomplete (nouveau SCRUM-147)

- `src/components/event/MentionAutocomplete.tsx` — dropdown qui flotte sous un textarea et se déclenche sur `@<prefix>` (≥ 2 chars, debounce 300ms).
- Props : `value`, `onChange(newValue, newCaretPos)`, `textareaRef: RefObject<HTMLTextAreaElement>`, `disabled?`.
- Consomme `searchUsernames(prefix, 8)` (le wrapper de `/api/users/search` figé par SCRUM-137). Aucune nouvelle dépendance externe.
- Navigation clavier ARIA combobox/listbox/option : ↑/↓ pour bouger l'active row, Enter pour insérer la suggestion sélectionnée, Esc pour fermer. Click-outside ferme aussi.
- À la sélection, remplace `@<typedPrefix>` par `@<username> ` (avec espace final) et repositionne le caret juste après l'espace via `requestAnimationFrame`.
- Le parser `detectActiveMention(value, caretPos)` vit dans `src/utils/mentions.ts` (pure function, unit-testable hors React) — gère les multi-`@` autour du caret, ignore `email@example.com`, accepte dash + dot dans la handle.

### ReportModal (étendu SCRUM-147)

- `src/components/event/ReportModal.tsx` — modal partagé event + comment.
- Nouveau prop `target?: 'event' | 'comment'` (default `'event'` pour back-compat). Swap le titre via `TITLES` const map. Le form (Select reason + Textarea description) reste identique aux deux cibles.
- L'`onSubmit` est typé `(reason, description?) => Promise<void>` — le hook `useReportComment` (ou `useReportEvent` selon la cible) wrappe l'appel API + toast feedback.

## Hooks SCRUM-137 / SCRUM-146

### useCoOrganizers

- `src/hooks/useCoOrganizers.ts`.
- Params : `eventId: number | null` (null = pas de chargement).
- Charge `listCoOrganizers(eventId)` au montage. Retourne `{ coOrganizers, loading, error, invite, remove, refresh }`.
- `invite(userId)` appelle `inviteCoOrganizerApi` puis refresh ; gère 404, 409 (déjà co-org), 422 (own event).
- `remove(userId)` appelle `removeCoOrganizerApi` optimistic puis refresh sur erreur.

### useCoOrganizerInvitations

- `src/hooks/useCoOrganizerInvitations.ts`.
- Charge `getMyInvitations({ status: 'PENDING' })` au montage (uniquement si user connecté).
- Retourne `{ invitations, pendingCount, loading, error, accept, decline, refresh }`.
- `accept(eventId)` / `decline(eventId)` : mutation optimistic + refresh.

### useComments

- `src/hooks/useComments.ts`.
- Params : `eventId: number`, `pageSize?: number = 20`.
- Charge `getEventComments(eventId, 0, pageSize)`. Pagination cumulative via `loadMore()`.
- `post(content, parentCommentId?)` : optimistic add + rollback sur erreur API.
- `remove(commentId)` : optimistic remove + rollback sur erreur API.
- Retourne `{ comments, hasMore, loading, posting, error, post, remove, loadMore, refresh }`.

## Hook SCRUM-151

### useOccurrences

- `src/hooks/useOccurrences.ts`.
- Params : `(parentId: number | null, { enabled: boolean })`.
- Fetch paresseux : `enabled === false` court-circuite l'effet, aucun appel réseau tant que le consumer ne flip pas `enabled` à `true` (cf. Décision G : la section repliable d'`EventDetailPage` ne charge les occurrences qu'au premier clic).
- Si `parentId === null` ou `enabled === false`, retourne `{ loading: false, error: null, data: null }`.
- Sinon appelle `getOccurrences(parentId)` ; state machine `loading → (data | error)` à la `useEvent`.
- Re-fetch automatique si `parentId` change pendant `enabled === true`.
- AbortController-like via un compteur monotone : les réponses d'une requête obsolète (unmount, refetch concurrent) sont silencieusement ignorées — aucune fuite `setState after unmount`.

## Sections SCRUM-151

### Section Récurrence dans `EventForm`

- Composant local `RecurrenceSection` non exporté (`EventForm.tsx`). Visible **uniquement** en `mode === 'create'` (Décision E).
- Header avec icône `Repeat` + switch « Événement récurrent ». Body conditionnel sur `enabled === true` : Select fréquence (3 options FR via `RECURRENCE_FREQUENCIES`), radio mutex `endDate | maxOccurrences` rendu en segmented control, puis l'`<Input>` correspondant (`type="date"` ou `type="number"` borné à `RECURRENCE_MAX_OCCURRENCES`).
- Erreur de validation rendue sous la section via la clé unique `errors.recurrence` (Décision C — un message global pour la section, pas un par sous-champ).
- Pattern visuel calqué sur la section « Date & heure » : `rounded-2xl border border-border/50 bg-foreground/[0.015] px-4 py-4`.

### Badge `Récurrent` sur `EventCard`

- Pill `RefreshCw + "Récurrent"` positionné `absolute bottom-4 right-4 z-10` sur le banner. Style neutre (`bg-background/80 backdrop-blur-sm border border-border/40 text-foreground/80`).
- Visible **uniquement** si `event.parentEventId != null` (Décision F — occurrences only, le parent reste sans badge pour ne pas être noyé dans 52 cards identiques).

### Section « Voir toutes les occurrences » sur `EventDetailPage`

- Composant local `OccurrencesSection` non exporté (`EventDetailPage.tsx`). Visible si `event.recurrenceRule != null` (parent) **ou** `event.parentEventId != null` (occurrence) — Décision G.
- Bouton plein-largeur avec `RefreshCw` + chevron (`ChevronDown` / `ChevronUp`). Premier clic → `useOccurrences(parentId, { enabled: true })` (fetch paresseux). Compteur `(N)` affiché à côté du libellé une fois `data` arrivé.
- Liste compacte (Décision H) : `[date · status badge][titre lien]` plus le marqueur `Vous êtes ici` (uppercase tracking-wide text-accent) sur la ligne dont l'`id` matche `currentEventId`. Pas de banner image, pas de meta location/capacity.
- Loading : `Skeleton` boneyard générique inline (4 lignes squelettes) — aucun nouveau `.bones.json` (Décision I).
- `parentId` calculé : `event.parentEventId ?? event.id`. Sur un parent on liste ses enfants ; sur une occurrence on remonte au parent et on liste ses sibblings.
