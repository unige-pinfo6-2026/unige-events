# Specs — filtre `?facultyNone=` sur recherche avancée

> **Branche :** `feature/s3-faculty-filter`
> **Contexte :** Extension du filtre faculty introduit par SCRUM-77. Un chip « Toutes facultés » est ajouté à la recherche avancée pour isoler les événements dont le champ `faculty` vaut `null` (événements non rattachés à une faculté précise).
> **Règle d'or :** `openapi.yaml` EN PREMIER, puis backend (service → resource → mocks → tests), puis frontend (types → hook → sidebar → tests), puis docs.

---

## Contexte

### Problème résolu

Depuis SCRUM-77, l'entité `Event` a un champ `faculty` nullable et les endpoints `GET /events` / `GET /events/search` supportent `?faculty=<FACULTY>` pour filtrer sur une faculté précise. Mais il n'existait aucun moyen d'isoler les événements **sans** faculté rattachée — sélectionner une faculté dans le filtre les exclut, et ne rien sélectionner retourne tous les événements mélangés.

Avec le rework UI qui affiche un badge neutre « Toutes facultés » sur l'`EventCard` pour les events `faculty = null`, l'absence de filtre symétrique devient incohérente : on voit le badge neutre sur les cards, mais impossible de filtrer dessus dans la recherche avancée.

### Décision

Ajouter un nouveau query param `?facultyNone=true` (boolean, optionnel) sur `GET /events` et `GET /events/search`, mutuellement exclusif avec `?faculty=`. Côté serveur, si les deux sont fournis, `facultyNone` a priorité. Côté UI, la sélection est exclusive via un chip « Toutes facultés » ajouté en tête de la rangée de chips Faculty.

Pas de modification d'entité, pas de migration DB — le champ `faculty` est déjà nullable.

---

## Étape 0 — `openapi.yaml` (OBLIGATOIRE EN PREMIER)

**Fichier :** `/workspace/openapi/openapi.yaml`

Ajouter un paramètre `facultyNone` sur les deux endpoints concernés.

### 0.1 — `GET /events`

Après le bloc `- name: faculty`, ajouter :

```yaml
        - name: facultyNone
          in: query
          description: |
            Si true, retourne uniquement les événements dont `faculty` est null
            (non rattachés à une faculté précise). Mutuellement exclusif avec le
            paramètre `faculty` — si les deux sont fournis, `facultyNone` a priorité.
          schema:
            type: boolean
```

### 0.2 — `GET /events/search`

Même ajout après `- name: faculty` dans les parameters de `/events/search`.

---

## Étape 1 — `EventService.getAll()`

**Fichier :** `backend/src/main/java/ch/unige/events/service/EventService.java`

Ajouter un paramètre `Boolean facultyNone` en fin de signature (après `Faculty faculty`). Dans la construction JPQL, remplacer :

```java
if (faculty != null) {
    conditions.add("e.faculty = :faculty");
    params.put("faculty", faculty);
}
```

par :

```java
// facultyNone=true has priority over faculty — mutually exclusive filter.
if (Boolean.TRUE.equals(facultyNone)) {
    conditions.add("e.faculty IS NULL");
} else if (faculty != null) {
    conditions.add("e.faculty = :faculty");
    params.put("faculty", faculty);
}
```

Ajouter `@SuppressWarnings("java:S107")` sur la méthode (elle a désormais 8 paramètres, le seuil Sonar est 7 — exception justifiée par la nature REST « 1 param Java par query param »).

---

## Étape 2 — `EventSearchService.search()`

**Fichier :** `backend/src/main/java/ch/unige/events/service/EventSearchService.java`

Ajouter `Boolean facultyNone` après `Faculty faculty` dans la signature. Même logique mutex dans la construction JPQL. `@SuppressWarnings("java:S107")` également.

---

## Étape 3 — Resources

**Fichier :** `backend/src/main/java/ch/unige/events/resource/EventResource.java`

Ajouter `@QueryParam("facultyNone") Boolean facultyNone` à `getAll()` et propager à `eventService.getAll(...)`. `@SuppressWarnings("java:S107")`.

**Fichier :** `backend/src/main/java/ch/unige/events/resource/EventSearchResource.java`

Même transformation sur `search()`.

---

## Étape 4 — Mocks

**Fichiers :**
- `backend/src/test/java/ch/unige/events/service/EventServiceMock.java`
- `backend/src/test/java/ch/unige/events/service/EventSearchServiceMock.java`

Aligner les signatures. Dans le filtre in-memory, remplacer :

```java
.filter(e -> faculty == null || e.faculty == faculty)
```

par :

```java
.filter(e -> {
    if (Boolean.TRUE.equals(facultyNone)) {
        return e.faculty == null;
    }
    return faculty == null || e.faculty == faculty;
})
```

---

## Étape 5 — Tests backend

### 5.1 — `EventResourceTest.java`

Ajouter :

| Test | Scénario | HTTP |
|---|---|---|
| `getAll_withFacultyNoneFilter_returnsOnlyUnaffiliated` | Seed 1 event SCIENCES + 1 event sans faculté → `?facultyNone=true` retourne l'event sans faculté (1 résultat, `faculty: null`) | 200 |
| `getAll_withFacultyNoneAndFaculty_facultyNoneWins` | Seed idem → `?faculty=SCIENCES&facultyNone=true` retourne l'event sans faculté, pas celui avec SCIENCES | 200 |

### 5.2 — `EventSearchResourceTest.java`

Mêmes 2 cas sur `/events/search` (`search_withFacultyNoneFilter_returnsOnlyUnaffiliated`, `search_withFacultyNoneAndFaculty_facultyNoneWins`).

### 5.3 — `EventServiceCoverageTest.java`

Cas d'intégration DB :
- `getAll_withFacultyNone_returnsNullFacultyEvents` — persiste 2 events (SCIENCES + null) → filtre `facultyNone=true` retourne 1 event (`faculty` null).
- `getAll_withFacultyNoneAndFaculty_facultyNoneWins` — priorité.

### 5.4 — `EventSearchServiceCoverageTest.java`

- `search_withFacultyNone_returnsNullFacultyEvents` — idem pour search.
- `search_withFacultyNoneAndFaculty_facultyNoneWins` — priorité.

### 5.5 — Callers existants

Tous les appels `eventService.getAll(...)` et `eventSearchService.search(...)` dans les tests coverage doivent être alignés à la nouvelle signature (ajout d'un `null` pour `facultyNone`). Pattern mécanique : insérer `null` en dernière position pour `getAll`, en 4ᵉ position (après `faculty`) pour `search`.

---

## Étape 6 — Frontend types

**Fichier :** `frontend/src/types/search.ts`

Ajouter `facultyNone?: boolean` à `SearchFilters` et à `SearchParams`, avec un commentaire expliquant la règle de mutex client/serveur.

**Fichier :** `frontend/src/services/eventApi.ts`

Ajouter `facultyNone?: boolean` (et `faculty?: Faculty` s'il manquait) à `EventsParams` pour cohérence — même si aucune page frontend actuelle n'utilise `getAll` avec ces filtres, le type doit rester cohérent avec `SearchParams`.

---

## Étape 7 — `useEventSearch`

**Fichier :** `frontend/src/hooks/useEventSearch.ts`

### 7.1 — Lecture URL au montage

```ts
facultyNone: searchParams.get('facultyNone') === 'true' ? true : undefined,
```

### 7.2 — Sync état → URL

Remplacer le bloc `if (filters.faculty) params.faculty = ...` par :

```ts
if (filters.facultyNone) {
  params.facultyNone = 'true'
} else if (filters.faculty) {
  params.faculty = filters.faculty
}
```

### 7.3 — Envoi aux services API

Dans `performSearch`, construire le `SearchParams` envoyé en appliquant la règle de mutex côté client :

```ts
const params: SearchParams = {
  q: trimmed || undefined,
  category: f.category,
  faculty: f.facultyNone ? undefined : f.faculty,
  facultyNone: f.facultyNone || undefined,
  dateFrom: effectiveDateFrom,
  dateTo: f.dateTo,
}
```

---

## Étape 8 — `EventSearchSidebar`

**Fichier :** `frontend/src/components/event/EventSearchSidebar.tsx`

Ajouter un chip « Toutes facultés » **en tête** de la rangée des chips Faculty (avant `Object.values(Faculty).map(...)`). Le chip utilise le même style de bouton pill que les autres, avec la classe active `bg-accent text-white border-accent` quand `filters.facultyNone === true`.

### Comportement au clic

- **Chip « Toutes facultés »** :
  - Actif → `setFilters({ ...filters, facultyNone: undefined, faculty: inchangé })`
  - Inactif → `setFilters({ ...filters, facultyNone: true, faculty: undefined })`

- **Chip faculté nommée** (logique existante étendue) :
  - Le clic pousse toujours `facultyNone: undefined` en plus du toggle sur `faculty`.
  - Un chip actif quand `filters.faculty === id` **ET** `!filters.facultyNone` (le mutex visuel garantit qu'un seul chip paraît actif à la fois).

---

## Étape 9 — Tests frontend

### 9.1 — `EventSearchSidebar.test.tsx`

| Test | Scénario |
|---|---|
| `renders_a_toutes_facultes_chip_alongside_the_faculty_chips` | Le bouton « Toutes facultés » est présent dans le DOM |
| `clicking_toutesFacultes_chip_sets_facultyNone_true` | Click → `setFilters` appelé avec `facultyNone: true` |
| `clicking_toutesFacultes_chip_clears_faculty` | Filtre initial `{ faculty: SCIENCES }` → click « Toutes facultés » → `setFilters` avec `facultyNone: true, faculty: undefined` |
| `clicking_faculty_chip_clears_facultyNone` | Filtre initial `{ facultyNone: true }` → click Sciences → `setFilters` avec `faculty: SCIENCES, facultyNone: undefined` |
| `toutes_facultes_chip_is_active_when_facultyNone_is_true` | Chip a la classe `bg-accent` quand `facultyNone: true` |
| `deselect_toutes_facultes_chip_clears_facultyNone` | Re-click sur le chip actif → `facultyNone: undefined` |
| `no_faculty_chip_appears_active_when_facultyNone_is_true` | Vérifie le mutex visuel : même si `faculty: SCIENCES` est encore en state, le chip SCIENCES ne paraît pas actif quand `facultyNone: true` |

### 9.2 — `useEventSearch.test.ts`

| Test | Scénario |
|---|---|
| `reads_facultyNone_from_url_on_mount` | URL `?facultyNone=true` → `filters.facultyNone === true` |
| `syncs_facultyNone_to_url_when_set` | `setFilters({ facultyNone: true })` → URL contient `?facultyNone=true` |
| `does_not_add_facultyNone_to_url_when_false_or_undefined` | Pas de param `facultyNone` dans l'URL quand absent |
| `sends_facultyNone_in_api_params_when_set` | `searchEvents` appelé avec `{ facultyNone: true, faculty: undefined }` |
| `setting_faculty_clears_facultyNone_in_url` | URL initiale `?facultyNone=true` puis `setFilters({ faculty: SCIENCES })` → URL contient `?faculty=SCIENCES` et plus `facultyNone` |
| `facultyNone_true_overrides_faculty_in_api_params` | Filtre `{ faculty: SCIENCES, facultyNone: true }` → API reçoit `{ facultyNone: true, faculty: undefined }` |

---

## Étape 10 — Documentation

- **`frontend/docs/components.md`** : section `FilterSidebar` — ajouter la description du chip « Toutes facultés » et la règle de mutex.
- **`frontend/docs/types.md`** : section `SearchParams` / `SearchFilters` — documenter `facultyNone?: boolean`.
- **`frontend/docs/sprint-context.md`** : mentionner le chip « Toutes facultés » et son sync URL.
- **`backend/docs/api-contract.md`** : ajouter `facultyNone` aux query params listés pour `/events` et `/events/search`.

---

## Récapitulatif des fichiers à modifier

| Fichier | Action |
|---|---|
| `openapi/openapi.yaml` | Ajouter `facultyNone` sur GET `/events` + GET `/events/search` |
| `backend/.../service/EventService.java` | Param `Boolean facultyNone` + branche mutex + `@SuppressWarnings("java:S107")` |
| `backend/.../service/EventSearchService.java` | Idem |
| `backend/.../resource/EventResource.java` | `@QueryParam("facultyNone") Boolean facultyNone` |
| `backend/.../resource/EventSearchResource.java` | Idem |
| `backend/.../test/.../service/EventServiceMock.java` | Signature + filtre in-memory |
| `backend/.../test/.../service/EventSearchServiceMock.java` | Idem |
| `backend/.../test/.../dto/EventDTOTest.java` | (inchangé) |
| `backend/.../test/.../resource/EventResourceTest.java` | +2 cas facultyNone |
| `backend/.../test/.../resource/EventSearchResourceTest.java` | +2 cas facultyNone |
| `backend/.../test/.../service/EventServiceCoverageTest.java` | +2 cas DB facultyNone + mise à jour des 9 callers existants |
| `backend/.../test/.../service/EventSearchServiceCoverageTest.java` | +2 cas DB facultyNone + mise à jour des callers existants |
| `frontend/src/types/search.ts` | `facultyNone?: boolean` sur SearchFilters et SearchParams |
| `frontend/src/services/eventApi.ts` | `facultyNone?: boolean` sur EventsParams |
| `frontend/src/hooks/useEventSearch.ts` | Lecture URL + sync URL + mutex dans `performSearch` |
| `frontend/src/components/event/EventSearchSidebar.tsx` | Chip « Toutes facultés » + mutex UI |
| `frontend/src/__tests__/components/event/EventSearchSidebar.test.tsx` | +7 cas |
| `frontend/src/__tests__/hooks/useEventSearch.test.ts` | +6 cas |
| `backend/docs/api-contract.md` | Mention `facultyNone` dans la liste des filtres |
| `frontend/docs/components.md` | Description chip « Toutes facultés » + mutex |
| `frontend/docs/types.md` | Champ `facultyNone` |
| `frontend/docs/sprint-context.md` | Mention du chip |
| `specs_archives/specs_claude/specs_s3-faculty-none-filter.md` | Ce document |

---

## Règles critiques

| Règle | Détail |
|---|---|
| `openapi.yaml` EN PREMIER | Modifier le contrat avant toute ligne de code |
| Mutex serveur | `facultyNone=true` ignore `faculty` — `Boolean.TRUE.equals(facultyNone)` en priorité dans le JPQL |
| Mutex client | Sélectionner un chip désactive l'autre côté state ET côté URL |
| Aucune validation 400 | Si les deux params arrivent, on applique la règle de priorité ; pas d'erreur |
| `@SuppressWarnings("java:S107")` | Justifié par la nature REST des endpoints — les params Java mappent 1:1 les query params HTTP |
| Pas de migration SQL | Le champ `faculty` existe déjà, nullable depuis SCRUM-77 |
| camelCase partout | `facultyNone` en camelCase en Java, JSON, TS, URL params |
| Rendu conditionnel sûr | Le chip « Toutes facultés » est actif **ssi** `filters.facultyNone === true` ; les chips nommés sont actifs **ssi** `filters.faculty === id && !filters.facultyNone` |

---

## Checklist Sonar

- [ ] `> 80 %` couverture sur les nouvelles lignes (JaCoCo + Vitest V8)
- [ ] Duplication `< 3 %` sur le code nouveau
- [ ] Security / Reliability / Maintainability Rating : A
- [ ] Backend `./mvnw test` → 292 tests (0 failure)
- [ ] Frontend `npm run build` + `npm run lint` + `npm run test` → 397 tests (0 failure)
