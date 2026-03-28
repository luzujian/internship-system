import request from '@/utils/request'

export interface Position {
  id: number
  companyId: number
  positionName: string
  department?: string
  positionType?: string
  province?: string
  city?: string
  district?: string
  detailAddress?: string
  salaryMin?: number | null
  salaryMax?: number | null
  description?: string
  requirements?: string
  internshipStartDate?: string | null
  internshipEndDate?: string | null
  plannedRecruit: number
  recruitedCount: number
  remainingQuota: number
  status: string
  publishDate: string
  createTime?: string
  updateTime?: string
}

export interface PositionStatistics {
  totalPositions: number
  activePositions: number
  pausedPositions: number
  totalPlannedRecruit: number
  totalRecruited: number
  totalRemaining: number
}

export default {
  getPositionsByCompanyId(companyId: number, params: Record<string, unknown> = {}) {
    return request({
      url: `/admin/positions/company/${companyId}`,
      method: 'get',
      params
    })
  },

  getPositionById(id: number) {
    return request({
      url: `/admin/positions/${id}`,
      method: 'get'
    })
  },

  createPosition(data: Partial<Position>) {
    return request({
      url: '/admin/positions',
      method: 'post',
      data
    })
  },

  updatePosition(id: number, data: Partial<Position>) {
    return request({
      url: `/admin/positions/${id}`,
      method: 'put',
      data
    })
  },

  deletePosition(id: number) {
    return request({
      url: `/admin/positions/${id}`,
      method: 'delete'
    })
  },

  pausePosition(id: number) {
    return request({
      url: `/admin/positions/${id}/pause`,
      method: 'put'
    })
  },

  resumePosition(id: number) {
    return request({
      url: `/admin/positions/${id}/resume`,
      method: 'put'
    })
  },

  getStatistics(companyId: number) {
    return request<PositionStatistics>({
      url: '/admin/positions/statistics',
      method: 'get',
      params: { companyId }
    })
  },

  updateRecruitedCount(id: number, change: number) {
    return request({
      url: `/admin/positions/${id}/recruited-count`,
      method: 'put',
      params: { change }
    })
  },

  // 学生端收藏相关
  // 切换收藏状态
  toggleFavorite(positionId: number) {
    return request({
      url: `/positions/favorite/${positionId}`,
      method: 'post'
    })
  },

  // 获取收藏列表
  getFavorites() {
    return request({
      url: '/positions/favorites',
      method: 'get'
    })
  }
}
