# SPEC v2 — Rework formulaire événement : layout immersif trois bandes

> Auteur : Claude  
> Date : 2026-04-11  
> Référence visuelle : `REF_event_form_gemini_layout.md` + image `Screenshot 2026-04-11 at 20-53-31 Design System Analysis for Website Revamp - Google Gemini.png`  
> Statut : **À implémenter**  
> Remplace partiellement : `SPEC_event_form_rework.md` (le hero SectionHeader et BlobsSubtle restent inchangés)

---

## 1. Vue d'ensemble

### Objectif

Remplacer le formulaire carte verticale (issu de `SPEC_event_form_rework.md`) par un layout en **trois bandes horizontales** inspiré de l'image de référence Gemini. L'idée directrice : le formulaire respire, il n'y a plus de conteneur card visible, les champs flottent sur le fond sombre et s'organisent par proximité spatiale plutôt que par sections nommées et séparateurs.

### Ce qui change par rapport à la v1

| Aspect | v1 (implémentée) | v2 (cette spec) |
|---|---|---|
| Conteneur formulaire | Card glassmorphism | Aucune card — champs flottants |
| Sections | 4 sections nommées + séparateurs | 3 bandes CSS grid |
| Bannière | Section dédiée en bas | Colonne gauche de la Bande 1 |
| Catégorie | `<Select>` dropdown | `CategoryPills` (pills toggle) |
| Lieu + Dates | Sections séparées | Bande 2 : `grid-cols-[2fr_1fr_1fr]` |
| CTA + Cancel | Footer séparé (border-t) | Inline dans la Bande 3 |
| max-w du wrapper | `size="md"` (max-w-3xl) | `size="lg"` (max-w-5xl) |
| "Brouillon" rapide | Absent | Lien texte sous CTA |

### Ce qui ne change PAS

- Header, footer, fond de page (`BlobsSubtle`), `SectionWrapper`, `SectionHeader` — inchangés
- `useEventForm` — quasi inchangé (une seule addition mineure, voir §6)
- `splitDateTime` / `joinDateTime` / `HOUR_OPTIONS` / `MINUTE_OPTIONS` — inchangés
- Callbacks `onFieldChange`, `onImageChange`, `onSubmit`, `onCancel` — inchangés
- `FormField`, `Input`, `Select`, `Textarea` — inchangés
- Validation, gestion erreurs, toast, redirections — inchangés
- `EventCreatePage.tsx` et `EventEditPage.tsx` : seules les props `size="lg"` et `onSaveDraft` changent (voir §7)

---

## 2. Périmètre des fichiers

| Fichier | Modifications |
|---|---|
| `src/components/event/EventForm.tsx` | Restructuration complète de la présentation (logique intouchée) |
| `src/components/event/CategoryPills.tsx` | **Nouveau composant** |
| `src/pages/event/EventCreatePage.tsx` | `size="lg"` sur SectionWrapper + prop `onSaveDraft` |
| `src/pages/event/EventEditPage.tsx` | `size="lg"` sur SectionWrapper |
| `src/hooks/useEventForm.ts` | Ajout d'un export `triggerDraftSave` (voir §6) |

---

## 3. Composant `CategoryPills` — Nouveau

### Fichier

`src/components/event/CategoryPills.tsx`

### Props

```typescript
interface CategoryPillsProps {
  value: '' | EventCategory
  onChange: (category: EventCategory) => void
  error?: string
}
```

### Comportement

- Affiche un pill par entrée de `EVENT_CATEGORIES` (toutes les catégories)
- **Pill actif** (valeur == id de la catégorie) : fond accent plein → `bg-accent text-white shadow-sm shadow-accent/30`
- **Pill inactif** : fond transparent avec bord → `border border-border text-foreground/60 hover:border-accent/50 hover:text-foreground/80`
- Clic sur un pill inactif → `onChange(categoryId)`
- Pas de déselection (la catégorie est requise, au minimum une reste active après le premier choix)
- Si `error` fourni → anneau d'erreur ou texte erreur dessous (via pattern `text-xs text-error mt-1.5`)

### Styles des pills

```tsx
// Const map — jamais de ternaire inline (convention dev-guide.md)
const pillVariants = {
  active: 'bg-accent text-white shadow-sm shadow-accent/30 border-transparent',
  inactive: 'border border-border text-foreground/60 hover:border-accent/50 hover:text-foreground/80 bg-transparent',
}

// Chaque pill :
<button
  key={id}
  type="button"
  onClick={() => onChange(id as EventCategory)}
  className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-all cursor-pointer ${
    pillVariants[value === id ? 'active' : 'inactive']
  }`}
>
  {category.name}
</button>
```

### Rendu complet

```tsx
export default function CategoryPills({ value, onChange, error }: Readonly<CategoryPillsProps>) {
  return (
    <div>
      <div className="flex flex-wrap gap-2">
        {Object.entries(EVENT_CATEGORIES).map(([id, category]) => (
          <button
            key={id}
            type="button"
            onClick={() => onChange(id as EventCategory)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-all cursor-pointer ${
              pillVariants[value === id ? 'active' : 'inactive']
            }`}
          >
            {category.name}
          </button>
        ))}
      </div>
      {error && <p className="text-xs text-error mt-1.5">{error}</p>}
    </div>
  )
}
```

---

## 4. Restructuration de `EventForm`

### 4.1 Interface — changements

```typescript
// AVANT (v1)
interface EventFormProps {
  submitLabel: string
  values: EventFormValues
  errors: EventFormErrors
  submitting: boolean
  imagePreview: string | null
  selectedImageName: string | null
  onFieldChange: ...
  onImageChange: ...
  onSubmit: ...
  onCancel: () => void
}

// APRÈS (v2) — une seule prop ajoutée
interface EventFormProps {
  submitLabel: string
  values: EventFormValues
  errors: EventFormErrors
  submitting: boolean
  imagePreview: string | null
  selectedImageName: string | null
  onFieldChange: ...
  onImageChange: ...
  onSubmit: ...
  onCancel: () => void
  onSaveDraft?: () => Promise<void>  // AJOUT — optionnel, uniquement passé depuis EventCreatePage
}
```

### 4.2 Structure JSX globale

```tsx
return (
  <form id="event-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-8">

    {/* Bande 1 */}
    <div className="grid grid-cols-[2fr_3fr] gap-6 max-lg:grid-cols-1">
      {/* ... bannière | titre+description ... */}
    </div>

    {/* Bande 2 */}
    <div className="grid grid-cols-[2fr_1fr_1fr] gap-4 max-sm:grid-cols-1">
      {/* ... lieu | début | fin ... */}
    </div>

    {/* Bande 3 */}
    <div className="flex flex-wrap items-end gap-4">
      {/* ... catégorie pills | capacité | statut | CTA ... */}
    </div>

    {/* Erreur image (si présente) */}
    {errors.image && <p className="text-xs text-error -mt-4">{errors.image}</p>}

  </form>
)
```

**Suppression** : l'ancien `<div className="bg-linear-to-br ...backdrop-blur-xl...">` (card glassmorphism v1) disparaît. Le `<form>` est le root direct.

---

## 5. Détail des trois bandes

### 5.1 Bande 1 — Bannière (gauche) | Titre + Description (droite)

```tsx
<div className="grid grid-cols-[2fr_3fr] gap-6 max-lg:grid-cols-1">

  {/* Colonne gauche — Zone bannière cliquable */}
  <div className="flex flex-col gap-3">
    <label htmlFor="event-banner" className="cursor-pointer block">
      {imagePreview ? (
        <img
          src={imagePreview}
          alt="Aperçu de la bannière"
          className="w-full h-full min-h-52 max-h-72 rounded-2xl border border-border object-cover"
        />
      ) : (
        <div className="w-full min-h-52 rounded-2xl border border-border border-dashed flex flex-col items-center justify-center gap-3 p-8 text-foreground/30 hover:border-accent/40 hover:text-foreground/50 transition-all">
          <div className="w-12 h-12 rounded-2xl bg-foreground/5 border border-border flex items-center justify-center">
            <ImagePlus className="w-6 h-6" />
          </div>
          <div className="text-center">
            <span className="text-sm font-medium block">Ajoutez une image de couverture</span>
            <span className="text-xs mt-1 block opacity-70">PNG, JPG ou WEBP — max {IMAGE_MAX_SIZE_MB} Mo</span>
          </div>
        </div>
      )}
    </label>
    <input id="event-banner" type="file" accept="image/*" onChange={onImageChange} className="hidden" />
    {selectedImageName && (
      <span className="text-xs text-foreground/40 break-all px-1">{selectedImageName}</span>
    )}
  </div>

  {/* Colonne droite — Titre + Description */}
  <div className="flex flex-col gap-4">
    <FormField label="Titre" htmlFor="event-title" required error={errors.title}>
      <Input
        id="event-title"
        type="text"
        value={values.title}
        onChange={(e) => onFieldChange('title', e.target.value)}
        error={errors.title}
        placeholder="Nom de l'événement"
        maxLength={EVENT_TITLE_MAX_LENGTH}
      />
      <div className="text-right text-xs text-foreground/40 mt-1">
        {values.title.length} / {EVENT_TITLE_MAX_LENGTH}
      </div>
    </FormField>

    <FormField label="Description" htmlFor="event-description" error={errors.description}>
      <Textarea
        id="event-description"
        value={values.description}
        onChange={(e) => onFieldChange('description', e.target.value)}
        className="resize-y min-h-36"
        placeholder="Quelques détails utiles pour les participants"
        rows={5}
        maxLength={EVENT_DESCRIPTION_MAX_LENGTH}
      />
      <div className="text-right text-xs text-foreground/40 mt-1">
        {values.description.length} / {EVENT_DESCRIPTION_MAX_LENGTH}
      </div>
    </FormField>
  </div>

</div>
```

**Points clés :**
- `<label htmlFor="event-banner">` englobe toute la zone de drop → la zone entière est cliquable
- `min-h-52` sur la zone vide pour donner une hauteur cohérente avec Titre + Description
- Sur `max-lg` (< 1024px) → `grid-cols-1`, bannière en haut, champs en dessous
- Le bouton séparé "Choisir une image" de la v1 disparaît — la zone entière est le trigger

### 5.2 Bande 2 — Lieu | Début | Fin

```tsx
<div className="grid grid-cols-[2fr_1fr_1fr] gap-4 max-sm:grid-cols-1">

  {/* Lieu avec icône */}
  <FormField label="Lieu" htmlFor="event-location" required error={errors.location}>
    <div className="relative">
      <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground/30 pointer-events-none" />
      <Input
        id="event-location"
        type="text"
        value={values.location}
        onChange={(e) => onFieldChange('location', e.target.value)}
        error={errors.location}
        placeholder="Uni Mail, Salle MR060"
        className="pl-10"
      />
    </div>
  </FormField>

  {/* Début */}
  {renderDateTimeField('startDate', 'Début', 'event-startDate', startDateTime, errors.startDate)}

  {/* Fin */}
  {renderDateTimeField('endDate', 'Fin', 'event-endDate', endDateTime, errors.endDate)}

</div>
```

**Import à ajouter** : `import { MapPin } from 'lucide-react'`

**Note sur `renderDateTimeField`** : la fonction interne existante est réutilisée telle quelle. Elle retourne un `<FormField>` avec la grille date + sélects heure/minute. Cela fonctionne dans la Bande 2 car chaque cellule de la grille parent est indépendante.

**Responsive :**
- `max-sm` (< 640px) → `grid-cols-1` : Lieu, Début, Fin empilés verticalement

### 5.3 Bande 3 — Catégorie | Capacité | Statut | CTA

```tsx
<div className="flex flex-wrap items-end gap-x-6 gap-y-4">

  {/* Catégorie — pills */}
  <FormField label="Catégorie" htmlFor="" required error={errors.category} className="flex-1 min-w-48">
    <CategoryPills
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

  {/* Statut */}
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

  {/* Zone CTA — poussée à droite */}
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

**Points clés :**
- `flex flex-wrap items-end` : tous les éléments s'alignent sur la baseline basse (labels au-dessus ne décalent pas le bas)
- `gap-x-6 gap-y-4` : espacement horizontal généreux, vertical modéré pour le wrap
- `className="flex-1 min-w-48"` sur Catégorie : la zone pills prend tout l'espace disponible avant wrap
- `ml-auto` sur la zone CTA : pousse le bouton vers la droite, `max-sm:ml-0` annule sur mobile
- `ButtonSecondary` remplacé par un lien texte pour "Annuler" — moins de poids visuel, cohérent avec l'image de référence
- `onSaveDraft` est optionnel : "Sauvegarder en Brouillon" n'apparaît que si la prop est fournie (uniquement `EventCreatePage`)
- `FormField label=""` pour CategoryPills : on passe `label="Catégorie"` mais `htmlFor=""` car les pills ne sont pas un input unique

---

## 6. Modification de `useEventForm` — Addition minimale

### Pourquoi

"Sauvegarder en Brouillon" doit soumettre le formulaire en forçant `status = 'DRAFT'`, indépendamment de ce qui est sélectionné dans le Select Statut. Appeler `setFieldValue('status', 'DRAFT')` puis `handleSubmit()` ne fonctionne pas (React batche les mises à jour d'état — le submit lirait encore l'ancien statut).

### Solution

Exposer une fonction `triggerDraftSave()` dans `useEventForm` qui soumet avec statut `'DRAFT'` forcé via un `useRef` interne, sans dépendre du state.

```typescript
// Dans useEventForm.ts

// Ajouter dans l'interface UseEventFormResult :
triggerDraftSave: () => Promise<void>

// Ajouter dans useEventForm :
const forcedStatusRef = useRef<EventStatus | null>(null)

// Modifier submitForm() pour lire le ref en priorité sur values.status :
const effectiveStatus: EventStatus = forcedStatusRef.current ?? values.status

// Dans la construction du payload :
status: effectiveStatus,

// Réinitialiser après submit :
forcedStatusRef.current = null

// Nouvelle fonction exposée :
async function triggerDraftSave() {
  forcedStatusRef.current = 'DRAFT'
  await submitForm()
}

// Ajout dans le return :
return {
  // ...existant...
  triggerDraftSave,
}
```

### Impact

- Aucune régression : `handleSubmit` continue à lire `values.status` (le ref est null par défaut)
- `triggerDraftSave` force DRAFT et réinitialise le ref après la soumission
- `EventEditPage` n'expose pas `onSaveDraft` → la prop est optionnelle, aucun changement dans la page d'édition

---

## 7. Modifications des pages

### `EventCreatePage.tsx`

```tsx
// Changements par rapport à la v1 :

// 1. size="lg" sur SectionWrapper (au lieu de size="md")
<SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>

// 2. onSaveDraft passé à EventForm
<EventForm
  submitLabel="Créer l'événement"
  values={form.values}
  errors={form.errors}
  submitting={form.submitting}
  imagePreview={form.imagePreview}
  selectedImageName={form.selectedImageName}
  onFieldChange={form.setFieldValue}
  onImageChange={form.handleImageChange}
  onSubmit={form.handleSubmit}
  onCancel={() => navigate('/')}
  onSaveDraft={form.triggerDraftSave}  {/* AJOUT */}
/>
```

### `EventEditPage.tsx`

```tsx
// Seul changement :
<SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>
// onSaveDraft non passé — "Sauvegarder en Brouillon" n'apparaît pas en édition
```

---

## 8. Responsive — Comportements de breakpoint

| Breakpoint | Bande 1 | Bande 2 | Bande 3 |
|---|---|---|---|
| `>= lg` (≥ 1024px) | `grid-cols-[2fr_3fr]` | `grid-cols-[2fr_1fr_1fr]` | `flex-wrap` en ligne |
| `sm` à `lg` (640–1023px) | `grid-cols-1` (bannière haut) | `grid-cols-[2fr_1fr_1fr]` | `flex-wrap` wrap naturel |
| `< sm` (< 640px) | `grid-cols-1` | `grid-cols-1` | `flex-col` + CTA plein largeur |

Sur mobile (`< sm`), la zone CTA :
- `ml-auto` → `ml-0` + `w-full`
- `ButtonPrimary` prend toute la largeur (`w-full`)
- Les liens "Sauvegarder en Brouillon" et "Annuler" se centrent dessous

---

## 9. Design tokens — Référence complète

| Élément | Classes Tailwind |
|---|---|
| **Wrapper page** | `SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}` |
| **Form** | `flex flex-col gap-8` (pas de card wrapper) |
| **Grille Bande 1** | `grid grid-cols-[2fr_3fr] gap-6 max-lg:grid-cols-1` |
| **Grille Bande 2** | `grid grid-cols-[2fr_1fr_1fr] gap-4 max-sm:grid-cols-1` |
| **Bande 3** | `flex flex-wrap items-end gap-x-6 gap-y-4` |
| **Zone bannière (vide)** | `w-full min-h-52 rounded-2xl border border-border border-dashed flex flex-col items-center justify-center gap-3 p-8 text-foreground/30 hover:border-accent/40 hover:text-foreground/50 transition-all` |
| **Badge icône bannière** | `w-12 h-12 rounded-2xl bg-foreground/5 border border-border flex items-center justify-center` |
| **Aperçu bannière** | `w-full h-full min-h-52 max-h-72 rounded-2xl border border-border object-cover` |
| **Nom fichier** | `text-xs text-foreground/40 break-all px-1` |
| **Icône dans input** | `absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground/30 pointer-events-none` |
| **Input avec icône** | `className="pl-10"` en plus des classes par défaut de `Input` |
| **Pill actif** | `bg-accent text-white shadow-sm shadow-accent/30 border-transparent` |
| **Pill inactif** | `border border-border text-foreground/60 hover:border-accent/50 hover:text-foreground/80 bg-transparent` |
| **Pill base** | `px-3 py-1.5 rounded-lg text-sm font-medium transition-all cursor-pointer` |
| **CTA submit** | `ButtonPrimary type="submit" disabled={submitting}` |
| **Lien Brouillon** | `text-xs text-foreground/40 hover:text-foreground/60 transition-all disabled:opacity-50` |
| **Lien Annuler** | `text-xs text-foreground/40 hover:text-foreground/60 transition-all` |

---

## 10. Suppressions vs v1

Ces éléments de la v1 sont supprimés dans cette v2 :

```tsx
// SUPPRIMER — card glassmorphism
<div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border p-8 max-sm:p-5">

// SUPPRIMER — sections nommées avec labels uppercase
<p className="text-xs font-bold uppercase tracking-widest text-foreground/30">Informations générales</p>
<p className="text-xs font-bold uppercase tracking-widest text-foreground/30">Dates</p>
<p className="text-xs font-bold uppercase tracking-widest text-foreground/30">Configuration</p>
<p className="text-xs font-bold uppercase tracking-widest text-foreground/30">Bannière</p>

// SUPPRIMER — séparateurs visuels
<div className="border-t border-border" />

// SUPPRIMER — footer avec border-t
<div className="flex justify-end gap-3 pt-4 border-t border-border max-sm:flex-col-reverse">
  <ButtonSecondary onClick={onCancel}>Annuler</ButtonSecondary>
  ...
</div>

// SUPPRIMER — bouton séparé "Choisir une image" + label séparé
<label htmlFor="event-banner" className="px-4 py-2 rounded-xl ...">Choisir une image</label>
```

---

## 11. Wireframes ASCII — v2

### 11.1 Desktop (≥ 1024px)

```
┌──────────────────────────────────────────────────────────────────┐
│ [Navbar]                                                         │
└──────────────────────────────────────────────────────────────────┘
                                                   ·· blob pink ··

  Créer un événement
  ───────────────────────────────────────
  Renseignez les informations...

  ┌──────────────────────┐   ┌──────────────────────────────────────┐
  │                      │   │  Titre *                             │
  │    [↑ upload icon]   │   │  [__________________________________]│
  │                      │   │                              0 / 120 │
  │  Ajoutez une image   │   │                                      │
  │  PNG/JPG — max 5 Mo  │   │  Description                         │
  │                      │   │  [__________________________________]│
  │                      │   │  [__________________________________]│
  │                      │   │  [__________________________________] │
  │                      │   │                            0 / 2000  │
  └──────────────────────┘   └──────────────────────────────────────┘

  ┌──────────────────────────────────┬────────────────┬────────────┐
  │  📍 Lieu *                       │  Début *       │  Fin *     │
  │  [______________________________]│  [__________]  │  [________]│
  │                                  │  [HH ▼]:[MM ▼] │  [HH]:[MM] │
  └──────────────────────────────────┴────────────────┴────────────┘

  ┌───────────────────────────────────────────────────────┐
  │  Catégorie *                    Capacité  Statut      │
  │  [Académique][Sports][Culturel]  [___]   [Brouillon▼] │
  │  [Social][Conférence][Autre]                          │
  │                                        [CRÉER L'ÉV.] │
  │                          [Sauvegarder en Brouillon]   │
  │                                          [Annuler]    │
  └───────────────────────────────────────────────────────┘

                               ·· blob blue ··
```

### 11.2 Mobile (< 640px)

```
┌──────────────────────────────────┐
│ [Navbar]                         │

  Créer un
  événement
  ─────────────────────────────

  ┌────────────────────────────┐
  │    [↑]  Ajoutez une image  │
  │    PNG/JPG — max 5 Mo      │
  └────────────────────────────┘

  Titre *
  [__________________________]
                       0 / 120
  Description
  [__________________________]
  [__________________________]
                     0 / 2000

  📍 Lieu *
  [__________________________]

  Début *
  [____] [HH ▼]:[MM ▼]

  Fin *
  [____] [HH ▼]:[MM ▼]

  Catégorie *
  [Académique][Sports][Culturel]
  [Social][Conférence][Autre]

  Capacité
  [____]

  Statut
  [Brouillon ▼]

  [CRÉER L'ÉVÉNEMENT (full width)]
  [Sauvegarder en Brouillon]
  [Annuler]
```

---

## 12. Checklist d'implémentation

### Nouveau composant
- [ ] Créer `src/components/event/CategoryPills.tsx`
- [ ] Typer `CategoryPillsProps` (value, onChange, error)
- [ ] Implémenter les `pillVariants` const map (jamais de ternaire inline)
- [ ] Tester : sélection exclusive, affichage de l'erreur

### `useEventForm.ts`
- [ ] Ajouter `forcedStatusRef` avec `useRef<EventStatus | null>(null)`
- [ ] Modifier `submitForm()` pour utiliser `forcedStatusRef.current ?? values.status`
- [ ] Ajouter `triggerDraftSave()` dans le hook
- [ ] Exposer `triggerDraftSave` dans `UseEventFormResult` et le `return`

### `EventForm.tsx`
- [ ] Supprimer la card glassmorphism wrapper
- [ ] Supprimer les 4 sections nommées et leurs séparateurs
- [ ] Supprimer le footer avec `border-t` et `ButtonSecondary Annuler`
- [ ] Ajouter prop `onSaveDraft?: () => Promise<void>`
- [ ] Ajouter import `MapPin` depuis lucide-react
- [ ] Ajouter import `CategoryPills`
- [ ] Implémenter Bande 1 : `grid-cols-[2fr_3fr]` + zone bannière cliquable (label englobante) + Titre/Description
- [ ] Implémenter Bande 2 : `grid-cols-[2fr_1fr_1fr]` + icône MapPin dans Lieu
- [ ] Implémenter Bande 3 : `flex flex-wrap items-end` + `CategoryPills` + inputs Capacité/Statut + zone CTA
- [ ] Zone CTA : `ButtonPrimary` + liens texte Brouillon (conditionnel) + Annuler
- [ ] Afficher `errors.image` sous le formulaire si présent
- [ ] Vérifier `id="event-form"` sur le `<form>`

### Pages
- [ ] `EventCreatePage.tsx` : `size="lg"` sur SectionWrapper + `onSaveDraft={form.triggerDraftSave}`
- [ ] `EventEditPage.tsx` : `size="lg"` sur SectionWrapper

### Qualité
- [ ] `npm run lint` — zéro erreur
- [ ] `npm run build` — zéro erreur TypeScript
- [ ] `npm run test` — tests existants toujours verts (adapter si nécessaire les tests EventForm qui référencent des éléments supprimés)
- [ ] Tester visuellement desktop 1280px : les 3 bandes s'affichent horizontalement
- [ ] Tester visuellement mobile 375px : empilement correct
- [ ] Tester dark/light mode
- [ ] Tester : clic sur zone bannière → ouvre le sélecteur de fichier
- [ ] Tester : pills catégorie → sélection exclusive + erreur visible si non sélectionné
- [ ] Tester : "Sauvegarder en Brouillon" → création avec statut DRAFT même si Statut select = PUBLISHED
- [ ] Tester : "Annuler" → navigation correcte

---

## 13. Prompt d'implémentation

Copie-colle ce prompt dans la prochaine conversation pour lancer l'implémentation :

---

```
Implémente les specs du fichier `specs_archives/specs_claude/rework/SPEC_event_form_rework_v2.md`.

Tu as également accès à l'image de référence Gemini dans le même dossier : `Screenshot 2026-04-11 at 20-53-31 Design System Analysis for Website Revamp - Google Gemini.png`.

## Contexte

Il s'agit d'un rework du formulaire de création/édition d'événements (`/events/new` et `/events/:id/edit`) sur un frontend React/TypeScript/TailwindCSS v4. Le rework précédent (v1) a déjà été implémenté — les pages utilisent désormais `SectionWrapper`, `SectionHeader` et `BlobsSubtle`. Cette v2 remplace le layout formulaire par un design en 3 bandes horizontales inspiré de l'image de référence Gemini.

## Fichiers à modifier ou créer

1. `frontend/src/hooks/useEventForm.ts` — ajout de `triggerDraftSave()` via `useRef`
2. `frontend/src/components/event/CategoryPills.tsx` — nouveau composant pills de catégorie
3. `frontend/src/components/event/EventForm.tsx` — restructuration complète de la présentation (logique métier intouchée)
4. `frontend/src/pages/event/EventCreatePage.tsx` — `size="lg"` + `onSaveDraft`
5. `frontend/src/pages/event/EventEditPage.tsx` — `size="lg"` uniquement

## Contraintes impératives

- `useEventForm` : ne modifier QUE ce qui est spécifié (ajout du ref + triggerDraftSave). La validation, la soumission, l'upload image, etc. sont intouchables.
- Zéro ternaire inline pour les variantes visuelles — const maps typées uniquement (voir dev-guide.md convention "Pattern variants").
- Tous les design tokens du projet (`bg-accent`, `text-foreground`, `border-border`, `text-error`, etc.) — jamais de valeurs Tailwind brutes.
- Les tests existants doivent rester verts. Adapte uniquement les assertions qui référencent des éléments DOM supprimés dans cette v2 (ex: si un test cherche "Informations générales" qui n'existe plus).

## Ordre d'implémentation recommandé

1. `CategoryPills.tsx` (composant isolé, testable seul)
2. `useEventForm.ts` (ajout minimal)
3. `EventForm.tsx` (restructuration principale)
4. Pages (ajustements mineurs)
5. Vérification lint + build + tests

Commence par lire les fichiers actuels avant de les modifier. Documente les changements dans la checklist de la spec une fois chaque item complété.
```
