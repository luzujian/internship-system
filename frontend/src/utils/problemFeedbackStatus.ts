export const feedbackStatusOptions = [
  { label: '待处理', value: 'processing' },
  { label: '已处理', value: 'resolved' },
]

export const normalizeFeedbackStatus = (status: unknown): 'processing' | 'resolved' | undefined => {
  if (status === 'pending' || status === 'processing') return 'processing'
  if (status === 'resolved' || status === 'closed') return 'resolved'
  return undefined
}

export const getFeedbackStatusText = (status: unknown): string => {
  const value = normalizeFeedbackStatus(status)
  return feedbackStatusOptions.find(option => option.value === value)?.label || '未知状态'
}

export const getFeedbackStatusTag = (status: unknown): 'success' | 'warning' | 'info' => {
  const value = normalizeFeedbackStatus(status)
  return value === 'resolved' ? 'success' : value === 'processing' ? 'warning' : 'info'
}
