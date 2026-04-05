import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useEventForm } from '@/hooks'
import EventForm from '@/components/event/EventForm'
import Toast from '@/components/utils/Toast'
import { getById } from '@/services/eventApi'
import type { Event } from '@/types/event'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'
import { LoadingSpinner } from '@/components/utils/LoadingSpinner'
import { ErrorMessage } from '@/components/utils/ErrorMessage'

export default function EventEditPage() {
  const navigate = useNavigate()
  const { id } = useParams()
  const eventId = Number(id)
  const [event, setEvent] = useState<Event | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const redirectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (!Number.isInteger(eventId) || eventId <= 0) {
      setError("Identifiant d'événement invalide.")
      setLoading(false)
      return
    }

    let cancelled = false

    async function loadEvent() {
      setLoading(true)
      setError(null)
      try {
        const response = await getById(eventId)
        if (!cancelled) setEvent(response)
      } catch {
        if (!cancelled) setError('Impossible de charger cet événement.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void loadEvent()

    return () => {
      cancelled = true
      if (toastTimerRef.current) clearTimeout(toastTimerRef.current)
      if (redirectTimerRef.current) clearTimeout(redirectTimerRef.current)
    }
  }, [eventId])

  function showToast(type: 'success' | 'error', message: string) {
    setToast({ type, message })
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current)
    toastTimerRef.current = setTimeout(() => setToast(null), 4000)
  }

  const form = useEventForm({
    mode: 'edit',
    initialEvent: event,
    onSuccess: (savedEvent) => {
      setEvent(savedEvent)
      showToast('success', 'Événement mis à jour avec succès.')
      if (redirectTimerRef.current) clearTimeout(redirectTimerRef.current)
      redirectTimerRef.current = setTimeout(() => navigate(`/events/${savedEvent.id}`), 1000)
    },
    onError: (message) => showToast('error', message),
    onBannerError: (message) => sessionStorage.setItem(BANNER_UPLOAD_ERROR_KEY, message),
  })

  if (loading) return <LoadingSpinner/>
  if (error) return <ErrorMessage message={error}/>

  if (!event) return null

  return (
    <>
      <EventForm
        title="Modifier l'événement"
        submitLabel="Enregistrer"
        values={form.values}
        errors={form.errors}
        submitting={form.submitting}
        imagePreview={form.imagePreview}
        selectedImageName={form.selectedImageName}
        onFieldChange={form.setFieldValue}
        onImageChange={form.handleImageChange}
        onSubmit={form.handleSubmit}
        onCancel={() => navigate(`/events/${event.id}`)}
      />
      {toast && <Toast type={toast.type} message={toast.message} />}
    </>
  )
}
