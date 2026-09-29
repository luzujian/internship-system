<template>
  <div class="ai-model-container">
    <div class="page-header">
      <div class="header-content">
        <div class="title-section">
          <h1>AI大模型选择</h1>
          <p>在模型的添加或编辑窗口选择用途，可分别用于聊天、语音识别，也可两者共用</p>
        </div>
        <div class="header-illustration">
          <div class="illustration-circle circle-1"></div>
          <div class="illustration-circle circle-2"></div>
        </div>
      </div>
      <div v-if="currentModels.length" class="current-models">
      <div v-for="currentModel in currentModels" :key="currentModel.id" class="current-model-display">
        <div class="current-model-icon">
          <el-icon><Star /></el-icon>
        </div>
        <div class="current-model-info">
          <div class="current-model-label">
            <el-tag v-if="isDefaultModel(currentModel)" size="small">当前聊天模型</el-tag>
            <el-tag v-if="currentModel.useForVoice" size="small" type="success">当前语音识别模型</el-tag>
          </div>
          <div class="current-model-name">{{ currentModel.modelName }}</div>
          <div class="current-model-detail">
            <span class="detail-item">实际请求：<code class="mono">{{ currentModel.modelCode }}</code></span>
            <span class="detail-separator">|</span>
            <span class="detail-item">{{ providerLabel(currentModel.provider) }}</span>
            <span class="detail-separator">|</span>
            <span class="detail-item">Key：{{ currentModel.hasApiKey ? "已配置" : "未配置（走全局）" }}</span>
            <span class="detail-separator">|</span>
            <span class="detail-item">最大Token: {{ currentModel.maxTokens }}</span>
          </div>
        </div>
      </div>
      </div>
    </div>

    <el-alert title="聊天模型负责问答与业务协助；语音模型将录音转为可编辑文字。每种用途选择一个模型，保存后新请求生效。" type="info" :closable="false" style="margin-bottom: 16px" />

    <el-card class="actions-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <div class="search-row">
          <el-form-item label="模型名称">
            <el-input v-model="searchKeyword" placeholder="请输入模型名称" clearable @keyup.enter="handleSearch">
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="提供商">
            <el-select v-model="filterProvider" placeholder="全部提供商" clearable @change="handleFilter" class="filter-select">
              <el-option
                v-for="p in PROVIDER_PRESETS"
                :key="p.value"
                :label="p.label"
                :value="p.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filterStatus" placeholder="请选择状态" clearable @change="handleFilter" class="filter-select">
              <el-option label="启用" value="1" />
              <el-option label="禁用" value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <div class="search-actions">
              <el-button type="primary" @click="handleSearch" class="search-btn">
                <el-icon><Search /></el-icon>&nbsp;查询
              </el-button>
              <el-button type="warning" @click="handleReset" class="reset-btn">
                <el-icon><Refresh /></el-icon>&nbsp;重置
              </el-button>
            </div>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <span class="toolbar-title">模型列表</span>
        <el-button v-if="authStore.hasPermission('ai:model:view')" type="primary" data-agent-open @click="handleAdd">
          <el-icon><Plus /></el-icon>&nbsp;添加模型
        </el-button>
      </div>
      <el-table :data="modelList" border stripe class="data-table" v-loading="loading" empty-text="暂无AI模型">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="modelName" label="模型名称" width="170" />
        <el-table-column label="当前用途" width="160">
          <template #default="{ row }">
            <div class="usage-tags">
              <el-tag v-if="row.isDefault" size="small">聊天</el-tag>
              <el-tag v-if="row.useForVoice" type="success" size="small">语音识别</el-tag>
              <span v-if="!row.isDefault && !row.useForVoice" class="form-tip">备用</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="modelCode" label="实际请求模型" width="190">
          <template #default="{ row }">
            <code class="mono">{{ row.modelCode }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="provider" label="提供商" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ providerLabel(row.provider) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="apiEndpoint" label="API 地址" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="endpoint-text">{{ row.apiEndpoint || "—（全局）" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Key" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.hasApiKey ? 'success' : 'info'">
              {{ row.hasApiKey ? "已配置" : "未配置" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="maxTokens" label="最大Token" width="100" />
        <el-table-column prop="temperature" label="温度" width="80" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag class="status-tag" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="250" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-tooltip v-if="authStore.hasPermission('ai:model:view')" content="编辑" placement="top">
                <el-button type="primary" size="small" @click="handleEdit(row)" class="table-btn edit">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('ai:model:view')" content="测试连接" placement="top">
                <el-button type="success" size="small" @click="handleQuickTest(row)" class="table-btn test" :disabled="row.apiEndpoint?.endsWith('/realtime')" :loading="quickTestingId === row.id">
                  <el-icon><Connection /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('ai:model:view')" content="复制配置" placement="top">
                <el-button type="info" size="small" @click="handleDuplicate(row)" class="table-btn copy">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('ai:model:view')" content="删除" placement="top">
                <el-button type="danger" size="small" @click="handleDelete(row)" class="table-btn delete">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px" class="form-dialog" @close="handleDialogClose">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="130px" class="ai-model-form">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="展示名，如：千问 Omni Flash" />
          <div class="form-tip">页面上显示的名字，可随意修改</div>
        </el-form-item>
        <el-form-item label="实际请求模型" prop="modelCode">
          <el-input v-model="form.modelCode" placeholder="如：qwen3.8-omni-flash" />
          <div class="form-tip">修改后会自动同步引用旧值的辅导员配置；不可与其它模型重复</div>
        </el-form-item>
        <el-form-item label="模型用途">
          <div>
            <span v-if="capabilitiesLoading" class="form-tip">正在检查可用用途…</span>
            <template v-else>
              <div class="usage-options">
                <el-checkbox v-if="supportedUsages.includes('chat')" v-model="form.isDefault" :true-value="1" :false-value="0">用作聊天模型</el-checkbox>
                <el-checkbox v-if="supportedUsages.includes('voice')" v-model="form.useForVoice">用作语音识别模型</el-checkbox>
              </div>
              <div class="form-tip">{{ capabilityError || (!form.modelCode ? '填写实际请求模型后显示可用用途。' : !supportedUsages.length ? '暂未确认此模型的接入能力，不能选择用途。' : '勾选后替换该用途的当前模型；两项都勾选即可共用。') }}</div>
              <div v-if="supportedUsages.includes('voice')" class="form-tip">{{ form.modelCode.includes('asr-flash-realtime') ? '支持边说边出字，结束后核对发送。' : '结束录音后转为文字，核对修改后再发送。' }}</div>
            </template>
          </div>
        </el-form-item>
        <el-form-item label="提供商" prop="provider">
          <el-select v-model="form.provider" placeholder="请选择提供商" class="full-width">
            <el-option
              v-for="p in PROVIDER_PRESETS"
              :key="p.value"
              :label="p.label"
              :value="p.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="API 地址" prop="apiEndpoint">
          <el-input v-model="form.apiEndpoint" placeholder="如：https://api.deepseek.com，或中转站给的 .../v1 地址" clearable />
          <div class="form-tip">填写服务商提供的完整接入地址，保留其中的 /v1；手动填写的地址不会被预设覆盖。</div>
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            clearable
            :placeholder="form.id && editHasApiKey ? '已配置（留空则不修改）' : '请输入该模型的 API Key'"
          />
          <div class="form-tip">列表中仅显示掩码；编辑时留空表示保持原值</div>
          <el-checkbox v-if="form.id && editHasApiKey" v-model="form.clearApiConfig" class="clear-check">
            清空独立配置，该模型回退到全局地址与 Key
          </el-checkbox>
        </el-form-item>
        <el-form-item label="最大Token数" prop="maxTokens">
          <el-input-number v-model="form.maxTokens" :min="1" :max="8192" placeholder="请输入最大Token数" />
          <div class="form-tip">系统允许范围为 [1, 8192]，请结合模型限制填写</div>
        </el-form-item>
        <el-form-item label="温度参数" prop="temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" placeholder="请输入温度参数" />
          <div class="form-tip">温度参数控制输出的随机性，值越高输出越随机</div>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入模型描述" />
        </el-form-item>
        <el-form-item label="连接测试">
          <div class="test-row">
            <el-button type="success" plain @click="handleDialogTest" :loading="testing" :disabled="form.apiEndpoint?.endsWith('/realtime')">
              <el-icon><Connection /></el-icon>&nbsp;{{ testing ? "测试中…" : "测试连接" }}
            </el-button>
            <span class="form-tip">通用对话接口可在保存前测连，不写库；实时语音接口请保存后通过实际录音验证</span>
          </div>
          <el-alert
            v-if="testResult"
            :type="testResult.ok ? 'success' : 'error'"
            :closable="false"
            show-icon
            class="test-alert"
          >
            <template #title>
              <span v-if="testResult.ok">
                连接成功 · {{ testResult.latencyMs }}ms · 实际请求 {{ testResult.requestModel }} · 回复：{{ testResult.reply }}
              </span>
              <span v-else>
                连接失败{{ testResult.latencyMs != null ? ` · ${testResult.latencyMs}ms` : "" }} · {{ testResult.error }}
              </span>
            </template>
          </el-alert>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="cancel-btn" @click="dialogVisible = false">取消</el-button>
          <el-button class="confirm-btn" type="primary" @click="handleSave" :loading="saving" :disabled="capabilitiesLoading || !!capabilityError">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import logger from "@/utils/logger"
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from "vue"
import { ElMessage, ElMessageBox } from "element-plus"
import { Plus, Edit, Delete, Search, Star, Refresh, CopyDocument, Connection } from "@element-plus/icons-vue"
import aiModelService, { PROVIDER_PRESETS } from "@/api/aiModel"
import type { AIModel } from "@/api/aiModel"
import { useAuthStore } from "@/store/auth"
import type { FormFieldDescriptor } from "@/agent/types"
import { useFormContext } from "@/agent/formContext"

interface AIModelEnvelope<T = AIModel[]> {
  code?: number
  message?: string
  msg?: string
  data?: T
}

interface TestResult {
  ok: boolean
  httpStatus?: number
  latencyMs?: number
  requestModel?: string
  endpoint?: string
  reply?: string
  error?: string
}

const authStore = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const quickTestingId = ref<number | null>(null)
const dialogVisible = ref(false)
const dialogTitle = ref("")
const modelList = ref<AIModel[]>([])
const currentModels = ref<AIModel[]>([])
const searchKeyword = ref("")
const filterStatus = ref("")
const filterProvider = ref("")
const formRef = ref(null)
const testResult = ref<TestResult | null>(null)
const editHasApiKey = ref(false)

const searchForm = ref({
  modelName: "",
  status: ""
})

const defaultProvider = "qwen"
const presetEndpointOf = (provider?: string) =>
  PROVIDER_PRESETS.find((p) => p.value === provider)?.endpoint || ""

const emptyForm = (): AIModel & { apiKey?: string } => ({
  id: undefined,
  modelName: "",
  modelCode: "",
  provider: defaultProvider,
  apiEndpoint: presetEndpointOf(defaultProvider),
  apiKey: "",
  clearApiConfig: false,
  maxTokens: 4096,
  temperature: 0.7,
  status: 1,
  isDefault: 0,
  useForVoice: false,
  description: ""
})

const form = ref<AIModel & { apiKey?: string }>(emptyForm())
const supportedUsages = ref<string[]>([])
const capabilitiesLoading = ref(false)
const capabilityError = ref('')
let capabilityRequest = 0
watch(() => form.value.modelCode, async (code) => {
  const mine = ++capabilityRequest
  supportedUsages.value = []; capabilityError.value = ''
  if (!code?.trim()) { capabilitiesLoading.value = false; form.value.isDefault = 0; form.value.useForVoice = false; return }
  capabilitiesLoading.value = true
  try {
    const response: any = await aiModelService.getCapabilities(code.trim())
    if (mine !== capabilityRequest) return
    if (response.code !== 200 || !Array.isArray(response.data)) throw new Error()
    supportedUsages.value = response.data
    if (!response.data.includes('chat')) form.value.isDefault = 0
    if (!response.data.includes('voice')) form.value.useForVoice = false
  } catch {
    if (mine === capabilityRequest) capabilityError.value = '读取模型能力失败，请重新填写模型名称或刷新后重试。'
  } finally { if (mine === capabilityRequest) capabilitiesLoading.value = false }
})



const providerLabel = (value?: string) => {
  return PROVIDER_PRESETS.find((p) => p.value === value)?.label || value || "—"
}

const rules = {
  modelName: [{ required: true, message: "请输入模型名称（展示名）", trigger: "blur" }],
  modelCode: [
    { required: true, message: "请输入实际请求模型", trigger: "blur" },
    { pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$/, message: "字母数字打头，可含字母/数字/点/横线/下划线，最长50", trigger: "blur" }
  ],
  provider: [{ required: true, message: "请选择提供商", trigger: "change" }],
  apiEndpoint: [
    {
      validator: (_rule, value, callback) => {
        if (!value || !String(value).trim()) return callback()
        const v = String(value).trim().toLowerCase()
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
          return callback(new Error("API 地址必须以 http:// 或 https:// 开头"))
        }
        return callback()
      },
      trigger: "blur"
    }
  ],
  maxTokens: [{ required: true, message: "请输入最大Token数", trigger: "blur" }],
  temperature: [{ required: true, message: "请输入温度参数", trigger: "blur" }]
}

const isDefaultModel = (row: AIModel) => row.isDefault === 1 || row.isDefault === true

const fetchModels = async () => {
  loading.value = true
  try {
    const response = (await aiModelService.getAIModels()) as unknown as AIModelEnvelope
    if (response.code === 200) {
      const rows = Array.isArray(response.data) ? response.data : []
      modelList.value = rows
      currentModels.value = rows.filter(model => model.status === 1 && (isDefaultModel(model) || model.useForVoice))
        .sort((a, b) => Number(isDefaultModel(b)) - Number(isDefaultModel(a)))
    } else {
      ElMessage.error(response.message || response.msg || "获取AI模型列表失败")
    }
  } catch (error) {
    ElMessage.error("获取AI模型列表失败")
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  if (!searchKeyword.value) {
    fetchModels()
    return
  }

  loading.value = true
  try {
    const response = (await aiModelService.searchAIModels(searchKeyword.value)) as unknown as AIModelEnvelope
    if (response.code === 200) {
      modelList.value = Array.isArray(response.data) ? response.data : []
    } else {
      ElMessage.error(response.message || response.msg || "搜索失败")
    }
  } catch (error) {
    ElMessage.error("搜索失败")
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchKeyword.value = ""
  filterStatus.value = ""
  filterProvider.value = ""
  fetchModels()
}

const handleFilter = async () => {
  loading.value = true
  try {
    let response: AIModelEnvelope | undefined
    if (filterStatus.value !== "" && filterProvider.value) {
      const statusResp = (await aiModelService.getAIModelsByStatus(Number(filterStatus.value))) as unknown as AIModelEnvelope
      if (statusResp.code === 200 && Array.isArray(statusResp.data)) {
        statusResp.data = statusResp.data.filter((m) => m.provider === filterProvider.value)
      }
      response = statusResp
    } else if (filterStatus.value !== "") {
      response = (await aiModelService.getAIModelsByStatus(Number(filterStatus.value))) as unknown as AIModelEnvelope
    } else if (filterProvider.value) {
      response = (await aiModelService.getAIModelsByProvider(filterProvider.value)) as unknown as AIModelEnvelope
    } else {
      fetchModels()
      return
    }

    if (response && response.code === 200) {
      modelList.value = Array.isArray(response.data) ? response.data : []
    }
  } catch (error) {
    ElMessage.error("筛选失败")
  } finally {
    loading.value = false
  }
}

// ===== 智能填表：AI 模型弹窗（只写响应式 model，不触发提交） =====
const formContext = useFormContext()
// apiKey 不向 AI 开放；isDefault（聊天用途）、useForVoice（语音用途）和 clearApiConfig
// 由管理员手动选择，不能通过 AI 填表切换系统模型或清除凭据。
const aiModelFillFields = (): FormFieldDescriptor[] => [
  { prop: 'modelName', label: '模型名称', type: 'input', required: true },
  { prop: 'modelCode', label: '实际请求模型', type: 'input', required: true, patternHint: '字母数字打头，可含字母/数字/点/横线/下划线' },
  { prop: 'provider', label: '提供商', type: 'select', required: true,
    options: PROVIDER_PRESETS.map(p => ({ label: p.label, value: p.value })) },
  { prop: 'apiEndpoint', label: 'API 地址', type: 'input', patternHint: '如 https://api.deepseek.com；中转 .../v1 保留' },
  { prop: 'maxTokens', label: '最大Token数', type: 'number', valueType: 'number', required: true },
  { prop: 'temperature', label: '温度参数', type: 'number', valueType: 'number' },
  { prop: 'status', label: '状态', type: 'radio', valueType: 'number',
    options: [{ label: '启用', value: 1 }, { label: '禁用', value: 0 }] },
  { prop: 'description', label: '描述', type: 'textarea', patternHint: '模型用途需由管理员手动选择' }
]
const registerAiModelForm = (title: string) => {
  formContext.register({
    formId: 'admin:ai-model',
    title,
    fields: aiModelFillFields(),
    getFields: aiModelFillFields,
    getModel: () => form.value,
    getFormEl: () => formRef.value
  })
}
watch(dialogVisible, (open) => {
  if (!open) formContext.unregister('admin:ai-model')
})
onUnmounted(() => formContext.unregister('admin:ai-model'))

// 切换提供商时地址跟随变化；如果当前地址是手填的（与旧预设不同）则保留不覆盖。
watch(
  () => form.value.provider,
  (newProvider, oldProvider) => {
    if (newProvider === oldProvider) return
    const newPreset = PROVIDER_PRESETS.find((p) => p.value === newProvider)
    const oldPreset = PROVIDER_PRESETS.find((p) => p.value === oldProvider)
    const current = (form.value.apiEndpoint || "").trim()
    if (!current || (oldPreset && current === oldPreset.endpoint)) {
      form.value.apiEndpoint = newPreset && newPreset.endpoint ? newPreset.endpoint : ""
    }
  }
)

const openDialog = (title: string) => {
  dialogTitle.value = title
  testResult.value = null
  dialogVisible.value = true
  registerAiModelForm(title)
}

const handleAdd = () => {
  form.value = emptyForm()
  editHasApiKey.value = false
  openDialog("添加AI模型")
}

const handleEdit = (row: AIModel) => {
  form.value = {
    id: row.id,
    modelName: row.modelName,
    modelCode: row.modelCode,
    provider: row.provider || "deepseek",
    apiEndpoint: row.apiEndpoint || "",
    // 绝不把掩码回填到密码框：留空=不修改
    apiKey: "",
    clearApiConfig: false,
    maxTokens: row.maxTokens ?? 4096,
    temperature: row.temperature ?? 0.7,
    status: row.status ?? 1,
    isDefault: row.isDefault ? 1 : 0,
    useForVoice: !!row.useForVoice,
    description: row.description || ""
  }
  editHasApiKey.value = !!row.hasApiKey
  openDialog("编辑AI模型")
}

const handleDelete = async (row: AIModel) => {
  try {
    await ElMessageBox.confirm(`确定要删除AI模型"${row.modelName}"吗？`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    })

    const response = (await aiModelService.deleteAIModel(row.id as number)) as unknown as AIModelEnvelope<unknown>
    if (response.code === 200) {
      ElMessage.success("删除成功")
      fetchModels()
    } else {
      ElMessage.error(response.message || response.msg || "删除失败")
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("删除失败")
    }
  }
}

const handleDuplicate = async (row: AIModel) => {
  try {
    await ElMessageBox.confirm(`以"${row.modelName}"为模板复制一条配置吗？Key 会随行复制，可在打开的编辑框里继续改。`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "info"
    })
    const response = (await aiModelService.duplicateModel(row.id as number, "admin")) as unknown as AIModelEnvelope<AIModel>
    if (response.code === 200 && response.data) {
      ElMessage.success(`复制成功，新实际请求模型：${response.data.modelCode}`)
      await fetchModels()
      handleEdit(response.data)
    } else {
      ElMessage.error(response.message || response.msg || "复制失败")
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("复制失败")
    }
  }
}

const describeTestResult = (data: TestResult) => {
  if (data.ok) {
    return `连接成功 · ${data.latencyMs}ms · 实际请求 ${data.requestModel} · 回复：${data.reply}`
  }
  return `连接失败${data.latencyMs != null ? ` · ${data.latencyMs}ms` : ""} · ${data.error}`
}

const handleQuickTest = async (row: AIModel) => {
  quickTestingId.value = row.id as number
  try {
    const response = (await aiModelService.testConnection({ id: row.id })) as unknown as AIModelEnvelope<TestResult>
    if (response.code === 200 && response.data) {
      if (response.data.ok) {
        ElMessage.success(describeTestResult(response.data))
      } else {
        ElMessage.error(describeTestResult(response.data))
      }
    } else {
      ElMessage.error(response.message || response.msg || "测试失败")
    }
  } catch (error) {
    ElMessage.error("测试失败")
  } finally {
    quickTestingId.value = null
  }
}

const handleDialogTest = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }
  testing.value = true
  testResult.value = null
  try {
    const { hasApiKey: _omit, ...payload } = form.value
    const response = (await aiModelService.testConnection(payload)) as unknown as AIModelEnvelope<TestResult>
    if (response.code === 200 && response.data) {
      testResult.value = response.data
      if (response.data.ok) {
        ElMessage.success("连接成功")
      }
    } else {
      ElMessage.error(response.message || response.msg || "测试失败")
    }
  } catch (error) {
    ElMessage.error("测试失败")
  } finally {
    testing.value = false
  }
}

const handleSave = async () => {
  if (!formRef.value || capabilitiesLoading.value || capabilityError.value) return

  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  const operation = formContext.beginFormOperation('admin:ai-model')
    saving.value = true
  try {
    const { hasApiKey: _omit, ...payload } = form.value
    let response: AIModelEnvelope<unknown> | undefined
    if (payload.id) {
      response = (await aiModelService.updateAIModel(payload.id, payload)) as unknown as AIModelEnvelope<unknown>
      logger.log("更新 AI 模型响应:", response)
      logger.log("响应数据:", response.data)
    } else {
      response = (await aiModelService.addAIModel(payload)) as unknown as AIModelEnvelope<unknown>
      logger.log("添加 AI 模型响应:", response)
      logger.log("响应数据:", response.data)
    }

    logger.log("response.code:", response?.code)
    logger.log("response.message:", response?.message)

    if (response && response.code === 200) {
      operation.complete({ status: 'saved', label: 'AI模型配置已保存', url: '/admin/ai-model' })
      ElMessage.success("保存成功")
      dialogVisible.value = false
      fetchModels()
    } else {
      ElMessage.error(response?.message || response?.msg || "保存失败")
    }
  } catch (error) {
    logger.error("保存失败:", error)
    ElMessage.error("保存失败")
  } finally {
    saving.value = false
  }
}

const handleDialogClose = () => {
  nextTick(() => {
    if (formRef.value) {
      formRef.value.resetFields()
    }
  })
}

const formatDateTime = (date) => {
  if (!date) return ""
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, "0")
  const day = String(d.getDate()).padStart(2, "0")
  const hours = String(d.getHours()).padStart(2, "0")
  const minutes = String(d.getMinutes()).padStart(2, "0")
  const seconds = String(d.getSeconds()).padStart(2, "0")
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

onMounted(() => {
  fetchModels()
})
</script>

<style scoped>
.usage-tags, .usage-options { display: flex; gap: 8px; flex-wrap: wrap; }
.ai-model-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

/* 页面标题区域 */
.page-header {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
  overflow: hidden;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 16px 16px 0 0;
  padding: 24px 32px;
  color: white;
  position: relative;
  overflow: hidden;
}

.header-content::before {
  content: "";
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  transform: rotate(30deg);
}

.title-section h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  color: white;
}

.title-section p {
  font-size: 14px;
  opacity: 0.95;
  font-weight: 500;
  margin: 0;
}

.header-illustration {
  position: relative;
  width: 100px;
  height: 100px;
}

.illustration-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  animation: float 6s ease-in-out infinite;
}

.circle-1 {
  width: 60px;
  height: 60px;
  top: 0;
  right: 0;
  animation-delay: 0s;
}

.circle-2 {
  width: 40px;
  height: 40px;
  bottom: 10px;
  left: 10px;
  animation-delay: 3s;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

.current-models {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr));
  gap: 1px;
  background: #e4edf5;
}

.current-model-info { min-width: 0; }
.current-model-name, .current-model-detail code { overflow-wrap: anywhere; }

.current-model-display {
  display: flex;
  align-items: center;
  padding: 20px 32px;
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
}

.current-model-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #409EFF, #52c41a);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
  margin-right: 16px;
  flex-shrink: 0;
}

.current-model-label {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.current-model-name {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 4px;
}

.current-model-detail {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
  flex-wrap: wrap;
}

.detail-separator {
  color: #dcdfe6;
}

.mono {
  font-family: Consolas, "Courier New", monospace;
  background: #f0f2f5;
  border-radius: 4px;
  padding: 1px 6px;
  font-size: 12px;
  color: #409EFF;
}

.endpoint-text {
  color: #606266;
  font-size: 13px;
}

/* 搜索区域 */
.actions-card {
  border-radius: 16px;
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
}

.search-form {
  padding: 4px;
}

.search-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.search-actions {
  display: flex;
  gap: 12px;
}

.search-btn, .reset-btn {
  border-radius: 8px;
}

.filter-select {
  min-width: 150px;
}

/* 表格区域 */
.table-card {
  border-radius: 16px;
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.toolbar-title {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}

.data-table {
  border-radius: 8px;
}

.action-buttons {
  display: flex;
  gap: 6px;
  justify-content: center;
}

.table-btn {
  border-radius: 6px;
  padding: 7px;
}

/* 对话框样式 */
.form-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.form-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

.ai-model-form {
  padding: 20px 0;
}

.form-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

.full-width {
  width: 100%;
}

.suggest-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.suggest-label {
  font-size: 12px;
  color: #909399;
}

.suggest-tag {
  cursor: pointer;
}

.clear-check {
  margin-top: 6px;
}

.test-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.test-alert {
  margin-top: 10px;
  width: 100%;
}

/* 对话框按钮 */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.cancel-btn,
.confirm-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.confirm-btn {
  background: linear-gradient(135deg, #409EFF, #52c41a);
  border: none;
}

.confirm-btn:hover {
  background: linear-gradient(135deg, #66b1ff, #73d13d);
  transform: translateY(-1px);
}

/* 响应式设计 */
@media screen and (max-width: 768px) {
  .ai-model-container {
    padding: 15px;
  }

  .header-content {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }

  .header-illustration {
    margin-top: 15px;
  }

  .current-model-display {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }

  .current-model-label { justify-content: center; }

  .current-model-icon {
    margin-right: 0;
    margin-bottom: 16px;
  }

  .current-model-detail {
    flex-direction: column;
    gap: 4px;
  }

  .detail-separator {
    display: none;
  }

  .search-row {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }

  .search-actions {
    justify-content: center;
  }

  .action-buttons {
    flex-wrap: wrap;
    justify-content: center;
  }

  .data-table {
    font-size: 12px;
  }

  .action-buttons {
    gap: 4px;
  }
}
</style>
