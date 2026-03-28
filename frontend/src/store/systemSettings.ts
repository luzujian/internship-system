import { defineStore } from 'pinia'
import { ref, Ref } from 'vue'
import { systemSettingsApi } from '../api/systemSettings'
import { useAuthStore } from './auth'

interface SecuritySettings {
  minPasswordLength?: number
  passwordComplexity?: string | string[]
}

interface BasicSettings {
  systemName?: string
}

interface SystemSettings {
  security?: SecuritySettings
  basic?: BasicSettings
}

interface SystemSettingsResponse {
  code: number
  data: SystemSettings
  message?: string
}

export const useSystemSettingsStore = defineStore('systemSettings', () => {
  const minPasswordLength = ref<number>(6)
  const passwordComplexity = ref<string[]>(['lowercase', 'number'])
  const systemName = ref<string>('deepintern')
  const loading = ref<boolean>(false)

  const loadSettings = async (): Promise<void> => {
    if (loading.value) return

    const authStore = useAuthStore()
    if (!authStore.isAuthenticated) {
      console.log('[systemSettings] 用户未登录，跳过加载系统设置')
      return
    }

    if (authStore.role !== 'ROLE_ADMIN') {
      console.log('[systemSettings] 非管理员角色，跳过加载系统设置')
      return
    }

    loading.value = true
    try {
      const response = await systemSettingsApi.getSettings() as unknown as SystemSettingsResponse
      if (response.code === 200 && response.data) {
        const settings = response.data
        if (settings.basic) {
          if (settings.basic.systemName) {
            systemName.value = settings.basic.systemName
          }
        }
        if (settings.security) {
          if (settings.security.minPasswordLength) {
            minPasswordLength.value = settings.security.minPasswordLength
          }
          if (settings.security.passwordComplexity) {
            const complexity = settings.security.passwordComplexity
            if (typeof complexity === 'string') {
              passwordComplexity.value = complexity.split(',')
            } else if (Array.isArray(complexity)) {
              passwordComplexity.value = complexity
            }
          }
        }
      }
    } catch (error) {
      console.error('加载系统设置失败:', error)
    } finally {
      loading.value = false
    }
  }

  const updateSecuritySettings = (security: SecuritySettings): void => {
    if (security.minPasswordLength) {
      minPasswordLength.value = security.minPasswordLength
    }
    if (security.passwordComplexity) {
      const complexity = security.passwordComplexity
      if (typeof complexity === 'string') {
        passwordComplexity.value = complexity.split(',')
      } else if (Array.isArray(complexity)) {
        passwordComplexity.value = complexity
      }
    }
  }

  const getPasswordPlaceholder = (): string => {
    const complexityText = passwordComplexity.value.map(item => {
      const map: Record<string, string> = {
        'lowercase': '小写字母',
        'uppercase': '大写字母',
        'number': '数字',
        'special': '特殊字符'
      }
      return map[item] || item
    }).join('、')
    return `请输入新密码（${minPasswordLength.value}-20 位，必须包含${complexityText}）`
  }

  return {
    minPasswordLength,
    passwordComplexity,
    systemName,
    loading,
    loadSettings,
    updateSecuritySettings,
    getPasswordPlaceholder
  }
})
