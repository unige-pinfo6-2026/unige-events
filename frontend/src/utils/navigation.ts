let _navigate: ((path: string) => void) | null = null

export function setGlobalNavigate(fn: (path: string) => void): void {
  _navigate = fn
}

export function globalNavigate(path: string): void {
  if (_navigate) {
    _navigate(path)
  } else {
    window.location.href = path
  }
}
