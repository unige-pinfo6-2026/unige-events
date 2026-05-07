import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { Link } from 'react-router-dom'
import {
  LegalSection,
  LegalParagraph,
  LegalList,
  LegalBackLink,
} from '@/components/legal/LegalSection'

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
        <LegalSection title="Objet">
          <LegalParagraph>
            Les présentes conditions générales d'utilisation (ci-après « CGU ») régissent
            l'accès et l'utilisation de la plateforme UNIGE Events. En accédant à la plateforme
            ou en utilisant ses services, vous acceptez les présentes CGU dans leur intégralité.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Description de la plateforme">
          <LegalParagraph>
            UNIGE Events est une plateforme centralisée de gestion d'événements destinée à la
            communauté universitaire genevoise. Elle permet aux utilisateurs de découvrir, créer
            et gérer des événements universitaires (conférences, ateliers, activités sportives,
            culturelles, sociales et académiques).
          </LegalParagraph>
          <LegalParagraph>
            Cette plateforme est développée dans le cadre du cours PINFO (Projet d'informatique)
            de l'Université de Genève par un groupe d'étudiants. Elle ne constitue pas un service
            officiel de l'UNIGE et est fournie à titre académique.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Inscription et accès">
          <LegalParagraph>
            L'accès aux fonctionnalités de la plateforme nécessite une authentification via
            Auth0. Lors de votre première connexion, un compte est automatiquement créé à
            partir des informations fournies par le fournisseur d'identité.
          </LegalParagraph>
          <LegalParagraph>
            Vous êtes responsable de la confidentialité de vos identifiants de connexion
            et de toute activité effectuée sous votre compte. En cas d'utilisation non
            autorisée de votre compte, vous devez en informer immédiatement les administrateurs
            de la plateforme.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Contenu utilisateur">
          <LegalParagraph>
            En tant qu'utilisateur, vous êtes seul responsable du contenu que vous publiez
            sur la plateforme, notamment les événements que vous créez (titre, description,
            bannière, lieu, dates) et les informations de votre profil.
          </LegalParagraph>
          <LegalParagraph>
            Tout contenu publié doit respecter la Charte d'éthique et de déontologie de
            l'Université de Genève. Il est notamment interdit de publier du contenu :
          </LegalParagraph>
          <LegalList>
            <li>Illicite, frauduleux ou trompeur</li>
            <li>Discriminatoire, injurieux, diffamatoire ou haineux</li>
            <li>Portant atteinte à la vie privée d'autrui</li>
            <li>Constituant une contrefaçon de droits de propriété intellectuelle</li>
            <li>Contenant des données personnelles de tiers sans leur consentement</li>
          </LegalList>
        </LegalSection>

        <LegalSection title="Modération">
          <LegalParagraph>
            La plateforme dispose d'un système de signalement permettant aux utilisateurs
            de signaler tout contenu inapproprié. Les événements ayant reçu un nombre
            excessif de signalements peuvent être automatiquement masqués par le système
            de modération automatique.
          </LegalParagraph>
          <LegalParagraph>
            L'équipe de modération se réserve le droit d'examiner tout contenu signalé
            et de prendre les mesures appropriées, y compris le maintien, la modification
            ou la suppression du contenu concerné, sans préavis ni indemnisation.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Propriété intellectuelle">
          <LegalParagraph>
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
          </LegalParagraph>
          <LegalParagraph>
            Vous conservez l'intégralité de vos droits de propriété intellectuelle sur le
            contenu que vous publiez sur la plateforme. En publiant du contenu, vous accordez
            à UNIGE Events une licence non exclusive, gratuite et révocable d'affichage et de
            diffusion de ce contenu dans le cadre du fonctionnement de la plateforme.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Disponibilité">
          <LegalParagraph>
            UNIGE Events étant un projet académique, aucune garantie de disponibilité
            permanente ou de continuité de service n'est assurée. La plateforme peut être
            interrompue, suspendue ou arrêtée à tout moment, notamment pour des raisons
            de maintenance, de mise à jour ou à la fin du semestre académique, sans préavis
            ni indemnisation.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Limitation de responsabilité">
          <LegalParagraph>
            La plateforme UNIGE Events est réalisée dans un cadre pédagogique et est fournie
            « en l'état », sans aucune garantie expresse ou implicite. L'Université de Genève
            et les développeurs de la plateforme déclinent toute responsabilité pour :
          </LegalParagraph>
          <LegalList>
            <li>Les dommages directs ou indirects résultant de l'utilisation ou de
                l'impossibilité d'utiliser la plateforme</li>
            <li>La perte de données ou l'interruption de service</li>
            <li>Le contenu publié par les utilisateurs</li>
            <li>L'exactitude, la fiabilité ou l'exhaustivité des informations présentées</li>
          </LegalList>
        </LegalSection>

        <LegalSection title="Données personnelles">
          <LegalParagraph>
            Le traitement de vos données personnelles est décrit dans notre{' '}
            <Link to="/legal/privacy" className="text-accent hover:underline">
              Politique de confidentialité
            </Link>.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Modification des conditions">
          <LegalParagraph>
            Les présentes conditions générales peuvent être modifiées à tout moment.
            Les modifications prennent effet dès leur publication sur la plateforme.
            L'utilisation continue de la plateforme après la publication de modifications
            vaut acceptation des nouvelles conditions.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Droit applicable et for juridique">
          <LegalParagraph>
            Les présentes conditions sont régies par le droit suisse. Tout litige relatif
            à l'utilisation de la plateforme sera soumis à la compétence exclusive des
            tribunaux de la République et Canton de Genève, Suisse.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Contact">
          <LegalParagraph>
            Pour toute question relative aux présentes conditions, vous pouvez nous contacter à
            l'adresse{' '}
            <a href="mailto:contact@events.unige.ch" className="text-accent hover:underline">
              contact@events.unige.ch
            </a>.
          </LegalParagraph>
        </LegalSection>

        <LegalBackLink />
      </div>
    </SectionWrapper>
  )
}
