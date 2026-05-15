import { useMemo, useState } from 'react'
import { Mail, UserPlus, X } from 'lucide-react'
import { ButtonPrimary } from '@/components/utils/Buttons'
import FormField from '@/components/utils/FormField'
import UsernameAutocomplete from '@/components/user/UsernameAutocomplete'
import { useAuth } from '@/hooks/useAuth'
import { getUserByUsername } from '@/services/userService'
import { USERNAME_PATTERN } from '@/types/user'

/**
 * SCRUM-137 — co-organizer entry staged client-side in `EventCreatePage` before
 * the event exists in the backend. After `POST /events` succeeds the parent
 * page dispatches one `POST /events/{id}/co-organizers` per entry. Mirror of
 * `CoOrganizerDTO` minus the row id + status (everything is PENDING until the
 * invitee accepts).
 */
export interface PendingCoOrganizer {
  userId: string
  username: string
  displayName: string | null
  avatarUrl: string | null
}

interface Props {
  pending: PendingCoOrganizer[]
  onAdd: (entry: PendingCoOrganizer) => void
  onRemove: (userId: string) => void
}

/**
 * "Co-organisateurs" section embedded in `EventForm` create mode. Unlike the
 * edit-mode `CoOrganizersEditor`, this variant cannot call
 * `POST /events/{id}/co-organizers` (no event yet) — it stages entries client
 * side and exposes them through `onAdd`/`onRemove` so `EventCreatePage` can
 * dispatch the invites after the event is created.
 */
export default function PendingCoOrganizersEditor({
  pending,
  onAdd,
  onRemove,
}: Readonly<Props>) {
  const { user } = useAuth()
  const [username, setUsername] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [fieldError, setFieldError] = useState<string | null>(null)

  // Hide the caller's own handle and already-staged entries from the
  // autocomplete dropdown.
  const excludeUsernames = useMemo<string[]>(() => {
    const list = pending.map((p) => p.username)
    if (user?.username) list.push(user.username)
    return list
  }, [pending, user])

  async function handleAdd() {
    setFieldError(null)
    const trimmed = username.trim().toLowerCase()
    if (!USERNAME_PATTERN.test(trimmed)) {
      setFieldError('Username invalide (3-30 caractères, a-z, 0-9, ._-).')
      return
    }
    if (pending.some((p) => p.username === trimmed)) {
      setFieldError('Cet utilisateur est déjà dans la liste.')
      return
    }
    setSubmitting(true)
    try {
      const resolved = await getUserByUsername(trimmed)
      if (resolved === null) {
        setFieldError('Utilisateur introuvable.')
        return
      }
      onAdd({
        userId: resolved.id,
        username: resolved.username,
        displayName: resolved.displayName ?? null,
        avatarUrl: resolved.avatarUrl ?? null,
      })
      setUsername('')
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
            Les invitations seront envoyées après la création de l'événement.
          </p>
        </div>
      </header>

      <FormField label="Username de l'utilisateur à inviter" htmlFor="pending-co-org-username" error={fieldError ?? undefined}>
        <div className="flex gap-2 items-start">
          <div className="flex-1 min-w-0">
            <UsernameAutocomplete
              inputId="pending-co-org-username"
              value={username}
              onChange={(next) => { setUsername(next); if (fieldError) setFieldError(null) }}
              onSelect={(picked) => setUsername(picked.username)}
              placeholder="alice.martin"
              error={fieldError ?? undefined}
              excludeUsernames={excludeUsernames}
              disabled={submitting}
            />
          </div>
          <ButtonPrimary onClick={handleAdd} disabled={submitting || username.trim() === ''} size="sm">
            <Mail className="w-4 h-4" />
            Ajouter
          </ButtonPrimary>
        </div>
      </FormField>

      <div className="mt-5">
        {pending.length === 0 ? (
          <p className="text-sm text-foreground/50 italic">Aucun co-organisateur pour l'instant.</p>
        ) : (
          <ul className="space-y-2">
            {pending.map((entry) => (
              <PendingRow key={entry.userId} entry={entry} onRemove={() => onRemove(entry.userId)} />
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function PendingRow({
  entry,
  onRemove,
}: Readonly<{ entry: PendingCoOrganizer; onRemove: () => void }>) {
  const fallbackLabel = entry.displayName ?? entry.username
  const initials = (entry.displayName ?? entry.username).slice(0, 2).toUpperCase()
  return (
    <li className="flex items-center gap-3 p-3 rounded-2xl border border-border bg-background/30">
      <div className="w-10 h-10 rounded-full bg-foreground/10 flex items-center justify-center shrink-0 overflow-hidden">
        {entry.avatarUrl ? (
          <img src={entry.avatarUrl} alt="" className="w-full h-full object-cover" />
        ) : (
          <span className="text-xs font-semibold text-foreground/60">{initials}</span>
        )}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-foreground truncate">{fallbackLabel}</p>
        <p className="text-xs text-foreground/50 truncate">@{entry.username}</p>
      </div>
      <span className="px-2 py-1 rounded-lg text-xs font-medium border bg-warning/10 text-warning border-warning/30">
        À inviter
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
