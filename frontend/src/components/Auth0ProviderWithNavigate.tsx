import { Auth0Provider } from '@auth0/auth0-react'
import type { AppState } from '@auth0/auth0-react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'

export default function Auth0ProviderWithNavigate({ children }: Readonly<{ children: ReactNode }>) {
  const navigate = useNavigate()

  function onRedirectCallback(appState?: AppState) {
    navigate(appState?.returnTo ?? '/home', { replace: true })
  }

  return (
    <Auth0Provider
      domain={import.meta.env.VITE_AUTH0_DOMAIN}
      clientId={import.meta.env.VITE_AUTH0_CLIENT_ID}
      cacheLocation="localstorage"
      authorizationParams={{
        redirect_uri: `${globalThis.location.origin}/callback`,
        audience: import.meta.env.VITE_AUTH0_AUDIENCE || undefined,
        scope: 'openid profile email',
      }}
      onRedirectCallback={onRedirectCallback}
    >
      {children}
    </Auth0Provider>
  )
}
