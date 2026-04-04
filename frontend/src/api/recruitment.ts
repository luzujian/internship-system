import request from '@/utils/request'

export interface RecruitmentPosition {
  id: number
  title: string
  companyId?: number
  categoryId?: number
  description?: string
  requirements?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface RecruitmentApplication {
  id: number
  positionId: number
  studentId: number
  status?: number
  resumeUrl?: string
  createTime?: string
  updateTime?: string
}

export interface RecruitmentResponse {
  code: number
  data: RecruitmentPosition | RecruitmentApplication | RecruitmentPosition[] | RecruitmentApplication[]
  msg?: string
}

// 分页数据接口
export interface PaginatedData<T> {
  rows: T[]
  total: number
}

// 定义响应类型的 Axios 响应
type AxiosResponseWithMeta<T> = {
  data: {
    code: number
    data: T
    msg?: string
  }
}

const recruitmentService = {
  getPositions: (params?: Record<string, unknown>) => {
    return request.get<PaginatedData<RecruitmentPosition>>('/admin/recruitment/positions', { params }) as unknown as AxiosResponseWithMeta<PaginatedData<RecruitmentPosition>>
  },

  getApplications: (params?: Record<string, unknown>) => {
    return request.get<PaginatedData<RecruitmentApplication>>('/admin/recruitment/applications', { params }) as unknown as AxiosResponseWithMeta<PaginatedData<RecruitmentApplication>>
  },

  getStatistics: () => {
    return request.get('/admin/recruitment/statistics') as unknown as AxiosResponseWithMeta<Record<string, unknown>>
  },

  getPositionDetail: (id: number) => {
    return request.get<RecruitmentPosition>(`/admin/recruitment/position/${id}`) as unknown as AxiosResponseWithMeta<RecruitmentPosition>
  },

  getCompanyPositions: (companyId: number) => {
    return request.get<RecruitmentPosition[]>(`/admin/recruitment/company/${companyId}/positions`) as unknown as AxiosResponseWithMeta<RecruitmentPosition[]>
  }
}

export default recruitmentService
