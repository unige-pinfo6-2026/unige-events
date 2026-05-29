import { Shield } from 'lucide-react'

/**
 * Pill rendu à côté du `displayName` dans `ProfileHeader` quand le
 * profil affiché a un rôle Auth0 qualifiant comme "staff" (cf.
 * `isStaff(roles)` dans `@/types/user`).
 *
 * **Source des rôles** : champ `roles: string[]` de `UserPublicResponse`
 * exposé par le backend. Les rôles sont mirrorés depuis la claim JWT
 * Auth0 du propriétaire du profil à chaque hit `GET /users/me` — le
 * frontend les lit sur la réponse REST, indépendamment du rôle du
 * viewer. Cela permet d'afficher le badge sur **n'importe quel** profil
 * d'admin, pas seulement celui du viewer connecté.
 *
 * **Style** : pill bleue via le token `--color-link` (sky
 * `rgb(2,132,199)` light / `rgb(56,189,248)` dark), le seul token bleu du
 * thème (cf. `frontend/AGENTS.md`).
 */
export default function StaffBadge() {
  return (
    <span
      className="inline-flex items-center gap-1 shrink-0 text-[10px] font-bold uppercase tracking-widest px-2 py-0.5 rounded-full border bg-link/20 text-link border-link/40"
      aria-label="Membre du staff"
    >
      <Shield className="w-3 h-3" aria-hidden="true" />
      Staff
    </span>
  )
}
