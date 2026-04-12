# SPEC — Audit UI formulaire événement + shells backlog S5→S9

> Auteur : Claude  
> Date : 2026-04-11  
> Base : layout v3 (CategorySelect, suppression Statut) déjà implémenté  
> Statut : **À implémenter**

---

## 1. Vue d'ensemble

### Objectif

Deux missions simultanées :

1. **Corrections UI immédiates** identifiées par audit visuel de la page `/events/new` :
   - La zone bannière est trop haute → aligner son bord supérieur avec le champ "Titre"
   - Le champ Capacité (type `number`) affiche les flèches natives du navigateur → les masquer

2. **Shells visuels backlog** : rendre visible l'espace que chaque feature future occupera dans le formulaire, sans implémenter la logique. Les shells sont non-interactifs (`pointer-events-none`), stylés différemment des champs actifs (bords en tiretés, fond très légèrement teinté), et portent un badge "S5", "S8", etc. Cette approche permet à l'équipe de valider le layout global avant d'implémenter chaque sprint.

### Ce qui change

| Aspect | Avant | Après |
|---|---|---|
| Alignement bannière | Top bannière = top ligne de grille (trop haut) | Top bannière ≈ top du champ Titre (pt-7) |
| Flèches input Capacité | Arrows natives visibles | `appearance-none` → masquées |
| Checkbox "Toute la journée" | Absent | Shell disabled inline dans Bande 2, badge S5 |
| Bande 4 — champs additionnels | Absent | Shells : websiteUrl, contactEmail, deadline, tags — badge S5/S6 |
| Bande 4 — récurrence | Absent | Shell toggle collapse (create only) — badge S8 |
| Bande 4 — pièces jointes | Absent | Shell drop-zone (toujours visible) — badge S9 |
| Bande 5 — co-organisateurs | Absent | Shell section (edit mode uniquement) — badge S8 |

### Ce qui ne change PAS

- Layout Bandes 1, 2, 3 (hors pt-7 sur le wrapper gauche de la Bande 1)
- Logique métier, validations, appels API, `useEventForm`
- Tests existants (aucun test sur les shells — ils sont non-interactifs)
- Composants `CategorySelect`, `EventCreatePage`, `EventEditPage`

---

## 2. Périmètre des fichiers

| Fichier | Modifications |
|---|---|
| `src/components/event/EventForm.tsx` | Fix pt-7 bannière ; fix spinners Capacité ; prop `mode` activée ; allDay shell dans Band 2 ; Bande 4 shells ; Bande 5 shell |
| `docs/components.md` | Mise à jour description EventForm (bandes 4 et 5 shell) |

**Aucun nouveau fichier à créer** — tous les composants shell sont des fonctions locales dans `EventForm.tsx` (non exportées, utilisées une seule fois ou deux fois max).

---

## 3. Correction 1 — Alignement vertical de la bannière

### Diagnostic

La grille CSS de la Bande 1 (`grid grid-cols-[2fr_3fr] gap-6`) place les deux colonnes au même niveau. La colonne droite commence par un `FormField` qui rend :
```html
<label class="block text-sm font-semibold text-foreground/60 mb-2">Titre *</label>
<input ... />
```
La hauteur du label FormField vaut environ **26px** (`text-sm` 14px × line-height 1.25 + `mb-2` 8px). La colonne gauche (bannière) n'a pas de label équivalent — son bord supérieur est donc ~26px plus haut que l'input Titre.

### Fix

Ajouter `pt-7` (28px) au wrapper de la colonne gauche pour descendre la bannière jusqu'au niveau du champ Titre.

```tsx
{/* AVANT */}
<div className="flex flex-col gap-3">

{/* APRÈS */}
<div className="flex flex-col gap-3 pt-7 max-lg:pt-0">
```

`max-lg:pt-0` : en dessous de 1024px les colonnes sont empilées (`grid-cols-1`), le padding ne doit pas s'appliquer.

---

## 4. Correction 2 — Masquer les flèches du champ Capacité

### Diagnostic

Le navigateur affiche des flèches nationales ("spinners") sur tout `<input type="number">`. Avec `w-24` (96px), elles empiètent visuellement sur le placeholder "∞".

### Fix

Ajouter les classes utilitaires Tailwind `[appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none` directement sur l'Input Capacité dans `EventForm.tsx` :

```tsx
<Input
  id="event-capacity"
  type="number"
  min="1"
  step="1"
  value={values.capacity}
  onChange={(e) => onFieldChange('capacity', e.target.value)}
  error={errors.capacity}
  placeholder="∞"
  className="[appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
/>
```

---

## 5. Composant local `ComingSoonBlock` — Pattern shells

Toutes les zones "bientôt disponible" utilisent le même composant local non-exporté. Ce composant est défini directement dans `EventForm.tsx` (DRY : il est utilisé 5+ fois dans ce fichier).

### Interface

```typescript
// Composant local dans EventForm.tsx — non exporté
interface ComingSoonBlockProps {
  icon: LucideIcon
  label: string
  sprint: string
  children?: React.ReactNode  // optionnel : mock du contenu interne
}
```

### Const map variants

```typescript
const comingSoonVariants = {
  container: 'rounded-2xl border border-dashed border-border/40 bg-foreground/[0.018] px-4 py-3',
  header:    'flex items-center justify-between gap-3',
  iconLabel: 'flex items-center gap-2 text-foreground/30',
  icon:      'w-4 h-4 shrink-0',
  label:     'text-sm',
  badge:     'text-[10px] font-semibold tracking-widest uppercase text-foreground/20 bg-foreground/5 px-2 py-0.5 rounded-full border border-border/30 shrink-0',
  body:      'mt-3 pointer-events-none select-none opacity-30',
} as const
```

### Rendu JSX complet

```tsx
function ComingSoonBlock({ icon: Icon, label, sprint, children }: ComingSoonBlockProps) {
  return (
    <div className={comingSoonVariants.container}>
      <div className={comingSoonVariants.header}>
        <div className={comingSoonVariants.iconLabel}>
          <Icon className={comingSoonVariants.icon} />
          <span className={comingSoonVariants.label}>{label}</span>
        </div>
        <span className={comingSoonVariants.badge}>{sprint}</span>
      </div>
      {children && (
        <div className={comingSoonVariants.body}>
          {children}
        </div>
      )}
    </div>
  )
}
```

**Import supplémentaire nécessaire :** `type { LucideIcon } from 'lucide-react'`

---

## 6. Shell Bande 2 — Checkbox "Toute la journée" (SCRUM-125, S5)

La checkbox est intégrée **inline dans le label du champ "Début"**, sous forme d'un badge désactivé à droite du texte "Début *". Elle est rendue via une modification de l'appel `renderDateTimeField` pour `startDate` uniquement (pas `endDate`).

### Modification dans `EventForm.tsx`

Remplacer l'appel générique `renderDateTimeField` pour `startDate` par un rendu inline qui ajoute la checkbox shell :

```tsx
{/* Dans la Bande 2 — remplacer l'appel renderDateTimeField pour startDate */}
<FormField
  label={
    <span className="flex items-center justify-between w-full gap-3">
      <span>Début <span className="text-red-400">*</span></span>
      {/* Shell allDay — SCRUM-125 / S5 */}
      <label className="flex items-center gap-1.5 text-xs font-normal text-foreground/25 cursor-not-allowed select-none pointer-events-none">
        <input type="checkbox" disabled className="opacity-25 accent-accent w-3.5 h-3.5" />
        Toute la journée
        <span className={comingSoonVariants.badge}>S5</span>
      </label>
    </span>
  }
  htmlFor="event-startDate"
  required={false}
  error={errors.startDate}
>
  {/* Contenu identique à renderDateTimeField */}
  <div className="grid grid-cols-[1fr_auto] gap-3 max-sm:grid-cols-1">
    <Input
      id="event-startDate"
      type="date"
      value={startDateTime.datePart}
      onChange={(e) => setDatePart('startDate', e.target.value, startDateTime.hourPart, startDateTime.minutePart)}
      error={errors.startDate}
    />
    <div className="flex items-center gap-1.5">
      <label className="sr-only" htmlFor="event-startDate-hour">Heure de début</label>
      <Select
        id="event-startDate-hour"
        value={startDateTime.hourPart}
        onChange={(e) => setTimePart('startDate', startDateTime.datePart, startDateTime.hourPart, startDateTime.minutePart, 'hour', e.target.value)}
        error={errors.startDate}
        className="w-auto min-w-[4.5rem]"
      >
        <option value="">HH</option>
        {HOUR_OPTIONS.map((h) => <option key={h} value={h}>{h}</option>)}
      </Select>
      <span className="text-foreground/40 font-bold select-none">:</span>
      <label className="sr-only" htmlFor="event-startDate-minute">Minute de début</label>
      <Select
        id="event-startDate-minute"
        value={startDateTime.minutePart}
        onChange={(e) => setTimePart('startDate', startDateTime.datePart, startDateTime.hourPart, startDateTime.minutePart, 'minute', e.target.value)}
        error={errors.startDate}
        className="w-auto min-w-[4.5rem]"
      >
        <option value="">MM</option>
        {MINUTE_OPTIONS.map((m) => <option key={m} value={m}>{m}</option>)}
      </Select>
    </div>
  </div>
</FormField>
```

> **Note :** `required={false}` ici parce que le `required` est géré via le label inline qui contient déjà `<span className="text-red-400">*</span>`. Pour ne pas doubler l'astérisque, ne pas passer `required` à FormField dans ce cas.

**Imports supplémentaires :** aucun (tous déjà présents)

---

## 7. Bande 4 — Shells champs additionnels + récurrence + pièces jointes

La Bande 4 est insérée **entre la Bande 3 et `{errors.image}`**, toujours visible. Elle contient :
- 4 shells individuels via `ComingSoonBlock`
- Un separator visuel `<div className="border-t border-border/30" />`

### Structure JSX complète de la Bande 4

```tsx
{/* Bande 4 — Shells champs additionnels (SCRUM-127/128/147/162) */}
<div className="flex flex-col gap-3">

  {/* Ligne 1 : websiteUrl + contactEmail en grille 2 colonnes */}
  <div className="grid grid-cols-2 gap-3 max-sm:grid-cols-1">
    <ComingSoonBlock icon={Globe} label="Site web de l'événement" sprint="S6">
      <div className="flex items-center gap-2 mt-0.5">
        <Globe className="w-4 h-4 text-foreground/30 shrink-0" />
        <div className="flex-1 rounded-xl border border-border/30 px-3 py-2 text-xs text-foreground/20 bg-transparent">https://unige.ch/…</div>
      </div>
    </ComingSoonBlock>

    <ComingSoonBlock icon={Mail} label="Email de contact" sprint="S6">
      <div className="flex items-center gap-2 mt-0.5">
        <Mail className="w-4 h-4 text-foreground/30 shrink-0" />
        <div className="flex-1 rounded-xl border border-border/30 px-3 py-2 text-xs text-foreground/20 bg-transparent">contact@unige.ch</div>
      </div>
    </ComingSoonBlock>
  </div>

  {/* Ligne 2 : deadline inscription (pleine largeur) */}
  <ComingSoonBlock icon={CalendarClock} label="Date limite d'inscription" sprint="S6">
    <div className="grid grid-cols-[1fr_auto] gap-2 mt-0.5 max-sm:grid-cols-1">
      <div className="rounded-xl border border-border/30 px-3 py-2 text-xs text-foreground/20">jj/mm/aaaa</div>
      <div className="flex items-center gap-1">
        <div className="rounded-xl border border-border/30 px-2 py-2 text-xs text-foreground/20 w-14">HH</div>
        <span className="text-foreground/20 font-bold">:</span>
        <div className="rounded-xl border border-border/30 px-2 py-2 text-xs text-foreground/20 w-14">MM</div>
      </div>
    </div>
  </ComingSoonBlock>

  {/* Ligne 3 : tags / mots-clés (pleine largeur) */}
  <ComingSoonBlock icon={Tag} label="Mots-clés" sprint="S5">
    <div className="flex flex-wrap gap-1.5 mt-0.5 min-h-8 rounded-xl border border-dashed border-border/30 bg-transparent px-3 py-2">
      {(['conférence', 'réseau', 'emploi'] as const).map((tag) => (
        <span key={tag} className="inline-flex items-center gap-1 px-2 py-0.5 rounded-lg text-xs bg-foreground/5 border border-border/30 text-foreground/20">
          {tag} <span className="text-foreground/15">×</span>
        </span>
      ))}
      <span className="text-xs text-foreground/15 ml-1">Ajoutez des mots-clés…</span>
    </div>
  </ComingSoonBlock>

  <div className="border-t border-border/20" />

  {/* Récurrence — create mode uniquement */}
  {_mode === 'create' && (
    <ComingSoonBlock icon={Repeat} label="Répéter cet événement" sprint="S8">
      <div className="flex gap-3 mt-2 flex-wrap opacity-100">
        <div className="flex-1 min-w-36 rounded-xl border border-dashed border-border/30 px-3 py-2 text-xs text-foreground/20">Toutes les semaines ▾</div>
        <div className="flex-1 min-w-36 rounded-xl border border-dashed border-border/30 px-3 py-2 text-xs text-foreground/20">Répéter jusqu'au…</div>
      </div>
    </ComingSoonBlock>
  )}

  {/* Pièces jointes — toujours visible */}
  <ComingSoonBlock icon={Paperclip} label="Pièces jointes (PDF, DOCX, slides…)" sprint="S9">
    <div className="mt-2 rounded-xl border border-dashed border-border/30 p-4 flex flex-col items-center gap-1.5 text-center">
      <Paperclip className="w-5 h-5 text-foreground/15" />
      <span className="text-xs text-foreground/15">Glissez vos fichiers ici ou cliquez pour parcourir</span>
    </div>
  </ComingSoonBlock>

</div>
```

**Imports à ajouter dans `EventForm.tsx` :**
```typescript
import { ImagePlus, MapPin, Globe, Mail, CalendarClock, Tag, Repeat, Paperclip } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
```

**Activation de `_mode` :** La prop `mode` est désormais utilisée dans la Bande 4 (condition `_mode === 'create'`). Renommer `_mode` → `mode` dans la déstructuration :

```typescript
// AVANT
export default function EventForm({
  mode: _mode,
  ...

// APRÈS
export default function EventForm({
  mode,
  ...
```

---

## 8. Bande 5 — Shell co-organisateurs (SCRUM-137, S8, mode édition uniquement)

La Bande 5 est insérée **après la Bande 4**, visible uniquement si `mode === 'edit'`. Elle contient un en-tête de section + un mock du champ de recherche.

```tsx
{/* Bande 5 — Shell co-organisateurs (SCRUM-137) — edit only */}
{mode === 'edit' && (
  <div className="flex flex-col gap-3 border-t border-border/30 pt-6">
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2 text-foreground/30">
        <Users className="w-4 h-4" />
        <span className="text-sm">Co-organisateurs</span>
      </div>
      <span className={comingSoonVariants.badge}>S8</span>
    </div>

    {/* Mock champ de recherche */}
    <div className="pointer-events-none select-none opacity-40 max-w-sm">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground/30 pointer-events-none" />
        <div className="w-full rounded-xl border border-dashed border-border/40 bg-transparent px-4 py-2.5 pl-10 text-sm text-foreground/20">
          Inviter un collaborateur…
        </div>
      </div>
    </div>

    {/* Mock chips */}
    <div className="flex flex-wrap gap-2 pointer-events-none select-none opacity-35">
      {[
        { name: 'Alice Martin', status: 'Accepté' },
        { name: 'Bob Chen', status: 'En attente' },
      ].map((co) => (
        <span key={co.name} className="inline-flex items-center gap-2 px-3 py-1 rounded-xl border border-dashed border-border/40 text-sm text-foreground/25">
          <span>{co.name}</span>
          <span className="text-xs text-foreground/20">{co.status}</span>
          <span className="text-foreground/20">×</span>
        </span>
      ))}
    </div>
  </div>
)}
```

**Import à ajouter :** `Users`, `Search` depuis `lucide-react`

---

## 9. `EventForm.tsx` — Structure JSX globale v3b complète

```tsx
return (
  <form id="event-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-8">

    {/* Bande 1 — fix pt-7 sur colonne gauche */}
    <div className="grid grid-cols-[2fr_3fr] gap-6 max-lg:grid-cols-1">
      <div className="flex flex-col gap-3 pt-7 max-lg:pt-0">     {/* ← FIX ALIGNEMENT */}
        {/* ... bannière ... */}
      </div>
      <div className="flex flex-col gap-4">
        {/* ... Titre + Description ... */}
      </div>
    </div>

    {/* Bande 2 — avec shell allDay inline dans Début */}
    <div className="grid grid-cols-[2fr_1fr_1fr] gap-4 max-sm:grid-cols-1">
      {/* Lieu — inchangé */}
      {/* Début — version modifiée avec checkbox shell */}
      {/* Fin — renderDateTimeField inchangé */}
    </div>

    {/* Bande 3 — inchangée */}
    <div className="flex flex-wrap items-end gap-x-6 gap-y-4">
      {/* CategorySelect + Capacité (fix spinners) + CTA */}
    </div>

    {/* Bande 4 — shells champs additionnels */}
    <div className="flex flex-col gap-3">
      {/* Grid 2 cols : websiteUrl + email */}
      {/* pleine largeur : deadline */}
      {/* pleine largeur : tags */}
      {/* separator */}
      {/* récurrence (create only) */}
      {/* pièces jointes */}
    </div>

    {/* Bande 5 — shell co-organisateurs (edit only) */}

    {/* Erreur image */}
    {errors.image && <p className="text-xs text-error -mt-4">{errors.image}</p>}

  </form>
)
```

---

## 10. Wireframes ASCII v3b

### 10.1 Desktop ≥ 1024px — Mode création

```
┌────────────────────────────────────────────────────────────────────────┐
│ [Navbar]                                                               │
└────────────────────────────────────────────────────────────────────────┘
                                                      ·· blob pink ··

  Créer un événement
  ──────────────────────────────────────────

  ┌────────────────────────────┐ ┌──────────────────────────────────────┐
  │                            │ │  Titre *                             │
  │    [↑ upload icon]         │ │  [__________________________________]│
  │                            │ │                              0 / 120 │
  │  Ajoutez une image         │ │                                      │
  │  PNG/JPG — max 5 Mo        │ │  Description                         │
  │                            │ │  [__________________________________]│
  │                            │ │  [__________________________________]│
  └────────────────────────────┘ └──────────────────────────────────────┘
  ↑ bord supérieur bannière       ↑ top = "Titre *" label = même niveau
  = niveau input "Titre" (pt-7)

  ┌──────────────────────────────┬────────────────────────────┬──────────┐
  │  📍 Lieu *                   │  Début *   □ Toute la j.…S5│  Fin *   │
  │  [___________________________]│  [________] [HH▼]:[MM▼]   │  [____]  │
  └──────────────────────────────┴────────────────────────────┴──────────┘

  ┌──────────────────────────────────────────────────────────────────────┐
  │  Catégorie *        Capacité                                         │
  │  [● Choisir catég▾] [∞    ]                  [CRÉER L'ÉVÉNEMENT]    │
  │                                              [Sauvegarder Brouillon] │
  │                                                          [Annuler]   │
  └──────────────────────────────────────────────────────────────────────┘

  ╔══════════════════════════════════════════════════════════════════════╗
  ║  ┌────────────────────────────────┐  ┌────────────────────────────┐ ║
  ║  │ 🌐 Site web de l'événement  S6 │  │ ✉ Email de contact      S6 │ ║
  ║  │  · · https://unige.ch/· · · · │  │  · · contact@unige.ch · · │ ║
  ║  └────────────────────────────────┘  └────────────────────────────┘ ║
  ║  ┌──────────────────────────────────────────────────────────────────┐║
  ║  │ 📅 Date limite d'inscription                                  S6 ││
  ║  │  · · jj/mm/aaaa  [HH] : [MM] · · · · · · · · · · · · · · · · · ││
  ║  └──────────────────────────────────────────────────────────────────┘║
  ║  ┌──────────────────────────────────────────────────────────────────┐║
  ║  │ 🏷️ Mots-clés                                                  S5 ││
  ║  │ · · [conférence ×] [réseau ×] [emploi ×]  Ajoutez… · · · · · · ││
  ║  └──────────────────────────────────────────────────────────────────┘║
  ║  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  ║
  ║  ┌──────────────────────────────────────────────────────────────────┐║
  ║  │ 🔁 Répéter cet événement                                      S8 ││
  ║  │  · · [Toutes les semaines ▾ · · ]  [Répéter jusqu'au · · · · ] ││
  ║  └──────────────────────────────────────────────────────────────────┘║
  ║  ┌──────────────────────────────────────────────────────────────────┐║
  ║  │ 📎 Pièces jointes (PDF, DOCX, slides…)                        S9 ││
  ║  │                    [📎]                                          ││
  ║  │       Glissez vos fichiers ici ou cliquez pour parcourir         ││
  ║  └──────────────────────────────────────────────────────────────────┘║
  ╚══════════════════════════════════════════════════════════════════════╝
  (Bande 4 shells — bord pointillé, fond très légèrement teinté, 40% opacité)

                                    ·· blob blue ··
```

### 10.2 Desktop ≥ 1024px — Mode édition (avec Bande 5)

```
  [Bandes 1, 2, 3, 4 identiques sauf : pas de "Sauvegarder en Brouillon",
   pas de shell Récurrence dans Bande 4]

  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─

  ┌──────────────────────────────────────────────────────────────────────┐
  │  👥 Co-organisateurs                                              S8 │
  │                                                                      │
  │  · · [ 🔍  Inviter un collaborateur… · · · · · · · · · · · · · · ] │
  │                                                                      │
  │  · [Alice Martin  Accepté  ×] · [Bob Chen  En attente  ×] · · · · · │
  └──────────────────────────────────────────────────────────────────────┘
  (Bande 5 — bord pointillé, opacity 40%, pointer-events-none)
```

### 10.3 Mobile < 640px

```
  [Bannière + Titre + Description empilés — inchangé]

  📍 Lieu *
  [__________________________]

  Début *               □ Toute la journée [S5]
  [________] [HH▼]:[MM▼]

  Fin *
  [________] [HH▼]:[MM▼]

  Catégorie *
  [● Choisir une catégorie    ▾]

  Capacité
  [∞]  (sans flèches)

  [CRÉER L'ÉVÉNEMENT (full width)]
  [Sauvegarder en Brouillon]
  [Annuler]

  ╔══ Shells (grille 1 col sur mobile) ═══════════╗
  ║ 🌐 Site web                               S6 ║
  ║ ✉ Email de contact                        S6 ║
  ║ 📅 Date limite d'inscription              S6 ║
  ║ 🏷️ Mots-clés                              S5 ║
  ║ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ║
  ║ 🔁 Répéter (create only)                  S8 ║
  ║ 📎 Pièces jointes                         S9 ║
  ╚════════════════════════════════════════════════╝
```

---

## 11. Design tokens — Référence

| Élément | Classes Tailwind |
|---|---|
| **Bannière — fix** | `pt-7 max-lg:pt-0` sur le div wrapper colonne gauche |
| **Capacité — fix spinners** | `[appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none` |
| **ComingSoonBlock container** | `rounded-2xl border border-dashed border-border/40 bg-foreground/[0.018] px-4 py-3` |
| **ComingSoonBlock icon** | `w-4 h-4 shrink-0 text-foreground/30` |
| **ComingSoonBlock label** | `text-sm text-foreground/30` |
| **ComingSoonBlock badge** | `text-[10px] font-semibold tracking-widest uppercase text-foreground/20 bg-foreground/5 px-2 py-0.5 rounded-full border border-border/30 shrink-0` |
| **ComingSoonBlock body** | `mt-3 pointer-events-none select-none opacity-30` |
| **allDay checkbox (shell)** | `opacity-25 accent-accent w-3.5 h-3.5 cursor-not-allowed` |
| **allDay label** | `flex items-center gap-1.5 text-xs font-normal text-foreground/25 cursor-not-allowed select-none pointer-events-none` |
| **Separator intra-Bande 4** | `border-t border-border/20` |
| **Bande 5 separator** | `border-t border-border/30 pt-6` |
| **Co-org mock search** | `pointer-events-none select-none opacity-40 max-w-sm` |
| **Co-org mock chips** | `pointer-events-none select-none opacity-35` |
| **Mock chips** | `inline-flex items-center gap-2 px-3 py-1 rounded-xl border border-dashed border-border/40 text-sm text-foreground/25` |
| **Mock input fields** | `rounded-xl border border-border/30 px-3 py-2 text-xs text-foreground/20 bg-transparent` |

---

## 12. Responsive — Tableau des breakpoints

| Breakpoint | B1 fix | B2 allDay | B4 shells | B4 récurrence | B5 co-org |
|---|---|---|---|---|---|
| `>= lg` (≥ 1024px) | `pt-7` actif | Inline après "Début *" | `grid-cols-2` pour URL+email, rest full | Visible si create | Visible si edit |
| `sm`→`lg` (640–1023px) | `pt-7` actif | Inline après "Début *" | `grid-cols-2` | Visible si create | Visible si edit |
| `< sm` (< 640px) | `max-lg:pt-0` annule | Reste inline (réduit) | `grid-cols-1` tous | Visible si create | Visible si edit |

---

## 13. Checklist d'implémentation

### Corrections immédiates

- [ ] `EventForm.tsx` — Bande 1 colonne gauche : ajouter `pt-7 max-lg:pt-0`
- [ ] `EventForm.tsx` — Input Capacité : ajouter les 3 classes `[appearance:textfield]` + webkit
- [ ] Vérifier visuellement : bannière aligne avec l'input Titre (pas le label) à 1280px
- [ ] Vérifier visuellement : input Capacité sans flèches

### ComingSoonBlock

- [ ] Définir `ComingSoonBlockProps` dans `EventForm.tsx` (local, non exporté)
- [ ] Définir `comingSoonVariants` const map (jamais de ternaire inline)
- [ ] Définir `function ComingSoonBlock(...)` (local, non exporté)
- [ ] Ajouter `type { LucideIcon }` à l'import lucide-react

### Imports lucide-react

- [ ] Ajouter `Globe, Mail, CalendarClock, Tag, Repeat, Paperclip, Users, Search` à l'import lucide

### Activation de `mode`

- [ ] Renommer `_mode` → `mode` dans la déstructuration de `EventForm`
- [ ] Utiliser `mode === 'create'` pour le shell Récurrence dans Bande 4
- [ ] Utiliser `mode === 'edit'` pour la Bande 5

### Shell Bande 2 — allDay

- [ ] Remplacer `renderDateTimeField('startDate', 'Début', ...)` par le rendu inline avec checkbox shell
- [ ] Le `<FormField>` pour Début reçoit un `label` JSX (span flex avec checkbox inline)
- [ ] `required={false}` sur ce FormField (l'astérisque est dans le span)
- [ ] Checkbox `disabled` + `pointer-events-none`

### Shell Bande 4

- [ ] Wrapper `<div className="flex flex-col gap-3">` après Bande 3
- [ ] Grille 2 colonnes websiteUrl + contactEmail (ComingSoonBlock avec mock input)
- [ ] ComingSoonBlock deadline pleine largeur (mock grille date + heures)
- [ ] ComingSoonBlock tags pleine largeur (mock chips)
- [ ] `<div className="border-t border-border/20" />` separator
- [ ] ComingSoonBlock récurrence : conditionnel `mode === 'create'`, mock grille fréquence + date fin
- [ ] ComingSoonBlock pièces jointes : toujours visible, mock drop-zone

### Shell Bande 5

- [ ] Conditionnel `mode === 'edit'` autour de la Bande 5
- [ ] En-tête : icône Users + label + badge S8
- [ ] Mock champ recherche (`pointer-events-none opacity-40`)
- [ ] 2 mock chips co-org (`pointer-events-none opacity-35`)

### Qualité

- [ ] `npm run lint` — zéro erreur
- [ ] `npm run build` — zéro erreur TypeScript
- [ ] `npm run test` — tests existants verts (aucun test shell à écrire)
- [ ] Test visuel create mode 1280px : bannière alignée, shells B4 visibles, pas de B5
- [ ] Test visuel edit mode 1280px : bannière alignée, shells B4 sans récurrence, B5 visible
- [ ] Test visuel mobile 375px : grille B4 en 1 colonne, shells empilés proprement

---

## 14. Prompt d'implémentation

```
Implémente les specs de `specs_archives/specs_claude/rework/SPEC_event_form_audit_v1.md`
en intégralité.

## Contexte projet

Frontend UNIGE Events — React 19 / TypeScript strict / TailwindCSS v4 / Vite.
Conventions critiques dans `frontend/AGENTS.md` :
- Zéro ternaire inline pour les variantes visuelles → const maps typées
- Design tokens uniquement (bg-background, text-foreground, border-border, text-error, border-accent…)
- Composant extrait dès qu'il apparaît 2 fois (mais reste local si utilisé dans un seul fichier)
- Imports @/ uniquement

## État actuel du formulaire

Le formulaire `EventForm.tsx` est en layout v3 (3 bandes, CategorySelect, pas de Select Statut).
Il accepte déjà une prop `mode: 'create' | 'edit'` (déstructurée comme `_mode` — inutilisée pour l'instant).
Les pages `EventCreatePage.tsx` et `EventEditPage.tsx` passent déjà `mode="create"` / `mode="edit"`.

## Ce qu'il faut faire (dans cet ordre)

1. **Fix bannière** : ajouter `pt-7 max-lg:pt-0` sur le div wrapper de la colonne gauche de la Bande 1
   (ligne ~150 dans EventForm.tsx : `<div className="flex flex-col gap-3">`)

2. **Fix spinners Capacité** : ajouter sur l'Input Capacité (Bande 3) :
   `className="[appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"`

3. **Activer `mode`** : renommer `_mode` → `mode` dans la déstructuration EventForm

4. **Composant local `ComingSoonBlock`** : définir dans EventForm.tsx (non exporté) avec
   `comingSoonVariants` const map — voir §5 de la spec

5. **Shell allDay Bande 2** : remplacer l'appel `renderDateTimeField('startDate', 'Début', ...)`
   par un FormField avec label JSX inline contenant une checkbox disabled + badge S5 — voir §6

6. **Bande 4 complète** : insérer entre Bande 3 et `{errors.image}` — voir §7
   Nouveaux imports lucide : Globe, Mail, CalendarClock, Tag, Repeat, Paperclip
   (en plus des icônes déjà importées)

7. **Bande 5** : insérer après Bande 4, conditionnel `mode === 'edit'` — voir §8
   Nouveaux imports lucide : Users, Search
   + `type { LucideIcon }` pour le type de ComingSoonBlock

## Fichiers à modifier

- `frontend/src/components/event/EventForm.tsx` — seul fichier à modifier
- `frontend/docs/components.md` — mettre à jour la description de EventForm

## Contraintes

- Les shells sont NON-INTERACTIFS : `pointer-events-none`, `select-none`, éléments `disabled`
- Aucun état React ajouté (pas de useState pour les shells)
- Aucun test à écrire ou modifier (les shells ne sont pas testés)
- Zéro valeur de couleur brute → `border-border/40`, `text-foreground/30`, etc.
- `[appearance:textfield]` est une Tailwind arbitrary property (Tailwind v4 compatible)

## Vérification finale

`npm run lint && npm run build && npm run test` doivent passer au vert.
Lire EventForm.tsx avant de le modifier.
```
