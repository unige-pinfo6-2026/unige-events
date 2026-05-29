import { Lock } from 'lucide-react'
import ProfileHeader from '@/components/profile/ProfileHeader'
import FollowButton from '@/components/user/FollowButton'
import UserBanner from '@/components/user/UserBanner'
import type { UserPublicResponse } from '@/types/user'

interface ProfilePrivateStateProps {
  /**
   * Restricted projection retournée par le backend (cf. SCRUM-169
   * Décision E) — id + username + displayName + avatarUrl, `bannerUrl`
   * null. Quand présent, on garde le même cadre visuel qu'un profil
   * public (banner + avatar overlapping + displayName) pour signaler
   * qu'il s'agit d'un vrai compte, simplement verrouillé. Quand absent
   * (404 backend, user introuvable), on retombe sur un banner gradient
   * générique sans avatar ni displayName.
   */
  profile?: UserPublicResponse | null
  /**
   * `true` quand le viewer est authentifié et ne regarde pas son propre
   * profil — affiche alors un FollowButton pour qu'il puisse envoyer une
   * demande de suivi même sur un compte privé.
   */
  canFollow?: boolean
  /** Callback passé au FollowButton pour re-fetcher le profil après mutation. */
  onProfileMutated?: () => void
}

/**
 * Placeholder « profil privé » pour `/profile/:username` (SCRUM-141 redesign).
 *
 * Affiche le cadre visuel d'un profil public — bannière + avatar +
 * displayName — puis remplace toute la zone de contenu par un grand
 * cadenas centré accompagné du texte « Compte privé ». Quand `canFollow`
 * est vrai et que la projection restreinte contient l'`id`, un FollowButton
 * est rendu dans le header (top-right) pour permettre l'envoi d'une demande.
 */
export default function ProfilePrivateState({
  profile,
  canFollow = false,
  onProfileMutated,
}: Readonly<ProfilePrivateStateProps>) {
  const followAction =
    canFollow && profile ? (
      <FollowButton
        targetId={profile.id}
        followStatus={profile.followStatus}
        onMutated={onProfileMutated}
      />
    ) : null

  return (
    <div>
      {profile ? (
        <ProfileHeader profile={profile} action={followAction} />
      ) : (
        <UserBanner user={null} className="h-52">
          <div className="absolute inset-0 bg-linear-to-t from-background/50 to-transparent" />
        </UserBanner>
      )}

      <div className="max-w-5xl mx-auto px-6 lg:px-8 pb-20">
        <div className="flex flex-col items-center justify-center text-center py-20 lg:py-28 gap-6">
          <Lock
            className="w-24 h-24 lg:w-28 lg:h-28 text-foreground/30"
            strokeWidth={1.5}
            aria-hidden="true"
          />
          <h2 className="text-2xl lg:text-3xl font-bold text-foreground/70">
            Compte privé
          </h2>
        </div>
      </div>
    </div>
  )
}
