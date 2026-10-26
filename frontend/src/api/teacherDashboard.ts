import request from '../utils/request'

export interface DashboardStats {
  totalStudents: number
  confirmed: number
  offer: number
  noOffer: number
  interrupted?: number
  delay: number
  scopeName?: string
  gradeData: Array<{
    gradeName: string
    total: number
    confirmed: number
    offer: number
    noOffer: number
    interrupted?: number
    delay: number
  }>
  majorData: Array<{
    majorName: string
    total: number
    confirmed: number
    offer: number
    noOffer: number
    interrupted?: number
    delay: number
  }>
  classData: Array<{
    className: string
    total: number
    confirmed: number
    offer: number
    noOffer: number
    interrupted?: number
    delay: number
  }>
  divisionData?: Array<{
    divisionName: string
    total: number
    confirmed: number
    offer: number
    noOffer: number
    interrupted?: number
    delay: number
  }>
}

export const dashboardApi = {
  getDashboardStats: async (params?: { startDate?: string; endDate?: string }): Promise<DashboardStats> => {
    try {
      const response = await request.get<DashboardStats>('/teacher/dashboard/stats', { params })
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch dashboard stats: ${error}`)
    }
  },

  getCounselorDashboardStats: async (counselorId: number, params?: { startDate?: string; endDate?: string }): Promise<DashboardStats> => {
    try {
      const response = await request.get<DashboardStats>(`/teacher/dashboard/counselor-stats/${counselorId}`, { params })
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch counselor dashboard stats: ${error}`)
    }
  }
}
