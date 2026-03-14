import request from '@/utils/request'

export interface CompanyInfo {
  id: number
  companyName: string
  companyType?: string
  companyScale?: string
  industry?: string
  description?: string
  location?: string
  address?: string
  website?: string
  contactPhone?: string
  contactEmail?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface CompanyProfile {
  id: number
  username: string
  name: string
  phone?: string
  email?: string
}

export interface PasswordChangeData {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export interface CompanyStats {
  totalPositions?: number
  totalApplications?: number
  totalInterns?: number
  [key: string]: unknown
}

export default {
  getCompanyInfo(companyId: number) {
    return request<CompanyInfo>({
      url: '/company/info',
      method: 'get',
      params: { companyId }
    })
  },

  updateCompanyInfo(companyInfo: Partial<CompanyInfo>) {
    return request({
      url: '/company/info',
      method: 'put',
      data: companyInfo
    })
  },

  getStats() {
    return request<CompanyStats>({
      url: '/company/stats',
      method: 'get'
    })
  },

  getRecentPositions() {
    return request({
      url: '/company/positions/recent',
      method: 'get'
    })
  },

  getPositions(page: number, pageSize: number) {
    return request({
      url: '/company/positions',
      method: 'get',
      params: { page, pageSize }
    })
  },

  createPosition(position: Record<string, unknown>) {
    return request({
      url: '/company/positions',
      method: 'post',
      data: position
    })
  },

  updatePosition(id: number, position: Record<string, unknown>) {
    return request({
      url: `/company/positions/${id}`,
      method: 'put',
      data: position
    })
  },

  deletePosition(id: number) {
    return request({
      url: `/company/positions/${id}`,
      method: 'delete'
    })
  },

  getRecentApplications(companyId: number, limit: number) {
    return request({
      url: '/company/applications/recent',
      method: 'get',
      params: { companyId, limit }
    })
  },

  getNotifications(limit: number) {
    return request({
      url: '/company/notifications',
      method: 'get',
      params: { limit }
    })
  },

  getProfile() {
    return request<CompanyProfile>({
      url: '/company/profile',
      method: 'get'
    })
  },

  updateProfile(profileData: Partial<CompanyProfile>) {
    return request({
      url: '/company/profile',
      method: 'put',
      data: profileData
    })
  },

  changePassword(passwordData: PasswordChangeData) {
    return request({
      url: '/company/password',
      method: 'put',
      data: passwordData
    })
  }
}
