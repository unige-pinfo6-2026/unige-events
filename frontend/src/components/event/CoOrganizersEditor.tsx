import { useState } from 'react'
import { Mail, UserPlus, X } from 'lucide-react'
import { Skeleton } from 'boneyard-js/react'
import { useCoOrganizers } from '@/hooks/useCoOrganizers'
import { ButtonPrimary } from '@/components/utils/Buttons'
import FormField, { Input } from '@/components/utils/FormField'
import type { CoOrganizer, CoOrganizerStatus } from '@/types/coOrganizer'

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

const statusChip: Record<CoOrganizerStatus, string> = {
  PENDING: 'bg-warning/10 text-warning border-warning/30',
  ACCEPTED: 'bg-emerald-500/10 text-emerald-500 border-emerald-500/30',
  DECLINED: 'bg-foreground/10 text-foreground/60 border-border',
}

const statusLabel: Record<CoOrganizerStatus, string> = {
  PENDING: 'En attente',
  ACCEPTED: 'Accepté',
  DECLINED: 'Refusé',
}

interface Props {
  eventId: number
}

/**
 * "Co-organisateurs" section embedded in `EventForm` edit mode. Invitation is
 * by UUID (Décision A — `GET /users/search` does not exist server-side).
 * The 404 response from `POST /events/{id}/co-organizers` (unknown UUID) is
 * surfaced as a friendly error inside the field.
 */
export default function CoOrganizersEditor({ eventId }: Readonly<Props>) {
  const { coOrganizers, loading, error, invite, remove } = useCoOrganizers(eventId)
  const [userId, setUserId] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [fieldError, setFieldError] = useState<string | null>(null)

  async function handleInvite() {
    setFieldError(null)
    const trimmed = userId.trim()
    if (!UUID_RE.test(trimmed)) {
      setFieldError('Format UUID v4 attendu.')
      return
    }
    setSubmitting(true)
    const outcome = await invite(trimmed)
    setSubmitting(false)
    if (outcome.ok) {
      setUserId('')
    } else {
      setFieldError(outcome.error ?? 'Erreur lors de l\'invitation.')
    }
  }

  return (
    <section
      aria-label="Co-organisateurs"
      className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border"
    >
      <header className="flex items-center gap-3 mb-4">
        <div className="w-10 h-10 rounded-2xl bg-linear-to-br from-accent/30 to-pink-600/30 flex items-center justify-center">
          <UserPlus className="w-5 h-5 text-accent" />
        </div>
        <div>
          <h3 className="text-lg font-semibold text-foreground">Co-organisateurs</h3>
          <p className="text-xs text-foreground/60">
            Invitez d'autres personnes à gérer cet événement avec vous.
          </p>
        </div>
      </header>

      <FormField label="UUID de l'utilisateur à inviter" htmlFor="co-org-uuid" error={fieldError ?? undefined}>
        <div className="flex gap-2">
          <Input
            id="co-org-uuid"
            type="text"
            placeholder="00000000-0000-0000-0000-000000000000"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            error={fieldError ?? undefined}
            disabled={submitting}
          />
          <ButtonPrimary onClick={handleInvite} disabled={submitting || userId.trim() === ''} size="sm">
            <Mail className="w-4 h-4" />
            Inviter
          </ButtonPrimary>
        </div>
      </FormField>

      <div className="mt-5">
        {loading && (
          <Skeleton name="co-organizers-section" loading={true}>
            <div className="space-y-2">
              <div className="h-14 rounded-2xl" />
              <div className="h-14 rounded-2xl" />
            </div>
          </Skeleton>
        )}
        {!loading && error && (
          <p className="text-sm text-error">{error}</p>
        )}
        {!loading && !error && coOrganizers.length === 0 && (
          <p className="text-sm text-foreground/50 italic">Aucun co-organisateur pour l'instant.</p>
        )}
        {!loading && coOrganizers.length > 0 && (
          <ul className="space-y-2">
            {coOrganizers.map((co) => (
              <CoOrganizerRow key={co.id} coOrganizer={co} onRemove={() => remove(co.userId)} />
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function CoOrganizerRow({
  coOrganizer,
  onRemove,
}: Readonly<{ coOrganizer: CoOrganizer; onRemove: () => void }>) {
  const initials = (coOrganizer.displayName ?? '??').slice(0, 2).toUpperCase()
  return (
    <li className="flex items-center gap-3 p-3 rounded-2xl border border-border bg-background/30">
      <div className="w-10 h-10 rounded-full bg-foreground/10 flex items-center justify-center shrink-0 overflow-hidden">
        {coOrganizer.avatarUrl ? (
          <img src={coOrganizer.avatarUrl} alt="" className="w-full h-full object-cover" />
        ) : (
          <span className="text-xs font-semibold text-foreground/60">{initials}</span>
        )}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-foreground truncate">
          {coOrganizer.displayName ?? coOrganizer.userId}
        </p>
        <p className="text-xs text-foreground/50 truncate">{coOrganizer.userId}</p>
      </div>
      <span
        className={`px-2 py-1 rounded-lg text-xs font-medium border ${statusChip[coOrganizer.status]}`}
      >
        {statusLabel[coOrganizer.status]}
      </span>
      <button
        type="button"
        onClick={onRemove}
        aria-label={`Retirer ${coOrganizer.displayName ?? coOrganizer.userId}`}
        className="p-2 rounded-lg text-foreground/40 hover:text-error hover:bg-error/10 transition-colors"
      >
        <X className="w-4 h-4" />
      </button>
    </li>
  )
}
