import request from '@/utils/request'

export default {
  getStudentArchives(studentId) {
    return request({
      url: `/api/admin/archives/company/student/${studentId}`,
      method: 'get'
    })
  },

  downloadArchive(id) {
    return request({
      url: `/api/admin/archives/company/download/${id}`,
      method: 'get',
      responseType: 'blob'
    })
  },

  previewArchive(id) {
    return request({
      url: `/api/admin/archives/company/preview/${id}`,
      method: 'get',
      responseType: 'blob'
    })
  }
}
