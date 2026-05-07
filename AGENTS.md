# AGENTS.md — unige-events (root)

Ce dépôt est un monorepo contenant le frontend React et le backend Quarkus de UNIGE Events.

Chaque sous-projet possède son propre `AGENTS.md` avec les conventions, commandes et règles qui lui sont propres. **Lire et suivre le fichier correspondant au périmètre de la tâche en cours.**

## Sous-projets

| Dossier | Stack | AGENTS.md |
|---|---|---|
| `frontend/` | React 19 · TypeScript · Vite · Nginx | [`frontend/AGENTS.md`](frontend/AGENTS.md) |
| `backend/` | Java 21 · Quarkus 3 · Hibernate Panache · PostgreSQL 16 | [`backend/AGENTS.md`](backend/AGENTS.md) |

## Contrat API partagé

`openapi/openapi.yaml` est la **source de vérité unique** pour le contrat API, partagée entre frontend et backend. Ne jamais dupliquer ce fichier.

## Workflow Git global

- Branche : `feature/SCRUM-XX-description`
- 1 PR par tâche, review obligatoire avant merge sur main
- Titre de PR : format `<type>(<scope>): <description>` (validé par CI)
- Types autorisés : `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `perf`
- Pour `feat` / `refactor` / `perf`, le scope est obligatoirement l'identifiant Jira en minuscules, ex. `feat(scrum-133): ...`
