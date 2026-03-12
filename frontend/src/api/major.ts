// 专业服务
import request from '@/utils/request'
import cacheService, { CACHE_CONFIG } from './cacheService'
import logger from '@/utils/logger'

interface Major {
  id: number
  name: string
  code: string
  departmentId?: number
  description?: string
  createTime?: string
  updateTime?: string
}

interface MajorListResponse {
  rows: Major[]
  total: number
}

const MajorService = {
  // 获取所有专业 - 添加详细调试信息，暂时禁用缓存
  getMajors: async () => {
    logger.log('开始获取专业数据...')

    // 为了调试，先清除缓存
    const cacheKey = 'all_majors'
    cacheService.delete(cacheKey)
    logger.log('已清除专业缓存')

    // 直接请求数据，不使用缓存（调试模式）
    try {
      logger.log('发送请求到：/majors')
      const response = await request.get('/majors')
      logger.log('获取专业数据响应:', response)

      // 仅在成功时缓存数据
      if (response && response.data) {
        logger.log('响应数据:', response.data)
        // 暂不缓存数据，以便调试
        // cacheService.set(cacheKey, response.data, CACHE_CONFIG.EXPIRATION_TIME.LONG)
      }

      return response
    } catch (error) {
      logger.error('获取专业数据失败:', error)
      logger.error('错误详情:', (error as { response?: unknown; message?: string }).response || (error as { message?: string }).message || error)
      throw error
    }
  },

  // 根据 ID 获取专业 - 添加缓存
  getMajorById: async (id: number) => {
    const cacheKey = `major_${id}`
    const cachedData = cacheService.get<{ code: number; data: Major }>(cacheKey)

    if (cachedData) {
      // 返回缓存数据的 Promise
      return Promise.resolve({ data: cachedData })
    }

    // 缓存未命中，请求数据
    const response = await request.get<Major>(`majors/${id}`)
    // 缓存数据
    if (response.data) {
      cacheService.set(cacheKey, response.data, CACHE_CONFIG.EXPIRATION_TIME.MEDIUM)
    }
    return response
  },

  // 根据名称搜索专业
  searchMajorsByName: async (name: string) => {
    try {
      logger.log('根据名称搜索专业:', name)
      const response = await request.get<Major[]>('majors/search', {
        params: { name }
      })
      logger.log('搜索专业响应:', response)
      return response
    } catch (error) {
      logger.error('搜索专业失败:', error)
      // 如果后端 API 不支持搜索，返回空结果或使用前端过滤
      return { data: { code: 200, data: [] } }
    }
  },

  // 添加专业
  addMajor: async (major: Omit<Major, 'id' | 'createTime' | 'updateTime'>) => {
    // 添加数据后清除相关缓存
    const response = await request.post('majors', major)
    // 清除专业列表缓存
    cacheService.delete('all_majors')
    return response
  },

  // 更新专业
  updateMajor: async (id: number, major: Partial<Major>) => {
    // 更新数据后清除相关缓存
    const response = await request.put(`majors/${id}`, major)
    // 清除专业列表和该专业的缓存
    cacheService.delete('all_majors')
    cacheService.delete(`major_${id}`)
    return response
  },

  // 删除专业
  deleteMajor: async (id: number) => {
    // 删除数据后清除相关缓存
    const response = await request.delete(`majors/${id}`)
    // 清除专业列表和该专业的缓存
    cacheService.delete('all_majors')
    cacheService.delete(`major_${id}`)
    return response
  }
}

export default MajorService
