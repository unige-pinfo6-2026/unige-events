import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import './EventPage.css'
import { useEventForm } from '@/hooks'
import EventForm from '@/components/event/EventForm'

function CreateEventPage() {
  const navigate = useNavigate()
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const redirectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => () => {
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current)
    if (redirectTimerRef.current) clearTimeout(redirectTimerRef.current)
  }, [])

  function showToast(type: 'success' | 'error', message: string) {
    setToast({ type, message })
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current)
    toastTimerRef.current = setTimeout(() => setToast(null), 4000)
  }

  const form = useEventForm({
    mode: 'create',
    onSuccess: (event) => {
      showToast('success', 'Événement créé avec succès.')
      if (redirectTimerRef.current) clearTimeout(redirectTimerRef.current)
      redirectTimerRef.current = setTimeout(() => navigate(`/events/${event.id}`), 1000)
    },
    onError: (message) => showToast('error', message),
  })

  return (
    <>
      <EventForm
        title="Créer un événement"
        submitLabel="Créer l'événement"
        values={form.values}
        errors={form.errors}
        submitting={form.submitting}
        imagePreview={form.imagePreview}
        selectedImageName={form.selectedImageName}
        onFieldChange={form.setFieldValue}
        onImageChange={form.handleImageChange}
        onSubmit={form.handleSubmit}
        onCancel={() => navigate('/home')}
      />

      {toast && <output className={`event-toast event-toast--${toast.type}`}>{toast.message}</output>}
    </>
  )
}

export default CreateEventPage
