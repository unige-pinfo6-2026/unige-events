import { type ChangeEvent, type ComponentProps } from 'react'
import type { EventFormErrors, EventFormValues } from '@/hooks/useEventForm'

type FormSubmitEvent = Parameters<NonNullable<ComponentProps<'form'>['onSubmit']>>[0]
import { EVENT_CATEGORIES, EVENT_STATUSES } from '@/types/event'
import FormField, { inputClass } from '@/components/utils/FormField'
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
  return (
    <div className="max-w-3xl mx-auto">
      <div className="bg-background border border-border rounded-3xl p-8 max-sm:p-5">
        <h1 className="text-3xl font-bold text-foreground mb-8">{title}</h1>

        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">

          <FormField label="Titre" htmlFor="event-title" required error={errors.title}>
            <input
              id="event-title"
              type="text"
              value={values.title}
              onChange={(e) => onFieldChange('title', e.target.value)}
              className={inputClass(errors.title)}
              placeholder="Nom de l'événement"
            />
          </FormField>

          <FormField label="Description" htmlFor="event-description">
            <textarea
              id="event-description"
              value={values.description}
              onChange={(e) => onFieldChange('description', e.target.value)}
              className={[inputClass(), 'resize-y min-h-28'].join(' ')}
              placeholder="Quelques détails utiles pour les participants"
              rows={4}
            />
          </FormField>

          <FormField label="Lieu" htmlFor="event-location" required error={errors.location}>
            <input
              id="event-location"
              type="text"
              value={values.location}
              onChange={(e) => onFieldChange('location', e.target.value)}
              className={inputClass(errors.location)}
              placeholder="Uni Mail, Salle MR060"
            />
          </FormField>

          <div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1">
            <FormField label="Début" htmlFor="event-startDate" required error={errors.startDate}>
              <input
                id="event-startDate"
                type="datetime-local"
                value={values.startDate}
                onChange={(e) => onFieldChange('startDate', e.target.value)}
                className={inputClass(errors.startDate)}
              />
            </FormField>

            <FormField label="Fin" htmlFor="event-endDate" required error={errors.endDate}>
              <input
                id="event-endDate"
                type="datetime-local"
                value={values.endDate}
                onChange={(e) => onFieldChange('endDate', e.target.value)}
                className={inputClass(errors.endDate)}
              />
            </FormField>
          </div>

          <div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1">
            <FormField label="Catégorie" htmlFor="event-category" required error={errors.category}>
              <select
                id="event-category"
                value={values.category}
                onChange={(e) => onFieldChange('category', e.target.value as EventFormValues['category'])}
                className={inputClass(errors.category)}
              >
                <option value="">Sélectionner</option>
                {Object.entries(EVENT_CATEGORIES).map(([id, category]) => (
                  <option key={id} value={id}>{category.name}</option>
                ))}
              </select>
            </FormField>

            <FormField label="Capacité" htmlFor="event-capacity" error={errors.capacity}>
              <input
                id="event-capacity"
                type="number"
                min="1"
                step="1"
                value={values.capacity}
                onChange={(e) => onFieldChange('capacity', e.target.value)}
                className={inputClass(errors.capacity)}
                placeholder="150"
              />
            </FormField>
          </div>

          <FormField label="Statut" htmlFor="event-status">
            <select
              id="event-status"
              value={values.status}
              onChange={(e) => onFieldChange('status', e.target.value as EventFormValues['status'])}
              className={inputClass()}
            >
              {Object.entries(EVENT_STATUSES).filter(([id]) => id !== 'CANCELLED').map(([id, s]) => (
                <option key={id} value={id}>{s.name}</option>
              ))}
            </select>
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
                <span className="text-sm text-foreground/40 flex-1 min-w-48 break-all">
                  {selectedImageName ?? 'PNG, JPG ou WEBP'}
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
