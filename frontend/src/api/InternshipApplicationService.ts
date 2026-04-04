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

  /**
   * 获取当前登录企业的岗位申请列表（企业端专用）
   */
  getCurrentCompanyApplications(params?: Record<string, unknown>) {
    console.log('[API] 调用 getCurrentCompanyApplications, URL: /company/applications')
    return request({
      url: '/company/applications',
      method: 'get',
      params: {
        ...params,
        _t: Date.now() // 添加时间戳避免缓存
      }
    })
  },

  /**
   * 获取当前登录企业的学生快速申请列表（从student_job_application表）
   */
  getJobApplications(params?: Record<string, unknown>) {
    console.log('[API] 调用 getJobApplications, URL: /company/job-applications')
    return request({
      url: '/company/job-applications',
      method: 'get',
      params: {
        ...params,
        _t: Date.now()
      }
    })
  },

  /**
   * 更新学生快速申请状态（同意/拒绝）
   */
  updateJobApplicationStatus(id: number, status: string) {
    console.log('[API] 调用 updateJobApplicationStatus, ID:', id, '状态:', status)
    return request({
      url: `/company/job-applications/${id}/status`,
      method: 'put',
      data: { status }
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
      url: `/company/applications/${id}/status`,
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
