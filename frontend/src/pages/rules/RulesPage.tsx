import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { Link } from 'react-router-dom'
import {
  LegalSection,
  LegalParagraph,
  LegalList,
  LegalSubheading,
  LegalBackLink,
} from '@/components/legal/LegalSection'

export default function RulesPage() {
  return (
    <SectionWrapper padding="md" size="md" background={<BlobsSubtle />}>
      <SectionHeader
        title={<>Règles de la <mark>communauté</mark></>}
        subtitle="Dernière mise à jour : mai 2026"
        heading="md"
        align="center"
      />

      <div className="space-y-8 relative z-10">
        <section className="space-y-4">
          <LegalParagraph>
            UNIGE Events est un espace partagé par toute la communauté universitaire genevoise.
            Ces règles définissent les comportements attendus et le contenu autorisé sur la
            plateforme. En l'utilisant, vous acceptez de les respecter.
          </LegalParagraph>
          <LegalParagraph>
            Elles complètent les{' '}
            <Link to="/legal/terms" className="text-accent hover:underline">
              Conditions générales d'utilisation
            </Link>{' '}
            et s'inspirent de la Charte d'éthique et de déontologie de l'Université de Genève
            ainsi que du Code de conduite du projet.
          </LegalParagraph>
        </section>

        <LegalSection title="Notre engagement">
          <LegalParagraph>
            Nous voulons offrir à chacune et chacun une expérience accueillante et exempte de
            harcèlement, sans distinction d'âge, de handicap visible ou invisible, d'origine
            ethnique, d'identité ou d'expression de genre, de niveau d'expérience, de formation,
            de nationalité, d'apparence, de religion ou d'orientation sexuelle.
          </LegalParagraph>
          <LegalParagraph>
            Chaque membre contribue à un environnement ouvert, bienveillant, diversifié et sain.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Comportements encouragés">
          <LegalList>
            <li>Faire preuve d'empathie et de bienveillance envers les autres</li>
            <li>Respecter les opinions, points de vue et expériences différents</li>
            <li>Donner et accepter avec grâce les retours constructifs</li>
            <li>Assumer ses erreurs, présenter ses excuses et en tirer des leçons</li>
            <li>Privilégier ce qui est bénéfique pour l'ensemble de la communauté</li>
          </LegalList>
        </LegalSection>

        <LegalSection title="Comportements interdits">
          <LegalParagraph>
            Les comportements suivants ne sont pas tolérés sur la plateforme :
          </LegalParagraph>
          <LegalList>
            <li>L'utilisation de langage ou d'imagerie à caractère sexuel et toute attention
                sexuelle non sollicitée</li>
            <li>Le trolling, les commentaires insultants ou désobligeants, et les attaques
                personnelles ou politiques</li>
            <li>Le harcèlement public ou privé</li>
            <li>La publication d'informations privées de tiers (adresse physique ou
                électronique) sans leur autorisation explicite</li>
            <li>Tout comportement raisonnablement considéré comme inapproprié dans un cadre
                professionnel ou académique</li>
          </LegalList>
        </LegalSection>

        <LegalSection title="Contenu des événements">
          <LegalParagraph>
            En tant qu'organisateur, vous êtes seul responsable du contenu que vous publiez
            (titre, description, bannière, lieu, dates). Tout contenu publié doit respecter la
            Charte d'éthique de l'UNIGE. Il est notamment interdit de publier du contenu :
          </LegalParagraph>
          <LegalList>
            <li>Illicite, frauduleux ou trompeur</li>
            <li>Discriminatoire, injurieux, diffamatoire ou haineux</li>
            <li>Portant atteinte à la vie privée d'autrui</li>
            <li>Constituant une contrefaçon de droits de propriété intellectuelle</li>
            <li>Contenant des données personnelles de tiers sans leur consentement</li>
            <li>Sans rapport avec la vie universitaire ou la communauté UNIGE</li>
          </LegalList>
        </LegalSection>

        <LegalSection title="Signaler un contenu">
          <LegalParagraph>
            Si vous rencontrez un événement ou un comportement qui enfreint ces règles, vous
            pouvez le signaler directement depuis la page de l'événement concerné via le bouton
            de signalement. Chaque signalement est examiné par l'équipe de modération.
          </LegalParagraph>
          <LegalParagraph>
            Tous les signalements sont traités avec sérieux et la confidentialité de la personne
            qui signale est respectée.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Modération et sanctions">
          <div className="space-y-3">
            <div>
              <LegalSubheading>Modération automatique</LegalSubheading>
              <LegalParagraph>
                Les événements ayant reçu un nombre excessif de signalements peuvent être
                automatiquement masqués par le système de modération en attendant une revue
                manuelle.
              </LegalParagraph>
            </div>
            <div>
              <LegalSubheading>Revue manuelle</LegalSubheading>
              <LegalParagraph>
                L'équipe de modération examine le contenu signalé et peut décider de le
                maintenir, le modifier ou le supprimer, sans préavis ni indemnisation.
              </LegalParagraph>
            </div>
            <div>
              <LegalSubheading>Mesures graduées</LegalSubheading>
              <LegalParagraph>
                Selon la gravité et la récurrence des manquements, les mesures vont de
                l'avertissement à la suppression du contenu, voire à la suspension du compte
                concerné.
              </LegalParagraph>
            </div>
          </div>
        </LegalSection>

        <LegalSection title="Application">
          <LegalParagraph>
            Les administrateurs de la plateforme sont chargés de clarifier et de faire respecter
            ces règles. Ils ont le droit et la responsabilité de retirer ou de modifier tout
            contenu qui n'y est pas conforme, et communiquent les raisons de leurs décisions de
            modération lorsque cela est pertinent.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Contact">
          <LegalParagraph>
            Pour toute question relative à ces règles ou pour signaler un manquement qui ne peut
            l'être depuis la plateforme, vous pouvez nous écrire à{' '}
            <a href="mailto:contact@events.unige.ch" className="text-accent hover:underline">
              contact@events.unige.ch
            </a>. Besoin d'aide pour utiliser la plateforme ? Consultez notre{' '}
            <Link to="/support" className="text-accent hover:underline">
              Centre d'aide
            </Link>.
          </LegalParagraph>
        </LegalSection>

        <LegalBackLink />
      </div>
    </SectionWrapper>
  )
}
