import Header from './Header'
import Footer from './Footer'
import Toast from './utils/Toast'
import { useAuth } from '@/hooks/useAuth'
import { Outlet } from 'react-router-dom'

export default function Layout() {
  const { error } = useAuth()

  return (
    <div className="min-h-screen bg-background text-foreground">
      <Header/>

      <main className="flex-1">
        <Outlet/>
      </main>

      <Footer/>

      {error != null && (
        <Toast type="error" message={error} />
      )}
    </div>
  )
}
