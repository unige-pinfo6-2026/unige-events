# SPEC — Rework page détail événement : layout deux colonnes

> Auteur : Claude  
> Date : 2026-04-12  
> Référence visuelle : `specs_archives/specs_claude/rework/events-view/Screenshot_2.png`  
> Statut : **À implémenter**  
> Fichier cible : `frontend/src/pages/event/EventDetailPage.tsx`

---

## 1. Vue d'ensemble

### Objectif

Remplacer le layout linéaire vertical de `EventDetailPage.tsx` par un layout en **deux colonnes** : colonne principale (contenu) à gauche et sidebar (infos clés + actions) à droite. L'objectif est de mieux hiérarchiser l'information, de rendre les actions (inscription, modification, suppression) immédiatement visibles sans scroller, et d'anticiper proprement les champs futurs du backlog S5→S6 avec des shells `ComingSoonBlock` identiques à ceux d'`EventForm.tsx`.

### Ce qui change

| Aspect | Avant | Après |
|---|---|---|
| Layout | `max-w-3xl` colonne unique | `grid-cols-[3fr_2fr]` deux colonnes, `size="lg"` (max-w-5xl) |
| Conteneur page | `div` direct, pas de `SectionWrapper` | `SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}` |
| Header page | Absent | `SectionHeader` "Détails de l'événement" |
| Bannière hauteur | `h-72` | `h-72 lg:h-80`, haut de la colonne principale |
| Titre bannière | `text-2xl font-extrabold` | `text-2xl lg:text-3xl font-extrabold` |
| Actions organisateur | Boutons "Modifier"/"Supprimer" dans la card principale | Déplacés dans la sidebar, pleine largeur `rounded-2xl` |
| Meta infos (date, lieu, capacité, organisateur) | Dans la card principale | Card dédiée dans la sidebar, via composant local `InfoRow` |
| Description | Dans la card principale avec les metas | Card glassmorphism dédiée dans la colonne principale, label "À PROPOS" |
| `AttendanceButtons` | Card séparée après la card principale | Card glassmorphism dans la sidebar |
| `IcsExportButton` | Après la card Attendance | Dans la sidebar, sous AttendanceButtons |
| Shells backlog | Absents | `ComingSoonBlock` S5/S6 pour champs additionnels, places, participants, stats |
| Mobile | Colonne unique naturelle | `grid-cols-1`, sidebar remonte en `order-1` avant la colonne principale |

### Ce qui ne change PAS

- `EventDetailFixture` et `<Skeleton>` boneyard — **intouchables dans ce rework**
- `useEvent`, `getUserById`, `deleteEvent`, `useAuth` — hooks et services inchangés
- Modale de confirmation suppression — code conservé tel quel
- `AttendanceButtons` — comportement et props inchangés
- `IcsExportButton` — comportement et props inchangés
- `UserAvatar` — utilisé de la même façon
- `formatEventDateTime`, `EVENT_CATEGORIES` — inchangés
- La garde `if (eventId === null)` — inchangée
- La garde `if (loading || isBoneyardBuild)` — inchangée
- La gestion `bannerWarning` sessionStorage — inchangée
- `isBoneyardBuild` const — inchangée

---

## 2. Périmètre des fichiers

| Fichier | Modifications |
|---|---|
| `src/pages/event/EventDetailPage.tsx` | Restructuration complète du rendu — un seul fichier |

Aucun composant partagé à créer ou modifier. Tous les nouveaux composants (`InfoRow`, `ComingSoonBlock`) sont locaux, non-exportés, définis dans le même fichier.

---

## 3. Composants locaux non-exportés

### 3.1 `InfoRow`

Composant local pour chaque ligne de meta-information dans la card infos clés de la sidebar. Pattern identique à `AboutRow` dans `ProfilePage.tsx` (AGENTS.md : « composant local non-exporté si structure répétée dans le même fichier »).

```typescript
interface InfoRowProps {
  icon: LucideIcon
  color?: string           // category.color — passé via style inline (seule exception autorisée)
  children: React.ReactNode
}
```

```tsx
function InfoRow({ icon: Icon, color, children }: InfoRowProps) {
  return (
    <div className="flex items-start gap-3 text-sm text-foreground/60">
      <Icon
        className="w-4 h-4 shrink-0 mt-0.5"
        style={color ? { color } : undefined}
      />
      <span className="leading-snug">{children}</span>
    </div>
  )
}
```

Utilisé pour : date, lieu, capacité dans la card sidebar.

### 3.2 `ComingSoonBlock`

**Copie exacte** du composant local d'`EventForm.tsx`. Ne pas modifier l'interface ni les variants — cohérence visuelle absolue entre les deux pages.

```typescript
interface ComingSoonBlockProps {
  icon: LucideIcon
  label: string
  sprint: string
  children?: React.ReactNode
}
```

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

### 3.3 `capacityBadgeVariants` — const map

Déclaré dès ce rework même si non encore utilisé activement (les champs `availableSpots`/`waitlistedCount` n'existent pas encore dans l'API). Il sera référencé lors de la levée du shell S5 (SCRUM-130) sans nécessiter de nouvelle const map.

```typescript
const capacityBadgeVariants = {
  full:      'bg-error/10 border-error/30 text-error',
  low:       'bg-orange-500/10 border-orange-500/30 text-orange-400',
  available: 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400',
} as const
```

---

## 4. Structure JSX globale

### 4.1 Racine de la page — vue aérienne

```tsx
return (
  <SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>

    <SectionHeader
      title={<>Détails de <mark>l'événement</mark></>}
      subtitle="Toutes les informations pour participer"
      align="left"
    />

    {/* Grille deux colonnes */}
    <div className="grid grid-cols-[3fr_2fr] gap-6 items-start max-lg:grid-cols-1">

      {/* Colonne principale — order-2 sur mobile */}
      <div className="flex flex-col gap-5 max-lg:order-2">
        {/* bannière, description, shells champs additionnels, shell participants */}
      </div>

      {/* Sidebar — order-1 sur mobile (remonte au-dessus) */}
      <div className="flex flex-col gap-4 lg:sticky lg:top-6 max-lg:order-1">
        {/* card infos clés, attendance, ICS, actions organisateur, shell stats */}
      </div>

    </div>

    {bannerWarning && <InfoMessage type="error" message={bannerWarning} />}

    {/* Modale confirmation suppression — inchangée */}
    {showConfirm && (
      <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50">
        {/* ... code identique à l'actuel ... */}
      </div>
    )}

  </SectionWrapper>
)
```

**Pourquoi `lg:sticky lg:top-6` sur la sidebar** : la description + les shells rendent la colonne principale plus longue que la sidebar sur desktop. La sidebar sticky permet que les actions (inscription, modification) restent visibles pendant le scroll. Sur mobile (`max-lg`), `sticky` ne s'applique pas, comportement naturel.

**Pourquoi inverser l'ordre sur mobile** : sur petit écran, les actions (inscription, infos clés) doivent précéder le contenu long (description, shells). `max-lg:order-2` / `max-lg:order-1` permet d'inverser sans toucher à l'ordre HTML, préservant ainsi la sémantique et l'accessibilité.

---

## 5. Détail de chaque section

### 5.1 Bannière (haut de la colonne principale)

Conservation exacte du comportement actuel (dégradé catégorie si pas de bannière, overlay dégradé bas, ligne colorée en haut, badge catégorie, titre en overlay). Ajustements uniquement visuels :

- Hauteur : `h-72 lg:h-80` (légèrement plus haute sur desktop)
- Titre : `text-2xl lg:text-3xl font-extrabold leading-snug drop-shadow-sm` (responsive)

```tsx
{/* Bannière */}
<div
  className="relative rounded-3xl overflow-hidden h-72 lg:h-80"
  style={{
    background: event.bannerUrl
      ? `url(${event.bannerUrl}) center/cover`
      : `linear-gradient(135deg, ${category.color}55, ${category.color}cc)`,
  }}
>
  <div className="absolute inset-0 bg-linear-to-t from-black/70 via-black/20 to-transparent" />
  <div className="absolute top-0 left-0 right-0 h-0.5" style={{ background: category.color }} />
  <span
    className="absolute top-4 left-4 text-white text-xs font-bold px-3 py-1.5 rounded-full tracking-wide backdrop-blur-sm"
    style={{ background: `${category.color}cc` }}
  >
    {category.name}
  </span>
  <div className="absolute bottom-0 left-0 right-0 p-6">
    <h1 className="text-white text-2xl lg:text-3xl font-extrabold leading-snug drop-shadow-sm">
      {event.title}
    </h1>
  </div>
</div>
```

### 5.2 Card description (colonne principale)

Affichée uniquement si `event.description` est non-vide. Label "À PROPOS" uppercase pour la hiérarchie visuelle, style identique aux sections nommées d'autres pages.

```tsx
{event.description && (
  <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border">
    <h2 className="text-xs font-bold uppercase tracking-widest text-foreground/30 mb-3">
      À propos
    </h2>
    <p className="text-foreground/70 leading-relaxed whitespace-pre-wrap text-sm">
      {event.description}
    </p>
  </div>
)}
```

### 5.3 Shells "Informations complémentaires" (colonne principale)

Quatre `ComingSoonBlock` dans un `flex flex-col gap-3`. Ils anticipent les champs SCRUM-127 (S5/S6). Positionnés sous la description, ils rendent l'emplacement visible dès maintenant sans logique backend.

```tsx
{/* Section shells champs additionnels — S5 */}
<div className="flex flex-col gap-3">

  <ComingSoonBlock icon={Globe} label="Site web de l'événement" sprint="S5">
    <div className="flex items-center gap-2">
      <Globe className="w-4 h-4 text-foreground/30 shrink-0" />
      <span className="text-xs text-foreground/20 truncate">https://unige.ch/evenement…</span>
    </div>
  </ComingSoonBlock>

  <ComingSoonBlock icon={Mail} label="Email de contact" sprint="S5">
    <div className="flex items-center gap-2">
      <Mail className="w-4 h-4 text-foreground/30 shrink-0" />
      <span className="text-xs text-foreground/20">contact@unige.ch</span>
    </div>
  </ComingSoonBlock>

  <ComingSoonBlock icon={CalendarClock} label="Date limite d'inscription" sprint="S5">
    <span className="text-xs text-foreground/20">jj/mm/aaaa à HH:MM</span>
  </ComingSoonBlock>

  <ComingSoonBlock icon={Tag} label="Mots-clés" sprint="S5">
    <div className="flex flex-wrap gap-1.5">
      {(['conférence', 'réseau', 'emploi'] as const).map((tag) => (
        <span
          key={tag}
          className="inline-flex items-center px-2 py-0.5 rounded-lg text-xs bg-foreground/5 border border-border/30 text-foreground/20"
        >
          {tag}
        </span>
      ))}
    </div>
  </ComingSoonBlock>

</div>
```

**Levée du shell S5 (SCRUM-127)** : remplacer chaque `ComingSoonBlock` par l'affichage réel du champ correspondant. Les emplacements et la structure sont déjà en place.

### 5.4 Shell "Participants" (colonne principale — organisateur uniquement)

Visible uniquement si `isOrganizer`. Anticipe SCRUM-101 (S6) — liste des inscrits avec avatars.

```tsx
{isOrganizer && (
  <ComingSoonBlock icon={Users} label="Liste des participants" sprint="S6">
    <div className="flex items-center gap-3 mt-1">
      <div className="flex -space-x-2">
        {[1, 2, 3].map((i) => (
          <div
            key={i}
            className="w-8 h-8 rounded-full bg-foreground/10 border-2 border-background"
          />
        ))}
      </div>
      <span className="text-xs text-foreground/20">12 participants · 4 intéressés</span>
    </div>
  </ComingSoonBlock>
)}
```

### 5.5 Card infos clés (sidebar)

Glassmorphism `rounded-3xl`. Chaque meta-info via `InfoRow`. Séparateur horizontal avant le block organisateur. Shell S5 (places disponibles) intégré à la fin de cette card, séparé par un `border-t`.

**Note `allDay` S5 (SCRUM-125)** : quand ce champ sera disponible dans l'API, si `event.allDay === true` → afficher uniquement la date dans l'`InfoRow` Calendar, sans les heures. L'emplacement est déjà là, aucun code `allDay` n'est ajouté maintenant.

```tsx
<div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-5 border border-border flex flex-col gap-4">

  {/* Meta rows */}
  <div className="flex flex-col gap-3">
    <InfoRow icon={Calendar} color={category.color}>
      <span>
        {formatEventDateTime(event.startDate)}
        <span className="text-foreground/30 mx-1.5">→</span>
        {formatEventDateTime(event.endDate)}
      </span>
    </InfoRow>

    <InfoRow icon={MapPin} color={category.color}>
      {event.location}
    </InfoRow>

    {event.capacity !== undefined && (
      <InfoRow icon={Users} color={category.color}>
        {event.capacity} places au total
      </InfoRow>
    )}
  </div>

  <div className="border-t border-border" />

  {/* Organisateur */}
  {organizer && (
    <Link
      to={`/profile/${organizer.id}`}
      className="flex items-center gap-3 hover:opacity-80 transition-opacity no-underline"
    >
      <UserAvatar user={organizer} size={36} className="shrink-0" />
      <div className="flex flex-col min-w-0">
        <span className="text-xs text-foreground/40">Organisé par</span>
        <span className="text-sm font-semibold text-foreground truncate">
          {organizer.displayName ?? organizer.email}
        </span>
      </div>
    </Link>
  )}

  {/* Shell places disponibles — S5 (SCRUM-130) */}
  <div className="border-t border-border" />
  <ComingSoonBlock icon={Users} label="Places disponibles" sprint="S5">
    <div className="flex flex-wrap gap-2 mt-1">
      <span className={`px-2.5 py-1 rounded-lg text-xs font-semibold border ${capacityBadgeVariants.available}`}>
        8 places disponibles
      </span>
      <span className="px-2.5 py-1 rounded-lg text-xs font-semibold border bg-foreground/5 border-border/30 text-foreground/20">
        2 en liste d'attente
      </span>
    </div>
  </ComingSoonBlock>

</div>
```

**Levée du shell S5 (SCRUM-130)** : remplacer le `ComingSoonBlock` par la logique réelle utilisant `event.availableSpots` et `event.waitlistedCount`, et indexer `capacityBadgeVariants` selon le taux de remplissage.

### 5.6 Card AttendanceButtons (sidebar)

Déplacée de sa position actuelle (card séparée après la card principale) vers la sidebar. Props strictement identiques.

```tsx
<div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl px-5 py-4 border border-border">
  <AttendanceButtons
    key={event.id}
    eventId={event.id}
    initialAttendingCount={event.attendingCount}
    initialStatus={null}
  />
</div>
```

### 5.7 IcsExportButton (sidebar)

Déplacé de sa position actuelle vers la sidebar, sous AttendanceButtons. Aucune modification.

```tsx
<IcsExportButton event={event} />
```

### 5.8 Actions organisateur (sidebar)

Déplacées hors de la card principale. Deux boutons pleine largeur `rounded-2xl` dans un `flex flex-col gap-2`. Modifier en premier (moins destructif), Supprimer en rouge.

```tsx
{isOrganizer && (
  <div className="flex flex-col gap-2">
    <Link
      to={`/events/${event.id}/edit`}
      className="w-full text-center px-4 py-2.5 rounded-2xl border border-border text-foreground text-sm font-semibold no-underline hover:border-foreground/30 transition-colors"
    >
      Modifier l'événement
    </Link>
    <button
      type="button"
      onClick={() => setShowConfirm(true)}
      className="w-full px-4 py-2.5 bg-error/10 border border-error/30 text-error rounded-2xl text-sm font-semibold cursor-pointer hover:bg-error/20 transition-colors"
    >
      Supprimer l'événement
    </button>
  </div>
)}
```

### 5.9 Shell statistiques organisateur (sidebar)

Visible uniquement si `isOrganizer`. Anticipe SCRUM-92 (S6) — dashboard stats `GET /api/events/{id}/stats`.

```tsx
{isOrganizer && (
  <ComingSoonBlock icon={BarChart2} label="Statistiques de participation" sprint="S6">
    <div className="grid grid-cols-3 gap-2 mt-1">
      {[
        { label: 'Vues', value: '—' },
        { label: 'Inscrits', value: '—' },
        { label: 'Intéressés', value: '—' },
      ].map(({ label, value }) => (
        <div key={label} className="flex flex-col items-center rounded-xl border border-border/30 bg-foreground/5 py-2">
          <span className="text-sm font-bold text-foreground/20">{value}</span>
          <span className="text-[10px] text-foreground/20 mt-0.5">{label}</span>
        </div>
      ))}
    </div>
  </ComingSoonBlock>
)}
```

### 5.10 Modale de confirmation suppression

**Aucune modification.** Conservée exactement telle quelle. Voici le code de référence actuel pour s'assurer qu'il n'est pas altéré :

```tsx
{showConfirm && (
  <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50">
    <div className="bg-background border border-border rounded-3xl p-8 max-w-sm w-[90%] shadow-2xl">
      <h2 className="text-lg font-bold text-foreground mb-2">Supprimer l'événement ?</h2>
      <p className="text-sm text-foreground/50 mb-6">
        Cette action annulera l'événement <strong className="text-foreground">"{event.title}"</strong>. Elle est irréversible.
      </p>
      <div className="flex gap-3 justify-end">
        <button
          type="button"
          onClick={() => setShowConfirm(false)}
          disabled={deleting}
          className="px-4 py-2.5 rounded-xl border border-border text-foreground text-sm font-semibold disabled:opacity-50 hover:border-foreground/30 transition-colors cursor-pointer bg-transparent"
        >
          Annuler
        </button>
        <button
          type="button"
          onClick={handleDelete}
          disabled={deleting}
          className="px-4 py-2.5 rounded-xl bg-error text-white text-sm font-semibold disabled:opacity-50 hover:bg-error/80 transition-colors cursor-pointer border-0"
        >
          {deleting ? 'Suppression...' : 'Confirmer'}
        </button>
      </div>
    </div>
  </div>
)}
```

---

## 6. Responsive

| Breakpoint | Grille | Ordre |
|---|---|---|
| `≥ lg` (≥ 1024px) | `grid-cols-[3fr_2fr]` | Colonne principale à gauche, sidebar à droite |
| `< lg` (< 1024px) | `grid-cols-1` | Sidebar remonte en premier (`order-1`), colonne principale en dessous (`order-2`) |

**Pourquoi sidebar en premier sur mobile** : les utilisateurs mobiles cherchent en priorité la date, le lieu, les boutons d'inscription et les actions — pas la description longue. L'inversion d'ordre via CSS (`order`) préserve l'ordre HTML et l'accessibilité (screen readers).

```tsx
{/* Colonne principale */}
<div className="flex flex-col gap-5 max-lg:order-2">

{/* Sidebar */}
<div className="flex flex-col gap-4 lg:sticky lg:top-6 max-lg:order-1">
```

---

## 7. Imports — Liste complète

```tsx
// Déjà présents (conserver)
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth, useEvent } from '@/hooks'
import { getUserById } from '@/services/userService'
import { deleteEvent } from '@/services/eventApi'
import UserAvatar from '@/components/user/UserAvatar'
import type { User } from '@/types/user'
import { EVENT_CATEGORIES } from '@/types/event'
import { formatEventDateTime } from '@/utils/dateTime'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import AttendanceButtons from '@/components/event/AttendanceButtons'
import IcsExportButton from '@/components/event/IcsExportButton'

// À ajouter
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import type { LucideIcon } from 'lucide-react'
import { Calendar, MapPin, Users, Globe, Mail, CalendarClock, Tag, BarChart2 } from 'lucide-react'
```

**Note** : `Calendar`, `MapPin`, `Users` étaient déjà importés dans le fichier actuel. Vérifier et dédupliquer lors de l'implémentation.

---

## 8. Design tokens — Tableau récapitulatif

| Élément | Classes Tailwind |
|---|---|
| **Wrapper page** | `SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}` |
| **Grille principale** | `grid grid-cols-[3fr_2fr] gap-6 items-start max-lg:grid-cols-1` |
| **Colonne principale** | `flex flex-col gap-5 max-lg:order-2` |
| **Sidebar** | `flex flex-col gap-4 lg:sticky lg:top-6 max-lg:order-1` |
| **Bannière** | `relative rounded-3xl overflow-hidden h-72 lg:h-80` |
| **Titre bannière** | `text-white text-2xl lg:text-3xl font-extrabold leading-snug drop-shadow-sm` |
| **Card glassmorphism** | `bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border` |
| **Card padding description** | `p-6` |
| **Card padding infos sidebar** | `p-5` |
| **Card padding attendance** | `px-5 py-4` |
| **Label section uppercase** | `text-xs font-bold uppercase tracking-widest text-foreground/30 mb-3` |
| **Séparateur** | `border-t border-border` |
| **InfoRow icône** | `w-4 h-4 shrink-0 mt-0.5` + `style={{ color }}` |
| **InfoRow texte** | `text-sm text-foreground/60 leading-snug` |
| **Lien organisateur** | `flex items-center gap-3 hover:opacity-80 transition-opacity no-underline` |
| **Nom organisateur** | `text-sm font-semibold text-foreground truncate` |
| **Sous-label organisateur** | `text-xs text-foreground/40` |
| **Bouton Modifier** | `w-full text-center px-4 py-2.5 rounded-2xl border border-border text-foreground text-sm font-semibold no-underline hover:border-foreground/30 transition-colors` |
| **Bouton Supprimer** | `w-full px-4 py-2.5 bg-error/10 border border-error/30 text-error rounded-2xl text-sm font-semibold cursor-pointer hover:bg-error/20 transition-colors` |
| **ComingSoonBlock container** | `rounded-2xl border border-dashed border-border/40 bg-foreground/[0.018] px-4 py-3` |
| **ComingSoonBlock badge** | `text-[10px] font-semibold tracking-widest uppercase text-foreground/20 bg-foreground/5 px-2 py-0.5 rounded-full border border-border/30 shrink-0` |
| **Badge places — vert** | `bg-emerald-500/10 border-emerald-500/30 text-emerald-400` |
| **Badge places — orange** | `bg-orange-500/10 border-orange-500/30 text-orange-400` |
| **Badge places — rouge** | `bg-error/10 border-error/30 text-error` |
| **Mock avatar shell** | `w-8 h-8 rounded-full bg-foreground/10 border-2 border-background` |
| **KPI shell stats** | `flex flex-col items-center rounded-xl border border-border/30 bg-foreground/5 py-2` |

---

## 9. Wireframes ASCII

### 9.1 Desktop (≥ 1024px)

```
┌──────────────────────────────────────────────────────────────────────────┐
│ [Navbar]                                                                 │
└──────────────────────────────────────────────────────────────────────────┘
                                                         ·· blob pink ··

  Détails de l'événement
  ──────────────────────────────────────
  Toutes les informations pour participer

  ┌──────────────────────────────────────────┐  ┌────────────────────────────┐
  │  [BANNIÈRE h-80              🏷 Académiq]│  │ 📅 Lun 14 avr → Mar 15 avr│
  │  [                                      ]│  │ 📍 Uni Mail, Salle MR060   │
  │  [  Titre de l'événement en overlay     ]│  │ 👥 40 places au total      │
  └──────────────────────────────────────────┘  │ ─────────────────────────  │
                                                │ [ava] Organisé par         │
  ┌──────────────────────────────────────────┐  │       Alice Martin         │
  │ À PROPOS                                 │  │ ─────────────────────────  │
  │ Lorem ipsum description de l'événement  │  │ 🔵 Places disponibles [S5] │
  │ sur plusieurs lignes, avec whitespace    │  └────────────────────────────┘
  │ pre-wrap preservé...                     │
  └──────────────────────────────────────────┘  ┌────────────────────────────┐
                                                │ [Je suis intéressé(e)]     │
  ┌──────────────────────────────────────────┐  │ [Je participe]             │
  │ 🌐 Site web de l'événement        [S5]  │  │ 3 participent · 1 ↗        │
  │ ✉  Email de contact               [S5]  │  └────────────────────────────┘
  │ 🕐 Date limite d'inscription      [S5]  │
  │ 🏷  Mots-clés                     [S5]  │  ┌────────────────────────────┐
  └──────────────────────────────────────────┘  │ [Télécharger .ics]         │
                                                │ [Google Calendar ↗]        │
  {si isOrganizer}                             └────────────────────────────┘
  ┌──────────────────────────────────────────┐
  │ 👥 Liste des participants          [S6]  │  {si isOrganizer}
  │  ⬤⬤⬤  12 participants · 4 intéressés  │  ┌────────────────────────────┐
  └──────────────────────────────────────────┘  │ [Modifier l'événement    ] │
                                                │ [Supprimer l'événement   ] │
                                                └────────────────────────────┘

                                                {si isOrganizer}
                                                ┌────────────────────────────┐
                                                │ 📊 Statistiques      [S6]  │
                                                │  Vues   Inscrits   Intér.  │
                                                │   —        —          —    │
                                                └────────────────────────────┘

                                    ·· blob blue ··
```

### 9.2 Mobile (< 1024px) — sidebar en premier

```
┌──────────────────────────────────┐
│ [Navbar]                         │

  Détails de
  l'événement
  ────────────────────────────
  Toutes les informations...

  ┌──────────────────────────────┐
  │ [BANNIÈRE h-72  🏷 Académiq] │
  │ [  Titre en overlay         ]│
  └──────────────────────────────┘

  ── sidebar order-1 (remonte) ──

  ┌──────────────────────────────┐
  │ 📅 Lun 14 avr → Mar 15 avr  │
  │ 📍 Uni Mail, Salle MR060    │
  │ 👥 40 places au total       │
  │ ──────────────────────────  │
  │ [ava] Alice Martin          │
  │       Organisé par          │
  │ ──────────────────────────  │
  │ 🔵 Places disponibles [S5]  │
  └──────────────────────────────┘

  ┌──────────────────────────────┐
  │ [Je suis intéressé(e)]       │
  │ [Je participe]               │
  │ 3 participent · 1 intéressé  │
  └──────────────────────────────┘

  ┌──────────────────────────────┐
  │ [Télécharger .ics]           │
  │ [Google Calendar ↗]          │
  └──────────────────────────────┘

  {si isOrganizer}
  ┌──────────────────────────────┐
  │ [Modifier l'événement      ] │
  │ [Supprimer l'événement     ] │
  └──────────────────────────────┘

  ── colonne principale order-2 ──

  ┌──────────────────────────────┐
  │ À PROPOS                     │
  │ Lorem ipsum...               │
  └──────────────────────────────┘

  ┌──────────────────────────────┐
  │ 🌐 Site web           [S5]  │
  │ ✉  Email contact      [S5]  │
  │ 🕐 Deadline           [S5]  │
  │ 🏷  Mots-clés         [S5]  │
  └──────────────────────────────┘

  {si isOrganizer}
  ┌──────────────────────────────┐
  │ 👥 Participants        [S6]  │
  └──────────────────────────────┘
```

---

## 10. Checklist d'implémentation

### Composants locaux
- [ ] Déclarer `InfoRowProps` et `InfoRow` (local, non-exporté)
- [ ] Copier `ComingSoonBlockProps`, `comingSoonVariants` et `ComingSoonBlock` depuis `EventForm.tsx` (copie exacte)
- [ ] Déclarer `capacityBadgeVariants` const map

### Imports
- [ ] Ajouter `SectionWrapper`, `SectionHeader` depuis `@/components/utils/Section`
- [ ] Ajouter `BlobsSubtle` depuis `@/components/utils/Blobs`
- [ ] Ajouter `type LucideIcon` depuis `lucide-react`
- [ ] Ajouter `Globe, Mail, CalendarClock, Tag, BarChart2` aux imports lucide-react (dédupliquer `Calendar`, `MapPin`, `Users` déjà présents)

### Layout
- [ ] Entourer le contenu dans `<SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>`
- [ ] Ajouter `<SectionHeader title={<>Détails de <mark>l'événement</mark></>} subtitle="Toutes les informations pour participer" align="left" />`
- [ ] Créer la grille `grid grid-cols-[3fr_2fr] gap-6 items-start max-lg:grid-cols-1`
- [ ] `max-lg:order-2` sur la colonne principale
- [ ] `lg:sticky lg:top-6 max-lg:order-1` sur la sidebar

### Colonne principale
- [ ] Bannière : `h-72 lg:h-80`, titre `text-2xl lg:text-3xl font-extrabold`
- [ ] Card description : glassmorphism `rounded-3xl p-6`, label "À PROPOS" uppercase, conditionnelle si `event.description`
- [ ] Shells champs additionnels : `flex flex-col gap-3` avec 4 `ComingSoonBlock` (Globe/S5, Mail/S5, CalendarClock/S5, Tag/S5)
- [ ] Shell participants : `ComingSoonBlock` (Users/S6), conditionnel `isOrganizer`

### Sidebar
- [ ] Card infos clés : glassmorphism `rounded-3xl p-5`, `InfoRow` ×3 (date, lieu, capacité conditionnelle)
- [ ] Block organisateur dans la card : `Link` vers `/profile/:id`, `UserAvatar size={36}`, displayName/email
- [ ] Shell places disponibles : `ComingSoonBlock` (Users/S5) dans la card infos clés, avec mock badges utilisant `capacityBadgeVariants.available`
- [ ] Card AttendanceButtons : glassmorphism `rounded-3xl px-5 py-4`, props identiques à l'actuel
- [ ] `IcsExportButton` : déplacé en sidebar, aucune modification
- [ ] Actions organisateur : `flex flex-col gap-2`, conditionnel `isOrganizer`, boutons `w-full rounded-2xl`
- [ ] Shell stats : `ComingSoonBlock` (BarChart2/S6), conditionnel `isOrganizer`, grille KPI 3 colonnes

### Suppressions
- [ ] Supprimer `<div className="max-w-3xl mx-auto flex flex-col gap-5">` wrapper actuel
- [ ] Supprimer le bloc "Organizer actions row" de la card principale (déplacé en sidebar)
- [ ] Supprimer le bloc "Meta" (date/lieu/capacité/organisateur) de la card principale (déplacé en sidebar)
- [ ] Supprimer la card `AttendanceButtons` standalone (déplacée en sidebar)
- [ ] Supprimer `IcsExportButton` de sa position actuelle (déplacé en sidebar)

### Intouchable — à vérifier après implémentation
- [ ] `EventDetailFixture` — code inchangé ligne par ligne
- [ ] `<Skeleton>` boneyard — code inchangé
- [ ] Modale confirmation suppression — code inchangé
- [ ] `isBoneyardBuild` const — inchangée

### Qualité
- [ ] `npm run lint` — zéro erreur
- [ ] `npm run build` — zéro TypeScript error
- [ ] `npm run test` — tests existants toujours verts
- [ ] Tester visuellement desktop 1280px : deux colonnes, sidebar sticky lors du scroll
- [ ] Tester visuellement tablette 768px (`max-lg`) : `grid-cols-1`, sidebar au-dessus
- [ ] Tester visuellement mobile 375px : ordre correct (sidebar → colonne principale)
- [ ] Tester dark/light mode
- [ ] Tester : organisateur connecté → actions Modifier/Supprimer visibles dans sidebar, shells participants + stats visibles
- [ ] Tester : non-organisateur → actions absentes, shells participants + stats absents
- [ ] Tester : événement sans description → card "À propos" absente
- [ ] Tester : événement sans bannière → dégradé catégorie affiché
- [ ] Tester : clic "Supprimer" → modale s'ouvre, confirm → redirection `/`

---

## 11. Prompt d'implémentation

Copie-colle ce prompt dans la prochaine conversation pour lancer l'implémentation :

---

```
Implémente les specs du fichier `specs_archives/specs_claude/rework/events-view/SPEC_event_detail_rework.md`.

Tu as également accès à l'image de référence approximative dans le même dossier : `Screenshot_2.png`.

## Contexte

Il s'agit d'un rework de la page de détail d'événement (`/events/:id`) sur un frontend React/TypeScript/TailwindCSS v4. Le formulaire events/new et events/:id/edit a déjà été reworké (voir SPEC_event_form_rework_v3.md et le code actuel d'EventForm.tsx). C'est au tour de la page de consultation de recevoir le même niveau d'ambition visuelle.

## Fichier à modifier

`frontend/src/pages/event/EventDetailPage.tsx` — un seul fichier.

## Contraintes impératives

- `EventDetailFixture` et `<Skeleton>` boneyard : **intouchables** — ne pas modifier une seule ligne de ces blocs.
- Modale de confirmation suppression : conservée telle quelle.
- `useEvent`, `getUserById`, `deleteEvent`, `useAuth` : hooks et services inchangés.
- `ComingSoonBlock` et `comingSoonVariants` : copie exacte depuis `EventForm.tsx` (composant local non-exporté, même interface, mêmes classes).
- `InfoRow` : composant local non-exporté, pattern AGENTS.md.
- `capacityBadgeVariants` : const map typée déclarée même si non utilisée activement dans ce rework.
- Zéro ternaire inline pour les variantes visuelles — const maps uniquement.
- Tous les design tokens du projet (`bg-background`, `text-foreground`, `border-border`, `text-error`, `bg-accent`…) — jamais de valeurs Tailwind brutes.

## Ordre d'implémentation recommandé

1. Composants locaux (`InfoRow`, `ComingSoonBlock`/`comingSoonVariants`, `capacityBadgeVariants`)
2. Mise à jour des imports (SectionWrapper, SectionHeader, BlobsSubtle, LucideIcon, nouvelles icônes)
3. Restructuration du rendu principal (SectionWrapper, SectionHeader, grille deux colonnes)
4. Colonne principale (bannière h-72 lg:h-80, card description, shells champs additionnels, shell participants)
5. Sidebar (card infos via InfoRow, block organisateur, card attendance, IcsExportButton, actions organisateur, shell stats)
6. Vérification : EventDetailFixture et Skeleton intouchés ligne par ligne, modale inchangée
7. npm run lint + npm run build + npm run test

Commence par lire le fichier actuel avant de le modifier.
```
