import { useEffect } from "react"
import { useAuth } from "@/hooks/useAuth"

const LoginPage = () => {
  const { login } = useAuth()

  useEffect(() => {
    login()
  }, [])

  return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="h-10 w-10 animate-spin rounded-full border-4 border-gray-300 border-t-blue-500" />
    </div>
  )
}

export default LoginPage