import request from '../utils/request'

export interface StudentApplication {
  id?: number
  studentId?: number
  studentName?: string
  studentUserId?: string
  grade?: string
  className?: string
  applicationType?: string
  reason?: string
  company?: string
  positionName?: string
  companyCity?: string
  hrManagerName?: string
  hrManagerPhone?: string
  cooperativeCompany?: boolean
  cooperativeCompanyId?: number
  oldCompany?: string
  newCompany?: string
  materials?: Record<string, string>
  status?: string
  rejectReason?: string
  reviewerId?: number
  reviewTime?: string
  applyTime?: string
  createTime?: string
  updateTime?: string
}

export interface CompanyQualification {
  id?: number
  companyId?: number
  companyName?: string
  companyAddress?: string
  contactPerson?: string
  contactPhone?: string
  qualifications?: Record<string, string>
  isPhoneVerified?: boolean
  isInternshipBase?: boolean
  hasReceivedStudents?: boolean
  currentEmployeeCount?: number
  internshipBaseLevel?: string
  companyTags?: string[]
  status?: string
  rejectReason?: string
  reviewerId?: number
  reviewTime?: string
  applyTime?: string
  createTime?: string
  updateTime?: string
}

export interface ApprovalActionResult {
  code: number
  message?: string
  data?: {
    emailNotified?: boolean
  }
}

export interface ApprovalStats {
  selfPracticePending: number
  delayPending: number
  companyQualificationPending: number
}

export const approvalApi = {
  getStats: async () => {
    try {
      const response = await request.get<{ code: number; message: string; data: ApprovalStats }>('/approval/stats')
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch approval stats: ${error}`)
    }
  },

  getStudentApplications: async (params: {
    page?: number
    pageSize?: number
    applicationType?: string
    status?: string
    studentName?: string
    studentUserId?: string
    majorId?: number
    classId?: number
  }) => {
    try {
      const response = await request.get<{ code: number; message: string; data: { total: number; rows: StudentApplication[] } }>('/approval/student-applications', { params })
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch student applications: ${error}`)
    }
  },

  getStudentApplicationById: async (id: number) => {
    try {
      const response = await request.get<{ code: number; message: string; data: StudentApplication }>(`/approval/student-applications/${id}`)
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch student application ${id}: ${error}`)
    }
  },

  approveStudentApplication: async (id: number, reviewerId: number): Promise<ApprovalActionResult> => {
    try {
      const response = await request.post(`/approval/student-applications/${id}/approve`, null, { params: { reviewerId } })
      return response as unknown as ApprovalActionResult
    } catch (error) {
      throw new Error(`Failed to approve student application ${id}: ${error}`)
    }
  },

  rejectStudentApplication: async (id: number, reviewerId: number, rejectReason: string): Promise<ApprovalActionResult> => {
    try {
      const response = await request.post(`/approval/student-applications/${id}/reject`, null, {
        params: { reviewerId, rejectReason }
      })
      return response as unknown as ApprovalActionResult
    } catch (error) {
      throw new Error(`Failed to reject student application ${id}: ${error}`)
    }
  },

  getCompanyQualifications: async (params: {
    page?: number
    pageSize?: number
    status?: string
    companyName?: string
  }) => {
    try {
      const response = await request.get<{ code: number; message: string; data: { total: number; rows: CompanyQualification[] } }>('/approval/company-qualifications', { params })
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch company qualifications: ${error}`)
    }
  },

  getCompanyQualificationById: async (id: number) => {
    try {
      const response = await request.get<{ code: number; message: string; data: CompanyQualification }>(`/approval/company-qualifications/${id}`)
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch company qualification ${id}: ${error}`)
    }
  },

  approveCompanyQualification: async (id: number, reviewerId: number): Promise<ApprovalActionResult> => {
    try {
      const response = await request.post(`/approval/company-qualifications/${id}/approve`, null, { params: { reviewerId } })
      return response as unknown as ApprovalActionResult
    } catch (error) {
      throw new Error(`Failed to approve company qualification ${id}: ${error}`)
    }
  },

  rejectCompanyQualification: async (id: number, reviewerId: number, rejectReason: string): Promise<ApprovalActionResult> => {
    try {
      const response = await request.post(`/approval/company-qualifications/${id}/reject`, null, {
        params: { reviewerId, rejectReason }
      })
      return response as unknown as ApprovalActionResult
    } catch (error) {
      throw new Error(`Failed to reject company qualification ${id}: ${error}`)
    }
  },

  getApplications: async (params: {
    page?: number
    pageSize?: number
    type?: string
    status?: string
    keyword?: string
  }) => {
    try {
      const response = await request.get<{ code: number; message: string; data: any }>('/approval/applications', { params })
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch applications: ${error}`)
    }
  }
}
