<script setup lang="ts">
import logger from '@/utils/logger'
import { useRoute } from 'vue-router'
import { getRoleName, getRiskName, getRiskTagType, getEventTypeName, getResultTagType, getRoleTagType, getResultName } from '@/utils/securityEventPresentation'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useFormContext } from '@/agent/formContext'
import {
  getLogSummaryApi, getOperationsApi, getLoginEventsApi, getSecurityEventsApi,
  getLogDetailApi, getLogOptionsApi, exportLogsApi,
  getRetentionPoliciesApi, updateRetentionPoliciesApi, previewRetentionApi, runRetentionApi,
  verifyIntegrityApi,
  type LogSummary, type OperateLogItem, type LoginLogItem, type SecurityEventItem, type LogOptions
} from '@/api/log'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, DataAnalysis } from '@element-plus/icons-vue'
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
  activeTab.value = tab as TabName
  pagination.currentPage = 1
  queryPage()
}

// ===== 统计 =====
const summary = ref<LogSummary | null>(null)
const loadSummary = async () => {
  try {
    const response = await getLogSummaryApi() as unknown as { code: number; data: LogSummary }
    if (response.code === 200) {
      summary.value = response.data
    }
  } catch (error) {
    logger.error('获取日志统计失败:', error)
  }
}

// ===== 筛选 =====
const defaultTimeRange = (): [string, string] => {
  const end = new Date()
  const start = new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000)
  const fmt = (d: Date) => {
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  }
  return [fmt(start), fmt(end)]
}

const searchForm = reactive({
  timeRange: defaultTimeRange() as [string, string] | null,
  operatorName: '',
  role: '',
  module: '',
  action: '',
  result: '',
  riskLevel: '',
  ip: '',
  requestId: ''
})

const options = ref<LogOptions>({ actions: [], modules: [], eventTypes: [], roles: [], riskLevels: [], results: [] })

const loadOptions = async () => {
  try {
    const response = await getLogOptionsApi() as unknown as { code: number; data: LogOptions }
    if (response.code === 200) {
      options.value = response.data
    }
  } catch (error) {
    logger.error('获取筛选选项失败:', error)
  }
}

// ===== 列表 =====
const loading = ref(false)
const operateTableData = ref<OperateLogItem[]>([])
const loginTableData = ref<LoginLogItem[]>([])
const securityTableData = ref<SecurityEventItem[]>([])
const pagination = reactive({ currentPage: 1, pageSize: 20, total: 0 })

const buildParams = () => {
  return {
    page: pagination.currentPage,
    pageSize: pagination.pageSize,
    startTime: searchForm.timeRange?.[0] || undefined,
    endTime: searchForm.timeRange?.[1] || undefined,
    operatorName: searchForm.operatorName || undefined,
    role: searchForm.role || undefined,
    module: activeTab.value === 'operations' ? (searchForm.module || undefined) : undefined,
    action: activeTab.value !== 'login-events' ? (searchForm.action || undefined) : undefined,
    result: searchForm.result || undefined,
    riskLevel: searchForm.riskLevel || undefined,
    ip: searchForm.ip || undefined,
    requestId: searchForm.requestId || undefined
  }
}

const queryPage = async (): Promise<void> => {
  loading.value = true
  try {
    let response: { code: number; data: { rows: unknown[]; total: number } } | null = null
    if (activeTab.value === 'operations') {
      response = await getOperationsApi(buildParams()) as unknown as typeof response
    } else if (activeTab.value === 'login-events') {
      response = await getLoginEventsApi(buildParams()) as unknown as typeof response
    } else {
      response = await getSecurityEventsApi(buildParams()) as unknown as typeof response
    }

    if (response && response.code === 200 && response.data) {
      pagination.total = response.data.total || 0
      if (activeTab.value === 'operations') {
        operateTableData.value = response.data.rows as OperateLogItem[]
      } else if (activeTab.value === 'login-events') {
        loginTableData.value = response.data.rows as LoginLogItem[]
      } else {
        securityTableData.value = response.data.rows as SecurityEventItem[]
      }
    } else {
      operateTableData.value = []
      loginTableData.value = []
      securityTableData.value = []
      pagination.total = 0
    }
  } catch (error: unknown) {
    logger.error('查询日志列表失败:', error)
    operateTableData.value = []
    loginTableData.value = []
    securityTableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const handleSizeChange = (newSize: number) => {
  pagination.pageSize = newSize
  queryPage()
}

const handleCurrentChange = (newCurrent: number) => {
  pagination.currentPage = newCurrent
  queryPage()
}

const resetSearch = () => {
  searchForm.timeRange = defaultTimeRange()
  searchForm.operatorName = ''
  searchForm.role = ''
  searchForm.module = ''
  searchForm.action = ''
  searchForm.result = ''
  searchForm.riskLevel = ''
  searchForm.ip = ''
  searchForm.requestId = ''
  pagination.currentPage = 1
  queryPage()
}

// ===== 详情抽屉 =====
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<Record<string, unknown> | null>(null)
const detailCategory = ref('')

const openDetail = async (id: number) => {
  const category = tabCategoryMap[activeTab.value]
  detailCategory.value = category
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  try {
    const response = await getLogDetailApi(category, id) as unknown as { code: number; message?: string; msg?: string; data: Record<string, unknown> }
    if (response.code === 200) {
      detailData.value = response.data
    } else {
      ElMessage.error(response.message || '获取详情失败')
    }
  } catch (error) {
    logger.error('获取日志详情失败:', error)
    ElMessage.error('获取日志详情失败')
  } finally {
    detailLoading.value = false
  }
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
        ['结果', data.operationResult === 'SUCCESS' ? '成功' : data.operationResult === 'ATTEMPTED' ? '尝试' : '失败'],
        ['风险等级', getRiskName(data.riskLevel as string)], ['耗时', data.durationMs != null ? `${data.durationMs}ms` : null],
        ['描述', data.description], ['错误码', data.errorCode]
      ] },
      { title: '目标对象', fields: [
        ['目标类型', data.targetType], ['目标ID', data.targetId],
        ['影响数量', data.affectedCount != null ? String(data.affectedCount) : null]
      ] },
      { title: '请求信息', fields: [
        ['请求方法', data.httpMethod], ['请求路径', data.requestPath], ['IP地址', data.ipAddress],
        ['设备类别', data.deviceType], ['requestId', data.requestId],
        ['操作时间', data.operateTime]
      ] },
      { title: '完整性哈希链', fields: data.entryHash ? [
        ['节点哈希', data.entryHash], ['前驱哈希', data.prevHash], ['密钥版本', data.keyVersion != null ? 'v' + data.keyVersion : null]
      ] : [['节点哈希', '（该记录早于哈希链启用，未受链保护）']] },
      { title: '详情数据', fields: data.detailJson ? [['详情JSON', data.detailJson]] : [] }
    ]
  }
  if (detailCategory.value === 'login-events') {
    return [
      { title: '用户信息', fields: [
        ['操作人', data.operatorName], ['用户名', data.userName], ['角色', getRoleName(data.userType as string)]
      ] },
      { title: '登录信息', fields: [
        ['状态', data.loginStatus === 'SUCCESS' ? '成功' : '失败'], ['风险等级', getRiskName(data.riskLevel as string)],
        ['错误码', data.errorCode], ['耗时', data.durationMs != null ? `${data.durationMs}ms` : null],
        ['登录时间', data.loginTime]
      ] },
      { title: '请求信息', fields: [
        ['IP地址', data.ipAddress], ['设备类别', data.deviceType], ['requestId', data.requestId], ['设备信息', data.deviceInfo]
      ] },
      { title: '完整性哈希链', fields: data.entryHash ? [
        ['节点哈希', data.entryHash], ['前驱哈希', data.prevHash], ['密钥版本', data.keyVersion != null ? 'v' + data.keyVersion : null]
      ] : [['节点哈希', '（该记录早于哈希链启用，未受链保护）']] },
      { title: '详情数据', fields: data.detailJson ? [['详情JSON', data.detailJson]] : [] }
    ]
  }
  return [
    { title: '用户信息', fields: [
      ['操作人', data.operatorName], ['用户名', data.username], ['角色', getRoleName(data.userRole as string)],
      ...(data.realOperatorName ? [['真实管理员身份', data.realOperatorName] as [string, unknown]] : [])
    ] },
    { title: '事件信息', fields: [
      ['事件类型', getEventTypeName(data.eventType as string)], ['结果', data.result],
      ['风险等级', getRiskName(data.riskLevel as string)], ['错误码', data.errorCode],
      ['影响数量', data.affectedCount != null ? String(data.affectedCount) : null],
      ['描述', data.description], ['事件时间', data.eventTime]
    ] },
    { title: '目标对象', fields: [
      ['目标类型', data.targetType], ['目标ID', data.targetId]
    ] },
    { title: '请求信息', fields: [
      ['请求方法', data.httpMethod], ['请求路径', data.requestPath], ['IP地址', data.ipAddress],
      ['设备类别', data.deviceType], ['requestId', data.requestId]
    ] },
    { title: '完整性哈希链', fields: data.entryHash ? [
      ['节点哈希', data.entryHash], ['前驱哈希', data.prevHash], ['密钥版本', data.keyVersion != null ? 'v' + data.keyVersion : null]
    ] : [['节点哈希', '（该记录早于哈希链启用，未受链保护）']] },
    { title: '详情数据', fields: data.detailJson ? [['详情JSON', data.detailJson]] : [] }
  ]
})

// ===== 导出 =====
const exportLoading = ref(false)
const handleExport = async (): Promise<void> => {
  exportLoading.value = true
  try {
    const response = await exportLogsApi({ ...buildParams(), category: tabCategoryMap[activeTab.value] }) as unknown as Blob

    // 后端错误时返回 JSON（如超限提示）
    if (response && response.type && response.type.includes('application/json')) {
      const text = await response.text()
      try {
        const err = JSON.parse(text)
        ElMessage.error(err.message || '导出失败')
      } catch {
        ElMessage.error('导出失败')
      }
      return
    }

    const url = URL.createObjectURL(response)
    const link = document.createElement('a')
    link.href = url
    link.download = `日志导出_${tabCategoryMap[activeTab.value]}_${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出任务已完成')
  } catch (error) {
    logger.error('导出失败:', error)
    ElMessage.error('导出失败，请重试')
  } finally {
    exportLoading.value = false
  }
}

// ===== 留存策略 =====
const retentionVisible = ref(false)
const retentionLoading = ref(false)
const retentionForm = ref({ highRiskDays: 365, normalDays: 180, loginDays: 180, logbackDays: 30 })
const retentionPreviewData = ref<Record<string, number> | null>(null)
const retentionReason = ref('')
const retentionRunning = ref(false)

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
      getValidationSummary: () => ({ missingFields: [], validationErrors: [retentionForm.value.highRiskDays, retentionForm.value.normalDays, retentionForm.value.loginDays].some(value => !Number.isInteger(Number(value)) || Number(value) < 30 || Number(value) > 3650) ? ['日志保留天数应为30到3650之间的整数'] : [] })
    })
  } else formContext.unregister('admin:log-retention')
})
onUnmounted(() => formContext.unregister('admin:log-retention'))

const openRetention = async () => {
  retentionVisible.value = true
  retentionPreviewData.value = null
  retentionReason.value = ''
  try {
    const response = await getRetentionPoliciesApi() as unknown as { code: number; data: typeof retentionForm.value }
    if (response.code === 200) {
      retentionForm.value = response.data
    }
  } catch (error) {
    logger.error('获取留存策略失败:', error)
    ElMessage.error('获取留存策略失败')
  }
}

const saveRetentionPolicies = async () => {
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
      ElMessage.success('留存策略已更新')
    } else {
      ElMessage.error(response.message || '更新留存策略失败')
    }
  } catch (error) {
    logger.error('更新留存策略失败:', error)
    ElMessage.error('更新留存策略失败')
  } finally {
    retentionLoading.value = false
  }
}

const previewRetention = async () => {
  try {
    const response = await previewRetentionApi() as unknown as { code: number; message?: string; msg?: string; data: Record<string, number> }
    if (response.code === 200) {
      retentionPreviewData.value = response.data
    } else {
      ElMessage.error(response.message || '预览失败')
    }
  } catch (error) {
    logger.error('留存预览失败:', error)
    ElMessage.error('留存预览失败')
  }
}

const retentionPreviewTotal = computed(() => {
  const data = retentionPreviewData.value
  if (!data) return 0
  return (data.operateLogNormal || 0) + (data.operateLogHighRisk || 0) + (data.loginLog || 0)
    + (data.securityEventNormal || 0) + (data.securityEventHighRisk || 0)
})

const executeRetention = async () => {
  if (!retentionPreviewData.value) {
    ElMessage.warning('请先预览到期数据量')
    return
  }
  if (!retentionReason.value.trim()) {
    ElMessage.warning('请填写清理原因')
    return
  }
  try {
    await ElMessageBox.confirm(
      `即将删除 ${retentionPreviewTotal.value} 条到期日志，此操作不可恢复，是否继续？`,
      '执行留存清理',
      { confirmButtonText: '确认清理', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  retentionRunning.value = true
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
      ElMessage.error(response.message || '执行清理失败')
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
const integrityReport = ref<Record<string, unknown> | null>(null)

const runIntegrityVerify = async (): Promise<void> => {
  integrityVerifying.value = true
  integrityReport.value = null
  try {
    const response = await verifyIntegrityApi() as unknown as { code: number; data: Record<string, unknown>; message?: string }
    if (response.code === 200) {
      integrityReport.value = response.data
      if (response.data.passed) {
        ElMessage.success('完整性校验通过：哈希链与签名根均一致')
      } else {
        ElMessage.error(`完整性校验发现 ${response.data.totalIssues} 个问题，已触发告警`)
      }
    } else {
      ElMessage.error(response.message || '完整性校验失败')
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
  CREATE: '新增', UPDATE: '修改', DELETE: '删除', READ: '查询', IMPORT: '导入', EXPORT: '导出',
  UPLOAD: '上传', AUDIT: '审核', CLEAN: '清理', LOGIN: '登录', LOGOUT: '登出', SWITCH_ROLE: '切换角色',
  BACKUP: '备份', RESTORE: '恢复', RESET_PASSWORD: '重置密码', ASSIGN: '分配', DOWNLOAD: '下载', VERIFY: '验证',
  ADD: '新增', INSERT: '新增', MODIFY: '修改', EDIT: '修改', REMOVE: '删除', QUERY: '查询',
  SELECT: '查询', VIEW: '查看', CREATE_: '创建'
}

const getActionName = (action?: string) => action ? (actionNameMap[action] || action) : ''

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
  queryPage()
})
</script>

<template>
  <div class="log-management-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">日志中心</h1>
        <p class="page-description">操作日志、登录日志与安全事件统一审计，日志由服务端记录、不可伪造</p>
        <p class="auto-clean-tip">日志按时间留存策略自动清理：高风险事件保留365天，登录与普通操作保留180天（可在保留策略中调整）</p>
      </div>
      <div class="header-actions">
        <el-button v-if="hasPermission('log:retention:manage')" type="primary" plain @click="openRetention">
          <el-icon><DataAnalysis /></el-icon>&nbsp;保留策略
        </el-button>
        <el-button v-if="hasPermission('log:export')" type="success" :loading="exportLoading" @click="handleExport">
          <el-icon><Download /></el-icon>&nbsp;导出Excel
        </el-button>
      </div>
    </div>

    <!-- 顶部统计 -->
    <div class="stats-grid" v-if="summary">
      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-label">今日操作</div>
          <div class="stat-value">{{ summary.todayOperations }}</div>
        </div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-label">今日失败操作</div>
          <div class="stat-value stat-warning">{{ summary.todayFailedOperations }}</div>
        </div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-label">今日登录失败</div>
          <div class="stat-value stat-warning">{{ summary.todayFailedLogins }}</div>
        </div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-label">今日高风险事件</div>
          <div class="stat-value stat-danger">{{ summary.todayHighRiskEvents }}</div>
        </div>
      </el-card>
    </div>

    <!-- 筛选区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" class="search-form" @submit.prevent>
        <div class="search-row">
          <el-form-item label="时间范围">
            <el-date-picker
              v-model="searchForm.timeRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              :default-time="[new Date(0, 0, 0, 0, 0, 0), new Date(0, 0, 0, 23, 59, 59)]"
              style="width: 360px"
            />
          </el-form-item>
          <el-form-item label="操作人">
            <el-input v-model="searchForm.operatorName" placeholder="姓名/用户名" clearable style="width: 140px" />
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="searchForm.role" placeholder="全部" clearable style="width: 110px">
              <el-option v-for="role in options.roles" :key="role" :label="getRoleName(role)" :value="role" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="activeTab === 'operations'" label="模块">
            <el-select v-model="searchForm.module" placeholder="全部" clearable filterable style="width: 150px">
              <el-option v-for="m in options.modules" :key="m" :label="getModuleName(m)" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="activeTab !== 'login-events'" :label="activeTab === 'operations' ? '动作' : '事件类型'">
            <el-select v-model="searchForm.action" placeholder="全部" clearable filterable style="width: 130px">
              <el-option v-for="a in (activeTab === 'operations' ? options.actions : options.eventTypes)" :key="a"
                :label="activeTab === 'operations' ? getActionName(a) : getEventTypeName(a)" :value="a" />
            </el-select>
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="searchForm.result" placeholder="全部" clearable style="width: 100px">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILURE" />
              <el-option v-if="activeTab === 'security-events'" label="尝试" value="ATTEMPTED" />
            </el-select>
          </el-form-item>
          <el-form-item label="风险">
            <el-select v-model="searchForm.riskLevel" placeholder="全部" clearable style="width: 100px">
              <el-option v-for="r in options.riskLevels" :key="r" :label="getRiskName(r)" :value="r" />
            </el-select>
          </el-form-item>
          <el-form-item label="IP">
            <el-input v-model="searchForm.ip" placeholder="IP地址" clearable style="width: 130px" />
          </el-form-item>
          <el-form-item label="requestId">
            <el-input v-model="searchForm.requestId" placeholder="请求链路ID" clearable style="width: 140px" />
          </el-form-item>
          <el-form-item>
            <div class="search-actions">
              <el-button type="primary" @click="() => { pagination.currentPage = 1; queryPage() }">
                <el-icon><Search /></el-icon>&nbsp;查询
              </el-button>
              <el-button @click="resetSearch">
                <el-icon><Refresh /></el-icon>&nbsp;重置
              </el-button>
            </div>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <!-- 日志列表 -->
    <el-card class="table-card" shadow="never">
      <el-tabs :model-value="activeTab" class="log-tabs" @tab-change="onTabChange">
        <el-tab-pane v-for="tab in visibleTabs" :key="tab.name" :label="tab.label" :name="tab.name" />
      </el-tabs>

      <!-- 操作日志 -->
      <el-table v-if="activeTab === 'operations'" :data="operateTableData" border v-loading="loading" class="data-table">
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
              {{ scope.row.operationResult === 'SUCCESS' ? '成功' : scope.row.operationResult === 'ATTEMPTED' ? '尝试' : '失败' }}
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
        <el-table-column label="操作" align="center" width="80" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openDetail(scope.row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 登录日志 -->
      <el-table v-else-if="activeTab === 'login-events'" :data="loginTableData" border v-loading="loading" class="data-table">
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
            <el-tag :type="scope.row.loginStatus === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ scope.row.loginStatus === 'SUCCESS' ? '成功' : '失败' }}
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
        <el-table-column label="操作" align="center" width="80" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openDetail(scope.row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 安全事件 -->
      <el-table v-else :data="securityTableData" border v-loading="loading" class="data-table">
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
        <el-table-column prop="eventType" label="事件类型" align="center" width="110">
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
        <el-table-column label="操作" align="center" width="80" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openDetail(scope.row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination v-model:current-page="pagination.currentPage" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" :total="pagination.total"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="日志详情" size="480px">
      <div v-loading="detailLoading" class="detail-content">
        <template v-if="detailData">
          <div v-for="section in detailSections" :key="section.title" class="detail-section">
            <template v-if="section.fields.length > 0">
              <h4 class="section-title">{{ section.title }}</h4>
              <div v-for="[label, value] in section.fields" :key="label" class="detail-row">
                <span class="detail-label">{{ label }}</span>
                <span class="detail-value" :title="String(value ?? '')">{{ value != null && value !== '' ? String(value) : '-' }}</span>
              </div>
            </template>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 保留策略对话框 -->
    <el-dialog v-model="retentionVisible" title="日志保留策略" width="640px">
      <el-form label-width="150px">
        <el-form-item label="高风险事件保留">
          <el-input-number v-model="retentionForm.highRiskDays" :min="30" :max="3650" :disabled="retentionLoading" />
          <span class="retention-unit">天（权限、备份、日志管理等 HIGH/CRITICAL 记录）</span>
        </el-form-item>
        <el-form-item label="普通操作保留">
          <el-input-number v-model="retentionForm.normalDays" :min="30" :max="3650" :disabled="retentionLoading" />
          <span class="retention-unit">天（普通业务状态变更）</span>
        </el-form-item>
        <el-form-item label="登录日志保留">
          <el-input-number v-model="retentionForm.loginDays" :min="30" :max="3650" :disabled="retentionLoading" />
          <span class="retention-unit">天（登录成功/失败记录）</span>
        </el-form-item>
        <el-form-item label="运行日志保留">
          <span>{{ retentionForm.logbackDays }} 天（服务器运行日志，由部署配置控制）</span>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">完整性校验</el-divider>

      <div class="retention-actions">
        <el-button :loading="integrityVerifying" @click="runIntegrityVerify">校验哈希链完整性</el-button>
      </div>
      <el-alert v-if="integrityReport" :type="integrityReport.passed ? 'success' : 'error'" :closable="false" class="preview-alert">
        <template #title>
          {{ integrityReport.passed ? '完整性校验通过' : `发现 ${integrityReport.totalIssues} 个问题（已告警）` }}
          —— 签名根：操作日志 {{ (integrityReport.signatureRoots as Record<string, number>)?.['operate_log'] || 0 }} 个、
          登录日志 {{ (integrityReport.signatureRoots as Record<string, number>)?.['login_log'] || 0 }} 个、
          安全事件 {{ (integrityReport.signatureRoots as Record<string, number>)?.['security_event_log'] || 0 }} 个
        </template>
      </el-alert>

      <el-divider content-position="left">到期清理</el-divider>

      <div class="retention-actions">
        <el-button type="primary" plain :loading="retentionLoading" @click="saveRetentionPolicies">保存策略</el-button>
        <el-button @click="previewRetention">预览到期数据</el-button>
        <el-button type="primary" :disabled="!retentionPreviewData || !retentionReason.trim() || retentionRunning"
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

      <el-input v-model="retentionReason" placeholder="请填写清理原因（必填，将记入安全事件）" class="reason-input" />
    </el-dialog>
  </div>
</template>

<style scoped>
.log-management-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 16px;
  padding: 24px 32px;
  color: white;
  margin-bottom: 20px;
  box-shadow: 0 8px 25px rgba(64, 158, 255, 0.25);
  position: relative;
  overflow: hidden;
}

.header-content h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  color: white;
}

.header-content p {
  font-size: 14px;
  opacity: 0.95;
  font-weight: 500;
  margin: 0;
}

.auto-clean-tip {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 4px;
  font-weight: 400;
}

.header-actions {
  display: flex;
  gap: 10px;
  z-index: 1;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 12px;
  border: none;
}

.stat-card :deep(.el-card__body) {
  padding: 14px 20px;
}

.stat-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
}

.stat-warning {
  color: #e6a23c;
}

.stat-danger {
  color: #f56c6c;
}

.search-card,
.table-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 20px;
  overflow: hidden;
}

.search-card :deep(.el-card__body) {
  padding: 18px 20px 4px;
}

.search-form {
  width: 100%;
}

.search-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  align-items: flex-start;
}

.search-row .el-form-item {
  margin-bottom: 14px;
}

.search-actions {
  display: flex;
  gap: 10px;
}

.table-card :deep(.el-card__body) {
  padding: 0;
}

.log-tabs {
  padding: 0 20px;
}

.data-table {
  border-radius: 8px;
}

.data-table :deep(.el-table__header) th {
  background-color: #f8fbff;
  color: #409EFF;
  font-weight: 600;
}

.unknown-text {
  color: #C0C4CC;
}

.pagination-container {
  display: flex;
  justify-content: center;
  padding: 20px;
  border-top: 1px solid #f0f0f0;
}

.detail-content {
  padding: 0 4px;
}

.detail-section {
  margin-bottom: 18px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #409EFF;
  margin: 0 0 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #ebeef5;
}

.detail-row {
  display: flex;
  gap: 12px;
  padding: 5px 0;
  font-size: 13px;
  line-height: 1.5;
}

.detail-label {
  flex: 0 0 110px;
  color: #909399;
}

.detail-value {
  flex: 1;
  color: #303133;
  word-break: break-all;
  white-space: pre-wrap;
}

.retention-unit {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.retention-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.preview-alert {
  margin-bottom: 12px;
}

.reason-input {
  margin-top: 4px;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
    padding: 18px 20px;
    margin-bottom: 16px;
  }

  .header-content h1 {
    font-size: 22px;
  }

  .header-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }

  .search-card :deep(.el-card__body) {
    padding: 16px 18px 4px;
  }

  .search-row :deep(.el-date-editor) {
    width: 100% !important;
  }

  .search-row :deep(.el-select) {
    width: 100% !important;
  }

  .retention-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .retention-actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
