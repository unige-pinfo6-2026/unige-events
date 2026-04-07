import { InfoMessage } from '@/components/utils/InfoMessage'
import { LoadingSpinner } from '@/components/utils/LoadingSpinner'
import { useAuth0 } from '@auth0/auth0-react'
import { Navigate } from 'react-router-dom'

// Auth0Provider processes the code from the URL on mount here.
// onRedirectCallback in AuthProvider.tsx then navigates to / automatically.
export default function CallbackPage() {
  const { isAuthenticated, isLoading, error } = useAuth0()

  if(isAuthenticated) return <Navigate to="/" />

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