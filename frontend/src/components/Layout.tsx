import { useTheme } from '../contexts/ThemeContext'
import Header from './Header'
import Footer from './Footer'
import { Outlet } from 'react-router-dom'

function Layout() {
  const { theme } = useTheme()
  
  return (
    <div className="min-h-screen bg-background text-foreground" data-theme={theme}>
      <Header/>

      <main>
        <Outlet/>
      </main>

      <Footer/>
    </div>
  )
}

export default Layout
