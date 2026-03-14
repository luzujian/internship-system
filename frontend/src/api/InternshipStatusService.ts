import request from '@/utils/request'

export interface InternshipStatus {
  id: number
  studentId: number
  student: {
    id: number
    name: string
    studentUserId: string
    gender: string
    grade: string
    majorId: number
    classId: number
  }
  companyId: number
  company: {
    id: number
    companyName: string
  }
  positionId: number
  position: {
    id: number
    positionName: string
  }
  status: number
  companyConfirmStatus: number
  internshipStartTime: string
  internshipEndTime: string
  internshipDuration: number
  remark: string
  feedback: string
  createTime: string
  updateTime: string
}

export default {
  getInternshipStatusByCompanyId(companyId: number) {
    return request({
      url: `/admin/internship-status/company/${companyId}`,
      method: 'get'
    })
  },

  getInternshipStatusById(id: number) {
    return request({
      url: `/admin/internship-status/${id}`,
      method: 'get'
    })
  },

  updateInternshipStatus(id: number, status: Partial<InternshipStatus>) {
    return request({
      url: `/admin/internship-status/${id}`,
      method: 'put',
      data: status
    })
  },

  approveInternshipStatus(id: number) {
    return request({
      url: `/admin/internship-status/${id}/approve`,
      method: 'put'
    })
  },

  rejectInternshipStatus(id: number) {
    return request({
      url: `/admin/internship-status/${id}/reject`,
      method: 'put'
    })
  }
}
