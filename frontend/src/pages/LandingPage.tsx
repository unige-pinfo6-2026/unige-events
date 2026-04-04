import { useState } from 'react'
import {
  Calendar, Filter, User, PlusCircle, BarChart3, Bell, ChevronDown
} from 'lucide-react'
import FacultyMarquee from '../components/faculty/FacultyMarquee'
import { ButtonPrimary, ButtonSecondary } from '../components/utils/Buttons'
import { BlobsHero, BlobsSubtle, BlobsCta } from '@/components/utils/Blobs'
import EventCards from '@/components/event/EventCards'
import { Link } from 'react-router-dom'

// TODO : Fix ça en quelque chose de plus minimal
// TODO : Ensuite voir pour dashboard/profiles/events etc...  plus proprement (navbar) 
function SectionWrapper({ children, id, className = 'py-20 lg:py-32', maxWidth = 'max-w-7xl', background, footer, contentClassName }: Readonly<{
  children: React.ReactNode
  id?: string
  className?: string
  maxWidth?: string
  background?: React.ReactNode
  footer?: React.ReactNode
  contentClassName?: string
}>) {
  const sectionClass = ['relative overflow-hidden', className].filter(Boolean).join(' ')
  const innerClass = [maxWidth, 'mx-auto px-4 sm:px-6 lg:px-8 relative z-10', contentClassName].filter(Boolean).join(' ')
  return (
    <section id={id} className={sectionClass}>
      {background}
      <div className={innerClass}>
        {children}
      </div>
      {footer}
    </section>
  )
}

function SectionHeader({ title, subtitle }: Readonly<{ title: React.ReactNode; subtitle?: string }>) {
  return (
    <div className="text-center max-w-3xl mx-auto mb-16">
      <h2 className="text-5xl lg:text-7xl font-bold mb-6 tracking-tight">{title}</h2>
      {subtitle && <p className="text-xl text-foreground/60 font-light">{subtitle}</p>}
    </div>
  )
}

const Hero = () => {
  return (
    <SectionWrapper
      className="flex flex-col h-[calc(100vh-var(--height-navbar))] py-6"
      background={<BlobsHero />}
      contentClassName="my-auto"
      footer={
        <div className="relative z-10 w-full overflow-hidden">
          <FacultyMarquee />
        </div>
      }
    >
      <div className="flex flex-col items-center text-center space-y-8">
        <h1 className="text-5xl lg:text-7xl font-bold tracking-tight leading-[0.95]">
          Tous les{' '}
          <span className="text-accent-gradient">
            évènements
          </span>
          <br />
          en un lieu
        </h1>

        <p className="text-xl lg:text-2xl text-foreground/60 leading-relaxed max-w-2xl font-light">
          La plateforme qui connecte étudiants, associations et administration autour des événements du campus.
        </p>

        <div className="flex flex-col sm:flex-row gap-4">
          <ButtonPrimary size="lg"><a href="/#events">Explorer les derniers évènements</a></ButtonPrimary>
          <ButtonSecondary size="lg"><Link to="/events/new">Créer un évènement</Link></ButtonSecondary>
        </div>
      </div>
    </SectionWrapper>
  )
}

function Events() {
  return (
    <SectionWrapper
      id="events"
      className="py-20 lg:py-32 bg-foreground/2"
      background={<BlobsSubtle />}
    >
      <SectionHeader
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
      <SectionHeader
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
    {
      q: "La plateforme sera-t-elle intégrée aux systèmes officiels de l'UNIGE ?",
      a: "C'est l'un de nos avantages stratégiques. Nous travaillons à une intégration avec l'intranet universitaire et les canaux officiels pour maximiser la visibilité de chaque événement.",
    },
  ]

  return (
    <SectionWrapper id="faq" className="py-20 lg:py-32 bg-foreground/2" background={<BlobsSubtle />}>
      <SectionHeader
        title="Questions fréquentes"
        subtitle="Tout ce que vous devez savoir avant de commencer."
      />

      <div className="max-w-3xl mx-auto flex flex-col gap-3">
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
    <SectionWrapper
      id="get-started"
      maxWidth="max-w-5xl"
      background={<BlobsCta />}
    >
      <div className="text-center space-y-10">
        <h2 className="text-5xl lg:text-8xl font-bold tracking-tight leading-[0.95]">
          Ne rate plus jamais
          <br/>
          <span className="text-accent-gradient">un événement</span>
        </h2>

        <p className="text-xl lg:text-2xl text-foreground/60 leading-relaxed max-w-3xl mx-auto font-light">
          Fais partie de la plateforme qui connecte toute la communauté UNIGE. Découvre des événements, connecte-toi avec tes pairs, et{' '}
          <span className="text-accent font-medium">maximise ton expérience universitaire.</span>
        </p>

        <div className="flex flex-col sm:flex-row gap-4 justify-center pt-4">
          <ButtonPrimary size="lg">Commencer maintenant</ButtonPrimary>
          <ButtonSecondary size="lg">En savoir plus</ButtonSecondary>
        </div>
      </div>
    </SectionWrapper>
  )
}

export default function LandingPage() {
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
