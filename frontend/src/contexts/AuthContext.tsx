import { createContext, useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import type { User } from '../types'
import { getMe } from '../services/userService'
import { setToken } from '../services/tokenStore'

export interface AuthContextValue {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  login: () => void
  logout: () => void
  updateUser: (updated: User) => void
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: Readonly<{ children: ReactNode }>) {
  const {
    isAuthenticated,
    isLoading: auth0IsLoading,
    user: auth0User,
    loginWithRedirect,
    logout: auth0Logout,
    getAccessTokenSilently,
  } = useAuth0()

  const [user, setUser] = useState<User | null>(null)
  const [backendLoading, setBackendLoading] = useState(false)

  const login = useCallback(() => {
    loginWithRedirect()
  }, [loginWithRedirect])

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
    auth0Logout({ logoutParams: { returnTo: globalThis.location.origin } })
  }, [auth0Logout])

  const updateUser = useCallback((updated: User) => {
    setUser(updated)
  }, [])

  useEffect(() => {
    if (auth0IsLoading) return

    if (!isAuthenticated || !auth0User) {
      setToken(null)
      setUser(null)
      return
    }

    setBackendLoading(true)
    getAccessTokenSilently()
      .then((token) => {
        setToken(token)
        return getMe()
      })
      .then((backendUser) => setUser(backendUser))
      .catch((err: unknown) => {
        const status = (err as { response?: { status?: number } })?.response?.status
        if (status === 401) {
          // Token rejected by backend — force re-login
          setToken(null)
          auth0Logout({ logoutParams: { returnTo: globalThis.location.origin } })
        }
        // Other errors (network, 5xx): leave user as null, PrivateRoute handles the empty state
      })
      .finally(() => setBackendLoading(false))
  }, [isAuthenticated, auth0IsLoading, auth0User, getAccessTokenSilently, auth0Logout])

  const value = useMemo(
    () => ({ user, isAuthenticated, isLoading: auth0IsLoading || backendLoading, login, logout, updateUser }),
    [user, isAuthenticated, auth0IsLoading, backendLoading, login, logout, updateUser],
  )

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}
