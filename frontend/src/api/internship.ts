import request from '@/utils/request'

export interface InternshipStatus {
  id: number
  studentId: number
  status: number
  companyId?: number
  positionId?: number
  internshipTime?: string
  reviewTime?: string
  createTime?: string
  updateTime?: string
}

export interface InternshipResponse {
  code: number
  data: InternshipStatus | InternshipStatus[]
  msg?: string
}

type AxiosResponseWithMeta<T> = {
  data: {
    code: number
    data: T
    msg?: string
  }
}

export default {
  getInternshipStatusList: (params?: Record<string, unknown>) => {
    return request.get<InternshipStatus[]>('/admin/internship-status', { params }) as unknown as AxiosResponseWithMeta<InternshipStatus[]>
  },

  getInternshipStatusById: (id: number) => {
    return request.get<InternshipStatus>(`/admin/internship-status/${id}`)
  },

  addInternshipStatus: (data: Omit<InternshipStatus, 'id' | 'createTime' | 'updateTime'>) => {
    return request.post('/admin/internship-status', data)
  },

  updateInternshipStatus: (id: number, data: Partial<InternshipStatus>) => {
    return request.put(`/admin/internship-status/${id}`, data)
  },

  deleteInternshipStatus: (id: number) => {
    return request.delete(`/admin/internship-status/${id}`)
  },

  batchDeleteInternshipStatus: (ids: number[]) => {
    return request.delete('/admin/internship-status/batch', { data: ids })
  },

  exportInternshipStatus: (params?: Record<string, unknown>) => {
    return request.get('/admin/internship-status/export', { params })
  },

  getInternshipStatusByStudentId: (studentId: number) => {
    return request.get<InternshipStatus[]>(`/admin/internship-status/student/${studentId}`)
  },

  deleteInternshipStatusByStudentId: (studentId: number) => {
    return request.delete(`/admin/internship-status/student/${studentId}`)
  },

  batchDeleteInternshipStatusByStudentIds: (studentIds: number[]) => {
    return request.delete('/admin/internship-status/student/batch', { data: studentIds })
  },

  bindOrUpdateInternshipStatus: (data: Partial<InternshipStatus>) => {
    return request.post('/admin/internship-status/bind', data)
  },

  getPendingRecallAuditInternships: (params?: Record<string, unknown>) => {
    return request.get('/admin/internship-status/recall/pending', { params })
  },

  auditRecallApplication: (id: number, params?: Record<string, unknown>) => {
    return request.put(`/admin/internship-status/${id}/recall-audit`, params)
  },

  getRecallStatistics: () => {
    return request.get('/admin/internship-status/recall/statistics')
  },

  clearRecallData: () => {
    return request.delete('/admin/internship-status/recall/clear')
  },

  getMyInternshipStatus: () => {
    return request.get<InternshipStatus>('/student/internship-status')
  },

  submitRecallApplication: (id: number, recallReason: string) => {
    return request.put(`/student/internship-status/${id}/recall`, { recallReason })
  }
}
