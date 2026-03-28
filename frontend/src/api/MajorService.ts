import request from '@/utils/request'

export interface Major {
  id: number
  name: string
  code: string
  departmentId?: number
  createTime?: string
  updateTime?: string
}

export default {
  getAllMajors() {
    return request<Major[]>({
      url: '/majors',
      method: 'get'
    })
  },

  getMajorById(id: number) {
    return request<Major>({
      url: `/majors/${id}`,
      method: 'get'
    })
  },

  searchMajorsByName(name: string) {
    return request<Major[]>({
      url: '/majors/search',
      method: 'get',
      params: { name }
    })
  }
}
