import { useMemo, useState } from 'react'
import { Mail, UserPlus, X } from 'lucide-react'
import { Skeleton } from 'boneyard-js/react'
import { useCoOrganizers } from '@/hooks/useCoOrganizers'
import { useAuth } from '@/hooks/useAuth'
import { ButtonPrimary } from '@/components/utils/Buttons'
import FormField from '@/components/utils/FormField'
import UsernameAutocomplete from '@/components/user/UsernameAutocomplete'
import { getUserByUsername } from '@/services/userService'
import { USERNAME_PATTERN } from '@/types/user'
import type { CoOrganizer, CoOrganizerStatus } from '@/types/coOrganizer'

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
 * "Co-organisateurs" section embedded in `EventForm` edit mode. Since SCRUM-169
 * the invitation is by **username** (the user-facing handle) — the editor
 * resolves it to a UUID via `GET /users/by-username/{u}` before hitting the
 * existing `POST /events/{id}/co-organizers` endpoint (which still expects a
 * UUID, cf. SCRUM-137 Décision A on the backend side).
 */
export default function CoOrganizersEditor({ eventId }: Readonly<Props>) {
  const { coOrganizers, loading, error, invite, remove } = useCoOrganizers(eventId)
  const { user } = useAuth()
  const [username, setUsername] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [fieldError, setFieldError] = useState<string | null>(null)

  // Hide the caller's own handle + already-invited handles from the
  // autocomplete dropdown. The backend already excludes the caller, but
  // the existing co-organizers must be filtered client-side.
  const excludeUsernames = useMemo<string[]>(() => {
    const list = coOrganizers.map((co) => co.username).filter((u): u is string => Boolean(u))
    if (user?.username) list.push(user.username)
    return list
  }, [coOrganizers, user?.username])

  async function handleInvite() {
    setFieldError(null)
    const trimmed = username.trim().toLowerCase()
    if (!USERNAME_PATTERN.test(trimmed)) {
      setFieldError('Username invalide (3-30 caractères, a-z, 0-9, ._-).')
      return
    }
    setSubmitting(true)
    try {
      const resolved = await getUserByUsername(trimmed)
      if (resolved === null) {
        setFieldError('Utilisateur introuvable.')
        return
      }
      const outcome = await invite(resolved.id)
      if (outcome.ok) {
        setUsername('')
      } else {
        setFieldError(outcome.error ?? 'Erreur lors de l\'invitation.')
      }
    } catch {
      setFieldError('Erreur réseau lors de la résolution du username.')
    } finally {
      setSubmitting(false)
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

      <FormField label="Username de l'utilisateur à inviter" htmlFor="co-org-username" error={fieldError ?? undefined}>
        <div className="flex gap-2 items-start">
          <div className="flex-1 min-w-0">
            <UsernameAutocomplete
              inputId="co-org-username"
              value={username}
              onChange={(next) => { setUsername(next); if (fieldError) setFieldError(null) }}
              onSelect={(picked) => setUsername(picked.username)}
              placeholder="alice.martin"
              error={fieldError ?? undefined}
              excludeUsernames={excludeUsernames}
              disabled={submitting}
            />
          </div>
          <ButtonPrimary onClick={handleInvite} disabled={submitting || username.trim() === ''} size="sm">
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
  const fallbackLabel = coOrganizer.displayName ?? coOrganizer.username ?? coOrganizer.userId
  const initials = (coOrganizer.displayName ?? coOrganizer.username ?? '??').slice(0, 2).toUpperCase()
  const handle = coOrganizer.username ? `@${coOrganizer.username}` : coOrganizer.userId
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
        <p className="text-sm font-medium text-foreground truncate">{fallbackLabel}</p>
        <p className="text-xs text-foreground/50 truncate">{handle}</p>
      </div>
      <span
        className={`px-2 py-1 rounded-lg text-xs font-medium border ${statusChip[coOrganizer.status]}`}
      >
        {statusLabel[coOrganizer.status]}
      </span>
      <button
        type="button"
        onClick={onRemove}
        aria-label={`Retirer ${fallbackLabel}`}
        className="p-2 rounded-lg text-foreground/40 hover:text-error hover:bg-error/10 transition-colors"
      >
        <X className="w-4 h-4" />
      </button>
    </li>
  )
}
