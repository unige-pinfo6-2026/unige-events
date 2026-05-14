import { useEffect, useState } from 'react'
import { getPublicProfile } from '@/services/userService'
import type { UserPublicResponse } from '@/types/user'

export interface UseUserProfileResult {
  profile: UserPublicResponse | null
  /** `true` when the backend returned 404 (private profile OR truly missing). */
  isNotFound: boolean
  loading: boolean
  error: string | null
}

/**
 * Fetches the public projection of `/users/{id}` for `/profile/:id`
 * (SCRUM-141). `id === undefined` keeps the hook in loading state without
 * firing a request — used while the parent component is validating the URL
 * param.
 *
 * The backend ISSUE-93 anti-oracle returns 404 for both "user not found"
 * and "profile is private and caller is neither owner nor admin"; the two
 * cases are indistinguishable by design. The hook surfaces them uniformly
 * via `isNotFound`, and the consumer renders a "Ce profil est privé ou
 * introuvable" UI without leaking the distinction.
 */
export function useUserProfile(id: string | undefined): UseUserProfileResult {
  const [profile, setProfile] = useState<UserPublicResponse | null>(null)
  const [isNotFound, setIsNotFound] = useState(false)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) {
      setProfile(null)
      setIsNotFound(false)
      setLoading(true)
      setError(null)
      return
    }

    let cancelled = false
    setLoading(true)
    setError(null)
    setIsNotFound(false)
    setProfile(null)

    getPublicProfile(id)
      .then(data => {
        if (cancelled) return
        if (data === null) {
          setIsNotFound(true)
        } else {
          setProfile(data)
        }
        setLoading(false)
      })
      .catch(() => {
        if (cancelled) return
        setError('Impossible de charger le profil.')
        setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [id])

  return { profile, isNotFound, loading, error }
}
