import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig, AxiosHeaders } from 'axios'
import { ElMessage } from 'element-plus'
import { getUploadErrorMessage } from './uploadErrorMessage'
import router from '@/router'
import { useAuthStore } from '@/store/auth'
import { clearTabSession } from '@/store/auth/tabSession.mjs'

interface Config {
  BASE_URL: string
  TIMEOUT: number
  RETRY_COUNT: number
  RETRY_DELAY_BASE: number
  UNAUTHORIZED_STATUS: number
  FORBIDDEN_STATUS: number
  BEARER_PREFIX: string
  PUBLIC_ENDPOINTS: string[]
}

interface StorageKeys {
  CURRENT_ROLE: string
  ACCESS_TOKEN: string
  REFRESH_TOKEN: string
  ROLE_PREFIXES: Record<string, string>
}

interface ErrorMessages {
  NETWORK_ERROR: string
  UNAUTHORIZED: string
  FORBIDDEN: string
  ACCOUNT_DISABLED: string
  SERVER_ERROR: string
  REQUEST_ERROR: string
  TOKEN_REFRESH_FAILED: string
}

interface RefreshTokenResponse {
  data: {
    code: number
    data: {
      accessToken: string
      refreshToken: string
    }
  }
}

const CONFIG: Config = {
  BASE_URL: import.meta.env.VITE_API_BASE_URL || '/api',
  TIMEOUT: 60000,
  RETRY_COUNT: 2,
  RETRY_DELAY_BASE: 1000,
  UNAUTHORIZED_STATUS: 401,
  FORBIDDEN_STATUS: 403,
  BEARER_PREFIX: 'Bearer ',
  PUBLIC_ENDPOINTS: [
    '/auth/login',
    '/auth/forgot-password',
    '/auth/reset-password',
    '/auth/refresh-token',
    '/auth/password-rules',
    '/company/register',
    '/company/check-username',
    '/company/check-status',
    '/company/update/',
    '/company/recall',
    '/teacher/login',
    '/admin/ai-model/public/enabled'
  ]
}

const STORAGE_KEYS: StorageKeys = {
  CURRENT_ROLE: 'current_role',
  ACCESS_TOKEN: 'accessToken',
  REFRESH_TOKEN: 'refreshToken',
  ROLE_PREFIXES: {
    ROLE_STUDENT: 'student_',
    ROLE_TEACHER: 'teacher_',
    ROLE_TEACHER_COLLEGE: 'teacher_',
    ROLE_TEACHER_DEPARTMENT: 'teacher_',
    ROLE_TEACHER_COUNSELOR: 'teacher_',
    ROLE_ADMIN: 'admin_',
    ROLE_COMPANY: 'company_'
  }
}

const ERROR_MESSAGES: ErrorMessages = {
  NETWORK_ERROR: '网络请求失败，请检查网络连接',
  UNAUTHORIZED: '认证信息已过期，请重新登录',
  FORBIDDEN: '您没有访问权限，请联系管理员分配相应权限',
  ACCOUNT_DISABLED: '您的账号已被禁用，请联系管理员',
  SERVER_ERROR: '服务器错误，请稍后再试',
  REQUEST_ERROR: '请求配置错误',
  TOKEN_REFRESH_FAILED: '刷新令牌失败，请重新登录'
}

function getRoleFromPath(path = window.location.pathname): string {
  if (!path) {
    return 'ROLE_ADMIN'
  }

  if (path.includes('/admin')) {
    return 'ROLE_ADMIN'
  } else if (path.includes('/teacher')) {
    // 对于教师路径，返回 ROLE_TEACHER 作为基础角色
    // 具体的教师子角色（ROLE_TEACHER_COLLEGE 等）由 auth store 管理
    return 'ROLE_TEACHER'
  } else if (path.includes('/student')) {
    return 'ROLE_STUDENT'
  } else if (path.includes('/company')) {
    return 'ROLE_COMPANY'
  }

  return 'ROLE_ADMIN'
}

function getRoleStoragePrefix(role: string): string {
  if (!role) {
    console.error('[request] 角色参数为空')
    return STORAGE_KEYS.ROLE_PREFIXES.ROLE_ADMIN
  }

  const prefix = STORAGE_KEYS.ROLE_PREFIXES[role]
  if (prefix) {
    return prefix
  }

  console.error(`[request] 无效的角色参数：${role}`)
  return STORAGE_KEYS.ROLE_PREFIXES.ROLE_ADMIN
}

export function getAccessToken(): string | null {
  // The in-memory store belongs to this tab; shared localStorage may belong to
  // another tab/account, even when both accounts have the same teacher role.
  return useAuthStore().token || null
}

function cleanupTokens(): void {
  clearTabSession()
  console.log('[request] 清理认证令牌信息')

  const keysToRemove = [
    STORAGE_KEYS.ACCESS_TOKEN,
    STORAGE_KEYS.REFRESH_TOKEN,
    STORAGE_KEYS.CURRENT_ROLE,
    'expires_at'
  ]

  keysToRemove.forEach(key => {
    localStorage.removeItem(key)
  })

  Object.values(STORAGE_KEYS.ROLE_PREFIXES).forEach(prefix => {
    const roles = ['ROLE_STUDENT', 'ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR', 'ROLE_ADMIN', 'ROLE_COMPANY']
    roles.forEach(role => {
      localStorage.removeItem(`${prefix}${STORAGE_KEYS.ACCESS_TOKEN}_${role}`)
      localStorage.removeItem(`${prefix}token_${role}`)
      localStorage.removeItem(`${prefix}${STORAGE_KEYS.REFRESH_TOKEN}_${role}`)
      localStorage.removeItem(`${prefix}refreshError_${role}`)
      localStorage.removeItem(`${prefix}expires_at_${role}`)
      localStorage.removeItem(`${prefix}isReadOnly_${role}`)
      localStorage.removeItem(`${prefix}originalAdminUsername_${role}`)
    })

    localStorage.removeItem(`${prefix}${STORAGE_KEYS.ACCESS_TOKEN}`)
    localStorage.removeItem(`${prefix}token`)
    localStorage.removeItem(`${prefix}${STORAGE_KEYS.REFRESH_TOKEN}`)
    localStorage.removeItem(`${prefix}refreshError`)
    localStorage.removeItem(`${prefix}expires_at`)
  })
}

function handleLogout(): void {
  const authStore = useAuthStore()

  if (authStore.user?.isReadOnly) {
    ElMessage.warning('您当前处于只读模式，正在返回管理员端...')
    const originalAdminUsername = authStore.user?.originalAdminUsername
    if (originalAdminUsername) {
      localStorage.setItem('current_role', 'ROLE_ADMIN')
      localStorage.setItem('admin_accessToken_ROLE_ADMIN', localStorage.getItem('admin_accessToken_ROLE_ADMIN') || '')
      localStorage.setItem('admin_refreshToken_ROLE_ADMIN', localStorage.getItem('admin_refreshToken_ROLE_ADMIN') || '')
      localStorage.setItem('admin_role_ROLE_ADMIN', 'ROLE_ADMIN')
      localStorage.setItem('admin_userId_ROLE_ADMIN', localStorage.getItem('admin_userId_ROLE_ADMIN') || '')
      localStorage.setItem('admin_username_ROLE_ADMIN', originalAdminUsername)

      authStore.role = 'ROLE_ADMIN'
      authStore.user = {
        id: localStorage.getItem('admin_userId_ROLE_ADMIN') || '',
        username: originalAdminUsername,
        name: originalAdminUsername
      }
      authStore.token = localStorage.getItem('admin_accessToken_ROLE_ADMIN') || ''
      authStore.isAuthenticated = true
      authStore.user.isReadOnly = false
      authStore.user.originalAdminUsername = null

      router.push('/admin/dashboard')
      return
    }
  }

  ElMessage.warning('会话已超时，请重新登录')
  // 清除 in-memory authStore 状态，避免路由守卫把用户踢回 dashboard
  authStore.user = null
  authStore.token = null
  authStore.role = null
  authStore.isAuthenticated = false
  cleanupTokens()
  router.push('/login')
}

/**
 * 后端统一业务包形态。响应拦截器已将 `response.data` 直接返回，
 * 调用方拿到的即此包体而非完整 AxiosResponse，类型在此如实声明。
 */
export interface ApiResult<T = unknown> {
  code: number
  message?: string
  msg?: string
  data: T
}

/**
 * 解包后的实例类型：与 AxiosInstance 同一运行时对象，
 * 仅把各请求方法的返回声明为拦截器实际返回的业务包体。
 */
export interface UnwrappedAxiosInstance extends Omit<AxiosInstance,
  'request' | 'get' | 'delete' | 'head' | 'options' | 'post' | 'put' | 'patch'> {
  <T = unknown>(config: AxiosRequestConfig): Promise<ApiResult<T>>
  <T = unknown>(url: string, config?: AxiosRequestConfig): Promise<ApiResult<T>>
  request<T = unknown>(config: AxiosRequestConfig): Promise<ApiResult<T>>
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<ApiResult<T>>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<ApiResult<T>>
  head<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<ApiResult<T>>
  options<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<ApiResult<T>>
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResult<T>>
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResult<T>>
  patch<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResult<T>>
}

const request = axios.create({
  baseURL: CONFIG.BASE_URL,
  timeout: CONFIG.TIMEOUT,
  headers: {
    'Content-Type': 'application/json'
  }
}) as UnwrappedAxiosInstance

interface CustomAxiosRequestConfig extends InternalAxiosRequestConfig {
  skipReadOnlyCheck?: boolean
  skipTokenRefresh?: boolean
  skipBackendLogout?: boolean
  skipErrorMessage?: boolean
  _retry?: boolean
  _authOwner?: string
}

function requestOwner(): string {
  const auth = useAuthStore()
  return JSON.stringify([auth.role, auth.user?.id, auth.user?.username, auth.user?.isReadOnly === true])
}

request.interceptors.request.use(
  (config: CustomAxiosRequestConfig) => {
    if (!config.headers) {
      config.headers = {} as AxiosHeaders
    }

    const authStore = useAuthStore()

    if (authStore.user?.isReadOnly) {
      const method = config.method?.toUpperCase()
      const writeMethods = ['POST', 'PUT', 'DELETE', 'PATCH']

      if (writeMethods.includes(method || '')) {
        const skipReadOnlyCheck = config.skipReadOnlyCheck || false

        if (!skipReadOnlyCheck) {
          const isRefreshTokenRequest = config.url?.includes('/auth/refresh-token')

          if (!isRefreshTokenRequest) {
            console.warn('[request] 只读模式下禁止写操作:', method, config.url)
            ElMessage.warning('您当前处于只读模式，无法进行修改操作。如需修改，请返回管理员端。')
            return Promise.reject(new Error('只读模式下禁止写操作'))
          }
        }
      }
    }

    // 检查是否为公开接口（支持完整路径或相对路径匹配）
    const isPublicEndpoint = CONFIG.PUBLIC_ENDPOINTS.some(endpoint => {
      const url = config.url || ''
      // 匹配完整路径或去掉 /api 前缀的路径
      return url.includes(endpoint) || url.replace('/api', '').includes(endpoint)
    })

    // 如果是获取AI模型列表的公开接口，直接跳过token添加
    const isAIModelPublicEndpoint = config.url?.includes('/admin/ai-model/public/')

    if (!isPublicEndpoint && !isAIModelPublicEndpoint) {
      const owner = requestOwner()
      if (config._authOwner && config._authOwner !== owner) throw new axios.CanceledError('登录身份已变化，请重新操作。')
      config._authOwner = owner
      const accessToken = getAccessToken()

      if (accessToken) {
        config.headers.Authorization = `${CONFIG.BEARER_PREFIX}${accessToken}`
      } else {
        console.warn('[request] 未找到访问令牌')
      }
    }

    config.headers['X-Request-Time'] = Date.now().toString()

    return config
  },
  (error: unknown) => {
    console.error('[request] 请求错误:', error)
    return Promise.reject(error)
  }
)

let isRefreshing = false
let refreshSubscribers: ((newToken: string | null) => void)[] = []

function addSubscriber(callback: (newToken: string | null) => void): void {
  refreshSubscribers.push(callback)
}

function executeSubscribers(newToken: string | null): void {
  refreshSubscribers.forEach(callback => callback(newToken))
  refreshSubscribers = []
}

async function refreshToken(): Promise<string | null> {
  const authStore = useAuthStore()

  const success = await authStore.refreshAccessToken()

  if (success) {
    const newToken = getAccessToken()
    executeSubscribers(newToken)
    return newToken
  }

  // 刷新失败，通知所有等待者停止等待
  executeSubscribers(null)
  return null
}

request.interceptors.response.use(
  (response: AxiosResponse) => {
    console.log('[request] 响应拦截器 - URL:', response.config.url)
    console.log('[request] 响应拦截器 - 状态码:', response.status)

    const owner = (response.config as CustomAxiosRequestConfig)._authOwner
    if (owner && owner !== requestOwner()) return Promise.reject(new axios.CanceledError('登录身份已变化，已忽略旧请求结果。'))
    // 检查业务状态码
    const responseData = response.data
    if (responseData && responseData.code !== undefined && responseData.code !== 200) {
      // 业务状态码表示失败，抛出错误让 catch 捕获
      // 标记 skipErrorMessage=true，避免错误分支重复显示消息
      const error = new Error(responseData.message || '请求失败')
      ;(error as any).response = response
      ;(error as any).skipErrorMessage = true
      return Promise.reject(error)
    }

    return response.data
  },
  async (error) => {
    const { config, response, code } = error || {}
    if (axios.isCancel(error) || (config?._authOwner && config._authOwner !== requestOwner())) return Promise.reject(error)

    if (code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      ElMessage.error('请求超时，请检查网络或稍后重试')
      return Promise.reject(error)
    }

    if (!response) {
      ElMessage.error(ERROR_MESSAGES.NETWORK_ERROR)
      return Promise.reject(error)
    }

    if (response.status === 413) {
      ElMessage.error(getUploadErrorMessage(error))
      error.skipErrorMessage = true
      return Promise.reject(error)
    }

    if (response.status === CONFIG.FORBIDDEN_STATUS) {
      const message = error.response.data?.message || ERROR_MESSAGES.FORBIDDEN
      const details = error.response.data?.details

      if (message.includes('被禁用') || message.includes('账号已被禁用')) {
        ElMessage.error(ERROR_MESSAGES.ACCOUNT_DISABLED)
        cleanupTokens()
        router.push('/login')
      } else {
        const fullMessage = details ? `${message}（${details}）` : message
        ElMessage.warning(fullMessage)
      }
      return Promise.reject(error)
    }

    if (response.status === CONFIG.UNAUTHORIZED_STATUS) {
      if (config?.skipTokenRefresh || config?.skipBackendLogout) {
        return Promise.reject(error)
      }

      if (config?.url?.includes('/auth/refresh-token')) {
        ElMessage.warning(ERROR_MESSAGES.TOKEN_REFRESH_FAILED)
        return Promise.reject(error)
      }

      if (config?._retry) {
        return Promise.reject(error)
      }

      config._retry = true

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          addSubscriber((newToken) => {
            if (!newToken || config._authOwner !== requestOwner()) { reject(error); return }
            if (newToken && config.headers) {
              config.headers.Authorization = `${CONFIG.BEARER_PREFIX}${newToken}`
            }
            resolve(request(config as InternalAxiosRequestConfig))
          })
        })
      }

      isRefreshing = true

      try {
        const newToken = await refreshToken()

        if (!newToken) {
          throw new Error('Token refresh failed')
        }

        if (config.headers) {
          config.headers.Authorization = `${CONFIG.BEARER_PREFIX}${newToken}`
        }

        return request(config as InternalAxiosRequestConfig)
      } catch (refreshError) {
        console.error('[request] 刷新令牌失败，自动退出到登录页:', refreshError)
        refreshSubscribers = []
        if (!config._authOwner || config._authOwner === requestOwner()) handleLogout()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    if (error.response) {
      const message = error.response.data?.message || error.response.data?.msg || ERROR_MESSAGES.SERVER_ERROR

      // skipErrorMessage 标记在 error 对象上（来自业务错误）或 config 上（来自请求配置）
      const shouldSkipMessage = (error as any).skipErrorMessage || config?.skipErrorMessage
      if (!shouldSkipMessage) {
        ElMessage.error(message)
      }
    } else if (error.request) {
      ElMessage.error(ERROR_MESSAGES.NETWORK_ERROR)
    } else {
      console.error('[request] 请求配置错误:', error.message)
      ElMessage.error(ERROR_MESSAGES.REQUEST_ERROR)
    }

    return Promise.reject(error)
  }
)

export default request
export { handleLogout, cleanupTokens, getRoleFromPath, getRoleStoragePrefix, refreshToken }
export type { Config, StorageKeys, ErrorMessages, CustomAxiosRequestConfig }
