# Specs SCRUM-166 — Pages légales `/legal/privacy` et `/legal/terms`

> **Branche :** `feature/SCRUM-166-legal-pages`
> **Base :** `origin/main`
> **Sprint :** S7 (28 avr.–8 mai 2026)
> **Ticket Jira :** [SCRUM-166](https://pinfo-groupe6.atlassian.net/browse/SCRUM-166) (2 SP)
> **Épic :** SCRUM-13
> **Dépendances :** Aucune
> **Règle d'or openapi-first :** **NON APPLICABLE — aucun endpoint backend, purement frontend statique.**

---

## Contexte

### Le problème

Le [`Footer.tsx`](frontend/src/components/Footer.tsx#L88-L91) affiche deux liens en bas de chaque page :

```tsx
<TextLink href="/privacy">Politique de confidentialité</TextLink>
<TextLink href="/terms">Conditions générales</TextLink>
```

Ces liens pointent vers `/privacy` et `/terms`, mais **aucune route ni page n'existe** pour ces chemins. Cliquer dessus tombe sur le catch-all `NotFoundPage` (404). De plus, `TextLink` utilise une balise `<a>` native — ce qui provoque un rechargement complet de la SPA au lieu d'une navigation côté client.

### La solution

Créer deux pages statiques de contenu légal, accessibles publiquement, visuellement cohérentes avec le reste de l'application, et corriger les liens du Footer pour utiliser `<Link>` de `react-router-dom` (navigation SPA).

### Pourquoi maintenant

- Sprint 7 (28 avr.–8 mai 2026) — sprint courant, capacité disponible.
- Tâche isolée (2 SP), aucune dépendance technique.
- Le Footer est visible sur **toutes les pages** de l'application — des liens 404 dégradent l'expérience utilisateur et la crédibilité de la plateforme.
- La soutenance approche (S8) — les pages légales sont un prérequis de maturité minimale.

### Contexte juridique

UNIGE Events est un projet académique développé dans le cadre du cours PINFO (Université de Genève). Le contenu des pages légales s'inspire de la politique officielle de l'UNIGE en matière de données personnelles ([memento.unige.ch/doc/0339](https://memento.unige.ch/doc/0339)) et du cadre légal genevois (LIPAD, RIPAD), tout en précisant clairement qu'il ne s'agit pas d'un service officiel de l'UNIGE.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### 1. Routes sous `/legal/*` — pas `/privacy` et `/terms` à la racine

**Décision.** Les pages sont servies sous `/legal/privacy` et `/legal/terms`, pas sous `/privacy` et `/terms`.

**Justification.** Regrouper sous un préfixe `/legal/` permet d'ajouter d'autres pages légales à l'avenir (mentions légales, RGPD, etc.) sans polluer l'espace de noms racine. Cohérent avec les conventions d'URL de sites institutionnels.

### 2. `<Link>` de react-router-dom dans le Footer — pas `<TextLink>` ni `<a>`

**Décision.** Remplacer `<TextLink href="/privacy">` et `<TextLink href="/terms">` par des `<Link to="/legal/privacy">` et `<Link to="/legal/terms">` de `react-router-dom`, en reprenant les classes CSS de `TextLink` pour conserver le style visuel.

**Justification.** `TextLink` ([`Links.tsx:16-23`](frontend/src/components/utils/Links.tsx#L16-L23)) utilise une balise `<a>` native, ce qui provoque un rechargement complet de la page (perte du state React, du contexte Auth, du thème). `<Link>` de react-router-dom effectue une navigation côté client sans reload — comportement attendu pour une SPA.

### 3. Layout `SectionWrapper` size `md` — pas `xl`

**Décision.** Les pages légales utilisent `SectionWrapper` avec `size="md"` (= `max-w-3xl`) et non `size="xl"` (= `max-w-7xl`).

**Justification.** Ce sont des pages de lecture textuelle. Une largeur de 768px (max-w-3xl) offre une longueur de ligne optimale (~65–75 caractères) pour la lisibilité. `size="xl"` est réservé aux pages à grilles (événements, profil).

### 4. Pas de composant partagé `LegalPage` — duplication acceptable

**Décision.** Chaque page légale est un composant autonome (`PrivacyPage.tsx`, `TermsPage.tsx`). Pas d'abstraction `LegalPage` partagée.

**Justification.** Les deux pages partagent le même layout mais leur contenu est fondamentalement différent (sections, titres, liens). Extraire un composant `LegalPage` qui accepterait un `children` ou un tableau de sections serait de la sur-ingénierie pour 2 pages statiques. Règle AGENTS.md : *« Three similar lines of code is better than a premature abstraction »*.

### 5. Contenu en dur dans le JSX — pas de fichier JSON/MD externe

**Décision.** Le texte légal est écrit directement dans le JSX des composants.

**Justification.** Ce sont des pages statiques sans internationalisation ni CMS. Un fichier externe (JSON, Markdown) ajouterait une couche d'indirection sans valeur. Le contenu est versionné dans Git comme tout le reste du code.

### 6. Pas de skeleton — pages statiques

**Décision.** Aucun nouveau skeleton n'est requis.

**Justification.** Les pages légales n'effectuent aucun appel API et n'ont pas d'état `loading`. Le contenu est rendu immédiatement au montage. Conformément à `AGENTS.md` : *« toute page/composant avec appel API = skeleton obligatoire »* — a contrario, une page sans appel API n'en a pas besoin.

### 7. Langue : français — cohérent avec toute l'UI

**Décision.** Le contenu est intégralement en français.

**Justification.** Toute l'interface de l'application est en français (boutons, messages, toasts, formulaires). Les pages légales suivent la même convention.

---

## Ce qui existe déjà (ne pas retoucher sauf indication contraire)

| Fichier | État |
|---|---|
| [`frontend/src/components/utils/Section.tsx`](frontend/src/components/utils/Section.tsx) | `SectionWrapper` et `SectionHeader` — composants de layout à utiliser. **Ne pas modifier.** |
| [`frontend/src/components/utils/Blobs.tsx`](frontend/src/components/utils/Blobs.tsx) | `BlobsSubtle` — background décoratif à utiliser. **Ne pas modifier.** |
| [`frontend/src/components/utils/Links.tsx`](frontend/src/components/utils/Links.tsx) | `TextLink` — composant existant utilisant `<a>`. **Ne pas modifier** (on le remplace par `<Link>` dans le Footer). |
| [`frontend/src/pages/NotFoundPage.tsx`](frontend/src/pages/NotFoundPage.tsx) | Page simple de référence pour le style. **Ne pas modifier.** |
| [`frontend/src/__tests__/pages/NotFoundPage.test.tsx`](frontend/src/__tests__/pages/NotFoundPage.test.tsx) | Pattern de test pour pages simples (MemoryRouter + render + screen). **Ne pas modifier.** |

## Ce qui est à créer

| Fichier | Action |
|---|---|
| `frontend/src/pages/legal/PrivacyPage.tsx` | **Créer** — page Politique de confidentialité |
| `frontend/src/pages/legal/TermsPage.tsx` | **Créer** — page Conditions générales d'utilisation |
| `frontend/src/__tests__/pages/legal/PrivacyPage.test.tsx` | **Créer** — tests de la page Privacy |
| `frontend/src/__tests__/pages/legal/TermsPage.test.tsx` | **Créer** — tests de la page Terms |

## Ce qui est à modifier

| Fichier | Modification |
|---|---|
| `frontend/src/router/AppRouter.tsx` | Ajouter 2 routes publiques `/legal/privacy` et `/legal/terms` (lazy import) |
| `frontend/src/components/Footer.tsx` | Remplacer `<TextLink>` par `<Link>` de react-router-dom pour les liens légaux |
| `frontend/docs/architecture.md` | Ajouter les 2 routes à la table de routage |
| `frontend/docs/components.md` | Ajouter les 2 pages à la section Pages |

## Ce qui n'est PAS dans le scope

- ❌ Aucune modification backend, openapi, ou entité
- ❌ Pas de nouveau hook, service, ou context
- ❌ Pas de nouveau skeleton (pages statiques sans appel API)
- ❌ Pas de système de consentement cookies (pas de banner RGPD)
- ❌ Pas de page admin pour éditer le contenu légal
- ❌ Pas de modification de `Links.tsx` (on ne touche pas à `TextLink`)
- ❌ Pas d'internationalisation — contenu français uniquement

---

## Étape 1 — Créer `PrivacyPage.tsx`

**Fichier :** `frontend/src/pages/legal/PrivacyPage.tsx` (nouveau)

```tsx
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
```

**Points à respecter :**
- Import alias `@/` — jamais de chemin relatif.
- `export default` pour le lazy import dans `AppRouter`.
- `Readonly<Props>` non requis — le composant n'a pas de props.
- Design tokens Tailwind : `text-foreground`, `text-foreground/70`, `text-accent`, `hover:underline`.
- Lien interne `/profile/me/edit` via `<Link>` de react-router-dom.
- Liens mailto via `<a>` natif (pas de routing SPA pour les liens externes/mailto).
- `relative z-10` sur le conteneur de texte pour passer devant les blobs.

---

## Étape 2 — Créer `TermsPage.tsx`

**Fichier :** `frontend/src/pages/legal/TermsPage.tsx` (nouveau)

```tsx
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { Link } from 'react-router-dom'

export default function TermsPage() {
  return (
    <SectionWrapper padding="md" size="md" background={<BlobsSubtle />}>
      <SectionHeader
        title="Conditions générales d'utilisation"
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
          <Link to="/" className="text-sm text-foreground/40 hover:text-foreground/60 transition-colors">
            Retour à l'accueil
          </Link>
        </div>
      </div>
    </SectionWrapper>
  )
}
```

---

## Étape 3 — Modifier `AppRouter.tsx`

**Fichier :** `frontend/src/router/AppRouter.tsx`

### 3.1 — Ajouter les lazy imports

Après la ligne `const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'))` (ligne 23), ajouter :

```tsx
const PrivacyPage = lazy(() => import('@/pages/legal/PrivacyPage'))
const TermsPage = lazy(() => import('@/pages/legal/TermsPage'))
```

### 3.2 — Ajouter les routes publiques

Dans le bloc `<Route element={<Layout />}>`, après le bloc `/calendar` (ligne 44) et **avant** le bloc `<Route element={<PrivateRoute/>}>` (ligne 46), ajouter :

```tsx
            <Route path="/legal">
              <Route path="privacy" element={<PrivacyPage />} />
              <Route path="terms" element={<TermsPage />} />
            </Route>
```

**Points à respecter :**
- Routes **publiques** — hors du bloc `<PrivateRoute>`.
- Nested sous `/legal` pour la cohérence URL.
- `lazy` import pour le code splitting (cohérent avec toutes les autres pages).

**Fichier résultant (extrait pertinent) :**

```tsx
const PrivacyPage = lazy(() => import('@/pages/legal/PrivacyPage'))
const TermsPage = lazy(() => import('@/pages/legal/TermsPage'))

// ...dans le JSX :
            <Route path="/calendar">
              <Route index element={<CalendarPage />} />
            </Route>

            <Route path="/legal">
              <Route path="privacy" element={<PrivacyPage />} />
              <Route path="terms" element={<TermsPage />} />
            </Route>

            <Route element={<PrivateRoute/>}>
```

---

## Étape 4 — Modifier `Footer.tsx`

**Fichier :** `frontend/src/components/Footer.tsx`

### 4.1 — Ajouter l'import `Link`

En haut du fichier, après les imports existants (ligne 1–4), ajouter :

```tsx
import { Link } from 'react-router-dom'
```

### 4.2 — Remplacer les liens légaux

Remplacer le bloc (lignes 88–91) :

```tsx
                    <div className="flex gap-8">
                        <TextLink href="/privacy">Politique de confidentialité</TextLink>
                        <TextLink href="/terms">Conditions générales</TextLink>
                    </div>
```

Par :

```tsx
                    <div className="flex gap-8">
                        <Link to="/legal/privacy" className="text-sm text-overlay hover:text-foreground transition-colors">
                            Politique de confidentialité
                        </Link>
                        <Link to="/legal/terms" className="text-sm text-overlay hover:text-foreground transition-colors">
                            Conditions générales
                        </Link>
                    </div>
```

**Points à respecter :**
- Les classes CSS reprennent le style de `TextLink` (`text-sm text-overlay hover:text-foreground transition-colors`) sans la flèche décorative (`decorate` prop de `TextLink`), puisque les liens du Footer n'utilisaient pas `decorate`.
- `<Link to=...>` au lieu de `<a href=...>` pour la navigation SPA.
- L'import de `TextLink` depuis `Links.tsx` reste utilisé ailleurs dans le Footer (liens Plateforme, Ressources) — ne pas le supprimer.

---

## Étape 5 — Tests

### 5.1 — `PrivacyPage.test.tsx`

**Fichier :** `frontend/src/__tests__/pages/legal/PrivacyPage.test.tsx` (nouveau)

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import PrivacyPage from '@/pages/legal/PrivacyPage'

vi.mock('@/components/utils/Blobs', () => ({
  BlobsSubtle: () => <div data-testid="blobs-subtle" />,
}))

afterEach(() => { cleanup() })

describe('PrivacyPage', () => {
  function renderPage() {
    return render(
      <MemoryRouter>
        <PrivacyPage />
      </MemoryRouter>,
    )
  }

  it('renders the page title', () => {
    renderPage()
    expect(screen.getByText('Politique de confidentialité')).toBeTruthy()
  })

  it('renders the academic project disclaimer', () => {
    renderPage()
    expect(screen.getByText(/projet académique/i)).toBeTruthy()
  })

  it('renders the legal framework section', () => {
    renderPage()
    expect(screen.getByText('Cadre légal')).toBeTruthy()
  })

  it('renders the data collection section', () => {
    renderPage()
    expect(screen.getByText('Données collectées')).toBeTruthy()
  })

  it('renders the cookies section', () => {
    renderPage()
    expect(screen.getByText('Cookies')).toBeTruthy()
  })

  it('renders the user rights section', () => {
    renderPage()
    expect(screen.getByText('Vos droits')).toBeTruthy()
  })

  it('renders the security section', () => {
    renderPage()
    expect(screen.getByText('Sécurité')).toBeTruthy()
  })

  it('renders the responsible entity', () => {
    renderPage()
    expect(screen.getByText('Responsable du traitement')).toBeTruthy()
    expect(screen.getByText('Université de Genève')).toBeTruthy()
  })

  it('renders the back to home link', () => {
    renderPage()
    const link = screen.getByText("Retour à l'accueil")
    expect(link).toBeTruthy()
    expect(link.getAttribute('href')).toBe('/')
  })

  it('renders the profile edit link for rights exercise', () => {
    renderPage()
    const link = screen.getByText('Mon profil')
    expect(link).toBeTruthy()
    expect(link.getAttribute('href')).toBe('/profile/me/edit')
  })
})
```

### 5.2 — `TermsPage.test.tsx`

**Fichier :** `frontend/src/__tests__/pages/legal/TermsPage.test.tsx` (nouveau)

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import TermsPage from '@/pages/legal/TermsPage'

vi.mock('@/components/utils/Blobs', () => ({
  BlobsSubtle: () => <div data-testid="blobs-subtle" />,
}))

afterEach(() => { cleanup() })

describe('TermsPage', () => {
  function renderPage() {
    return render(
      <MemoryRouter>
        <TermsPage />
      </MemoryRouter>,
    )
  }

  it('renders the page title', () => {
    renderPage()
    expect(screen.getByText("Conditions générales d'utilisation")).toBeTruthy()
  })

  it('renders the academic project disclaimer', () => {
    renderPage()
    expect(screen.getByText(/cadre du cours PINFO/i)).toBeTruthy()
  })

  it('renders the object section', () => {
    renderPage()
    expect(screen.getByText('Objet')).toBeTruthy()
  })

  it('renders the user content section', () => {
    renderPage()
    expect(screen.getByText('Contenu utilisateur')).toBeTruthy()
  })

  it('renders the moderation section', () => {
    renderPage()
    expect(screen.getByText('Modération')).toBeTruthy()
  })

  it('renders the liability limitation section', () => {
    renderPage()
    expect(screen.getByText('Limitation de responsabilité')).toBeTruthy()
  })

  it('renders the applicable law section', () => {
    renderPage()
    expect(screen.getByText('Droit applicable et for juridique')).toBeTruthy()
  })

  it('renders the privacy policy link', () => {
    renderPage()
    const link = screen.getByText('Politique de confidentialité')
    expect(link).toBeTruthy()
    expect(link.getAttribute('href')).toBe('/legal/privacy')
  })

  it('renders the back to home link', () => {
    renderPage()
    const link = screen.getByText("Retour à l'accueil")
    expect(link).toBeTruthy()
    expect(link.getAttribute('href')).toBe('/')
  })

  it('renders the GitHub link', () => {
    renderPage()
    const link = screen.getByText('GitHub')
    expect(link).toBeTruthy()
    expect(link.getAttribute('href')).toBe('https://github.com/unige-pinfo6-2026/unige-events')
    expect(link.getAttribute('target')).toBe('_blank')
    expect(link.getAttribute('rel')).toBe('noopener noreferrer')
  })
})
```

**Récap :** 10 tests pour PrivacyPage, 10 tests pour TermsPage. Couvrent le titre, les sections clés, les liens internes/externes, et la mention projet académique. Pattern identique à `NotFoundPage.test.tsx`.

---

## Étape 6 — Documentation

### 6.1 — `frontend/docs/architecture.md`

**Ajouter** les 2 routes à la table de routage, après la ligne `/calendar` et avant `/admin/*` :

```markdown
| /legal/privacy | PrivacyPage | pages/legal/PrivacyPage.tsx | publique |
| /legal/terms | TermsPage | pages/legal/TermsPage.tsx | publique |
```

### 6.2 — `frontend/docs/components.md`

**Ajouter** les 2 pages dans la section « Pages », après `MyPublicationsPage` :

```markdown
| /legal/privacy | PrivacyPage | fait |
| /legal/terms | TermsPage | fait |
```

**Ajouter** une description des pages après les fiches existantes :

```markdown
### PrivacyPage

- Route `/legal/privacy`, publique.
- Page statique de lecture (SectionWrapper size `md` = `max-w-3xl`, BlobsSubtle).
- Contenu : politique de confidentialité inspirée du cadre légal UNIGE (LIPAD, RIPAD, RGPD).
- Sections : cadre légal, responsable du traitement, données collectées, finalités, base légale, principes, partage, cookies, durée de conservation, droits, sécurité, contact.
- Lien interne vers `/profile/me/edit` pour l'exercice du droit de rectification.
- Mention explicite du caractère académique (projet PINFO).

### TermsPage

- Route `/legal/terms`, publique.
- Page statique de lecture (même layout que PrivacyPage).
- Contenu : conditions générales d'utilisation de la plateforme.
- Sections : objet, description de la plateforme, inscription, contenu utilisateur, modération, propriété intellectuelle, disponibilité, limitation de responsabilité, données personnelles (renvoi vers `/legal/privacy`), modification des conditions, droit applicable, contact.
- Lien externe vers le dépôt GitHub (open source).
- Mention explicite du caractère académique (projet PINFO).
```

---

## Critères d'acceptation

- [ ] Naviguer vers `/legal/privacy` affiche la page Politique de confidentialité avec le titre centré, les blobs en arrière-plan, et tout le contenu textuel structuré en sections
- [ ] Naviguer vers `/legal/terms` affiche la page Conditions générales d'utilisation avec le même layout
- [ ] Cliquer « Politique de confidentialité » dans le Footer navigue vers `/legal/privacy` **sans rechargement de page** (navigation SPA)
- [ ] Cliquer « Conditions générales » dans le Footer navigue vers `/legal/terms` **sans rechargement de page**
- [ ] Sur la page Terms, le lien « Politique de confidentialité » navigue vers `/legal/privacy`
- [ ] Sur la page Privacy, le lien « Mon profil » navigue vers `/profile/me/edit`
- [ ] Les deux pages sont accessibles sans authentification (routes publiques)
- [ ] Les deux pages sont responsives (lisibles sur mobile et desktop)
- [ ] Le thème sombre fonctionne correctement sur les deux pages
- [ ] `npm run lint` vert
- [ ] `npm run test` vert

---

## Edge cases

| Cas | Comportement attendu | Implémenté par |
|---|---|---|
| Accès direct via URL `/legal/privacy` | Page rendue correctement (pas de 404) | Route publique dans `AppRouter` |
| Accès direct via URL `/legal/terms` | Page rendue correctement (pas de 404) | Route publique dans `AppRouter` |
| Anciens liens `/privacy` et `/terms` | Tombent sur `NotFoundPage` (404) — acceptable, aucun bookmark externe connu | Catch-all `*` existant dans `AppRouter` |
| Accès `/legal` sans suffixe | Tombe sur `NotFoundPage` (404) — pas d'index `/legal` | Pas de route `index` sous `/legal` |
| Thème sombre | Texte `text-foreground/70` s'adapte automatiquement | Design tokens CSS dans `index.css` |
| Mobile petit écran | Contenu lisible, `max-w-3xl` + `px-4 sm:px-6 lg:px-8` via `SectionWrapper` | Props `size="md"` de `SectionWrapper` |

---

## Résumé des fichiers à créer/modifier

| Fichier | Action |
|---|---|
| `frontend/src/pages/legal/PrivacyPage.tsx` | **Créer** |
| `frontend/src/pages/legal/TermsPage.tsx` | **Créer** |
| `frontend/src/__tests__/pages/legal/PrivacyPage.test.tsx` | **Créer** |
| `frontend/src/__tests__/pages/legal/TermsPage.test.tsx` | **Créer** |
| `frontend/src/router/AppRouter.tsx` | **Modifier** — 2 lazy imports + 2 routes publiques |
| `frontend/src/components/Footer.tsx` | **Modifier** — import `Link` + remplacer `TextLink` par `Link` pour les liens légaux |
| `frontend/docs/architecture.md` | **Modifier** — 2 routes ajoutées à la table |
| `frontend/docs/components.md` | **Modifier** — 2 pages ajoutées + fiches descriptives |

**Total :** 4 fichiers créés, 4 fichiers modifiés.

---

## Règles critiques à respecter

| Règle | Détail |
|---|---|
| Pas de backend | Purement frontend — aucune modification de `openapi.yaml`, d'entité ou de service backend |
| Pas de hook/service/context | Pages statiques, pas d'appel API |
| Pas de skeleton | Pages sans état `loading` — pas d'appel réseau |
| Routes publiques | Hors du bloc `<PrivateRoute>` dans `AppRouter` |
| Lazy import | `const PrivacyPage = lazy(() => import('@/pages/legal/PrivacyPage'))` — cohérent avec toutes les autres pages |
| `<Link>` dans le Footer | Navigation SPA — pas de `<a>` natif pour les liens internes |
| Design tokens | `text-foreground`, `text-foreground/70`, `text-accent`, `bg-background` — jamais de couleurs brutes |
| Alias `@/` | Pas de chemin relatif `../` |
| Pas de `any` TS | Pas de props sur ces pages, mais respect de TypeScript strict |
| Contenu en français | Cohérent avec toute l'UI de l'application |
| Doc dans le même commit | `architecture.md` + `components.md` mis à jour avec le code |
| SonarCloud | ≥ 80 % coverage sur le nouveau code, ≤ 3 % duplication, Security/Reliability/Maintainability Rating A |

---

## Prompt de lancement d'implémentation

````
Tu vas implémenter la tâche SCRUM-166 (pages légales `/legal/privacy` et `/legal/terms`) sur **une seule branche `feature/SCRUM-166-legal-pages`** créée depuis `main`, et **une seule PR finale**.

## Source unique de vérité
Le fichier `specs_archives/specs_claude/specs_scrum-166.md` est la source de vérité pour QUOI et POURQUOI. Lis-le entièrement avant de commencer et reviens-y à chaque étape.

## Lectures préliminaires obligatoires
Avant d'écrire du code, lis ces fichiers en entier :
- `frontend/AGENTS.md` (conventions, design tokens, pattern variants)
- `frontend/docs/architecture.md`, `components.md`
- Tous les fichiers à modifier (cf. liste « Résumé des fichiers à créer/modifier » de la spec) — TOUS, pas juste les diffs
- `frontend/src/components/utils/Section.tsx` et `Blobs.tsx` (composants de layout utilisés)
- `frontend/src/pages/NotFoundPage.tsx` (page simple de référence)
- `frontend/src/__tests__/pages/NotFoundPage.test.tsx` (pattern de test)

## Préparation de la branche
```bash
git checkout main
git pull origin main
git checkout -b feature/SCRUM-166-legal-pages
```

## Ordre d'implémentation strict

### Phase 1 — Pages légales
1. Créer `frontend/src/pages/legal/PrivacyPage.tsx` (cf. spec étape 1 — code complet à reprendre).
2. Créer `frontend/src/pages/legal/TermsPage.tsx` (cf. spec étape 2 — code complet à reprendre).

### Phase 2 — Routeur
3. Modifier `frontend/src/router/AppRouter.tsx` (cf. spec étape 3) :
   - Ajouter 2 lazy imports pour `PrivacyPage` et `TermsPage`
   - Ajouter le bloc `<Route path="/legal">` avec les 2 sous-routes, dans le bloc public (avant `<PrivateRoute>`)

### Phase 3 — Footer
4. Modifier `frontend/src/components/Footer.tsx` (cf. spec étape 4) :
   - Ajouter `import { Link } from 'react-router-dom'`
   - Remplacer les 2 `<TextLink>` des liens légaux par des `<Link>` avec les mêmes classes CSS

### Phase 4 — Tests
5. Créer `frontend/src/__tests__/pages/legal/PrivacyPage.test.tsx` (cf. spec étape 5.1 — 10 tests).
6. Créer `frontend/src/__tests__/pages/legal/TermsPage.test.tsx` (cf. spec étape 5.2 — 10 tests).
7. Lancer `npm run test` — tout doit passer.

### Phase 5 — Documentation (dans le même commit)
8. `frontend/docs/architecture.md` — ajouter les 2 routes à la table de routage.
9. `frontend/docs/components.md` — ajouter les 2 pages à la section Pages + fiches descriptives.

### Phase 6 — Vérification finale
10. `npm run lint` — vert.
11. `npm run test` — vert.
12. Test manuel dans le navigateur (`npm run dev`) :
    - Naviguer vers `/legal/privacy` → page affichée avec titre, sections, blobs
    - Naviguer vers `/legal/terms` → page affichée avec titre, sections, blobs
    - Cliquer les liens dans le Footer → navigation SPA (pas de reload)
    - Sur `/legal/terms`, cliquer « Politique de confidentialité » → navigue vers `/legal/privacy`
    - Sur `/legal/privacy`, cliquer « Mon profil » → navigue vers `/profile/me/edit`
    - Vérifier le thème sombre (toggle dans la navbar)
    - Vérifier le responsive (réduire la fenêtre)
13. Commit + push + PR.

## Interdits stricts
- ❌ Ne pas modifier le backend, openapi, ou les entités.
- ❌ Ne pas créer de hook, service, ou context.
- ❌ Ne pas créer de skeleton.
- ❌ Ne pas modifier `Links.tsx` (on ne touche pas à `TextLink`).
- ❌ Ne jamais utiliser `any` TypeScript.
- ❌ Ne jamais importer en chemin relatif (`../`) — toujours `@/`.

## Conventions à respecter
- camelCase partout
- Design tokens Tailwind (`text-foreground`, `text-foreground/70`, `text-accent`, `bg-background`)
- `export default` sur les pages (requis pour le `lazy` import)
- Doc mise à jour **dans le même commit** que le code (règle d'or AGENTS.md)

## Commit & PR
- Commit unique avec code + tests + docs
- PR title : `feat(scrum-166): add legal pages /legal/privacy and /legal/terms`
- Push sur `feature/SCRUM-166-legal-pages`

## Critères de done
- [ ] `npm run lint` vert
- [ ] `npm run test` vert avec couverture ≥ 80 % sur `PrivacyPage.tsx` et `TermsPage.tsx`
- [ ] Vérification manuelle navigateur (Phase 6 point 12) — les 2 pages s'affichent, les liens Footer naviguent en SPA, le thème sombre fonctionne, le responsive est correct
- [ ] PR ouverte sur `feature/SCRUM-166-legal-pages`, titre `feat(scrum-166): add legal pages /legal/privacy and /legal/terms`
- [ ] SonarCloud sur la PR : Quality Gate vert
````
