# Specs — Bug fixes : filtrage des événements à venir + limites de caractères

> **Branche :** `fix/upcoming-events-filter-and-char-limits`
> **Règle d'or :** Modifier `openapi.yaml` EN PREMIER, puis backend, puis frontend

---

## Contexte

### Ce qui existe déjà (ne pas retoucher sauf ce qui est listé ci-dessous)

| Fichier | État |
|---|---|
| `backend/src/main/java/.../resource/EventResource.java` | `GET /events` — 5 query params : page, size, status, category, organizerId |
| `backend/src/main/java/.../service/EventService.java` | `getAll()` — filtres dynamiques status/category/organizerId, **aucun filtre de date** |
| `backend/src/main/java/.../dto/event/EventRequestBase.java` | `title` avec `@NotBlank` uniquement, `description` sans contrainte |
| `backend/src/main/java/.../entity/Event.java` | `title` et `description` : `String` sans `@Column(length=…)` ni `@Size` |
| `frontend/src/hooks/useEvents.ts` | Appelle `getAll({ status: 'PUBLISHED' })` — **aucun filtre de date** |
| `frontend/src/services/eventApi.ts` | Interface `EventsParams` — pas de champ `startDateFrom` |
| `frontend/src/hooks/useEventForm.ts` | `EVENT_TITLE_MAX_LENGTH = 120`, `EVENT_DESCRIPTION_MAX_LENGTH = 2000` |
| `/workspace/openapi/openapi.yaml` | `GET /events` — pas de param `startDateFrom` ; schemas `CreateEventRequest`/`UpdateEventRequest` — pas de `maxLength` |

### Bug 1 — Événements passés dans "Événements à venir"

La page d'accueil affiche des événements dont la `startDate` est déjà passée. Le hook `useEvents.ts` passe uniquement `status: 'PUBLISHED'` à l'API, sans filtre de date. Le service backend `EventService.getAll()` n'a pas de condition sur `startDate`.

**Fix :** Ajouter un paramètre optionnel `startDateFrom` (type `LocalDate`) à `GET /events`. Le frontend le valorise systématiquement avec la date du jour.

### Bug 2 — Limites de caractères incohérentes

Le frontend limite le titre à 120 caractères et la description à 2000 caractères. Le backend n'a aucune contrainte `@Size` sur ces champs — Hibernate crée des colonnes `VARCHAR(255)` par défaut, soit une description tronquée à 255 chars si envoyée depuis l'API directement, et un titre acceptant 255 chars alors que le frontend en refuse 121.

**Fix :** Aligner le backend sur les limites frontend. Titre : 120 chars. Description : 2000 chars.

---

## Étape 0 — `openapi.yaml` (OBLIGATOIRE EN PREMIER)

**Fichier :** `/workspace/openapi/openapi.yaml`

### 0.1 — Ajouter le paramètre `startDateFrom` sur `GET /events`

Localiser le path `/events` > `get` > `parameters` (ligne ~559). Ajouter après le paramètre `organizerId` :

```yaml
        - name: startDateFrom
          in: query
          description: Filtre les événements dont startDate >= startDateFrom (début de journée, 00:00:00). Utilisé par la page d'accueil pour n'afficher que les événements à venir.
          schema:
            type: string
            format: date
```

### 0.2 — Ajouter `maxLength` sur `title` et `description` dans les schemas

Localiser `CreateEventRequest` (ligne ~194) et `UpdateEventRequest` (ligne ~228). Dans les deux schemas, remplacer :

```yaml
        title:
          type: string
```
par :
```yaml
        title:
          type: string
          maxLength: 120
```

Et remplacer :
```yaml
        description:
          type: string
          nullable: true
```
par :
```yaml
        description:
          type: string
          maxLength: 2000
          nullable: true
```

---

## Étape 1 — Backend : `EventService.java`

**Fichier :** `backend/src/main/java/ch/unige/events/service/EventService.java`

### 1.1 — Ajouter l'import `LocalDate`

```java
import java.time.LocalDate;
```

### 1.2 — Modifier la signature de `getAll()`

Remplacer :
```java
public List<EventDTO> getAll(int page, int size, EventStatus status, EventCategory category, UUID organizerId) {
```
par :
```java
public List<EventDTO> getAll(int page, int size, EventStatus status, EventCategory category, UUID organizerId, LocalDate startDateFrom) {
```

### 1.3 — Ajouter le filtre `startDateFrom` dans le corps de `getAll()`

Après le bloc `if (organizerId != null)` (ligne ~54), ajouter :

```java
        if (startDateFrom != null) {
            conditions.add("startDate >= :startDateFrom");
            params.put("startDateFrom", startDateFrom.atStartOfDay());
        }
```

---

## Étape 2 — Backend : `EventResource.java`

**Fichier :** `backend/src/main/java/ch/unige/events/resource/EventResource.java`

### 2.1 — Ajouter l'import `LocalDate`

```java
import java.time.LocalDate;
```

### 2.2 — Ajouter le query param `startDateFrom` dans `getAll()`

Remplacer la signature de la méthode `getAll()` :
```java
    public List<EventDTO> getAll(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size,
            @QueryParam("status") EventStatus status,
            @QueryParam("category") EventCategory category,
            @QueryParam("organizerId") UUID organizerId) {
        return eventService.getAll(page, size, status, category, organizerId);
    }
```
par :
```java
    public List<EventDTO> getAll(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size,
            @QueryParam("status") EventStatus status,
            @QueryParam("category") EventCategory category,
            @QueryParam("organizerId") UUID organizerId,
            @QueryParam("startDateFrom") LocalDate startDateFrom) {
        return eventService.getAll(page, size, status, category, organizerId, startDateFrom);
    }
```

---

## Étape 3 — Backend : `EventRequestBase.java`

**Fichier :** `backend/src/main/java/ch/unige/events/dto/event/EventRequestBase.java`

### 3.1 — Ajouter l'import `@Size`

```java
import jakarta.validation.constraints.Size;
```

### 3.2 — Ajouter les contraintes de taille

Remplacer :
```java
    @NotBlank
    public String title;

    public String description;
```
par :
```java
    @NotBlank
    @Size(max = 120)
    public String title;

    @Size(max = 2000)
    public String description;
```

---

## Étape 4 — Backend : `Event.java`

**Fichier :** `backend/src/main/java/ch/unige/events/entity/Event.java`

### 4.1 — Ajouter les annotations `@Column` sur `title` et `description`

Ces annotations permettent à Hibernate de créer les colonnes avec les bonnes contraintes DB, cohérentes avec les validations du DTO.

Remplacer :
```java
    public String title;

    public String description;
```
par :
```java
    @Column(length = 120)
    public String title;

    @Column(columnDefinition = "TEXT")
    public String description;
```

> **Note schéma :** Hibernate est configuré en mode `update`. Il créera les nouvelles colonnes avec ces définitions. Sur une base existante, Hibernate ne rétrécit pas automatiquement `title` de VARCHAR(255) à VARCHAR(120) ni ne convertit `description` de VARCHAR(255) à TEXT — ces changements DDL doivent être appliqués manuellement si nécessaire en environnement de dev (`ALTER TABLE events ALTER COLUMN title TYPE VARCHAR(120); ALTER TABLE events ALTER COLUMN description TYPE TEXT;`). En production, planifier une migration.

---

## Étape 5 — Frontend : `eventApi.ts`

**Fichier :** `frontend/src/services/eventApi.ts`

### 5.1 — Ajouter `startDateFrom` dans `EventsParams`

Remplacer :
```typescript
export interface EventsParams {
  page?: number
  size?: number
  status?: EventStatus
  category?: EventCategory
  organizerId?: string
}
```
par :
```typescript
export interface EventsParams {
  page?: number
  size?: number
  status?: EventStatus
  category?: EventCategory
  organizerId?: string
  startDateFrom?: string
}
```

---

## Étape 6 — Frontend : `useEvents.ts`

**Fichier :** `frontend/src/hooks/useEvents.ts`

### 6.1 — Passer `startDateFrom` égal à la date du jour

Remplacer :
```typescript
      const data = await getAll({ page: pageNum, size: PAGE_SIZE, status: 'PUBLISHED' })
```
par :
```typescript
      const today = new Date().toISOString().split('T')[0]
      const data = await getAll({ page: pageNum, size: PAGE_SIZE, status: 'PUBLISHED', startDateFrom: today })
```

---

## Étape 7 — Documentation

### 7.1 — `backend/docs/data-model.md`

Dans le tableau **Event** (section `### Event`), mettre à jour les lignes `title` et `description` :

| Avant | Après |
|---|---|
| `title` → Contraintes : `@NotBlank` | `title` → Contraintes : `@NotBlank`, `@Size(max=120)`, `@Column(length=120)` |
| `description` → Contraintes : nullable | `description` → Contraintes : nullable, `@Size(max=2000)` (DTO), `@Column(columnDefinition="TEXT")` |

Dans le tableau **Règles de validation JPA** (section `## Règles de validation JPA`), ajouter :

| Annotation | Champ(s) concerné(s) |
|---|---|
| `@Size(max=120)` | `EventRequestBase.title`, `Event.title` |
| `@Size(max=2000)` | `EventRequestBase.description` |

Dans le tableau **CreateEventRequest** (section `### CreateEventRequest`), ajouter les contraintes :

| Champ | Validation |
|---|---|
| `title` | `@NotBlank`, `@Size(max=120)` |
| `description` | `@Size(max=2000)`, optionnel |

### 7.2 — `backend/docs/api-contract.md` (si le fichier existe)

Mettre à jour la ligne de `GET /events` pour mentionner le nouveau paramètre :

```
| `GET` | `/events` | `@PermitAll` | Liste paginée — filtres : status, category, organizerId, startDateFrom | 200 |
```

---

## Résumé des fichiers à modifier

| Fichier | Action |
|---|---|
| `/workspace/openapi/openapi.yaml` | **Modifier** — ajouter `startDateFrom` sur `GET /events` ; ajouter `maxLength: 120` sur `title` et `maxLength: 2000` sur `description` dans `CreateEventRequest` et `UpdateEventRequest` |
| `backend/src/main/java/.../service/EventService.java` | **Modifier** — signature + filtre `startDateFrom` dans `getAll()` |
| `backend/src/main/java/.../resource/EventResource.java` | **Modifier** — ajouter `@QueryParam("startDateFrom") LocalDate startDateFrom` et le passer au service |
| `backend/src/main/java/.../dto/event/EventRequestBase.java` | **Modifier** — ajouter `@Size(max=120)` sur `title` et `@Size(max=2000)` sur `description` |
| `backend/src/main/java/.../entity/Event.java` | **Modifier** — ajouter `@Column(length=120)` sur `title` et `@Column(columnDefinition="TEXT")` sur `description` |
| `frontend/src/services/eventApi.ts` | **Modifier** — ajouter `startDateFrom?: string` dans `EventsParams` |
| `frontend/src/hooks/useEvents.ts` | **Modifier** — passer `startDateFrom: today` dans l'appel à `getAll()` |
| `backend/docs/data-model.md` | **Mettre à jour** — contraintes de `title` et `description` |

---

## Règles critiques à respecter

| Règle | Détail |
|---|---|
| `openapi.yaml` EN PREMIER | Toujours modifier `/workspace/openapi/openapi.yaml` avant tout code |
| camelCase partout | `startDateFrom` en Java et en TypeScript — jamais `start_date_from` |
| Pas de logique dans la Resource | `EventResource` reçoit le paramètre et le délègue — aucun traitement |
| `atStartOfDay()` dans le Service | Convertir `LocalDate` → `LocalDateTime` avec `.atStartOfDay()` pour le filtre JPQL |
| `@Size` sur le DTO, `@Column` sur l'entité | La validation `@Size` s'applique au DTO (`EventRequestBase`) ; la définition de colonne `@Column` s'applique à l'entité (`Event`). Les deux sont nécessaires pour que Hibernate Validator et le schéma DB soient cohérents. |
| Ne pas modifier `EVENT_TITLE_MAX_LENGTH` ni `EVENT_DESCRIPTION_MAX_LENGTH` | Ces constantes frontend sont déjà correctes (120 / 2000) — le bug était uniquement côté backend |
| SonarCloud | Pas de nouveaux tests requis pour ces corrections mineures — les tests existants couvrent les chemins modifiés |
