import request from '@/utils/request'

export interface ProblemFeedback {
  id: number
  title: string
  content: string
  userId?: number
  userType?: string
  status?: string
  adminReply?: string
  reply?: string
  replyTime?: string
  createTime?: string
  updateTime?: string
}

export interface FeedbackResponse {
  code: number
  data: ProblemFeedback | ProblemFeedback[] | { rows: ProblemFeedback[]; total: number }
  msg?: string
}

type AxiosResponseWithMeta<T> = {
  data: {
    code: number
    data: T
    msg?: string
  }
}

export const getAllFeedback = () => {
  return request.get<ProblemFeedback[]>('/problem-feedback') as unknown as AxiosResponseWithMeta<ProblemFeedback[]>
}

export const getFeedbackById = (id: number) => {
  return request.get<ProblemFeedback>(`/problem-feedback/${id}`)
}

export const getFeedbackByPage = (params: {
  page?: number
  pageSize?: number
  status?: string
  userType?: string
  userId?: number
  userName?: string
  title?: string
}) => {
  return request.get<ProblemFeedback[]>('/problem-feedback/page', { params })
}

export const addFeedback = (data: Omit<ProblemFeedback, 'id' | 'createTime' | 'updateTime'>) => {
  return request.post('/problem-feedback', data)
}

export const updateFeedback = (id: number, data: Partial<ProblemFeedback>) => {
  return request.put(`/problem-feedback/${id}`, data)
}

export const deleteFeedback = (id: number) => {
  return request.delete(`/problem-feedback/${id}`)
}

export const batchDeleteFeedback = (ids: number[]) => {
  return request.delete('/problem-feedback/batch', { data: { ids } })
}

export const getMyFeedback = (userType: string, userId: number) => {
  return request.get<ProblemFeedback[]>('/problem-feedback/my-feedback', {
    params: { userType, userId }
  })
}

export const updateFeedbackStatus = (id: number, status: string) => {
  return request.put(`/problem-feedback/${id}/status`, { status })
}

export type FeedbackDeliveryStatus = 'SENDING' | 'SENT' | 'FAILED' | 'UNKNOWN'

export interface FeedbackEmailReceipt {
  id: number
  requestId: string
  recipientEmail: string
  subject: string
  replyContent: string
  adminName: string
  feedbackStatus: string
  deliveryStatus: FeedbackDeliveryStatus
  deliveryDetail: string
  createdAt: string
  finishedAt?: string
}

export interface FeedbackEmailContext {
  feedback: ProblemFeedback
  recipientEmail: string
  senderName: string
  senderEmail: string
  mailConfigured: boolean
  feedbackVersion: number
  history: FeedbackEmailReceipt[]
}

export interface FeedbackEmailReplyRequest {
  requestId: string
  recipientEmail: string
  adminReply: string
  status: string
  feedbackVersion: number
  previousReplyId: number | null
}

export const getFeedbackEmailContext = (id: number) => {
  return request.get(`/problem-feedback/${id}/email-reply`)
}

export const replyFeedback = (id: number, data: FeedbackEmailReplyRequest) => {
  return request.put(`/problem-feedback/${id}/reply`, data)
}

export const getFeedbackStatistics = () => {
  return request.get('/problem-feedback/statistics')
}

export const getProcessingCount = () => {
  return request.get('/problem-feedback/processing-count')
}

export const exportFeedbackData = (params: Record<string, unknown> = {}) => {
  return request.get('/problem-feedback/export', {
      params,
    responseType: 'blob'
  })
}
