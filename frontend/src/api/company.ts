import request from '@/utils/request'

export interface Company {
  id: number
  name: string
  creditCode?: string
  legalPerson?: string
  address?: string
  contact?: string
  phone?: string
  email?: string
  status?: number
  auditStatus?: number
  auditRemark?: string
  createTime?: string
  updateTime?: string
}

export interface CompanyResponse {
  code: number
  data: Company | Company[] | { rows: Company[]; total: number }
  msg?: string
}

// 定义响应类型的 Axios 响应
type AxiosResponseWithMeta<T> = {
  data: {
    code: number
    data: T
    msg?: string
  }
}

/**
 * 企业管理相关 API
 */
const companyService = {
  /**
   * 获取企业列表（分页）
   */
  getCompanies: (params?: {
    page?: number
    pageSize?: number
    companyName?: string
    status?: number
  }) => {
    return request.get<Company[]>('/admin/companies', { params }) as unknown as AxiosResponseWithMeta<Company[]>
  },

  /**
   * 根据 ID 获取企业信息
   */
  getCompanyById: (id: number) => {
    return request.get<Company>(`/admin/companies/${id}`)
  },

  /**
   * 添加企业信息
   */
  addCompany: (company: Omit<Company, 'id' | 'createTime' | 'updateTime'>) => {
    return request.post('/admin/companies', company)
  },

  /**
   * 更新企业信息
   */
  updateCompany: (company: Partial<Company>) => {
    return request.put('/admin/companies', company)
  },

  /**
   * 删除企业信息
   */
  deleteCompany: (id: number) => {
    return request.delete(`/admin/companies/${id}`)
  },

  /**
   * 批量删除企业信息
   */
  batchDeleteCompanies: (ids: number[]) => {
    return request.delete('/admin/companies/batch', { data: ids })
  },

  /**
   * 审核企业注册
   */
  auditCompany: (id: number, params: { status: number; remark?: string }) => {
    return request.put(`/admin/companies/${id}/audit`, params)
  },

  /**
   * 获取待审核企业列表（分页）
   */
  getPendingCompanies: (params?: { page?: number; pageSize?: number }) => {
    return request.get<Company[]>('/admin/companies/pending', { params })
  },

  /**
   * 批量导入企业信息
   */
  importCompanies: (formData: FormData) => {
    return request.post('/admin/companies/import', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  /**
   * 导出企业数据到 Excel
   */
  exportCompanies: (params?: Record<string, unknown>) => {
    return request.get('/admin/companies/export', { params, responseType: 'blob' })
  },

  /**
   * 清除企业撤回申请数据
   */
  clearRecallData: () => {
    return request.delete('/admin/companies/recall/clear')
  },

  /**
   * 重置企业密码
   */
  resetCompanyPassword: (id: number, password: string) => {
    return request.post(`/admin/companies/${id}/reset-password`, { password })
  },

  /**
   * 更新企业状态（启用/禁用）
   */
  updateCompanyStatus: (id: number, status: number) => {
    return request.put(`/admin/companies/${id}/status`, { status: String(status) })
  }
}

export default companyService
