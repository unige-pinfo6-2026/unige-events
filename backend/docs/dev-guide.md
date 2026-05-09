# Developer Guide — unige-events-api

## Prérequis

- **Java 21** (Temurin recommandé)
- **Maven** via le wrapper `./mvnw` inclus dans le repo — pas besoin d'installation globale
- **Docker** — requis pour les tests (DevServices lance un PostgreSQL éphémère via Testcontainers)
- Accès aux variables d'environnement Auth0 (voir `.env.example`)

---

## Layout Maven (multi-module — post-migration)

Depuis Sprint 8, `backend/` est un projet **multi-module** avec parent POM
agrégateur à la racine et **14 microservices Quarkus** sous
`backend/services/` (un par bounded context). Le legacy-monolith a été
supprimé à step 15. Voir [`backend/AGENTS.md`](../AGENTS.md) section
« Layout Maven » et [`architecture.md`](architecture.md) pour la table
des endpoints owned par service.

**Conséquences pratiques pour le dev local** :
- `cd backend && ./mvnw verify` build TOUS les microservices (~1 min 10 s).
- `quarkus:dev` ne tourne PAS depuis le parent — il s'exécute par
  service. Exemple :
  `cd backend/services/event-service && ../../mvnw quarkus:dev`.
- Pour faire tourner plusieurs services en même temps il faut leur
  attribuer des ports HTTP distincts (par défaut tous écoutent
  `:8080`) via `-Dquarkus.http.port=8082` etc.

---

## Lancement en développement local

```bash
# Copier les variables d'environnement
cp .env.example .env
# Éditer .env avec les vraies valeurs Auth0 (OIDC_AUTH_SERVER_URL, OIDC_CLIENT_ID, etc.)

# Lancer un service en mode dev (hot reload + PostgreSQL auto via DevServices)
cd backend/services/event-service
../../mvnw quarkus:dev
```

L'API du service est accessible sur `http://localhost:8080/api`.
Swagger UI : `http://localhost:8080/api/swagger-ui`
OpenAPI JSON : `http://localhost:8080/api/openapi`

Pour reproduire la topologie complète (Kong + 13 services + db + minio
+ kafka), passer par le chart Helm via Minikube ou un cluster preview ;
il n'y a pas de `docker-compose.dev.yml` qui orchestre les 13 pods en
local — le coût démarrage est élevé et le dev se fait service par
service avec DevServices.

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
   → Hibernate applique le changement automatiquement au démarrage.

5. **Écrire les tests** (`src/test/java/`)
   Annoter la classe avec `@QuarkusTest`.
   Utiliser `@TestSecurity(user = "test-user", roles = {})` pour simuler l'authentification.
   Cibler >80% de couverture (seuil SonarCloud).

6. **Mettre à jour la doc** :
   - `docs/api-contract.md` si la signature change
   - `docs/data-model.md` si une entité change

---

## Workflow : modifier le schéma de base de données

Le schéma est géré exclusivement par Hibernate en mode `update`. Pour modifier le schéma :

1. Modifier l'entité JPA concernée dans `src/main/java/**/entity/`
2. Hibernate applique les changements automatiquement au démarrage
3. Mettre à jour `docs/data-model.md` pour refléter le nouveau schéma

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
