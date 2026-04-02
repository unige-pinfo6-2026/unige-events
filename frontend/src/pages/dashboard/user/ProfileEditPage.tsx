import { type ChangeEvent, type KeyboardEvent as ReactKeyboardEvent, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { getMe, updateProfile, uploadPhoto } from '@/services/userService'
import { Faculty, StudyLevel } from '@/types'
import type { User } from '@/types'
import './ProfileEditPage.css'

const MAX_BIO_LENGTH = 500
const MAX_PHOTO_SIZE = 2 * 1024 * 1024 // 2MB

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
      if (user.avatarUrl) {
        setPhotoPreview(user.avatarUrl)
      }
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
      setErrors((prev) => ({
        ...prev,
        photo: 'La photo ne doit pas dépasser 2 Mo.',
      }))
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

    if (!name.trim()) {
      newErrors.name = 'Le nom est requis.'
    }
    if (bio.length > MAX_BIO_LENGTH) {
      newErrors.bio = `La biographie ne doit pas dépasser ${MAX_BIO_LENGTH} caractères.`
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  async function handleSubmit(e: React.SyntheticEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!validate()) return

    setSubmitting(true)

    try {
      if (photoFile) {
        await uploadPhoto(photoFile)
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
      setTimeout(() => navigate('/profile/me'), 1500)
    } catch {
      showToast('error', 'Une erreur est survenue. Veuillez réessayer.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="profile-edit-page">
      <div className="profile-edit-card">
        <h1 className="profile-edit-title">Modifier mon profil</h1>

        <form onSubmit={handleSubmit} noValidate>
          {/* Photo */}
          <div className="form-group">
            <label className="form-label" htmlFor="photo-input">Photo de profil</label>
            <div className="photo-preview-wrapper">
              {photoPreview ? (
                <img src={photoPreview} alt="Aperçu" className="photo-preview-img" />
              ) : (
                <div className="photo-preview-placeholder">📷</div>
              )}
              <label className="photo-upload-btn" htmlFor="photo-input">
                Choisir une photo
              </label>
              <input
                id="photo-input"
                type="file"
                accept="image/*"
                onChange={handlePhotoChange}
                className="photo-input-hidden"
              />
            </div>
            {errors.photo && <span className="form-error">{errors.photo}</span>}
          </div>

          {/* Name */}
          <div className="form-group">
            <label htmlFor="name" className="form-label">
              Nom <span className="required">*</span>
            </label>
            <input
              id="name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className={`form-input ${errors.name ? 'form-input--error' : ''}`}
              placeholder="Votre nom complet"
            />
            {errors.name && <span className="form-error">{errors.name}</span>}
          </div>

          {/* Faculty */}
          <div className="form-group">
            <label htmlFor="faculty" className="form-label">
              Faculté
            </label>
            <select
              id="faculty"
              value={faculty}
              onChange={(e) => setFaculty(e.target.value)}
              className="form-select"
            >
              <option value="">— Sélectionner une faculté —</option>
              {Object.values(Faculty).map((f) => (
                <option key={f} value={f}>
                  {f}
                </option>
              ))}
            </select>
          </div>

          {/* Study level */}
          <div className="form-group">
            <label htmlFor="studyLevel" className="form-label">
              Niveau d&apos;études
            </label>
            <select
              id="studyLevel"
              value={studyLevel}
              onChange={(e) => setStudyLevel(e.target.value)}
              className="form-select"
            >
              <option value="">— Sélectionner un niveau —</option>
              {Object.values(StudyLevel).map((l) => (
                <option key={l} value={l}>
                  {l}
                </option>
              ))}
            </select>
          </div>

          {/* Bio */}
          <div className="form-group">
            <label htmlFor="bio" className="form-label">
              Biographie
            </label>
            <textarea
              id="bio"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              className={`form-textarea ${errors.bio ? 'form-input--error' : ''}`}
              placeholder="Parlez un peu de vous…"
              rows={4}
              maxLength={MAX_BIO_LENGTH + 1}
            />
            <div className={`bio-counter ${bio.length > MAX_BIO_LENGTH ? 'bio-counter--over' : ''}`}>
              {bio.length} / {MAX_BIO_LENGTH}
            </div>
            {errors.bio && <span className="form-error">{errors.bio}</span>}
          </div>

          {/* Interests */}
          <div className="form-group">
            <label htmlFor="interests" className="form-label">
              Centres d&apos;intérêt
            </label>
            <input
              id="interests"
              type="text"
              value={interestInput}
              onChange={(e) => setInterestInput(e.target.value)}
              onKeyDown={handleInterestKeyDown}
              className="form-input"
              placeholder="Tapez un intérêt et appuyez sur Entrée"
            />
            {interests.length > 0 && (
              <div className="interests-tags">
                {interests.map((interest) => (
                  <span key={interest} className="interest-tag">
                    {interest}
                    <button
                      type="button"
                      className="interest-tag-remove"
                      onClick={() => removeInterest(interest)}
                      aria-label={`Supprimer ${interest}`}
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>

          {/* Public toggle */}
          <div className="form-group form-group--toggle">
            <label className="toggle-label" htmlFor="isProfilePublic">
              <span>Rendre mon profil visible publiquement</span>
              <input
                id="isProfilePublic"
                type="checkbox"
                checked={profilePublic}
                onChange={(e) => setProfilePublic(e.target.checked)}
                className="toggle-input"
              />
              <span className="toggle-switch" />
            </label>
          </div>

          {/* Actions */}
          <div className="form-actions">
            <button
              type="button"
              className="btn btn--secondary"
              onClick={() => navigate('/profile/me')}
              disabled={submitting}
            >
              Annuler
            </button>
            <button type="submit" className="btn btn--primary" disabled={submitting}>
              {submitting ? 'Enregistrement…' : 'Enregistrer'}
            </button>
          </div>
        </form>
      </div>

      {toast && (
        <output className={`toast toast--${toast.type}`}>
          {toast.message}
        </output>
      )}
    </div>
  )
}

export default ProfileEditPage
