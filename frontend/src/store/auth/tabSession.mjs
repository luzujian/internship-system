// Each tab keeps its own authenticated identity. Shared role caches are only a
// migration source for tabs which have never established a session.
const KEY = 'deepintern.auth.tab-session'
export function readTabSession(storage = sessionStorage) {
  try {
    const value = JSON.parse(storage.getItem(KEY) || 'null')
    return value?.token && value?.role && value?.user?.username ? value : null
  } catch { return null }
}
export function writeTabSession(state, storage = sessionStorage) {
  if (!state.token || !state.role || !state.user?.username) return
  storage.setItem(KEY, JSON.stringify({ token: state.token, role: state.role,
    user: state.user, refreshToken: state.allUsers?.[state.role]?.refreshToken || null,
    adminUser: state.user.originalAdminUsername ? state.allUsers?.ROLE_ADMIN : undefined }))
}
export function clearTabSession(storage = sessionStorage) { storage.removeItem(KEY) }
