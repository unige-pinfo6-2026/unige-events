Tu vas rédiger un fichier de spécification pour une nouvelle feature du projet UNIGE Events dans le dossier `specs_archives/specs_claude/`. Le fichier s'appellera `specs_drafts_recovery.md`.

## Contexte du projet
Lire d'abord `backend/docs/` et `frontend/docs/` en entier pour comprendre l'architecture, les conventions, et le système actuel de création/modification d'événements avec les statuts `DRAFT` / `PUBLISHED` / `CANCELLED`.

## Modèles de référence à suivre IMPÉRATIVEMENT

### Structure et format du document
Tes specs doivent suivre exactement le format, le niveau de détail, la structure de sections, et le style des specs existantes :
- `specs_archives/specs_claude/specs_scrum-89.md` (référence principale — feature frontend+backend)
- `specs_archives/specs_claude/specs_scrum-89-bis.md` (référence secondaire — feature frontend)
- `specs_archives/specs_claude/specs_scrum-76.md` (référence — feature de recherche)

Tu DOIS reprendre la même organisation : contexte, analyse de l'existant, décision technique justifiée, architecture proposée, changements backend (si nécessaire), changements frontend fichier par fichier, types TypeScript, skeletons, tests, critères d'acceptation, fichiers touchés, checklist d'implémentation.

### Design visuel de la bannière
Pour le design de la bannière à intégrer en haut de `/events/new`, inspire-toi des specs de rework visuel existantes :
- `specs_archives/specs_claude/rework/events-create-edit/SPEC_event_form_rework_v3.md` (layout actuel en 5 bandes de `EventForm`)
- `specs_archives/specs_claude/rework/events-create-edit/SPEC_event_form_audit_v1.md`
- `specs_archives/specs_claude/rework/events-view/SPEC_event_detail_rework.md`

La bannière doit être cohérente avec l'esthétique glassmorphism / design tokens (`bg-background`, `text-foreground`, `border-border`, `text-accent`) et le layout actuel de `CreateEventPage`. Elle doit se fondre visuellement avec `EventForm` sans casser son équilibre en 5 bandes.

**PRENDS DES LIBERTÉS. INNOVE.** Ne te contente pas d'une rangée de cards génériques. Propose quelque chose qui a du caractère et qui raconte l'idée d'un "travail en cours" qu'on vient reprendre. Pistes non-exhaustives à explorer et à trancher dans la spec (avec justification) :

- **Carrousel horizontal scrollable** de cartes brouillon mini (snap-scroll, peek du suivant) — donne un sens de "pile de travaux en cours" sans prendre toute la hauteur.
- **Ruban horizontal compact** avec chips brouillon et un petit visuel (icône ou mini-banner circulaire) — façon "onglets de travail" d'un IDE.
- **Stack effect** façon cartes empilées en perspective (CSS transforms) où seul le brouillon le plus récent est pleinement visible, les autres dépassent légèrement derrière — très peu de hauteur, effet mémoriel.
- **Bannière rétractable** : collapsed par défaut (une barre fine "Vous avez N brouillons en cours →"), s'étend au clic pour révéler les cartes. Respecte l'intention principale de la page (créer un nouvel événement).
- **Métadonnée visible par carte** : titre du brouillon (ou "Brouillon sans titre"), date de dernière modification relative ("il y a 2 h"), et un indicateur visuel du niveau de complétion du formulaire (barre de progression fine ou ring % — calculé côté front à partir des champs présents sur l'Event).
- **Micro-interaction au hover** : léger tilt / glow accent, curseur "pen" pour suggérer la reprise de saisie.
- **Transition d'entrée** : fade + translateY discret au montage (stagger si plusieurs brouillons), mais respecter `prefers-reduced-motion`.
- **État vide traité comme un vrai moment de design**, pas un fallback : si 0 brouillon, la bannière peut soit disparaître totalement, soit devenir un petit pill discret ("Astuce : sauvegardez en brouillon à tout moment"). Tranche dans la spec.

Tranche explicitement sur UN parti pris final dans la spec (pas "au choix du dev") et justifie-le en 3–5 lignes : pourquoi ce choix sert mieux l'UX que les alternatives, comment il préserve la hiérarchie visuelle de `EventForm`, et comment il reste sobre en hauteur pour ne pas pousser le formulaire sous la ligne de flottaison.

## Feature à spécifier

**Problème :** Aujourd'hui, quand un utilisateur sauvegarde un événement en brouillon via le lien "Sauvegarder en Brouillon" de `CreateEventPage` (qui appelle `triggerDraftSave()` de `useEventForm`), l'événement est bien persisté en base avec `status=DRAFT` via `POST /events`, MAIS l'utilisateur n'a ensuite aucun moyen de le retrouver depuis l'UI — il est perdu.

**Solution (décision technique déjà prise — à JUSTIFIER en détail dans la spec) :** Les brouillons sont stockés en base de données, pas en localStorage. Raisons (développer dans la spec) :
1. Le backend supporte déjà nativement `Event.status=DRAFT` et le filtre `GET /events?status=DRAFT&organizerId=me` — aucun endpoint à créer.
2. Persistance multi-appareils (Auth0 déjà en place).
3. Source unique de vérité — le Sprint 5 planifie une page "Mes Événements" qui consommera les mêmes données. Éviter la dette technique d'une double source (DB + localStorage) à réconcilier.
4. Pas de perte à un `Clear browsing data` ni de fuite sur navigateur partagé.
5. Isolation de sécurité déjà appliquée par le backend (`creatorId`, `@Authenticated`).
6. La route `/events/:id/edit` fonctionne déjà sur les DRAFT — le flux reprendre→publier est déjà opérationnel.

**Scope précis — NE PAS élargir :**
- ✅ Ajouter une bannière en haut de la page `CreateEventPage` (`/events/new`) qui permet d'afficher et reprendre les brouillons existants de l'utilisateur connecté.
- ✅ La bannière doit afficher/exposer les brouillons existants selon le parti pris visuel que tu auras tranché.
- ✅ Cliquer sur un brouillon → navigation vers `/events/:id/edit` pour reprendre sa saisie. La logique de publication existe déjà dans `useEventForm`.
- ✅ La bannière doit gérer les états loading / empty / error / data (avec skeleton si nécessaire — voir plus bas).
- ✅ Elle ne s'affiche que si l'utilisateur est authentifié (`CreateEventPage` est déjà sous `PrivateRoute`).
- ✅ Traiter l'état vide comme un vrai moment de design.
- ❌ NE PAS créer la page "Mes Événements" (`/my-events`) — elle viendra au Sprint 5 via SCRUM-93.
- ❌ NE PAS toucher à la logique d'édition ni de publication — elles fonctionnent déjà.
- ❌ NE PAS créer de nouveaux endpoints backend — le filtre `GET /events?organizerId={me}&status=DRAFT` suffit.

## Points d'analyse obligatoires dans la spec

1. **Analyse de l'existant** — lire et citer les fichiers réellement présents dans le repo :
   - `frontend/src/pages/CreateEventPage.tsx` (ou équivalent) — comment y injecter la bannière
   - `frontend/src/hooks/useEventForm.ts` — confirmer que `triggerDraftSave()` envoie bien `status=DRAFT`
   - `frontend/src/services/eventApi.ts` — vérifier si `getAll(params)` accepte déjà `status` et `organizerId`
   - `frontend/src/contexts/AuthContext.tsx` (ou équivalent) — récupérer l'ID de l'utilisateur connecté
   - `frontend/src/pages/event/EventEditPage.tsx` — confirmer que l'édition de DRAFT fonctionne
   - Backend `EventResource.java` et `EventService.java` — confirmer la signature du filtre

2. **Flux utilisateur complet** — décrire en numéroté depuis "user arrive sur /events/new" jusqu'à "user publie son brouillon".

3. **Contrat d'API** — appel exact, paramètres, réponse attendue. Référencer `backend/docs/api-contract.md` et `backend/docs/openapi/openapi.yaml`.

4. **Découpage frontend fichier par fichier** — pour chaque fichier touché ou créé :
   - Chemin exact
   - Responsabilité
   - Props / signature / contenu
   - Interaction avec les autres fichiers

   Composants probables (à confirmer pendant ton analyse) :
   - `components/event/DraftsBanner.tsx` (nouveau) — bannière visuelle
   - Sous-composants si ton parti pris le justifie (ex. `DraftCard.tsx`, `DraftCompletionRing.tsx`…)
   - `hooks/useMyDrafts.ts` (nouveau) — fetch + état loading/error/data
   - `services/eventApi.ts` — éventuel helper `getMyDrafts()`
   - `pages/CreateEventPage.tsx` — intégration de la bannière
   - `types/event.ts` — éventuels types additionnels

5. **Skeleton** — lire `frontend/skeleton/README.md` avant d'écrire cette section. Si la bannière a un état loading visible (≥ 200ms perçu) → spécifier le skeleton :
   - Nom du fichier `frontend/src/bones/drafts-banner.bones.json`
   - Breakpoints, dimensions, bones (coordonnées, isContainer, leaf)
   - Enregistrement dans `frontend/src/bones/registry.js`
   - Fixture locale non-exportée dans `DraftsBanner.tsx`
   - Intégration `<Skeleton name="drafts-banner">` avec `useTheme`
   Si ton parti pris visuel (ex. bannière rétractable collapsed par défaut) rend le skeleton inutile, justifie-le explicitement.

6. **Types TypeScript** — pas d'ajout attendu (réutiliser `Event`), mais le vérifier explicitement. Si un type utilitaire (`DraftCompletion`, `DraftCardViewModel`…) émerge naturellement de ton parti pris, le spécifier.

7. **Tests** :
   - Unitaires : `useMyDrafts` (loading, success, empty, error), `DraftsBanner` (rendu des 4 états, navigation au clic, a11y, `prefers-reduced-motion` si animations)
   - Service : stub Axios de `getMyDrafts()` vérifiant les query params envoyés
   - Cible couverture ≥ 80% (seuil SonarCloud du projet)

8. **Critères d'acceptation** — liste à cocher, user-facing, testables manuellement.

9. **Edge cases à traiter explicitement** :
   - Utilisateur avec 0 brouillon → état vide designé, pas un fallback générique
   - Brouillon dont `startDate` est maintenant passée → affichage spécifique (badge "Date expirée" ? tri en bas ?)
   - Beaucoup de brouillons → limite affichée dans la bannière (5–8 derniers ?), le reste accessible plus tard via `/my-events`
   - Erreur réseau → ne pas bloquer `CreateEventPage`, fallback silencieux
   - Ordre d'affichage → `updatedAt DESC` probable, à confirmer
   - `creatorId` côté backend est une UUID (l'ID interne, pas l'`auth0Id`) — confirmer quel identifiant est disponible côté `AuthContext`
   - Accessibilité : navigation clavier sur les cartes, `aria-label`, focus visible

10. **Conventions du projet à respecter** (rappel) :
    - camelCase partout, booléens sans préfixe `is`
    - Pas de `any` TS, pas de style inline sauf hex dynamique
    - Design tokens Tailwind (`bg-background`, `text-foreground`, `border-border`, `text-accent`, `text-error`)
    - Const maps typées pour les variantes visuelles, pas de ternaires inline
    - Mise à jour de `frontend/docs/components.md`, `frontend/docs/architecture.md` si pertinent, et `frontend/docs/sprint-context.md`
    - Mise à jour de `AGENTS.md` section Skeletons si un skeleton est créé

## Livrable attendu

Un fichier unique `specs_archives/specs_claude/specs_drafts_recovery.md` qui, lu par un dev ou un agent IA, lui permet d'implémenter la feature sans avoir à inventer une décision. Longueur attendue : comparable à `specs_scrum-89-bis.md` — détaillé mais scopé. Pas d'implémentation de code dans la spec ; uniquement les signatures, structures JSX, pseudo-code critique et appels API.

## DERNIÈRE SECTION OBLIGATOIRE DU FICHIER DE SPECS

Le tout dernier bloc du fichier `specs_drafts_recovery.md` doit être une section intitulée exactement :

> ## Prompt de lancement d'implémentation

Cette section contient — dans un bloc de code markdown fenced (````) — le prompt prêt à copier-coller que l'utilisateur enverra ensuite à un agent pour déclencher l'implémentation effective de la feature à partir de cette spec. Le prompt doit :

- Pointer vers le fichier de spec lui-même (`specs_archives/specs_claude/specs_drafts_recovery.md`) comme source unique de vérité.
- Rappeler de lire `AGENTS.md`, `backend/docs/` et `frontend/docs/` avant de commencer.
- Lister dans l'ordre les étapes d'implémentation (backend si nécessaire, service, hook, skeleton, composant, intégration dans CreateEventPage, tests, doc).
- Rappeler explicitement les interdits : ne pas créer `/my-events`, ne pas toucher à la logique publish/edit existante, ne pas créer d'endpoint backend.
- Exiger le respect des conventions du projet (camelCase, tokens Tailwind, pas de `any`, skeleton si loading visible, tests ≥ 80%).
- Demander la mise à jour de la doc frontend et de `AGENTS.md` en fin d'implémentation.
- Se terminer sur les critères de done (lint, tests, preview manuelle de la bannière dans les 4 états, vérification a11y clavier).

Autrement dit : le fichier de spec doit être auto-porteur — la spec décrit QUOI et POURQUOI, et la toute dernière section fournit le prompt exact qui déclenche le COMMENT.
