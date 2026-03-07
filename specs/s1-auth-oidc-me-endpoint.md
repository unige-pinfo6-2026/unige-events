# Spec : SCRUM-20 — Intégration OIDC + endpoint GET /api/me

## Contexte Jira

- **Tâche** : SCRUM-20 — [BACK][S1] Intégration OAuth0/OIDC (quarkus-oidc) + endpoint GET /api/me
- **Epic** : SCRUM-13 — Epic 1 – Authentification & Profils (US-01, US-14)
- **Sprint** : Sprint 1
- **Statut** : En cours

---

## 1. Contrat de l'endpoint

### `GET /api/me`

| Attribut        | Valeur                                                         |
|-----------------|----------------------------------------------------------------|
| Méthode         | GET                                                            |
| Path complet    | `/api/me`  (root-path `api` + `@Path("/me")`)                  |
| Authentification| Bearer JWT obligatoire (header `Authorization: Bearer <token>`) |
| Produces        | `application/json`                                             |

#### Réponse 200 OK

```json
{
  "sub": "<valeur du claim 'sub'>",
  "email": "<valeur du claim 'email'>",
  "name": "<valeur du claim 'name'>"
}
```

Toutes les valeurs sont de type `String`. Un claim absent dans le token doit être sérialisé `null` (pas d'erreur 500).

#### Codes d'erreur

| Code | Condition                                               |
|------|---------------------------------------------------------|
| 401  | Token absent, expiré, signature invalide, ou mal formé |
| 403  | Token valide mais scope/role insuffisant (non utilisé dans cette tâche) |

> La 401 est levée automatiquement par `quarkus-oidc` — aucun code manuel nécessaire.

---

## 2. Dépendances Maven à ajouter

Version Quarkus du projet : **3.32.1** (géré via BOM `io.quarkus.platform:quarkus-bom`).
Toutes les versions sont gérées par le BOM — ne pas spécifier de `<version>` explicite.

### Dépendances de production

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-oidc</artifactId>
</dependency>
```

### Dépendances de test

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-test-security</artifactId>
    <scope>test</scope>
</dependency>
```

> `quarkus-oidc` inclut déjà `org.eclipse.microprofile.jwt.JsonWebToken` — ne pas ajouter `quarkus-smallrye-jwt` séparément, cela provoquerait des conflits de configuration.

---

## 3. Propriétés application.properties

### Profil principal (src/main/resources/application.properties)

Ajouter à la fin du fichier existant, sous un commentaire `# OIDC` :

```properties
# OIDC
quarkus.oidc.auth-server-url=${OIDC_AUTH_SERVER_URL:https://placeholder.auth0.com}
quarkus.oidc.client-id=${OIDC_CLIENT_ID:placeholder-client-id}
quarkus.oidc.application-type=service
```

> **Important** : `application-type=service` (Bearer token) et NON `web-app`.
> `web-app` active un flow de redirection OIDC (Authorization Code Flow) incompatible avec une API REST.
> Le serveur réel sera fourni par Auth0 via les variables d'environnement `OIDC_AUTH_SERVER_URL` et `OIDC_CLIENT_ID`.

### Profil %test

```properties
%test.quarkus.oidc.enabled=false
%test.quarkus.security.auth.enabled-in-dev-mode=false
```

> Désactiver OIDC en test permet d'utiliser `@TestSecurity` sans connexion réseau vers un serveur OIDC réel. Sans ces propriétés, Quarkus tente de joindre `auth-server-url` au démarrage des tests et échoue.

---

## 4. Fichiers à créer / modifier

| Action   | Chemin                                                                          | Rôle                                                                 |
|----------|---------------------------------------------------------------------------------|----------------------------------------------------------------------|
| Modifier | `pom.xml`                                                                       | Ajouter `quarkus-oidc` (prod) et `quarkus-test-security` (test)      |
| Modifier | `src/main/resources/application.properties`                                    | Ajouter config OIDC (prod) et désactivation OIDC pour `%test`        |
| Créer    | `src/main/java/ch/unige/events/dto/MeResponse.java`                            | Record Java (ou POJO) représentant la réponse JSON de `/api/me`       |
| Créer    | `src/main/java/ch/unige/events/resource/MeResource.java`                       | Resource JAX-RS exposant `GET /me`, injecte `JsonWebToken`            |
| Créer    | `src/test/java/ch/unige/events/resource/MeResourceTest.java`                   | Tests @QuarkusTest : token valide (200) + sans token (401)            |

### Fichiers à NE PAS créer dans cette tâche (scope SCRUM-21)

- `src/main/java/ch/unige/events/entity/User.java`
- Toute migration Flyway (`src/main/resources/db/migration/`)
- `src/main/java/ch/unige/events/resource/UserResource.java`

---

## 5. Critères de validation

### Assertions de test exactes attendues

**Scénario 1 — Token valide**

```java
// Avec @TestSecurity(user = "alice", attributes = {"sub","alice-sub","email","alice@unige.ch","name","Alice"})
// ou via @OidcSecurity claims
given()
    .when().get("/me")
    .then()
    .statusCode(200)
    .contentType(ContentType.JSON)
    .body("sub", notNullValue())
    .body("email", notNullValue())
    .body("name", notNullValue());
```

**Scénario 2 — Sans token (401)**

```java
given()
    .when().get("/me")
    .then()
    .statusCode(401);
```

> Note : avec `quarkus-test-security` et `%test.quarkus.oidc.enabled=false`, l'absence de `@TestSecurity` retourne 401 automatiquement si l'endpoint est annoté `@Authenticated`.

### Commandes Maven à lancer

```bash
# Compilation seule
./mvnw compile

# Tests unitaires (profil test)
./mvnw test

# Test d'un fichier spécifique
./mvnw test -Dtest=MeResourceTest

# Vérification complète
./mvnw verify
```

---

## 6. Pièges connus à éviter

### Piège 1 : `application-type=web-app` au lieu de `service`
La tâche Jira mentionne `web-app` mais c'est incorrect pour une API REST.
`web-app` active un redirect OIDC — l'endpoint retournera 302 au lieu de 401.
**Utiliser `application-type=service`.**

### Piège 2 : OIDC actif en mode test sans serveur disponible
Sans `%test.quarkus.oidc.enabled=false`, Quarkus tente de découvrir le serveur OIDC
(`/.well-known/openid-configuration`) au démarrage des tests. Le test échoue avec
`ConnectException` même si la logique du code est correcte.

### Piège 3 : Mélanger `quarkus-oidc` et `quarkus-smallrye-jwt`
Ces deux extensions gèrent toutes les deux la validation JWT. Les activer ensemble
crée des conflits de configuration. `quarkus-oidc` seul suffit pour valider un Bearer token
et injecter `JsonWebToken`.

### Piège 4 : Mauvais path de l'endpoint
Le `quarkus.http.root-path=api` est déjà configuré dans `application.properties`.
Cela signifie que `@Path("/me")` donne `/api/me`. Ne pas mettre `@Path("/api/me")`
(ce serait `/api/api/me`).
En test avec RestAssured, utiliser `.get("/me")` — RestAssured respecte le `quarkus.http.root-path`.

### Piège 5 : `JsonWebToken` non injecté en test
Avec `@TestSecurity`, l'objet `JsonWebToken` injecté dans la resource aura uniquement
les attributs définis dans l'annotation. Les claims `email` et `name` ne sont pas remplis
automatiquement — utiliser `@OidcSecurity` ou tester seulement que les champs ne crashent
pas (valeur null acceptable dans les assertions).

### Piège 6 : `@RolesAllowed` vs `@Authenticated`
Ne pas utiliser `@RolesAllowed` (exige un rôle spécifique) — utiliser
`@io.quarkus.security.Authenticated` pour exiger simplement un token valide sans rôle.

### Piège 7 : Conflit futur avec SCRUM-21
SCRUM-21 va créer une entité `User` et peut vouloir appeler `/api/me` pour créer le profil.
Ce endpoint ne doit PAS contenir de logique de persistance (pas d'accès DB).
Garder `MeResource` purement stateless, limité à la lecture des claims JWT.

---

## 7. Ce qui NE doit PAS être modifié (hors scope)

| Fichier                                                              | Raison                                              |
|----------------------------------------------------------------------|-----------------------------------------------------|
| `src/main/java/ch/unige/events/resource/ExampleResource.java`        | Endpoint existant, ne pas toucher                   |
| `src/test/java/ch/unige/events/ExampleResourceTest.java`             | Test existant, ne pas modifier                      |
| `src/main/java/ch/unige/events/entity/Event.java`                   | Entité existante, hors scope SCRUM-20               |
| Tout fichier sous `src/main/resources/db/migration/`                | Flyway est dans le scope de SCRUM-21                |
| `src/main/java/ch/unige/events/entity/User.java` (pas encore créé)  | Sera créé par SCRUM-21                              |
| `Dockerfile`, `.github/`, `docker-compose*.yml`                     | Infrastructure, hors scope                          |
