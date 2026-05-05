import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { LoadingSpinner } from './utils/LoadingSpinner'

export default function AdminRoute() {
  const { user, isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) return <LoadingSpinner />

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ returnTo: location.pathname + location.search + location.hash }} />
  }

  if (!user?.admin) {
    return <Navigate to="/403" replace />
  }

  return <Outlet />
}
