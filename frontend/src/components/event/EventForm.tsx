import type { ChangeEvent, FormEvent } from 'react'
import type { EventFormErrors, EventFormValues } from '@/hooks/useEventForm'
import { EVENT_CATEGORIES, EVENT_STATUSES } from '@/types/event'

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
  onSubmit: (event: FormEvent<HTMLFormElement>) => Promise<void>
  onCancel: () => void
}

function EventForm({
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

  const inputClass = (error?: string) =>
    `w-full px-3 py-2.5 border rounded-lg text-[0.9375rem] text-[var(--input-color)] bg-[var(--input-bg)] transition-colors focus:outline-none focus:border-[#CF0063] focus:ring-2 focus:ring-[var(--input-focus-shadow)] ${
      error ? 'border-red-400' : 'border-[var(--input-border)]'
    }`

  return (
    <div className="max-w-[760px] mx-auto">
      <div className="bg-[var(--card-bg)] border border-[var(--card-border)] rounded-2xl p-8 max-sm:p-5">
        <h1 className="text-2xl font-bold text-[var(--text-primary)] mb-8">{title}</h1>

        <form onSubmit={onSubmit} noValidate>

          {/* Titre */}
          <div className="mb-5">
            <label className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5" htmlFor="event-title">
              Titre <span className="text-red-400">*</span>
            </label>
            <input
              id="event-title"
              type="text"
              value={values.title}
              onChange={(e) => onFieldChange('title', e.target.value)}
              className={inputClass(errors.title)}
              placeholder="Nom de l'événement"
            />
            {errors.title && <span className="block text-[0.8125rem] text-red-400 mt-1">{errors.title}</span>}
          </div>

          {/* Description */}
          <div className="mb-5">
            <label className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5" htmlFor="event-description">
              Description
            </label>
            <textarea
              id="event-description"
              value={values.description}
              onChange={(e) => onFieldChange('description', e.target.value)}
              className={`${inputClass()} resize-y min-h-[120px]`}
              placeholder="Quelques détails utiles pour les participants"
              rows={5}
            />
          </div>

          {/* Lieu */}
          <div className="mb-5">
            <label className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5" htmlFor="event-location">
              Lieu <span className="text-red-400">*</span>
            </label>
            <input
              id="event-location"
              type="text"
              value={values.location}
              onChange={(e) => onFieldChange('location', e.target.value)}
              className={inputClass(errors.location)}
              placeholder="Uni Mail, Salle MR060"
            />
            {errors.location && <span className="block text-[0.8125rem] text-red-400 mt-1">{errors.location}</span>}
          </div>

          {/* Dates */}
          <div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1 max-sm:gap-0">
            <div className="mb-5">
              <label className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5" htmlFor="event-startDate">
                Début <span className="text-red-400">*</span>
              </label>
              <input
                id="event-startDate"
                type="datetime-local"
                value={values.startDate}
                onChange={(e) => onFieldChange('startDate', e.target.value)}
                className={inputClass(errors.startDate)}
              />
              {errors.startDate && <span className="block text-[0.8125rem] text-red-400 mt-1">{errors.startDate}</span>}
            </div>

            <div className="mb-5">
              <label className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5" htmlFor="event-endDate">
                Fin <span className="text-red-400">*</span>
              </label>
              <input
                id="event-endDate"
                type="datetime-local"
                value={values.endDate}
                onChange={(e) => onFieldChange('endDate', e.target.value)}
                className={inputClass(errors.endDate)}
              />
              {errors.endDate && <span className="block text-[0.8125rem] text-red-400 mt-1">{errors.endDate}</span>}
            </div>
          </div>

          {/* Catégorie + Capacité */}
          <div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1 max-sm:gap-0">
            <div className="mb-5">
              <label className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5" htmlFor="event-category">
                Catégorie <span className="text-red-400">*</span>
              </label>
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
              {errors.category && <span className="block text-[0.8125rem] text-red-400 mt-1">{errors.category}</span>}
            </div>

            <div className="mb-5">
              <label className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5" htmlFor="event-capacity">
                Capacité
              </label>
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
              {errors.capacity && <span className="block text-[0.8125rem] text-red-400 mt-1">{errors.capacity}</span>}
            </div>
          </div>

          {/* Statut */}
          <div className="mb-5">
            <label className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5" htmlFor="event-status">
              Statut
            </label>
            <select
              id="event-status"
              value={values.status}
              onChange={(e) => onFieldChange('status', e.target.value as EventFormValues['status'])}
              className={inputClass()}
            >
              {Object.entries(EVENT_STATUSES).filter(([id]) => id !== 'CANCELLED').map(([id, category]) => (
                <option key={id} value={id}>{category.name}</option>
              ))}
            </select>
          </div>

          {/* Bannière */}
          <div className="mb-5">
            <label className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5" htmlFor="event-banner">
              Bannière
            </label>
            <div className="grid gap-4">
              {imagePreview ? (
                <img
                  src={imagePreview}
                  alt="Aperçu de la bannière"
                  className="w-full max-h-[320px] min-h-[220px] rounded-xl border border-[var(--input-border)] object-cover"
                />
              ) : (
                <div className="w-full min-h-[220px] rounded-xl border border-[var(--input-border)] flex items-center justify-center p-6 text-[var(--text-muted)] text-center bg-[var(--input-bg)]">
                  Ajoutez une image de couverture
                </div>
              )}

              <div className="flex items-start gap-3 flex-wrap max-sm:flex-col">
                <label
                  htmlFor="event-banner"
                  className="px-4 py-2 bg-[var(--btn-secondary-bg)] border border-[var(--input-border)] rounded-lg text-sm text-[var(--text-secondary)] cursor-pointer hover:bg-[var(--btn-secondary-hover)] transition-colors flex-none"
                >
                  Choisir une image
                </label>
                <input id="event-banner" type="file" accept="image/*" onChange={onImageChange} className="hidden" />
                <span className="text-sm text-[var(--text-muted)] flex-1 min-w-[12rem] break-all max-sm:w-full">
                  {selectedImageName ?? 'PNG, JPG ou WEBP'}
                </span>
              </div>
            </div>
            {errors.image && <span className="block text-[0.8125rem] text-red-400 mt-1">{errors.image}</span>}
          </div>

          {/* Actions */}
          <div className="flex justify-end gap-3 mt-7 max-sm:flex-col-reverse">
            <button
              type="button"
              onClick={onCancel}
              className="px-5 py-2.5 rounded-lg text-[0.9375rem] font-semibold bg-[var(--btn-secondary-bg)] text-[var(--btn-secondary-color)] hover:bg-[var(--btn-secondary-hover)] transition-colors max-sm:w-full"
            >
              Annuler
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-5 py-2.5 rounded-lg text-[0.9375rem] font-semibold bg-[#CF0063] text-white hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity max-sm:w-full"
            >
              {submitting ? 'Enregistrement...' : submitLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default EventForm