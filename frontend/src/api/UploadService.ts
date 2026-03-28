import request from '@/utils/request'

export interface UploadResponse {
  code: number
  data: {
    url: string
    fileName: string
  }
  message?: string
}

export default {
  uploadImage(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request<UploadResponse>({
      url: '/upload/image',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  uploadImages(files: File[]) {
    const formData = new FormData()
    files.forEach(file => {
      formData.append('files', file)
    })
    return request<UploadResponse>({
      url: '/upload/images',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  uploadFile(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request<UploadResponse>({
      url: '/upload/file',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  uploadFiles(files: File[]) {
    const formData = new FormData()
    files.forEach(file => {
      formData.append('files', file)
    })
    return request<UploadResponse>({
      url: '/upload/files',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  }
}
