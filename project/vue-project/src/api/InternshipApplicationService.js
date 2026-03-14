import request from '@/utils/request'

export default {
  getApplications(companyId, params = {}) {
    return request({
      url: `/api/admin/applications/company/${companyId}`,
      method: 'get',
      params
    })
  },

  getApplicationById(id) {
    return request({
      url: `/api/admin/applications/${id}`,
      method: 'get'
    })
  },

  addApplication(data) {
    return request({
      url: '/api/admin/applications',
      method: 'post',
      data
    })
  },

  updateApplyStatus(id, status) {
    return request({
      url: `/api/admin/applications/${id}/status`,
      method: 'put',
      data: { status }
    })
  },

  markAsViewed(id) {
    return request({
      url: `/api/admin/applications/${id}/view`,
      method: 'put'
    })
  },

  deleteApplication(id) {
    return request({
      url: `/api/admin/applications/${id}`,
      method: 'delete'
    })
  },

  getApplicationsByPosition(positionId, params = {}) {
    return request({
      url: `/api/admin/applications/position/${positionId}`,
      method: 'get',
      params
    })
  },

  getApplicationsByStatus(status, params = {}) {
    return request({
      url: '/api/admin/applications',
      method: 'get',
      params: { status, ...params }
    })
  }
}
