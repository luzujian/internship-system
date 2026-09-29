const roleNameMap: Record<string, string> = {
  STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员', COMPANY: '企业', SYSTEM: '系统', UNKNOWN: '未知',
  '管理员切换': '管理员切换'
}

export const getRoleName = (role?: string) => {
  if (!role) return '未知'
  const normalized = role.startsWith('ROLE_') ? role.substring(5) : role
  return roleNameMap[normalized] || normalized
}

const riskNameMap: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '严重' }
export const getRiskName = (risk?: string) => risk ? (riskNameMap[risk] || risk) : ''

export const getRiskTagType = (risk?: string) => {
  switch (risk) {
    case 'CRITICAL': return 'danger'
    case 'HIGH': return 'danger'
    case 'MEDIUM': return 'warning'
    default: return 'info'
  }
}

const eventNameMap: Record<string, string> = {
  LOGIN_SUCCESS: '登录成功', LOGIN_FAILURE: '登录失败', ACCOUNT_LOCK: '账号锁定', LOGOUT: '登出',
  SESSION_TIMEOUT: '会话超时', ACCOUNT_DISABLED: '账号禁用', TOKEN_INVALID: '无效令牌',
  RETENTION_CLEAN: '留存清理'
}
export const getEventTypeName = (type?: string) => type ? (eventNameMap[type] || type) : ''

export const getResultTagType = (result?: string) => {
  switch (result) {
    case 'SUCCESS': return 'success'
    case 'ATTEMPTED': return 'info'
    case 'FAILURE': return 'danger'
    default: return 'info'
  }
}

export const getRoleTagType = (role?: string) => {
  const normalized = role?.startsWith('ROLE_') ? role.substring(5) : role
  switch (normalized) {
    case 'ADMIN': return 'danger'
    case 'TEACHER': return 'success'
    case 'TEACHER_COLLEGE': return 'success'
    case 'TEACHER_DEPARTMENT': return 'success'
    case 'TEACHER_COUNSELOR': return 'success'
    case 'STUDENT': return 'primary'
    case 'COMPANY': return 'warning'
    default: return 'info'
  }
}

export const getResultName = (result?: string) => {
  const names: Record<string, string> = { SUCCESS: '成功', FAILURE: '失败', ATTEMPTED: '尝试' }
  return result ? (names[result] || result) : '未知结果'
}
