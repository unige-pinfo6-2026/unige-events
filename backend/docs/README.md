# Documentation — unige-events-api

Index de tous les fichiers de documentation du service API.

---

| Fichier | Description |
|---|---|
| [architecture.md](architecture.md) | Vue d'ensemble microservices, architecture en couches du backend, flux de requête, domain model, infra Kubernetes, CI/CD |
| [data-model.md](data-model.md) | Entités JPA (champs, types, contraintes), DTOs, conventions de nommage (camelCase, booléens sans `is`), enums, gestion du schéma |
| [api-contract.md](api-contract.md) | Tableau de tous les endpoints (méthode, auth, codes HTTP, comportements critiques), endpoints planifiés par sprint |
| [openapi/openapi.yaml](openapi/openapi.yaml) | Contrat API complet au format OpenAPI 3.0 — **source de vérité** pour tous les endpoints et schémas |
| [dev-guide.md](dev-guide.md) | Prérequis, lancement local, commandes Maven, workflows spec-first, conventions de nommage, variables d'environnement |
| [sprint-context.md](sprint-context.md) | État d'avancement des sprints, bugs connus, backlog par sprint, dette technique |

---

## Règle d'or

> **`docs/openapi/openapi.yaml` est la source de vérité.**
> Avant d'implémenter un endpoint → l'ajouter dans `openapi.yaml`. Avant de modifier un schéma → mettre à jour `openapi.yaml`.

## Lire aussi

- [AGENTS.md](../AGENTS.md) — conventions critiques pour les agents IA et les développeurs
- [openspec/](../openspec/) — specs actives et archives des décisions techniques (spec-driven development). Les specs actives dans `openspec/specs/` définissent les exigences du prochain sprint. Les archives dans `openspec/changes/` expliquent *pourquoi* des décisions ont été prises.
