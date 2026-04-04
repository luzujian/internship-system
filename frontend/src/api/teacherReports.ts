import request from '../utils/request'

export interface CoreMetrics {
  companyCount: number
  companyChange: number
  internshipRate: number
  internshipRateChange: number
  approvalCount: number
  approvalCountChange: number
  resourceDownloads: number
  resourceDownloadsChange: number
}

export interface CompanyTrend {
  label: string
  value: number
}

export interface CompanyDetail {
  id: number
  name: string
  joinDate: string
  positionCount: number
  admittedCount: number
  status: string
  tags: string[]
}

export interface StudentInternshipDetail {
  id: number
  college: string
  major: string
  totalCount: number
  internshipCount: number
  internshipRate: number
}

export interface ApprovalDetail {
  id: number
  type: string
  totalCount: number
  approvedCount: number
  rejectedCount: number
  approvalRate: number
}

export const reportsApi = {
  async getCoreMetrics(params?: { timeRange?: string; startDate?: string; endDate?: string }) {
    try {
      const response = await request.get<{ code: number; message: string; data: CoreMetrics }>('/admin/reports/metrics', { params })
      return response
    } catch (error) {
      throw new Error(`Failed to fetch core metrics: ${error}`)
    }
  },

  async getCompanyTrend(params?: { year?: number }) {
    try {
      const response = await request.get<{ code: number; message: string; data: CompanyTrend[] }>('/admin/reports/company-trend', { params })
      return response
    } catch (error) {
      throw new Error(`Failed to fetch company trend: ${error}`)
    }
  },

  async getCompanyDetails(params?: { page?: number; pageSize?: number }) {
    try {
      const response = await request.get<{ code: number; message: string; data: CompanyDetail[] }>('/admin/reports/companies', { params })
      return response
    } catch (error) {
      throw new Error(`Failed to fetch company details: ${error}`)
    }
  },

  async getCompanyDetailsCount() {
    try {
      const response = await request.get<{ code: number; message: string; data: number }>('/admin/reports/companies/count')
      return response
    } catch (error) {
      throw new Error(`Failed to fetch company details count: ${error}`)
    }
  },

  async getStudentInternshipDetails(params?: { page?: number; pageSize?: number }) {
    try {
      const response = await request.get<{ code: number; message: string; data: StudentInternshipDetail[] }>('/admin/reports/students', { params })
      return response
    } catch (error) {
      throw new Error(`Failed to fetch student internship details: ${error}`)
    }
  },

  async getStudentInternshipDetailsCount() {
    try {
      const response = await request.get<{ code: number; message: string; data: number }>('/admin/reports/students/count')
      return response
    } catch (error) {
      throw new Error(`Failed to fetch student internship details count: ${error}`)
    }
  },

  async getApprovalDetails(params?: { page?: number; pageSize?: number }) {
    try {
      const response = await request.get<{ code: number; message: string; data: ApprovalDetail[] }>('/admin/reports/approvals', { params })
      return response
    } catch (error) {
      throw new Error(`Failed to fetch approval details: ${error}`)
    }
  },

  async getApprovalDetailsCount() {
    try {
      const response = await request.get<{ code: number; message: string; data: number }>('/admin/reports/approvals/count')
      return response
    } catch (error) {
      throw new Error(`Failed to fetch approval details count: ${error}`)
    }
  },

  async exportReport(params?: { timeRange?: string; startDate?: string; endDate?: string; type?: string }) {
    try {
      return await request.get('/admin/reports/export', {
        params,
        responseType: 'blob'
      })
    } catch (error) {
      throw new Error(`Failed to export report: ${error}`)
    }
  },

  async exportCompanyReport() {
    try {
      return await request.get('/admin/reports/export/companies', {
        responseType: 'blob'
      })
    } catch (error) {
      throw new Error(`Failed to export company report: ${error}`)
    }
  },

  async exportStudentReport() {
    try {
      return await request.get('/admin/reports/export/students', {
        responseType: 'blob'
      })
    } catch (error) {
      throw new Error(`Failed to export student report: ${error}`)
    }
  },

  async exportApprovalReport() {
    try {
      return await request.get('/admin/reports/export/approvals', {
        responseType: 'blob'
      })
    } catch (error) {
      throw new Error(`Failed to export approval report: ${error}`)
    }
  }
}
