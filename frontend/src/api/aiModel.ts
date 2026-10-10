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
  shortName: string
  color: string
  docs?: string
  hint: string
  maxTokens?: number
  temperature?: number
}

// 官方 OpenAI Chat Completions 接口示例（2026-10 核对）；模型权限以各平台控制台为准。
export const PROVIDER_PRESETS: ProviderPreset[] = [
  { value: "custom", label: "自定义网关", shortName: "API", color: "#475569", endpoint: "", models: [], hint: "填写网关的 OpenAI Chat Completions 地址、网关 Key 与准确模型 ID。支持中转及私有部署。" },
  { value: "deepseek", label: "DeepSeek", shortName: "D", color: "#4d6bfe", endpoint: "https://api.deepseek.com", models: ["deepseek-flash", "deepseek-v4-pro"], docs: "https://api-docs.deepseek.com/", hint: "使用 DeepSeek 开放平台 API Key，也可替换为兼容网关。", temperature: 1 },
  { value: "qwen", label: "阿里云 · 通义千问", shortName: "Q", color: "#7655dc", endpoint: "https://dashscope.aliyuncs.com/compatible-mode/v1", models: ["qwen-plus", "qwen-max", "qwen3.8-omni-flash", "qwen3-asr-flash-realtime"], docs: "https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope", hint: "预设为北京地域百炼兼容接口；其他地域请替换对应地址和 Key。实时语音模型需使用语音服务地址。" },
  { value: "zhipu", label: "智谱 · GLM", shortName: "智", color: "#3065d6", endpoint: "https://open.bigmodel.cn/api/paas/v4", models: ["glm-5.3", "glm-5.3-flash"], docs: "https://docs.bigmodel.cn/cn/guide/develop/openai/introduction", hint: "使用智谱开放平台 API Key；套餐专属地址请按官方说明替换。" },
  { value: "moonshot", label: "月之暗面 · Kimi", shortName: "K", color: "#171f32", endpoint: "https://api.moonshot.cn/v1", models: ["kimi-k3", "kimi-k2.6"], docs: "https://platform.kimi.com/docs/get-api-key", hint: "使用 Kimi API 开放平台 Key；新系列建议温度设为 1，并保留足够的输出 Token。", temperature: 1 },
  { value: "doubao", label: "火山方舟 · 豆包", shortName: "豆", color: "#168a88", endpoint: "https://ark.cn-beijing.volces.com/api/v3", models: ["doubao-seed-2-1-pro-260628"], docs: "https://docs.volcengine.com/docs/ark/chat-api?lang=zh", hint: "模型 ID 可填已开通的模型名称或控制台的 ep-… 推理接入点 ID。" },
  { value: "baidu", label: "百度 · 文心千帆", shortName: "百", color: "#315aee", endpoint: "https://qianfan.baidubce.com/v2", models: ["ernie-4.0-turbo-128k"], docs: "https://cloud.baidu.com/doc/BCM/s/tm8zur2cg", hint: "使用千帆 v2 API Key 鉴权，模型 ID 请以千帆控制台当前可用模型为准。" },
  { value: "tencent", label: "腾讯 · 混元", shortName: "混", color: "#2776d5", endpoint: "https://api.hunyuan.cloud.tencent.com/v1", models: ["hunyuan-turbos-latest"], docs: "https://cloud.tencent.com/document/product/1729/111007", hint: "预设对应混元已购服务；新开通 TokenHub 用户请填写其兼容地址、Key 和模型 ID。" },
  { value: "minimax", label: "MiniMax", shortName: "M", color: "#dc7055", endpoint: "https://api.minimax.cn/v1", models: ["MiniMax-M2.7", "MiniMax-M2.5"], docs: "https://platform.minimax.cn/docs/api-reference/text-openai-api", hint: "使用 MiniMax 开放平台 Key，推理模型需要足够的输出 Token。", temperature: 1 },
  { value: "spark", label: "讯飞 · 星火", shortName: "讯", color: "#237ba5", endpoint: "https://spark-api-open.xf-yun.com/v1", models: ["generalv3.5", "4.0Ultra"], docs: "https://www.xfyun.cn/doc/spark/HTTP调用文档.html", hint: "API Key 填写控制台 HTTP 服务的 APIPassword，不是 WebSocket 的 APISecret。" },
  { value: "siliconflow", label: "硅基流动", shortName: "硅", color: "#7653c8", endpoint: "https://api.siliconflow.cn/v1", models: ["deepseek-ai/DeepSeek-V4-Flash"], docs: "https://docs.siliconflow.cn/docs/api/chat-completions-post", hint: "保留模型 ID 中的组织前缀与斜杠；可获取账号可见的模型列表。" },
  { value: "baichuan", label: "百川智能", shortName: "川", color: "#d0812b", endpoint: "https://api.baichuan-ai.com/v1", models: ["Baichuan4-Turbo", "Baichuan4-Air"], docs: "https://platform.baichuan-ai.com/docs-v2/api", hint: "通用对话接口建议输出上限 2048、温度 0–1；医疗模型请填写对应接入配置。", maxTokens: 2048, temperature: 0.3 },
  { value: "stepfun", label: "阶跃星辰", shortName: "阶", color: "#327a7d", endpoint: "https://api.stepfun.com/v1", models: ["step-1-8k"], docs: "https://platform.stepfun.com/docs/guide/json_mode", hint: "使用阶跃开放平台 API Key；示例可替换为账号已开通的最新模型 ID，订阅套餐请填写套餐对应地址。" },
  { value: "openai", label: "OpenAI", shortName: "O", color: "#298773", endpoint: "https://api.openai.com/v1", models: [], hint: "选择支持 Chat Completions 的模型，或填写 OpenAI 兼容网关。" },
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
    return request.post("/admin/ai-model/test", data, { timeout: 100000 }) as unknown as AxiosResponseWithMeta<AIModelTestResult>
  },

  /** 获取兼容网关的模型列表；未实现该接口的网关仍可手动填写模型 ID。 */
  discoverModels: (data: Partial<AIModel>) => {
    return request.post("/admin/ai-model/models", data, { timeout: 100000 }) as unknown as AxiosResponseWithMeta<{ models: string[], endpoint: string }>
  },

  /** 返回未保存的配置模板，不复制凭据，保留真实模型 ID。 */
  duplicateModel: (id: number, creator = "admin") => {
    return request.post(`/admin/ai-model/${id}/duplicate`, { creator }) as unknown as AxiosResponseWithMeta<AIModel>
  }
}
