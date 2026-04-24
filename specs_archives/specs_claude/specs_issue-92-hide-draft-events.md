# Specs ISSUE-92 — Cacher les events DRAFT / CANCELLED sur `GET /api/events/{id}` (Pentest 4.12 + 4.15)

> **Branche :** `feature/ISSUE-92-hide-draft-events`
> **Base :** `origin/main`
> **Sprint :** S6 — Hotfix sécurité post-pentest
> **Findings couverts :** 4.12 (anonymous read of DRAFT events via direct ID, Medium) + 4.15 (sequential integer event IDs, Low — cesse d'être exploitable une fois 4.12 corrigé)
> **Rapport :** `unige-events-pentest-report.md` — audit du 2026-04-17 par Elie Bussod
> **Règle d'or :** Modifier `openapi/openapi.yaml` EN PREMIER, puis Resource → Service → Tests. Doc dans le **même commit** que le code.

---

## Contexte

### La faille

`GET /api/events/{id}` est annoté `@PermitAll` (cf. [`EventResource.java:82`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L82)) et `EventService.getById(Long id)` ([`EventService.java:133-139`](backend/src/main/java/ch/unige/events/service/EventService.java#L133-L139)) n'applique **aucune vérification de statut**. N'importe qui, y compris un appelant non authentifié, peut lire un event `DRAFT` ou `CANCELLED` en connaissant (ou en énumérant) son ID.

Finding **4.15** précise que les IDs d'events sont des `Long` auto-incrémentés séquentiels. Une boucle `for id in 1..1000` suffit donc à lister **tous les brouillons et events annulés** de la plateforme en quelques secondes.

Reproduction, vérifiée le 2026-04-17 (extrait du rapport) :

```bash
# 1. User B crée un brouillon
curl -H "Authorization: Bearer $TOKEN_B" -H "Content-Type: application/json" \
     -d '{"title":"PENTEST-B-target","description":"secret draft","startDate":"2030-01-01T10:00:00","endDate":"2030-01-01T12:00:00","location":"X","category":"OTHER"}' \
     https://pinfo6.duckdns.org/api/events
# → 201 Created, id: 304, status: "DRAFT"

# 2. Appelant NON authentifié lit le brouillon
curl https://pinfo6.duckdns.org/api/events/304
# → 200 OK, payload DRAFT complet (title, description, location, creatorId, …)
```

### La correction structurelle

Un event avec `status != PUBLISHED` devient visible **uniquement** par :
- son créateur (`event.creator.auth0Id == auth0Id` de l'appelant) **OU**
- un administrateur (`identity.hasRole("ADMIN")`)

Dans tout autre cas — y compris appelant anonyme, appelant authentifié non-créateur non-admin, et appelant authentifié dont le profil User n'est pas provisionné en DB — le service **throw `NotFoundException`**, ce qui produit un **`404 not_found`** avec l'envelope standard.

**Statuts couverts** : `DRAFT` **et** `CANCELLED`. Seul `PUBLISHED` reste anon-accessible. Un event `CANCELLED` est du contenu historique sensible (noms d'invités déjà annoncés, coûts internes de salle, collaborateurs confidentiels — les mêmes risques que pour un `DRAFT`) : aucune raison produit de le laisser exposé.

### Pourquoi 404 et pas 403

Renvoyer `403` distinguerait « l'event existe mais vous n'avez pas le droit » de « l'event n'existe pas » — c'est un **oracle d'existence**. Exactement la critique du rapport de pentest sur finding 4.1 (`403` vs `404` sur `GET /users/{id}` privé). En retournant `404 not_found` dans les deux cas, l'appelant ne peut plus confirmer qu'un ID correspond à un event caché ; l'énumération séquentielle mentionnée en 4.15 devient indistinguable d'une plage d'IDs inexistants.

Oracle résiduel assumé : un attaquant sophistiqué pourrait théoriquement distinguer « event PUBLISHED existe » vs « event inexistant » via le timing (les deux branches lisent la DB, mais la branche PUBLISHED mappe un DTO complet). **Hors scope** — le but est de ne plus distinguer « existe mais caché » vs « n'existe pas », pas « existe » vs « n'existe pas ».

### Pourquoi pas d'UUIDs opaques

L'alternative « passer les IDs d'events en UUID opaques » (mentionnée en 4.15) nécessite : migration DB, regénération des FKs dans `favorites`, `attendances`, `event_tags`, mise à jour de tous les DTOs, des URLs frontend, des liens partagés déjà émis, et des tokens de partage (`shareCode`). Surface de changement énorme, indépendante de la faille elle-même. **Hors scope.** Une fois 4.12 corrigé, 4.15 cesse d'être exploitable : l'énumération n'expose plus rien.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### 1. Signature du service : unique, 3 paramètres

**Décision.** `EventService.getById` passe de `getById(Long id)` à `getById(Long id, String auth0Id, boolean isAdmin)`. Pas d'overload, pas de méthode de convenance.

**Justification.** Aligné avec le pattern déjà en place dans le même fichier pour `publish(Long id, String auth0Id, boolean isAdmin)` ([`EventService.java:236`](backend/src/main/java/ch/unige/events/service/EventService.java#L236)) et `uploadImage(Long id, String auth0Id, FileUpload, boolean isAdmin)` ([`EventService.java:286`](backend/src/main/java/ch/unige/events/service/EventService.java#L286)). Introduire un overload diluerait la règle de sécurité et inviterait un futur appelant à passer par la version sans garde. Le coût est de mettre à jour **12 call-sites internes** (tous dans les tests — voir §5), qui deviennent `eventService.getById(id, null, false)` pour les cas où la sémantique « admin » est suffisante en lecture. Précédent direct : SCRUM-131 a étendu la signature de `EventSearchService.search(...)` avec un nouveau paramètre en forçant la mise à jour de 16 call-sites existants.

### 2. Lieu de la vérification : dans le Service

**Décision.** La règle d'autorisation vit dans `EventService.getById`. La Resource se limite à résoudre `auth0Id` / `isAdmin` depuis `SecurityIdentity` et à les transmettre.

**Justification.** La règle dépend d'un état lu en DB (`event.status`, `event.creator`). Le helper `isCreator(Event, String auth0Id)` existe déjà dans le Service ([`EventService.java:343-347`](backend/src/main/java/ch/unige/events/service/EventService.java#L343-L347)) et est utilisé partout (update, delete, cancel, restore, publish). Dupliquer la logique dans la Resource créerait deux sources de vérité. De plus, toute logique métier dans une Resource viole le principe rappelé dans [`backend/AGENTS.md`](backend/AGENTS.md) (« Jamais de saut de couche. La logique métier est dans le Service »).

### 3. Enveloppe d'erreur 404 : envelope standard, réutilisée

**Décision.** `throw new NotFoundException()` — sans message custom. Le mapper existant ([`NotFoundExceptionMapper.java`](backend/src/main/java/ch/unige/events/exception/mapper/NotFoundExceptionMapper.java)) produit :

```json
{ "error": "not_found", "message": "Profile not found" }
```

**Justification.** L'envelope est identique pour « l'event n'existe pas » et « l'event existe mais vous ne pouvez pas le voir » — c'est précisément ce qu'on veut pour fermer l'oracle d'existence. Introduire un nouveau code d'erreur (`not_visible`, `private_draft`, …) créerait l'oracle qu'on cherche à éviter. Le message générique « Profile not found » est sous-optimal pour un event, mais le changer globalement impacterait toutes les 404 existantes — **hors scope**. Ne pas customiser le message via `new NotFoundException("message")` : cela injecterait un texte spécifique au chemin authentifié qui divergerait du texte d'un 404 « vrai ».

### 4. Statuts couverts : DRAFT + CANCELLED

**Décision.** La règle s'applique dès lors que `event.status != PUBLISHED`. Donc DRAFT et CANCELLED sont cachés ; seul PUBLISHED est anon-accessible.

**Justification.** L'issue GitHub le demande explicitement (critère d'acceptation n°5). Un event CANCELLED contient souvent les mêmes données sensibles qu'un DRAFT en cours (voire plus, puisqu'il a été complètement renseigné avant annulation). Un futur statut `ARCHIVED` ou `HIDDEN` retomberait automatiquement dans la même règle (« tout ce qui n'est pas PUBLISHED »), ce qui est robuste à l'évolution de l'enum.

### 5. User authentifié sans profil en DB : traité comme non-identifié

**Décision.** Si `auth0Id != null` mais `User.findByAuth0Id(auth0Id)` retourne `Optional.empty()`, le service **ne throw pas** : il continue la comparaison. `isCreator(event, auth0Id)` retournera `false` (parce que `event.creator.auth0Id` ne matchera pas un utilisateur inconnu). Résultat : 404 sur un event non-PUBLISHED, 200 sur un PUBLISHED.

**Justification.** Un utilisateur Auth0 sans profil ne peut **pas** être créateur d'un event par construction (la FK `event.creator_id` référence `users.id` — un event sans créateur provisionné est impossible en DB). Donc traiter ce cas comme « ne correspond à aucun créateur » est exact. **Important** : ne pas réutiliser le pattern `User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new)` (cf. [`EventService.java:85-86`](backend/src/main/java/ch/unige/events/service/EventService.java#L85-L86)) car cela produirait un 404 sur un event **PUBLISHED** demandé par un utilisateur authentifié non provisionné, ce qui serait une régression (ce cas existe : un utilisateur fraîchement loggé dont le provisioning `GET /users/me` n'a pas encore tourné).

En pratique, la vérification ne lance même pas la requête DB `findByAuth0Id` — voir §6 ci-dessous.

### 6. Comparaison `isCreator` : par `auth0Id`, pas par UUID

**Décision.** Utiliser directement le helper `isCreator(event, auth0Id)` existant, qui compare `event.creator.auth0Id` au `auth0Id` passé. **Pas d'appel à `User.findByAuth0Id`.**

**Justification.** Une comparaison de `String auth0Id` est O(1) sur l'objet `Event` déjà chargé (la relation `creator` est `@ManyToOne(LAZY)` mais `creator.auth0Id` est un champ direct qui, avec Hibernate, déclenche au plus une seule requête lazy-load). Appeler `User.findByAuth0Id` ajouterait une requête DB inutile pour extraire un UUID qu'on n'utilise pas, et ouvrirait le cas 5 ci-dessus. Le pattern est déjà en place pour `update`, `delete`, `cancel`, `restore`, `publish` — on reste cohérent.

### 7. Admin : détection via `identity.hasRole("ADMIN")`

**Décision.** La Resource lit `identity.hasRole("ADMIN")` et transmet le booléen au Service. Pas de nouveau rôle à définir, pas de claim custom à parser.

**Justification.** Pattern déjà en place pour `publish` ([`EventResource.java:133`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L133)) et `uploadImage` ([`EventResource.java:144`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L144)). Le rôle `ADMIN` est connu du backend (mentionné dans les messages d'erreur existants — cf. rapport de pentest finding 4.20). Aucun token `STUDENT` actuel ne peut produire ce rôle, mais la branche reste testable via `@TestSecurity(user = "auth0|admin", roles = {"ADMIN"})` — pattern déjà utilisé dans [`EventResourceTest.java:556`](backend/src/test/java/ch/unige/events/resource/EventResourceTest.java#L556). Aucune dépendance au provisioning Auth0 réel.

### 8. Extraction de `auth0Id` et `isAdmin` côté Resource : `isAnonymous()`-safe

**Décision.** La Resource résout :

```java
String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
boolean isAdmin = !identity.isAnonymous() && identity.hasRole("ADMIN");
```

**Justification.** `SecurityIdentity` est toujours injecté par Quarkus, même sans JWT — mais `identity.getPrincipal()` retourne un principal `Anonymous` dans ce cas. `identity.isAnonymous()` est le test standard et ne lève pas d'exception sur un appelant non authentifié (l'endpoint reste `@PermitAll`). Appeler `identity.getPrincipal().getName()` sur un identity anonyme retournerait la chaîne `"Anonymous"` — une valeur qui ne matche aucun `auth0Id` mais qui est fragile à comparer. Passer `null` explicitement rend la sémantique « pas de caller identifié » lisible dans le Service.

### 9. Pas de log, pas de metric sur le rejet

**Décision.** Aucun log INFO/WARN n'est ajouté lorsque la règle renvoie 404.

**Justification.** Aligné sur la décision 9 de [`specs_scrum-133.md`](specs_archives/specs_claude/specs_scrum-133.md). Logger chaque refus générerait du bruit applicatif (un scanner énumérant les IDs produit un flood de warnings) sans valeur actionnable. Si un audit sécurité devient nécessaire, un compteur Micrometer `events.hidden.access_denied` pourra être ajouté au Sprint 7+.

---

## Analyse de l'existant

### Ce qui existe déjà (à réutiliser)

| Élément | Fichier / ligne | Rôle |
|---|---|---|
| `@PermitAll` sur `GET /events/{id}` | [`EventResource.java:82`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L82) | Endpoint déjà anon-accessible — à conserver |
| Injection `SecurityIdentity identity` | [`EventResource.java:35,40`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L35) | Déjà câblée — pas de nouveau `@Inject` |
| Pattern `identity.hasRole("ADMIN")` | [`EventResource.java:133,144`](backend/src/main/java/ch/unige/events/resource/EventResource.java#L133) | Pattern exact à reprendre dans `getById` |
| Helper `isCreator(Event, String)` | [`EventService.java:343-347`](backend/src/main/java/ch/unige/events/service/EventService.java#L343-L347) | Utilisé par update/delete/cancel/restore/publish — à réutiliser tel quel |
| `NotFoundExceptionMapper` | [`NotFoundExceptionMapper.java`](backend/src/main/java/ch/unige/events/exception/mapper/NotFoundExceptionMapper.java) | Produit l'envelope `{"error":"not_found","message":"..."}` |
| `EventServiceMock.getById` | [`EventServiceMock.java:128-135`](backend/src/test/java/ch/unige/events/service/EventServiceMock.java#L128-L135) | Mock unitaire — signature à aligner |
| Helpers de test `persistUser` / `persistEvent` | [`EventServiceCoverageTest.java:1180-1208`](backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java#L1180-L1208) | Seeding DB pour tests d'intégration DevServices |
| Pattern de test de sécurité multi-users | [`EventResourceTest.java:419,476,556`](backend/src/test/java/ch/unige/events/resource/EventResourceTest.java#L419) | `@TestSecurity(user = "auth0|alice")` / `"auth0|bob"` / `"auth0|admin", roles = {"ADMIN"}` |

### Ce qui est à modifier

| Fichier | Modification |
|---|---|
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | Path `/events/{id}` GET (ligne ~1319) : durcir la `description`, ajouter `security: [{}, {BearerAuth: []}]` (auth optionnelle) |
| [`backend/src/main/java/ch/unige/events/resource/EventResource.java`](backend/src/main/java/ch/unige/events/resource/EventResource.java) | `getById(...)` (lignes 80-86) : extraire `auth0Id` / `isAdmin` de `identity` et les transmettre |
| [`backend/src/main/java/ch/unige/events/service/EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) | `getById(...)` (lignes 133-139) : nouvelle signature à 3 params + contrôle d'accès basé sur statut |
| [`backend/src/test/java/ch/unige/events/service/EventServiceMock.java`](backend/src/test/java/ch/unige/events/service/EventServiceMock.java) | Override `getById` (lignes 128-135) : aligner la signature + appliquer la même règle in-memory |
| [`backend/src/test/java/ch/unige/events/resource/EventResourceTest.java`](backend/src/test/java/ch/unige/events/resource/EventResourceTest.java) | Section `// --- GET /events/{id} ---` (ligne 212) : ajouter 8 nouveaux tests sécurité |
| [`backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java) | Mettre à jour les **6 call-sites** existants (ligne 328, 338, 995, 1009, 1089, 1112) + ajouter ~8 nouveaux tests intégration |
| [`backend/src/test/java/ch/unige/events/service/AttendanceServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/AttendanceServiceCoverageTest.java) | Mettre à jour les **6 call-sites** existants (lignes 264, 271, 278, 296, 492, 505) |
| [`backend/docs/api-contract.md`](backend/docs/api-contract.md) | Ajouter une ligne pour `GET /events/{id}` dans la table « Endpoints implémentés » + section détaillée |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Enrichir la section Event avec la règle d'autorisation sur `status` |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Entrée ISSUE-92 — hotfix pentest 4.12 + 4.15 |

### Ce qui n'est PAS dans le scope

- ❌ Pas de migration Flyway (aucun changement de schéma).
- ❌ Pas de modification de `EventDTO` ni ajout de nouveau champ.
- ❌ Pas d'UUIDs opaques pour les IDs d'events (finding 4.15 alternative écartée).
- ❌ Pas de changement de `@PermitAll` → `@Authenticated` sur `GET /events/{id}` — l'endpoint **doit** rester anon-accessible pour les events PUBLISHED (non-régression pour les vues publiques de détail).
- ❌ Pas de modification frontend. [`useEvent.ts`](frontend/src/hooks/useEvent.ts) catche déjà toutes les erreurs sur `getById(id)` et affiche `'Impossible de charger cet événement.'` — le cas 404 est déjà traité visuellement par la page de détail. Vérifier (grep) mais ne pas toucher.
- ❌ Pas de refactor opportuniste d'`EventService` (pas de réorganisation des helpers, pas de renommage).
- ❌ Pas de nouveau code d'erreur dans `ApiErrorResponse` — réutiliser `not_found`.
- ❌ Pas de log applicatif sur le rejet (voir Décision 9).
- ❌ Pas d'application de la même règle à d'autres endpoints (share, favorite, attend, view, …). Chaque endpoint concerné devra faire l'objet d'une issue dédiée si le pentest le soulève. Cette PR ne corrige que `GET /events/{id}`.

---

## Étape 0 — `openapi/openapi.yaml` (OBLIGATOIRE EN PREMIER)

**Fichier :** [`/workspace/openapi/openapi.yaml`](openapi/openapi.yaml) — path `/events/{id}` GET, lignes ~1319-1344.

**Modifications exactes :**

1. Remplacer `summary` et ajouter une `description` explicite :

```yaml
  /events/{id}:
    get:
      summary: Détail d'un événement
      description: |
        Retourne le détail d'un événement.

        **Règle d'autorisation** : seuls les événements `PUBLISHED` sont accessibles
        anonymement. Les événements `DRAFT` ou `CANCELLED` ne sont visibles que par
        leur créateur ou un administrateur (rôle `ADMIN`). Dans tout autre cas —
        appelant anonyme, appelant authentifié non-créateur non-admin, ou appelant
        authentifié dont le profil n'est pas provisionné — le serveur renvoie `404
        not_found`, identique à la réponse pour un ID inexistant (évite un oracle
        d'existence — cf. pentest 2026-04-17 finding 4.12 + 4.15).
      operationId: getEventById
      tags: [events]
      security:
        - {}
        - BearerAuth: []
```

2. Conserver les `parameters` (id path param) et la réponse `200` inchangés.

3. Durcir la description de la réponse `404` :

```yaml
        '404':
          description: |
            Événement introuvable, OU événement en statut `DRAFT` / `CANCELLED`
            demandé par un appelant qui n'en est ni le créateur ni un admin.
            Le corps est identique dans les deux cas (pas d'oracle d'existence).
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

**Points à respecter :**

- **`security: [{}, {BearerAuth: []}]`** : le tableau vide `{}` signifie « accès sans authentification autorisé ». L'entrée `BearerAuth: []` signale que l'endpoint **peut** consommer un JWT si présent et que la réponse en dépend. C'est la seule façon OpenAPI 3 propre d'exprimer « auth optionnelle » ; `security: []` seul masquerait le fait qu'un token change le comportement.
- **Pas de nouveau code d'erreur** dans le schéma `ApiErrorResponse`. Le champ `error` vaut toujours `"not_found"`, message inchangé.
- **Pas de nouveau paramètre.** Le path param `id` est inchangé.

---

## Étape 1 — `EventResource.java`

**Fichier :** [`backend/src/main/java/ch/unige/events/resource/EventResource.java`](backend/src/main/java/ch/unige/events/resource/EventResource.java) — méthode `getById`, lignes 80-86.

### 1.1 — Nouvelle signature

Remplacer :

```java
@GET
@Path("/{id}")
@PermitAll
public Response getById(@PathParam("id") Long id) {
    EventDTO event = eventService.getById(id);
    return Response.ok(event).build();
}
```

par :

```java
@GET
@Path("/{id}")
@PermitAll
public Response getById(@PathParam("id") Long id) {
    String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
    boolean isAdmin = !identity.isAnonymous() && identity.hasRole("ADMIN");
    EventDTO event = eventService.getById(id, auth0Id, isAdmin);
    return Response.ok(event).build();
}
```

**Points à respecter :**

- `@PermitAll` est **conservé** — la règle de visibilité est appliquée dans le Service, pas par l'annotation de sécurité JAX-RS. Un `@Authenticated` casserait le cas anon + event PUBLISHED qui est légitime.
- `identity.isAnonymous()` est le test standard Quarkus pour un appelant sans JWT ; pas besoin d'importer de classe supplémentaire (`SecurityIdentity` est déjà importé ligne 21).
- Ne **pas** appeler `identity.getPrincipal().getName()` si `isAnonymous()` est `true` — dans ce cas le principal est `Anonymous` et son `name` vaut la chaîne `"Anonymous"`, pas `null`. Passer explicitement `null` au Service évite toute ambiguïté.
- Pas de refactor des autres méthodes de `EventResource`. `@SuppressWarnings` non nécessaire (la méthode reste à 1 paramètre visible).

### 1.2 — Pas de nouvel import

`SecurityIdentity` est déjà importé. `identity.hasRole()` et `identity.isAnonymous()` sont des méthodes de base de `SecurityIdentity`.

---

## Étape 2 — `EventService.java`

**Fichier :** [`backend/src/main/java/ch/unige/events/service/EventService.java`](backend/src/main/java/ch/unige/events/service/EventService.java) — méthode `getById`, lignes 133-139.

### 2.1 — Nouvelle signature

Remplacer :

```java
@Transactional
public EventDTO getById(Long id) {
    Event event = Event.<Event>findByIdOptional(id)
            .orElseThrow(NotFoundException::new);
    long att = countAttending(id);
    return EventDTO.from(event, att, computeAvailableSpots(event.capacity, att), countWaitlisted(id));
}
```

par :

```java
@Transactional
public EventDTO getById(Long id, String auth0Id, boolean isAdmin) {
    Event event = Event.<Event>findByIdOptional(id)
            .orElseThrow(NotFoundException::new);

    // Hotfix pentest 4.12 : hide DRAFT / CANCELLED events from non-owners / non-admins.
    // 404 (not 403) is intentional — same envelope as "does not exist" to close the
    // existence oracle highlighted in finding 4.12 (ID enumeration).
    if (event.status != EventStatus.PUBLISHED && !isAdmin && !isCreator(event, auth0Id)) {
        throw new NotFoundException();
    }

    long att = countAttending(id);
    return EventDTO.from(event, att, computeAvailableSpots(event.capacity, att), countWaitlisted(id));
}
```

**Points à respecter :**

- **Ordre des checks (important)** : charger l'event → si `PUBLISHED` → retourner sans autre contrôle ; sinon vérifier `isAdmin` OR `isCreator` ; sinon 404. Cette séquence garantit que :
  - Un event PUBLISHED reste accessible à tous (non-régression du cas public).
  - Un appelant admin passe la règle avant même que `isCreator` ne soit évalué — utile quand un admin inspecte un DRAFT qu'il n'a pas créé.
  - `isCreator` gère lui-même le cas `auth0Id == null` et `event.creator == null` (voir son implémentation ligne 343-347 : `event.creator != null && event.creator.auth0Id != null && event.creator.auth0Id.equals(auth0Id)`). Un appelant anonyme (`auth0Id == null`) ne matche jamais.
- **Ne pas** appeler `User.findByAuth0Id(auth0Id)` ici. La comparaison par `String auth0Id` sur `event.creator.auth0Id` est suffisante et évite une requête DB inutile (cf. Décision 6).
- **Pas de log** — aucune trace d'audit applicative (cf. Décision 9).
- **Pas de message dans `NotFoundException`** — l'envelope produite par le mapper est identique avec ou sans message custom, et garder le constructeur vide aligne avec le `orElseThrow(NotFoundException::new)` juste au-dessus.

### 2.2 — Pas de nouvel import

`NotFoundException` est déjà importé (ligne 19). `EventStatus` est déjà importé (ligne 10). `isCreator` est un helper statique privé du même fichier.

### 2.3 — Autres méthodes du Service : inchangées

`getAll`, `getMyEvents`, `create`, `update`, `delete`, `cancel`, `restore`, `publish`, `uploadImage` restent strictement inchangés. Aucun refactor opportuniste.

---

## Étape 3 — `EventServiceMock.java` (tests unitaires)

**Fichier :** [`backend/src/test/java/ch/unige/events/service/EventServiceMock.java`](backend/src/test/java/ch/unige/events/service/EventServiceMock.java) — méthode `getById`, lignes 128-135.

### 3.1 — Aligner l'override sur la nouvelle signature

Remplacer :

```java
@Override
public EventDTO getById(Long id) {
    Event event = eventsById.get(id);
    if (event == null) {
        throw new NotFoundException();
    }
    return EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L);
}
```

par :

```java
@Override
public EventDTO getById(Long id, String auth0Id, boolean isAdmin) {
    Event event = eventsById.get(id);
    if (event == null) {
        throw new NotFoundException();
    }
    // Même règle qu'en prod — hotfix pentest 4.12.
    boolean isCreator = event.creator != null
            && event.creator.auth0Id != null
            && event.creator.auth0Id.equals(auth0Id);
    if (event.status != EventStatus.PUBLISHED && !isAdmin && !isCreator) {
        throw new NotFoundException();
    }
    return EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L);
}
```

**Points à respecter :**

- **La règle est dupliquée** (comparaison `auth0Id` en ligne dans le mock) plutôt qu'appelée depuis `EventService.isCreator`. Le mock doit être capable de tester indépendamment du Service — c'est le pattern déjà en place pour `update` ([`EventServiceMock.java:146-151`](backend/src/test/java/ch/unige/events/service/EventServiceMock.java#L146-L151)).
- `seedEvent` par défaut crée un event en statut `DRAFT` ([`EventServiceMock.java:63`](backend/src/test/java/ch/unige/events/service/EventServiceMock.java#L63)). Les tests resource qui ont besoin de PUBLISHED devront utiliser `seedEventWithStatus(auth0Id, title, EventStatus.PUBLISHED, now)` ([`EventServiceMock.java:72`](backend/src/test/java/ch/unige/events/service/EventServiceMock.java#L72)).

### 3.2 — Pas de modification des autres méthodes du mock

---

## Étape 4 — Mise à jour des call-sites internes

**12 call-sites à mettre à jour**, tous dans les tests. Aucun consommateur de production (le seul appelant prod est `EventResource.getById`, déjà traité à l'étape 1).

### 4.1 — `EventServiceCoverageTest.java`

| Ligne | Contexte (test existant) | Modification |
|---|---|---|
| 328 | `getById_existingEvent_returnsDTO` | `eventService.getById(event.id)` → `eventService.getById(event.id, null, false)` (event PUBLISHED dans le seed ? à vérifier → sinon passer l'auth0Id du creator) |
| 338 | `getById_unknownEvent_throwsNotFound` | `eventService.getById(999999L)` → `eventService.getById(999999L, null, false)` |
| 995 | contexte : assert après création | ajouter `, null, false` (ou l'auth0Id du creator si l'event seedé est DRAFT) |
| 1009 | idem | idem |
| 1089 | `getById_withCapacityAndAttending_computesAvailableSpots` | idem |
| 1112 | `getById_capacityReducedBelowAttending_clampsAvailableSpotsToZero` | idem |

**Règle de migration par call-site :** si le test seedait un event en `DRAFT`/`CANCELLED` et attendait un 200, il faut désormais passer l'`auth0Id` du créateur ou `isAdmin=true`. Si l'event était `PUBLISHED`, `null, false` suffit. **Vérifier statut par statut** — ne pas passer aveuglément `null, false` partout.

### 4.2 — `AttendanceServiceCoverageTest.java`

Les 6 call-sites (lignes 264, 271, 278, 296, 492, 505) appellent `eventService.getById(event.id)` pour relire un event après une mutation d'attendance. Dans tous les cas, l'event seedé est **PUBLISHED** (prérequis pour `attend`), donc `eventService.getById(event.id, null, false)` est correct — `null` auth0Id + not admin → le service renvoie le DTO (PUBLISHED est public). **Vérifier le seed** avant de migrer ; si un test utilise un DRAFT, passer l'auth0Id du créateur.

### 4.3 — Tests resource (EventResourceTest.java) déjà couverts par l'étape 5

---

## Étape 5 — Tests `EventResourceTest.java`

**Fichier :** [`backend/src/test/java/ch/unige/events/resource/EventResourceTest.java`](backend/src/test/java/ch/unige/events/resource/EventResourceTest.java) — section `// --- GET /events/{id} ---`, lignes 212-234.

### 5.1 — Conserver les tests existants en adaptant le seed

Les deux tests existants (`getById_existingEvent_returns200` ligne 215, `getById_unknownEvent_returns404` ligne 228) sont à conserver **mais** le test 215 seede un event en DRAFT via `seedEvent(...)` et appelle l'endpoint sans auth → avec la nouvelle règle, il renvoie 404. **À refactorer :**

- Remplacer `var event = eventServiceMock.seedEvent("auth0|alice", "Mon Événement");` par `var event = eventServiceMock.seedEventWithStatus("auth0|alice", "Mon Événement", EventStatus.PUBLISHED, LocalDateTime.now());` pour rester dans le cas non-régression (anon lit event PUBLISHED).
- Renommer en `getById_publishedEvent_anon_returns200` pour clarifier l'intention.

### 5.2 — Nouveaux tests à ajouter (après le test existant `getById_unknownEvent_returns404`)

| Test | `@TestSecurity` | Setup | Appel | Attendu |
|---|---|---|---|---|
| `getById_draftEvent_anon_returns404` | (aucune — anon) | `seedEvent("auth0|alice", ...)` (DRAFT par défaut) | `get("/events/" + id)` | `404 not_found` |
| `getById_cancelledEvent_anon_returns404` | (aucune — anon) | `seedEventWithStatus("auth0|alice", ..., CANCELLED, ...)` | `get("/events/" + id)` | `404 not_found` |
| `getById_draftEvent_otherUser_returns404` | `@TestSecurity(user = "auth0|bob")` | `seedEvent("auth0|alice", ...)` (DRAFT) | `get("/events/" + id)` | `404 not_found` |
| `getById_cancelledEvent_otherUser_returns404` | `@TestSecurity(user = "auth0|bob")` | `seedEventWithStatus("auth0|alice", ..., CANCELLED, ...)` | `get("/events/" + id)` | `404 not_found` |
| `getById_draftEvent_creator_returns200` | `@TestSecurity(user = "auth0|alice")` | `seedEvent("auth0|alice", ...)` (DRAFT) | `get("/events/" + id)` | `200` + payload |
| `getById_cancelledEvent_creator_returns200` | `@TestSecurity(user = "auth0|alice")` | `seedEventWithStatus("auth0|alice", ..., CANCELLED, ...)` | `get("/events/" + id)` | `200` + payload |
| `getById_draftEvent_admin_returns200` | `@TestSecurity(user = "auth0|admin", roles = {"ADMIN"})` | `seedEvent("auth0|alice", ...)` (DRAFT) | `get("/events/" + id)` | `200` + payload |
| `getById_cancelledEvent_admin_returns200` | `@TestSecurity(user = "auth0|admin", roles = {"ADMIN"})` | `seedEventWithStatus("auth0|alice", ..., CANCELLED, ...)` | `get("/events/" + id)` | `200` + payload |

### 5.3 — Squelette d'un nouveau test (référence)

```java
@Test
void getById_draftEvent_anon_returns404() {
    var event = eventServiceMock.seedEvent("auth0|alice", "Brouillon secret"); // DRAFT par défaut

    given()
            .when().get("/events/" + event.id)
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
}

@Test
@TestSecurity(user = "auth0|alice")
void getById_draftEvent_creator_returns200() {
    var event = eventServiceMock.seedEvent("auth0|alice", "Mon brouillon");

    given()
            .when().get("/events/" + event.id)
            .then()
            .statusCode(200)
            .body("id", equalTo(event.id.intValue()))
            .body("status", is("DRAFT"));
}
```

### 5.4 — Vérification envelope identique

L'un des tests (par exemple `getById_draftEvent_anon_returns404`) doit vérifier que le body est **strictement identique** à celui de `getById_unknownEvent_returns404` (même `error`, même `message`) — c'est la garantie anti-oracle :

```java
.body("error", equalTo("not_found"))
.body("message", equalTo("Profile not found"))  // même message qu'un 404 standard
```

Si le message diverge, c'est un bug de mapper — réinvestiguer avant de tagger la PR.

---

## Étape 6 — Tests `EventServiceCoverageTest.java` (intégration DevServices)

**Fichier :** [`backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java) — section `// --- getById ---`, ligne 320.

### 6.1 — Nouveaux tests à ajouter

| Test | Setup | Appel | Attendu |
|---|---|---|---|
| `getById_publishedEvent_anon_returns200` | `persistEvent("...", EventStatus.PUBLISHED, user)` | `getById(id, null, false)` | DTO retourné |
| `getById_draftEvent_anon_throwsNotFound` | `persistEvent("...", EventStatus.DRAFT, user)` | `getById(id, null, false)` | `NotFoundException` |
| `getById_cancelledEvent_anon_throwsNotFound` | `persistEvent("...", EventStatus.CANCELLED, user)` | `getById(id, null, false)` | `NotFoundException` |
| `getById_draftEvent_otherUser_throwsNotFound` | user A creator ; user B appelle | `getById(id, "auth0|bob", false)` | `NotFoundException` |
| `getById_draftEvent_creator_returns200` | user A creator, appel en tant qu'A | `getById(id, "auth0|alice", false)` | DTO retourné |
| `getById_draftEvent_admin_returns200` | user A creator, appel en admin | `getById(id, "auth0|admin", true)` | DTO retourné |
| `getById_cancelledEvent_admin_returns200` | idem avec CANCELLED | `getById(id, "auth0|admin", true)` | DTO retourné |
| `getById_draftEvent_authenticatedButNoProfile_throwsNotFound` | user A creator, auth0Id sans User en DB | `getById(id, "auth0|ghost", false)` | `NotFoundException` — ghost n'est créateur d'aucun event par construction |

### 6.2 — Squelette d'un nouveau test (référence)

```java
@Test
@TestTransaction
void getById_draftEvent_otherUser_throwsNotFound() {
    User alice = persistUser("auth0|alice", "alice@example.com");
    persistUser("auth0|bob", "bob@example.com");
    Event event = persistEvent("Alice's draft", EventCategory.ACADEMIC, EventStatus.DRAFT, alice);

    assertThrows(NotFoundException.class, () -> eventService.getById(event.id, "auth0|bob", false));
}

@Test
@TestTransaction
void getById_draftEvent_creator_returns200() {
    User alice = persistUser("auth0|alice2", "alice2@example.com");
    Event event = persistEvent("Alice's draft", EventCategory.ACADEMIC, EventStatus.DRAFT, alice);

    EventDTO result = eventService.getById(event.id, "auth0|alice2", false);

    assertEquals(event.id, result.id());
    assertEquals("Alice's draft", result.title());
}
```

### 6.3 — Cible de couverture

**Viser 100 % sur les lignes nouvelles de `getById` :**

- `event.status != EventStatus.PUBLISHED` → branche couverte par `getById_publishedEvent_anon_returns200` (false) + tous les autres tests (true).
- `!isAdmin` → `getById_draftEvent_admin_returns200` (false) + tous les autres (true).
- `!isCreator(event, auth0Id)` → `getById_draftEvent_creator_returns200` (false) + `getById_draftEvent_otherUser_throwsNotFound` (true) + `getById_draftEvent_anon_throwsNotFound` (true, auth0Id = null).

---

## Étape 7 — Documentation

### 7.1 — [`backend/docs/api-contract.md`](backend/docs/api-contract.md)

**Ajouter** une ligne dans la table « Endpoints implémentés » (section « ## Endpoints implémentés », après la ligne 17 `GET /events`) :

```markdown
| `GET` | `/events/{id}` | `@PermitAll` | Détail d'un événement — **DRAFT/CANCELLED cachés** (créateur ou admin uniquement, sinon 404) | 200, 404 |
```

**Ajouter** une nouvelle sous-section dans « ## Détail des endpoints », après la section `GET /users/{id}` :

```markdown
### `GET /events/{id}`

Détail d'un événement.

**Règle d'autorisation** (hotfix pentest 2026-04-17, findings 4.12 + 4.15) :
- Un événement `PUBLISHED` est accessible **anonymement** (pas de JWT requis).
- Un événement `DRAFT` ou `CANCELLED` n'est visible que par son créateur (JWT dont `sub` matche `event.creator.auth0Id`) ou par un admin (rôle `ADMIN`).
- Sinon : `404 not_found` — envelope identique à celle d'un ID inexistant, pour ne pas créer d'oracle d'existence (pas de distinction « n'existe pas » / « existe mais caché »).

**Paramètre :** `id` — ID numérique de l'événement (sequential `Long`).

**Réponses :**
- `200 OK` — `EventDTO` complet (mêmes champs que `GET /events`)
- `404 Not Found` — événement introuvable, OU événement non-PUBLISHED demandé par un appelant non autorisé
```

### 7.2 — [`backend/docs/data-model.md`](backend/docs/data-model.md)

**Enrichir** la sous-section Event avec une note sur la règle de visibilité. À ajouter juste après le tableau des champs (après la ligne 58 sur les index DB) :

```markdown
#### Règle de visibilité par statut (hotfix pentest 2026-04-17)

Le statut `Event.status` détermine qui peut lire l'événement via `GET /api/events/{id}` :

| Statut | Visibilité |
|---|---|
| `PUBLISHED` | Public (anon + authentifié) |
| `DRAFT` | Créateur (`event.creator.auth0Id`) ou rôle `ADMIN` uniquement |
| `CANCELLED` | Créateur ou rôle `ADMIN` uniquement |

Un appelant non autorisé reçoit `404 not_found` — même envelope qu'un ID inexistant,
pour fermer l'oracle d'existence (cf. findings 4.12 + 4.15 du rapport de pentest).
La règle est appliquée dans `EventService.getById(Long, String, boolean)`.

Les endpoints de liste (`GET /events`, `GET /events/search`) filtrent déjà les statuts
non publics correctement — voir `backlog_s5_s10.md` et SCRUM-133 pour le contexte.
```

### 7.3 — [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md)

**Ajouter** en haut du fichier (avant Sprint 1) une nouvelle section Sprint 6 :

```markdown
## Sprint 6 — Hotfix sécurité post-pentest (ISSUE-92) — 2026-04-24

Correction du finding **4.12** (Medium) du rapport de pentest du 2026-04-17 :
`GET /api/events/{id}` renvoyait `200` avec le payload complet d'un event `DRAFT`
ou `CANCELLED` à n'importe quel appelant, y compris anonyme. Combiné au finding
**4.15** (IDs séquentiels), n'importe qui pouvait énumérer tous les brouillons et
events annulés de la plateforme avec `for id in 1..1000; do curl .../events/$id; done`.

Fix :
- `EventService.getById(Long, String, boolean)` — signature étendue avec l'`auth0Id`
  de l'appelant et un flag `isAdmin`. Si `event.status != PUBLISHED` et que
  l'appelant n'est ni le créateur ni un admin → `NotFoundException` (→ `404 not_found`).
- `EventResource.getById` reste `@PermitAll` (PUBLISHED doit rester anon-accessible)
  mais lit `identity.isAnonymous()` + `identity.hasRole("ADMIN")` pour transmettre
  au Service.
- Envelope d'erreur identique à une 404 classique (pas de code d'erreur custom) —
  ferme l'oracle d'existence.
- 12 call-sites internes migrés (tests DB-backed + mock unitaire).

**Pas de changement DB.** Pas d'impact frontend (`useEvent` consomme déjà le 404).
```

---

## Edge cases à traiter explicitement

| Cas | Appelant | Event | Comportement attendu | Implémenté par |
|---|---|---|---|---|
| Anon + PUBLISHED | `identity.isAnonymous()` | `status=PUBLISHED` | `200` + DTO | Court-circuit statut=PUBLISHED dans le Service |
| Anon + DRAFT | `identity.isAnonymous()` | `status=DRAFT` | `404 not_found` | `auth0Id=null` → `isCreator=false`, `isAdmin=false` |
| Anon + CANCELLED | `identity.isAnonymous()` | `status=CANCELLED` | `404 not_found` | idem |
| Auth + own DRAFT | `auth0|alice` | DRAFT créé par alice | `200` + DTO | `isCreator(event, auth0Id)` true |
| Auth + own CANCELLED | `auth0|alice` | CANCELLED créé par alice | `200` + DTO | idem |
| Auth + other DRAFT | `auth0|bob` | DRAFT créé par alice | `404 not_found` | `isCreator` false, pas admin |
| Auth + other CANCELLED | `auth0|bob` | CANCELLED créé par alice | `404 not_found` | idem |
| Auth + admin + any DRAFT | `auth0|admin` + role ADMIN | DRAFT quelconque | `200` + DTO | `isAdmin=true` |
| Auth + admin + any CANCELLED | `auth0|admin` + role ADMIN | CANCELLED quelconque | `200` + DTO | idem |
| Auth sans profil en DB + DRAFT | `auth0|ghost`, pas de User | DRAFT quelconque | `404 not_found` | `isCreator` false (aucun event.creator.auth0Id ne matche « ghost ») |
| Auth sans profil en DB + PUBLISHED | `auth0|ghost`, pas de User | PUBLISHED | `200` + DTO | Court-circuit PUBLISHED (indépendant du profil) |
| Unknown ID (anon ou auth) | n'importe qui | `findByIdOptional` vide | `404 not_found` | `orElseThrow(NotFoundException::new)` — non-régression |
| Event `creator == null` (défense en profondeur) | n'importe qui | créateur orphan | `404 not_found` sauf PUBLISHED | `isCreator` retourne false sur `event.creator == null` (cf. `EventService.java:344`) |

**Note sur le dernier cas :** l'entité `Event` a `creator` en `@ManyToOne` avec une FK DB — un event sans créateur ne devrait pas exister. Mais `isCreator` garde la garde `event.creator != null`, donc la règle reste sûre même si un event orphelin apparaissait (séquence de migration, test mal nettoyé). Pas de test dédié à créer — la garde est implicite.

---

## Critères d'acceptation (repris de l'issue GitHub)

- [ ] Anon + `GET /events/{draftId}` → `404`
- [ ] User A (non-créateur, non-admin) + `GET /events/{draftOfB}` → `404`
- [ ] User B (créateur) + `GET /events/{draftOfB}` → `200` + payload complet
- [ ] Admin + `GET /events/{anyDraft}` → `200` (le rôle `ADMIN` est testable via `@TestSecurity(... roles = {"ADMIN"})` même si aucun token STUDENT actuel ne peut le produire côté Auth0 — voir §Décision 7)
- [ ] Même logique appliquée à `status = CANCELLED`
- [ ] `GET /events/{id}` sur un event **PUBLISHED** reste anon-accessible (non-régression)
- [ ] Coverage ≥ 80 % sur le nouveau code
- [ ] OpenAPI : la règle d'autorisation est décrite dans `description` de `/events/{id}` et le `404` est documenté comme pouvant cacher un draft

---

## Conventions du projet à respecter

- **Règle d'or :** `openapi.yaml` modifié en premier (cf. [`backend/AGENTS.md`](backend/AGENTS.md)).
- **camelCase** partout, pas de snake_case dans les champs ni les réponses JSON.
- **Pas de préfixe `is`** sur les booléens d'entités (le flag `isAdmin` est un paramètre de méthode, pas un champ — pas concerné).
- **Pas de migration Flyway** — aucun changement de schéma.
- **Pas de logique métier dans la Resource** au-delà de la lecture de `identity`.
- **SonarCloud** : ≥ 80 % couverture sur le nouveau code, ≤ 3 % duplication, ratings A (Security, Reliability, Maintainability, Security Review).
- **Doc mise à jour dans le même commit** que le code correspondant.
- **Commit messages atomiques** : `feat(ISSUE-92): …`, `test(ISSUE-92): …`, `docs(ISSUE-92): …`.

---

## Interdits stricts

- ❌ **Pas de migration Flyway** ni changement de schéma.
- ❌ **Pas de changement d'ID Event** (UUIDs opaques hors scope — finding 4.15 alternative écartée).
- ❌ **Pas de changement** de `@PermitAll` → `@Authenticated` sur `GET /events/{id}` (PUBLISHED doit rester anon-accessible).
- ❌ **Pas de 403** sur draft/cancelled non autorisé — **404 uniquement** (éviter l'oracle d'existence).
- ❌ **Pas de modification frontend** — [`useEvent.ts`](frontend/src/hooks/useEvent.ts) gère déjà `404` génériquement.
- ❌ **Pas d'ajout de champ** à `EventDTO`.
- ❌ **Pas de refactor opportuniste** d'`EventService` (signature de `getById` + call-sites uniquement).
- ❌ **Pas de nouvelle exception custom** ni de nouveau code d'erreur dans `ApiErrorResponse` — réutiliser `NotFoundException` + envelope existante.
- ❌ **Pas de log** applicatif ou metric sur le rejet 404 (cohérent avec Décision 9 de [`specs_scrum-133.md`](specs_archives/specs_claude/specs_scrum-133.md)).
- ❌ **Pas de message custom** dans `NotFoundException` pour l'event caché — l'envelope doit être identique à un 404 standard.
- ❌ **Pas d'élargissement** du scope à d'autres endpoints (`share`, `favorite`, `attend`, `view`, `report`, `duplicate`, `stats`, `attendees`). Chacun fera l'objet d'une issue dédiée si le pentest le soulève.

---

## Résumé des fichiers touchés

| Fichier | Action |
|---|---|
| `/workspace/openapi/openapi.yaml` | Modifier — description + `security` + description 404 sur `/events/{id}` GET |
| `backend/src/main/java/ch/unige/events/resource/EventResource.java` | Modifier — `getById` extrait `auth0Id` et `isAdmin` |
| `backend/src/main/java/ch/unige/events/service/EventService.java` | Modifier — nouvelle signature + contrôle d'accès |
| `backend/src/test/java/ch/unige/events/service/EventServiceMock.java` | Modifier — aligner override + appliquer la règle in-memory |
| `backend/src/test/java/ch/unige/events/resource/EventResourceTest.java` | Modifier — renommer `getById_existingEvent_returns200` → `_publishedEvent_anon_returns200`, seed PUBLISHED, ajouter 8 nouveaux tests |
| `backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java` | Modifier — 6 call-sites existants + ~8 nouveaux tests intégration |
| `backend/src/test/java/ch/unige/events/service/AttendanceServiceCoverageTest.java` | Modifier — 6 call-sites existants (passer `, null, false` ou auth0Id du creator) |
| `backend/docs/api-contract.md` | Modifier — ligne table + section détail pour `GET /events/{id}` |
| `backend/docs/data-model.md` | Modifier — note règle de visibilité par statut |
| `backend/docs/sprint-context.md` | Modifier — entrée Sprint 6 / ISSUE-92 |

**Total :** 10 fichiers modifiés, 0 créé.

---

## Branche et PR

- **Branche :** `feature/ISSUE-92-hide-draft-events`, basée sur `origin/main`.
  ```bash
  git fetch origin
  git checkout -b feature/ISSUE-92-hide-draft-events origin/main
  ```
- **PR :** ciblant `main`, titre : `ISSUE-92 — Cacher les events DRAFT/CANCELLED sur GET /events/{id} (pentest 4.12)`.
- **Description PR** (modèle) :

  ```markdown
  Closes #<num>

  Hotfix du finding **4.12** (Medium) + **4.15** (Low) du pentest interne du 2026-04-17.

  ## Avant
  ```bash
  curl https://.../api/events/304   # sans auth
  # → 200 OK — payload DRAFT complet (title, description, creatorId, …)
  ```

  ## Après
  ```bash
  curl https://.../api/events/304   # sans auth, event DRAFT
  # → 404 not_found — envelope identique à un ID inexistant (pas d'oracle d'existence)

  curl -H "Authorization: Bearer $TOKEN_OWNER" https://.../api/events/304
  # → 200 OK — le créateur récupère bien son brouillon
  ```

  ## Fichiers touchés
  - `openapi/openapi.yaml`
  - `backend/src/main/java/ch/unige/events/resource/EventResource.java`
  - `backend/src/main/java/ch/unige/events/service/EventService.java`
  - Tests : `EventResourceTest`, `EventServiceCoverageTest`, `AttendanceServiceCoverageTest`, `EventServiceMock`
  - Doc : `api-contract.md`, `data-model.md`, `sprint-context.md`

  ## Référence
  Rapport pentest : `unige-events-pentest-report.md` — findings 4.12 + 4.15.
  ```

- **Commits atomiques suggérés :**
  - `feat(ISSUE-92): hide DRAFT/CANCELLED events from non-owners on GET /events/{id}`
  - `test(ISSUE-92): cover anon, other-user, creator and admin paths on GET /events/{id}`
  - `docs(ISSUE-92): document authorization rule on GET /events/{id}`

---

## Checklist Sonar / qualité

- [ ] Coverage ≥ 80 % sur les lignes nouvelles (JaCoCo).
- [ ] Duplication < 3 % sur le code nouveau.
- [ ] Security Rating : A.
- [ ] Reliability Rating : A.
- [ ] Maintainability Rating : A.
- [ ] Security Review Rating : A.

---

## Checklist finale

### Avant push

- [ ] `./mvnw verify` vert localement.
- [ ] Rapport JaCoCo `backend/target/site/jacoco/index.html` — lignes nouvelles ≥ 80 %.
- [ ] Les **3 tests de sécurité critiques** nommément verts (run ciblé) :
  - `getById_draftEvent_anon_returns404` (anon-voit-draft → 404)
  - `getById_draftEvent_otherUser_returns404` (other-user-voit-draft → 404)
  - `getById_publishedEvent_anon_returns200` (non-régression PUBLISHED public)
- [ ] `openapi.yaml` validé par le linter pré-commit (si configuré) et cohérent avec le code.
- [ ] Aucun log applicatif ajouté (grep `logger.info\|logger.warn` sur le diff).
- [ ] Frontend inchangé (`git diff --stat frontend/` → 0 file).

### Avant PR

- [ ] Branche `feature/ISSUE-92-hide-draft-events` basée sur `origin/main`.
- [ ] Commits atomiques nommés selon la convention.
- [ ] Description de PR reprenant la motivation sécurité + curl avant/après + `Closes #<num>`.
- [ ] Base du PR : `main`.

### Avant merge

- [ ] CI verte.
- [ ] Review approuvée.
- [ ] SonarCloud quality gate vert.

---

## Prompt de lancement d'implémentation

````
Tu vas implémenter ISSUE-92 (hotfix pentest 4.12 + 4.15) : cacher les events DRAFT et CANCELLED sur `GET /api/events/{id}` aux appelants qui ne sont ni le créateur ni un admin, en renvoyant `404 not_found` (envelope identique à un ID inexistant — pas d'oracle d'existence).

## ÉTAPE 0 — Création de la branche

Avant TOUT code :

    git fetch origin
    git checkout -b feature/ISSUE-92-hide-draft-events origin/main

Remplace `ISSUE-92` par le numéro réel de l'issue GitHub associée au finding 4.12. Si tu ne le connais pas, ouvre `gh issue list --search "draft events"` ou demande à l'utilisateur avant de commencer.

## Source unique de vérité

`specs_archives/specs_claude/specs_ISSUE-92-hide-draft-events.md` — à lire INTÉGRALEMENT avant d'écrire une ligne de code. Toutes les décisions (signature à 3 paramètres, règle d'autorisation dans le Service, 404 vs 403, envelope standard, extraction `isAnonymous`, pas de `User.findByAuth0Id` dans la règle, pas de log) y sont tranchées. Tu n'as RIEN à inventer.

## À lire avant de commencer

1. `unige-events-pentest-report.md` à la racine — findings 4.12 (anonymous read of DRAFT events) et 4.15 (sequential integer event IDs). Pour comprendre ce qu'un attaquant voit aujourd'hui et pourquoi le fix doit fermer un oracle d'existence.
2. `backend/AGENTS.md` — conventions (camelCase, openapi-first, pas de Flyway, logique métier dans le Service, seuil Sonar 80%).
3. `backend/docs/` en entier (README, architecture, data-model, api-contract, sprint-context).
4. Code backend sur `main` :
   - `backend/src/main/java/ch/unige/events/resource/EventResource.java` — méthode `getById` ligne 80, pattern `identity.hasRole("ADMIN")` aux lignes 133 et 144.
   - `backend/src/main/java/ch/unige/events/service/EventService.java` — méthode `getById` ligne 133, helper `isCreator` ligne 343.
   - `backend/src/main/java/ch/unige/events/exception/mapper/NotFoundExceptionMapper.java` — envelope produite.
   - `backend/src/test/java/ch/unige/events/service/EventServiceMock.java` — mock `getById` ligne 128.
   - `backend/src/test/java/ch/unige/events/resource/EventResourceTest.java` — tests existants `getById_*` lignes 212-234, patterns `@TestSecurity` lignes 419 (bob) et 556 (admin).
   - `backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java` — tests intégration lignes 320-339, helpers `persistUser` / `persistEvent` lignes 1180-1208.

## Ordre d'implémentation strict

1. **`openapi/openapi.yaml`** EN PREMIER (règle d'or `backend/AGENTS.md`). Path `/events/{id}` GET (ligne ~1319) : mettre à jour la `description` pour documenter la règle d'autorisation, passer `security` à `[{}, {BearerAuth: []}]` (auth optionnelle), enrichir la description du `404`. Pas de nouveau code d'erreur dans `ApiErrorResponse`, pas de nouveau paramètre.

2. **`backend/src/main/java/ch/unige/events/resource/EventResource.java`** — dans `getById(id)`, résoudre :
   ```java
   String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
   boolean isAdmin = !identity.isAnonymous() && identity.hasRole("ADMIN");
   EventDTO event = eventService.getById(id, auth0Id, isAdmin);
   ```
   Conserver `@PermitAll`. Pas de nouvel import.

3. **`backend/src/main/java/ch/unige/events/service/EventService.java`** — nouvelle signature `getById(Long id, String auth0Id, boolean isAdmin)`. Dans le corps, APRÈS `findByIdOptional` et AVANT `countAttending`, ajouter :
   ```java
   if (event.status != EventStatus.PUBLISHED && !isAdmin && !isCreator(event, auth0Id)) {
       throw new NotFoundException();
   }
   ```
   Ne PAS appeler `User.findByAuth0Id` ici. Ne PAS passer de message custom à `NotFoundException`. Réutiliser le helper privé `isCreator` existant (ligne 343).

4. **`backend/src/test/java/ch/unige/events/service/EventServiceMock.java`** — aligner l'override `getById` sur la nouvelle signature et dupliquer la règle in-memory (la comparaison `event.creator.auth0Id.equals(auth0Id)` en ligne, comme c'est déjà fait pour `update` ligne 146).

5. **Mise à jour des 12 call-sites internes :**
   - `EventServiceCoverageTest.java` lignes 328, 338, 995, 1009, 1089, 1112 — passer `, null, false` (ou l'auth0Id du créateur si le seed est DRAFT/CANCELLED). Vérifier le statut seedé par test avant de migrer.
   - `AttendanceServiceCoverageTest.java` lignes 264, 271, 278, 296, 492, 505 — les events seedés sont PUBLISHED (prérequis `attend`) donc `, null, false` est correct. Vérifier quand même.

6. **`backend/src/test/java/ch/unige/events/resource/EventResourceTest.java`** :
   - Renommer `getById_existingEvent_returns200` (ligne 215) en `getById_publishedEvent_anon_returns200` et remplacer `seedEvent(...)` par `seedEventWithStatus(..., EventStatus.PUBLISHED, LocalDateTime.now())` pour rester dans le cas non-régression.
   - Conserver `getById_unknownEvent_returns404` tel quel (ligne 228).
   - Ajouter les 8 nouveaux tests : `_draftEvent_anon_returns404`, `_cancelledEvent_anon_returns404`, `_draftEvent_otherUser_returns404`, `_cancelledEvent_otherUser_returns404`, `_draftEvent_creator_returns200`, `_cancelledEvent_creator_returns200`, `_draftEvent_admin_returns200`, `_cancelledEvent_admin_returns200`. Annotations : `@TestSecurity(user = "auth0|alice")` / `"auth0|bob"` / `"auth0|admin", roles = {"ADMIN"}`. Vérifier pour au moins un des tests 404 que `body("message", equalTo("Profile not found"))` — même message qu'un 404 standard, garantit l'absence d'oracle.

7. **`backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java`** — ajouter ~8 nouveaux tests intégration dans la section `// --- getById ---` (après ligne 339). Utiliser `persistUser` + `persistEvent` pour seed. Viser 100 % de couverture sur les 3 lignes nouvelles du Service (`status != PUBLISHED`, `!isAdmin`, `!isCreator`).

8. **`./mvnw verify`** — DOIT être vert avec couverture ≥ 80 % sur le nouveau code. Corriger avant de passer à la doc.

9. **Documentation (même commit que le code correspondant) :**
   - `backend/docs/api-contract.md` — ajouter ligne table + section détail pour `GET /events/{id}`.
   - `backend/docs/data-model.md` — note règle de visibilité par statut sous l'entité Event.
   - `backend/docs/sprint-context.md` — entrée Sprint 6 ISSUE-92.

## Interdits stricts

- PAS de migration Flyway ni changement de schéma DB.
- PAS de changement `@PermitAll` → `@Authenticated` sur `GET /events/{id}` — PUBLISHED doit rester anon-accessible.
- PAS de 403 sur le refus — 404 uniquement, envelope identique au 404 standard.
- PAS de message custom dans `NotFoundException` pour l'event caché.
- PAS de modification frontend (`useEvent.ts` gère déjà 404).
- PAS d'ajout de champ à `EventDTO`, pas de nouveau code d'erreur dans `ApiErrorResponse`.
- PAS de refactor opportuniste d'`EventService` (signature de `getById` + call-sites uniquement).
- PAS de `User.findByAuth0Id` dans la règle `getById` — utiliser `isCreator(event, auth0Id)` qui compare la chaîne.
- PAS de log applicatif ni de metric sur le rejet 404.
- PAS d'élargissement à d'autres endpoints (share, favorite, attend, view, report, duplicate, stats, attendees). Cette PR ne corrige QUE `GET /events/{id}`.
- PAS de snake_case, pas de `any`, pas de TODO commenté.

## Conventions à respecter

- camelCase partout.
- `openapi.yaml` modifié en PREMIER (règle d'or `backend/AGENTS.md`).
- Couverture JaCoCo ≥ 80 % sur les lignes nouvelles ; duplication < 3 % ; Sonar ratings A partout.
- Doc mise à jour dans le même commit que le code correspondant.
- Commits atomiques nommés `feat(ISSUE-92): …`, `test(ISSUE-92): …`, `docs(ISSUE-92): …`.

## Critères de done

- [ ] `./mvnw verify` vert localement et en CI.
- [ ] JaCoCo ≥ 80 % sur les lignes nouvelles (rapport `backend/target/site/jacoco/index.html`).
- [ ] Les **2 tests de sécurité critiques** verts nommément :
  - `getById_draftEvent_anon_returns404` (anon voit draft → 404)
  - `getById_draftEvent_otherUser_returns404` (user B voit draft de user A → 404)
- [ ] Test de non-régression vert : `getById_publishedEvent_anon_returns200` (anon voit published → 200, comportement public préservé).
- [ ] Au moins un test 404 assert `body("message", equalTo("Profile not found"))` pour prouver l'absence d'oracle d'existence.
- [ ] `git diff --stat frontend/` vide (pas de changement frontend).
- [ ] SonarCloud Quality Gate vert sur la PR.
- [ ] `openapi.yaml` modifié EN PREMIER et cohérent avec le code.
- [ ] `backend/docs/api-contract.md`, `backend/docs/data-model.md`, `backend/docs/sprint-context.md` mis à jour dans le même PR.
- [ ] PR ouverte avec base `main`, titre `ISSUE-92 — Cacher les events DRAFT/CANCELLED sur GET /events/{id} (pentest 4.12)`, description contenant `Closes #<num>` pour linker automatiquement l'issue GitHub existante, curl avant/après, fichiers touchés, référence au rapport de pentest.
- [ ] Commits atomiques bien nommés (`feat(ISSUE-92)`, `test(ISSUE-92)`, `docs(ISSUE-92)`).
````
