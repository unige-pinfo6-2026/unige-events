# Spécification technique — Fix : accès des co-organisateurs ACCEPTED et des admins à `/events/:id/stats`

> Branche : `fix/co-organizers-stats-access`
> Statut : **analyse + plan** (aucun changement de code à ce stade)
> Date : 2026-05-23
> Périmètre : **frontend + petite modif backend** — le backend autorise déjà créateur + co-organisateur ACCEPTED mais **refuse explicitement les admins** sur `/events/{id}/stats`. Le fix demande donc un élargissement backend (ajouter `isAdmin`) en plus du fix frontend (consommer `coOrganizerOf` + `isAdmin`).

---

## 1. Résumé du bug

Deux populations ne peuvent pas accéder à la page stats alors qu'elles le devraient :

1. **Co-organisateur ACCEPTED** : clique sur **« Voir les statistiques »** (visible depuis `EventDetailPage` pour les co-organisateurs depuis SCRUM-137) → la page répond **« Accès réservé à l'organisateur de l'événement. »**. Bug **frontend** ; le backend autoriserait déjà l'appel.
2. **Admin du site** (claim Auth0 `roles=["ADMIN"]`) : même symptôme côté UI, **et** un 403 backend s'il atteint l'endpoint en direct. Bug **frontend + backend** : `EventStatsService.isCreatorOrAcceptedCoOrganizer` ([service.java:51-59](../../backend/services/event-service/src/main/java/ch/unige/events/event/stats/service/EventStatsService.java)) ne fait aucune exception pour le rôle ADMIN.

Note : le défaut co-organisateur est une **régression connue et documentée dans le code** — `EventStatsPage.tsx` lignes 229-235 décrivent exactement la limitation et la renvoient à un futur fix.

---

## 2. Diagnostic — résultat attendu vs réalité

### 2.1 Le backend est partiellement correct

`event-service` autorise déjà créateur + co-organisateur ACCEPTED sur `GET /events/{id}/stats`, mais **pas** les admins :

- **Resource** : [`EventStatsResource.java:30-37`](../../backend/services/event-service/src/main/java/ch/unige/events/event/stats/resource/EventStatsResource.java) — `@Authenticated`, délègue à la couche service.
- **Service** : [`EventStatsService.java:32-59`](../../backend/services/event-service/src/main/java/ch/unige/events/event/stats/service/EventStatsService.java) — appelle `isCreatorOrAcceptedCoOrganizer(event, callerUuid)` qui matche `event.creatorId == callerUuid` **OU** `EventCoOrganizer.isAcceptedFor(eventId, callerUuid)`. **Aucune branche `isAdmin`.**
- **Entité** : [`EventCoOrganizer.isAcceptedFor()`](../../backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/entity/EventCoOrganizer.java) — filtre strict sur `status = ACCEPTED`.

→ **Modification backend nécessaire** : élargir la check pour inclure le rôle ADMIN (pattern déjà utilisé ailleurs, ex. `AttendanceService.getAttendees` lit `identity.hasRole(ROLE_ADMIN)` via `SecurityIdentity`).

### 2.2 Le frontend bloque avant même d'appeler l'endpoint

Trois défauts dans **un seul fichier** : [`frontend/src/pages/event/EventStatsPage.tsx`](../../frontend/src/pages/event/EventStatsPage.tsx).

#### Défaut #1 — `useEvent` appelé sans le paramètre `checkCoOrgOf` (ligne 227)

```tsx
const { event, loading: eventLoading, error: eventError } = useEvent(eventId)
```

Le hook `useEvent(id, checkCoOrgOf?)` propage `?check-co-org-of=<uuid>` à `GET /events/{id}`. Sans ce param, **le backend renvoie `event.coOrganizerOf = null`** ([cf. `Event.coOrganizerOf` doc, types/event.ts:33-41](../../frontend/src/types/event.ts)). Le frontend n'a donc jamais l'info nécessaire pour identifier le co-organisateur.

> Le bon pattern est déjà appliqué dans `EventDetailPage.tsx:309` :
> ```tsx
> const { event, ... } = useEvent(eventId, user?.id ?? null)
> ```

#### Défaut #2 — la check d'organisateur ignore `coOrganizerOf` (ligne 236)

```tsx
const isConfirmedOrganizer = event !== null && user !== null && user.id === event.creatorId
```

Compare uniquement à `creatorId`. Le bon pattern existe déjà à `EventDetailPage.tsx:315-317` :

```tsx
const isAcceptedCoOrganizer = event !== null && event.coOrganizerOf === true
const isCreator = user !== null && event !== null && user.id === event.creatorId
const isOrganizer = isCreator || isAcceptedCoOrganizer
```

#### Défaut #3 — le message d'erreur exclut sémantiquement les co-organisateurs (ligne 270)

```tsx
return <InfoMessage type="error" message="Accès réservé à l'organisateur de l'événement." />
```

À élargir : « Accès réservé à l'organisateur ou aux co-organisateurs de l'événement. »

#### Bonus — le commentaire (lignes 229-235) devient obsolète et doit être supprimé

Le commentaire actuel acte la limitation comme un TODO connu (« the frontend has no co-organizer integration yet … Tracked separately from review #90 »). Cette PR résout précisément ce TODO ; le commentaire est à retirer dans le même changement.

### 2.3 Le bouton « Voir les statistiques » est déjà correct côté `EventDetailPage`

[`EventDetailPage.tsx:836-846`](../../frontend/src/pages/event/EventDetailPage.tsx) affiche le lien `Voir les statistiques` dès que `isOrganizer === true` (créateur **ou** co-organisateur ACCEPTED). Donc un co-organisateur voit déjà le bouton, clique, et c'est `EventStatsPage` qui le rejette à tort.

---

## 3. Plan de correction

### 3.1 Backend — élargir l'autorisation aux admins

**Fichier :** [`backend/services/event-service/src/main/java/ch/unige/events/event/stats/service/EventStatsService.java`](../../backend/services/event-service/src/main/java/ch/unige/events/event/stats/service/EventStatsService.java)

Pattern à reproduire (déjà utilisé dans `AttendanceService.getAttendees` qui injecte `SecurityIdentity` et lit `identity.hasRole(ROLE_ADMIN)`) :

```diff
+ import io.quarkus.security.identity.SecurityIdentity;
  …
  public class EventStatsService {

+     private static final String ROLE_ADMIN = "ADMIN";
+
      @Inject CallerIdentity callerIdentity;
+     @Inject SecurityIdentity identity;
      @Inject @RestClient EngagementServiceClient engagementClient;

      @Transactional
      public EventStatsDTO getStats(String auth0Id, Long eventId) {
          …
          UUID callerUuid = callerIdentity.requireUuid();

-         if (!isCreatorOrAcceptedCoOrganizer(event, callerUuid)) {
-             throw new ForbiddenException("Only the event creator or an accepted co-organizer can view stats");
+         if (!isAuthorizedToViewStats(event, callerUuid)) {
+             throw new ForbiddenException("Only the event creator, an accepted co-organizer, or an admin can view stats");
          }
          …
      }

-     private static boolean isCreatorOrAcceptedCoOrganizer(Event event, UUID callerUuid) {
+     private boolean isAuthorizedToViewStats(Event event, UUID callerUuid) {
          if (event == null || callerUuid == null) {
              return false;
          }
+         if (identity.hasRole(ROLE_ADMIN)) {
+             return true;
+         }
          if (callerUuid.equals(event.creatorId)) {
              return true;
          }
          return EventCoOrganizer.isAcceptedFor(event.id, callerUuid);
      }
  }
```

Notes :
- La méthode passe de `static` à instance pour pouvoir lire `identity`.
- `ROLE_ADMIN` aligné sur la constante utilisée ailleurs (vérifier la valeur exacte dans `AttendanceService` ou un fichier de constantes partagé `shared-jaxrs` / `shared-platform` — réutiliser si elle existe, sinon constante locale).
- Le message d'erreur sert seulement en log côté serveur ; côté client le mapper transforme la 403 en envelope `ApiErrorResponse`.

### 3.2 Frontend — consommer `coOrganizerOf` + `isAdmin` dans `EventStatsPage`

**Fichier :** [`frontend/src/pages/event/EventStatsPage.tsx`](../../frontend/src/pages/event/EventStatsPage.tsx)

Diff conceptuel :

```diff
- import { useAuth, useEvent } from '@/hooks'
+ import { useAuth, useEvent } from '@/hooks'   // (déjà OK ; on lira `isAdmin` du même hook)
  …
  export default function EventStatsPage() {
      const { id } = useParams<{ id: string }>()
-     const { user } = useAuth()
+     const { user, isAdmin } = useAuth()
      …
-     const { event, loading: eventLoading, error: eventError } = useEvent(eventId)
-
-     // Confirm organizer before fetching stats (avoids 403 noise for non-organizers).
-     // Caveat: the backend also lets ACCEPTED co-organizers view stats
-     // (cf. EventStatsService.getStats + isCreatorOrAcceptedCoOrganizerPublic),
-     // but the frontend has no co-organizer integration yet (no service, no
-     // hook, no ACCEPTED list to consult). Until that lands, accepted
-     // co-organizers see "Accès réservé à l'organisateur" even though the API
-     // would serve them. Tracked separately from review #90.
-     const isConfirmedOrganizer = event !== null && user !== null && user.id === event.creatorId
+     const { event, loading: eventLoading, error: eventError } = useEvent(eventId, user?.id ?? null)
+
+     // Aligned with EventDetailPage: creator OR accepted co-organizer OR site admin
+     // can view stats (backend authorises all three — EventStatsService).
+     const isAcceptedCoOrganizer = event !== null && event.coOrganizerOf === true
+     const isCreator = user !== null && event !== null && user.id === event.creatorId
+     const isOrganizer = isCreator || isAcceptedCoOrganizer || isAdmin
```

Remplacer ensuite **toutes** les occurrences de `isConfirmedOrganizer` par `isOrganizer` dans la suite du composant (lignes 243, 249, 269).

Et le message d'erreur (ligne 270) :

```diff
- return <InfoMessage type="error" message="Accès réservé à l'organisateur de l'événement." />
+ return <InfoMessage type="error" message="Accès réservé à l'équipe organisatrice." />
```

### 3.3 Frontend — bouton « Voir les statistiques » visible pour les admins

**Fichier :** [`frontend/src/pages/event/EventDetailPage.tsx`](../../frontend/src/pages/event/EventDetailPage.tsx)

`isAdmin` est déjà exposé via `useAuth()` (lu ligne 305 : `const { user, isAdmin } = useAuth()`). Seule la condition d'affichage du bouton (ligne 836) doit s'élargir, **sans** toucher à `isOrganizer` (qui gate d'autres actions hors scope) :

```diff
- {isOrganizer && (
+ {(isOrganizer || isAdmin) && (
    <div className="max-lg:order-10">
      <Link to={`/events/${event.id}/stats`} … >
        <BarChart2 className="w-4 h-4 shrink-0" />
        Voir les statistiques
      </Link>
    </div>
  )}
```

**Aucune autre modification.** Pas de nouveau hook, pas de nouveau service, pas de changement de types ni de routes, pas de skeleton à régénérer (le layout ne change pas).

---

## 4. Tests

### 4.1 Backend — `EventStatsServiceTest.java` (event-service)

- **T1 (régression)** — caller = créateur → 200, stats retournées.
- **T2 (régression)** — caller = co-organisateur ACCEPTED → 200, stats retournées.
- **T3 (régression)** — caller = co-organisateur PENDING/DECLINED → 403.
- **T4 (nouveau)** — caller = admin, **non créateur**, **non co-organisateur** → 200, stats retournées.
- **T5 (régression)** — caller authentifié sans aucun rôle/lien → 403.
- **T6** — caller non authentifié → 401 (déjà couvert par `@Authenticated` sur la resource).

### 4.2 Frontend — `EventStatsPage.test.tsx`

- **T1 (régression)** — caller = créateur, `event.coOrganizerOf=null`, `isAdmin=false` → stats affichées.
- **T2 (nouveau)** — caller = co-organisateur ACCEPTED, `event.coOrganizerOf=true`, `isAdmin=false` → stats affichées.
- **T3 (nouveau)** — caller = admin, `event.coOrganizerOf=false`, `user.id != event.creatorId` → stats affichées.
- **T4 (régression)** — caller authentifié non-organisateur, non-admin, `event.coOrganizerOf=false`, `isAdmin=false` → message « Accès réservé à l'équipe organisatrice. » ; `useEventStats` **non** appelé (pas de 403 inutile).
- **T5** — vérifier que `useEvent` est bien appelé avec `user?.id` en second argument (snapshot des appels ou mock du hook).
- **T6** — utilisateur non authentifié (`user === null`) → bloqué par `PrivateRoute` en amont ; test optionnel de defensiveness : `user === null` → message d'erreur, pas de crash.

---

## 5. Critères d'acceptance

| # | Scénario | Résultat attendu |
|---|---|---|
| AC-1 | Créateur de l'événement clique sur « Voir les statistiques » | Page stats s'affiche normalement (régression) |
| AC-2 | Co-organisateur **ACCEPTED** clique sur « Voir les statistiques » depuis `EventDetailPage` | Page stats s'affiche normalement avec les vraies données |
| AC-3 | **Admin** du site (claim Auth0 `ADMIN`) ouvre `/events/:id/stats` sur n'importe quel event | Page stats s'affiche normalement ; backend renvoie 200 |
| AC-4 | Co-organisateur **PENDING** ou **DECLINED** essaie d'ouvrir `/events/:id/stats` directement | Message « Accès réservé à l'équipe organisatrice. » ; aucun appel à `GET /events/{id}/stats` |
| AC-5 | Utilisateur authentifié non admin et non lié à l'événement essaie d'ouvrir `/events/:id/stats` | Message « Accès réservé à l'équipe organisatrice. » ; aucun appel à `GET /events/{id}/stats` |
| AC-6 | Le bouton « Voir les statistiques » est visible pour créateur, co-organisateur ACCEPTED **et admin** depuis `EventDetailPage` | Bouton apparaît dans la barre d'actions ; clic mène à la page stats qui s'affiche normalement |
| AC-7 | Le commentaire-TODO lignes 229-235 est supprimé | Plus de dette documentée résiduelle |
| AC-8 | Backend : appel direct à `GET /events/{id}/stats` par un admin (sans lien à l'event) | 200, payload `EventStatsDTO` (régression-positive) |

---

## 6. Documentation à mettre à jour

D'après `frontend/AGENTS.md` § « Maintenance de la documentation » :

- ❌ Pas de nouveau composant, service, route, type, skeleton, page → **aucune mise à jour de `docs/components.md`, `docs/architecture.md`, `docs/types.md` ou de la table des skeletons**.
- ✅ `docs/sprint-context.md` — mention du fix à la fin de la tâche (entrée S9 ou équivalent).
- ❌ `openapi.yaml` — non touché.

---

## 7. Questions ouvertes / décisions tranchées

1. ~~**Identifiant Jira**~~ — **Tranché** : plus de Jira. Branche `fix/co-organizers-stats-access` conservée ; titre de PR libre, ex. `fix(frontend+backend): allow accepted co-organizers and admins to view event stats`.
2. ~~**Message d'erreur**~~ — **Tranché** : « Accès réservé à l'équipe organisatrice. » (concis, couvre créateur + co-organisateurs ; les admins passent la check et ne voient jamais ce message).
3. ~~**Bouton « Voir les statistiques » sur `EventDetailPage` pour les admins**~~ — **Tranché : (a) admin voit le bouton sur tous les events**. Modification ciblée : juste cette condition d'affichage, **ne pas étendre `isOrganizer` aux autres actions** (edit/cancel/restore restent dans leur périmètre actuel — c'est un fix admin-stats, pas un élargissement global des permissions admin).
4. **Skeleton** : pas de changement de layout → pas de régénération de `event-stats.bones.json`. À confirmer une fois le diff appliqué.
