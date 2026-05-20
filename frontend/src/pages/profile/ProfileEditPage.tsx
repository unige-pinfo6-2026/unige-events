import { type KeyboardEvent as ReactKeyboardEvent, useCallback, useEffect, useState } from 'react'
import { AxiosError } from 'axios'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import {
  checkUsernameAvailable,
  deleteBanner,
  getMe,
  updateProfile,
  updateUsername,
  uploadBanner,
  uploadPhoto,
} from '@/services/userService'
import { FACULTIES } from '@/types/faculty'
import {
  RESERVED_USERNAMES,
  STUDY_LEVELS,
  USERNAME_MAX_LENGTH,
  USERNAME_MIN_LENGTH,
  USERNAME_PATTERN,
  type User,
} from '@/types/user'
import { useDebounce } from '@/hooks/useDebounce'
import FormField, { Input, Select, Textarea } from '@/components/utils/FormField'
import { ButtonPrimary, ButtonSecondary } from '@/components/utils/Buttons'
import { Check, ImagePlus, Loader2, Trash2, X } from 'lucide-react'
import UserAvatar from '@/components/user/UserAvatar'
import UserBanner from '@/components/user/UserBanner'
import { useToast } from '@/hooks/useToast'
import ImageCropper from '@/components/utils/ImageCropper'
import { useImageCropFlow } from '@/hooks/useImageCropFlow'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'

const MAX_BIO_LENGTH = 500
const MAX_PHOTO_SIZE = 2 * 1024 * 1024
const MAX_BANNER_SIZE = 5 * 1024 * 1024
const AVATAR_ASPECT = 1
const PROFILE_BANNER_ASPECT = 3
const USERNAME_DEBOUNCE_MS = 400

/**
 * Status of the username live-check (SCRUM-169) — used to render the
 * inline icon + helper text under the input.
 */
type UsernameStatus =
  | 'idle' // empty input or no edit yet
  | 'unchanged' // matches the persisted value, no submit needed
  | 'invalid' // local pattern KO
  | 'reserved' // hits the blocklist
  | 'checking' // debounce settled, HEAD in flight
  | 'available' // HEAD returned 404 → libre
  | 'taken' // HEAD returned 200 → pris
  | 'error' // network error during check

interface FormErrors {
  name?: string
  bio?: string
  photo?: string
  banner?: string
}

export default function ProfileEditPage() {
  const { user, updateUser } = useAuth()
  const { showToast } = useToast()

  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [username, setUsername] = useState('')
  const [usernameInitial, setUsernameInitial] = useState('')
  const [faculty, setFaculty] = useState('')
  const [studyLevel, setStudyLevel] = useState('')
  const [bio, setBio] = useState('')
  const [interests, setInterests] = useState<string[]>([])
  const [interestInput, setInterestInput] = useState('')
  const [profilePublic, setProfilePublic] = useState(false)
  const [photoFile, setPhotoFile] = useState<File | null>(null)
  const [photoPreview, setPhotoPreview] = useState<string | null>(null)
  const [bannerFile, setBannerFile] = useState<File | null>(null)
  const [bannerPreview, setBannerPreview] = useState<string | null>(null)
  const [bannerDeleted, setBannerDeleted] = useState(false)
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [usernameStatus, setUsernameStatus] = useState<UsernameStatus>('idle')

  useEffect(() => {
    if (user) {
      setName(user.displayName ?? '')
      setUsername(user.username)
      setUsernameInitial(user.username)
      setFaculty(user.faculty ?? '')
      setStudyLevel(user.studyLevel ?? '')
      setBio(user.bio ?? '')
      setInterests(user.interests ?? [])
      setProfilePublic(user.profilePublic ?? false)
      if (user.avatarUrl) setPhotoPreview(user.avatarUrl)
      if (user.bannerUrl) setBannerPreview(user.bannerUrl)
    }
  }, [user])

  // SCRUM-169 — debounced live-check of the username (cf. spec § 5 Étape 13).
  const debouncedUsername = useDebounce(username, USERNAME_DEBOUNCE_MS)

  useEffect(() => {
    // Skip the network probe entirely when the local pre-checks already
    // give a definitive answer.
    if (username === usernameInitial) {
      setUsernameStatus('unchanged')
      return
    }
    if (!username) {
      setUsernameStatus('idle')
      return
    }
    if (RESERVED_USERNAMES.has(username)) {
      setUsernameStatus('reserved')
      return
    }
    if (!USERNAME_PATTERN.test(username)) {
      setUsernameStatus('invalid')
      return
    }
    // Wait until the debounce settles before hitting the network.
    if (debouncedUsername !== username) {
      setUsernameStatus('checking')
      return
    }

    let cancelled = false
    setUsernameStatus('checking')
    checkUsernameAvailable(username)
      .then((available) => {
        if (cancelled) return
        setUsernameStatus(available ? 'available' : 'taken')
      })
      .catch(() => {
        if (cancelled) return
        setUsernameStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [username, usernameInitial, debouncedUsername])

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
  const { confirmCrop: confirmPhotoCrop } = photoCrop

  const bannerCrop = useImageCropFlow({
    aspect: PROFILE_BANNER_ASPECT,
    circular: false,
    validate: validateBanner,
    onValidationError: (message) => setErrors((prev) => ({ ...prev, banner: message })),
  })
  const { confirmCrop: confirmBannerCrop } = bannerCrop

  const handlePhotoCropComplete = useCallback((blob: Blob) => {
    const file = confirmPhotoCrop(blob)
    if (!file) return
    setErrors((prev) => ({ ...prev, photo: undefined }))
    setPhotoFile(file)
    setPhotoPreview(URL.createObjectURL(blob))
  }, [confirmPhotoCrop])

  const handleBannerCropComplete = useCallback((blob: Blob) => {
    const file = confirmBannerCrop(blob)
    if (!file) return
    setErrors((prev) => ({ ...prev, banner: undefined }))
    setBannerFile(file)
    setBannerPreview(URL.createObjectURL(blob))
    setBannerDeleted(false)
  }, [confirmBannerCrop])

  function handleBannerDelete() {
    setBannerFile(null)
    setBannerPreview(null)
    setBannerDeleted(true)
    setErrors((prev) => ({ ...prev, banner: undefined }))
  }

  function handleInterestKeyDown(e: ReactKeyboardEvent<HTMLInputElement>) {
    if (e.key !== 'Enter') return
    e.preventDefault()
    const value = interestInput.trim()
    if (value && !interests.includes(value)) {
      setInterests((prev) => [...prev, value])
    }
    setInterestInput('')
  }

  function removeInterest(interest: string) {
    setInterests((prev) => prev.filter((i) => i !== interest))
  }

  function validate(): boolean {
    const newErrors: FormErrors = {}
    if (!name.trim()) newErrors.name = 'Le nom est requis.'
    if (bio.length > MAX_BIO_LENGTH) newErrors.bio = `La biographie ne doit pas dépasser ${MAX_BIO_LENGTH} caractères.`
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  async function handleSubmit(e: React.SyntheticEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!validate()) return

    // SCRUM-169 — block submit when username is locally invalid / reserved /
    // confirmed taken. The 'checking' state is treated as soft-block (let the
    // user retry after the probe settles).
    if (
      usernameStatus === 'invalid' ||
      usernameStatus === 'reserved' ||
      usernameStatus === 'taken'
    ) {
      showToast('error', 'Le nom d’utilisateur saisi est invalide. Corrigez-le avant d’enregistrer.')
      return
    }

    setSubmitting(true)
    try {
      // SCRUM-169 — push the username FIRST, separately from the rest of the
      // profile, so a 409 username_taken surfaces without rolling back the
      // other fields. Skip the call entirely if unchanged.
      if (username && username !== usernameInitial) {
        try {
          const updated = await updateUsername(username)
          // Reflect the new value in the auth store + local initial so the
          // status flips to 'unchanged' for any subsequent edit pass.
          updateUser(updated)
          setUsernameInitial(updated.username)
        } catch (error) {
          if (error instanceof AxiosError && error.response?.status === 409) {
            setUsernameStatus('taken')
            showToast('error', 'Ce nom d’utilisateur est déjà pris.')
            return
          }
          throw error
        }
      }

      if (photoFile) await uploadPhoto(photoFile)
      if (bannerFile) await uploadBanner(bannerFile)
      else if (bannerDeleted) await deleteBanner()
      const profileData: Partial<User> = {
        displayName: name.trim(),
        faculty: faculty as User['faculty'],
        studyLevel: studyLevel as User['studyLevel'],
        bio: bio.trim(),
        interests,
        profilePublic,
      }
      await updateProfile(profileData)
      const freshUser = await getMe()
      updateUser(freshUser)
      showToast('success', 'Profil mis à jour avec succès.')
      navigate('/profile/me')
    } catch {
      showToast('error', 'Une erreur est survenue. Veuillez réessayer.')
    } finally {
      setSubmitting(false)
    }
  }

  const previewUser = user ? { ...user, displayName: name, avatarUrl: photoPreview ?? user.avatarUrl, bannerUrl: bannerPreview } : null

  return (
    <SectionWrapper padding="sm" size="md" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        title={<>Modifier <mark>mon profil</mark></>}
        subtitle="Mettez à jour vos informations personnelles."
      />
      <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-8 max-sm:p-5 border border-border">
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">

          {/* Banner */}
          <div className="flex flex-col gap-3">
            <UserBanner user={previewUser} className="h-32 rounded-2xl" />
            <div className="flex gap-2 flex-wrap">
              <label
                htmlFor="banner-input"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-border text-sm font-semibold text-foreground/60 cursor-pointer hover:border-accent/50 hover:text-foreground transition-all"
              >
                <ImagePlus className="w-4 h-4" />
                Changer la bannière
              </label>
              <input id="banner-input" type="file" accept="image/*" onChange={bannerCrop.handleFileSelect} className="hidden" />
              {bannerPreview !== null && (
                <button
                  type="button"
                  onClick={handleBannerDelete}
                  className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-border text-sm font-semibold text-foreground/60 hover:border-error/50 hover:text-error transition-all cursor-pointer bg-transparent"
                >
                  <Trash2 className="w-4 h-4" />
                  Supprimer la bannière
                </button>
              )}
            </div>
            {errors.banner && <p className="text-xs text-error">{errors.banner}</p>}
          </div>

          <div className="border-t border-border" />

          {/* Photo */}
          <div className="flex items-center gap-5">
            <UserAvatar user={previewUser} className="size-16" />
            <div className="flex flex-col gap-1">
              <label
                htmlFor="photo-input"
                className="px-4 py-2 rounded-xl border border-border text-sm font-semibold text-foreground/60 cursor-pointer hover:border-accent/50 hover:text-foreground transition-all w-fit"
              >
                Changer la photo
              </label>
              <input id="photo-input" type="file" accept="image/*" onChange={photoCrop.handleFileSelect} className="hidden" />
              {errors.photo && <p className="text-xs text-error">{errors.photo}</p>}
            </div>
          </div>

          <div className="border-t border-border" />

          {/* SCRUM-169 — username with debounced live-check */}
          <FormField label="Nom d'utilisateur" htmlFor="username" required>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-foreground/30 text-sm pointer-events-none">@</span>
              <Input
                id="username"
                type="text"
                inputMode="text"
                autoCapitalize="none"
                autoComplete="off"
                spellCheck={false}
                value={username}
                onChange={(e) => setUsername(e.target.value.toLowerCase())}
                placeholder="jean.dupont"
                error={usernameStatusErrorText(usernameStatus)}
                aria-describedby="username-helper"
                className="pl-7"
                maxLength={USERNAME_MAX_LENGTH}
              />
            </div>
            <UsernameStatusRow
              status={usernameStatus}
              id="username-helper"
            />
          </FormField>

          <FormField label="Nom" htmlFor="name" required error={errors.name}>
            <Input
              id="name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              error={errors.name}
              placeholder="Votre nom complet"
            />
          </FormField>

          <div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1">
            <FormField label="Faculté" htmlFor="faculty">
              <Select id="faculty" value={faculty} onChange={(e) => setFaculty(e.target.value)}>
                <option value="">Sélectionner</option>
                {Object.entries(FACULTIES).map(([id, f]) => (
                  <option key={id} value={id}>{f.name}</option>
                ))}
              </Select>
            </FormField>

            <FormField label="Niveau d'études" htmlFor="studyLevel">
              <Select id="studyLevel" value={studyLevel} onChange={(e) => setStudyLevel(e.target.value)}>
                <option value="">Sélectionner</option>
                {Object.entries(STUDY_LEVELS).map(([id, s]) => (
                  <option key={id} value={id}>{s.name}</option>
                ))}
              </Select>
            </FormField>
          </div>

          <FormField label="Biographie" htmlFor="bio" error={errors.bio}>
            <Textarea
              id="bio"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              error={errors.bio}
              className="resize-y min-h-24"
              placeholder="Parlez un peu de vous..."
              rows={4}
              maxLength={MAX_BIO_LENGTH + 1}
            />
            <p className={['text-xs text-right mt-1', bio.length > MAX_BIO_LENGTH ? 'text-error font-semibold' : 'text-foreground/30'].join(' ')}>
              {bio.length} / {MAX_BIO_LENGTH}
            </p>
          </FormField>

          <FormField label="Centres d'intérêt" htmlFor="interests">
            <Input
              id="interests"
              type="text"
              value={interestInput}
              onChange={(e) => setInterestInput(e.target.value)}
              onKeyDown={handleInterestKeyDown}
              placeholder="Tapez un intérêt et appuyez sur Entrée"
            />
            {interests.length > 0 && (
              <div className="flex flex-wrap gap-2 mt-2.5">
                {interests.map((interest) => (
                  <span
                    key={interest}
                    className="inline-flex items-center gap-1.5 bg-foreground/5 border border-border text-foreground/60 rounded-full px-3 py-1 text-sm font-medium"
                  >
                    {interest}
                    <button
                      type="button"
                      onClick={() => removeInterest(interest)}
                      aria-label={`Supprimer ${interest}`}
                      className="text-foreground/30 hover:text-foreground/60 transition-colors cursor-pointer bg-transparent border-0 p-0 leading-none"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                ))}
              </div>
            )}
          </FormField>

          {/* Visibility toggle */}
          <div className="flex items-center justify-between py-4 border-t border-b border-border">
            <div>
              <p className="text-sm font-semibold text-foreground">Profil public</p>
              <p className="text-xs text-foreground/40 mt-0.5">Visible par tous les membres de la communauté</p>
            </div>
            <label htmlFor="profilePublic" aria-label="Rendre le profil public" className="relative cursor-pointer flex-none">
              <input
                id="profilePublic"
                type="checkbox"
                checked={profilePublic}
                onChange={(e) => setProfilePublic(e.target.checked)}
                className="sr-only peer"
              />
              <div className="w-11 h-6 rounded-full border border-border bg-foreground/10 peer-checked:bg-accent peer-checked:border-accent transition-all" />
              <div className="absolute top-0.75 left-0.75 w-4.5 h-4.5 rounded-full bg-white shadow-sm transition-transform peer-checked:translate-x-5" />
            </label>
          </div>

          <div className="flex justify-end gap-3 pt-2 max-sm:flex-col-reverse">
            <ButtonSecondary onClick={() => navigate('/profile/me')} disabled={submitting}>
              Annuler
            </ButtonSecondary>
            <ButtonPrimary type="submit" disabled={submitting}>
              {submitting ? 'Enregistrement...' : 'Enregistrer'}
            </ButtonPrimary>
          </div>
        </form>
      </div>
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
    </SectionWrapper>
  )
}

/**
 * SCRUM-169 — inline helper text under the username field. Mirrors the
 * status state from {@link UsernameStatus}.
 */
function UsernameStatusRow({ status, id }: Readonly<{ status: UsernameStatus; id: string }>) {
  const variant = USERNAME_STATUS_VARIANTS[status]
  const Icon = variant.icon
  return (
    <p
      id={id}
      className={[
        'flex items-center gap-1.5 text-xs mt-1.5',
        variant.className,
      ].join(' ')}
      aria-live="polite"
    >
      {Icon && (
        <Icon
          className={['w-3.5 h-3.5 shrink-0', variant.iconClassName ?? ''].join(' ')}
          aria-hidden="true"
        />
      )}
      <span>{variant.text}</span>
    </p>
  )
}

const USERNAME_HELPER = `${USERNAME_MIN_LENGTH} à ${USERNAME_MAX_LENGTH} caractères, lettres minuscules, chiffres, "." "_" "-".`

const USERNAME_STATUS_VARIANTS: Record<UsernameStatus, {
  text: string
  icon: typeof Check | null
  className: string
  iconClassName?: string
}> = {
  idle: {
    text: USERNAME_HELPER,
    icon: null,
    className: 'text-foreground/40',
  },
  unchanged: {
    text: USERNAME_HELPER,
    icon: null,
    className: 'text-foreground/40',
  },
  invalid: {
    text: 'Format invalide. ' + USERNAME_HELPER,
    icon: X,
    className: 'text-error font-medium',
  },
  reserved: {
    text: 'Ce nom est réservé.',
    icon: X,
    className: 'text-error font-medium',
  },
  checking: {
    text: 'Vérification…',
    icon: Loader2,
    className: 'text-foreground/40',
    iconClassName: 'animate-spin',
  },
  available: {
    text: 'Disponible.',
    icon: Check,
    className: 'text-emerald-500 font-medium',
  },
  taken: {
    text: 'Déjà pris.',
    icon: X,
    className: 'text-error font-medium',
  },
  error: {
    text: 'Vérification impossible. Réessayez.',
    icon: X,
    className: 'text-error font-medium',
  },
}

/** Surface a string error to FormField only when the status is a real failure. */
function usernameStatusErrorText(status: UsernameStatus): string | undefined {
  return status === 'invalid' || status === 'reserved' || status === 'taken'
    ? USERNAME_STATUS_VARIANTS[status].text
    : undefined
}
