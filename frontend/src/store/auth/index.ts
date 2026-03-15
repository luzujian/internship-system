import { defineStore } from 'pinia'
import request, { cleanupTokens, CustomAxiosRequestConfig } from '@/utils/request'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'

// 导入模块化的配置和工具
import {
  CONFIG,
  STORAGE_KEYS,
  ERROR_MESSAGES,
  TEACHER_ROLES,
  ROLE_TO_DASHBOARD_MAP
} from './config'
import {
  getRoleFromPath,
  getRoleStoragePrefix,
  validateString,
  validateObject,
  clearRoleStorage,
  clearAllRolesStorage
} from './storage'
import {
  decodeToken,
  isTokenExpired,
  validateToken,
  refreshAccessToken as refreshTokenUtil
} from './token'
import {
  isTeacherRole,
  isAdminRole,
  isStudentRole,
  isCompanyRole,
  saveRoleInfoToStorage,
  setRoleId,
  getDashboardPath
} from './roles'

interface User {
  id: string
  username: string
  name: string
  isReadOnly?: boolean
  originalAdminUsername?: string | null
  teacherType?: string
  [key: string]: unknown
}

interface AllUsers {
  [key: string]: {
    accessToken: string | null
    refreshToken: string | null
    role: string
    userId: string | null
    username: string | null
    name?: string
    isReadOnly?: boolean
    originalAdminUsername?: string | null
  }
}

interface AuthState {
  user: User | null
  token: string | null
  role: string | null
  isAuthenticated: boolean
  hasValidatedToken: boolean
  allUsers: AllUsers
  storageListenerInitialized: boolean
}

interface LoginResult {
  token?: string
  accessToken?: string
  refreshToken?: string
  userInfo?: User & { role: string; teacherType?: string }
  user?: User & { role: string }
  permissions?: unknown[]
}

interface LoginResponse {
  code: number
  data: LoginResult
  message?: string
}

interface SwitchRoleResult {
  token?: string
  accessToken?: string
  refreshToken?: string
  userInfo: User & { role: string; teacherType?: string; teacherUserId?: string }
  user: User & { role: string }
  role: string
  permissions?: unknown[]
  isReadOnly?: boolean
  originalAdminUsername?: string
}

interface SwitchRoleResponse {
  code: number
  data: {
    code: number
    data: SwitchRoleResult
  }
  message?: string
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    token: null,
    role: null,
    isAuthenticated: false,
    hasValidatedToken: false,
    allUsers: {},
    storageListenerInitialized: false
  }),

  getters: {
    currentUser: (state) => state.user,
    currentRole: (state) => state.role,
    isAuth: (state) => state.isAuthenticated,
    displayName: (state) => {
      if (state.user && state.user.name) {
        return state.user.name
      }
      return state.user ? state.user.username : ''
    },
    adminPermissions: () => {
      try {
        const permissions = localStorage.getItem(STORAGE_KEYS.ADMIN_PERMISSIONS)
        return permissions ? JSON.parse(permissions) : []
      } catch (error) {
        logger.error('获取管理员权限失败:', error)
        return []
      }
    },
    hasPermission: () => (permissionCode: string) => {
      try {
        const permissions = localStorage.getItem(STORAGE_KEYS.ADMIN_PERMISSIONS)
        const permissionList = permissions ? JSON.parse(permissions) : []
        return permissionList.some((p: { permissionCode: string }) => p.permissionCode === permissionCode)
      } catch (error) {
        logger.error('检查权限失败:', error)
        return false
      }
    }
  },

  actions: {
    // ==================== 存储监听 ====================

    initStorageListener() {
      if (typeof window !== 'undefined' && !this.storageListenerInitialized) {
        window.addEventListener('storage', (event) => {
          if (event.key === 'auth_logout_event' && event.newValue) {
            logger.log('检测到其他标签页的登出事件，同步登出当前标签页')
            cleanupTokens()
            this.user = null
            this.token = null
            this.role = null
            this.isAuthenticated = false
            this.allUsers = {}

            if (window.location.pathname !== '/login') {
              window.location.href = '/login'
            }
          }
        })
        this.storageListenerInitialized = true
        logger.log('已初始化跨标签页同步监听器')
      }
    },

    // ==================== 多角色管理 ====================

    manageMultiRoleLogin() {
      logger.log('当前所有已保存的角色信息:')
      const roleTypes = Object.values(CONFIG.ROLES)

      roleTypes.forEach(roleType => {
        const rolePrefix = getRoleStoragePrefix(roleType)
        const token = localStorage.getItem(rolePrefix + 'token_' + roleType)
        const userId = localStorage.getItem(rolePrefix + 'userId_' + roleType)
        const username = localStorage.getItem(rolePrefix + 'username_' + roleType)

        if (token) {
          logger.log(`${roleType}: 用户名=${username}, 用户 ID=${userId}, 令牌存在`)
        } else {
          logger.log(`${roleType}: 未登录`)
        }
      })

      logger.log('当前活跃角色:', localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE))
    },

    // ==================== 登录相关 ====================

    saveRoleInfoToLocalStorage(role: string, accessToken: string, userId: string, username: string, refreshToken: string | null = null) {
      if (!validateString(role, 'role') || !validateString(accessToken, 'accessToken')) {
        return
      }

      const rolePrefix = getRoleStoragePrefix(role)

      console.log('[auth] 保存角色信息到 localStorage:')
      console.log('[auth] - 角色:', role)
      console.log('[auth] - 角色前缀:', rolePrefix)

      saveRoleInfoToStorage(role, accessToken, userId, username, refreshToken)
      this.restoreAuthState()
    },

    async login(username: string, password: string): Promise<boolean> {
      try {
        if (!validateString(username, 'username') || !validateString(password, 'password')) {
          return false
        }

        let userRole = getRoleFromPath() || CONFIG.ROLES.ROLE_STUDENT

        // 清理旧角色信息
        this.clearOldRoleInfo(userRole)

        // 检查用户禁用状态
        if (this.isUserDisabled(username)) {
          ElMessage.error(ERROR_MESSAGES.USER_DISABLED)
          return false
        }

        // 发送登录请求
        logger.log('发送登录请求到:', request.defaults.baseURL + '/auth/login')
        const response = await request.post('/auth/login', {
          username,
          password
        })

        if (!response || response.code !== 200) {
          ElMessage.error(response?.message || ERROR_MESSAGES.LOGIN_FAILED)
          return false
        }

        return this.handleLoginSuccess(response, '')
      } catch (error) {
        logger.error('登录失败:', error)
        ElMessage.error(ERROR_MESSAGES.LOGIN_FAILED)
        return false
      }
    },

    handleLoginSuccess(result: LoginResponse['data'], userType: string): boolean {
      let accessToken: string, refreshToken: string | null, userInfo: User & { role: string; teacherType?: string }, role: string, teacherType: string | undefined, permissions: unknown[] | undefined

      // 解析不同格式的响应数据
      // 响应可能是 { code, data, message } 格式，其中 data 包含实际的登录结果
      const loginResult = result.data || result

      if (loginResult.token) {
        accessToken = loginResult.token
        refreshToken = loginResult.refreshToken || null
        userInfo = loginResult.userInfo || loginResult.user as User & { role: string }
        role = userInfo.role
        permissions = loginResult.permissions
      } else if (loginResult.accessToken) {
        accessToken = loginResult.accessToken
        refreshToken = loginResult.refreshToken || null
        userInfo = loginResult.user as User & { role: string }
        role = userInfo.role
        permissions = loginResult.permissions
      } else if (loginResult.userInfo) {
        accessToken = loginResult.accessToken || loginResult.token || ''
        refreshToken = loginResult.refreshToken || null
        userInfo = loginResult.userInfo
        role = userInfo.role
        teacherType = userInfo.teacherType
        permissions = loginResult.permissions
      } else {
        logger.error('无法识别的响应数据格式:', result)
        ElMessage.error(ERROR_MESSAGES.LOGIN_FAILED)
        return false
      }

      if (!role) {
        logger.error('登录失败，未获取到角色信息')
        ElMessage.error(ERROR_MESSAGES.LOGIN_FAILED)
        return false
      }

      // 保存角色信息
      this.saveRoleInfoToLocalStorage(role, accessToken, String(userInfo.id), userInfo.username, refreshToken)

      // 保存额外信息
      this.saveExtraInfo(role, userInfo, teacherType, permissions, loginResult)

      // 更新状态
      this.updateAuthState(accessToken, userInfo, role, refreshToken)

      logger.log('登录成功，用户信息:', this.user, '角色:', this.role)
      return true
    },

    // ==================== 注册 ====================

    async register(
      username: string,
      password: string,
      name: string,
      role = CONFIG.ROLES.ROLE_STUDENT,
      majorId = '',
      classId = '',
      departmentId = '',
      grade = ''
    ): Promise<boolean> {
      try {
        if (!validateString(username, 'username') || !validateString(password, 'password') || !validateString(name, 'name')) {
          return false
        }

        const requestData: Record<string, unknown> = { username, password, role }

        if (role === CONFIG.ROLES.ROLE_ADMIN) {
          requestData.phone = name
        } else {
          requestData.name = name
        }

        if (role === CONFIG.ROLES.ROLE_STUDENT) {
          requestData.majorId = majorId
          requestData.classId = classId
          requestData.grade = grade
        }

        if (isTeacherRole(role)) {
          requestData.departmentId = departmentId
        }

        const response = await request.post('/auth/register', requestData)

        if (response && response.code === 200) {
          return true
        } else {
          ElMessage.error(response?.message || ERROR_MESSAGES.REGISTER_FAILED)
          return false
        }
      } catch (error) {
        logger.error('注册失败:', error)
        ElMessage.error(ERROR_MESSAGES.REGISTER_FAILED)
        return false
      }
    },

    // ==================== 角色切换 ====================

    async switchRole(targetRole: string, targetUsername: string, adminPassword: string): Promise<boolean> {
      try {
        if (!validateString(targetRole, 'targetRole') || !validateString(targetUsername, 'targetUsername')) {
          return false
        }

        if (!validateString(adminPassword, 'adminPassword')) {
          ElMessage.error('请输入管理员密码')
          return false
        }

        if (this.role !== CONFIG.ROLES.ROLE_ADMIN) {
          ElMessage.error('只有管理员才能切换角色')
          return false
        }

        const response = await request.post('/auth/switch-role', {
          targetRole,
          targetUsername,
          adminPassword
        })

        if (!response || response.code !== 200) {
          ElMessage.error(response?.message || '角色切换失败')
          return false
        }

        return this.handleSwitchRoleSuccess(response.data, targetRole)
      } catch (error) {
        logger.error('角色切换失败:', error)
        ElMessage.error('角色切换失败：' + ((error as { message?: string }).message || '未知错误'))
        return false
      }
    },

    handleSwitchRoleSuccess(result: SwitchRoleResult, targetRole: string): boolean {
      let accessToken: string, refreshToken: string | null, userInfo: User & { role: string; teacherType?: string; teacherUserId?: string }, role: string, permissions: unknown[] | undefined

      if (targetRole === 'teacher') {
        accessToken = result.token || ''
        refreshToken = result.refreshToken || null
        userInfo = result.userInfo
        role = result.role
        permissions = result.permissions || []

        localStorage.setItem(STORAGE_KEYS.TEACHER_TYPE, userInfo.teacherType || '')
        localStorage.setItem(STORAGE_KEYS.TEACHER_ID, String(userInfo.id))

        if (permissions && permissions.length > 0) {
          localStorage.setItem(STORAGE_KEYS.TEACHER_PERMISSIONS, JSON.stringify(permissions))
        }
      } else {
        accessToken = result.accessToken || ''
        refreshToken = result.refreshToken || null
        userInfo = result.user
        role = result.role

        setRoleId(role, String(userInfo.id))
      }

      const username = targetRole === 'teacher' ? (userInfo.teacherUserId || userInfo.username) : userInfo.username
      const rolePrefix = getRoleStoragePrefix(role)

      // 保存到 localStorage
      localStorage.setItem(rolePrefix + 'token_' + role, accessToken)
      localStorage.setItem(rolePrefix + 'accessToken_' + role, accessToken)
      if (refreshToken) {
        localStorage.setItem(rolePrefix + 'refreshToken_' + role, refreshToken)
      }
      localStorage.setItem(rolePrefix + 'role_' + role, role)
      localStorage.setItem(rolePrefix + 'userId_' + role, String(userInfo.id))
      localStorage.setItem(rolePrefix + 'username_' + role, username)
      localStorage.setItem(rolePrefix + 'isReadOnly_' + role, String(result.isReadOnly || false))
      if (result.originalAdminUsername) {
        localStorage.setItem(rolePrefix + 'originalAdminUsername_' + role, result.originalAdminUsername)
      }

      localStorage.setItem(STORAGE_KEYS.CURRENT_ROLE, role)

      // 更新状态
      this.setToken(accessToken)
      this.user = {
        id: String(userInfo.id),
        username: username,
        name: userInfo.name || '',
        isReadOnly: result.isReadOnly || false,
        originalAdminUsername: result.originalAdminUsername || null
      }
      this.role = role
      this.isAuthenticated = true

      this.allUsers[role] = {
        accessToken, refreshToken, role,
        userId: String(userInfo.id),
        username: username,
        name: userInfo.name,
        isReadOnly: result.isReadOnly || false,
        originalAdminUsername: result.originalAdminUsername || null
      }

      logger.log('角色切换成功：管理员 ->', role, '只读模式:', result.isReadOnly || false)
      return true
    },

    // ==================== 登出 ====================

    async logout(skipBackendLogout = false): Promise<void> {
      try {
        const currentRole = this.role || localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE)

        // 调用后端登出
        if (currentRole && !skipBackendLogout) {
          await this.callBackendLogout(currentRole)
        }

        // 清理当前角色存储
        if (this.role) {
          clearRoleStorage(this.role)

          if (this.allUsers && this.allUsers[this.role]) {
            delete this.allUsers[this.role]
          }
        }

        // 清理 current_role 标记
        if (localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE) === this.role) {
          localStorage.removeItem(STORAGE_KEYS.CURRENT_ROLE)
        }

        // 重置状态
        this.user = null
        this.token = null
        this.role = null
        this.isAuthenticated = false

        logger.log('注销成功，当前剩余已登录用户:', Object.keys(this.allUsers))
      } catch (error) {
        logger.error('登出过程出错:', error)
        this.user = null
        this.token = null
        this.role = null
        this.isAuthenticated = false
      }

      cleanupTokens()

      // 触发跨标签页登出事件
      localStorage.setItem('auth_logout_event', Date.now().toString())
      setTimeout(() => {
        localStorage.removeItem('auth_logout_event')
      }, 100)
    },

    async callBackendLogout(role: string): Promise<void> {
      const rolePrefix = getRoleStoragePrefix(role)
      const accessToken = localStorage.getItem(rolePrefix + 'accessToken_' + role)

      if (accessToken) {
        try {
          await request.post('/auth/logout', {}, {
            headers: { 'Authorization': `Bearer ${accessToken}` }
          } as CustomAxiosRequestConfig)
          logger.log('后端登出接口调用成功')
        } catch (logoutError) {
          logger.warn('后端登出接口调用失败，继续本地登出')
        }
      }
    },

    // ==================== Token 管理 ====================

    setToken(token: string) {
      this.token = token

      if (this.role && this.allUsers) {
        if (!this.allUsers[this.role]) {
          this.allUsers[this.role] = {} as AllUsers[string]
        }
        this.allUsers[this.role].accessToken = token
      }

      logger.log('已设置访问令牌，角色:', this.role)
    },

    async refreshAccessToken(): Promise<boolean> {
      let currentRole = this.role || localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE) || getRoleFromPath()

      if (!currentRole) {
        logger.error('无法刷新令牌：未找到当前角色')
        return false
      }

      const newToken = await refreshTokenUtil(currentRole)

      if (newToken) {
        this.setToken(newToken)
        if (this.allUsers && this.allUsers[currentRole]) {
          this.allUsers[currentRole].accessToken = newToken
        }
        return true
      }

      return false
    },

    // ==================== Token 工具方法（导出供外部使用） ====================

    decodeToken,
    isTokenExpired,
    validateToken,

    // ==================== 认证状态恢复 ====================

    async restoreAuthState(allowPathRoleInference = true): Promise<boolean> {
      logger.log('开始从本地存储恢复认证状态，允许路径角色推断:', allowPathRoleInference)

      let currentRole = localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE)
      const pathRole = getRoleFromPath()

      // 尝试从路径推断角色
      if (!currentRole && allowPathRoleInference && Object.values(CONFIG.ROLES).includes(pathRole as string)) {
        const rolePrefix = getRoleStoragePrefix(pathRole)
        if (localStorage.getItem(rolePrefix + 'accessToken_' + pathRole)) {
          currentRole = pathRole
          logger.log('localStorage 中无 current_role，根据页面路径和登录信息确定角色:', currentRole)
        }
      }

      // 重建 allUsers
      this.allUsers = {}
      const rolePrefixes = CONFIG.ROLE_PREFIXES

      for (const role of Object.values(CONFIG.ROLES)) {
        const rolePrefix = rolePrefixes[role as keyof typeof rolePrefixes]
        const accessToken = localStorage.getItem(rolePrefix + 'accessToken_' + role)

        if (accessToken) {
          if (isTokenExpired(accessToken)) {
            logger.log(role, '角色令牌已过期，尝试刷新...')
            const tempRole = this.role
            this.role = role
            const refreshSuccess = await this.refreshAccessToken()
            this.role = tempRole

            if (!refreshSuccess) {
              logger.log(role, '角色令牌刷新失败，清除本地存储')
              localStorage.removeItem(rolePrefix + 'accessToken_' + role)
              localStorage.removeItem(rolePrefix + 'refreshToken_' + role)
            } else {
              this.allUsers[role] = this.buildUserFromStorage(role, rolePrefix)
            }
          } else {
            this.allUsers[role] = this.buildUserFromStorage(role, rolePrefix)
          }
        }
      }

      // 确定要恢复的角色
      const restoreResult = this.determineRestoreRole(currentRole, pathRole, allowPathRoleInference)

      logger.log('本地存储信息 - token:', restoreResult.token ? '存在' : '不存在', 'role:', restoreResult.role)

      if (restoreResult.token) {
        this.role = restoreResult.role
        this.setToken(restoreResult.token)
        this.isAuthenticated = true

        const decoded = decodeToken(restoreResult.token)
        if (decoded) {
          this.user = {
            id: decoded.userId || '',
            username: decoded.username || '',
            name: decoded.name || '',
            isReadOnly: decoded.isReadOnly || restoreResult.isReadOnly || false,
            originalAdminUsername: decoded.originalAdminUsername || restoreResult.originalAdminUsername || null
          }
        } else if (restoreResult.userId) {
          this.user = {
            id: restoreResult.userId,
            username: restoreResult.username || '',
            name: localStorage.getItem(getRoleStoragePrefix(restoreResult.role || '') + 'name_' + restoreResult.role) || '',
            isReadOnly: restoreResult.isReadOnly || false,
            originalAdminUsername: restoreResult.originalAdminUsername || null
          }
        }

        // 更新 localStorage
        this.updateLocalStorageAfterRestore(currentRole, restoreResult.role || '', restoreResult.userId)

        this.hasValidatedToken = true
        logger.log('恢复认证状态成功 - 角色:', this.role, '已认证:', this.isAuthenticated)
        return true
      } else {
        logger.log('本地存储中没有找到 token，认证状态未恢复')
        return false
      }
    },

    // ==================== 辅助方法 ====================

    buildUserFromStorage(role: string, rolePrefix: string) {
      return {
        accessToken: localStorage.getItem(rolePrefix + 'accessToken_' + role),
        refreshToken: localStorage.getItem(rolePrefix + 'refreshToken_' + role),
        role: role,
        userId: localStorage.getItem(rolePrefix + 'userId_' + role),
        username: localStorage.getItem(rolePrefix + 'username_' + role),
        isReadOnly: localStorage.getItem(rolePrefix + 'isReadOnly_' + role) === 'true',
        originalAdminUsername: localStorage.getItem(rolePrefix + 'originalAdminUsername_' + role) || null
      }
    },

    determineRestoreRole(currentRole: string | null, pathRole: string, allowPathRoleInference: boolean) {
      let token: string | null = null
      let role: string | null = null
      let userId: string | null = null
      let username: string | null = null
      let isReadOnly = false
      let originalAdminUsername: string | null = null

      if (currentRole && this.allUsers[currentRole]) {
        const currentUser = this.allUsers[currentRole]
        token = currentUser.accessToken
        role = currentUser.role
        userId = currentUser.userId
        username = currentUser.username
        isReadOnly = currentUser.isReadOnly || false
        originalAdminUsername = currentUser.originalAdminUsername || null
      } else if (Object.keys(this.allUsers).length > 0 && allowPathRoleInference) {
        // 尝试按优先级选择角色
        const priorityRoles = [
          pathRole,
          CONFIG.ROLES.ROLE_ADMIN,
          ...TEACHER_ROLES,
          CONFIG.ROLES.ROLE_STUDENT
        ]

        for (const tryRole of priorityRoles) {
          if (this.allUsers[tryRole]) {
            const currentUser = this.allUsers[tryRole]
            token = currentUser.accessToken
            role = currentUser.role
            userId = currentUser.userId
            username = currentUser.username
            isReadOnly = currentUser.isReadOnly || false
            originalAdminUsername = currentUser.originalAdminUsername || null
            break
          }
        }
      }

      return { token, role, userId, username, isReadOnly, originalAdminUsername }
    },

    updateLocalStorageAfterRestore(currentRole: string | null, role: string, userId: string | null) {
      if (!currentRole) {
        localStorage.setItem(STORAGE_KEYS.CURRENT_ROLE, this.role || '')
        logger.log('没有保存的 current_role，已设置为当前恢复的角色:', this.role)
        setRoleId(this.role || '', userId || '')
      } else if (currentRole === this.role) {
        setRoleId(this.role || '', userId || '')
      }
    },

    clearOldRoleInfo(newRole: string) {
      logger.log('清理旧的角色信息')
      this.user = null
      this.token = null
      this.role = null
      this.isAuthenticated = false

      const oldRole = localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE)
      if (oldRole && oldRole !== newRole) {
        clearRoleStorage(oldRole)
        logger.log('已清理旧角色信息:', oldRole)
      }
    },

    clearAllAuthInfo() {
      logger.log('清理所有角色的旧认证信息')
      this.user = null
      this.token = null
      this.role = null
      this.isAuthenticated = false

      clearAllRolesStorage()
    },

    isUserDisabled(username: string): boolean {
      try {
        const studentStatusMap = JSON.parse(localStorage.getItem(STORAGE_KEYS.STUDENT_STATUS_MAP) || '{}')
        if (studentStatusMap[username] === 0) {
          logger.error('用户已被禁用:', username)
          return true
        }

        const teacherStatusMap = JSON.parse(localStorage.getItem(STORAGE_KEYS.TEACHER_STATUS_MAP) || '{}')
        if (teacherStatusMap[username] === 0) {
          logger.error('用户已被禁用:', username)
          return true
        }
      } catch (error) {
        logger.error('检查用户禁用状态时出错:', error)
      }
      return false
    },

    saveExtraInfo(
      role: string,
      userInfo: User & { role: string; teacherType?: string },
      teacherType: string | undefined,
      permissions: unknown[] | undefined,
      data: LoginResult
    ) {
      console.log('[saveExtraInfo] role:', role)
      console.log('[saveExtraInfo] permissions:', permissions)
      console.log('[saveExtraInfo] data.permissions:', data.permissions)

      if (teacherType) {
        localStorage.setItem(STORAGE_KEYS.TEACHER_TYPE, teacherType)
        logger.log('已保存教师类型:', teacherType)
      }

      if (permissions && permissions.length > 0) {
        localStorage.setItem(STORAGE_KEYS.TEACHER_PERMISSIONS, JSON.stringify(permissions))
        logger.log('已保存教师权限，数量:', permissions.length)
      }

      localStorage.setItem(STORAGE_KEYS.CURRENT_ROLE, role)
      this.role = role
      logger.log('已更新 current_role 为用户实际角色:', role)

      setRoleId(role, String(userInfo.id))

      if (isAdminRole(role)) {
        const perms = data.permissions || permissions || []
        if (perms && perms.length > 0) {
          localStorage.setItem(STORAGE_KEYS.ADMIN_PERMISSIONS, JSON.stringify(perms))
          console.log('[saveExtraInfo] 管理员登录成功，已获取权限数量:', perms.length)
          console.log('[saveExtraInfo] 权限列表:', (perms as Array<{ permissionCode: string }>).map(p => p.permissionCode))
        } else {
          console.warn('[saveExtraInfo] 管理员登录但没有获取到权限数据')
        }
      }
    },

    updateAuthState(accessToken: string, userInfo: User & { role: string }, role: string, refreshToken: string | null) {
      this.setToken(accessToken)
      this.user = {
        id: String(userInfo.id),
        username: userInfo.username,
        name: userInfo.name
      }
      this.role = role
      this.isAuthenticated = true

      this.allUsers[role] = {
        accessToken, refreshToken, role,
        userId: String(userInfo.id),
        username: userInfo.username,
        name: userInfo.name
      }

      // 加载其他已登录用户
      const roleTypes = Object.values(CONFIG.ROLES)
      roleTypes.forEach(roleType => {
        if (roleType !== role) {
          const rolePrefix = getRoleStoragePrefix(roleType)
          const savedAccessToken = localStorage.getItem(rolePrefix + 'accessToken_' + roleType)
          const savedRefreshToken = localStorage.getItem(rolePrefix + 'refreshToken_' + roleType)
          if (savedAccessToken) {
            this.allUsers[roleType] = {
              accessToken: savedAccessToken,
              refreshToken: savedRefreshToken,
              role: roleType,
              userId: localStorage.getItem(rolePrefix + 'userId_' + roleType),
              username: localStorage.getItem(rolePrefix + 'username_' + roleType)
            }
          }
        }
      })

      logger.log('当前所有已登录用户:', Object.keys(this.allUsers))
    },

    switchToRole(newRole: string): boolean {
      if (this.allUsers && this.allUsers[newRole]) {
        const userInfo = this.allUsers[newRole]
        this.setToken(userInfo.accessToken || '')
        this.role = userInfo.role
        this.user = {
          id: userInfo.userId || '',
          username: userInfo.username || '',
          name: userInfo.name || ''
        }
        this.isAuthenticated = true

        localStorage.setItem(STORAGE_KEYS.CURRENT_ROLE, newRole)
        setRoleId(newRole, userInfo.userId || '')

        logger.log('切换角色成功 - 新角色:', newRole)
        return true
      }
      return false
    }
  }
})

// 导出配置供外部使用
export { CONFIG, STORAGE_KEYS, ERROR_MESSAGES, ROLE_TO_DASHBOARD_MAP, TEACHER_ROLES }
export { getRoleFromPath, getRoleStoragePrefix }
export { decodeToken, isTokenExpired, validateToken }
export { isTeacherRole, isAdminRole, isStudentRole, isCompanyRole, getDashboardPath } from './roles'
