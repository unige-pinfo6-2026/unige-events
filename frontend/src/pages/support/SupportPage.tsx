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

export default function SupportPage() {
  return (
    <SectionWrapper padding="md" size="md" background={<BlobsSubtle />}>
      <SectionHeader
        title={<>Centre <mark>d'aide</mark></>}
        subtitle="Tout ce qu'il faut savoir pour profiter de UNIGE Events"
        heading="md"
        align="center"
      />

      <div className="space-y-8 relative z-10">
        <section className="space-y-4">
          <LegalParagraph>
            Bienvenue dans le centre d'aide de UNIGE Events. Vous trouverez ici les réponses aux
            questions les plus fréquentes ainsi que les étapes pour tirer le meilleur parti de la
            plateforme. Si vous ne trouvez pas votre réponse, contactez-nous directement.
          </LegalParagraph>
        </section>

        <LegalSection title="Premiers pas">
          <LegalParagraph>
            L'accès aux fonctionnalités de la plateforme nécessite une connexion via Auth0. Lors
            de votre première connexion, un compte est automatiquement créé à partir des
            informations transmises par le fournisseur d'identité — aucun mot de passe n'est
            stocké par UNIGE Events.
          </LegalParagraph>
          <LegalList>
            <li>Cliquez sur « Connexion » depuis la barre de navigation</li>
            <li>Authentifiez-vous avec votre compte universitaire</li>
            <li>Complétez votre profil depuis la page Mon profil</li>
          </LegalList>
        </LegalSection>

        <LegalSection title="Découvrir des événements">
          <LegalParagraph>
            Plusieurs façons d'explorer ce qui se passe sur le campus :
          </LegalParagraph>
          <div className="space-y-3">
            <div>
              <LegalSubheading>Recherche et filtres</LegalSubheading>
              <LegalParagraph>
                Affinez vos résultats par faculté, catégorie, date ou type d'événement depuis la
                page de recherche pour trouver exactement ce qui vous intéresse.
              </LegalParagraph>
            </div>
            <div>
              <LegalSubheading>Calendrier</LegalSubheading>
              <LegalParagraph>
                Visualisez l'ensemble des événements à venir dans une vue calendrier centralisée
                depuis la page Calendrier.
              </LegalParagraph>
            </div>
            <div>
              <LegalSubheading>Fil d'actualité</LegalSubheading>
              <LegalParagraph>
                Parcourez les événements sous forme de fil chronologique depuis la page Feed.
              </LegalParagraph>
            </div>
          </div>
        </LegalSection>

        <LegalSection title="Créer et gérer un événement">
          <LegalParagraph>
            Tout membre de la communauté UNIGE peut créer et publier un événement en quelques
            minutes depuis le bouton « Créer un événement ». Vous pourrez ensuite le modifier ou
            l'annuler à tout moment depuis vos publications.
          </LegalParagraph>
          <LegalParagraph>
            Chaque organisateur dispose d'un tableau de bord présentant le nombre de vues, de
            participations et l'engagement global de ses événements.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Participer et suivre">
          <LegalList>
            <li>Indiquez votre participation à un événement depuis sa page de détail</li>
            <li>Ajoutez des événements à vos favoris pour les retrouver facilement</li>
            <li>Suivez vos associations et organisateurs préférés pour ne rien manquer</li>
            <li>Retrouvez vos favoris et participations dans la section Mes événements</li>
          </LegalList>
        </LegalSection>

        <LegalSection title="Co-organisation">
          <LegalParagraph>
            Un organisateur peut inviter d'autres membres à co-organiser un événement. Les
            invitations reçues s'acceptent ou se refusent depuis votre profil. Une fois acceptée,
            la co-organisation vous donne accès à la gestion de l'événement concerné.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Votre profil et vos données">
          <LegalParagraph>
            Vous pouvez modifier à tout moment vos informations de profil (biographie, faculté,
            niveau d'études, centres d'intérêt, avatar et bannière) depuis la page{' '}
            <Link to="/profile/me/edit" className="text-accent hover:underline">
              Modifier mon profil
            </Link>.
          </LegalParagraph>
          <LegalParagraph>
            Le détail du traitement de vos données et des droits dont vous disposez figure dans
            notre{' '}
            <Link to="/legal/privacy" className="text-accent hover:underline">
              Politique de confidentialité
            </Link>.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Signaler un problème">
          <LegalParagraph>
            Si vous rencontrez un contenu inapproprié, vous pouvez le signaler depuis la page de
            l'événement concerné. Les règles de bonne conduite et le fonctionnement de la
            modération sont décrits dans les{' '}
            <Link to="/rules" className="text-accent hover:underline">
              Règles de la communauté
            </Link>.
          </LegalParagraph>
        </LegalSection>

        <LegalSection title="Questions fréquentes">
          <div className="space-y-3">
            <div>
              <LegalSubheading>La plateforme est-elle gratuite ?</LegalSubheading>
              <LegalParagraph>
                Oui. UNIGE Events est un projet académique développé dans le cadre du cours PINFO
                de l'Université de Genève et son accès est entièrement gratuit pour la communauté
                universitaire.
              </LegalParagraph>
            </div>
            <div>
              <LegalSubheading>Qui peut créer un événement ?</LegalSubheading>
              <LegalParagraph>
                Tout membre de la communauté UNIGE (étudiant, association ou administration)
                connecté à la plateforme peut créer et publier un événement.
              </LegalParagraph>
            </div>
            <div>
              <LegalSubheading>Mes données sont-elles en sécurité ?</LegalSubheading>
              <LegalParagraph>
                Les communications sont chiffrées via HTTPS, l'authentification repose sur des
                tokens signés et vos mots de passe sont gérés exclusivement par Auth0, jamais
                stockés par la plateforme.
              </LegalParagraph>
            </div>
            <div>
              <LegalSubheading>La disponibilité est-elle garantie ?</LegalSubheading>
              <LegalParagraph>
                S'agissant d'un projet académique, aucune garantie de disponibilité permanente
                n'est assurée. La plateforme est fournie « en l'état ».
              </LegalParagraph>
            </div>
          </div>
        </LegalSection>

        <LegalSection title="Besoin d'aide supplémentaire ?">
          <LegalParagraph>
            Vous n'avez pas trouvé de réponse à votre question ? Notre équipe est à votre
            disposition à l'adresse{' '}
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
