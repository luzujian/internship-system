import request from '@/utils/request'

export interface CompanyApplicationActivity {
  id: number
  studentName: string
  positionName: string
  status: string
  viewed: boolean
  applyTime: string | null
  activityTime: string | null
  version: number | null
}

export interface CompanyActivityPage {
  items: CompanyApplicationActivity[]
  total: number
  unreadCount: number
  hasMore: boolean
}

const base = '/company/application-activities'
export const fetchCompanyActivities = async (params: { offset: number; limit: number; unreadOnly: boolean }) =>
  (await request<CompanyActivityPage>({ url: base, method: 'get', params })).data

export const fetchCompanyActivityDetail = async (id: number) =>
  (await request<{ activity: CompanyApplicationActivity; snapshot: Record<string, any>; application: Record<string, any> | null }>({
    url: `${base}/${id}`, method: 'get'
  })).data

export const fetchActivityForApplication = async (id: number) =>
  (await request<CompanyApplicationActivity | null>({ url: `${base}/for-application/${id}`, method: 'get' })).data

export const markCompanyActivityRead = (activity: CompanyApplicationActivity) => request({
  url: `${base}/${activity.id}/read`, method: 'put',
  data: { status: activity.status, version: activity.version }
})
