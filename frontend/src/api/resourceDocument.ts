import request from '@/utils/request'

export interface ResourceDocument {
  id: number
  title: string
  description?: string
  fileUrl?: string
  fileName?: string
  fileType?: string
  fileSize?: number
  publisherId?: number
  publisher?: string
  publisherRole?: string
  publishTime?: string
  targetType?: string
  targetValue?: string
  status?: string
  downloadCount?: number
  viewCount?: number
  createTime?: string
  updateTime?: string
}

export interface ResourceDocumentResponse {
  code: number
  data: ResourceDocument | ResourceDocument[] | { rows: ResourceDocument[]; total: number }
  msg?: string
}

type AxiosResponseWithMeta<T> = {
  data: {
    code: number
    data: T
    msg?: string
  }
}

export const getAllResourceDocuments = () => {
  return request.get<ResourceDocument[]>('/resource-documents') as unknown as AxiosResponseWithMeta<ResourceDocument[]>
}

export const getPublishedResourceDocuments = () => {
  return request.get<ResourceDocument[]>('/resource-documents/published') as unknown as AxiosResponseWithMeta<ResourceDocument[]>
}

export const getResourceDocumentById = (id: number) => {
  return request.get<ResourceDocument>(`/resource-documents/${id}`)
}

export const getResourceDocumentsByPage = (params: {
  page?: number
  pageSize?: number
  title?: string
  fileType?: string
  status?: string
  publisherRole?: string
}) => {
  return request<ResourceDocumentResponse>('/resource-documents/page', { params })
}

export const addResourceDocument = (data: Omit<ResourceDocument, 'id' | 'createTime' | 'updateTime'>) => {
  return request.post('/resource-documents', data)
}

export const updateResourceDocument = (id: number, data: Partial<ResourceDocument>) => {
  return request.put(`/resource-documents/${id}`, data)
}

export const deleteResourceDocument = (id: number) => {
  return request.delete(`/resource-documents/${id}`)
}

export const batchDeleteResourceDocuments = (ids: number[]) => {
  return request.delete('/resource-documents/batch', { data: { ids } })
}

export const searchResourceDocuments = (params: {
  keyword?: string
  fileType?: string
  page?: number
  pageSize?: number
}) => {
  return request.get<ResourceDocument[]>('/resource-documents/search', { params })
}

export const uploadResourceDocument = (formData: FormData) => {
  return request.post('/resource-documents/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const downloadResourceDocument = (id: number) => {
  return request.get(`/resource-documents/download/${id}`, {
    responseType: 'blob'
  })
}

export const getTeacherResourceDocumentsByPage = (params: {
  page?: number
  pageSize?: number
  title?: string
  status?: string
  publisherRole?: string
  filterType?: string
}) => {
  return request<ResourceDocumentResponse>('/resource-documents/teacher/page', { params })
}

export const teacherUploadResourceDocument = (formData: FormData) => {
  return request.post('/resource-documents/teacher/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const teacherUpdateResourceDocument = (id: number, data: Partial<ResourceDocument>) => {
  return request.put(`/resource-documents/teacher/${id}`, data)
}

export const teacherDeleteResourceDocument = (id: number) => {
  return request.delete(`/resource-documents/teacher/${id}`)
}

export const teacherBatchDeleteResourceDocuments = (ids: number[]) => {
  return request.delete('/resource-documents/teacher/batch', { data: { ids } })
}
