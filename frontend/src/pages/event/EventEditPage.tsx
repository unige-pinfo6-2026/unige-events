import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useEventForm } from '@/hooks'
import EventForm from '@/components/event/EventForm'
import { deleteEvent, getById } from '@/services/eventApi'
import type { Event } from '@/types/event'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { useToast } from '@/hooks/useToast'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'

function EventFormFixture() {
  return (
    <div className="flex flex-col gap-8">
      {/* Band 1: Banner (left 2fr) | Title + Description (right 3fr) */}
      <div className="grid grid-cols-[2fr_3fr] gap-6 max-lg:grid-cols-1">
        <div className="pt-7 max-lg:pt-0">
          <div className="h-52 rounded-2xl" />
        </div>
        <div className="flex flex-col gap-4">
          <div className="h-[92px] rounded-xl" />
          <div className="h-[192px] rounded-xl" />
        </div>
      </div>
      {/* Band 2: Lieu | Début | Fin */}
      <div className="grid grid-cols-[2fr_1fr_1fr] gap-4 max-sm:grid-cols-1">
        <div className="h-[72px]" />
        <div className="h-[72px]" />
        <div className="h-[72px]" />
      </div>
      {/* Band 3: Catégorie | Capacité | CTA */}
      <div className="flex flex-wrap items-end gap-x-6 gap-y-4">
        <div className="w-48 h-[72px]" />
        <div className="w-24 h-[72px]" />
        <div className="ml-auto max-sm:ml-0 max-sm:w-full w-[140px] h-[68px]" />
      </div>
      {/* Band 4: ComingSoon shells */}
      <div className="flex flex-col gap-3">
        <div className="grid grid-cols-2 gap-3 max-sm:grid-cols-1">
          <div className="h-[92px] rounded-2xl" />
          <div className="h-[92px] rounded-2xl" />
        </div>
        <div className="h-[92px] rounded-2xl" />
        <div className="h-[88px] rounded-2xl" />
        <div className="border-t border-border/20" />
        <div className="h-[124px] rounded-2xl" />
      </div>
      {/* Band 5: Co-organisateurs (edit only) */}
      <div className="flex flex-col gap-3 border-t border-border/30 pt-6">
        <div className="h-5" />
        <div className="h-10 max-w-sm rounded-xl" />
        <div className="h-8" />
      </div>
    </div>
  )
}

export default function EventEditPage() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { id } = useParams<{ id: string }>()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
  const parsedId = id === undefined ? Number.NaN : Number(id)
  const eventId = Number.isInteger(parsedId) && parsedId > 0 ? parsedId : null

  const [event, setEvent] = useState<Event | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  useEffect(() => {
    if (eventId === null) { setLoading(false); return }

    let cancelled = false

    async function loadEvent() {
      setLoading(true)
      setError(null)
      try {
        const response = await getById(eventId!)
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
    }
  }, [eventId])

  const draftMode = event?.status === 'DRAFT'

  const form = useEventForm({
    mode: 'edit',
    initialEvent: event,
    onSuccess: (updatedEvent) => {
      setEvent(updatedEvent)
      if (draftMode && updatedEvent.status === 'DRAFT') {
        showToast('success', 'Brouillon enregistré.')
        navigate('/')
        return
      }
      if (draftMode && updatedEvent.status === 'PUBLISHED') {
        showToast('success', 'Événement créé avec succès.')
        navigate(`/events/${updatedEvent.id}`)
        return
      }
      showToast('success', 'Événement mis à jour avec succès.')
      navigate(`/events/${updatedEvent.id}`)
    },
    onError: (message) => showToast('error', message),
    onBannerError: (message) => sessionStorage.setItem(BANNER_UPLOAD_ERROR_KEY, message),
  })

  async function handleDraftEditSubmit(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault()
    await form.triggerPublish()
  }

  async function confirmDeleteDraft() {
    if (eventId === null || deleting) return
    setDeleting(true)
    try {
      await deleteEvent(eventId)
      form.clearPersistedDraft()
      showToast('success', 'Brouillon supprimé.')
      navigate('/')
    } catch {
      setDeleting(false)
      setShowDeleteConfirm(false)
      showToast('error', 'Impossible de supprimer ce brouillon.')
    }
  }

  if (eventId === null) return <InfoMessage type="error" message="Identifiant d'événement invalide." />

  if (loading) return (
    <SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        heading="lg"
        title={<>Modifier <mark>l'événement</mark></>}
        subtitle="Mettez à jour les informations et republiez pour informer les participants."
      />
      <Skeleton
        name="event-edit"
        loading={true}
        animate="pulse"
        color={skeletonColor}
      ><EventFormFixture /></Skeleton>
    </SectionWrapper>
  )

  if (error) return <InfoMessage type="error" message={error} />
  if (!event) return <InfoMessage type="error" message="Événement introuvable." />

  const title = draftMode
    ? (<>Terminer votre <mark>brouillon</mark></>)
    : (<>Modifier <mark>l'événement</mark></>)
  const subtitle = draftMode
    ? 'Complétez les informations manquantes puis publiez votre événement.'
    : 'Mettez à jour les informations et republiez pour informer les participants.'

  return (
    <SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        heading="lg"
        title={title}
        subtitle={subtitle}
      />
      <EventForm
        mode="edit"
        submitLabel={draftMode ? "Créer l'événement" : 'Enregistrer'}
        values={form.values}
        errors={form.errors}
        submitting={form.submitting}
        draftSaving={form.draftSaving}
        imagePreview={form.imagePreview}
        selectedImageName={form.selectedImageName}
        onFieldChange={form.setFieldValue}
        onImageChange={form.handleImageChange}
        onSubmit={draftMode ? handleDraftEditSubmit : form.handleSubmit}
        onCancel={draftMode ? undefined : () => {
          form.clearPersistedDraft()
          navigate(`/events/${event.id}`)
        }}
        onSaveDraft={draftMode ? form.triggerDraftSave : undefined}
        saveDraftLabel={draftMode ? 'Enregistrer' : undefined}
        onDelete={draftMode ? () => setShowDeleteConfirm(true) : undefined}
        deleting={deleting}
        deleteLabel={draftMode ? 'Supprimer le brouillon' : undefined}
      />

      {showDeleteConfirm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="bg-background border border-border rounded-3xl p-8 max-w-sm w-[90%] shadow-2xl">
            <h2 className="text-lg font-bold text-foreground mb-2">Supprimer le brouillon ?</h2>
            <p className="text-sm text-foreground/50 mb-6">
              Cette action supprimera définitivement le brouillon <strong className="text-foreground">« {event.title || 'Brouillon sans titre'} »</strong>. Elle est irréversible.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                type="button"
                onClick={() => setShowDeleteConfirm(false)}
                disabled={deleting}
                className="px-4 py-2.5 rounded-xl border border-border text-foreground text-sm font-semibold disabled:opacity-50 hover:border-foreground/30 transition-colors cursor-pointer bg-transparent"
              >
                Annuler
              </button>
              <button
                type="button"
                onClick={() => { void confirmDeleteDraft() }}
                disabled={deleting}
                className="px-4 py-2.5 rounded-xl bg-error text-white text-sm font-semibold disabled:opacity-50 hover:bg-error/80 transition-colors cursor-pointer border-0"
              >
                {deleting ? 'Suppression...' : 'Confirmer'}
              </button>
            </div>
          </div>
        </div>
      )}
    </SectionWrapper>
  )
}
