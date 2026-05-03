import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { Link } from 'react-router-dom'

export default function PrivacyPage() {
  return (
    <SectionWrapper padding="md" size="md" background={<BlobsSubtle />}>
      <SectionHeader
        title="Politique de confidentialité"
        subtitle="Dernière mise à jour : mai 2026"
        heading="md"
        align="center"
      />

      <div className="space-y-8 relative z-10">
        {/* Préambule */}
        <section className="space-y-4">
          <p className="text-foreground/70 leading-relaxed">
            La présente politique de confidentialité vous informe sur les données personnelles
            collectées et traitées par la plateforme UNIGE Events, ainsi que sur les droits que
            vous pouvez faire valoir concernant le traitement de vos données personnelles.
          </p>
          <p className="text-foreground/70 leading-relaxed">
            UNIGE Events est un projet académique développé dans le cadre du cours PINFO
            (Projet d'informatique) de l'Université de Genève. Il ne constitue pas un service
            officiel de l'UNIGE.
          </p>
        </section>

        {/* Cadre légal */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Cadre légal</h2>
          <p className="text-foreground/70 leading-relaxed">
            L'Université de Genève est un établissement de droit public doté de la personnalité
            juridique, soumis au droit public genevois. Le traitement des données personnelles
            est régi par :
          </p>
          <ul className="list-disc list-inside space-y-2 text-foreground/70 leading-relaxed">
            <li>
              La <strong className="text-foreground">Loi sur l'information du public, l'accès aux documents
              et la protection des données personnelles (LIPAD)</strong> du 5 octobre 2001
            </li>
            <li>
              Le <strong className="text-foreground">Règlement d'application de la LIPAD (RIPAD)</strong> du
              21 décembre 2011
            </li>
            <li>
              Le droit fondamental à la protection de la sphère privée consacré par l'article 21,
              alinéa 2, de la Constitution de la République et Canton de Genève
            </li>
            <li>
              Le <strong className="text-foreground">Règlement général sur la protection des données
              (RGPD)</strong> de l'Union européenne, lorsqu'il est applicable (utilisateurs situés
              dans l'UE)
            </li>
          </ul>
        </section>

        {/* Responsable du traitement */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Responsable du traitement</h2>
          <div className="text-foreground/70 leading-relaxed">
            <p>Université de Genève</p>
            <p>24, rue du Général-Dufour</p>
            <p>CH-1211 Genève 4</p>
            <p>Tél. : +41 (0)22 379 71 11</p>
          </div>
        </section>

        {/* Données collectées */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Données collectées</h2>
          <p className="text-foreground/70 leading-relaxed">
            Nous collectons les catégories de données suivantes :
          </p>
          <div className="space-y-3">
            <div>
              <h3 className="font-medium text-foreground">Données d'authentification (via Auth0)</h3>
              <p className="text-foreground/70 leading-relaxed">
                Identifiant unique, adresse e-mail, nom, prénom et photo de profil transmis
                par le fournisseur d'identité lors de la connexion.
              </p>
            </div>
            <div>
              <h3 className="font-medium text-foreground">Données de profil (saisie volontaire)</h3>
              <p className="text-foreground/70 leading-relaxed">
                Biographie, faculté, niveau d'études, centres d'intérêt, avatar personnalisé
                et bannière de profil.
              </p>
            </div>
            <div>
              <h3 className="font-medium text-foreground">Données d'utilisation</h3>
              <p className="text-foreground/70 leading-relaxed">
                Événements créés, participations, favoris, vues d'événements, co-organisations
                et signalements.
              </p>
            </div>
          </div>
        </section>

        {/* Finalités */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Finalités du traitement</h2>
          <p className="text-foreground/70 leading-relaxed">
            Vos données personnelles sont traitées pour les finalités suivantes :
          </p>
          <ul className="list-disc list-inside space-y-2 text-foreground/70 leading-relaxed">
            <li>Gestion de votre compte utilisateur et authentification</li>
            <li>Personnalisation de votre expérience (recommandations, calendrier)</li>
            <li>Fonctionnement de la plateforme : création et gestion d'événements,
                inscriptions, favoris, co-organisation</li>
            <li>Modération du contenu et traitement des signalements</li>
            <li>Production de statistiques anonymes d'utilisation</li>
          </ul>
        </section>

        {/* Base légale */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Base légale</h2>
          <p className="text-foreground/70 leading-relaxed">
            Conformément à l'article 35, alinéa 1 de la LIPAD, le traitement de données
            personnelles n'est licite que s'il est nécessaire à l'accomplissement des tâches
            légales de l'institution publique concernée. Le traitement de vos données est
            nécessaire au fonctionnement de la plateforme universitaire et s'inscrit dans
            le cadre des missions d'enseignement de l'UNIGE.
          </p>
        </section>

        {/* Principes */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Principes respectés</h2>
          <p className="text-foreground/70 leading-relaxed">
            Le traitement de vos données respecte les principes suivants, conformément à la LIPAD :
          </p>
          <ul className="list-disc list-inside space-y-2 text-foreground/70 leading-relaxed">
            <li>
              <strong className="text-foreground">Bonne foi</strong> (art. 38) — la collecte de données
              est reconnaissable pour la personne concernée
            </li>
            <li>
              <strong className="text-foreground">Proportionnalité</strong> (art. 36) — seules les données
              nécessaires et pertinentes sont traitées
            </li>
            <li>
              <strong className="text-foreground">Finalité</strong> (art. 35, al. 1) — les données ne sont
              utilisées que pour les finalités indiquées ci-dessus
            </li>
            <li>
              <strong className="text-foreground">Exactitude</strong> (art. 36) — nous veillons à ce que les
              informations soient exactes et à jour
            </li>
            <li>
              <strong className="text-foreground">Sécurité des données</strong> (art. 37) — les données sont
              protégées contre tout traitement illicite
            </li>
            <li>
              <strong className="text-foreground">Destruction</strong> (art. 40) — les données qui ne sont
              plus nécessaires sont détruites ou anonymisées
            </li>
          </ul>
        </section>

        {/* Partage */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Partage des données</h2>
          <p className="text-foreground/70 leading-relaxed">
            Vos données personnelles ne sont pas partagées avec des tiers à des fins commerciales.
            Les seuls sous-traitants impliqués sont :
          </p>
          <ul className="list-disc list-inside space-y-2 text-foreground/70 leading-relaxed">
            <li>
              <strong className="text-foreground">Auth0</strong> — fournisseur d'authentification.
              Traite uniquement les données nécessaires à la connexion (identifiant, e-mail, nom)
            </li>
            <li>
              <strong className="text-foreground">MinIO</strong> — stockage d'images (avatars, bannières).
              Hébergé sur l'infrastructure Kubernetes de l'UNIGE, aucun transfert hors de Suisse
            </li>
          </ul>
        </section>

        {/* Cookies */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Cookies</h2>
          <p className="text-foreground/70 leading-relaxed">
            La plateforme utilise uniquement les cookies de session nécessaires au fonctionnement
            de l'authentification Auth0. Aucun cookie de tracking, d'analyse d'audience ou
            publicitaire n'est utilisé. Aucun outil de type Google Analytics n'est déployé.
          </p>
        </section>

        {/* Durée de conservation */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Durée de conservation</h2>
          <p className="text-foreground/70 leading-relaxed">
            Vos données personnelles sont conservées aussi longtemps que votre compte est actif
            sur la plateforme. Les données associées aux événements (participations, favoris,
            signalements) sont conservées tant que l'événement existe. Vous pouvez demander
            la suppression de votre compte et de vos données à tout moment.
          </p>
        </section>

        {/* Droits */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Vos droits</h2>
          <p className="text-foreground/70 leading-relaxed">
            Conformément aux articles 44 et suivants de la LIPAD, vous disposez des droits suivants :
          </p>
          <ul className="list-disc list-inside space-y-2 text-foreground/70 leading-relaxed">
            <li>
              <strong className="text-foreground">Droit d'accès</strong> — vous pouvez demander si des données
              personnelles vous concernant sont traitées
            </li>
            <li>
              <strong className="text-foreground">Droit de rectification</strong> — vous pouvez demander la
              correction de données inexactes. Vous pouvez modifier directement vos informations de
              profil depuis la page{' '}
              <Link to="/profile/me/edit" className="text-accent hover:underline">
                Mon profil
              </Link>
            </li>
            <li>
              <strong className="text-foreground">Droit de suppression</strong> — vous pouvez demander la
              destruction de vos données personnelles
            </li>
            <li>
              <strong className="text-foreground">Droit de contestation</strong> — vous pouvez exiger la
              cessation d'un traitement illicite
            </li>
          </ul>
          <p className="text-foreground/70 leading-relaxed">
            Pour exercer vos droits, vous pouvez nous contacter par écrit à l'adresse{' '}
            <a href="mailto:pdt@unige.ch" className="text-accent hover:underline">
              pdt@unige.ch
            </a>{' '}
            en joignant une pièce d'identité.
          </p>
        </section>

        {/* Sécurité */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Sécurité</h2>
          <p className="text-foreground/70 leading-relaxed">
            Nous mettons en œuvre des mesures techniques et organisationnelles appropriées pour
            protéger vos données personnelles :
          </p>
          <ul className="list-disc list-inside space-y-2 text-foreground/70 leading-relaxed">
            <li>Communications chiffrées via HTTPS/TLS</li>
            <li>Authentification par tokens JWT signés</li>
            <li>Mots de passe gérés exclusivement par Auth0 — jamais stockés sur la plateforme</li>
            <li>Infrastructure déployée sur Kubernetes avec accès restreint</li>
          </ul>
        </section>

        {/* Contact */}
        <section className="space-y-4">
          <h2 className="text-xl font-semibold text-foreground">Contact</h2>
          <p className="text-foreground/70 leading-relaxed">
            Pour toute question relative à la protection de vos données personnelles, vous pouvez
            contacter le Préposé à la protection des données et à la transparence :
          </p>
          <div className="text-foreground/70 leading-relaxed">
            <p>
              E-mail :{' '}
              <a href="mailto:pdt@unige.ch" className="text-accent hover:underline">
                pdt@unige.ch
              </a>
            </p>
          </div>
        </section>

        {/* Retour */}
        <div className="pt-4 text-center">
          <Link to="/" className="text-sm text-foreground/40 hover:text-foreground/60 transition-colors">
            Retour à l'accueil
          </Link>
        </div>
      </div>
    </SectionWrapper>
  )
}
