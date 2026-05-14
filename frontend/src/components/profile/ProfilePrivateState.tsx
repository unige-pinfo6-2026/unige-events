import { Lock } from 'lucide-react'
import type { FollowStatus } from '@/types/user'

interface ProfilePrivateStateProps {
  /**
   * Caller's follow status toward the target. `null` / `'ACCEPTED'` produce
   * no badge — `ACCEPTED` should normally surface the full profile, so this
   * component is only reached when the caller doesn't have follower access
   * to a private profile (backend returned 404 — anti-oracle ISSUE-93).
   *
   * Only `'PENDING'` produces the "Demande de suivi envoyée" disabled badge.
   */
  followStatus?: FollowStatus | null
}

/**
 * Private-profile placeholder card for `/profile/:id` (SCRUM-141).
 *
 * The backend's anti-oracle ISSUE-93 returns 404 indistinguishably for
 * "user does not exist" and "profile is private (caller is not owner /
 * admin)". The page surfaces both as this same component so the existence
 * of private accounts is never leaked.
 */
export default function ProfilePrivateState({ followStatus }: Readonly<ProfilePrivateStateProps>) {
  return (
    <div>
      <div className="relative h-52 overflow-hidden bg-linear-to-br from-foreground/5 via-foreground/3 to-foreground/5">
        <div className="absolute inset-0 bg-linear-to-t from-background/60 to-transparent" />
      </div>
      <div className="max-w-5xl mx-auto px-6 lg:px-8 -mt-10 flex justify-center pb-20">
        <div className="bg-linear-to-br from-background/90 to-background/60 backdrop-blur-xl border border-border rounded-3xl p-10 max-w-sm w-full flex flex-col items-center gap-4 text-center">
          <div className="w-16 h-16 rounded-2xl bg-foreground/5 border border-border flex items-center justify-center">
            <Lock className="w-7 h-7 text-foreground/30" />
          </div>
          <div>
            <h2 className="font-bold text-xl mb-1">Ce profil est privé</h2>
            <p className="text-foreground/40 text-sm leading-relaxed">
              Cet utilisateur a choisi de ne pas rendre son profil public.
            </p>
          </div>
          {followStatus === 'PENDING' && (
            <span
              aria-disabled="true"
              className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-foreground/5 border border-border text-xs font-semibold text-foreground/50 cursor-not-allowed select-none"
            >
              Demande de suivi envoyée
            </span>
          )}
        </div>
      </div>
    </div>
  )
}
