// 系服务
import request from '@/utils/request'
import cacheService, { CACHE_CONFIG } from './cacheService'
import logger from '@/utils/logger'

interface Division {
  id: number
  name: string
  departmentId: number
  departmentName?: string
  createTime?: string
  updateTime?: string
  teacherCount?: number
  studentCount?: number
  confirmedCount?: number
  notFoundCount?: number
  hasOfferCount?: number
}

interface DivisionListResponse {
  rows: Division[]
  total: number
}

const DivisionService = {
  // 获取所有系
  getDivisions: async () => {
    logger.log('开始获取系数据...')

    const cacheKey = 'all_divisions'
    cacheService.delete(cacheKey)

    try {
      logger.log('发送请求到：/admin/divisions')
      const response = await request.get('/admin/divisions')
      logger.log('获取系数据响应:', response)

      return response
    } catch (error) {
      logger.error('获取系数据失败:', error)
      throw error
    }
  },

  // 根据 ID 获取系
  getDivisionById: async (id: number) => {
    const cacheKey = `division_${id}`
    const cachedData = cacheService.get<{ code: number; data: Division }>(cacheKey)

    if (cachedData) {
      return Promise.resolve({ data: cachedData })
    }

    const response = await request.get<Division>(`admin/divisions/${id}`)
    if (response.data) {
      cacheService.set(cacheKey, response.data, CACHE_CONFIG.EXPIRATION_TIME.MEDIUM)
    }
    return response
  },

  // 根据学院 ID 获取系列表
  getDivisionsByDepartmentId: async (departmentId: number) => {
    try {
      logger.log('根据学院ID获取系列表:', departmentId)
      const response = await request.get<Division[]>(`admin/divisions/department/${departmentId}`)
      logger.log('获取系列表响应:', response)
      return response
    } catch (error) {
      logger.error('获取系列表失败:', error)
      return { data: { code: 200, data: [] } }
    }
  },

  // 添加系
  addDivision: async (division: Omit<Division, 'id' | 'createTime' | 'updateTime'>) => {
    const response = await request.post('admin/divisions', division)
    cacheService.delete('all_divisions')
    return response
  },

  // 更新系
  updateDivision: async (id: number, division: Partial<Division>) => {
    const response = await request.put(`admin/divisions/${id}`, division)
    cacheService.delete('all_divisions')
    cacheService.delete(`division_${id}`)
    return response
  },

  // 删除系
  deleteDivision: async (id: number) => {
    const response = await request.delete(`admin/divisions/${id}`)
    cacheService.delete('all_divisions')
    cacheService.delete(`division_${id}`)
    return response
  }
}

export default DivisionService
