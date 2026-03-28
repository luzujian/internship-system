import request from '../utils/request'

export interface InternshipStatus {
  name: string
  value: number
  color: string
}

export interface AnnouncementWithReadStatus {
  id: number
  title: string
  content: string
  time: string
  isRead: boolean
  expanded?: boolean
}

export interface HomeStats {
  internshipRate: number
  unreadCount: number
  pendingApprovalCount: number
  companyCount: number
  statusData: InternshipStatus[]
  announcements: AnnouncementWithReadStatus[]
}

export const homeApi = {
  getHomeStats: async (): Promise<HomeStats> => {
    try {
      const response = await request.get<{ code: number; message: string; data: HomeStats }>('/home/stats')
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch home stats: ${error}`)
    }
  },

  markAnnouncementAsRead: async (announcementId: number): Promise<void> => {
    try {
      await request.post(`/home/announcement/${announcementId}/read`)
    } catch (error) {
      throw new Error(`Failed to mark announcement ${announcementId} as read: ${error}`)
    }
  },

  markAnnouncementAsUnread: async (announcementId: number): Promise<void> => {
    try {
      await request.post(`/home/announcement/${announcementId}/unread`)
    } catch (error) {
      throw new Error(`Failed to mark announcement ${announcementId} as unread: ${error}`)
    }
  }
}
