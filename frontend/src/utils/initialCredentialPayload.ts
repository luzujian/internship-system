export interface InitialCredential {
  username: string
  password: string
}

export const readInitialCredentials = (data: unknown): InitialCredential[] => {
  if (data === null || typeof data !== 'object') return []
  const value = (data as { initialCredentials?: unknown }).initialCredentials
  if (!Array.isArray(value)) return []
  return value.filter((entry): entry is InitialCredential => entry !== null
    && typeof entry === 'object'
    && typeof entry.username === 'string' && entry.username.length > 0
    && typeof entry.password === 'string' && entry.password.length > 0)
    .map(({ username, password }) => ({ username, password }))
}
