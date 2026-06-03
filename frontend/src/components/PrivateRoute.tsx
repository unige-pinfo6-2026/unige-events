import { Navigate, Outlet, useLocation } from "react-router-dom"
import { useAuth } from "@/hooks/useAuth"
import LoadingPage from "@/pages/LoadingPage"

export default function PrivateRoute() {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) return <LoadingPage />

  return isAuthenticated ? (
    <Outlet />
  ) : (
    <Navigate to="/login" state={{ returnTo: location.pathname + location.search + location.hash }} />
  )
}
