import request from '@/utils/request'

export default {
  getCompanyInfo(companyId) {
    return request({
      url: '/api/company/info',
      method: 'get',
      params: { companyId }
    })
  },

  updateCompanyInfo(companyInfo) {
    return request({
      url: '/api/company/info',
      method: 'put',
      data: companyInfo
    })
  },

  getStats() {
    return request({
      url: '/api/company/stats',
      method: 'get'
    })
  },

  getRecentPositions() {
    return request({
      url: '/api/company/positions/recent',
      method: 'get'
    })
  },

  getPositions(page, pageSize) {
    return request({
      url: '/api/company/positions',
      method: 'get',
      params: { page, pageSize }
    })
  },

  createPosition(position) {
    return request({
      url: '/api/company/positions',
      method: 'post',
      data: position
    })
  },

  updatePosition(id, position) {
    return request({
      url: `/api/company/positions/${id}`,
      method: 'put',
      data: position
    })
  },

  deletePosition(id) {
    return request({
      url: `/api/company/positions/${id}`,
      method: 'delete'
    })
  },

  getRecentApplications(companyId, limit) {
    return request({
      url: '/api/company/applications/recent',
      method: 'get',
      params: { companyId, limit }
    })
  },

  getNotifications(limit) {
    return request({
      url: '/api/company/notifications',
      method: 'get',
      params: { limit }
    })
  },

  getProfile() {
    return request({
      url: '/api/company/profile',
      method: 'get'
    })
  },

  updateProfile(profileData) {
    return request({
      url: '/api/company/profile',
      method: 'put',
      data: profileData
    })
  },

  changePassword(passwordData) {
    return request({
      url: '/api/company/password',
      method: 'put',
      data: passwordData
    })
  }
}
