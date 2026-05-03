import { InfoMessage } from '@/components/utils/InfoMessage'
import { LoadingSpinner } from '@/components/utils/LoadingSpinner'
import { useAuth0 } from '@auth0/auth0-react'

// Auth0Provider processes the code from the URL on mount here.
// onRedirectCallback in AuthProvider.tsx then navigates to appState.returnTo.
export default function CallbackPage() {
  const { isLoading, error } = useAuth0()

  return (
    <div className="flex items-center justify-center min-h-hero">
        {error != null && <InfoMessage type='error' message={`Erreur d'authentification ${error}`}/>}

        {error == null && isLoading && (
          <LoadingSpinner>
            Connexion en cours...
          </LoadingSpinner>
        )}
    </div>
  )
}