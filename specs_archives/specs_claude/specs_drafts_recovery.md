# Specs — Reprise des brouillons depuis la page de création d'événement

> **Branche suggérée :** `feature/drafts-recovery-strip`
> **Sprint :** Sprint 4 — Correctif UX (hors SCRUM numéroté)
> **Prérequis :** Aucun — le backend supporte déjà `Event.status=DRAFT` et le filtre `GET /events?organizerId={uuid}&status=DRAFT` ; le flux d'édition `/events/:id/edit` fonctionne déjà sur les DRAFT
> **Règle d'or :** **Aucun endpoint backend à créer.** Toute la feature est frontend : un service helper, un hook, un composant, un skeleton. La source de vérité reste `backend/docs/openapi/openapi.yaml`.
> **Scope interdit :** ne pas implémenter `/my-events` (c'est SCRUM-93 au Sprint 5) ; ne pas modifier `useEventForm` ni la logique d'édition/publication.

---

## Contexte

### Principe fonctionnel

Aujourd'hui, la page [CreateEventPage.tsx](frontend/src/pages/event/EventCreatePage.tsx) expose un lien "Sauvegarder en Brouillon" dans `EventForm` (Bande 3), câblé à `useEventForm.triggerDraftSave()`. Le flux actuel :

1. L'utilisateur remplit partiellement le formulaire.
2. Il clique "Sauvegarder en Brouillon".
3. `useEventForm` force `status=DRAFT` via un `useRef` interne, puis appelle `createEvent(payload)` → `POST /api/events`.
4. Le backend persiste l'événement avec `status=DRAFT`, lie `creator` à l'utilisateur courant, retourne `EventDTO`.
5. **Fin du flux — l'événement est "perdu" côté UI.** Aucune route frontend n'expose les brouillons existants. L'utilisateur n'a aucun moyen de reprendre sa saisie, à moins de garder manuellement l'URL `/events/:id/edit` dans son historique.

**Objectif :** rendre ces brouillons à nouveau accessibles, sans créer la page complète `/my-events` (qui est planifiée au Sprint 5 via SCRUM-93). L'accès doit se faire **depuis la page où l'utilisateur en a le plus besoin** : `/events/new` — le moment exact où il pense à créer un événement, c'est aussi le moment où il devrait se rappeler qu'un brouillon traîne.

### Frontières avec SCRUM-93 (page /my-events)

| SCRUM-93 (Sprint 5, à ne PAS faire ici) | Cette spec |
|---|---|
| Page complète `/my-events` avec onglets Publiés / Brouillons / Annulés | Aucune nouvelle route |
| Table ou grille exhaustive paginée | Max 5 brouillons récents affichés inline |
| Actions "Modifier", "Statistiques", "Publier", "Annuler" | Une seule action : clic → `/events/:id/edit` |
| Lien permanent dans la navbar | Accessible **uniquement** depuis `/events/new` |
| Endpoint backend nouveau ou étendu | Réutilise `GET /events?organizerId={uuid}&status=DRAFT` existant |

Quand SCRUM-93 arrivera, la page `/my-events` consommera exactement le même hook `useMyDrafts` (ou un hook frère `useMyEvents`) et le même appel API — **zéro dette technique à migrer**, ce qui est l'argument numéro 1 pour le choix "base de données" documenté ci-dessous.

---

## Décision technique — stockage en base de données, pas en localStorage

Cette décision a été prise en amont. Elle est documentée ici pour que tout futur lecteur (dev, relecteur, agent IA) comprenne pourquoi `localStorage` a été explicitement écarté.

### Arguments en faveur de la base de données (retenus)

| # | Argument | Pourquoi décisif |
|---|---|---|
| 1 | **Le backend supporte déjà nativement les DRAFT.** `EventStatus.DRAFT` est dans l'enum depuis le Sprint 2, `POST /events` accepte `status=DRAFT` dans `CreateEventRequest`, `PUT /events/{id}` accepte le changement de statut côté créateur, `PATCH /events/{id}/publish` existe. | Rien à construire côté backend. L'option localStorage impliquerait de ré-implémenter côté front une logique de persistance que le backend maîtrise déjà. |
| 2 | **Persistance multi-appareils.** Auth0 est en place, l'utilisateur est identifié de façon stable. Un organisateur commence la saisie sur son laptop à la bibliothèque UNIGE, reprend sur son téléphone dans le tram — `localStorage` casse ce flux dès le changement de navigateur. | Un brouillon représente typiquement plusieurs minutes de saisie (titre, description, dates, lieu, catégorie, capacité). Le perdre à un changement d'appareil est inacceptable. |
| 3 | **Source unique de vérité pour SCRUM-93.** Le Sprint 5 planifie `MyEventsPage.tsx` avec un onglet "Brouillons" qui filtrera via `GET /api/events?organizerId=me&status=DRAFT` ([backlog_s5_s10.md:225](backend/docs/backlog_s5_s10.md#L225)). | Stocker en localStorage aujourd'hui = deux sources divergentes à réconcilier dans 2 semaines (migration, dédup, conflits de version). Dette technique garantie et évitable. |
| 4 | **Pas de perte à un `Clear browsing data`.** Les utilisateurs vident leur cache, changent de session privée, réinstallent leur OS. | localStorage est volatile par design. Un brouillon DB survit à tout ce qui n'est pas un `DELETE` explicite. |
| 5 | **Isolation de sécurité par le backend.** `POST /events` est `@Authenticated`, `PUT /events/{id}` et `DELETE /events/{id}` appliquent `isCreator()` → 403 sinon. L'isolation par `creator.id` est faite côté serveur. | localStorage est partagé entre tous les utilisateurs d'un même navigateur (PC familial, poste public UNIGE, kiosque) → fuite potentielle de contenu entre comptes. |
| 6 | **La route `/events/:id/edit` fonctionne déjà sur les DRAFT.** `EventEditPage.tsx` ne filtre pas par statut, `useEventForm(mode='edit')` accepte n'importe quel `initialEvent` et la publication finale est déjà gérée via le select de statut dans `EventForm`. | Le flux *reprendre → compléter → publier* est déjà 100% opérationnel. Il ne manque **qu'une UI pour retrouver les DRAFT existants**. C'est exactement le scope de cette spec. |

### Arguments en faveur de localStorage (écartés)

| Argument | Pourquoi écarté |
|---|---|
| "Plus rapide, pas de latence réseau" | Un brouillon se charge en une seule requête `GET /events?organizerId=X&status=DRAFT`. La latence est négligeable comparée à la valeur apportée. |
| "Fonctionne offline" | `CreateEventPage` est déjà sous `PrivateRoute` → nécessite un login Auth0 → nécessite le réseau. Offline n'est pas un use case valide ici. |
| "Pas d'entité persistée tant qu'on n'a pas validé" | Faux problème. Le backend **accepte déjà** un DRAFT incomplet (le seul garde-fou est `startDate @Future` côté `CreateEventRequest` — comportement actuel conservé). Le brouillon est par définition "incomplet mais en cours". |
| "Simple à implémenter côté front" | Vrai, mais crée la dette mentionnée en argument #3. Le coût de SCRUM-93 bondirait mécaniquement. |

### Note — point d'attention backend à ne PAS corriger ici

L'analyse backend a révélé que `GET /events` est `@PermitAll` et applique aucun filtre par `principal.getName()` quand `organizerId` est passé. En théorie, un utilisateur authentifié curieux pourrait énumérer les DRAFT d'un autre utilisateur en devinant son UUID. **Ce point dépasse le scope de cette spec.** Il doit être remonté comme ticket de sécurité séparé (probablement "ajouter un filtre implicite `@Authenticated` + `creator.id = principal` quand `status=DRAFT`"). La présente spec n'introduit pas de régression — elle consomme simplement un endpoint existant tel qu'il est documenté.

---

## Ce qui existe déjà (ne pas retoucher sauf indication contraire)

| Fichier | État | Pourquoi on ne touche pas |
|---|---|---|
| `backend/src/main/java/ch/unige/events/resource/EventResource.java` | Complet — `GET /events` accepte déjà `status: EventStatus` et `organizerId: UUID` | Filtre suffisant tel quel |
| `backend/src/main/java/ch/unige/events/service/EventService.java` | Complet — `getAll(page, size, status, category, organizerId, endDateFrom)` construit déjà la clause JPQL `creator.id = :organizerId` | Rien à étendre |
| `backend/docs/openapi/openapi.yaml` | Déjà conforme au filtre exposé | **Relire pour confirmer**, mais pas à modifier |
| `frontend/src/hooks/useEventForm.ts` | `triggerDraftSave()` force bien `status='DRAFT'` via `forcedStatusRef` avant `submitForm()` | Ne pas toucher, contrat stable |
| `frontend/src/services/eventApi.ts` | `getAll(params: EventsParams)` accepte déjà `status?: EventStatus` et `organizerId?: string` dans `EventsParams` | On ajoute juste un **helper** qui pré-remplit ces params, on ne modifie pas `getAll` |
| `frontend/src/pages/event/EventEditPage.tsx` | Charge l'event via `getById(id)`, instancie `useEventForm({ mode: 'edit', initialEvent })`, fonctionne sur DRAFT | Aucune modification |
| `frontend/src/hooks/useAuth.ts` + `frontend/src/contexts/AuthContext.tsx` | `useAuth()` expose `user: User \| null`, avec `user.id: string` (UUID backend) | Source pour l'`organizerId` à envoyer |
| `frontend/src/types/event.ts` | `Event` complet avec `id`, `title`, `description?`, `startDate`, `endDate`, `category`, `bannerUrl?`, `capacity?`, `status`, `createdAt`, `updatedAt?` | Réutilisé tel quel |
| `frontend/src/components/event/EventForm.tsx` | Layout en 5 bandes, pas de header au-dessus | Aucune modification — le strip se greffe **au-dessus** dans `EventCreatePage`, pas dans `EventForm` |
| `frontend/src/pages/event/EventCreatePage.tsx` | Layout `SectionWrapper` → `SectionHeader` → `EventForm` | **Seul** fichier touché côté pages : injection du strip entre `SectionHeader` et `EventForm` |

---

## Ce qui est à créer

| Fichier | Rôle |
|---|---|
| `frontend/src/services/eventApi.ts` (ajout d'un helper) | Fonction `getMyDrafts(organizerId: string, limit?: number): Promise<Event[]>` — wrapper typé autour de `getAll` |
| `frontend/src/hooks/useMyDrafts.ts` | Hook React : fetch sur montage, gère `loading / error / data`, renvoie max N brouillons triés par `updatedAt` (fallback `createdAt`) DESC |
| `frontend/src/utils/computeEventCompletion.ts` | Fonction pure `computeCompletion(event: Event): number` retournant un score 0–100 basé sur les champs présents |
| `frontend/src/utils/computeEventCompletion.test.ts` | Tests unitaires Vitest (pure function, trivial à tester) |
| `frontend/src/components/event/DraftsResumeStrip.tsx` | Composant principal — le "resume strip" horizontal au-dessus du form |
| `frontend/src/components/event/DraftsResumeStrip.test.tsx` | Tests composant (loading / empty / error / data / navigation au clic / a11y) |
| `frontend/src/components/event/DraftResumeCard.tsx` | Sous-composant — carte compacte d'un brouillon |
| `frontend/src/components/event/DraftCompletionRing.tsx` | Sous-composant SVG — anneau de progression 16px |
| `frontend/src/hooks/useMyDrafts.test.ts` | Tests hook (mock `getMyDrafts`) couvrant les 4 états |
| `frontend/src/bones/drafts-resume-strip.bones.json` | Skeleton boneyard — layout statique, JSON manuel (1 seul BP suffit) |

---

## Ce qui est à modifier

| Fichier | Modification |
|---|---|
| `frontend/src/pages/event/EventCreatePage.tsx` | Injecter `<DraftsResumeStrip />` entre `<SectionHeader>` et `<EventForm>` |
| `frontend/src/services/eventApi.ts` | Exporter `getMyDrafts(organizerId, limit)` (ne pas toucher à `getAll`) |
| `frontend/src/services/eventApi.test.ts` (ou équivalent si absent) | Ajouter un test stub Axios vérifiant que `getMyDrafts` envoie bien `?organizerId=X&status=DRAFT&size=N` |
| `frontend/src/bones/registry.js` | `import _drafts_resume_strip from './drafts-resume-strip.bones.json'` + `registerBones({ ..., "drafts-resume-strip": _drafts_resume_strip })` |
| `frontend/docs/components.md` | Section "Composants réutilisables" — ajouter `DraftsResumeStrip`, `DraftResumeCard`, `DraftCompletionRing`. Section "Hooks" — ajouter `useMyDrafts`. Section "Skeleton screens" — ajouter `drafts-resume-strip` dans la table |
| `frontend/docs/sprint-context.md` | Nouvelle entrée Sprint 4 : "Correctif UX reprise des brouillons (2026-04-xx)" avec liste des fichiers livrés |
| `AGENTS.md` | Table "Skeletons existants" — ajouter la ligne `drafts-resume-strip` |

---

## Flux utilisateur complet

1. L'utilisateur, connecté, navigue vers `/events/new` (clic sur "Créer un événement" depuis la navbar, ou URL directe).
2. `EventCreatePage` se monte ; `PrivateRoute` a déjà garanti `useAuth().isAuthenticated === true`.
3. `DraftsResumeStrip` monte en parallèle du formulaire. Il appelle `useMyDrafts(user.id)` qui déclenche `getMyDrafts(user.id, 5)` → `GET /api/events?organizerId={user.id}&status=DRAFT&size=5`.
4. **Pendant le fetch** (attendu < 500 ms) : le strip affiche son skeleton `drafts-resume-strip` (1 barre fine + 3–5 chips fantômes). Le formulaire `EventForm` en dessous s'affiche normalement, immédiatement interactif — le skeleton du strip **ne bloque pas** la saisie.
5. **Cas A — au moins 1 brouillon** : le strip affiche une fine barre glassmorphism intitulée "Reprendre un brouillon" à gauche, suivie d'une rangée horizontale scrollable (max 5 cartes `DraftResumeCard`) triées par `updatedAt DESC`. Chaque carte affiche :
   - Titre du brouillon (ou "Brouillon sans titre" si vide)
   - Date de dernière modification en relatif français ("il y a 2 h", "hier", "il y a 3 jours")
   - Anneau de complétion à droite (0–100%)
   - Badge subtil "Date expirée" si `startDate < now()` (texte `text-error/70`, pas de fond agressif)
6. **Cas B — 0 brouillon** : le strip retourne `null`. Pas de pill d'astuce, pas de message, aucun footprint visuel. L'utilisateur n'a jamais sauvegardé → il ne se demande pas où sont ses brouillons, donc aucun rappel nécessaire. Cette décision est justifiée plus bas dans "Parti pris design".
7. **Cas C — erreur réseau** : le strip retourne `null` silencieusement. `CreateEventPage` ne doit jamais être bloquée par une panne du listing DRAFT — l'intention primaire reste **créer un nouvel événement**.
8. **Clic sur une carte** : navigation `useNavigate()` → `/events/{draft.id}/edit`.
9. `EventEditPage` se monte, `getById(draft.id)` retourne le DRAFT complet, `useEventForm({ mode: 'edit', initialEvent })` pré-remplit le formulaire, l'utilisateur complète et choisit "Publier" dans le select de statut → `PUT /events/{id}` avec `status=PUBLISHED`.
10. Fin du flux — le brouillon est publié, prochain `GET /events?organizerId=X&status=DRAFT` renverra une liste amputée d'un élément.

### Variante clavier / a11y

- Tab amène le focus sur le premier `DraftResumeCard` (élément `<button>`).
- Flèches gauche/droite → navigation inter-cartes (via `onKeyDown`).
- Entrée / Espace → déclenche la navigation vers `/events/:id/edit`.
- `aria-label` explicite sur chaque bouton : `"Reprendre le brouillon ‘Titre de l'event’, modifié il y a 2 heures, complété à 60%"`.
- Focus visible via `focus-visible:ring-2 focus-visible:ring-accent`.
- Scroll horizontal : si `prefers-reduced-motion: reduce` → `scroll-behavior: auto` ; sinon `smooth`.

---

## Contrat d'API — rien de nouveau

L'appel utilisé existe déjà.

### `GET /api/events`

**Query params utilisés par cette feature :**

| Param | Valeur envoyée | Source |
|---|---|---|
| `organizerId` | `user.id` (UUID backend, pas `auth0Id`) | `useAuth().user.id` |
| `status` | `DRAFT` | constante |
| `size` | `5` (constante `MAX_DRAFTS_IN_STRIP`) | constante locale à `useMyDrafts` |
| `page` | non envoyé (défaut backend `0`) | — |

**Exemple exact :**
```http
GET /api/events?organizerId=a1b2c3d4-e5f6-7890-abcd-ef1234567890&status=DRAFT&size=5
```

**Réponse attendue :** `200 OK` avec `List<EventDTO>` (voir [data-model.md](backend/docs/data-model.md) pour la structure complète). Peut être `[]` si l'utilisateur n'a aucun brouillon.

**Codes d'erreur pertinents :**
- `500` (ou timeout réseau) → traité comme "silencieux", le strip renvoie `null`.
- Pas de `401` attendu (endpoint `@PermitAll` — même si `CreateEventPage` est sous `PrivateRoute`, on n'exige pas l'auth sur l'appel).

**Tri côté backend vs côté front :** `EventService.getAll` trie actuellement par `startDate, id` ASC ([EventService.java:59](backend/src/main/java/ch/unige/events/service/EventService.java#L59)). Le front re-trie localement par `updatedAt DESC` (fallback `createdAt DESC`) dans `useMyDrafts` — c'est acceptable pour un `size=5` et évite toute modification backend.

---

## Parti pris design — "Resume Strip" horizontal compact

### Décision finale (non négociable)

Après arbitrage entre **carrousel**, **ruban/chips IDE-like**, **stack effect en perspective** et **bannière rétractable**, le choix retenu est un **"Resume Strip"** inspiré d'un dock d'IDE moderne, avec cette anatomie exacte :

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  ↻ Reprendre un brouillon    [carte][carte][carte][carte][carte] →          │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Hauteur totale** : `56 px` desktop, `52 px` mobile. Moins que la moitié d'un champ de formulaire.
- **Label fixe à gauche** : icône `RotateCcw` (lucide-react) + texte "Reprendre un brouillon" en `text-sm text-foreground/70`. Largeur fixe ~220 px desktop, masqué < `sm` (mobile = cartes seules avec label screen-reader).
- **Rail horizontal à droite** : `overflow-x-auto snap-x snap-mandatory`, scrollbar masquée (classe `scrollbar-hide` ou équivalent utility), `gap-3`. Jusqu'à 5 cartes visibles simultanément sur desktop ≥ `lg`.
- **Séparation visuelle** : strip entouré d'un `border border-border/60`, `rounded-2xl`, `backdrop-blur-xl bg-background/60`. Même vocabulaire glassmorphism que `Toast` et les cards d'événements.
- **Insertion** : entre `<SectionHeader>` et `<EventForm>` dans `EventCreatePage`, avec un `mt-6 mb-8` (aligné avec les autres gaps du `SectionWrapper`).

### Anatomie d'une `DraftResumeCard`

```
┌──────────────────────────────────┐
│  Titre du brouillon …        ◐  │
│  il y a 2 heures · Expirée      │
└──────────────────────────────────┘
```

- **Dimensions** : `w-64 h-10` (desktop), `w-56 h-10` (mobile). Snap-align `start`.
- **Ligne 1** : titre tronqué à 30 chars (`truncate`), classe `text-sm font-medium text-foreground`. Fallback `"Brouillon sans titre"` en `text-foreground/60 italic` si `title.trim() === ''`.
- **Ligne 2** : `text-xs text-foreground/50`. Format : `"il y a 2 h"`, avec suffixe `" · Expirée"` (en `text-error/70`) si `startDate < now()`.
- **À droite** : `DraftCompletionRing` 16×16 SVG, couleur `stroke-accent`, piste `stroke-border/40`.
- **Fond** : `bg-background/40 hover:bg-background/80 border border-border/50`. `rounded-xl`, `px-3`, `gap-2`.
- **Interactions** :
  - Hover desktop : légère élévation `hover:-translate-y-0.5 transition-transform duration-200`, glow accent subtil via `hover:ring-1 hover:ring-accent/30`.
  - `focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-background`.
  - `prefers-reduced-motion: reduce` → désactiver `translate`, `transition-none`.
- **Role / a11y** : élément `<button type="button">`, `aria-label` verbeux.

### État vide — tranche nette : `return null`

L'alternative "pill d'astuce" a été explicitement rejetée. Raisons :

1. Un utilisateur qui n'a jamais sauvegardé de brouillon **ne cherche rien** — lui injecter une pastille "💡 Sauvegardez en brouillon à tout moment" revient à ajouter du bruit pour un pattern qu'il découvrira naturellement au Bande 3 du formulaire (où le lien existe déjà).
2. Cela préserve 100% de la hauteur au-dessus du form pour l'utilisateur nominal (taux d'adoption brouillon initial = 0% pour tous les nouveaux users).
3. Cela évite la situation "bannière d'astuce permanente qu'on finit par ignorer" — syndrome des cookie banners.

**Règle :** si `drafts.length === 0` → `return null`. Point.

### État d'erreur — silencieux

`return null` également. L'intention primaire de `/events/new` est de créer un événement. Si le listing des brouillons tombe, on ne bloque pas, on ne montre pas de toast d'erreur, on ne montre pas de bannière rouge. Un `console.warn` dans `useMyDrafts` suffit pour le debug.

### Justification — pourquoi ce parti pris > alternatives

| Alternative | Raison du rejet |
|---|---|
| **Carrousel horizontal à cards pleines** (hauteur 140–160 px) | Trop de vertical real estate, pousse `EventForm` sous la ligne de flottaison desktop en 1080p. Un brouillon n'est pas un événement publié — il ne mérite pas l'équivalent d'une `EventCard`. |
| **Ruban chips façon onglets IDE** (24–32 px) | Trop compact pour afficher à la fois titre, date et complétion. Finit en "listé de vignettes illisibles" sur mobile. |
| **Stack effect cartes empilées en perspective** (CSS 3D) | Joli à la démo, difficile à rendre accessible (lecteurs d'écran, navigation clavier), complexe à implémenter sans glitch au resize. Effort / valeur défavorable. |
| **Bannière rétractable collapsed par défaut** | Ajoute un état d'interaction (clic pour déployer) pour consommer un élément déjà secondaire à la page. Un clic supplémentaire pour atteindre un brouillon existant = friction que la feature est censée supprimer. |
| **Resume Strip (retenu)** | 56 px fixes, non-rétractable, directement actionnable (1 clic). Glassmorphism cohérent avec le reste du site. Scrollable horizontalement sur mobile, donc scale naturellement de 0 à 5 cartes. Label fixe à gauche = ancrage visuel qui donne du sens même si on ne scrolle pas. |

Le Resume Strip fait **la moitié de la hauteur d'un champ de formulaire** — le form reste l'élément principal de la page, le strip est visuellement secondaire mais immédiatement exploitable.

### Micro-interactions

| Élément | Comportement | Respecte `prefers-reduced-motion` |
|---|---|---|
| Apparition du strip | `opacity-0 translate-y-1` → `opacity-100 translate-y-0`, `duration-300`, stagger 40ms par carte | Oui : `motion-safe:animate-*` / classes conditionnelles |
| Hover card | `-translate-y-0.5` + `ring-1 ring-accent/30` | Oui : désactivé en reduce |
| Scroll horizontal | `scroll-smooth` | Oui : `scroll-auto` en reduce |
| Chargement du skeleton | `animate="pulse"` (boneyard) | Géré nativement par boneyard |

---

## Découpage frontend fichier par fichier

### 1. `frontend/src/utils/computeEventCompletion.ts`

**Rôle :** fonction pure, stateless, testable isolément.

**Signature :**
```ts
import type { Event } from '@/types/event'

export function computeEventCompletion(event: Event): number
```

**Logique :** score 0–100 sur 7 champs pondérés, pour refléter "à quel point ce brouillon est prêt à être publié".

| Champ | Présence → poids | Poids |
|---|---|---|
| `title` non vide (après trim) | +20 | 20 |
| `description` non vide et `length >= 40` | +15 | 15 |
| `location` non vide | +15 | 15 |
| `startDate` défini | +10 | 10 |
| `endDate` défini | +10 | 10 |
| `category` défini | +15 | 15 |
| `bannerUrl` défini | +15 | 15 |

Total : 100. Arrondir à l'entier. Clamper `[0, 100]`.

**Pourquoi ces poids :** `title` et `description` portent la valeur éditoriale la plus lourde. `bannerUrl` a volontairement 15 plutôt que 5 pour refléter l'effort d'upload (le cropper arrive au Sprint 6). `category` est obligatoire à la publication donc compte comme "pas de publication sans catégorie".

**Pas de TypeScript `any`**, pas d'import d'entités externes, zéro effet de bord.

### 2. `frontend/src/utils/computeEventCompletion.test.ts`

Tests Vitest :
- Event vide (juste les required minima) → score bas
- Event complet sur tous les champs → 100
- Description 30 chars → pas les +15
- Description 100 chars → +15
- `title = "   "` (espaces seulement) → pas les +20
- `bannerUrl: undefined` → pas les +15

### 3. `frontend/src/services/eventApi.ts` — ajout du helper

**Ajouter** (sans modifier `getAll`) :

```ts
const MAX_DRAFTS_FETCH = 5

export async function getMyDrafts(
  organizerId: string,
  limit: number = MAX_DRAFTS_FETCH
): Promise<Event[]> {
  return getAll({
    organizerId,
    status: 'DRAFT',
    size: limit,
  })
}
```

Ne pas exporter `MAX_DRAFTS_FETCH` (constante locale — la limite d'affichage vit côté hook/composant).

### 4. `frontend/src/hooks/useMyDrafts.ts`

**Signature :**
```ts
export interface UseMyDraftsResult {
  drafts: Event[]
  loading: boolean
  error: string | null
}

export function useMyDrafts(organizerId: string | undefined): UseMyDraftsResult
```

**Comportement :**
- Si `organizerId` est `undefined` (user pas encore chargé) → renvoyer `{ drafts: [], loading: true, error: null }`.
- Au montage et à chaque changement de `organizerId` : appelle `getMyDrafts(organizerId, 5)`.
- Tri local par `updatedAt ?? createdAt` décroissant.
- Catch : stocke `error = 'Erreur de chargement des brouillons'`, `loading = false`, `drafts = []`. Log console.warn (pas console.error — on ne veut pas polluer Sentry avec un listing optionnel).
- Pas de refetch automatique, pas de polling, pas d'invalidation — on fait simple, le prochain montage suffit.

**Pas d'abort controller** (le `useEffect` se fait ignorer via un flag `cancelled` local, pattern standard du projet).

### 5. `frontend/src/hooks/useMyDrafts.test.ts`

Tests Vitest avec mock de `getMyDrafts` via `vi.mock('@/services/eventApi', ...)` :

| # | Scénario | Assertion |
|---|---|---|
| 1 | `organizerId = undefined` | `loading: true`, pas d'appel API |
| 2 | fetch réussi avec 3 drafts | `drafts.length === 3`, `loading: false`, `error: null` |
| 3 | fetch réussi, drafts triés par `updatedAt DESC` | ordre correct même si API renvoie ASC |
| 4 | fetch réussi, `updatedAt` absent → fallback `createdAt` | pas de crash, ordre cohérent |
| 5 | fetch échoue (rejet de la promesse) | `error: 'Erreur de chargement des brouillons'`, `drafts: []`, `loading: false` |
| 6 | unmount pendant fetch | pas de `setState` sur composant démonté (pas de warning React) |

### 6. `frontend/src/components/event/DraftCompletionRing.tsx`

**Props :**
```ts
interface DraftCompletionRingProps {
  completion: number // 0..100
  size?: number // default 16
}
```

**Implémentation :** SVG pur. Un cercle de fond (stroke `stroke-border/40`, stroke-width 2), un cercle d'avant-plan avec `stroke-dasharray` calculé (classe `stroke-accent`). Pas de label texte à l'intérieur (16 px trop petit). `aria-hidden` — la complétion est déjà annoncée dans l'`aria-label` du bouton parent.

**Pas de style inline** sauf `strokeDasharray` et `strokeDashoffset` qui sont dynamiques et ne peuvent pas être Tailwind.

### 7. `frontend/src/components/event/DraftResumeCard.tsx`

**Props :**
```ts
interface DraftResumeCardProps {
  draft: Event
  onOpen: (id: number) => void
}
```

**Dérivations locales :**
- `completion = computeEventCompletion(draft)`
- `relativeTime = formatRelative(draft.updatedAt ?? draft.createdAt)` — utiliser l'utilitaire d'affichage français déjà utilisé dans le projet si présent (grep `formatRelative` / `timeAgo` avant d'en créer un nouveau) ; sinon créer un petit helper local dans `src/utils/formatRelativeTime.ts`
- `isExpired = new Date(draft.startDate) < new Date()`
- `title = draft.title.trim() || 'Brouillon sans titre'`
- `ariaLabel = \`Reprendre le brouillon « ${title} », modifié ${relativeTime}, complété à ${completion}%${isExpired ? ', date expirée' : ''}\``

**JSX (pseudo) :**
```tsx
<button
  type="button"
  onClick={() => onOpen(draft.id)}
  aria-label={ariaLabel}
  className={cardClass(isExpired)}
>
  <div className="min-w-0 flex-1">
    <p className="truncate text-sm font-medium text-foreground">{title}</p>
    <p className="text-xs text-foreground/50">
      {relativeTime}
      {isExpired && <span className="text-error/70"> · Expirée</span>}
    </p>
  </div>
  <DraftCompletionRing completion={completion} />
</button>
```

**Const map typée pour les variantes** (pas de ternaire inline) :
```ts
const cardVariants = {
  default: 'border-border/50 bg-background/40 hover:bg-background/80',
  expired: 'border-error/30 bg-background/40 hover:bg-background/80',
} as const

function cardClass(expired: boolean) {
  return `snap-start shrink-0 w-64 h-10 rounded-xl border px-3 flex items-center gap-2 transition-all duration-200 motion-safe:hover:-translate-y-0.5 focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-background ${cardVariants[expired ? 'expired' : 'default']}`
}
```

### 8. `frontend/src/components/event/DraftsResumeStrip.tsx`

**Props :** aucune — entièrement autonome.

**Structure :**
```tsx
export default function DraftsResumeStrip() {
  const { user } = useAuth()
  const { drafts, loading, error } = useMyDrafts(user?.id)
  const { theme } = useTheme()
  const navigate = useNavigate()

  if (loading) {
    return (
      <Skeleton
        name="drafts-resume-strip"
        loading
        animate="pulse"
        color={theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'}
      >
        <DraftsResumeStripFixture />
      </Skeleton>
    )
  }

  if (error || drafts.length === 0) return null

  return (
    <section
      aria-label="Reprendre un brouillon"
      className="mt-6 mb-8 flex h-14 items-center gap-4 rounded-2xl border border-border/60 bg-background/60 px-4 backdrop-blur-xl motion-safe:animate-fade-in"
    >
      <div className="hidden sm:flex items-center gap-2 shrink-0 text-sm text-foreground/70 w-[220px]">
        <RotateCcw className="size-4" aria-hidden />
        <span>Reprendre un brouillon</span>
      </div>
      <div
        className="flex-1 flex items-center gap-3 overflow-x-auto scrollbar-hide snap-x snap-mandatory motion-safe:scroll-smooth"
        onKeyDown={handleArrowNav}
      >
        {drafts.map(draft => (
          <DraftResumeCard
            key={draft.id}
            draft={draft}
            onOpen={id => navigate(`/events/${id}/edit`)}
          />
        ))}
      </div>
    </section>
  )
}

// Non-exporté, utilisé UNIQUEMENT pour donner des dimensions au skeleton
function DraftsResumeStripFixture() {
  return (
    <div className="mt-6 mb-8 h-14 rounded-2xl flex items-center gap-4 px-4">
      <div className="hidden sm:block h-5 w-[220px] shrink-0" />
      <div className="flex-1 flex gap-3">
        <div className="w-64 h-10 rounded-xl" />
        <div className="w-64 h-10 rounded-xl" />
        <div className="w-64 h-10 rounded-xl" />
      </div>
    </div>
  )
}
```

**Navigation clavier (`handleArrowNav`)** : gère `ArrowLeft` / `ArrowRight` pour déplacer le focus entre les `<button>` enfants. Implémentation : trouver l'élément `document.activeElement`, chercher son sibling, `.focus()`.

### 9. `frontend/src/components/event/DraftsResumeStrip.test.tsx`

Tests React Testing Library :

| # | Scénario | Assertion |
|---|---|---|
| 1 | `loading = true` | Skeleton rendu (data-testid ou role busy) |
| 2 | `drafts = []` | `container.firstChild === null` (return null) |
| 3 | `error = '...'` | Idem, `null` rendu |
| 4 | `drafts = [d1, d2]` | 2 cartes rendues, chaque carte contient le titre |
| 5 | Clic sur une carte | `navigate('/events/42/edit')` appelé |
| 6 | `aria-label` présent sur chaque carte | vérifier contenu |
| 7 | `ArrowRight` sur la première carte focused | focus passe à la seconde |
| 8 | Draft sans `title` | "Brouillon sans titre" affiché |
| 9 | Draft expiré | badge "Expirée" présent |
| 10 | Draft sans `updatedAt` | fallback `createdAt` utilisé, pas de crash |

Mock `useMyDrafts`, `useAuth`, `useNavigate`, `useTheme` via `vi.mock`.

### 10. `frontend/src/pages/event/EventCreatePage.tsx` — intégration

Seule modification : insérer le composant. L'analyse a confirmé la structure actuelle :

```tsx
<SectionWrapper padding="sm" size="lg" ...>
  <SectionHeader ... />
  <DraftsResumeStrip />           {/* ← INJECTION ICI */}
  <EventForm ... onSaveDraft={form.triggerDraftSave} />
</SectionWrapper>
```

Aucune autre modification — pas de prop, pas de context, pas de routing.

---

## Types TypeScript

Aucun nouveau type d'entité nécessaire — `Event` existe et contient tous les champs requis ([types.md](frontend/docs/types.md#L30)).

Types utilitaires locaux uniquement :
- `UseMyDraftsResult` (hook — interne à `useMyDrafts.ts`)
- `DraftResumeCardProps`, `DraftCompletionRingProps` (props — internes aux composants)

**Vérifier avant commit** : `Event.updatedAt` est bien `string | undefined` dans `frontend/src/types/event.ts` — sinon ajuster le fallback `updatedAt ?? createdAt` dans `useMyDrafts`.

---

## Skeleton — `drafts-resume-strip.bones.json`

Le strip est affiché pendant la latence réseau (≤ 500 ms typique mais potentiellement jusqu'à 2 s sur un réseau dégradé). **Un skeleton est requis** parce que :

1. Le strip occupe une zone visible au-dessus du form (`h-14` = 56 px).
2. Sans skeleton, on aurait soit un flash de contenu vide, soit un CLS (cumulative layout shift) quand les cartes arrivent.
3. La règle du projet [skeleton/README.md](frontend/skeleton/README.md) est explicite : "Toute page ou composant qui effectue un appel API et affiche un état `loading` doit avoir un skeleton `.bones.json`".

### Décisions techniques

- **Méthode** : JSON manuel. Layout fixe sans grille dynamique, 1 seul breakpoint suffit (on n'adapte pas la largeur interne au container — les cartes ont `w-64` fixe et débordent en scroll horizontal).
- **Breakpoints** : un seul à `320` (tous les containers ≥ 320 px utilisent la même structure, le débord horizontal est natif).
- **Hauteur `bones.height`** : **56** (= `h-14` Tailwind, doit correspondre exactement à la hauteur du fixture).

### Structure des bones

```
Layout :
┌────────────────────────────────────────────────────────┐
│ [label line — caché en mobile, mais bone visible] [card][card][card] │
└────────────────────────────────────────────────────────┘
```

Bones (positions approximatives pour un container de 320 px) :
1. **Container** `[0, 0, 100, 56, 16, true]` — surface du strip (bg lighter)
2. **Label line** `[5, 20, 35, 16, 4]` — ligne de texte du label gauche
3. **Card 1** `[44, 12, 25, 32, 8, true]` — première carte (container)
4. **Card 2** `[71, 12, 25, 32, 8, true]` — deuxième carte (container)
5. **Card 3 peek** `[97, 12, 15, 32, 8, true]` — carte qui dépasse à droite (effet "il y en a plus")

Ajuster les valeurs lors de l'implémentation selon la largeur mesurée du container au runtime (utiliser l'inspecteur boneyard en dev).

### Fichier final attendu

```jsonc
{
  "breakpoints": {
    "320": {
      "name": "drafts-resume-strip",
      "viewportWidth": 320,
      "width": 320,
      "height": 56,
      "bones": [
        [0, 0, 100, 56, 16, true],
        [5, 20, 35, 16, 4],
        [44, 12, 25, 32, 8, true],
        [71, 12, 25, 32, 8, true],
        [97, 12, 15, 32, 8, true]
      ]
    }
  }
}
```

### Registry

```js
// frontend/src/bones/registry.js
import _drafts_resume_strip from './drafts-resume-strip.bones.json'

registerBones({
  // ... existants
  "drafts-resume-strip": _drafts_resume_strip,
})
```

### Fixture

Déjà décrit dans `DraftsResumeStrip.tsx` plus haut (`DraftsResumeStripFixture`). Il reproduit fidèlement `mt-6 mb-8 h-14`, le label 220 px et 3 cartes `w-64 h-10`. **Obligatoire** sinon `bones.height = 0` et skeleton invisible.

---

## Tests

### Couverture globale visée

≥ 80% sur les nouveaux fichiers (seuil SonarCloud du projet).

### Matrice de tests

| Fichier testé | Fichier de test | Nb cas minimum |
|---|---|---|
| `computeEventCompletion.ts` | `computeEventCompletion.test.ts` | 6 |
| `eventApi.ts` (nouveau helper `getMyDrafts`) | extension de test Axios existant ou nouveau `eventApi.test.ts` | 2 (params envoyés OK, erreur propagée) |
| `useMyDrafts.ts` | `useMyDrafts.test.ts` | 6 |
| `DraftsResumeStrip.tsx` | `DraftsResumeStrip.test.tsx` | 10 |
| `DraftResumeCard.tsx` | `DraftResumeCard.test.tsx` (ou couvert via `DraftsResumeStrip.test.tsx`) | 4 |
| `DraftCompletionRing.tsx` | couvert indirectement via les tests du parent | 0 dédiés |

### Points d'attention tests

- **Mocking Auth** : `vi.mock('@/hooks/useAuth', () => ({ useAuth: () => ({ user: { id: 'uuid-test' } }) }))`.
- **Mocking Theme** : `vi.mock('@/contexts/ThemeContext', () => ({ useTheme: () => ({ theme: 'dark' }) }))`.
- **Mocking navigate** : `vi.mock('react-router-dom', ...)`, garder les vrais `BrowserRouter`/etc. via `vi.importActual`.
- **Prefers-reduced-motion** : tester que le composant rend (on ne teste pas visuellement l'animation, juste que les classes `motion-safe:*` sont présentes).
- **A11y** : `axe-core` via `@axe-core/react` si déjà présent ; sinon, tests manuels type "aria-label présent, role=button, focus visible".

---

## Edge cases à traiter explicitement

| Cas | Comportement attendu |
|---|---|
| Utilisateur non authentifié (impossible en théorie grâce à `PrivateRoute`, mais sécurité en profondeur) | `user?.id === undefined` → hook reste en `loading: true` sans fetch ; au render `loading` rend le skeleton ; ce n'est pas bloquant car `PrivateRoute` redirige avant |
| 0 brouillon | `return null`, pas de footprint |
| 1 brouillon | 1 carte affichée, scroll natif inerte |
| 5 brouillons exactement | 5 cartes, pas de débord sauf si < `lg` |
| > 5 brouillons (backend renvoie max 5 car `size=5`) | 5 cartes affichées, les autres sont inaccessibles jusqu'à SCRUM-93. **Ne PAS afficher** un lien "voir tous" — il pointerait vers `/my-events` qui n'existe pas encore |
| Brouillon avec `startDate` passée (peut arriver si le brouillon dort depuis longtemps — `@Future` s'applique à la création, pas au DRAFT vieillissant) | Badge "Expirée" dans la ligne 2, classe `text-error/70`. Pas de tri spécial — on trie toujours par `updatedAt DESC` |
| Brouillon avec `title` vide / whitespace | Affichage "Brouillon sans titre" en italique atténué |
| Brouillon avec `description` volumineuse | Non affichée dans le strip, aucun impact |
| Erreur réseau | `return null` silencieux, `console.warn` |
| Erreur 401 (théorique — endpoint est `@PermitAll`) | Traitée comme n'importe quelle erreur : `return null` |
| `updatedAt` absent (champ optionnel dans le type TS) | Fallback sur `createdAt` pour le tri et l'affichage relatif |
| `createdAt` lui-même absent (impossible car requis backend) | TypeScript l'empêche, pas de test dédié |
| Scroll horizontal bloque le scroll vertical sur mobile | Utiliser `overscroll-behavior-x: contain` sur le rail pour éviter de "voler" le scroll vertical de la page |
| User a un très long displayName dans Auth0 | Non concerné (on n'affiche pas le nom d'user) |
| Plusieurs instances de `DraftsResumeStrip` sur la même page | Impossible par design — une seule insertion dans `EventCreatePage` |

---

## Conventions du projet — rappel avant commit

- [ ] camelCase partout — aucun `is_profile_public`, aucun `draft_id`
- [ ] Pas de booléen préfixé `is` dans les types métiers (on utilise `expired` pas `isExpired` dans les variables locales — mais les props React type `expired: boolean` sont OK car ce n'est pas une entité JPA)
- [ ] Pas de `any` TypeScript
- [ ] Pas de styles inline sauf `strokeDasharray`/`strokeDashoffset` (dynamiques SVG)
- [ ] Design tokens Tailwind uniquement : `bg-background`, `text-foreground`, `border-border`, `text-accent`, `text-error` — jamais `text-red-500`, jamais `bg-gray-200`
- [ ] Const maps typées pour les variantes (`cardVariants`) — zéro ternaire inline
- [ ] Classes `motion-safe:*` pour toutes les animations — respecte `prefers-reduced-motion`
- [ ] `focus-visible:ring-*` sur tous les éléments interactifs
- [ ] `aria-label` sur chaque bouton (pas juste un tooltip)
- [ ] Imports absolus via `@/...` si c'est la convention du repo, sinon relatifs cohérents avec le voisinage
- [ ] Pas d'import circulaire (`eventApi` ne doit pas importer de hook)
- [ ] Pas de commentaire WHAT — uniquement WHY non-obvious

---

## Mise à jour de la documentation

### `frontend/docs/components.md`

**Section "Composants réutilisables" — ajouter** (entre FavoriteButton et EventCards, respectant l'ordre alphabétique/logique) :

```markdown
### DraftsResumeStrip

- Bandeau horizontal compact (56 px) au-dessus de `EventForm` sur `CreateEventPage`.
- Affiche jusqu'à 5 brouillons récents de l'utilisateur connecté, triés par `updatedAt` DESC.
- Retourne `null` si aucun brouillon ou en cas d'erreur réseau (pas de blocage de la page).
- Clic sur une carte → navigation vers `/events/:id/edit` pour reprendre la saisie.
- Utilise `useMyDrafts()` pour le fetch et la gestion d'état.
- Skeleton `drafts-resume-strip` pendant le chargement.
- A11y : navigation clavier Tab + flèches, `aria-label` verbeux sur chaque carte.

### DraftResumeCard

- Carte compacte (`w-64 h-10`) d'un brouillon, sous-composant de `DraftsResumeStrip`.
- Affiche titre tronqué, date relative, badge "Expirée" si `startDate < now()`, anneau de complétion.
- Props : `draft: Event`, `onOpen: (id: number) => void`.

### DraftCompletionRing

- Anneau SVG 16×16 affichant un pourcentage de complétion (0–100).
- Utilisé dans `DraftResumeCard`.
- Props : `completion: number`, `size?: number`.
```

**Section "Hooks" — ajouter** :

```markdown
### useMyDrafts

- Charge les brouillons de l'utilisateur authentifié via `GET /api/events?organizerId=X&status=DRAFT&size=5`.
- Params : `organizerId: string | undefined`.
- Retourne `{ drafts: Event[], loading: boolean, error: string | null }`.
- Tri local par `updatedAt DESC` (fallback `createdAt`).
- Erreur réseau → `error` rempli, `drafts = []`, `loading = false` (pas de retry).
```

**Section "Skeleton screens" — ajouter dans la table** :

```markdown
| `drafts-resume-strip` | `drafts-resume-strip.bones.json` | `DraftsResumeStrip` | manuel |
```

### `frontend/docs/sprint-context.md`

**Ajouter** sous Sprint 4 :

```markdown
## Sprint 4 — Correctif UX reprise des brouillons (2026-04-XX)

Terminé le 2026-04-XX.

Fonctionnalités livrées :
- `DraftsResumeStrip` : bandeau compact de reprise des brouillons affiché en haut de `CreateEventPage`.
- `useMyDrafts` : hook de chargement des brouillons de l'utilisateur via `GET /api/events?organizerId=X&status=DRAFT`.
- `DraftResumeCard`, `DraftCompletionRing` : sous-composants visuels.
- `computeEventCompletion` : utilitaire de calcul du % de complétion d'un événement brouillon.
- `getMyDrafts` : helper typé dans `eventApi.ts` (ne modifie pas `getAll`).
- Skeleton `drafts-resume-strip` pour l'état de chargement.
- Décision architecturale : stockage en base de données (pas en localStorage) — documenté dans `specs_archives/specs_claude/specs_drafts_recovery.md`.
- Aucun nouveau endpoint backend — consomme le filtre existant.
```

### `AGENTS.md`

Dans la table "Skeletons existants" :

```markdown
| `drafts-resume-strip` | `drafts-resume-strip.bones.json` | `DraftsResumeStrip` | manuel |
```

---

## Fichiers touchés — vue d'ensemble

### Créés (11)
- `frontend/src/utils/computeEventCompletion.ts`
- `frontend/src/utils/computeEventCompletion.test.ts`
- `frontend/src/hooks/useMyDrafts.ts`
- `frontend/src/hooks/useMyDrafts.test.ts`
- `frontend/src/components/event/DraftsResumeStrip.tsx`
- `frontend/src/components/event/DraftsResumeStrip.test.tsx`
- `frontend/src/components/event/DraftResumeCard.tsx`
- `frontend/src/components/event/DraftResumeCard.test.tsx` (optionnel si couvert via parent)
- `frontend/src/components/event/DraftCompletionRing.tsx`
- `frontend/src/bones/drafts-resume-strip.bones.json`
- `frontend/src/utils/formatRelativeTime.ts` (créer uniquement si aucun utilitaire équivalent n'existe déjà dans le repo — grep avant)

### Modifiés (6)
- `frontend/src/services/eventApi.ts` (ajout `getMyDrafts`)
- `frontend/src/services/eventApi.test.ts` (ou équivalent — ajout tests helper)
- `frontend/src/pages/event/EventCreatePage.tsx` (injection du strip)
- `frontend/src/bones/registry.js` (enregistrement skeleton)
- `frontend/docs/components.md` (ajout des 3 composants, 1 hook, 1 skeleton)
- `frontend/docs/sprint-context.md` (entrée Sprint 4 correctif)
- `AGENTS.md` (table skeletons)

### Inchangés malgré la proximité
- `frontend/src/hooks/useEventForm.ts` — on ne touche pas
- `frontend/src/pages/event/EventEditPage.tsx` — on ne touche pas
- `frontend/src/components/event/EventForm.tsx` — on ne touche pas
- `backend/**` — **rien** ne bouge

---

## Critères d'acceptation

### Fonctionnels
- [ ] Sur `/events/new`, si l'utilisateur connecté n'a aucun brouillon, aucun bandeau ne s'affiche.
- [ ] Sur `/events/new`, si l'utilisateur a ≥ 1 brouillon, un bandeau compact (56 px) apparaît entre le header de page et `EventForm`.
- [ ] Le bandeau affiche maximum 5 brouillons, triés du plus récemment modifié au plus ancien.
- [ ] Chaque carte affiche titre (ou fallback), date relative, anneau de complétion, badge "Expirée" si applicable.
- [ ] Clic sur une carte → navigation vers `/events/:id/edit`.
- [ ] Sur `/events/:id/edit`, le formulaire est pré-rempli avec les champs du brouillon.
- [ ] L'utilisateur peut modifier le brouillon et le publier via le select de statut existant (flux inchangé).
- [ ] Après publication, un retour sur `/events/new` montre une carte en moins dans le bandeau.
- [ ] Pendant le chargement, le skeleton `drafts-resume-strip` est visible (pas de flash vide, pas de CLS).
- [ ] En cas d'erreur réseau, `CreateEventPage` reste pleinement fonctionnelle (le strip disparaît silencieusement).

### Design / Cohérence
- [ ] Le strip utilise les design tokens `bg-background/60`, `border-border/60`, `text-foreground/70`, `text-accent` — aucune valeur Tailwind brute.
- [ ] Glassmorphism visible (`backdrop-blur-xl`).
- [ ] Hauteur ≤ 60 px — ne pousse pas `EventForm` sous la ligne de flottaison en desktop 1080p.
- [ ] Scroll horizontal fluide sur mobile, sans voler le scroll vertical.

### A11y
- [ ] Navigation clavier Tab + flèches gauche/droite opérationnelle.
- [ ] `focus-visible:ring-2 focus-visible:ring-accent` sur chaque carte.
- [ ] `aria-label` sur chaque bouton, contenu verbeux incluant titre + date relative + % complétion + statut expiré.
- [ ] `prefers-reduced-motion: reduce` supprime les transitions `translate` et `scroll-smooth`.

### Qualité code
- [ ] ESLint + TypeScript : 0 erreur.
- [ ] Couverture tests ≥ 80% sur les nouveaux fichiers.
- [ ] Aucun `any`.
- [ ] Aucun style inline sauf SVG dynamique.
- [ ] Aucun commentaire WHAT, commentaires WHY uniquement si non-évident.
- [ ] `npm run lint && npm run test` passe.
- [ ] `npm run build` passe.

### Documentation
- [ ] `frontend/docs/components.md` mis à jour.
- [ ] `frontend/docs/sprint-context.md` mis à jour.
- [ ] `AGENTS.md` table skeletons mise à jour.

---

## Checklist d'implémentation (ordre chronologique)

1. [ ] Lire `AGENTS.md`, `frontend/docs/` et cette spec en entier.
2. [ ] Grep `formatRelative`, `timeAgo`, `dateFns` dans `frontend/src/` pour réutiliser un utilitaire existant avant d'en créer un nouveau.
3. [ ] Créer `computeEventCompletion.ts` + tests — green.
4. [ ] Créer `formatRelativeTime.ts` si nécessaire + tests — green.
5. [ ] Étendre `eventApi.ts` avec `getMyDrafts` + tests.
6. [ ] Créer `useMyDrafts.ts` + tests — green.
7. [ ] Créer `DraftCompletionRing.tsx` (pur visuel, pas de test dédié).
8. [ ] Créer `DraftResumeCard.tsx` (pur visuel + clic).
9. [ ] Écrire le fixture `DraftsResumeStripFixture` et mesurer sa hauteur réelle au runtime (devrait être 56 px).
10. [ ] Créer `drafts-resume-strip.bones.json` avec `height: 56` et les 5 bones.
11. [ ] Enregistrer dans `registry.js`.
12. [ ] Créer `DraftsResumeStrip.tsx` avec le bloc `if (loading) return <Skeleton>`.
13. [ ] Injecter `<DraftsResumeStrip />` dans `EventCreatePage.tsx`.
14. [ ] Lancer `npm run dev` — vérifier les 4 états :
    - a) 0 brouillon → rien ne s'affiche
    - b) 1–5 brouillons → strip visible avec cartes
    - c) Throttle réseau Slow 3G → skeleton visible ~500 ms puis strip
    - d) Backend down (couper le proxy) → strip disparaît, page reste fonctionnelle
15. [ ] Écrire `DraftsResumeStrip.test.tsx` — green.
16. [ ] Vérifier a11y clavier manuellement : Tab → première carte, flèches pour se déplacer, Entrée pour naviguer.
17. [ ] Activer "Reduce motion" dans OS et vérifier que les hover/scroll ne bougent plus.
18. [ ] `npm run lint && npm run test && npm run build` — tout vert.
19. [ ] Mettre à jour `frontend/docs/components.md`, `frontend/docs/sprint-context.md`, `AGENTS.md`.
20. [ ] Commit + push + PR.

---

## Notes annexes

### Pourquoi `limit = 5` et pas `limit = 10`

Au-delà de 5 cartes `w-64` sur un écran 1280 px, le débord horizontal devient inévitable même sans label — et un rail scrollable "qui semble déjà rempli" signale visuellement "il y en a plus". 5 est aussi le sweet spot ergonomique pour un choix rapide sans over-scroll. Le jour où SCRUM-93 arrive, il prendra le relais pour les cas à 10+ brouillons.

### Pourquoi pas de bouton "voir tous les brouillons"

Parce que la cible (`/my-events`) n'existe pas encore. Afficher un lien mort ou un bouton grisé est pire que ne rien afficher : ça suscite une attente qu'on ne peut pas satisfaire, et il faudra enlever ce lien pile au moment où la page arrivera, créant du travail inverse. Au Sprint 5, SCRUM-93 ajoutera naturellement ce lien (et potentiellement un `+N autres` explicite).

### Pourquoi la complétion est calculée côté front

Parce que le backend n'expose pas de "completion score" et n'a aucune raison de le faire (c'est une métrique d'affichage, pas un concept métier). Calculer côté front à partir des champs déjà retournés par `EventDTO` est trivial (~20 lignes) et zéro coût réseau supplémentaire. Si un jour la complétion devient une métrique persistée (ex. "alerte si > 7 jours inactif et complétion < 30%"), ce sera un ticket séparé.

### Interaction avec SCRUM-125 (allDay) et SCRUM-126 (websiteUrl, contactEmail, tags)

Ces tâches du Sprint 5 ajouteront des champs optionnels à `Event`. `computeEventCompletion` utilise **uniquement les champs présents à la date de cette spec** — ces ajouts futurs n'ont pas à modifier le calcul tant que les nouveaux champs restent optionnels. Si un jour ils deviennent obligatoires, il faudra mettre à jour `computeEventCompletion` (et ses tests) — c'est une responsabilité du ticket qui rendra le champ obligatoire, pas de celui-ci.

---

## Prompt de lancement d'implémentation

Copier-coller intégralement le bloc ci-dessous dans un nouvel agent pour déclencher l'implémentation à partir de cette spec.

````
Tu vas implémenter la feature "Reprise des brouillons depuis CreateEventPage" du projet UNIGE Events.

## Source unique de vérité
La spec complète est dans `specs_archives/specs_claude/specs_drafts_recovery.md`. Lis-la en entier avant toute action — elle contient le QUOI, le POURQUOI, le découpage fichier par fichier, les décisions de design déjà prises (resume strip compact 56 px, retour null si 0 brouillon, stockage en base de données — pas en localStorage), les edge cases, les tests attendus et les critères d'acceptation.

## Lecture obligatoire avant de coder
1. `AGENTS.md` — conventions critiques du projet
2. `backend/docs/` en entier — pour comprendre le contrat API et confirmer qu'aucun endpoint nouveau n'est nécessaire
3. `frontend/docs/` en entier — architecture, composants existants, hooks, skeletons, conventions
4. `frontend/skeleton/README.md` — format des bones et workflow skeleton
5. La spec `specs_archives/specs_claude/specs_drafts_recovery.md` dans son intégralité

## Étapes d'implémentation dans l'ordre

1. **Vérifications préalables**
   - Grep `formatRelative`, `timeAgo`, `dateFns` dans `frontend/src/` pour détecter un utilitaire existant avant de créer `formatRelativeTime.ts`.
   - Confirmer que `eventApi.getAll()` accepte bien `status` et `organizerId` dans `EventsParams` — ne pas modifier cette signature.
   - Confirmer que `useAuth()` expose `user.id` (UUID backend, pas `auth0Id`).
   - Confirmer que `EventEditPage` + `useEventForm(mode='edit')` fonctionnent sur un event `status=DRAFT` sans aucun garde.

2. **Utilitaires (aucune dépendance React)**
   - Créer `frontend/src/utils/computeEventCompletion.ts` avec la pondération de la spec (7 champs, total 100).
   - Tests Vitest associés (≥ 6 cas). Green.
   - Créer `frontend/src/utils/formatRelativeTime.ts` uniquement si aucun utilitaire équivalent n'existe. Tests basiques.

3. **Service**
   - Étendre `frontend/src/services/eventApi.ts` avec `getMyDrafts(organizerId, limit=5)` — wrapper autour de `getAll`.
   - Tests Vitest : vérifier que les query params envoyés sont exactement `{organizerId, status: 'DRAFT', size: 5}`.

4. **Hook**
   - Créer `frontend/src/hooks/useMyDrafts.ts` — retourne `{drafts, loading, error}`.
   - Tri local par `updatedAt DESC` avec fallback `createdAt`.
   - Erreur réseau → `error` rempli + `console.warn`, pas de retry.
   - Tests Vitest couvrant les 6 scénarios de la spec (loading, success, sort, fallback, error, unmount safety).

5. **Skeleton — AVANT le composant**
   - Écrire le fixture `DraftsResumeStripFixture` (dans le même fichier que `DraftsResumeStrip.tsx`) en respectant `h-14` + structure label/rail/3 cartes `w-64 h-10`.
   - Créer `frontend/src/bones/drafts-resume-strip.bones.json` avec `height: 56` et les 5 bones de la spec. JSON manuel, 1 breakpoint (`320`).
   - Enregistrer dans `frontend/src/bones/registry.js`.

6. **Composants visuels**
   - `frontend/src/components/event/DraftCompletionRing.tsx` — SVG pur, props `completion: number`, `size?: number`. `aria-hidden`.
   - `frontend/src/components/event/DraftResumeCard.tsx` — `<button type="button">`, props `draft: Event`, `onOpen: (id: number) => void`. Const map typée pour variantes `default` / `expired`. `aria-label` verbeux. Pas de ternaire inline.
   - `frontend/src/components/event/DraftsResumeStrip.tsx` — composant autonome, utilise `useAuth` + `useMyDrafts` + `useNavigate` + `useTheme` (pour skeletonColor). Bloc `if (loading) return <Skeleton name="drafts-resume-strip">`. Bloc `if (error || drafts.length === 0) return null`. Navigation clavier flèches gauche/droite entre cartes.

7. **Intégration**
   - Dans `frontend/src/pages/event/EventCreatePage.tsx`, insérer `<DraftsResumeStrip />` entre `<SectionHeader>` et `<EventForm>`. Aucune autre modification du fichier.

8. **Tests composants**
   - `DraftsResumeStrip.test.tsx` : au moins 10 cas couvrant loading/empty/error/data/clic/a11y/clavier/fallback updatedAt/titre vide/badge expirée.
   - Mocker `useAuth`, `useMyDrafts`, `useNavigate`, `useTheme`.

9. **Vérifications manuelles (non skippables)**
   - `npm run dev` et tester les 4 états du strip dans un vrai navigateur :
     a) Utilisateur sans brouillon → rien ne s'affiche
     b) Utilisateur avec 1–5 brouillons → strip visible, clic fonctionne, navigation vers `/events/:id/edit` pré-remplit le form
     c) Network throttling "Slow 3G" → skeleton visible ~500 ms avant les cartes
     d) Backend coupé (stopper le container api) → strip disparaît silencieusement, formulaire reste pleinement utilisable
   - Tester au clavier : Tab amène sur la première carte, flèches déplacent le focus, Entrée navigue.
   - Activer "Reduce motion" dans l'OS et vérifier qu'aucune animation `translate` / `scroll-smooth` ne se produit.

10. **Commandes de qualité**
    - `npm run lint` — 0 erreur
    - `npm run test` — tous verts, couverture ≥ 80% sur les nouveaux fichiers
    - `npm run build` — pas d'erreur TS

11. **Documentation**
    - Mettre à jour `frontend/docs/components.md` — sections "Composants réutilisables" (DraftsResumeStrip, DraftResumeCard, DraftCompletionRing), "Hooks" (useMyDrafts), "Skeleton screens" (drafts-resume-strip).
    - Mettre à jour `frontend/docs/sprint-context.md` — nouvelle entrée Sprint 4 correctif UX.
    - Mettre à jour `AGENTS.md` — table "Skeletons existants" avec `drafts-resume-strip`.

## Interdits explicites — ne surtout PAS faire

- ❌ Créer la page `/my-events` ou `MyEventsPage.tsx` ou `useMyEvents.ts`. Ça viendra au Sprint 5 via SCRUM-93.
- ❌ Ajouter un lien "voir tous les brouillons" dans le strip. La cible n'existe pas encore — ne pas créer de promesse cassée.
- ❌ Modifier `useEventForm.ts`, `EventForm.tsx`, `EventEditPage.tsx`, `EventCreatePage.tsx` (au-delà de l'injection du strip), `eventApi.getAll` (juste ajouter `getMyDrafts` à côté).
- ❌ Créer, modifier ou étendre un endpoint backend. Aucun fichier `.java` ne doit être touché. Aucune modification de `backend/docs/openapi/openapi.yaml`.
- ❌ Stocker quoi que ce soit dans `localStorage` ou `sessionStorage`. Décision architecturale déjà tranchée dans la spec — base de données uniquement.
- ❌ Afficher une bannière d'astuce, un pill d'information ou une notification quand l'utilisateur n'a pas de brouillon. État vide = `return null`.
- ❌ Afficher un toast d'erreur quand le fetch échoue. Erreur = `return null` silencieux.
- ❌ Utiliser `LoadingSpinner` au lieu d'un skeleton pour l'état loading.
- ❌ Utiliser des valeurs Tailwind brutes comme `text-red-500`, `bg-gray-200`, `border-blue-400`. Design tokens uniquement.
- ❌ Utiliser des ternaires inline pour les variantes de classe. Const maps typées obligatoires.
- ❌ Utiliser `any` TypeScript.
- ❌ Ajouter des commentaires WHAT. Seuls les commentaires WHY non-évidents sont acceptés, et en une ligne max.

## Conventions du projet — rappel

- camelCase partout, pas de snake_case dans le code TS/JSX
- Pas de préfixe `is` sur les booléens d'entités (props React type `expired: boolean` OK)
- Design tokens Tailwind : `bg-background`, `text-foreground`, `border-border`, `text-accent`, `text-error`
- Classes `motion-safe:*` pour toutes les animations (hover, scroll, fade)
- `focus-visible:ring-2 focus-visible:ring-accent` sur tout élément interactif
- `aria-label` verbeux sur chaque bouton
- Tests ≥ 80% sur les nouveaux fichiers (seuil SonarCloud du projet)
- Hauteur fixe du strip ≤ 60 px — ne pas pousser `EventForm` sous la ligne de flottaison

## Critères de done

- [ ] `npm run lint` vert
- [ ] `npm run test` vert avec couverture ≥ 80% sur les nouveaux fichiers
- [ ] `npm run build` vert
- [ ] Preview manuelle validée dans les 4 états : 0 brouillon / 1–5 brouillons / loading throttlé / erreur réseau
- [ ] Navigation clavier validée (Tab + flèches + Entrée)
- [ ] `prefers-reduced-motion` respecté (vérifié OS)
- [ ] Aucun fichier backend touché
- [ ] `frontend/docs/components.md`, `frontend/docs/sprint-context.md`, `AGENTS.md` mis à jour
- [ ] Clic sur une carte → `/events/:id/edit` → formulaire pré-rempli → publication réussie → le brouillon disparaît du strip au retour sur `/events/new`

Si un point de la spec est ambigu pendant l'implémentation, relis la spec en priorité. Si la spec ne tranche pas, préfère toujours la solution la plus conservatrice et la plus cohérente avec les conventions du projet.
````
