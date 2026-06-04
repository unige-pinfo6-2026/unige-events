# ADR-002 — `GET /events/{id}/organizer-uuids` reste `@PermitAll`

| Champ | Valeur |
|---|---|
| Date | 2026-05-10 |
| Status | Accepted |
| Author | Backend / Étape 24.1.3 finalization-pre-merge |
| Review reference | Item A16 review consolidée multi-agent PR #158 |

## Préambule (FR / EN)

- **FR** — Cet ADR formalise la décision de laisser l'endpoint
  `GET /events/{id}/organizer-uuids` annoté `@PermitAll` au lieu de le marquer
  `@Internal` (filter `X-Internal-Token`). Décision actée pour la clôture de la
  PR #158 (migration backend monolithe → 5 microservices).
- **EN** — This ADR formalizes the decision to keep the `GET
  /events/{id}/organizer-uuids` endpoint as `@PermitAll` rather than marking it
  `@Internal` (X-Internal-Token filter). Acted at PR #158 closure (backend
  monolith → 5 microservices migration).

## Context

Le seul consommateur de `/events/{id}/organizer-uuids` est `engagement-service`
(`CommentService.computeOrganizerUserIds`, `ReportService.bulkFetchEvents`). Il
appelle d'abord `GET /events/{id}?check-co-org-of={callerUuid}` qui applique la
cascade ISSUE-92 (404 sur Event DRAFT/CANCELLED/EXPIRED non-creator) +
SCRUM-136 (`coOrganizerOf: bool` dans la réponse).

La review consolidée (item A16, code-reviewer Décision G) signale que
l'endpoint, bien que documenté interne dans `internal-endpoints.md`, n'est pas
protégé par `@Internal`. Un anonyme peut donc invoquer
`/events/123/organizer-uuids` et énumérer les UUIDs des co-organisateurs.

## Decision

L'endpoint reste **`@PermitAll`**. Aucune annotation `@Internal` n'est ajoutée.
~~Aucune route Kong-strip n'est posée.~~ **(OBSOLÈTE depuis l'addendum 2026-06-04 —
voir plus bas : une route Kong publique `events-organizer-uuids` a été ajoutée quand
le frontend `usePublicOrganizers` est devenu consommateur de l'endpoint.)**

## Why this is necessary

- **Couplage de protection unique inutile.** Le consommateur (engagement-service)
  a déjà passé la garde ISSUE-92 + SCRUM-136 via `getById?check-co-org-of=`
  avant d'invoquer `getOrganizerUuids`. La sortie de `getOrganizerUuids` n'est
  exploitable métier que par un caller qui a déjà passé la garde de visibilité
  en amont.
- **Stabilité cross-service.** Marquer cet endpoint `@Internal` couplerait la
  liste des co-organisateurs à la disponibilité du header `X-Internal-Token`
  côté consommateur, alors que la primitive est stable cross-service depuis
  post-consolidation 14→5 (Étape 23.2).
- **Mitigations en place.**
  1. `getOrganizerUuids` filtre les Events `BANNED` (404).
  2. UUIDs retournés non corrélables à des comptes Auth0 sans accès
     `user-service`.
  3. Pagination naturelle borne l'exfiltration (1 event ≤ 10 co-orgs).
  4. Sentinel test (Étape 24.7.4) pin l'invariant « `getOrganizerUuids` ne
     retourne **jamais** les UUIDs si l'Event est `BANNED`, indépendamment du
     caller ».

## Alternatives considered

| Alternative | Rejet |
|---|---|
| Ajouter `@Internal` + Kong strip | Couple deux services par un secret partagé sans bénéfice métier ; le consommateur appelle déjà un endpoint sécurisé en amont. |
| Restreindre à `@RolesAllowed("ADMIN")` | Casse engagement-service qui n'a pas de claim ADMIN sur ses appels REST cross-service. |
| Supprimer l'endpoint et inliner la primitive dans `getById` | Coût de refactor élevé sans bénéfice de sécurité (l'info partirait quand même par le wire). |

## When to revisit

- Si engagement-service cesse d'être le seul consommateur (ex. SCRUM-99
  notification-service) ; alors évaluer si le nouveau consommateur passe aussi
  par la garde ISSUE-92 amont.
- Si une nouvelle classe de pentest émerge où l'énumération des UUIDs des
  co-organisateurs devient un risque démontré (ex. social engineering ciblé
  par UUID).

## Consequences

- **Test sentinel obligatoire.** Étape 24.7.4 pin l'invariant filtre BANNED.
  Toute régression future qui retournerait les UUIDs sur un Event BANNED
  casse le test.
- **Documentation interne-endpoints.** `backend/docs/internal-endpoints.md`
  référence ADR-002 dans la sous-section « `GET /events/{id}/organizer-uuids` »
  (mise à jour Étape 24.9.16).

## Addendum 2026-06-04 — consommateur public ajouté → route Kong + openapi

La condition « When to revisit » #1 (« si engagement-service cesse d'être le seul
consommateur ») est désormais remplie : le **frontend** consomme l'endpoint via
`usePublicOrganizers` pour afficher la section « Équipe organisatrice » de la page
publique d'un événement (créateur + co-organisateurs `ACCEPTED`, y compris pour les
visiteurs anonymes).

Décision maintenue : l'endpoint **reste `@PermitAll`** (mêmes mitigations — filtre
BANNED, UUIDs non corrélables sans user-service, équipe organisatrice destinée à
être publique). En revanche, la phrase « Aucune route Kong-strip n'est posée » est
**révisée** — une route Kong publique est désormais nécessaire, sinon le frontend
reçoit `404 no Route matched` (bug observé en prod : l'équipe organisatrice
n'affichait que le créateur). Conséquences :

- Route Kong `events-organizer-uuids` (`~/api/events/(?:\d+)/organizer-uuids$`)
  ajoutée à `docker/kong.yml`, `helm/templates/kong/configmap-routes.yaml` et
  `k8s/templates/kong/configmap-routes.yaml`.
- Endpoint déclaré dans `openapi/openapi.yaml` (contrat public).
- La note de recon pentest « `organizer-uuids` → Kong 404 no Route matched » ne
  tient plus : l'exposition publique est désormais intentionnelle (l'équipe
  organisatrice est de toute façon affichée publiquement sur la page événement).
