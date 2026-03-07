# Prompt d'implémentation : SCRUM-20 — OIDC + GET /api/me

> Ce fichier est le prompt à envoyer à un agent Claude pour implémenter SCRUM-20.
> Il référence @specs/s1-auth-oidc-me-endpoint.md comme source de vérité unique.

---

## Prompt

Tu vas implémenter la tâche SCRUM-20 sur ce repo Quarkus.
La spec complète est dans @specs/s1-auth-oidc-me-endpoint.md — lis-la intégralement avant toute action.

### Ordre précis des opérations

Respecte cet ordre strictement. Après chaque fichier créé ou modifié, compile et vérifie.

---

**Étape 0 — Lecture obligatoire avant toute écriture**

Lis ces fichiers dans cet ordre exact :
1. `specs/s1-auth-oidc-me-endpoint.md` (la spec)
2. `pom.xml` (dépendances actuelles, version Quarkus)
3. `src/main/resources/application.properties` (config actuelle)
4. `src/main/java/ch/unige/events/resource/ExampleResource.java` (pattern resource existant)
5. `src/test/java/ch/unige/events/ExampleResourceTest.java` (pattern test existant)

Ne commence à écrire aucun fichier avant d'avoir lu ces 5 fichiers.

---

**Étape 1 — Modifier pom.xml**

Ajoute exactement les deux dépendances décrites dans la section 2 de la spec.
- Pas de `<version>` explicite (géré par le BOM)
- Ne supprime aucune dépendance existante
- Ne modifie pas les plugins

Après modification, lance :
```
./mvnw compile -q
```
Si la compilation échoue, lis l'erreur complète et corrige `pom.xml` avant de continuer.
Ne passe pas à l'étape 2 tant que la compilation n'est pas verte.

---

**Étape 2 — Modifier application.properties**

Ajoute les propriétés OIDC décrites dans la section 3 de la spec (profil prod + profil %test).
- Ajoute-les à la fin du fichier existant
- Ne modifie pas les lignes existantes

Après modification, lance :
```
./mvnw compile -q
```
Si la compilation échoue, corrige avant de continuer.

---

**Étape 3 — Créer MeResponse.java**

Crée `src/main/java/ch/unige/events/dto/MeResponse.java`.
- Package : `ch.unige.events.dto`
- Représente la réponse JSON avec les champs `sub`, `email`, `name` (tous String)
- Utilise un Java record ou un POJO simple — pas de logique métier
- Respecte le contrat JSON de la section 1 de la spec

Après création, lance :
```
./mvnw compile -q
```
Corrige toute erreur de compilation avant de continuer.

---

**Étape 4 — Créer MeResource.java**

Crée `src/main/java/ch/unige/events/resource/MeResource.java`.
- Package : `ch.unige.events.resource`
- Respecte le contrat exact de la section 1 de la spec (path, auth, produces, codes d'erreur)
- Injecte `org.eclipse.microprofile.jwt.JsonWebToken`
- Utilise `@io.quarkus.security.Authenticated` (pas `@RolesAllowed`)
- Lit les claims `sub`, `email`, `name` depuis le token
- Retourne un `MeResponse`
- Pas d'accès DB, pas de logique métier

Relis la section 6 (Pièges) avant d'écrire — particulièrement le piège 4 sur le path.

Après création, lance :
```
./mvnw compile -q
```
Corrige toute erreur de compilation avant de continuer.

---

**Étape 5 — Créer MeResourceTest.java**

Crée `src/test/java/ch/unige/events/resource/MeResourceTest.java`.
- Package : `ch.unige.events.resource`
- Annotation `@QuarkusTest`
- Implémente les deux scénarios de la section 5 de la spec :
  - Scénario 1 : token valide (200 + body JSON correct) avec `@TestSecurity`
  - Scénario 2 : sans token (401)
- N'utilise PAS de serveur OIDC réel
- Importe `io.restassured.http.ContentType` et les matchers Hamcrest

Après création, lance :
```
./mvnw test -Dtest=MeResourceTest -q
```

---

**Étape 6 — Correction automatique des erreurs**

Si un test échoue :
1. Lis le message d'erreur complet dans la sortie Maven
2. Identifie si c'est une erreur de compilation, de configuration, ou d'assertion
3. Relis la section correspondante de la spec (section 3 pour config, section 6 pour pièges)
4. Applique la correction minimale nécessaire
5. Relance `./mvnw test -Dtest=MeResourceTest -q`
6. Ne modifie pas `ExampleResource.java` ni `ExampleResourceTest.java`

---

**Étape 7 — Validation finale**

Lance la suite complète :
```
./mvnw test -q
```

Tous les tests (existants + nouveaux) doivent être verts.
Si `ExampleResourceTest` régresse, c'est que tu as modifié un fichier hors scope — relis la section 7 de la spec.

Rapporte le résultat final : nombre de tests passés, nombre d'échecs.

---

### Contraintes absolues

- Ne crée PAS `User.java`, ni aucune migration Flyway (scope SCRUM-21)
- Ne modifie PAS `ExampleResource.java` ni `ExampleResourceTest.java`
- Ne touche PAS aux fichiers `Dockerfile`, `.github/`, `docker-compose*.yml`
- Toute dépendance Maven ajoutée doit être dans le BOM Quarkus 3.32.1 — pas de version explicite
- Le path de l'endpoint est `/me` (pas `/api/me`) dans l'annotation `@Path`
