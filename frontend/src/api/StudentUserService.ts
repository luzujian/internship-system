import request from '@/utils/request'
import cacheService, { CACHE_CONFIG } from './cacheService'
import { getRoleFromPath } from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import logger from '@/utils/logger'

export interface StudentUser {
  id: number
  studentId: number
  username: string
  name: string
  gender?: number
  grade?: number
  majorId?: number
  classId?: number
  status?: number
  createTime?: string
  updateTime?: string
}

export interface StudentSearchParams {
  studentId?: number | string
  name?: string
  grade?: number | string
  majorId?: number | string
  classId?: number | string
  status?: number
  gender?: number
}

export interface StudentListResponse {
  code: number
  data: {
    rows: StudentUser[]
    total: number
  }
  msg?: string
}

const StudentUserService = {
  getStudents: async (currentPage: number, pageSize: number, searchParams: Record<string, unknown> = {}): Promise<StudentListResponse> => {
    try {
      const role = getRoleFromPath()
      let apiPath = ''
      let params: Record<string, unknown> = {}

      if (role === 'ROLE_TEACHER') {
        apiPath = '/teacher/students'
        let teacherId: string | null = localStorage.getItem('teacherId') || localStorage.getItem('userId')

        if (!teacherId) {
          const authStore = useAuthStore()
          teacherId = authStore.user?.id?.toString() || null
        }

        logger.log('使用 teacherId:', teacherId, '查询学生列表')

        params = {
          teacherId: teacherId || undefined,
          page: currentPage || 1,
          pageSize: pageSize || 10,
          searchName: searchParams.name,
          classId: searchParams.classId
        }
      } else {
        apiPath = '/admin/students'
        params = {
          page: currentPage,
          pageSize: pageSize,
          studentId: searchParams.studentId,
          name: searchParams.name,
          grade: searchParams.grade ? parseInt(searchParams.grade as string) : undefined,
          majorId: searchParams.majorId,
          classId: searchParams.classId,
          status: searchParams.status,
          gender: searchParams.gender
        }
      }

      const response = await request.get(apiPath, { params })
      logger.log('getStudents - axios response:', response)
      logger.log('getStudents - response.code:', response?.code)
      logger.log('getStudents - response.data:', response?.data)
      logger.log('getStudents - response.data.rows:', response?.data?.rows)

      const result = response || { code: 500, data: null, msg: '响应为空' }
      logger.log('getStudents - 返回的 result:', result)
      return result
    } catch (error) {
      logger.error('查询学生列表失败:', error)

      const errorCode = (error as { response?: { status?: number } }).response?.status || 500
      let errorMsg = (error as { message?: string }).message || '查询失败'

      if ((error as { response?: Record<string, unknown> }).response) {
        if (errorCode === 403) {
          errorMsg = '您没有权限访问学生列表，请检查您的权限设置'
        } else if (errorCode === 401) {
          errorMsg = '登录已过期，请重新登录'
        } else if (errorCode === 404) {
          errorMsg = '请求的接口不存在'
        } else if ((error as { response?: { data?: { message?: string } } }).response?.data?.message) {
          errorMsg = (error as { response?: { data?: { message?: string } } }).response?.data?.message as string
        }
      } else if ((error as { request?: unknown }).request) {
        errorMsg = '网络请求超时，请检查网络连接'
      }

      return {
        code: errorCode,
        data: null,
        msg: errorMsg
      }
    }
  },

  getStudentById: async (id: number) => {
    const cacheKey = `student_${id}`
    const cachedData = cacheService.get(cacheKey)

    if (cachedData) {
      return Promise.resolve({ data: cachedData })
    }

    const response = await request.get<StudentUser>(`/admin/students/${id}`)
    if (response.data) {
      cacheService.set(cacheKey, response.data, CACHE_CONFIG.EXPIRATION_TIME.MEDIUM)
    }
    return response
  },

  getStudentCourses: async (studentId: number) => {
    try {
      const response = await request.get(`/admin/student-users/${studentId}/courses`)
      return {
        code: (response && response.code === 200) || response?.code === undefined ? 200 : response.code,
        data: response.data || response,
        msg: response.msg || '查询成功'
      }
    } catch (error) {
      logger.error('查询学生课程失败:', error)
      return {
        code: 500,
        data: null,
        msg: (error as { message?: string }).message || '查询失败'
      }
    }
  },

  addStudent: async (studentData: Omit<StudentUser, 'id' | 'createTime' | 'updateTime'>) => {
    const response = await request.post('/admin/students', studentData)
    cacheService.clear()
    return response
  },

  batchAddStudents: async (studentList: Omit<StudentUser, 'id' | 'createTime' | 'updateTime'>[]) => {
    const response = await request.post('/admin/students/batch', studentList)
    cacheService.clear()
    return response
  },

  updateStudent: async (id: number, studentData: Partial<StudentUser>) => {
    try {
      const updateData = {
        id: id,
        name: studentData.name,
        studentId: studentData.studentId,
        gender: studentData.gender,
        grade: studentData.grade,
        majorId: studentData.majorId,
        classId: studentData.classId
      }
      logger.log('提交的更新数据:', updateData)
      const response = await request.put('/admin/students', updateData)
      logger.log('更新学生响应:', response)
      logger.log('响应数据详情:', response)
      if (response && response.message) {
        logger.log('后端返回的消息:', response.message)
      }
      cacheService.clear()
      return response
    } catch (error) {
      logger.error('更新学生用户信息失败:', error)
      logger.error('错误详情:', error)
      throw error
    }
  },

  deleteStudent: async (id: number) => {
    try {
      const response = await request.delete(`/admin/students/${id}`)
      return response
    } catch (error) {
      logger.error('删除学生用户失败:', error)
      throw error
    }
  },

  batchDeleteStudents: async (ids: number[]) => {
    try {
      const response = await request.delete('/admin/students/batch', {
        data: ids
      })
      return response
    } catch (error) {
      logger.error('批量删除学生用户失败:', error)
      throw error
    }
  },

  resetStudentPassword: async (id: number, password: string) => {
    try {
      const response = await request.post(`/admin/students/${id}/reset-password`, { password })
      return response
    } catch (error) {
      logger.error('重置学生用户密码失败:', error)
      throw error
    }
  },

  getAllStudents: async () => {
    try {
      const response = await StudentUserService.getStudents(1, 10000)
      return response
    } catch (error) {
      logger.error('获取所有学生列表失败:', error)
      throw error
    }
  },

  getStudentsByClassId: async (classId: number) => {
    try {
      const response = await request.get(`/admin/students/class/${classId}`)
      return response
    } catch (error) {
      logger.error('根据班级 ID 获取学生列表失败:', error)
      throw error
    }
  }
}

export const queryPageApi = async (params: {
  pageNum?: number
  pageSize?: number
  studentId?: number | string
  name?: string
  grade?: number | string
  majorId?: number | string
  classId?: number | string
  status?: number
  gender?: number
}): Promise<StudentListResponse> => {
  try {
    const {
      pageNum = 1,
      pageSize = 10,
      studentId,
      name,
      grade,
      majorId,
      classId,
      status,
      gender
    } = params || {}

    const response = await StudentUserService.getStudents(
      pageNum,
      pageSize,
      {
        studentId,
        name,
        grade,
        majorId,
        classId,
        status,
        gender
      }
    )

    logger.log('queryPageApi - response:', response)
    logger.log('queryPageApi - response.code:', (response as { code?: number }).code)
    logger.log('queryPageApi - response.data:', (response as { data?: unknown }).data)

    // 处理嵌套响应格式：{ code: 200, data: { rows: [...], total: 100 } }
    const responseCode = (response as { code?: number }).code
    const responseData = (response as { data?: { rows?: StudentUser[]; list?: StudentUser[]; total?: number } }).data

    let rows: StudentUser[] = []
    let total = 0

    if (responseData) {
      // 从 data 中提取 rows 或 list
      rows = (responseData as { rows?: StudentUser[]; list?: StudentUser[] }).rows || (responseData as { rows?: StudentUser[]; list?: StudentUser[] }).list || []
      total = (responseData as { total?: number }).total || 0
    } else if (Array.isArray(response)) {
      // 如果 response 本身就是数组
      rows = response
      total = response.length
    } else if ((response as { rows?: StudentUser[] }).rows) {
      // 如果 response 直接包含 rows
      rows = (response as { rows?: StudentUser[] }).rows
      total = (response as { total?: number }).total || 0
    }

    logger.log('queryPageApi - 解析后的 rows:', rows)
    logger.log('queryPageApi - 解析后的 total:', total)

    return {
      code: responseCode || 200,
      data: {
        rows,
        total
      },
      msg: (response as { msg?: string }).msg || '查询成功'
    }
  } catch (error) {
    logger.error('查询学生列表失败:', error)
    return { code: 500, data: { rows: [], total: 0 }, msg: '查询失败' }
  }
}

export const addApi = async (studentData: Omit<StudentUser, 'id' | 'createTime' | 'updateTime'>): Promise<{ code: number; data?: unknown; msg: string }> => {
  try {
    const response = await StudentUserService.addStudent(studentData)
    const result = response || { code: 500, msg: '响应为空' }

    if (result.code === 200) {
      return {
        code: 200,
        data: result.data || result,
        msg: result.msg || '添加成功'
      }
    } else {
      return {
        code: result.code || 500,
        data: result.data || null,
        msg: result.msg || (result as { message?: string }).message || '添加失败'
      }
    }
  } catch (error) {
    logger.error('添加学生失败:', error)
    const errorMsg = (error as { response?: { data?: { message?: string; msg?: string }; message?: string }; message?: string }).response?.data?.message || (error as { response?: { data?: { message?: string; msg?: string }; message?: string }; message?: string }).response?.data?.msg || (error as { message?: string }).message || '添加失败'
    return { code: 500, msg: errorMsg }
  }
}

export const queryInfoApi = async (id: number) => {
  try {
    const response = await StudentUserService.getStudentById(id)
    const result = response || { code: 500, msg: '响应为空' }

    if ((result as { code?: number }).code === 200) {
      return {
        code: 200,
        data: (result as { data?: unknown }).data || result,
        msg: (result as { msg?: string }).msg || '查询成功'
      }
    } else {
      return {
        code: (result as { code?: number }).code || 500,
        data: (result as { data?: unknown }).data || null,
        msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '查询失败'
      }
    }
  } catch (error) {
    logger.error('查询学生信息失败:', error)
    return { code: 500, msg: '查询失败' }
  }
}

export const updateApi = async (studentData: Partial<StudentUser>) => {
  try {
    const response = await StudentUserService.updateStudent(studentData.id!, studentData)
    const result = response || { code: 500, msg: '响应为空' }
    return {
      code: (result as { code?: number }).code === 200 ? 200 : 500,
      data: (result as { data?: unknown }).data || result,
      msg: (result as { code?: number }).code === 200 ? ((result as { msg?: string }).msg || '更新成功') : ((result as { msg?: string }).msg || '更新失败')
    }
  } catch (error) {
    logger.error('更新学生失败:', error)
    return { code: 500, msg: (error as { response?: { data?: { message?: string }; message?: string }; message?: string }).response?.data?.message || (error as { message?: string }).message || '更新失败' }
  }
}

export const deleteApi = async (id: number): Promise<{ code: number; msg: string; data?: unknown }> => {
  try {
    if (!id || id <= 0) {
      return { code: 400, msg: '学生用户 ID 无效' }
    }

    logger.log('准备删除学生 ID:', id)

    const response = await StudentUserService.deleteStudent(id)
    const result = response || { code: 500, msg: '响应为空' }
    logger.log('删除学生响应:', response)
    logger.log('删除学生结果:', result)

    const isSuccess = result.code === 200 || result.code === '200' ||
      (result.code === undefined && !(result as { error?: unknown; message?: string }).error && !(result as { message?: string }).message?.includes('fail')) ||
      (typeof result === 'number' && result > 0)

    return {
      code: isSuccess ? 200 : 500,
      msg: isSuccess ? ((result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '删除成功') : ((result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '删除失败'),
      data: result
    }
  } catch (error) {
    logger.error('删除学生异常:', error)
    let errorMsg = '删除学生用户失败'

    if ((error as { response?: Record<string, unknown> }).response) {
      if ((error as { response?: { data?: { message?: string } } }).response?.data && (error as { response?: { data?: { message?: string } } }).response?.data?.message) {
        errorMsg = (error as { response?: { data?: { message?: string } } }).response?.data?.message || '删除失败'
        if (errorMsg.includes('外键约束') || errorMsg.includes('foreign key')) {
          errorMsg = '删除失败：该学生存在关联数据'
        } else if (errorMsg.includes('不存在')) {
          errorMsg = '该学生用户不存在或已被删除'
        }
      } else if ((error as { response?: { status?: number } }).response?.status === 403) {
        errorMsg = '权限不足，无法删除学生用户'
      } else if ((error as { response?: { status?: number } }).response?.status === 404) {
        errorMsg = '该学生用户不存在或已被删除'
      }
    } else if ((error as { request?: unknown }).request) {
      errorMsg = '网络请求超时或服务器无响应\n建议：检查网络连接并重试'
    } else {
      errorMsg = (error as { message?: string }).message || '请求配置错误'
    }

    return { code: 500, msg: errorMsg }
  }
}

export const resetPasswordApi = async (id: number): Promise<{ code: number; msg: string }> => {
  try {
    const response = await StudentUserService.resetStudentPassword(id, '123456')
    const result = response || { code: 500, msg: '响应为空' }

    if (result.code !== undefined) {
      return {
        code: result.code === 200 ? 200 : 500,
        msg: result.code === 200 ? (result.msg || '重置密码成功') : (result.msg || '重置密码失败')
      }
    }
    return { code: 200, msg: '重置密码成功' }
  } catch (error) {
    logger.error('重置学生密码失败:', error)
    return {
      code: 500,
      msg: (error as { response?: { data?: { message?: string; msg?: string }; message?: string }; message?: string }).response?.data?.message || (error as { response?: { data?: { message?: string; msg?: string }; message?: string }; message?: string }).response?.data?.msg || (error as { message?: string }).message || '重置密码失败'
    }
  }
}

export const batchDeleteApi = async (ids: number[]): Promise<{ code: number; msg: string; successCount?: number; failedCount?: number; details?: unknown[] }> => {
  try {
    if (!ids || !Array.isArray(ids) || ids.length === 0) {
      return { code: 400, msg: '请选择要删除的学生用户' }
    }

    const batchSize = 10
    const allResults: unknown[] = []
    let totalSuccess = 0
    let totalFailed = 0

    for (let i = 0; i < ids.length; i += batchSize) {
      const batchIds = ids.slice(i, i + batchSize)

      try {
        const response = await StudentUserService.batchDeleteStudents(batchIds)
        const result = response || { code: 500, msg: '响应为空' }
        logger.log(`批量删除批次 ${Math.floor(i / batchSize) + 1} 响应:`, result)

        const isBatchSuccess = (result as { code?: number | string }).code === 200 || (result as { code?: number | string }).code === '200' ||
          ((result as { code?: number | string }).code === undefined && !(result as { error?: unknown; message?: string }).error && !(result as { message?: string }).message?.includes('fail'))

        if (isBatchSuccess) {
          if (typeof result === 'number') {
            totalSuccess += result
          } else if ((result as { successCount?: number }).successCount !== undefined) {
            totalSuccess += (result as { successCount?: number }).successCount!
          } else {
            totalSuccess += batchIds.length
          }
          allResults.push({
            batch: batchIds,
            success: true,
            message: ((result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '删除成功') as string
          })
        } else {
          totalFailed += batchIds.length
          allResults.push({
            batch: batchIds,
            success: false,
            message: ((result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '删除失败') as string
          })
        }
      } catch (batchError) {
        logger.error(`批次 ${Math.floor(i / batchSize) + 1} 删除失败:`, batchError)
        totalFailed += batchIds.length
        allResults.push({
          batch: batchIds,
          success: false,
          message: (batchError as { message?: string }).message || '处理失败'
        })
      }
    }

    const overallSuccess = totalFailed === 0
    let summaryMessage = ''

    if (overallSuccess) {
      summaryMessage = `成功删除全部 ${totalSuccess} 个学生用户`
    } else if (totalSuccess === 0) {
      summaryMessage = `批量删除失败：${totalFailed} 个学生用户无法删除`
    } else {
      summaryMessage = `部分删除成功：成功 ${totalSuccess} 个，失败 ${totalFailed} 个`
    }

    return {
      code: overallSuccess ? 200 : (totalSuccess > 0 ? 207 : 500),
      msg: summaryMessage,
      successCount: totalSuccess,
      failedCount: totalFailed,
      details: allResults
    }
  } catch (error) {
    logger.error('批量删除学生用户处理异常:', error)

    let errorMsg = '批量删除学生用户失败'

    if ((error as { response?: Record<string, unknown> }).response) {
      if ((error as { response?: { data?: { message?: string } } }).response?.data && (error as { response?: { data?: { message?: string } } }).response?.data?.message) {
        errorMsg = (error as { response?: { data?: { message?: string } } }).response?.data?.message || '批量删除失败'
        if (errorMsg.includes('外键约束')) {
          errorMsg += '\n建议：请先检查学生是否参与了课程、小组或有未完成的作业'
        } else if (errorMsg.includes('超时')) {
          errorMsg += '\n建议：请减少批量删除的数量，分批处理'
        }
      } else {
        errorMsg = `服务器错误：${(error as { response?: { status?: number; statusText?: string } }).response?.status} ${(error as { response?: { status?: number; statusText?: string } }).response?.statusText}`
      }
    } else if ((error as { request?: unknown }).request) {
      errorMsg = '网络请求超时，请检查网络连接并稍后重试'
    } else {
      errorMsg = (error as { message?: string }).message || '请求配置错误'
    }

    return { code: 500, msg: errorMsg }
  }
}

export const importExcelApi = async (formData: FormData): Promise<{ code: number; data?: unknown; msg: string }> => {
  try {
    const response = await request.post('/admin/students/import', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    const result = response || { code: 500, msg: '响应为空' }

    if (result.code === 200) {
      return {
        code: 200,
        data: result.data || result,
        msg: result.msg || '导入成功'
      }
    } else {
      return {
        code: result.code || 500,
        data: result.data || null,
        msg: result.msg || (result as { message?: string }).message || '导入失败'
      }
    }
  } catch (error) {
    logger.error('导入学生数据失败:', error)
    return { code: 500, msg: (error as { response?: { data?: { message?: string; msg?: string }; message?: string }; message?: string }).response?.data?.message || (error as { response?: { data?: { message?: string; msg?: string }; message?: string }; message?: string }).response?.data?.msg || (error as { message?: string }).message || '导入失败' }
  }
}

export const updateStatusApi = async (id: number, status: number): Promise<{ code: number; msg: string }> => {
  try {
    const response = await request.put('/admin/students/status', {
      id: id,
      status: status
    })

    const result = response || { code: 500, msg: '响应为空' }
    return {
      code: result.code || 200,
      msg: result.msg || (status === 1 ? '启用成功' : '禁用成功')
    }
  } catch (error) {
    logger.error('更新学生状态失败:', error)
    return { code: 500, msg: (error as { response?: { data?: { msg?: string } }; msg?: string }).response?.data?.msg || '状态更新失败' }
  }
}

export const batchUpdateStatusApi = async (ids: number[], status: number): Promise<{ code: number; msg: string }> => {
  try {
    const updatePromises = ids.map(id => updateStatusApi(id, status))
    const results = await Promise.all(updatePromises)
    const allSuccess = results.every(result => result.code === 200)

    return {
      code: allSuccess ? 200 : 500,
      msg: status === 1 ? '批量启用成功' : '批量禁用成功'
    }
  } catch (error) {
    logger.error('批量更新学生状态失败:', error)
    return { code: 500, msg: '批量状态更新失败' }
  }
}

export default StudentUserService
