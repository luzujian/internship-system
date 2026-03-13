import request from '@/utils/request'
import cacheService, { CACHE_CONFIG } from './cacheService'

export interface PositionCategory {
  id: number
  name: string
  code?: string
  description?: string
  createTime?: string
  updateTime?: string
}

type AxiosResponseWithMeta<T> = {
  data: {
    code: number
    data: T
    msg?: string
  }
}

const PositionCategoryService = {
  getCategories: async (name?: string) => {
    try {
      const params = name ? { name } : {}
      const response = await request.get<PositionCategory[]>('/admin/position-categories', { params })
      return response as unknown as AxiosResponseWithMeta<PositionCategory[]>
    } catch (error) {
      console.error('获取岗位类别列表失败:', error)
      throw error
    }
  },

  getCategoryById: async (id: number) => {
    const cacheKey = `position_category_${id}`
    const cachedData = cacheService.get<PositionCategory>(cacheKey)

    if (cachedData) {
      return Promise.resolve({ data: cachedData })
    }

    const response = await request.get<PositionCategory>(`/admin/position-categories/${id}`)
    if (response.data) {
      cacheService.set(cacheKey, response.data, CACHE_CONFIG.EXPIRATION_TIME.MEDIUM)
    }
    return response
  },

  getPositionsByCategoryId: async (categoryId: number) => {
    try {
      const response = await request.get(`/admin/position-categories/${categoryId}/positions`)
      return response
    } catch (error) {
      console.error('获取岗位列表失败:', error)
      throw error
    }
  },

  getStatistics: async () => {
    try {
      const response = await request.get('/admin/position-categories/statistics')
      return response
    } catch (error) {
      console.error('获取统计数据失败:', error)
      throw error
    }
  },

  addCategory: async (category: Omit<PositionCategory, 'id' | 'createTime' | 'updateTime'>) => {
    const response = await request.post('/admin/position-categories', category)
    cacheService.delete('all_position_categories')
    return response
  },

  updateCategory: async (id: number, category: Partial<PositionCategory>) => {
    const response = await request.put(`/admin/position-categories/${id}`, category)
    cacheService.delete('all_position_categories')
    cacheService.delete(`position_category_${id}`)
    return response
  },

  deleteCategory: async (id: number) => {
    const response = await request.delete(`/admin/position-categories/${id}`)
    cacheService.delete('all_position_categories')
    cacheService.delete(`position_category_${id}`)
    return response
  }
}

export default PositionCategoryService
