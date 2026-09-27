// 日志中心 API（P1 统一接口）
import request from '@/utils/request'

export interface LogSummary {
  todayOperations: number
  todayFailedOperations: number
  todayFailedLogins: number
  todayHighRiskEvents: number
}

export interface OperateLogItem {
  id: number
  operateAdminId?: number
  operatorName?: string
  operatorUsername?: string
  operatorRole?: string
  ipAddress?: string
  operationType?: string
  module?: string
  description?: string
  operationResult?: string
  operateTime?: string
  requestId?: string
  riskLevel?: string
  errorCode?: string
  targetType?: string
  targetId?: string
  affectedCount?: number
  realOperatorName?: string
  httpMethod?: string
  requestPath?: string
  deviceType?: string
  durationMs?: number
  detailJson?: string
}

export interface LoginLogItem {
  id: number
  userId?: string
  userType?: string
  userName?: string
  operatorName?: string
  loginTime?: string
  ipAddress?: string
  deviceInfo?: string
  loginStatus?: string
  requestId?: string
  riskLevel?: string
  errorCode?: string
  deviceType?: string
  durationMs?: number
  detailJson?: string
}

export interface SecurityEventItem {
  id: number
  eventTime?: string
  requestId?: string
  eventType?: string
  userId?: string
  username?: string
  userRole?: string
  operatorName?: string
  realOperatorName?: string
  riskLevel?: string
  result?: string
  errorCode?: string
  targetType?: string
  targetId?: string
  affectedCount?: number
  httpMethod?: string
  requestPath?: string
  ipAddress?: string
  deviceType?: string
  durationMs?: number
  description?: string
  detailJson?: string
  entryHash?: string
  prevHash?: string
  keyVersion?: number
}

export interface PageData<T> {
  rows: T[]
  total: number
  pages: number
  current: number
  pageSize: number
}

export interface LogListParams {
  page?: number
  pageSize?: number
  startTime?: string
  endTime?: string
  operatorName?: string
  role?: string
  module?: string
  action?: string
  result?: string
  riskLevel?: string
  ip?: string
  targetId?: string
  requestId?: string
  sortField?: string
  sortOrder?: string
}

export interface LogOptions {
  actions: string[]
  modules: string[]
  eventTypes: string[]
  roles: string[]
  riskLevels: string[]
  results: string[]
}

export interface RetentionPolicies {
  highRiskDays: number
  normalDays: number
  loginDays: number
  logbackDays: number
}

export interface RetentionPreview {
  operateLogNormal: number
  operateLogHighRisk: number
  loginLog: number
  securityEventNormal: number
  securityEventHighRisk: number
}

export interface LogResponse<T = unknown> {
  code: number
  data: T
  message?: string
  msg?: string
}

// 顶部统计
export const getLogSummaryApi = () => {
  return request.get<LogResponse<LogSummary>>('/admin/logs/summary')
}

// 操作日志列表
export const getOperationsApi = (params: LogListParams) => {
  return request.get<LogResponse<PageData<OperateLogItem>>>('/admin/logs/operations', { params })
}

// 登录日志列表
export const getLoginEventsApi = (params: LogListParams) => {
  return request.get<LogResponse<PageData<LoginLogItem>>>('/admin/logs/login-events', { params })
}

// 安全事件列表
export const getSecurityEventsApi = (params: LogListParams) => {
  return request.get<LogResponse<PageData<SecurityEventItem>>>('/admin/logs/security-events', { params })
}

// 日志详情（category: operations / login-events / security-events）
export const getLogDetailApi = (category: string, id: number) => {
  return request.get<LogResponse<OperateLogItem | LoginLogItem | SecurityEventItem>>(
    `/admin/logs/${category}/${id}`
  )
}

// 筛选选项（动作/模块/事件类型等由后端返回）
export const getLogOptionsApi = () => {
  return request.get<LogResponse<LogOptions>>('/admin/logs/options')
}

// 导出日志（当前标签及筛选条件，服务端流式 XLSX）
export const exportLogsApi = (data: LogListParams & { category: string }) => {
  return request.post('/admin/logs/export', data, { responseType: 'blob' })
}

// 获取留存策略
export const getRetentionPoliciesApi = () => {
  return request.get<LogResponse<RetentionPolicies>>('/admin/logs/retention/policies')
}

// 更新留存策略
export const updateRetentionPoliciesApi = (policies: Partial<RetentionPolicies>) => {
  return request.put<LogResponse<RetentionPolicies>>('/admin/logs/retention/policies', policies)
}

// 留存清理预览
export const previewRetentionApi = () => {
  return request.post<LogResponse<RetentionPreview>>('/admin/logs/retention/preview', {})
}

// 执行留存清理（需填写原因）
export const runRetentionApi = (reason: string) => {
  return request.post<LogResponse<{ deletedCount: number }>>('/admin/logs/retention/run', { reason })
}

export interface IntegrityVerifyReport {
  passed: boolean
  totalIssues: number
  signatureRoots: Record<string, number>
  [key: string]: unknown
}

// 手动触发全量完整性校验（P2：哈希链重算 + 签名根验证）
export const verifyIntegrityApi = () => {
  return request.post<LogResponse<IntegrityVerifyReport>>('/admin/logs/integrity/verify', {})
}
