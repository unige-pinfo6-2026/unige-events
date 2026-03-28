import type { ReactNode } from 'react'
import { useTheme } from '../contexts/ThemeContext'
import Navbar from './Navbar'
import './Layout.css'

interface LayoutProps {
  children: ReactNode
}

function Layout({ children }: Readonly<LayoutProps>) {
  const { theme } = useTheme()
  return (
    <div className="layout" data-theme={theme}>
      <Navbar />
      <main className="layout-main">{children}</main>
    </div>
  )
}

export default Layout
