import request from '../utils/request'
import type { ApiResult } from '../utils/request'

export interface AutoTaskPreview {
  targetStudentIds?: number[]
  targetLabel?: string
  actionId?: string
  name: string
  triggerCondition: string
  triggerLabel: string
  triggerDays: number | null
  action: string
  actionLabel: string
  frequencyMinutes: number
  frequencyLabel: string
  endAt: string | null
  scheduleConfig: string
  scheduleLabel: string
  messageTemplate: string
  questions: string[]
  needsConfirm: boolean
  channelAvailable: boolean
}

export interface AutoTask {
  targetStudentIds?: number[]
  targetLabel?: string
  version: number
  id: number
  teacherId: number
  name: string
  triggerCondition: string
  triggerDays: number | null
  frequencyMinutes: number
  endAt?: string | null
  scheduleConfig?: string | null
  status: 'ACTIVE' | 'PAUSED' | 'ENDED' | string
  blockedReason?: string | null
  action: string
  messageTemplate: string
  enabled: number
  lastRunAt?: string
  createTime?: string
  updateTime?: string
}

export interface AutoTaskCapability {
  scopeLabel?: string
  globalCooldownMinutes?: number
  canManage: boolean
  canNotify: boolean
  canExecute: boolean
  actions: string[]
  channels: string[]
  scopes: string[]
  maxTargetsPerRun: number
  teacherType?: string | null
  emailConfigured: boolean
  aiEnabled?: boolean
  timezone?: string
  missedGraceMinutes?: number
}

export interface AutoTaskLog {
  id: number
  taskId: number
  executedAt: string
  channel: string
  targetCount: number
  successCount: number
  detail: string
}

export interface AutoTaskSendLog {
  id: number
  taskId: number
  studentId: number
  studentName: string
  channel: string
  sentAt: string
  status: 'SENT' | 'SKIPPED_COOLDOWN' | 'FAILED' | 'PENDING' | 'UNKNOWN' | string
  detail: string
}

export interface AutoTaskDraft {
  targetStudentIds?: number[]
  targetLabel?: string
  version?: number
  name: string
  triggerCondition: string
  triggerDays: number | null
  action: string
  messageTemplate: string
  frequencyMinutes: number
  endAt?: string | null
  scheduleConfig: AutoTaskSchedule
}

export interface AutoTaskSchedule {
  mode: 'WINDOW' | 'ONCE' | 'DAILY' | 'WEEKLY' | 'MANUAL'
  date?: string | null
  time?: string | null
  weekdays?: number[]
}

export interface TaskActionPreview {
  task?: AutoTask
  scopeLabel?: string
  scopeCount?: number
  matchingCount?: number
  effectiveFrequencyMinutes?: number
  maxTargetsPerRun?: number
  id: string
  status: string
  parameters: Record<string, unknown>
  expectedVersion?: string
  expiresAt?: number
  receipt?: AutoTask
  errorMessage?: string
}
export interface AutoTaskRun {
  runId?: string
  status: 'NOT_FOUND' | 'RUNNING' | 'UNKNOWN' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED' | 'CANCELLED'
  receipt?: { targetCount: number; successCount: number; detail: string }
  errorMessage?: string
}
export const previewAutoTaskConfirmation = async (payload: AutoTaskDraft): Promise<ApiResult<TaskActionPreview>> => {
  return request.post('/teacher/auto-tasks/confirmation-preview', payload)
}
export const previewAutoTaskEnable = async (id: number): Promise<ApiResult<TaskActionPreview>> => {
  return request.post(`/teacher/auto-tasks/${id}/enable/preview`)
}
export const getAutoTaskAction = async (id: string): Promise<ApiResult<TaskActionPreview>> => {
  return request.get(`/agent/actions/${id}`)
}
export const getAutoTaskRun = async (id: number, clientRequestId: string): Promise<ApiResult<AutoTaskRun>> => {
  return request.get(`/teacher/auto-tasks/${id}/runs/${clientRequestId}`)
}
export const getPendingAutoTaskRun = async (id: number): Promise<ApiResult<AutoTaskRun | null>> => {
  return request.get(`/teacher/auto-tasks/${id}/runs/pending`)
}

export const previewAutoTask = async (message: string): Promise<ApiResult<AutoTaskPreview>> => {
  return request.post('/teacher/auto-tasks/preview', { message })
}

export const getAutoTaskCapability = async (): Promise<ApiResult<AutoTaskCapability>> => {
  return request.get('/teacher/auto-tasks/capability')
}

export const createAutoTask = async (payload: AutoTaskDraft & { actionId: string; confirmed: true }): Promise<ApiResult<AutoTask>> => {
  return request.post('/teacher/auto-tasks', payload)
}

export const updateAutoTask = async (id: number, payload: AutoTaskDraft): Promise<ApiResult<AutoTask>> => {
  return request.put(`/teacher/auto-tasks/${id}`, payload)
}

export const endAutoTask = async (id: number): Promise<ApiResult<AutoTask>> => {
  return request.post(`/teacher/auto-tasks/${id}/end`)
}

export const listAutoTasks = async (): Promise<ApiResult<AutoTask[]>> => {
  return request.get('/teacher/auto-tasks')
}

export const enableAutoTask = async (id: number, actionId: string, clientRequestId: string): Promise<ApiResult<AutoTask>> => {
  return request.post(`/teacher/auto-tasks/${id}/enable`, { actionId, clientRequestId, confirmed: true })
}

export const disableAutoTask = async (id: number): Promise<ApiResult<AutoTask>> => {
  return request.post(`/teacher/auto-tasks/${id}/disable`)
}

export const deleteAutoTask = async (id: number): Promise<ApiResult<unknown>> => {
  return request.delete(`/teacher/auto-tasks/${id}`)
}

export const getAutoTaskLogs = async (id: number, limit = 20): Promise<ApiResult<AutoTaskLog[]>> => {
  return request.get(`/teacher/auto-tasks/${id}/logs`, { params: { limit } })
}

export const getAutoTaskSends = async (id: number, limit = 50): Promise<ApiResult<AutoTaskSendLog[]>> => {
  return request.get(`/teacher/auto-tasks/${id}/sends`, { params: { limit } })
}

export const runAutoTask = async (id: number, clientRequestId: string): Promise<ApiResult<AutoTaskRun>> => {
  return request.post(`/teacher/auto-tasks/${id}/run`, { clientRequestId })
}
