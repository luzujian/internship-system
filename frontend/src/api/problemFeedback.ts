import request from '@/utils/request'

export interface ProblemFeedback {
  id: number
  title: string
  content: string
  userId?: number
  userType?: string
  status?: number
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
  status?: number
  userType?: string
  userId?: number
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

export const updateFeedbackStatus = (id: number, status: number) => {
  return request.put(`/problem-feedback/${id}/status`, { status })
}

export const replyFeedback = (id: number, data: { reply: string }) => {
  return request.put(`/problem-feedback/${id}/reply`, data)
}

export const getFeedbackStatistics = () => {
  return request.get('/problem-feedback/statistics')
}

export const getProcessingCount = () => {
  return request.get('/problem-feedback/processing-count')
}

export const exportFeedbackData = () => {
  return request.get('/problem-feedback/export', {
    responseType: 'blob'
  })
}
