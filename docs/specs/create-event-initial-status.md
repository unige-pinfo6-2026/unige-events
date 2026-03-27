# Spec — Permettre la création d'un événement directement en PUBLISHED

**Origine :** Review PR frontend — le devops signale qu'il est impossible de créer un événement directement en `PUBLISHED`. Il faut passer par DRAFT puis faire un PUT séparé. Ce n'est pas un comportement voulu.

---

## Contexte technique

### Cause du problème

1. `CreateEventRequest` n'a pas de champ `status` (ni dans la classe, ni dans `EventRequestBase`).
2. `EventService.create()` ne valorise jamais `event.status` → l'entité garde son défaut JPA : `EventStatus.DRAFT` (défini ligne 35 de `Event.java`).
3. Le schéma `CreateEventRequest` dans `docs/openapi/openapi.yaml` ne documente pas de champ `status` → le contrat API ne permet pas de le passer.

### Fichiers concernés

| Fichier | Rôle |
|---|---|
| `docs/openapi/openapi.yaml` (schéma `CreateEventRequest`, ~l.258) | Contrat API — source de vérité, à modifier EN PREMIER |
| `src/main/java/ch/unige/events/dto/event/CreateEventRequest.java` | DTO de création — classe vide qui étend `EventRequestBase` |
| `src/main/java/ch/unige/events/dto/event/EventRequestBase.java` | Champs communs aux DTOs — ne pas y ajouter `status` |
| `src/main/java/ch/unige/events/service/EventService.java` (méthode `create()`) | Logique de création — c'est ici que le statut doit être affecté |
| `src/test/java/ch/unige/events/service/EventServiceCoverageTest.java` | Tests de service — un test manque pour ce cas |
| `docs/sprint-context.md` | À mettre à jour après implémentation |

---

## Changements attendus

### 1. `docs/openapi/openapi.yaml` — EN PREMIER

Ajouter le champ `status` dans le schéma `CreateEventRequest` :

```yaml
status:
  allOf:
    - $ref: '#/components/schemas/EventStatus'
  nullable: true
  description: Statut initial de l'événement. Par défaut DRAFT si absent.
```

### 2. `CreateEventRequest.java`

Ajouter le champ directement dans cette classe (pas dans `EventRequestBase` — `UpdateEventRequest` a déjà son propre `status` séparé, c'est voulu) :

```java
public EventStatus status;
```

### 3. `EventService.java` — méthode `create()`

Après `event.creator = creator;`, remplacer le comportement implicite par :

```java
event.status = request.status != null ? request.status : EventStatus.DRAFT;
```

### 4. `EventServiceCoverageTest.java`

Ajouter un test couvrant la création avec statut explicite :

- **`create_withPublishedStatus_persistsPublished()`** : passe `status = PUBLISHED` dans le `CreateEventRequest`, vérifie que le DTO retourné a `status() == EventStatus.PUBLISHED`.
- **`create_withoutStatus_defaultsToDraft()`** : passe `status = null`, vérifie `status() == EventStatus.DRAFT`.

### 5. `docs/sprint-context.md`

Ajouter dans la section Sprint 2 — "À faire dans ce sprint" :

```
- [x] `POST /events` : création directement en `PUBLISHED` (champ `status` optionnel dans `CreateEventRequest`, défaut `DRAFT`)
```

---

## Contraintes (AGENTS.md)

- `status` va dans `CreateEventRequest`, **pas** dans `EventRequestBase`
- Aucune logique dans la Resource (`EventResource` ne change pas)
- Coverage Sonar ≥ 80% — les deux nouveaux tests sont obligatoires
- Pas de fichier SQL de migration (schéma géré par Hibernate `update`)
- camelCase partout, pas de préfixe `is`
