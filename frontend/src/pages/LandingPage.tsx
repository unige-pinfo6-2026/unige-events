import { useState, useEffect } from 'react'
import {
  Calendar, Filter, User, PlusCircle, BarChart3, Bell, ChevronDown
} from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import FacultyMarquee from '@/components/faculty/FacultyMarquee'
import { ButtonPrimary, ButtonSecondary, ButtonsWrapper } from '@/components/utils/Buttons'
import { BlobsHero, BlobsSubtle, BlobsCta } from '@/components/utils/Blobs'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import EventCards from '@/components/event/EventCards'

const Hero = () => {
  return (
    <SectionWrapper
      padding="hero"
      background={<BlobsHero />}
      footer={
        <div className="relative z-10 w-full overflow-hidden">
          <FacultyMarquee />
        </div>
      }
    >
      <SectionHeader heading="xl"
        title={<>Tous les <mark>évènements</mark><br/>en un lieu</>}
        subtitle={<>La plateforme qui connecte étudiants, associations et administration autour des événements du campus.</>}
      />
      
      <ButtonsWrapper>
        <ButtonPrimary size="lg"><Link to="/events">Explorer les derniers évènements</Link></ButtonPrimary>
        <ButtonSecondary size="lg"><Link to="/events/new">Créer un évènement</Link></ButtonSecondary>
      </ButtonsWrapper>
    </SectionWrapper>
  )
}

function Events() {
  return (
    <SectionWrapper id="events" background={<BlobsSubtle />}>
      <SectionHeader heading="xl"
        title="Événements à venir"
        subtitle="Découvrez ce qui se passe sur le campus : conférences, soirées, sports et bien plus."
      />

      <EventCards />
    </SectionWrapper>
  )
}

function Features() {
  const features = [
    { icon: Calendar, title: 'Calendrier Centralisé', description: "Tous les événements universitaires dans une interface élégante. Visualisez tout ce qui se passe sur le campus.", gradient: 'from-accent to-pink-600' },
    { icon: Filter, title: 'Filtres Intelligents', description: "Trouvez exactement ce que vous cherchez avec des filtres avancés par faculté, thème, date et type d'événement.", gradient: 'from-blue-500 to-cyan-500' },
    { icon: User, title: 'Profils Détaillés', description: "Pages complètes pour chaque événement et organisateur. Suivez vos associations préférées.", gradient: 'from-purple-500 to-pink-500' },
    { icon: PlusCircle, title: 'Publication Facile', description: "Créez et publiez des événements en quelques minutes. Formulaires simples et visibilité instantanée.", gradient: 'from-green-500 to-emerald-500' },
    { icon: BarChart3, title: 'Analytics Puissants', description: "Suivez les participations, les vues et l'engagement. Comprenez ce qui résonne avec votre audience.", gradient: 'from-orange-500 to-red-500' },
    { icon: Bell, title: 'Notifications Smart', description: "Restez informé avec des alertes personnalisées. Ne manquez jamais un événement qui vous intéresse.", gradient: 'from-violet-500 to-purple-500' },
  ]

  return (
    <SectionWrapper id="features">
      <SectionHeader heading="xl"
        title="Tout ce dont vous avez besoin"
        subtitle="Des fonctionnalités puissantes conçues pour la communauté universitaire"
      />

      <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
        {features.map(feature => (
          <div key={feature.title} className="group relative h-full bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-8 border border-border overflow-hidden hover:border-foreground/50 transition-colors">
            <div className={`w-16 h-16 rounded-2xl bg-linear-to-br ${feature.gradient} flex items-center justify-center mb-6 shadow-lg`}>
              <feature.icon className="w-8 h-8 text-foreground" />
            </div>
            <h3 className="text-2xl font-bold mb-3">{feature.title}</h3>
            <p className="text-foreground/60 leading-relaxed">{feature.description}</p>
            <div className="absolute top-0 right-0 w-32 h-32 bg-linear-to-br from-white/5 to-transparent rounded-bl-full" />
          </div>
        ))}
      </div>
    </SectionWrapper>
  )
}

function Faq() {
  const [open, setOpen] = useState<number | null>(null)

  const faqs = [
    {
      q: "Qui peut créer un événement ?",
      a: "Tout membre de la communauté UNIGE (étudiant, association ou administration) peut créer et publier un événement en quelques minutes depuis son profil.",
    },
    {
      q: "Comment trouver des événements qui m'intéressent ?",
      a: "Utilisez les filtres par faculté, thème, date ou type d'événement pour affiner votre recherche. Vous pouvez aussi suivre des associations et recevoir des alertes personnalisées.",
    },
    {
      q: "La plateforme est-elle gratuite ?",
      a: "L'accès de base est entièrement gratuit pour tous les étudiants UNIGE. Des fonctionnalités premium sont disponibles pour les associations et clubs souhaitant plus de visibilité et d'outils de gestion avancés.",
    },
    {
      q: "Puis-je suivre les participations à mes événements ?",
      a: "Oui, chaque organisateur dispose d'un tableau de bord avec le nombre de vues, de participations et l'engagement global de ses événements pour mieux comprendre son audience.",
    },
    {
      q: "Mes données sont-elles en sécurité ?",
      a: "La plateforme est réservée à la communauté universitaire. Seuls les membres connectés avec un compte UNIGE peuvent accéder aux événements et aux profils.",
    },
  ]

  return (
    <SectionWrapper id="faq" background={<BlobsSubtle />}>
      <SectionHeader heading="xl"
        title="Questions fréquentes"
        subtitle="Tout ce que vous devez savoir avant de commencer."
      />

      <div className="max-w-3xl mx-auto w-full flex flex-col gap-3">
        {faqs.map((faq, i) => (
          <div key={faq.q} className="border border-border rounded-2xl overflow-hidden transition-colors hover:border-foreground/20">
            <button
              type="button"
              onClick={() => setOpen(open === i ? null : i)}
              className="w-full flex items-center justify-between px-6 py-5 text-left gap-4 cursor-pointer bg-transparent border-0"
            >
              <span className="font-semibold text-foreground">{faq.q}</span>
              <ChevronDown className={`w-5 h-5 text-foreground/40 shrink-0 transition-transform duration-200 ${open === i ? 'rotate-180' : ''}`} />
            </button>
            {open === i && (
              <div className="px-6 pb-6 text-foreground/60 leading-relaxed border-t border-border pt-4">
                {faq.a}
              </div>
            )}
          </div>
        ))}
      </div>
    </SectionWrapper>
  )
}

function GetStarted() {
  return (
    <SectionWrapper id="get-started" background={<BlobsCta />}>
      <SectionHeader heading="xl"
        title={<>Ne rate plus jamais <br/><mark>un événement</mark></>}
        subtitle={<>Fais partie de la plateforme qui connecte toute la communauté UNIGE. Découvre des événements, connecte-toi avec tes pairs, et <mark>maximise ton expérience universitaire.</mark></>}
      />

      <ButtonsWrapper>
        <ButtonPrimary size="lg"><Link to="/calendar">Commencer maintenant</Link></ButtonPrimary>
      </ButtonsWrapper>
    </SectionWrapper>
  )
}

export default function LandingPage() {
  const location = useLocation()

  useEffect(() => {
    if (!location.hash) return

    const id = location.hash.slice(1)
    let attempts = 0
    const maxAttempts = 10

    const tryScroll = () => {
      const el = document.getElementById(id)
      if (el) {
        const top = el.getBoundingClientRect().top + globalThis.scrollY 
        globalThis.scrollTo({ top, behavior: 'smooth' })
        return
      }
      attempts++
      if (attempts < maxAttempts) {
        setTimeout(tryScroll, 100)
      }
    }

    setTimeout(tryScroll, 50)
  }, [location.hash])

  return (
    <div>
      <Hero />
      <Events />
      <Features />
      <Faq />
      <GetStarted />
    </div>
  )
}
