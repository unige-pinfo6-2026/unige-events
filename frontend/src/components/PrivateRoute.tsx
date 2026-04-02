import { Navigate, Outlet } from "react-router-dom"
import { useAuth } from "@/hooks/useAuth"

function PrivateRoute() {
  const { isAuthenticated, isLoading } = useAuth()

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-gray-300 border-t-blue-500" />
      </div>
    )
  }

  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />
}

export default PrivateRoute