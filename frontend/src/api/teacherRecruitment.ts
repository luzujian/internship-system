import request from '@/utils/request'
import type { Position } from './PositionService'

export interface RecruitmentPosition extends Position {
  companyName: string
  companyPositionCount: number
  categoryId?: number | null
  categoryName?: string | null
  requiredMajors?: string
  internshipEnvironment?: string
  providesAccommodation?: number
  interviewTime?: string
  interviewLocation?: string
  interviewMethod?: string
  interviewRemark?: string
}

export interface RecruitmentCompany {
  id: number
  companyName: string
  creditCode?: string
  contactPerson?: string
  contactPhone?: string
  contactEmail?: string
  industry?: string
  scale?: string
  province?: string
  city?: string
  district?: string
  detailAddress?: string
  address?: string
  website?: string
  introduction?: string
  companyTag?: string
  cooperationMode?: string
  isInternshipBase?: number
  acceptBackup?: number
  hasTrainingBaseAgreement?: number
  isAlumniUnit?: number
  logo?: string
  photos?: string
  videos?: string
  alumniMembers?: Array<{ id: number; name: string; phone?: string; position?: string }>
  schoolLiaisons?: Array<{ teacherId: number; teacherName?: string; teacherPhone?: string }>
}

export interface RecruitmentDetail {
  position: RecruitmentPosition
  company: RecruitmentCompany
}

export function getRecruitmentPositions(params: {
  page: number; pageSize: number; companyName?: string; positionName?: string; status?: string; categoryId?: number
}) {
  return request.get<{ rows: RecruitmentPosition[]; total: number }>('/teacher/recruitment/positions', { params })
}

export function getRecruitmentDetail(id: number) {
  return request.get<RecruitmentDetail>(`/teacher/recruitment/positions/${id}`)
}
