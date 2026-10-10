<template>
  <div class="ai-model-container">
    <header class="gateway-header">
      <div><div class="eyebrow">AI WORKSPACE / 模型管理</div><h1>AI 大模型选择</h1><p>集中管理供应商与自定义网关，为系统选择合适的聊天和语音模型。</p></div>
      <div class="header-actions"><el-button @click="fetchModels" :loading="loading" :icon="Refresh">刷新</el-button><el-button v-if="canManage" type="primary" :icon="Plus" data-agent-open @click="handleAdd">添加模型</el-button></div>
    </header>
    <div class="active-grid">
      <section class="active-panel">
        <div class="panel-label"><el-icon><Star /></el-icon>当前聊天模型<el-tag size="small" type="success">新请求生效</el-tag></div>
        <template v-if="chatModel"><h2>{{ chatModel.modelName }}</h2><code>{{ chatModel.modelCode }}</code><div class="active-bottom"><span>{{ providerLabel(chatModel.provider) }}</span><el-button v-if="canManage" link type="primary" @click="handleEdit(chatModel)">配置模型 →</el-button></div></template>
        <template v-else><h2>尚未选择</h2><p>添加并启用聊天模型后，将它设为默认。</p></template>
      </section>
      <section class="active-panel voice-panel">
        <div class="panel-label"><el-icon><Connection /></el-icon>当前语音识别模型</div>
        <template v-if="voiceModel"><h2>{{ voiceModel.modelName }}</h2><code>{{ voiceModel.modelCode }}</code><div class="active-bottom"><span>{{ providerLabel(voiceModel.provider) }}</span><el-button v-if="canManage" link type="primary" @click="handleEdit(voiceModel)">配置用途 →</el-button></div></template>
        <template v-else><h2>尚未选择</h2><p>语音识别使用专门的适配器，在模型配置中选择用途。</p></template>
      </section>
    </div>
    <section class="gateway-guide"><div><strong>国内模型，也可以统一走你的网关</strong><p>选择供应商预设，或填写自定义地址、网关 API Key 和真实模型 ID。接入协议为 OpenAI Chat Completions。</p></div><el-button v-if="canManage" plain type="primary" @click="handleAddPreset('custom')">添加自定义网关</el-button></section>
    <section class="registry-panel">
      <div class="registry-heading"><h2>模型配置 <span>{{ modelList.length }}</span></h2><span class="muted">{{ modelList.filter(m => m.status === 1).length }} 个已启用 · {{ providerCount }} 家供应商</span></div>
      <div class="registry-filters">
        <el-input v-model="searchKeyword" clearable aria-label="搜索模型" placeholder="搜索名称、模型 ID 或网关地址" :prefix-icon="Search" />
        <el-select v-model="filterProvider" clearable placeholder="全部供应商" aria-label="筛选供应商"><el-option v-for="p in providerFilters" :key="p.value" :label="p.label" :value="p.value" /></el-select>
        <el-select v-model="filterStatus" clearable placeholder="全部状态" aria-label="筛选状态"><el-option label="已启用" value="1"/><el-option label="已停用" value="0"/></el-select>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <div v-loading="loading" class="model-grid">
        <article v-for="row in visibleModels" :key="row.id" class="model-card" :class="{ 'is-current': isDefaultModel(row), 'is-disabled': row.status !== 1 }" :style="{ '--provider-color': presetOf(row.provider)?.color || '#475569' }">
          <div class="card-top"><div class="provider-badge">{{ presetOf(row.provider)?.shortName || 'API' }}</div><div class="card-heading"><h3>{{ row.modelName }}</h3><span>{{ providerLabel(row.provider) }}</span></div><el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '已启用' : '已停用' }}</el-tag></div>
          <div class="usage-tags"><el-tag v-if="isDefaultModel(row)" size="small">当前聊天</el-tag><el-tag v-if="row.useForVoice" size="small" type="success">当前语音</el-tag><span v-if="!isDefaultModel(row) && !row.useForVoice" class="muted">备用配置</span></div>
          <div class="card-field"><span>模型 ID</span><code>{{ row.modelCode }}</code></div>
          <div class="card-field"><span>API 地址</span><span class="endpoint-text" :title="row.apiEndpoint || '继承全局配置'">{{ row.apiEndpoint || '继承全局配置' }}</span></div>
          <div class="card-meta"><span :class="{ 'key-missing': row.apiEndpoint && !row.hasApiKey }">{{ row.hasApiKey ? '● 独立密钥已配置' : row.apiEndpoint ? '○ 缺少网关密钥' : '◌ 使用全局凭证' }}</span><span>{{ row.maxTokens || '默认' }} Token · 温度 {{ row.temperature ?? '默认' }}</span></div>
          <p v-if="row.description" class="card-description">{{ row.description }}</p>
          <div v-if="connectionResults[row.id || 0]" class="connection-state" :class="{ failed: !connectionResults[row.id || 0].ok }" role="status">{{ describeTestResult(connectionResults[row.id || 0]) }}</div>
          <div v-if="canManage" class="card-actions">
            <el-button size="small" :icon="Connection" @click="handleQuickTest(row)" :loading="quickTestingId === row.id" :disabled="quickTestingId !== null || !canChat(row)">测连</el-button>
            <el-button size="small" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-dropdown trigger="click"><el-button size="small" aria-label="更多模型操作">更多 ▾</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item :disabled="!canChat(row) || row.status !== 1 || isDefaultModel(row)" @click="handleSetDefault(row)">设为聊天默认</el-dropdown-item><el-dropdown-item :disabled="isDefaultModel(row) || row.useForVoice" @click="handleToggleStatus(row)">{{ row.status === 1 ? '停用模型' : '启用模型' }}</el-dropdown-item><el-dropdown-item @click="handleDuplicate(row)">复制为新配置</el-dropdown-item><el-dropdown-item divided :disabled="isDefaultModel(row) || !!row.useForVoice" @click="handleDelete(row)">删除配置</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
          </div>
        </article>
      </div>
      <el-empty v-if="!loading && !visibleModels.length" :description="modelList.length ? '没有符合筛选条件的模型' : '还没有模型配置'"><el-button v-if="canManage && !modelList.length" type="primary" @click="handleAdd">添加第一个模型</el-button></el-empty>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" top="4vh" width="min(800px, calc(100vw - 24px))" class="form-dialog gateway-form-dialog" :close-on-click-modal="false" @close="handleDialogClose">
      <div v-if="!form.id" class="preset-picker"><div class="section-label">选择供应商预设</div><div class="preset-grid"><button v-for="p in PROVIDER_PRESETS" :key="p.value" type="button" class="preset-button" :class="{ selected: form.provider === p.value }" @click="choosePreset(p.value)"><span :style="{ color: p.color }">{{ p.shortName }}</span>{{ p.label }}</button></div></div>
      <div class="provider-hint"><span>{{ selectedPreset?.hint || '填写供应商或兼容网关的接入信息。' }}</span><a v-if="selectedPreset?.docs" :href="selectedPreset.docs" target="_blank" rel="noopener noreferrer">官方文档 ↗</a></div>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="130px" class="ai-model-form">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="展示名，如：千问 Omni Flash" />
          <div class="form-tip">页面上显示的名字，可随意修改</div>
        </el-form-item>
        <el-form-item label="实际请求模型" prop="modelCode">
          <el-input v-model="form.modelCode" placeholder="填写厂商模型 ID、组织/模型名或 ep-… 接入点 ID" maxlength="50" />
          <el-select v-if="modelOptions.length" v-model="form.modelCode" filterable placeholder="从预设或网关候选中选择" class="full-width model-candidates"><el-option v-for="code in modelOptions" :key="code" :label="code" :value="code" /></el-select>
          <div class="form-tip">模型 ID 原样发送给网关，支持 / : @ +；沿用现有 50 字限制，不可与其他配置重复。候选列表仅供参考，也可直接手输。</div>
        </el-form-item>
        <el-form-item label="模型用途">
          <div>
            <span v-if="capabilitiesLoading" class="form-tip">正在检查可用用途…</span>
            <template v-else>
              <div class="usage-options">
                <el-checkbox v-if="supportedUsages.includes('chat')" v-model="form.isDefault" :true-value="1" :false-value="0" :disabled="editingCurrentChat">用作聊天模型</el-checkbox>
                <el-checkbox v-if="supportedUsages.includes('voice')" v-model="form.useForVoice">用作语音识别模型</el-checkbox>
              </div>
              <div class="form-tip">{{ capabilityError || (!form.modelCode ? '填写实际请求模型后显示可用用途。' : !supportedUsages.length ? '暂未确认此模型的接入能力，不能选择用途。' : '勾选后替换该用途的当前模型；两项都勾选即可共用。') }}</div>
              <div v-if="editingCurrentChat" class="form-tip">当前聊天默认模型不能在此取消用途，请先将其他模型设为聊天默认。</div>
              <div v-if="supportedUsages.includes('voice')" class="form-tip">{{ form.modelCode.includes('asr-flash-realtime') ? '支持边说边出字，结束后核对发送。' : '结束录音后转为文字，核对修改后再发送。' }}</div>
            </template>
          </div>
        </el-form-item>
        <el-form-item label="提供商" prop="provider">
          <el-select v-model="form.provider" placeholder="请选择提供商" class="full-width" @change="applyProviderPreset">
            <el-option
              v-for="p in PROVIDER_PRESETS"
              :key="p.value"
              :label="p.label"
              :value="p.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="API 地址" prop="apiEndpoint">
          <el-input v-model="form.apiEndpoint" placeholder="如：https://gateway.example.com/v1" clearable :disabled="!!form.clearApiConfig" maxlength="255" />
          <div class="form-tip">保留 /v1、/api/v3 等网关路径；粘贴完整 /chat/completions 地址会自动归一化。地址与 Key 都留空时继承全局配置。</div>
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            clearable
            autocomplete="new-password"
            :disabled="!!form.clearApiConfig"
            :placeholder="form.id && editHasApiKey ? '已配置（留空则不修改）' : '请输入该模型的 API Key'"
          />
          <div class="form-tip">{{ endpointChanged ? '网关地址已更改，请重新填写对应的 Key。' : '密钥仅保存在后端；编辑留空会沿用该地址已保存的密钥。独立网关必须使用自己的 Key。' }}</div>
          <el-checkbox v-if="form.id" v-model="form.clearApiConfig" class="clear-check">
            清空独立配置，该模型回退到全局地址与 Key
          </el-checkbox>
        </el-form-item>
        <el-form-item label="获取模型列表"><el-button :loading="discovering" :disabled="testing || saving" @click="handleDiscover">{{ discovering ? '正在获取…' : '从网关获取模型' }}</el-button><div class="form-tip" role="status">{{ discoveryMessage || '使用 /models 获取当前 Key 可访问的模型；网关不提供列表时仍可手动填写 ID。' }}</div></el-form-item>
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
            <el-radio :label="0" :disabled="mustRemainEnabled">禁用</el-radio>
          </el-radio-group>
          <div v-if="mustRemainEnabled" class="form-tip">当前聊天或语音模型必须保持启用，请先选择替代模型后再停用。语音用途也可在此取消并保存，再次编辑时停用。</div>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入模型描述" />
        </el-form-item>
        <el-form-item label="连接测试">
          <div class="test-row">
            <el-button type="success" plain @click="handleDialogTest" :loading="testing" :disabled="discovering || saving || form.apiEndpoint?.endsWith('/realtime') || !supportedUsages.includes('chat')">
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
          <el-button class="confirm-btn" type="primary" @click="handleSave" :loading="saving" :disabled="testing || discovering || capabilitiesLoading || !!capabilityError">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import logger from "@/utils/logger"
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from "vue"
import { ElMessage, ElMessageBox } from "element-plus"
import { Plus, Edit, Search, Star, Refresh, Connection } from "@element-plus/icons-vue"
import aiModelService, { PROVIDER_PRESETS } from "@/api/aiModel"
import type { AIModel } from "@/api/aiModel"
import type { FormInstance } from "element-plus"
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
const formRef = ref<FormInstance>()
const testResult = ref<TestResult | null>(null)
const editHasApiKey = ref(false)
const editEndpoint = ref("")
const discovering = ref(false)
const discoveredModels = ref<string[]>([])
const discoveryMessage = ref("")
const connectionResults = ref<Record<number, TestResult>>({})
const canManage = computed(() => authStore.hasPermission('ai:model:view'))
const presetOf = (provider?: string) => PROVIDER_PRESETS.find(p => p.value === provider)
const chatModel = computed(() => currentModels.value.find(isDefaultModel))
const voiceModel = computed(() => currentModels.value.find(m => m.useForVoice))
const providerCount = computed(() => new Set(modelList.value.map(m => m.provider)).size)
const providerFilters = computed(() => [
  ...PROVIDER_PRESETS,
  ...[...new Set(modelList.value.map(m => m.provider))].filter(value => !presetOf(value)).map(value => ({ value, label: value }))
])
const visibleModels = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  return modelList.value.filter(row => (!filterProvider.value || row.provider === filterProvider.value)
    && (filterStatus.value === '' || !filterStatus.value || String(row.status) === filterStatus.value)
    && (!keyword || [row.modelName, row.modelCode, row.provider, providerLabel(row.provider), row.apiEndpoint, row.description]
      .some(value => value?.toLowerCase().includes(keyword))))
    .slice().sort((a, b) => Number(isDefaultModel(b)) - Number(isDefaultModel(a)))
})
const canChat = (row: AIModel) => !row.apiEndpoint?.endsWith('/realtime') && !!row.supportedUsages?.includes('chat')
const selectedPreset = computed(() => presetOf(form.value.provider))
const modelOptions = computed(() => [...new Set([...discoveredModels.value, ...(selectedPreset.value?.models || [])])])
const normalizeAddress = (value = '') => value.trim().replace(/\/+$/, '').replace(/\/chat\/completions$/, '').replace(/\/+$/, '')
const endpointChanged = computed(() => !!form.value.id && normalizeAddress(form.value.apiEndpoint) !== normalizeAddress(editEndpoint.value))

const defaultProvider = "custom"
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
const editingCurrentChat = computed(() => !!form.value.id && modelList.value.some(row => row.id === form.value.id && isDefaultModel(row)))
const editingCurrentVoice = computed(() => !!form.value.id && modelList.value.some(row => row.id === form.value.id && !!row.useForVoice))
const mustRemainEnabled = computed(() => editingCurrentChat.value || editingCurrentVoice.value || form.value.isDefault === 1 || form.value.isDefault === true || !!form.value.useForVoice)
watch(() => [form.value.apiEndpoint, form.value.apiKey, form.value.id, form.value.clearApiConfig], () => {
  discoveredModels.value = []; discoveryMessage.value = ''; testResult.value = null
})
watch(() => [form.value.modelCode, form.value.maxTokens, form.value.temperature], () => { testResult.value = null })
const supportedUsages = ref<string[]>([])
const capabilitiesLoading = ref(false)
const capabilityError = ref('')
let capabilityRequest = 0
watch(() => [form.value.modelCode, form.value.id] as const, async ([code]) => {
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
    { pattern: /^[A-Za-z0-9][A-Za-z0-9._:/@+-]{0,49}$/, message: "字母数字打头，可含 . _ - / : @ +，最多50个字符", trigger: "blur" }
  ],
  provider: [{ required: true, message: "请选择提供商", trigger: "change" }],
  apiEndpoint: [
    {
      validator: (_rule, value, callback) => {
        if (!value || !String(value).trim()) return callback()
        if (form.value.clearApiConfig) return callback()
        try {
          const url = new URL(normalizeAddress(value))
          if (!['http:', 'https:'].includes(url.protocol) || !url.hostname) throw new Error()
          if (url.username || url.password || url.search || url.hash) return callback(new Error('地址不能包含密钥、查询参数或片段'))
          if (/\/(responses|messages)$/.test(url.pathname)) return callback(new Error('请填写 Chat Completions 兼容基地址'))
        } catch { return callback(new Error('请输入有效的 http(s) API 基地址')) }
        return callback()
      },
      trigger: "blur"
    }
  ],
  apiKey: [{ validator: (_rule, value, callback) => {
    if (form.value.clearApiConfig) return callback()
    if (form.value.apiEndpoint?.trim() && !value?.trim() && (!editHasApiKey.value || endpointChanged.value))
      return callback(new Error('新增或更换网关地址时，请填写对应的完整 API Key'))
    if (value && (value.includes('****') || /[\x00-\x1f\x7f]/.test(value))) return callback(new Error('请输入完整且不含控制字符的 API Key'))
    callback()
  }, trigger: 'blur' }],
  maxTokens: [{ required: true, message: "请输入最大Token数", trigger: "blur" }],
  temperature: [{ required: true, message: "请输入温度参数", trigger: "blur" }]
}

const isDefaultModel = (row: AIModel) => row.isDefault === 1 || row.isDefault === true

const fetchModels = async () => {
  loading.value = true
  connectionResults.value = {}
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

const handleReset = () => { searchKeyword.value = ""; filterStatus.value = ""; filterProvider.value = "" }

// ===== 智能填表：AI 模型弹窗（只写响应式 model，不触发提交） =====
const formContext = useFormContext()
// apiKey 不向 AI 开放；isDefault（聊天用途）、useForVoice（语音用途）和 clearApiConfig
// 由管理员手动选择，不能通过 AI 填表切换系统模型或清除凭据。
const aiModelFillFields = (): FormFieldDescriptor[] => [
  { prop: 'modelName', label: '模型名称', type: 'input', required: true },
  { prop: 'modelCode', label: '实际请求模型', type: 'input', required: true, patternHint: '真实模型ID原样发送，支持 / : @ +，最多50个字符' },
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

const previousProvider = ref(defaultProvider)
const applyProviderPreset = (value: string) => {
  const oldPreset = presetOf(previousProvider.value)
  const preset = presetOf(value)
  const before = normalizeAddress(form.value.apiEndpoint)
  if (!before || before === normalizeAddress(oldPreset?.endpoint)) form.value.apiEndpoint = preset?.endpoint || ''
  if (before !== normalizeAddress(form.value.apiEndpoint)) form.value.apiKey = ''
  if (!form.value.modelCode || oldPreset?.models.includes(form.value.modelCode)) form.value.modelCode = preset?.models[0] || ''
  form.value.maxTokens = preset?.maxTokens || 4096
  form.value.temperature = preset?.temperature ?? 0.7
  previousProvider.value = value
}
const choosePreset = (value: string) => { form.value.provider = value; applyProviderPreset(value) }
const handleAddPreset = (value: string) => { handleAdd(); choosePreset(value) }
const connectionFingerprint = () => JSON.stringify([form.value.id, form.value.apiEndpoint, form.value.apiKey, form.value.clearApiConfig])
const probeFingerprint = () => JSON.stringify([connectionFingerprint(), form.value.modelCode, form.value.maxTokens, form.value.temperature])
let dialogTestRequest = 0
let discoveryRequest = 0
const handleDiscover = async () => {
  try { await formRef.value?.validateField(['apiEndpoint', 'apiKey']) } catch { return }
  const mine = ++discoveryRequest, fingerprint = connectionFingerprint()
  discovering.value = true; discoveryMessage.value = ''; discoveredModels.value = []
  try {
    const response = await aiModelService.discoverModels({ id: form.value.id, apiEndpoint: form.value.apiEndpoint, apiKey: form.value.apiKey, clearApiConfig: form.value.clearApiConfig, modelCode: form.value.modelCode }) as unknown as AIModelEnvelope<{models: string[], endpoint: string}>
    if (mine !== discoveryRequest || fingerprint !== connectionFingerprint() || !dialogVisible.value) return
    if (response.code !== 200 || !Array.isArray(response.data?.models)) throw new Error(response.message || response.msg || '获取模型列表失败')
    discoveredModels.value = response.data.models
    discoveryMessage.value = response.data.models.length ? '已获取 ' + response.data.models.length + ' 个模型，在上方候选列表中选择。' : '网关未返回可用模型，请手动填写准确的模型 ID。'
  } catch (error) {
    if (mine === discoveryRequest && fingerprint === connectionFingerprint() && dialogVisible.value) discoveryMessage.value = (error instanceof Error ? error.message : '获取模型列表失败') + '，仍可手动填写模型 ID。'
  } finally { if (mine === discoveryRequest) discovering.value = false }
}
const handleSetDefault = async (row: AIModel) => {
  await ElMessageBox.confirm('将“' + row.modelName + '”设为系统聊天默认模型？后续新请求会使用该配置。', '切换聊天模型', {type:'info'}).then(async () => {
    const response = await aiModelService.setAsDefault(row.id!, authStore.user?.username || 'admin') as unknown as AIModelEnvelope
    if (response.code === 200) { ElMessage.success('聊天模型已切换'); await fetchModels() }
  }).catch(error => { if (error !== 'cancel' && error !== 'close') ElMessage.error('切换失败，请检查配置') })
}
const handleToggleStatus = async (row: AIModel) => {
  try {
    const response = await aiModelService.updateAIModel(row.id!, {status: row.status === 1 ? 0 : 1, updater: authStore.user?.username || 'admin'}) as unknown as AIModelEnvelope
    if (response.code === 200) { ElMessage.success(row.status === 1 ? '模型已停用' : '模型已启用'); await fetchModels() }
  } catch { ElMessage.error('更新模型状态失败') }
}

const openDialog = (title: string) => {
  dialogTitle.value = title
  testResult.value = null
  dialogVisible.value = true
  registerAiModelForm(title)
  nextTick(() => {
    formRef.value?.clearValidate()
    const root = formRef.value?.$el?.closest('.gateway-form-dialog')
    root?.querySelector('.preset-grid')?.scrollTo(0, 0)
    root?.querySelector('.el-dialog__body')?.scrollTo(0, 0)
  })
}

const handleAdd = () => {
  form.value = emptyForm()
  editHasApiKey.value = false
  editEndpoint.value = ""; previousProvider.value = defaultProvider
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
  editEndpoint.value = row.apiEndpoint || ""; previousProvider.value = row.provider
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
    const response = await aiModelService.duplicateModel(row.id!, authStore.user?.username || 'admin') as unknown as AIModelEnvelope<AIModel>
    if (response.code === 200 && response.data) {
      handleEdit(response.data); dialogTitle.value = '复制为新配置'; registerAiModelForm(dialogTitle.value)
      ElMessage.info('已生成未保存模板。请填写不同的真实模型 ID 和对应的 Key，启用后保存。')
    }
  } catch { ElMessage.error('复制配置失败') }
}

const describeTestResult = (data: TestResult) => {
  if (data.ok) {
    return `连接成功 · ${data.latencyMs}ms · 实际请求 ${data.requestModel} · 回复：${data.reply}`
  }
  return `连接失败${data.latencyMs != null ? ` · ${data.latencyMs}ms` : ""} · ${data.error}`
}

const handleQuickTest = async (row: AIModel) => {
  if (quickTestingId.value !== null) return
  quickTestingId.value = row.id as number
  try {
    const response = (await aiModelService.testConnection({ id: row.id })) as unknown as AIModelEnvelope<TestResult>
    if (response.code === 200 && response.data) {
      connectionResults.value[row.id!] = response.data
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
  const mine = ++dialogTestRequest, fingerprint = probeFingerprint()
  testing.value = true
  testResult.value = null
  try {
    const { hasApiKey: _omit, ...payload } = form.value
    const response = (await aiModelService.testConnection(payload)) as unknown as AIModelEnvelope<TestResult>
    if (mine !== dialogTestRequest || fingerprint !== probeFingerprint() || !dialogVisible.value) return
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
    if (mine === dialogTestRequest) testing.value = false
  }
}

const handleSave = async () => {
  if (!formRef.value || capabilitiesLoading.value || capabilityError.value) return

  if (editingCurrentChat.value && form.value.isDefault !== 1 && form.value.isDefault !== true) {
    ElMessage.warning('请先将其他模型设为聊天默认，再取消当前模型的聊天用途')
    return
  }
  if (mustRemainEnabled.value && form.value.status !== 1) {
    ElMessage.warning('当前聊天或语音模型必须保持启用，请先保存解除语音用途或选择替代模型')
    return
  }

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
  dialogTestRequest++; discoveryRequest++; testing.value = false; discovering.value = false
  nextTick(() => {
    if (formRef.value) {
      formRef.value.resetFields()
    }
  })
}

onMounted(() => {
  fetchModels()
})
</script>

<style scoped>
.ai-model-container { padding: 24px; min-height: 100%; color: #243248; background: #f6f8fb; }
.gateway-header { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-bottom:24px; }
.eyebrow { color:#718098; font-size:11px; font-weight:600; letter-spacing:1.4px; margin-bottom:8px; }
.gateway-header h1 { margin:0 0 8px; font-size:28px; font-weight:700; letter-spacing:-.7px; }
.gateway-header p { margin:0; font-size:13px; line-height:1.7; color:#718098; }
.header-actions { display:flex; gap:10px; flex-shrink:0; }
.header-actions :deep(.el-button + .el-button) { margin-left:0; }
.active-grid { display:grid; grid-template-columns:1fr 1fr; gap:18px; margin-bottom:18px; }
.active-panel { padding:22px 24px 16px; border:1px solid #cfdbef; border-radius:16px; background:linear-gradient(130deg,#f0f5ff,#fff 80%); min-width:0; }
.voice-panel { border-color:#dce5ee; background:linear-gradient(130deg,#f0f8f7,#fff 80%); }
.panel-label { display:flex; align-items:center; gap:8px; font-size:12px; font-weight:600; color:#526580; }
.panel-label :deep(.el-tag) { margin-left:auto; }
.active-panel h2 { font-size:21px; margin:18px 0 8px; font-weight:650; }
.active-panel code { color:#657894; font-size:12px; overflow-wrap:anywhere; }
.active-panel p { font-size:12px; color:#7a8798; line-height:1.6; }
.active-bottom { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-top:16px; padding-top:12px; border-top:1px solid #e1e8f3; font-size:12px; color:#6b7b92; }
.gateway-guide { display:flex; justify-content:space-between; align-items:center; gap:16px; padding:16px 20px; margin-bottom:24px; background:#fff; border:1px solid #e1e7ef; border-radius:12px; }
.gateway-guide strong { font-size:13px; font-weight:600; }
.gateway-guide p { color:#78869a; font-size:12px; margin:6px 0 0; line-height:1.7; }
.registry-panel { min-width:0; }
.registry-heading { display:flex; justify-content:space-between; align-items:center; gap:12px; margin-bottom:16px; }
.registry-heading h2 { font-size:17px; margin:0; }
.registry-heading h2 span { display:inline-block; color:#64748b; font-size:12px; background:#e8edf4; padding:3px 8px; border-radius:6px; margin-left:5px; vertical-align:middle; }
.muted { color:#8a95a5; font-size:12px; }
.registry-filters { display:grid; grid-template-columns:minmax(220px,1fr) 210px 140px auto; gap:12px; margin-bottom:20px; }
.model-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(300px,1fr)); gap:16px; }
.model-card { display:flex; flex-direction:column; min-width:0; border:1px solid #e1e7ef; border-radius:14px; background:#fff; padding:19px; box-shadow:0 3px 12px #263b5910; }
.model-card.is-current { border-color:#aec6f4; box-shadow:0 0 0 1px #dce8ff; }
.model-card.is-disabled { background:#fcfcfd; }
.card-top { display:flex; gap:11px; align-items:center; margin-bottom:14px; }
.provider-badge { display:flex; align-items:center; justify-content:center; flex-shrink:0; width:39px; height:39px; border-radius:11px; color:var(--provider-color); font-size:17px; font-weight:700; background:color-mix(in srgb,var(--provider-color) 10%,white); }
.card-heading { min-width:0; flex:1; }
.card-heading h3 { font-size:14px; font-weight:600; margin:0 0 5px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.card-heading span { color:#8a95a5; font-size:11px; }
.usage-tags { display:flex; align-items:center; gap:6px; flex-wrap:wrap; margin-bottom:17px; min-height:20px; }
.card-field { display:flex; flex-direction:column; gap:6px; margin-bottom:14px; min-width:0; }
.card-field > span:first-child { font-size:10px; letter-spacing:.3px; color:#95a0af; }
.card-field code { font-family:ui-monospace,SFMono-Regular,Consolas,monospace; font-size:12px; color:#435472; overflow-wrap:anywhere; }
.endpoint-text { display:block; max-width:100%; color:#718097; font-size:11px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.card-meta { display:flex; justify-content:space-between; flex-wrap:wrap; gap:8px; color:#8793a3; font-size:10px; margin-top:2px; }
.card-meta > span:first-child { color:#498574; }
.card-meta .key-missing { color:#d58928; }
.card-description { font-size:11px; line-height:1.7; color:#8793a3; overflow-wrap:anywhere; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; margin:13px 0 0; }
.card-actions { display:flex; gap:8px; padding-top:15px; border-top:1px solid #eef1f5; margin-top:18px; }
.card-actions :deep(.el-button + .el-button) { margin-left:0; }
.card-actions :deep(.el-dropdown) { margin-left:auto; }
.connection-state { padding:9px 11px; background:#edf8f2; border-radius:7px; font-size:11px; color:#2d8666; line-height:1.6; margin-top:12px; overflow-wrap:anywhere; }
.connection-state.failed { background:#fff2f0; color:#bb564e; }
.section-label { font-size:12px; font-weight:600; color:#62728b; margin-bottom:12px; }
.preset-picker { margin-bottom:18px; }
.preset-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; max-height:208px; overflow-y:auto; padding:1px; }
.preset-button { display:flex; align-items:center; gap:9px; padding:10px 12px; background:#fff; border:1px solid #e4e9f0; border-radius:8px; color:#53627a; font:inherit; font-size:11px; cursor:pointer; text-align:left; }
.preset-button span { min-width:23px; font-weight:700; font-size:13px; }
.preset-button:hover,.preset-button.selected { border-color:#6b9cec; background:#f1f6ff; }
.preset-button:focus-visible { outline:2px solid #409eff; outline-offset:2px; }
.provider-hint { display:flex; justify-content:space-between; gap:12px; margin-bottom:22px; font-size:12px; line-height:1.8; color:#75849b; padding:12px 14px; border-radius:8px; background:#f5f8fc; }
.provider-hint a { flex-shrink:0; color:#477ccd; text-decoration:none; }
.full-width { width:100%; }
.model-candidates { margin-top:8px; }
.form-tip { font-size:11px; color:#8a97ab; line-height:1.7; margin-top:5px; width:100%; overflow-wrap:anywhere; }
.usage-options { display:flex; gap:12px; flex-wrap:wrap; }
.clear-check { white-space:normal; height:auto; margin-top:8px; }
.clear-check :deep(.el-checkbox__label) { white-space:normal; font-size:12px; line-height:1.6; }
.test-row { display:flex; flex-wrap:wrap; align-items:center; gap:8px; }
.test-alert { width:100%; margin-top:10px; }
.test-alert :deep(.el-alert__title) { overflow-wrap:anywhere; line-height:1.7; }
.dialog-footer { display:flex; justify-content:flex-end; gap:10px; }
:global(.gateway-form-dialog .el-dialog__body) { max-height:calc(100vh - 190px); overflow-y:auto; padding-right:5px; }
@media (max-width:1100px) { .registry-filters { grid-template-columns:minmax(180px,1fr) 170px 120px auto; } }
@media (max-width:768px) {
  .ai-model-container { padding:16px 12px; }
  .gateway-header { align-items:flex-start; flex-direction:column; gap:16px; }
  .gateway-header h1 { font-size:24px; }
  .header-actions { width:100%; }
  .header-actions :deep(.el-button) { flex:1; }
  .active-grid { grid-template-columns:1fr; gap:12px; }
  .active-panel { padding:18px 20px 14px; }
  .active-panel h2 { font-size:19px; }
  .gateway-guide { align-items:flex-start; flex-direction:column; padding:15px; }
  .registry-heading { align-items:flex-start; flex-direction:column; gap:8px; }
  .registry-filters { grid-template-columns:1fr 1fr; gap:9px; }
  .registry-filters > :first-child { grid-column:1 / -1; }
  .registry-filters > :last-child { grid-column:1 / -1; }
  .model-grid { grid-template-columns:minmax(0,1fr); }
  .preset-grid { grid-template-columns:1fr 1fr; max-height:190px; }
  .preset-button { padding:9px 7px; gap:5px; font-size:10px; }
  .provider-hint { flex-direction:column; gap:5px; }
  :global(.gateway-form-dialog .el-form-item) { display:block; }
  :global(.gateway-form-dialog .el-form-item__label) { justify-content:flex-start; width:auto !important; height:25px; margin-bottom:5px; }
  :global(.gateway-form-dialog .el-form-item__content) { margin-left:0 !important; }
  :global(.gateway-form-dialog .el-dialog__body) { max-height:calc(100vh - 190px); }
}
</style>
