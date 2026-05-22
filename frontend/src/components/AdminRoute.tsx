import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { LoadingSpinner } from './utils/LoadingSpinner'
import ForbiddenPage from '@/pages/ForbiddenPage'

export default function AdminRoute() {
  const { isAuthenticated, isAdmin, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) return <LoadingSpinner />

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ returnTo: location.pathname + location.search + location.hash }} />
  }

  if (!isAdmin) {
    return <ForbiddenPage />
  }

  return <Outlet />
}
