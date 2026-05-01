# Specs SCRUM-131 + SCRUM-132 — Filtre par tags sur la recherche d'événements (back + front)

> **Branche :** `feature/s6-search-tags`
> **Sprint :** S6 — Feature 6f (US-30 « filtrer par mots-clés ») — back + front traités sur **une seule branche / une seule PR**
> **Tickets couverts :** SCRUM-131 (BACK, 2 SP, Antoine) · SCRUM-132 (FRONT, 2 SP, Daniel)
> **Prérequis :** main à jour. SCRUM-126 ✅ mergé (`Event.tags` + `event_tags` table + `EventService.normalizeTags`). SCRUM-128 ✅ mergé (composant `TagInput`). SCRUM-77 ✅ mergé (`Faculty` enum, filtres `faculty` / `facultyNone` déjà branchés).
> **Règle d'or :** Modifier `openapi/openapi.yaml` EN PREMIER, puis coder Resource → Service → Mock → Tests back. Ensuite types → service → hook → sidebar → tests front. Enfin la doc dans le **même commit que le code**.

---

## Contexte

### Problème

Aujourd'hui, `GET /api/events/search` accepte `q`, `category`, `faculty`, `facultyNone`, `dateFrom`, `dateTo`, `page`, `size`. Les événements portent une collection `tags: List<String>` (SCRUM-126, normalisée trim + lowercase + dédup) **mais aucun filtre serveur n'expose cette dimension**. Côté UI, la sidebar de recherche n'a pas de zone de saisie de mots-clés. La user story US-30 (« je veux filtrer par mots-clés ») n'est donc pas livrable sans cette tâche.

### Solution

1. **Backend** — étendre `GET /api/events/search` avec un paramètre multi-valeurs `tags` (sémantique **OR** : un event remonte s'il porte **au moins un** des tags demandés). JPQL via clause `EXISTS` sur la collection `event_tags`. Normalisation lowercase côté service pour matcher la normalisation de persistance.
2. **Frontend** — insérer un `<TagInput>` (composant existant SCRUM-128) dans `EventSearchSidebar`, propager les tags dans `SearchFilters` / `SearchParams` / l'URL (multi-param `?tags=foo&tags=bar`) et l'appel `GET /events/search`.

### Décision OR vs AND (à acter dans la spec)

**Choix : OR.** Justifications :
- Cohérent avec le wording de US-30 (« filtrer par mots-clés ») qui suggère un élargissement de la recherche, pas une intersection.
- Comportement courant des moteurs de recherche grand public (Google, GitHub) sur les filtres tags.
- Le ticket SCRUM-131 propose explicitement `lower(t) IN :tags` → sémantique OR.
- AND nécessiterait `COUNT(DISTINCT t) = :tagCount` ou un `EXISTS` par tag — plus coûteux et hors scope du sprint. Si AND est demandé un jour, ajouter un param `tagsMode=any|all` plutôt que casser l'existant.

### Sémantique URL

Multi-valeurs via **répétition du paramètre** : `?tags=quarkus&tags=sport`. **Pas de séparateur virgule** (`?tags=quarkus,sport` serait interprété comme un seul tag « quarkus,sport »). Cohérent avec le contrat JAX-RS `@QueryParam("tags") List<String> tags`.

---

### Ce qui existe déjà (ne pas retoucher sauf indication contraire)

| Fichier | État |
|---|---|
| `openapi/openapi.yaml` (lignes 1235–1301) | Path `/events/search` complet — manque uniquement le paramètre `tags`. Le schéma `Event` (ligne ~244) expose déjà `tags: array of string, max 20`. |
| `backend/src/main/java/ch/unige/events/entity/Event.java` (lignes 67–74) | Champ `tags: List<String>` via `@ElementCollection(fetch=EAGER)` sur la table `event_tags(event_id, tag VARCHAR(64))`. À ne pas toucher. |
| `backend/src/main/java/ch/unige/events/service/EventService.java` (lignes 328–341) | Méthode `static List<String> normalizeTags(List<String> input)` package-private — trim + lowercase Locale.ROOT + dédup ordonnée. **À réutiliser depuis `EventSearchService`**. |
| `backend/src/main/java/ch/unige/events/dto/event/EventDTO.java` | Expose `tags` en sortie via `List.copyOf`. À ne pas toucher. |
| `frontend/src/components/utils/TagInput.tsx` | Composant complet (props : `value: string[]`, `onChange: (tags: string[]) => void`, `placeholder?: string`, `maxTags?: number`). Trim à l'ajout, dédup automatique, gestion Enter/virgule/Backspace. **À utiliser tel quel.** |
| `frontend/src/services/api.ts` | Instance Axios avec intercepteur Bearer. **À ne pas modifier** — le `paramsSerializer` sera passé localement dans `searchApi.ts`. |
| `frontend/src/bones/search-results.bones.json` | Skeleton de la grille de résultats. **Inchangé** : la sidebar n'a pas d'état loading propre, le `<TagInput>` est rendu instantanément. |

### Ce qui est à modifier

| Fichier | Modification |
|---|---|
| `openapi/openapi.yaml` | Ajouter le paramètre `tags` dans `/events/search` (étape 0). |
| `backend/src/main/java/ch/unige/events/resource/EventSearchResource.java` | Ajouter `@QueryParam("tags") List<String> tags` au handler. |
| `backend/src/main/java/ch/unige/events/service/EventSearchService.java` | Ajouter `List<String> tags` dans la signature de `search(...)`, condition JPQL `EXISTS`. |
| `backend/src/test/java/ch/unige/events/service/EventSearchServiceMock.java` | Aligner l'override sur la nouvelle signature, filtrer in-memory sur `event.tags`. |
| `backend/src/test/java/ch/unige/events/service/EventSearchServiceCoverageTest.java` | Mettre à jour tous les call-sites existants (passer `null` pour `tags`) + ajouter 4 nouveaux tests couvrant le filtre. |
| `backend/src/test/java/ch/unige/events/resource/EventSearchResourceTest.java` | Ajouter 4 nouveaux tests REST. |
| `frontend/src/types/search.ts` | Ajouter `tags?: string[]` dans `SearchFilters` et `SearchParams`. |
| `frontend/src/types/event.ts` | Ajouter `tags?: string[]` dans le type `Event` (la search response l'expose désormais). |
| `frontend/src/services/searchApi.ts` | Configurer `paramsSerializer: { indexes: null }` pour la sérialisation `?tags=a&tags=b` sans crochets. |
| `frontend/src/hooks/useEventSearch.ts` | Lire `searchParams.getAll('tags')` à l'init, générer un `URLSearchParams` à la place du `Record<string,string>` plat lors de la sync vers l'URL, ajouter `tags` dans `SearchParams`. |
| `frontend/src/components/event/EventSearchSidebar.tsx` | Insérer une nouvelle section « Mots-clés » entre Date et Reset, avec `<TagInput>`. |

### Ce qui est à créer

Aucun **nouveau fichier**. La feature est entièrement composée d'extensions de fichiers existants — les composants (`TagInput`) et l'entité (`Event.tags`) existent déjà.

### Ce qui n'est PAS dans le scope

- ❌ Pas de modification de `frontend/src/pages/event/EventsSearchPage.tsx` (la page consomme `useSearch` et n'a pas besoin d'être touchée).
- ❌ Pas de nouveau skeleton — la sidebar n'a pas d'état loading, et `search-results.bones.json` reste valide pour la grille de résultats.
- ❌ Pas d'endpoint backend de suggestions de tags (le stub `fetchSuggestions` reste tel quel).
- ❌ Pas de filtrage par tags dans `GET /api/events` (paginated list) — uniquement dans `GET /api/events/search`.
- ❌ Pas de chips cliquables dans `EventDetailPage` ou `EventCard` (mentionné dans SCRUM-127, hors scope).
- ❌ Pas de support du séparateur virgule dans l'URL (uniquement `?tags=foo&tags=bar`).

---

## Étape 0 — `openapi/openapi.yaml` (OBLIGATOIRE EN PREMIER)

**Fichier :** `/workspace/openapi/openapi.yaml`

Localiser le path `/events/search` (ligne ~1236) et **insérer** le paramètre suivant **après** `facultyNone` (ligne ~1267, juste avant `dateFrom`) :

```yaml
        - name: tags
          in: query
          description: |
            Filtre les événements possédant au moins un des tags fournis (sémantique OR).
            Comparaison case-insensitive — les tags sont stockés en lowercase côté serveur.
            Multi-valeurs via répétition du paramètre : `?tags=foo&tags=bar`.
            Les valeurs blanches/vides sont ignorées silencieusement.
          schema:
            type: array
            items:
              type: string
              maxLength: 64
          style: form
          explode: true
```

Mettre également à jour la `description` du path (ligne ~1239) en y ajoutant `tags` :

```yaml
      description: |
        Recherche insensible à la casse (ILIKE) sur le titre et la description.
        Filtres optionnels : category, faculty, plage de dates (startDate), tags.
```

Enfin, **supprimer** le commentaire `# TODO: à implémenter — endpoint absent du backend et du frontend` (ligne 1235) — l'endpoint existe et est désormais complet.

> **Pourquoi `style: form` + `explode: true`** : c'est le style par défaut OpenAPI 3 pour les paramètres `in: query` de type `array`, et il sérialise en `?tags=a&tags=b` (et **pas** `?tags=a,b` ni `?tags[0]=a&tags[1]=b`). Le rendre explicite évite toute ambiguïté pour les générateurs de SDK et reflète exactement ce que JAX-RS attend.

---

## Étape 1 — `EventSearchResource.java`

**Fichier :** `backend/src/main/java/ch/unige/events/resource/EventSearchResource.java`

### 1.1 — Ajouter l'import

```java
import java.util.List;
```

### 1.2 — Ajouter le paramètre dans la méthode `search`

Remplacer la signature actuelle (lignes 33–43) par :

```java
    @GET
    @PermitAll
    @SuppressWarnings("java:S107") // Filter-heavy search endpoint — flat params match the REST query signature 1:1.
    public List<EventDTO> search(
            @QueryParam("q") String q,
            @QueryParam("category") EventCategory category,
            @QueryParam("faculty") Faculty faculty,
            @QueryParam("facultyNone") Boolean facultyNone,
            @QueryParam("tags") List<String> tags,
            @QueryParam("dateFrom") LocalDate dateFrom,
            @QueryParam("dateTo") LocalDate dateTo,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        return eventSearchService.search(q, category, faculty, facultyNone, tags, dateFrom, dateTo, page, size);
    }
```

**Points à respecter :**
- `tags` est **inséré entre `facultyNone` et `dateFrom`** — ordre logique : (full-text) → (catégorisation : category, faculty, facultyNone, tags) → (temporel : dateFrom, dateTo) → (pagination).
- Pas de `@DefaultValue` ni `@NotNull` : un paramètre absent donne `null`, un paramètre présent sans valeur (`?tags=`) donne une liste contenant une chaîne vide — géré par `EventSearchService` (étape 2).
- Le `@SuppressWarnings("java:S107")` reste valide : la signature passe de 8 à 9 paramètres, justification inchangée.
- JAX-RS RESTEasy lit nativement `?tags=a&tags=b` en `List<String>` sans configuration supplémentaire.

---

## Étape 2 — `EventSearchService.java`

**Fichier :** `backend/src/main/java/ch/unige/events/service/EventSearchService.java`

### 2.1 — Ajouter les imports manquants

Vérifier la présence de `java.util.List` (déjà importé). Aucun autre import à ajouter — `EventService.normalizeTags` est dans le même package `ch.unige.events.service`.

### 2.2 — Mettre à jour la signature de `search`

Remplacer la signature (ligne 40) par :

```java
    @Transactional
    @SuppressWarnings("java:S107") // Filter-heavy search endpoint — flat params match the REST query signature 1:1.
    public List<EventDTO> search(String q, EventCategory category, Faculty faculty, Boolean facultyNone,
                                  List<String> tags,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  int page, int size) {
```

### 2.3 — Ajouter la condition JPQL `EXISTS` sur les tags

**Insérer** ce bloc **juste après** le filtre `facultyNone` / `faculty` (après la ligne 67 `params.put("faculty", faculty);`) et **avant** le filtre `dateFrom` (ligne 68) :

```java
        // Tags (SCRUM-131) : substring match case-insensitive, sémantique OR entre les valeurs.
        // Ex. ?tags=foot matche un event dont un tag est "football". `%` et `_` saisis sont traités
        // littéralement via ESCAPE '|' + escapeLikePattern.
        List<String> normalizedTags = EventService.normalizeTags(tags);
        if (!normalizedTags.isEmpty()) {
            List<String> tagClauses = new ArrayList<>();
            for (int i = 0; i < normalizedTags.size(); i++) {
                String paramName = "tag" + i;
                tagClauses.add("LOWER(t) LIKE :" + paramName + " ESCAPE '|'");
                params.put(paramName, "%" + escapeLikePattern(normalizedTags.get(i)) + "%");
            }
            conditions.add("EXISTS (SELECT 1 FROM Event e2 JOIN e2.tags t WHERE e2.id = e.id AND ("
                    + String.join(" OR ", tagClauses) + "))");
        }
```

Et en méthode privée statique (ordre important : échapper d'abord le char d'échappement `|`) :

```java
private static String escapeLikePattern(String s) {
    return s.replace("|", "||").replace("%", "|%").replace("_", "|_");
}
```

**Points à respecter :**
- **Substring match** via `LIKE '%...%'` : `?tags=foot` matche `football`, `barefoot-running`, etc. Sur-ensemble strict de l'égalité exacte (tous les anciens tests passent sans modification).
- **Réutilisation explicite** de `EventService.normalizeTags(tags)` — une seule source de vérité pour la règle de normalisation. Garantit que `["Quarkus", " sport ", "Quarkus", null]` devienne `["quarkus", "sport"]`. Filtre aussi le cas blank/null.
- **JPQL EXISTS avec sous-requête corrélée** sur `e2.id = e.id` — Hibernate génère un EXISTS SQL natif, pas un `IN (SELECT ...)`. Pas de produit cartésien, pas de duplication de lignes même si un event a 5 tags qui matchent.
- **`LOWER(t)`** dans la sous-requête — défense en profondeur : même si la persistance était contournée par un import direct DB, la comparaison reste case-insensitive.
- **Escape des wildcards user-input** : `%` et `_` saisis par l'utilisateur sont échappés via `ESCAPE '|'`. Le char `|` est choisi plutôt que `\` pour éviter les conflits avec l'échappement JDBC/Hibernate.
- **Binding individuel** (`:tag0`, `:tag1`, …) — jamais de concaténation de valeur utilisateur dans la chaîne JPQL. Toute la sécurité repose sur `params.put(paramName, ...)`.
- **Pas d'accent-folding** : `concert` ne matchera pas `concèrt`. Comportement assumé et documenté dans la doc OpenAPI / api-contract (les utilisateurs saisissent les tags qu'ils ont mis sur leurs events).
- **Performance** : `Event.tags` est en `@ElementCollection(fetch = EAGER)` — la condition EXISTS reste correcte. `LIKE '%x%'` n'est pas indexable (pas de préfixe fixe) mais sur un dataset typique (1000 events × 3 tags moyens) la performance reste sous-milliseconde. Si le volume explose, migration vers Postgres `pg_trgm` ou full-text à envisager — hors scope actuel.

### 2.4 — Vérification post-modification

Le reste de la méthode (construction du `jpql`, ORDER BY, pagination, comptage Attendance, mapping EventDTO) **n'est pas modifié**. Ne pas toucher à la logique Zurich/UTC pour les dates ni au calcul `availableSpots` / `waitlistedCount`.

---

## Étape 3 — `EventSearchServiceMock.java`

**Fichier :** `backend/src/test/java/ch/unige/events/service/EventSearchServiceMock.java`

### 3.1 — Mettre à jour l'override pour la nouvelle signature

Remplacer la méthode `search` (lignes 61–88) par :

```java
    @Override
    @SuppressWarnings("java:S107")
    public List<EventDTO> search(String q, EventCategory category, Faculty faculty, Boolean facultyNone,
                                  List<String> tags,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  int page, int size) {
        // Normalisation alignée sur EventService.normalizeTags (lowercase + trim + dédup, ignore null/blank).
        List<String> normalizedTags = (tags == null || tags.isEmpty())
                ? List.of()
                : tags.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(t -> t.trim().toLowerCase(java.util.Locale.ROOT))
                    .filter(t -> !t.isEmpty())
                    .distinct()
                    .toList();

        return eventsById.values().stream()
                .filter(e -> {
                    if (q == null || q.isBlank()) return true;
                    String lower = q.toLowerCase();
                    return (e.title != null && e.title.toLowerCase().contains(lower))
                            || (e.description != null && e.description.toLowerCase().contains(lower));
                })
                .filter(e -> category == null || e.category == category)
                .filter(e -> {
                    if (Boolean.TRUE.equals(facultyNone)) {
                        return e.faculty == null;
                    }
                    return faculty == null || e.faculty == faculty;
                })
                .filter(e -> {
                    if (normalizedTags.isEmpty()) return true;
                    if (e.tags == null || e.tags.isEmpty()) return false;
                    // Substring match (OR semantics) : au moins un tag de l'event doit contenir
                    // au moins une des valeurs fournies. String.contains() est littéral → '%' et '_'
                    // saisis sont naturellement traités comme du texte (aligné avec ESCAPE '|' côté DB).
                    return e.tags.stream()
                            .filter(java.util.Objects::nonNull)
                            .map(t -> t.toLowerCase(java.util.Locale.ROOT))
                            .anyMatch(eventTag -> normalizedTags.stream().anyMatch(eventTag::contains));
                })
                .filter(e -> dateFrom == null || !e.startDate.isBefore(dateFrom.atStartOfDay()))
                .filter(e -> dateTo == null || !e.startDate.isAfter(dateTo.atTime(23, 59, 59)))
                .sorted(Comparator.comparing((Event e) -> e.startDate).thenComparingLong(e -> e.id))
                .skip((long) page * size)
                .limit(size)
                .map(e -> EventDTO.from(e, 0L, e.capacity == null ? null : (long) e.capacity, 0L))
                .toList();
    }
```

**Points à respecter :**
- La normalisation in-memory **réplique** `EventService.normalizeTags` plutôt que de l'appeler — évite un couplage entre le mock et la classe testée. Si le mock cassait silencieusement, on s'en rendrait compte au test.
- Filtre OR : `anyMatch(normalizedTags::contains)` — au moins un tag commun.
- Si l'event n'a pas de tag mais que le filtre en demande un → le mock retourne `false` (aligné sur le comportement JPQL EXISTS).

### 3.2 — Pas de modification de `seedEvent`

`seedEvent` initialise `event.tags` à `null` par défaut (le constructeur de `Event` initialise `tags = new ArrayList<>()` côté champ — vérifier que les tests qui veulent tester le filtre tags assignent `event.tags = List.of("...")` après création, comme c'est déjà le pattern pour `faculty`).

---

## Étape 4 — Tests `EventSearchResourceTest.java`

**Fichier :** `backend/src/test/java/ch/unige/events/resource/EventSearchResourceTest.java`

### 4.1 — Ajouter les nouveaux tests à la fin du fichier (avant la fermeture de la classe)

Ajouter le bloc suivant après `search_withFacultyNoneAndFaculty_facultyNoneWins` (ligne 322) :

```java
    // --- Filtre ?tags= (SCRUM-131) ---

    @Test
    void search_withSingleTag_returnsMatchingEvents() {
        var e1 = eventSearchServiceMock.seedEvent("Conférence Java", null, EventCategory.CONFERENCE, null);
        e1.tags = java.util.List.of("quarkus", "java");
        var e2 = eventSearchServiceMock.seedEvent("Match de foot", null, EventCategory.SPORTS, null);
        e2.tags = java.util.List.of("sport");

        given()
                .queryParam("tags", "quarkus")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Conférence Java"));
    }

    @Test
    void search_withMultipleTags_returnsUnion() {
        // Sémantique OR : un event remonte s'il porte au moins UN des tags demandés.
        var e1 = eventSearchServiceMock.seedEvent("Event Quarkus", null, EventCategory.CONFERENCE, null);
        e1.tags = java.util.List.of("quarkus");
        var e2 = eventSearchServiceMock.seedEvent("Event Sport", null, EventCategory.SPORTS, null);
        e2.tags = java.util.List.of("sport");
        var e3 = eventSearchServiceMock.seedEvent("Event Cinéma", null, EventCategory.CULTURAL, null);
        e3.tags = java.util.List.of("cinema");

        given()
                .queryParam("tags", "quarkus")
                .queryParam("tags", "sport")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(2));
    }

    @Test
    void search_withTags_isCaseInsensitive() {
        var e = eventSearchServiceMock.seedEvent("Event Quarkus", null, EventCategory.CONFERENCE, null);
        e.tags = java.util.List.of("quarkus");

        given()
                .queryParam("tags", "QUARKUS")  // Maj côté requête → doit matcher "quarkus" en DB
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1));
    }

    @Test
    void search_withUnknownTag_returnsEmpty() {
        var e = eventSearchServiceMock.seedEvent("Event Quarkus", null, EventCategory.CONFERENCE, null);
        e.tags = java.util.List.of("quarkus");

        given()
                .queryParam("tags", "totallyimpossibletag")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(0));
    }

    @Test
    void search_withBlankTag_isIgnored() {
        // `?tags=` (vide) → ignoré silencieusement, retourne tous les events comme s'il n'y avait pas de filtre.
        eventSearchServiceMock.seedEvent("Event A", null, EventCategory.ACADEMIC, null);
        eventSearchServiceMock.seedEvent("Event B", null, EventCategory.ACADEMIC, null);

        given()
                .queryParam("tags", "")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(2));
    }
```

### 4.2 — Récapitulatif des nouveaux cas

| Test | Comportement vérifié |
|---|---|
| `search_withSingleTag_returnsMatchingEvents` | `?tags=quarkus` retourne uniquement les events avec ce tag |
| `search_withMultipleTags_returnsUnion` | `?tags=a&tags=b` retourne les events avec a OU b (OR) |
| `search_withTags_isCaseInsensitive` | `?tags=QUARKUS` matche `tags=["quarkus"]` |
| `search_withUnknownTag_returnsEmpty` | Tag inexistant → liste vide (pas 404) |
| `search_withBlankTag_isIgnored` | `?tags=` vide → ignoré silencieusement |

---

## Étape 5 — Tests `EventSearchServiceCoverageTest.java`

**Fichier :** `backend/src/test/java/ch/unige/events/service/EventSearchServiceCoverageTest.java`

### 5.1 — Mettre à jour TOUS les call-sites existants

Tous les appels à `eventSearchService.search(...)` passent actuellement 8 arguments. **Insérer `null` à la 5e position** (entre `facultyNone` et `dateFrom`).

Exemple — remplacer :
```java
List<EventDTO> result = eventSearchService.search(null, null, null, null, null, null, 0, 20);
```
par :
```java
List<EventDTO> result = eventSearchService.search(null, null, null, null, null, null, null, 0, 20);
```

Faire ce remplacement sur les **16 occurrences existantes** dans le fichier (lignes ~41, 55, 68, 80, 94, 108, 123, 138, 155–162, 186, 200, 215–217, 233, 247, 260, 272, 286). Vérifier la compilation après remplacement.

### 5.2 — Ajouter les nouveaux tests DB-backed

Ajouter en fin de classe (après `search_withFacultyNoneAndFaculty_facultyNoneWins`, avant le bloc `// --- helpers ---`) :

```java
    // --- Filtre tags (SCRUM-131) ---

    @Test
    @TestTransaction
    void search_withSingleTag_jpqlExistsMatches() {
        User user = persistUser("auth0|st1", "st1@example.com");
        Event e1 = persistEvent("Conf Java", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);
        e1.tags = List.of("quarkus", "java");
        Event e2 = persistEvent("Foot", null, EventCategory.SPORTS, LocalDateTime.now().plusDays(2), user);
        e2.tags = List.of("sport");
        entityManager.flush();

        List<EventDTO> result = eventSearchService.search(
                null, null, null, null, List.of("quarkus"), null, null, 0, 20);

        assertEquals(1, result.size());
        assertEquals("Conf Java", result.get(0).title());
    }

    @Test
    @TestTransaction
    void search_withMultipleTags_returnsUnion() {
        User user = persistUser("auth0|st2", "st2@example.com");
        Event e1 = persistEvent("Quarkus event", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);
        e1.tags = List.of("quarkus");
        Event e2 = persistEvent("Sport event", null, EventCategory.SPORTS, LocalDateTime.now().plusDays(2), user);
        e2.tags = List.of("sport");
        Event e3 = persistEvent("Cinéma event", null, EventCategory.CULTURAL, LocalDateTime.now().plusDays(3), user);
        e3.tags = List.of("cinema");
        entityManager.flush();

        List<EventDTO> result = eventSearchService.search(
                null, null, null, null, List.of("quarkus", "sport"), null, null, 0, 20);

        assertEquals(2, result.size());
    }

    @Test
    @TestTransaction
    void search_withTagsCaseInsensitive_matches() {
        User user = persistUser("auth0|st3", "st3@example.com");
        Event e = persistEvent("Quarkus event", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);
        e.tags = List.of("quarkus");
        entityManager.flush();

        List<EventDTO> result = eventSearchService.search(
                null, null, null, null, List.of("QUARKUS"), null, null, 0, 20);

        assertEquals(1, result.size());
    }

    @Test
    @TestTransaction
    void search_withUnknownTag_returnsEmpty() {
        User user = persistUser("auth0|st4", "st4@example.com");
        Event e = persistEvent("Quarkus event", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);
        e.tags = List.of("quarkus");
        entityManager.flush();

        List<EventDTO> result = eventSearchService.search(
                null, null, null, null, List.of("totallyimpossibletag"), null, null, 0, 20);

        assertTrue(result.isEmpty());
    }

    @Test
    @TestTransaction
    void search_withBlankAndNullTags_areFilteredOut() {
        // Une liste contenant des null/blank et un tag valide → comportement = filtre sur le seul tag valide.
        User user = persistUser("auth0|st5", "st5@example.com");
        Event e = persistEvent("Quarkus event", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);
        e.tags = List.of("quarkus");
        entityManager.flush();

        List<EventDTO> tagsList = new java.util.ArrayList<>();
        tagsList.add(null);
        tagsList.add("");
        tagsList.add("  ");
        tagsList.add("quarkus");

        List<EventDTO> result = eventSearchService.search(
                null, null, null, null, tagsList, null, null, 0, 20);

        assertEquals(1, result.size());
    }

    @Test
    @TestTransaction
    void search_withTagsAndCategory_combined() {
        User user = persistUser("auth0|st6", "st6@example.com");
        Event e1 = persistEvent("Conf Quarkus", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);
        e1.tags = List.of("quarkus");
        Event e2 = persistEvent("Match Quarkus", null, EventCategory.SPORTS, LocalDateTime.now().plusDays(2), user);
        e2.tags = List.of("quarkus");
        entityManager.flush();

        List<EventDTO> result = eventSearchService.search(
                null, EventCategory.CONFERENCE, null, null, List.of("quarkus"), null, null, 0, 20);

        assertEquals(1, result.size());
        assertEquals("Conf Quarkus", result.get(0).title());
    }
```

> **Note SonarCloud** : 6 nouveaux tests DB-backed couvrant les 4 branches du filtre (vide, valide, case-insensitive, intersection avec autre filtre). Combinés avec les 5 tests REST de l'étape 4, on dépasse largement les 80% sur le nouveau code.

---

## Étape 6 — Frontend : types

### 6.1 — `frontend/src/types/search.ts`

Remplacer les interfaces `SearchFilters` et `SearchParams` par :

```ts
import type { Event, EventCategory } from "./event"
import type { Faculty } from "./faculty"

// `faculty` et `facultyNone` sont mutuellement exclusifs côté UI :
// `facultyNone: true` signifie « uniquement les events sans faculté rattachée »
// et désélectionne toute sélection `faculty`. Côté serveur, si les deux sont
// envoyés, `facultyNone` a priorité (règle documentée dans openapi.yaml).
export interface SearchFilters {
  category?: EventCategory
  faculty?: Faculty
  facultyNone?: boolean
  tags?: string[]
  dateFrom?: string
  dateTo?: string
  includePast: boolean
}

export interface SearchParams {
  q?: string
  category?: EventCategory
  faculty?: Faculty
  facultyNone?: boolean
  tags?: string[]
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
}

export type SearchResponse = Event[]
```

**Points à respecter :**
- `tags?: string[]` — optionnel, `undefined` quand aucun tag n'est filtré (cohérent avec les autres champs).
- Position `tags` placée logiquement entre `facultyNone` et `dateFrom`, miroir du backend.

### 6.2 — `frontend/src/types/event.ts`

Le DTO backend `EventDTO` expose désormais `tags` (toujours une liste, jamais `null` — cf. `data-model.md` ligne 209). Pour aligner le type TypeScript :

Remplacer le type `Event` (lignes 3–20) par :

```ts
export type Event = {
  id: number
  title: string
  description?: string
  location: string
  startDate: string
  endDate: string
  category: EventCategory
  faculty?: Faculty | null
  bannerUrl?: string
  creatorId: string
  status: EventStatus
  capacity?: number
  allDay: boolean
  attendingCount: number
  tags?: string[]
  createdAt: string
  updatedAt?: string
}
```

> **Pourquoi `tags?` et non `tags: string[]`** : le backend retourne toujours un array (potentiellement vide), mais le rendre optionnel évite de casser tous les fixtures de tests existants (`searchApi.test.ts`, `useEventSearch.test.ts`, `EventCard.test.tsx`, …) qui ne fournissent pas `tags` dans leurs mocks. Les consommateurs qui veulent afficher les tags peuvent défensivement `event.tags ?? []`.

---

## Étape 7 — Frontend : `searchApi.ts`

**Fichier :** `frontend/src/services/searchApi.ts`

### 7.1 — Configurer le `paramsSerializer` pour Axios

Remplacer la fonction `searchEvents` par :

```ts
import api from './api'
import type { SearchParams, SearchResponse } from '@/types/search'

// Sérialise les arrays sans crochets (`?tags=a&tags=b` au lieu de `?tags[]=a&tags[]=b`),
// ce qui correspond au format attendu par JAX-RS (`@QueryParam("tags") List<String> tags`).
const ARRAY_PARAMS_SERIALIZER = {
  indexes: null,
} as const

export async function searchEvents(params: SearchParams, signal?: AbortSignal): Promise<SearchResponse> {
  const response = await api.get<SearchResponse>('/events/search', {
    params,
    paramsSerializer: ARRAY_PARAMS_SERIALIZER,
    ...(signal !== undefined && { signal }),
  })
  return response.data
}

// TODO: No suggestion endpoint defined in openapi.yaml — stub returning empty array until backend provides one
// TODO: Forward signal to Axios when the real endpoint is available
export async function fetchSuggestions(_query: string, _signal?: AbortSignal): Promise<string[]> {
  return Promise.resolve([])
}
```

**Points à respecter :**
- `indexes: null` est la valeur Axios qui sérialise `[a, b]` en `key=a&key=b` **sans crochets ni indices** — c'est le comportement requis par JAX-RS pour `List<String>`.
- `as const` figé pour garantir le type littéral `null` (pas `null | undefined`).
- La constante est externalisée (vs inline dans l'objet `get`) pour rendre l'intention explicite et éviter sa duplication si un autre endpoint avec param array apparaît plus tard.
- **Ne pas toucher** à `services/api.ts` — ce serializer est local à `searchEvents` pour ne pas affecter les autres appels qui ne manipulent pas d'arrays.

### 7.2 — Adapter le test existant

Le test `'calls GET /events/search with params and returns data'` (lignes 36–44 de `searchApi.test.ts`) compare l'argument config sans `paramsSerializer`. Il faudra mettre à jour les assertions pour autoriser la propriété `paramsSerializer`. Voir étape 11.

---

## Étape 8 — Frontend : `useEventSearch.ts`

**Fichier :** `frontend/src/hooks/useEventSearch.ts`

### 8.1 — Init depuis `searchParams.getAll('tags')`

Remplacer le bloc d'initialisation `setFiltersState` (lignes 31–38) par :

```ts
  const [filters, setFiltersState] = useState<SearchFilters>(() => {
    const initialTags = searchParams.getAll('tags').filter((t) => t.trim().length > 0)
    return {
      category: (searchParams.get('category') as EventCategory) || undefined,
      faculty: (searchParams.get('faculty') as Faculty) || undefined,
      facultyNone: searchParams.get('facultyNone') === 'true' ? true : undefined,
      tags: initialTags.length > 0 ? initialTags : undefined,
      dateFrom: searchParams.get('dateFrom') || undefined,
      dateTo: searchParams.get('dateTo') || undefined,
      includePast: searchParams.get('includePast') === 'true',
    }
  })
```

**Points à respecter :**
- `searchParams.getAll('tags')` — `.get()` ne retourne que la première occurrence ; `.getAll()` retourne **toutes** les valeurs du paramètre multi-valeurs.
- Filtrage `.filter(t => t.trim().length > 0)` pour ignorer les `?tags=` vides côté URL.
- `tags: initialTags.length > 0 ? initialTags : undefined` — convention du hook : un filtre absent vaut `undefined`, pas `[]`.
- Wrapper en lazy initializer `useState(() => …)` pour ne lire `searchParams` qu'au premier rendu (cohérent avec les autres champs si on les passe en lazy ; sinon ce wrap reste localisé à la modification — vérifier au moment de l'implémentation s'il faut aussi lazy-wrap les autres pour la cohérence ou laisser tel quel).

### 8.2 — Sync état → URL avec `URLSearchParams`

Remplacer le `useEffect` de sync (lignes 57–71) par :

```ts
  // Sync state → URL (replace so browser history stays clean)
  useEffect(() => {
    const next = new URLSearchParams()
    const trimmedQuery = query.trim()
    if (trimmedQuery) next.set('q', trimmedQuery)
    if (filters.category) next.set('category', filters.category)
    if (filters.facultyNone) {
      next.set('facultyNone', 'true')
    } else if (filters.faculty) {
      next.set('faculty', filters.faculty)
    }
    if (filters.tags && filters.tags.length > 0) {
      filters.tags.forEach((t) => next.append('tags', t))
    }
    if (filters.dateFrom) next.set('dateFrom', filters.dateFrom)
    if (filters.dateTo) next.set('dateTo', filters.dateTo)
    if (filters.includePast) next.set('includePast', 'true')
    setSearchParams(next, { replace: true })
  }, [query, filters, setSearchParams])
```

**Points à respecter :**
- Passage du `Record<string, string>` plat à un `URLSearchParams` instance — c'est la seule structure de l'API `setSearchParams` qui supporte les multi-valeurs.
- `forEach((t) => next.append('tags', t))` — `append` (pas `set`) pour répéter le paramètre.
- Ordre : `tags` après faculty/facultyNone, avant les dates — cohérent avec l'OpenAPI.

### 8.3 — Inclure `tags` dans `performSearch`

Remplacer le bloc `params: SearchParams` (lignes 108–115) par :

```ts
    const params: SearchParams = {
      q: trimmed || undefined,
      category: f.category,
      faculty: f.facultyNone ? undefined : f.faculty,
      facultyNone: f.facultyNone || undefined,
      tags: f.tags && f.tags.length > 0 ? f.tags : undefined,
      dateFrom: effectiveDateFrom,
      dateTo: f.dateTo,
    }
```

### 8.4 — Pas de modification de `setFilters`, `resetFilters`, `selectSuggestion`, `searchNow`

Ces callbacks consomment déjà `f` ou `filtersRef.current` opaquement — ils transportent automatiquement le nouveau champ `tags`. `DEFAULT_FILTERS = { includePast: false }` reste inchangé : reset → `tags` redevient `undefined`. Aucune autre modification du hook.

---

## Étape 9 — Frontend : `EventSearchSidebar.tsx`

**Fichier :** `frontend/src/components/event/EventSearchSidebar.tsx`

### 9.1 — Ajouter les imports

```ts
import TagInput from '@/components/utils/TagInput'
```

### 9.2 — Insérer la nouvelle section « Mots-clés »

**Insérer** ce bloc **entre** la section Date (qui se termine ligne 133 par `</div>` du wrapper Date) et `<div className="border-t border-border/50" />` (ligne 135 — celui avant le checkbox includePast) :

```tsx
      <div className="border-t border-border/50" />

      {/* Tags (SCRUM-132) */}
      <div>
        <SectionLabel>Mots-clés</SectionLabel>
        <TagInput
          value={filters.tags ?? []}
          onChange={(tags) =>
            setFilters({ ...filters, tags: tags.length > 0 ? tags : undefined })
          }
          placeholder="Ex. quarkus, sport…"
          maxTags={10}
        />
      </div>
```

> **Position dans la sidebar** : Catégorie → Faculté → Date → **Mots-clés** → Afficher les événements passés → Reset. Place les filtres « catégorisants » avant les filtres « contextuels » et garde le checkbox include-past + reset en bas comme actions globales.

**Points à respecter :**
- `value={filters.tags ?? []}` — `TagInput` attend `string[]`, jamais `undefined`.
- `tags.length > 0 ? tags : undefined` — convention du hook : un filtre vide est représenté `undefined`, pas `[]`. Symétrique de la sync URL.
- `maxTags={10}` — limite cohérente avec la limite serveur (`max 20` dans l'OpenAPI). 10 suffit largement à l'UX search ; on garde de la marge sous le plafond.
- Pas de `placeholder` accent ou caractère exotique, pour ne pas surprendre sur les inputs date juste au-dessus.

### 9.3 — Const map / variants ?

Pas applicable ici — le bloc « Mots-clés » est un wrapper unique sans variantes visuelles.

---

## Étape 10 — Skeleton

**Décision : aucun nouveau skeleton requis.**

Justification :
- `EventSearchSidebar` n'a **pas d'état loading propre**. Il rend instantanément à partir des props `filters`, `setFilters`, `resetFilters` qui sont synchrones. Aucun appel API direct.
- `TagInput` est purement contrôlé (pas de fetch interne, pas de loading state).
- Les **résultats** de recherche utilisent déjà `search-results.bones.json` via `<Skeleton name="search-results">` dans `EventsSearchPage.tsx` (lignes 178–185). Ce skeleton couvre la grille de cards résultat — non concerné par l'ajout du filtre tags qui ne change pas la structure de la grille.
- Conformément à `frontend/skeleton/README.md` § « Quand générer un skeleton », un nouveau bones n'est requis que pour une **page ou un composant qui effectue un appel API et affiche un état `loading`**. Aucune des deux conditions n'est remplie.

**Aucune mise à jour** de `frontend/src/bones/`, `registry.js`, ou de la table « Skeletons existants » dans `AGENTS.md` n'est nécessaire.

---

## Étape 11 — Tests frontend

### 11.1 — `frontend/src/__tests__/services/searchApi.test.ts`

Mettre à jour les assertions des tests existants pour autoriser la nouvelle clé `paramsSerializer`. Remplacer les lignes 42–43 (et équivalents dans les autres tests qui matchent `toHaveBeenCalledWith`) par :

```ts
expect(mockGet).toHaveBeenCalledWith('/events/search', expect.objectContaining({ params }))
```

Ajouter le nouveau test à la fin du `describe('searchEvents', …)` :

```ts
  it('passes tags array through SearchParams', async () => {
    mockGet.mockResolvedValue({ data: [] })

    const params: SearchParams = { tags: ['quarkus', 'sport'] }
    await searchEvents(params)

    expect(mockGet).toHaveBeenCalledWith('/events/search', expect.objectContaining({
      params: { tags: ['quarkus', 'sport'] },
      paramsSerializer: expect.objectContaining({ indexes: null }),
    }))
  })
```

### 11.2 — `frontend/src/__tests__/components/event/EventSearchSidebar.test.tsx`

Ajouter à la fin du `describe('FilterSidebar', …)` :

```ts
  it('renders the Mots-clés section label', () => {
    renderSidebar()
    expect(screen.getByText('Mots-clés')).toBeTruthy()
  })

  it('renders existing tags as chips', () => {
    renderSidebar({ includePast: false, tags: ['quarkus', 'sport'] })
    expect(screen.getByText('quarkus')).toBeTruthy()
    expect(screen.getByText('sport')).toBeTruthy()
  })

  it('calls setFilters when a tag is added via Enter', () => {
    const setFilters = vi.fn()
    renderSidebar(defaultFilters, setFilters)
    const input = screen.getByPlaceholderText('Ex. quarkus, sport…') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'quarkus' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ tags: ['quarkus'] }))
  })

  it('calls setFilters with undefined when last tag is removed', () => {
    const setFilters = vi.fn()
    renderSidebar({ includePast: false, tags: ['quarkus'] }, setFilters)
    fireEvent.click(screen.getByLabelText('Remove tag quarkus'))
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ tags: undefined }))
  })
```

> Note : `TagInput` utilise un `inputRef.current.value = ''` — `fireEvent.change` initialise la valeur, `fireEvent.keyDown(Enter)` déclenche `addTag`. Si happy-dom pose problème, alternative : `fireEvent.keyDown(input, { key: 'Enter' })` après avoir simulé la frappe, puis vérifier directement via `screen.getByText('quarkus')`.

### 11.3 — `frontend/src/__tests__/hooks/useEventSearch.test.ts`

Ajouter à la fin du `describe('useSearch', …)` :

```ts
  it('initializes tags from URL multi-param', () => {
    function Wrapper({ children }: { children: ReactNode }) {
      return createElement(MemoryRouter, { initialEntries: ['/events/search?tags=quarkus&tags=sport'] }, children)
    }
    const { result } = renderHook(() => useSearch(), { wrapper: Wrapper })
    expect(result.current.filters.tags).toEqual(['quarkus', 'sport'])
  })

  it('omits tags from URL when filter is empty', async () => {
    const { result } = renderHook(() => useSearchAndParams(), { wrapper })

    act(() => {
      result.current.setFilters({ ...result.current.filters, tags: ['quarkus'] })
    })
    await act(async () => { await vi.runAllTimersAsync() })
    expect(result.current.searchParams.getAll('tags')).toEqual(['quarkus'])

    act(() => {
      result.current.setFilters({ ...result.current.filters, tags: undefined })
    })
    await act(async () => { await vi.runAllTimersAsync() })
    expect(result.current.searchParams.getAll('tags')).toEqual([])
  })

  it('forwards tags to searchEvents', async () => {
    mockSearchEvents.mockResolvedValue([])
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ ...result.current.filters, tags: ['quarkus', 'sport'] })
    })
    await act(async () => { await vi.runAllTimersAsync() })

    const lastCall = mockSearchEvents.mock.calls[mockSearchEvents.mock.calls.length - 1]
    expect(lastCall[0]).toMatchObject({ tags: ['quarkus', 'sport'] })
  })

  it('treats blank tag values from URL as no filter', () => {
    function Wrapper({ children }: { children: ReactNode }) {
      return createElement(MemoryRouter, { initialEntries: ['/events/search?tags=&tags=  '] }, children)
    }
    const { result } = renderHook(() => useSearch(), { wrapper: Wrapper })
    expect(result.current.filters.tags).toBeUndefined()
  })
```

### 11.4 — `frontend/src/__tests__/pages/event/EventsSearchPage.test.tsx`

**Pas d'ajout requis** — la page consomme `useSearch` via les props existantes. Le rendu du `<TagInput>` est testé au niveau du composant `EventSearchSidebar`. Vérifier en local que les tests existants continuent de passer (ils ne testent pas le contenu de la sidebar).

### 11.5 — Récapitulatif des nouveaux tests front

| Fichier | Cas | Vérifie |
|---|---|---|
| `searchApi.test.ts` | `passes tags array through SearchParams` | Tags passés à axios + `paramsSerializer.indexes: null` |
| `searchApi.test.ts` | (mise à jour des tests existants) | Compatibilité avec `paramsSerializer` ajouté au config |
| `EventSearchSidebar.test.tsx` | `renders the Mots-clés section label` | Section label rendue |
| `EventSearchSidebar.test.tsx` | `renders existing tags as chips` | Tags initiaux affichés |
| `EventSearchSidebar.test.tsx` | `calls setFilters when a tag is added via Enter` | Ajout via Enter |
| `EventSearchSidebar.test.tsx` | `calls setFilters with undefined when last tag is removed` | Suppression dernier tag → `tags: undefined` |
| `useEventSearch.test.ts` | `initializes tags from URL multi-param` | `?tags=a&tags=b` → `filters.tags = ['a','b']` |
| `useEventSearch.test.ts` | `omits tags from URL when filter is empty` | Sync inverse : URL vidée |
| `useEventSearch.test.ts` | `forwards tags to searchEvents` | Tags transmis à l'API |
| `useEventSearch.test.ts` | `treats blank tag values from URL as no filter` | `?tags=` filtré silencieusement |

---

## Étape 12 — Documentation

### 12.1 — `backend/docs/api-contract.md`

Mettre à jour la ligne 19 du tableau « Endpoints implémentés » (`GET /events/search`) :

```markdown
| `GET` | `/events/search` | `@PermitAll` | Recherche full-text (q, category, faculty, facultyNone, tags, dateFrom, dateTo, page, size) | 200 |
```

### 12.2 — `backend/docs/sprint-context.md`

Ajouter dans la section **Sprint 5** (ou créer une section **Sprint 6** si elle n'existe pas encore — le sprint-context backend est figé au sprint 5 actuellement) :

```markdown
- [x] **SCRUM-131** — Filtre `?tags=` (sémantique OR) sur `GET /api/events/search`. Clause JPQL `EXISTS` sur la collection `event_tags`, normalisation lowercase via `EventService.normalizeTags`. Multi-valeurs `?tags=a&tags=b`, blank/null filtrés silencieusement.
```

### 12.3 — `frontend/docs/components.md`

Mettre à jour la fiche `EventsSearchPage` (ligne 101–111) en ajoutant à la fin de la liste :

```markdown
- Filtre par mots-clés : section `<TagInput>` dans la sidebar (SCRUM-132), multi-tags, persistés dans l'URL via `?tags=foo&tags=bar`.
```

### 12.4 — `frontend/docs/types.md`

Mettre à jour la section `SearchParams` (ligne 127) :

```markdown
- tags : string[] — filtre par mots-clés (au moins un tag commun). Sérialisé en `?tags=a&tags=b` sans crochets via `paramsSerializer: { indexes: null }` côté Axios.
```

Mettre à jour la section `SearchFilters` (ligne 145–147) :

```markdown
Champs : `category?`, `faculty?`, `facultyNone?`, `tags?` (string[] — multi-tags OR), `dateFrom?`, `dateTo?`, `includePast`.
```

Mettre à jour la table de `Event` (ligne 65–84) — ajouter une ligne :

```markdown
| tags            | string[]      | non    |
```

### 12.5 — `frontend/docs/sprint-context.md`

Ajouter une nouvelle entrée en haut du fichier :

```markdown
## Sprint 6 — Filtre par tags sur la recherche (SCRUM-131 + SCRUM-132) — 2026-04-22

Terminé.

Fonctionnalités livrées :
- Backend : paramètre `?tags=` (multi-valeurs) sur `GET /api/events/search`. Sémantique OR via clause JPQL `EXISTS`. Normalisation lowercase via `EventService.normalizeTags` (réutilisée).
- Frontend : section « Mots-clés » dans `EventSearchSidebar` avec `TagInput` existant (SCRUM-128). Synchro état ↔ URL via `URLSearchParams.append('tags', t)`. Tags inclus dans `SearchParams` envoyés à l'API.
- Axios : `paramsSerializer: { indexes: null }` configuré dans `searchApi.ts` pour produire `?tags=a&tags=b` (sans crochets), format attendu par JAX-RS.
- Type `Event` enrichi du champ `tags?: string[]` (la search response l'expose désormais).
- Tests : 4 nouveaux tests REST + 6 DB-backed côté backend ; 4 sidebar + 4 hook + 1 service + mises à jour des tests existants côté frontend.
- Aucun nouveau skeleton — la sidebar n'a pas d'état loading.
```

### 12.6 — Pas de modification de `AGENTS.md`

Aucun nouveau composant, hook, page, route, skeleton ou service. Les conventions restent inchangées.

---

## Critères d'acceptation

- [ ] Saisir un tag dans la sidebar (Enter ou virgule) → la liste de résultats se met à jour immédiatement (bypass du debounce via `setFilters`)
- [ ] L'URL contient `?tags=foo&tags=bar` après ajout de deux tags
- [ ] Recharger la page sur l'URL `?tags=foo&tags=bar` restaure les chips dans le `TagInput` et les filtres dans `useSearch`
- [ ] Combinaison `?q=…&tags=…&category=…&faculty=…&dateFrom=…&dateTo=…` fonctionne sans interférence
- [ ] Recherche par tag est case-insensitive (`?tags=QUARKUS` matche `event.tags = ["quarkus"]`)
- [ ] Sémantique OR : `?tags=a&tags=b` retourne les events qui ont a OU b (vérifié back + front)
- [ ] Cliquer sur « Réinitialiser les filtres » vide aussi les tags
- [ ] Supprimer le dernier tag du `TagInput` retire le param `?tags=` de l'URL
- [ ] Aucune dégradation des filtres existants (q, category, faculty, facultyNone, dateFrom, dateTo, includePast)
- [ ] Pas de régression sur le skeleton `search-results` ni sur la pagination

---

## Edge cases à traiter explicitement

| Cas | Comportement attendu | Implémenté par |
|---|---|---|
| `?tags=` (vide) | Filtre ignoré silencieusement, retourne tous les events | `EventSearchService.normalizeTags` filtre les blank ; côté front `getAll().filter(t => t.trim().length > 0)` |
| Tag avec espaces autour (`"  quarkus  "`) | Trim et lowercase appliqués | `TagInput` trim à l'ajout + `normalizeTags` côté backend |
| Substring partiel (`?tags=foot` vs tag `"football"`) | Matche — le filtre est un substring match case-insensitive | `LOWER(t) LIKE :tagN ESCAPE '|'` côté JPQL + `String.contains` côté mock |
| Tag accentué (`"concèrt"`) | **Pas** d'accent-folding — `concert` ne matchera pas `concèrt`. Documenté dans api-contract et sprint-context | `LOWER(t)` JPQL ne strip pas les accents |
| Tag dupliqué dans la liste | Dédupliqué silencieusement | `TagInput` dédup à l'ajout + `normalizeTags` LinkedHashSet |
| Wildcard SQL saisi (`%`, `_`) | Traité **littéralement** — `?tags=%` ne matche pas tout, `?tags=_` ne matche pas les tags d'une seule lettre | `escapeLikePattern` échappe `|`, `%`, `_` + `ESCAPE '|'` dans le JPQL |
| 0 résultat | État `empty` existant rendu par `EventsSearchPage` (`Aucun résultat…`) | Inchangé |
| Beaucoup de tags (> 10) | Bloqué côté UI par `maxTags={10}` ; côté API, validation `maxItems: 20` documentée mais non enforced (soft limit) | `TagInput` + OpenAPI doc |
| Performance EXISTS sur EAGER | Acceptable — 1 sous-requête corrélée par requête principale, pas de N+1. `LIKE '%x%'` non indexable mais OK sur volumétrie actuelle. | JPQL `EXISTS` + `LIKE` |
| Tags avec caractères spéciaux SQL (`'`, `;`, `--`) | Pas d'injection : Hibernate utilise des paramètres préparés via `:tagN` | Bind parameter Hibernate |

---

## Résumé des fichiers à créer/modifier

| Fichier | Action |
|---|---|
| `/workspace/openapi/openapi.yaml` | **Modifier** — ajouter param `tags` dans `/events/search`, mettre à jour la description |
| `backend/src/main/java/ch/unige/events/resource/EventSearchResource.java` | **Modifier** — ajouter `@QueryParam("tags") List<String> tags` |
| `backend/src/main/java/ch/unige/events/service/EventSearchService.java` | **Modifier** — ajouter `List<String> tags` dans la signature + condition JPQL EXISTS |
| `backend/src/test/java/ch/unige/events/service/EventSearchServiceMock.java` | **Modifier** — aligner override sur la nouvelle signature, filtre OR in-memory |
| `backend/src/test/java/ch/unige/events/service/EventSearchServiceCoverageTest.java` | **Modifier** — mettre à jour les 16 call-sites (insérer `null`) + 6 nouveaux tests |
| `backend/src/test/java/ch/unige/events/resource/EventSearchResourceTest.java` | **Modifier** — 5 nouveaux tests REST |
| `frontend/src/types/search.ts` | **Modifier** — ajouter `tags?: string[]` dans `SearchFilters` et `SearchParams` |
| `frontend/src/types/event.ts` | **Modifier** — ajouter `tags?: string[]` dans `Event` |
| `frontend/src/services/searchApi.ts` | **Modifier** — `paramsSerializer: { indexes: null }` |
| `frontend/src/hooks/useEventSearch.ts` | **Modifier** — init `getAll('tags')`, sync via `URLSearchParams.append`, inclure dans `SearchParams` |
| `frontend/src/components/event/EventSearchSidebar.tsx` | **Modifier** — section « Mots-clés » avec `<TagInput>` |
| `frontend/src/__tests__/services/searchApi.test.ts` | **Modifier** — assertions `expect.objectContaining`, nouveau test tags |
| `frontend/src/__tests__/components/event/EventSearchSidebar.test.tsx` | **Modifier** — 4 nouveaux tests |
| `frontend/src/__tests__/hooks/useEventSearch.test.ts` | **Modifier** — 4 nouveaux tests |
| `backend/docs/api-contract.md` | **Modifier** — ligne `/events/search` |
| `backend/docs/sprint-context.md` | **Modifier** — entrée SCRUM-131 |
| `frontend/docs/components.md` | **Modifier** — fiche `EventsSearchPage` |
| `frontend/docs/types.md` | **Modifier** — `SearchParams`, `SearchFilters`, table `Event` |
| `frontend/docs/sprint-context.md` | **Modifier** — entrée Sprint 6 SCRUM-131+132 |

**Total :** 19 fichiers modifiés, 0 créé.

---

## Règles critiques à respecter

| Règle | Détail |
|---|---|
| `openapi.yaml` EN PREMIER | Étape 0 obligatoire avant tout code |
| Réutilisation | `EventService.normalizeTags` (back) et `TagInput` (front) — pas de duplication |
| camelCase / pas de snake_case | Param `tags`, champ JSON `tags` — jamais `tag_list` ou autre |
| Pas d'accent-folding | Comportement assumé et documenté (`concert ≠ concèrt`) |
| JPQL EXISTS corrélé | Pas de produit cartésien, pas de duplication de lignes |
| Sémantique OR | `IN :tags` dans la sous-requête — pas AND, pas COUNT |
| `paramsSerializer.indexes: null` | Sérialisation Axios sans crochets, format JAX-RS-compatible |
| `URLSearchParams.append` | Pour les multi-valeurs côté front — pas `Record<string, string>` |
| `getAll()` pas `get()` | À la lecture initiale depuis l'URL |
| Pas de skeleton créé | Justifié — sidebar sans loading state |
| Pas de `any` TS | Tous les types déclarés explicitement |
| Design tokens Tailwind | `border-border`, `text-foreground/40`, etc. — `TagInput` les utilise déjà |
| Une seule branche `feature/s6-search-tags` | Backend + frontend dans la même PR |
| Doc dans le même commit | api-contract, sprint-context, types, components |
| SonarCloud | ≥ 80 % coverage sur le nouveau code, ≤ 3 % duplication, Security/Reliability/Maintainability Rating A |

---

## Prompt de lancement d'implémentation

````
Tu vas implémenter la feature SCRUM-131 + SCRUM-132 (filtre par tags sur la recherche d'événements, back + front) sur **une seule branche `feature/s6-search-tags`** et **une seule PR finale**.

## Source unique de vérité
Le fichier `specs_archives/specs_claude/specs_scrum-131-132.md` est la source de vérité pour QUOI et POURQUOI. Lis-le entièrement avant de commencer et reviens-y à chaque étape.

## Lectures préliminaires obligatoires
Avant d'écrire du code, lis ces fichiers en entier :
- `backend/AGENTS.md` (conventions backend)
- `frontend/AGENTS.md` (conventions frontend, design tokens, pattern variants)
- `backend/docs/README.md`, `architecture.md`, `data-model.md`, `api-contract.md`, `dev-guide.md`, `sprint-context.md`
- `frontend/docs/README.md`, `architecture.md`, `components.md`, `types.md`, `dev-guide.md`, `sprint-context.md`
- `frontend/skeleton/README.md` (pour confirmer pourquoi aucun nouveau skeleton n'est requis)
- `openapi/openapi.yaml` section `/events/search` (~ligne 1235)
- Les fichiers actuels que tu vas modifier — TOUS, pas juste les diffs (cf. liste « Résumé des fichiers à créer/modifier » de la spec)

## Ordre d'implémentation strict

### Phase 1 — `openapi/openapi.yaml`
1. Ajouter le paramètre `tags` (array, query, items.string maxLength 64, style:form, explode:true) dans le path `/events/search` — diff exact dans la spec étape 0.
2. Mettre à jour la description du path.
3. Supprimer le commentaire TODO obsolète.

### Phase 2 — Backend (commits successifs ou un seul, peu importe — même PR)
4. `EventSearchResource.java` — ajouter `@QueryParam("tags") List<String> tags` à la 5e position (entre `facultyNone` et `dateFrom`).
5. `EventSearchService.java` — ajouter `List<String> tags` dans la signature de `search(...)` à la même position. Insérer la condition JPQL `EXISTS (SELECT 1 FROM Event e2 JOIN e2.tags t WHERE e2.id = e.id AND LOWER(t) IN :tags)` après le filtre faculty/facultyNone. Réutiliser `EventService.normalizeTags(tags)` pour la normalisation.
6. `EventSearchServiceMock.java` — aligner l'override sur la nouvelle signature, implémenter le filtre OR in-memory avec normalisation lowercase + trim + dédup + ignore null/blank.
7. `EventSearchResourceTest.java` — ajouter les 5 nouveaux tests (single, multi OR, case-insensitive, unknown, blank ignored).
8. `EventSearchServiceCoverageTest.java` — **mettre à jour les 16 call-sites existants** (insérer `null` à la 5e position) puis ajouter les 6 nouveaux tests DB-backed.
9. Lancer `./mvnw verify` depuis `backend/` — tout doit passer (couverture ≥ 80 % sur le nouveau code).

### Phase 3 — Frontend
10. `frontend/src/types/search.ts` — ajouter `tags?: string[]` dans `SearchFilters` et `SearchParams`.
11. `frontend/src/types/event.ts` — ajouter `tags?: string[]` dans le type `Event`.
12. `frontend/src/services/searchApi.ts` — déclarer `ARRAY_PARAMS_SERIALIZER = { indexes: null } as const` et le passer dans la config Axios de `searchEvents`.
13. `frontend/src/hooks/useEventSearch.ts` — wrapper l'init de `filters` en lazy `useState(() => ...)` et lire les tags via `searchParams.getAll('tags').filter(t => t.trim().length > 0)`. Refactor du `useEffect` de sync : passer du `Record<string, string>` plat à un `URLSearchParams` instance, utiliser `next.append('tags', t)` pour chaque tag. Inclure `tags: f.tags && f.tags.length > 0 ? f.tags : undefined` dans `SearchParams` envoyé à `searchEvents`.
14. `frontend/src/components/event/EventSearchSidebar.tsx` — importer `TagInput`, insérer la section « Mots-clés » avec `<SectionLabel>Mots-clés</SectionLabel>` + `<TagInput value={filters.tags ?? []} onChange={...} placeholder="Ex. quarkus, sport…" maxTags={10} />` entre la section Date et le séparateur précédant le checkbox `includePast`.
15. Tests :
    - `searchApi.test.ts` — passer les assertions à `expect.objectContaining({ params })` + ajouter le test « passes tags array through SearchParams ».
    - `EventSearchSidebar.test.tsx` — ajouter les 4 nouveaux tests (label, chips initiaux, ajout via Enter, suppression dernier tag → undefined).
    - `useEventSearch.test.ts` — ajouter les 4 nouveaux tests (init depuis URL multi-param, sync inverse, transmission API, blank tags filtrés).
16. Lancer `npm run lint` et `npm run test` depuis `frontend/` — tout doit passer (couverture ≥ 80 % sur les fichiers touchés).

### Phase 4 — Documentation (dans le même commit que le code correspondant)
17. `backend/docs/api-contract.md` — ligne `GET /events/search` mise à jour avec `tags` dans la liste des params.
18. `backend/docs/sprint-context.md` — ajouter l'entrée SCRUM-131 dans la section Sprint 5 (ou créer Sprint 6).
19. `frontend/docs/components.md` — fiche `EventsSearchPage` mise à jour.
20. `frontend/docs/types.md` — sections `SearchParams`, `SearchFilters`, table `Event`.
21. `frontend/docs/sprint-context.md` — ajouter l'entrée Sprint 6 SCRUM-131+132 en haut du fichier.

### Phase 5 — Vérification finale
22. `./mvnw verify` (backend) — vert
23. `npm run lint` + `npm run test` (frontend) — verts
24. Couverture V8 / JaCoCo sur les fichiers touchés ≥ 80 %
25. Test manuel dans le navigateur (`npm run dev` côté frontend + `./mvnw quarkus:dev` côté backend) :
    - Aller sur `/events/search`
    - Saisir un tag dans la sidebar « Mots-clés », appuyer Entrée → la liste se met à jour, l'URL contient `?tags=…`
    - Ajouter un deuxième tag → URL contient `?tags=a&tags=b`, sémantique OR vérifiée sur les résultats
    - Recharger la page → les chips reviennent dans le `TagInput`
    - Cliquer « Réinitialiser les filtres » → tags vidés, URL nettoyée
    - Combiner avec category, faculty, dates → tous les filtres se composent

## Interdits stricts
- ❌ Ne pas casser la signature existante de `EventSearchService.search()` au-delà du strict ajout du paramètre `tags`. Pas de refacto opportuniste.
- ❌ Ne pas modifier la logique de filtre `faculty` / `facultyNone` / dates (Zurich/UTC) ni le calcul `availableSpots` / `waitlistedCount`.
- ❌ Ne pas créer de nouveau composant frontend — réutiliser `TagInput` (`@/components/utils/TagInput`).
- ❌ Ne pas toucher au stub `fetchSuggestions`.
- ❌ Ne pas créer de nouveau skeleton.
- ❌ Ne pas modifier `frontend/src/services/api.ts` (le `paramsSerializer` est local à `searchApi.ts`).
- ❌ Ne pas implémenter la sémantique AND, ni un séparateur virgule dans l'URL.
- ❌ Ne pas ajouter `tags` au filtre de `GET /api/events` (paginated list) — uniquement `/events/search`.
- ❌ Ne jamais utiliser `any` TypeScript.
- ❌ Ne jamais importer en chemin relatif (`../`) côté front — toujours `@/`.

## Conventions à respecter
- camelCase partout (Java + TypeScript)
- Booléens sans préfixe `is`
- Design tokens Tailwind (`border-border`, `text-foreground/X`, `text-accent`, `bg-background`)
- Const map typée pour toute variante visuelle (pas de ternaire inline sur className)
- JPQL : parenthèses pour isoler les conditions composées (déjà en place pour `q`)
- `@Transactional` conservé sur la méthode service
- `@SuppressWarnings("java:S107")` conservé (back) — la justification reste valide après ajout
- Doc mise à jour **dans le même commit** que le code correspondant (règle d'or AGENTS.md)

## Critères de done
- [ ] `./mvnw verify` vert
- [ ] `npm run lint` vert (TypeScript strict, ESLint)
- [ ] `npm run test` vert avec couverture ≥ 80 % sur tous les fichiers touchés
- [ ] Vérification manuelle navigateur (Phase 5 point 25) — recherche par tag fonctionne, URL synchronisée dans les deux sens, reset OK, combinaisons OK
- [ ] PR ouverte sur la branche `feature/s6-search-tags`, titre clair (ex: « SCRUM-131 + SCRUM-132 — Filtre par tags sur la recherche »), description listant les deux tickets et les fichiers touchés
- [ ] SonarCloud sur la PR : Quality Gate vert (couverture ≥ 80 %, duplication ≤ 3 %, Security/Reliability/Maintainability Rating A)
````
