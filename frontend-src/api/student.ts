import request from '@/utils/request'

export interface Student {
  id: number
  username: string
  name: string
  email?: string
  phone?: string
  role?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface StudentDashboardStats {
  internshipCount?: number
  applicationCount?: number
  noticeCount?: number
  [key: string]: unknown
}

const StudentService = {
  // 获取学生仪表盘统计数据
  getDashboardStats: (studentId: number) => {
    return request.get<StudentDashboardStats>('/student/dashboard/stats', {
      params: { studentId }
    })
  },

  // 获取学生个人信息
  getStudentInfo: (studentId: number) => {
    return request.get<Student>('/student/info', {
      params: { studentId }
    })
  }
}

export default StudentService
