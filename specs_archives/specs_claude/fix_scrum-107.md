# Spécification technique — Fix SCRUM-107 : Upload de bannière lors de la création d'événements

## 1. Résumé du bug

Lors de la création d'un événement avec une bannière, l'événement est bien persisté en base (HTTP 201), mais l'upload de l'image échoue avec un HTTP 403 car `POST /events/{id}/image` exigeait le rôle `ORGANIZER` ou `ADMIN`, que les utilisateurs normaux ne possèdent pas. Le frontend, ne distinguant pas l'échec de la création de l'échec de l'upload, affichait "La création de l'événement a échoué" et laissait le formulaire actif — poussant l'utilisateur à re-soumettre et créer des doublons sans bannière.

Un second problème structurel s'est révélé lors du test : le `bannerUrl` stocké en base (`/uploads/...`) était un chemin mort en dev comme en production, car avec `quarkus.http.root-path=api` les fichiers statiques sont servis sous `/api/uploads/...`, seul chemin proxifié par Nginx et Vite..

---

## 2. Changements backend

### 2.1 `EventResource.java` — Correction de l'annotation de sécurité

**Fichier :** `backend/src/main/java/ch/unige/events/resource/EventResource.java`

Remplacer `@RolesAllowed({"ORGANIZER", "ADMIN"})` par `@Authenticated` sur `POST /events/{id}/image`.

```java
// AVANT
@POST
@Path("/{id}/image")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@RolesAllowed({"ORGANIZER", "ADMIN"})
public Response uploadImage(@PathParam("id") Long id, @RestForm("file") FileUpload file) {

// APRÈS
@POST
@Path("/{id}/image")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Authenticated
public Response uploadImage(@PathParam("id") Long id, @RestForm("file") FileUpload file) {
```

**Justification :**
- `@RolesAllowed` vérifie les rôles du token JWT avant d'exécuter la méthode. L'intention était *"seul le créateur peut uploader"*, ce qui est une vérification de propriété de ressource, pas de rôle.
- `EventService.uploadImage()` effectue déjà la vérification via `isCreator()` : un utilisateur non-créateur reçoit un 403 venant du service. La vérification de rôle en couche resource était redondante et incorrecte.
- `POST /events` (création) utilise `@Authenticated` : cohérence sémantique.
- `PATCH /events/{id}/publish` conserve `@RolesAllowed({"ORGANIZER", "ADMIN"})` — publier un événement est une action réellement réservée à un rôle, c'est l'usage correct de cette annotation.

### 2.2 `EventService.java` — Correction du chemin `bannerUrl`

**Fichier :** `backend/src/main/java/ch/unige/events/service/EventService.java`

```java
// AVANT
event.bannerUrl = "/uploads/" + uniqueFileName;

// APRÈS
event.bannerUrl = "/api/uploads/" + uniqueFileName;
```

**Justification :**
- Avec `quarkus.http.root-path=api`, le Router Vert.x enregistre les routes sous `/api/`. La route `router.route("/uploads/*")` de `UploadsRouteConfig` est donc accessible à `http://backend:8080/api/uploads/...`.
- Nginx (`location /api`) et le proxy Vite (`'/api'`) proxifient déjà `/api` vers le backend. Aucune configuration supplémentaire n'est nécessaire.
- Stocker `/api/uploads/...` en base est le seul chemin qui fonctionne en dev comme en production.

### 2.3 Mise à jour de l'OpenAPI spec

**Fichier :** `openapi/openapi.yaml`

La spec est déjà correcte (`BearerAuth: []` uniquement). Aucune modification nécessaire.

---

## 3. Changements frontend

### 3.1 `useEventForm.ts` — Séparation des erreurs de création et d'upload

**Fichier :** `frontend/src/hooks/useEventForm.ts`

#### 3.1.1 Nouveau callback dans `UseEventFormOptions`

```ts
// AVANT
interface UseEventFormOptions {
  mode: 'create' | 'edit'
  initialEvent?: Event | null
  onSuccess?: (event: Event) => void
  onError?: (message: string) => void
}

// APRÈS
interface UseEventFormOptions {
  mode: 'create' | 'edit'
  initialEvent?: Event | null
  onSuccess?: (event: Event) => void
  onError?: (message: string) => void
  onBannerError?: (message: string) => void
}
```

#### 3.1.2 Refactoring de `submitForm()`

Remplacer le bloc `try/catch` monolithique par deux blocs indépendants :

```ts
// AVANT
async function submitForm() {
  if (!validate()) return
  setSubmitting(true)
  try {
    // ... payload build ...
    let savedEvent: Event
    if (mode === 'create') {
      savedEvent = await createEvent(payload)
    } else {
      // ... updateEvent ...
    }
    if (imageFile) {
      savedEvent = await uploadEventImage(savedEvent.id, imageFile)
    }
    onSuccess?.(savedEvent)
  } catch (error) {
    onError?.(getApiErrorMessage(error, mode))  // attrape TOUT, y compris les erreurs d'upload
  } finally {
    setSubmitting(false)
  }
}

// APRÈS
async function submitForm() {
  if (!validate()) return
  setSubmitting(true)

  let savedEvent: Event

  // Étape 1 : création / mise à jour de l'événement (bloquante)
  try {
    const payload: CreateEventRequest = { /* ... identique à avant ... */ }

    if (mode === 'create') {
      savedEvent = await createEvent(payload)
    } else {
      if (!initialEvent) throw new Error('Missing event for edit mode')
      const updatePayload: UpdateEventRequest = { /* ... identique à avant ... */ }
      savedEvent = await updateEvent(initialEvent.id, updatePayload)
    }
  } catch (error) {
    onError?.(getApiErrorMessage(error, mode))
    setSubmitting(false)
    return  // l'event n'a pas été créé, on s'arrête
  }

  // Étape 2 : upload de la bannière (non bloquant pour la navigation)
  if (imageFile) {
    try {
      savedEvent = await uploadEventImage(savedEvent.id, imageFile)
    } catch {
      // L'événement existe — on redirige quand même, avec un avertissement
      onBannerError?.("L'événement a été créé mais la bannière n'a pas pu être uploadée.")
    }
  }

  setSubmitting(false)
  onSuccess?.(savedEvent)
}
```

**Règle clé :** `onSuccess` est toujours appelé dès lors que l'événement existe (création ou mise à jour réussie), qu'il y ait eu un problème de bannière ou non.

### 3.2 `CreateEventPage.tsx` — Gestion du `onBannerError`

**Fichier :** `frontend/src/pages/CreateEventPage.tsx`

Passer un handler `onBannerError` au hook. Ce handler stocke le message dans `sessionStorage` sous la clé `bannerUploadError` avant que `onSuccess` déclenche la redirection.

```ts
const form = useEventForm({
  mode: 'create',
  onSuccess: (event) => {
    showToast('success', 'Événement créé avec succès.')
    redirectTimerRef.current = setTimeout(() => navigate(`/events/${event.id}`), 1000)
  },
  onError: (message) => showToast('error', message),
  onBannerError: (message) => sessionStorage.setItem('bannerUploadError', message),
})
```

### 3.3 `EditEventPage.tsx` — Même correction

**Fichier :** `frontend/src/pages/EditEventPage.tsx`

Même logique `onBannerError` pour la cohérence.

### 3.4 `EventDetailPage.tsx` — Affichage de l'avertissement bannière

**Fichier :** `frontend/src/pages/EventDetailPage.tsx`

Au montage, lire `sessionStorage['bannerUploadError']`. Si présent : supprimer la clé immédiatement, stocker le message dans un état local, afficher un toast d'avertissement pendant 6 secondes.

```ts
const [bannerWarning, setBannerWarning] = useState<string | null>(null)

useEffect(() => {
  const warning = sessionStorage.getItem('bannerUploadError')
  if (warning) {
    sessionStorage.removeItem('bannerUploadError')
    setBannerWarning(warning)
    const t = setTimeout(() => setBannerWarning(null), 6000)
    return () => clearTimeout(t)
  }
}, [])
```

```tsx
{bannerWarning && (
  <output className="event-toast event-toast--warning">{bannerWarning}</output>
)}
```

### 3.5 `EventPage.css` — Variante warning du toast

**Fichier :** `frontend/src/pages/EventPage.css`

```css
.event-toast--warning { background: #fefce8; color: #854d0e; border: 1px solid #fef08a; }
```

---

## 4. Tests

### 4.1 Backend — `EventResourceTest.java`

Trois nouveaux cas ajoutés sur `POST /events/{id}/image` :

#### Test 1 : créateur authentifié sans rôle spécial → 200

```java
@Test
@TestSecurity(user = "auth0|alice")
void uploadImage_asAuthenticatedCreator_returns200() {
    var event = eventServiceMock.seedEvent("auth0|alice", "Event avec bannière");

    given()
            .contentType("multipart/form-data")
            .multiPart("file", "banner-no-role.png", "fake-png-bytes".getBytes(), "image/png")
            .when().post("/events/" + event.id + "/image")
            .then()
            .statusCode(200)
            .body("bannerUrl", notNullValue());
}
```

#### Test 2 : non-authentifié → 401

```java
@Test
void uploadImage_unauthenticated_returns401() {
    var event = eventServiceMock.seedEvent("auth0|alice", "Event");

    given()
            .contentType("multipart/form-data")
            .multiPart("file", "banner.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
            .when().post("/events/" + event.id + "/image")
            .then()
            .statusCode(401);
}
```

#### Test 3 : authentifié mais pas le créateur → 403 (venant du service)

```java
@Test
@TestSecurity(user = "auth0|alice")
void uploadImage_asAuthenticatedNonCreator_returns403() {
    EventServiceMock.forceForbiddenOnUpdate = true;
    var event = eventServiceMock.seedEvent("auth0|bob", "Event de Bob");

    given()
            .contentType("multipart/form-data")
            .multiPart("file", "banner.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
            .when().post("/events/" + event.id + "/image")
            .then()
            .statusCode(403)
            .body("error", equalTo("forbidden"));
}
```

> **Tests existants conservés :** `uploadImageWithValidJpegReturns200` et `uploadImageWithInvalidMimeReturns400` (avec rôle `ORGANIZER`) restent valides — un ORGANIZER doit continuer à pouvoir uploader.

### 4.2 Frontend — `useEventForm.test.tsx`

Deux nouveaux cas ajoutés :

#### Test 4 : `createEvent` réussit, `uploadEventImage` échoue → `onSuccess` + `onBannerError` appelés, `onError` non appelé

```ts
it('calls onSuccess and onBannerError when create succeeds but image upload fails', async () => {
  const createdEvent = { ...baseEvent, bannerUrl: undefined }
  mockCreateEvent.mockResolvedValue(createdEvent)
  mockUploadEventImage.mockRejectedValue(new Error('Network error'))
  globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview')
  globalThis.URL.revokeObjectURL = vi.fn()

  const onSuccess = vi.fn()
  const onError = vi.fn()
  const onBannerError = vi.fn()

  const { result } = renderHook(() =>
    useEventForm({ mode: 'create', onSuccess, onError, onBannerError })
  )

  act(() => {
    result.current.setFieldValue('title', 'Forum')
    result.current.setFieldValue('location', 'Uni Dufour')
    result.current.setFieldValue('startDate', '2099-04-10T10:00')
    result.current.setFieldValue('endDate', '2099-04-10T12:00')
    result.current.setFieldValue('category', EventCategory.SOCIAL)
    result.current.handleImageChange({
      target: { files: [new File(['img'], 'banner.png', { type: 'image/png' })] }
    } as never)
  })

  await act(async () => {
    await result.current.handleSubmit(submitEvent())
  })

  expect(mockCreateEvent).toHaveBeenCalledOnce()
  expect(mockUploadEventImage).toHaveBeenCalledOnce()
  expect(onSuccess).toHaveBeenCalledWith(createdEvent)
  expect(onBannerError).toHaveBeenCalledWith(
    "L'événement a été créé mais la bannière n'a pas pu être uploadée."
  )
  expect(onError).not.toHaveBeenCalled()
  expect(result.current.submitting).toBe(false)
})
```

#### Test 5 : `createEvent` échoue → seul `onError` appelé, `uploadEventImage` jamais tenté

```ts
it('calls only onError when createEvent fails, never attempts image upload', async () => {
  mockCreateEvent.mockRejectedValue({
    isAxiosError: true,
    response: { data: { message: 'Erreur serveur' } },
  })
  globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview')
  globalThis.URL.revokeObjectURL = vi.fn()

  const onSuccess = vi.fn()
  const onError = vi.fn()
  const onBannerError = vi.fn()

  const { result } = renderHook(() =>
    useEventForm({ mode: 'create', onSuccess, onError, onBannerError })
  )

  act(() => {
    result.current.setFieldValue('title', 'Forum')
    result.current.setFieldValue('location', 'Uni Dufour')
    result.current.setFieldValue('startDate', '2099-04-10T10:00')
    result.current.setFieldValue('endDate', '2099-04-10T12:00')
    result.current.setFieldValue('category', EventCategory.SOCIAL)
    result.current.handleImageChange({
      target: { files: [new File(['img'], 'banner.png', { type: 'image/png' })] }
    } as never)
  })

  await act(async () => {
    await result.current.handleSubmit(submitEvent())
  })

  expect(mockCreateEvent).toHaveBeenCalledOnce()
  expect(mockUploadEventImage).not.toHaveBeenCalled()
  expect(onSuccess).not.toHaveBeenCalled()
  expect(onBannerError).not.toHaveBeenCalled()
  expect(onError).toHaveBeenCalledOnce()
  expect(result.current.submitting).toBe(false)
})
```

---

## 5. Critères d'acceptance

| # | Scénario | Résultat attendu |
|---|---|---|
| AC-1 | Utilisateur authentifié (sans rôle) crée un événement **avec** bannière | Événement créé, bannière uploadée, redirection vers la page de l'événement avec bannière visible |
| AC-2 | Utilisateur authentifié crée un événement **sans** bannière | Comportement inchangé — événement créé, redirection normale |
| AC-3 | L'upload de la bannière échoue après une création réussie (réseau, taille, MIME) | L'événement est créé, l'utilisateur est redirigé vers l'événement, un toast d'avertissement indique que la bannière n'a pas pu être uploadée |
| AC-4 | La création de l'événement elle-même échoue | Message d'erreur affiché dans le formulaire, formulaire reste actif, aucun événement créé |
| AC-5 | Utilisateur non-authentifié appelle `POST /events/{id}/image` directement | 401 Unauthorized |
| AC-6 | Utilisateur authentifié appelle `POST /events/{id}/image` sur un événement dont il n'est **pas** le créateur | 403 Forbidden (venant du service, pas de la couche resource) |
| AC-7 | `ORGANIZER` ou `ADMIN` appelle `POST /events/{id}/image` | 200 OK (comportement inchangé) |
