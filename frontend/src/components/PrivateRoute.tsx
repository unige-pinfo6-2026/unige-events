import { Navigate, Outlet } from "react-router-dom"
import { useAuth } from "@/hooks/useAuth"
import { LoadingSpinner } from "./utils/LoadingSpinner"

// Boneyard CLI sets window.__BONEYARD_BUILD before any page load so <Skeleton fixture> renders.
// Skip auth check in build mode so protected routes are reachable for fixture capture.
const isBoneyardBuild =
  typeof window !== 'undefined' &&
  !!(window as { __BONEYARD_BUILD?: boolean }).__BONEYARD_BUILD

export default function PrivateRoute() {
  const { isAuthenticated, isLoading } = useAuth()

  if (isBoneyardBuild) return <Outlet />
  if (isLoading) return <LoadingSpinner />

  return isAuthenticated ? <Outlet /> : <Navigate to="/login" />
}