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

## Sprint 4 — Skeleton screens Boneyard (2026-04-12)

Terminé le 2026-04-12.

Fonctionnalités livrées :
- Skeleton screens Boneyard — `EventCards`, `EventDetailPage`, `ProfilePage`, `EventsSearchPage`, `EventCalendar`, `EventEditPage`, `Navbar` (bouton utilisateur).
- Intégration de `boneyard-js` : import du registry dans `main.tsx`, générateur custom `skeleton/generate.mjs` (pas de CLI Playwright — routes protégées inaccessibles sans auth).
- `src/components/utils/Skeleton.tsx` supprimé — `SkeletonBlock` retiré, remplacé par `<Skeleton>` de `boneyard-js/react` partout.
- Fixtures locales non-exportées dans chaque composant ciblé — JSX statique reproduisant le layout réel pour établir les dimensions du container.
- `LoadingSpinner` retiré des pages/composants couverts par un skeleton — conservé dans `PrivateRoute` et `LoadingPage`.
- Règle établie : **tout futur composant ou page avec appel API doit générer son skeleton** (documenté dans `AGENTS.md` et `docs/dev-guide.md`).

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
