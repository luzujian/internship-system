import { ref } from 'vue'
import request from '@/utils/request'
import { isReflectionRoute, isReflectionPermission } from './reflectionBusiness.mjs'

export const reflectionEnabled = ref(false)
let loadedAt = 0
let pending: Promise<void> | null = null

export function isBusinessRouteEnabled(path: string): boolean {
  return reflectionEnabled.value || !isReflectionRoute(path)
}

export function isBusinessPermissionEnabled(permission: unknown): boolean {
  return reflectionEnabled.value || !isReflectionPermission(permission)
}

export async function loadBusinessFeatures(force = false): Promise<void> {
  if (pending) return pending
  if (!force && loadedAt && Date.now() - loadedAt < 30_000) return
  pending = (async () => {
    try {
      const response = await request.get<{ reflectionEnabled?: boolean }>('/home/business-features')
      reflectionEnabled.value = response.code === 200 && response.data?.reflectionEnabled === true
    } catch {
      reflectionEnabled.value = false
    } finally {
      loadedAt = Date.now()
    }
  })()
  try { await pending } finally { pending = null }
}
