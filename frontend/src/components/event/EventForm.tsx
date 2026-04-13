import { type ChangeEvent, type ComponentProps } from 'react'
import type { EventFormErrors, EventFormValues } from '@/hooks/useEventForm'
import { EVENT_TITLE_MAX_LENGTH, EVENT_DESCRIPTION_MAX_LENGTH, IMAGE_MAX_SIZE_MB } from '@/hooks/useEventForm'

type FormSubmitEvent = Parameters<NonNullable<ComponentProps<'form'>['onSubmit']>>[0]
import { EVENT_CATEGORIES, EVENT_STATUSES, FACULTY_LABELS, Faculty } from '@/types/event'
import FormField, { Input, Select, Textarea } from '@/components/utils/FormField'
import { ButtonPrimary, ButtonSecondary } from '@/components/utils/Buttons'
import { ImagePlus } from 'lucide-react'

interface EventFormProps {
  title: string
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
  title,
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
}: Readonly<EventFormProps>) {
  const startDateTime = splitDateTime(values.startDate)
  const endDateTime = splitDateTime(values.endDate)

  function setDatePart(field: 'startDate' | 'endDate', datePart: string, currentHourPart: string, currentMinutePart: string) {
    if (!datePart) {
      onFieldChange(field, '' as EventFormValues[typeof field])
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
    onFieldChange(field, joinDateTime(datePart, nextHourPart, nextMinutePart) as EventFormValues[typeof field])
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
    <div className="max-w-3xl mx-auto">
      <div className="bg-background border border-border rounded-3xl p-8 max-sm:p-5">
        <h1 className="text-3xl font-bold text-foreground mb-8">{title}</h1>

        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">

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
            <div className="text-right text-xs text-foreground/40 mt-1">{values.title.length} / {EVENT_TITLE_MAX_LENGTH}</div>
          </FormField>

          <FormField label="Description" htmlFor="event-description">
            <Textarea
              id="event-description"
              value={values.description}
              onChange={(e) => onFieldChange('description', e.target.value)}
              className="resize-y min-h-28"
              placeholder="Quelques détails utiles pour les participants"
              rows={4}
              maxLength={EVENT_DESCRIPTION_MAX_LENGTH}
            />
            <div className="text-right text-xs text-foreground/40 mt-1">{values.description.length} / {EVENT_DESCRIPTION_MAX_LENGTH}</div>
          </FormField>

          <FormField label="Lieu" htmlFor="event-location" required error={errors.location}>
            <Input
              id="event-location"
              type="text"
              value={values.location}
              onChange={(e) => onFieldChange('location', e.target.value)}
              error={errors.location}
              placeholder="Uni Mail, Salle MR060"
            />
          </FormField>

          <div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1">
            {renderDateTimeField('startDate', 'Début', 'event-startDate', startDateTime, errors.startDate)}
            {renderDateTimeField('endDate', 'Fin', 'event-endDate', endDateTime, errors.endDate)}
          </div>

          <div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1">
            <FormField label="Catégorie" htmlFor="event-category" required error={errors.category}>
              <Select
                id="event-category"
                value={values.category}
                onChange={(e) => onFieldChange('category', e.target.value as EventFormValues['category'])}
                error={errors.category}
              >
                <option value="">Sélectionner</option>
                {Object.entries(EVENT_CATEGORIES).map(([id, category]) => (
                  <option key={id} value={id}>{category.name}</option>
                ))}
              </Select>
            </FormField>

            <FormField label="Capacité" htmlFor="event-capacity" error={errors.capacity}>
              <Input
                id="event-capacity"
                type="number"
                min="1"
                step="1"
                value={values.capacity}
                onChange={(e) => onFieldChange('capacity', e.target.value)}
                error={errors.capacity}
                placeholder="150"
              />
            </FormField>
          </div>

          <FormField label="Statut" htmlFor="event-status">
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

          <FormField label="Faculté concernée" htmlFor="event-faculty">
            <Select
              id="event-faculty"
              value={values.faculty ?? ''}
              onChange={(e) => onFieldChange('faculty', (e.target.value as Faculty) || null)}
            >
              <option value="">Aucune faculté</option>
              {Object.values(Faculty).map((id) => (
                <option key={id} value={id}>{FACULTY_LABELS[id]}</option>
              ))}
            </Select>
          </FormField>

          <FormField label="Bannière" htmlFor="event-banner" error={errors.image}>
            <div className="flex flex-col gap-3">
              {imagePreview ? (
                <img
                  src={imagePreview}
                  alt="Aperçu de la bannière"
                  className="w-full max-h-72 min-h-40 rounded-2xl border border-border object-cover"
                />
              ) : (
                <div className="w-full min-h-40 rounded-2xl border border-border border-dashed flex flex-col items-center justify-center gap-2 p-6 text-foreground/30">
                  <ImagePlus className="w-8 h-8" />
                  <span className="text-sm">Ajoutez une image de couverture</span>
                </div>
              )}

              <div className="flex items-center gap-3 flex-wrap">
                <label
                  htmlFor="event-banner"
                  className="px-4 py-2 rounded-xl border border-border text-sm font-semibold text-foreground/60 cursor-pointer hover:border-accent/50 hover:text-foreground transition-all flex-none"
                >
                  Choisir une image
                </label>
                <input id="event-banner" type="file" accept="image/*" onChange={onImageChange} className="hidden" />
                <span className="text-sm text-foreground/40 flex-1 min-w-48 break-all flex flex-col gap-0.5">
                  {selectedImageName ?? 'PNG, JPG ou WEBP'}
                  <span className="text-xs opacity-75">Taille max : {IMAGE_MAX_SIZE_MB} Mo</span>
                </span>
              </div>
            </div>
          </FormField>

          <div className="flex justify-end gap-3 pt-3 border-t border-border max-sm:flex-col-reverse">
            <ButtonSecondary onClick={onCancel}>Annuler</ButtonSecondary>
            <ButtonPrimary type="submit" disabled={submitting}>
              {submitting ? 'Enregistrement...' : submitLabel}
            </ButtonPrimary>
          </div>
        </form>
      </div>
    </div>
  )
}
