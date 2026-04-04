// 日志服务
import request from '@/utils/request'

export interface OperationLog {
  id: number
  operatorName?: string
  operatorRole?: string
  operationType?: string
  module?: string
  action?: string
  method?: string
  url?: string
  params?: string
  result?: string
  ip?: string
  userAgent?: string
  time?: number
  createTime?: string
}

export interface LoginLog {
  id: number
  username: string
  userType: string
  loginTime: string
  ip?: string
  userAgent?: string
  status?: number
}

export interface LogResponse {
  code: number
  data: OperationLog[] | LoginLog[] | { rows: OperationLog[] | LoginLog[]; total: number }
  msg?: string
}

// 获取登录日志数据 - 登录日志存储在操作日志表中，操作类型为 LOGIN
export const getLoginLogsApi = (params: {
  page?: number
  pageSize?: number
  userType?: string
  startDate?: string
  endDate?: string
  sortField?: string
  sortOrder?: string
}) => {
  // 确保分页参数是数字类型
  const numericPage = parseInt(params.page as unknown as string) || 1
  const numericPageSize = parseInt(params.pageSize as unknown as string) || 1000

  const queryParams: Record<string, unknown> = {
    page: numericPage,
    pageSize: numericPageSize,
    sortField: params.sortField || 'login_time',
    sortOrder: params.sortOrder || 'desc',
    operationType: 'LOGIN'
  }

  // 添加可选参数
  if (params.userType && String(params.userType).trim() !== '') {
    queryParams.userType = String(params.userType)
  }
  if (params.startDate) {
    queryParams.startDate = params.startDate
  }
  if (params.endDate) {
    queryParams.endDate = params.endDate
  }

  return request.get<LogResponse>('/admin/logs/operation', {
    params: queryParams
  })
}

// 获取登录日志数据 - 调用新的/login-logs 接口
export const getLoginLogsByNewApi = (params: {
  page?: number
  pageSize?: number
  userType?: string
  startDate?: string
  endDate?: string
  sortField?: string
  sortOrder?: string
}) => {
  // 确保分页参数是数字类型
  const numericPage = parseInt(params.page as unknown as string) || 1
  const numericPageSize = parseInt(params.pageSize as unknown as string) || 1000

  const queryParams: Record<string, unknown> = {
    page: numericPage,
    pageSize: numericPageSize,
    sortField: params.sortField || 'login_time',
    sortOrder: params.sortOrder || 'desc'
  }

  // 添加可选参数
  if (params.userType && String(params.userType).trim() !== '') {
    queryParams.userType = String(params.userType)
  }
  if (params.startDate) {
    queryParams.startDate = params.startDate
  }
  if (params.endDate) {
    queryParams.endDate = params.endDate
  }

  return request.get<LoginLog[]>('/login-logs', {
    params: queryParams
  })
}

// 记录登录日志（由后端在登录时自动完成，不需要前端手动调用）
export const recordLoginLogApi = (loginInfo: Record<string, unknown>) => {
  return Promise.resolve()
}

// 分页条件查询
export const queryPageApi = (
  operatorName?: string,
  operatorRole?: string,
  operationType?: string,
  module?: string,
  page?: number | string,
  pageSize?: number | string,
  sortField = 'id',
  sortOrder = 'desc'
) => {
  // 确保 page 和 pageSize 是数字类型
  const numericPage = parseInt(page as unknown as string) || 1
  const numericPageSize = parseInt(pageSize as unknown as string) || 15

  // 构建参数对象
  const params: Record<string, unknown> = {
    page: numericPage,
    pageSize: numericPageSize,
    sortField: sortField,
    sortOrder: sortOrder
  }

  // 只有当参数有实际值且转换为字符串后 trim 不为空时才添加到查询参数中
  if (operatorName && String(operatorName).trim() !== '') {
    params.operatorName = String(operatorName)
  }
  if (operatorRole && String(operatorRole).trim() !== '') {
    params.operatorRole = String(operatorRole)
  }
  if (operationType && String(operationType).trim() !== '') {
    params.operationType = String(operationType)
  }
  if (module && String(module).trim() !== '') {
    params.module = String(module)
  }

  return request.get<LogResponse>('/admin/logs/operation', {
    params: params
  })
}

// 清理操作日志（手动清理 - 支持按角色清理）
export const cleanLogsApi = (operatorRole: string | null = null, deleteAll = false) => {
  const params: Record<string, unknown> = {}
  if (operatorRole) {
    params.operatorRole = operatorRole
  }
  if (deleteAll) {
    params.deleteAll = true
  }
  return request.delete('/admin/logs/operation/clean', { params })
}

// 清理操作日志（自动清理 - 按角色阈值自动清理）
export const autoCleanLogsApi = () => {
  return request.delete('/admin/logs/operation/clean/auto')
}
