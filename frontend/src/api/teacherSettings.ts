import request from '../utils/request'

export const getInternshipStatus = () => {
  return request({
    url: '/settings/internship-status',
    method: 'get'
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

export interface InternshipCalendar {
  startDate: string
  endDate: string
  reportCycle: number
  appraisalDeadline: string
  version: string | null
}

export const getInternshipCalendar = () => {
  return request({
    url: '/settings/internship-calendar',
    method: 'get'
  })
}

export const updateInternshipCalendar = (data: InternshipCalendar) => {
  return request({
    url: '/settings/internship-calendar',
    method: 'put',
    data
  })
}

// 查询心得周期配置项是否可见（依赖智慧评分菜单权限 menu:evaluation 是否授予任一教师角色）
export const getReportCycleVisible = () => {
  return request({
    url: '/settings/report-cycle-visible',
    method: 'get'
  })
}
