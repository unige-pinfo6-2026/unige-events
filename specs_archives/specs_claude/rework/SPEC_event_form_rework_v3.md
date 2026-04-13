# SPEC v3 — Formulaire événement : CategorySelect, suppression Statut, et emplacements backlog S5→S8

> Auteur : Claude  
> Date : 2026-04-11  
> Remplace partiellement : `SPEC_event_form_rework_v2.md` (le layout 3 bandes reste identique)  
> Statut : **À implémenter**

---

## 1. Vue d'ensemble

### Objectif

Cette spec couvre deux niveaux :

**Niveau 1 — Ajustements immédiats** (aucune dépendance backend, implémentables maintenant) :
- Remplacer `CategoryPills` (pills/boutons) par `CategorySelect` (menu déroulant avec color dot). La croissance des catégories rend les pills impraticables.
- Supprimer le `<Select>` Statut de la Bande 3. Le workflow de publication est désormais : submit principal → `PUBLISHED`, "Sauvegarder en Brouillon" → `DRAFT`.

**Niveau 2 — Réservations d'emplacements** (UI shell sans logique backend ni appels API) :
- S5 : Checkbox "Toute la journée" dans la Bande 2
- S5/S6 : Bande 4 "Détails additionnels" (websiteUrl, contactEmail, registrationDeadline, tags + récurrence)
- S5/S6 : Composant `TagInput` réutilisable
- S7 : Bande 5 "Co-organisateurs" (mode édition uniquement)
- S8 : Section récurrence dans la Bande 4 (mode création uniquement)

### Ce qui change par rapport à la v2

| Aspect | v2 (implémentée) | v3 (cette spec) |
|---|---|---|
| Sélecteur catégorie | `CategoryPills` (pills toggle) | `CategorySelect` (dropdown + color dot) |
| Statut | `<Select>` Brouillon/Publié dans Bande 3 | **Supprimé** — submit = PUBLISHED, draft = lien texte |
| `DEFAULT_VALUES.status` | `'DRAFT'` | `'PUBLISHED'` |
| Nombre de bandes | 3 | 3 + Bande 4 (shell) + Bande 5 edit-only (shell) |
| Checkbox allDay | Absent | Shell dans Bande 2 (SCRUM-125) |
| Champs additionnels | Absents | Shell Bande 4 : url, email, deadline, tags (SCRUM-127) |
| TagInput | Absent | Nouveau composant utils (SCRUM-128) |
| Co-organisateurs | Absent | Shell Bande 5 edit-only (SCRUM-137) |
| Récurrence | Absent | Shell dans Bande 4 create-only (SCRUM-147) |

### Ce qui ne change PAS

- Header, footer, fond de page (`BlobsSubtle`), `SectionWrapper`, `SectionHeader`
- Layout Bandes 1, 2, 3 (à l'exception du remplacement CategoryPills → CategorySelect et suppression Statut)
- `useEventForm` : seul `DEFAULT_VALUES.status` change, tout le reste est intact
- `triggerDraftSave()` — mécanisme inchangé
- Callbacks, validation, toast, redirections
- `EventCreatePage.tsx` et `EventEditPage.tsx` — structure inchangée

---

## 2. Périmètre des fichiers

| Fichier | Modifications |
|---|---|
| `src/components/event/CategorySelect.tsx` | **Nouveau composant** — remplace CategoryPills |
| `src/components/event/CategoryPills.tsx` | **Supprimé** (ou conservé pour usage futur hors formulaire) |
| `src/components/event/EventForm.tsx` | Bande 3 : swap CategoryPills → CategorySelect, retrait Select Statut ; ajout Bandes 4 et 5 (shells) |
| `src/components/utils/TagInput.tsx` | **Nouveau composant** réutilisable |
| `src/hooks/useEventForm.ts` | `DEFAULT_VALUES.status` : `'DRAFT'` → `'PUBLISHED'` |
| `src/types/event.ts` | Ajout types `CoOrganizerSlot`, `CoOrganizerStatus`, `RecurrenceFrequency` (shells) |
| `src/__tests__/hooks/useEventForm.test.tsx` | Adapter les assertions sur `status` (voir §9) |
| `src/__tests__/pages/event/EventCreatePage.test.tsx` | Retrait assertions Select Statut (voir §9) |

---

## 3. Composant `CategorySelect` — Nouveau

### Fichier

`src/components/event/CategorySelect.tsx`

### Raison du remplacement

Avec l'augmentation prévue du nombre de catégories (S6+), les pills débordent sur plusieurs lignes et deviennent visuellement lourdes dans la Bande 3. Un `<select>` stylé avec un indicateur de couleur offre la même information en une ligne compacte.

### Interface TypeScript

```typescript
interface CategorySelectProps {
  value: '' | EventCategory
  onChange: (category: EventCategory) => void
  error?: string
}
```

Identique à `CategoryPillsProps` — remplacement transparent dans `EventForm`.

### Comportement

- Affiche `<select>` avec une option vide en tête ("Choisir une catégorie…") si `value === ''`
- Chaque `<option>` affiche le nom de la catégorie ; la color dot est rendue via un pseudo-élément CSS ou un élément inline précédant le select
- Le select natif ne permet pas d'injecter du HTML dans les `<option>` sur tous les navigateurs → la color dot est affichée dans un `<span>` coloré **à gauche du `<select>`**, à l'intérieur d'un wrapper `relative`
- La couleur du dot reflète `EVENT_CATEGORIES[value].color` pour la valeur sélectionnée (aucun dot si `value === ''`)
- Si `error` fourni → bordure `border-error` sur le select + texte `text-xs text-error mt-1.5`
- Pattern variants obligatoire pour les états du select (défaut, erreur)

### Styles

```tsx
// Const map variants — jamais de ternaire inline (AGENTS.md)
const selectVariants = {
  default: 'border-border bg-background text-foreground focus:border-accent/60',
  error:   'border-error bg-background text-foreground focus:border-error',
} as const

// Dot couleur de la catégorie sélectionnée
const dotColor: string = value ? EVENT_CATEGORIES[value].color : 'transparent'
```

Classes du wrapper : `relative flex items-center gap-2`  
Classes du dot : `w-2.5 h-2.5 rounded-full shrink-0 transition-colors` (couleur via style inline `backgroundColor: dotColor`)  
Classes du select : `w-full appearance-none rounded-xl border px-3 py-2 text-sm transition-colors outline-none cursor-pointer pr-8 ${selectVariants[error ? 'error' : 'default']}`  
Icône chevron droite : `ChevronDown` positionné en `absolute right-2 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground/40 pointer-events-none`

### Rendu JSX complet

```tsx
import { ChevronDown } from 'lucide-react'
import type { EventCategory } from '@/types/event'
import { EVENT_CATEGORIES } from '@/types/event'

interface CategorySelectProps {
  value: '' | EventCategory
  onChange: (category: EventCategory) => void
  error?: string
}

const selectVariants = {
  default: 'border-border bg-background text-foreground focus:border-accent/60',
  error:   'border-error bg-background text-foreground focus:border-error',
} as const

export default function CategorySelect({ value, onChange, error }: Readonly<CategorySelectProps>) {
  const dotColor = value ? EVENT_CATEGORIES[value].color : 'transparent'

  return (
    <div>
      <div className="relative flex items-center gap-2">
        <span
          className="w-2.5 h-2.5 rounded-full shrink-0 transition-colors"
          style={{ backgroundColor: dotColor }}
          aria-hidden="true"
        />
        <div className="relative flex-1">
          <select
            value={value}
            onChange={(e) => { if (e.target.value) onChange(e.target.value as EventCategory) }}
            className={`w-full appearance-none rounded-xl border px-3 py-2 text-sm transition-colors outline-none cursor-pointer pr-8 ${selectVariants[error ? 'error' : 'default']}`}
          >
            <option value="">Choisir une catégorie…</option>
            {Object.entries(EVENT_CATEGORIES).map(([id, cat]) => (
              <option key={id} value={id}>{cat.name}</option>
            ))}
          </select>
          <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground/40 pointer-events-none" />
        </div>
      </div>
      {error && <p className="text-xs text-error mt-1.5">{error}</p>}
    </div>
  )
}
```

---

## 4. Suppression du Select Statut — Impact complet

### 4.1 Dans `EventForm.tsx`

**Supprimer** le bloc suivant de la Bande 3 :

```tsx
// SUPPRIMER — Select Statut
<FormField label="Statut" htmlFor="event-status" className="w-36 flex-none">
  <Select
    id="event-status"
    value={values.status}
    onChange={(e) => onFieldChange('status', e.target.value as EventFormValues['status'])}
  >
    {Object.entries(EVENT_STATUSES).filter(([id]) => id !== 'CANCELLED').map(([id, s]) => (
      <option key={id} value={id}>{s.name}</option>
    ))}
  </Select>
</FormField>
```

**Supprimer également** l'import `EVENT_STATUSES` de `@/types/event` s'il n'est plus utilisé ailleurs dans le fichier.

### 4.2 Dans `useEventForm.ts`

```typescript
// AVANT
const DEFAULT_VALUES: EventFormValues = {
  // ...
  status: "DRAFT",
}

// APRÈS
const DEFAULT_VALUES: EventFormValues = {
  // ...
  status: 'PUBLISHED',
}
```

**Logique après ce changement :**
- `handleSubmit` standard → crée/met à jour avec `status: 'PUBLISHED'` par défaut
- `triggerDraftSave()` → force `forcedStatusRef.current = 'DRAFT'` avant submit → résultat `status: 'DRAFT'`
- `toFormValues(event)` pour le mode édition → toujours `event.status` (inchangé — on ne touche pas aux events existants)

**`triggerDraftSave` reste intact** — le mécanisme `forcedStatusRef` est indépendant de `DEFAULT_VALUES`.

### 4.3 Impact sur les tests — Liste exhaustive

#### `src/__tests__/hooks/useEventForm.test.tsx`

Chercher et adapter tous les tests qui vérifient le payload soumis par `handleSubmit` standard (hors `triggerDraftSave`) :

| Test concerné (pattern à chercher) | Avant | Après |
|---|---|---|
| Test vérifiant `status` dans le payload de `createEvent` lors d'un submit normal | `expect(createEvent).toHaveBeenCalledWith(expect.objectContaining({ status: 'DRAFT' }))` | `expect(createEvent).toHaveBeenCalledWith(expect.objectContaining({ status: 'PUBLISHED' }))` |
| Test vérifiant la valeur initiale de `values.status` à la création | `expect(form.result.current.values.status).toBe('DRAFT')` | `expect(form.result.current.values.status).toBe('PUBLISHED')` |
| Tests `triggerDraftSave` | Inchangés — forcent explicitement `'DRAFT'` | Inchangés |
| Tests mode édition avec `initialEvent.status = 'DRAFT'` | Inchangés — `toFormValues` lit `event.status` | Inchangés |

#### `src/__tests__/pages/event/EventCreatePage.test.tsx`

Chercher et adapter :

| Test concerné | Avant | Après |
|---|---|---|
| Test cherchant le select `event-status` dans le DOM | `expect(screen.getByRole('combobox', { name: /statut/i })).toBeInTheDocument()` | **Supprimer ce test** — l'élément n'existe plus |
| Test changeant la valeur du select Statut | `userEvent.selectOptions(screen.getByLabelText('Statut'), 'PUBLISHED')` | **Supprimer ce test** |
| Test vérifiant le payload avec `status: 'PUBLISHED'` après sélection | Dépend du select | Vérifier que le submit envoie `status: 'PUBLISHED'` sans manipulation préalable |

---

## 5. Bande 3 — Mise à jour (v3)

La Bande 3 après les deux ajustements immédiats :

```tsx
{/* Bande 3 — Catégorie | Capacité | CTA */}
<div className="flex flex-wrap items-end gap-x-6 gap-y-4">

  {/* Catégorie — select avec color dot */}
  <FormField label="Catégorie" htmlFor="event-category" required error={errors.category} className="w-48 flex-none">
    <CategorySelect
      value={values.category}
      onChange={(cat) => onFieldChange('category', cat)}
      error={errors.category}
    />
  </FormField>

  {/* Capacité */}
  <FormField label="Capacité" htmlFor="event-capacity" error={errors.capacity} className="w-24 flex-none">
    <Input
      id="event-capacity"
      type="number"
      min="1"
      step="1"
      value={values.capacity}
      onChange={(e) => onFieldChange('capacity', e.target.value)}
      error={errors.capacity}
      placeholder="∞"
    />
  </FormField>

  {/* Zone CTA — inchangée */}
  <div className="flex flex-col items-end gap-2 ml-auto max-sm:ml-0 max-sm:w-full">
    <ButtonPrimary type="submit" disabled={submitting} size="md">
      {submitting ? 'Enregistrement...' : submitLabel}
    </ButtonPrimary>

    <div className="flex gap-4">
      {onSaveDraft && (
        <button
          type="button"
          onClick={() => { void onSaveDraft() }}
          disabled={submitting}
          className="text-xs text-foreground/40 hover:text-foreground/60 transition-all disabled:opacity-50"
        >
          Sauvegarder en Brouillon
        </button>
      )}
      <button
        type="button"
        onClick={onCancel}
        className="text-xs text-foreground/40 hover:text-foreground/60 transition-all"
      >
        Annuler
      </button>
    </div>
  </div>

</div>
```

**Changements vs v2 :**
- `CategoryPills` → `CategorySelect` (import mis à jour)
- `FormField Catégorie` : `htmlFor=""` → `htmlFor="event-category"`, `className="flex-1 min-w-48"` → `className="w-48 flex-none"` (la largeur fixe est plus appropriée pour un select)
- Bloc `FormField Statut` entièrement supprimé
- Supprimer l'import `Select` si plus utilisé dans la Bande 3 (vérifier si `renderDateTimeField` l'utilise encore — il l'utilise, donc conserver l'import)

---

## 6. Shell Bande 2 — Checkbox "Toute la journée" (SCRUM-125)

### Emplacement

La checkbox est intégrée **dans la Bande 2**, visible à droite du label "Début", au-dessus des sélects d'heure.

### Types à prévoir dans `EventFormValues`

```typescript
// À ajouter dans EventFormValues (useEventForm.ts) lors de SCRUM-125
allDay: boolean   // default: false
```

### Structure JSX du shell (à ajouter lors de SCRUM-125)

```tsx
{/* Modification de renderDateTimeField pour accueillir allDay */}
{/* La checkbox est positionnée dans le label du champ "Début" */}

{/* Dans la Bande 2, remplacer l'appel renderDateTimeField('startDate', ...) par : */}
<FormField
  label={
    <span className="flex items-center justify-between w-full">
      Début
      {/* SLOT allDay — SCRUM-125 */}
      <label className="flex items-center gap-1.5 text-xs font-normal text-foreground/50 cursor-pointer">
        <input
          type="checkbox"
          checked={values.allDay}
          onChange={(e) => onFieldChange('allDay', e.target.checked)}
          className="accent-accent w-3.5 h-3.5"
        />
        Toute la journée
      </label>
    </span>
  }
  htmlFor="event-startDate"
  required
  error={errors.startDate}
>
  <div className="grid grid-cols-[1fr_auto] gap-3 max-sm:grid-cols-1">
    <Input id="event-startDate" type="date" ... />
    {/* Sélects heure : masqués si allDay — className={values.allDay ? 'hidden' : ''} */}
    <div className={`flex items-center gap-1.5 ${values.allDay ? 'invisible' : ''}`}>
      {/* ... selects HH:MM ... */}
    </div>
  </div>
</FormField>
```

**Note de conception :** utiliser `invisible` (garde l'espace) plutôt que `hidden` pour éviter le saut de layout. Les selects heure de "Fin" sont masqués de la même façon si `allDay = true`.

**Logique à implémenter dans SCRUM-125 (pas maintenant) :**
- Si `allDay` passe à `true` : forcer `startDate` à `${datePart}T00:00` et `endDate` à `${datePart}T23:59`
- Validation : si `allDay = true`, ne pas valider les heures

---

## 7. Shell Bande 4 — Détails additionnels (SCRUM-127 / SCRUM-147)

La Bande 4 est une nouvelle section sous la Bande 3. Elle contient deux sous-sections :
1. Les 4 champs additionnels (SCRUM-127)
2. La section récurrence collapsed (SCRUM-147), visible uniquement en mode création

### 7.1 Nouveaux champs dans `EventFormValues` (shell — à activer dans SCRUM-127)

```typescript
// À ajouter dans EventFormValues (useEventForm.ts) lors de SCRUM-127
websiteUrl: string            // default: ''
contactEmail: string          // default: ''
registrationDeadline: string  // default: '' — format identique à startDate/endDate
tags: string[]                // default: []
```

### 7.2 Structure JSX de la Bande 4

```tsx
{/* Bande 4 — Détails additionnels (SCRUM-127) */}
{/* Shell : rendu conditionnel optionnel, peut être déplié via un toggle "Ajouter des détails ▾" */}
<div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1">

  {/* websiteUrl — SCRUM-127 */}
  <FormField label="Site web" htmlFor="event-website" error={errors.websiteUrl}>
    <div className="relative">
      <Globe className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground/30 pointer-events-none" />
      <Input
        id="event-website"
        type="url"
        value={values.websiteUrl}
        onChange={(e) => onFieldChange('websiteUrl', e.target.value)}
        error={errors.websiteUrl}
        placeholder="https://unige.ch/evenement"
        className="pl-10"
      />
    </div>
  </FormField>

  {/* contactEmail — SCRUM-127 */}
  <FormField label="Email de contact" htmlFor="event-contact-email" error={errors.contactEmail}>
    <div className="relative">
      <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground/30 pointer-events-none" />
      <Input
        id="event-contact-email"
        type="email"
        value={values.contactEmail}
        onChange={(e) => onFieldChange('contactEmail', e.target.value)}
        error={errors.contactEmail}
        placeholder="contact@unige.ch"
        className="pl-10"
      />
    </div>
  </FormField>

  {/* registrationDeadline — SCRUM-127 — pleine largeur */}
  <div className="col-span-2 max-sm:col-span-1">
    {renderDateTimeField('registrationDeadline', 'Date limite d\'inscription', 'event-deadline', deadlineDateTime, errors.registrationDeadline)}
  </div>

  {/* tags — SCRUM-128 — pleine largeur */}
  <div className="col-span-2 max-sm:col-span-1">
    <FormField label="Mots-clés" htmlFor="event-tags" error={errors.tags}>
      <TagInput
        value={values.tags}
        onChange={(tags) => onFieldChange('tags', tags)}
        placeholder="Ajoutez des mots-clés…"
        maxTags={10}
      />
    </FormField>
  </div>

  {/* Récurrence — SCRUM-147 — create mode uniquement, pleine largeur */}
  {mode === 'create' && (
    <div className="col-span-2 max-sm:col-span-1">
      <RecurrenceSection
        values={values}
        onFieldChange={onFieldChange}
      />
    </div>
  )}

</div>
```

**Imports à ajouter lors de SCRUM-127 :** `Globe`, `Mail` depuis `lucide-react` ; `TagInput` depuis `@/components/utils/TagInput`.

### 7.3 Prop `mode` à ajouter à `EventFormProps`

```typescript
// Dans EventFormProps (EventForm.tsx)
mode: 'create' | 'edit'  // AJOUT — nécessaire pour afficher/masquer les zones mode-dépendantes
```

- `EventCreatePage.tsx` : passer `mode="create"`
- `EventEditPage.tsx` : passer `mode="edit"`

---

## 8. Composant `TagInput` — Nouveau (SCRUM-128)

### Fichier

`src/components/utils/TagInput.tsx`

### Interface TypeScript

```typescript
interface TagInputProps {
  value: string[]
  onChange: (tags: string[]) => void
  placeholder?: string
  maxTags?: number
  error?: string
}
```

### Comportement

- Affiche les tags existants en chips : texte + bouton `×`
- Input texte inline après les chips : `type="text"`, `placeholder` si fourni
- **Entrée** ou **virgule** → trim le texte → si non vide et non dupliqué et `value.length < maxTags` → ajoute ; vide l'input
- **Backspace sur input vide** → supprime le dernier tag
- **Clic `×`** → supprime le tag correspondant
- Affichage `error` : `text-xs text-error mt-1.5`
- `maxTags` atteint → input masqué (`hidden`) ou désactivé (`disabled`)

### Styles des chips

```tsx
// Const map variants chips
const chipVariants = {
  default: 'bg-foreground/8 border border-border text-foreground/70 hover:border-foreground/30',
} as const

// Chip :
<span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-lg text-xs font-medium transition-colors ${chipVariants.default}`}>
  {tag}
  <button
    type="button"
    onClick={() => removeTag(tag)}
    className="text-foreground/40 hover:text-foreground/70 transition-colors cursor-pointer"
    aria-label={`Supprimer le tag ${tag}`}
  >
    <X className="w-3 h-3" />
  </button>
</span>
```

### Rendu JSX complet

```tsx
import { useRef, useState } from 'react'
import { X } from 'lucide-react'

interface TagInputProps {
  value: string[]
  onChange: (tags: string[]) => void
  placeholder?: string
  maxTags?: number
  error?: string
}

const chipVariants = {
  default: 'bg-foreground/8 border border-border text-foreground/70 hover:border-foreground/30',
} as const

export default function TagInput({ value, onChange, placeholder, maxTags, error }: Readonly<TagInputProps>) {
  const [inputValue, setInputValue] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const isMaxReached = maxTags !== undefined && value.length >= maxTags

  function addTag(raw: string) {
    const tag = raw.trim()
    if (!tag || value.includes(tag) || isMaxReached) {
      setInputValue('')
      return
    }
    onChange([...value, tag])
    setInputValue('')
  }

  function removeTag(tag: string) {
    onChange(value.filter((t) => t !== tag))
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault()
      addTag(inputValue)
    } else if (e.key === 'Backspace' && inputValue === '') {
      onChange(value.slice(0, -1))
    }
  }

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const raw = e.target.value
    if (raw.endsWith(',')) {
      addTag(raw.slice(0, -1))
    } else {
      setInputValue(raw)
    }
  }

  return (
    <div>
      <div
        className="flex flex-wrap gap-1.5 min-h-10 w-full rounded-xl border border-border bg-background px-3 py-2 cursor-text transition-colors focus-within:border-accent/60"
        onClick={() => inputRef.current?.focus()}
      >
        {value.map((tag) => (
          <span
            key={tag}
            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-lg text-xs font-medium transition-colors ${chipVariants.default}`}
          >
            {tag}
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); removeTag(tag) }}
              className="text-foreground/40 hover:text-foreground/70 transition-colors cursor-pointer"
              aria-label={`Supprimer le tag ${tag}`}
            >
              <X className="w-3 h-3" />
            </button>
          </span>
        ))}
        {!isMaxReached && (
          <input
            ref={inputRef}
            type="text"
            value={inputValue}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            placeholder={value.length === 0 ? placeholder : undefined}
            className="flex-1 min-w-20 bg-transparent text-sm text-foreground placeholder:text-foreground/30 outline-none"
          />
        )}
      </div>
      {error && <p className="text-xs text-error mt-1.5">{error}</p>}
    </div>
  )
}
```

---

## 9. Shell Section Récurrence (SCRUM-147)

### Fichier

Composant local `RecurrenceSection` défini dans `EventForm.tsx` (non exporté — utilisé une seule fois dans ce fichier).

### Types à prévoir dans `src/types/event.ts`

```typescript
// À ajouter dans types/event.ts lors de SCRUM-147
export type RecurrenceFrequency = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY'

// À ajouter dans EventFormValues lors de SCRUM-147
recurrence: {
  enabled: boolean
  frequency: RecurrenceFrequency | ''
  endDate: string   // date ISO sans heure
} | null
```

### Props du composant shell

```typescript
interface RecurrenceSectionProps {
  values: EventFormValues        // lit values.recurrence
  onFieldChange: EventFormProps['onFieldChange']
}
```

### Comportement shell

- Toggle (checkbox ou switch) "Répéter cet événement" — si décoché : `recurrence = null`
- Si coché : révèle un sous-groupe en `flex gap-3 flex-wrap mt-3` :
  - `<Select>` fréquence : "Toutes les semaines" / "Toutes les 2 semaines" / "Tous les mois"
  - `<Input type="date">` date de fin de récurrence
- **Visible uniquement en mode création** (`mode === 'create'` dans `EventForm`)
- Section collapsed par défaut (toggle décoché)

### Rendu JSX du shell

```tsx
// Composant local dans EventForm.tsx (non exporté)
const recurrenceFrequencyLabels: Record<RecurrenceFrequency, string> = {
  WEEKLY:    'Toutes les semaines',
  BIWEEKLY:  'Toutes les 2 semaines',
  MONTHLY:   'Tous les mois',
}

function RecurrenceSection({ values, onFieldChange }: RecurrenceSectionProps) {
  const enabled = values.recurrence?.enabled ?? false

  return (
    <div className="border-t border-border pt-4">
      <label className="flex items-center gap-2 cursor-pointer text-sm text-foreground/70 select-none">
        <input
          type="checkbox"
          checked={enabled}
          onChange={(e) => onFieldChange('recurrence', e.target.checked
            ? { enabled: true, frequency: 'WEEKLY', endDate: '' }
            : null
          )}
          className="accent-accent w-4 h-4"
        />
        <Repeat className="w-4 h-4 text-foreground/40" />
        Répéter cet événement
      </label>

      {enabled && (
        <div className="flex gap-3 flex-wrap mt-3 pl-6">
          <FormField label="Fréquence" htmlFor="event-recurrence-freq" className="flex-1 min-w-40">
            <Select
              id="event-recurrence-freq"
              value={values.recurrence?.frequency ?? ''}
              onChange={(e) => onFieldChange('recurrence', {
                ...values.recurrence!,
                frequency: e.target.value as RecurrenceFrequency,
              })}
            >
              <option value="">Choisir…</option>
              {(Object.entries(recurrenceFrequencyLabels) as [RecurrenceFrequency, string][]).map(([freq, label]) => (
                <option key={freq} value={freq}>{label}</option>
              ))}
            </Select>
          </FormField>

          <FormField label="Répéter jusqu'au" htmlFor="event-recurrence-end" className="flex-1 min-w-40">
            <Input
              id="event-recurrence-end"
              type="date"
              value={values.recurrence?.endDate ?? ''}
              onChange={(e) => onFieldChange('recurrence', {
                ...values.recurrence!,
                endDate: e.target.value,
              })}
            />
          </FormField>
        </div>
      )}
    </div>
  )
}
```

**Import à ajouter :** `Repeat` depuis `lucide-react`

---

## 10. Shell Bande 5 — Co-organisateurs (SCRUM-137, mode édition uniquement)

### Types à ajouter dans `src/types/event.ts`

```typescript
// À ajouter dans types/event.ts lors de SCRUM-137
export type CoOrganizerStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED'

export interface CoOrganizerSlot {
  userId: string
  displayName: string
  avatarUrl?: string
  status: CoOrganizerStatus
}
```

### Props à ajouter dans `EventFormProps`

```typescript
// À ajouter dans EventFormProps (EventForm.tsx) lors de SCRUM-137
// Toutes optionnelles — ignorées si non fournies
coOrganizers?: CoOrganizerSlot[]
onAddCoOrganizer?: (userId: string) => Promise<void>
onRemoveCoOrganizer?: (userId: string) => Promise<void>
```

### Structure JSX de la Bande 5

```tsx
{/* Bande 5 — Co-organisateurs (mode édition uniquement — SCRUM-137) */}
{mode === 'edit' && (
  <div className="flex flex-col gap-4 border-t border-border pt-6">

    <p className="text-xs font-bold uppercase tracking-widest text-foreground/30">
      Co-organisateurs
    </p>

    {/* Recherche utilisateur — SCRUM-137 */}
    <div className="relative max-w-sm">
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground/30 pointer-events-none" />
      <Input
        type="search"
        placeholder="Rechercher un utilisateur…"
        className="pl-10"
        /* value et onChange fournis lors de SCRUM-137 */
      />
    </div>

    {/* Liste des co-organisateurs ajoutés */}
    {coOrganizers && coOrganizers.length > 0 && (
      <div className="flex flex-wrap gap-2">
        {coOrganizers.map((co) => (
          <CoOrganizerChip
            key={co.userId}
            coOrganizer={co}
            onRemove={() => onRemoveCoOrganizer?.(co.userId)}
          />
        ))}
      </div>
    )}

    {(!coOrganizers || coOrganizers.length === 0) && (
      <p className="text-sm text-foreground/30">
        Aucun co-organisateur pour l'instant.
      </p>
    )}

  </div>
)}
```

### Composant local `CoOrganizerChip`

```tsx
// Composant local dans EventForm.tsx (non exporté)
const coOrgStatusVariants: Record<CoOrganizerStatus, string> = {
  PENDING:  'bg-foreground/8 text-foreground/50 border-border',
  ACCEPTED: 'bg-accent/10 text-accent border-accent/30',
  DECLINED: 'bg-error/10 text-error border-error/30',
} as const

const coOrgStatusLabels: Record<CoOrganizerStatus, string> = {
  PENDING:  'En attente',
  ACCEPTED: 'Accepté',
  DECLINED: 'Refusé',
} as const

function CoOrganizerChip({
  coOrganizer,
  onRemove,
}: {
  coOrganizer: CoOrganizerSlot
  onRemove: () => void
}) {
  return (
    <span className={`inline-flex items-center gap-2 px-3 py-1 rounded-xl border text-sm transition-colors ${coOrgStatusVariants[coOrganizer.status]}`}>
      <span className="font-medium">{coOrganizer.displayName}</span>
      <span className="text-xs opacity-70">{coOrgStatusLabels[coOrganizer.status]}</span>
      <button
        type="button"
        onClick={onRemove}
        className="opacity-50 hover:opacity-80 transition-opacity cursor-pointer"
        aria-label={`Retirer ${coOrganizer.displayName}`}
      >
        <X className="w-3.5 h-3.5" />
      </button>
    </span>
  )
}
```

**Import à ajouter :** `Search` depuis `lucide-react`

---

## 11. `EventFormProps` — Interface complète v3

```typescript
interface EventFormProps {
  // --- Existants (inchangés) ---
  submitLabel: string
  values: EventFormValues
  errors: EventFormErrors
  submitting: boolean
  imagePreview: string | null
  selectedImageName: string | null
  onFieldChange: <K extends keyof EventFormValues>(field: K, value: EventFormValues[K]) => void
  onImageChange: (event: ChangeEvent<HTMLInputElement>) => void
  onSubmit: (event: FormSubmitEvent) => Promise<void>
  onCancel: () => void
  onSaveDraft?: () => Promise<void>

  // --- Nouveaux (v3) ---
  mode: 'create' | 'edit'                              // SCRUM-125/127/147 — requis

  // Co-organisateurs (SCRUM-137) — optionnels
  coOrganizers?: CoOrganizerSlot[]
  onAddCoOrganizer?: (userId: string) => Promise<void>
  onRemoveCoOrganizer?: (userId: string) => Promise<void>
}
```

**Impact sur `EventCreatePage.tsx` :** ajouter `mode="create"`.  
**Impact sur `EventEditPage.tsx` :** ajouter `mode="edit"`.

---

## 12. Wireframes ASCII v3

### 12.1 Desktop (≥ 1024px) — Mode création

```
┌──────────────────────────────────────────────────────────────────┐
│ [Navbar]                                                         │
└──────────────────────────────────────────────────────────────────┘
                                                   ·· blob pink ··

  Créer un événement
  ───────────────────────────────────────

  ┌──────────────────────┐   ┌──────────────────────────────────────┐
  │                      │   │  Titre *                             │
  │    [↑ upload icon]   │   │  [__________________________________]│
  │                      │   │                              0 / 120 │
  │  Ajoutez une image   │   │                                      │
  │  PNG/JPG — max 5 Mo  │   │  Description                         │
  │                      │   │  [__________________________________]│
  │                      │   │  [__________________________________]│
  │                      │   │  [__________________________________]│
  │                      │   │                            0 / 2000  │
  └──────────────────────┘   └──────────────────────────────────────┘

  ┌──────────────────────────┬────────────────────────┬────────────┐
  │  📍 Lieu *               │  Début *  [Toute la j.]│  Fin *     │
  │  [______________________]│  [________]            │  [________]│
  │                          │  [HH ▼]:[MM ▼]         │  [HH]:[MM] │
  └──────────────────────────┴────────────────────────┴────────────┘

  ┌───────────────────────────────────────────────────────┐
  │  Catégorie *              Capacité                    │
  │  [● Académique      ▾]    [___]                       │
  │                                      [CRÉER L'ÉV.]   │
  │                          [Sauvegarder en Brouillon]   │
  │                                          [Annuler]    │
  └───────────────────────────────────────────────────────┘

  ┌───────────────────────────────────────────────────────┐
  │  Site web                   Email de contact          │
  │  [🌐 ___________________]   [✉ ___________________]  │
  │                                                       │
  │  Date limite d'inscription                            │
  │  [________] [HH ▼]:[MM ▼]                            │
  │                                                       │
  │  Mots-clés                                            │
  │  [python × ] [unige × ] [________________]           │
  │                                                       │
  │  ─────────────────────────────────────────────────    │
  │  □ 🔁 Répéter cet événement                          │
  │    (si coché → [Fréquence ▾]  [Jusqu'au ________])   │
  └───────────────────────────────────────────────────────┘

                               ·· blob blue ··
```

### 12.2 Desktop — Mode édition (avec Bande 5)

```
  [... Bandes 1, 2, 3, 4 identiques sauf : pas de "Sauvegarder en Brouillon", pas de récurrence ...]

  ┌───────────────────────────────────────────────────────┐
  │  CO-ORGANISATEURS                                     │
  │                                                       │
  │  [🔍 Rechercher un utilisateur…]                     │
  │                                                       │
  │  [Alice Martin  En attente  ×]  [Bob Chen Accepté ×] │
  └───────────────────────────────────────────────────────┘
```

### 12.3 Mobile (< 640px)

```
┌──────────────────────────────────┐
│ [Navbar]                         │

  Créer un
  événement
  ─────────────────────────────

  ┌────────────────────────────┐
  │    [↑]  Ajoutez une image  │
  └────────────────────────────┘

  Titre *
  [__________________________]
                       0 / 120

  Description
  [__________________________]
                     0 / 2000

  📍 Lieu *
  [__________________________]

  Début *              □ Toute la journée
  [____] [HH ▼]:[MM ▼]

  Fin *
  [____] [HH ▼]:[MM ▼]

  Catégorie *
  [● Académique            ▾]

  Capacité
  [____]

  [CRÉER L'ÉVÉNEMENT (full width)]
  [Sauvegarder en Brouillon]
  [Annuler]

  ─────────────────────────────

  Site web
  [🌐 ___________________________]

  Email de contact
  [✉ ___________________________]

  Date limite d'inscription
  [________] [HH ▼]:[MM ▼]

  Mots-clés
  [python ×] [unige ×]
  [___________________________]

  □ 🔁 Répéter cet événement

  ─────────────────────────────
  (mode édition uniquement)
  CO-ORGANISATEURS
  [🔍 Rechercher...]
  [Alice En attente ×]
```

---

## 13. Design tokens — Référence complète v3

| Élément | Classes Tailwind |
|---|---|
| **Bandes 1, 2, 3** | Inchangées vs v2 |
| **Bande 4** | `grid grid-cols-2 gap-4 max-sm:grid-cols-1` |
| **Bande 5** | `flex flex-col gap-4 border-t border-border pt-6` |
| **CategorySelect wrapper** | `relative flex items-center gap-2` |
| **CategorySelect dot** | `w-2.5 h-2.5 rounded-full shrink-0 transition-colors` (couleur inline) |
| **CategorySelect** | `w-full appearance-none rounded-xl border px-3 py-2 text-sm transition-colors outline-none cursor-pointer pr-8` |
| **CategorySelect default** | `border-border bg-background text-foreground focus:border-accent/60` |
| **CategorySelect error** | `border-error bg-background text-foreground focus:border-error` |
| **TagInput container** | `flex flex-wrap gap-1.5 min-h-10 w-full rounded-xl border border-border bg-background px-3 py-2 cursor-text transition-colors focus-within:border-accent/60` |
| **Tag chip** | `inline-flex items-center gap-1 px-2 py-0.5 rounded-lg text-xs font-medium bg-foreground/8 border border-border text-foreground/70 hover:border-foreground/30 transition-colors` |
| **Tag input** | `flex-1 min-w-20 bg-transparent text-sm text-foreground placeholder:text-foreground/30 outline-none` |
| **Récurrence toggle label** | `flex items-center gap-2 cursor-pointer text-sm text-foreground/70 select-none` |
| **Récurrence sous-section** | `flex gap-3 flex-wrap mt-3 pl-6` |
| **Co-org section header** | `text-xs font-bold uppercase tracking-widest text-foreground/30` |
| **Co-org chip PENDING** | `bg-foreground/8 text-foreground/50 border-border` |
| **Co-org chip ACCEPTED** | `bg-accent/10 text-accent border-accent/30` |
| **Co-org chip DECLINED** | `bg-error/10 text-error border-error/30` |
| **Co-org chip base** | `inline-flex items-center gap-2 px-3 py-1 rounded-xl border text-sm transition-colors` |
| **Champ avec icône (Bande 4)** | `absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground/30 pointer-events-none` + `className="pl-10"` |
| **Erreur** | `text-xs text-error mt-1.5` |

---

## 14. Suppressions vs v2

```tsx
// SUPPRIMER dans EventForm.tsx — Select Statut (Bande 3)
<FormField label="Statut" htmlFor="event-status" className="w-36 flex-none">
  <Select
    id="event-status"
    value={values.status}
    onChange={(e) => onFieldChange('status', e.target.value as EventFormValues['status'])}
  >
    {Object.entries(EVENT_STATUSES).filter(([id]) => id !== 'CANCELLED').map(([id, s]) => (
      <option key={id} value={id}>{s.name}</option>
    ))}
  </Select>
</FormField>

// SUPPRIMER dans EventForm.tsx — import CategoryPills
import CategoryPills from '@/components/event/CategoryPills'

// REMPLACER par
import CategorySelect from '@/components/event/CategorySelect'

// SUPPRIMER dans useEventForm.ts — DEFAULT_VALUES.status = 'DRAFT'
status: "DRAFT",
// REMPLACER par
status: 'PUBLISHED',
```

---

## 15. Responsive — Tableau des breakpoints pour chaque bande

| Breakpoint | B1 | B2 | B3 | B4 | B5 |
|---|---|---|---|---|---|
| `>= lg` (≥ 1024px) | `grid-cols-[2fr_3fr]` | `grid-cols-[2fr_1fr_1fr]` | `flex-wrap` ligne | `grid-cols-2` | `flex-col` |
| `sm` à `lg` (640–1023px) | `grid-cols-1` (bannière haut) | `grid-cols-[2fr_1fr_1fr]` | `flex-wrap` wrap | `grid-cols-2` | `flex-col` |
| `< sm` (< 640px) | `grid-cols-1` | `grid-cols-1` | `flex-col` + CTA full | `grid-cols-1` (`col-span-2` → `col-span-1`) | `flex-col` |

**Bande 4 responsive :**
- Sur `< sm` : `grid-cols-1` — `registrationDeadline` et `tags` perdent leur `col-span-2` (qui se comporte comme `col-span-1` dans une grille à 1 colonne)
- `renderDateTimeField` pour `registrationDeadline` : la grille interne `grid-cols-[1fr_auto]` passe à `grid-cols-1` sur `max-sm` (déjà géré par la fonction existante)

---

## 16. Checklist d'implémentation

### Ajustements immédiats (aucune dépendance backend)

#### `CategorySelect.tsx` — Nouveau composant
- [ ] Créer `src/components/event/CategorySelect.tsx`
- [ ] Typer `CategorySelectProps` identique à `CategoryPillsProps`
- [ ] Implémenter `selectVariants` const map (jamais de ternaire inline)
- [ ] Rendu : dot coloré inline + `<select>` natif + `ChevronDown` absolu
- [ ] La couleur du dot reflète `EVENT_CATEGORIES[value].color` via style inline
- [ ] Afficher l'option vide "Choisir une catégorie…" si `value === ''`
- [ ] Message d'erreur `text-xs text-error mt-1.5`

#### `useEventForm.ts` — Modification minimale
- [ ] Changer `DEFAULT_VALUES.status` de `'DRAFT'` à `'PUBLISHED'`
- [ ] Vérifier que `triggerDraftSave` n'est pas affecté (il force `forcedStatusRef.current = 'DRAFT'` explicitement)

#### `EventForm.tsx` — Ajustements Bande 3
- [ ] Remplacer l'import `CategoryPills` par `CategorySelect`
- [ ] Remplacer `<CategoryPills>` par `<CategorySelect>` dans la Bande 3
- [ ] Mettre à jour `FormField` de Catégorie : `htmlFor="event-category"`, `className="w-48 flex-none"`
- [ ] Supprimer le `<FormField label="Statut">` entier
- [ ] Vérifier que l'import `Select` reste (il est encore utilisé dans `renderDateTimeField`)
- [ ] Supprimer l'import `EVENT_STATUSES` si non utilisé ailleurs

#### `EventForm.tsx` — Prop `mode`
- [ ] Ajouter `mode: 'create' | 'edit'` à `EventFormProps`
- [ ] `EventCreatePage.tsx` : passer `mode="create"` à `<EventForm>`
- [ ] `EventEditPage.tsx` : passer `mode="edit"` à `<EventForm>`

#### Tests
- [ ] `useEventForm.test.tsx` : adapter `status: 'DRAFT'` → `status: 'PUBLISHED'` pour les submits standards
- [ ] `useEventForm.test.tsx` : vérifier que `triggerDraftSave` est toujours testé avec `status: 'DRAFT'`
- [ ] `EventCreatePage.test.tsx` : supprimer assertions sur l'existence du Select Statut
- [ ] Vérifier `npm run lint`, `npm run build`, `npm run test`

---

### Shells backlog (à implémenter sprint par sprint)

#### `TagInput.tsx` — Nouveau composant (SCRUM-128)
- [ ] Créer `src/components/utils/TagInput.tsx`
- [ ] Props : `value`, `onChange`, `placeholder`, `maxTags`, `error`
- [ ] Entrée/virgule → ajoute ; Backspace vide → supprime dernier ; clic × → supprime
- [ ] `chipVariants` const map
- [ ] Input masqué si `maxTags` atteint
- [ ] Message d'erreur `text-xs text-error mt-1.5`
- [ ] Tests : ajout tag, suppression, limite maxTags, backspace

#### `EventForm.tsx` — Bande 4 (SCRUM-127)
- [ ] Ajouter les champs `websiteUrl`, `contactEmail`, `registrationDeadline`, `tags` à `EventFormValues`
- [ ] Ajouter les champs à `DEFAULT_VALUES`
- [ ] Ajouter les champs à `EventFormErrors`
- [ ] Implémenter la grille Bande 4 `grid-cols-2 gap-4 max-sm:grid-cols-1`
- [ ] Import `Globe`, `Mail` depuis `lucide-react`
- [ ] Import `TagInput` depuis `@/components/utils/TagInput`
- [ ] `renderDateTimeField` pour `registrationDeadline` : ajouter `deadlineDateTime = splitDateTime(values.registrationDeadline)`
- [ ] Section récurrence : composant local `RecurrenceSection` (SCRUM-147)

#### `EventForm.tsx` — Shell récurrence (SCRUM-147)
- [ ] Ajouter type `RecurrenceFrequency` dans `src/types/event.ts`
- [ ] Ajouter `recurrence` dans `EventFormValues`
- [ ] Composant local `RecurrenceSection` (non exporté)
- [ ] `recurrenceFrequencyLabels` const map
- [ ] Visibilité conditionnelle `mode === 'create'`
- [ ] Import `Repeat` depuis `lucide-react`

#### `EventForm.tsx` — Shell Bande 5 co-org (SCRUM-137)
- [ ] Ajouter `CoOrganizerStatus`, `CoOrganizerSlot` dans `src/types/event.ts`
- [ ] Ajouter `mode`, `coOrganizers?`, `onAddCoOrganizer?`, `onRemoveCoOrganizer?` à `EventFormProps`
- [ ] Composant local `CoOrganizerChip` (non exporté)
- [ ] `coOrgStatusVariants`, `coOrgStatusLabels` const maps
- [ ] Bande 5 visible uniquement `mode === 'edit'`
- [ ] Import `Search` depuis `lucide-react`
- [ ] `EventEditPage.tsx` : passer `coOrganizers` + callbacks quand SCRUM-137 est implémenté

#### `docs/components.md`
- [ ] Mettre à jour l'entrée `CategoryPills` → `CategorySelect`
- [ ] Ajouter l'entrée `TagInput` dans la section Composants utils
- [ ] Mettre à jour la description de `EventForm` (bandes 4 et 5)

---

## 17. Prompt d'implémentation prêt à copier-coller

---

```
Implémente les ajustements immédiats définis dans
`specs_archives/specs_claude/rework/SPEC_event_form_rework_v3.md`,
sections §3, §4, §5 uniquement (CategorySelect + suppression Statut).

## Contexte

Frontend React 19 / TypeScript strict / TailwindCSS v4. Le formulaire est en layout
3 bandes (SPEC v2 déjà implémentée). Deux changements immédiats sont demandés :
1. Remplacer `CategoryPills` par `CategorySelect` (dropdown + color dot) dans la Bande 3
2. Supprimer le `<Select>` Statut de la Bande 3 + changer DEFAULT_VALUES.status en 'PUBLISHED'

## Fichiers à créer ou modifier (dans cet ordre)

1. `frontend/src/components/event/CategorySelect.tsx` — nouveau composant (voir §3)
2. `frontend/src/hooks/useEventForm.ts` — une seule ligne : `status: 'DRAFT'` → `status: 'PUBLISHED'`
3. `frontend/src/components/event/EventForm.tsx` — swap CategoryPills → CategorySelect + retrait Select Statut (voir §5)
4. `frontend/src/pages/event/EventCreatePage.tsx` — ajouter prop `mode="create"` à EventForm
5. `frontend/src/pages/event/EventEditPage.tsx` — ajouter prop `mode="edit"` à EventForm
6. Tests : adapter `useEventForm.test.tsx` et `EventCreatePage.test.tsx` (voir §4.3)

## Contraintes impératives

- Zéro ternaire inline pour les variantes visuelles → `selectVariants` const map typée
- Uniquement design tokens (`bg-background`, `border-border`, `text-foreground`, `text-error`, `border-error`, `border-accent/60`)
- La couleur du dot de la catégorie sélectionnée est injectée via `style={{ backgroundColor: dotColor }}`
  (valeur hexa depuis `EVENT_CATEGORIES[value].color`) — c'est la seule valeur inline autorisée
- `mode: 'create' | 'edit'` devient une prop requise de `EventFormProps`
- Lire chaque fichier avant de le modifier
- `npm run lint && npm run build && npm run test` doivent passer au vert

## Ce qu'il ne faut PAS toucher

- La logique de `useEventForm.ts` (validation, submit, draft, image) — une seule ligne change
- Le layout Bandes 1 et 2 — inchangés
- Les callbacks `onSaveDraft`, `triggerDraftSave` — inchangés
- `CategoryPills.tsx` — peut rester (pas de suppression obligatoire), simplement déréférencé depuis EventForm

Commence par lire `frontend/src/components/event/EventForm.tsx` et
`frontend/src/components/event/CategoryPills.tsx` avant tout.
```
