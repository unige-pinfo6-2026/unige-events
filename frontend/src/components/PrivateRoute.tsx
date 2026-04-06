import { Navigate, Outlet } from "react-router-dom"
import { useAuth } from "@/hooks/useAuth"
import { LoadingSpinner } from "./utils/LoadingSpinner"

export default function PrivateRoute() {
  const { isAuthenticated, isLoading } = useAuth()

  if (isLoading) return <LoadingSpinner />

  return isAuthenticated ? <Outlet /> : <Navigate to="/login" />
}