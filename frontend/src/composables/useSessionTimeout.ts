import { onMounted, onBeforeUnmount, watch } from 'vue'
import { useAuthStore } from '@/store/auth'
import request, { handleSessionTimeout, type CustomAxiosRequestConfig } from '@/utils/request'
import { createSessionActivityMonitor } from '@/utils/sessionActivity.mjs'

interface SessionState { lastActivityAt: number; timeoutMinutes: number; serverTime: number }

export function useSessionTimeout() {
  const auth = useAuthStore()
  let monitor: ReturnType<typeof createSessionActivityMonitor> | undefined
  let unwatch: (() => void) | undefined
  const events = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart', 'input']
  const activity = (event: Event) => {
    if (event.isTrusted && document.visibilityState === 'visible') monitor?.activity()
  }
  const check = () => { if (document.visibilityState === 'visible') void monitor?.check() }
  const storage = (event: StorageEvent) => {
    if (event.key !== 'deepintern.auth.session-expired' || !event.newValue) return
    try {
      if (auth.sessionId && JSON.parse(event.newValue).sid === auth.sessionId) handleSessionTimeout(false)
    } catch { /* Ignore unrelated or malformed storage events. */ }
  }

  onMounted(() => {
    unwatch = watch(() => auth.isAuthenticated ? `${auth.role}:${auth.user?.username}:${auth.sessionId || 'legacy'}` : '', owner => {
      monitor?.stop(); monitor = undefined
      if (!owner) return
      monitor = createSessionActivityMonitor({
        async request(activity: boolean) {
          const config = { skipErrorMessage: true } as CustomAxiosRequestConfig
          const result = activity
            ? await request.post<SessionState>('/auth/session/activity', {}, config)
            : await request.get<SessionState>('/auth/session', config)
          return result.data
        },
        expire: handleSessionTimeout
      })
    }, { immediate: true, flush: 'sync' })
    events.forEach(name => window.addEventListener(name, activity, { capture: true, passive: true }))
    window.addEventListener('focus', check)
    document.addEventListener('visibilitychange', check)
    window.addEventListener('storage', storage)
  })
  onBeforeUnmount(() => {
    unwatch?.(); monitor?.stop()
    events.forEach(name => window.removeEventListener(name, activity, true))
    window.removeEventListener('focus', check)
    document.removeEventListener('visibilitychange', check)
    window.removeEventListener('storage', storage)
  })
}
