import { type KeyboardEvent as ReactKeyboardEvent, useCallback, useEffect, useRef, useState } from 'react'
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
  isValidUsername,
  RESERVED_USERNAMES,
  STUDY_LEVELS,
  USERNAME_MAX_LENGTH,
  USERNAME_MIN_LENGTH,
  USERNAME_PATTERN,
  type User,
} from '@/types/user'
import FormField, { Input, Select, Textarea } from '@/components/utils/FormField'
import { ButtonPrimary, ButtonSecondary } from '@/components/utils/Buttons'
import { ImagePlus, Trash2, X } from 'lucide-react'
import UserAvatar from '@/components/user/UserAvatar'
import UserBanner from '@/components/user/UserBanner'
import { useToast } from '@/hooks/useToast'
import ImageCropper from '@/components/utils/ImageCropper'
import { useImageCropFlow } from '@/hooks/useImageCropFlow'

const MAX_BIO_LENGTH = 500
const MAX_PHOTO_SIZE = 2 * 1024 * 1024
const MAX_BANNER_SIZE = 5 * 1024 * 1024
const AVATAR_ASPECT = 1
const PROFILE_BANNER_ASPECT = 3

interface FormErrors {
  name?: string
  bio?: string
  photo?: string
  banner?: string
  username?: string
}

const USERNAME_DEBOUNCE_MS = 400

type UsernameStatus = 'idle' | 'checking' | 'available' | 'taken' | 'invalid' | 'reserved'

export default function ProfileEditPage() {
  const { user, updateUser } = useAuth()
  const { showToast } = useToast()

  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [username, setUsername] = useState('')
  const [usernameStatus, setUsernameStatus] = useState<UsernameStatus>('idle')
  const usernameDebounce = useRef<ReturnType<typeof setTimeout> | null>(null)
  const usernameCheckSeq = useRef(0)
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

  useEffect(() => {
    if (user) {
      setName(user.displayName ?? '')
      setUsername(user.username ?? '')
      setFaculty(user.faculty ?? '')
      setStudyLevel(user.studyLevel ?? '')
      setBio(user.bio ?? '')
      setInterests(user.interests ?? [])
      setProfilePublic(user.profilePublic ?? false)
      if (user.avatarUrl) setPhotoPreview(user.avatarUrl)
      if (user.bannerUrl) setBannerPreview(user.bannerUrl)
    }
  }, [user])

  useEffect(() => {
    if (usernameDebounce.current) clearTimeout(usernameDebounce.current)
    const trimmed = username.trim().toLowerCase()
    if (!trimmed || trimmed === user?.username) {
      setUsernameStatus('idle')
      return
    }
    if ((RESERVED_USERNAMES as readonly string[]).includes(trimmed)) {
      setUsernameStatus('reserved')
      return
    }
    if (!USERNAME_PATTERN.test(trimmed)) {
      setUsernameStatus('invalid')
      return
    }
    setUsernameStatus('checking')
    const seq = ++usernameCheckSeq.current
    usernameDebounce.current = setTimeout(() => {
      checkUsernameAvailable(trimmed)
        .then((available) => {
          if (seq !== usernameCheckSeq.current) return
          setUsernameStatus(available ? 'available' : 'taken')
        })
        .catch(() => {
          if (seq !== usernameCheckSeq.current) return
          setUsernameStatus('idle')
        })
    }, USERNAME_DEBOUNCE_MS)
    return () => {
      if (usernameDebounce.current) clearTimeout(usernameDebounce.current)
    }
  }, [username, user?.username])

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

    const trimmedUsername = username.trim().toLowerCase()
    if (trimmedUsername !== (user?.username ?? '')) {
      if (!trimmedUsername) {
        newErrors.username = "Le nom d'utilisateur est requis."
      } else if ((RESERVED_USERNAMES as readonly string[]).includes(trimmedUsername)) {
        newErrors.username = "Ce nom d'utilisateur est réservé."
      } else if (!isValidUsername(trimmedUsername)) {
        newErrors.username = `Doit faire ${USERNAME_MIN_LENGTH}-${USERNAME_MAX_LENGTH} caractères : a-z, 0-9, ._-`
      } else if (usernameStatus === 'taken') {
        newErrors.username = "Ce nom d'utilisateur est déjà pris."
      }
    }
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  async function handleSubmit(e: React.SyntheticEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!validate()) return
    setSubmitting(true)
    try {
      if (photoFile) await uploadPhoto(photoFile)
      if (bannerFile) await uploadBanner(bannerFile)
      else if (bannerDeleted) await deleteBanner()

      const trimmedUsername = username.trim().toLowerCase()
      if (trimmedUsername && trimmedUsername !== user?.username) {
        try {
          await updateUsername(trimmedUsername)
        } catch (error) {
          if (error instanceof AxiosError && error.response?.status === 409) {
            setErrors((prev) => ({ ...prev, username: "Ce nom d'utilisateur est déjà pris." }))
            setUsernameStatus('taken')
            setSubmitting(false)
            return
          }
          throw error
        }
      }

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

  const usernameHint: Record<UsernameStatus, string | null> = {
    idle: null,
    checking: 'Vérification…',
    available: 'Disponible',
    taken: "Ce nom d'utilisateur est déjà pris.",
    invalid: `Doit faire ${USERNAME_MIN_LENGTH}-${USERNAME_MAX_LENGTH} caractères : a-z, 0-9, ._-`,
    reserved: "Ce nom d'utilisateur est réservé.",
  }
  const usernameHintColor: Record<UsernameStatus, string> = {
    idle: 'text-foreground/40',
    checking: 'text-foreground/40',
    available: 'text-emerald-500',
    taken: 'text-error',
    invalid: 'text-error',
    reserved: 'text-error',
  }

  const previewUser = user ? { ...user, displayName: name, avatarUrl: photoPreview ?? user.avatarUrl, bannerUrl: bannerPreview } : null

  return (
    <div className="max-w-2xl mx-auto">
      <div className="bg-background border border-border rounded-3xl p-8 max-sm:p-5">
        <h1 className="text-3xl font-bold text-foreground mb-8">Modifier mon profil</h1>

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

          <FormField label="Nom d'utilisateur" htmlFor="username" required error={errors.username}>
            <Input
              id="username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value.toLowerCase())}
              error={errors.username}
              placeholder="ex. jean.dupont"
              minLength={USERNAME_MIN_LENGTH}
              maxLength={USERNAME_MAX_LENGTH}
              autoCapitalize="none"
              autoComplete="off"
              spellCheck={false}
            />
            {!errors.username && usernameHint[usernameStatus] && (
              <p className={`text-xs mt-1 ${usernameHintColor[usernameStatus]}`}>
                {usernameHint[usernameStatus]}
              </p>
            )}
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
    </div>
  )
}
