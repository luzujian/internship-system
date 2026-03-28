import request from '@/utils/request'

export interface Teacher {
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

export interface TeacherCourse {
  id: number
  name: string
  teacherId: number
  description?: string
  createTime?: string
  updateTime?: string
}

export interface TeacherStudent {
  id: number
  name: string
  studentId: number
  courseId: number
  createTime?: string
}

export interface TeacherNotification {
  id: number
  title: string
  content: string
  teacherId: number
  isRead?: boolean
  createTime?: string
  updateTime?: string
}

export interface TeacherEvaluation {
  id: number
  content: string
  rating: number
  courseId: number
  studentId: number
  createTime?: string
}

export interface TeacherStats {
  assignmentCount: number
  pendingSubmissions: number
  studentCount: number
}

const TeacherService = {
  // 获取仪表盘统计数据
  getDashboardStats: (teacherId: number) => {
    return request.get<TeacherStats>('/teacher/dashboard/stats', {
      params: { teacherId }
    })
  },

  // 获取教师信息
  getTeacherInfo: (teacherId: number) => {
    return request.get<Teacher>(`/teacher/${teacherId}`)
  },

  // 获取当前登录教师信息
  getCurrentTeacherInfo: () => {
    return request.get<Teacher>('/teacher/current')
  },

  // 更新教师信息
  updateTeacherInfo: (teacherId: number, teacherData: Partial<Teacher>) => {
    return request.put(`/teacher/${teacherId}`, teacherData)
  },

  // 获取教师课程
  getTeacherCourses: (params?: Record<string, unknown>) => {
    return request.get<TeacherCourse[]>('/teacher/courses', { params })
  },

  // 添加课程
  addCourse: (courseData: Omit<TeacherCourse, 'id' | 'createTime' | 'updateTime'>) => {
    return request.post('/teacher/courses/create', courseData)
  },

  // 更新课程
  updateCourse: (courseId: number, courseData: Partial<TeacherCourse>) => {
    return request.put('/teacher/courses/edit', courseData)
  },

  // 删除课程
  deleteCourse: (courseId: number) => {
    return request.delete(`/teacher/courses/${courseId}`)
  },

  // 获取课程下的学生
  getCourseStudents: (courseId: number, page = 1, pageSize = 10) => {
    return request.get<TeacherStudent[]>(`/teacher/courses/${courseId}/students`, {
      params: { page, pageSize }
    })
  },

  // 获取属于当前教师课程的学生
  getStudentsByTeacher: (teacherId: number, params?: {
    page?: number
    pageSize?: number
    searchName?: string
    classId?: number
  }) => {
    return request.get<TeacherStudent[]>('/teacher/students', {
      params: {
        teacherId,
        page: params?.page || 1,
        pageSize: params?.pageSize || 10,
        searchName: params?.searchName,
        classId: params?.classId
      }
    })
  },

  // 获取可用学生列表
  getAvailableStudents: (params?: Record<string, unknown>) => {
    return request.get<TeacherStudent[]>('/teacher/students/available', { params })
  },

  // 添加学生到课程
  addStudentsToCourse: (courseId: number, studentIds: number[]) => {
    return request.post(`/teacher/courses/${courseId}/students`, {
      studentIds
    })
  },

  // 从课程中移除学生
  removeStudentFromCourse: (courseId: number, studentId: number) => {
    return request.delete(`/teacher/courses/${courseId}/students/${studentId}`)
  },

  // 获取教师发布的作业统计
  getTeacherAssignmentStats: (teacherId: number) => {
    return request.get('/teacher/assignments/stats', {
      params: { teacherId }
    })
  },

  // 获取教师通知列表
  getTeacherNotifications: (teacherId: number, page = 1, pageSize = 10) => {
    return request.get<TeacherNotification[]>('/teacher/notifications', {
      params: { teacherId, page, pageSize }
    })
  },

  // 发送通知
  sendNotification: (notificationData: Omit<TeacherNotification, 'id' | 'createTime' | 'updateTime'>) => {
    return request.post('/teacher/notifications', notificationData)
  },

  // 标记通知为已读
  markNotificationAsRead: (notificationId: number) => {
    return request.put(`/teacher/notifications/${notificationId}/read`)
  },

  // 批量标记通知为已读
  batchMarkNotificationsAsRead: (notificationIds: number[]) => {
    return request.put('/teacher/notifications/batch/read', {
      notificationIds
    })
  },

  // 获取教师评价统计
  getTeacherEvaluationStats: (teacherId: number) => {
    return request.get('/teacher/evaluations/stats', {
      params: { teacherId }
    })
  },

  // 获取课程评价列表
  getCourseEvaluations: (courseId: number, page = 1, pageSize = 10) => {
    return request.get<TeacherEvaluation[]>(`/teacher/courses/${courseId}/evaluations`, {
      params: { page, pageSize }
    })
  },

  // 获取所有教师列表
  getTeachers: async (page = 1, pageSize = 10) => {
    try {
      const response = await request.get<Teacher[]>('/admin/teachers', { params: { page, pageSize } })
      return response
    } catch (error) {
      console.error('获取教师列表失败:', error)
      throw new Error('获取教师列表失败，请检查网络连接或联系管理员')
    }
  },

  // 获取教师通知列表
  getNotifications: (limit: number = 5) => {
    return request.get('/teacher/notifications', {
      params: { limit }
    })
  }
}

export default TeacherService
