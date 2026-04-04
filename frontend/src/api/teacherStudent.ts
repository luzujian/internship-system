import request from '../utils/request'

export interface ClassItem {
  id: number
  name: string
  grade?: number
  majorId?: number
  teacherId?: string
  teacherName?: string
  studentCount?: number
  confirmedCount?: number
  notFoundCount?: number
  hasOfferCount?: number
}

export interface MajorItem {
  id: number
  name: string
  departmentId?: number
  teacherCount?: number
  studentCount?: number
  confirmedCount?: number
  notFoundCount?: number
  hasOfferCount?: number
}

export interface StudentUser {
  id?: number
  studentId?: string
  name?: string
  className?: string
  majorId?: string
  major?: string
  grade?: string
  gender?: string
  phone?: string
  email?: string
  status?: number
  companyId?: number
  company?: string
  progress?: number
  birthday?: string
  internshipStartDate?: string
  internshipEndDate?: string
  supervisorName?: string
  supervisorPhone?: string
  reportCount?: number
  attendanceRate?: number
  internshipLogCount?: number
  evaluationScore?: number
}

export interface StudentPageResult {
  total: number
  rows: StudentUser[]
  pages: number
  current: number
  pageSize: number
}

export interface StudentFilters {
  studentId?: string
  name?: string
  grade?: string
  major?: string
  class?: string
  status?: string
  company?: string
  search?: string
}

export const studentApi = {
  getStudentList: async (params: {
    page?: number
    pageSize?: number
    studentId?: string
    name?: string
    grade?: string
    major?: string
    className?: string
    status?: number
    gender?: number
    companyId?: number
    companyName?: string
  }) => {
    try {
      const response = await request.get<{ code: number; message: string; data: StudentPageResult }>('/teacher/internship-status', { params })
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch student list: ${error}`)
    }
  },

  getStatistics: async () => {
    try {
      const response = await request.get<{ code: number; message: string; data: { studentCount: number; noOfferCount: number; pendingCount: number; confirmedCount: number; interningCount: number; finishedCount: number; interruptedCount: number; delayedCount: number } }>('/teacher/internship-status/statistics')
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch statistics: ${error}`)
    }
  },

  getStudentById: async (id: number) => {
    try {
      const response = await request.get<{ code: number; message: string; data: StudentUser }>(`/teacher/internship-status/${id}`)
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch student ${id}: ${error}`)
    }
  },

  exportStudentData: async (params: {
    studentId?: string
    name?: string
    grade?: number
    majorId?: string
    classId?: string
    status?: number
  }) => {
    try {
      return await request.get('/teacher/internship-status/export', {
        params,
        responseType: 'blob'
      })
    } catch (error) {
      throw new Error(`Failed to export student data: ${error}`)
    }
  },

  exportBatchData: async (params: {
    scope?: string
    className?: string
    grade?: string
    status?: number
  }) => {
    try {
      return await request.get('/teacher/internship-status/export/batch', {
        params,
        responseType: 'blob'
      })
    } catch (error) {
      throw new Error(`Failed to export batch data: ${error}`)
    }
  },

  getClasses: async () => {
    try {
      console.log('getClasses 开始请求 /teacher/classes')
      const response = await request.get<{ code: number; message: string; data: ClassItem[] }>('/teacher/classes/all')
      console.log('getClasses 响应:', response)
      console.log('getClasses 响应类型:', typeof response)
      console.log('getClasses 响应是否为数组:', Array.isArray(response))
      console.log('getClasses 响应是否为null:', response === null)
      console.log('getClasses 响应是否为undefined:', response === undefined)
      
      if (response === null || response === undefined) {
        console.error('getClasses 响应为空')
        return []
      }
      
      if (Array.isArray(response)) {
        return response
      }
      
      if (response && response.data) {
        return response.data
      }
      
      console.error('getClasses 响应格式异常:', response)
      return []
    } catch (error) {
      console.error('getClasses 错误:', error)
      throw new Error(`Failed to fetch classes: ${error}`)
    }
  },

  getMajors: async () => {
    try {
      console.log('getMajors 开始请求 /teacher/majors')
      const response = await request.get<{ code: number; message: string; data: MajorItem[] }>('/teacher/majors')
      console.log('getMajors 响应:', response)
      console.log('getMajors 响应类型:', typeof response)
      console.log('getMajors 响应是否为数组:', Array.isArray(response))
      console.log('getMajors 响应是否为null:', response === null)
      console.log('getMajors 响应是否为undefined:', response === undefined)
      
      if (response === null || response === undefined) {
        console.error('getMajors 响应为空')
        return []
      }
      
      if (Array.isArray(response)) {
        return response
      }
      
      if (response && response.data) {
        return response.data
      }
      
      console.error('getMajors 响应格式异常:', response)
      return []
    } catch (error) {
      console.error('getMajors 错误:', error)
      throw new Error(`Failed to fetch majors: ${error}`)
    }
  },

  getGrades: async () => {
    try {
      console.log('getGrades 开始请求 /teacher/grades')
      const response = await request.get<{ code: number; message: string; data: number[] }>('/teacher/grades')
      console.log('getGrades 响应:', response)
      console.log('getGrades 响应类型:', typeof response)
      console.log('getGrades 响应是否为数组:', Array.isArray(response))
      console.log('getGrades 响应是否为null:', response === null)
      console.log('getGrades 响应是否为undefined:', response === undefined)
      
      if (response === null || response === undefined) {
        console.error('getGrades 响应为空')
        return []
      }
      
      if (Array.isArray(response)) {
        return response
      }
      
      if (response && response.data) {
        return response.data
      }
      
      console.error('getGrades 响应格式异常:', response)
      return []
    } catch (error) {
      console.error('getGrades 错误:', error)
      throw new Error(`Failed to fetch grades: ${error}`)
    }
  },

  getCompanies: async () => {
    try {
      console.log('getCompanies 开始请求 /teacher/companies')
      const response = await request.get<{ code: number; message: string; data: { rows: any[]; total: number } }>('/teacher/companies')
      console.log('getCompanies 响应:', response)

      if (response === null || response === undefined) {
        console.error('getCompanies 响应为空')
        return []
      }

      // 后端返回的是 PageResult 结构，需要取 rows 数组
      if (response.data && response.data.rows) {
        return response.data.rows
      }

      // 兼容直接返回数组的情况
      if (Array.isArray(response.data)) {
        return response.data
      }

      console.error('getCompanies 响应格式异常:', response)
      return []
    } catch (error) {
      console.error('getCompanies 错误:', error)
      throw new Error(`Failed to fetch companies: ${error}`)
    }
  }
}
