import request from '@/utils/request'

export default {
  getAllMajors() {
    return request({
      url: '/api/majors',
      method: 'get'
    })
  },

  getMajorById(id) {
    return request({
      url: `/api/majors/${id}`,
      method: 'get'
    })
  },

  searchMajorsByName(name) {
    return request({
      url: '/api/majors/search',
      method: 'get',
      params: { name }
    })
  }
}
