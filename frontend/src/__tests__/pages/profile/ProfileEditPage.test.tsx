// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ProfileEditPage from '@/pages/profile/ProfileEditPage'
import { ToastProvider } from '@/contexts/ToastContext'
import ToastsWrapper from '@/components/utils/Toast'

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

vi.mock('@/services/userService', () => ({
  getMe: vi.fn(),
  updateProfile: vi.fn(),
  uploadPhoto: vi.fn(),
  uploadBanner: vi.fn(),
  deleteBanner: vi.fn(),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

import { useAuth } from '@/hooks/useAuth'
import { deleteBanner, getMe, updateProfile, uploadBanner, uploadPhoto } from '@/services/userService'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockGetMe = getMe as ReturnType<typeof vi.fn>
const mockUpdateProfile = updateProfile as ReturnType<typeof vi.fn>
const mockUploadPhoto = uploadPhoto as ReturnType<typeof vi.fn>
const mockUploadBanner = uploadBanner as ReturnType<typeof vi.fn>
const mockDeleteBanner = deleteBanner as ReturnType<typeof vi.fn>

const mockUser = {
  id: '123',
  auth0Id: 'auth0|123',
  email: 'test@example.com',
  displayName: 'Test User',
  bio: 'My bio',
  interests: ['coding'],
  profilePublic: true,
  createdAt: '2024-01-01',
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderProfileEditPage() {
  return render(
    <ToastProvider>
      <ToastsWrapper />
      <MemoryRouter>
        <ProfileEditPage />
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('ProfileEditPage', () => {
  it('renders form with user data', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    expect(await screen.findByDisplayValue('Test User')).toBeTruthy()
    expect(screen.getByDisplayValue('My bio')).toBeTruthy()
  })

  it('renders existing interests', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    expect(await screen.findByText('coding')).toBeTruthy()
  })

  it('shows validation error when name is empty', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    const nameInput = await screen.findByPlaceholderText('Votre nom complet')
    fireEvent.change(nameInput, { target: { value: '' } })
    fireEvent.click(screen.getByText('Enregistrer'))
    expect(await screen.findByText('Le nom est requis.')).toBeTruthy()
  })

  it('submits form and shows success toast', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, updateUser: vi.fn() })
    mockUpdateProfile.mockResolvedValue({})
    mockGetMe.mockResolvedValue(mockUser)
    renderProfileEditPage()
    fireEvent.click(await screen.findByText('Enregistrer'))
    expect(await screen.findByText('Profil mis à jour avec succès.')).toBeTruthy()
  })

  it('shows error toast on submit failure', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUpdateProfile.mockRejectedValue(new Error('Server error'))
    renderProfileEditPage()
    fireEvent.click(await screen.findByText('Enregistrer'))
    expect(await screen.findByText('Une erreur est survenue. Veuillez réessayer.')).toBeTruthy()
  })

  it('adds interest on Enter key', async () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, interests: [] } })
    renderProfileEditPage()
    const input = await screen.findByPlaceholderText("Tapez un intérêt et appuyez sur Entrée")
    fireEvent.change(input, { target: { value: 'Jazz' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(await screen.findByText('Jazz')).toBeTruthy()
  })

  it('removes interest on × click', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    const removeBtn = await screen.findByLabelText('Supprimer coding')
    fireEvent.click(removeBtn)
    await waitFor(() => expect(screen.queryByText('coding')).toBeNull())
  })

  it('navigates to profile on Cancel click', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    fireEvent.click(await screen.findByText('Annuler'))
    expect(mockNavigate).toHaveBeenCalledWith('/profile/me')
  })

  it('renders form without user (no crash)', () => {
    mockUseAuth.mockReturnValue({ user: null })
    expect(() => renderProfileEditPage()).not.toThrow()
  })

  it('shows photo preview when user has avatarUrl', async () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, avatarUrl: 'https://example.com/photo.jpg' } })
    renderProfileEditPage()
    const img = await screen.findByAltText('Test User')
    expect((img as HTMLImageElement).src).toContain('example.com/photo.jpg')
  })

  it('rejects non-image file', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    const input = await screen.findByDisplayValue('Test User').then(() =>
      document.querySelector<HTMLInputElement>('#photo-input')!,
    )
    const file = new File(['data'], 'document.pdf', { type: 'application/pdf' })
    fireEvent.change(input, { target: { files: [file] } })
    expect(await screen.findByText('Le fichier doit être une image.')).toBeTruthy()
  })

  it('rejects image file larger than 2MB', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    const input = document.querySelector<HTMLInputElement>('#photo-input')!
    const largeFile = new File(['x'.repeat(3 * 1024 * 1024)], 'big.jpg', { type: 'image/jpeg' })
    fireEvent.change(input, { target: { files: [largeFile] } })
    expect(await screen.findByText('La photo ne doit pas dépasser 2 Mo.')).toBeTruthy()
  })

  it('accepts valid image file and shows preview', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#photo-input')!
    const file = new File(['img'], 'photo.jpg', { type: 'image/jpeg' })
    fireEvent.change(input, { target: { files: [file] } })
    expect(await screen.findByAltText('Test User')).toBeTruthy()
  })

  it('calls uploadPhoto when photo file is selected on submit', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, updateUser: vi.fn() })
    mockUpdateProfile.mockResolvedValue({})
    mockGetMe.mockResolvedValue(mockUser)
    mockUploadPhoto.mockResolvedValue({ avatarUrl: 'https://example.com/new.jpg' })
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#photo-input')!
    const file = new File(['img'], 'photo.jpg', { type: 'image/jpeg' })
    fireEvent.change(input, { target: { files: [file] } })
    fireEvent.click(screen.getByText('Enregistrer'))
    await screen.findByText('Profil mis à jour avec succès.')
    expect(mockUploadPhoto).toHaveBeenCalledWith(file)
  })

  it('shows bio length validation error when bio exceeds 500 chars', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    const textarea = await screen.findByPlaceholderText('Parlez un peu de vous...')
    fireEvent.change(textarea, { target: { value: 'a'.repeat(501) } })
    fireEvent.click(screen.getByText('Enregistrer'))
    expect(await screen.findByText(/ne doit pas dépasser 500 caractères/)).toBeTruthy()
  })

  it('changes faculty select', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, updateUser: vi.fn() })
    mockUpdateProfile.mockResolvedValue({})
    mockGetMe.mockResolvedValue(mockUser)
    renderProfileEditPage()
    const select = await screen.findByRole('combobox', { name: /Faculté/i })
    fireEvent.change(select, { target: { value: 'SCIENCES' } })
    fireEvent.click(screen.getByText('Enregistrer'))
    await screen.findByText('Profil mis à jour avec succès.')
    expect(mockUpdateProfile).toHaveBeenCalledWith(expect.objectContaining({ faculty: 'SCIENCES' }))
  })

  it('changes studyLevel select', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, updateUser: vi.fn() })
    mockUpdateProfile.mockResolvedValue({})
    mockGetMe.mockResolvedValue(mockUser)
    renderProfileEditPage()
    const select = await screen.findByRole('combobox', { name: /Niveau/i })
    fireEvent.change(select, { target: { value: 'MASTER' } })
    fireEvent.click(screen.getByText('Enregistrer'))
    await screen.findByText('Profil mis à jour avec succès.')
    expect(mockUpdateProfile).toHaveBeenCalledWith(expect.objectContaining({ studyLevel: 'MASTER' }))
  })

  it('toggles profilePublic checkbox', async () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, profilePublic: false }, updateUser: vi.fn() })
    mockUpdateProfile.mockResolvedValue({})
    mockGetMe.mockResolvedValue(mockUser)
    renderProfileEditPage()
    const checkbox = await screen.findByRole('checkbox')
    fireEvent.click(checkbox)
    fireEvent.click(screen.getByText('Enregistrer'))
    await screen.findByText('Profil mis à jour avec succès.')
    expect(mockUpdateProfile).toHaveBeenCalledWith(expect.objectContaining({ profilePublic: true }))
  })

  it('does not add duplicate interest', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    const input = await screen.findByPlaceholderText("Tapez un intérêt et appuyez sur Entrée")
    fireEvent.change(input, { target: { value: 'coding' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    // 'coding' already exists, should not duplicate
    expect(screen.getAllByText('coding')).toHaveLength(1)
  })

  it('ignores non-Enter keys in interest input', async () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, interests: [] } })
    renderProfileEditPage()
    const input = await screen.findByPlaceholderText("Tapez un intérêt et appuyez sur Entrée")
    fireEvent.change(input, { target: { value: 'test' } })
    fireEvent.keyDown(input, { key: 'a' })
    expect(screen.queryByText('test')).toBeNull()
  })

  it('shows banner preview when user has bannerUrl', async () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, bannerUrl: 'https://example.com/banner.jpg' } })
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const banner = document.querySelector<HTMLImageElement>('img[src*="banner.jpg"]')
    expect(banner).toBeTruthy()
    expect(banner!.src).toContain('banner.jpg')
  })

  it('shows gradient placeholder when user has no bannerUrl', async () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, bannerUrl: null } })
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    expect(document.querySelector('[style*="banner"]')).toBeNull()
  })

  it('rejects non-image banner file', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#banner-input')!
    const file = new File(['data'], 'document.pdf', { type: 'application/pdf' })
    fireEvent.change(input, { target: { files: [file] } })
    expect(await screen.findByText('Le fichier doit être une image.')).toBeTruthy()
  })

  it('rejects banner file larger than 5MB', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#banner-input')!
    const largeFile = new File(['x'.repeat(6 * 1024 * 1024)], 'big.jpg', { type: 'image/jpeg' })
    fireEvent.change(input, { target: { files: [largeFile] } })
    expect(await screen.findByText('La bannière ne doit pas dépasser 5 Mo.')).toBeTruthy()
  })

  it('shows banner preview after selecting a valid banner file', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:banner-preview-url')
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#banner-input')!
    const file = new File(['img'], 'banner.jpg', { type: 'image/jpeg' })
    fireEvent.change(input, { target: { files: [file] } })
    await waitFor(() => {
      const banner = document.querySelector<HTMLImageElement>('img[src*="banner-preview-url"]')
      expect(banner).toBeTruthy()
    })
  })

  it('calls uploadBanner on submit when banner file is selected', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, updateUser: vi.fn() })
    mockUpdateProfile.mockResolvedValue({})
    mockGetMe.mockResolvedValue(mockUser)
    mockUploadBanner.mockResolvedValue({ bannerUrl: 'https://example.com/new-banner.jpg' })
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:banner-preview-url')
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#banner-input')!
    const file = new File(['img'], 'banner.jpg', { type: 'image/jpeg' })
    fireEvent.change(input, { target: { files: [file] } })
    fireEvent.click(screen.getByText('Enregistrer'))
    await screen.findByText('Profil mis à jour avec succès.')
    expect(mockUploadBanner).toHaveBeenCalledWith(file)
  })

  it('calls deleteBanner on submit when banner is deleted', async () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, bannerUrl: 'https://example.com/banner.jpg' }, updateUser: vi.fn() })
    mockUpdateProfile.mockResolvedValue({})
    mockGetMe.mockResolvedValue(mockUser)
    mockDeleteBanner.mockResolvedValue({ bannerUrl: null })
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    fireEvent.click(screen.getByText('Supprimer la bannière'))
    fireEvent.click(screen.getByText('Enregistrer'))
    await screen.findByText('Profil mis à jour avec succès.')
    expect(mockDeleteBanner).toHaveBeenCalled()
  })

  it('hides delete button after banner is deleted', async () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, bannerUrl: 'https://example.com/banner.jpg' } })
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    expect(screen.getByText('Supprimer la bannière')).toBeTruthy()
    fireEvent.click(screen.getByText('Supprimer la bannière'))
    await waitFor(() => expect(screen.queryByText('Supprimer la bannière')).toBeNull())
  })

  it('does nothing when photo input receives no file', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#photo-input')!
    fireEvent.change(input, { target: { files: [] } })
    expect(screen.queryByText('Le fichier doit être une image.')).toBeNull()
  })

  it('does nothing when banner input receives no file', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#banner-input')!
    fireEvent.change(input, { target: { files: [] } })
    expect(screen.queryByText('Le fichier doit être une image.')).toBeNull()
  })

  it('uses empty fallbacks when user fields are undefined', async () => {
    const sparseUser = {
      id: '123', auth0Id: 'auth0|123', email: 'test@example.com',
      displayName: undefined, bio: undefined, interests: undefined,
      profilePublic: undefined, createdAt: '2024-01-01',
    }
    mockUseAuth.mockReturnValue({ user: sparseUser as never })
    renderProfileEditPage()
    const nameInput = await screen.findByPlaceholderText('Votre nom complet')
    expect((nameInput as HTMLInputElement).value).toBe('')
  })
})
