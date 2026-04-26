# Specs SCRUM-123 — Intégration `ImageCropper` sur avatar profil + bannière profil + bannière événement

> **Branche :** `feature/s6-image-crop-integration` (deja créée depuis `main` — la feature est totalement orthogonale aux changements `feature/s6-search-tags` ; aucun fichier code en commun, utilise la branche deja créer)
> **Sprint :** S6 — Feature 4 (US-25 « recadrer mon avatar et mes bannières directement dans l'interface »)
> **Tickets couverts :** SCRUM-123 (FRONT, 2 SP, Daniel)
> **Prérequis :** SCRUM-122 ✅ mergé (`ImageCropper.tsx` + `imageCropperUtils.ts` + `react-image-crop@11.0.10`). Endpoints back `POST /users/me/image`, `POST /users/me/banner`, `DELETE /users/me/banner`, `POST /events/{id}/image` déjà en place. Aucune modification backend ni openapi.
> **Règle d'or :** Extraire la logique commune dans `useImageCropFlow` AVANT d'intégrer dans les 3 cibles. Coder hook → tests hook → ProfileEditPage → tests ProfileEditPage → useEventForm → EventForm → tests Event* → doc dans le **même commit** que le code.

---

## Contexte

### Problème

Aujourd'hui, l'utilisateur uploade trois types d'images sur la plateforme — l'avatar de profil, la bannière de profil et la bannière d'événement — sans aucune possibilité de recadrer côté frontend. Le fichier original est envoyé tel quel au backend, qui le stocke à l'identique. Conséquences UX :

- Avatars rectangulaires affichés dans des cercles avec un crop CSS qui peut couper la tête
- Bannières aux ratios variés affichées dans des box `h-32` ou `aspect-video` qui les déforment ou les rognent involontairement
- Impossible de cadrer précisément le sujet d'une photo

US-25 demande explicitement de pouvoir recadrer ces trois cibles directement dans l'interface, sans outil externe.

### Solution

Le composant `ImageCropper` (modal full-screen avec `react-image-crop`, SCRUM-122 mergé) est prêt à être branché. Il faut l'intégrer dans **les trois flux d'upload existants** sans toucher à la couche service ni au backend.

Les trois cibles partagent **exactement le même flux** : sélection fichier → validation MIME/taille → `FileReader.readAsDataURL` → modale crop → confirm = `Blob` → conversion en `File` → push dans le state existant (`photoFile` / `bannerFile` / `imageFile`) → preview via `URL.createObjectURL(blob)` → submit déclenche l'upload existant `uploadPhoto/uploadBanner/uploadEventImage(file)`.

Plutôt que de dupliquer la logique 3 ×, on **extrait un hook utilitaire** `useImageCropFlow` qui encapsule FileReader, validation, gestion du nom original, reset de l'input. Trois consommateurs : `ProfileEditPage` (× 2 instances : avatar + bannière) + `useEventForm` (× 1 instance : bannière event). Cohérent avec la règle DRY de `frontend/AGENTS.md`.

### Décisions techniques tranchées

| # | Décision | Justification |
|---|---|---|
| 1 | **Convertir le `Blob` du cropper en `File`** via `new File([blob], originalName, { type: blob.type \|\| 'image/png' })` | Les services `uploadPhoto(file: File)`, `uploadBanner(file: File)`, `uploadEventImage(id, file: File)` ont une signature stricte. Convertir évite de toucher la couche service. Préserver le `name` original aide pour le multipart côté backend (filename de `Content-Disposition`). |
| 2 | **`FileReader.readAsDataURL` pour la source du cropper** (pas `URL.createObjectURL`) | `react-image-crop` lit `<img src>` ; la dataURL est immédiatement disponible et ne nécessite pas de gestion `revokeObjectURL`. La source du cropper vit le temps de la modale (~secondes), pas un objet long-lived. |
| 3 | **`URL.createObjectURL(blob)` pour le preview** (pas dataURL base64) | Le preview reste affiché plusieurs minutes (le temps que l'utilisateur termine le formulaire). Un objectURL est ~30 % plus léger en mémoire qu'un dataURL inliné en base64. Pattern déjà en place via `objectUrlRef` dans `useEventForm`. |
| 4 | **Validation MIME + taille AVANT ouverture du cropper** | Le cropper ne doit jamais être atteint pour un fichier invalide. Messages d'erreur existants conservés (`"Le fichier doit être une image."`, `"La photo ne doit pas dépasser 2 Mo."`, etc.). |
| 5 | **Reset de `event.target.value = ''`** dans le handler de file select | Sans reset, re-sélectionner le **même fichier** après cancel ne redéclenche pas l'event `change` (comportement HTML standard) → l'utilisateur bloqué. |
| 6 | **Hook `useImageCropFlow` extrait dans `frontend/src/hooks/useImageCropFlow.ts`** | 3 consommateurs avec logique strictement identique → règle DRY de `frontend/AGENTS.md` (« 3 occurrences ou plus → extraire »). Hook compact (~60 lignes), pas de sur-ingénierie. |
| 7 | **Suppression de la bannière profil ne passe PAS par le cropper** | `handleBannerDelete` (lignes 92–97 de `ProfileEditPage.tsx`) garde son comportement actuel (`setBannerDeleted(true)`). Le crop ne s'applique qu'à un nouvel upload. |
| 8 | **Aspects en const nommées** : `AVATAR_ASPECT = 1`, `PROFILE_BANNER_ASPECT = 3`, `EVENT_BANNER_ASPECT = 16 / 9` | Convention `frontend/AGENTS.md` — pas de magic numbers, pas de calcul `16/9` répété inline. |
| 9 | **Rendu de `<ImageCropper>` directement dans `ProfileEditPage` et `EventForm`** (pas dans `EventCreatePage` / `EventEditPage`) | Garde le cropper colocalisé avec l'input file qu'il accompagne. `EventCreatePage` et `EventEditPage` n'ont rien à modifier (zéro couplage supplémentaire). |
| 10 | **Pas de modification de `ImageCropper.tsx`, `imageCropperUtils.ts`, `userService.ts`, `eventApi.ts`** | Hors scope SCRUM-123. SCRUM-122 a déjà testé `ImageCropper` à 100 %. Les services ont une signature `(file: File)` qui reste valide après conversion Blob → File. |

### Ce qui existe déjà (ne pas retoucher sauf indication contraire)

| Fichier | État |
|---|---|
| `frontend/src/components/utils/ImageCropper.tsx` (lignes 1–69) | Modal full-screen complète. Props : `src: string`, `aspect: number`, `circular?: boolean`, `onCropComplete: (blob: Blob) => void`, `onCancel: () => void`. Bouton « Recadrer » désactivé tant que `completedCrop` est null. **Tests à 100 % — ne pas modifier.** |
| `frontend/src/components/utils/imageCropperUtils.ts` (lignes 1–46) | Helpers `initCrop(width, height, aspect)` et `cropToBlob(image, pixelCrop, circular)` (retourne `Promise<Blob>` en `image/jpeg` qualité 0.9). **Ne pas modifier.** |
| `frontend/src/services/userService.ts` (lignes 21–35) | `uploadPhoto(file: File)`, `uploadBanner(file: File)`, `deleteBanner()` — multipart sur `/users/me/image`, `/users/me/banner`. **Signatures conservées telles quelles.** |
| `frontend/src/services/eventApi.ts` (lignes 47–51) | `uploadEventImage(id: number, file: File)` — multipart sur `/events/{id}/image`. **Signature conservée.** |
| `openapi/openapi.yaml` | Endpoints existants. **Aucune modification.** |
| `frontend/src/bones/profile.bones.json`, `event-edit.bones.json` | Skeletons des pages cibles déjà en place. **Aucun nouveau bones requis** (cf. section Skeleton). |

### Ce qui est à créer

| Fichier | Action |
|---|---|
| `frontend/src/hooks/useImageCropFlow.ts` | **Nouveau** — hook utilitaire encapsulant FileReader, validation, conservation du nom original, reset de l'input, conversion Blob → File. |
| `frontend/src/__tests__/hooks/useImageCropFlow.test.ts` | **Nouveau** — couverture du hook (sélection valide, rejets de validation, confirm, cancel, reset input). |

### Ce qui est à modifier

| Fichier | Modification |
|---|---|
| `frontend/src/pages/profile/ProfileEditPage.tsx` | Remplacer `handlePhotoChange` et `handleBannerChange` (lignes 59–90) par 2 instances de `useImageCropFlow`. Ajouter un state `cropSource: { src: string, kind: 'avatar' \| 'banner' } \| null`. Rendre `<ImageCropper>` au-dessus du formulaire selon `cropSource.kind`. |
| `frontend/src/hooks/useEventForm.ts` | Remplacer `handleImageChange` (lignes 390–417) par une intégration de `useImageCropFlow`. Exposer `cropSource` (string \| null), `confirmCrop(blob)`, `cancelCrop()` dans `UseEventFormResult`. |
| `frontend/src/components/event/EventForm.tsx` | Étendre `EventFormProps` avec 3 props (`cropSource`, `onCropConfirm`, `onCropCancel`). Rendre `<ImageCropper>` au-dessus du formulaire si `cropSource !== null`. |
| `frontend/src/__tests__/pages/profile/ProfileEditPage.test.tsx` | Mettre à jour les tests qui faisaient `fireEvent.change` sur l'input file (le flow passe désormais par le cropper) + nouveaux tests cropper. |
| `frontend/src/__tests__/hooks/useEventForm.test.tsx` | Mettre à jour les tests `handleImageChange` (renommé en intégration `useImageCropFlow`) + nouveaux tests confirm/cancel. |
| `frontend/src/__tests__/components/event/EventForm.test.tsx` | Ajouter tests rendu conditionnel de `<ImageCropper>` selon prop `cropSource`. |
| `frontend/src/__tests__/pages/event/EventCreatePage.test.tsx`, `frontend/src/__tests__/pages/event/EventEditPage.test.tsx` | **Adapter les tests existants si la signature de `EventForm` change** — vérifier que tous les `<EventForm ...>` instanciés dans les tests passent les nouvelles props (peuvent être `null` / no-op dans la plupart des cas). |
| `frontend/docs/components.md` | Compléter la fiche `ImageCropper` avec la liste des 3 intégrations + ajouter une fiche `useImageCropFlow`. |
| `frontend/docs/sprint-context.md` | Ajouter une nouvelle entrée Sprint 6 SCRUM-123 en haut du fichier. |

### Ce qui n'est PAS dans le scope

- ❌ Pas de modification de `ImageCropper.tsx` ni `imageCropperUtils.ts` (SCRUM-122 mergé, tests à 100 %).
- ❌ Pas de modification des services (`userService.ts`, `eventApi.ts`) — la conversion Blob → File préserve la signature `(file: File)`.
- ❌ Pas de modification de `openapi/openapi.yaml`, ni du backend, ni des entités.
- ❌ Pas de modification de `EventCreatePage.tsx` ou `EventEditPage.tsx` au-delà de l'éventuelle adaptation des props passées à `<EventForm>` (qui seront récupérées du hook, donc transparent).
- ❌ Pas de nouveau skeleton (cf. section Skeleton).
- ❌ Pas de modification du flux suppression de bannière profil — reste à l'identique.
- ❌ Pas de gestion du crop pendant la suppression / le re-crop d'une image existante (out of scope ; le crop ne s'applique qu'à un nouvel upload).

---

## Étape 1 — Créer `useImageCropFlow.ts`

**Fichier :** `frontend/src/hooks/useImageCropFlow.ts` (nouveau)

```ts
import { useCallback, useRef, useState, type ChangeEvent } from 'react'

export interface UseImageCropFlowOptions {
  aspect: number
  circular?: boolean
  /** Retourne un message d'erreur (string) si le fichier est invalide, sinon `null`. */
  validate?: (file: File) => string | null
  /** Callback déclenché quand la validation échoue. Reçoit le message renvoyé par `validate`. */
  onValidationError?: (message: string) => void
}

export interface UseImageCropFlowResult {
  /** Source data URL passée à `<ImageCropper>` quand un fichier valide a été sélectionné. `null` = cropper fermé. */
  cropSource: string | null
  /** À brancher sur `onChange` de l'`<input type="file">`. Lit le fichier, valide, et ouvre le cropper si OK. */
  handleFileSelect: (event: ChangeEvent<HTMLInputElement>) => void
  /** Aspect ratio à passer au `<ImageCropper>`. */
  aspect: number
  /** Mode circulaire (avatar) à passer au `<ImageCropper>`. */
  circular: boolean
  /** À brancher sur `onCropComplete` du `<ImageCropper>`. Convertit le Blob en File (préserve le nom original) et ferme le cropper. Retourne le File ou `null` si plus de fichier en attente. */
  confirmCrop: (blob: Blob) => File | null
  /** À brancher sur `onCancel` du `<ImageCropper>`. Ferme le cropper sans toucher à l'état parent. */
  cancelCrop: () => void
}

export function useImageCropFlow({
  aspect,
  circular = false,
  validate,
  onValidationError,
}: UseImageCropFlowOptions): UseImageCropFlowResult {
  const [cropSource, setCropSource] = useState<string | null>(null)
  // Mémorise le nom du fichier sélectionné pour le réinjecter dans le File issu du Blob.
  const pendingFileNameRef = useRef<string | null>(null)
  // Mémorise l'input file pour pouvoir reset .value après confirm/cancel — sans reset,
  // re-sélectionner le même fichier ne redéclenche pas `change` (comportement HTML standard).
  const inputRef = useRef<HTMLInputElement | null>(null)

  const handleFileSelect = useCallback((event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return

    if (validate) {
      const error = validate(file)
      if (error !== null) {
        onValidationError?.(error)
        // Reset l'input — sinon re-sélectionner le même fichier après correction
        // de l'erreur côté UI ne redéclencherait rien.
        event.target.value = ''
        return
      }
    }

    pendingFileNameRef.current = file.name
    inputRef.current = event.target

    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result
      if (typeof result === 'string') setCropSource(result)
    }
    reader.readAsDataURL(file)
  }, [validate, onValidationError])

  const confirmCrop = useCallback((blob: Blob): File | null => {
    const name = pendingFileNameRef.current
    if (!name) {
      cleanup()
      return null
    }
    const file = new File([blob], name, { type: blob.type || 'image/png' })
    cleanup()
    return file
  }, [])

  const cancelCrop = useCallback(() => {
    cleanup()
  }, [])

  function cleanup() {
    setCropSource(null)
    pendingFileNameRef.current = null
    if (inputRef.current) inputRef.current.value = ''
    inputRef.current = null
  }

  return { cropSource, handleFileSelect, aspect, circular, confirmCrop, cancelCrop }
}
```

**Points à respecter :**
- Pas de `any` — toutes les signatures typées explicitement (`ChangeEvent<HTMLInputElement>`, `File`, `Blob`).
- `useCallback` sur les handlers exposés pour éviter des re-renders inutiles côté consommateurs.
- `cleanup()` est interne (pas exposée) — appelée par `confirmCrop` et `cancelCrop` pour garantir un reset cohérent.
- L'input est resetté **dans tous les cas** (succès, cancel, validation échouée). C'est ce qui permet la re-sélection du même fichier.
- `validate` retourne `string | null` (pas `boolean`) pour permettre au consommateur de propager le message d'erreur via `onValidationError`. Pattern cohérent avec les conventions internes (cf. `Record<string,string|undefined>` dans `useEventForm.errors`).

---

## Étape 2 — Tests `useImageCropFlow.test.ts`

**Fichier :** `frontend/src/__tests__/hooks/useImageCropFlow.test.ts` (nouveau)

```ts
import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useImageCropFlow } from '@/hooks/useImageCropFlow'

let originalFileReader: typeof FileReader

beforeEach(() => {
  originalFileReader = globalThis.FileReader
})

afterEach(() => {
  globalThis.FileReader = originalFileReader
  vi.resetAllMocks()
})

function mockFileReader(result: string) {
  class MockReader {
    public onload: (() => void) | null = null
    public result: string | null = null
    readAsDataURL() {
      this.result = result
      // Simule une lecture asynchrone synchrone-friendly pour les tests
      queueMicrotask(() => this.onload?.())
    }
  }
  globalThis.FileReader = MockReader as unknown as typeof FileReader
}

function fileSelectEvent(file: File): React.ChangeEvent<HTMLInputElement> {
  const input = document.createElement('input')
  Object.defineProperty(input, 'files', { value: [file], configurable: true })
  return { target: input, currentTarget: input } as unknown as React.ChangeEvent<HTMLInputElement>
}

describe('useImageCropFlow', () => {
  it('initialise avec cropSource null', () => {
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    expect(result.current.cropSource).toBeNull()
    expect(result.current.aspect).toBe(1)
    expect(result.current.circular).toBe(false)
  })

  it('expose le mode circulaire quand fourni', () => {
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1, circular: true }))
    expect(result.current.circular).toBe(true)
  })

  it('ouvre le cropper avec la dataURL après sélection d\'un fichier valide', async () => {
    mockFileReader('data:image/png;base64,abc')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    const file = new File(['x'], 'photo.png', { type: 'image/png' })

    await act(async () => {
      result.current.handleFileSelect(fileSelectEvent(file))
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    expect(result.current.cropSource).toBe('data:image/png;base64,abc')
  })

  it('rejette le fichier si validate retourne un message + appelle onValidationError', () => {
    const onValidationError = vi.fn()
    const validate = vi.fn(() => 'Trop gros.')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1, validate, onValidationError }))
    const file = new File(['x'], 'big.png', { type: 'image/png' })

    act(() => {
      result.current.handleFileSelect(fileSelectEvent(file))
    })

    expect(validate).toHaveBeenCalledWith(file)
    expect(onValidationError).toHaveBeenCalledWith('Trop gros.')
    expect(result.current.cropSource).toBeNull()
  })

  it('ne fait rien si aucun fichier n\'est sélectionné', () => {
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    const event = { target: { files: [] } } as unknown as React.ChangeEvent<HTMLInputElement>

    act(() => {
      result.current.handleFileSelect(event)
    })

    expect(result.current.cropSource).toBeNull()
  })

  it('confirmCrop retourne un File avec le nom original et le type du blob', async () => {
    mockFileReader('data:image/png;base64,abc')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    const file = new File(['x'], 'photo-original.jpg', { type: 'image/jpeg' })

    await act(async () => {
      result.current.handleFileSelect(fileSelectEvent(file))
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    let resultFile: File | null = null
    act(() => {
      const blob = new Blob(['cropped'], { type: 'image/jpeg' })
      resultFile = result.current.confirmCrop(blob)
    })

    expect(resultFile).toBeInstanceOf(File)
    expect(resultFile?.name).toBe('photo-original.jpg')
    expect(resultFile?.type).toBe('image/jpeg')
    expect(result.current.cropSource).toBeNull()  // cropper fermé après confirm
  })

  it('confirmCrop retourne null si appelé sans fichier en attente', () => {
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    let resultFile: File | null = null
    act(() => {
      resultFile = result.current.confirmCrop(new Blob([]))
    })
    expect(resultFile).toBeNull()
  })

  it('confirmCrop fallback sur image/png si le blob.type est vide', async () => {
    mockFileReader('data:image/png;base64,abc')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    const file = new File(['x'], 'photo.png', { type: 'image/png' })

    await act(async () => {
      result.current.handleFileSelect(fileSelectEvent(file))
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    let resultFile: File | null = null
    act(() => {
      const blob = new Blob(['x'], { type: '' })
      resultFile = result.current.confirmCrop(blob)
    })

    expect(resultFile?.type).toBe('image/png')
  })

  it('cancelCrop ferme le cropper et reset l\'input', async () => {
    mockFileReader('data:image/png;base64,abc')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    const file = new File(['x'], 'photo.png', { type: 'image/png' })
    const event = fileSelectEvent(file)

    await act(async () => {
      result.current.handleFileSelect(event)
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    act(() => {
      result.current.cancelCrop()
    })

    expect(result.current.cropSource).toBeNull()
    expect((event.target as HTMLInputElement).value).toBe('')
  })

  it('reset l\'input après échec de validation pour permettre la re-sélection', () => {
    const validate = vi.fn(() => 'Invalide.')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1, validate }))
    const file = new File(['x'], 'doc.pdf', { type: 'application/pdf' })
    const event = fileSelectEvent(file)

    act(() => {
      result.current.handleFileSelect(event)
    })

    expect((event.target as HTMLInputElement).value).toBe('')
  })
})
```

**Récap :** 9 tests couvrant les 4 branches conditionnelles (validate présent/absent, file présent/absent, confirm avec/sans pendingName, blob.type vide). Cible **100 %** de couverture sur le hook (~60 lignes).

---

## Étape 3 — Intégration dans `ProfileEditPage.tsx`

**Fichier :** `frontend/src/pages/profile/ProfileEditPage.tsx`

### 3.1 — Imports

Remplacer les imports existants (lignes 1–12) par :

```tsx
import { type KeyboardEvent as ReactKeyboardEvent, useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { deleteBanner, getMe, updateProfile, uploadBanner, uploadPhoto } from '@/services/userService'
import { FACULTIES } from '@/types/faculty'
import { STUDY_LEVELS, type User } from '@/types/user'
import FormField, { Input, Select, Textarea } from '@/components/utils/FormField'
import { ButtonPrimary, ButtonSecondary } from '@/components/utils/Buttons'
import { ImagePlus, Trash2, X } from 'lucide-react'
import UserAvatar from '@/components/user/UserAvatar'
import UserBanner from '@/components/user/UserBanner'
import { useToast } from '@/hooks/useToast'
import ImageCropper from '@/components/utils/ImageCropper'
import { useImageCropFlow } from '@/hooks/useImageCropFlow'
```

(`ChangeEvent` retiré — les handlers de file passent désormais par le hook ; `useCallback` ajouté pour les `onCropComplete`.)

### 3.2 — Constantes d'aspect

Ajouter en haut du fichier, après les constantes existantes (après ligne 16) :

```tsx
const AVATAR_ASPECT = 1
const PROFILE_BANNER_ASPECT = 3
```

### 3.3 — Remplacer `handlePhotoChange` et `handleBannerChange` par 2 instances de `useImageCropFlow`

**Supprimer** les fonctions `handlePhotoChange` (lignes 59–73) et `handleBannerChange` (lignes 75–90).

**Insérer** à leur place (juste après le bloc `useEffect` de hydratation, ligne 57) :

```tsx
  function validatePhoto(file: File): string | null {
    if (!file.type.startsWith('image/')) return 'Le fichier doit être une image.'
    if (file.size > MAX_PHOTO_SIZE) return 'La photo ne doit pas dépasser 2 Mo.'
    return null
  }

  function validateBanner(file: File): string | null {
    if (!file.type.startsWith('image/')) return 'Le fichier doit être une image.'
    if (file.size > MAX_BANNER_SIZE) return 'La bannière ne doit pas dépasser 5 Mo.'
    return null
  }

  const photoCrop = useImageCropFlow({
    aspect: AVATAR_ASPECT,
    circular: true,
    validate: validatePhoto,
    onValidationError: (message) => setErrors((prev) => ({ ...prev, photo: message })),
  })

  const bannerCrop = useImageCropFlow({
    aspect: PROFILE_BANNER_ASPECT,
    circular: false,
    validate: validateBanner,
    onValidationError: (message) => setErrors((prev) => ({ ...prev, banner: message })),
  })

  const handlePhotoCropComplete = useCallback((blob: Blob) => {
    const file = photoCrop.confirmCrop(blob)
    if (!file) return
    setErrors((prev) => ({ ...prev, photo: undefined }))
    setPhotoFile(file)
    setPhotoPreview(URL.createObjectURL(blob))
  }, [photoCrop])

  const handleBannerCropComplete = useCallback((blob: Blob) => {
    const file = bannerCrop.confirmCrop(blob)
    if (!file) return
    setErrors((prev) => ({ ...prev, banner: undefined }))
    setBannerFile(file)
    setBannerPreview(URL.createObjectURL(blob))
    setBannerDeleted(false)
  }, [bannerCrop])
```

### 3.4 — Brancher les `<input>` sur le hook

**Remplacer** ligne 169 (l'input bannière) :
```tsx
              <input id="banner-input" type="file" accept="image/*" onChange={handleBannerChange} className="hidden" />
```
par :
```tsx
              <input id="banner-input" type="file" accept="image/*" onChange={bannerCrop.handleFileSelect} className="hidden" />
```

**Remplacer** ligne 196 (l'input photo) :
```tsx
              <input id="photo-input" type="file" accept="image/*" onChange={handlePhotoChange} className="hidden" />
```
par :
```tsx
              <input id="photo-input" type="file" accept="image/*" onChange={photoCrop.handleFileSelect} className="hidden" />
```

### 3.5 — Rendre `<ImageCropper>` conditionnellement

**Insérer** **juste avant** la fermeture `</div>` racine de la page (avant ligne 311 `</div>`) :

```tsx
        {photoCrop.cropSource && (
          <ImageCropper
            src={photoCrop.cropSource}
            aspect={photoCrop.aspect}
            circular={photoCrop.circular}
            onCropComplete={handlePhotoCropComplete}
            onCancel={photoCrop.cancelCrop}
          />
        )}
        {bannerCrop.cropSource && (
          <ImageCropper
            src={bannerCrop.cropSource}
            aspect={bannerCrop.aspect}
            circular={bannerCrop.circular}
            onCropComplete={handleBannerCropComplete}
            onCancel={bannerCrop.cancelCrop}
          />
        )}
```

**Points à respecter :**
- Les 2 `<ImageCropper>` sont mutuellement exclusifs en pratique (l'utilisateur ne peut pas avoir 2 file pickers ouverts simultanément), mais le rendu conditionnel sur `cropSource !== null` les sépare proprement.
- Le `useCallback` sur `handlePhotoCropComplete` / `handleBannerCropComplete` évite que `<ImageCropper>` se re-render inutilement.
- `URL.createObjectURL(blob)` directement (pas `URL.createObjectURL(file)`) — fonctionne aussi sur `Blob`, et c'est l'objet qu'on reçoit. Léger.
- **Note memory leak** : les `URL.createObjectURL` ici ne sont pas explicitement révoqués. C'est cohérent avec le code actuel (ligne 72, 88) qui ne révoquait pas non plus. Le navigateur les libère au navigate / unmount via GC. Hors scope SCRUM-123 — un fix global nécessiterait un `useEffect` cleanup, à traiter dans une tâche dédiée si on voulait plus de rigueur.

---

## Étape 4 — Intégration dans `useEventForm.ts`

**Fichier :** `frontend/src/hooks/useEventForm.ts`

### 4.1 — Imports

Ajouter en haut du fichier (après les imports existants, après ligne 13) :

```ts
import { useImageCropFlow } from '@/hooks/useImageCropFlow'
```

### 4.2 — Constante d'aspect

Ajouter après les constantes existantes (après ligne 118 `IMAGE_MAX_SIZE_BYTES`) :

```ts
export const EVENT_BANNER_ASPECT = 16 / 9
```

### 4.3 — Étendre `UseEventFormResult`

Remplacer le bloc `interface UseEventFormResult` (lignes 47–60) par :

```ts
interface UseEventFormResult {
  values: EventFormValues
  errors: EventFormErrors
  submitting: boolean
  draftSaving: boolean
  imagePreview: string | null
  selectedImageName: string | null
  cropSource: string | null
  cropAspect: number
  setFieldValue: <K extends keyof EventFormValues>(field: K, value: EventFormValues[K]) => void
  handleImageChange: (event: ChangeEvent<HTMLInputElement>) => void
  confirmCrop: (blob: Blob) => void
  cancelCrop: () => void
  handleSubmit: (event: FormEvent<HTMLFormElement>) => Promise<void>
  triggerDraftSave: () => Promise<void>
  triggerPublish: () => Promise<void>
  clearPersistedDraft: () => void
}
```

**Notes :**
- `handleImageChange` est conservé dans la signature publique mais sa **délégation interne change** : il appelle `imageCrop.handleFileSelect`. Cela évite de toucher à `EventForm` côté `onImageChange` prop si on ne veut pas le renommer (transparent pour les tests existants).
- `confirmCrop(blob: Blob): void` ne retourne rien depuis le hook — il pousse directement le File résultant dans le state interne `imageFile`.

### 4.4 — Remplacer `handleImageChange` par l'intégration `useImageCropFlow`

**Supprimer** la fonction `handleImageChange` (lignes 390–417).

**Insérer** à sa place :

```ts
  function validateImage(file: File): string | null {
    if (!file.type.startsWith('image/')) return 'Le fichier doit être une image.'
    if (file.size > IMAGE_MAX_SIZE_BYTES) {
      return `Le fichier dépasse la taille maximale autorisée (${IMAGE_MAX_SIZE_MB} Mo).`
    }
    return null
  }

  const imageCrop = useImageCropFlow({
    aspect: EVENT_BANNER_ASPECT,
    circular: false,
    validate: validateImage,
    onValidationError: (message) => setErrors((current) => ({ ...current, image: message })),
  })

  function handleImageChange(event: ChangeEvent<HTMLInputElement>) {
    imageCrop.handleFileSelect(event)
  }

  function confirmCrop(blob: Blob) {
    const file = imageCrop.confirmCrop(blob)
    if (!file) return

    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
    }
    const previewUrl = URL.createObjectURL(blob)
    objectUrlRef.current = previewUrl

    setImageFile(file)
    setSelectedImageName(file.name)
    setImagePreview(previewUrl)
    setErrors((current) => ({ ...current, image: undefined }))
  }

  function cancelCrop() {
    imageCrop.cancelCrop()
  }
```

### 4.5 — Étendre le `return` du hook

Remplacer le bloc `return { ... }` (lignes 578–591) par :

```ts
  return {
    values,
    errors,
    submitting,
    draftSaving,
    imagePreview,
    selectedImageName,
    cropSource: imageCrop.cropSource,
    cropAspect: imageCrop.aspect,
    setFieldValue,
    handleImageChange,
    confirmCrop,
    cancelCrop,
    handleSubmit,
    triggerDraftSave,
    triggerPublish,
    clearPersistedDraft,
  }
```

**Points à respecter :**
- `objectUrlRef` existant est conservé — il révoque correctement le previous preview avant d'en créer un nouveau (fix memory leak existant que le hook actuel maîtrise déjà).
- L'ordre `URL.revokeObjectURL` AVANT `URL.createObjectURL` est crucial — sinon le revoke s'applique à l'URL qu'on vient de créer.
- Le state `imageFile` reste interne au hook — la consommation par `submitForm` (ligne 539–541) est inchangée.

---

## Étape 5 — Intégration dans `EventForm.tsx`

**Fichier :** `frontend/src/components/event/EventForm.tsx`

### 5.1 — Imports

Ajouter en haut du fichier :

```tsx
import ImageCropper from '@/components/utils/ImageCropper'
```

### 5.2 — Étendre `EventFormProps`

Localiser l'interface `EventFormProps` (vers ligne 14, signature courante avec `onImageChange`). Ajouter ces 4 props :

```tsx
  cropSource: string | null
  cropAspect: number
  onCropConfirm: (blob: Blob) => void
  onCropCancel: () => void
```

### 5.3 — Destructurer dans la signature de fonction

Remplacer la destructuration des props (lignes 94–112) par :

```tsx
export default function EventForm({
  mode,
  submitLabel,
  values,
  errors,
  submitting,
  draftSaving = false,
  imagePreview,
  selectedImageName,
  cropSource,
  cropAspect,
  onFieldChange,
  onImageChange,
  onCropConfirm,
  onCropCancel,
  onSubmit,
  onCancel,
  onSaveDraft,
  saveDraftLabel = 'Sauvegarder en Brouillon',
  onDelete,
  deleting = false,
  deleteLabel = 'Supprimer',
}: Readonly<EventFormProps>) {
```

### 5.4 — Rendre `<ImageCropper>` conditionnellement

**Insérer** juste avant la fermeture `</form>` du formulaire (à la fin du composant, juste avant la dernière `</form>` du JSX) :

```tsx
      {cropSource && (
        <ImageCropper
          src={cropSource}
          aspect={cropAspect}
          circular={false}
          onCropComplete={onCropConfirm}
          onCancel={onCropCancel}
        />
      )}
```

> Note : la modale est `position: fixed inset-0 z-50` côté `ImageCropper` — elle se rend correctement même imbriquée dans le `<form>`. Pas besoin de portail (overkill et hors scope).

---

## Étape 6 — Adaptation de `EventCreatePage.tsx` et `EventEditPage.tsx`

`EventCreatePage` et `EventEditPage` consomment `useEventForm` puis passent ses valeurs à `<EventForm ...>`. Ils doivent désormais passer les **4 nouvelles props** : `cropSource`, `cropAspect`, `onCropConfirm`, `onCropCancel`.

**Recherche & remplacement** dans chacune des 2 pages : à l'endroit où `<EventForm>` est instancié, ajouter (à côté de `imagePreview={form.imagePreview}` ou équivalent) :

```tsx
        cropSource={form.cropSource}
        cropAspect={form.cropAspect}
        onCropConfirm={form.confirmCrop}
        onCropCancel={form.cancelCrop}
```

**Aucune autre modification** de ces 2 pages n'est requise.

---

## Étape 7 — Tests

### 7.1 — `useEventForm.test.tsx` — mises à jour

**Tests existants à adapter** (lignes 140–193 environ — bloc `handleImageChange`) :

Le flow change. Les tests qui faisaient `result.current.handleImageChange({ target: { files: [...] } })` puis vérifiaient immédiatement `imagePreview === 'blob:first'` doivent désormais :
1. Mocker `FileReader` (cf. helper du test 7.1.1 ci-dessous)
2. Appeler `handleImageChange` → `cropSource` devient la dataURL
3. Appeler `confirmCrop(blob)` → c'est à ce moment que `imagePreview` est setté

**Adapter le test « stores the selected image and rejects invalid files »** :

```tsx
  it('stores the cropped image after confirmCrop and rejects invalid files', async () => {
    mockFileReader('data:image/png;base64,abc')
    const createObjectURL = vi.fn().mockReturnValueOnce('blob:first').mockReturnValueOnce('blob:second')
    const revokeObjectURL = vi.fn()
    globalThis.URL.createObjectURL = createObjectURL
    globalThis.URL.revokeObjectURL = revokeObjectURL

    const { result, unmount } = renderHook(() => useEventForm({ mode: 'create' }))

    // Fichier invalide → erreur, pas de crop
    act(() => {
      result.current.handleImageChange({ target: { files: [new File(['x'], 'doc.txt', { type: 'text/plain' })] } } as never)
    })
    expect(result.current.errors.image).toBe('Le fichier doit être une image.')
    expect(result.current.cropSource).toBeNull()

    // Aucun fichier → no-op
    act(() => {
      result.current.handleImageChange({ target: { files: [] } } as never)
    })
    expect(createObjectURL).not.toHaveBeenCalled()

    // Fichier valide → cropSource set après FileReader
    await act(async () => {
      result.current.handleImageChange({ target: { files: [new File(['a'], 'banner-a.png', { type: 'image/png' })] } } as never)
      await new Promise((r) => queueMicrotask(r as () => void))
    })
    expect(result.current.cropSource).toBe('data:image/png;base64,abc')
    expect(result.current.imagePreview).toBeNull()  // pas encore confirmé

    // Confirm crop → imagePreview + selectedImageName settés
    act(() => {
      result.current.confirmCrop(new Blob(['cropped'], { type: 'image/png' }))
    })
    expect(result.current.imagePreview).toBe('blob:first')
    expect(result.current.selectedImageName).toBe('banner-a.png')
    expect(result.current.cropSource).toBeNull()  // cropper fermé

    // Deuxième confirm → ancien revoked, nouveau créé
    await act(async () => {
      result.current.handleImageChange({ target: { files: [new File(['b'], 'banner-b.png', { type: 'image/png' })] } } as never)
      await new Promise((r) => queueMicrotask(r as () => void))
    })
    act(() => {
      result.current.confirmCrop(new Blob(['cropped2'], { type: 'image/png' }))
    })
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:first')
    expect(result.current.imagePreview).toBe('blob:second')

    unmount()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:second')
  })
```

**Nouveau test** : `cancelCrop` ne touche pas à `imageFile` :

```tsx
  it('cancelCrop closes the cropper without touching imageFile/imagePreview', async () => {
    mockFileReader('data:image/png;base64,abc')
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    await act(async () => {
      result.current.handleImageChange({ target: { files: [new File(['x'], 'p.png', { type: 'image/png' })] } } as never)
      await new Promise((r) => queueMicrotask(r as () => void))
    })
    expect(result.current.cropSource).not.toBeNull()

    act(() => {
      result.current.cancelCrop()
    })
    expect(result.current.cropSource).toBeNull()
    expect(result.current.imagePreview).toBeNull()
    expect(result.current.selectedImageName).toBeNull()
  })
```

**Helper `mockFileReader`** à ajouter dans le scope du fichier de test (ou dans un beforeEach partagé). Voir 7.1.1 ci-dessous.

### 7.1.1 — Helper `mockFileReader` partagé

À ajouter dans `useEventForm.test.tsx` et `ProfileEditPage.test.tsx` :

```tsx
function mockFileReader(result: string) {
  class MockReader {
    public onload: (() => void) | null = null
    public result: string | null = null
    readAsDataURL() {
      this.result = result
      queueMicrotask(() => this.onload?.())
    }
  }
  globalThis.FileReader = MockReader as unknown as typeof FileReader
}
```

Wrapper dans un `beforeEach` qui sauvegarde l'original et un `afterEach` qui le restaure.

### 7.2 — `ProfileEditPage.test.tsx` — mises à jour

**Tests existants à adapter** (lignes 140–185 — bloc photo upload) :

Les tests qui faisaient `fireEvent.change(input, { target: { files: [file] } })` puis vérifiaient `await screen.findByAltText('Test User')` (preview affiché) doivent désormais :
1. Mocker `FileReader` (helper ci-dessus)
2. `fireEvent.change` → ouvre le cropper (rend `<ImageCropper>` à l'écran)
3. Simuler le confirm en récupérant le bouton « Recadrer » de la modale et le cliquant — **mais** `react-image-crop` requiert un `completedCrop`, ce qui demande une simulation complexe. **Solution pragmatique** : mocker `ImageCropper` au niveau du test pour qu'il appelle `onCropComplete(new Blob(...))` immédiatement.

**Pattern recommandé** :

```tsx
// En haut du fichier de test, après les autres mocks :
vi.mock('@/components/utils/ImageCropper', () => ({
  default: ({ onCropComplete, onCancel, src }: { onCropComplete: (b: Blob) => void; onCancel: () => void; src: string }) => (
    <div data-testid="image-cropper-mock" data-src={src}>
      <button type="button" onClick={() => onCropComplete(new Blob(['cropped'], { type: 'image/png' }))}>
        Mock Recadrer
      </button>
      <button type="button" onClick={onCancel}>
        Mock Annuler
      </button>
    </div>
  ),
}))
```

**Test mis à jour** « accepts valid image file and shows preview » :

```tsx
  it('opens cropper on valid photo file and shows preview after confirm', async () => {
    mockFileReader('data:image/png;base64,abc')
    mockUseAuth.mockReturnValue({ user: mockUser })
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#photo-input')!
    const file = new File(['img'], 'photo.jpg', { type: 'image/jpeg' })

    await act(async () => {
      fireEvent.change(input, { target: { files: [file] } })
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    // Cropper ouvert
    expect(screen.getByTestId('image-cropper-mock')).toBeTruthy()

    // Confirm → preview affiché
    fireEvent.click(screen.getByText('Mock Recadrer'))
    expect(await screen.findByAltText('Test User')).toBeTruthy()
  })
```

**Test mis à jour** « calls uploadPhoto when photo file is selected on submit » : même pattern, ajouter le clic sur « Mock Recadrer » entre `fireEvent.change` et `fireEvent.click(Enregistrer)`. Attendre — `expect(mockUploadPhoto).toHaveBeenCalledWith(expect.any(File))` (le File a un nom différent du `file` original car c'est celui issu du Blob — le name est préservé donc on peut tester `(call.name === 'photo.jpg')`).

**Nouveaux tests** :

```tsx
  it('cancel cropper for photo does not push photoFile', async () => {
    mockFileReader('data:image/png;base64,abc')
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#photo-input')!

    await act(async () => {
      fireEvent.change(input, { target: { files: [new File(['x'], 'p.png', { type: 'image/png' })] } })
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    fireEvent.click(screen.getByText('Mock Annuler'))
    expect(screen.queryByTestId('image-cropper-mock')).toBeNull()
    // Submit ne doit pas appeler uploadPhoto
    fireEvent.click(screen.getByText('Enregistrer'))
    await waitFor(() => expect(mockUploadPhoto).not.toHaveBeenCalled())
  })

  it('opens banner cropper with 3:1 aspect on banner file select', async () => {
    mockFileReader('data:image/png;base64,abc')
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#banner-input')!

    await act(async () => {
      fireEvent.change(input, { target: { files: [new File(['x'], 'b.png', { type: 'image/png' })] } })
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    expect(screen.getByTestId('image-cropper-mock')).toBeTruthy()
  })
```

**Tests inchangés** : « rejects non-image file », « rejects image file larger than 2MB » — la validation a lieu avant l'ouverture du cropper, donc la modale ne s'ouvre pas. Vérifier que `screen.queryByTestId('image-cropper-mock')` est `null` après l'erreur.

### 7.3 — `EventForm.test.tsx` — nouveaux tests

```tsx
  it('does not render ImageCropper when cropSource is null', () => {
    render(<EventForm {...defaultProps} cropSource={null} cropAspect={16/9} onCropConfirm={vi.fn()} onCropCancel={vi.fn()} />)
    expect(screen.queryByRole('dialog', { name: /Recadrer/i })).toBeNull()
  })

  it('renders ImageCropper when cropSource is set', () => {
    render(<EventForm {...defaultProps} cropSource="data:image/png;base64,abc" cropAspect={16/9} onCropConfirm={vi.fn()} onCropCancel={vi.fn()} />)
    expect(screen.getByRole('dialog', { name: /Recadrer/i })).toBeTruthy()
  })
```

(`defaultProps` doit inclure les 4 nouvelles props par défaut : `cropSource: null`, `cropAspect: 16/9`, `onCropConfirm: vi.fn()`, `onCropCancel: vi.fn()`. Mettre à jour le helper de création de props existant en haut du fichier de test.)

### 7.4 — `EventCreatePage.test.tsx` et `EventEditPage.test.tsx` — adaptation

Ces fichiers consomment indirectement `EventForm`. Vérifier que les snapshots / imports passent toujours après ajout des 4 props (qui sont fournies via `useEventForm` mocké ou réel). Si des tests mockent `useEventForm`, **étendre le mock** pour inclure les nouvelles propriétés `cropSource: null`, `cropAspect: 16/9`, `confirmCrop: vi.fn()`, `cancelCrop: vi.fn()`.

### 7.5 — Récapitulatif des tests

| Fichier | Cas | Action |
|---|---|---|
| `useImageCropFlow.test.ts` | 9 nouveaux | Couverture du hook (init, validation, confirm, cancel, blob.type fallback, reset input) |
| `useEventForm.test.tsx` | Adapter tests `handleImageChange` (2 tests) + 1 nouveau (`cancelCrop`) | Flow passe par `cropSource` puis `confirmCrop` |
| `ProfileEditPage.test.tsx` | Adapter ~5 tests photo/banner upload + 2 nouveaux (cancel, banner cropper) | Mock `ImageCropper` au niveau du fichier de test |
| `EventForm.test.tsx` | 2 nouveaux | Rendu conditionnel du cropper |
| `EventCreatePage.test.tsx` | Étendre les mocks de `useEventForm` si présents | 4 nouvelles propriétés |
| `EventEditPage.test.tsx` | Étendre les mocks de `useEventForm` si présents | 4 nouvelles propriétés |
| `ImageCropper.test.tsx` | Aucune | **Ne pas toucher** — déjà à 100 % |

Cible **≥ 80 %** sur le nouveau code (`useImageCropFlow.ts` + nouvelles branches dans les pages).

---

## Étape 8 — Skeleton

**Décision : aucun nouveau skeleton requis.** Justifications :

- `<ImageCropper>` est une **modale full-screen** (`position: fixed inset-0 z-50`) rendue de manière instantanée dès que `cropSource !== null`. Pas d'état loading propre côté React — `react-image-crop` gère son propre rendu interne via le `<img>` qui charge la dataURL.
- La dataURL est déjà entièrement chargée en mémoire au moment où `cropSource` est setté (FileReader a fini son `readAsDataURL`). Le rendu de l'image est donc immédiat (pas de network round-trip).
- Les pages cibles (`ProfileEditPage`, `EventCreatePage`, `EventEditPage`) ont déjà leur skeleton (`profile`, `event-edit`) qui couvre le rendu initial du formulaire — la modale crop n'apparaît qu'après une interaction utilisateur (sélection fichier), donc hors du scope du skeleton.
- Conformément à `frontend/skeleton/README.md` § « Quand générer un skeleton », un nouveau bones n'est requis que pour une **page ou un composant qui effectue un appel API et affiche un état `loading`**. La modale crop ne fait aucun appel API.

**Aucune mise à jour** de `frontend/src/bones/`, `registry.js`, ou de la table « Skeletons existants » dans `AGENTS.md` n'est nécessaire.

---

## Critères d'acceptation

- [ ] Sur `/profile/me/edit`, sélectionner un avatar → modale de crop **circulaire 1:1** s'ouvre → confirm → preview affiché en cercle dans `UserAvatar`
- [ ] Sur `/profile/me/edit`, sélectionner une bannière → modale de crop **3:1 (rectangulaire)** s'ouvre → confirm → preview affiché en bandeau dans `UserBanner`
- [ ] Sur `/events/new` et `/events/:id/edit`, sélectionner une bannière → modale de crop **16:9** s'ouvre → confirm → preview affiché dans la zone bannière
- [ ] Annuler dans la modale ferme le cropper sans modifier le state du formulaire (preview précédent conservé)
- [ ] Re-sélectionner le **même fichier** après cancel rouvre la modale (input `value` resetté)
- [ ] Validation MIME et taille appliquée **avant** ouverture du cropper (un fichier non-image ou trop gros affiche l'erreur sans ouvrir la modale)
- [ ] Submit du formulaire upload bien le fichier croppé (vérifié via les noms — le nom original est préservé) et **pas** le fichier brut original
- [ ] Suppression de la bannière profil reste fonctionnelle (n'invoque pas le cropper)
- [ ] Aucune régression sur les autres champs des formulaires

---

## Edge cases à traiter explicitement

| Cas | Comportement attendu | Implémenté par |
|---|---|---|
| Fichier non-image | Erreur affichée, **pas d'ouverture** du cropper, input resetté | `validate*()` retourne un message → `onValidationError` → `setErrors` + reset `event.target.value` dans `useImageCropFlow.handleFileSelect` |
| Fichier > taille max | Erreur affichée, **pas d'ouverture** du cropper, input resetté | Idem |
| Cancel puis re-sélection du même fichier | Cropper se rouvre | Reset de `inputRef.current.value` dans `cleanup()` |
| Cancel puis sélection d'un autre fichier | Cropper s'ouvre avec le nouveau fichier | `inputRef` est mis à jour à chaque `handleFileSelect` |
| Image très large (4000×4000) | Cropper la rend en `max-h-[60vh]` | Géré par `ImageCropper` existant (ligne 55) |
| Image très petite (200×100) | `react-image-crop` gère, le crop initial est calculé par `initCrop` | Géré par `imageCropperUtils.initCrop` |
| Submit pendant que le cropper est ouvert | Impossible — la modale `position: fixed z-50` capture le focus visuel et `<button type="submit">` est en arrière-plan | Comportement natif de la modale |
| Confirm crop alors que `completedCrop` est null | Impossible — bouton « Recadrer » `disabled` | Géré par `ImageCropper` (ligne 62) |
| Memory leak `URL.createObjectURL(blob)` du preview | `useEventForm` revoke l'ancien via `objectUrlRef`. `ProfileEditPage` ne revoke pas (cohérent avec le code actuel — fix global hors scope) | `objectUrlRef` dans `useEventForm` |
| Blob retourné par `cropToBlob` avec type `''` | Fallback sur `'image/png'` lors de la conversion en File | `useImageCropFlow.confirmCrop` |
| FileReader échoue (rare, file corrompu) | `cropSource` ne devient jamais non-null → cropper ne s'ouvre pas. Pas d'erreur explicite mais pas de crash | Aucun `onerror` handler — comportement assumé. Hors scope si on voulait gérer ce cas explicitement. |

---

## Documentation

### `frontend/docs/components.md`

**Compléter** la fiche `ImageCropper` (vers ligne 115–120) — ajouter à la fin :

```markdown
**Intégrations actives (SCRUM-123) :**
- `ProfileEditPage` — avatar (aspect 1:1, circular) et bannière profil (aspect 3:1)
- `EventForm` (consommé par `EventCreatePage` et `EventEditPage`) — bannière événement (aspect 16:9)

Le flux d'intégration (sélection fichier → validation → FileReader → modale crop → confirm → File final) est centralisé dans le hook réutilisable `useImageCropFlow` (`@/hooks/useImageCropFlow`).
```

**Ajouter** une nouvelle fiche dans la section « Hooks » (ou créer la section si absente) :

```markdown
### useImageCropFlow

Hook utilitaire qui encapsule le flux complet « sélection fichier → validation → FileReader → ouverture du cropper → conversion Blob → File ». Utilisé par `ProfileEditPage` (×2) et `useEventForm` (×1).

Options : `aspect`, `circular?`, `validate?`, `onValidationError?`.
Résultat : `cropSource`, `handleFileSelect`, `aspect`, `circular`, `confirmCrop`, `cancelCrop`.

Garantit la **réinitialisation de l'input file** après confirm/cancel/erreur — sans cela, re-sélectionner le même fichier ne redéclenche pas l'event `change` (comportement HTML standard).
```

### `frontend/docs/sprint-context.md`

**Ajouter** en haut du fichier (avant la dernière entrée Sprint 6) :

```markdown
## Sprint 6 — Intégration ImageCropper sur avatar + bannières (SCRUM-123) — 2026-04-22

Terminé.

Fonctionnalités livrées :
- Hook `useImageCropFlow` (`src/hooks/useImageCropFlow.ts`) : encapsule le flux sélection fichier → validation → FileReader → cropper → Blob → File. Préserve le nom original, reset l'input pour permettre la re-sélection du même fichier après cancel.
- `ProfileEditPage` : intégration sur l'avatar (aspect 1:1, circular) et la bannière profil (aspect 3:1). Validation MIME + taille préservée avant ouverture du cropper.
- `useEventForm` + `EventForm` : intégration sur la bannière événement (aspect 16:9). 4 nouvelles props sur `EventForm` (`cropSource`, `cropAspect`, `onCropConfirm`, `onCropCancel`). `EventCreatePage` et `EventEditPage` passent simplement les valeurs du hook à `EventForm`.
- Aucun nouvel endpoint ni modification des services — la conversion Blob → File préserve la signature `(file: File)` de `uploadPhoto`, `uploadBanner`, `uploadEventImage`.
- Aucun nouveau skeleton — la modale n'a pas d'état loading.
- Tests : 9 nouveaux pour `useImageCropFlow`, 2 adaptés + 1 nouveau pour `useEventForm`, ~5 adaptés + 2 nouveaux pour `ProfileEditPage`, 2 nouveaux pour `EventForm`. `ImageCropper.test.tsx` non touché.
```

### `frontend/AGENTS.md`

Aucune modification — pas de nouveau composant réutilisable au sens de la table « Composants utilitaires à utiliser en priorité ». Le hook `useImageCropFlow` est documenté dans `components.md`.

---

## Résumé des fichiers à créer/modifier

| Fichier | Action |
|---|---|
| `frontend/src/hooks/useImageCropFlow.ts` | **Créer** |
| `frontend/src/__tests__/hooks/useImageCropFlow.test.ts` | **Créer** |
| `frontend/src/pages/profile/ProfileEditPage.tsx` | **Modifier** — supprimer `handlePhotoChange` + `handleBannerChange`, ajouter 2 instances de `useImageCropFlow`, brancher inputs, rendre 2 `<ImageCropper>` |
| `frontend/src/hooks/useEventForm.ts` | **Modifier** — remplacer `handleImageChange` interne par `useImageCropFlow`, exposer `cropSource` / `cropAspect` / `confirmCrop` / `cancelCrop` |
| `frontend/src/components/event/EventForm.tsx` | **Modifier** — étendre `EventFormProps` (4 props), rendre `<ImageCropper>` conditionnel |
| `frontend/src/pages/event/EventCreatePage.tsx` | **Modifier** — passer 4 nouvelles props à `<EventForm>` |
| `frontend/src/pages/event/EventEditPage.tsx` | **Modifier** — passer 4 nouvelles props à `<EventForm>` |
| `frontend/src/__tests__/pages/profile/ProfileEditPage.test.tsx` | **Modifier** — mock `ImageCropper`, adapter ~5 tests, ajouter 2 nouveaux |
| `frontend/src/__tests__/hooks/useEventForm.test.tsx` | **Modifier** — adapter 2 tests, ajouter 1 test cancel |
| `frontend/src/__tests__/components/event/EventForm.test.tsx` | **Modifier** — étendre `defaultProps`, ajouter 2 tests rendu conditionnel |
| `frontend/src/__tests__/pages/event/EventCreatePage.test.tsx` | **Modifier (si besoin)** — étendre mocks `useEventForm` |
| `frontend/src/__tests__/pages/event/EventEditPage.test.tsx` | **Modifier (si besoin)** — étendre mocks `useEventForm` |
| `frontend/docs/components.md` | **Modifier** — fiche `ImageCropper` complétée + nouvelle fiche `useImageCropFlow` |
| `frontend/docs/sprint-context.md` | **Modifier** — entrée Sprint 6 SCRUM-123 |

**Total :** 11–13 fichiers modifiés (selon que les tests Event*Page nécessitent une adaptation), 2 créés.

---

## Règles critiques à respecter

| Règle | Détail |
|---|---|
| Hook extrait | `useImageCropFlow` créé en premier, testé, puis intégré aux 3 cibles |
| Pas de duplication | La logique « sélection → validation → FileReader → cropper » n'apparaît qu'une fois (dans le hook) |
| Validation pré-crop | MIME + taille validés AVANT ouverture du cropper |
| Reset input file | `event.target.value = ''` après confirm/cancel/validation échouée |
| Blob → File | `new File([blob], originalName, { type: blob.type \|\| 'image/png' })` |
| Aspects en const nommées | `AVATAR_ASPECT = 1`, `PROFILE_BANNER_ASPECT = 3`, `EVENT_BANNER_ASPECT = 16 / 9` — pas de magic numbers |
| Pas de modification de `ImageCropper` ni `imageCropperUtils` | Hors scope SCRUM-123 |
| Pas de modification des services | `userService.ts`, `eventApi.ts` — signatures `(file: File)` conservées |
| Pas de modification du backend / openapi | Purement frontend |
| Pas de skeleton | Justifié — modale sans loading |
| Pas de `any` TS | Tous les types déclarés explicitement, `Readonly<Props>` |
| Design tokens Tailwind | `border-border`, `text-foreground/X`, `text-accent`, `bg-background` |
| Alias `@/` | Pas de chemin relatif `../` |
| `useCallback` sur les handlers passés à `ImageCropper` | Évite les re-renders inutiles de la modale |
| `URL.revokeObjectURL` du previous preview | Pattern existant dans `useEventForm` à conserver |
| Une seule branche `feature/s6-image-crop-integration` (depuis `main`) | Une seule PR couvrant SCRUM-123 |
| Doc dans le même commit | `components.md` + `sprint-context.md` |
| SonarCloud | ≥ 80 % coverage sur le nouveau code, ≤ 3 % duplication, Security/Reliability/Maintainability Rating A |

---

## Prompt de lancement d'implémentation

````
Tu vas implémenter la feature SCRUM-123 (intégration `ImageCropper` sur avatar profil + bannière profil + bannière événement) sur **une seule branche `feature/s6-image-crop-integration`** créée depuis `main`, et **une seule PR finale**.

## Source unique de vérité
Le fichier `specs_archives/specs_claude/specs_scrum-123.md` est la source de vérité pour QUOI et POURQUOI. Lis-le entièrement avant de commencer et reviens-y à chaque étape.

## Lectures préliminaires obligatoires
Avant d'écrire du code, lis ces fichiers en entier :
- `frontend/AGENTS.md` (conventions, design tokens, pattern variants, règle DRY)
- `frontend/docs/README.md`, `architecture.md`, `components.md`, `types.md`, `dev-guide.md`, `sprint-context.md`
- `frontend/skeleton/README.md` (pour confirmer pourquoi aucun nouveau skeleton n'est requis)
- Tous les fichiers à modifier (cf. liste « Résumé des fichiers à créer/modifier » de la spec) — TOUS, pas juste les diffs
- `frontend/src/components/utils/ImageCropper.tsx` et `imageCropperUtils.ts` (pour comprendre la signature exacte du composant à brancher)

## Préparation de la branche
```bash
git checkout main
git pull origin main
git checkout -b feature/s6-image-crop-integration
```

## Ordre d'implémentation strict

### Phase 1 — Hook utilitaire
1. Créer `frontend/src/hooks/useImageCropFlow.ts` (cf. spec étape 1 — code complet à reprendre).
2. Créer `frontend/src/__tests__/hooks/useImageCropFlow.test.ts` avec les 9 tests (cf. spec étape 2).
3. Lancer `npm run test -- useImageCropFlow` — tout doit passer, couverture du hook proche de 100 %.

### Phase 2 — ProfileEditPage
4. Modifier `frontend/src/pages/profile/ProfileEditPage.tsx` (cf. spec étape 3) :
   - Imports + 2 constantes d'aspect (`AVATAR_ASPECT = 1`, `PROFILE_BANNER_ASPECT = 3`)
   - Supprimer `handlePhotoChange` + `handleBannerChange`
   - Ajouter `validatePhoto` + `validateBanner` + 2 instances de `useImageCropFlow`
   - Ajouter `handlePhotoCropComplete` + `handleBannerCropComplete` (avec `useCallback`)
   - Brancher les 2 inputs `onChange` sur `*.handleFileSelect`
   - Rendre 2 `<ImageCropper>` conditionnels
5. Adapter `frontend/src/__tests__/pages/profile/ProfileEditPage.test.tsx` (cf. spec étape 7.2) :
   - Ajouter le mock de `ImageCropper` au niveau du fichier
   - Adapter les ~5 tests existants photo/banner pour passer par le mock cropper
   - Ajouter les 2 nouveaux tests (cancel, banner cropper)
6. Lancer `npm run test -- ProfileEditPage` — tout doit passer.

### Phase 3 — useEventForm + EventForm
7. Modifier `frontend/src/hooks/useEventForm.ts` (cf. spec étape 4) :
   - Import + constante `EVENT_BANNER_ASPECT = 16 / 9`
   - Étendre `UseEventFormResult` (4 nouvelles propriétés exposées)
   - Supprimer l'ancien corps de `handleImageChange` (lignes 390–417)
   - Ajouter `validateImage` + instance `useImageCropFlow` + `handleImageChange` (délégué) + `confirmCrop` + `cancelCrop`
   - Étendre le `return` du hook
8. Modifier `frontend/src/components/event/EventForm.tsx` (cf. spec étape 5) :
   - Import `ImageCropper`
   - Étendre `EventFormProps` (4 nouvelles props)
   - Étendre la destructuration de la signature
   - Rendre `<ImageCropper>` conditionnel à la fin du `<form>`
9. Modifier `frontend/src/pages/event/EventCreatePage.tsx` et `frontend/src/pages/event/EventEditPage.tsx` (cf. spec étape 6) :
   - Passer les 4 nouvelles props à `<EventForm>` depuis le hook
10. Adapter `frontend/src/__tests__/hooks/useEventForm.test.tsx` (cf. spec étape 7.1) :
    - Ajouter le helper `mockFileReader`
    - Adapter les 2 tests `handleImageChange` existants (passer par `cropSource` puis `confirmCrop`)
    - Ajouter le nouveau test `cancelCrop`
11. Adapter `frontend/src/__tests__/components/event/EventForm.test.tsx` (cf. spec étape 7.3) :
    - Étendre `defaultProps` avec les 4 nouvelles props
    - Ajouter les 2 tests de rendu conditionnel
12. Vérifier `frontend/src/__tests__/pages/event/EventCreatePage.test.tsx` et `EventEditPage.test.tsx` :
    - Si `useEventForm` est mocké → étendre le mock avec `cropSource: null`, `cropAspect: 16/9`, `confirmCrop: vi.fn()`, `cancelCrop: vi.fn()`
    - Si `useEventForm` est utilisé tel quel → vérifier que les tests passent toujours
13. Lancer `npm run test` complet — tout doit passer (couverture ≥ 80 % sur les fichiers touchés).

### Phase 4 — Documentation (dans le même commit que le code correspondant)
14. `frontend/docs/components.md` — compléter la fiche `ImageCropper` + ajouter la fiche `useImageCropFlow` (cf. spec section Documentation).
15. `frontend/docs/sprint-context.md` — ajouter l'entrée Sprint 6 SCRUM-123 en haut du fichier (cf. spec section Documentation).

### Phase 5 — Vérification finale
16. `npm run lint` — vert (TypeScript strict, ESLint).
17. `npm run test` — vert avec couverture ≥ 80 % sur tous les fichiers touchés.
18. Test manuel dans le navigateur (`npm run dev`) :
    - **`/profile/me/edit`** :
      - Cliquer « Changer la photo » → sélectionner une image → modale circulaire 1:1 s'ouvre → drag pour crop → « Recadrer » → preview en cercle dans `UserAvatar`
      - Cliquer « Changer la bannière » → sélectionner une image → modale rectangulaire 3:1 → confirm → preview en bandeau dans `UserBanner`
      - Re-sélectionner le **même fichier** après « Annuler » → modale se rouvre (test du reset input)
      - Sélectionner un PDF → erreur affichée, pas de modale
    - **`/events/new` puis `/events/:id/edit`** :
      - Sélectionner une bannière → modale 16:9 s'ouvre → confirm → preview affiché
      - Cancel → preview précédent conservé
    - **Submit** : vérifier dans le Network tab que le fichier multipart envoyé est bien la version croppée (taille différente de l'original)

## Interdits stricts
- ❌ Ne pas modifier `ImageCropper.tsx` ni `imageCropperUtils.ts` (SCRUM-122 mergé, tests à 100 %).
- ❌ Ne pas modifier `userService.ts` ni `eventApi.ts` — la conversion Blob → File préserve la signature `(file: File)`.
- ❌ Ne pas créer ni modifier d'endpoint backend — purement frontend.
- ❌ Ne pas créer de nouveau skeleton (cf. justification spec étape 8).
- ❌ Ne pas dupliquer la logique de crop entre les 3 cibles — utiliser `useImageCropFlow`.
- ❌ Ne pas faire passer la suppression de bannière par le cropper — `handleBannerDelete` reste à l'identique.
- ❌ Ne jamais utiliser `any` TypeScript.
- ❌ Ne jamais importer en chemin relatif (`../`) côté front — toujours `@/`.
- ❌ Ne pas inline `16/9` dans plusieurs endroits — utiliser la const `EVENT_BANNER_ASPECT`.

## Conventions à respecter
- camelCase partout
- Pas de `any`, `Readonly<Props>` sur tous les composants
- Design tokens Tailwind (`border-border`, `text-foreground/X`, `text-accent`, `bg-background`)
- Const map typée pour toute variante visuelle (pas de ternaire inline sur className)
- `useCallback` sur les handlers passés à `<ImageCropper>` (évite re-renders)
- Doc mise à jour **dans le même commit** que le code correspondant (règle d'or AGENTS.md)

## Critères de done
- [ ] `npm run lint` vert
- [ ] `npm run test` vert avec couverture ≥ 80 % sur tous les fichiers touchés (`useImageCropFlow.ts`, `ProfileEditPage.tsx`, `useEventForm.ts`, `EventForm.tsx`)
- [ ] Vérification manuelle navigateur (Phase 5 point 18) — les 3 cibles fonctionnent en confirm + cancel + re-sélection du même fichier
- [ ] PR ouverte sur la branche `feature/s6-image-crop-integration` (basée sur `main`), titre clair (ex: « SCRUM-123 — Intégration ImageCropper (avatar + bannières) »), description listant le ticket et les fichiers touchés
- [ ] SonarCloud sur la PR : Quality Gate vert (couverture ≥ 80 %, duplication ≤ 3 %, Security/Reliability/Maintainability Rating A)
````
