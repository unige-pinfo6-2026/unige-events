
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
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
  updateUsername: vi.fn(),
  checkUsernameAvailable: vi.fn(),
  uploadPhoto: vi.fn(),
  uploadBanner: vi.fn(),
  deleteBanner: vi.fn(),
}))

vi.mock('@/components/utils/ImageCropper', () => ({
  default: ({ onCropComplete, onCancel, src, aspect, circular }: { onCropComplete: (b: Blob) => void; onCancel: () => void; src: string; aspect: number; circular: boolean }) => (
    <div data-testid="image-cropper-mock" data-src={src} data-aspect={String(aspect)} data-circular={String(circular)}>
      <button type="button" onClick={() => onCropComplete(new Blob(['cropped'], { type: 'image/png' }))}>
        Mock Recadrer
      </button>
      <button type="button" onClick={onCancel}>
        Mock Annuler
      </button>
    </div>
  ),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

let originalFileReader: typeof FileReader
let originalCreateObjectURL: typeof URL.createObjectURL

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

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockGetMe = getMe as ReturnType<typeof vi.fn>
const mockUpdateProfile = updateProfile as ReturnType<typeof vi.fn>
const mockUpdateUsername = updateUsername as ReturnType<typeof vi.fn>
const mockCheckUsernameAvailable = checkUsernameAvailable as ReturnType<typeof vi.fn>
const mockUploadPhoto = uploadPhoto as ReturnType<typeof vi.fn>
const mockUploadBanner = uploadBanner as ReturnType<typeof vi.fn>
const mockDeleteBanner = deleteBanner as ReturnType<typeof vi.fn>

const mockUser = {
  id: '123',
  auth0Id: 'auth0|123',
  email: 'test@example.com',
  username: 'test.user',
  displayName: 'Test User',
  bio: 'My bio',
  interests: ['coding'],
  profilePublic: true,
  createdAt: '2024-01-01',
}

beforeEach(() => {
  originalFileReader = globalThis.FileReader
  originalCreateObjectURL = globalThis.URL.createObjectURL
})

afterEach(() => {
  globalThis.FileReader = originalFileReader
  globalThis.URL.createObjectURL = originalCreateObjectURL
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

    const cropper = screen.getByTestId('image-cropper-mock')
    expect(cropper).toBeTruthy()
    expect(cropper.getAttribute('data-aspect')).toBe('1')
    expect(cropper.getAttribute('data-circular')).toBe('true')

    fireEvent.click(screen.getByText('Mock Recadrer'))
    expect(await screen.findByAltText('Test User')).toBeTruthy()
  })

  it('calls uploadPhoto with cropped file when photo is selected on submit', async () => {
    mockFileReader('data:image/png;base64,abc')
    mockUseAuth.mockReturnValue({ user: mockUser, updateUser: vi.fn() })
    mockUpdateProfile.mockResolvedValue({})
    mockGetMe.mockResolvedValue(mockUser)
    mockUploadPhoto.mockResolvedValue({ avatarUrl: 'https://example.com/new.jpg' })
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#photo-input')!
    const file = new File(['img'], 'photo.jpg', { type: 'image/jpeg' })

    await act(async () => {
      fireEvent.change(input, { target: { files: [file] } })
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    fireEvent.click(screen.getByText('Mock Recadrer'))
    fireEvent.click(screen.getByText('Enregistrer'))
    await screen.findByText('Profil mis à jour avec succès.')
    const uploaded = mockUploadPhoto.mock.calls[0][0] as File
    expect(uploaded).toBeInstanceOf(File)
    expect(uploaded.name).toBe('photo.jpg')
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

  it('shows banner preview after confirming a valid banner file in the cropper', async () => {
    mockFileReader('data:image/png;base64,abc')
    mockUseAuth.mockReturnValue({ user: mockUser })
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:banner-preview-url')
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#banner-input')!
    const file = new File(['img'], 'banner.jpg', { type: 'image/jpeg' })

    await act(async () => {
      fireEvent.change(input, { target: { files: [file] } })
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    fireEvent.click(screen.getByText('Mock Recadrer'))
    await waitFor(() => {
      const banner = document.querySelector<HTMLImageElement>('img[src*="banner-preview-url"]')
      expect(banner).toBeTruthy()
    })
  })

  it('calls uploadBanner on submit when banner file is cropped', async () => {
    mockFileReader('data:image/png;base64,abc')
    mockUseAuth.mockReturnValue({ user: mockUser, updateUser: vi.fn() })
    mockUpdateProfile.mockResolvedValue({})
    mockGetMe.mockResolvedValue(mockUser)
    mockUploadBanner.mockResolvedValue({ bannerUrl: 'https://example.com/new-banner.jpg' })
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:banner-preview-url')
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#banner-input')!
    const file = new File(['img'], 'banner.jpg', { type: 'image/jpeg' })

    await act(async () => {
      fireEvent.change(input, { target: { files: [file] } })
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    fireEvent.click(screen.getByText('Mock Recadrer'))
    fireEvent.click(screen.getByText('Enregistrer'))
    await screen.findByText('Profil mis à jour avec succès.')
    const uploaded = mockUploadBanner.mock.calls[0][0] as File
    expect(uploaded).toBeInstanceOf(File)
    expect(uploaded.name).toBe('banner.jpg')
  })

  it('cancel cropper for photo does not push photoFile', async () => {
    mockFileReader('data:image/png;base64,abc')
    mockUseAuth.mockReturnValue({ user: mockUser, updateUser: vi.fn() })
    mockUpdateProfile.mockResolvedValue({})
    mockGetMe.mockResolvedValue(mockUser)
    renderProfileEditPage()
    await screen.findByDisplayValue('Test User')
    const input = document.querySelector<HTMLInputElement>('#photo-input')!

    await act(async () => {
      fireEvent.change(input, { target: { files: [new File(['x'], 'p.png', { type: 'image/png' })] } })
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    fireEvent.click(screen.getByText('Mock Annuler'))
    expect(screen.queryByTestId('image-cropper-mock')).toBeNull()
    fireEvent.click(screen.getByText('Enregistrer'))
    await screen.findByText('Profil mis à jour avec succès.')
    expect(mockUploadPhoto).not.toHaveBeenCalled()
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

    const cropper = screen.getByTestId('image-cropper-mock')
    expect(cropper).toBeTruthy()
    expect(cropper.getAttribute('data-aspect')).toBe('3')
    expect(cropper.getAttribute('data-circular')).toBe('false')
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
      id: '123', auth0Id: 'auth0|123', email: 'test@example.com', username: 'test.user',
      displayName: undefined, bio: undefined, interests: undefined,
      profilePublic: undefined, createdAt: '2024-01-01',
    }
    mockUseAuth.mockReturnValue({ user: sparseUser as never })
    renderProfileEditPage()
    const nameInput = await screen.findByPlaceholderText('Votre nom complet')
    expect((nameInput as HTMLInputElement).value).toBe('')
  })

  // ─── SCRUM-169 — username field ──────────────────────────────────────

  describe('username field', () => {
    beforeEach(() => {
      mockUseAuth.mockReturnValue({ user: mockUser, updateUser: vi.fn() })
    })

    it('pre-fills the username input from user.username', async () => {
      renderProfileEditPage()
      const input = await screen.findByPlaceholderText('jean.dupont')
      expect((input as HTMLInputElement).value).toBe('test.user')
    })

    it('forces lowercase on keystroke', async () => {
      renderProfileEditPage()
      const input = await screen.findByPlaceholderText('jean.dupont')
      fireEvent.change(input, { target: { value: 'NEW.Handle' } })
      expect((input as HTMLInputElement).value).toBe('new.handle')
    })

    it('shows "Ce nom est réservé." when typing a blocklist entry', async () => {
      renderProfileEditPage()
      const input = await screen.findByPlaceholderText('jean.dupont')
      fireEvent.change(input, { target: { value: 'admin' } })
      expect(await screen.findByText('Ce nom est réservé.')).toBeTruthy()
      expect(mockCheckUsernameAvailable).not.toHaveBeenCalled()
    })

    it('shows the format error when pattern does not match', async () => {
      renderProfileEditPage()
      const input = await screen.findByPlaceholderText('jean.dupont')
      fireEvent.change(input, { target: { value: 'ab' } }) // too short (the lowercase coerce makes this match-able otherwise)
      expect(await screen.findByText(/Format invalide/)).toBeTruthy()
      expect(mockCheckUsernameAvailable).not.toHaveBeenCalled()
    })

    it('debounces the live availability check and shows "Disponible." on 404', async () => {
      mockCheckUsernameAvailable.mockResolvedValue(true)
      renderProfileEditPage()
      const input = await screen.findByPlaceholderText('jean.dupont')

      fireEvent.change(input, { target: { value: 'new.handle' } })
      // The debounce of 400 ms is real here — `waitFor` polls until the
      // network call lands (default 1 s budget is plenty).
      await waitFor(() => {
        expect(mockCheckUsernameAvailable).toHaveBeenCalledWith('new.handle')
      })
      expect(await screen.findByText('Disponible.')).toBeTruthy()
    })

    it('shows "Déjà pris." when HEAD returns 200', async () => {
      mockCheckUsernameAvailable.mockResolvedValue(false)
      renderProfileEditPage()
      const input = await screen.findByPlaceholderText('jean.dupont')

      fireEvent.change(input, { target: { value: 'pris.handle' } })
      await waitFor(() => {
        expect(mockCheckUsernameAvailable).toHaveBeenCalledWith('pris.handle')
      })
      expect(await screen.findByText('Déjà pris.')).toBeTruthy()
    })

    it('skips the API call when username is unchanged on submit', async () => {
      mockUpdateProfile.mockResolvedValue(mockUser)
      mockGetMe.mockResolvedValue(mockUser)
      renderProfileEditPage()
      await screen.findByDisplayValue('Test User')

      fireEvent.submit(document.querySelector('form')!)

      await waitFor(() => {
        expect(mockUpdateProfile).toHaveBeenCalled()
      })
      expect(mockUpdateUsername).not.toHaveBeenCalled()
    })

    it('submits updateUsername before updateProfile when username changed', async () => {
      const fresh = { ...mockUser, username: 'new.handle' }
      mockUpdateUsername.mockResolvedValue(fresh)
      mockUpdateProfile.mockResolvedValue(fresh)
      mockGetMe.mockResolvedValue(fresh)
      renderProfileEditPage()

      const input = await screen.findByPlaceholderText('jean.dupont')
      fireEvent.change(input, { target: { value: 'new.handle' } })
      fireEvent.submit(document.querySelector('form')!)

      await waitFor(() => {
        expect(mockUpdateUsername).toHaveBeenCalledWith('new.handle')
      })
      expect(mockUpdateProfile).toHaveBeenCalled()
    })

    it('stops on 409 from updateUsername — no updateProfile call, no navigate', async () => {
      const conflict = Object.assign(new Error('Conflict'), {
        isAxiosError: true,
        response: { status: 409 },
        name: 'AxiosError',
      })
      // Make it pass the `instanceof AxiosError` check too.
      const { AxiosError } = await import('axios')
      Object.setPrototypeOf(conflict, AxiosError.prototype)

      mockUpdateUsername.mockRejectedValue(conflict)
      renderProfileEditPage()

      const input = await screen.findByPlaceholderText('jean.dupont')
      fireEvent.change(input, { target: { value: 'taken.handle' } })
      fireEvent.submit(document.querySelector('form')!)

      await waitFor(() => {
        expect(mockUpdateUsername).toHaveBeenCalledWith('taken.handle')
      })
      expect(mockUpdateProfile).not.toHaveBeenCalled()
      expect(mockNavigate).not.toHaveBeenCalled()
    })
  })
})
