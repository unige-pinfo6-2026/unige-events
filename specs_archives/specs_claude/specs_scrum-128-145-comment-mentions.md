# SCRUM-128 / SCRUM-145 — Notifications de mention dans les commentaires et `NEW_COMMENT`

| Champ | Valeur |
|---|---|
| Ticket Jira (planification d'origine) | [SCRUM-128](https://pinfo-groupe6.atlassian.net/browse/SCRUM-128) — `[BACK][S9] Notifications de mention dans les commentaires` |
| Ticket Jira (suivi actuel dans le repo) | [SCRUM-145](https://pinfo-groupe6.atlassian.net/browse/SCRUM-145) — phase 3 du chantier notifications, **réservé** côté schema (`NotificationType.COMMENT_MENTION` + `NEW_COMMENT` déjà persistables, cf. [`NotificationType.java`](backend/services/notification-service/src/main/java/ch/unige/events/notification/entity/NotificationType.java) + [`V2__widen_notification_type_check.sql`](backend/services/notification-service/src/main/resources/db/migration/V2__widen_notification_type_check.sql)) |
| Sprint | S9 (calendrier produit). Ticket strictement backend ; le frontend (autocomplete `@`, render des mentions cliquables) est traité dans un ticket S9+ séparé (cf. § 1 non-objectifs). |
| Épic | [SCRUM-13](https://pinfo-groupe6.atlassian.net/browse/SCRUM-13) — Profils utilisateurs et social (les notifications de commentaire participent à la boucle d'engagement entre users) |
| Story | [SCRUM-99 / SCRUM-140](https://pinfo-groupe6.atlassian.net/browse/SCRUM-99) (phases 1 + 2 livrées) — cette spec livre la **phase 3** annoncée dans le commentaire de [`NotificationType.java`](backend/services/notification-service/src/main/java/ch/unige/events/notification/entity/NotificationType.java#L39) et dans [`V2__widen_notification_type_check.sql:8-11`](backend/services/notification-service/src/main/resources/db/migration/V2__widen_notification_type_check.sql#L8-L11) |
| Story Points | 8 (proposition — à valider par le PO) |
| Branche | `feature/scrum-145-comment-mentions` (cf. Décision A) |
| Base | `main` (la branche du repo `feature/s7-comment-mentions` mentionnée dans le prompt n'existe pas encore — à créer depuis le tip de `main`) |
| Auteur spec | Daniel (rédaction assistée Claude Opus 4.7) |
| Date | 2026-05-19 |
| PR de référence | `feat(scrum-145): wire CommentMention and NewComment notification consumers` |
| Mode de travail | **Une seule PR backend, livrée en pleine autonomie** (à l'issue d'une session d'implémentation séparée). La présente spec est l'artefact de planification ; aucun code de production n'est livré dans la session courante. |
| Règle d'or `openapi-first` | **NON applicable** — toutes les modifications de contrat public liées à cette feature sont **déjà présentes** dans [`openapi/openapi.yaml`](openapi/openapi.yaml) (lignes 727-756 : `COMMENT_MENTION` + `NEW_COMMENT` dans `NotificationType.enum`). Cette PR câble les consumers sans toucher au YAML. |

> **Pré-requis lecture obligatoires avant implémentation :**
> - [`backend/AGENTS.md`](backend/AGENTS.md) — conventions camelCase, layer Resource → Service → Entity, Kafka émis post-commit, REST clients `@RegisterRestClient`, `Hibernate.validate` + Flyway par service.
> - [`AGENTS.md`](AGENTS.md) racine — workflow git, convention de scope `scrum-145`.
> - [`specs_archives/specs_claude/specs_scrum-169.md`](specs_archives/specs_claude/specs_scrum-169.md) — système de username (livré). Cette spec consomme directement le contrat figé par SCRUM-169 (champ `User.username`, regex, lookup case-insensitive, search endpoint).
> - [`specs_archives/specs_claude/specs_scrum-139.md`](specs_archives/specs_claude/specs_scrum-139.md) — backend des commentaires (livré). Contient le pattern `CommentCreatedEvent` + le bridge `CommentCreatedKafkaBridge` qu'on consomme ici.
> - La spec SCRUM-99 / SCRUM-140 (notifications phases 1 + 2 livrées) — pattern Kafka consumer + `NotificationService.create(...)` + leçon at-least-once.

---

> ## ⚠️ Avertissement — décisions verrouillées en chat à recroiser
>
> Le prompt qui a généré cette spec acte 13 décisions « verrouillées » côté chat. **Plusieurs d'entre elles sont déjà tranchées dans le code livré** (SCRUM-169) et **divergent** de la formulation du prompt. La spec **respecte ce qui est en production** ; les divergences sont listées au § 0 (« Reconciliation avec les décisions verrouillées »). Si une divergence n'est pas acceptable côté produit, il faut soit (a) ré-ouvrir la décision et trancher autrement (impact : revisiter SCRUM-169 livré), soit (b) ajuster la décision chat pour l'aligner sur la réalité du repo.

---

## 0. Reconciliation avec les décisions verrouillées en chat

Cette spec est rédigée sous l'hypothèse que l'instance « handle » désignée dans le prompt est le champ **`User.username`** déjà livré par [SCRUM-169](specs_archives/specs_claude/specs_scrum-169.md) (PR mergée mi-mai 2026). En conséquence :

| # | Décision verrouillée en chat | État dans le repo | Action |
|---|---|---|---|
| 1 | Syntaxe de mention : `@<handle>`, **space-terminated**. Regex `@([a-z0-9._]+)(?=\s\|$\|[^\w.])`. | Pas encore implémentée — point ouvert à trancher dans cette PR. | **Adoptée telle quelle** — voir Décision E ci-dessous (regex ajustée à la charset username réelle `[a-z0-9._-]`). |
| 2 | Format de handle : `prenom.nom`, ASCII-fold, lowercased. | **Plus permissif que ça** : la regex en place est `^[a-z0-9._-]{3,30}$` (cf. [`User.java:52`](backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java#L52) + V3 migration). Les usernames `alice.martin`, `alice123`, `alice-m`, `ad.min2` sont tous valides. | **Réalité du repo retenue.** La regex de mention doit accepter `[a-z0-9._-]` (PAS uniquement `[a-z0-9._]`). |
| 3 | Politique de collision : `daniel.dosh`, `daniel.dosh.2`, `daniel.dosh.3` (suffixe **après un point**). | **Différent** : le backfill SCRUM-169 utilise un suffixe **numérique sans point** (`daniel.dosh2`, `daniel.dosh3` — cf. [`V3__add_user_username.sql`](backend/services/user-service/src/main/resources/db/migration/V3__add_user_username.sql) Décision G). | **Réalité du repo retenue.** Pas d'impact sur la spec de mention — c'est un détail de génération à la création de compte, pas une convention de parsing. |
| 4 | Handles **immutables** pour l'instant. | **Mutables** : `PATCH /users/me/username` existe (rate-limit 5/h — cf. [`UserResource.java:177-185`](backend/services/user-service/src/main/java/ch/unige/events/user/resource/UserResource.java#L177-L185)). | **Réalité du repo retenue.** Voir § 9 Risques — un rename casse les anciennes mentions, mais elles n'étaient pas notifiantes (mention = trigger one-shot, jamais persistée comme lien dur). |
| 5 | Backfill généré depuis `firstName`/`lastName` avec fallback `displayName`. | **Livré** par V3 — ASCII-fold via `unaccent`, fallback chain `displayName > firstName.lastName > 'user'`. | **Réalité du repo retenue.** Décision tranchée dans SCRUM-169, hors scope ici. |
| 6 | Affichage UI : `@username` gris sous `displayName`. | **Livré** : [`UserIdentity.tsx:65`](frontend/src/components/user/UserIdentity.tsx#L65), [`CommentItem.tsx`](frontend/src/components/event/CommentItem.tsx), `FollowListRow`, `EventOrganizerTeam`, `PendingCoOrganizersEditor`. | **Réalité du repo retenue.** Aucune action UI requise dans cette PR. |
| 7 | Handles uniques par construction → pas d'ambiguïté `displayName`. | **Vrai** — `User.username` est `UNIQUE NOT NULL`. | Pas d'action. |
| 8 | Dedup des mentions au sein d'un commentaire. | À implémenter. | **Adoptée telle quelle** — voir Décision G ci-dessous. |
| 9 | `NEW_COMMENT` envoyé au **créateur primaire seul** ; co-organisateurs exclus ; pas pour l'auteur si lui-même créateur ; déclenché sur tous les commentaires (top-level + replies). | À implémenter. | **Adoptée telle quelle** — voir Décision H ci-dessous. |
| 10 | Messages FR : *« {author} vous a mentionné dans un commentaire sur {event} »* et *« {author} a commenté votre événement {event} »*. | À implémenter. | **Adoptée telle quelle** — voir Décision I ci-dessous. |
| 11 | Self-mentions ignorées. | À implémenter. | **Adoptée telle quelle.** |
| 12 | Auteur = créateur → pas de `NEW_COMMENT`. | À implémenter. | **Adoptée telle quelle** — équivaut à filtrer `creatorId == authorId` côté consumer, miroir de [`AttendanceCreatedConsumer.java:55-58`](backend/services/notification-service/src/main/java/ch/unige/events/notification/kafka/AttendanceCreatedConsumer.java#L55-L58). |
| 13 | Mention + organizer overlap : Alice mentionnée + créatrice → 2 lignes (mention + new-comment). | À implémenter. | **Adoptée telle quelle.** Le code émet 2 lignes indépendantes ; sémantique différente. |

**Conclusion.** Les 13 décisions du prompt restent valides modulo (a) la charset de mention élargie à `[a-z0-9._-]` au lieu de `[a-z0-9._]` pour matcher la regex SCRUM-169, et (b) le retrait des décisions 2, 3, 4, 5, 6, 7 du périmètre de cette PR (déjà livrées par SCRUM-169).

---

## 1. Objectifs & non-objectifs

### Objectifs

- **Axe 1 — Kafka consumer `CommentMentionConsumer`** dans `notification-service` qui écoute `comments.created` et émet une notification `COMMENT_MENTION` par utilisateur mentionné dans le commentaire (après dédup, auto-mention exclue, mentions non-résolues silencieusement ignorées).
- **Axe 2 — Kafka consumer `NewCommentConsumer`** dans `notification-service` qui écoute `comments.created` et émet une notification `NEW_COMMENT` à destination du créateur primaire de l'événement (sauf si l'auteur du commentaire est lui-même le créateur). S'applique aux top-level et aux replies (cf. Décision H).
- **Axe 3 — Enrichissement du payload Kafka `CommentCreatedEvent`** d'un champ `content: String` (et `eventTitle: String` pour économiser un REST callback sur chaque comment ; cf. Décision D). Modification purement additive — Jackson tolère les anciens producers / consumers (les anciens events sans `content` ne déclenchent juste pas le parsing de mention).
- **Axe 4 — Endpoint user-service internal `GET /users/_internal-by-usernames?usernames=alice.dosh,bob.smith`** retournant `List<UserPublicResponse>` (ou un projection slim `IdProjection`) pour la résolution batchée des handles vers UUIDs. Internal-only (header `X-Internal-Token`, hors `openapi.yaml`, documenté dans [`backend/docs/internal-endpoints.md`](backend/docs/internal-endpoints.md)).
- **Axe 5 — REST client `UserServiceClient.getByUsernames(List<String>)`** dans `shared-domain-dtos` pour appeler le nouvel endpoint depuis `notification-service`. Fault-tolerance : `@Retry` (3 tentatives, 200 ms), `@Timeout` (2 s), `@CircuitBreaker`, `@Fallback` → liste vide + log warn (comportement aligné sur `getAttendeeProjections`).
- **Axe 6 — Parseur de mentions `MentionParser`** dans `notification-service` (ou shared lib si réutilisé par le futur frontend autocomplete — voir Décision F). Regex extraite en constante, méthode `extractHandles(String content): Set<String>` qui retourne les handles uniques lowercased. Couvre la dédup (locked-in #8).
- **Axe 7 — Migration Flyway côté notification-service : aucune.** Le CHECK constraint a déjà été élargi par V2 (SCRUM-140 anticipation, Décision G de [`specs_scrum-140`](specs_archives/specs_claude/) — pas re-vérifié ici mais documenté dans le fichier V2). **NotificationType.COMMENT_MENTION et NEW_COMMENT sont déjà persistables sans nouvelle migration.**
- **Axe 8 — Tests Quarkus** :
  - `MentionParserTest` (pur unit, < 1 s) — pattern, dédup, espaces, ponctuation, accents (les handles sont ASCII donc les caractères Unicode dans le texte ne sont jamais des handles), edge cases (`@@`, `@.`, `@-`, `@ alice`, `@alice@bob`).
  - `CommentMentionConsumerTest` (`@QuarkusTest` + `@InjectMock` des REST clients) — happy path, dédup, auto-mention, mention inexistante, mention vers user avec `profilePublic=false` (le notifier quand même — la mention est une primitive d'attention, pas une consultation de profil), failure modes downstream (user-service `getByUsernames` retourne empty → 0 notif sans crash).
  - `NewCommentConsumerTest` — happy top-level, happy reply, auteur=créateur → skip, event introuvable → skip + log, créateur null → skip + log, créateur a `profilePublic=false` → notifier quand même (cas symétrique au mention).
  - `UserServiceClient.getByUsernamesTest` — bulk OK, missing handles silently dropped, fallback liste vide.
  - Integration test bout-en-bout sur la chaîne `engagement-service publishes → notification-service consumes → notifications.user_id rows persistent` — en `@QuarkusTest` profile multi-service ou via in-memory Kafka (le repo a-t-il déjà ce pattern ? À confirmer au moment de l'impl, sinon se borner aux 4 tests unitaires + consumer).
- **Axe 9 — Documentation** :
  - [`backend/docs/internal-endpoints.md`](backend/docs/internal-endpoints.md) — ajouter ligne `GET /users/_internal-by-usernames` (entry numéro suivant disponible).
  - [`backend/docs/data-model.md`](backend/docs/data-model.md) — section `Notification` : mettre à jour le tableau des `NotificationType` pour passer `COMMENT_MENTION` + `NEW_COMMENT` de « réservés » à « émis » (préciser le consumer correspondant).
  - [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) — section finale datée `2026-05-XX (SCRUM-145 livré)`.
  - [`backend/docs/api-contract.md`](backend/docs/api-contract.md) — pas de changement (aucun endpoint **public** ajouté).
  - Pas de modification de `openapi/openapi.yaml`.
- **Couverture** : ≥ 80 % L sur le nouveau code (Sonar gate).

### Non-objectifs

- **Frontend autocomplete `@<prefix>` dans `CommentForm`** — sera traité dans un ticket frontend S9+ séparé. La présente PR câble uniquement la pipeline backend ; le user expérimenté tape `@handle` à la main pour déclencher une mention.
- **Frontend rendu des mentions cliquables dans `CommentItem`** — transformer `@alice.martin` en `<Link to="/profile/alice.martin">@alice.martin</Link>` est un ticket frontend S9+ séparé.
- **Préférences de notification par event** (mute mentions, mute new-comment) — produit S10+.
- **`NEW_COMMENT` pour les co-organisateurs ACCEPTED** — locked-in #9 acte explicitement « créateur primaire seul ». À évaluer en S10 si le PO le souhaite (la liste des organisateurs est déjà disponible côté event-service via `GET /events/{id}/organizer-uuids`, le coût d'ajout est faible).
- **Notification quand un commentaire est édité ou supprimé** — pas dans le scope.
- **Notification quand un commentaire est liké** — pas dans le scope (SCRUM-144 livre le like, le compteur s'incrémente silencieusement).
- **Persistence d'une table « mentions »** — la mention est volatile (consommée à la création du commentaire pour la notif, puis oubliée). Si le frontend a besoin de re-render des mentions cliquables il les re-parse depuis `content` — pas de table à maintenir.
- **Backfill rétroactif** des commentaires déjà persistés (pré-SCRUM-145) — décision UX : aucun. Les commentaires antérieurs ne déclenchent pas de notifications a posteriori (cf. § 9).
- **Modification du contrat public OpenAPI** — toute la surface publique impactée est déjà figée.
- **Modification des entités existantes** (`Comment`, `User`, `Notification`) — additif Kafka payload + nouveau consumer + nouveau REST endpoint internal seulement.
- **Merge de la PR** par l'agent.

---

## 2. Contexte

### 2.1 Le besoin produit

Lorsqu'un commentaire est posté sur un événement, deux signaux d'engagement sont attendus :

1. **L'auteur (Alice) peut « pinguer » des autres utilisateurs** en écrivant `@bob.smith` dans son texte. Bob doit recevoir une notification in-app *« Alice vous a mentionné dans un commentaire sur <event> »* qui ouvre le détail de l'event. Sans ce signal, la fonctionnalité commentaire reste passive — les conversations ne s'amorcent pas.
2. **L'organisateur d'un event doit savoir quand un commentaire est posté sur son event**, qu'il soit mentionné explicitement ou pas. C'est le pendant de `NEW_ATTENDEE` (SCRUM-99 phase 1) pour la facette « engagement social » de l'event.

SCRUM-99 (phase 1, livré) a livré la primitive `Notification` (entité, REST endpoints `/api/users/me/notifications`, listing + mark-read + mark-all-read, header `X-Unread-Count`). SCRUM-140 (phase 2, livré) a ajouté 3 types liés aux follows. SCRUM-145 livre les **deux derniers types réservés** côté schema : `COMMENT_MENTION` + `NEW_COMMENT`.

### 2.2 Ce qui manque aujourd'hui

| Pièce manquante | Conséquence |
|---|---|
| Aucun consumer Kafka qui écoute `comments.created` dans `notification-service` | Les commentaires sont publiés (cf. [`CommentCreatedKafkaBridge`](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/kafka/CommentCreatedKafkaBridge.java)) mais **aucun** consumer n'en fait quoi que ce soit côté notification |
| Le payload Kafka `CommentCreatedEvent` ne contient pas le texte du commentaire | Impossible de parser les mentions sans un round-trip REST vers engagement-service pour fetcher `Comment.content` |
| Aucun endpoint user-service `GET /_internal-by-usernames?usernames=...` | Impossible de batcher la résolution `handle → UUID` ; un parsing de 5 mentions ferait 5 hop unitaires `GET /users/by-username/{u}` (NotFoundExceptions inclues, anti-CB hostile) |
| Aucun parseur `MentionParser` côté backend | La regex de mention vit dans la tête du dev, pas en code testable |
| `NotificationType.COMMENT_MENTION` et `NEW_COMMENT` sont **réservés mais non émis** | Le frontend voit `NotificationType.enum` dans OpenAPI mais ne recevra jamais de row de ces types tant que les consumers ne sont pas câblés |

### 2.3 Ce qui existe déjà à RÉUTILISER tel quel (ne pas recréer)

| Élément | Fichier / ligne | Rôle dans SCRUM-145 |
|---|---|---|
| Enum `NotificationType` avec `COMMENT_MENTION` + `NEW_COMMENT` déjà déclarés | [`NotificationType.java:68-70`](backend/services/notification-service/src/main/java/ch/unige/events/notification/entity/NotificationType.java#L68-L70) | **Aucune modif** — values existent. |
| Flyway V2 widening notification CHECK constraint avec les 9 valeurs | [`V2__widen_notification_type_check.sql:33-35`](backend/services/notification-service/src/main/resources/db/migration/V2__widen_notification_type_check.sql#L33-L35) | **Aucune modif** — CHECK déjà élargie. |
| `NotificationService.create(userId, type, eventId, relatedUserId, message)` | [`NotificationService.java:57-67`](backend/services/notification-service/src/main/java/ch/unige/events/notification/service/NotificationService.java#L57-L67) | **Primitive d'écriture** consommée par les 2 nouveaux consumers — pas de nouvelle méthode |
| Topic Kafka `comments.created` (config + serdes) | Channel configuré dans `notification-service/application.properties` ou via `quarkus-messaging-kafka` (à vérifier au moment de l'impl) | **Channel à brancher** si pas déjà présent en preview/prod ; sinon, ajout local en `%dev`/`%test` via DevServices |
| Pattern Kafka consumer + `@Transactional` + skip self / event absent + `Log.warnf` | [`AttendanceCreatedConsumer.java`](backend/services/notification-service/src/main/java/ch/unige/events/notification/kafka/AttendanceCreatedConsumer.java) | **Modèle direct** pour les 2 nouveaux consumers — mêmes annotations, mêmes garde-fous, mêmes logs |
| Pattern `EventServiceClient.getById(eventId)` pour résoudre title / creatorId | [`UserServiceClient.java`](backend/shared/domain-dtos/src/main/java/ch/unige/events/shared/client/UserServiceClient.java) + `EventServiceClient` (homologue) | `NewCommentConsumer` charge l'event pour récupérer `creatorId` + `title` — pattern identique à `AttendanceCreatedConsumer` |
| Pattern REST client interne avec `@Fallback` retournant liste vide / log warn | [`UserServiceClient.getAttendeeProjectionsFallback`](backend/shared/domain-dtos/src/main/java/ch/unige/events/shared/client/UserServiceClient.java#L84-L87) | **Modèle direct** pour `getByUsernamesFallback` |
| Pattern endpoint internal `@Path("/_internal-*")` gated par `InternalTokenFilter` | `UserResource._internal-attendee-projections` (cf. `UserServiceClient.java:77`) | **Modèle direct** pour `GET /users/_internal-by-usernames` |
| Champ `User.username` (regex `^[a-z0-9._-]{3,30}$`, lowercased, indexé unique) | [`User.java:52`](backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java#L52) | **Source de vérité** pour la résolution `@handle → User` |
| `User.findByUsername(String)` case-insensitive | [`User.java:110-115`](backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java#L110-L115) | À étendre en `findByUsernames(Collection<String>)` (un `IN` JPQL) |
| `User.searchByUsernamePrefix(...)` | [`User.java:132-151`](backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java#L132-L151) | **Pas réutilisé** ici — la recherche prefix est pour l'autocomplete frontend, pas pour la résolution exacte |
| `CommentCreatedEvent` record Kafka (`commentId`, `eventId`, `authorId`, `parentCommentId`, `createdAt`) | [`CommentCreatedEvent.java`](backend/shared/kafka-events/src/main/java/ch/unige/events/shared/kafka/events/CommentCreatedEvent.java) | **Modifié** : ajout de `content: String` et `eventTitle: String` (Décision D — payload self-contained, pas de hop REST pour parsing) |
| `Comment.content` (TEXT, max 500) | [`Comment.java:53-56`](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/entity/Comment.java#L53-L56) | **Source de contenu** pour le parsing — projeté dans le payload Kafka enrichi |
| `OptionalToken` / `IdProjection` / `UserPublicResponse` records shared-domain-dtos | `shared/domain-dtos/.../dto/` | DTOs disponibles ; `UserPublicResponse` est probablement overkill pour la résolution mention (besoin uniquement de `id` + `username`) — utiliser un projection slim au choix de l'implémenteur |
| Pattern `Set` dédup + `stream().collect(...)` pour bulk resolution | [`CommentService.getByEvent:166-177`](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentService.java#L166-L177) | **Modèle direct** pour la dédup des mentions et la résolution batchée |

### 2.4 Pourquoi maintenant

- **NotificationType.COMMENT_MENTION + NEW_COMMENT sont réservés dans le code et le schema depuis SCRUM-140 (phase 2)** mais ne sont émis nulle part. Le tableau de bord notification frontend a une enum déclarée qui ne se manifeste jamais — **dette ouverte explicite** (cf. comment de [`NotificationType.java:39-50`](backend/services/notification-service/src/main/java/ch/unige/events/notification/entity/NotificationType.java#L39-L50)).
- **SCRUM-169 (usernames) vient d'être mergé** (PR #172). Toutes les conditions backend pour la résolution `@handle` sont en place ; bloquer plus longtemps fait dériver le pari produit (mentions = primitive d'engagement social).
- **Pré-requis pour les tickets frontend S9+** d'autocomplete et de rendu cliquable — sans la pipeline backend, le frontend ne pourrait pas tester la chaîne complète (pas de notification générée → écran de notif vide).
- **Aucune dépendance amont restante** — toutes les briques (Kafka, REST clients, notification entity, user search) sont livrées.

---

## 3. Décisions techniques tranchées (NE PAS REVISITER pendant l'implémentation)

> **Règle.** Une fois la spec validée par Daniel, ces décisions ne se rediscutent pas pendant l'implémentation. Toute déviation doit être documentée dans `sprint-context.md` à la livraison.

### Décision A — Branche `feature/scrum-145-comment-mentions`, base = `main`

**Décision.** La branche s'appelle `feature/scrum-145-comment-mentions` et est créée depuis le tip de `main`. La PR cible `main`.

**Justification.** SCRUM-169 (PR #172) est mergée sur `main` — la branche actuelle `feature/s7-attachments-front` n'est pas dans le périmètre de cette spec. Le ticket Jira d'origine est SCRUM-128 (`[BACK][S9]`) mais le projet a reclassé le travail en SCRUM-145 dès la phase 2 des notifications (cf. [`V2__widen_notification_type_check.sql:8`](backend/services/notification-service/src/main/resources/db/migration/V2__widen_notification_type_check.sql#L8)). Le scope reste `scrum-145` (pour le commit + le titre de PR) ; SCRUM-128 reste l'umbrella ticket produit. **Voir le § 12 (Recommended Jira restructure) pour la suggestion de rapprochement.**

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) `feature/scrum-145-comment-mentions`, base = `main` | Branche propre, pas de dépendance | — | ✅ retenu |
| (b) `feature/s7-comment-mentions` comme dans le prompt | Aligne avec le nommage informel | Non conforme à `AGENTS.md` (scope Jira obligatoire pour `feat`) | ❌ |
| (c) Ré-utiliser une branche existante | — | Risque conflits, pas isolable | ❌ |

### Décision B — Périmètre = backend seul, deux consumers Kafka indépendants

**Décision.** La PR ne touche **que** le backend (et la doc associée). Le frontend autocomplete `@<prefix>` + rendu cliquable des mentions sera traité dans un ticket frontend S9+ séparé. Les deux consumers (`CommentMentionConsumer`, `NewCommentConsumer`) sont **deux classes séparées** dans le même package `notification-service.kafka` consommant le **même topic** `comments.created`.

**Justification.**

| Aspect | 2 consumers séparés | 1 consumer combiné |
|---|---|---|
| Séparation des responsabilités | Une classe = une intention métier (`COMMENT_MENTION` vs `NEW_COMMENT`) | Couplage des deux logiques |
| Test isolation | Un test par classe, mocks plus simples | Un test géant avec 2x les cas |
| Évolution future | Désactiver un type sans toucher à l'autre (feature flag, mute préférences) | Refactor obligatoire |
| Cohérence avec le repo | Le pattern existant utilise 1 consumer par type (cf. `EventCancelledConsumer`, `EventUpdatedConsumer`, `AttendanceCreatedConsumer` — 3 consumers pour 3 types phase 1) | Rupture de convention |

| Option | Verdict |
|---|---|
| (a) 2 consumers indépendants sur le même topic `comments.created` | ✅ retenu |
| (b) 1 consumer combiné | ❌ |
| (c) 2 topics séparés depuis engagement-service | ❌ — engagement-service ne devrait pas connaître les intents notification |

**Note Kafka.** 2 consumers sur le même topic = 2 group-ids différents côté Quarkus reactive-messaging (`@Incoming("comments-mentions")` et `@Incoming("comments-new")` avec chacun son channel mapping vers le même topic). À configurer dans `notification-service/src/main/resources/application.properties` :

```properties
mp.messaging.incoming.comments-mentions.connector=smallrye-kafka
mp.messaging.incoming.comments-mentions.topic=comments.created
mp.messaging.incoming.comments-mentions.group.id=notification-service-mentions

mp.messaging.incoming.comments-new.connector=smallrye-kafka
mp.messaging.incoming.comments-new.topic=comments.created
mp.messaging.incoming.comments-new.group.id=notification-service-new-comment
```

### Décision C — Idempotence at-least-once acceptée (cohérent SCRUM-99 phase 1)

**Décision.** Une livraison Kafka dupliquée du même `CommentCreatedEvent` produit **au plus** 2 lignes `notifications` dupliquées par destinataire mentionné. **Aucune UK sur `notifications`** pour dédupliquer (cohérent avec la Décision D livrée par SCRUM-99 phase 1).

**Justification.** L'application est in-app feed, pas push notification ni email. Une duplication ponctuelle est sans gravité (l'utilisateur voit deux lignes identiques très proches dans le temps), et l'ajout d'une UK `(userId, type, eventId, relatedUserId, message)` est coûteux (index) et fragile (le `message` change si on modifie le template — backfill cassé).

| Option | Verdict |
|---|---|
| (a) At-least-once, pas de dédup côté DB | ✅ retenu (cohérent existant) |
| (b) UK `(userId, type, eventId, relatedUserId)` pour dédup | ❌ — complexité, fragilité, hors-scope |
| (c) Idempotency-key par `commentId` (nouveau champ) | ❌ — bouleverse `notifications` |

### Décision D — Enrichissement du payload `CommentCreatedEvent` avec `content` et `eventTitle`

**Décision.** Le record Kafka [`CommentCreatedEvent`](backend/shared/kafka-events/src/main/java/ch/unige/events/shared/kafka/events/CommentCreatedEvent.java) est élargi de deux champs :

```java
public record CommentCreatedEvent(
        long commentId,
        long eventId,
        UUID authorId,
        Long parentCommentId,
        Instant createdAt,
        String content,           // NEW (SCRUM-145) — texte brut du commentaire, max 500 chars
        String eventTitle         // NEW (SCRUM-145) — titre de l'event au moment de l'émission
) { ... }
```

`CommentService.post(...)` (engagement-service) doit donc charger le titre de l'event (déjà fait via `assertEventVisibleAndLoad`, le `EventDTO.title()` est disponible) et le passer au CDI fire :

```java
commentCreatedEvent.fire(CommentCreatedEvent.created(
        comment.id, eventId, authorId, parent != null ? parent.id : null,
        comment.content, event.title()));
```

**Justification.**

| Aspect | Payload enrichi (`content` + `eventTitle`) | REST callback engagement → comment + event |
|---|---|---|
| Hops réseau par comment | 0 hop pour le parsing (1 hop pour résoudre les handles) | 2 hops (comment + event) AVANT même de commencer le parsing |
| Sensibilité incident downstream | Le consumer est totalement autonome ; si engagement-service tombe, les notifs sont quand même envoyées | Cascade : engagement-service down → pas de mention parsing → notifs perdues |
| Taille du payload | +500 chars + ~60 chars (title) ≈ +560 octets par event. Topic Kafka volume marginal | Plus petit (~150 octets) mais coût caché en hops |
| Backward-compat | Jackson tolère les nouveaux champs additifs — les anciens producers (pré-SCRUM-145) émettent sans `content`, les consumers reçoivent `null`, le parseur retourne `Set.of()` → 0 notif (comportement gracieux) | Identique |
| Évolution future | `eventStatus` ou autre signal additif coûte 0 (juste un nouveau champ record) | Hop supplémentaire chaque enrichissement |

| Option | Verdict |
|---|---|
| (a) Élargir le payload Kafka avec `content` + `eventTitle` | ✅ retenu — autonomie consumer + perf |
| (b) Garder le payload minimal, faire 2 REST callbacks | ❌ — hops, fragilité, latence |
| (c) Élargir seulement `content` (pas `eventTitle`) et faire le hop event | ❌ — incohérent, partial benefit |

**Sécurité (PII).** Le contenu d'un commentaire est public (visible sur `GET /events/{id}/comments`). Le faire transiter par Kafka n'ajoute pas de surface de leak. Topics non chiffrés au repos = aligné sur le standard interne (cf. `architecture.md` infra).

**Backward-compat test.** Un test devra valider que `CommentCreatedEvent.created(id, eventId, authorId, null)` (signature ancienne, 4 args) compile encore via une factory overload ou — préféré — que les 6 args sont mandatory (signature record stricte) et que toute migration de producers se fait dans le même commit que la consommation. Le bridge engagement-service est le seul producer existant ; pas de tiers à coordonner.

### Décision E — Regex de mention : `@([a-z0-9._-]{3,30})(?![a-z0-9._-])`

**Décision.** Le `MentionParser` utilise la regex compilée une fois statique :

```java
private static final Pattern MENTION_RE = Pattern.compile(
        "@([a-z0-9._-]{3,30})(?![a-z0-9._-])",
        Pattern.CASE_INSENSITIVE);
```

Justifications :
- `@` littéral.
- Capture groupe 1 = handle brut, ensuite normalisé (`.toLowerCase()` + trim implicite par la regex) avant comparaison DB.
- Charset `[a-z0-9._-]` aligné sur la regex SCRUM-169 (cf. § 0 reconciliation point 2).
- `{3,30}` aligné sur le `length=30` colonne + `min=3` UI (cohérent UserPublicResponse OpenAPI).
- **Lookahead négatif `(?![a-z0-9._-])`** au lieu de `\s|$|[^\w.]` du prompt : plus strict et plus simple. Garantit que `@alice.dosh!` matche `alice.dosh` (le `!` est hors charset, lookahead OK), que `@alice.dosh.eats` matche `alice.dosh.eats` (continuation possible, jusqu'à la limite 30), et que `@alice@bob` matche `alice` puis (à la prochaine itération) `bob` — la dédup gère le reste.
- `CASE_INSENSITIVE` pour tolérer `@Alice.Dosh` que l'auteur peut taper — le résultat est lowercased côté parser avant la requête DB.

**Cas-limites couverts par la regex** :

| Input | Mentions extraites |
|---|---|
| `Hello @alice.dosh thanks!` | `{"alice.dosh"}` |
| `@alice.dosh @bob.smith @alice.dosh` | `{"alice.dosh","bob.smith"}` (dédup au niveau Set) |
| `@alice.dosh, @bob` | `{"alice.dosh","bob"}` (la virgule est hors charset → lookahead OK) — note : `bob` < 3 chars → **ne matche pas** la regex |
| `email@alice.dosh.com` | `{"alice.dosh.com"}` MAIS dans `extractHandles` on **filtre `length > 30`** et on tente la résolution — si `alice.dosh.com` n'est pas un user, c'est silent skip. Pas de false positive sémantique. |
| `@@alice.dosh` | `{"alice.dosh"}` (la regex matche le 2e `@`, l'usage est rare) |
| `@.alice` | aucune (commence par `.` → en-tête `[a-z0-9]`... non, la regex tolère `.` en début ; collisionne avec un user `.alice` qui ne peut pas exister côté backend car la V3 backfill génère `user2` au lieu de `.alice`) — c'est correct, juste « pas trouvé » |
| `@-ab` | longueur 3 OK, mais la regex tolère `-` en début ; pareil — pas de user backend correspondant, silent skip |

**Justification du choix regex.**

| Option | Verdict |
|---|---|
| (a) `@([a-z0-9._-]{3,30})(?![a-z0-9._-])` | ✅ retenu |
| (b) `@([a-z0-9._-]+)(?=\s\|$\|[^\w.-])` (variante prompt) | ❌ — ne garantit pas `{3,30}`, plus laxiste |
| (c) Parser custom char-by-char | ❌ — réinvente la roue |

### Décision F — `MentionParser` dans `notification-service`, pas dans un shared lib

**Décision.** Le parseur vit dans `notification-service.kafka.MentionParser` (package du consumer). Pas dans `shared-domain-dtos`, `shared-domain-enums`, ni autre lib partagée.

**Justification.** Aucun autre service backend ne parse les mentions aujourd'hui. Quand le frontend autocomplete arrive en S9+, il aura **sa propre regex** côté TypeScript (mirror manuel, comme `RESERVED_USERNAMES` l'est dans [`frontend/src/types/user.ts`](frontend/src/types/user.ts)). Tirer le parseur dans une shared lib coûterait : nouveau module à compiler, nouveaux tests à porter ; pas de bénéfice.

Si dans 2 sprints `moderation-service` veut auditer les mentions, on déplacera vers `shared-platform` ou similaire à ce moment-là (rule of three : aucune duplication actuelle, pas d'extraction prématurée).

### Décision G — Dédup au niveau `Set<String>` côté `MentionParser`, lowercased

**Décision.** `MentionParser.extractHandles(content)` retourne un `Set<String>` (et non une `List`) — la dédup est mécanique. Les handles sont lowercased avant insertion dans le Set.

```java
public Set<String> extractHandles(String content) {
    if (content == null || content.isBlank()) return Set.of();
    Set<String> out = new LinkedHashSet<>();   // LinkedHashSet pour ordre déterministe en test
    Matcher m = MENTION_RE.matcher(content);
    while (m.find()) {
        out.add(m.group(1).toLowerCase());
    }
    return out;
}
```

Justifie locked-in decision #8. Un `LinkedHashSet` garde l'ordre d'apparition (utile pour les logs et les tests).

### Décision H — `NEW_COMMENT` : créateur primaire uniquement, top-level + reply, skip si auteur=créateur

**Décision.** `NewCommentConsumer.onCommentCreated(ev)` :

1. Charge l'event via `EventServiceClient.getById(ev.eventId())`. Si null → log warn, return.
2. Récupère `creatorId = event.creatorId()`. Si null → log warn, return (idem `AttendanceCreatedConsumer`).
3. **Si `creatorId.equals(ev.authorId())` → return silencieusement** (locked-in #12 : auteur=créateur, pas de self-notif).
4. **Émet une notification `NEW_COMMENT` avec `relatedUserId = ev.authorId()` et le template « {author} a commenté votre événement {event} »** — l'`{author}` est résolu via un hop `userClient.getById(ev.authorId())` (à défaut, fallback `"Un utilisateur"` quand `getById` renvoie null — Décision J ci-dessous).
5. Pas de filtrage `top-level vs reply` : les replies déclenchent aussi `NEW_COMMENT` (locked-in #9). Le créateur voit ainsi tout le fil sans avoir à distinguer.

**Justification.** Aligné mot-pour-mot sur le locked-in #9 + #12. Pas de cas piège : la mention vient de Bob, l'event est créé par Alice, Alice reçoit `NEW_COMMENT` ; si Bob mentionne aussi Alice dans le texte, Alice reçoit en **plus** un `COMMENT_MENTION` (cf. locked-in #13, Décision K ci-dessous).

### Décision I — Templates de messages FR

**Décision.** Constantes statiques dans chaque consumer :

```java
// CommentMentionConsumer
private static final String MESSAGE_TEMPLATE =
        "%s vous a mentionné dans un commentaire sur « %s ».";
//                                                ↑ author        ↑ eventTitle

// NewCommentConsumer
private static final String MESSAGE_TEMPLATE =
        "%s a commenté votre événement « %s ».";
//                                            ↑ author        ↑ eventTitle
```

Aligné sur locked-in #10 (chevrons français autour du titre = cohérent avec [`AttendanceCreatedConsumer.MESSAGE_TEMPLATE`](backend/services/notification-service/src/main/java/ch/unige/events/notification/kafka/AttendanceCreatedConsumer.java#L31-L32) : `"Un nouvel inscrit pour « %s »."`).

**Substitution `{authorDisplayName}`**. Le `%s` author est `author.displayName()` si non-null, sinon `"@" + author.username()` si non-null, sinon `"Un utilisateur"`. Helper privé `resolveAuthorLabel(UserPublicResponse author)`. Aligné sur la fallback chain frontend `userDisplayLabel` (cf. [`displayName.ts`](frontend/src/utils/displayName.ts)).

### Décision J — Résolution `authorId → displayName` via `UserServiceClient.getById`, fallback gracieux

**Décision.** Les deux consumers récupèrent l'auteur du commentaire pour insérer son nom dans le message :

```java
UserPublicResponse author = userClient.getById(ev.authorId());
String authorLabel = resolveAuthorLabel(author);
```

`resolveAuthorLabel(author)` retourne `"Un utilisateur"` si `author == null` (downstream timeout ou hard-delete) — la notif est quand même créée (le destinataire voit *« Un utilisateur a commenté votre événement »* — dégradé mais informatif).

**Justification.** Cohérent avec `safeGetUser` de [`CommentService:297-313`](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentService.java#L297-L313). Le pattern de dégradation gracieuse est le standard projet pour les enrichissements cross-service.

### Décision K — `COMMENT_MENTION` + `NEW_COMMENT` sont indépendants : Alice peut recevoir les deux

**Décision.** Les deux consumers tournent indépendamment. Si Bob commente l'event d'Alice avec `@alice.dosh`, **Alice reçoit deux notifs distinctes** :
- `COMMENT_MENTION` (Bob l'a mentionnée)
- `NEW_COMMENT` (Bob a commenté son event)

Aucune logique de mute / dédup cross-type. Sémantique différente, lignes différentes dans la table.

Aligné sur locked-in #13. Documenté en PR + dans `data-model.md`.

### Décision L — Endpoint internal `GET /users/_internal-by-usernames?usernames=alice.dosh,bob.smith` : projection slim

**Décision.** Nouveau handler dans `UserResource` :

```java
@GET
@Path("/_internal-by-usernames")
@PermitAll                // gated par InternalTokenFilter (X-Internal-Token)
public Response getInternalByUsernames(
        @QueryParam("usernames")
        @NotBlank
        @Size(min = 1, max = 50)   // garde-fou anti-DoS : max 50 handles par appel
        String csv) {
    List<String> normalised = Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(String::toLowerCase)
            .distinct()
            .limit(50)
            .toList();
    if (normalised.isEmpty()) return Response.ok(List.of()).build();
    List<User> matches = User.findByUsernames(normalised);
    List<IdProjection> body = matches.stream()
            .map(u -> new IdProjection(u.id, u.username))   // ou record dédié si IdProjection ne porte pas username
            .toList();
    return Response.ok(body).build();
}
```

Statique JPA-side : `User.findByUsernames(Collection<String> usernames)` = `find("username IN ?1", usernames).list()`.

**Garde-fous** :
- `@Size(min=1, max=50)` borne la longueur du CSV — un commentaire de 500 chars peut contenir au max ~30 mentions de 16 chars, 50 est large.
- Chaque handle individuel est validé (pattern + length 3-30) en client-side du `MentionParser` — ici on accepte au runtime des handles invalides qui ne matcheront simplement aucune row.
- Pas de pagination ; le cap de 50 vaut.
- Pas de ISSUE-93 anti-oracle ici : c'est un endpoint internal-only consommé par `notification-service`, pas exposé au client browser. Le filtre `InternalTokenFilter` empêche l'accès anonyme.

**Pourquoi pas `UserPublicResponse` complet ?** Le consumer notification a besoin de `id` (pour `notifications.user_id`) et **rien d'autre**. Une projection slim limite la surface de fuite (bio, interests, avatarUrl ne servent pas).

**Documentation.** Ajouter une entrée dans [`backend/docs/internal-endpoints.md`](backend/docs/internal-endpoints.md) numéro suivant disponible (vérifier au moment de l'impl).

| Option | Verdict |
|---|---|
| (a) Endpoint internal CSV, projection slim, cap 50 | ✅ retenu |
| (b) Endpoint public, ISSUE-93 anti-oracle complet | ❌ — coûteux, expose ce qui n'a pas à l'être |
| (c) N hops `GET /users/by-username/{u}` | ❌ — N+1, fragile vis-à-vis CB |

### Décision M — Mentions vers users avec `profilePublic=false` : on notifie quand même

**Décision.** Si Bob mentionne `@alice.dosh` et qu'Alice a `profilePublic=false`, **Alice reçoit la notification `COMMENT_MENTION`**. Le fait que son profil soit privé n'invalide pas la mention — c'est un signal d'attention, pas une consultation de profil.

Le clic dans la notif emmène Bob vers l'event ; s'il essaie ensuite d'aller sur le profil d'Alice, la règle ISSUE-93 anti-oracle s'applique normalement (404 si non-self non-admin).

**Justification.** L'inverse (skip si privé) créerait un oracle d'existence inverse : Bob saurait par absence-de-notif-livrée que `@alice.dosh` existe ET est privée. Trop coûteux, peu utile. Cohérent aussi avec `NEW_ATTENDEE` qui notifie un créateur potentiellement privé.

---

## 4. Inventaire des changements

### 4.1 OpenAPI (aucun changement)

| Fichier | Changement |
|---|---|
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | **AUCUN.** `NotificationType.COMMENT_MENTION` + `NEW_COMMENT` sont déjà déclarés (lignes 751-752). |

### 4.2 Backend — shared kafka-events

| Fichier | Type | Motif |
|---|---|---|
| [`CommentCreatedEvent.java`](backend/shared/kafka-events/src/main/java/ch/unige/events/shared/kafka/events/CommentCreatedEvent.java) | Update | + champs `String content`, `String eventTitle`. Mise à jour de la factory `created(...)` pour 6 args. Cf. Décision D. |
| [`CommentCreatedEventTest.java`](backend/shared/kafka-events/src/test/java/ch/unige/events/shared/kafka/events/CommentCreatedEventTest.java) | Update | + assertions sur les 2 nouveaux champs |

### 4.3 Backend — shared domain-dtos (REST client)

| Fichier | Type | Motif |
|---|---|---|
| [`UserServiceClient.java`](backend/shared/domain-dtos/src/main/java/ch/unige/events/shared/client/UserServiceClient.java) | Update | + méthode `getByUsernames(List<String> usernames)` → `GET /_internal-by-usernames?usernames=<csv>` (joiner côté caller via `String.join(",")`), retournant `List<IdProjection>`. Annotations : `@Retry(maxRetries=3, delay=200ms)`, `@Timeout(2s)`, `@CircuitBreaker(failureRatio=0.5, requestVolumeThreshold=10)`, `@Fallback(fallbackMethod="getByUsernamesFallback")`. Fallback retourne `List.of()` + log warn. |
| Test `UserServiceClientTest` (si existe ; sinon nouveau) | Update / Nouveau | Couvre la méthode, le fallback, la sérialisation CSV |

**Note** : `IdProjection` est actuellement `record IdProjection(UUID id)` (cf. usage dans `getInternalByAuth0Id`). Il faut soit l'élargir en `record IdProjection(UUID id, String username)` (impact transitif : SCRUM-99 caller `NotificationService.resolveUserId` lit uniquement `id` → safe), soit créer un nouveau record `UserIdProjection(UUID id, String username)`. **Décision implémenteur** : extension d'`IdProjection` préférée pour rester KISS (1 record, 2 usages).

### 4.4 Backend — user-service (endpoint internal)

| Fichier | Type | Motif |
|---|---|---|
| [`UserResource.java`](backend/services/user-service/src/main/java/ch/unige/events/user/resource/UserResource.java) | Update | + endpoint `GET /_internal-by-usernames` (cf. Décision L). Doc JavaDoc complète sur la sémantique. Gated par `InternalTokenFilter` (déjà présent globalement sur la classe via le pattern existant — vérifier que `_internal-by-auth0-id` et `_internal-attendee-projections` ne sont pas explicitement annotés et reposent sur le filter global). |
| [`UserService.java`](backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java) | Update | + méthode `getByUsernames(List<String> usernames)` retournant `List<User>` — wrapper `User.findByUsernames(usernames)` |
| [`User.java`](backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java) | Update | + finder statique `findByUsernames(Collection<String> usernames)` : `find("username IN ?1", usernames).list()`. Pas de tri imposé. Pas de pagination (cap appliqué côté Resource). |
| `IdProjection.java` (shared-domain-dtos) | Update | + champ `String username` (nullable pour back-compat avec usages existants). À valider que `getInternalByAuth0Id` reste fonctionnel (consumers lisent uniquement `.id()`). |
| [`UserResourceTest.java`](backend/services/user-service/src/test/java/ch/unige/events/user/resource/UserResourceTest.java) | Update | + 4 cas (cf. § 6 backend) — endpoint internal happy/cap/empty/invalid CSV |
| [`UserServiceTest.java`](backend/services/user-service/src/test/java/ch/unige/events/user/service/UserServiceTest.java) | Update | + 1 cas — `getByUsernames(...)` returns matched users only |

### 4.5 Backend — engagement-service (producer payload)

| Fichier | Type | Motif |
|---|---|---|
| [`CommentService.java`](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentService.java) | Update | Ligne 109-110 : mettre à jour le fire CDI pour passer `content` + `eventTitle` au record étendu. `eventTitle` lu sur `event.title()` (déjà chargé). |
| `CommentServiceTest.java` (ou équivalent ITs) | Update | Asserter que le payload Kafka émis contient `content` et `eventTitle` (mock CDI event listener) |
| `CommentCreatedKafkaBridge.java` | (vérifier) | Si le bridge transforme le record en bytes via Jackson : aucune modif nécessaire (record étendu = sérialisation additive). À confirmer au moment de l'impl. |

### 4.6 Backend — notification-service (consumers + parser)

| Fichier | Type | Motif |
|---|---|---|
| `MentionParser.java` (nouveau) | **Nouveau** | Classe `@ApplicationScoped` ou utility statique (au choix de l'implémenteur — `@ApplicationScoped` préféré pour injection dans tests). Méthode `extractHandles(String content): Set<String>`. Regex statique compilée. Cf. Décisions E + G. |
| `CommentMentionConsumer.java` (nouveau) | **Nouveau** | `@Incoming("comments-mentions")` + `@Transactional`. Algorithme : (1) parser → handles ; (2) si vide, return ; (3) `userClient.getByUsernames(handles.stream().toList())` ; (4) pour chaque match qui n'est PAS `ev.authorId()` : `userClient.getById(ev.authorId())` pour récupérer le label auteur (1 hop unique, sortable du loop), puis `notificationService.create(matchUserId, COMMENT_MENTION, eventId, authorId, message)`. Cf. Décisions E, J, M. |
| `NewCommentConsumer.java` (nouveau) | **Nouveau** | `@Incoming("comments-new")` + `@Transactional`. Algorithme : cf. Décision H. |
| `notification-service/src/main/resources/application.properties` | Update | + 2 blocs channel mapping (cf. Décision B). |
| `MentionParserTest.java` (nouveau) | **Nouveau** | Pure unit tests — pas `@QuarkusTest` nécessaire. Cf. § 6. |
| `CommentMentionConsumerTest.java` (nouveau) | **Nouveau** | `@QuarkusTest` + `@InjectMock UserServiceClient` + spy `NotificationService`. Cf. § 6. |
| `NewCommentConsumerTest.java` (nouveau) | **Nouveau** | `@QuarkusTest` + `@InjectMock EventServiceClient` + spy. Cf. § 6. |

### 4.7 Backend — documentation

| Fichier | Section | Modif |
|---|---|---|
| [`backend/docs/internal-endpoints.md`](backend/docs/internal-endpoints.md) | Table internal endpoints | + ligne `GET /users/_internal-by-usernames` (consumer : notification-service, gated InternalTokenFilter) |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Section `Notification.type` | Passer `COMMENT_MENTION` et `NEW_COMMENT` de « réservé non émis » à « émis (consumer = CommentMentionConsumer / NewCommentConsumer dans notification-service.kafka) » |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Section finale | Ajout section datée `2026-05-XX — SCRUM-145 livré (comment mentions + new comment notifications)`. Lister les fichiers touchés, les décisions clés, et la confirmation que SCRUM-128 (umbrella produit) reste à fermer côté Jira. |
| [`backend/docs/architecture.md`](backend/docs/architecture.md) | Section Kafka topics → consumers | Mettre à jour le tableau pour ajouter les 2 consumers de `comments.created`. |

### 4.8 Frontend (hors scope)

Aucune modification. Le frontend continue de recevoir des notifications via `GET /users/me/notifications` (déjà câblé). Les types `COMMENT_MENTION` + `NEW_COMMENT` sont déjà dans l'enum OpenAPI consommée par les types TS — pas de changement requis pour afficher ces nouvelles lignes (le composant de notif rend `message: string` brut).

Les tickets frontend S9+ à créer après merge de SCRUM-145 :
- `[FRONT][S9+] Autocomplete @<prefix> dans CommentForm` — consomme `GET /users/search?q=` existant
- `[FRONT][S9+] Rendu cliquable des mentions dans CommentItem` — transforme `@handle` → `<Link to="/profile/handle">` via parsing client-side

---

## 5. Plan d'exécution séquentiel (étapes numérotées, ordre strict)

> **Règle.** Un commit par étape. Format de message : `<type>(scrum-145): <description courte>`. Co-author Claude sur chaque commit. Vérification post-commit : la commande indiquée pour l'étape. Si elle échoue : revert local + fix + nouveau commit (pas d'amend).

### Étape 1 — Élargir le payload Kafka (`CommentCreatedEvent`)

- **Commit** : `feat(scrum-145): enrich CommentCreatedEvent with content and eventTitle`
- **Modifs** : `CommentCreatedEvent.java` + test associé.
- **Vérification** : `cd backend && ./mvnw -pl shared/kafka-events -am verify`.
- **Garde-fou** : aucune autre référence au record en production pour le moment (seul `CommentService.post` instancie via la factory). Le test confirme la sérialisation Jackson.

### Étape 2 — Adapter le producer (`CommentService.post`)

- **Commit** : `feat(scrum-145): pass content and eventTitle to CommentCreatedEvent at post time`
- **Modifs** : `CommentService.java` ligne 109-110.
- **Vérification** : `./mvnw -pl services/engagement-service -am verify`.

### Étape 3 — Élargir `IdProjection` avec `username`

- **Commit** : `feat(scrum-145): add username field to IdProjection record`
- **Modifs** : `IdProjection.java` + tests existants (assertions ajoutées).
- **Vérification** : `./mvnw -pl shared/domain-dtos -am verify`.

### Étape 4 — Endpoint internal `GET /users/_internal-by-usernames`

- **Commit** : `feat(scrum-145): add internal getByUsernames endpoint on user-service`
- **Modifs** : `User.findByUsernames`, `UserService.getByUsernames`, `UserResource.getInternalByUsernames` + tests.
- **Vérification** : `./mvnw -pl services/user-service -am verify`.

### Étape 5 — REST client `UserServiceClient.getByUsernames`

- **Commit** : `feat(scrum-145): add getByUsernames REST client method`
- **Modifs** : `UserServiceClient.java` + fallback + test.
- **Vérification** : `./mvnw -pl shared/domain-dtos -am verify`.

### Étape 6 — `MentionParser`

- **Commit** : `feat(scrum-145): add MentionParser for @handle extraction`
- **Modifs** : `MentionParser.java` + `MentionParserTest.java`.
- **Vérification** : `./mvnw -pl services/notification-service -am verify`.

### Étape 7 — `CommentMentionConsumer`

- **Commit** : `feat(scrum-145): wire CommentMentionConsumer on comments.created topic`
- **Modifs** : `CommentMentionConsumer.java` + channel config dans `application.properties` + `CommentMentionConsumerTest.java`.
- **Vérification** : `./mvnw -pl services/notification-service -am verify`.

### Étape 8 — `NewCommentConsumer`

- **Commit** : `feat(scrum-145): wire NewCommentConsumer on comments.created topic`
- **Modifs** : `NewCommentConsumer.java` + channel config + `NewCommentConsumerTest.java`.
- **Vérification** : `./mvnw -pl services/notification-service -am verify`.

### Étape 9 — Tests d'intégration (optionnel selon ce qui existe)

- **Commit** : `test(scrum-145): cover end-to-end comments.created → notification rows flow`
- **Modifs** : si le repo a déjà un harness multi-service in-memory Kafka (à vérifier au moment de l'impl) — sinon, sauter.
- **Vérification** : `./mvnw verify` reactor complet.

### Étape 10 — Documentation

- **Commit** : `docs(scrum-145): document mention consumer, internal endpoint, and update sprint context`
- **Modifs** : `internal-endpoints.md`, `data-model.md`, `architecture.md`, `sprint-context.md`.
- **Vérification** : `git diff` cohérent.

### Étape 11 — Vérification finale + push + PR

- **Pas un commit unique** — étape de vérif globale (cf. § 7).
- **Push** : `git push -u origin feature/scrum-145-comment-mentions`.
- **PR** : `gh pr create --base main --title "feat(scrum-145): wire CommentMention and NewComment notification consumers"`.

---

## 6. Tests

### 6.1 Backend — `MentionParserTest` (pur unit)

| Test | Input | Output attendu |
|---|---|---|
| `empty_returnsEmptySet` | `""` | `Set.of()` |
| `null_returnsEmptySet` | `null` | `Set.of()` |
| `noMentions_returnsEmptySet` | `"Hello world"` | `Set.of()` |
| `singleMention_simple` | `"Hello @alice.dosh"` | `{"alice.dosh"}` |
| `multipleMentions_dedup` | `"@alice.dosh @bob @alice.dosh"` | `{"alice.dosh"}` (bob < 3 chars → skip) |
| `multipleMentions_distinct` | `"@alice.dosh and @bob.smith"` | `{"alice.dosh","bob.smith"}` |
| `caseInsensitive_lowercased` | `"@Alice.Dosh and @ALICE.DOSH"` | `{"alice.dosh"}` |
| `punctuationTrailing_ok` | `"@alice.dosh, thanks!"` | `{"alice.dosh"}` |
| `emailLike_capturesDomain` | `"contact@example.com for help"` | `{"example.com"}` — sémantique acceptée (Décision E, silent skip à la résolution si pas user) |
| `consecutiveAts_capturesSecond` | `"@@alice.dosh"` | `{"alice.dosh"}` |
| `minLength_3_enforced` | `"@ab @abc"` | `{"abc"}` |
| `maxLength_30_enforced` | `"@" + "a".repeat(31)` | `{"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}` (capture jusqu'à 30 chars, ce qui ne matchera aucun user, silent skip) |
| `noBoundaryNeeded_atEndOfString` | `"@alice.dosh"` | `{"alice.dosh"}` |
| `preservesOrder_LinkedHashSet` | `"@bob.smith @alice.dosh"` | `["bob.smith","alice.dosh"]` (order check) |
| `accentedTextDoesNotCreateMention` | `"François a dit @alice.dosh"` | `{"alice.dosh"}` (les accents dans le texte ne créent pas de handle, charset n'inclut pas Unicode) |

### 6.2 Backend — `CommentMentionConsumerTest` (`@QuarkusTest`)

| Test | Setup | Assertion |
|---|---|---|
| `happyPath_singleMention_createsOne` | Event with `content="@alice.dosh hi"`, `userClient.getByUsernames(["alice.dosh"])` → `[{id=alice-uuid, username="alice.dosh"}]`, `getById(authorId)` → bob | 1 `notificationService.create(alice-uuid, COMMENT_MENTION, eventId, bobId, "Bob vous a mentionné dans un commentaire sur « X ».")` |
| `happyPath_multipleMentions_createsN` | `content="@alice.dosh @charlie.x"`, both resolve | 2 calls |
| `dedup_singleMentionForRepeats` | `content="@alice.dosh @alice.dosh @alice.dosh"` | 1 call |
| `selfMention_skipped` | `content="@bob.smith"`, authorId resolves to bob | 0 call (locked-in #11) |
| `unresolvedMention_skipped` | `content="@ghost.user"`, `getByUsernames` returns empty | 0 call, no exception |
| `mixedResolvedAndUnresolved` | `content="@alice.dosh @ghost"`, alice resolves, ghost doesn't | 1 call (alice only) |
| `noContent_skipped` | `ev.content() == null` | 0 call, no exception |
| `blankContent_skipped` | `ev.content() == ""` | 0 call |
| `noMentions_skipped` | `content="Just a comment"` | 0 call (no hop to user-service) |
| `userClient_throws_handledGracefully` | `getByUsernames` throws RuntimeException | log error, 0 call, consumer does NOT crash the channel |
| `authorResolutionFails_useFallbackLabel` | `getById(authorId)` returns null | notif created with `"Un utilisateur vous a mentionné…"` |
| `privateProfileTarget_stillNotified` | alice has `profilePublic=false` | notif created (Décision M) |

### 6.3 Backend — `NewCommentConsumerTest` (`@QuarkusTest`)

| Test | Setup | Assertion |
|---|---|---|
| `happyPath_creator_notified` | event creatorId=alice, ev.authorId=bob | 1 `notificationService.create(alice, NEW_COMMENT, eventId, bob, "Bob a commenté votre événement « X ».")` |
| `authorIsCreator_skipped` | event creatorId=alice, ev.authorId=alice | 0 call (locked-in #12) |
| `eventNotFound_skipped` | `eventClient.getById(eventId)` returns null | 0 call, log warn |
| `creatorIdNull_skipped` | event with null creatorId (defensive) | 0 call, log warn |
| `reply_stillNotifiesCreator` | `parentCommentId != null` | 1 call (locked-in #9 — replies trigger NEW_COMMENT) |
| `authorResolutionFails_useFallback` | `getById(authorId)` returns null | notif created with `"Un utilisateur"` label |
| `creatorWithPrivateProfile_stillNotified` | creator has `profilePublic=false` | notif created (Décision M-bis) |
| `overlapWithMention_independentNotif` | content `"@alice.dosh"`, alice is creator, bob is author | NewCommentConsumer creates 1 notif (alice gets NEW_COMMENT) — orthogonal au CommentMentionConsumer qui crée séparément un COMMENT_MENTION |

### 6.4 Backend — `UserResource` extensions (`UserResourceTest`)

| Test | Assertion |
|---|---|
| `internalByUsernames_happyPath` | `GET /users/_internal-by-usernames?usernames=alice.dosh,bob.smith` (avec X-Internal-Token) → 200 + body 2 IdProjection |
| `internalByUsernames_missingDropped` | `?usernames=alice.dosh,ghost` → 200 + body 1 IdProjection (alice seulement) |
| `internalByUsernames_capRespected` | 51 handles → seuls les 50 premiers résolus (ou 400 si on choisit strict — implémentation : silent cap au stream `.limit(50)`) |
| `internalByUsernames_empty_returnsEmpty` | `?usernames=` → 200 + body `[]` |
| `internalByUsernames_withoutInternalToken_returns401` | Pas de X-Internal-Token → 401 (gated par filter) |
| `internalByUsernames_caseInsensitive` | `?usernames=Alice.Dosh` matche row `alice.dosh` |
| `internalByUsernames_dedupInQuery` | `?usernames=alice.dosh,alice.dosh` → 200 + body 1 IdProjection |

### 6.5 Backend — `UserServiceClient.getByUsernamesTest`

| Test | Assertion |
|---|---|
| `serializesCsv` | `getByUsernames(["alice.dosh","bob.smith"])` → query string `usernames=alice.dosh%2Cbob.smith` |
| `fallback_returnsEmptyOnFailure` | Downstream timeout → `List.of()` + log warn |
| `parses200_returnsProjections` | Mock 200 + body 2 IdProjection → return as-is |

### 6.6 Cas-limites explicites couverts (récap)

- Self-mention → silent skip (§ 6.2 `selfMention_skipped`).
- Mention de handle inexistant → silent skip (§ 6.2 `unresolvedMention_skipped`).
- Dédup `@x @x @x` → 1 notif (§ 6.2 `dedup_singleMentionForRepeats`).
- Reply qui déclenche `NEW_COMMENT` (§ 6.3 `reply_stillNotifiesCreator`).
- Auteur = créateur → skip `NEW_COMMENT` (§ 6.3 `authorIsCreator_skipped`).
- Mention + organizer overlap → 2 notifs distinctes (§ 6.3 `overlapWithMention_independentNotif`).
- Performance : N mentions → 1 hop batché user-service (assertion implicite — un seul `getByUsernames` appelé dans la suite).
- Downstream user-service down → fallback liste vide, pas de crash (§ 6.2 `userClient_throws_handledGracefully`).
- Profil privé reçoit quand même la notif (§ 6.2 `privateProfileTarget_stillNotified` + § 6.3 `creatorWithPrivateProfile_stillNotified`).

---

## 7. Critères de done (checklist à exécuter avant `gh pr create`)

- [ ] `cd backend && ./mvnw verify` — reactor complet 15 modules, SUCCESS.
- [ ] `cd backend && ./mvnw -pl services/notification-service -am verify` — focus avec ITs.
- [ ] `cd backend && ./mvnw -pl services/user-service -am verify` — focus avec ITs.
- [ ] `cd backend && ./mvnw -pl shared/kafka-events -am verify` — record étendu OK.
- [ ] `cd backend && ./mvnw -pl shared/domain-dtos -am verify` — REST client OK.
- [ ] **Pas de modif de `openapi/openapi.yaml`** : `git diff origin/main HEAD -- openapi/openapi.yaml` → 0 ligne.
- [ ] Jacoco ≥ 80 % L sur le code nouveau (notification-service + user-service deltas).
- [ ] `find backend/services -name '*Stub.java'` = ∅ (invariant projet).
- [ ] `ls backend/services/notification-service/src/main/resources/db/migration` montre V1, V2 — aucune V3 ajoutée par cette PR (CHECK constraint déjà élargi).
- [ ] `git diff` sur la doc cohérent : `internal-endpoints.md`, `data-model.md`, `architecture.md`, `sprint-context.md`.
- [ ] `git status` propre, pas de `.env`, pas de fichiers générés committés.
- [ ] PR ouverte avec titre `feat(scrum-145): wire CommentMention and NewComment notification consumers`, body template GitHub rempli.
- [ ] **Pas de merge** par l'agent. Daniel merge lui-même.
- [ ] Boucle review Copilot itérée jusqu'à 0 BLOQUANT / 0 IMPORTANT non-clos.

---

## 8. Workflow Git

- **Branche** : `feature/scrum-145-comment-mentions`, base = `main`.
- **1 commit par étape** du Plan d'exécution.
- **Format de message** : `<type>(scrum-145): <description courte>`. Types autorisés : `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `perf`. Scope obligatoire `scrum-145` pour `feat`/`refactor`/`perf`.
- **Co-author Claude** sur chaque commit (HEREDOC standard).
- **Push** : `git push -u origin feature/scrum-145-comment-mentions`.
- **PR** : `gh pr create --base main --title "..." --body "$(cat <<'EOF' ... EOF)"`.
- **Pas de merge** par l'agent.

---

## 9. Risques et concerns

### Risques techniques

- **Compatibilité Kafka backward-compat** : enrichir `CommentCreatedEvent` ajoute 2 champs. Jackson tolère côté consumer (champs additifs), mais si **engagement-service ou notification-service sont déployés à des moments différents en production**, un mismatch est possible :
  - Si `engagement-service` n'est pas encore upgradé → publie l'ancien payload sans `content`/`eventTitle` → le consumer reçoit `null`/`null` → `MentionParser.extractHandles(null)` retourne `Set.of()` → 0 mention notifiée. Le `NEW_COMMENT` use case dégrade : pas de `eventTitle` → fallback `"Un utilisateur a commenté un événement"` ? **Implémenteur** : valider que `event.title()` est null-safe ou que le consumer falsh à un placeholder. Préférable : déployer engagement-service AVANT notification-service.
  - Si `notification-service` est upgradé mais pas `engagement-service` → idem cas ci-dessus.
- **Latence du parsing** : regex compilée statique → O(n) sur la longueur du commentaire (max 500 chars). Coût négligeable.
- **Hop user-service.getByUsernames** : 1 hop par comment **qui contient des mentions**. Les commentaires sans `@` court-circuitent (`extractHandles` vide → return). Acceptable.
- **Hop user-service.getById** pour le label auteur : 1 hop additionnel par comment. Idem `AttendanceCreatedConsumer`. Si volume devient un souci, mettre en cache TTL court — out-of-scope MVP.

### Risques produit

- **Rename de username** (locked-in #4 réconciliation : actuel = mutable) : si Alice change son username de `alice.dosh` → `alice.m`, les commentaires existants qui contenaient `@alice.dosh` deviennent "morts" — le rendu cliquable côté frontend (S9+) pointera vers `/profile/alice.dosh` qui retournera 404 (`getByUsername`). Pour les notifs : aucun problème, elles ont déjà été envoyées au moment du POST. **Acceptable.**
- **Mention vers un user supprimé** : équivalent au profil privé non-trouvé. Silent skip côté consumer.
- **Spam de notifs via commentaires répétés** : SCRUM-139 / SCRUM-146 ont déjà cap à 500 chars et rate-limit côté `POST /events/{eventId}/comments`. Le `@PerUserRateLimit` borne le rate. Acceptable.
- **Commentaires antérieurs à SCRUM-145 (pré-déploiement)** : aucun backfill. Les notifs ne sont déclenchées qu'à la publication, pas rétroactivement. Document explicitement dans le sprint-context et la release note.

### Risques d'opération

- **Topic `comments.created` partition strategy** : déjà partitionné par `eventId` (cf. JavaDoc `CommentCreatedEvent`). 2 group-ids différents = 2 consumers lisent indépendamment, pas de contention. OK.
- **Replay** : si un consumer plante pendant le traitement et le offset n'est pas committé, Kafka redélivre — le `@Transactional` garantit que la notif n'est créée que si la transaction commit. La dédup côté DB est inexistante (Décision C) → ré-livraison = double notif (acceptable).
- **Observabilité** : log `[NOTIF_COMMENT_MENTION] notified=<UUID> for event=<id> (mention=@<handle>, author=<UUID>)` et `[NOTIF_NEW_COMMENT] notified creator=<UUID> for event=<id> (author=<UUID>, parent=<long?>)`. Cohérent avec le style de [`AttendanceCreatedConsumer:62`](backend/services/notification-service/src/main/java/ch/unige/events/notification/kafka/AttendanceCreatedConsumer.java#L62).

---

## 10. Future work / known follow-ups

| Ticket suggéré | Description |
|---|---|
| `[FRONT][S9+]` Mention autocomplete | Dans `CommentForm`, détecter `@<prefix>` en cours de frappe, hit `GET /users/search?q=<prefix>`, dropdown sélection, insertion `@<handle> ` |
| `[FRONT][S9+]` Rendu cliquable des mentions | Dans `CommentItem`, parser le contenu côté client (regex miroir backend) et wrapper chaque `@handle` dans `<Link to="/profile/handle">` |
| `[BACK/FRONT][S10+]` Préférences de notification par event | Mute des `COMMENT_MENTION` ou `NEW_COMMENT` sur un event donné — entité `NotificationPreference` |
| `[BACK][S10+]` `NEW_COMMENT` étendu aux co-organisateurs ACCEPTED | Locked-in #9 acte « créateur primaire seul » — extension produit décidée par PO |
| `[BACK][S10+]` Notification push / email | Aujourd'hui in-app feed only — extensions natives Quarkus mail / push optionnelles |
| `[BACK][S10+]` Reconciler SCRUM-128 et SCRUM-145 côté Jira | Choisir entre fermer SCRUM-128 en doublon de SCRUM-145, ou utiliser SCRUM-128 comme umbrella produit (mentions visibles bout-en-bout) et SCRUM-145 comme sous-ticket BACK |

---

## 11. Garde-fous

- **Aucune action destructive** : pas de `rm -rf`, `git reset --hard`, `--no-verify`, force-push.
- **Pas de modif du contrat OpenAPI public** : enum `NotificationType` déjà aligné. Toute envie d'ajouter un endpoint public dans cette PR doit être stoppée et discutée avec Daniel.
- **Pas de migration Flyway dans cette PR** : la widening V2 a anticipé cette feature. Si l'implémenteur sent le besoin d'une migration → revenir voir Daniel avant.
- **Pas de stub JPA cross-service** (`find backend/services -name '*Stub.java' = ∅`).
- **Pas de Kafka emission in-transaction** : `notificationService.create(...)` insère en DB, c'est tout — pas de fan-out Kafka secondaire depuis ces consumers.
- **Cohérence doc / code** : touche au code = touche à la doc dans le même commit.

---

## 12. Recommended Jira restructure (à exécuter par Daniel)

> **Constat.** Le repo référence SCRUM-145 comme l'identifiant Jira de cette feature (cf. `NotificationType.java` + `V2__widen_notification_type_check.sql`). Le prompt fait référence à SCRUM-128. Les deux peuvent coexister si on les positionne correctement.

### Option A — Fusion (recommandée)

Fermer SCRUM-128 comme duplicate de SCRUM-145. Conserver SCRUM-145 comme le ticket unique implémenté par cette PR.

### Option B — Hiérarchie (alternative)

Garder SCRUM-128 comme **umbrella** (Story produit : « bout-en-bout mentions dans les commentaires », couvre back + front) et créer SCRUM-145 comme **child task** BACK :

- **SCRUM-128 (Story)** : reste produit-facing. Decomposed in :
  - **SCRUM-145 (Task, BACK)** : ce qui est livré par cette PR.
  - **`SCRUM-XXX` (Task, FRONT)** : autocomplete + rendu cliquable (à créer).

### Suggested Jira ticket body (à coller, pour SCRUM-145 si pas encore créé)

```
Title: [BACK][S9] Notifications de mention dans les commentaires + NEW_COMMENT (phase 3 chantier notifications)

Story Points: 8 (à valider)

Description:
Phase 3 du chantier notifications (SCRUM-99 phase 1 + SCRUM-140 phase 2 livrés).
Câble les deux derniers types réservés côté schema : COMMENT_MENTION et NEW_COMMENT.

Pré-requis livrés :
- SCRUM-169 : système de username (PR #172).
- SCRUM-140 : enum NotificationType + Flyway V2 (CHECK constraint élargi à 9 valeurs,
  incluant COMMENT_MENTION et NEW_COMMENT en réservé).
- SCRUM-139 : entité Comment + topic Kafka comments.created.

Périmètre :
- Backend only. Frontend (autocomplete + render cliquable) = tickets séparés.
- Spec détaillée : specs_archives/specs_claude/specs_scrum-128-145-comment-mentions.md
- 2 nouveaux Kafka consumers (CommentMentionConsumer, NewCommentConsumer) sur
  comments.created.
- 1 nouvel endpoint internal user-service (GET /users/_internal-by-usernames).
- Enrichissement du payload Kafka CommentCreatedEvent (content + eventTitle).
- Pas de migration Flyway requise.

Critères d'acceptation :
- COMMENT_MENTION émis pour chaque user mentionné (déduplique, skip self, skip privé
  inclus, silent skip si handle inconnu).
- NEW_COMMENT émis au créateur primaire (skip si auteur=créateur, top-level + reply).
- Templates FR conformes à la spec.
- Couverture ≥ 80 % L sur le nouveau code (Sonar).
- Pas de modif de openapi.yaml.

Dépendances :
- Bloque les tickets frontend S9+ (autocomplete @, render cliquable).
```

---

## 13. Open questions

Aucune ambiguïté bloquante détectée au moment de la rédaction. Les seuls points à clarifier avec Daniel **avant** de lancer l'implémentation :

1. **Identifiant Jira final** : SCRUM-128 (re-ouvert / repurposed) ou SCRUM-145 (déjà référencé dans le code) ? Voir § 12 pour les options.
2. **Stratégie de déploiement coordonné** engagement-service vs notification-service pour éviter la fenêtre de payload Kafka mixte (pré-/post-enrichissement). Probablement non-bloquant en preview (les deux services redeploy ensemble via Argo), mais à valider sur le pipeline prod.
3. **`IdProjection` étendu (+ `username`) vs nouveau record `UserIdProjection`** : préférence implémenteur ; aucune des deux options ne casse un caller existant, mais la fusion ajoute un champ nullable qui pollue légèrement le namespace. Validation cosmétique.

---

## 14. Skills à utiliser (lors de l'implémentation, dans une session séparée)

| Skill | Quand |
|---|---|
| `superpowers:executing-plans` | Itérer méthodiquement étape par étape du § 5 |
| `superpowers:test-driven-development` | Pour chaque consumer (étapes 7-8) — écrire le test d'abord |
| `superpowers:systematic-debugging` | Si un test Kafka casse de manière inattendue |
| `superpowers:verification-before-completion` | **Obligatoire** avant chaque claim "done" et avant `gh pr create` — exécuter toutes les commandes du § 7 |
| `superpowers:requesting-code-review` + `pr-review-toolkit:review-pr` | Une fois la PR ouverte, lancer la boucle Copilot |
| `superpowers:receiving-code-review` | Traiter les retours Copilot avec rigueur |
| `superpowers:finishing-a-development-branch` | Décider du moment exact de push + ouverture PR |
| `github` MCP | `gh pr create`, `gh pr checks --watch`, `gh api .../pulls/.../comments` |

---

## Launch prompt (literal, à copier-coller pour lancer l'implémentation dans une session séparée)

````markdown
Implémente SCRUM-145 (notifications de mention + new-comment) en autonomie complète
selon la spec `specs_archives/specs_claude/specs_scrum-128-145-comment-mentions.md`.

Étapes :

1. Lis la spec en entier avant de toucher au moindre fichier. Internalise les
   Décisions techniques A → M et le Plan d'exécution séquentiel (§ 3 et § 5).
2. Lis `AGENTS.md` (racine + `backend/AGENTS.md`) pour les conventions de commit,
   scope, doc à toucher.
3. Crée la branche `feature/scrum-145-comment-mentions` depuis `main`.
4. Vérifie le § 0 (Reconciliation) — n'introduis pas de logique qui re-décide
   ce qui a déjà été décidé par SCRUM-169 (format de handle, mutabilité, etc.).
5. Exécute chaque étape du Plan dans l'ordre exact (§ 5, étapes 1-11). Un commit
   par étape, format `<type>(scrum-145): <description>` avec co-author Claude.
   Vérifie après chaque commit avec la commande indiquée
   (`./mvnw -pl <module> -am verify`).
6. Étape 1 (élargissement Kafka payload) : attention à mettre à jour le bridge
   engagement-service dans le même commit pour éviter une fenêtre de payload
   mixte en production.
7. Avant `gh pr create` (étape 11) : `superpowers:verification-before-completion`
   non négociable — exécute toutes les commandes de la section 7 (Critères de
   done) et confirme chaque ligne. Aucun claim "done" sans cette verification.
8. Ouvre la PR via `gh pr create --base main --title "feat(scrum-145): wire
   CommentMention and NewComment notification consumers"` + body issu du template
   `.github/pull_request_template.md` (sections Résumé, Changements, Tests,
   Test plan, Documentation obligatoires). **Pas de merge.**
9. Lance la boucle review Copilot : `gh pr checks <PR#> --watch`, puis
   `gh api repos/<org>/unige-events/pulls/<PR#>/comments` pour chaque retour.
   Itère jusqu'à 0 BLOQUANT / 0 IMPORTANT non-clos.
10. Quand tous les checks sont verts et la review propre : signale-moi avec le
    lien de la PR et un résumé des commits livrés. **Je merge moi-même.**

Garde-fous (rappel) :
- Aucune action destructive sans confirmation explicite.
- Aucune modification de `openapi/openapi.yaml` (l'enum est déjà élargi).
- Aucune migration Flyway dans cette PR (V2 a anticipé).
- Si la doc dérive du code → fix dans le même commit.
- Si un cas non couvert par la spec émerge : documente-le dans `sprint-context.md`
  (section datée finale) et continue ; ne me réveille pas pour des
  micro-arbitrages.
````
