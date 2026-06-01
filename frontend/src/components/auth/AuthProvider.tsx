import { Auth0Provider } from '@auth0/auth0-react'
import type { AppState } from '@auth0/auth0-react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'

export default function AuthProvider({ children }: Readonly<{ children: ReactNode }>) {
  const navigate = useNavigate()

  function onRedirectCallback(appState?: AppState) {
    const returnTo = appState?.returnTo
    const isRelative = typeof returnTo === 'string' && returnTo.startsWith('/') && !returnTo.startsWith('//')
    navigate(isRelative ? returnTo : '/', { replace: true })
  }

  return (
    <Auth0Provider
      domain={import.meta.env.VITE_AUTH0_DOMAIN}
      clientId={import.meta.env.VITE_AUTH0_CLIENT_ID}
      cacheLocation="localstorage"
      useRefreshTokens={true}
      authorizationParams={{
        redirect_uri: `${globalThis.location.origin}/login/callback`,
        audience: import.meta.env.VITE_AUTH0_AUDIENCE || undefined,
        scope: 'openid profile email offline_access',
      }}
      onRedirectCallback={onRedirectCallback}
    >
      {children}
    </Auth0Provider>
  )
}