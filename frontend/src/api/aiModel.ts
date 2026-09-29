import request from "@/utils/request"

/** 与后端 AIModel 实体对齐。modelName=展示名，modelCode=实际请求模型（厂商 model 参数）。 */
export interface AIModel {
  id?: number
  modelName: string
  modelCode: string
  provider: string
  apiEndpoint?: string
  /** 写：非空=更新为该值，空=保持不变；读：脱敏掩码，仅展示用。 */
  apiKey?: string
  /** 读：该模型是否已配置 Key。 */
  hasApiKey?: boolean
  /** 写：true=清空该模型的独立地址与 Key，回退全局配置。 */
  clearApiConfig?: boolean
  maxTokens?: number
  temperature?: number
  description?: string
  status?: number
  useForVoice?: boolean
  supportedUsages?: string[]
  isDefault?: number | boolean
  creator?: string
  updater?: string
  createTime?: string
  updateTime?: string
}

export interface AIModelTestResult {
  ok: boolean
  httpStatus?: number
  latencyMs?: number
  requestModel?: string
  endpoint?: string
  reply?: string
  error?: string
}

/** 提供商预设：切换时自动带出常用地址与请求模型候选（均可再手动改）。 */
export interface ProviderPreset {
  value: string
  label: string
  endpoint: string
  models: string[]
}

export const PROVIDER_PRESETS: ProviderPreset[] = [
  { value: "deepseek", label: "DeepSeek", endpoint: "https://api.deepseek.com", models: ["deepseek-chat", "deepseek-reasoner"] },
  { value: "openai", label: "OpenAI 兼容", endpoint: "https://api.openai.com", models: ["gpt-4o-mini", "gpt-4o"] },
  { value: "qwen", label: "通义千问", endpoint: "https://dashscope.aliyuncs.com/compatible-mode", models: ["qwen3.8-omni-flash", "qwen-plus", "qwen3-asr-flash-realtime"] },
  { value: "zhipu", label: "智谱", endpoint: "https://open.bigmodel.cn/api/paas/v4", models: ["glm-4-flash", "glm-4"] },
  { value: "custom", label: "自定义网关", endpoint: "", models: [] },
]

// 定义响应类型的 Axios 响应
type AxiosResponseWithMeta<T> = {
  data: {
    code: number
    data: T
    message?: string
    msg?: string
  }
}

export default {
  getCapabilities: (modelCode: string) => request.get("/admin/ai-model/capabilities", { params: { modelCode } }),
  getVoiceModel: () => request.get('/admin/ai-model/voice-model'),
  setVoiceModel: (modelId: number) => request.put('/admin/ai-model/voice-model', { modelId }),
  getAIModels: () => {
    return request.get<AIModel[]>("/admin/ai-model") as unknown as AxiosResponseWithMeta<AIModel[]>
  },

  getEnabledAIModels: () => {
    return request.get<AIModel[]>("/admin/ai-model/enabled") as unknown as AxiosResponseWithMeta<AIModel[]>
  },

  getDefaultAIModel: () => {
    return request.get<AIModel>("/admin/ai-model/default") as unknown as AxiosResponseWithMeta<AIModel>
  },

  getAIModelById: (id: number) => {
    return request.get<AIModel>(`/admin/ai-model/${id}`) as unknown as AxiosResponseWithMeta<AIModel>
  },

  addAIModel: (data: Omit<AIModel, "id" | "createTime" | "updateTime" | "hasApiKey">) => {
    return request.post("/admin/ai-model", data) as unknown as AxiosResponseWithMeta<AIModel>
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
    return request.delete("/admin/ai-model/all") as unknown as AxiosResponseWithMeta<void>
  },

  getAIModelsByProvider: (provider: string) => {
    return request.get<AIModel[]>(`/admin/ai-model/provider/${provider}`) as unknown as AxiosResponseWithMeta<AIModel[]>
  },

  getAIModelsByStatus: (status: number) => {
    return request.get<AIModel[]>(`/admin/ai-model/status/${status}`) as unknown as AxiosResponseWithMeta<AIModel[]>
  },

  searchAIModels: (keyword: string) => {
    return request.get<AIModel[]>(
      `/admin/ai-model/search/${encodeURIComponent(keyword)}`
    ) as unknown as AxiosResponseWithMeta<AIModel[]>
  },

  /** 一键测连：可测已保存模型（传 id，Key 留空沿用已存），也可测未保存的表单值（不传 id，不写库）。 */
  testConnection: (data: Partial<AIModel>) => {
    return request.post("/admin/ai-model/test", data) as unknown as AxiosResponseWithMeta<AIModelTestResult>
  },

  /** 复制配置：以已有模型为模板新建一条，Key 随行复制、默认关闭。 */
  duplicateModel: (id: number, creator = "admin") => {
    return request.post(`/admin/ai-model/${id}/duplicate`, { creator }) as unknown as AxiosResponseWithMeta<AIModel>
  }
}
