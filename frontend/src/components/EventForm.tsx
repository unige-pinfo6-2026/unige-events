import type { ChangeEvent, FormEvent } from 'react'
import { EventCategory, EventStatus } from '../types'
import {
  EVENT_DESCRIPTION_MAX_LENGTH,
  EVENT_TITLE_MAX_LENGTH,
  IMAGE_MAX_SIZE_MB,
} from '../hooks/useEventForm'
import type { EventFormErrors, EventFormValues } from '../hooks/useEventForm'
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

interface DateAndTimeParts {
  datePart: string
  hourPart: string
  minutePart: string
}

const HOUR_OPTIONS = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0'))
const MINUTE_OPTIONS = Array.from({ length: 60 }, (_, minute) => String(minute).padStart(2, '0'))

function splitDateTime(value: string): DateAndTimeParts {
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
    if (!datePart) {
      return
    }

    const nextHourPart = part === 'hour' ? value : hourPart
    const nextMinutePart = part === 'minute' ? value : minutePart
    onFieldChange(field, joinDateTime(datePart, nextHourPart, nextMinutePart) as EventFormValues[typeof field])
  }

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
              maxLength={EVENT_TITLE_MAX_LENGTH}
            />
            <div className='event-char-counter'>
              {values.title.length} / {EVENT_TITLE_MAX_LENGTH}
            </div>
            {errors.title && <span className='form-error'>{errors.title}</span>}
          </div>

          <div className='form-group'>
            <label className='form-label' htmlFor='event-description'>Description</label>
            <textarea
              id='event-description'
              value={values.description}
              onChange={(event) => onFieldChange('description', event.target.value)}
              className={`form-textarea ${errors.description ? 'form-input--error' : ''}`}
              placeholder='Quelques détails utiles pour les participants'
              rows={5}
              maxLength={EVENT_DESCRIPTION_MAX_LENGTH}
            />
            <div className='event-char-counter'>
              {values.description.length} / {EVENT_DESCRIPTION_MAX_LENGTH}
            </div>
            {errors.description && <span className='form-error'>{errors.description}</span>}
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
              <div className='event-datetime-controls'>
                <input
                  id='event-startDate'
                  type='date'
                  value={startDateTime.datePart}
                  onChange={(event) => setDatePart('startDate', event.target.value, startDateTime.hourPart, startDateTime.minutePart)}
                  className={`form-input ${errors.startDate ? 'form-input--error' : ''}`}
                />
                <div className='event-time-controls'>
                  <label className='event-sr-only' htmlFor='event-startHour'>Heure de début</label>
                  <select
                    id='event-startHour'
                    value={startDateTime.hourPart}
                    onChange={(event) => setTimePart(
                      'startDate',
                      startDateTime.datePart,
                      startDateTime.hourPart,
                      startDateTime.minutePart,
                      'hour',
                      event.target.value,
                    )}
                    className={`form-select ${errors.startDate ? 'form-input--error' : ''}`}
                    disabled={!startDateTime.datePart}
                  >
                    <option value=''>HH</option>
                    {HOUR_OPTIONS.map((hour) => (
                      <option key={hour} value={hour}>{hour}</option>
                    ))}
                  </select>
                  <span className='event-time-separator'>:</span>
                  <label className='event-sr-only' htmlFor='event-startMinute'>Minute de début</label>
                  <select
                    id='event-startMinute'
                    value={startDateTime.minutePart}
                    onChange={(event) => setTimePart(
                      'startDate',
                      startDateTime.datePart,
                      startDateTime.hourPart,
                      startDateTime.minutePart,
                      'minute',
                      event.target.value,
                    )}
                    className={`form-select ${errors.startDate ? 'form-input--error' : ''}`}
                    disabled={!startDateTime.datePart}
                  >
                    <option value=''>MM</option>
                    {MINUTE_OPTIONS.map((minute) => (
                      <option key={minute} value={minute}>{minute}</option>
                    ))}
                  </select>
                </div>
              </div>
              {errors.startDate && <span className='form-error'>{errors.startDate}</span>}
            </div>

            <div className='form-group'>
              <label className='form-label' htmlFor='event-endDate'>
                Fin <span className='required'>*</span>
              </label>
              <div className='event-datetime-controls'>
                <input
                  id='event-endDate'
                  type='date'
                  value={endDateTime.datePart}
                  onChange={(event) => setDatePart('endDate', event.target.value, endDateTime.hourPart, endDateTime.minutePart)}
                  className={`form-input ${errors.endDate ? 'form-input--error' : ''}`}
                />
                <div className='event-time-controls'>
                  <label className='event-sr-only' htmlFor='event-endHour'>Heure de fin</label>
                  <select
                    id='event-endHour'
                    value={endDateTime.hourPart}
                    onChange={(event) => setTimePart(
                      'endDate',
                      endDateTime.datePart,
                      endDateTime.hourPart,
                      endDateTime.minutePart,
                      'hour',
                      event.target.value,
                    )}
                    className={`form-select ${errors.endDate ? 'form-input--error' : ''}`}
                    disabled={!endDateTime.datePart}
                  >
                    <option value=''>HH</option>
                    {HOUR_OPTIONS.map((hour) => (
                      <option key={hour} value={hour}>{hour}</option>
                    ))}
                  </select>
                  <span className='event-time-separator'>:</span>
                  <label className='event-sr-only' htmlFor='event-endMinute'>Minute de fin</label>
                  <select
                    id='event-endMinute'
                    value={endDateTime.minutePart}
                    onChange={(event) => setTimePart(
                      'endDate',
                      endDateTime.datePart,
                      endDateTime.hourPart,
                      endDateTime.minutePart,
                      'minute',
                      event.target.value,
                    )}
                    className={`form-select ${errors.endDate ? 'form-input--error' : ''}`}
                    disabled={!endDateTime.datePart}
                  >
                    <option value=''>MM</option>
                    {MINUTE_OPTIONS.map((minute) => (
                      <option key={minute} value={minute}>{minute}</option>
                    ))}
                  </select>
                </div>
              </div>
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
                <span className='banner-upload-hint'>
                  {selectedImageName ?? 'PNG, JPG ou WEBP'}
                  <span className='banner-upload-size-hint'>Taille max : {IMAGE_MAX_SIZE_MB} Mo</span>
                </span>
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
