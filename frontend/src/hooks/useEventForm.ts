import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import axios from 'axios'
import { createEvent, updateEvent, uploadEventImage } from '@/services/eventApi'
import {
  type CreateEventRequest,
  type Event,
  type EventCategory,
  type EventStatus,
  type UpdateEventRequest,
} from '@/types/event'
import { toLocalDateTimeInputValue } from '@/utils/dateTime'

export interface EventFormValues {
  title: string
  description: string
  location: string
  startDate: string
  endDate: string
  category: '' | EventCategory
  capacity: string
  status: EventStatus
}

export interface EventFormErrors {
  title?: string
  description?: string
  location?: string
  startDate?: string
  endDate?: string
  category?: string
  capacity?: string
  image?: string
}

interface UseEventFormOptions {
  mode: 'create' | 'edit'
  initialEvent?: Event | null
  onSuccess?: (event: Event) => void
  onError?: (message: string) => void
  onBannerError?: (message: string) => void
}

interface UseEventFormResult {
  values: EventFormValues
  errors: EventFormErrors
  submitting: boolean
  draftSaving: boolean
  imagePreview: string | null
  selectedImageName: string | null
  setFieldValue: <K extends keyof EventFormValues>(field: K, value: EventFormValues[K]) => void
  handleImageChange: (event: ChangeEvent<HTMLInputElement>) => void
  handleSubmit: (event: FormEvent<HTMLFormElement>) => Promise<void>
  triggerDraftSave: () => Promise<void>
  triggerPublish: () => Promise<void>
  clearPersistedDraft: () => void
}

// Local persistence for the in-progress form so an accidental refresh of the page
// does not wipe the user's entries. Scoped to the browser tab via sessionStorage.
// The banner image is intentionally NOT persisted (File objects aren't serializable
// and blob: URLs die on refresh anyway).
// - Create flow uses a single key.
// - Edit flow uses one key per event id so multiple drafts being edited in different
//   tabs (or successive visits in the same tab) don't collide.
export const DRAFT_FORM_KEY = 'unige:event-create-draft'
export const EDIT_FORM_KEY_PREFIX = 'unige:event-edit-draft:'
// Debounce interval for persisting form changes. Aligned with useEventSearch (300ms for
// suggestions) — feels instant to the user, batches rapid keystrokes in one write.
export const DRAFT_FORM_PERSIST_DEBOUNCE_MS = 300

interface ValidationErrorDetail {
  field?: string | null
  message: string
}

const DEFAULT_VALUES: EventFormValues = {
  title: '',
  description: '',
  location: '',
  startDate: '',
  endDate: '',
  category: '',
  capacity: '',
  status: 'PUBLISHED',
}

const VALIDATABLE_FIELDS = new Set<keyof EventFormErrors>([
  'title',
  'description',
  'location',
  'startDate',
  'endDate',
  'category',
  'capacity',
])

const FIELD_LABELS: Record<string, string> = {
  title: 'Le titre',
  description: 'La description',
  location: 'Le lieu',
  startDate: 'La date de début',
  endDate: 'La date de fin',
  category: 'La catégorie',
  capacity: 'La capacité',
  status: 'Le statut',
  bannerUrl: 'La bannière',
}

export const EVENT_TITLE_MAX_LENGTH = 120
export const EVENT_DESCRIPTION_MAX_LENGTH = 2000
export const IMAGE_MAX_SIZE_MB = 5
export const IMAGE_MAX_SIZE_BYTES = IMAGE_MAX_SIZE_MB * 1024 * 1024

function toApiDateTime(dateTime: string): string {
  return new Date(dateTime).toISOString()
}

function toFormValues(event?: Event | null): EventFormValues {
  if (!event) {
    return { ...DEFAULT_VALUES }
  }

  return {
    title: event.title,
    description: event.description ?? '',
    location: event.location,
    startDate: toLocalDateTimeInputValue(event.startDate),
    endDate: toLocalDateTimeInputValue(event.endDate),
    category: event.category,
    capacity: event.capacity?.toString() ?? '',
    status: event.status,
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function readPersistedForm(key: string): EventFormValues | null {
  try {
    const raw = sessionStorage.getItem(key)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (!isObject(parsed)) return null
    return { ...DEFAULT_VALUES, ...(parsed as Partial<EventFormValues>) }
  } catch (err) {
    console.warn('[useEventForm] failed to restore persisted form', err)
    try { sessionStorage.removeItem(key) } catch { /* ignore */ }
    return null
  }
}

function writePersistedForm(key: string, values: EventFormValues) {
  try {
    sessionStorage.setItem(key, JSON.stringify(values))
  } catch { /* ignore quota / private mode */ }
}

function clearPersistedForm(key: string) {
  try { sessionStorage.removeItem(key) } catch { /* ignore */ }
}

function editFormKey(eventId: number): string {
  return `${EDIT_FORM_KEY_PREFIX}${eventId}`
}

function isProbablyFrenchMessage(message: string): boolean {
  return /[àâçéèêëîïôûùüÿœ]/i.test(message) || /\b(le|la|les|un|une|des|est|sont|doit|requise|invalide|événement)\b/i.test(message)
}

function isTechnicalValidationMessage(message: string): boolean {
  const normalized = message.trim().toLowerCase()
  return normalized === 'post validation error'
    || normalized === 'put validation error'
    || normalized === 'validation error'
    || normalized === 'validation failed'
    || normalized === 'constraint violation'
    || normalized.includes('validation')
    || normalized.includes('constraint')
}

function getCurrentMinute(): Date {
  const now = new Date()
  now.setSeconds(0, 0)
  return now
}

function getValidationDetails(error: unknown): ValidationErrorDetail[] {
  if (!axios.isAxiosError(error) || !isObject(error.response?.data)) {
    return []
  }

  const { details } = error.response.data
  if (!Array.isArray(details)) {
    return []
  }

  return details.flatMap((detail) => {
    if (!isObject(detail) || typeof detail.message !== 'string' || !detail.message.trim()) {
      return []
    }

    return [{
      field: typeof detail.field === 'string' ? detail.field : null,
      message: detail.message.trim(),
    }]
  })
}

function localizeValidationDetail({ field, message }: ValidationErrorDetail): string {
  const normalized = message.toLowerCase()
  const fieldLabel = field ? FIELD_LABELS[field] : null

  if (normalized === 'must not be blank' || normalized === 'must not be null') {
    return fieldLabel ? fieldLabel + ' est requis.' : 'Ce champ est requis.'
  }

  if (normalized.includes('must be in the future') || normalized.includes('must be a future date')) {
    return fieldLabel ? fieldLabel + ' doit être dans le futur.' : 'La date doit être dans le futur.'
  }

  if (field === 'endDate' && normalized.includes('after')) {
    return 'La date de fin doit être postérieure à la date de début.'
  }

  if (normalized.includes('must be greater than 0')) {
    return fieldLabel ? fieldLabel + ' doit être supérieure à 0.' : 'La valeur doit être supérieure à 0.'
  }

  if (normalized.includes('must be greater than or equal to 1')) {
    return fieldLabel ? fieldLabel + ' doit être supérieure ou égale à 1.' : 'La valeur doit être supérieure ou égale à 1.'
  }

  if (isProbablyFrenchMessage(message)) {
    return message
  }

  return fieldLabel ? fieldLabel + ' : ' + message : message
}

function getApiErrorMessage(error: unknown, mode: 'create' | 'edit'): string {
  const details = getValidationDetails(error)
  if (details.length > 0) {
    return details.map(localizeValidationDetail).join(' ')
  }

  if (axios.isAxiosError(error)) {
    const rawMessage = error.response?.data?.message
    if (typeof rawMessage === 'string' && rawMessage.trim()) {
      if (isTechnicalValidationMessage(rawMessage)) {
        return 'La validation de l\'événement a échoué. Vérifiez les informations saisies.'
      }

      if (isProbablyFrenchMessage(rawMessage)) {
        return rawMessage.trim()
      }
    }
  }

  return mode === 'create'
    ? 'La création de l\'événement a échoué. Veuillez réessayer.'
    : 'La mise à jour de l\'événement a échoué. Veuillez réessayer.'
}

export function useEventForm({ mode, initialEvent, onSuccess, onError, onBannerError }: UseEventFormOptions): UseEventFormResult {
  const [values, setValues] = useState<EventFormValues>(() => {
    // Create mode: try sessionStorage first. Edit mode hydration happens in the effect
    // below once `initialEvent` is loaded (its id is required to compute the per-event key).
    if (mode === 'create') {
      const persisted = readPersistedForm(DRAFT_FORM_KEY)
      if (persisted) return persisted
    }
    return toFormValues(initialEvent)
  })
  const [errors, setErrors] = useState<EventFormErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [draftSaving, setDraftSaving] = useState(false)
  const [imagePreview, setImagePreview] = useState<string | null>(initialEvent?.bannerUrl ?? null)
  const [selectedImageName, setSelectedImageName] = useState<string | null>(null)
  const [imageFile, setImageFile] = useState<File | null>(null)
  const objectUrlRef = useRef<string | null>(null)
  const forcedStatusRef = useRef<EventStatus | null>(null)
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const pendingPersistRef = useRef<{ key: string; values: EventFormValues } | null>(null)

  function currentPersistKey(): string | null {
    if (mode === 'create') return DRAFT_FORM_KEY
    if (initialEvent?.id != null) return editFormKey(initialEvent.id)
    return null
  }

  function flushPersist() {
    if (persistTimerRef.current) {
      clearTimeout(persistTimerRef.current)
      persistTimerRef.current = null
    }
    if (pendingPersistRef.current) {
      writePersistedForm(pendingPersistRef.current.key, pendingPersistRef.current.values)
      pendingPersistRef.current = null
    }
  }

  function cancelPersist() {
    if (persistTimerRef.current) {
      clearTimeout(persistTimerRef.current)
      persistTimerRef.current = null
    }
    pendingPersistRef.current = null
  }

  function schedulePersist(next: EventFormValues) {
    const key = currentPersistKey()
    if (!key) return
    pendingPersistRef.current = { key, values: next }
    if (persistTimerRef.current) clearTimeout(persistTimerRef.current)
    persistTimerRef.current = setTimeout(() => {
      if (pendingPersistRef.current) writePersistedForm(pendingPersistRef.current.key, pendingPersistRef.current.values)
      pendingPersistRef.current = null
      persistTimerRef.current = null
    }, DRAFT_FORM_PERSIST_DEBOUNCE_MS)
  }

  useEffect(() => {
    // Only fire when we actually receive an event to hydrate from (edit mode, after the
    // async getById resolves). In create mode initialEvent is always nullish and resetting
    // here would wipe the sessionStorage hydration performed in the useState initializer.
    if (!initialEvent) return
    // Edit mode: prefer a per-event persisted form if the user had unsaved edits from a
    // previous render of the same event (typical F5 recovery). Fall back to the event
    // loaded from the backend otherwise.
    const persisted = mode === 'edit' ? readPersistedForm(editFormKey(initialEvent.id)) : null
    setValues(persisted ?? toFormValues(initialEvent))
    setImagePreview(initialEvent.bannerUrl ?? null)
    setSelectedImageName(null)
    setImageFile(null)
    setErrors({})
  }, [initialEvent, mode])

  useEffect(() => () => {
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
    }
    // Flush any pending debounced write so a fast unmount (e.g. refresh) doesn't drop
    // the last keystrokes. Safe in edit mode because no write is ever scheduled there.
    flushPersist()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function setFieldValue<K extends keyof EventFormValues>(field: K, value: EventFormValues[K]) {
    setValues((current) => {
      const next = { ...current, [field]: value }
      // schedulePersist is a no-op if currentPersistKey() returns null (edit mode before
      // the async event load), so we can call it unconditionally for both flows.
      schedulePersist(next)
      return next
    })

    if (VALIDATABLE_FIELDS.has(field as keyof EventFormErrors)) {
      setErrors((current) => ({ ...current, [field]: undefined }))
    }
  }

  function handleImageChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }

    if (!file.type.startsWith('image/')) {
      setErrors((current) => ({ ...current, image: 'Le fichier doit être une image.' }))
      return
    }

    if (file.size > IMAGE_MAX_SIZE_BYTES) {
      setErrors((current) => ({ ...current, image: `Le fichier dépasse la taille maximale autorisée (${IMAGE_MAX_SIZE_MB} Mo).` }))
      return
    }

    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
    }

    const previewUrl = URL.createObjectURL(file)
    objectUrlRef.current = previewUrl

    setImageFile(file)
    setSelectedImageName(file.name)
    setImagePreview(previewUrl)
    setErrors((current) => ({ ...current, image: undefined }))
  }

  function validate(): boolean {
    const nextErrors: EventFormErrors = {}

    if (!values.title.trim()) {
      nextErrors.title = 'Le titre est requis.'
    } else if (values.title.trim().length > EVENT_TITLE_MAX_LENGTH) {
      nextErrors.title = `Le titre ne doit pas dépasser ${EVENT_TITLE_MAX_LENGTH} caractères.`
    }

    if (values.description.trim().length > EVENT_DESCRIPTION_MAX_LENGTH) {
      nextErrors.description = `La description ne doit pas dépasser ${EVENT_DESCRIPTION_MAX_LENGTH} caractères.`
    }

    if (!values.location.trim()) {
      nextErrors.location = 'Le lieu est requis.'
    }

    if (!values.startDate) {
      nextErrors.startDate = 'La date de début est requise.'
    }

    if (!values.endDate) {
      nextErrors.endDate = 'La date de fin est requise.'
    }

    if (!values.category) {
      nextErrors.category = 'La catégorie est requise.'
    }

    if (values.startDate && values.endDate) {
      const startDate = new Date(values.startDate)
      const endDate = new Date(values.endDate)

      if (Number.isNaN(startDate.getTime())) {
        nextErrors.startDate = 'La date de début est invalide.'
      }

      if (Number.isNaN(endDate.getTime())) {
        nextErrors.endDate = 'La date de fin est invalide.'
      }

      if (!nextErrors.startDate && startDate < getCurrentMinute()) {
        nextErrors.startDate = 'La date de début doit être postérieure à la date et à l\'heure actuelles.'
      }

      if (!nextErrors.startDate && !nextErrors.endDate && endDate <= startDate) {
        nextErrors.endDate = 'La date de fin doit être postérieure à la date de début.'
      }
    }

    if (values.capacity.trim()) {
      const capacity = Number(values.capacity)
      if (!Number.isInteger(capacity) || capacity <= 0) {
        nextErrors.capacity = 'La capacité doit être un entier strictement positif.'
      }
    }

    setErrors(nextErrors)
    return Object.keys(nextErrors).length === 0
  }

  async function submitForm(kind: 'publish' | 'draft') {
    if (submitting || draftSaving) {
      forcedStatusRef.current = null
      return
    }

    const effectiveStatus: EventStatus = forcedStatusRef.current ?? values.status
    forcedStatusRef.current = null

    if (!validate()) {
      return
    }

    const setInFlight = kind === 'publish' ? setSubmitting : setDraftSaving
    setInFlight(true)

    let savedEvent: Event

    // Step 1: create / update the event
    try {
      const payload: CreateEventRequest = {
        title: values.title.trim(),
        description: values.description.trim() || undefined,
        location: values.location.trim(),
        startDate: toApiDateTime(values.startDate),
        endDate: toApiDateTime(values.endDate),
        category: values.category || "OTHER",
        capacity: values.capacity.trim() ? Number(values.capacity) : undefined,
        status: effectiveStatus,
      }

      if (mode === 'create') {
        savedEvent = await createEvent(payload)
      } else {
        if (!initialEvent) {
          throw new Error('Missing event for edit mode')
        }

        const updatePayload: UpdateEventRequest = {
          ...payload,
          title: payload.title,
          location: payload.location,
          startDate: payload.startDate,
          endDate: payload.endDate,
          category: payload.category,
          bannerUrl: initialEvent.bannerUrl,
        }

        savedEvent = await updateEvent(initialEvent.id, updatePayload)
      }
    } catch (error) {
      onError?.(getApiErrorMessage(error, mode))
      setInFlight(false)
      return
    }

    // Step 2: banner upload (optional, non-blocking for navigation)
    if (imageFile) {
      try {
        savedEvent = await uploadEventImage(savedEvent.id, imageFile)
      } catch {
        onBannerError?.("L'événement a été créé mais la bannière n'a pas pu être uploadée.")
      }
    }

    setInFlight(false)
    // Clean up any in-flight debounced write + the persisted key for the current flow.
    // Works for both create (single key) and edit (per-event key): once the backend has
    // accepted the payload, the sessionStorage cache is stale and must not resurrect.
    cancelPersist()
    const key = currentPersistKey()
    if (key) clearPersistedForm(key)
    onSuccess?.(savedEvent)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await submitForm('publish')
  }

  async function triggerDraftSave() {
    forcedStatusRef.current = 'DRAFT'
    await submitForm('draft')
  }

  async function triggerPublish() {
    forcedStatusRef.current = 'PUBLISHED'
    await submitForm('publish')
  }

  function clearPersistedDraft() {
    cancelPersist()
    const key = currentPersistKey()
    if (key) clearPersistedForm(key)
  }

  return {
    values,
    errors,
    submitting,
    draftSaving,
    imagePreview,
    selectedImageName,
    setFieldValue,
    handleImageChange,
    handleSubmit,
    triggerDraftSave,
    triggerPublish,
    clearPersistedDraft,
  }
}
