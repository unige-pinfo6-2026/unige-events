import type { ReactNode } from 'react'
import UserAvatar from '@/components/user/UserAvatar'
import UserBanner from '@/components/user/UserBanner'
import StaffBadge from '@/components/profile/StaffBadge'
import { type UserPublicResponse, isStaff } from '@/types/user'

interface ProfileHeaderProps {
  profile: UserPublicResponse
  children?: ReactNode
  stats?: ReactNode
  action?: ReactNode
}

export default function ProfileHeader({ profile, children, stats, action }: Readonly<ProfileHeaderProps>) {
  const showStaffBadge = isStaff(profile.roles)

  return (
    <>
      <UserBanner user={profile} className="h-52">
        <div className="absolute inset-0 bg-linear-to-t from-background/50 to-transparent" />
      </UserBanner>

      <div className="max-w-5xl mx-auto px-6 lg:px-8">
        <div className="relative z-10 -mt-14">
          <div className="flex flex-wrap items-start gap-x-8 gap-y-6 justify-between">
            <div className="min-w-0 flex flex-col">
              <UserAvatar user={profile} className="size-28 shrink-0 ring-4 ring-background shadow-2xl" />

              <div className="mt-4 flex flex-col items-start gap-1">
                <h1 className="text-3xl lg:text-4xl font-bold tracking-tight leading-tight wrap-anywhere">
                  {profile.displayName}
                </h1>
                {profile.username && (
                  <p className="text-sm text-foreground/50 font-medium">@{profile.username}</p>
                )}
                {showStaffBadge && <StaffBadge />}
              </div>
            </div>

            {(stats || action) && (
              // Mobile-first : colonne full-width par défaut (wrapping naturel sous
              // l'identité). À partir de 583px les deux colonnes tiennent sur la même
              // ligne → on revient à la largeur naturelle + ml-auto pour pousser à droite.
              // Sans stats : self-center centre le bouton verticalement dans la ligne.
              // Avec stats + ≤520px : le bouton est en absolu top-right (à côté de
              // l'avatar) pendant que les compteurs occupent toute la largeur en dessous.
              <div className={`flex flex-col gap-4 items-end ${
                stats
                  // Avec stats : full-width par défaut (wrapping naturel sous l'identité).
                  // À partir de 583px les deux colonnes tiennent sur la même ligne.
                  ? 'w-full ml-0 min-[583px]:w-auto min-[583px]:shrink-0 min-[583px]:ml-auto min-[583px]:pt-2'
                  // Sans stats : largeur naturelle + ml-auto à tous les breakpoints,
                  // self-center pour aligner verticalement le bouton avec le bloc identité.
                  : 'shrink-0 self-center'
              }`}>
                {stats}
                {action && (
                  // flex justify-end garantit que le bouton reste à droite quel que soit
                  // le contexte (le wrapper peut être un block qui stretche à ≥521px).
                  // L'absolu ≤520px remonte le bouton à côté de l'avatar quand stats présents.
                  <div className={`flex justify-end ${stats ? 'max-[583px]:absolute max-[583px]:right-0 max-[583px]:top-16 max-[583px]:z-20' : ''}`}>
                    {action}
                  </div>
                )}
              </div>
            )}
          </div>

          {children && (
            <div className="min-w-0 mt-6">
              {children}
            </div>
          )}
        </div>
      </div>
    </>
  )
}
