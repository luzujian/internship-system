<script setup lang="ts">
import logger from '@/utils/logger'
import { useRoute } from 'vue-router'
import { getRoleName, getRiskName, getRiskTagType, getEventTypeName as getBaseEventTypeName, getResultTagType, getRoleTagType, getResultName } from '@/utils/securityEventPresentation'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useFormContext } from '@/agent/formContext'
import {
  getLogSummaryApi, getOperationsApi, getLoginEventsApi, getSecurityEventsApi,
  getLogDetailApi, getLogOptionsApi, exportLogsApi,
  getRetentionPoliciesApi, updateRetentionPoliciesApi, previewRetentionApi, runRetentionApi,
  verifyIntegrityApi,
  type LogSummary, type OperateLogItem, type LoginLogItem, type SecurityEventItem, type LogOptions, type LogListParams, type IntegrityVerifyReport
} from '@/api/log'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, DataAnalysis, Document, Warning, Lock, WarningFilled, ArrowDown, ArrowUp, CopyDocument } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
const route = useRoute()

// ===== 标签与权限 =====
type TabName = 'operations' | 'login-events' | 'security-events'

const tabPermissionMap: Record<TabName, string> = {
  'operations': 'log:view',
  'login-events': 'log:login:view',
  'security-events': 'log:security:view'
}

const tabCategoryMap: Record<TabName, string> = {
  'operations': 'operations',
  'login-events': 'login-events',
  'security-events': 'security-events'
}

const activeTab = ref<TabName>('operations')
const hasPermission = (code: string) => authStore.hasPermission(code)

const visibleTabs = computed(() => {
  const tabs: Array<{ name: TabName; label: string }> = [
    { name: 'operations', label: '操作日志' },
    { name: 'login-events', label: '登录日志' },
    { name: 'security-events', label: '安全事件' }
  ]
  return tabs.filter(tab => hasPermission(tabPermissionMap[tab.name]))
})

const onTabChange = (tab: string | number) => {
  const target = visibleTabs.value.find(item => item.name === tab)
  if (!target || target.name === activeTab.value) return
  const applied = appliedParams.value
  activeTab.value = target.name
  // 标签切换沿用已经生效的公共条件，避免未查询或无效条件影响新标签。
  Object.assign(searchForm, emptyFilters(), {
    timeRange: applied.startTime && applied.endTime ? [applied.startTime, applied.endTime] : defaultTimeRange(),
    operatorName: applied.operatorName || '', role: applied.role || '',
    result: activeTab.value === 'login-events' && applied.result === 'ATTEMPTED' ? '' : (applied.result || ''),
    riskLevel: applied.riskLevel || '', ip: applied.ip || '', requestId: applied.requestId || ''
  })
  handleSearch()
}

// ===== 今日概览：统计独立于列表筛选 =====
const summary = ref<LogSummary | null>(null)
const summaryLoading = ref(false)
const summaryError = ref(false)
const loadSummary = async () => {
  summaryLoading.value = true
  summaryError.value = false
  try {
    const response = await getLogSummaryApi() as unknown as { code: number; data: LogSummary }
    if (response.code !== 200) throw new Error('获取概览失败')
    summary.value = response.data
  } catch (error) {
    summary.value = null
    summaryError.value = true
    logger.error('获取日志统计失败:', error)
  } finally {
    summaryLoading.value = false
  }
}
const summaryCards = computed(() => [
  { key: 'todayOperations', label: '今日操作', hint: '全部业务操作', tab: 'operations', result: '', risk: '', icon: Document, tone: 'blue' },
  { key: 'todayFailedOperations', label: '操作失败', hint: '查看失败的业务操作', tab: 'operations', result: 'FAILURE', risk: '', icon: Warning, tone: 'orange' },
  { key: 'todayFailedLogins', label: '登录失败', hint: '排查账号与认证异常', tab: 'login-events', result: 'FAILURE', risk: '', icon: Lock, tone: 'orange' },
  { key: 'todayHighRiskEvents', label: '高风险事件', hint: '高风险及严重安全事件', tab: 'security-events', result: '', risk: 'HIGH,CRITICAL', icon: WarningFilled, tone: 'red' }
].filter(card => hasPermission(tabPermissionMap[card.tab as TabName])))
const summaryCount = (key: string) => summary.value?.[key as keyof LogSummary]?.toLocaleString() ?? '—'

// ===== 筛选：编辑条件与已应用条件分开，翻页/导出始终对应当前结果 =====
const formatDate = (d: Date) => {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
const timeRangeFor = (days: number): [string, string] => {
  const end = new Date()
  const start = days === 0 ? new Date(end.getFullYear(), end.getMonth(), end.getDate()) : new Date(end.getTime() - days * 86400000)
  return [formatDate(start), formatDate(end)]
}
const defaultTimeRange = () => timeRangeFor(7)
const emptyFilters = () => ({
  timeRange: defaultTimeRange() as [string, string] | null,
  operatorName: '', role: '', module: '', action: '', result: '', riskLevel: '', ip: '', requestId: '', targetId: ''
})
const searchForm = reactive(emptyFilters())
const advancedVisible = ref(false)
const sortOrder = ref('desc')
const liveRangeDays = ref<number | null>(7)
const appliedParams = ref<LogListParams>({})
const rangeShortcuts = [
  { text: '今天', value: () => timeRangeFor(0).map(value => new Date(value.replace(' ', 'T'))) },
  { text: '最近 7 天', value: () => timeRangeFor(7).map(value => new Date(value.replace(' ', 'T'))) },
  { text: '最近 30 天', value: () => timeRangeFor(30).map(value => new Date(value.replace(' ', 'T'))) }
]
const options = ref<LogOptions>({ actions: [], modules: [], eventTypes: [], roles: [], riskLevels: [], results: [] })
const optionsError = ref(false)
const loadOptions = async () => {
  optionsError.value = false
  try {
    const response = await getLogOptionsApi() as unknown as { code: number; data: LogOptions }
    if (response.code !== 200) throw new Error('获取筛选选项失败')
    options.value = response.data
  } catch (error) {
    optionsError.value = true
    logger.error('获取筛选选项失败:', error)
  }
}
const buildParams = (): LogListParams => ({
  startTime: searchForm.timeRange?.[0] || undefined,
  endTime: searchForm.timeRange?.[1] || undefined,
  operatorName: searchForm.operatorName.trim() || undefined,
  role: searchForm.role || undefined,
  module: activeTab.value === 'operations' ? (searchForm.module || undefined) : undefined,
  action: activeTab.value !== 'login-events' ? (searchForm.action || undefined) : undefined,
  result: searchForm.result || undefined,
  riskLevel: searchForm.riskLevel || undefined,
  ip: searchForm.ip.trim() || undefined,
  requestId: searchForm.requestId.trim() || undefined,
  targetId: activeTab.value !== 'login-events' ? (searchForm.targetId.trim() || undefined) : undefined
})
const filtersDirty = computed(() => JSON.stringify(buildParams()) !== JSON.stringify(appliedParams.value))
const appliedFilterLabels = computed(() => {
  const p = appliedParams.value
  return [
    p.operatorName && `操作人：${p.operatorName}`, p.role && `角色：${getRoleName(p.role)}`,
    p.module && `模块：${getModuleName(p.module)}`,
    p.action && `类型：${activeTab.value === 'operations' ? getActionName(p.action) : getEventTypeName(p.action)}`,
    p.result && `结果：${getResultName(p.result)}`, p.riskLevel && `风险：${p.riskLevel === 'HIGH,CRITICAL' ? '高风险及严重' : getRiskName(p.riskLevel)}`,
    p.ip && `IP：${p.ip}`, p.requestId && `请求 ID：${p.requestId}`, p.targetId && `目标 ID：${p.targetId}`
  ].filter(Boolean)
})
const activeDescription = computed(() => ({
  operations: '追溯实习、学生、企业、岗位和权限等业务操作，核对操作人、目标与处理结果。',
  'login-events': '核查登录结果和失败原因。认证失败且身份未确认时角色为未知；查找失败记录可选择全部角色，并扩大时间范围。',
  'security-events': '关注账号锁定、权限访问及审计管理事件；风险等级代表关注程度，不等同于已发生安全事故。'
}[activeTab.value]))

// ===== 列表 =====
const loading = ref(false)
const listError = ref('')
const lastUpdated = ref('')
const operateTableData = ref<OperateLogItem[]>([])
const loginTableData = ref<LoginLogItem[]>([])
const securityTableData = ref<SecurityEventItem[]>([])
const pagination = reactive({ currentPage: 1, pageSize: 20, total: 0 })
let listRequestVersion = 0
const clearRows = () => {
  operateTableData.value = []
  loginTableData.value = []
  securityTableData.value = []
}
const queryPage = async (): Promise<void> => {
  if (!hasPermission(tabPermissionMap[activeTab.value])) return
  const version = ++listRequestVersion
  const category = activeTab.value
  const params = { ...appliedParams.value, page: pagination.currentPage, pageSize: pagination.pageSize, sortOrder: sortOrder.value }
  loading.value = true
  listError.value = ''
  clearRows()
  try {
    const api = category === 'operations' ? getOperationsApi : category === 'login-events' ? getLoginEventsApi : getSecurityEventsApi
    const response = await api(params) as unknown as { code: number; data: { rows: unknown[]; total: number }; message?: string; msg?: string }
    if (version !== listRequestVersion) return
    if (response.code !== 200 || !response.data) throw new Error(response.message || response.msg || '查询日志失败')
    pagination.total = response.data.total || 0
    if (category === 'operations') operateTableData.value = response.data.rows as OperateLogItem[]
    else if (category === 'login-events') loginTableData.value = response.data.rows as LoginLogItem[]
    else securityTableData.value = response.data.rows as SecurityEventItem[]
    lastUpdated.value = formatDate(new Date())
  } catch (error) {
    if (version !== listRequestVersion) return
    listError.value = error instanceof Error ? error.message : '日志加载失败，请稍后重试'
    logger.error('查询日志列表失败:', error)
  } finally {
    if (version === listRequestVersion) loading.value = false
  }
}
const handleSearch = () => {
  if (!searchForm.timeRange?.[0] || !searchForm.timeRange?.[1]) searchForm.timeRange = defaultTimeRange()
  const [start, end] = searchForm.timeRange.map(value => new Date(value.replace(' ', 'T')).getTime())
  if (!Number.isFinite(start) || !Number.isFinite(end) || start > end || end - start > 365 * 86400000) {
    ElMessage.warning('请选择有效时间范围，开始时间不能晚于结束时间，跨度不超过 365 天')
    return
  }
  appliedParams.value = buildParams()
  pagination.currentPage = 1
  queryPage()
}
const handleSizeChange = (newSize: number) => {
  pagination.pageSize = newSize
  pagination.currentPage = 1
  queryPage()
}
const handleCurrentChange = (newCurrent: number) => {
  pagination.currentPage = newCurrent
  queryPage()
}
const resetSearch = () => {
  Object.assign(searchForm, emptyFilters())
  sortOrder.value = 'desc'
  liveRangeDays.value = 7
  handleSearch()
}
const quickFilter = (tab: string, result = '', risk = '', days = 0) => {
  if (!hasPermission(tabPermissionMap[tab as TabName])) return
  activeTab.value = tab as TabName
  liveRangeDays.value = days
  Object.assign(searchForm, emptyFilters(), { timeRange: timeRangeFor(days), result, riskLevel: risk })
  handleSearch()
}
const refreshPage = () => {
  if (liveRangeDays.value != null) {
    const range = timeRangeFor(liveRangeDays.value)
    const draftMatches = searchForm.timeRange?.[0] === appliedParams.value.startTime && searchForm.timeRange?.[1] === appliedParams.value.endTime
    appliedParams.value = { ...appliedParams.value, startTime: range[0], endTime: range[1] }
    if (draftMatches) searchForm.timeRange = range
  }
  queryPage()
  loadSummary()
}
// ===== 详情抽屉 =====
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<Record<string, unknown> | null>(null)
const detailCategory = ref('')
const detailError = ref('')
let detailRequestVersion = 0

const openDetail = async (id: number) => {
  const category = tabCategoryMap[activeTab.value]
  const version = ++detailRequestVersion
  detailError.value = ''
  detailCategory.value = category
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  try {
    const response = await getLogDetailApi(category, id) as unknown as { code: number; message?: string; msg?: string; data: Record<string, unknown> }
    if (version !== detailRequestVersion) return
    if (response.code === 200 && response.data) {
      detailData.value = response.data
    } else {
      detailError.value = response.message || response.msg || '该日志不存在或已按留存策略清理'
    }
  } catch (error) {
    if (version !== detailRequestVersion) return
    logger.error('获取日志详情失败:', error)
    detailError.value = '详情加载失败，请关闭后重新打开'
  } finally {
    if (version === detailRequestVersion) detailLoading.value = false
  }
}

const failureDetailFields = (detail: unknown): [string, unknown][] => {
  try {
    const parsed = typeof detail === 'string' ? JSON.parse(detail) : detail
    const failure = parsed && typeof parsed === 'object' ? parsed.failure : null
    if (!failure || typeof failure !== 'object') return []
    const stageNames: Record<string, string> = {
      LOGIN: '账号登录', RESET_SEND_CODE: '找回密码：发送验证码', RESET_VERIFY_CODE: '找回密码：确认身份',
      RESET_PASSWORD: '找回密码：重置密码', REQUEST_HANDLING: '请求处理', MULTIPART_PARSE: '上传请求解析',
      EMPTY_FILE: '文件非空校验', FILE_SIZE: '文件大小校验', MEDIA_TYPE: '文件类型校验',
      FILE_SIGNATURE: '文件内容签名校验', STORAGE: '文件存储'
    }
    const fields: [string, unknown][] = [
      ['失败原因', typeof failure.reason === 'string' ? failure.reason : null],
      ['诊断代码', typeof failure.reasonCode === 'string' ? failure.reasonCode : null],
      ['异常类型', typeof failure.exceptionType === 'string' ? failure.exceptionType : null],
      ['失败阶段', typeof failure.validationStage === 'string' ? (stageNames[failure.validationStage] || failure.validationStage) : null],
      ['请求大小', Number.isFinite(failure.requestSize) ? `${failure.requestSize} 字节` : null]
    ]
    if (Array.isArray(failure.files)) {
      failure.files.slice(0, 10).forEach((file: Record<string, unknown>, index: number) => {
        if (!file || typeof file !== 'object') return
        fields.push([`文件 ${index + 1} 元信息`, [
          Number.isFinite(file.size) ? `${file.size} 字节` : null,
          typeof file.declaredMime === 'string' ? `声明类型：${file.declaredMime}` : null,
          typeof file.extension === 'string' ? `扩展名：${file.extension}` : null
        ].filter(Boolean).join('；')])
      })
    }
    return fields
  } catch { return [] }
}

const detailSections = computed<{ title: string; fields: [string, unknown][] }[]>(() => {
  if (!detailData.value) return []
  const data = detailData.value
  if (detailCategory.value === 'operations') {
    return [
      { title: '用户信息', fields: [
        ['操作人', data.operatorName], ['用户名', data.operatorUsername], ['角色', getRoleName(data.operatorRole as string)],
        ...(data.realOperatorName ? [['真实管理员身份', data.realOperatorName] as [string, unknown]] : [])
      ] },
      { title: '操作信息', fields: [
        ['模块', getModuleName(data.module as string)], ['动作', getActionName(data.operationType as string)],
        ['结果', getResultName(data.operationResult as string)],
        ['风险等级', getRiskName(data.riskLevel as string)], ['耗时', data.durationMs != null ? `${data.durationMs}ms` : null],
        ['描述', data.description], ['错误码', data.errorCode],
        ...failureDetailFields(data.detailJson)
      ] },
      { title: '目标对象', fields: [
        ['目标类型', data.targetType], ['目标ID', data.targetId],
        ['影响数量', data.affectedCount != null ? String(data.affectedCount) : null]
      ] },
      { title: '请求信息', fields: [
        ['请求方法', data.httpMethod], ['请求路径', data.requestPath], ['IP地址', data.ipAddress],
        ['设备类别', data.deviceType], ['请求 ID', data.requestId],
        ['操作时间', data.operateTime]
      ] },
      { title: '完整性哈希链', fields: data.entryHash ? [
        ['节点哈希', data.entryHash], ['前驱哈希', data.prevHash], ['密钥版本', data.keyVersion != null ? 'v' + data.keyVersion : null]
      ] : [['节点哈希', '未提供完整性信息，不能据此判定记录完整性']] },
      { title: '详情数据', fields: data.detailJson ? [['详情JSON', data.detailJson]] : [] }
    ]
  }
  if (detailCategory.value === 'login-events') {
    return [
      { title: '用户信息', fields: [
        ['操作人', data.operatorName], ['用户名', data.userName], ['角色', getRoleName(data.userType as string)]
      ] },
      { title: '登录信息', fields: [
        ['状态', getResultName(data.loginStatus as string)], ['风险等级', getRiskName(data.riskLevel as string)],
        ['错误码', data.errorCode], ['耗时', data.durationMs != null ? `${data.durationMs}ms` : null],
        ['登录时间', data.loginTime], ...failureDetailFields(data.detailJson)
      ] },
      { title: '请求信息', fields: [
        ['IP地址', data.ipAddress], ['设备类别', data.deviceType], ['请求 ID', data.requestId], ['设备信息', data.deviceInfo]
      ] },
      { title: '完整性哈希链', fields: data.entryHash ? [
        ['节点哈希', data.entryHash], ['前驱哈希', data.prevHash], ['密钥版本', data.keyVersion != null ? 'v' + data.keyVersion : null]
      ] : [['节点哈希', '未提供完整性信息，不能据此判定记录完整性']] },
      { title: '详情数据', fields: data.detailJson ? [['详情JSON', data.detailJson]] : [] }
    ]
  }
  return [
    { title: '用户信息', fields: [
      ['操作人', data.operatorName], ['用户名', data.username], ['角色', getRoleName(data.userRole as string)],
      ...(data.realOperatorName ? [['真实管理员身份', data.realOperatorName] as [string, unknown]] : [])
    ] },
    { title: '事件信息', fields: [
      ['事件类型', getEventTypeName(data.eventType as string)], ['结果', getResultName(data.result as string)],
      ['风险等级', getRiskName(data.riskLevel as string)], ['错误码', data.errorCode],
      ['影响数量', data.affectedCount != null ? String(data.affectedCount) : null],
      ['描述', data.description], ['事件时间', data.eventTime], ...failureDetailFields(data.detailJson)
    ] },
    { title: '目标对象', fields: [
      ['目标类型', data.targetType], ['目标ID', data.targetId]
    ] },
    { title: '请求信息', fields: [
      ['请求方法', data.httpMethod], ['请求路径', data.requestPath], ['IP地址', data.ipAddress],
      ['设备类别', data.deviceType], ['请求 ID', data.requestId]
    ] },
    { title: '完整性哈希链', fields: data.entryHash ? [
      ['节点哈希', data.entryHash], ['前驱哈希', data.prevHash], ['密钥版本', data.keyVersion != null ? 'v' + data.keyVersion : null]
    ] : [['节点哈希', '未提供完整性信息，不能据此判定记录完整性']] },
    { title: '详情数据', fields: data.detailJson ? [['详情JSON', data.detailJson]] : [] }
  ]
})

const copyRequestId = async () => {
  if (!detailData.value?.requestId) return
  try {
    await navigator.clipboard.writeText(String(detailData.value.requestId))
    ElMessage.success('请求 ID 已复制')
  } catch {
    ElMessage.warning('无法访问剪贴板，请在详情中手动复制请求 ID')
  }
}
const traceRequest = (tab: TabName) => {
  if (!detailData.value?.requestId || !hasPermission(tabPermissionMap[tab])) return
  const data = detailData.value
  const eventTime = String(data.operateTime || data.loginTime || data.eventTime || '')
  const eventDate = new Date(eventTime.replace(' ', 'T'))
  const range: [string, string] = Number.isFinite(eventDate.getTime())
    ? [formatDate(new Date(eventDate.getTime() - 86400000)), formatDate(new Date(eventDate.getTime() + 86400000))]
    : defaultTimeRange()
  activeTab.value = tab
  liveRangeDays.value = null
  Object.assign(searchForm, emptyFilters(), { requestId: String(data.requestId), timeRange: range })
  advancedVisible.value = true
  detailVisible.value = false
  handleSearch()
}
const formatDetailValue = (label: string, value: unknown) => {
  if (value == null || value === '') return '—'
  if (label === '详情JSON') {
    try { return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2) } catch { /* 兼容历史非 JSON 记录 */ }
  }
  return String(value)
}

// ===== 导出 =====
const exportLoading = ref(false)
const handleExport = async (): Promise<void> => {
  if (exportLoading.value || loading.value || listError.value || pagination.total === 0) return
  if (pagination.total > 100000) {
    ElMessage.warning('单次最多导出 100,000 条，请缩小筛选范围')
    return
  }
  const category = activeTab.value
  exportLoading.value = true
  try {
    const response = await exportLogsApi({ ...appliedParams.value, category }) as unknown as Blob

    // 后端错误时返回 JSON（如超限提示）
    if (response && response.type && response.type.includes('application/json')) {
      const text = await response.text()
      try {
        const err = JSON.parse(text)
        ElMessage.error(err.message || err.msg || '导出失败')
      } catch {
        ElMessage.error('导出失败')
      }
      return
    }

    const url = URL.createObjectURL(response)
    const link = document.createElement('a')
    link.href = url
    link.download = `日志导出_${category}_${formatDate(new Date()).replace(/[: ]/g, '-')}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出任务已完成')
  } catch (error) {
    logger.error('导出失败:', error)
    const errorBody = (error as { response?: { data?: unknown } })?.response?.data
    let message = '导出失败，请重试'
    if (errorBody instanceof Blob && errorBody.type.includes('json')) {
      try {
        const data = JSON.parse(await errorBody.text())
        message = data.message || data.msg || message
      } catch { /* 无法解析时保留通用错误提示 */ }
    }
    ElMessage.error(message)
  } finally {
    exportLoading.value = false
  }
}

// ===== 留存策略 =====
const retentionVisible = ref(false)
const retentionLoading = ref(false)
const retentionFetching = ref(false)
const retentionLoaded = ref(false)
const retentionPreviewLoading = ref(false)
const savedRetention = ref('')
const policySnapshot = () => JSON.stringify([retentionForm.value.highRiskDays, retentionForm.value.normalDays, retentionForm.value.loginDays])
const retentionDirty = computed(() => policySnapshot() !== savedRetention.value)
const retentionInvalid = computed(() => [retentionForm.value.highRiskDays, retentionForm.value.normalDays, retentionForm.value.loginDays].some(value => !Number.isInteger(value) || value < 30 || value > 3650) || retentionForm.value.highRiskDays < retentionForm.value.normalDays)
const retentionForm = ref({ highRiskDays: 365, normalDays: 180, loginDays: 180, logbackDays: 30 })
const retentionPreviewData = ref<Record<string, number> | null>(null)
const retentionReason = ref('')
const retentionRunning = ref(false)
watch(() => policySnapshot(), () => { retentionPreviewData.value = null })

// ===== 智能填表：日志保留策略注册到 AI 侧栏（只写响应式 model，不触发保存/执行） =====
// logbackDays 为只读展示（部署配置控制），清理原因必填文本；保存与执行仍由用户点击原按钮
const formContext = useFormContext()
const retentionProxy = {
  get highRiskDays() { return String(retentionForm.value.highRiskDays) },
  set highRiskDays(v: unknown) { const n = Number(v); if (!Number.isNaN(n)) retentionForm.value.highRiskDays = n },
  get normalDays() { return String(retentionForm.value.normalDays) },
  set normalDays(v: unknown) { const n = Number(v); if (!Number.isNaN(n)) retentionForm.value.normalDays = n },
  get loginDays() { return String(retentionForm.value.loginDays) },
  set loginDays(v: unknown) { const n = Number(v); if (!Number.isNaN(n)) retentionForm.value.loginDays = n },
  get retentionReason() { return retentionReason.value },
  set retentionReason(v: unknown) { retentionReason.value = String(v ?? '') }
}
watch(retentionVisible, (open) => {
  if (open) {
    formContext.register({
      formId: 'admin:log-retention',
      title: '日志保留策略',
      fields: [
        { prop: 'highRiskDays', label: '高风险日志保留天数', type: 'number', valueType: 'number' },
        { prop: 'normalDays', label: '普通日志保留天数', type: 'number', valueType: 'number' },
        { prop: 'loginDays', label: '登录日志保留天数', type: 'number', valueType: 'number' },
        { prop: 'retentionReason', label: '清理原因', type: 'textarea', required: true }
      ],
      getModel: () => retentionProxy,
      getValidationSummary: () => ({ missingFields: [], validationErrors: [retentionForm.value.highRiskDays, retentionForm.value.normalDays, retentionForm.value.loginDays].some(value => !Number.isInteger(Number(value)) || Number(value) < 30 || Number(value) > 3650) ? ['日志保留天数应为30到3650之间的整数'] : retentionForm.value.highRiskDays < retentionForm.value.normalDays ? ['高风险日志保留天数不能少于普通日志'] : [] })
    })
  } else formContext.unregister('admin:log-retention')
})
onUnmounted(() => formContext.unregister('admin:log-retention'))

const openRetention = async () => {
  if (retentionFetching.value || retentionLoading.value || retentionRunning.value || retentionPreviewLoading.value || integrityVerifying.value) return
  retentionVisible.value = true
  retentionFetching.value = true
  retentionLoaded.value = false
  integrityReport.value = null
  retentionPreviewData.value = null
  retentionReason.value = ''
  try {
    const response = await getRetentionPoliciesApi() as unknown as { code: number; data: typeof retentionForm.value }
    if (response.code === 200) {
      retentionForm.value = response.data
      savedRetention.value = policySnapshot()
      retentionLoaded.value = true
    } else {
      ElMessage.error('获取留存策略失败，请重试')
    }
  } catch (error) {
    logger.error('获取留存策略失败:', error)
    ElMessage.error('获取留存策略失败')
  } finally {
    retentionFetching.value = false
  }
}

const saveRetentionPolicies = async () => {
  if (!retentionLoaded.value || retentionBusy.value || retentionInvalid.value) return
  retentionLoading.value = true
  try {
    const operation = formContext.beginFormOperation('admin:log-retention', ['highRiskDays', 'normalDays', 'loginDays'])
    const response = await updateRetentionPoliciesApi({
      highRiskDays: retentionForm.value.highRiskDays,
      normalDays: retentionForm.value.normalDays,
      loginDays: retentionForm.value.loginDays
    }) as unknown as { code: number; data: typeof retentionForm.value; message?: string }
    if (response.code === 200) {
      operation.complete({ status: 'saved', label: '日志保留策略已保存，未执行清理', url: '/admin/logs' })
      retentionForm.value = response.data
      savedRetention.value = policySnapshot()
      retentionPreviewData.value = null
      ElMessage.success('留存策略已更新，请重新预览到期数据')
    } else {
      ElMessage.error(response.message || (response as { msg?: string }).msg || '更新留存策略失败')
    }
  } catch (error) {
    logger.error('更新留存策略失败:', error)
    ElMessage.error('更新留存策略失败')
  } finally {
    retentionLoading.value = false
  }
}

const previewRetention = async () => {
  if (!retentionLoaded.value || retentionDirty.value || retentionBusy.value) return
  retentionPreviewLoading.value = true
  retentionPreviewData.value = null
  const snapshot = policySnapshot()
  try {
    const response = await previewRetentionApi() as unknown as { code: number; message?: string; msg?: string; data: Record<string, number> }
    if (response.code === 200) {
      if (snapshot === policySnapshot()) retentionPreviewData.value = response.data
    } else {
      ElMessage.error(response.message || response.msg || '预览失败')
    }
  } catch (error) {
    logger.error('留存预览失败:', error)
    ElMessage.error('留存预览失败')
  } finally {
    retentionPreviewLoading.value = false
  }
}

const retentionPreviewTotal = computed(() => {
  const data = retentionPreviewData.value
  if (!data) return 0
  return (data.operateLogNormal || 0) + (data.operateLogHighRisk || 0) + (data.loginLog || 0)
    + (data.securityEventNormal || 0) + (data.securityEventHighRisk || 0)
})

const executeRetention = async () => {
  if (!retentionLoaded.value || retentionDirty.value || retentionBusy.value) return
  if (!retentionPreviewData.value) {
    ElMessage.warning('请先预览到期数据量')
    return
  }
  if (retentionPreviewTotal.value === 0) return
  if (!retentionReason.value.trim()) {
    ElMessage.warning('请填写清理原因')
    return
  }
  retentionRunning.value = true
  try {
    await ElMessageBox.confirm(
      `即将按已保存策略删除 ${retentionPreviewTotal.value} 条到期日志（预估，实际数量以执行时为准），此操作不可恢复，是否继续？`,
      '执行留存清理',
      { confirmButtonText: '确认清理到期日志', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    retentionRunning.value = false
    return
  }

  if (retentionDirty.value || !retentionPreviewData.value) {
    retentionRunning.value = false
    ElMessage.warning('策略已变化，请重新预览')
    return
  }
  try {
    const operation = formContext.beginFormOperation('admin:log-retention', ['retentionReason'])
    const response = await runRetentionApi(retentionReason.value.trim()) as unknown as { code: number; data: { deletedCount: number }; message?: string }
    if (response.code === 200) {
      operation.complete({ status: 'submitted', label: `留存清理完成，实际删除 ${response.data?.deletedCount ?? 0} 条日志`, url: '/admin/logs' })
      ElMessage.success(`清理完成，共删除 ${response.data?.deletedCount ?? 0} 条到期日志`)
      retentionPreviewData.value = null
      retentionReason.value = ''
      loadSummary()
      queryPage()
    } else {
      ElMessage.error(response.message || (response as { msg?: string }).msg || '执行清理失败')
    }
  } catch (error) {
    logger.error('执行留存清理失败:', error)
    ElMessage.error('执行留存清理失败')
  } finally {
    retentionRunning.value = false
  }
}

// ===== P2 完整性校验 =====
const integrityVerifying = ref(false)
const retentionBusy = computed(() => retentionFetching.value || retentionLoading.value || retentionRunning.value || retentionPreviewLoading.value || integrityVerifying.value)
const integrityReport = ref<IntegrityVerifyReport | null>(null)
const integrityStatus = computed(() => integrityReport.value?.status || (integrityReport.value?.passed ? 'PASSED' : 'FAILED'))
const integrityTitle = computed(() => ({
  PASSED: '本次完整性校验通过',
  FAILED: `发现 ${integrityReport.value?.totalIssues ?? 0} 项内容或签名异常，请核查`,
  INCOMPLETE: '记录校验未发现内容异常，但历史链连续性无法完整验证',
  NO_DATA: '暂无可校验的审计记录'
}[integrityStatus.value]))
const integrityTables = computed(() => ['operate_log', 'login_log', 'security_event_log'].map((key, index) => ({
  key, label: ['操作日志', '登录日志', '安全事件'][index],
  data: integrityReport.value?.[key] as { verifiedEntries?: number; unsignedEntries?: number; issues?: string[]; continuityWarnings?: string[] } | undefined
})))
const integrityFindings = computed(() => integrityTables.value.flatMap(table => [
  ...(table.data?.issues || []).map(message => ({ table: table.label, kind: '异常', message })),
  ...(table.data?.continuityWarnings || []).map(message => ({ table: table.label, kind: '待核查', message }))
]))

const runIntegrityVerify = async (): Promise<void> => {
  if (retentionBusy.value) return
  integrityVerifying.value = true
  integrityReport.value = null
  try {
    const response = await verifyIntegrityApi() as unknown as { code: number; data: IntegrityVerifyReport; message?: string }
    if (response.code === 200) {
      integrityReport.value = response.data
      if (response.data.status === 'INCOMPLETE' || response.data.status === 'NO_DATA') {
        ElMessage.warning(integrityTitle.value)
      } else if (response.data.passed) {
        ElMessage.success('完整性校验通过：本次检查未发现不一致')
      } else {
        ElMessage.error(`完整性校验发现 ${response.data.totalIssues} 个问题，请核查校验报告`)
      }
    } else {
      ElMessage.error(response.message || (response as { msg?: string }).msg || '完整性校验失败')
    }
  } catch (error) {
    logger.error('完整性校验失败:', error)
    ElMessage.error('完整性校验失败，请重试')
  } finally {
    integrityVerifying.value = false
  }
}

// ===== 展示映射 =====
const actionNameMap: Record<string, string> = {
  SEND_CODE: '发送验证码',
  CREATE: '新增', UPDATE: '修改', DELETE: '删除', READ: '查询', IMPORT: '导入', EXPORT: '导出',
  UPLOAD: '上传', AUDIT: '审核', CLEAN: '清理', LOGIN: '登录', LOGOUT: '登出', SWITCH_ROLE: '切换角色',
  BACKUP: '备份', RESTORE: '恢复', RESET_PASSWORD: '重置密码', ASSIGN: '分配', DOWNLOAD: '下载', VERIFY: '验证',
  ADD: '新增', INSERT: '新增', MODIFY: '修改', EDIT: '修改', REMOVE: '删除', QUERY: '查询',
  SELECT: '查询', VIEW: '查看', CREATE_: '创建'
}

const getActionName = (action?: string) => action ? (actionNameMap[action] || action) : ''
const getEventTypeName = (type?: string) => type === 'REQUEST_FAILURE' ? '请求处理失败'
  : type === 'AUTH_SERVICE_FAILURE' ? '登录服务异常' : getBaseEventTypeName(type)

const moduleNameMap: Record<string, string> = {
  AUTH: '认证管理', USER_MANAGEMENT: '用户管理', STUDENT_MANAGEMENT: '学生管理', TEACHER_MANAGEMENT: '教师管理',
  CLASS_MANAGEMENT: '班级管理', MAJOR_MANAGEMENT: '专业管理', DEPARTMENT_MANAGEMENT: '院系管理',
  COMPANY_MANAGEMENT: '企业管理', POSITION_MANAGEMENT: '岗位管理', ANNOUNCEMENT_MANAGEMENT: '公告管理',
  INTERNSHIP_MANAGEMENT: '实习管理', STUDENT_INTERNSHIP: '学生实习', INTERNSHIP_REFLECTION: '实习心得',
  INTERNSHIP_REPORT: '实习报告', LOG_MANAGEMENT: '日志管理', SYSTEM_SETTINGS: '系统设置',
  PERMISSION: '权限管理', AI_MODEL: 'AI模型管理', FILE_ACCESS: '文件访问'
}
const getModuleName = (module?: string) => {
  if (!module) return ''
  if (moduleNameMap[module]) return moduleNameMap[module]
  return module.split('_').map(w => w.toLowerCase()).map(w =>
    ({ user: '用户', student: '学生', teacher: '教师', management: '管理', company: '企业', internship: '实习', log: '日志', system: '系统', permission: '权限', announcement: '公告', position: '岗位', data: '数据', export: '导出', backup: '备份', settings: '设置', config: '配置', resource: '资源', document: '文档', application: '申请', evaluation: '评分', reflection: '心得', report: '报告', visit: '走访', plan: '计划', keyword: '关键词', library: '库', scoring: '评分', rule: '规则', chat: '聊天', model: '模型', feedback: '反馈', problem: '问题', archive: '档案', template: '模板', withdrawal: '撤回', record: '记录', chat_management: '聊天' } as Record<string, string>)[w] || w
  ).join('')
}

const formatTarget = (row: OperateLogItem) => {
  if (!row.targetType && !row.targetId) return ''
  const type = row.targetType || ''
  return row.targetId ? `${type ? type + ' ' : ''}#${row.targetId}` : type
}

watch(() => route.query.tab, (tab) => {
  const target = visibleTabs.value.find(item => item.name === tab)
  if (target && target.name !== activeTab.value) onTabChange(target.name)
})

// ===== 初始化 =====
onMounted(() => {
  if (visibleTabs.value.length === 0) {
    ElMessage.warning('您没有日志查看权限')
    return
  }
  // 首页可直接打开安全事件标签；仍以当前账号权限为准。
  activeTab.value = visibleTabs.value.find(tab => tab.name === route.query.tab)?.name
    ?? visibleTabs.value[0].name
  loadSummary()
  loadOptions()
  handleSearch()
})
</script>

<template>
  <div class="log-management-container">
    <div class="page-header">
      <div class="header-content">
        <div class="page-eyebrow"><el-icon><Document /></el-icon> 系统管理 / 审计与追溯</div>
        <h1 class="page-title">日志中心</h1>
        <p class="page-description">记录关键行为，定位业务异常，让每一次重要操作有迹可循。</p>
      </div>
      <div class="header-actions">
        <el-button v-if="hasPermission('log:retention:manage')" :icon="DataAnalysis" @click="openRetention">保留策略</el-button>
        <el-button v-if="hasPermission('log:export') && visibleTabs.length" type="primary" :icon="Download"
          :loading="exportLoading" :disabled="loading || !!listError || pagination.total === 0" @click="handleExport">导出查询结果</el-button>
      </div>
    </div>
    <el-empty v-if="!visibleTabs.length" description="暂无日志查看权限，请联系管理员分配权限" />
    <template v-else>
    <div class="section-heading">
      <div><h2>今日概览</h2><span>系统当天累计 · 不随下方筛选变化</span></div>
      <el-button link :icon="Refresh" :loading="summaryLoading" @click="loadSummary">刷新概览</el-button>
    </div>
    <el-alert v-if="summaryError" title="概览暂时无法加载，请点击刷新概览重试" type="warning" :closable="false" class="summary-alert" />
    <div class="stats-grid" :aria-busy="summaryLoading">
      <button v-for="card in summaryCards" :key="card.key" type="button" class="stat-card" :class="'tone-' + card.tone"
        :disabled="summaryLoading || summary?.[card.key as keyof LogSummary] == null"
        @click="quickFilter(card.tab, card.result, card.risk)">
        <div class="stat-top"><span>{{ card.label }}</span><span class="stat-icon"><el-icon><component :is="card.icon" /></el-icon></span></div>
        <div class="stat-value">{{ summaryLoading ? '…' : summaryCount(card.key) }}</div>
        <div class="stat-hint">{{ card.hint }} <span aria-hidden="true">↗</span></div>
      </button>
    </div>

    <el-card class="search-card" shadow="never">
      <el-tabs :model-value="activeTab" class="log-tabs" @tab-change="onTabChange">
        <el-tab-pane v-for="tab in visibleTabs" :key="tab.name" :label="tab.label" :name="tab.name" />
      </el-tabs>
      <p class="category-description">{{ activeDescription }}</p>
      <div class="quick-filters">
        <span>快捷查看</span>
        <el-button size="small" @click="quickFilter(activeTab, '', '', 0)">今日全部</el-button>
        <el-button size="small" @click="quickFilter(activeTab, 'FAILURE', '', 7)">近 7 天失败记录</el-button>
        <el-button size="small" @click="quickFilter(activeTab, '', 'HIGH,CRITICAL', 7)">近 7 天高风险</el-button>
      </div>
      <el-alert v-if="optionsError" type="warning" :closable="false" class="summary-alert">
        <template #title>筛选选项加载失败 <el-button link type="primary" @click="loadOptions">重新加载</el-button></template>
      </el-alert>
      <el-form label-position="top" class="search-form" @submit.prevent="handleSearch">
        <div class="filter-grid">
          <el-form-item label="时间范围" class="time-filter">
            <el-date-picker v-model="searchForm.timeRange" type="datetimerange" range-separator="至"
              start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss"
              :shortcuts="rangeShortcuts" :clearable="false" @change="liveRangeDays = null"
              :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]" />
          </el-form-item>
          <el-form-item label="操作人 / 账号">
            <el-input v-model="searchForm.operatorName" placeholder="搜索姓名或账号" maxlength="100" clearable />
          </el-form-item>
          <el-form-item label="执行结果">
            <el-select v-model="searchForm.result" placeholder="全部结果" clearable>
              <el-option label="成功" value="SUCCESS" /><el-option label="失败" value="FAILURE" />
              <el-option v-if="activeTab !== 'login-events'" label="尝试" value="ATTEMPTED" />
            </el-select>
          </el-form-item>
          <el-form-item label="风险等级">
            <el-select v-model="searchForm.riskLevel" placeholder="全部风险" clearable>
              <el-option label="高风险及严重" value="HIGH,CRITICAL" />
              <el-option v-for="r in options.riskLevels" :key="r" :label="getRiskName(r)" :value="r" />
            </el-select>
          </el-form-item>
        </div>
        <div v-show="advancedVisible" class="filter-grid advanced-filters" id="log-advanced-filters">
          <el-form-item label="操作人角色">
            <el-select v-model="searchForm.role" placeholder="全部角色" clearable>
              <el-option v-for="role in options.roles" :key="role" :label="getRoleName(role)" :value="role" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="activeTab === 'operations'" label="业务模块">
            <el-select v-model="searchForm.module" placeholder="全部模块" clearable filterable>
              <el-option v-for="m in options.modules" :key="m" :label="getModuleName(m)" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="activeTab !== 'login-events'" :label="activeTab === 'operations' ? '操作动作' : '事件类型'">
            <el-select v-model="searchForm.action" placeholder="全部类型" clearable filterable>
              <el-option v-for="a in (activeTab === 'operations' ? options.actions : options.eventTypes)" :key="a"
                :label="activeTab === 'operations' ? getActionName(a) : getEventTypeName(a)" :value="a" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源 IP">
            <el-input v-model="searchForm.ip" placeholder="输入 IP 地址" maxlength="64" clearable />
          </el-form-item>
          <el-form-item label="请求 ID">
            <el-input v-model="searchForm.requestId" placeholder="关联同一次请求" maxlength="128" clearable />
          </el-form-item>
          <el-form-item v-if="activeTab !== 'login-events'" label="目标 ID">
            <el-input v-model="searchForm.targetId" placeholder="业务对象 ID" maxlength="128" clearable />
          </el-form-item>
        </div>
        <div class="filter-footer">
          <div class="search-actions">
            <el-button type="primary" :icon="Search" native-type="submit" :loading="loading">查询</el-button>
            <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
            <el-button link type="primary" :aria-expanded="advancedVisible" aria-controls="log-advanced-filters"
              @click="advancedVisible = !advancedVisible">{{ advancedVisible ? '收起筛选' : '高级筛选' }}<el-icon><ArrowUp v-if="advancedVisible" /><ArrowDown v-else /></el-icon></el-button>
          </div>
          <span class="filter-hint">{{ filtersDirty ? '筛选条件已修改，点击查询后生效' : '默认最近 7 天，单次查询跨度不超过 365 天' }}</span>
        </div>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <div class="table-title"><h2>日志记录</h2><span>{{ loading ? '加载中…' : listError ? '加载失败' : `共 ${pagination.total.toLocaleString()} 条` }}</span></div>
        <div class="table-actions">
          <el-select v-model="sortOrder" aria-label="日志时间排序" class="sort-select" @change="() => { pagination.currentPage = 1; queryPage() }">
            <el-option label="最新优先" value="desc" /><el-option label="最早优先" value="asc" />
          </el-select>
          <el-button :icon="Refresh" :loading="loading" @click="refreshPage">刷新</el-button>
        </div>
      </div>
      <div class="query-context">
        <span>{{ appliedParams.startTime }} 至 {{ appliedParams.endTime }}</span>
        <el-tag v-for="label in appliedFilterLabels" :key="String(label)" type="info" effect="plain" size="small">{{ label }}</el-tag>
      </div>
      <div class="table-notice"><el-icon><Lock /></el-icon> 列表 IP 已脱敏，详情按权限展示；导出包含当前已应用筛选的全部结果，最多 100,000 条。</div>
      <div v-if="listError" class="list-error" role="alert">
        <el-icon><Warning /></el-icon><h3>日志加载失败</h3><p>{{ listError }}</p><el-button type="primary" plain @click="queryPage">重试加载</el-button>
      </div>
      <template v-else>
      <!-- 操作日志 -->
      <el-table v-if="activeTab === 'operations'" :data="operateTableData" stripe v-loading="loading" class="data-table" row-key="id" :empty-text="loading ? '正在加载日志…' : '当前条件下暂无记录，请调整时间范围或筛选条件'">
        <el-table-column prop="operateTime" label="时间" align="center" width="165">
          <template #default="scope">{{ scope.row.operateTime || '未知' }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" align="center" width="110">
          <template #default="scope">{{ scope.row.operatorName || '未知' }}</template>
        </el-table-column>
        <el-table-column prop="operatorRole" label="角色" align="center" width="90">
          <template #default="scope">
            <el-tag :type="getRoleTagType(scope.row.operatorRole)" size="small">{{ getRoleName(scope.row.operatorRole) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" align="center" width="130" show-overflow-tooltip>
          <template #default="scope">{{ getModuleName(scope.row.module) }}</template>
        </el-table-column>
        <el-table-column prop="operationType" label="动作" align="center" width="90">
          <template #default="scope">{{ getActionName(scope.row.operationType) }}</template>
        </el-table-column>
        <el-table-column label="目标" align="center" width="120" show-overflow-tooltip>
          <template #default="scope">{{ formatTarget(scope.row) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="operationResult" label="结果" align="center" width="80">
          <template #default="scope">
            <el-tag :type="getResultTagType(scope.row.operationResult)" size="small">
              {{ getResultName(scope.row.operationResult) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险" align="center" width="90">
          <template #default="scope">
            <el-tag v-if="scope.row.riskLevel" :type="getRiskTagType(scope.row.riskLevel)" size="small">{{ getRiskName(scope.row.riskLevel) }}</el-tag>
            <span v-else class="unknown-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时" align="center" width="80">
          <template #default="scope">{{ scope.row.durationMs != null ? scope.row.durationMs + 'ms' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP地址" align="center" width="130">
          <template #default="scope">{{ scope.row.ipAddress || '-' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" align="center" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="90" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openDetail(scope.row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 登录日志 -->
      <el-table v-else-if="activeTab === 'login-events'" :data="loginTableData" stripe v-loading="loading" class="data-table" row-key="id" :empty-text="loading ? '正在加载日志…' : '当前条件下暂无记录，请调整时间范围或筛选条件'">
        <el-table-column prop="loginTime" label="时间" align="center" width="165">
          <template #default="scope">{{ scope.row.loginTime || '未知' }}</template>
        </el-table-column>
        <el-table-column prop="userName" label="用户名" align="center" width="130">
          <template #default="scope">{{ scope.row.userName || '未知' }}</template>
        </el-table-column>
        <el-table-column prop="userType" label="角色" align="center" width="100">
          <template #default="scope">
            <el-tag :type="getRoleTagType(scope.row.userType)" size="small">{{ getRoleName(scope.row.userType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="loginStatus" label="状态" align="center" width="80">
          <template #default="scope">
            <el-tag :type="getResultTagType(scope.row.loginStatus)" size="small">
              {{ getResultName(scope.row.loginStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险" align="center" width="90">
          <template #default="scope">
            <el-tag v-if="scope.row.riskLevel" :type="getRiskTagType(scope.row.riskLevel)" size="small">{{ getRiskName(scope.row.riskLevel) }}</el-tag>
            <span v-else class="unknown-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="errorCode" label="错误码" align="center" width="110">
          <template #default="scope">{{ scope.row.errorCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP地址" align="center" width="130">
          <template #default="scope">{{ scope.row.ipAddress || '-' }}</template>
        </el-table-column>
        <el-table-column prop="deviceType" label="设备类别" align="center" width="100">
          <template #default="scope">{{ scope.row.deviceType || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="90" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openDetail(scope.row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 安全事件 -->
      <el-table v-else :data="securityTableData" stripe v-loading="loading" class="data-table" row-key="id" :empty-text="loading ? '正在加载日志…' : '当前条件下暂无记录，请调整时间范围或筛选条件'">
        <el-table-column prop="eventTime" label="时间" align="center" width="165">
          <template #default="scope">{{ scope.row.eventTime || '未知' }}</template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" align="center" width="120">
          <template #default="scope">{{ scope.row.username || '未知' }}</template>
        </el-table-column>
        <el-table-column prop="userRole" label="角色" align="center" width="90">
          <template #default="scope">
            <el-tag :type="getRoleTagType(scope.row.userRole)" size="small">{{ getRoleName(scope.row.userRole) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="eventType" label="事件类型" align="center" width="150" show-overflow-tooltip>
          <template #default="scope">{{ getEventTypeName(scope.row.eventType) }}</template>
        </el-table-column>
        <el-table-column prop="result" label="结果" align="center" width="80">
          <template #default="scope">
            <el-tag :type="getResultTagType(scope.row.result)" size="small">
              {{ getResultName(scope.row.result) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险" align="center" width="90">
          <template #default="scope">
            <el-tag :type="getRiskTagType(scope.row.riskLevel)" size="small">{{ getRiskName(scope.row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP地址" align="center" width="130">
          <template #default="scope">{{ scope.row.ipAddress || '-' }}</template>
        </el-table-column>
        <el-table-column prop="requestPath" label="请求路径" align="center" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.requestPath || '-' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" align="center" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="90" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openDetail(scope.row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      </template>
      <div class="pagination-container">
        <span class="update-time">{{ lastUpdated && !listError ? `更新于 ${lastUpdated}` : '' }}</span>
        <el-pagination v-model:current-page="pagination.currentPage" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" :total="pagination.total" :disabled="loading || !!listError"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </el-card>

    </template>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="日志详情" size="min(640px, 100vw)">
      <div v-loading="detailLoading" class="detail-content">
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" />
        <template v-if="detailData">
          <div class="detail-intro"><el-tag effect="plain">记录 #{{ detailData.id }}</el-tag><span>服务端审计记录</span></div>
          <el-alert title="信息按当前账号权限展示；完整性字段仅供核查，请结合完整性校验结果判断。" type="info" :closable="false" />
          <div v-if="detailData.requestId" class="trace-panel">
            <div class="trace-title"><strong>请求关联</strong><el-button link type="primary" :icon="CopyDocument" @click="copyRequestId">复制请求 ID</el-button></div>
            <code>{{ detailData.requestId }}</code>
            <p>按请求 ID 检索事件前后 24 小时的关联记录</p>
            <div class="trace-actions"><el-button v-for="tab in visibleTabs" :key="tab.name" size="small" @click="traceRequest(tab.name)">{{ tab.label }}</el-button></div>
          </div>
          <div v-for="section in detailSections" :key="section.title" class="detail-section">
            <template v-if="section.fields.length > 0">
              <h4 class="section-title">{{ section.title }}</h4>
              <div v-for="[label, value] in section.fields" :key="label" class="detail-row">
                <span class="detail-label">{{ label }}</span>
                <span class="detail-value" :title="String(value ?? '')">{{ formatDetailValue(label, value) }}</span>
              </div>
            </template>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 保留策略对话框 -->
    <el-dialog v-model="retentionVisible" title="日志保留策略" width="min(680px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!retentionBusy" :show-close="!retentionBusy">
      <el-alert title="按日志类型设置保存期限。保存策略不会立即删除日志；手动清理需先预览并填写原因。" type="info" :closable="false" class="preview-alert" />
      <div v-loading="retentionFetching">
      <el-alert v-if="!retentionFetching && !retentionLoaded" title="策略加载失败，无法保存或执行清理" type="error" :closable="false" class="preview-alert"><el-button @click="openRetention">重新加载策略</el-button></el-alert>
      <el-form label-width="150px" :disabled="!retentionLoaded || retentionLoading || retentionRunning || retentionPreviewLoading">
        <el-form-item label="高风险事件保留">
          <el-input-number :precision="0" v-model="retentionForm.highRiskDays" :min="30" :max="3650" :disabled="retentionLoading" />
          <span class="retention-unit">天（权限、备份、日志管理等 HIGH/CRITICAL 记录）</span>
        </el-form-item>
        <el-form-item label="普通操作保留">
          <el-input-number :precision="0" v-model="retentionForm.normalDays" :min="30" :max="3650" :disabled="retentionLoading" />
          <span class="retention-unit">天（普通业务状态变更）</span>
        </el-form-item>
        <el-form-item label="登录日志保留">
          <el-input-number :precision="0" v-model="retentionForm.loginDays" :min="30" :max="3650" :disabled="retentionLoading" />
          <span class="retention-unit">天（登录成功/失败记录）</span>
        </el-form-item>
        <el-form-item label="运行日志保留">
          <span>{{ retentionForm.logbackDays }} 天（服务器运行日志，由部署配置控制）</span>
        </el-form-item>
      </el-form>

      <el-alert v-if="retentionLoaded && retentionInvalid" title="保留天数须为 30～3650 的整数，且高风险期限不能短于普通日志" type="warning" :closable="false" class="preview-alert" />
      <div class="retention-actions"><el-button type="primary" :loading="retentionLoading" :disabled="!retentionLoaded || !retentionDirty || retentionInvalid || retentionRunning || retentionPreviewLoading || integrityVerifying" @click="saveRetentionPolicies">保存策略</el-button><span class="filter-hint">{{ retentionDirty && retentionLoaded ? '有未保存的修改，请先保存再预览' : '清理始终按已保存策略执行' }}</span></div>
      </div>
      <el-divider content-position="left">完整性校验</el-divider>

      <div class="retention-actions">
        <el-button :loading="integrityVerifying" :disabled="retentionFetching || retentionLoading || retentionRunning || retentionPreviewLoading" @click="runIntegrityVerify">校验哈希链完整性</el-button>
      </div>
      <template v-if="integrityReport">
        <el-alert :type="integrityStatus === 'PASSED' ? 'success' : integrityStatus === 'FAILED' ? 'error' : 'warning'"
          :title="integrityTitle" :closable="false" class="preview-alert"
          :description="integrityStatus === 'INCOMPLETE' ? '存在历史记录缺失、未签名记录或不可核对的锚点。可能与留存清理有关，需结合归档与清理审计核查；不能据此认定整条链完整。' : integrityStatus === 'NO_DATA' ? '没有受哈希保护的记录与签名根，无法得出完整性结论。' : undefined" />
        <div class="integrity-counts">
          <div v-for="table in integrityTables" :key="table.key"><strong>{{ table.label }}</strong><span>已校验 {{ table.data?.verifiedEntries ?? 0 }} 条 · 签名根 {{ integrityReport.signatureRoots?.[table.key] ?? 0 }} 个</span><span v-if="table.data?.unsignedEntries">未签名历史记录 {{ table.data.unsignedEntries }} 条</span></div>
        </div>
        <ul v-if="integrityFindings.length" class="integrity-findings">
          <li v-for="(finding, index) in integrityFindings.slice(0, 20)" :key="index"><strong>{{ finding.table }} / {{ finding.kind }}</strong> {{ finding.message }}</li>
        </ul>
        <p v-if="integrityFindings.length > 20" class="filter-hint">共 {{ integrityFindings.length }} 项，页面展示前 20 项。请结合服务端校验记录继续核查。</p>
      </template>

      <el-divider content-position="left">到期清理</el-divider>

      <div class="retention-actions">
        <el-button :loading="retentionPreviewLoading" :disabled="!retentionLoaded || retentionDirty || retentionLoading || retentionRunning || integrityVerifying" @click="previewRetention">预览到期数据</el-button>
        <el-button type="danger" :disabled="!retentionLoaded || retentionDirty || !retentionPreviewData || retentionPreviewTotal === 0 || !retentionReason.trim() || retentionBusy"
          :loading="retentionRunning" @click="executeRetention">
          执行清理
        </el-button>
      </div>

      <el-alert v-if="retentionPreviewData" type="info" :closable="false" class="preview-alert">
        <template #title>
          到期数据共 {{ retentionPreviewTotal }} 条：操作日志（普通 {{ retentionPreviewData.operateLogNormal || 0 }} 条 / 高风险
          {{ retentionPreviewData.operateLogHighRisk || 0 }} 条），登录日志 {{ retentionPreviewData.loginLog || 0 }} 条，
          安全事件（普通 {{ retentionPreviewData.securityEventNormal || 0 }} 条 / 高风险 {{ retentionPreviewData.securityEventHighRisk || 0 }} 条）
        </template>
      </el-alert>

      <el-input v-model="retentionReason" type="textarea" :rows="2" maxlength="500" show-word-limit :disabled="retentionRunning" aria-label="清理原因" placeholder="请填写清理原因（必填，将记入安全事件）" class="reason-input" />
      <p class="retention-warning">普通到期记录删除后无法在页面恢复；高风险操作与安全事件由服务端先归档再清理。预览为估算，执行数量以服务端结果为准。</p>
    </el-dialog>
  </div>
</template>

<style scoped>
.log-management-container { padding: 24px; background: #f5f7fb; min-height: 100%; color: #263449; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 30px; }
.page-eyebrow { display: flex; gap: 7px; align-items: center; color: #64748b; font-size: 12px; margin-bottom: 10px; }
.page-title { margin: 0 0 10px; font-size: 28px; letter-spacing: -0.5px; font-weight: 650; color: #182a42; }
.page-description { margin: 0; font-size: 14px; line-height: 1.7; color: #64748b; }
.header-actions, .table-actions, .search-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.header-actions { flex-shrink: 0; }
.header-actions .el-button + .el-button, .search-actions .el-button + .el-button, .table-actions .el-button + .el-button { margin-left: 0; }
.section-heading, .section-heading > div, .table-title { display: flex; align-items: center; gap: 12px; }
.section-heading { justify-content: space-between; margin-bottom: 14px; }
h2 { font-size: 16px; font-weight: 600; margin: 0; color: #25354b; }
.section-heading span, .table-title span { font-size: 12px; color: #64748b; }
.summary-alert { margin-bottom: 12px; }
.stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }
.stat-card { font: inherit; text-align: left; background: #fff; border: 1px solid #e3e8ef; border-radius: 12px; padding: 18px 20px; cursor: pointer; transition: border-color .15s, box-shadow .15s; }
.stat-card:hover:not(:disabled) { border-color: #98b4d9; box-shadow: 0 4px 16px #234b7a0a; }
.stat-card:focus-visible { outline: 3px solid #409eff; outline-offset: 2px; }
.stat-card:disabled { cursor: default; }
.stat-top { display: flex; align-items: center; justify-content: space-between; color: #526176; font-size: 13px; }
.stat-icon { width: 32px; height: 32px; display: grid; place-items: center; background: #eaf2ff; color: #397bdd; border-radius: 8px; font-size: 17px; }
.stat-value { font-size: 30px; line-height: 1.4; font-weight: 650; color: #20314b; font-variant-numeric: tabular-nums; margin: 6px 0; }
.stat-hint { display: flex; justify-content: space-between; font-size: 12px; color: #718096; }
.tone-orange .stat-icon { background: #fff3e7; color: #b87420; }
.tone-red .stat-icon { background: #fff0f0; color: #ca5151; }
.search-card, .table-card { border: 1px solid #e3e8ef; border-radius: 12px; margin-bottom: 20px; background: #fff; }
.search-card :deep(.el-card__body) { padding: 0 22px 18px; }
.log-tabs :deep(.el-tabs__item) { height: 54px; font-size: 14px; }
.log-tabs :deep(.el-tabs__header) { margin-bottom: 12px; }
.log-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; background: #edf0f5; }
.category-description { color: #64748b; font-size: 13px; margin: 0 0 18px; line-height: 1.7; }
.quick-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 20px; }
.quick-filters > span { color: #64748b; font-size: 12px; margin-right: 4px; }
.quick-filters .el-button + .el-button { margin: 0; }
.filter-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 0 16px; }
.filter-grid .time-filter { grid-column: span 3; }
.filter-grid .el-form-item { min-width: 0; margin-bottom: 16px; }
.filter-grid :deep(.el-form-item__label) { font-size: 12px; color: #526176; margin-bottom: 7px; }
.filter-grid :deep(.el-date-editor), .filter-grid :deep(.el-select) { width: 100%; min-width: 0; }
.advanced-filters { border-top: 1px dashed #e3e8ef; padding-top: 16px; }
.filter-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 2px; flex-wrap: wrap; }
.filter-hint { color: #64748b; font-size: 12px; line-height: 1.6; }
.table-card :deep(.el-card__body) { padding: 0; }
.table-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 18px 22px 12px; flex-wrap: wrap; }
.sort-select { width: 115px; }
.query-context { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 0 22px 12px; font-size: 12px; color: #64748b; overflow-wrap: anywhere; }
.query-context :deep(.el-tag) { max-width: 100%; height: auto; white-space: normal; padding: 3px 7px; }
.table-notice { display: flex; align-items: center; gap: 7px; padding: 10px 22px; font-size: 12px; color: #64748b; background: #f8fafc; border-top: 1px solid #edf0f5; line-height: 1.7; }
.table-notice .el-icon { flex-shrink: 0; }
.data-table { width: 100%; font-size: 13px; }
.data-table :deep(.el-table__header) th { background: #f5f7fb; color: #526176; font-weight: 600; height: 44px; }
.data-table :deep(.el-table__row) td { padding: 13px 0; }
.data-table :deep(.el-table__empty-block) { min-height: 180px; }
.data-table :deep(.el-table__empty-text) { width: 90%; line-height: 1.8; }
.unknown-text { color: #64748b; }
.pagination-container { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 14px; padding: 18px 22px; border-top: 1px solid #edf0f5; }
.update-time { font-size: 12px; color: #64748b; }
.list-error { text-align: center; padding: 35px 20px; color: #64748b; font-size: 13px; }
.list-error > .el-icon { font-size: 28px; color: #c3832d; }
.list-error h3 { font-size: 16px; color: #334155; }
.detail-content { padding: 0 4px; min-height: 160px; }
.detail-intro { display: flex; align-items: center; gap: 10px; color: #64748b; font-size: 12px; margin-bottom: 16px; }
.detail-section { margin: 22px 0; }
.section-title { font-size: 14px; font-weight: 600; color: #334155; margin: 0 0 10px; padding-bottom: 8px; border-bottom: 1px solid #ebeef5; }
.detail-row { display: flex; gap: 12px; padding: 7px 0; font-size: 13px; line-height: 1.65; }
.detail-label { flex: 0 0 105px; color: #64748b; }
.detail-value { flex: 1; min-width: 0; color: #263449; overflow-wrap: anywhere; white-space: pre-wrap; }
.trace-panel { margin-top: 20px; padding: 14px; border: 1px solid #dbe7f5; background: #f6f9fe; border-radius: 8px; font-size: 13px; }
.trace-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; flex-wrap: wrap; }
.trace-panel code { overflow-wrap: anywhere; color: #3b618c; }
.trace-panel p { font-size: 12px; color: #64748b; }
.trace-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.trace-actions .el-button + .el-button { margin: 0; }
.retention-unit { margin-left: 8px; font-size: 12px; color: #64748b; }
.retention-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 12px; }
.retention-actions .el-button + .el-button { margin: 0; }
.preview-alert { margin-bottom: 16px; }
.reason-input { margin-top: 4px; }
.integrity-counts { display: grid; gap: 10px; margin-bottom: 16px; font-size: 12px; color: #64748b; }
.integrity-counts > div { display: flex; flex-wrap: wrap; gap: 6px 12px; }
.integrity-counts strong { color: #334155; }
.integrity-findings { padding-left: 18px; max-height: 220px; overflow-y: auto; font-size: 12px; line-height: 1.8; color: #64748b; overflow-wrap: anywhere; }
.retention-warning { font-size: 12px; color: #916326; line-height: 1.7; }
@media (max-width: 1200px) {
  .filter-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .filter-grid .time-filter { grid-column: span 2; }
  .advanced-filters { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .log-management-container { padding: 16px; }
  .page-header { flex-direction: column; align-items: flex-start; gap: 16px; }
  .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
  .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-grid .time-filter { grid-column: span 2; }
  .pagination-container :deep(.el-pagination) { flex-wrap: wrap; row-gap: 10px; }
}
@media (max-width: 520px) {
  .log-management-container { padding: 12px; }
  .page-title { font-size: 24px; }
  .section-heading > div { align-items: flex-start; flex-direction: column; gap: 5px; }
  .stat-card { padding: 14px 12px; }
  .stat-value { font-size: 26px; }
  .stat-hint { font-size: 11px; }
  .search-card :deep(.el-card__body) { padding: 0 12px 16px; }
  .filter-grid { gap: 0 10px; }
  .filter-grid :deep(.el-range-editor) { padding: 3px 5px; }
  .filter-grid :deep(.el-range-input) { font-size: 11px; }
  .quick-filters { gap: 6px; }
  .quick-filters > span { width: 100%; }
  .table-toolbar { padding: 15px 12px 12px; }
  .query-context, .table-notice { padding-left: 12px; padding-right: 12px; }
  .pagination-container { padding: 14px 12px; }
  .pagination-container :deep(.el-pagination__jump) { display: none; }
  .pagination-container :deep(.el-pager li) { min-width: 26px; }
  .detail-label { flex-basis: 85px; }
}
</style>
