import request from '@/utils/request'
import cacheService, { CACHE_CONFIG } from './cacheService'
import logger from '@/utils/logger'
import type { AxiosResponse } from 'axios'

export interface ClassItem {
  id: number
  name: string
  grade?: number
  majorId?: number
  description?: string
  studentCount?: number
  createTime?: string
  updateTime?: string
}

export interface ClassResponse {
  code: number
  data: ClassItem | ClassItem[] | { rows: ClassItem[]; total: number }
  msg?: string
}

type ClassAPIResponse = AxiosResponse<{ code: number; data: ClassItem[]; msg?: string }>

type AxiosResponseWithMeta<T> = {
  data: {
    code: number
    data: T
    msg?: string
  }
}

// 核心服务方法
const ClassService = {
  // 获取所有班级 - 添加缓存
  getClasses: async () => {
    const cacheKey = 'all_classes'

    // 缓存未命中，请求数据
    const response = await request.get<ClassItem[]>('/classes')
    // 缓存数据
    if (response.data) {
      cacheService.set(cacheKey, response.data, CACHE_CONFIG.EXPIRATION_TIME.LONG)
    }
    return response as unknown as ClassAPIResponse
  },

  // 根据 ID 获取班级 - 添加缓存
  getClassById: async (id: number | string) => {
    // 确保 ID 是数字类型
    const numericId = parseInt(id as string)
    if (isNaN(numericId)) {
      logger.error('Invalid class ID:', id)
      return Promise.reject(new Error('Invalid class ID'))
    }

    const cacheKey = `class_${numericId}`
    const cachedData = cacheService.get<ClassItem>(cacheKey)

    if (cachedData) {
      // 返回缓存数据的 Promise
      return Promise.resolve({ data: cachedData })
    }

    // 缓存未命中，请求数据
    const response = await request.get<ClassItem>(`/classes/${numericId}`)
    // 缓存数据
    if (response.data) {
      cacheService.set(cacheKey, response.data, CACHE_CONFIG.EXPIRATION_TIME.MEDIUM)
    }
    return response
  },

  // 根据专业 ID 获取班级 - 添加缓存
  getClassesByMajorId: async (majorId: number | string) => {
    const cacheKey = `class_by_major_${majorId}`
    const cachedData = cacheService.get<ClassItem[]>(cacheKey)

    if (cachedData) {
      // 返回缓存数据的 Promise
      return Promise.resolve({ data: cachedData })
    }

    // 缓存未命中，请求数据
    const response = await request.get<ClassItem[]>(`/classes/major/${majorId}`)
    // 缓存数据
    if (response.data) {
      cacheService.set(cacheKey, response.data, CACHE_CONFIG.EXPIRATION_TIME.MEDIUM)
    }
    return response
  },

  // 添加班级
  addClass: async (classData: Omit<ClassItem, 'id' | 'createTime' | 'updateTime'>) => {
    // 添加数据后清除相关缓存
    const response = await request.post('/classes', classData)
    // 清除班级列表相关缓存
    cacheService.delete('all_classes')
    if (classData.majorId) {
      cacheService.delete(`class_by_major_${classData.majorId}`)
    }
    return response
  },

  // 更新班级
  updateClass: async (id: number | string, classData: Partial<ClassItem>) => {
    // 确保 ID 是数字类型
    const numericId = parseInt(id as string)
    if (isNaN(numericId)) {
      logger.error('Invalid class ID:', id)
      return Promise.reject(new Error('Invalid class ID'))
    }

    // 更新数据后清除相关缓存
    const response = await request.put(`/classes/${numericId}`, classData)
    // 清除班级列表相关缓存
    cacheService.delete('all_classes')
    cacheService.delete(`class_${numericId}`)
    if (classData.majorId) {
      cacheService.delete(`class_by_major_${classData.majorId}`)
    }
    return response
  },

  // 删除班级
  deleteClass: async (id: number | string) => {
    // 确保 ID 是数字类型
    const numericId = parseInt(id as string)
    if (isNaN(numericId)) {
      logger.error('Invalid class ID:', id)
      return Promise.reject(new Error('Invalid class ID'))
    }

    // 删除数据后清除相关缓存
    const response = await request.delete(`/classes/${numericId}`)
    // 清除班级列表相关缓存
    cacheService.delete('all_classes')
    cacheService.delete(`class_${numericId}`)
    return response
  },

  // 批量删除班级
  batchDeleteClass: async (ids: (number | string)[]) => {
    try {
      // 确保 ids 是数组
      if (!Array.isArray(ids)) {
        ids = [ids]
      }

      // 确保所有 ID 都是数字类型
      const numericIds = ids.map(id => {
        const numericId = parseInt(id as string)
        if (isNaN(numericId)) {
          throw new Error(`Invalid class ID: ${id}`)
        }
        return numericId
      })

      logger.log('批量删除班级，IDs:', numericIds)

      // 调用批量删除接口
      const response = await request.delete('/classes/batch', {
        data: { ids: numericIds }
      })

      // 清除相关缓存
      cacheService.delete('all_classes')
      numericIds.forEach(id => {
        cacheService.delete(`class_${id}`)
      })

      return response
    } catch (error) {
      logger.error('批量删除班级失败:', error)
      throw error
    }
  }
}

// API 适配器 - 提供命名导出和统一的数据格式
export const queryPageApi = async (
  name?: string,
  grade?: number | string,
  majorId?: number | string,
  currentPage = 1,
  pageSize = 10
) => {
  try {
    const response = await ClassService.getClasses()
    logger.log('班级 API 响应:', response)
    logger.log('响应类型:', typeof response)
    logger.log('是否为数组:', Array.isArray(response))

    const data = response.data || response
    logger.log('提取的数据:', data)
    logger.log('数据类型:', typeof data)
    logger.log('数据是否为数组:', Array.isArray(data))

    let filteredData = Array.isArray(data) ? data : ((data as { data?: ClassItem[] }).data || [])
    logger.log('过滤后的数据:', filteredData)

    if (name) {
      filteredData = filteredData.filter((item: ClassItem) => item.name && item.name.includes(name))
    }
    if (grade) {
      filteredData = filteredData.filter((item: ClassItem) => item.grade && item.grade.toString() === grade.toString())
    }
    if (majorId) {
      filteredData = filteredData.filter((item: ClassItem) => item.majorId && item.majorId.toString() === majorId.toString())
    }

    logger.log('最终过滤后的数据:', filteredData)

    const total = filteredData.length
    const startIndex = (currentPage - 1) * pageSize
    const endIndex = startIndex + pageSize
    const pageData = filteredData.slice(startIndex, endIndex)

    return {
      code: 200,
      data: {
        rows: pageData,
        total: total
      }
    }
  } catch (error) {
    logger.error('查询班级列表失败:', error)
    return { code: 500, msg: '查询失败' }
  }
}

export const addApi = async (classData: Omit<ClassItem, 'id' | 'createTime' | 'updateTime'>) => {
  try {
    logger.log('addApi - 接收到的数据:', classData)
    
    // 确保数据格式正确
    const addData = {
      name: classData.name,
      grade: classData.grade ? Number(classData.grade) : undefined,
      majorId: classData.majorId ? Number(classData.majorId) : undefined,
      teacherId: classData.teacherId || undefined
    }
    
    logger.log('addApi - 转换后的数据:', addData)
    
    const response = await request.post('/classes', addData)
    logger.log('addApi - 响应:', response)
    
    const result = response as { code?: number; data?: unknown; msg?: string; message?: string }

    if (result.code === 200) {
      return {
        code: 200,
        data: result.data || null,
        msg: result.msg || result.message || '添加成功'
      }
    } else {
      return {
        code: result.code || 500,
        data: null,
        msg: result.msg || result.message || '添加失败'
      }
    }
  } catch (error) {
    logger.error('添加班级失败:', error)
    return { code: 500, data: null, msg: '添加失败' }
  }
}

export const queryInfoApi = async (id: number | string) => {
  try {
    // 直接调用 API，不使用缓存，确保获取最新数据
    const response = await request.get(`/classes/${id}`)
    logger.log('queryInfoApi - 班级详情响应:', response)
    
    // response 可能已经是 { code, data, message } 格式（后端使用 message 字段）
    const result = response as { code?: number; data?: unknown; msg?: string; message?: string }
    
    // 检查响应是否成功
    if (result.code === 200 && result.data) {
      return {
        code: 200,
        data: result.data,
        msg: result.msg || result.message || '查询成功'
      }
    } else {
      return {
        code: result.code || 500,
        data: null,
        msg: result.msg || result.message || '查询失败'
      }
    }
  } catch (error) {
    logger.error('查询班级信息失败:', error)
    return { code: 500, data: null, msg: '查询失败' }
  }
}

export const updateApi = async (classData: Partial<ClassItem>) => {
  try {
    logger.log('updateApi - 接收到的数据:', classData)
    
    // 确保数据格式正确
    const updateData = {
      id: classData.id ? Number(classData.id) : undefined,
      name: classData.name,
      grade: classData.grade ? Number(classData.grade) : undefined,
      majorId: classData.majorId ? Number(classData.majorId) : undefined,
      teacherId: classData.teacherId || undefined
    }
    
    logger.log('updateApi - 转换后的数据:', updateData)
    
    const response = await request.put(`/classes/${updateData.id}`, updateData)
    logger.log('updateApi - 响应:', response)
    
    const result = response as { code?: number; data?: unknown; msg?: string; message?: string }

    if (result.code === 200) {
      return {
        code: 200,
        data: result.data || null,
        msg: result.msg || result.message || '更新成功'
      }
    } else {
      return {
        code: result.code || 500,
        data: null,
        msg: result.msg || result.message || '更新失败'
      }
    }
  } catch (error) {
    logger.error('更新班级失败:', error)
    return { code: 500, data: null, msg: '更新失败' }
  }
}

export const deleteApi = async (id: number | string) => {
  try {
    const response = await ClassService.deleteClass(id)
    const result = response.data || response

    if ((result as { code: number }).code === 200) {
      return {
        code: 200,
        msg: (result as { msg?: string }).msg || '删除成功'
      }
    } else {
      return {
        code: (result as { code?: number }).code || 500,
        msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '删除失败'
      }
    }
  } catch (error) {
    logger.error('删除班级失败:', error)
    return { code: 500, msg: '删除失败' }
  }
}

// 批量删除班级 API
export const batchDeleteApi = async (ids: (number | string)[]) => {
  try {
    const response = await ClassService.batchDeleteClass(ids)
    const result = response.data || response

    if ((result as { code: number }).code === 200) {
      return {
        code: 200,
        msg: (result as { msg?: string }).msg || '批量删除成功'
      }
    } else {
      return {
        code: (result as { code?: number }).code || 500,
        msg: (result as { msg?: string; message?: string }).msg || (result as { msg?: string; message?: string }).message || '批量删除失败'
      }
    }
  } catch (error) {
    logger.error('批量删除班级失败:', error)
    return { code: 500, msg: '批量删除失败' }
  }
}

// Excel 导入 API
export const importExcelApi = async (formData: FormData) => {
  try {
    const response = await request.post('/classes/import', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    // 处理后端返回的 Result 对象
    const result = response.data || response

    if ((result as { code: number }).code === 200) {
      return {
        code: 200,
        data: (result as { data?: unknown }).data || result,
        msg: (result as { msg?: string; message?: string }).message || (result as { msg?: string; message?: string }).msg || '操作成功'
      }
    } else {
      return {
        code: (result as { code?: number }).code || 500,
        data: (result as { data?: unknown }).data || null,
        msg: (result as { msg?: string; message?: string }).message || (result as { msg?: string; message?: string }).msg || '操作失败'
      }
    }
  } catch (error) {
    logger.error('导入班级 Excel 数据失败:', error)
    throw error
  }
}

// 下载导入模板 API
export const downloadClassTemplateApi = async () => {
  try {
    const response = await request.get('/classes/import-template', {
      responseType: 'blob'
    })
    return response
  } catch (error) {
    logger.error('下载班级导入模板失败:', error)
    throw error
  }
}

// Excel 导出 API
export const exportClassDataApi = async () => {
  try {
    // 创建一个临时的 a 标签用于下载
    const link = document.createElement('a')
    link.href = '/classes/export'
    link.target = '_blank'
    // 触发下载
    document.body.appendChild(link)
    link.click()
    // 清理
    document.body.removeChild(link)

    return {
      code: 200,
      msg: '导出请求已发送，请等待文件下载完成'
    }
  } catch (error) {
    logger.error('导出班级 Excel 数据失败:', error)
    return { code: 500, msg: '导出失败：' + ((error as { message?: string }).message || '未知错误') }
  }
}

// 默认导出
export default ClassService
