import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useAuthStore } from '@/store/auth'

/** A single visible-page polling loop. Requests and responses belong to the current login. */
export function useOnlinePolling<T>(load: (signal: AbortSignal) => Promise<T>) {
  const auth = useAuthStore()
  const data = shallowRef<T>()
  const loading = ref(false)
  const error = ref('')
  const mounted = ref(false)
  const visible = ref(document.visibilityState === 'visible')
  const identity = computed(() => JSON.stringify([auth.role, auth.user?.username, auth.sessionId]))
  const enabled = computed(() => mounted.value && visible.value && auth.isAuthenticated && !auth.isLoggingOut
    && !!auth.token && auth.role === 'ROLE_ADMIN' && auth.hasPermission('log:login:view'))
  let timer: ReturnType<typeof setInterval> | undefined
  let controller: AbortController | undefined
  let version = 0

  const cancel = () => {
    version++
    controller?.abort()
    controller = undefined
    loading.value = false
  }
  const refresh = async () => {
    if (!enabled.value) return
    cancel()
    const current = version, owner = identity.value
    controller = new AbortController()
    loading.value = true
    try {
      const value = await load(controller.signal)
      if (current !== version || owner !== identity.value || !enabled.value) return
      data.value = value
      error.value = ''
      if (!timer) timer = setInterval(() => { if (!loading.value) void refresh() }, 15_000)
    } catch (failure: any) {
      if (current !== version || owner !== identity.value || !enabled.value) return
      error.value = '在线状态暂时无法获取'
      if (failure?.response?.status === 403) {
        data.value = undefined
        clearInterval(timer)
        timer = undefined
        error.value = '暂无在线用户查看权限'
      }
    } finally {
      if (current === version) loading.value = false
    }
  }
  const visibilityChanged = () => { visible.value = document.visibilityState === 'visible' }
  watch([enabled, identity], ([active, owner], previous) => {
    clearInterval(timer)
    timer = undefined
    cancel()
    if (previous && owner !== previous[1]) {
      data.value = undefined
      error.value = ''
    }
    if (!auth.isAuthenticated || auth.isLoggingOut || auth.role !== 'ROLE_ADMIN' || !auth.hasPermission('log:login:view')) data.value = undefined
    if (active) {
      void refresh()
      timer = setInterval(() => { if (!loading.value) void refresh() }, 15_000)
    }
  }, { flush: 'sync' })
  onMounted(() => {
    document.addEventListener('visibilitychange', visibilityChanged)
    mounted.value = true
  })
  onUnmounted(() => {
    mounted.value = false
    clearInterval(timer)
    cancel()
    document.removeEventListener('visibilitychange', visibilityChanged)
  })
  return { data, loading, error, refresh }
}
