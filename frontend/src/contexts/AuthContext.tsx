import { createContext, useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import { getMe } from '@/services/userService'
import { setToken } from '@/services/tokenStore'
import type { User } from '@/types/user'
import { useToast } from '@/hooks/useToast'
import { isAuthSessionExpiredError } from '@/utils/authErrors'

export interface AuthContextValue {
  user: User | null
  isAuthenticated: boolean
  isAdmin: boolean
  isLoading: boolean
  error: string | null
  login: (returnTo?: string) => void
  logout: () => void
  updateUser: (updated: User) => void
}

// SCRUM-94: le rôle ADMIN vit uniquement dans la claim Auth0, pas dans le payload profil.
// La clé du namespace doit correspondre à `OIDC_ROLE_NAMESPACE` côté backend
// (cf. application.properties). Default aligné sur la valeur backend par défaut.
const ROLES_CLAIM = import.meta.env.VITE_AUTH0_ROLES_CLAIM ?? 'https://unige-events/roles'

function readRolesFromAuth0User(auth0User: unknown): string[] {
  if (!auth0User || typeof auth0User !== 'object') return []
  const claim = (auth0User as Record<string, unknown>)[ROLES_CLAIM]
  return Array.isArray(claim) ? claim.filter((r): r is string => typeof r === 'string') : []
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: Readonly<{ children: ReactNode }>) {
  const location = useLocation()

  const {
    isAuthenticated,
    isLoading: auth0IsLoading,
    user: auth0User,
    loginWithRedirect,
    logout: auth0Logout,
    getAccessTokenSilently,
  } = useAuth0()

  const { showToast } = useToast()

  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const login = useCallback((returnTo?: string) => {
    const candidate = returnTo ?? location.pathname + location.search + location.hash
    const isRelative = candidate.startsWith('/') && !candidate.startsWith('//')
    const safeReturnTo = isRelative && !candidate.startsWith('/login') ? candidate : '/'
    loginWithRedirect({ appState: { returnTo: safeReturnTo } })
  }, [loginWithRedirect, location])

  const logout = useCallback(() => {
    auth0Logout({ logoutParams: { returnTo: globalThis.location.origin } })
  }, [auth0Logout])

  const updateUser = useCallback((updated: User) => {
    setUser(updated)
  }, [])

  useEffect(() => {
    if(error != null) {
      showToast('error', error)
    }
  }, [error, showToast])

  useEffect(() => {
    if (auth0IsLoading) return

    if (!isAuthenticated || !auth0User) {
      setToken(null)
      setUser(null)
      setError(null)
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)
    getAccessTokenSilently()
      .then((token) => {
        setToken(token)
        return getMe()
      })
      .then((user) => setUser(user))
      .catch((err: unknown) => {
        const status = (err as { response?: { status?: number } })?.response?.status
        if (status === 401) {
          // Token rejected by backend — force re-login
          setToken(null)
          auth0Logout({ logoutParams: { returnTo: globalThis.location.origin } })
          return
        }
        // ISSUE-107 — expired SSO session is NOT an infrastructure failure.
        // The Auth0 SDK throws `login_required` / `invalid_grant` /
        // `consent_required` / `missing_refresh_token` when the session is
        // gone (refresh token expired/revoked, user wiped storage, etc.).
        // Treat it as "fall back to unauthenticated state silently" — clear
        // the local token + call `auth0Logout({ openUrl: false })` so the
        // SDK's own `isAuthenticated` flips to `false` on the next render
        // without redirecting the user away. No toast.
        if (isAuthSessionExpiredError(err)) {
          setToken(null)
          auth0Logout({ openUrl: false })
          return
        }
        setError('Impossible d\'établir la connexion à votre compte. Veuillez réessayer plus tard.')
      })
      .finally(() => setLoading(false))
  }, [isAuthenticated, auth0IsLoading, auth0User, getAccessTokenSilently, auth0Logout, showToast])

  const isAdmin = useMemo(
    () => readRolesFromAuth0User(auth0User).includes('ADMIN'),
    [auth0User],
  )

  const value = useMemo(
    () => ({ user, isAuthenticated, isAdmin, isLoading: auth0IsLoading || loading, error, login, logout, updateUser }),
    [user, isAuthenticated, isAdmin, auth0IsLoading, loading, error, login, logout, updateUser],
  )

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}