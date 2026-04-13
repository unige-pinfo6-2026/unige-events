# SPEC — Rework visuel `/events/new` et `/events/:id/edit`

> Auteur : Claude (spec-driven development)  
> Date : 2026-04-11  
> Statut : **À implémenter**

---

## 1. Vue d'ensemble

### Objectif

Aligner les pages de création et d'édition d'événement sur le design system reworké du reste de l'application. Actuellement ces deux pages sont les seules à afficher un formulaire sans aucune mise en page héroïque : fond neutre, carte simple `bg-background border border-border`, titre `h1` minimaliste. Elles détonnent par rapport à CalendarPage, ProfilePage, EventDetailPage et EventsSearchPage qui ont toutes été revampées.

### Périmètre

| Fichier | Action |
|---|---|
| `src/pages/event/EventCreatePage.tsx` | Ajouter layout hero + blobs |
| `src/pages/event/EventEditPage.tsx` | Ajouter layout hero + blobs + gestion loading/error stylée |
| `src/components/event/EventForm.tsx` | Supprimer wrapper et h1 internes, passer en glassmorphism card, ajouter groupements visuels |

### Contrainte centrale

Les deux pages partagent `EventForm`. **Toute la logique métier de `EventForm` est intouchable** : hook `useEventForm`, `splitDateTime`/`joinDateTime`, validation, callbacks, gestion image, `FormField`/`Input`/`Select`/`Textarea`. Seule la couche présentation change.

---

## 2. Modèle visuel de référence

| Page | Ce qu'on en prend |
|---|---|
| `CalendarPage.tsx` | Pattern exact pour pages privées : `SectionWrapper padding="sm" background={<BlobsSubtle />}` + `SectionHeader align="left"` avec `<mark>` sur le mot-clé accent |
| `ProfilePage.tsx` | Glassmorphism card : `bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border` |
| `EventDetailPage.tsx` | Même domaine événement, `max-w-3xl`, séparateurs `border-t border-border`, labels `text-xs font-bold uppercase tracking-widest text-foreground/30` pour les sections |

---

## 3. Layout — `EventCreatePage`

### Structure JSX cible

```tsx
<SectionWrapper padding="sm" size="md" background={<BlobsSubtle />}>
  <SectionHeader
    align="left"
    heading="lg"
    title={<>Créer un <mark>événement</mark></>}
    subtitle="Renseignez les informations de votre événement pour le partager avec la communauté UNIGE."
  />
  <EventForm
    submitLabel="Créer l'événement"
    {/* ...autres props identiques */}
  />
</SectionWrapper>
```

### Justification des props SectionWrapper

- `padding="sm"` → `py-12 lg:py-16` : cohérent avec CalendarPage, pas trop massif pour une page de formulaire
- `size="md"` → `max-w-3xl` : remplace l'actuel `max-w-3xl mx-auto` du wrapper supprimé dans EventForm ; le SectionWrapper centre et pad automatiquement
- `background={<BlobsSubtle />}` → deux petits blobs animés (pink top-right + blue bottom-left) : identique à CalendarPage — sobre, n'écrase pas la lisibilité du formulaire

### SectionHeader

- `heading="lg"` → titre `text-4xl lg:text-7xl` : légèrement plus petit qu'un hero de landing (xl), adapté à une page utilitaire
- `align="left"` : cohérent avec CalendarPage, les pages internes s'alignent à gauche
- `title` : `Créer un <mark>événement</mark>` — `<mark>` sur "événement" → gradient accent via le CSS global
- `subtitle` : `"Renseignez les informations de votre événement pour le partager avec la communauté UNIGE."`

### Responsive

- Mobile : stack vertical naturel via SectionWrapper (`flex flex-col gap-12`)
- Le SectionHeader prend toute la largeur, EventForm dessous dans sa card
- `max-w-3xl` + `px-4 sm:px-6 lg:px-8` gérés par SectionWrapper → aucun padding manuel dans la page

---

## 4. Layout — `EventEditPage`

### Structure JSX cible

```tsx
// États loading/error : rendus AVANT SectionWrapper (plein écran, pas de layout hero)
if (eventId === null) return <InfoMessage type="error" message="Identifiant d'événement invalide." />
if (loading) return <LoadingSpinner />
if (error) return <InfoMessage type="error" message={error} />
if (!event) return <InfoMessage type="error" message="Événement introuvable." />

// Rendu principal
<SectionWrapper padding="sm" size="md" background={<BlobsSubtle />}>
  <SectionHeader
    align="left"
    heading="lg"
    title={<>Modifier <mark>l'événement</mark></>}
    subtitle="Mettez à jour les informations et republiez pour informer les participants."
  />
  <EventForm
    submitLabel="Enregistrer"
    {/* ...autres props identiques */}
  />
</SectionWrapper>
```

### Différences avec EventCreatePage

- `title` du SectionHeader : `Modifier <mark>l'événement</mark>` (générique, pas le nom de l'event — évite de charger le titre 2 fois et de gérer la troncature dans le hero)
- `subtitle` : message centré sur la mise à jour
- Les `LoadingSpinner` et `InfoMessage` restent en dehors du layout hero, rendus tels quels avant le `return` principal. Ils sont déjà stylés de manière cohérente et ne nécessitent pas de wrapper hero quand la page ne peut pas se charger.

---

## 5. Modifications à apporter à `EventForm`

### 5.1 Suppression du wrapper actuel

**Supprimer** ces deux divs englobantes :

```tsx
// SUPPRIMER
<div className="max-w-3xl mx-auto">
  <div className="bg-background border border-border rounded-3xl p-8 max-sm:p-5">
```

**Raison** : le centrage et la largeur max sont désormais gérés par `SectionWrapper size="md"`. Garder ce double wrapper créerait un conflit de largeur max et casserait l'alignement du padding.

### 5.2 Suppression du `h1` et de la prop `title`

**Supprimer** :
```tsx
<h1 className="text-3xl font-bold text-foreground mb-8">{title}</h1>
```

**Supprimer `title` de l'interface `EventFormProps`** :
```ts
// AVANT
interface EventFormProps {
  title: string
  submitLabel: string
  // ...

// APRÈS
interface EventFormProps {
  submitLabel: string
  // ...
```

**Raison** : le titre est maintenant la responsabilité de la page parente via `SectionHeader`. `EventForm` est un composant de formulaire pur — il ne gère pas le header de page.

### 5.3 Nouvelle card glassmorphism

Le `<form>` doit être enveloppé dans une card glassmorphism. Structure résultante :

```tsx
export default function EventForm({ submitLabel, ... }: Readonly<EventFormProps>) {
  // ...logique inchangée...

  return (
    <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border p-8 max-sm:p-5">
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6">
        {/* sections du formulaire */}
      </form>
    </div>
  )
}
```

**Classes de la card** :
- `bg-linear-to-br from-background/80 to-background/40` : dégradé glassmorphism (identique à ProfilePage "À propos" card)
- `backdrop-blur-xl` : effet verre
- `rounded-3xl` : coins larges, cohérent avec les autres cards du projet
- `border border-border` : bordure standard
- `p-8 max-sm:p-5` : padding conservé

**Changement gap** : `gap-5` → `gap-6` pour donner un peu plus d'air avec les groupements visuels.

### 5.4 Groupements visuels avec sections nommées

Remplacer le formulaire à champs empilés par des sections visuellement séparées. Chaque section utilise le pattern de `EventDetailPage` (séparateur `border-t border-border` + label `text-xs font-bold uppercase tracking-widest text-foreground/30`).

**Structure des sections** :

```
┌─────────────────────────────────────┐
│ Informations générales              │  ← label section
│   [Titre + compteur]                │
│   [Description + compteur]          │
│   [Lieu]                            │
├─────────────────────────────────────┤
│ Dates                               │  ← label section
│   [Début (date + heure + minute)]   │
│   [Fin   (date + heure + minute)]   │
├─────────────────────────────────────┤
│ Configuration                       │  ← label section
│   [Catégorie]    [Capacité]         │
│   [Statut]                          │
├─────────────────────────────────────┤
│ Bannière                            │  ← label section
│   [Zone upload]                     │
│   [Bouton choisir + nom fichier]    │
├─────────────────────────────────────┤
│ (border-t)          [Annuler] [OK]  │  ← footer actions
└─────────────────────────────────────┘
```

**Pattern JSX d'une section** :
```tsx
<div className="flex flex-col gap-5">
  <p className="text-xs font-bold uppercase tracking-widest text-foreground/30">
    Informations générales
  </p>
  {/* champs de la section */}
</div>
```

Les sections sont séparées par `<div className="border-t border-border" />`.

### 5.5 Section "Bannière" — enrichissement visuel

**Zone de drop actuelle** (à conserver mais enrichir) :

```tsx
// AVANT
<div className="w-full min-h-40 rounded-2xl border border-border border-dashed flex flex-col items-center justify-center gap-2 p-6 text-foreground/30">
  <ImagePlus className="w-8 h-8" />
  <span className="text-sm">Ajoutez une image de couverture</span>
</div>

// APRÈS
<div className="w-full min-h-48 rounded-2xl border border-border border-dashed flex flex-col items-center justify-center gap-3 p-8 text-foreground/30 hover:border-accent/40 hover:text-foreground/50 transition-all">
  <div className="w-12 h-12 rounded-2xl bg-foreground/5 border border-border flex items-center justify-center">
    <ImagePlus className="w-6 h-6" />
  </div>
  <div className="text-center">
    <span className="text-sm font-medium block">Ajoutez une image de couverture</span>
    <span className="text-xs mt-1 block opacity-70">PNG, JPG ou WEBP — max {IMAGE_MAX_SIZE_MB} Mo</span>
  </div>
</div>
```

**Changements** :
- `min-h-40` → `min-h-48` : zone plus généreuse
- `gap-2 p-6` → `gap-3 p-8` : plus d'espace
- Ajout `hover:border-accent/40 hover:text-foreground/50 transition-all` : feedback au survol
- Icône dans un badge carré `w-12 h-12 rounded-2xl bg-foreground/5 border border-border` (pattern Features de LandingPage)
- Sous-texte avec taille max intégré directement dans la zone (supprimer le `<span>` séparé en dessous)

**Aperçu image** : inchangé — déjà bon :
```tsx
<img
  src={imagePreview}
  alt="Aperçu de la bannière"
  className="w-full max-h-72 min-h-40 rounded-2xl border border-border object-cover"
/>
```

**Bouton "Choisir une image"** : garder la `<label>` actuelle, juste supprimer le bloc d'info en double (déplacé dans la zone) :
```tsx
<div className="flex items-center gap-3 flex-wrap mt-2">
  <label
    htmlFor="event-banner"
    className="px-4 py-2 rounded-xl border border-border text-sm font-semibold text-foreground/60 cursor-pointer hover:border-accent/50 hover:text-foreground transition-all flex-none"
  >
    Choisir une image
  </label>
  <input id="event-banner" type="file" accept="image/*" onChange={onImageChange} className="hidden" />
  {selectedImageName && (
    <span className="text-sm text-foreground/40 flex-1 min-w-48 break-all">{selectedImageName}</span>
  )}
</div>
```

### 5.6 Footer du formulaire (actions)

Conserver le footer actuel avec séparateur, mais ajuster :

```tsx
<div className="flex justify-end gap-3 pt-4 border-t border-border max-sm:flex-col-reverse">
  <ButtonSecondary onClick={onCancel}>Annuler</ButtonSecondary>
  <ButtonPrimary type="submit" disabled={submitting}>
    {submitting ? 'Enregistrement...' : submitLabel}
  </ButtonPrimary>
</div>
```

**Changement** : `pt-3` → `pt-4` pour un peu plus d'espace après les champs. Le reste est inchangé (ButtonPrimary et ButtonSecondary sont déjà dans le design system).

---

## 6. Design tokens et classes CSS — référence complète

| Élément | Classes exactes |
|---|---|
| **Wrapper page** | `SectionWrapper padding="sm" size="md" background={<BlobsSubtle />}` |
| **Hero title** | Via `SectionHeader heading="lg" align="left"` — génère `text-4xl lg:text-7xl font-bold tracking-tight` |
| **Mot accent dans le titre** | `<mark>événement</mark>` → gradient défini par le CSS global `.prose mark` ou `mark` |
| **Hero subtitle** | Via `SectionHeader subtitle="..."` — génère `text-lg lg:text-xl text-foreground/60 font-light` |
| **Card formulaire** | `bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border p-8 max-sm:p-5` |
| **Form** | `flex flex-col gap-6` |
| **Label section** | `text-xs font-bold uppercase tracking-widest text-foreground/30` |
| **Séparateur section** | `<div className="border-t border-border" />` |
| **Container section** | `flex flex-col gap-5` |
| **Grid dates (2 colonnes)** | `grid grid-cols-2 gap-4 max-sm:grid-cols-1` (inchangé) |
| **Grid catégorie+capacité** | `grid grid-cols-2 gap-4 max-sm:grid-cols-1` (inchangé) |
| **Badge icône bannière** | `w-12 h-12 rounded-2xl bg-foreground/5 border border-border flex items-center justify-center` |
| **Zone upload (vide)** | `w-full min-h-48 rounded-2xl border border-border border-dashed flex flex-col items-center justify-center gap-3 p-8 text-foreground/30 hover:border-accent/40 hover:text-foreground/50 transition-all` |
| **Aperçu bannière** | `w-full max-h-72 min-h-40 rounded-2xl border border-border object-cover` (inchangé) |
| **Bouton choisir image** | `px-4 py-2 rounded-xl border border-border text-sm font-semibold text-foreground/60 cursor-pointer hover:border-accent/50 hover:text-foreground transition-all flex-none` (inchangé) |
| **Footer** | `flex justify-end gap-3 pt-4 border-t border-border max-sm:flex-col-reverse` |
| **Bouton submit** | `ButtonPrimary type="submit" disabled={submitting}` (inchangé) |
| **Bouton annuler** | `ButtonSecondary onClick={onCancel}` (inchangé) |

---

## 7. Hors scope — ne pas toucher

- `useEventForm` et toute sa logique (validation, soumission, upload image, gestion erreurs backend)
- `splitDateTime` / `joinDateTime` / `renderDateTimeField`
- `HOUR_OPTIONS` / `MINUTE_OPTIONS`
- `FormField`, `Input`, `Select`, `Textarea` et leurs classes internes
- `EVENT_CATEGORIES`, `EVENT_STATUSES` et leur utilisation dans les selects
- `EVENT_TITLE_MAX_LENGTH`, `EVENT_DESCRIPTION_MAX_LENGTH`, `IMAGE_MAX_SIZE_MB`
- Callbacks `onFieldChange`, `onImageChange`, `onSubmit`, `onCancel`
- `imagePreview` et `selectedImageName` (logique de preview)
- Gestion BANNER_UPLOAD_ERROR_KEY (dans EventCreatePage et EventEditPage)
- `getById`, `LoadingSpinner`, `InfoMessage` (déjà corrects dans EventEditPage)
- Types TypeScript : `EventFormProps`, `EventFormValues`, `EventFormErrors`, `DateTimeParts`
- `submitLabel` prop (toujours utilisée pour le bouton submit)

---

## 8. Wireframes ASCII

### 8.1 `EventCreatePage` — Desktop

```
┌──────────────────────────────────────────────────────────┐
│ [Navbar]                                                 │
└──────────────────────────────────────────────────────────┘
                                             ·· blob pink ··
  Créer un événement                          (top-right)
  ──────────────────────────
  Renseignez les informations de votre événement...

  ┌─────────────────────────────────────────────────────┐
  │  (glassmorphism card, backdrop-blur)                │
  │                                                     │
  │  INFORMATIONS GÉNÉRALES                             │
  │  ┌─────────────────────────────────────────────┐   │
  │  │ Titre *                                     │   │
  │  │ [_________________________________] 0/120   │   │
  │  └─────────────────────────────────────────────┘   │
  │  ┌─────────────────────────────────────────────┐   │
  │  │ Description                                 │   │
  │  │ [___________________________________]       │   │
  │  │ [___________________________________] 0/2000│   │
  │  └─────────────────────────────────────────────┘   │
  │  ┌─────────────────────────────────────────────┐   │
  │  │ Lieu *                                      │   │
  │  │ [_________________________________]         │   │
  │  └─────────────────────────────────────────────┘   │
  │  ─────────────────────────────────────────────     │
  │  DATES                                              │
  │  ┌──────────────────┐  ┌──────────────────────┐   │
  │  │ Début *          │  │ Fin *                 │   │
  │  │ [jj/mm/aaaa][HH][MM]│ [jj/mm/aaaa][HH][MM] │   │
  │  └──────────────────┘  └──────────────────────┘   │
  │  ─────────────────────────────────────────────     │
  │  CONFIGURATION                                      │
  │  ┌───────────────────┐  ┌──────────────────────┐  │
  │  │ Catégorie *       │  │ Capacité             │  │
  │  │ [Sélectionner  ▼] │  │ [150               ] │  │
  │  └───────────────────┘  └──────────────────────┘  │
  │  ┌─────────────────────────────────────────────┐   │
  │  │ Statut                                      │   │
  │  │ [Brouillon                              ▼]  │   │
  │  └─────────────────────────────────────────────┘   │
  │  ─────────────────────────────────────────────     │
  │  BANNIÈRE                                           │
  │  ┌─────────────────────────────────────────────┐   │
  │  │                                             │   │
  │  │          ┌──────────────┐                  │   │
  │  │          │  [img icon]  │                  │   │
  │  │          └──────────────┘                  │   │
  │  │    Ajoutez une image de couverture          │   │
  │  │       PNG, JPG ou WEBP — max 5 Mo           │   │
  │  │                                             │   │
  │  └─────────────────────────────────────────────┘   │
  │  [Choisir une image]                                │
  │  ─────────────────────────────────────────────     │
  │                       [Annuler] [Créer l'événement] │
  └─────────────────────────────────────────────────────┘
                       ·· blob blue ··
                        (bottom-left)
```

### 8.2 `EventCreatePage` — Mobile

```
┌──────────────────────────────────┐
│ [Navbar]                         │
└──────────────────────────────────┘

  Créer un
  événement
  ───────────────────
  Renseignez les infos...

  ┌────────────────────────────┐
  │  INFORMATIONS GÉNÉRALES    │
  │  Titre *                   │
  │  [____________________]    │
  │                   0/120    │
  │  Description               │
  │  [____________________]    │
  │  [____________________]    │
  │                   0/2000   │
  │  Lieu *                    │
  │  [____________________]    │
  │  ─────────────────────     │
  │  DATES                     │
  │  Début *                   │
  │  [jj/mm/aaaa]              │
  │  [HH ▼] : [MM ▼]          │
  │  Fin *                     │
  │  [jj/mm/aaaa]              │
  │  [HH ▼] : [MM ▼]          │
  │  ─────────────────────     │
  │  CONFIGURATION             │
  │  Catégorie *               │
  │  [Sélectionner         ▼]  │
  │  Capacité                  │
  │  [150                   ]  │
  │  Statut                    │
  │  [Brouillon            ▼]  │
  │  ─────────────────────     │
  │  BANNIÈRE                  │
  │  [  [img]               ]  │
  │  [  Ajoutez une image   ]  │
  │  [  PNG, JPG — max 5 Mo ]  │
  │  [Choisir une image]       │
  │  ─────────────────────     │
  │  [Créer l'événement]       │
  │  [Annuler]                 │
  └────────────────────────────┘
```

### 8.3 `EventEditPage` — Desktop

```
┌──────────────────────────────────────────────────────────┐
│ [Navbar]                                                 │
└──────────────────────────────────────────────────────────┘
                                             ·· blob pink ··

  Modifier l'événement
  ──────────────────────────────────
  Mettez à jour les informations et republiez...

  ┌─────────────────────────────────────────────────────┐
  │  (identique à EventCreatePage mais                  │
  │   pré-rempli avec les valeurs de l'événement        │
  │   + bouton submit "Enregistrer")                    │
  └─────────────────────────────────────────────────────┘
```

*Note : Les états `loading` et `error` sont rendus AVANT le layout hero (plein écran, pas de SectionWrapper). `LoadingSpinner` et `InfoMessage` gèrent leur propre centrage.*

---

## 9. Checklist d'implémentation

- [ ] `EventForm.tsx` : supprimer `title` de `EventFormProps` et le `<h1>`
- [ ] `EventForm.tsx` : supprimer les deux divs wrapper externes (`max-w-3xl mx-auto` et la card simple)
- [ ] `EventForm.tsx` : ajouter la card glassmorphism comme wrapper du `<form>`
- [ ] `EventForm.tsx` : restructurer les champs en 4 sections nommées avec séparateurs
- [ ] `EventForm.tsx` : enrichir la zone upload bannière (badge icône, hover, texte intégré)
- [ ] `EventForm.tsx` : `gap-5` → `gap-6` sur le `<form>`
- [ ] `EventCreatePage.tsx` : ajouter `SectionWrapper` + `BlobsSubtle` + `SectionHeader`
- [ ] `EventCreatePage.tsx` : retirer la prop `title` de `<EventForm>`
- [ ] `EventEditPage.tsx` : ajouter `SectionWrapper` + `BlobsSubtle` + `SectionHeader`
- [ ] `EventEditPage.tsx` : retirer la prop `title` de `<EventForm>`
- [ ] Vérifier que `npm run lint` passe sans erreur
- [ ] Vérifier que `npm run build` passe sans erreur TypeScript
- [ ] Tester visuellement : création → redirection vers détail ✓, édition → redirection vers détail ✓
- [ ] Tester responsive : mobile 375px + desktop 1280px
- [ ] Tester dark/light mode
