import request from '@/utils/request'
import { applicationMaterialFormData, type ApplicationMaterialSelection } from './jobApplicationMaterials'

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

  create(data: StudentJobApplication, materials?: ApplicationMaterialSelection) {
    return request({
      url: '/student/job-applications',
      method: 'post',
      data: materials ? applicationMaterialFormData(data, materials) : data,
      ...(materials ? { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 } : {})
    })
  },

  delete(id: number) {
    return request({
      url: `/student/job-applications/${id}`,
      method: 'delete'
    })
  }
}
