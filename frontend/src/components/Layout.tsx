import Header from './Header'
import Footer from './Footer'
import ToastsWrapper from './utils/Toast'
import { Outlet } from 'react-router-dom'
import InfoBanner from './utils/InfoBanner'

export default function Layout() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <div className="sticky top-0 z-50">
        {import.meta.env.MODE === "preview" && <InfoBanner type='warning' message="Cette version est une prévisualisation. Les données et fonctionnalités présentées sont susceptibles d'évoluer ou d'être modifiées."/>}

        <Header />
      </div>


      <main className="flex-1">
        <Outlet/>
      </main>

      <Footer/>

      <ToastsWrapper />
    </div>
  )
}
