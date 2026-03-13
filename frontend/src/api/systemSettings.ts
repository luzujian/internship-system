import request from '../utils/request'
import { useAuthStore } from '../store/auth'

interface SystemSettingsResponse {
  code: number
  data: unknown
  message?: string
}

export const systemSettingsApi = {
  getSettings: (): Promise<SystemSettingsResponse> => {
    const authStore = useAuthStore()
    const role = authStore.role

    if (!role || role !== 'ROLE_ADMIN') {
      console.warn('[systemSettingsApi] 非管理员角色跳过系统设置加载')
      return Promise.reject(new Error('非管理员角色无法访问系统设置'))
    }

    return request({
      url: '/admin/settings',
      method: 'get'
    }) as Promise<SystemSettingsResponse>
  },

  updateSettings: (data: unknown): Promise<SystemSettingsResponse> => {
    return request({
      url: '/admin/settings',
      method: 'put',
      data
    }) as Promise<SystemSettingsResponse>
  },

  testEmail: (): Promise<SystemSettingsResponse> => {
    return request({
      url: '/admin/settings/test-email',
      method: 'post'
    }) as Promise<SystemSettingsResponse>
  }
}

export default systemSettingsApi
