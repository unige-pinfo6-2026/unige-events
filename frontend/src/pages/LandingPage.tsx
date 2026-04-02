import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Calendar, Menu, X, Zap,
  Building2, Users, TrendingUp,
  Filter, User, PlusCircle, BarChart3, Bell,
  AlertCircle, Mail, Search, CheckCircle
} from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import FacultyMarquee from '../components/faculties/FacultyMarquee'
import { ButtonPrimary, ButtonSecondary } from '../components/utils/Buttons'
import Footer from '../components/Footer'
import Header from '../components/Header'
import Navbar from '../components/Navbar'
import { TextLink } from '../components/utils/Links'

// ─── Hero ─────────────────────────────────────────────────────────────────────

function HeroSection() {
  return (
    <section className="relative flex flex-col justify-between h-screen overflow-hidden py-8">
      {/* Background blobs */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-0 left-1/4 w-[500px] h-[500px] bg-gradient-to-br from-accent/30 via-pink-600/20 to-purple-600/20 rounded-full blur-3xl animate-pulse" />
        <div className="absolute bottom-0 right-1/4 w-[600px] h-[600px] bg-gradient-to-tl from-accent/20 via-purple-600/20 to-blue-600/20 rounded-full blur-3xl animate-pulse [animation-delay:700ms]" />
      </div>

      {/* Spacer top */}
      <div />

      {/* Centre */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 px-2">
        <div className="flex flex-col items-center text-center space-y-8 max-w-4xl mx-auto">
          <h1 className="text-5xl lg:text-7xl font-bold tracking-tight leading-[0.95]">
            Tous les{' '}
            <span className="bg-linear-to-r from-accent via-pink-500 to-purple-500 bg-clip-text text-transparent">
              évènements
            </span>
            <br />
            en un lieu
          </h1>

          <p className="text-xl lg:text-2xl text-white/60 leading-relaxed max-w-2xl font-light">
            La plateforme qui connecte étudiants, associations et administration autour des événements du campus.
          </p>

          <div className="flex flex-col sm:flex-row gap-4">
            <ButtonPrimary size="lg">
              Explorer les derniers évènements
            </ButtonPrimary>

            <ButtonSecondary size="lg">
              Créer un évènement
            </ButtonSecondary>
          </div>
        </div>
      </div>

      {/* Bottom */}
      <div className="relative z-10 w-full overflow-hidden">
        <FacultyMarquee />
      </div>
    </section>
  )
}

// ─── Trust Bar ────────────────────────────────────────────────────────────────

function TrustBar() {
  const stats = [
    { icon: Calendar, value: '42', label: 'Events cette semaine', color: 'from-accent to-pink-600' },
    { icon: Users, value: '30+', label: 'Groupes étudiants', color: 'from-blue-500 to-cyan-500' },
    { icon: Building2, value: '8', label: 'Facultés', color: 'from-purple-500 to-pink-500' },
    { icon: TrendingUp, value: '2.5K', label: 'Membres actifs', color: 'from-orange-500 to-red-500' },
  ]

  return (
    <section className="py-12 border-y border-white/5 bg-linear-to-r from-card/50 to-secondary/50 backdrop-blur-xl">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-8">
          {stats.map((stat, i) => {
            const Icon = stat.icon
            return (
              <div key={i} className="flex items-center gap-3">
                <div className={`w-14 h-14 rounded-2xl bg-gradient-to-br ${stat.color} flex items-center justify-center flex-shrink-0 shadow-lg`}>
                  <Icon className="w-7 h-7 text-white" />
                </div>
                <div>
                  <div className="text-3xl font-bold bg-linear-to-r from-white to-white/70 bg-clip-text text-transparent">{stat.value}</div>
                  <div className="text-sm text-white/60">{stat.label}</div>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}

// ─── Problem / Solution ───────────────────────────────────────────────────────

function ProblemSolutionSection() {
  const problems = [
    { icon: Mail, title: 'Events perdus dans les emails', description: 'Les événements importants se noient dans les boîtes mail et listes de diffusion.', gradient: 'from-red-500 to-orange-500' },
    { icon: Search, title: 'Aucun point central', description: 'Les étudiants perdent du temps à chercher sur plusieurs plateformes.', gradient: 'from-orange-500 to-yellow-500' },
    { icon: AlertCircle, title: "Manque d'organisation", description: 'Les activités du campus sont désorganisées et difficiles à découvrir.', gradient: 'from-yellow-500 to-red-500' },
  ]

  const solutions = [
    { icon: Calendar, title: 'Calendrier centralisé', description: 'Tous les événements universitaires dans un calendrier unique et accessible.', gradient: 'from-accent to-pink-600' },
    { icon: Filter, title: 'Filtres & recherche intelligents', description: 'Trouve exactement ce que tu cherches avec des filtres avancés.', gradient: 'from-blue-500 to-cyan-500' },
    { icon: Users, title: 'Communauté active', description: 'Tout le monde peut créer des événements et animer la vie du campus.', gradient: 'from-purple-500 to-pink-500' },
    { icon: Zap, title: "Profils d'events complets", description: "Pages détaillées avec toutes les infos et suivi de l'engagement.", gradient: 'from-green-500 to-emerald-500' },
  ]

  return (
    <section id="how" className="py-20 lg:py-32 relative overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-b from-secondary/30 to-background" />
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="text-center max-w-3xl mx-auto mb-16">
          <h2 className="text-5xl lg:text-7xl font-bold mb-6 tracking-tight">
            <span className="bg-linear-to-r from-white via-white to-white/70 bg-clip-text text-transparent">Du chaos à la simplicité</span>
          </h2>
          <p className="text-xl text-white/60 font-light">On comprend les défis. Voici comment on les résout.</p>
        </div>

        <div className="grid lg:grid-cols-2 gap-12">
          {/* Problems */}
          <div className="space-y-6">
            <div className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-linear-to-r from-red-500/20 to-orange-500/20 border border-red-500/30">
              <AlertCircle className="w-5 h-5 text-red-400" />
              <span className="text-sm font-medium text-red-400">Problèmes Actuels</span>
            </div>
            <div className="space-y-4">
              {problems.map((p, i) => {
                const Icon = p.icon
                return (
                  <div key={i} className="bg-gradient-to-br from-card/60 to-card/30 backdrop-blur-xl rounded-2xl p-6 border border-white/10 flex gap-4">
                    <div className={`w-14 h-14 rounded-xl bg-gradient-to-br ${p.gradient} flex items-center justify-center flex-shrink-0 shadow-lg opacity-60`}>
                      <Icon className="w-7 h-7 text-white" />
                    </div>
                    <div>
                      <h3 className="font-semibold mb-1 text-lg text-white">{p.title}</h3>
                      <p className="text-sm text-white/60">{p.description}</p>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          {/* Solutions */}
          <div className="space-y-6">
            <div className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-linear-to-r from-accent/20 to-pink-600/20 border border-accent/30">
              <CheckCircle className="w-5 h-5 text-accent" />
              <span className="text-sm font-medium bg-linear-to-r from-accent to-pink-400 bg-clip-text text-transparent">Notre Solution</span>
            </div>
            <div className="space-y-4">
              {solutions.map((s, i) => {
                const Icon = s.icon
                return (
                  <div key={i} className="relative group overflow-hidden bg-gradient-to-br from-card/80 to-card/40 backdrop-blur-xl rounded-2xl p-6 border border-white/10 flex gap-4 hover:border-accent/30 transition-colors">
                    <div className={`w-14 h-14 rounded-xl bg-gradient-to-br ${s.gradient} flex items-center justify-center flex-shrink-0 shadow-lg`}>
                      <Icon className="w-7 h-7 text-white" />
                    </div>
                    <div>
                      <h3 className="font-semibold mb-1 text-lg bg-linear-to-r from-white to-white/80 bg-clip-text text-transparent">{s.title}</h3>
                      <p className="text-sm text-white/60">{s.description}</p>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

// ─── Features ─────────────────────────────────────────────────────────────────

function FeaturesSection() {
  const features = [
    { icon: Calendar, title: 'Calendrier Centralisé', description: "Tous les événements universitaires dans une interface élégante. Visualisez tout ce qui se passe sur le campus.", gradient: 'from-accent to-pink-600' },
    { icon: Filter, title: 'Filtres Intelligents', description: "Trouvez exactement ce que vous cherchez avec des filtres avancés par faculté, thème, date et type d'événement.", gradient: 'from-blue-500 to-cyan-500' },
    { icon: User, title: 'Profils Détaillés', description: "Pages complètes pour chaque événement et organisateur. Suivez vos associations préférées.", gradient: 'from-purple-500 to-pink-500' },
    { icon: PlusCircle, title: 'Publication Facile', description: "Créez et publiez des événements en quelques minutes. Formulaires simples et visibilité instantanée.", gradient: 'from-green-500 to-emerald-500' },
    { icon: BarChart3, title: 'Analytics Puissants', description: "Suivez les participations, les vues et l'engagement. Comprenez ce qui résonne avec votre audience.", gradient: 'from-orange-500 to-red-500' },
    { icon: Bell, title: 'Notifications Smart', description: "Restez informé avec des alertes personnalisées. Ne manquez jamais un événement qui vous intéresse.", gradient: 'from-violet-500 to-purple-500' },
  ]

  return (
    <section id="features" className="py-20 lg:py-32 relative overflow-hidden">
      <div className="absolute top-0 left-0 w-96 h-96 bg-gradient-to-br from-accent/10 to-transparent rounded-full blur-3xl" />
      <div className="absolute bottom-0 right-0 w-96 h-96 bg-gradient-to-tl from-purple-600/10 to-transparent rounded-full blur-3xl" />
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="text-center max-w-3xl mx-auto mb-16">
          <h2 className="text-5xl lg:text-7xl font-bold mb-6 tracking-tight">
            <span className="bg-linear-to-r from-white via-white to-white/70 bg-clip-text text-transparent">
              Tout ce dont vous avez besoin
            </span>
          </h2>
          <p className="text-xl text-white/60 font-light">Des fonctionnalités puissantes conçues pour la communauté universitaire</p>
        </div>

        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
          {features.map((f, i) => {
            const Icon = f.icon
            return (
              <div key={i} className="group relative h-full bg-gradient-to-br from-card/80 to-card/40 backdrop-blur-xl rounded-3xl p-8 border border-white/10 overflow-hidden hover:border-white/20 transition-colors">
                <div className={`w-16 h-16 rounded-2xl bg-gradient-to-br ${f.gradient} flex items-center justify-center mb-6 shadow-lg`}>
                  <Icon className="w-8 h-8 text-white" />
                </div>
                <h3 className="text-2xl font-bold mb-3 bg-linear-to-r from-white to-white/80 bg-clip-text text-transparent">{f.title}</h3>
                <p className="text-white/60 leading-relaxed">{f.description}</p>
                <div className="absolute top-0 right-0 w-32 h-32 bg-gradient-to-br from-white/5 to-transparent rounded-bl-full" />
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}

// ─── Final CTA ────────────────────────────────────────────────────────────────

function FinalCTA() {
  return (
    <section className="py-20 lg:py-32 relative overflow-hidden">
      <div className="absolute inset-0">
        <div className="absolute top-0 left-1/4 w-[600px] h-[600px] bg-gradient-to-br from-accent/30 via-pink-600/20 to-purple-600/20 rounded-full blur-3xl" />
        <div className="absolute bottom-0 right-1/4 w-[600px] h-[600px] bg-gradient-to-tl from-purple-600/30 via-blue-600/20 to-cyan-600/20 rounded-full blur-3xl" />
      </div>
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 text-center space-y-10">
        <h2 className="text-5xl lg:text-8xl font-bold tracking-tight leading-[0.95]">
          <span className="bg-linear-to-r from-white via-white to-white/70 bg-clip-text text-transparent">Ne rate plus jamais</span>
          <br />
          <span className="bg-linear-to-r from-accent via-pink-500 to-purple-500 bg-clip-text text-transparent">un événement</span>
        </h2>

        <p className="text-xl lg:text-2xl text-white/60 leading-relaxed max-w-3xl mx-auto font-light">
          Fais partie de la plateforme qui connecte toute la communauté UNIGE. Découvre des événements, connecte-toi avec tes pairs, et{' '}
          <span className="text-accent font-medium">maximise ton expérience universitaire.</span>
        </p>

        <div className="flex flex-col sm:flex-row gap-4 justify-center pt-4">
          <ButtonPrimary size="lg">
            Commencer maintenant
          </ButtonPrimary>
          <ButtonSecondary size="lg">En savoir plus</ButtonSecondary>
        </div>
      </div>
    </section>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

function LandingPage() {
  return (
    <div>
      <HeroSection />
      {/* <TrustBar /> */}
      <ProblemSolutionSection />
      <FeaturesSection />
      <FinalCTA />
    </div>
  )
}

export default LandingPage



// TODO:
// - Revoir Header/Footer
// - Revoir FacTicker
// - Revoir Structure