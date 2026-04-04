import request from '@/utils/request'

export interface StudentArchive {
  id: number
  studentId: number
  fileName: string
  fileUrl: string
  fileType: string
  uploadTime: string
}

export default {
  getStudentArchives(studentId: number) {
    return request({
      url: `/admin/archives/company/student/${studentId}`,
      method: 'get'
    })
  },

  downloadArchive(id: number) {
    return request({
      url: `/admin/archives/company/download/${id}`,
      method: 'get',
      responseType: 'blob'
    })
  },

  previewArchive(id: number) {
    return request({
      url: `/admin/archives/company/preview/${id}`,
      method: 'get',
      responseType: 'blob'
    })
  }
}
