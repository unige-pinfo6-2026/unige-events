import Header from './Header'
import Footer from './Footer'
import { Outlet } from 'react-router-dom'

export default function Layout() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <Header/>

      <main>
        <Outlet/>
      </main>

      <Footer/>
    </div>
  )
}