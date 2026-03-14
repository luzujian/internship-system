import request from '@/utils/request'

export interface InternshipApplication {
  id: number
  studentId: number
  studentName: string
  studentUserId: string
  major: string
  phone: string
  positionName: string
  applyTime: string
  status: string
  viewed: boolean
  email: string
  gender: string
  grade: string
  school: string
  education: string
  skills: string[]
  experience: string
  selfEvaluation: string
}

export default {
  getApplications(companyId: number, params?: Record<string, unknown>) {
    return request({
      url: `/admin/applications/company/${companyId}`,
      method: 'get',
      params
    })
  },

  getApplicationById(id: number) {
    return request({
      url: `/admin/applications/${id}`,
      method: 'get'
    })
  },

  addApplication(data: Partial<InternshipApplication>) {
    return request({
      url: '/admin/applications',
      method: 'post',
      data
    })
  },

  updateApplyStatus(id: number, status: string) {
    return request({
      url: `/admin/applications/${id}/status`,
      method: 'put',
      data: { status }
    })
  },

  markAsViewed(id: number) {
    return request({
      url: `/admin/applications/${id}/view`,
      method: 'put'
    })
  },

  deleteApplication(id: number) {
    return request({
      url: `/admin/applications/${id}`,
      method: 'delete'
    })
  },

  getApplicationsByPosition(positionId: number, params?: Record<string, unknown>) {
    return request({
      url: `/admin/applications/position/${positionId}`,
      method: 'get',
      params
    })
  },

  getApplicationsByStatus(status: string, params?: Record<string, unknown>) {
    return request({
      url: '/admin/applications',
      method: 'get',
      params: { status, ...params }
    })
  }
}
