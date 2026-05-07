import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { X } from 'lucide-react'
import { useEventForm } from '@/hooks'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'
import EventForm from '@/components/event/EventForm'
import DraftsResumeStrip from '@/components/event/DraftsResumeStrip'
import { useToast } from '@/hooks/useToast'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import type { Event } from '@/types/event'

export default function EventCreatePage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { showToast } = useToast()

  const [template, setTemplate] = useState<Event | null>(
    () => (location.state as { template?: Event } | null)?.template ?? null
  )

  const form = useEventForm({
    mode: 'create',
    templateEvent: template,
    onSuccess: (event) => {
      if (event.status === 'DRAFT') {
        showToast('success', 'Brouillon enregistré.')
        navigate('/')
        return
      }
      showToast('success', 'Événement créé avec succès.')
      navigate(`/events/${event.id}`)
    },
    onError: (message) => showToast('error', message),
    onBannerError: (message) => sessionStorage.setItem(BANNER_UPLOAD_ERROR_KEY, message),
  })

  function handleClearTemplate() {
    form.resetForm()
    setTemplate(null)
    navigate('/events/new', { replace: true })
  }

  return (
    <SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        heading="lg"
        title={<>Créer un <mark>événement</mark></>}
        subtitle="Renseignez les informations de votre événement pour le partager avec la communauté UNIGE."
      />
      {template ? (
        <div className="flex items-center justify-between gap-3 rounded-2xl border border-accent/30 bg-accent/10 px-4 py-3 text-sm text-accent">
          <span>Pré-rempli depuis l'événement <strong>"{template.title}"</strong></span>
          <button
            type="button"
            onClick={handleClearTemplate}
            className="flex items-center gap-1.5 shrink-0 px-3 py-1 rounded-lg border border-accent/40 text-accent text-xs font-semibold hover:bg-accent/20 transition-colors cursor-pointer bg-transparent"
          >
            <X className="size-3.5" />
            Effacer le template
          </button>
        </div>
      ) : (
        <DraftsResumeStrip />
      )}
      <EventForm
        mode="create"
        submitLabel="Créer l'événement"
        values={form.values}
        errors={form.errors}
        submitting={form.submitting}
        draftSaving={form.draftSaving}
        imagePreview={form.imagePreview}
        selectedImageName={form.selectedImageName}
        cropSource={form.cropSource}
        cropAspect={form.cropAspect}
        onFieldChange={form.setFieldValue}
        onImageChange={form.handleImageChange}
        onCropConfirm={form.confirmCrop}
        onCropCancel={form.cancelCrop}
        onSubmit={form.handleSubmit}
        onCancel={() => {
          form.clearPersistedDraft()
          navigate('/')
        }}
        onSaveDraft={form.triggerDraftSave}
      />
    </SectionWrapper>
  )
}
