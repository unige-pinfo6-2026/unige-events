import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { Link } from 'react-router-dom'

export default function TermsPage() {
  return (
    <SectionWrapper padding="md" size="md" background={<BlobsSubtle />}>
      <SectionHeader
        title={<>Conditions générales <mark>d'utilisation</mark></>}
        subtitle="Dernière mise à jour : mai 2026"
        heading="md"
        align="center"
      />

      <div className="space-y-8 relative z-10">
        {/* Objet */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Objet</h2>
          <p className="text-foreground/70 leading-relaxed">
            Les présentes conditions générales d'utilisation (ci-après « CGU ») régissent
            l'accès et l'utilisation de la plateforme UNIGE Events. En accédant à la plateforme
            ou en utilisant ses services, vous acceptez les présentes CGU dans leur intégralité.
          </p>
        </section>

        {/* Description */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Description de la plateforme</h2>
          <p className="text-foreground/70 leading-relaxed">
            UNIGE Events est une plateforme centralisée de gestion d'événements destinée à la
            communauté universitaire genevoise. Elle permet aux utilisateurs de découvrir, créer
            et gérer des événements universitaires (conférences, ateliers, activités sportives,
            culturelles, sociales et académiques).
          </p>
          <p className="text-foreground/70 leading-relaxed">
            Cette plateforme est développée dans le cadre du cours PINFO (Projet d'informatique)
            de l'Université de Genève par un groupe d'étudiants. Elle ne constitue pas un service
            officiel de l'UNIGE et est fournie à titre académique.
          </p>
        </section>

        {/* Inscription */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Inscription et accès</h2>
          <p className="text-foreground/70 leading-relaxed">
            L'accès aux fonctionnalités de la plateforme nécessite une authentification via
            Auth0. Lors de votre première connexion, un compte est automatiquement créé à
            partir des informations fournies par le fournisseur d'identité.
          </p>
          <p className="text-foreground/70 leading-relaxed">
            Vous êtes responsable de la confidentialité de vos identifiants de connexion
            et de toute activité effectuée sous votre compte. En cas d'utilisation non
            autorisée de votre compte, vous devez en informer immédiatement les administrateurs
            de la plateforme.
          </p>
        </section>

        {/* Contenu utilisateur */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Contenu utilisateur</h2>
          <p className="text-foreground/70 leading-relaxed">
            En tant qu'utilisateur, vous êtes seul responsable du contenu que vous publiez
            sur la plateforme, notamment les événements que vous créez (titre, description,
            bannière, lieu, dates) et les informations de votre profil.
          </p>
          <p className="text-foreground/70 leading-relaxed">
            Tout contenu publié doit respecter la Charte d'éthique et de déontologie de
            l'Université de Genève. Il est notamment interdit de publier du contenu :
          </p>
          <ul className="list-disc list-inside space-y-2 text-foreground/70 leading-relaxed">
            <li>Illicite, frauduleux ou trompeur</li>
            <li>Discriminatoire, injurieux, diffamatoire ou haineux</li>
            <li>Portant atteinte à la vie privée d'autrui</li>
            <li>Constituant une contrefaçon de droits de propriété intellectuelle</li>
            <li>Contenant des données personnelles de tiers sans leur consentement</li>
          </ul>
        </section>

        {/* Modération */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Modération</h2>
          <p className="text-foreground/70 leading-relaxed">
            La plateforme dispose d'un système de signalement permettant aux utilisateurs
            de signaler tout contenu inapproprié. Les événements ayant reçu un nombre
            excessif de signalements peuvent être automatiquement masqués par le système
            de modération automatique.
          </p>
          <p className="text-foreground/70 leading-relaxed">
            L'équipe de modération se réserve le droit d'examiner tout contenu signalé
            et de prendre les mesures appropriées, y compris le maintien, la modification
            ou la suppression du contenu concerné, sans préavis ni indemnisation.
          </p>
        </section>

        {/* Propriété intellectuelle */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Propriété intellectuelle</h2>
          <p className="text-foreground/70 leading-relaxed">
            Le code source de la plateforme UNIGE Events est un projet open source hébergé
            sur{' '}
            <a
              href="https://github.com/unige-pinfo6-2026/unige-events"
              target="_blank"
              rel="noopener noreferrer"
              className="text-accent hover:underline"
            >
              GitHub
            </a>.
          </p>
          <p className="text-foreground/70 leading-relaxed">
            Vous conservez l'intégralité de vos droits de propriété intellectuelle sur le
            contenu que vous publiez sur la plateforme. En publiant du contenu, vous accordez
            à UNIGE Events une licence non exclusive, gratuite et révocable d'affichage et de
            diffusion de ce contenu dans le cadre du fonctionnement de la plateforme.
          </p>
        </section>

        {/* Disponibilité */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Disponibilité</h2>
          <p className="text-foreground/70 leading-relaxed">
            UNIGE Events étant un projet académique, aucune garantie de disponibilité
            permanente ou de continuité de service n'est assurée. La plateforme peut être
            interrompue, suspendue ou arrêtée à tout moment, notamment pour des raisons
            de maintenance, de mise à jour ou à la fin du semestre académique, sans préavis
            ni indemnisation.
          </p>
        </section>

        {/* Limitation de responsabilité */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Limitation de responsabilité</h2>
          <p className="text-foreground/70 leading-relaxed">
            La plateforme UNIGE Events est réalisée dans un cadre pédagogique et est fournie
            « en l'état », sans aucune garantie expresse ou implicite. L'Université de Genève
            et les développeurs de la plateforme déclinent toute responsabilité pour :
          </p>
          <ul className="list-disc list-inside space-y-2 text-foreground/70 leading-relaxed">
            <li>Les dommages directs ou indirects résultant de l'utilisation ou de
                l'impossibilité d'utiliser la plateforme</li>
            <li>La perte de données ou l'interruption de service</li>
            <li>Le contenu publié par les utilisateurs</li>
            <li>L'exactitude, la fiabilité ou l'exhaustivité des informations présentées</li>
          </ul>
        </section>

        {/* Données personnelles */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Données personnelles</h2>
          <p className="text-foreground/70 leading-relaxed">
            Le traitement de vos données personnelles est décrit dans notre{' '}
            <Link to="/legal/privacy" className="text-accent hover:underline">
              Politique de confidentialité
            </Link>.
          </p>
        </section>

        {/* Modification des CGU */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Modification des conditions</h2>
          <p className="text-foreground/70 leading-relaxed">
            Les présentes conditions générales peuvent être modifiées à tout moment.
            Les modifications prennent effet dès leur publication sur la plateforme.
            L'utilisation continue de la plateforme après la publication de modifications
            vaut acceptation des nouvelles conditions.
          </p>
        </section>

        {/* Droit applicable */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Droit applicable et for juridique</h2>
          <p className="text-foreground/70 leading-relaxed">
            Les présentes conditions sont régies par le droit suisse. Tout litige relatif
            à l'utilisation de la plateforme sera soumis à la compétence exclusive des
            tribunaux de la République et Canton de Genève, Suisse.
          </p>
        </section>

        {/* Contact */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Contact</h2>
          <p className="text-foreground/70 leading-relaxed">
            Pour toute question relative aux présentes conditions, vous pouvez nous contacter à
            l'adresse{' '}
            <a href="mailto:contact@events.unige.ch" className="text-accent hover:underline">
              contact@events.unige.ch
            </a>.
          </p>
        </section>

        {/* Retour */}
        <div className="pt-4 text-center">
          <Link to="/" onClick={() => window.scrollTo({ top: 0 })} className="text-sm text-foreground/40 hover:text-foreground/60 transition-colors">
            Retour à l'accueil
          </Link>
        </div>
      </div>
    </SectionWrapper>
  )
}
