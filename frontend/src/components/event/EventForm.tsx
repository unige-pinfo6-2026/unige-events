import type { ChangeEvent, FormEvent } from 'react'
import { EventCategory, EventStatus } from '@/types'
import type { EventFormErrors, EventFormValues } from '@/hooks/useEventForm'
import './EventForm.css'

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
  return (
    <div className='event-form-page'>
      <div className='event-form-card'>
        <h1 className='event-form-title'>{title}</h1>

        <form onSubmit={onSubmit} noValidate>
          <div className='form-group'>
            <label className='form-label' htmlFor='event-title'>
              Titre <span className='required'>*</span>
            </label>
            <input
              id='event-title'
              type='text'
              value={values.title}
              onChange={(event) => onFieldChange('title', event.target.value)}
              className={`form-input ${errors.title ? 'form-input--error' : ''}`}
              placeholder='Nom de l’événement'
            />
            {errors.title && <span className='form-error'>{errors.title}</span>}
          </div>

          <div className='form-group'>
            <label className='form-label' htmlFor='event-description'>Description</label>
            <textarea
              id='event-description'
              value={values.description}
              onChange={(event) => onFieldChange('description', event.target.value)}
              className='form-textarea'
              placeholder='Quelques détails utiles pour les participants'
              rows={5}
            />
          </div>

          <div className='form-group'>
            <label className='form-label' htmlFor='event-location'>
              Lieu <span className='required'>*</span>
            </label>
            <input
              id='event-location'
              type='text'
              value={values.location}
              onChange={(event) => onFieldChange('location', event.target.value)}
              className={`form-input ${errors.location ? 'form-input--error' : ''}`}
              placeholder='Uni Mail, Salle MR060'
            />
            {errors.location && <span className='form-error'>{errors.location}</span>}
          </div>

          <div className='event-form-grid'>
            <div className='form-group'>
              <label className='form-label' htmlFor='event-startDate'>
                Début <span className='required'>*</span>
              </label>
              <input
                id='event-startDate'
                type='datetime-local'
                value={values.startDate}
                onChange={(event) => onFieldChange('startDate', event.target.value)}
                className={`form-input ${errors.startDate ? 'form-input--error' : ''}`}
              />
              {errors.startDate && <span className='form-error'>{errors.startDate}</span>}
            </div>

            <div className='form-group'>
              <label className='form-label' htmlFor='event-endDate'>
                Fin <span className='required'>*</span>
              </label>
              <input
                id='event-endDate'
                type='datetime-local'
                value={values.endDate}
                onChange={(event) => onFieldChange('endDate', event.target.value)}
                className={`form-input ${errors.endDate ? 'form-input--error' : ''}`}
              />
              {errors.endDate && <span className='form-error'>{errors.endDate}</span>}
            </div>
          </div>

          <div className='event-form-grid'>
            <div className='form-group'>
              <label className='form-label' htmlFor='event-category'>
                Catégorie <span className='required'>*</span>
              </label>
              <select
                id='event-category'
                value={values.category}
                onChange={(event) => onFieldChange('category', event.target.value as EventFormValues['category'])}
                className={`form-select ${errors.category ? 'form-input--error' : ''}`}
              >
                <option value=''>Sélectionner</option>
                {Object.values(EventCategory).map((category) => (
                  <option key={category} value={category}>{category}</option>
                ))}
              </select>
              {errors.category && <span className='form-error'>{errors.category}</span>}
            </div>

            <div className='form-group'>
              <label className='form-label' htmlFor='event-capacity'>Capacité</label>
              <input
                id='event-capacity'
                type='number'
                min='1'
                step='1'
                value={values.capacity}
                onChange={(event) => onFieldChange('capacity', event.target.value)}
                className={`form-input ${errors.capacity ? 'form-input--error' : ''}`}
                placeholder='150'
              />
              {errors.capacity && <span className='form-error'>{errors.capacity}</span>}
            </div>
          </div>

          <div className='form-group'>
            <label className='form-label' htmlFor='event-status'>Statut</label>
            <select
              id='event-status'
              value={values.status}
              onChange={(event) => onFieldChange('status', event.target.value as EventStatus)}
              className='form-select'
            >
              <option value={EventStatus.DRAFT}>DRAFT</option>
              <option value={EventStatus.PUBLISHED}>PUBLISHED</option>
            </select>
          </div>

          <div className='form-group'>
            <label className='form-label' htmlFor='event-banner'>Bannière</label>
            <div className='banner-upload-card'>
              {imagePreview ? (
                <img src={imagePreview} alt='Aperçu de la bannière' className='banner-preview-img' />
              ) : (
                <div className='banner-preview-placeholder'>Ajoutez une image de couverture</div>
              )}

              <div className='banner-upload-actions'>
                <label className='photo-upload-btn' htmlFor='event-banner'>Choisir une image</label>
                <input
                  id='event-banner'
                  type='file'
                  accept='image/*'
                  onChange={onImageChange}
                  className='photo-input-hidden'
                />
                <span className='banner-upload-hint'>{selectedImageName ?? 'PNG, JPG ou WEBP'}</span>
              </div>
            </div>
            {errors.image && <span className='form-error'>{errors.image}</span>}
          </div>

          <div className='form-actions'>
            <button type='button' className='btn btn--secondary' onClick={onCancel}>
              Annuler
            </button>
            <button type='submit' className='btn btn--primary' disabled={submitting}>
              {submitting ? 'Enregistrement...' : submitLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default EventForm
