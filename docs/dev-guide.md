# Developer Guide — unige-events-api

## Prérequis

- **Java 21** (Temurin recommandé)
- **Maven** via le wrapper `./mvnw` inclus dans le repo — pas besoin d'installation globale
- **Docker** — requis pour les tests (DevServices lance un PostgreSQL éphémère via Testcontainers)
- Accès aux variables d'environnement Auth0 (voir `.env.example`)

---

## Lancement en développement local

```bash
# Copier les variables d'environnement
cp .env.example .env
# Éditer .env avec les vraies valeurs Auth0 (OIDC_AUTH_SERVER_URL, OIDC_CLIENT_ID, etc.)

# Lancer l'API en mode dev (hot reload + PostgreSQL auto via DevServices)
./mvnw quarkus:dev
```

L'API est accessible sur `http://localhost:8080/api`.
Swagger UI : `http://localhost:8080/api/swagger-ui`
OpenAPI JSON : `http://localhost:8080/api/openapi`

**DevServices :** Quarkus lance automatiquement un PostgreSQL éphémère via Testcontainers en mode dev. Aucune DB externe n'est requise si `quarkus.datasource.jdbc.url` n'est pas défini pour le profil dev.

**Hibernate update :** Le schéma est géré automatiquement par Hibernate en mode `update` en dev. Toute modification d'entité est répercutée en DB sans migration manuelle.

---

## Commandes courantes

```bash
./mvnw quarkus:dev          # Dev local avec hot reload + DevServices PostgreSQL auto
./mvnw verify               # Build + tests complets (CI — nécessite Docker)
./mvnw test                 # Tests uniquement (nécessite Docker pour DevServices)
./mvnw quarkus:dev -Ddebug  # Debug sur le port 5005
```

> **Important :** `./mvnw test` nécessite **Docker-in-Docker** (ou Docker actif localement) car Quarkus DevServices lance un PostgreSQL éphémère via Testcontainers. En CI, Docker-in-Docker est configuré dans le pipeline.

---

## Workflow : ajouter un endpoint (spec-first)

L'ordre est impératif — **spec d'abord, code ensuite**.

1. **Spécifier dans `docs/openapi/openapi.yaml`**
   Définir le path, les paramètres, les schémas de requête et de réponse en camelCase.
   Vérifier que les booléens n'ont pas de préfixe `is`.

2. **Coder la Resource** (`src/main/java/.../resource/`)
   Annoter avec `@Path`, `@GET/@POST/@PUT`, `@Authenticated` ou `@PermitAll`.
   Déléguer au Service via `@Inject` (constructor injection).

3. **Coder le Service** (`src/main/java/.../service/`)
   Annoter avec `@ApplicationScoped`. Ajouter `@Transactional` sur chaque méthode de mutation.
   Contenir toute la logique métier — jamais dans la Resource.

4. **Mettre à jour / créer l'Entity** si nécessaire (`src/main/java/.../entity/`)
   Ajouter les champs en camelCase. Annoter avec les contraintes JPA.
   → Écrire une migration Flyway correspondante (voir section suivante).

5. **Écrire les tests** (`src/test/java/`)
   Annoter la classe avec `@QuarkusTest`.
   Utiliser `@TestSecurity(user = "test-user", roles = {})` pour simuler l'authentification.
   Cibler >80% de couverture (seuil SonarCloud).

6. **Mettre à jour la doc** :
   - `docs/api-contract.md` si la signature change
   - `docs/data-model.md` si une entité change

---

## Workflow : ajouter une migration Flyway

> Actuellement Flyway est désactivé en dev (mode Hibernate `update`). Écrire les migrations quand même pour la prod.

1. Créer `src/main/resources/db/migration/V{N}__{description}.sql`
   Exemple : `V3__add_attendance_table.sql`

2. Écrire le SQL standard PostgreSQL (pas de dialecte Hibernate).

3. **Ne jamais modifier** un fichier Flyway existant — toujours créer un nouveau fichier.

4. Mettre à jour `docs/data-model.md` (section migrations).

```sql
-- Exemple : V3__add_attendance_table.sql
CREATE TABLE attendance (
    id         BIGSERIAL PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users(id),
    event_id   BIGINT      NOT NULL REFERENCES events(id),
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_attendance_user_event UNIQUE (user_id, event_id)
);
```

---

## Conventions de nommage — rappel critique

### camelCase partout

```java
// CORRECT
String displayName;
LocalDateTime startDate;
boolean profilePublic;

// INTERDIT
String display_name;      // snake_case → jamais dans le code Java
LocalDateTime start_date;
```

### Booléens sans préfixe `is`

```java
// CORRECT
boolean profilePublic;   // → JSON: "profilePublic": true
boolean active;          // → JSON: "active": true
boolean featured;        // → JSON: "featured": true

// INTERDIT — Lombok génère isIsActive() → conflit
boolean isProfilePublic; // ← JAMAIS
boolean isActive;        // ← JAMAIS
```

### Injection par constructeur

```java
// CORRECT
private final UserService userService;

@Inject
public UserResource(UserService userService) {
    this.userService = userService;
}

// DÉCONSEILLÉ (viole les règles SonarCloud du projet)
@Inject
UserService userService;  // injection par champ
```

---

## Variables d'environnement

| Variable | Défaut (dev) | Description |
|---|---|---|
| `APP_PORT` | `8080` | Port HTTP de l'API |
| `DB_HOST` | `db` | Hôte PostgreSQL |
| `DB_PORT` | `5432` | Port PostgreSQL |
| `DB_NAME` | `unige_events` | Nom de la base |
| `DB_USER` | `postgres` | Utilisateur DB |
| `DB_PASSWORD` | `postgres` | Mot de passe DB |
| `OIDC_AUTH_SERVER_URL` | — | URL Auth0 (ex: `https://dev-xxx.us.auth0.com/`) |
| `OIDC_CLIENT_ID` | — | Client ID Auth0 |
| `OIDC_CLIENT_SECRET` | — | Client Secret Auth0 |
| `OIDC_AUDIENCE` | `https://unige-events/api` | Audience JWT |

En mode `%test`, OIDC est désactivé automatiquement (`quarkus.oidc.enabled=false`) — les variables Auth0 ne sont pas nécessaires pour les tests.

---

## Activer les hooks Git localement

```bash
git config core.hooksPath .github/hooks
```

Les hooks vérifient :
- Qu'aucun champ booléen n'est préfixé `is` dans les entités
- Que la documentation est mise à jour quand du code critique change
