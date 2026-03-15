import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig, AxiosHeaders } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/store/auth'

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
  BASE_URL: '/api',
  TIMEOUT: 60000,
  RETRY_COUNT: 2,
  RETRY_DELAY_BASE: 1000,
  UNAUTHORIZED_STATUS: 401,
  FORBIDDEN_STATUS: 403,
  BEARER_PREFIX: 'Bearer ',
  PUBLIC_ENDPOINTS: [
    '/auth/login',
    '/auth/register',
    '/auth/forgot-password',
    '/auth/reset-password',
    '/auth/refresh-token',
    '/company/register',
    '/company/check-username',
    '/company/check-status',
    '/company/send-verify-code',
    '/company/recall',
    '/teacher/login',
    '/upload/image',
    '/upload/images',
    '/upload/file',
    '/upload/files'
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

function getAccessToken(): string | null {
  let currentRole = localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE)

  if (!currentRole) {
    currentRole = getRoleFromPath()
  }

  const rolePrefix = getRoleStoragePrefix(currentRole)

  console.log('[request] 获取访问令牌 - 当前角色:', currentRole, '角色前缀:', rolePrefix)

  let tokenKeys = [
    `${rolePrefix}${STORAGE_KEYS.ACCESS_TOKEN}_${currentRole}`,
    `${rolePrefix}${STORAGE_KEYS.ACCESS_TOKEN}`,
    `${rolePrefix}token_${currentRole}`,
    `${rolePrefix}token`
  ]

  // 如果是教师路径，添加所有教师子角色的 token key
  if (currentRole === 'ROLE_TEACHER') {
    const teacherRoles = ['ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
    teacherRoles.forEach(teacherRole => {
      tokenKeys.unshift(`${rolePrefix}${STORAGE_KEYS.ACCESS_TOKEN}_${teacherRole}`)
      tokenKeys.unshift(`${rolePrefix}token_${teacherRole}`)
    })
  }

  console.log('[request] 尝试的 token keys:', tokenKeys)

  for (const key of tokenKeys) {
    const token = localStorage.getItem(key)
    console.log(`[request] 检查 key "${key}":`, token ? '找到 token' : '无 token')
    if (token) {
      console.log('[request] 成功获取访问令牌，key:', key)
      return token
    }
  }

  console.warn('[request] 未找到访问令牌，所有 key 都未找到 token')
  return null
}

function cleanupTokens(): void {
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
    const roles = ['ROLE_STUDENT', 'ROLE_TEACHER', 'ROLE_ADMIN', 'ROLE_COMPANY']
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

  ElMessage.warning('您已成功登出系统')
  cleanupTokens()
  router.push('/login')
}

const request: AxiosInstance = axios.create({
  baseURL: CONFIG.BASE_URL,
  timeout: CONFIG.TIMEOUT,
  headers: {
    'Content-Type': 'application/json'
  }
})

interface CustomAxiosRequestConfig extends InternalAxiosRequestConfig {
  skipReadOnlyCheck?: boolean
  skipTokenRefresh?: boolean
  skipBackendLogout?: boolean
  skipErrorMessage?: boolean
  _retry?: boolean
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

    const isPublicEndpoint = CONFIG.PUBLIC_ENDPOINTS.some(endpoint => config.url?.includes(endpoint))

    if (!isPublicEndpoint) {
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

  const currentRole = authStore.role || localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE) || 'ROLE_ADMIN'
  const rolePrefix = getRoleStoragePrefix(currentRole)
  let refreshToken = localStorage.getItem(rolePrefix + 'refreshToken_' + currentRole)

  if (!refreshToken) {
    refreshToken = localStorage.getItem(rolePrefix + 'refreshToken')
  }

  if (!refreshToken) {
    refreshToken = localStorage.getItem('refreshToken')
  }

  if (!refreshToken) {
    return null
  }

  const success = await authStore.refreshAccessToken()

  if (success) {
    const newToken = getAccessToken()
    executeSubscribers(newToken)
    return newToken
  }

  return null
}

request.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data
  },
  async (error) => {
    const { config, response, code } = error || {}

    if (code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      ElMessage.error('请求超时，请检查网络或稍后重试')
      return Promise.reject(error)
    }

    if (!response) {
      ElMessage.error(ERROR_MESSAGES.NETWORK_ERROR)
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

      if (config?.url === '/auth/refresh-token') {
        ElMessage.warning(ERROR_MESSAGES.TOKEN_REFRESH_FAILED)
        return Promise.reject(error)
      }

      if (config?._retry) {
        return Promise.reject(error)
      }

      config._retry = true

      if (isRefreshing) {
        return new Promise((resolve) => {
          addSubscriber((newToken) => {
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

        if (newToken && config.headers) {
          config.headers.Authorization = `${CONFIG.BEARER_PREFIX}${newToken}`
        }

        return request(config as InternalAxiosRequestConfig)
      } catch (refreshError) {
        console.error('[request] 刷新令牌失败:', refreshError)
        refreshSubscribers = []
        handleLogout()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    if (error.response) {
      const message = error.response.data?.message || error.response.data?.msg || ERROR_MESSAGES.SERVER_ERROR

      if (!config?.skipErrorMessage) {
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
