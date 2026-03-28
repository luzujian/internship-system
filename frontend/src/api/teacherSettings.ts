import request from '../utils/request'

export const getInternshipStatus = () => {
  return request({
    url: '/settings/internship-status',
    method: 'get'
  })
}

export const getInternshipNodes = () => {
  return request({
    url: '/settings/internship-nodes',
    method: 'get'
  })
}

export const updateInternshipNodes = (data: any) => {
  return request({
    url: '/settings/internship-nodes',
    method: 'put',
    data
  })
}

export const getAIAnalysisSettings = () => {
  return request({
    url: '/settings/ai-analysis',
    method: 'get'
  })
}

export const updateAIAnalysisSettings = (data: any) => {
  return request({
    url: '/settings/ai-analysis',
    method: 'put',
    data
  })
}

// 获取实习时间设置（学生端使用）
export const getInternshipTimeSettings = () => {
  return request({
    url: '/settings/internship-time',
    method: 'get'
  })
}
