import request from '@/utils/request'

export interface LearningResource {
  id: number
  title: string
  description?: string
  fileType?: string
  type?: string
  resourceType?: string
  file_category?: string
  category?: string
  url?: string
  fileSize?: number
  downloadCount?: number
  uploaderId?: number
  uploaderRole?: string
  uploader?: string
  status?: string
  createdTime?: string
  uploadTime?: string
  aiSummary?: string
}

export interface AISearchRequest {
  query: string
  fileType?: string
  page?: number
  pageSize?: number
}

export interface AISearchResponse {
  list: LearningResource[]
  total: number
}

export interface ResourceApiResponse {
  code: number
  data: LearningResource[] | AISearchResponse | LearningResource
  msg?: string
}

export const resourceApi = () => ({
  aiSearchResources: async (params: AISearchRequest): Promise<ResourceApiResponse> => {
    try {
      const response = await request.post<ResourceApiResponse>('/resources/ai/search', params)
      return response.data || response
    } catch (error) {
      console.error('AI搜索失败:', error)
      throw error
    }
  },

  getApprovedResources: async (params?: { page?: number; pageSize?: number }): Promise<ResourceApiResponse> => {
    try {
      const response = await request.get<ResourceApiResponse>('/resources/approved', { params })
      return response.data || response
    } catch (error) {
      console.error('获取已审核资源失败:', error)
      throw error
    }
  },

  getResourceDetail: async (id: number): Promise<ResourceApiResponse> => {
    try {
      const response = await request.get<ResourceApiResponse>(`/resources/${id}`)
      return response.data || response
    } catch (error) {
      console.error('获取资源详情失败:', error)
      throw error
    }
  },

  deleteResource: async (id: number): Promise<void> => {
    try {
      await request.delete(`/resources/${id}`)
    } catch (error) {
      console.error('删除资源失败:', error)
      throw error
    }
  },

  uploadResource: async (formData: FormData): Promise<ResourceApiResponse> => {
    try {
      const response = await request.post<ResourceApiResponse>('/resources/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      return response.data || response
    } catch (error) {
      console.error('上传资源失败:', error)
      throw error
    }
  },

  searchResources: async (query: string): Promise<ResourceApiResponse> => {
    try {
      const response = await request.post<ResourceApiResponse>('/resources/search', { query })
      return response.data || response
    } catch (error) {
      console.error('搜索资源失败:', error)
      throw error
    }
  },

  getPendingResources: async (): Promise<ResourceApiResponse> => {
    try {
      const response = await request.get<ResourceApiResponse>('/resources/pending')
      return response.data || response
    } catch (error) {
      console.error('获取待审核资源失败:', error)
      throw error
    }
  },

  getCategories: async (): Promise<ResourceApiResponse> => {
    try {
      const response = await request.get<ResourceApiResponse>('/resources/categories')
      return response.data || response
    } catch (error) {
      console.error('获取资源分类失败:', error)
      throw error
    }
  },

  addCategory: async (category: { name: string; description?: string }): Promise<ResourceApiResponse> => {
    try {
      const response = await request.post<ResourceApiResponse>('/resources/categories', category)
      return response.data || response
    } catch (error) {
      console.error('添加资源分类失败:', error)
      throw error
    }
  },

  reviewResource: async (id: number, approved: boolean, reviewerId: number): Promise<void> => {
    try {
      await request.post(`/resources/${id}/review`, { approved, reviewerId })
    } catch (error) {
      console.error('审核资源失败:', error)
      throw error
    }
  },

  // 教师端专属接口
  getTeacherResources: async (params?: { page?: number; pageSize?: number }): Promise<ResourceApiResponse> => {
    try {
      const response = await request.get<ResourceApiResponse>('/teacher/resources', { params })
      return response.data || response
    } catch (error) {
      console.error('获取教师资源列表失败:', error)
      throw error
    }
  },

  getMyResources: async (params?: { page?: number; pageSize?: number }): Promise<ResourceApiResponse> => {
    try {
      const response = await request.get<ResourceApiResponse>('/teacher/resources/my', { params })
      return response.data || response
    } catch (error) {
      console.error('获取我的资源列表失败:', error)
      throw error
    }
  },

  updateResource: async (id: number, resource: Partial<LearningResource>): Promise<ResourceApiResponse> => {
    try {
      const response = await request.put<ResourceApiResponse>(`/teacher/resources/${id}`, resource)
      return response.data || response
    } catch (error) {
      console.error('更新资源失败:', error)
      throw error
    }
  },

  deleteTeacherResource: async (id: number): Promise<void> => {
    try {
      await request.delete(`/teacher/resources/${id}`)
    } catch (error) {
      console.error('删除资源失败:', error)
      throw error
    }
  }
})
