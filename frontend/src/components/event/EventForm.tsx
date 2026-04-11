import { type ChangeEvent, type ComponentProps } from 'react'
import type { EventFormErrors, EventFormValues } from '@/hooks/useEventForm'
import { EVENT_TITLE_MAX_LENGTH, EVENT_DESCRIPTION_MAX_LENGTH, IMAGE_MAX_SIZE_MB } from '@/hooks/useEventForm'

type FormSubmitEvent = Parameters<NonNullable<ComponentProps<'form'>['onSubmit']>>[0]
import { EVENT_STATUSES } from '@/types/event'
import FormField, { Input, Select, Textarea } from '@/components/utils/FormField'
import { ButtonPrimary } from '@/components/utils/Buttons'
import { ImagePlus, MapPin } from 'lucide-react'
import CategoryPills from '@/components/event/CategoryPills'

interface EventFormProps {
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
}

interface DateTimeParts {
  datePart: string
  hourPart: string
  minutePart: string
}

const HOUR_OPTIONS = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0'))
const MINUTE_OPTIONS = Array.from({ length: 60 }, (_, minute) => String(minute).padStart(2, '0'))

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
  submitLabel,
  values,
  errors,
  submitting,
  imagePreview,
  selectedImageName,
  onFieldChange,
  onImageChange,
  onSubmit,
  onCancel,
  onSaveDraft,
}: Readonly<EventFormProps>) {
  const startDateTime = splitDateTime(values.startDate)
  const endDateTime = splitDateTime(values.endDate)

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

        {renderDateTimeField('startDate', 'Début', 'event-startDate', startDateTime, errors.startDate)}
        {renderDateTimeField('endDate', 'Fin', 'event-endDate', endDateTime, errors.endDate)}

      </div>

      {/* Bande 3 — Catégorie | Capacité | Statut | CTA */}
      <div className="flex flex-wrap items-end gap-x-6 gap-y-4">

        <FormField label="Catégorie" htmlFor="" required className="flex-1 min-w-48">
          <CategoryPills
            value={values.category}
            onChange={(cat) => onFieldChange('category', cat)}
            error={errors.category}
          />
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
          />
        </FormField>

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

        {/* Zone CTA */}
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

      {/* Erreur image (si présente) */}
      {errors.image && <p className="text-xs text-error -mt-4">{errors.image}</p>}

    </form>
  )
}
