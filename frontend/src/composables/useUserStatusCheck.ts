import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/store/auth'
import request from '@/utils/request'
import type { Ref } from 'vue'

export function useUserStatusCheck(interval = 30000) {
  const authStore = useAuthStore()
  let checkTimer: ReturnType<typeof setInterval> | null = null
  const isChecking: Ref<boolean> = ref(false)

  const checkUserStatus = async (): Promise<void> => {
    if (isChecking.value) {
      return
    }

    try {
      isChecking.value = true
      const response = await request.get('/auth/check-status', { skipTokenRefresh: true } as Record<string, unknown>)

      if (response.data && (response.data as { code?: number }).code === 200) {
        const status = (response.data as { data?: { status?: number } }).data?.status
        if (status === 0) {
          ElMessage.error('您的账号已被禁用，请联系管理员')
          authStore.logout()
          router.push('/login')
        }
      }
    } catch (error) {
      if ((error as { response?: { status?: number; data?: { message?: string } } }).response?.status === 403) {
        const message = (error as { response?: { data?: { message?: string } } }).response?.data?.message || ''
        if (message.includes('被禁用') || message.includes('账号已被禁用')) {
          ElMessage.error('您的账号已被禁用，请联系管理员')
          authStore.logout()
          router.push('/login')
        }
      }
    } finally {
      isChecking.value = false
    }
  }

  const startChecking = (): void => {
    if (checkTimer) {
      clearInterval(checkTimer)
    }

    checkTimer = setInterval(() => {
      checkUserStatus()
    }, interval)
  }

  const stopChecking = (): void => {
    if (checkTimer) {
      clearInterval(checkTimer)
      checkTimer = null
    }
  }

  onMounted(() => {
    startChecking()
  })

  onUnmounted(() => {
    stopChecking()
  })

  return {
    checkUserStatus,
    startChecking,
    stopChecking,
    isChecking
  }
}
