import request from '@/utils/request'

interface User {
  id: number
  username: string
  name: string
  role?: string
  status?: number
  createTime?: string
  updateTime?: string
}

interface PageResponse<T = unknown> {
  rows: T[]
  total: number
}

interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

interface PasswordData {
  newPassword: string
  confirmPassword: string
}

interface SystemSettings {
  [key: string]: unknown
}

interface LogParams {
  operatorName?: string
  operation?: string
  status?: number
  startTime?: string
  endTime?: string
}

// 管理员服务
const AdminService = {
  // 获取用户统计信息
  getUserStats: () => {
    return request.get<ApiResponse<{ total: number; active: number }>>('/admin/stats/users')
  },

  // 获取所有用户（分页）
  getUsers: (page = 1, pageSize = 10) => {
    return request.get<ApiResponse<PageResponse<User>>>('/admin/users', {
      params: { page, pageSize }
    })
  },

  // 根据 ID 获取用户
  getUserById: (id: number) => {
    return request.get<ApiResponse<User>>(`/admin/users/${id}`)
  },

  // 添加用户
  addUser: (user: Omit<User, 'id' | 'createTime' | 'updateTime'>) => {
    return request.post<ApiResponse<User>>('/admin/users', user)
  },

  // 更新用户
  updateUser: (user: Partial<User> & { id: number }) => {
    return request.put<ApiResponse<User>>('/admin/users', user)
  },

  // 删除用户
  deleteUser: (id: number) => {
    return request.delete<ApiResponse<void>>(`/admin/users/${id}`)
  },

  // 重置管理员密码
  resetAdminPassword: (adminId: number, passwordData: PasswordData) => {
    return request.post<ApiResponse<void>>(`/admin/reset-password`, {
      adminId,
      ...passwordData
    })
  },

  // 获取系统统计信息
  getSystemStats: () => {
    return request.get<ApiResponse<Record<string, number>>>('/admin/stats/system')
  },

  // 获取操作日志
  getOperationLogs: (page = 1, pageSize = 10, params: LogParams = {}) => {
    return request.get<ApiResponse<PageResponse<unknown>>>('/admin/logs/operation', {
      params: { page, pageSize, ...params }
    })
  },

  // 获取登录日志
  getLoginLogs: (page = 1, pageSize = 10, params: LogParams = {}) => {
    return request.get<ApiResponse<PageResponse<unknown>>>('/admin/logs/login', {
      params: { page, pageSize, ...params }
    })
  },

  // 更新系统设置
  updateSystemSettings: (settings: SystemSettings) => {
    return request.put<ApiResponse<SystemSettings>>('/admin/settings', settings)
  },

  // 获取系统设置
  getSystemSettings: () => {
    return request.get<ApiResponse<SystemSettings>>('/admin/settings')
  },

  // 批量禁用/启用用户
  batchUpdateUserStatus: (userIds: number[], status: number) => {
    return request.put<ApiResponse<void>>('/admin/users/batch/status', {
      userIds,
      status
    })
  }
}

export default AdminService
