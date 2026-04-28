<!--
Merci de remplir cette description de PR.
Les sections marquées (optionnel) peuvent être supprimées si elles ne s'appliquent pas.
Le titre de la PR doit suivre la convention : <type>(<scope>): <description>
  - types : feat, fix, docs, style, refactor, test, chore, ci, perf
  - exemple : feat(backend): add /users/me/events endpoint
-->

## Résumé

<!-- 1 à 3 phrases. Mentionner le ticket en gras, ex. **SCRUM-133**. Quoi + pourquoi. -->

<!-- Optionnel — supprimer si non pertinent -->
## Why / Motivation

<!-- Pour les features non triviales : contexte, problème résolu, alternatives écartées. -->

## Changements

<!-- Sous-sections par domaine. Bullet lists avec références de fichiers cliquables. -->

### Backend
<!-- - Ajout de `getMyEvents` (`backend/src/main/java/.../UserResource.java`) -->

### Frontend
<!-- - Nouveau hook `useMyEvents` (`frontend/src/hooks/useMyEvents.ts`) -->

### Infrastructure
<!-- - Workflow CI mis à jour (`.github/workflows/ci.yml`) -->

### Documentation
<!-- - `docs/components.md` : ajout du service `myEventsService` -->

## Tests

<!-- Tests ajoutés/modifiés (unitaires, intégration, e2e) + comment valider manuellement. -->

## Test plan

<!-- Checklist concrète à cocher par le reviewer ou l'author avant merge. -->
- [ ] 
- [ ] 
- [ ] 

## Documentation

<!--
Règle d'or AGENTS.md : si tu touches au code, tu touches à la doc correspondante dans le même commit.
Lister les fichiers de doc modifiés, ou justifier explicitement qu'aucun n'était nécessaire.
Cibles fréquentes : docs/components.md, docs/architecture.md, docs/types.md,
docs/sprint-context.md, docs/openapi/openapi.yaml, frontend/AGENTS.md.
-->
- [ ] Documentation mise à jour ou non applicable (justifier ci-dessous)

<!-- Optionnel — supprimer si non pertinent -->
## Dépendances / ordre de merge

<!-- Lister les PRs/branches à merger avant celle-ci, ex. dépend de #123 (feature/SCRUM-130-…). -->

<!-- Optionnel — supprimer si non pertinent -->
## Décisions techniques tranchées

<!-- Points déjà arbitrés en amont qu'on ne souhaite pas re-débattre en review.
     Ex. "choix de Zustand plutôt que Redux : validé en sprint planning S6". -->

<!-- Optionnel — supprimer si non pertinent -->
## Notes pour le reviewer

<!-- Zones sensibles à regarder en priorité, points d'attention, captures d'écran, etc. -->
