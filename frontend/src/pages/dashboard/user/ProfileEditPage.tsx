import { type ChangeEvent, type KeyboardEvent as ReactKeyboardEvent, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { getMe, updateProfile, uploadPhoto } from '@/services/userService'
import { FACULTIES } from '@/types/faculty'
import { STUDY_LEVELS, type User } from '@/types/user'

const MAX_BIO_LENGTH = 500
const MAX_PHOTO_SIZE = 2 * 1024 * 1024

interface FormErrors {
  name?: string
  bio?: string
  photo?: string
}

function ProfileEditPage() {
  const { user, updateUser } = useAuth()
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [faculty, setFaculty] = useState<string>('')
  const [studyLevel, setStudyLevel] = useState<string>('')
  const [bio, setBio] = useState('')
  const [interests, setInterests] = useState<string[]>([])
  const [interestInput, setInterestInput] = useState('')
  const [profilePublic, setProfilePublic] = useState(false)
  const [photoFile, setPhotoFile] = useState<File | null>(null)
  const [photoPreview, setPhotoPreview] = useState<string | null>(null)

  const [errors, setErrors] = useState<FormErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (user) {
      setName(user.displayName ?? '')
      setFaculty(user.faculty ?? '')
      setStudyLevel(user.studyLevel ?? '')
      setBio(user.bio ?? '')
      setInterests(user.interests ?? [])
      setProfilePublic(user.profilePublic ?? false)
      if (user.avatarUrl) setPhotoPreview(user.avatarUrl)
    }
  }, [user])

  useEffect(() => {
    return () => {
      if (toastTimerRef.current) clearTimeout(toastTimerRef.current)
    }
  }, [])

  function showToast(type: 'success' | 'error', message: string) {
    setToast({ type, message })
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current)
    toastTimerRef.current = setTimeout(() => setToast(null), 4000)
  }

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

  function handleInterestKeyDown(e: ReactKeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') {
      e.preventDefault()
      const value = interestInput.trim()
      if (value && !interests.includes(value)) {
        setInterests((prev) => [...prev, value])
      }
      setInterestInput('')
    }
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
      setTimeout(() => navigate('/profile/me'), 1500)
    } catch {
      showToast('error', 'Une erreur est survenue. Veuillez réessayer.')
    } finally {
      setSubmitting(false)
    }
  }

  const inputClass = (error?: string) =>
    `w-full px-3 py-2.5 border rounded-lg text-[0.9375rem] text-[var(--input-color)] bg-[var(--input-bg)] transition-colors focus:outline-none focus:border-[#CF0063] focus:ring-2 focus:ring-[var(--input-focus-shadow)] ${
      error ? 'border-red-400' : 'border-[var(--input-border)]'
    }`

  return (
    <div className="max-w-[640px] mx-auto">
      <div className="bg-[var(--card-bg)] border border-[var(--card-border)] rounded-2xl p-8">
        <h1 className="text-2xl font-bold text-[var(--text-primary)] mb-8">
          Modifier mon profil
        </h1>

        <form onSubmit={handleSubmit} noValidate>

          {/* Photo */}
          <div className="mb-5">
            <label className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5" htmlFor="photo-input">
              Photo de profil
            </label>
            <div className="flex items-center gap-4">
              {photoPreview ? (
                <img src={photoPreview} alt="Aperçu" className="w-18 h-18 rounded-full object-cover border-2 border-[var(--divider)]" />
              ) : (
                <div className="w-18 h-18 rounded-full bg-[var(--input-bg)] border-2 border-dashed border-[var(--input-border)] flex items-center justify-center text-3xl">
                  📷
                </div>
              )}
              <label
                htmlFor="photo-input"
                className="px-4 py-2 bg-[var(--btn-secondary-bg)] border border-[var(--input-border)] rounded-lg text-sm text-[var(--text-secondary)] cursor-pointer hover:bg-[var(--btn-secondary-hover)] transition-colors"
              >
                Choisir une photo
              </label>
              <input id="photo-input" type="file" accept="image/*" onChange={handlePhotoChange} className="hidden" />
            </div>
            {errors.photo && <span className="block text-[0.8125rem] text-red-400 mt-1">{errors.photo}</span>}
          </div>

          {/* Name */}
          <div className="mb-5">
            <label htmlFor="name" className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5">
              Nom <span className="text-red-400">*</span>
            </label>
            <input
              id="name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className={inputClass(errors.name)}
              placeholder="Votre nom complet"
            />
            {errors.name && <span className="block text-[0.8125rem] text-red-400 mt-1">{errors.name}</span>}
          </div>

          {/* Faculty */}
          <div className="mb-5">
            <label htmlFor="faculty" className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5">
              Faculté
            </label>
            <select
              id="faculty"
              value={faculty}
              onChange={(e) => setFaculty(e.target.value)}
              className={inputClass()}
            >
              <option value="">— Sélectionner une faculté —</option>
              {Object.entries(FACULTIES).map(([id, faculty]) => (
                <option key={id} value={id}>{faculty.name}</option>
              ))}
            </select>
          </div>

          {/* Study level */}
          <div className="mb-5">
            <label htmlFor="studyLevel" className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5">
              Niveau d&apos;études
            </label>
            <select
              id="studyLevel"
              value={studyLevel}
              onChange={(e) => setStudyLevel(e.target.value)}
              className={inputClass()}
            >
              <option value="">— Sélectionner un niveau —</option>
              {Object.entries(STUDY_LEVELS).map(([id, studyLevel]) => (
                <option key={id} value={id}>{studyLevel.name}</option>
              ))}
            </select>
          </div>

          {/* Bio */}
          <div className="mb-5">
            <label htmlFor="bio" className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5">
              Biographie
            </label>
            <textarea
              id="bio"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              className={`${inputClass(errors.bio)} resize-y min-h-[100px]`}
              placeholder="Parlez un peu de vous…"
              rows={4}
              maxLength={MAX_BIO_LENGTH + 1}
            />
            <div className={`text-[0.8rem] text-right mt-1 ${bio.length > MAX_BIO_LENGTH ? 'text-red-400 font-semibold' : 'text-[var(--text-muted)]'}`}>
              {bio.length} / {MAX_BIO_LENGTH}
            </div>
            {errors.bio && <span className="block text-[0.8125rem] text-red-400 mt-1">{errors.bio}</span>}
          </div>

          {/* Interests */}
          <div className="mb-5">
            <label htmlFor="interests" className="block text-sm font-semibold text-[var(--text-secondary)] mb-1.5">
              Centres d&apos;intérêt
            </label>
            <input
              id="interests"
              type="text"
              value={interestInput}
              onChange={(e) => setInterestInput(e.target.value)}
              onKeyDown={handleInterestKeyDown}
              className={inputClass()}
              placeholder="Tapez un intérêt et appuyez sur Entrée"
            />
            {interests.length > 0 && (
              <div className="flex flex-wrap gap-2 mt-2.5">
                {interests.map((interest) => (
                  <span
                    key={interest}
                    className="inline-flex items-center gap-1.5 bg-[var(--tag-bg)] text-[var(--tag-color)] border border-[var(--tag-border)] rounded-full px-2.5 py-1 text-[0.8125rem] font-medium"
                  >
                    {interest}
                    <button
                      type="button"
                      onClick={() => removeInterest(interest)}
                      aria-label={`Supprimer ${interest}`}
                      className="text-[var(--tag-color)] opacity-70 hover:opacity-100 text-base leading-none"
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>

          {/* Toggle */}
          <div className="py-3 border-t border-b border-[var(--divider)] mb-5">
            <label className="flex items-center justify-between cursor-pointer gap-4 text-[0.9375rem] text-[var(--text-secondary)]" htmlFor="isProfilePublic">
              <span>Rendre mon profil visible publiquement</span>
              <div className="relative flex-none">
                <input
                  id="isProfilePublic"
                  type="checkbox"
                  checked={profilePublic}
                  onChange={(e) => setProfilePublic(e.target.checked)}
                  className="sr-only peer"
                />
                <div className="w-[42px] h-6 rounded-full bg-[var(--card-border-subtle)] peer-checked:bg-[#CF0063] transition-colors" />
                <div className="absolute top-[3px] left-[3px] w-[18px] h-[18px] rounded-full bg-white shadow-sm transition-transform peer-checked:translate-x-[18px]" />
              </div>
            </label>
          </div>

          {/* Actions */}
          <div className="flex justify-end gap-3 mt-7">
            <button
              type="button"
              onClick={() => navigate('/profile/me')}
              disabled={submitting}
              className="px-5 py-2.5 rounded-lg text-[0.9375rem] font-semibold bg-[var(--btn-secondary-bg)] text-[var(--btn-secondary-color)] hover:bg-[var(--btn-secondary-hover)] disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              Annuler
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-5 py-2.5 rounded-lg text-[0.9375rem] font-semibold bg-[#CF0063] text-white hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-opacity"
            >
              {submitting ? 'Enregistrement…' : 'Enregistrer'}
            </button>
          </div>
        </form>
      </div>

      {toast && (
        <output
          className={`fixed bottom-6 right-6 px-5 py-3.5 rounded-xl text-sm font-medium shadow-lg z-50 animate-[toast-in_0.25s_ease] border ${
            toast.type === 'success'
              ? 'bg-green-50 text-green-800 border-green-200'
              : 'bg-red-50 text-red-700 border-red-200'
          }`}
        >
          {toast.message}
        </output>
      )}
    </div>
  )
}

export default ProfileEditPage