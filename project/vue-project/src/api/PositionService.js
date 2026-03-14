import request from '@/utils/request'

export default {
  getPositionsByCompanyId(companyId, params = {}) {
    return request({
      url: `/api/admin/positions/company/${companyId}`,
      method: 'get',
      params
    })
  },

  getPositionById(id) {
    return request({
      url: `/api/admin/positions/${id}`,
      method: 'get'
    })
  },

  createPosition(data) {
    return request({
      url: '/api/admin/positions',
      method: 'post',
      data
    })
  },

  updatePosition(id, data) {
    return request({
      url: `/api/admin/positions/${id}`,
      method: 'put',
      data
    })
  },

  deletePosition(id) {
    return request({
      url: `/api/admin/positions/${id}`,
      method: 'delete'
    })
  },

  pausePosition(id) {
    return request({
      url: `/api/admin/positions/${id}/pause`,
      method: 'put'
    })
  },

  resumePosition(id) {
    return request({
      url: `/api/admin/positions/${id}/resume`,
      method: 'put'
    })
  },

  getStatistics(companyId) {
    return request({
      url: '/api/admin/positions/statistics',
      method: 'get',
      params: { companyId }
    })
  },

  updateRecruitedCount(id, change) {
    return request({
      url: `/api/admin/positions/${id}/recruited-count`,
      method: 'put',
      params: { change }
    })
  }
}
