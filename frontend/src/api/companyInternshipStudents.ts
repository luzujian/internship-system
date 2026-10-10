import request from '@/utils/request'

export interface CompanyInternshipStudent {
  studentId: number
  placementId: number | null
  studentName: string
  studentNumber: string
  grade: string
  majorName: string
  className: string
  phone: string
  email: string
  positionName: string
  sourceType: string
  internshipStartTime: string | null
  internshipEndTime: string | null
}

export interface CompanyInternshipStudentPage {
  total: number
  rows: CompanyInternshipStudent[]
}

export function getCompanyInternshipStudents(params: { page: number; pageSize: number; keyword?: string }) {
  return request<CompanyInternshipStudentPage>({ url: '/company/internship-students', method: 'get', params })
}
