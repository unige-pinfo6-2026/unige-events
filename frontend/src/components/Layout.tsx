import type { ReactNode } from 'react'
import { useTheme } from '../contexts/ThemeContext'
import Header from './Header'
import Navbar from './Navbar'
import { TextLink } from './utils/Links'
import Footer from './Footer'

interface LayoutProps {
  children: ReactNode
}

function Layout({ children }: Readonly<LayoutProps>) {
  const { theme } = useTheme()
  
  return (
    <div className="min-h-screen bg-background text-foreground" data-theme={theme}>
      <Header/>

      <main>
        {children}
      </main>

      <Footer/>
    </div>
  )
}

export default Layout
