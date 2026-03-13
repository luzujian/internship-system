import request from '@/utils/request'

export interface AdminUser {
  id: number
  adminId: number
  username: string
  name: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface PasswordChangeData {
  oldPassword: string
  newPassword: string
}

const AdminUserService = {
  getAdmins: async (currentPage: number, pageSize: number, searchQuery = '') => {
    try {
      const response = await request.get('/admin/admins', {
        params: {
          pageNum: currentPage,
          pageSize: pageSize,
          keyword: searchQuery
        }
      })
      return response
    } catch (error) {
      console.error('获取管理员用户列表失败:', error)
      throw error
    }
  },

  getAdminById: async (id: number) => {
    try {
      const response = await request.get(`/admin/admins/${id}`)
      return response
    } catch (error) {
      console.error('获取管理员用户信息失败:', error)
      throw error
    }
  },

  addAdmin: async (adminData: Omit<AdminUser, 'id' | 'createTime' | 'updateTime'>) => {
    try {
      const response = await request.post('/admin/admins', adminData)
      return response
    } catch (error) {
      console.error('添加管理员用户失败:', error)
      throw error
    }
  },

  batchAddAdmins: async (adminList: Omit<AdminUser, 'id' | 'createTime' | 'updateTime'>[]) => {
    try {
      const response = await request.post('/admin/admins/batch', adminList)
      return response
    } catch (error) {
      console.error('批量添加管理员用户失败:', error)
      throw error
    }
  },

  updateAdmin: async (adminData: Partial<AdminUser>) => {
    try {
      const response = await request.put('/admin/admins', adminData)
      return response
    } catch (error) {
      console.error('更新管理员用户信息失败:', error)
      throw error
    }
  },

  deleteAdmin: async (id: number) => {
    try {
      const response = await request.delete(`/admin/admins/${id}`)
      return response
    } catch (error) {
      console.error('删除管理员用户失败:', error)
      throw error
    }
  },

  batchDeleteAdmins: async (ids: number[]) => {
    try {
      const response = await request.delete('/admin/admins/batch', {
        data: ids
      })
      return response
    } catch (error) {
      console.error('批量删除管理员用户失败:', error)
      throw error
    }
  },

  resetAdminPassword: async (id: number, password: string) => {
    try {
      const response = await request.post(`/admin/admins/${id}/reset-password`, { password })
      return response
    } catch (error) {
      console.error('重置管理员用户密码失败:', error)
      throw error
    }
  },

  updateAdminStatus: async (id: number, status: number) => {
    try {
      const response = await request.put(`/admin/admins/${id}/status`, { status })
      return response
    } catch (error) {
      console.error('更新管理员用户状态失败:', error)
      throw error
    }
  },

  batchUpdateAdminStatus: async (ids: number[], status: number) => {
    try {
      const response = await request.put('/admin/admins/batch/status', {
        ids: ids,
        status: status
      })
      return response
    } catch (error) {
      console.error('批量更新管理员用户状态失败:', error)
      throw error
    }
  },

  changeAdminPassword: async (passwordData: PasswordChangeData) => {
    try {
      const { oldPassword, newPassword } = passwordData

      const response = await request.put('/auth/change-password', {
        oldPassword: oldPassword,
        newPassword: newPassword
      })
      return response
    } catch (error) {
      console.error('修改管理员用户密码失败:', error)
      throw error
    }
  },

  verifyAdminPassword: async (password: string) => {
    try {
      const response = await request.post('/auth/verify-password', {
        password: password
      })
      return response
    } catch (error) {
      console.error('验证管理员用户密码失败:', error)
      throw error
    }
  }
}

export default AdminUserService
