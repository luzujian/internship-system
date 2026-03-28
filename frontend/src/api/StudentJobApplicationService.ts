import request from '@/utils/request'

export interface StudentJobApplication {
  id?: number
  studentId?: number
  positionId: number
  companyId: number
  positionName: string
  companyName: string
  location?: string
  salary?: string
  duration?: string
  coverLetter?: string
  resumeUrl?: string
  studentName?: string
  studentNo?: string
  phone?: string
  email?: string
  major?: string
  grade?: string
  selfIntroduction?: string
  status?: string
  rejectReason?: string
  applyDate?: string
  createTime?: string
  updateTime?: string
}

export default {
  list() {
    return request({
      url: '/student/job-applications',
      method: 'get'
    })
  },

  create(data: StudentJobApplication) {
    return request({
      url: '/student/job-applications',
      method: 'post',
      data
    })
  },

  delete(id: number) {
    return request({
      url: `/student/job-applications/${id}`,
      method: 'delete'
    })
  }
}