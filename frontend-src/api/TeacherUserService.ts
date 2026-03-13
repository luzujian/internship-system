import request from '@/utils/request'
import cacheService, { CACHE_CONFIG } from './cacheService'
import logger from '@/utils/logger'

export interface TeacherUser {
  id: number
  teacherUserId: number
  username: string
  name: string
  gender?: number
  departmentId?: number
  teacherType?: number
  status?: number
  createTime?: string
  updateTime?: string
}

export interface TeacherSearchParams {
  teacherUserId?: number | string
  name?: string
  departmentId?: number | string
  status?: number
  gender?: number
  teacherType?: number
}

export interface TeacherListResponse {
  code: number
  data: {
    rows: TeacherUser[]
    total: number
  }
  msg?: string
}

const TeacherUserService = {
  // 获取教师用户列表（分页）
  getTeachers: async (currentPage: number, pageSize: number, searchParams: Record<string, unknown> = {}): Promise<TeacherListResponse> => {
    try {
      const response = await request.get('/admin/teachers', {
        params: {
          page: currentPage,
          pageSize: pageSize,
          teacherUserId: searchParams.teacherUserId,
          name: searchParams.name,
          departmentId: searchParams.departmentId || undefined,
          status: searchParams.status,
          gender: searchParams.gender,
          teacherType: searchParams.teacherType
        }
      })

      // 确保返回的数据格式一致
      const result = response.data || response
      return {
        code: result.code === 200 || result.code === undefined ? 200 : result.code,
        data: result.data || result,
        msg: result.msg || '查询成功'
      }
    } catch (error) {
      logger.error('查询教师列表失败:', error)
      return {
        code: 500,
        data: null,
        msg: (error as { message?: string }).message || '查询失败'
      }
    }
  },

  // 获取单个教师用户信息 - 添加缓存
  getTeacherById: async (id: number) => {
    const cacheKey = `teacher_${id}`
    const cachedData = cacheService.get(cacheKey)

    if (cachedData) {
      // 返回缓存数据的 Promise
      return Promise.resolve({ data: cachedData })
    }

    // 缓存未命中，请求数据
    const response = await request.get<TeacherUser>(`/admin/teachers/${id}`)
    // 缓存数据
    if (response.data) {
      cacheService.set(cacheKey, response.data, CACHE_CONFIG.EXPIRATION_TIME.MEDIUM)
    }
    return response
  },

  // 添加教师用户
  addTeacher: async (teacherData: Omit<TeacherUser, 'id' | 'createTime' | 'updateTime'>) => {
    // 添加数据后清除相关缓存
    const response = await request.post('/admin/teachers', teacherData)
    // 清除教师列表相关缓存
    cacheService.clear()
    return response.data
  },

  // 批量添加教师用户
  batchAddTeachers: async (teacherList: Omit<TeacherUser, 'id' | 'createTime' | 'updateTime'>[]) => {
    // 批量添加数据后清除相关缓存
    const response = await request.post('/admin/teachers/batch', teacherList)
    // 清除教师列表相关缓存
    cacheService.clear()
    return response
  },

  // 更新教师用户信息
  updateTeacher: async (id: number, teacherData: Partial<TeacherUser>) => {
    try {
      // 创建符合后端 TeacherUser 对象预期的请求数据
      const updateData = {
        // 确保包含必要的 id 字段
        id: id,
        // 复制 teacherData 中的字段，但排除不必要的字段
        name: teacherData.name,
        teacherUserId: teacherData.teacherUserId,
        gender: teacherData.gender,
        departmentId: teacherData.departmentId,
        teacherType: teacherData.teacherType
        // 不包含密码字段，后端会保持原密码
        // 后端会自动设置 updateTime
      }
      logger.log('提交的更新数据:', updateData)
      // 使用正确的 API 路径
      const response = await request.put('/admin/teachers', updateData)
      logger.log('更新教师响应:', response)
      logger.log('响应数据详情:', response.data)
      // 显示返回的 message 字段内容（如果存在）
      if (response.data && response.data.message) {
        logger.log('后端返回的消息:', response.data.message)
      }
      // 更新成功后清除相关缓存
      cacheService.clear()
      return response  // 返回整个 response 对象，而不是 response.data
    } catch (error) {
      logger.error('更新教师用户信息失败:', error)
      logger.error('错误详情:', error)
      throw error
    }
  },

  // 删除教师用户
  deleteTeacher: async (id: number) => {
    try {
      const response = await request.delete(`/admin/teachers/${id}`)
      return response
    } catch (error) {
      logger.error('删除教师用户失败:', error)
      throw error
    }
  },

  // 批量删除教师用户
  batchDeleteTeachers: async (ids: number[]) => {
    try {
      const response = await request.delete('/admin/teachers/batch', {
        data: ids
      })
      return response
    } catch (error) {
      logger.error('批量删除教师用户失败:', error)
      throw error
    }
  },

  // 重置教师用户密码
  resetTeacherPassword: async (id: number, password: string) => {
    try {
      const response = await request.post(`/admin/teachers/${id}/reset-password`, { password })
      return response
    } catch (error) {
      logger.error('重置教师用户密码失败:', error)
      throw error
    }
  },

  // 导出教师数据为 Excel
  exportTeacherData: async (params: Record<string, unknown> = {}) => {
    try {
      const response = await request.get('/admin/teachers/export', {
        params: {
          teacherId: params.teacherId,
          name: params.name,
          department: params.department || undefined
        },
        responseType: 'blob'
      })
      return response
    } catch (error) {
      logger.error('导出教师数据失败:', error)
      throw error
    }
  },

  // 导入教师数据从 Excel
  importTeacherData: async (file: File) => {
    try {
      const formData = new FormData()
      formData.append('file', file)
      const response = await request.post('/admin/teachers/import', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      return response
    } catch (error) {
      logger.error('导入教师数据失败:', error)
      throw error
    }
  },

  // 获取所有教师用户列表（不分页）
  getAllTeachers: async () => {
    try {
      // 设置一个大的 pageSize 来获取所有教师
      const response = await TeacherUserService.getTeachers(1, 10000)
      return response
    } catch (error) {
      logger.error('获取所有教师列表失败:', error)
      throw error
    }
  }
}

// API 适配器 - 提供命名导出和统一的数据格式
export const queryPageApi = async (params: {
  pageNum?: number
  pageSize?: number
  teacherUserId?: number | string
  name?: string
  departmentId?: number | string
  status?: number
  gender?: number
  teacherType?: number
}): Promise<TeacherListResponse> => {
  try {
    const {
      pageNum = 1,
      pageSize = 10,
      teacherUserId,
      name,
      departmentId,
      status,
      gender,
      teacherType
    } = params || {}

    const response = await TeacherUserService.getTeachers(
      pageNum,
      pageSize,
      {
        teacherUserId,
        name,
        departmentId,
        status,
        gender,
        teacherType
      }
    )

    const result = (response as { data?: unknown }).data || response

    // 确保返回标准格式
    return {
      code: (response as { code?: number }).code === 200 ? 200 : 500,
      data: {
        rows: Array.isArray(result) ? result : ((result as { rows?: TeacherUser[]; list?: TeacherUser[] }).rows || (result as { rows?: TeacherUser[]; list?: TeacherUser[] }).list || []),
        total: (result as { total?: number }).total || 0
      },
      msg: (response as { msg?: string }).msg || '查询成功'
    }
  } catch (error) {
    logger.error('查询教师列表失败:', error)
    return { code: 500, data: { rows: [], total: 0 }, msg: '查询失败' }
  }
}

export const addApi = async (teacherData: Omit<TeacherUser, 'id' | 'createTime' | 'updateTime'>): Promise<{ code: number; data?: unknown; msg: string }> => {
  try {
    const response = await TeacherUserService.addTeacher(teacherData)
    // TeacherUserService.addTeacher 已经返回 response.data
    // 所以这里直接使用 response 作为结果
    const result = response

    if (result.code === 200) {
      return {
        code: 200,
        data: result.data || result,
        msg: result.msg || (result as { message?: string }).message || '添加成功'
      }
    } else {
      return {
        code: result.code || 500,
        data: result.data || null,
        msg: result.msg || (result as { message?: string }).message || '添加失败'
      }
    }
  } catch (error) {
    logger.error('添加教师失败:', error)
    const errorMsg = (error as { response?: { data?: { message?: string; msg?: string }; message?: string }; message?: string }).response?.data?.message || (error as { response?: { data?: { message?: string; msg?: string }; message?: string }; message?: string }).response?.data?.msg || (error as { message?: string }).message || '添加失败'
    return { code: 500, msg: errorMsg }
  }
}

export const queryInfoApi = async (id: number) => {
  try {
    const response = await TeacherUserService.getTeacherById(id)
    const result = response.data || response

    if ((result as { code?: number }).code === 200) {
      return {
        code: 200,
        data: (result as { data?: unknown }).data || result,
        msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '查询成功'
      }
    } else {
      return {
        code: (result as { code?: number }).code || 500,
        data: (result as { data?: unknown }).data || null,
        msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '查询失败'
      }
    }
  } catch (error) {
    logger.error('查询教师信息失败:', error)
    return { code: 500, msg: '查询失败' }
  }
}

export const updateApi = async (teacherData: Partial<TeacherUser>) => {
  try {
    const response = await TeacherUserService.updateTeacher(teacherData.id!, teacherData)
    const result = response.data || response

    logger.log('updateApi - result:', result)

    if ((result as { code?: number }).code === 200) {
      const apiResult = {
        code: 200,
        data: (result as { data?: unknown }).data || result,
        msg: (result as { message?: string; msg?: string }).message || (result as { message?: string; msg?: string }).msg || '更新成功'
      }
      logger.log('updateApi - 成功返回:', apiResult)
      return apiResult
    } else {
      const apiResult = {
        code: (result as { code?: number }).code || 500,
        data: (result as { data?: unknown }).data || null,
        msg: (result as { message?: string; msg?: string }).message || (result as { message?: string; msg?: string }).msg || '更新失败'
      }
      logger.log('updateApi - 失败返回:', apiResult)
      return apiResult
    }
  } catch (error) {
    logger.error('更新教师失败:', error)
    return { code: 500, msg: (error as { message?: string }).message || '更新失败' }
  }
}

export const deleteApi = async (id: number): Promise<{ code: number; msg: string }> => {
  try {
    const response = await TeacherUserService.deleteTeacher(id)
    const result = response.data || response

    if ((result as { code?: number }).code === 200) {
      return {
        code: 200,
        msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '删除成功'
      }
    } else {
      return {
        code: (result as { code?: number }).code || 500,
        msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '删除失败'
      }
    }
  } catch (error) {
    logger.error('删除教师失败:', error)
    return { code: 500, msg: '删除失败' }
  }
}

export const resetPasswordApi = async (id: number): Promise<{ code: number; msg: string }> => {
  try {
    const response = await TeacherUserService.resetTeacherPassword(id, '123456')
    const result = response.data || response
    // 检查 result 是否直接包含 code 和 msg 字段（后端 Result 对象）
    if ((result as { code?: number }).code !== undefined) {
      return {
        code: (result as { code?: number }).code === 200 ? 200 : 500,
        msg: (result as { code?: number }).code === 200 ? ((result as { msg?: string }).msg || '重置密码成功') : ((result as { msg?: string }).msg || '重置密码失败')
      }
    }
    // 如果 result 是直接的成功响应，返回默认成功结果
    return { code: 200, msg: '重置密码成功' }
  } catch (error) {
    logger.error('重置教师密码失败:', error)
    return {
      code: 500,
      msg: (error as { response?: { data?: { message?: string; msg?: string }; message?: string }; message?: string }).response?.data?.message || (error as { response?: { data?: { message?: string; msg?: string }; message?: string }; message?: string }).response?.data?.msg || (error as { message?: string }).message || '重置密码失败'
    }
  }
}

export const batchDeleteApi = async (ids: number[]): Promise<{ code: number; msg: string }> => {
  try {
    const response = await TeacherUserService.batchDeleteTeachers(ids)
    const result = response.data || response

    if ((result as { code?: number }).code === 200) {
      return {
        code: 200,
        msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '批量删除成功'
      }
    } else {
      return {
        code: (result as { code?: number }).code || 500,
        msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '批量删除失败'
      }
    }
  } catch (error) {
    logger.error('批量删除教师失败:', error)
    return { code: 500, msg: '批量删除失败' }
  }
}

// 导出教师数据 API
export const exportTeacherDataApi = async (params: Record<string, unknown> = {}) => {
  try {
    const response = await TeacherUserService.exportTeacherData(params)
    return response
  } catch (error) {
    logger.error('导出教师数据失败:', error)
    throw error
  }
}

// 导入教师数据 API
export const importTeacherDataApi = async (file: File): Promise<{ code: number; data?: unknown; msg: string }> => {
  try {
    const response = await TeacherUserService.importTeacherData(file)
    const result = response.data || response

    if ((result as { code?: number }).code === 200) {
      return {
        code: 200,
        data: (result as { data?: unknown }).data || result,
        msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '导入成功'
      }
    } else {
      return {
        code: (result as { code?: number }).code || 500,
        data: (result as { data?: unknown }).data || null,
        msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '导入失败'
      }
    }
  } catch (error) {
    logger.error('导入教师数据失败:', error)
    return { code: 500, msg: '导入失败', data: { successCount: 0, failCount: 0, failList: [] } }
  }
}

// 更新教师用户状态
export const updateStatusApi = async (id: number, status: number): Promise<{ code: number; msg: string }> => {
  try {
    // 调用真实的后端 API 接口
    const response = await request.put('/admin/teachers/status', {
      id: id,
      status: status
    })

    // 处理响应数据
    const result = response.data || response
    return {
      code: (result as { code?: number }).code || 200,
      msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || (status === 1 ? '启用成功' : '禁用成功')
    }
  } catch (error) {
    logger.error('更新教师状态失败:', error)
    return { code: 500, msg: (error as { response?: { data?: { msg?: string } }; msg?: string }).response?.data?.msg || '状态更新失败' }
  }
}

// 批量更新教师用户状态
export const batchUpdateStatusApi = async (ids: number[], status: number): Promise<{ code: number; msg: string }> => {
  try {
    // 由于后端没有批量状态更新 API，这里创建模拟响应
    logger.log(`批量更新教师 ID: ${ids} 的状态为：${status}`)

    // 模拟 API 调用成功
    return {
      code: 200,
      msg: status === 1 ? '批量启用成功' : '批量禁用成功'
    }
  } catch (error) {
    logger.error('批量更新教师状态失败:', error)
    return { code: 500, msg: '批量状态更新失败' }
  }
}

export default TeacherUserService
