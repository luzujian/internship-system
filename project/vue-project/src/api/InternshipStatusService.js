import request from '@/utils/request'

export default {
  getInternshipStatusByCompanyId(companyId) {
    return request({
      url: `/api/admin/internship-status/company/${companyId}`,
      method: 'get'
    })
  },

  getInternshipStatusById(id) {
    return request({
      url: `/api/admin/internship-status/${id}`,
      method: 'get'
    })
  },

  updateInternshipStatus(id, status) {
    return request({
      url: `/api/admin/internship-status/${id}`,
      method: 'put',
      data: status
    })
  },

  approveInternshipStatus(id) {
    return request({
      url: `/api/admin/internship-status/${id}/approve`,
      method: 'put'
    })
  },

  rejectInternshipStatus(id) {
    return request({
      url: `/api/admin/internship-status/${id}/reject`,
      method: 'put'
    })
  }
}
