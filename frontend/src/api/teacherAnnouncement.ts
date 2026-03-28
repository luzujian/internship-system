import request from '../utils/request'
import type { Announcement, AnnouncementFormData, PageResult, ApiResponse } from '../types/admin'

export const announcementApi = {
  async getAllAnnouncements(): Promise<Announcement[]> {
    try {
      const response = await request.get<{ code: number; message: string; data: Announcement[] }>('/announcements')
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch all announcements: ${error}`)
    }
  },

  async getAnnouncementById(id: number): Promise<Announcement> {
    try {
      const response = await request.get<{ code: number; message: string; data: Announcement }>(`/announcements/${id}`)
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch announcement ${id}: ${error}`)
    }
  },

  async getAnnouncementsByPage(
    params?: { page?: number; pageSize?: number; title?: string; status?: string }
  ): Promise<PageResult<Announcement>> {
    try {
      const response = await request.get<{ code: number; message: string; data: PageResult<Announcement> }>('/announcements/page', {
        params: params || {}
      })
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch announcements by page: ${error}`)
    }
  },

  async createAnnouncement(data: AnnouncementFormData): Promise<void> {
    try {
      await request.post('/announcements', data)
    } catch (error) {
      throw new Error(`Failed to create announcement: ${error}`)
    }
  },

  async updateAnnouncement(id: number, data: AnnouncementFormData): Promise<void> {
    try {
      await request.put(`/announcements/${id}`, data)
    } catch (error) {
      throw new Error(`Failed to update announcement ${id}: ${error}`)
    }
  },

  async deleteAnnouncement(id: number): Promise<void> {
    try {
      await request.delete(`/announcements/${id}`)
    } catch (error) {
      throw new Error(`Failed to delete announcement ${id}: ${error}`)
    }
  },

  async batchDeleteAnnouncements(ids: number[]): Promise<void> {
    try {
      await request.delete('/announcements/batch', { data: { ids } })
    } catch (error) {
      throw new Error(`Failed to batch delete announcements: ${error}`)
    }
  },

  async searchAnnouncements(
    title?: string,
    status?: string
  ): Promise<Announcement[]> {
    try {
      const response = await request.get<{ code: number; message: string; data: Announcement[] }>('/announcements/search', {
        params: { title, status }
      })
      return response.data
    } catch (error) {
      throw new Error(`Failed to search announcements: ${error}`)
    }
  },

  async getAnnouncementsForUser(
    userType: string,
    userInfo?: string
  ): Promise<Announcement[]> {
    try {
      const response = await request.get<{ code: number; message: string; data: Announcement[] }>('/announcements/user', {
        params: { userType, userInfo }
      })
      return response.data
    } catch (error) {
      throw new Error(`Failed to fetch announcements for user: ${error}`)
    }
  },

  async createReadRecord(announcementId: number, userId: number, userType: string): Promise<void> {
    try {
      await request.post('/announcement-read-records', {
        announcementId,
        userId,
        userType
      })
    } catch (error) {
      throw new Error(`Failed to create read record: ${error}`)
    }
  },

  async checkReadStatus(announcementId: number, userId: number, userType: string): Promise<boolean> {
    try {
      const response = await request.get<{ code: number; message: string; data: boolean }>('/announcement-read-records/check', {
        params: { announcementId, userId, userType }
      })
      return response.data
    } catch (error) {
      throw new Error(`Failed to check read status: ${error}`)
    }
  },

  async getUnreadCount(announcementId: number, targetType?: string, targetValue?: string): Promise<number> {
    try {
      const response = await request.get<{ code: number; message: string; data: number }>(`/announcement-read-records/unread-count/${announcementId}`, {
        params: { targetType, targetValue }
      })
      return response.data
    } catch (error) {
      throw new Error(`Failed to get unread count: ${error}`)
    }
  },

  async getReadCount(announcementId: number): Promise<number> {
    try {
      const response = await request.get<{ code: number; message: string; data: number }>(`/announcement-read-records/read-count/${announcementId}`)
      return response.data
    } catch (error) {
      throw new Error(`Failed to get read count: ${error}`)
    }
  },

  async getUserUnreadCount(userId: number, userType: string): Promise<number> {
    try {
      const response = await request.get<{ code: number; message: string; data: number }>('/announcement-read-records/user-unread-count', {
        params: { userId, userType }
      })
      return response.data
    } catch (error) {
      throw new Error(`Failed to get user unread count: ${error}`)
    }
  }
}
