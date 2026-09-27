import request from '@/utils/request'

export interface ProgressListResponse {
  items: Array<any>
  nextCursor: string | null
  hasMore: boolean
}

// 获取学生首页最近动态时间线（游标分页）
export function getProgressList({ limit, cursor }: { limit: number; cursor?: string | null }): Promise<ProgressListResponse> {
  return request.get('/student/home/progress-list', {
    params: { limit, ...(cursor ? { cursor } : {}) }
  }).then((response: any) => {
    if (response?.code && response.code !== 200) {
      throw new Error(response.message || '获取最近动态失败')
    }
    return response?.data ?? response ?? {}
  })
}
