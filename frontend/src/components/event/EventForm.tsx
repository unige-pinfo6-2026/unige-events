import { type ChangeEvent, type ComponentProps } from 'react'
import type { EventFormErrors, EventFormValues } from '@/hooks/useEventForm'
import { EVENT_TITLE_MAX_LENGTH, EVENT_DESCRIPTION_MAX_LENGTH, IMAGE_MAX_SIZE_MB } from '@/hooks/useEventForm'

type FormSubmitEvent = Parameters<NonNullable<ComponentProps<'form'>['onSubmit']>>[0]
import FormField, { Input, Select, Textarea } from '@/components/utils/FormField'
import { ButtonDestructive, ButtonNeutral, ButtonPrimary, ButtonSecondary } from '@/components/utils/Buttons'
import { ImagePlus, MapPin, Globe, Mail, CalendarClock, Tag, Repeat, Paperclip, Users, Search } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import CategorySelect from '@/components/event/CategorySelect'
import { FACULTIES, type Faculty } from '@/types/faculty'

interface EventFormProps {
  mode: 'create' | 'edit'
  submitLabel: string
  values: EventFormValues
  errors: EventFormErrors
  submitting: boolean
  draftSaving?: boolean
  imagePreview: string | null
  selectedImageName: string | null
  onFieldChange: <K extends keyof EventFormValues>(field: K, value: EventFormValues[K]) => void
  onImageChange: (event: ChangeEvent<HTMLInputElement>) => void
  onSubmit: (event: FormSubmitEvent) => Promise<void>
  onCancel?: () => void
  onSaveDraft?: () => Promise<void>
  saveDraftLabel?: string
  onDelete?: () => void | Promise<void>
  deleting?: boolean
  deleteLabel?: string
}

interface DateTimeParts {
  datePart: string
  hourPart: string
  minutePart: string
}

interface ComingSoonBlockProps {
  icon: LucideIcon
  label: string
  sprint: string
  children?: React.ReactNode
}

const HOUR_OPTIONS = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0'))
const MINUTE_OPTIONS = Array.from({ length: 60 }, (_, minute) => String(minute).padStart(2, '0'))

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

function splitDateTime(value: string): DateTimeParts {
  if (!value.includes('T')) {
    return { datePart: '', hourPart: '', minutePart: '' }
  }
  const [datePart, timePart] = value.split('T')
  const [hourPart = '', minutePart = ''] = timePart.split(':')
  return { datePart, hourPart, minutePart }
}

function joinDateTime(datePart: string, hourPart: string, minutePart: string): string {
  if (!datePart || hourPart === '' || minutePart === '') {
    return ''
  }
  return `${datePart}T${hourPart}:${minutePart}`
}

export default function EventForm({
  mode,
  submitLabel,
  values,
  errors,
  submitting,
  draftSaving = false,
  imagePreview,
  selectedImageName,
  onFieldChange,
  onImageChange,
  onSubmit,
  onCancel,
  onSaveDraft,
  saveDraftLabel = 'Sauvegarder en Brouillon',
  onDelete,
  deleting = false,
  deleteLabel = 'Supprimer',
}: Readonly<EventFormProps>) {
  const startDateTime = splitDateTime(values.startDate)
  const endDateTime = splitDateTime(values.endDate)
  const busy = submitting || draftSaving || deleting

  function setDatePart(field: 'startDate' | 'endDate', datePart: string, currentHourPart: string, currentMinutePart: string) {
    if (!datePart) {
      onFieldChange(field, '')
      return
    }
    const hourPart = currentHourPart || '00'
    const minutePart = currentMinutePart || '00'
    onFieldChange(field, joinDateTime(datePart, hourPart, minutePart) as EventFormValues[typeof field])
  }

  function setTimePart(
    field: 'startDate' | 'endDate',
    datePart: string,
    hourPart: string,
    minutePart: string,
    part: 'hour' | 'minute',
    value: string,
  ) {
    if (!datePart) return
    const nextHourPart = part === 'hour' ? value : hourPart
    const nextMinutePart = part === 'minute' ? value : minutePart
    onFieldChange(field, joinDateTime(datePart, nextHourPart, nextMinutePart))
  }

  function renderDateTimeField(
    field: 'startDate' | 'endDate',
    label: string,
    inputId: string,
    dt: DateTimeParts,
    error: string | undefined,
  ) {
    return (
      <FormField label={label} htmlFor={inputId} required error={error}>
        <div className="grid grid-cols-[1fr_auto] gap-3 max-sm:grid-cols-1">
          <Input
            id={inputId}
            type="date"
            value={dt.datePart}
            onChange={(e) => setDatePart(field, e.target.value, dt.hourPart, dt.minutePart)}
            error={error}
          />
          <div className="flex items-center gap-1.5">
            <label className="sr-only" htmlFor={`${inputId}-hour`}>
              {field === 'startDate' ? 'Heure de début' : 'Heure de fin'}
            </label>
            <Select
              id={`${inputId}-hour`}
              value={dt.hourPart}
              onChange={(e) => setTimePart(field, dt.datePart, dt.hourPart, dt.minutePart, 'hour', e.target.value)}
              error={error}
              className="w-auto min-w-[4.5rem]"
            >
              <option value="">HH</option>
              {HOUR_OPTIONS.map((h) => <option key={h} value={h}>{h}</option>)}
            </Select>
            <span className="text-foreground/40 font-bold select-none">:</span>
            <label className="sr-only" htmlFor={`${inputId}-minute`}>
              {field === 'startDate' ? 'Minute de début' : 'Minute de fin'}
            </label>
            <Select
              id={`${inputId}-minute`}
              value={dt.minutePart}
              onChange={(e) => setTimePart(field, dt.datePart, dt.hourPart, dt.minutePart, 'minute', e.target.value)}
              error={error}
              className="w-auto min-w-[4.5rem]"
            >
              <option value="">MM</option>
              {MINUTE_OPTIONS.map((m) => <option key={m} value={m}>{m}</option>)}
            </Select>
          </div>
        </div>
      </FormField>
    )
  }

  return (
    <form id="event-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-8">

      {/* Bande 1 — Bannière (gauche) | Titre + Description (droite) */}
      <div className="grid grid-cols-[2fr_3fr] gap-6 max-lg:grid-cols-1">

        {/* Colonne gauche — Zone bannière cliquable */}
        <div className="flex flex-col gap-3 pt-7 max-lg:pt-0">
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

      {/* Bande 2 — Lieu | Début | Fin */}
      <div className="grid grid-cols-[2fr_1fr_1fr] gap-4 max-sm:grid-cols-1">

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

        {/* Début — label row + shell allDay comme frères (pas dans <label>) */}
        <div>
          <div className="flex items-center justify-between gap-3 mb-2">
            <label htmlFor="event-startDate" className="block text-sm font-semibold text-foreground/60">
              Début <span className="text-error"> *</span>
            </label>
            {/* Shell allDay — SCRUM-125 / S5 */}
            <span aria-hidden="true" className="flex items-center gap-1.5 text-xs font-normal text-foreground/25 cursor-not-allowed select-none pointer-events-none">
              <input type="checkbox" disabled className="opacity-25 accent-accent w-3.5 h-3.5" />
              Toute la journée
              <span className={comingSoonVariants.badge}>S5</span>
            </span>
          </div>
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
          {errors.startDate && <p className="text-xs text-error mt-1.5">{errors.startDate}</p>}
        </div>

        {renderDateTimeField('endDate', 'Fin', 'event-endDate', endDateTime, errors.endDate)}

      </div>

      {/* Bande 3 — Catégorie | Faculté | Capacité | CTA */}
      <div className="flex flex-wrap items-end gap-x-6 gap-y-4">

        <FormField label="Catégorie" htmlFor="event-category" required className="w-48 flex-none">
          <CategorySelect
            id="event-category"
            value={values.category}
            onChange={(cat) => onFieldChange('category', cat)}
            error={errors.category}
          />
        </FormField>

        <FormField label="Faculté concernée" htmlFor="event-faculty" className="w-56 flex-none">
          <Select
            id="event-faculty"
            value={values.faculty ?? ''}
            onChange={(e) => onFieldChange('faculty', (e.target.value as Faculty) || null)}
          >
            <option value="">Toutes facultés</option>
            {Object.entries(FACULTIES).map(([id, faculty]) => (
              <option key={id} value={id}>{faculty.name}</option>
            ))}
          </Select>
        </FormField>

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
            className="[appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
          />
        </FormField>

        {/* Zone CTA — publish / draft save / delete are mutually exclusive: while any
            one is in flight, all other action buttons are disabled to prevent concurrent
            mutations on the same event (e.g. clicking "Supprimer" during a save-draft). */}
        <div className="flex flex-wrap items-center gap-3 ml-auto max-sm:ml-0 max-sm:w-full max-sm:flex-col max-sm:items-stretch">
          {onDelete && (
            <ButtonDestructive
              onClick={() => { void onDelete() }}
              disabled={busy}
              size="md"
            >
              {deleting ? 'Suppression...' : deleteLabel}
            </ButtonDestructive>
          )}
          {onCancel && (
            <ButtonSecondary onClick={onCancel} disabled={busy} size="md">
              Annuler
            </ButtonSecondary>
          )}
          {onSaveDraft && (
            <ButtonNeutral
              onClick={() => { void onSaveDraft() }}
              disabled={busy}
              size="md"
            >
              {draftSaving ? 'Enregistrement...' : saveDraftLabel}
            </ButtonNeutral>
          )}
          <ButtonPrimary type="submit" disabled={busy} size="md">
            {submitting ? 'Enregistrement...' : submitLabel}
          </ButtonPrimary>
        </div>

      </div>

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
        {mode === 'create' && (
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

      {/* Erreur image (si présente) */}
      {errors.image && <p className="text-xs text-error -mt-4">{errors.image}</p>}

    </form>
  )
}
