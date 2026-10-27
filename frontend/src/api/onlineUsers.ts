import request from '@/utils/request'

export interface OnlineUserSummary {
  totalOnline: number
  active: number
  idle: number
  byRole: Record<string, number>
  serverTime: number
}

export interface OnlineUser {
  key: string
  username: string
  displayName: string
  accountType: string
  role: string
  teacherType: string
  status: 'ACTIVE' | 'IDLE'
  lastSeenAt: number
  lastActivityAt: number
  sessionCount: number
  simulation: boolean
}

export interface OnlineUserQuery { keyword?: string; role?: string; status?: string; page?: number; pageSize?: number }
export interface OnlineUserPage {
  rows: OnlineUser[]
  total: number
  page: number
  pageSize: number
  summary: OnlineUserSummary
  serverTime: number
}

const config = (signal?: AbortSignal) => ({ signal, skipErrorMessage: true })
export const getOnlineUserSummary = (signal?: AbortSignal) =>
  request.get<OnlineUserSummary>('/admin/online-users/summary', config(signal))
export const getOnlineUsers = (params: OnlineUserQuery, signal?: AbortSignal) =>
  request.get<OnlineUserPage>('/admin/online-users', { ...config(signal), params })

export const ONLINE_ROLES = [
  { value: 'STUDENT', label: '学生' }, { value: 'TEACHER', label: '教师' },
  { value: 'COMPANY', label: '企业' }, { value: 'ADMIN', label: '管理员' }
]

export const formatOnlineTime = (time?: number) => time ? new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
}).format(time) : '—'
