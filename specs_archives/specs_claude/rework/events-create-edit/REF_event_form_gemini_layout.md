# REF — Description image de référence Gemini : layout formulaire événement

> Auteur : Claude  
> Date : 2026-04-11  
> Usage : Description technique de l'image de référence `Screenshot 2026-04-11 at 20-53-31 Design System Analysis for Website Revamp - Google Gemini.png`  
> Statut : **Document de référence visuelle — à utiliser pour rédiger les specs d'implémentation**

---

## 1. Contexte et périmètre

L'image montre une proposition de redesign de la page `/events/new` générée par Gemini. Elle conserve le header et le footer du site existant. **Seule la zone centrale du formulaire est à retravailler.** Ce document décrit précisément cette zone centrale pour qu'elle serve de base à des specs d'implémentation frontend.

---

## 2. Ce qui est conservé (hors scope du rework)

- **Header** : logo UNIGE, liens de navigation (En ce moment, Fonctionnalités, FAQ, Commencer), icône recherche, toggle dark mode, avatar utilisateur
- **Fond de page** : dark avec halo ambient violet/rose (identique au design system actuel — BlobsSubtle)
- **Footer** : bandeau bas avec copyright et décoration étoile

---

## 3. Zone centrale — Vue d'ensemble

La zone centrale abandonne le modèle "card verticale avec sections nommées" pour un layout **ouvert, horizontal, sans carte contenante visible**. Les champs flottent directement sur le fond sombre. La page respire plus. Le formulaire est organisé en **3 bandes horizontales** superposées, chacune avec sa propre logique de colonnes.

### 3.1 Hero (inchangé par rapport au rework actuel)

- Titre centré : `Créer un événement` — "événement" en dégradé rose/accent (via `<mark>`)
- Sous-titre centré : `Renseignez les informations de votre événement pour le partager avec la communauté UNIGE.`
- Police et hiérarchie identiques à la spec `SPEC_event_form_rework.md`

---

## 4. Bande 1 — Layout deux colonnes : Bannière | Titre + Description

### Structure

```
┌─────────────────────────────────┬─────────────────────────────────┐
│                                 │  Titre                          │
│      [Zone upload bannière]     │  [________________________]     │
│                                 │                                 │
│   ↑ upload icon                 │  Description                    │
│   "Ajoutez une image            │  [________________________]     │
│    de couverture"               │  [________________________]     │
│                                 │  [________________________]     │
└─────────────────────────────────┴─────────────────────────────────┘
```

### Colonne gauche — Zone upload bannière (~45% largeur)

- Rectangle à **bord en pointillés** (`border-dashed`), coins arrondis (`rounded-2xl` ou `rounded-xl`)
- Hauteur : environ celle de Titre + Description combinés (alignement vertical)
- Contenu centré verticalement et horizontalement :
  - Icône upload (flèche vers le haut, style outline léger, taille ~32px)
  - Texte : `Ajoutez une image de couverture` — petit, gris clair (`text-foreground/40`)
- Fond : légèrement plus sombre que la page ou transparent — pas de `bg-foreground/5` marqué
- Aucun bouton "Choisir une image" visible dans cette variante — le clic sur la zone entière déclencherait l'input file

### Colonne droite — Titre + Description (~55% largeur)

- **Champ Titre** :
  - Label `Titre` en petit texte gris au-dessus du champ
  - Input texte, fond sombre semi-transparent, bord fin (`border border-border`), coins arrondis (`rounded-xl`)
  - Hauteur : une ligne standard (~40-44px)
  - Placeholder : `Titre`

- **Champ Description** :
  - Label `Description` en petit texte gris au-dessus du champ
  - Textarea, même style que l'input Titre
  - Hauteur : environ 3-4 lignes visuelles, non redimensionnable dans la maquette
  - Placeholder : `Description`

- Les deux champs sont empilés verticalement dans la colonne droite, avec un gap modéré (~`gap-3` ou `gap-4`)

### Gap entre les deux colonnes

- Environ `gap-4` ou `gap-6` — les deux colonnes respirent sans être trop espacées

---

## 5. Bande 2 — Ligne unique : Lieu | Début | Fin

### Structure

```
┌──────────────────────────────┬──────────────────┬──────────────────┐
│  🔍  Lieu                    │  📅  Début        │  📅  Fin          │
│  [_________________________] │  [______________] │  [______________] │
└──────────────────────────────┴──────────────────┴──────────────────┘
```

### Détails

- **Trois champs sur une seule ligne horizontale**, dans une grille à 3 colonnes
- Proportions approximatives : Lieu ~`2fr`, Début ~`1fr`, Fin ~`1fr` (ou `grid-cols-[2fr_1fr_1fr]`)
- Chaque champ a :
  - Son **label** (`Lieu`, `Début`, `Fin`) en petit texte au-dessus ou en placeholder
  - Une **icône à gauche dans l'input** : pin/localisation pour Lieu, calendrier pour Début et Fin
  - Fond sombre semi-transparent, bord fin, coins arrondis (`rounded-xl`)
- Pour Début et Fin : **input de type `date`** (le sélecteur natif du navigateur est visible via l'icône calendrier)
- Même hauteur de champ que Titre (~40-44px)

---

## 6. Bande 3 — Ligne unique : Configuration | Capacité | Statut | CTA

### Structure

```
┌──────────────────────────────────┬──────────┬──────────┬─────────────────────┐
│  Configuration                   │ Capacité │ Statut   │                     │
│  [Académique] Sports Culturel    │  [1 ▲▼]  │ [♥ Brou] │  CRÉER L'ÉVÉNEMENT  │
│               Social             │          │          │                     │
└──────────────────────────────────┴──────────┴──────────┴─────────────────────┘
```

### Sous-section Configuration — Pills de catégorie

- **Label** `Configuration` en petit texte gris, au-dessus des pills
- **Catégories affichées comme des pills/chips horizontaux** (pas de `<select>`) :
  - `Académique` (sélectionné) : fond rose/accent plein (`bg-accent text-white`), coins arrondis (`rounded-full` ou `rounded-lg`)
  - `Sports`, `Culturel`, `Social` (non sélectionnés) : fond sombre, bord fin, texte gris clair — style `outlined`
  - Les pills sont sur la même ligne, séparés par un petit gap (`gap-2`)
- Comportement attendu : sélection exclusive (un seul actif à la fois), le pill actif prend la couleur accent

### Sous-section Capacité

- **Label** `Capacité` en petit texte gris au-dessus
- **Input numérique** avec flèches haut/bas (spinner natif ou custom) — valeur montrée : `1`
- Largeur réduite (~`w-20` ou `w-24`), même style de fond/bord que les autres inputs

### Sous-section Statut

- **Label** `Statut` en petit texte gris au-dessus
- **Select/dropdown** avec :
  - Icône cœur/favori en rose à gauche de la valeur affichée (décorative, contextuelle au statut Brouillon)
  - Valeur : `Brouillon`
  - Même style de fond/bord que les autres inputs, largeur modérée (~`w-32` ou `w-36`)

### CTA principal

- **Bouton `CRÉER L'ÉVÉNEMENT`** :
  - Texte en majuscules (`uppercase`)
  - Fond rose/accent plein (`bg-accent`), texte blanc, coins arrondis (`rounded-xl`)
  - Taille : hauteur identique aux inputs de la ligne, largeur généreuse (auto selon le texte + `px-6`)
  - **Pas de bouton "Annuler"** visible dans cette maquette — ou relégué ailleurs

### Lien secondaire sous la bande 3

- `Sauvegarder en Brouillon` — texte lien, petit, gris clair, aligné à droite ou centré-droite, en dessous du bouton CTA
- Style : `text-sm text-foreground/40 underline` ou sans soulignement, hover discret

---

## 7. Styles globaux des inputs

| Propriété | Valeur observée |
|---|---|
| Fond | Sombre semi-transparent — légèrement plus clair que la page (`bg-background/60` ou similaire) |
| Bord | Fin, discret (`border border-border` ou `border border-white/10`) |
| Coins | Arrondis moyens (`rounded-xl`) |
| Texte | Blanc/gris clair |
| Placeholder | Gris très atténué (`text-foreground/30`) |
| Label | Petit (`text-sm`), gris (`text-foreground/50`), au-dessus du champ |
| Focus | Non visible dans la maquette statique — à définir (probablement `ring-accent/50`) |

**Absence notable** : pas de `backdrop-blur` marqué sur les inputs (contrairement aux cards glassmorphism du rework actuel). L'effet "verre" est moins prononcé ici — les inputs sont plus "plats" sur le fond sombre.

---

## 8. Différences majeures vs implémentation actuelle (`SPEC_event_form_rework.md`)

| Aspect | Implémentation actuelle (SPEC_event_form_rework) | Référence Gemini |
|---|---|---|
| **Conteneur formulaire** | Card glassmorphism (`backdrop-blur-xl`, dégradé) | Aucune card — champs flottants sur le fond |
| **Sections nommées** | 4 sections avec labels uppercase + séparateurs | Aucune section nommée — groupement par proximité spatiale |
| **Bannière** | Champ dans la section Bannière, en bas du formulaire | En haut à gauche, colonne dédiée, côte à côte avec Titre/Description |
| **Catégorie** | `<select>` dropdown | Pills/chips de sélection (boutons toggle) |
| **Lieu + Dates** | Sections séparées (Lieu dans Infos générales, Dates dans section Dates) | Même ligne horizontale (`grid-cols-3`) |
| **CTA + Annuler** | Footer séparé avec séparateur `border-t` | CTA inline dans la ligne Configuration, "Brouillon" comme lien texte |
| **Logique de scroll** | Formulaire long, scroll nécessaire sur petits écrans | Formulaire compact, tentative d'afficher tout above the fold sur desktop |

---

## 9. Points d'attention pour l'implémentation future

1. **Pills de catégorie** : remplace `<select>` mais doit conserver la même valeur de champ (`category` dans `EventFormValues`). Comportement : clic → `onFieldChange('category', id)`.

2. **Zone upload = trigger** : si la zone bannière entière est cliquable, elle doit être une `<label htmlFor="event-banner">` englobante — conserver l'`<input type="file" id="event-banner" className="hidden">`.

3. **Layout responsive** : le layout 2 colonnes + la ligne Lieu/Dates à 3 colonnes s'effondrent en colonne unique sur mobile (< `sm`). Les pills de catégorie peuvent wrapper sur plusieurs lignes.

4. **Icônes dans les inputs** : Lieu (MapPin), Début/Fin (Calendar) de Lucide — wrapper `relative` sur l'input + icône `absolute left-3`.

5. **Logique "Sauvegarder en Brouillon"** : peut être implémenté comme un submit avec `status = 'DRAFT'` forcé, ou juste un lien vers la fonctionnalité existante de statut — à préciser dans les specs.

6. **Alignement vertical bande 3** : tous les éléments (pills, inputs Capacité/Statut, bouton CTA) doivent être alignés sur la même baseline. Utiliser `items-end` ou `items-center` selon l'ajustement visuel avec les labels au-dessus.
