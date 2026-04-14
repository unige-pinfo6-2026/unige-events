import { type ChangeEvent, type KeyboardEvent as ReactKeyboardEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { deleteBanner, getMe, updateProfile, uploadBanner, uploadPhoto } from '@/services/userService'
import { FACULTIES } from '@/types/faculty'
import { STUDY_LEVELS, type User } from '@/types/user'
import FormField, { Input, Select, Textarea } from '@/components/utils/FormField'
import { ButtonPrimary, ButtonSecondary } from '@/components/utils/Buttons'
import { ImagePlus, Trash2, X } from 'lucide-react'
import UserAvatar from '@/components/user/UserAvatar'
import { useToast } from '@/hooks/useToast'

const MAX_BIO_LENGTH = 500
const MAX_PHOTO_SIZE = 2 * 1024 * 1024
const MAX_BANNER_SIZE = 5 * 1024 * 1024

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
      setFaculty(user.faculty ?? '')
      setStudyLevel(user.studyLevel ?? '')
      setBio(user.bio ?? '')
      setInterests(user.interests ?? [])
      setProfilePublic(user.profilePublic ?? false)
      if (user.avatarUrl) setPhotoPreview(user.avatarUrl)
      if (user.bannerUrl) setBannerPreview(user.bannerUrl)
    }
  }, [user])

  function handlePhotoChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setErrors((prev) => ({ ...prev, photo: 'Le fichier doit être une image.' }))
      return
    }
    if (file.size > MAX_PHOTO_SIZE) {
      setErrors((prev) => ({ ...prev, photo: 'La photo ne doit pas dépasser 2 Mo.' }))
      return
    }
    setErrors((prev) => ({ ...prev, photo: undefined }))
    setPhotoFile(file)
    setPhotoPreview(URL.createObjectURL(file))
  }

  function handleBannerChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setErrors((prev) => ({ ...prev, banner: 'Le fichier doit être une image.' }))
      return
    }
    if (file.size > MAX_BANNER_SIZE) {
      setErrors((prev) => ({ ...prev, banner: 'La bannière ne doit pas dépasser 5 Mo.' }))
      return
    }
    setErrors((prev) => ({ ...prev, banner: undefined }))
    setBannerFile(file)
    setBannerPreview(URL.createObjectURL(file))
    setBannerDeleted(false)
  }

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
    setSubmitting(true)
    try {
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

  const previewUser = user ? { ...user, displayName: name, avatarUrl: photoPreview ?? user.avatarUrl } : null

  return (
    <div className="max-w-2xl mx-auto">
      <div className="bg-background border border-border rounded-3xl p-8 max-sm:p-5">
        <h1 className="text-3xl font-bold text-foreground mb-8">Modifier mon profil</h1>

        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">

          {/* Banner */}
          <div className="flex flex-col gap-3">
            <div
              className="relative h-32 rounded-2xl overflow-hidden bg-cover bg-center"
              style={
                bannerPreview
                  ? { backgroundImage: `url(${bannerPreview})` }
                  : undefined
              }
            >
              {!bannerPreview && (
                <div className="absolute inset-0 bg-linear-to-br from-accent/20 via-pink-600/15 to-purple-600/20" />
              )}
            </div>
            <div className="flex gap-2 flex-wrap">
              <label
                htmlFor="banner-input"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-border text-sm font-semibold text-foreground/60 cursor-pointer hover:border-accent/50 hover:text-foreground transition-all"
              >
                <ImagePlus className="w-4 h-4" />
                Changer la bannière
              </label>
              <input id="banner-input" type="file" accept="image/*" onChange={handleBannerChange} className="hidden" />
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
            <UserAvatar user={previewUser} size='xl' />
            <div className="flex flex-col gap-1">
              <label
                htmlFor="photo-input"
                className="px-4 py-2 rounded-xl border border-border text-sm font-semibold text-foreground/60 cursor-pointer hover:border-accent/50 hover:text-foreground transition-all w-fit"
              >
                Changer la photo
              </label>
              <input id="photo-input" type="file" accept="image/*" onChange={handlePhotoChange} className="hidden" />
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
    </div>
  )
}
