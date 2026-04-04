import request from '../utils/request'

interface Announcement {
  id: number
  title: string
  content: string
  publisherId?: number
  publisherName?: string
  publisherRole?: string
  publishTime?: string
  createTime?: string
  updateTime?: string
  status?: number
  attachments?: string[]
  isRead?: boolean
  readCount?: number
}

interface PageParams {
  page?: number
  pageSize?: number
  title?: string
  status?: string
  publisher?: string
  publisherRole?: string
  startTime?: string
  endTime?: string
}

interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

interface PageResponse<T = unknown> {
  rows: T[]
  total: number
}

interface ImportResult {
  code: number
  data?: unknown
  msg: string
}

interface ReadRecord {
  id: number
  announcementId: number
  userId: number
  userName: string
  userRole: string
  readTime: string
}

// 获取所有公告
export const getAllAnnouncements = () => {
  return request<ApiResponse<Announcement[]>>({
    url: '/announcements',
    method: 'get'
  }) as unknown as Promise<{ data: ApiResponse<Announcement[]> }>
}

// 根据 ID 获取公告
export const getAnnouncementById = (id: number) => {
  return request<ApiResponse<Announcement>>({
    url: `/announcements/${id}`,
    method: 'get'
  })
}

// 分页查询公告
export const getAnnouncementsByPage = (params: PageParams) => {
  return request<ApiResponse<PageResponse<Announcement>>>({
    url: '/announcements/page',
    method: 'get',
    params
  })
}

// 新增公告
export const addAnnouncement = (data: Omit<Announcement, 'id' | 'publishTime' | 'createTime' | 'updateTime'>) => {
  return request<ApiResponse<Announcement>>({
    url: '/announcements',
    method: 'post',
    data
  })
}

// 更新公告
export const updateAnnouncement = (id: number, data: Partial<Announcement>) => {
  return request<ApiResponse<Announcement>>({
    url: `/announcements/${id}`,
    method: 'put',
    data
  })
}

// 更新公告（带附件）
export const updateAnnouncementWithAttachments = (id: number, data: Partial<Announcement>) => {
  return request<ApiResponse<Announcement>>({
    url: `/announcements/${id}/with-attachments`,
    method: 'put',
    data
  })
}

// 删除公告
export const deleteAnnouncement = (id: number) => {
  return request<ApiResponse<void>>({
    url: `/announcements/${id}`,
    method: 'delete'
  })
}

// 批量删除公告
export const batchDeleteAnnouncements = (ids: number[]) => {
  return request<ApiResponse<void>>({
    url: '/announcements/batch',
    method: 'delete',
    data: { ids }
  })
}

// 搜索公告
export const searchAnnouncements = (params: PageParams) => {
  return request<ApiResponse<Announcement[]>>({
    url: '/announcements/search',
    method: 'get',
    params
  })
}

// 获取公告阅读记录
export const getAnnouncementReadRecords = (announcementId: number) => {
  return request<ApiResponse<ReadRecord[]>>({
    url: `/announcement-read-records/by-announcement/${announcementId}`,
    method: 'get'
  })
}

// 创建公告阅读记录
export const createAnnouncementReadRecord = (data: { announcementId: number; userId: number; userType: string }) => {
  return request<ApiResponse<ReadRecord>>({
    url: '/announcement-read-records',
    method: 'post',
    data
  })
}

// 检查公告阅读状态
export const checkAnnouncementReadStatus = (announcementId: number) => {
  return request<ApiResponse<{ isRead: boolean }>>({
    url: `/announcement-read-records/check-read/${announcementId}`,
    method: 'get'
  })
}

// 根据用户类型和用户信息获取公告列表
export const getAnnouncementsForUser = (userType: string, userInfo: string, userId?: string) => {
  return request<ApiResponse<Announcement[]>>({
    url: '/announcements/user',
    method: 'get',
    params: { userType, userInfo, userId }
  })
}

// 根据发布人身份获取用户列表
export const getUsersByPublisherRole = (publisherRole: string) => {
  return request<ApiResponse<unknown[]>>({
    url: '/announcements/users',
    method: 'get',
    params: { publisherRole }
  })
}

// 导出公告数据到 Excel
export const exportAnnouncements = (params: PageParams) => {
  return request({
    url: '/announcements/export',
    method: 'get',
    params,
    responseType: 'blob'
  })
}

// 从 Excel 导入公告数据
export const importAnnouncements = async (formData: FormData): Promise<ImportResult> => {
  try {
    const response = await request({
      url: '/announcements/import',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    const result = response.data || response

    if (result.code === 200) {
      return {
        code: 200,
        data: result.data || result,
        msg: result.msg || '导入成功'
      }
    } else {
      return {
        code: result.code || 500,
        data: result.data || null,
        msg: result.msg || result.message || '导入失败'
      }
    }
  } catch (error) {
    console.error('导入公告数据失败:', error)
    return {
      code: 500,
      msg: (error as { response?: { data?: { message?: string; msg?: string }; message?: string } }).response?.data?.message || (error as { response?: { data?: { message?: string; msg?: string }; message?: string } }).response?.data?.msg || (error as { message?: string }).message || '导入失败'
    }
  }
}

// 新增公告（带附件）
export const addAnnouncementWithAttachments = (data: Omit<Announcement, 'id' | 'publishTime' | 'createTime' | 'updateTime'>) => {
  return request<ApiResponse<Announcement>>({
    url: '/announcements/with-attachments',
    method: 'post',
    data
  })
}

// 上传单个文件
export const uploadFile = async (formData: FormData) => {
  try {
    const response = await request({
      url: '/upload/file',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    return response
  } catch (error) {
    console.error('文件上传失败:', error)
    throw error
  }
}

// 批量上传文件
export const uploadFiles = async (formData: FormData) => {
  try {
    const response = await request({
      url: '/upload/files',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    return response
  } catch (error) {
    console.error('批量上传文件失败:', error)
    throw error
  }
}

// 下载公告附件
export const downloadAnnouncementAttachment = (announcementId: number, fileName: string) => {
  return request({
    url: '/announcements/download-attachment',
    method: 'get',
    params: { announcementId, fileName },
    responseType: 'blob'
  })
}
