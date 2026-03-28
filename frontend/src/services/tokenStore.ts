export function getToken(): string | null {
  return localStorage.getItem("access_token")
}

export function setToken(token: string | null): void {
  if (token === null) {
    localStorage.removeItem("access_token")
  } else {
    localStorage.setItem("access_token", token)
  }
}
