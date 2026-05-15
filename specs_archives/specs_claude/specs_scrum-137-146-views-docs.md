# SCRUM-137 + SCRUM-146 + Backend view anonymes/dedup + Refresh documentation post-PR #158

| Champ | Valeur |
|---|---|
| Tickets Jira | [SCRUM-137](https://pinfo-groupe6.atlassian.net/browse/SCRUM-137) (3 SP, FRONT co-organisateurs UI) + [SCRUM-146](https://pinfo-groupe6.atlassian.net/browse/SCRUM-146) (5 SP, FRONT commentaires UI) + 2 axes hors-Jira (backend view + docs refresh) |
| Sprint | S8 (calendrier produit) — la PR #158 (refactor microservices) est mergée, on reprend le développement fonctionnel + dette doc |
| Épics | SCRUM-14 (gouvernance event — co-organisateurs) + SCRUM-16 (interactions communautaires — commentaires) |
| Stories | SCRUM-118 (US-29, co-organisateurs) + SCRUM-111 (US-22, commentaires) |
| Story Points | 8 SP (3 + 5) + ~5 SP non-Jira pour view + docs |
| Branche | `feature/scrum-137-146-doc-and-views` |
| Base | `origin/main` à HEAD `a85da460 Delete localhost_requests_failed.har [skip ci]` (PR #158 mergée à `ad6d422f`) |
| Auteur spec | Elie Bussod (rédaction assistée Claude Opus 4.7) |
| Date | 2026-05-14 |
| PR de référence | `feat(scrum-137): co-organizers UI + comments section + anonymous event views + post-PR158 docs refresh` |
| Mode de travail | **Une seule branche, une seule PR, livrée en pleine autonomie** jusqu'à ouverture PR + boucle CI/review Copilot. Elie merge lui-même. |
| Règle d'or `openapi-first` | **APPLICABLE — Axe 4 modifie `/events/{id}/view`** (sécurité + dédoublonnage + suppression du doublon de path actuel). Modifier [`openapi/openapi.yaml`](openapi/openapi.yaml) AVANT toute ligne de code Java. SCRUM-137 et SCRUM-146 consomment des endpoints **déjà figés** dans l'OpenAPI — aucun changement contractuel public. |

> **Contexte post-merge PR #158.** La migration vers microservices est livrée (5 services + 10 shared libs + Kong + Kafka + DB-per-service + Postgres dédié par service). Plusieurs commits post-merge ont fait dériver la documentation : DB-per-service (`f4b5968e`), notification-service `replicas:1` (idem), refactor module layout `services/shared-*` → `shared/<lib>` (`fab270e0`), drop de `contract-tests`/`e2e` du reactor. Cette spec acte ces changements dans la doc en première étape, puis livre les 3 implémentations.

---

## 1. Objectifs & non-objectifs

### Objectifs

- **Axe 1 — Documentation refresh post-PR #158.** Aligner backend + frontend docs sur la topologie réelle livrée (DB-per-service, notification actif, 15 modules leaf au lieu de 17, routes frontend manquantes, services frontend manquants, OpenAPI duplicate à corriger).
- **Axe 2 — SCRUM-137 (frontend co-organisateurs).** Livrer l'UI complète : section dans `EventForm` (édition uniquement), section "Équipe organisatrice" dans `EventDetailPage`, badge invitations dans `Navbar` + page d'invitations.
- **Axe 3 — SCRUM-146 (frontend commentaires).** Livrer la section commentaires dans `EventDetailPage` : liste + formulaire + replies 1 niveau + suppression + signalement de commentaire (lien vers modale existante si compat, sinon scope-réduit).
- **Axe 4 — Backend view anonyme + dédoublonnage anti-spam.** Le compteur de vues doit incrémenter aussi pour un utilisateur non connecté. Ajouter une dédup `session_id` côté anonyme. Dédupliquer `/events/{id}/view` dans `openapi.yaml`. Mettre à jour `statsApi.ts` côté frontend.
- **Tests.** Tous les nouveaux composants/hooks/services frontend ont une couverture ≥ 80 % lignes. Backend: tests unitaires de service + tests resource pour `EventViewService` étendu + `EventViewResource` anonyme.
- **Skeletons.** Tout composant frontend avec état `loading` reçoit son `.bones.json` (cf. AGENTS frontend règle non négociable).
- **Sprint context.** Mise à jour finale de `backend/docs/sprint-context.md` + `frontend/docs/sprint-context.md` avec une section datée `2026-05-14` qui résume la livraison.

### Non-objectifs

- **Pas d'endpoint backend `GET /users/search`** — explicitement listé dans le backlog SCRUM-137 mais **n'existe pas** côté backend actuellement. Décision A ci-dessous tranche le fallback (invitation par UUID, pas par recherche libre).
- **Pas de notifications Kafka pour co-organizers ou comments** — déjà câblées (events.co-organizers.invited/accepted, comments.created) côté backend ; le frontend ne consomme rien de Kafka.
- **Pas de likes ni de mentions de commentaires** — SCRUM-144 / SCRUM-145, S9+.
- **Pas de modification de la migration `V3__create_event_views.sql`** — immutabilité Flyway. Une migration **additive** `V4` (ou suivante disponible) sera créée.
- **Pas d'audit RGPD complet** — la spec rappelle les bonnes pratiques (pas d'IP brute, opt-in implicite via session UUID) mais ne ré-audite pas `legal/privacy`.
- **Pas de refonte de `EventDetailPage`** — uniquement insertion de la section commentaires + section équipe organisatrice. Le reste de la page reste tel quel.
- **Pas de merge** de la PR. Elie merge lui-même.
- **Pas de port vers le frontend** d'un mécanisme de fingerprinting avancé (Canvas/WebGL). Le `session_id` UUID v4 en `localStorage` est volontairement basique.

---

## 2. Inventaire des changements

### Fichiers documentation (Axe 1)

| Fichier | Type de changement | Motif |
|---|---|---|
| [`AGENTS.md`](AGENTS.md) (racine) | Update ligne 12 | Layout Maven : `15 modules dans le reactor` au lieu de `17` ; supprimer mentions `contract-tests` et `e2e` ; corriger la liste shared libs avec préfixe `shared-` (qui sont sous `backend/shared/<lib>`, pas `backend/services/shared-*`) |
| [`backend/AGENTS.md`](backend/AGENTS.md) | Réécriture sections « Layout Maven (post-finalisation) » + « Maintenance de la documentation » | Idem racine + ajout des Postgres dédiés par service + statut notification actif |
| [`backend/docs/architecture.md`](backend/docs/architecture.md) | Réécriture section « Topologie microservices » + table k8s | 5 Postgres dédiés (`postgres-event`, `postgres-user`, `postgres-engagement`, `postgres-moderation`, `postgres-notification`) ; notification-service `replicas: 1` ; suppression du « schéma `public` partagé » |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Update colonne « DB / Service propriétaire » | Migration de « schéma `public` partagé » → « DB dédiée par service » avec table → DB mapping. Conserver tous les sprints précédents inchangés. |
| [`backend/docs/dev-guide.md`](backend/docs/dev-guide.md) | Section « Layout multi-module » + commandes | 15 modules leaf : 5 services + 10 shared libs sous `backend/shared/` ; commande `./mvnw verify` mise à jour |
| [`backend/docs/devops-handoff.md`](backend/docs/devops-handoff.md) | Note de mise à jour | Notification-service actif ; DB-per-service livré (suppression item « DB-per-schema S9+ ») |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Ajout section finale « 2026-05-14 — État post-merge PR #158 & SCRUM-137/146 » | Ne pas supprimer historique. Résumé topologie stable + livraison de cette PR |
| [`frontend/AGENTS.md`](frontend/AGENTS.md) | Update table « Skeletons existants » | Ajouter `comments`, `co-organizers-section`, `co-organizer-invitations` (selon décision F) |
| [`frontend/docs/architecture.md`](frontend/docs/architecture.md) | Compléter table de routage + table services | Routes manquantes : `/admin`, `/events/:id/stats`, `/403`. Services manquants : `adminApi`, `attendanceApi`, `attendeesApi`, `reportApi`, `statsApi`, `commentApi` (nouveau), `coOrganizerApi` (nouveau) |
| [`frontend/docs/components.md`](frontend/docs/components.md) | Déduplication + ajouts | Retirer le doublon `eventApi` (sections lignes ~582-599) ; documenter nouveaux composants : `CommentSection`, `CommentItem`, `CommentForm`, `CoOrganizersEditor`, `EventOrganizerTeam`, `CoOrganizerInvitationsBadge`, `CoOrganizerInvitationsList` |
| [`frontend/docs/types.md`](frontend/docs/types.md) | Update | Ajouter types `Comment`, `CommentDTO`, `CoOrganizer`, `CoOrganizerInvitation`, `CoOrganizerStatus` |
| [`frontend/docs/sprint-context.md`](frontend/docs/sprint-context.md) | Section finale datée | Idem backend |

### Fichiers OpenAPI (Axe 4)

| Fichier | Changement | Motif |
|---|---|---|
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | (a) Supprimer la **2e** déclaration de `/events/{id}/view` (lignes 3560-3585, doublon hérité). (b) Mettre à jour la **1ère** déclaration (lignes 3482-3517) : `security: []` (au lieu de `BearerAuth`), retirer `401`, body optionnel `RecordViewRequest { sessionId?: uuid }`, ajouter `429` (rate-limit anonyme). | Vue anonyme + dédup session. **Cette modif viole l'invariant « `git diff openapi/` = 0 ligne ABSOLU »** posé par la Décision G de la spec finalization — **acté explicitement** dans la Décision C ci-dessous (l'invariant ne s'appliquait qu'au périmètre PR #158). |

### Fichiers backend (Axe 4)

| Fichier | Type | Motif |
|---|---|---|
| `backend/services/event-service/src/main/resources/db/migration/V11__add_event_views_session.sql` (nouveau) | Migration Flyway additive | Ajouter `session_id UUID NULL`, faire `user_id UUID NULL`, ajouter `CONSTRAINT uq_event_view_event_session UNIQUE (event_id, session_id)`, conserver `uq_event_view_user_event` existante mais la rendre `WHERE user_id IS NOT NULL` (partial unique index) |
| `EventView.java` | Update entité | Champ `sessionId: UUID` nullable, `userId: UUID` nullable |
| `EventViewService.java` | Refactor | Méthode `recordView(@Nullable String auth0Id, Long eventId, @Nullable UUID sessionId)` — branches authenticated / anonymous, UPSERT per branch |
| `EventViewResource.java` | Refactor | Retrait `@Authenticated` → `@PermitAll` ; accepte body optionnel `RecordViewRequest`. Anti-abus naïf : rate-limit `@PerUserRateLimit` ne s'applique qu'aux authentifiés ; ajouter une dédup applicative best-effort (`@RolesAllowed` non requis). |
| `RecordViewRequest.java` (nouveau) | DTO record | `record RecordViewRequest(UUID sessionId) {}` — body POST optionnel |
| `EventViewServiceTest.java` | Update + ajouts | Tests cas authentifié, anonyme avec sessionId, anonyme sans sessionId (silent skip), event introuvable, double appel idempotent |
| `EventViewResourceTest.java` | Update + ajouts | Tests `@TestSecurity` désactivé pour anonyme, 204 No-Content sur both branches, 404 si event absent |

### Fichiers frontend — SCRUM-137 (Axe 2)

| Fichier | Type | Motif |
|---|---|---|
| `frontend/src/types/coOrganizer.ts` (nouveau) | Types | `CoOrganizer`, `CoOrganizerInvitation`, `CoOrganizerStatus = 'PENDING' \| 'ACCEPTED'` (DECLINED transitoire, jamais retourné) |
| `frontend/src/services/coOrganizerApi.ts` (nouveau) | Service Axios | `inviteCoOrganizer(eventId, userId)`, `listCoOrganizers(eventId)`, `removeCoOrganizer(eventId, userId)`, `acceptInvitation(eventId)`, `declineInvitation(eventId)`, `getMyInvitations(status?, page?, size?)` |
| `frontend/src/hooks/useCoOrganizers.ts` (nouveau) | Hook | Gère load + invite + remove ; expose `{ coOrganizers, loading, error, invite, remove, refresh }` |
| `frontend/src/hooks/useCoOrganizerInvitations.ts` (nouveau) | Hook | Charge `getMyInvitations()` ; expose `{ invitations, pendingCount, loading, accept, decline, refresh }` ; **refresh automatique au montage et après mutation** |
| `frontend/src/components/event/CoOrganizersEditor.tsx` (nouveau) | Composant | Section "Co-organisateurs" dans `EventForm` édition. Pas de search libre (cf. Décision A) : champ **UUID coller-ou-tape** + validation `uuid` regex + bouton "Inviter". Liste des co-orgs avec chip statut + bouton ×. |
| `frontend/src/components/event/EventOrganizerTeam.tsx` (nouveau) | Composant | "Équipe organisatrice" dans `EventDetailPage`. Affiche créateur + co-orgs `ACCEPTED` (avatar, displayName, lien profil). Loading skeleton inline. |
| `frontend/src/components/user/CoOrganizerInvitationsBadge.tsx` (nouveau) | Composant | Petit badge rouge avec compteur dans `Navbar` (dropdown user). Cliquable → ouvre modale ou navigue vers page invitations. |
| `frontend/src/components/user/CoOrganizerInvitationsList.tsx` (nouveau) | Composant | Liste des invitations PENDING avec card par invitation (event + bouton Accepter / Décliner). Réutilisé dans `ProfilePage` (self-view) **et** dropdown navbar. |
| `frontend/src/pages/event/EventEditPage.tsx` | Modif | Monte `CoOrganizersEditor` **seulement** si `eventId` existe (mode édition). Passage de `eventId` en prop. |
| `frontend/src/components/event/EventForm.tsx` | Modif minimale | Ajout d'un slot optionnel `coOrganizersSection?: React.ReactNode` rendu en bas du formulaire édition. (Préserve `EventCreatePage` qui n'a pas d'eventId.) |
| `frontend/src/pages/event/EventDetailPage.tsx` | Modif | Insertion de `<EventOrganizerTeam eventId={id} creator={event.creator} />` dans la sidebar, avant ou après `EventStatsPanel` |
| `frontend/src/pages/profile/ProfilePage.tsx` | Modif | Si `isOwnProfile`, monte `<CoOrganizerInvitationsList />` (collapse ou section sticky) |
| `frontend/src/components/Navbar.tsx` | Modif | Dans le dropdown user, monte `<CoOrganizerInvitationsBadge />` près de "Mes événements" |
| `frontend/src/bones/co-organizers-section.bones.json` (nouveau) | Skeleton | Bones pour `CoOrganizersEditor` (édition only) |
| `frontend/src/bones/event-organizer-team.bones.json` (nouveau) | Skeleton | Bones pour `EventOrganizerTeam` (sidebar `EventDetailPage`) |
| `frontend/src/bones/co-organizer-invitations.bones.json` (nouveau) | Skeleton | Bones pour `CoOrganizerInvitationsList` |

Tests : 1 fichier `.test.tsx` par composant et hook créé, suivant le pattern existant.

### Fichiers frontend — SCRUM-146 (Axe 3)

| Fichier | Type | Motif |
|---|---|---|
| `frontend/src/types/comment.ts` (nouveau) | Types | `Comment` (alias `CommentDTO`), `CommentReply`, `CreateCommentRequest` |
| `frontend/src/services/commentApi.ts` (nouveau) | Service | `getEventComments(eventId, page?, size?)` → `Comment[]`, `postComment(eventId, content, parentCommentId?)` → `Comment`, `deleteComment(commentId)` → `void` |
| `frontend/src/hooks/useComments.ts` (nouveau) | Hook | Charge + paginate + optimistic post + optimistic delete. **Pas de TanStack Query** (le projet n'en utilise pas) — on suit le pattern manuel `useFavorite`/`useAttendance`. Expose `{ comments, hasMore, loading, error, post, postReply, remove, loadMore }` |
| `frontend/src/components/event/CommentSection.tsx` (nouveau) | Composant principal | Wrapper `SectionWrapper` size `lg`. Header + form + liste. État vide : "Aucun commentaire — soyez le premier." Loading : skeleton `comments`. |
| `frontend/src/components/event/CommentItem.tsx` (nouveau) | Composant | Card commentaire : avatar + displayName + badge "Organisateur" (si `authorIsOrganizer`) + contenu + date relative + actions (Répondre, Supprimer si auteur/organisateur, Signaler). Replies imbriquées max 1 niveau (rendu récursif limité à 1 ply). |
| `frontend/src/components/event/CommentForm.tsx` (nouveau) | Composant | `<textarea>` (max 2000) + compteur live + bouton Envoyer. Masqué pour anonyme avec message "Connectez-vous pour commenter" + lien vers `/login`. Optimistic submit. |
| `frontend/src/components/event/ReportCommentModal.tsx` (nouveau, **conditionnel**) | Composant | Cf. Décision E : on **scope-réduit** le signalement de commentaire à un toast "Bientôt disponible" pointant vers SCRUM-144 (likes/report comment S9). On **ne crée pas** la modale ici. Le bouton "Signaler" est présent mais ouvre uniquement un toast informatif. |
| `frontend/src/pages/event/EventDetailPage.tsx` | Modif | Ajout de `<CommentSection eventId={id} eventStatus={event.status} />` après le bloc "Informations complémentaires" (ou avant le footer de page). |
| `frontend/src/utils/relativeDate.ts` (nouveau si manquant) | Util | `formatRelativeDate(date)` → "il y a 2 h", "hier", etc. **À vérifier d'abord** si déjà présent dans `dateTime.ts` — si oui, réutiliser. |
| `frontend/src/bones/comments.bones.json` (nouveau) | Skeleton | Bones pour `CommentSection` (liste paginée) |

Tests : 1 fichier `.test.tsx` par composant et hook créé.

### Fichiers frontend — Axe 4 (vue anonyme)

| Fichier | Type | Motif |
|---|---|---|
| `frontend/src/services/sessionId.ts` (nouveau) | Util | `getOrCreateSessionId(): UUID` — lit `localStorage` clé `unige_session_id`, crée + persiste un UUID v4 sinon. Fonction pure (sauf side-effect storage). |
| `frontend/src/services/statsApi.ts` | Modif | `recordEventView(eventId)` → envoie `{ sessionId }` en body. Plus de guard `if (user) skip` côté caller. |
| `frontend/src/pages/event/EventDetailPage.tsx` | Modif | Le `useEffect` qui call `recordEventView` ne dépend plus de `user` — call inconditionnel au montage avec event chargé. |
| `frontend/src/__tests__/services/sessionId.test.ts` (nouveau) | Tests | UUID persistant, idempotent, format validé |
| `frontend/src/__tests__/services/statsApi.test.ts` (update) | Tests | Body envoyé contient `sessionId`, fallback erreur réseau silencieux |
| `frontend/src/__tests__/pages/event/EventDetailPage.test.tsx` (update) | Tests | View enregistrée même pour user anonyme |

---

## 3. Décisions techniques tranchées

> **Règle.** Une fois la spec validée par Elie, ces décisions ne se rediscutent pas pendant l'implémentation. Toute déviation doit être documentée dans `sprint-context.md` à la livraison.

### Décision A — SCRUM-137 : invitation par **UUID** au lieu de search libre (`GET /users/search` n'existe pas)

**Constat.** Le backlog SCRUM-137 (frontend) référence `GET /api/users/search?q=` pour le champ de recherche utilisateur. **Cet endpoint n'existe pas côté backend** (vérification : `grep -rn "search" backend/services/user-service/src/main/java` = 0 match REST endpoint correspondant ; `grep "/users/search" openapi/openapi.yaml` = 0 match).

**Décision.** L'UI livrée propose une **invitation par UUID** : l'utilisateur principal colle l'UUID du futur co-organisateur dans un champ texte (validation regex côté client + bouton "Inviter"). Le backend valide l'existence via `POST /api/events/{id}/co-organizers` qui retourne `404 user_not_found` si l'UUID est invalide. Le frontend mappe ce 404 sur un message d'erreur clair.

**Justification.** (1) Backend `GET /users/search` = S9+ minimum, hors scope. (2) UUID est trivialement partageable (bouton "copier mon UUID" futur sur `ProfilePage`). (3) Évite un endpoint exposé qui ferait fuiter la liste des utilisateurs (RGPD + anti-harvest, cf. pentest 4.1b). (4) Pattern utilisé dans le backend existant : `InviteCoOrganizerRequest` prend déjà un `userId: UUID`.

**Follow-up** documenté dans `frontend/docs/backlog_s5_s10.md` (à compléter à la livraison de cette PR) : « S9+ — endpoint `GET /users/search?q=` rate-limité + autocomplete côté front pour remplacer le champ UUID ». Pas dans cette PR.

| Option | Verdict |
|---|---|
| (a) Inviter par UUID (avec validation client + 404 mapping) | ✅ retenu |
| (b) Inviter par email | ❌ pas d'endpoint backend équivalent ; ouvre un canal d'enrôlement |
| (c) Bloquer la PR en attendant `GET /users/search` | ❌ trop long, contraint à split en 2 PRs |
| (d) Reverse-engineering : appeler `GET /users/{uuid}` à la frappe et valider visuellement | ❌ besoin d'autocomplete typeahead, complexe |

### Décision B — SCRUM-146 : signalement de commentaire scope-réduit

**Constat.** Le backlog SCRUM-146 mentionne un bouton "Signaler" sur chaque commentaire. Côté backend, `POST /api/comments/{id}/report` **n'existe pas** (vérification : `grep "comments.*report" openapi/openapi.yaml` = 0 match). Le ticket SCRUM-144 (likes + report comment) est S9+.

**Décision.** Le bouton "Signaler" est **présent visuellement** sur `CommentItem` mais ouvre uniquement un `Toast` informatif : *« Le signalement de commentaire arrive bientôt. En attendant, vous pouvez signaler l'événement complet via le bouton ⓘ. »* Pas d'appel API, pas de modale. Le bouton est `disabled` visuellement (opacity-50 + cursor-not-allowed).

**Justification.** (1) Évite de promettre une feature non livrée. (2) Présence visuelle = surface explicite pour SCRUM-144 quand elle débarquera (pas de churn UI). (3) Cohérent avec la pratique du projet : `MyParticipationsPage` a un stub similaire en attendant un endpoint enrichi.

### Décision C — Levée explicite de l'invariant « `git diff openapi/` = 0 ligne ABSOLU »

**Constat.** La Décision G de [`specs_microservices_migration_finalization.md`](specs_microservices_migration_finalization.md) acte que l'invariant `git diff origin/main HEAD -- openapi/ = 0` reste vrai sur la PR #158. Cet invariant **n'a jamais été éternel** : il visait à ne pas mélanger refonte backend et contrat public.

**Décision.** Cette PR **modifie** `openapi/openapi.yaml` (Axe 4) : suppression du doublon `/events/{id}/view`, passage en `security: []` (vue anonyme), ajout de body `RecordViewRequest`. Le diff est mesuré et localisé à ces 2 sections. **C'est désormais autorisé** parce que la migration microservices est terminée et que la PR est explicitement fonctionnelle.

**Justification.** L'invariant servait à figer le contrat pendant le refactor structurel ; il est levé pour les PRs fonctionnelles post-merge. Documenté dans `sprint-context.md` Axe 1.

### Décision D — Vues anonymes : **Option B (session UUID en localStorage)** comme stratégie de dédup primaire

**Options évaluées.**

| Option | Stratégie | Persistance | RGPD | Complexité | Verdict |
|---|---|---|---|---|---|
| A | `(eventId, userId)` pour auth + `(eventId, ipHash + salt)` pour anon, fenêtre temporelle de 30 min | Persistante en DB | OK si salt côté serveur + jamais d'IP brute | Moyenne (HMAC, header X-Forwarded-For) | Reasonable mais lourde |
| **B** | `(eventId, userId)` pour auth + `(eventId, session_id UUID)` pour anon, sessionId stocké localStorage frontend | Persistante en DB + localStorage | RGPD-clean (pas d'IP, pas de fingerprint) | Basse (UUID v4 + insert) | ✅ **retenu** |
| C | `(eventId, hash(ip+ua))` avec fenêtre courte (5 min) | Volatile (cache mémoire) | OK mais hash IP = data personnelle indirecte | Haute (cache distribué, TTL) | Surdimensionné |

**Décision.** Option B. Anti-spam best-effort : un utilisateur en navigation privée peut générer N vues (= N sessions) mais l'effort dépasse la valeur récoltée. Pour les utilisateurs authentifiés, l'`UPSERT ON CONFLICT` existant suffit (uniqueness éternelle par `(event_id, user_id)`). Pour les anonymes, idem avec `(event_id, session_id)`.

**Renforcement** (optionnel, listé en follow-up S9+ dans `sprint-context.md`) : ajouter en ligne 2 de défense un hash glissant `(ipHash, eventId, viewedAt)` avec fenêtre 5 min côté server-side (rejet `429`), si on observe de l'abus en production. Pas dans cette PR.

### Décision E — Schéma `event_views` : nullables + partial unique indexes

**Décision.** La migration `V11` (ou prochain V<N+1> selon état Flyway au moment du checkout) :

```sql
-- V11__add_event_views_session.sql
ALTER TABLE event_views ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE event_views ADD COLUMN session_id UUID NULL;

ALTER TABLE event_views DROP CONSTRAINT uq_event_view_user_event;
CREATE UNIQUE INDEX uq_event_view_user_event
  ON event_views (event_id, user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_event_view_event_session
  ON event_views (event_id, session_id) WHERE session_id IS NOT NULL;

ALTER TABLE event_views ADD CONSTRAINT chk_event_view_user_or_session
  CHECK (user_id IS NOT NULL OR session_id IS NOT NULL);
```

**Justification.** Garde l'index unique existant pour les authentifiés (intact sémantiquement) + ajoute un partial unique pour les anonymes. CHECK constraint empêche les rows orphelines (ni user ni session). Une row a toujours exactement **soit** un user_id **soit** un session_id (jamais les deux — un user connecté n'utilise pas son sessionId, on privilégie l'identité connectée).

> **À vérifier en checkout** : la migration la plus récente livrée dans `backend/services/event-service/src/main/resources/db/migration/` est `V10__add_event_recurrence.sql`. La nouvelle migration doit prendre **V11** (si rien d'autre ne s'est ajouté entre temps — vérifier au démarrage).

### Décision F — Skeletons : 3 nouveaux `.bones.json`

**Décision.** Trois fichiers manuels sous `frontend/src/bones/` :
- `comments.bones.json` — liste de N cards comment avec avatar + 2 lignes texte + footer (date + actions). Builder manuel (pas via `generate.mjs`) : la mise en page est trop variable selon le nombre de replies. Inspiration : profile.bones.json.
- `event-organizer-team.bones.json` — sidebar component : header + N avatars en ligne. Manuel.
- `co-organizer-invitations.bones.json` — liste paginée de cards invitation. Manuel.

**Justification.** Les 3 layouts sont simples (pas de grille auto-fit), le mode manuel est plus rapide et plus contrôlable. La table `Skeletons existants` dans `frontend/AGENTS.md` reçoit 3 entrées.

### Décision G — Versioning de la doc : **ajout** + section datée, pas réécriture

**Décision.** Toutes les docs touchées en Axe 1 **conservent leur historique** (sprints passés, décisions actées). Les changements prennent la forme :
- Soit un **bloc « Mise à jour 2026-05-14 »** en tête du fichier (à la suite du titre H1) qui résume les inflexions.
- Soit une **section finale datée** pour les `sprint-context.md`.
- Soit des **éditions in-place chirurgicales** des lignes obsolètes (tableaux de routage, listes de modules, etc.) **avec note inline** quand le changement est non trivial.

**Justification.** Le projet considère `sprint-context.md` comme un journal historique (cf. les ~1400 lignes existantes). On préserve la traçabilité. La pratique du "Mise à jour" en tête est cohérente avec `architecture.md` qui contient déjà ce pattern.

### Décision H — Leftovers `backend/services/shared-*/` : **suppression dans cette PR**

**Constat.** Le refactor `fab270e0` a déplacé les shared libs depuis `backend/services/shared-<lib>/` vers `backend/shared/<lib>/`, mais a laissé les **répertoires vides** (sans `pom.xml`, juste `src/main/resources/META-INF` vide + `target/`). Le reactor ne les référence plus.

**Décision.** `git rm -r backend/services/shared-*/` dans cette PR (en commit dédié `chore(backend): remove empty leftover shared-* directories under services/`). Vérifier qu'aucun script CI ou Maven n'y fait référence avant.

**Justification.** Garder du code mort augmente la confusion (un nouvel agent peut croire que le layout est `services/shared-*`). La suppression est triviale et chirurgicale.

### Décision I — Workflow Git : commits atomiques par étape, **pas** de squash

**Décision.** Chaque étape majeure du Plan d'exécution donne lieu à **1 commit (parfois 2)** :

```
docs(backend): refresh post-PR158 — DB-per-service, notification active, 15 modules
docs(frontend): refresh post-PR158 — routes admin/stats/403, services adminApi/statsApi
chore(backend): remove empty leftover shared-* directories under services/
feat(scrum-137): add coOrganizerApi service + types + hooks
feat(scrum-137): add CoOrganizersEditor in EventForm edit mode
feat(scrum-137): add EventOrganizerTeam in EventDetailPage
feat(scrum-137): add CoOrganizerInvitationsBadge + List in Navbar/ProfilePage
feat(scrum-146): add commentApi service + types + useComments hook
feat(scrum-146): add CommentSection + CommentForm + CommentItem with replies
feat(scrum-146): integrate CommentSection in EventDetailPage
feat(scrum-X): support anonymous event views via session UUID
docs(openapi): allow anonymous /events/{id}/view + optional sessionId body
test(frontend): coverage for new hooks/services/components
docs(backend): sprint-context.md — 2026-05-14 entry
docs(frontend): sprint-context.md — 2026-05-14 entry
```

**Justification.** Lisibilité du `git log` pour Elie (qui review chaque commit) ; débogage post-merge ; respecte la convention `<type>(<scope>): <desc>` validée par CI `pr-title-check.yml`.

### Décision J — Type/scope de la PR

**Titre PR :** `feat(scrum-137): co-organizers UI, comments section, anonymous views and post-PR158 docs refresh`

**Justification.** Convention CI : `feat` exige un scope `scrum-XXX`. SCRUM-137 a plus de SP que SCRUM-146 (3 vs 5 — corrigé, donc SCRUM-146 a plus), mais SCRUM-137 est plus en amont (co-organizers backend déjà livré depuis SCRUM-136). On retient SCRUM-137 comme scope canonique. La description PR cite les 2 tickets en gras.

| Option | Verdict |
|---|---|
| `feat(scrum-137): ...` | ✅ retenu |
| `feat(scrum-146): ...` | acceptable mais moins central |
| `chore(backend): ...` | ❌ ne reflète pas la livraison frontend majeure |

### Décision K — Code review autonome via skill `pr-review-toolkit`

**Décision.** Une fois la PR ouverte (Étape 9), l'agent lance immédiatement le skill `pr-review-toolkit` sur la PR (multi-agent code review en parallèle pendant que la CI tourne). Les findings non triviaux sont adressés dans des commits suffix `fix(scrum-137): apply review — <finding>`.

**Critères d'arrêt :** 0 BLOQUANT, 0 IMPORTANT non clos. MINEURS adressables en post-merge.

### Décision L — Boucle Copilot review : itérative jusqu'à 0 finding bloquant

**Décision.** Après push initial, Copilot review s'exécute automatiquement sur la PR. L'agent :
1. Attend que les commentaires Copilot apparaissent (`gh api repos/.../pulls/<n>/comments` + `gh pr view --comments`).
2. Adresse chaque commentaire dans un commit `fix(scrum-137): apply Copilot review — <résumé>`.
3. Pousse, attend la nouvelle review.
4. Boucle jusqu'à 0 commentaire bloquant **OU** 3 itérations maximum (au-delà, l'agent reporte à Elie).

---

## 4. Plan d'exécution séquentiel

### Étape 0 — Préparation

- **0.1** `git checkout main && git pull origin main` (s'assurer du HEAD `a85da460` ou plus récent).
- **0.2** `git checkout -b feature/scrum-137-146-doc-and-views`.
- **0.3** Lire la spec en entier. Vérifier que toute hypothèse de la spec tient encore (notamment : numéro de migration Flyway disponible, état OpenAPI, présence des endpoints backend co-organizers + comments).

### Étape 1 — Documentation backend refresh (Axe 1)

**1.1** `backend/AGENTS.md` : remplacer ligne 12 (« 17 modules ») par « 15 modules leaf » + retirer mentions `contract-tests` / `e2e` / `services/shared-*`. Mettre à jour la table « Layout Maven » ligne 8 à 18 : décrire `backend/shared/<lib>` (10 modules) + `backend/services/<svc>-service` (5 modules) + 2 aggregator POMs (`backend/shared/pom.xml`, `backend/services/pom.xml`).

**1.2** `AGENTS.md` racine : ligne 12 idem.

**1.3** `backend/docs/architecture.md` : réécrire section « Vue d'ensemble — topologie microservices » (lignes 17-30). Nouvelle topologie K8s : 5 Postgres `postgres-<service>` au lieu d'un partagé. Notification-service `replicas: 1`. Mettre à jour le diagramme texte « Flux de trafic ».

**1.4** `backend/docs/data-model.md` : ajouter colonne « DB physique » dans les tables par-entité, ou ajouter une section « Mapping entité → DB physique » en tête. Format : `User → postgres-user`, `Event/EventView/Favorite/EventCoOrganizer → postgres-event`, etc.

**1.5** `backend/docs/dev-guide.md` : section « Layout Maven » → 15 modules. Commande `./mvnw verify` : compter ~3 minutes maintenant. Section « DevServices » : noter que chaque service spawn son propre Postgres éphémère.

**1.6** `backend/docs/devops-handoff.md` : retirer item « DB-per-schema S9+ » ou le marquer ✅ livré le 2026-05-13. Notification-service S9+ → ✅ actif depuis `f4b5968e`.

**1.7** `backend/docs/sprint-context.md` : ajouter en **tête de fichier** une section :

```markdown
## 2026-05-14 — Post-merge PR #158 : state stable + reprise développement

PR #158 mergée à `ad6d422f` (2026-05-13). Suivie de fixes infra:
- `f4b5968e` — DB-per-service (5 Postgres dédiés).
- `60991692` — memory limits 512Mi.
- `dd8ca635` — outbox sequence + event-service OOM.
- `89caac11` — caller identity via user service.

Topologie stable :
- 5 microservices Quarkus actifs (notification S9+ ACTIVÉ depuis `f4b5968e`).
- 10 shared libs sous `backend/shared/<lib>` (refactor `fab270e0`).
- 15 modules leaf dans le reactor (drop `contract-tests` + `e2e`).
- 5 Postgres dédiés.

Reprise développement fonctionnel : SCRUM-137, SCRUM-146, fix vue anonyme.
```

**Commit Étape 1** : `docs(backend): refresh post-PR158 — DB-per-service, notification active, 15 modules`.

### Étape 2 — Documentation frontend refresh (Axe 1)

**2.1** `frontend/docs/architecture.md` : compléter table de routage (lignes 14-37) — ajouter `/events/:id/stats`, `/admin` (AdminRoute), `/403` (ForbiddenPage). Compléter table services (lignes 43-50) avec 5 services manquants : `adminApi`, `attendanceApi`, `attendeesApi`, `reportApi`, `statsApi`.

**2.2** `frontend/docs/components.md` : retirer le doublon de `eventApi` (sections lignes ~582-599 et ~592+ qui sont en double).

**2.3** `frontend/docs/types.md` : vérifier que `Report`, `ReportReason`, `EventStats`, etc. sont listés. Ajouter notes manquantes éventuelles.

**2.4** `frontend/docs/sprint-context.md` : ajouter section en **tête** :

```markdown
## 2026-05-14 — Post-merge PR #158 : reprise développement front

Backend migration microservices mergée (cf. backend/docs/sprint-context.md).
Aucun impact frontend (invariant `git diff frontend/` = 0 sur PR #158).

Cette PR couvre :
- SCRUM-137 — UI co-organisateurs (EventForm + EventDetailPage + Navbar).
- SCRUM-146 — Section commentaires dans EventDetailPage.
- Backend fix : vues anonymes + dédup par session UUID.
- Documentation post-PR #158.
```

**Commit Étape 2** : `docs(frontend): refresh post-PR158 — routes admin/stats/403, services adminApi/statsApi`.

### Étape 3 — Suppression leftovers `services/shared-*/`

**3.1** `git rm -r backend/services/shared-*/`.
**3.2** Vérifier : `grep -r "services/shared-" .github/workflows/ backend/pom.xml` = 0 match (sinon corriger).
**3.3** `./mvnw verify -DskipTests -q` à la racine `backend/` pour confirmer que la build ne dépend de rien dans ces dirs.

**Commit Étape 3** : `chore(backend): remove empty leftover shared-* directories under services/`.

### Étape 4 — OpenAPI update pour `/events/{id}/view` (Axe 4)

**4.1** Ouvrir `openapi/openapi.yaml`. Identifier les **deux** déclarations actuelles de `/events/{id}/view` (lignes ~3482 et ~3560).
**4.2** **Supprimer** la 2e (lignes 3560-3585) — c'est un doublon hérité.
**4.3** **Mettre à jour** la 1ère :

```yaml
/events/{id}/view:
  post:
    summary: Enregistrer une vue d'un événement (anonyme ou authentifié)
    description: |
      Appelé depuis le frontend à l'ouverture de la page détail. Idempotent.
      - Authentifié : déduplication par `(eventId, userId)`.
      - Anonyme : déduplication par `(eventId, sessionId)` — le frontend
        envoie un UUID v4 stable persisté en localStorage.
      Body optionnel. Si ni JWT ni sessionId fourni, l'appel est ignoré
      silencieusement (204).
    operationId: recordEventView
    tags: [events]
    security: []  # auth optionnelle
    parameters:
      - name: id
        in: path
        required: true
        schema: { type: integer, format: int64 }
    requestBody:
      required: false
      content:
        application/json:
          schema: { $ref: '#/components/schemas/RecordViewRequest' }
    responses:
      '204':
        description: Vue enregistrée (ou silencieusement ignorée si dédupliquée).
      '404':
        description: Événement introuvable.
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ApiErrorResponse' }
      '429':
        description: Trop de requêtes (rate-limit anonyme).
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ApiErrorResponse' }
```

**4.4** Ajouter le schéma `RecordViewRequest` dans `components.schemas` (en cherchant un emplacement alphabétique cohérent) :

```yaml
RecordViewRequest:
  type: object
  description: |
    Body optionnel pour `POST /events/{id}/view`. Permet de fournir un
    identifiant de session anonyme pour la déduplication. Ignoré si JWT
    présent (l'identité user prime).
  properties:
    sessionId:
      type: string
      format: uuid
      nullable: true
      description: UUID v4 généré côté client, persisté en localStorage.
```

**Commit Étape 4** : `docs(openapi): allow anonymous /events/{id}/view + optional sessionId body`.

### Étape 5 — Backend : view anonyme + dédup (Axe 4)

**5.1** Créer `backend/services/event-service/src/main/resources/db/migration/V11__add_event_views_session.sql` (cf. Décision E pour le SQL exact). **Vérifier d'abord** que `V11` est libre — sinon prendre le prochain disponible.

**5.2** Mettre à jour `EventView.java` : `userId` et `sessionId` nullables. Ajouter `@Column(name = "session_id")`.

**5.3** Mettre à jour `EventViewService.java` :

```java
@Transactional
public void recordView(@Nullable String auth0Id, Long eventId, @Nullable UUID sessionId) {
    if (entityManager.find(Event.class, eventId) == null) {
        throw new NotFoundException("Event not found");
    }
    if (auth0Id != null) {
        UUID userId = callerIdentity.requireUuid();  // au besoin getUuid() si null-safe
        upsertAuthenticated(eventId, userId);
    } else if (sessionId != null) {
        upsertAnonymous(eventId, sessionId);
    }
    // else: silent skip (204)
}
```

L'upsert authenticated reste tel quel mais avec `WHERE user_id = :userId` dans ON CONFLICT target (utiliser le partial unique index). L'upsert anonymous fait `ON CONFLICT (event_id, session_id)`.

**5.4** Créer `RecordViewRequest.java` (record).

**5.5** Mettre à jour `EventViewResource.java` :
- Retirer `@Authenticated`. Ajouter `@PermitAll`.
- Accepter `RecordViewRequest body` (optionnel, `@Nullable`).
- Récupérer `auth0Id` via `identity.isAnonymous() ? null : identity.getPrincipal().getName()`.
- Récupérer `sessionId = body != null ? body.sessionId() : null`.
- Appeler `service.recordView(auth0Id, eventId, sessionId)`.

**5.6** Ajouter tests : `EventViewServiceTest` (5+ cases), `EventViewResourceTest` (3+ cases).

**5.7** Build local : `./mvnw -pl services/event-service -am verify -DskipITs`.

**Commit Étape 5** : `feat(scrum-137): support anonymous event views via session UUID`
(le scope `scrum-137` est conservé pour cohérence PR ; le titre ne mentionne pas un SCRUM particulier — c'est un fix transversal).

### Étape 6 — Frontend SCRUM-137 : services + types + hooks

**6.1** Créer `frontend/src/types/coOrganizer.ts`.
**6.2** Créer `frontend/src/services/coOrganizerApi.ts`.
**6.3** Créer `frontend/src/hooks/useCoOrganizers.ts` + `useCoOrganizerInvitations.ts`.
**6.4** Tests `.test.ts` pour le service et les hooks.

**Commit** : `feat(scrum-137): add coOrganizerApi service + types + hooks`.

### Étape 7 — Frontend SCRUM-137 : composants UI

**7.1** Créer `CoOrganizersEditor.tsx` + bones `co-organizers-section.bones.json`.
**7.2** Modifier `EventForm.tsx` + `EventEditPage.tsx` pour monter le composant.
**7.3** Créer `EventOrganizerTeam.tsx` + bones `event-organizer-team.bones.json`.
**7.4** Modifier `EventDetailPage.tsx` pour monter `EventOrganizerTeam` dans la sidebar.
**7.5** Créer `CoOrganizerInvitationsBadge.tsx` + `CoOrganizerInvitationsList.tsx` + bones `co-organizer-invitations.bones.json`.
**7.6** Modifier `Navbar.tsx` + `ProfilePage.tsx` pour monter les composants.
**7.7** Tests pour chaque composant (`render`, props variantes, interactions).

**Commits** : 2 ou 3 commits atomiques (par section : edit form / detail page / navbar+profile).

### Étape 8 — Frontend SCRUM-146 : services + types + hook

**8.1** Créer `frontend/src/types/comment.ts`.
**8.2** Créer `frontend/src/services/commentApi.ts`.
**8.3** Créer `frontend/src/hooks/useComments.ts`.
**8.4** Tests.

**Commit** : `feat(scrum-146): add commentApi service + types + useComments hook`.

### Étape 9 — Frontend SCRUM-146 : composants UI

**9.1** Créer `CommentSection.tsx`, `CommentForm.tsx`, `CommentItem.tsx`.
**9.2** Créer `comments.bones.json`.
**9.3** Modifier `EventDetailPage.tsx` pour monter `<CommentSection />`.
**9.4** Tests pour chaque composant + intégration `EventDetailPage`.

**Commit** : `feat(scrum-146): add CommentSection + CommentForm + CommentItem with replies`
puis `feat(scrum-146): integrate CommentSection in EventDetailPage`.

### Étape 10 — Frontend Axe 4 : session UUID + view anonyme

**10.1** Créer `frontend/src/services/sessionId.ts`.
**10.2** Modifier `statsApi.ts` pour envoyer `sessionId` en body.
**10.3** Modifier le `useEffect` dans `EventDetailPage.tsx` (retirer le guard `user`).
**10.4** Tests.

**Commit** : `feat(scrum-137): record anonymous event views with session UUID`.

### Étape 11 — Validation locale globale

**11.1** Backend : `cd backend && ./mvnw verify -DskipITs` (5 services + 10 shared libs, ~3 min).
**11.2** Frontend : `cd frontend && npm ci && npm run lint && npm run test && npm run build`.
**11.3** Skeletons : `cd frontend && npm run skeleton` si `generate.mjs` utilisé.
**11.4** Smoke manuel : `npm run dev` et tester :
   - `EventDetailPage` anonyme → vue enregistrée ?
   - `EventDetailPage` connecté → équipe organisatrice affichée + commentaires visibles ?
   - `EventEditPage` → CoOrganizersEditor visible ?
   - `Navbar` → badge invitations si PENDING ?

Si erreurs : itérer en fix-commits petits.

### Étape 12 — Sprint context final + commits

**12.1** Compléter `backend/docs/sprint-context.md` et `frontend/docs/sprint-context.md` avec un résumé final de cette PR (livraison des 4 axes).

**Commits** : `docs(backend): sprint-context.md — 2026-05-14 entry` + `docs(frontend): sprint-context.md — 2026-05-14 entry`.

### Étape 13 — Push + ouverture PR

**13.1** `git push -u origin feature/scrum-137-146-doc-and-views`.
**13.2** Préparer le body PR à partir de `.github/pull_request_template.md` (cf. § Workflow Git ci-dessous).
**13.3** `gh pr create --title "feat(scrum-137): co-organizers UI, comments section, anonymous views and post-PR158 docs refresh" --body-file <body>`.
**13.4** Récupérer l'URL PR ; la communiquer à Elie via le summary final.

### Étape 14 — Code review autonome (skill `pr-review-toolkit`)

**14.1** Lancer le skill : `Skill pr-review-toolkit` sur la PR fraîchement créée.
**14.2** Pendant que la review tourne en parallèle de la CI : surveiller `gh pr checks <n> --watch`.
**14.3** Triage des findings : adresser BLOQUANTS + IMPORTANTS, lister les MINEURS dans un commit doc à la fin.

### Étape 15 — Boucle CI + Copilot review

**15.1** Attendre CI verte (`gh pr checks <n> --watch`).
**15.2** Récupérer commentaires Copilot : `gh api repos/unige-pinfo6-2026/unige-events/pulls/<n>/comments` + `gh pr view <n> --comments`.
**15.3** Adresser chaque commentaire avec un commit `fix(scrum-137): apply Copilot review — <résumé>`.
**15.4** Pousser, attendre la nouvelle review.
**15.5** Boucle jusqu'à 0 commentaire bloquant OU 3 itérations max (cap protectif).

**Critère de sortie :** CI verte + 0 finding bloquant Copilot + 0 finding bloquant `pr-review-toolkit`. Ne pas merger. Communiquer à Elie.

---

## 5. Critères de done (checklist binaire)

- [ ] Branche `feature/scrum-137-146-doc-and-views` créée depuis `main`.
- [ ] **Axe 1 — Documentation backend** : `backend/AGENTS.md`, `AGENTS.md`, `backend/docs/architecture.md`, `backend/docs/data-model.md`, `backend/docs/dev-guide.md`, `backend/docs/devops-handoff.md`, `backend/docs/sprint-context.md` à jour.
- [ ] **Axe 1 — Documentation frontend** : `frontend/AGENTS.md`, `frontend/docs/architecture.md`, `frontend/docs/components.md`, `frontend/docs/types.md`, `frontend/docs/sprint-context.md` à jour.
- [ ] `backend/services/shared-*/` répertoires supprimés (Décision H).
- [ ] **Axe 2 — SCRUM-137** : 4 composants UI créés (`CoOrganizersEditor`, `EventOrganizerTeam`, `CoOrganizerInvitationsBadge`, `CoOrganizerInvitationsList`), 2 hooks, 1 service, 1 type. Intégrés dans `EventForm`/`EventEditPage`, `EventDetailPage`, `Navbar`, `ProfilePage`.
- [ ] **Axe 3 — SCRUM-146** : 3 composants UI (`CommentSection`, `CommentForm`, `CommentItem`), 1 hook, 1 service, 1 type. Intégrés dans `EventDetailPage`.
- [ ] **Axe 4 — Backend view** : `V11__add_event_views_session.sql` créée ; `EventView`, `EventViewService`, `EventViewResource` mis à jour ; `RecordViewRequest` DTO ajouté ; tests verts.
- [ ] **Axe 4 — Frontend view** : `sessionId.ts` créé ; `statsApi.recordEventView` envoie `sessionId` en body ; `EventDetailPage` call inconditionnel.
- [ ] **OpenAPI** : doublon `/events/{id}/view` supprimé ; vue anonyme + body `RecordViewRequest` ajoutés.
- [ ] **Skeletons** : 4 nouveaux `.bones.json` (comments, event-organizer-team, co-organizers-section, co-organizer-invitations).
- [ ] **Tests backend** : `./mvnw verify -DskipITs` vert sur les 5 services + 10 shared libs.
- [ ] **Tests frontend** : `npm run test` vert ; couverture nouveau code ≥ 80 % L (V8).
- [ ] **Lint frontend** : `npm run lint` vert (ESLint + TypeScript).
- [ ] **Build frontend** : `npm run build` vert.
- [ ] **Smoke manuel** : `npm run dev` — 4 flux validés visuellement.
- [ ] **Sprint context** : entrées `2026-05-14` en tête de `backend/docs/sprint-context.md` ET `frontend/docs/sprint-context.md`.
- [ ] **PR** ouverte via `gh pr create` avec body suivant `.github/pull_request_template.md`.
- [ ] **Code review autonome** (`pr-review-toolkit`) exécutée + findings BLOQUANTS adressés.
- [ ] **CI** verte (build + lint + tests + Sonar quality gate `≥ 80 % L on new code`).
- [ ] **Copilot review** : 0 finding bloquant restant (ou 3 itérations atteintes + raison documentée).
- [ ] **PR non mergée** — Elie merge lui-même.
- [ ] Invariants :
  - Aucun `--no-verify`, `--amend` après push, `force-push`.
  - Aucun `@Disabled` / `@Ignore` / `@Tag("legacy-port-s9")` ajouté.
  - Aucun stub JPA cross-service ajouté.
  - Aucun snake_case dans JSON ou champs JPA.
  - Aucun `is` préfixe sur booléens (entités JPA).

---

## 6. Workflow Git

### Branche

`feature/scrum-137-146-doc-and-views` — créée depuis `origin/main` à `a85da460` (ou tip plus récent).

### Convention de commits

Tous les commits respectent `<type>(<scope>): <description>` validé par `.github/workflows/pr-title-check.yml`.

- Scope `scrum-137` pour les commits SCRUM-137 (frontend co-organizers).
- Scope `scrum-146` pour les commits SCRUM-146 (frontend comments).
- Scope `backend` / `frontend` / `openapi` / `infra` pour les commits hors-Jira (docs, fix view, leftover cleanup).
- Tous les commits incluent `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.

Pas de `--no-verify`, pas de `--amend` après push initial, pas de force-push.

### Body de la PR (template)

Suivre [`.github/pull_request_template.md`](.github/pull_request_template.md). Sections **obligatoires** : Résumé, Changements, Tests, Test plan, Documentation. Sections **optionnelles** à conserver (utiles ici) : Why / Motivation, Décisions techniques tranchées, Notes pour le reviewer.

Structure cible du body (à adapter, format markdown) :

```markdown
## Résumé

Cette PR livre **SCRUM-137** (UI co-organisateurs frontend) + **SCRUM-146**
(section commentaires sur `EventDetailPage`), corrige le compteur de vues
backend pour qu'il fonctionne aussi en anonyme avec une déduplication best-
effort par session UUID, et rafraîchit la documentation post-merge PR #158.

## Why / Motivation

PR #158 a migré le backend vers microservices et a laissé la documentation
légèrement divergente (DB-per-service livré post-merge, notification-service
activé, layout Maven refactoré). On rebascule sur du dev fonctionnel : 2
épics frontend (SCRUM-14 co-organizers, SCRUM-16 comments) débloqués par
SCRUM-136 et SCRUM-139.

## Changements

### Backend

- ...

### Frontend

- ...

### OpenAPI

- ...

### Documentation

- ...

### Infrastructure

- (aucune)

## Tests

- Backend : `./mvnw verify -DskipITs` ✅
- Frontend : `npm run test` ✅ (X tests passés, couverture nouveau code Y%)
- Lint : `npm run lint` ✅
- Build : `npm run build` ✅
- Smoke manuel : ✅ 4 flux validés (EventDetailPage anon, EventDetailPage auth, EventEditPage CoOrgs, Navbar badge)

## Test plan

- [ ] Tester `POST /events/{id}/view` sans token → 204.
- [ ] Tester `POST /events/{id}/view` deux fois avec même sessionId → 1 row en DB.
- [ ] Inviter un co-organisateur par UUID depuis `EventEditPage` → invitation reçue dans la navbar.
- [ ] Poster un commentaire → optimistic display puis confirmation serveur.
- [ ] Répondre à un commentaire → reply imbriquée niveau 1.
- [ ] Vérifier que la doc reflète la réalité (`git log` post-PR158).

## Documentation

- [x] `backend/AGENTS.md`, `frontend/AGENTS.md`, `AGENTS.md` (racine) — à jour.
- [x] `backend/docs/architecture.md`, `backend/docs/data-model.md`, `backend/docs/dev-guide.md`, `backend/docs/devops-handoff.md`, `backend/docs/sprint-context.md` — à jour.
- [x] `frontend/docs/architecture.md`, `frontend/docs/components.md`, `frontend/docs/types.md`, `frontend/docs/sprint-context.md` — à jour.
- [x] `openapi/openapi.yaml` — vue anonyme + body `RecordViewRequest` + doublon supprimé.

## Décisions techniques tranchées

Cf. spec [`specs_archives/specs_claude/specs_scrum-137-146-views-docs.md`](specs_archives/specs_claude/specs_scrum-137-146-views-docs.md) §3 (Décisions A → L).

## Notes pour le reviewer

- Décision A (invitation par UUID, pas `GET /users/search`) : à valider.
- Décision B (signaler commentaire = toast informatif, pas de modale) : à valider.
- Décision C (levée invariant `git diff openapi/` = 0) : à valider.
- La doc post-PR158 est lourde (5 commits) mais lisible commit par commit.
```

---

## 7. Skills à utiliser

| Skill | Usage |
|---|---|
| **`frontend-design`** | Pour les 4 composants SCRUM-137 + 3 composants SCRUM-146. Garantit l'alignement sur le design system du projet (tokens CSS, composants `utils/`, glassmorphism cards, lucide-react icons). À invoquer **avant** de générer chaque composant majeur. |
| **`code-simplifier`** | Passe finale avant push initial. Audit du code ajouté pour DRY, dead code, simplifications triviales. |
| **`pr-review-toolkit`** | Étape 14 — code review autonome multi-agent sur la PR fraîchement ouverte, en parallèle de la CI. Source de vérité pour fix BLOQUANTS/IMPORTANTS. |
| **`claude-md-management`** | Optionnel — si la mise à jour de `AGENTS.md` (root) ou `backend/AGENTS.md` ou `frontend/AGENTS.md` dépasse 20 lignes de diff, invoquer ce skill pour valider la structure. |
| **`context7`** | Si une question de doc précise se pose sur `react-image-crop`, `recharts`, `react-big-calendar`, `boneyard-js`, etc. — vérifier la doc à jour avant de coder. Notamment pour le pattern optimistic update + rollback dans `useComments`. |
| **`github`** | Toutes les opérations `gh` : `gh pr create`, `gh pr checks --watch`, `gh pr view --comments`, `gh api repos/.../pulls/<n>/comments` pour adresser Copilot, `gh pr merge` **NE PAS APPELER** (Elie merge). |
| **`superpowers`** | (Disponible mais pas requis dans cette spec.) |

---

## 8. Garde-fous & invariants

- **Pas de `--no-verify`** sur `git commit` ni `git push`.
- **Pas de `--amend`** après le push initial (sinon force-push requis).
- **Pas de `force-push`**.
- **Pas de squash** par l'agent (Elie peut le faire au merge).
- **OpenAPI-first** : la section `/events/{id}/view` est mise à jour **avant** la modification de `EventViewResource.java` (Étape 4 avant Étape 5).
- **Skeletons** obligatoires pour chaque composant nouveau avec état `loading`. Cf. AGENTS frontend.
- **Axios partagé** : tous les nouveaux services frontend utilisent l'instance de `@/services/api`, jamais `fetch` ni `axios.create()` local.
- **Alias `@/`** : aucun import relatif `../` ; tous les nouveaux imports utilisent `@/`.
- **Pas de snake_case** dans champs JPA ou JSON. Pas de préfixe `is` sur booléens entités.
- **Pas de stub JPA cross-service** : `find backend/services -name '*Stub.java'` doit rester à 0.
- **Pas de `@Disabled`/`@Ignore`/`@Tag("legacy-port-s9")`** ajouté.
- **`git diff openapi/` est désormais autorisé** (Décision C) — mais reste mesuré : ce PR ne touche QUE l'endpoint `/events/{id}/view`.
- **`git diff frontend/`** doit rester cohérent avec le contrat OpenAPI unique. Les types `Comment`, `CoOrganizer*` doivent matcher exactement les schémas existants.
- **Tests** : ne pas dégrader le coverage. Tout nouveau composant a son `.test.tsx`.
- **Conformité PR title check** : titre PR + commits matchent `<type>(<scope>): <desc>`.

---

## 9. Risques identifiés & mitigations

| Risque | Probabilité | Impact | Mitigation |
|---|---|---|---|
| Migration Flyway V11 conflit (autre PR ouvre V11 en parallèle) | Faible | Bloquant en preview deploy | Vérifier au checkout que V11 est libre. Si non : prendre V12. **Ne jamais modifier une V<N> committée.** |
| OpenAPI : modifier le doublon casse un consommateur frontend caché | Faible | Tests frontend rouges | Le doublon était syntaxiquement identique à la 1ère déclaration ; OpenAPI parsers prennent la dernière par défaut → suppression sans effet. |
| Backend `callerIdentity.requireUuid()` throw 401 en anon (regression `9e20b455`) | Moyenne | EventViewResource crash sur anon | Utiliser `getUuid()` (null-safe) au lieu de `requireUuid()` pour le path anonyme, ou bypass `callerIdentity` complètement en branche anon. |
| Frontend : pas de TanStack Query → optimistic update manuel + rollback | Moyenne | Bugs de désync | Suivre fidèlement le pattern `useFavorite`/`useAttendance` (état local + rollback sur erreur). Tests obligatoires : optimistic + rollback. |
| Skeleton `comments` mal calibré → flash UI au load | Faible | Cosmetic | Calibrer manuellement contre 3-4 commentaires de tailles variées. Si trop différent du DOM réel → ajuster bones. |
| Copilot review remonte des findings non bloquants en cascade | Moyenne | Boucle d'itération | Cap à 3 itérations Copilot (Décision L). Reporter à Elie au-delà. |
| SCRUM-137 invitation par UUID UX médiocre | Forte | Acceptable produit | Acté Décision A. Suivi S9+. |
| `EventEditPage` ne passe pas `eventId` à `EventForm` (mode création) | Moyenne | Bug de mount conditionnel | `CoOrganizersEditor` reçoit `eventId: Long \| null` et s'auto-mount uniquement si non-null. Tests dédiés (create vs edit). |
| Tests frontend trop fragiles sur le DOM des composants commentaires | Moyenne | CI rouge intermittent | Utiliser `getByRole` / `getByLabelText` plutôt que selectors fragiles. Mocker `commentApi` au niveau service, pas via `axios-mock-adapter`. |
| Section commentaires bloquée pour event `DRAFT/CANCELLED/EXPIRED/BANNED` | Forte | UX correct | Le composant `CommentSection` reçoit `eventStatus` et masque le form + affiche message "Commentaires désactivés sur cet événement" pour `!= PUBLISHED`. Aligné sur règles backend (`CommentService.post`). |

---

## 10. Section finale — Launch prompt

> **Bloc à coller tel quel pour lancer l'implémentation.** Tout est dedans : la spec à suivre, l'autonomie attendue, les garde-fous, la fin de course.

```
Tu vas implémenter la PR multi-axes décrite dans
specs_archives/specs_claude/specs_scrum-137-146-views-docs.md.

Lis la spec en entier AVANT de coder. Suis l'ordre des étapes (§4) strictement.
Toutes les décisions techniques (§3, Décisions A → L) sont tranchées — ne les
rediscute pas.

Mode de travail : autonomie complète. Travaille en pleine autonomie jusqu'à
l'ouverture de la PR via `gh pr create`. NE MERGE PAS la PR — Elie merge
lui-même.

Étapes clés :

1. Étape 0 — Crée la branche `feature/scrum-137-146-doc-and-views` depuis
   `origin/main`. Vérifie le HEAD courant.

2. Étapes 1 → 12 — Implémentation séquentielle. Commits atomiques par étape
   (cf. Décision I). Convention `<type>(<scope>): <desc>` validée par
   `.github/workflows/pr-title-check.yml`. Inclure `Co-Authored-By: Claude
   Opus 4.7 (1M context) <noreply@anthropic.com>` sur chaque commit.

3. Étape 13 — `git push -u origin feature/scrum-137-146-doc-and-views` puis
   `gh pr create` avec body suivant `.github/pull_request_template.md`
   (sections obligatoires : Résumé, Changements, Tests, Test plan,
   Documentation ; optionnelles utiles : Why, Décisions, Notes reviewer).
   Titre PR : `feat(scrum-137): co-organizers UI, comments section,
   anonymous views and post-PR158 docs refresh`.

4. Étape 14 — Code review autonome via skill `pr-review-toolkit` sur la PR
   fraîchement créée. Adresse les BLOQUANTS et IMPORTANTS dans des commits
   `fix(scrum-137): apply review — <résumé>`.

5. Étape 15 — Boucle CI + Copilot review :
   - `gh pr checks <n> --watch` pour la CI.
   - `gh pr view <n> --comments` + `gh api repos/.../pulls/<n>/comments`
     pour les commentaires Copilot.
   - Adresser chaque commentaire bloquant dans un commit
     `fix(scrum-137): apply Copilot review — <résumé>`.
   - Boucle jusqu'à CI verte + 0 finding bloquant Copilot + 0 finding
     bloquant `pr-review-toolkit` OU 3 itérations Copilot atteintes (cap
     protectif).

6. Critère d'arrêt définitif : CI verte + 0 finding bloquant restant + PR
   non mergée. Communique à Elie l'URL PR + un résumé en 5 bullets de ce
   qui a été livré.

Garde-fous (§8) :
- Pas de `--no-verify`, `--amend` après push, force-push, squash par toi.
- Pas de `@Disabled` / `@Ignore` / `@Tag("legacy-port-s9")` ajouté.
- Pas de stub JPA cross-service, pas de snake_case, pas de préfixe `is`.
- OpenAPI-first : Étape 4 avant Étape 5.
- Skeletons obligatoires (§ Décision F).
- Axios partagé + alias `@/`.
- `git diff frontend/` cohérent avec OpenAPI.

Skills utilisés (§7) :
- `frontend-design` avant chaque composant majeur SCRUM-137 / SCRUM-146.
- `code-simplifier` avant le push initial (passe DRY/dead code).
- `pr-review-toolkit` Étape 14.
- `github` pour toutes les opérations gh.
- `context7` si question doc lib (boneyard, recharts, etc.).
- `claude-md-management` si diff AGENTS.md > 20 lignes.

Validation locale obligatoire avant push (§4 Étape 11) :
- Backend : `cd backend && ./mvnw verify -DskipITs` ✅
- Frontend : `cd frontend && npm ci && npm run lint && npm run test && npm run build` ✅
- Skeletons : `npm run skeleton` si `generate.mjs` utilisé.
- Smoke manuel : `npm run dev` + tester les 4 flux clés.

Sprint-context à dater 2026-05-14, en tête de chaque fichier sprint-context
(backend + frontend). Ne pas supprimer l'historique des sprints précédents.

Tu peux faire des tâches en parallèle (sub-agents) quand pertinent — par
exemple lancer la review `pr-review-toolkit` pendant que la CI tourne.

Réussite = PR ouverte + CI verte + 0 bloquant Copilot/pr-review-toolkit +
sprint-context daté. Échec = bloqueur non documenté à Elie.

Commence par l'Étape 0. Bonne implémentation.
```

---

*Fin du document. Version 1.0 — 2026-05-14.*
