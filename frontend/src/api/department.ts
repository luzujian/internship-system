import request from '@/utils/request'
import cacheService, { CACHE_CONFIG } from './cacheService'

export interface Department {
  id: number
  name: string
  code?: string
  description?: string
  createTime?: string
  updateTime?: string
}

export interface DepartmentResponse {
  code: number
  data: Department | Department[]
  msg?: string
}

export default {
  // 获取所有院系或按名称查询 - 添加缓存
  getDepartments: async (name = '') => {
    // 为搜索添加不同的缓存键
    const cacheKey = name ? `departments_search_${name}` : 'all_departments'
    const cachedData = cacheService.get<Department[]>(cacheKey)

    if (cachedData) {
      // 返回缓存数据，保持与实际请求相同的格式
      return { data: { code: 200, data: cachedData } }
    }

    // 缓存未命中，请求数据
    let response
    if (name) {
      // 带查询参数的请求
      response = await request.get<Department[]>('/admin/departments/search', { params: { name } })
    } else {
      // 获取所有院系的请求
      response = await request.get<Department[]>('/admin/departments')
    }

    // 缓存数据
    if (response.data && (response.data as { code?: number }).code === 200 && (response.data as { data?: Department[] }).data) {
      cacheService.set(cacheKey, (response.data as { data?: Department[] }).data!, CACHE_CONFIG.EXPIRATION_TIME.LONG)
    }
    return response
  },

  // 根据 ID 获取院系 - 添加缓存
  getDepartmentById: async (id: number) => {
    const cacheKey = `department_${id}`
    const cachedData = cacheService.get<Department>(cacheKey)

    if (cachedData) {
      // 返回缓存数据的 Promise
      return Promise.resolve({ data: cachedData })
    }

    // 缓存未命中，请求数据
    const response = await request.get<Department>(`/admin/departments/${id}`)
    // 缓存数据
    if (response.data) {
      cacheService.set(cacheKey, response.data, CACHE_CONFIG.EXPIRATION_TIME.MEDIUM)
    }
    return response
  },

  // 添加院系
  addDepartment: async (department: Omit<Department, 'id' | 'createTime' | 'updateTime'>) => {
    // 添加数据后清除相关缓存
    const response = await request.post('/admin/departments', department)
    // 清除院系列表缓存
    cacheService.delete('all_departments')
    return response
  },

  // 更新院系
  updateDepartment: async (department: Partial<Department>) => {
    // 更新数据后清除相关缓存
    const response = await request.put('/admin/departments', department)
    // 清除院系列表和该院系的缓存
    cacheService.delete('all_departments')
    if (department.id) {
      cacheService.delete(`department_${department.id}`)
    }
    return response
  },

  // 删除院系
  deleteDepartment: async (id: number) => {
    // 删除数据后清除相关缓存
    const response = await request.delete(`/admin/departments/${id}`)
    // 清除院系列表和该院系的缓存
    cacheService.delete('all_departments')
    cacheService.delete(`department_${id}`)
    return response
  },

  // 导出院系专业数据
  exportMajorData: async () => {
    return request.get('/admin/departments/majors/export', {
      responseType: 'blob'
    })
  }
}
