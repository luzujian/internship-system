import request from '@/utils/request'

export default {
  uploadImage(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request({
      url: '/api/upload/image',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  uploadImages(files) {
    const formData = new FormData()
    files.forEach(file => {
      formData.append('files', file)
    })
    return request({
      url: '/api/upload/images',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  uploadFile(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request({
      url: '/api/upload/file',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  uploadFiles(files) {
    const formData = new FormData()
    files.forEach(file => {
      formData.append('files', file)
    })
    return request({
      url: '/api/upload/files',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  }
}
