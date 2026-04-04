import request from '@/utils/request'

export interface AIModel {
  id: number
  name: string
  provider: string
  modelType: string
  endpoint?: string
  apiKey?: string
  isEnabled?: boolean
  isDefault?: boolean
  config?: Record<string, unknown>
  createTime?: string
  updateTime?: string
}

export interface AIModelListResponse {
  code: number
  data: AIModel[]
  msg?: string
}

export interface AIModelResponse {
  code: number
  data: AIModel
  msg?: string
}

// 定义响应类型的 Axios 响应
type AxiosResponseWithMeta<T> = {
  data: {
    code: number
    data: T
    msg?: string
  }
}

export default {
  getAIModels: () => {
    return request.get<AIModel[]>('/admin/ai-model') as unknown as AxiosResponseWithMeta<AIModel[]>
  },

  getEnabledAIModels: () => {
    return request.get<AIModel[]>('/admin/ai-model/enabled') as unknown as AxiosResponseWithMeta<AIModel[]>
  },

  getDefaultAIModel: () => {
    return request.get<AIModel>('/admin/ai-model/default') as unknown as AxiosResponseWithMeta<AIModel>
  },

  getAIModelById: (id: number) => {
    return request.get<AIModel>(`/admin/ai-model/${id}`) as unknown as AxiosResponseWithMeta<AIModel>
  },

  addAIModel: (data: Omit<AIModel, 'id' | 'createTime' | 'updateTime'>) => {
    return request.post('/admin/ai-model', data) as unknown as AxiosResponseWithMeta<AIModel>
  },

  updateAIModel: (id: number, data: Partial<AIModel>) => {
    return request.put(`/admin/ai-model/${id}`, data) as unknown as AxiosResponseWithMeta<AIModel>
  },

  setAsDefault: (id: number, updater: string) => {
    return request.put(`/admin/ai-model/${id}/set-default`, { updater }) as unknown as AxiosResponseWithMeta<void>
  },

  deleteAIModel: (id: number) => {
    return request.delete(`/admin/ai-model/${id}`) as unknown as AxiosResponseWithMeta<void>
  },

  deleteAllAIModels: () => {
    return request.delete('/admin/ai-model/all') as unknown as AxiosResponseWithMeta<void>
  },

  getAIModelsByProvider: (provider: string) => {
    return request.get<AIModel[]>(`/admin/ai-model/provider/${provider}`) as unknown as AxiosResponseWithMeta<AIModel[]>
  },

  getAIModelsByStatus: (status: boolean) => {
    return request.get<AIModel[]>(`/admin/ai-model/status/${status}`) as unknown as AxiosResponseWithMeta<AIModel[]>
  },

  searchAIModels: (keyword: string) => {
    return request.get<AIModel[]>(`/admin/ai-model/search/${keyword}`) as unknown as AxiosResponseWithMeta<AIModel[]>
  }
}
