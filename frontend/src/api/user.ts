// 用户服务 - 仅保留全局功能和通用方法
import request from '@/utils/request'
import cacheService, { CACHE_CONFIG } from './cacheService'
import logger from '@/utils/logger'
import type { AxiosResponse } from 'axios'

interface User {
  id: number
  username: string
  name: string
  role?: string
  status?: number
  createTime?: string
  updateTime?: string
}

interface RegisterData {
  username: string
  password: string
  name: string
  role: string
  phone?: string
  majorId?: string
  classId?: string
  grade?: string
  departmentId?: string
}

interface PasswordData {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

const UserService = {
  // 获取用户详情 - 用于获取任何用户的基本信息
  getUserById: async (id: number): Promise<User> => {
    const cacheKey = `user_${id}`
    const cachedData = cacheService.get<User>(cacheKey)

    if (cachedData) {
      // 返回缓存数据的 Promise
      return Promise.resolve(cachedData)
    }

    // 缓存未命中，请求数据
    const response = await request.get<User>(`/users/${id}`)
    // 缓存数据
    if (response.data) {
      cacheService.set(cacheKey, response.data, CACHE_CONFIG.EXPIRATION_TIME.MEDIUM)
    }
    return response
  },

  // 根据角色获取用户列表
  getUsersByRole: async (role: string): Promise<User[]> => {
    const cacheKey = `users_by_role_${role}`
    const cachedData = cacheService.get<User[]>(cacheKey)

    if (cachedData) {
      // 返回缓存数据的 Promise
      return Promise.resolve(cachedData)
    }

    // 缓存未命中，请求数据
    const response = await request.get(`/users/role/${role}`)
    logger.log(`getUsersByRole(${role}) - response:`, response)
    logger.log(`getUsersByRole(${role}) - response.code:`, response.code)
    logger.log(`getUsersByRole(${role}) - response.data:`, response.data)

    // 解析后端响应数据格式：{ code: 200, data: [...], message: "success" }
    let users: User[] = []
    if (response && response.code === 200) {
      users = response.data || []
    } else if (Array.isArray(response)) {
      users = response
    } else if (response && response.data) {
      users = response.data
    }

    logger.log(`getUsersByRole(${role}) - 解析后的用户列表:`, users)

    // 缓存数据
    if (users.length > 0) {
      cacheService.set(cacheKey, users, CACHE_CONFIG.EXPIRATION_TIME.SHORT)
    }
    return users
  },

  // 用户登录 - 不缓存
  login: (credentials: { username: string; password: string; userType: string }) => {
    return request.post('/auth/login', credentials)
  },

  // 用户注册 - 注册后清除相关缓存
  register: async (userData: RegisterData) => {
    const response = await request.post('/auth/register', userData)
    // 清除相关缓存
    if (userData.role) {
      cacheService.delete(`users_by_role_${userData.role}`)
    }
    return response
  },

  // 获取当前登录用户信息 - 不缓存，确保获取最新信息
  getCurrentUser: () => {
    return request.get<User>('/auth/current-user')
  },

  // 修改密码 - 不影响用户基本信息，不需要清除缓存
  changePassword: (passwordData: PasswordData) => {
    return request.put('/auth/change-password', passwordData)
  },

  // 修改用户名 - 修改后需要重新登录
  updateUsername: (usernameData: { username: string; newUsername: string }) => {
    return request.put('/auth/update-username', usernameData)
  }
}

export default UserService
